"""The scoring engine.

Everything here is deterministic and dependency-free: the same resume and job always
produce the same result, which makes the numbers explainable to recruiters and easy to
unit-test. No LLM calls, no network, no persistence.
"""

from __future__ import annotations

import re

from .schemas import AnalyzeRequest, AnalyzeResponse
from .skills import (
    detected_years,
    extract_skills,
    find_email,
    find_phone,
    normalize_skill,
    resume_has_skill,
    section_headers,
)

MODEL = "smarthire-rules-v1"

# component weights for the overall match score
_W_SKILL_REQUIRED = 0.55
_W_SKILL_PREFERRED = 0.15
_W_EXPERIENCE = 0.18
_W_TITLE = 0.07
_W_ATS = 0.05


def _clamp(value: float, low: float = 0.0, high: float = 100.0) -> float:
    return max(low, min(high, value))


def _round(value: float) -> float:
    return round(_clamp(value), 1)


def _overlap(candidate: list[str], target: list[str]) -> tuple[list[str], list[str]]:
    """(matched, missing) preserving the caller's ordering for `missing`."""
    wanted = [normalize_skill(s) for s in target if normalize_skill(s)]
    matched = [s for s in wanted if s in candidate]
    missing = [s for s in wanted if s not in candidate]
    return matched, missing


def ats_score(resume_text: str) -> float:
    """How well a parser (and an ATS) can make sense of this document."""
    text = resume_text or ""
    score = 0.0

    length = len(text.strip())
    if length >= 1200:
        score += 25
    elif length >= 500:
        score += 18
    elif length >= 200:
        score += 10

    if find_email(text):
        score += 20
    if find_phone(text):
        score += 15

    if len(section_headers(text)) >= 3:
        score += 20
    elif len(section_headers(text)) >= 1:
        score += 10

    lines = [ln for ln in text.splitlines() if ln.strip()]
    if len(lines) >= 20:
        score += 10
    elif len(lines) >= 8:
        score += 6

    # penalise a wall of text with no line breaks at all
    if len(lines) <= 2 and length > 800:
        score -= 10

    unusual = len(re.findall(r"[^\x20-\x7E\n\r\t]", text))
    if unusual > length * 0.05:
        score -= 10

    return _round(score)


def _title_relevance(resume_text: str, job_title: str) -> float:
    tokens = [t for t in re.split(r"[^a-z0-9+#]+", (job_title or "").lower())
              if t and t not in {"and", "the", "of", "a"}]
    if not tokens:
        return 100.0
    haystack = (resume_text or "").lower()
    hits = sum(1 for t in tokens if re.search(r"(?<![a-z0-9])" + re.escape(t) + r"(?![a-z0-9])", haystack))
    return _round(100.0 * hits / len(tokens))


def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    resume = request.resume_text or ""
    required = [normalize_skill(s) for s in (request.required_skills or [])]
    preferred = [normalize_skill(s) for s in (request.preferred_skills or [])]
    required = [s for s in required if s]
    preferred = [s for s in preferred if s]

    # Nothing to screen: scoring a blank resume would otherwise produce a high match
    # purely from the (absent) requirements, which would be actively misleading.
    if not resume.strip():
        gaps = [s for s in dict.fromkeys(required + preferred)]
        return AnalyzeResponse(
            match_score=0.0,
            ats_score=0.0,
            extracted_skills=[],
            missing_skills=gaps,
            strengths=[],
            gaps=["No resume text was supplied for analysis"],
            summary="No resume text was provided, so no screening could be performed.",
            recommendations=["Ask the candidate to upload a resume before screening"],
            model=MODEL,
        )

    found = extract_skills(resume)
    found_set = set(found)

    matched_required, missing_required = _overlap(found_set, required)
    matched_preferred, missing_preferred = _overlap(found_set, preferred)

    # ---- skill component -------------------------------------------------
    if required:
        required_ratio = len(matched_required) / len(required)
    else:
        required_ratio = 1.0 if not preferred else 0.0
    preferred_ratio = (len(matched_preferred) / len(preferred)) if preferred else 1.0
    skill_score = 100.0 * (_W_SKILL_REQUIRED * required_ratio + _W_SKILL_PREFERRED * preferred_ratio)

    # ---- experience component -------------------------------------------
    wanted_years = request.min_years or 0
    stated_years = detected_years(resume)
    effective_years = max(stated_years, request.experience_years or 0)
    if wanted_years <= 0:
        experience_score = 100.0
    else:
        experience_score = 100.0 * min(effective_years / wanted_years, 1.0)

    # ---- title relevance -------------------------------------------------
    title_score = _title_relevance(resume, request.job_title)
    parsed_score = ats_score(resume)

    total = (_W_SKILL_REQUIRED * required_ratio * 100
             + _W_SKILL_PREFERRED * preferred_ratio * 100
             + _W_EXPERIENCE * experience_score
             + _W_TITLE * title_score
             + _W_ATS * parsed_score)

    match = _round(total)

    # ---- missing skills surface required gaps first ----------------------
    missing = missing_required + [s for s in missing_preferred if s not in missing_required]

    strengths = _strengths(request, matched_required, matched_preferred,
                           effective_years, wanted_years, parsed_score, title_score)
    gaps = _gaps(request, missing_required, effective_years, wanted_years, resume, parsed_score)
    recommendations = _recommendations(request, missing_required, wanted_years,
                                       effective_years, parsed_score, title_score)
    summary = _summary(request, match, len(matched_required), len(required),
                       missing_required, effective_years, wanted_years)

    return AnalyzeResponse(
        match_score=match,
        ats_score=parsed_score,
        extracted_skills=found,
        missing_skills=missing,
        strengths=strengths,
        gaps=gaps,
        summary=summary,
        recommendations=recommendations,
        model=MODEL,
    )


