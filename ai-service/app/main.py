"""SmartHire resume-intelligence service (FastAPI).

Scope is deliberately narrow - rule 2 of the architecture: <b>Python handles resume
intelligence only</b>. It is stateless, never talks to PostgreSQL, and is only ever
reached by the Java Spring Boot backend.
"""

from __future__ import annotations

import io
import re
import zipfile
from typing import Annotated

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from .analysis import analyze
from .schemas import AnalyzeRequest, AnalyzeResponse, HealthResponse, ParseResponse
from .skills import detected_years, extract_skills, find_email, find_phone, section_headers

app = FastAPI(
    title="SmartHire Resume Intelligence",
    description=(
        "Stateless resume parsing and candidate/job matching. "
        "Called exclusively by the SmartHire Java backend."
    ),
    version="1.0.0",
)

MAX_UPLOAD_BYTES = 5 * 1024 * 1024
TEXT_EXTENSIONS = {".txt", ".md", ".csv", ".rtf"}


@app.get("/health", response_model=HealthResponse, tags=["ops"])
def health() -> HealthResponse:
    return HealthResponse()


@app.post("/analyze", response_model=AnalyzeResponse, tags=["analysis"])
def analyze_endpoint(request: AnalyzeRequest) -> AnalyzeResponse:
    """Score a resume against a job and explain the result."""
    return analyze(request)


@app.post("/parse-resume", response_model=ParseResponse, tags=["analysis"])
async def parse_resume(file: Annotated[UploadFile, File(...)]) -> ParseResponse:
    """Extract plain text (and quick facts) from an uploaded resume."""
    raw = await file.read()
    if len(raw) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Resume exceeds the 5 MB limit")

    filename = file.filename or "resume"
    extension = _extension(filename)
    try:
        text = _extract_text(filename, extension, raw)
    except UnicodeDecodeError:
        raise HTTPException(status_code=415, detail="File is not readable text") from None
    except HTTPException:
        raise

    if not text.strip():
        raise HTTPException(
            status_code=415,
            detail=f"Could not extract text from '{filename}'. "
                   "Upload .txt, .md, .pdf or .docx (the Java backend also extracts "
                   "text with Apache Tika before analysis).",
        )

    return ParseResponse(
        filename=filename,
        content_type=file.content_type or "application/octet-stream",
        char_count=len(text),
        email=find_email(text),
        phone=find_phone(text),
        years_detected=detected_years(text),
        sections=sorted(set(section_headers(text))),
        extracted_skills=extract_skills(text),
        text=text,
    )


def _extension(filename: str) -> str:
    index = filename.rfind(".")
    return filename[index:].lower() if index >= 0 else ""


def _extract_text(filename: str, extension: str, raw: bytes) -> str:
    if extension in TEXT_EXTENSIONS:
        return raw.decode("utf-8", errors="replace")
    if extension == ".pdf":
        return _extract_pdf(raw)
    if extension == ".docx":
        return _extract_docx(raw)
    raise HTTPException(
        status_code=415,
        detail=f"Unsupported file type '{extension or filename}'. "
               "Upload .txt, .md, .pdf or .docx.",
    )


def _extract_pdf(raw: bytes) -> str:
    try:
        from pypdf import PdfReader
    except ImportError as exc:  # pragma: no cover - dependency guard
        raise HTTPException(status_code=500, detail="pypdf is not installed") from exc

    try:
        reader = PdfReader(io.BytesIO(raw))
        return "\n".join(page.extract_text() or "" for page in reader.pages)
    except Exception as exc:  # noqa: BLE001 - malformed PDF
        raise HTTPException(status_code=415, detail=f"Unreadable PDF: {exc}") from exc


def _extract_docx(raw: bytes) -> str:
    """Minimal DOCX reader: document.xml is plain XML inside a zip."""
    try:
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            xml = archive.read("word/document.xml").decode("utf-8", errors="replace")
    except (zipfile.BadZipFile, KeyError) as exc:
        raise HTTPException(status_code=415, detail="Unreadable DOCX") from exc

    xml = xml.replace("</w:p>", "\n")
    text = re.sub(r"<[^>]+>", "", xml)
    return text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")


@app.exception_handler(HTTPException)
def http_exception_handler(_request, exc: HTTPException) -> JSONResponse:  # noqa: ANN001
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})
