"""HTTP contract tests - these assert the exact JSON shape the Java backend expects."""

from __future__ import annotations

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_is_up() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["service"] == "smarthire-resume-ai"


def test_analyze_round_trips_camel_case() -> None:
    payload = {
        "resumeText": "Java and Spring Boot engineer with 6 years experience. "
                      "Skills: Java, Spring Boot, PostgreSQL. contact me at a@b.com",
        "jobText": "Java backend role",
        "jobTitle": "Backend Engineer",
        "requiredSkills": ["java", "spring boot"],
        "preferredSkills": ["redis"],
        "minYears": 4,
        "experienceYears": 6,
        "candidateName": "Alex",
    }
    response = client.post("/analyze", json=payload)
    assert response.status_code == 200

    body = response.json()
    # camelCase on the wire - consumed by AiDtos.AnalyzeResponse in Spring Boot
    assert set(body) >= {
        "matchScore", "atsScore", "extractedSkills", "missingSkills",
        "strengths", "gaps", "summary", "recommendations", "model",
    }
    assert isinstance(body["matchScore"], float)
    assert "redis" in body["missingSkills"]
    assert body["model"] == "smarthire-rules-v1"
    assert isinstance(body["extractedSkills"], list)


def test_analyze_accepts_snake_case_too() -> None:
    response = client.post("/analyze", json={"resume_text": "java", "job_title": "Engineer"})
    assert response.status_code == 200
    assert "java" in response.json()["extractedSkills"]


def test_camel_case_body_is_actually_applied() -> None:
    """Guards the Java<->Python wire contract.

    FastAPI/pydantic emit a false-positive ``UnsupportedFieldAttributeWarning`` about
    ``Field(alias=...)`` here; this test proves the alias really is applied, so the
    warning can be safely filtered out (see pytest.ini).
    """
    response = client.post("/analyze", json={
        "resumeText": "java and docker developer",
        "jobTitle": "Engineer",
        "requiredSkills": ["java"],
        "minYears": 3,
        "experienceYears": 5,
    })
    assert response.status_code == 200
    body = response.json()
    # if the alias were ignored, resumeText would be empty and no skills would be found
    assert "java" in body["extractedSkills"]
    assert body["missingSkills"] == []
    assert body["matchScore"] > 70


def test_analyze_with_empty_body_still_works() -> None:
    response = client.post("/analyze", json={})
    assert response.status_code == 200
    assert response.json()["matchScore"] == 0.0


def test_parse_resume_txt() -> None:
    content = ("Jane Doe\nEXPERIENCE\n- built things\n"
               "SKILLS\nJava, Docker\njane@example.com\n").encode()
    response = client.post(
        "/parse-resume",
        files={"file": ("resume.txt", content, "text/plain")},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["filename"] == "resume.txt"
    assert body["email"] == "jane@example.com"
    assert "java" in body["extractedSkills"]
    assert "experience" in body["sections"]
    assert body["charCount"] == len(body["text"])


def test_parse_resume_docx() -> None:
    import io
    import zipfile

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr(
            "word/document.xml",
            "<w:document><w:body><w:p><w:r><w:t>Java and Spring Boot</w:t></w:r></w:p></w:body></w:document>",
        )
    response = client.post(
        "/parse-resume",
        files={"file": ("resume.docx", buffer.getvalue(),
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
    )
    assert response.status_code == 200
    assert "Java and Spring Boot" in response.json()["text"]


def test_parse_resume_rejects_unknown_type() -> None:
    response = client.post(
        "/parse-resume",
        files={"file": ("malware.exe", b"\x00\x01\x02", "application/octet-stream")},
    )
    assert response.status_code == 415


def test_parse_resume_rejects_empty_file() -> None:
    response = client.post(
        "/parse-resume",
        files={"file": ("empty.txt", b"   \n  ", "text/plain")},
    )
    assert response.status_code == 415


def test_openapi_documents_the_service() -> None:
    schema = client.get("/openapi.json").json()
    assert "/analyze" in schema["paths"]
    assert "/parse-resume" in schema["paths"]
