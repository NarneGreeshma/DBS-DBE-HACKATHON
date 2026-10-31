# SmartHire — AI-assisted Smart Recruitment Management System

A full-stack recruitment platform: **Java Spring Boot is the primary backend**, **PostgreSQL**
stores all core application data, **React + Vite + Tailwind** is the client, and a small
**Python/FastAPI** service does *resume intelligence only*.

```
React (5173)  ──REST/JSON──►  Spring Boot (8080)  ──JDBC/Flyway──►  PostgreSQL 16 (5432)
                                      │
                                      └────POST /analyze────►  FastAPI (8000)
```

Hard rules this repo follows:

1. Java Spring Boot is the primary backend — every domain operation lives there.
2. Python handles resume intelligence only. It is stateless, has **no database**, and is
   never called directly by the browser.
3. PostgreSQL stores all core application data (schema is versioned with Flyway).
4. React talks to Java through REST APIs only.
5. Real authentication (JWT) with role-based access (`ADMIN`, `RECRUITER`, `CANDIDATE`).
6. Node.js is never a backend — it only builds/serves the static React bundle.

See [`docs/`](docs/) for the [architecture](docs/01-architecture.md),
[database design](docs/02-database-design.md) and
[implementation plan](docs/03-implementation-plan.md).

---

## Prerequisites

| Tool | Version used |
|---|---|
| JDK | 17 or 21 (`java.version` targets 17, runs on either) |
| Maven | 3.9+ |
| Python | 3.13 (`py` launcher on Windows) |
| Node.js | 24 / npm 11 |
| PostgreSQL | 16 (a project-local portable cluster is included in `.pg/`) |

## Running the system

Four terminals (or four background processes):

```bash
# 1) PostgreSQL 16 — project-local portable cluster, port 5432
./scripts/db-start.sh

# 2) Python resume-intelligence service — port 8000
cd ai-service
py -m pip install -r requirements.txt
py -m uvicorn app.main:app --host 127.0.0.1 --port 8000

# 3) Java Spring Boot backend — port 8080 (runs Flyway migrations on boot)
cd backend
mvn spring-boot:run

# 4) React frontend — port 5173 (proxies /api to :8080)
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**.

### Demo accounts (password `Password123!`)

| Role | Email |
|---|---|
| Administrator | `admin@smarthire.io` |
| Recruiter | `recruiter@smarthire.io` |
| Candidate | `candidate@smarthire.io` |
| Candidate | `candidate2@smarthire.io` |

Self-registration always creates a **CANDIDATE**; recruiters/admins are provisioned from
**Users** in the admin console.

### Configuration

Everything is overridable with environment variables (see `backend/src/main/resources/application.yml`):

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/smarthire` |
| `DB_USER` / `DB_PASSWORD` | `smarthire` / `smarthire` |
| `JWT_SECRET` | dev default (base64) |
| `AI_BASE_URL` | `http://localhost:8000` |
| `AI_ENABLED` | `true` |
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:4173` |
| `UPLOAD_DIR` | `./data/uploads` |

## Verification

```bash
# Java: 35 unit + integration tests against real PostgreSQL (db smarthire_test)
cd backend && mvn test

# Python: 26 tests (scoring engine + HTTP contract)
cd ai-service && py -m pytest

# React production build
cd frontend && npm run build

# End-to-end HTTP suite (79 checks: auth, RBAC, jobs, pipeline, interviews, AI)
py scripts/smoke.py
```

Database maintenance:

```bash
py scripts/db-reset.py     # drop + recreate smarthire / smarthire_test (dev only)
```

## Feature tour

| Area | What works |
|---|---|
| Auth | register, login, refresh, `/me`, suspended accounts, BCrypt + JWT |
| RBAC | route-level rules **and** `@PreAuthorize` on every mutating endpoint |
| Jobs | draft → publish → hold → close, skill tagging, public board, search/filter/pagination |
| Applications | multipart apply with resume, duplicate/eligibility guards, 8-stage pipeline with audit trail, withdraw |
| AI screening | Java extracts text (Tika) → FastAPI scores it → result persisted in `resume_analyses` |
| Interviews | schedule, round, mode, complete/cancel/no-show, feedback + 1–5 rating |
| Analytics | KPI summary, pipeline funnel, skill demand, application sources, recent activity |
| Notifications | in-app notifications for applications, stage changes and interviews |
| Admin | provision accounts, activate/suspend, search and filter users |
| Candidate | profile, resume upload with text extraction, own applications, own interviews |

**Graceful degradation:** if the Python service is down, applying still succeeds — the analysis
row is recorded as `FAILED` with a clear reason instead of breaking the request.
