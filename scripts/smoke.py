"""SmartHire :: HTTP smoke test.

Exercises the real REST contract (auth, RBAC, jobs, applications, AI degradation,
interviews, notifications, admin) against a running backend.

    py scripts/smoke.py [--base http://localhost:8080]

Exit code 0 == every check passed.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request

PASS: list[str] = []
FAIL: list[str] = []


def call(base: str, method: str, path: str, body=None, token=None, raw_body=None,
         content_type="application/json"):
    url = base + path
    data = None
    headers = {"Accept": "application/json"}
    if raw_body is not None:
        data = raw_body
        headers["Content-Type"] = content_type
    elif body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read().decode("utf-8", "replace")
            return resp.status, json.loads(payload) if payload else None
    except urllib.error.HTTPError as exc:
        payload = exc.read().decode("utf-8", "replace")
        try:
            parsed = json.loads(payload) if payload else None
        except json.JSONDecodeError:
            parsed = {"raw": payload}
        return exc.code, parsed
    except Exception as exc:  # noqa: BLE001 - surfaced as a failed check
        return -1, {"error": f"{type(exc).__name__}: {exc}"}


def check(name: str, ok: bool, detail: str = "") -> None:
    if ok:
        PASS.append(name)
        print(f"  PASS  {name}")
    else:
        FAIL.append(name)
        print(f"  FAIL  {name}  {detail}")


def status(name: str, base: str, method: str, path: str, expected: int, **kwargs) -> tuple[int, object]:
    code, payload = call(base, method, path, **kwargs)
    check(name, code == expected, f"expected {expected}, got {code}: {json.dumps(payload)[:220]}")
    return code, payload


def login(base: str, email: str, password: str = "Password123!") -> str | None:
    code, payload = status(f"login {email}", base, "POST", "/api/auth/login", 200,
                           body={"email": email, "password": password})
    if code != 200:
        return None
    return payload.get("accessToken")


APPLY_BOUNDARY = "----smarthireapply"


def multipart(fields: dict, boundary: str) -> tuple[bytes, str]:
    """Build a multipart/form-data body from simple string fields."""
    parts = []
    for name, value in fields.items():
        # each part is terminated by CRLF before the next boundary
        parts.append((f"--{boundary}\r\n"
                      f"Content-Disposition: form-data; name=\"{name}\"\r\n\r\n"
                      f"{value}\r\n").encode("utf-8"))
    parts.append(f"--{boundary}--\r\n".encode("utf-8"))
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080")
    args = parser.parse_args()
    base = args.base.rstrip("/")

    print(f"\n=== SmartHire smoke test -> {base} ===\n")

    # ---------------------------------------------------------------- health
    code, payload = status("health endpoint", base, "GET", "/api/health", 200)
    check("health reports UP", payload is not None and payload.get("status") == "UP")

    # ---------------------------------------------------------------- public board
    code, payload = status("anonymous job board", base, "GET", "/api/jobs", 200)
    anon_jobs = (payload or {}).get("content", [])
    check("anonymous board only returns OPEN jobs",
          all(j["status"] == "OPEN" for j in anon_jobs) and len(anon_jobs) > 0,
          f"got {[(j['id'], j['status']) for j in anon_jobs]}")

    status("job detail by id", base, "GET", "/api/jobs/1", 200)
    status("anonymous admin blocked", base, "GET", "/api/admin/users", 401)
    status("anonymous skills blocked", base, "GET", "/api/skills", 401)
    status("anonymous analytics blocked", base, "GET", "/api/analytics/summary", 401)
    status("anonymous apply blocked", base, "GET", "/api/applications/mine", 401)

    # ---------------------------------------------------------------- auth
    status("bad password rejected", base, "POST", "/api/auth/login", 401,
           body={"email": "recruiter@smarthire.io", "password": "nope"})
    status("garbage token rejected", base, "GET", "/api/auth/me", 401, token="not.a.jwt")

    admin = login(base, "admin@smarthire.io")
    recruiter = login(base, "recruiter@smarthire.io")
    candidate = login(base, "candidate@smarthire.io")
    check("all seeded accounts can log in", None not in (admin, recruiter, candidate))

    code, payload = status("GET /api/auth/me", base, "GET", "/api/auth/me", 200, token=recruiter)
    check("/api/auth/me returns role", payload and payload.get("role") == "RECRUITER",
          str(payload))

    # ---------------------------------------------------------------- RBAC matrix
    status("candidate blocked from analytics", base, "GET", "/api/analytics/summary", 403, token=candidate)
    status("candidate blocked from pipeline search", base, "GET", "/api/applications", 403, token=candidate)
    status("candidate blocked from admin users", base, "GET", "/api/admin/users", 403, token=candidate)
    status("candidate blocked from job create", base, "POST", "/api/jobs", 403, token=candidate,
           body={"title": "x", "description": "y"})
    status("recruiter blocked from admin users", base, "GET", "/api/admin/users", 403, token=recruiter)
    status("recruiter blocked from user create", base, "POST", "/api/admin/users", 403, token=recruiter,
           body={"fullName": "x", "email": "x@example.com", "password": "Password123!",
                 "role": "RECRUITER"})
    status("recruiter blocked from job delete", base, "DELETE", "/api/jobs/5", 403, token=recruiter)

    # ---------------------------------------------------------------- recruiter views
    code, payload = status("recruiter sees every job", base, "GET", "/api/jobs", 200, token=recruiter)
    staff_jobs = (payload or {}).get("content", [])
    check("staff job list includes non-OPEN jobs",
          len(staff_jobs) > len(anon_jobs), f"{len(staff_jobs)} vs {len(anon_jobs)}")

    status("skills taxonomy", base, "GET", "/api/skills", 200, token=recruiter)
    status("analytics summary", base, "GET", "/api/analytics/summary", 200, token=recruiter)
    status("analytics funnel", base, "GET", "/api/analytics/funnel", 200, token=recruiter)
    status("analytics skill demand", base, "GET", "/api/analytics/skills", 200, token=recruiter)
    status("analytics recent applications", base, "GET", "/api/analytics/recent-applications", 200,
           token=recruiter)
    status("pipeline search", base, "GET", "/api/applications?jobId=1", 200, token=recruiter)
    status("stage counts", base, "GET", "/api/applications/stage-counts", 200, token=recruiter)
    status("staff interview list", base, "GET", "/api/interviews", 200, token=recruiter)
    status("recruiter notifications", base, "GET", "/api/notifications", 200, token=recruiter)

    # ---------------------------------------------------------------- admin
    status("admin lists users", base, "GET", "/api/admin/users", 200, token=admin)
    status("admin lists roles", base, "GET", "/api/admin/users/roles", 200, token=admin)

    stamp = str(int(time.time() * 1000))[-9:]
    new_email = f"smoke.recruiter.{stamp}@smarthire.io"
    code, payload = status("admin provisions recruiter", base, "POST", "/api/admin/users", 201,
                           token=admin,
                           body={"fullName": "Smoke Recruiter", "email": new_email,
                                 "password": "Password123!", "role": "RECRUITER"})
    new_user_id = (payload or {}).get("id")
    if new_user_id:
        status("new recruiter can log in", base, "POST", "/api/auth/login", 200,
               body={"email": new_email, "password": "Password123!"})
        status("admin suspends account", base, "PATCH", f"/api/admin/users/{new_user_id}/status",
               200, token=admin, body={"status": "SUSPENDED"})
        status("suspended account cannot log in", base, "POST", "/api/auth/login", 403,
               body={"email": new_email, "password": "Password123!"})

    # ---------------------------------------------------------------- candidate flow
    cand_email = f"smoke.candidate.{stamp}@smarthire.io"
    code, payload = status("self-registration creates candidate", base, "POST", "/api/auth/register", 201,
                           body={"fullName": "Smoke Candidate", "email": cand_email,
                                 "password": "Password123!", "headline": "Test engineer"})
    fresh = (payload or {}).get("accessToken")
    if not fresh:
        fresh = login(base, cand_email)

    code, payload = status("candidate profile", base, "GET", "/api/candidates/me", 200, token=fresh)
    status("update candidate profile", base, "PUT", "/api/candidates/me", 200, token=fresh,
           body={"summary": "Smoke-tested profile", "experienceYears": 3, "location": "Remote"})
    status("update account", base, "PUT", "/api/users/me", 200, token=fresh,
           body={"fullName": "Smoke Candidate II"})

    # resume upload (TXT -> Tika -> extracted text)
    resume = b"Jane Doe\nSenior Backend Engineer\nSkills: Java, Spring Boot, PostgreSQL, Docker, AWS\n"
    boundary = "----smarthireboundary"
    parts = []
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
                 f"filename=\"resume.txt\"\r\nContent-Type: text/plain\r\n\r\n".encode())
    parts.append(resume)
    parts.append(f"\r\n--{boundary}--\r\n".encode())
    code, payload = status("resume upload + text extraction", base, "POST",
                           "/api/candidates/me/resume", 200, token=fresh,
                           raw_body=b"".join(parts),
                           content_type=f"multipart/form-data; boundary={boundary}")
    check("resume text stored", payload and payload.get("hasResume") is True, str(payload))

    # apply to an OPEN job (AI service may be down -> must still succeed)
    # /api/applications only consumes multipart/form-data
    def apply_job(job_id: int, cover_letter: str = "Smoke test application"):
        body, ctype = multipart({"jobId": job_id, "coverLetter": cover_letter}, APPLY_BOUNDARY)
        return status(f"apply to job {job_id}", base, "POST", "/api/applications", 409,
                      token=fresh, raw_body=body, content_type=ctype)

    body, ctype = multipart({"jobId": 2, "coverLetter": "Smoke test application"}, APPLY_BOUNDARY)
    code, payload = status("apply to open job (AI optional)", base, "POST", "/api/applications", 201,
                           token=fresh, raw_body=body, content_type=ctype)
    application_id = (payload or {}).get("id")

    code, _ = apply_job(2)
    check("duplicate application rejected", code == 409, f"got {code}")
    code, _ = apply_job(6)
    check("closed job rejects application", code == 409, f"got {code}")
    code, _ = apply_job(5)
    check("draft job rejects application", code == 409, f"got {code}")

    status("candidate sees own applications", base, "GET", "/api/applications/mine", 200, token=fresh)
    status("candidate cannot see someone else's application", base, "GET", "/api/applications/1", 403,
           token=fresh)
    status("candidate cannot move stage", base, "PATCH", f"/api/applications/{application_id}/stage",
           403, token=fresh, body={"stage": "HIRED"})

    if application_id:
        status("recruiter reads fresh application", base, "GET", f"/api/applications/{application_id}",
               200, token=recruiter)
        status("AI analysis recorded", base, "GET", f"/api/applications/{application_id}/analysis",
               200, token=recruiter)
        status("pipeline timeline", base, "GET", f"/api/applications/{application_id}/timeline",
               200, token=recruiter)
        status("recruiter moves stage", base, "PATCH", f"/api/applications/{application_id}/stage",
               200, token=recruiter, body={"stage": "SCREENING", "note": "smoke"})
        status("illegal stage transition rejected", base, "PATCH",
               f"/api/applications/{application_id}/stage", 400,
               token=recruiter, body={"stage": "HIRED"})
        status("candidate receives stage notification", base, "GET", "/api/notifications", 200,
               token=fresh)
        status("re-run AI screening", base, "POST", f"/api/applications/{application_id}/analyze",
               200, token=recruiter)

        # interview lifecycle
        code, interview = status("schedule interview", base, "POST", "/api/interviews", 201,
                                 token=recruiter,
                                 body={"applicationId": application_id, "round": 1, "mode": "ONLINE",
                                       "scheduledAt": "2030-01-15T10:00:00Z", "durationMinutes": 45})
        if interview and interview.get("id"):
            status("complete interview", base, "PATCH", f"/api/interviews/{interview['id']}", 200,
                   token=recruiter, body={"status": "COMPLETED", "feedback": "solid", "rating": 4})
            status("candidate sees own interview", base, "GET", "/api/interviews", 200, token=fresh)

        code, after = status("candidate can withdraw own application", base, "POST",
                             f"/api/applications/{application_id}/withdraw", 200, token=fresh)
        check("withdrawal moves stage to WITHDRAWN",
              after is not None and after.get("stage") == "WITHDRAWN", str(after))
        # a withdrawn application is a conflict, not an illegal-transition bad request
        status("withdrawn application cannot be moved again", base, "PATCH",
               f"/api/applications/{application_id}/stage", 409,
               token=recruiter, body={"stage": "OFFER"})

    # ---------------------------------------------------------------- job management
    job_body = {
        "title": "Smoke Test Engineer",
        "description": "Created by the automated smoke suite.",
        "location": "Remote",
        "workplaceType": "REMOTE",
        "employmentType": "FULL_TIME",
        "experienceLevel": "MID",
        "minYears": 2,
        "maxYears": 5,
        "openings": 1,
        "skills": [{"name": "java", "weight": 3, "required": True},
                   {"name": "pytest", "weight": 1, "required": False}],
    }
    code, new_job = status("recruiter creates job", base, "POST", "/api/jobs", 201,
                           token=recruiter, body=job_body)
    if new_job and new_job.get("id"):
        jid = new_job["id"]
        check("job slug generated", bool(new_job.get("slug")), str(new_job.get("slug")))
        status("publish requires skills", base, "PATCH", f"/api/jobs/{jid}/status", 200,
               token=recruiter, body={"status": "OPEN"})
        status("published job visible anonymously", base, "GET", f"/api/jobs/{jid}", 200)
        status("recruiter closes job", base, "PATCH", f"/api/jobs/{jid}/status", 200,
               token=recruiter, body={"status": "CLOSED"})
        body, ctype = multipart({"jobId": jid}, APPLY_BOUNDARY)
        code, _ = status(f"apply to closed job {jid}", base, "POST", "/api/applications", 409,
                         token=fresh, raw_body=body, content_type=ctype)
        check("closed job rejects application (post-close)", code == 409, f"got {code}")
        status("admin deletes job", base, "DELETE", f"/api/jobs/{jid}", 204, token=admin)

    status("openapi docs", base, "GET", "/v3/api-docs", 200)

    # ---------------------------------------------------------------- report
    print(f"\n=== {len(PASS)} passed, {len(FAIL)} failed ===")
    if FAIL:
        print("Failed checks:")
        for name in FAIL:
            print(f"  - {name}")
    print()
    return 1 if FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
