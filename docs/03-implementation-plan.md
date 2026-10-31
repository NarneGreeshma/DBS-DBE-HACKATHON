# SmartHire — Implementation Plan

Phased delivery. **Every phase ends with a build + test run; errors are fixed before the next
phase starts.**

## Phase 0 — Foundation ✅
- [x] Inspect workspace (confirmed empty), verify toolchain (JDK 21, Maven 3.9, Python 3.13, Node 24)
- [x] Create repository layout: `backend/`, `ai-service/`, `frontend/`, `docs/`, `scripts/`
- [x] Write architecture, database design and this plan
- [x] Provision a local PostgreSQL 16 instance and `smarthire` database
- **Gate:** `psql -c "select version()"` succeeds; `mvn -q validate` succeeds

## Phase 1 — Spring Boot skeleton + database schema
- Spring Boot 3.3 (Java 21), Web, Validation, Data JPA, Security, Flyway, Postgres driver,
  Lombok, springdoc-openapi, JUnit/Mockito
- `application.yml` (dev/test/prod profiles), Flyway `V1__schema.sql`, `V2__seed_data.sql`
- Health endpoint `/api/health`
- **Gate:** `mvn test` green, Hibernate `validate` passes against PostgreSQL

## Phase 2 — Authentication & RBAC
- `roles`, `users`, repositories, DTOs, mappers
- `JwtService` (access + refresh), `JwtAuthenticationFilter`, `UserDetailsService`
- `SecurityConfig` with route-level rules + method security
- Endpoints: `POST /api/auth/register`, `/login`, `/refresh`, `GET /api/auth/me`
- Candidate self-registration; admin-created recruiter accounts
- **Gate:** integration tests for login success/failure, wrong-role 403, invalid token 401

## Phase 3 — Job management
- CRUD `/api/jobs`, publish/close transitions, slug uniqueness, skill tagging
- Public job board (anonymous read of `OPEN` jobs) vs recruiter management
- **Gate:** `mvn test` green incl. authorization tests

## Phase 4 — Applications + pipeline
- Apply (multipart resume upload), list by job / by candidate, stage transitions with audit
  events, withdrawal, notifications
- **Gate:** `mvn test` green incl. duplicate-application rule

## Phase 5 — AI resume intelligence (Python FastAPI)
- `POST /parse-resume` (PDF/DOCX/TXT → text), `POST /analyze` (text + job context → score),
  `GET /health`
- Deterministic, dependency-light NLP: skill lexicon matching, weighted skill coverage,
  experience/education extraction, ATS readability scoring, gap + recommendation generation
- Pytest unit tests
- **Gate:** `py -m pytest` green; `uvicorn` boots

## Phase 6 — Java ↔ AI integration
- `AiClient` on Spring `RestClient`, `ResumeAnalysisService` orchestration, analysis endpoints
- Graceful degradation when the AI service is unavailable
- **Gate:** `mvn test` green (AI client mocked in unit tests)

## Phase 7 — Interviews + analytics
- Interview scheduling/status/feedback endpoints
- Dashboard analytics: funnel counts, stage distribution, top skills, avg score,
  upcoming interviews, recent activity
- **Gate:** `mvn test` green

## Phase 8 — React frontend (dark SaaS theme)
- Vite + React 18 + Tailwind CSS, react-router, axios client with JWT interceptor
- Screens: Login, Register, Dashboard, Jobs (public board + manage), Job detail,
  Applicants pipeline (kanban-ish table), Candidate profile & resume upload,
  Interviews, Analytics, Admin users
- Reusable UI kit: `Card`, `Button`, `Badge`, `Input`, `Table`, `Modal`, `StatCard`, `Sidebar`
- **Gate:** `npm run build` (type/size-lint clean) + `npm run lint`

## Phase 9 — End-to-end verification
- Start PostgreSQL, FastAPI, Spring Boot, Vite dev server
- Smoke-test the real flows over HTTP: register → login → post job → apply → AI analysis →
  stage change → interview → analytics
- **Gate:** all smoke checks pass; document how to run in `README.md`

## How to run (summary — see README)
```bash
# 1. database
./scripts/db-start.sh          # starts local PostgreSQL + creates smarthire

# 2. AI service  (port 8000)
cd ai-service && py -m pip install -r requirements.txt && py -m uvicorn app.main:app --port 8000

# 3. backend     (port 8080)
cd backend && mvn spring-boot:run

# 4. frontend    (port 5173)
cd frontend && npm install && npm run dev
```
