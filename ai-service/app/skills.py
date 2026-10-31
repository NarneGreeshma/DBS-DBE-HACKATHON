"""Curated skill vocabulary used for resume/job matching.

Keeping this in one module means the matching rules are testable in isolation and the
same vocabulary drives extraction, gap analysis and scoring.
"""

from __future__ import annotations

import re

# canonical skill -> aliases that count as evidence of that skill
LEXICON: dict[str, list[str]] = {
    "java": ["java"],
    "spring boot": ["spring boot", "springboot", "spring-boot"],
    "spring": ["spring framework"],
    "hibernate": ["hibernate"],
    "jpa": ["jakarta jpa", "java persistence api", "jpa"],
    "servlet": ["servlet"],
    "kotlin": ["kotlin"],
    "python": ["python"],
    "django": ["django"],
    "flask": ["flask"],
    "fastapi": ["fastapi", "fast api"],
    "javascript": ["javascript", "ecmascript"],
    "typescript": ["typescript"],
    "react": ["react", "react.js", "reactjs"],
    "angular": ["angular"],
    "vue": ["vue", "vue.js", "vuejs"],
    "nextjs": ["next.js", "nextjs", "next js"],
    "nodejs": ["node.js", "nodejs", "node js"],
    "html": ["html", "html5"],
    "css": ["css", "css3"],
    "tailwind": ["tailwind", "tailwind css", "tailwindcss"],
    "graphql": ["graphql", "graph ql"],
    "rest apis": ["rest api", "rest apis", "restful", "rest endpoints"],
    "postgresql": ["postgresql", "postgres"],
    "mysql": ["mysql", "mariadb"],
    "mongodb": ["mongodb", "mongo"],
    "redis": ["redis"],
    "sql": ["sql"],
    "elasticsearch": ["elasticsearch", "elastic search", "opensearch"],
    "docker": ["docker"],
    "kubernetes": ["kubernetes", "k8s"],
    "aws": ["aws", "amazon web services"],
    "gcp": ["gcp", "google cloud"],
    "azure": ["azure", "microsoft azure"],
    "terraform": ["terraform"],
    "jenkins": ["jenkins"],
    "ci/cd": ["ci/cd", "continuous integration", "continuous delivery"],
    "git": ["git", "github", "gitlab"],
    "linux": ["linux", "unix"],
    "nginx": ["nginx"],
    "prometheus": ["prometheus"],
    "grafana": ["grafana"],
    "kafka": ["kafka", "apache kafka"],
    "rabbitmq": ["rabbitmq"],
    "microservices": ["microservice", "microservices", "micro-services"],
    "machine learning": ["machine learning"],
    "nlp": ["nlp", "natural language processing"],
    "deep learning": ["deep learning"],
    "pytorch": ["pytorch", "torch"],
    "tensorflow": ["tensorflow"],
    "pandas": ["pandas"],
    "numpy": ["numpy"],
    "spark": ["apache spark", "pyspark"],
    "pytest": ["pytest"],
    "junit": ["junit"],
    "jest": ["jest"],
    "selenium": ["selenium"],
    "playwright": ["playwright"],
    "agile": ["agile"],
    "scrum": ["scrum"],
    "communication": ["communication skills", "stakeholder communication"],
    "leadership": ["leadership", "tech lead", "team lead"],
    "figma": ["figma"],
    "oauth": ["oauth", "oauth2"],
    "jwt": ["jwt", "json web token"],
}

# terms that look like skills but are too generic to be meaningful on their own
STOPWORDS = {
    "a", "an", "the", "and", "or", "of", "to", "in", "for", "with", "on", "at",
    "is", "are", "be", "as", "by", "it", "its", "we", "our", "you", "your",
}

_SECTION_HEADERS = re.compile(
    r"(?im)^\s{0,4}(experience|education|skills|summary|profile|objective|"
    r"projects|certifications?|employment|work history|additional)\b"
)

_EMAIL = re.compile(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")
_PHONE = re.compile(r"(?:\+?\d{1,3}[\s.-]?)?(?:\(\d{3}\)|\d{3})[\s.-]?\d{3}[\s.-]?\d{4}")
_YEARS = re.compile(r"(?i)(\d{1,2})\s*\+?\s*(?:years?|yrs?)\b")
_YEARS_ALT = re.compile(r"(?i)years?\s+(?:of\s+)?experience\s*[:\-]?\s*(\d{1,2})")


def _alias_pattern(alias: str) -> re.Pattern[str]:
    """Whole-token match so 'java' never fires inside 'javascript'."""
    return re.compile(r"(?<![a-z0-9+#])" + re.escape(alias.lower()) + r"(?![a-z0-9+#])")


_COMPILED: dict[str, list[re.Pattern[str]]] = {
    skill: [_alias_pattern(a) for a in aliases] for skill, aliases in LEXICON.items() if aliases
}


def normalize_skill(name: str) -> str:
    return re.sub(r"\s+", " ", (name or "").strip().lower())


def extract_skills(text: str) -> list[str]:
    """Every canonical skill the text provides evidence for, alphabetically."""
    haystack = (text or "").lower()
    found = [skill for skill, patterns in _COMPILED.items()
             if any(p.search(haystack) for p in patterns)]
    return sorted(found)


def resume_has_skill(text: str, skill: str) -> bool:
    """Does the resume evidence this (possibly free-form) skill name?"""
    canonical = normalize_skill(skill)
    if not canonical or canonical in STOPWORDS:
        return False
    haystack = (text or "").lower()

    if canonical in _COMPILED:
        return any(p.search(haystack) for p in _COMPILED[canonical])

    # unknown skill (e.g. a recruiter-created tag): fall back to a token-boundary match
    return re.compile(r"(?<![a-z0-9+#])" + re.escape(canonical) + r"(?![a-z0-9+#])") \
        .search(haystack) is not None


def section_headers(text: str) -> list[str]:
    return [m.group(1).lower() for m in _SECTION_HEADERS.finditer(text or "")]


def find_email(text: str) -> str | None:
    match = _EMAIL.search(text or "")
    return match.group(0) if match else None


def find_phone(text: str) -> str | None:
    match = _PHONE.search(text or "")
    return match.group(0) if match else None


def detected_years(text: str) -> int:
    """Best-effort years of experience mentioned anywhere in the resume."""
    candidates = [int(m.group(1)) for m in _YEARS.finditer(text or "")]
    candidates += [int(m.group(1)) for m in _YEARS_ALT.finditer(text or "")]
    # ignore implausible values that are really versions or dates
    candidates = [c for c in candidates if 0 <= c <= 45]
    return max(candidates, default=0)