def _strengths(req: AnalyzeRequest, matched_required: list[str],
               matched_preferred: list[str], years: int, wanted: int,
               parsed: float, title_score: float) -> list[str]:
    out: list[str] = []
    title = req.job_title or "this role"

    if matched_required:
        preview = ", ".join(matched_required[:5])
        out.append(f"Covers {len(matched_required)} required skill(s) for {title}: {preview}")
    elif not req.required_skills:
        top = ", ".join(extract_skills(req.resume_text or "")[:6])
        if top:
            out.append(f"Relevant skill coverage: {top}")

    if wanted and years >= wanted:
        out.append(f"Meets the {wanted}+ years experience bar ({years} year(s) evidenced)")
    elif not wanted and years:
        out.append(f"{years} year(s) of experience evidenced in the resume")

    if matched_preferred:
        out.append("Bonus skills matched: " + ", ".join(matched_preferred[:5]))

    if parsed >= 80:
        out.append("Resume parses cleanly: contact block and standard sections detected")
    if title_score >= 66:
        out.append(f"Resume wording aligns with the '{req.job_title}' title")

    if not out:
        out.append("Transferable background; the match rests on adjacent experience")
    return out


def _gaps(req: AnalyzeRequest, missing_required: list[str], years: int,
          wanted: int, resume: str, parsed: float) -> list[str]:
    out: list[str] = []
    if missing_required:
        out.append("Missing required skill(s): " + ", ".join(missing_required[:8]))
    if wanted and years < wanted:
        out.append(f"Shows {years} year(s) against {wanted} year(s) required")
    if not find_email(resume):
        out.append("No email address detected - ATS contact parsing will fail")
    if not find_phone(resume):
        out.append("No phone number detected")
    if parsed < 60:
        out.append("Low ATS readability - structure or length looks wrong for a parser")
    if not resume.strip():
        out.append("No resume text was supplied for analysis")
    return out or ["No significant gaps detected against the stated requirements"]


def _recommendations(req: AnalyzeRequest, missing_required: list[str], wanted: int,
                     years: int, parsed: float, title_score: float) -> list[str]:
    out: list[str] = []
    if missing_required:
        out.append("Add explicit evidence for: " + ", ".join(missing_required[:6]))
    if wanted and years < wanted:
        out.append("Make prior role durations obvious (months + years) so tenure is unambiguous")
    if parsed < 80:
        out.append("Use standard section headings: Experience, Education, Skills")
    if title_score < 50 and req.job_title:
        out.append(f"Mirror the target title '{req.job_title}' in the headline")
    out.append("Quantify impact in the two most recent roles (scale, latency, revenue, headcount)")
    return out[:6]


def _summary(req: AnalyzeRequest, match: float, matched: int, total: int,
             missing: list[str], years: int, wanted: int) -> str:
    title = req.job_title or "the role"
    if not (req.resume_text or "").strip():
        return "No resume text was provided, so no screening could be performed."

    band = ("a strong match" if match >= 75
            else "a reasonable match" if match >= 55
            else "a weak match" if match >= 35
            else "a poor match")

    parts = [f"The candidate is {band} for {title} (match score {match:.1f}/100)."]
    if total:
        parts.append(f"{matched} of {total} required skill(s) are evidenced.")
    elif matched:
        parts.append(f"{matched} relevant skill(s) were detected.")

    if wanted and years:
        parts.append(f"Experience evidenced: {years} year(s) against {wanted} required.")
    if missing:
        parts.append("Primary gaps: " + ", ".join(missing[:4]) + ".")
    return " ".join(parts)
