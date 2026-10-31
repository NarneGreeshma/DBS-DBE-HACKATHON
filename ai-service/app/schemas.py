"""Wire contract between Java Spring Boot (the primary backend) and this service.

Field aliases are camelCase because that is what the Java DTOs serialise.
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class AnalyzeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    resume_text: str = Field(default="", alias="resumeText")
    job_text: str = Field(default="", alias="jobText")
    job_title: str = Field(default="", alias="jobTitle")
    required_skills: list[str] = Field(default_factory=list, alias="requiredSkills")
    preferred_skills: list[str] = Field(default_factory=list, alias="preferredSkills")
    min_years: int | None = Field(default=None, alias="minYears")
    experience_years: int | None = Field(default=None, alias="experienceYears")
    experience_level: str | None = Field(default=None, alias="experienceLevel")
    candidate_name: str | None = Field(default=None, alias="candidateName")


class AnalyzeResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    match_score: float = Field(alias="matchScore")
    ats_score: float = Field(alias="atsScore")
    extracted_skills: list[str] = Field(default_factory=list, alias="extractedSkills")
    missing_skills: list[str] = Field(default_factory=list, alias="missingSkills")
    strengths: list[str] = Field(default_factory=list)
    gaps: list[str] = Field(default_factory=list)
    summary: str = ""
    recommendations: list[str] = Field(default_factory=list)
    model: str = "smarthire-rules-v1"


class ParseResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    filename: str
    content_type: str = Field(default="application/octet-stream", alias="contentType")
    char_count: int = Field(alias="charCount")
    email: str | None = None
    phone: str | None = None
    years_detected: int = Field(alias="yearsDetected")
    sections: list[str] = Field(default_factory=list)
    extracted_skills: list[str] = Field(default_factory=list, alias="extractedSkills")
    text: str = ""


class HealthResponse(BaseModel):
    status: str = "UP"
    service: str = "smarthire-resume-ai"
    version: str = "1.0.0"
