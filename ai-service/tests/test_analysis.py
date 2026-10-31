"""Unit tests for the scoring engine (no HTTP, no I/O)."""

from __future__ import annotations

from app.analysis import analyze, ats_score
from app.schemas import AnalyzeRequest
from app.skills import detected_years, extract_skills, resume_has_skill

STRONG_RESUME = """
Alex Rivera
Senior Backend Engineer
alex@example.com | +1 415 555 0177

SUMMARY
Backend engineer with 7 years building event-driven services.

EXPERIENCE
Senior Engineer, Acme Corp - 2019 to present
- Designed Spring Boot microservices serving 4M requests/day
- Tuned PostgreSQL queries, cutting p99 latency by 40%
- Ran Docker deployments on AWS
- Led a squad of four engineers through two migrations

Backend Engineer, Beta Ltd - 2016 to 2019
- Built Java services backed by Hibernate and JUnit
- Introduced a CI/CD pipeline that cut release time from days to hours
- Wrote runbooks and on-call rotations

SKILLS
Java, Spring Boot, PostgreSQL, Hibernate, Docker, AWS, JUnit, Git

EDUCATION
B.Tech Computer Science, State University

CERTIFICATIONS
AWS Certified Developer - Associate
"""

WEAK_RESUME = """
Jordan Doe
Marketing Intern
jordan@example.com

EXPERIENCE
Marketing Intern, Local Bakery - 2023
- Wrote newsletter copy

EDUCATION
B.A. Communications
"""


def _request(**overrides) -> AnalyzeRequest:
    payload = {
        "resumeText": STRONG_RESUME,
        "jobText": "Senior Java Engineer role",
        "jobTitle": "Senior Backend Engineer",
        "requiredSkills": ["java", "spring boot", "postgresql"],
        "preferredSkills": ["kubernetes"],
        "minYears": 5,
        "experienceYears": 7,
    }
    payload.update(overrides)
    return AnalyzeRequest(**payload)


class TestSkillExtraction:
    def test_detects_known_skills(self) -> None:
        found = extract_skills(STRONG_RESUME)
        assert "java" in found
        assert "spring boot" in found
        assert "postgresql" in found
        assert "docker" in found

    def test_java_does_not_match_javascript(self) -> None:
        assert resume_has_skill("Experienced javascript developer", "java") is False
        assert resume_has_skill("Experienced javascript developer", "javascript") is True

    def test_aliases_are_recognised(self) -> None:
        assert resume_has_skill("Worked with Postgres and k8s", "postgresql")
        assert resume_has_skill("Worked with Postgres and k8s", "kubernetes")

    def test_unknown_freeform_skill_uses_token_boundary(self) -> None:
        assert resume_has_skill("Expert in payroll systems", "payroll systems")
        assert not resume_has_skill("Expert in payroll systems", "payrollx")

    def test_empty_text_yields_nothing(self) -> None:
        assert extract_skills("") == []

    def test_years_detection(self) -> None:
        assert detected_years("7 years of experience in total") == 7
        assert detected_years("Experience: 4 years") == 4
        assert detected_years("no numbers here") == 0


class TestScoring:
    def test_strong_candidate_scores_high(self) -> None:
        result = analyze(_request())
        assert result.match_score >= 80
        assert result.ats_score >= 80
        assert result.missing_skills == ["kubernetes"]  # only the preferred gap remains
        assert any("Covers 3 required skill" in s for s in result.strengths)

    def test_missing_required_skills_are_reported(self) -> None:
        result = analyze(_request(requiredSkills=["java", "rust", "cobol"]))
        assert "rust" in result.missing_skills
        assert "cobol" in result.missing_skills
        assert any("Missing required skill" in gap for gap in result.gaps)
        assert any("rust" in rec for rec in result.recommendations)

    def test_irrelevant_resume_scores_poorly(self) -> None:
        result = analyze(_request(resumeText=WEAK_RESUME, experienceYears=0))
        assert result.match_score < 40
        assert any("Missing required skill" in gap for gap in result.gaps)
        assert any("year(s)" in gap for gap in result.gaps)

    def test_experience_shortfall_lowers_score(self) -> None:
        enough = analyze(_request(minYears=3, experienceYears=3))
        too_little = analyze(_request(minYears=10, experienceYears=2))
        assert too_little.match_score < enough.match_score
        assert any("against 10 year(s) required" in gap for gap in too_little.gaps)

    def test_scoring_is_deterministic(self) -> None:
        first = analyze(_request())
        second = analyze(_request())
        assert first == second

    def test_scores_stay_within_bounds(self) -> None:
        for payload in (_request(), _request(resumeText="", experienceYears=0),
                        _request(requiredSkills=["java"] * 50)):
            result = analyze(payload)
            assert 0 <= result.match_score <= 100
            assert 0 <= result.ats_score <= 100

    def test_empty_resume_is_explained_not_crashed(self) -> None:
        result = analyze(_request(resumeText="", experienceYears=0))
        assert result.summary.startswith("No resume text")
        assert any("No resume text was supplied" in gap for gap in result.gaps)


class TestAtsScore:
    def test_well_formed_resume_scores_high(self) -> None:
        assert ats_score(STRONG_RESUME) >= 80

    def test_bare_text_scores_low(self) -> None:
        assert ats_score("john doe") < 40

    def test_missing_contact_details_are_penalised(self) -> None:
        no_contact = "EXPERIENCE\n- did things\n" * 30
        with_contact = no_contact + "\njohn@example.com | +1 415 555 0100"
        assert ats_score(with_contact) > ats_score(no_contact)
