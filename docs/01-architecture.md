# SmartHire — System Architecture

SmartHire is an AI-assisted Smart Recruitment Management System. It is a **three-service
architecture** with a strict separation of responsibilities:

| Service | Technology | Port | Responsibility |
|---|---|---|---|
| `frontend/` | React + Vite + Tailwind CSS | 5173 | Presentation layer, dark-themed SaaS UI |
| `backend/` | **Java Spring Boot** (primary backend) | 8080 | All core business logic, REST API, auth, persistence |
| `ai-service/` | Python + FastAPI | 8000 | **Resume intelligence only** (parsing, scoring, gap analysis) |
| PostgreSQL 16 | RDBMS | 5432 | Single source of truth for all core application data |

## Hard constraints honoured by this design

1. **Java Spring Boot is the primary backend** — every domain operation (jobs, candidates,
   applications, interviews, analytics, users, security) is implemented in Spring Boot.
2. **Python handles resume intelligence only** — the FastAPI service is *stateless* and owns
   *no* application data. It never connects to PostgreSQL. It receives text/files and returns
   a JSON analysis. All results are persisted by Java.
3. **PostgreSQL stores all core application data** — the only relational store in the system.
   Schema is versioned with Flyway migrations.
4. **React talks to Java over REST** — React never calls the Python service directly; it calls
   `http://localhost:8080/api/...`. The Java backend proxies AI work, so JWT/RBAC always applies.
5. **Real authentication + role-based access** — Spring Security + stateless JWT (HS256),
   password hashing with BCrypt, method-level authorization with `@PreAuthorize`.
6. **No Node.js/Express backend** — Node is used only to *build* the static React assets
   (Vite dev server / `vite build`); it never serves API traffic.
7. **Not a static prototype** — real persistence, real migrations, real validation, real tests.

```
┌──────────────────────────────┐
│  React SPA (Vite + Tailwind) │
│  dark theme, JWT in memory   │
└──────────────┬───────────────┘
               │  HTTPS/JSON  REST  (/api/**)
               ▼
┌──────────────────────────────────────────────────────────────┐
│                  SPRING BOOT  (primary backend)             │
│  ┌────────────┐ ┌──────────────┐ ┌────────────────────────┐  │
│  │ Spring Sec │ │ Controllers  │ │ Services / Domain      │  │
│  │ JWT filter │ │ /api/v1/*    │ │ transactions, rules    │  │
│  └────────────┘ └──────────────┘ └───────────┬────────────┘  │
│                                              │               │
│                        Spring Data JPA + Hibernate           │
│                        Flyway migrations                     │
│                                  ┌───────────▼────────────┐  │
│                                  │  AiClient (RestClient) │  │
│                                  └───────────┬────────────┘  │
└──────────────────────────────────────────────┼───────────────┘
                          │ POST /analyze, /parse      │ JDBC
                          ▼                            ▼
               ┌────────────────────┐       ┌──────────────────┐
               │  FastAPI (Python)  │       │   PostgreSQL 16  │
               │  resume only,      │       │  all core data   │
               │  stateless, no DB  │       └──────────────────┘
               └────────────────────┘
```

## Request flows

### 1. Authentication
```
POST /api/auth/login {email,password}
  → AuthService verifies BCrypt hash
  → JwtService signs access token (15 min) + refresh token (7 days)
  → React stores access token in memory, refresh token in httpOnly-free localStorage
  → every subsequent request: Authorization: Bearer <token>
  → JwtAuthFilter validates signature/expiry → SecurityContext → @PreAuthorize checks role
```

### 2. Job application with AI screening
```
1. Candidate uploads resume  → POST /api/applications (multipart)
2. Java saves file to ./data/uploads, creates Application row (stage=APPLIED)
3. Java extracts text (Apache Tika) and calls FastAPI  POST /analyze
      { resume_text, job_description, required_skills[] }
4. FastAPI returns { match_score, ats_score, extracted_skills[], missing_skills[],
                     strengths[], gaps[], summary, recommendations[] }
5. Java persists the result in resume_analyses + updates applications.overall_score
6. React polls/reads GET /api/applications/{id}/analysis
```
If the AI service is down, the application still succeeds (graceful degradation, analysis
recorded as `PENDING`), because **Python is an enhancement, never a hard dependency**.

### 3. Pipeline movement (RBAC)
```
PATCH /api/applications/{id}/stage { stage, note }
  → @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
  → optimistic guard + application_events audit row
  → notification row created for the candidate
```

## Cross-cutting concerns

| Concern | Implementation |
|---|---|
| AuthN / AuthZ | Spring Security 6, stateless session, JWT HS256, `@PreAuthorize` |
| Passwords | BCrypt (strength 10) |
| Validation | Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Size`) |
| Errors | `@RestControllerAdvice` → uniform `{timestamp,status,error,message,fieldErrors}` |
| API docs | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |
| Pagination | `Pageable` + stable response DTO `{content,page,totalPages,totalElements}` |
| CORS | Whitelisted origins from `app.cors.allowed-origins` |
| Auditing | `@CreatedDate/@LastModifiedDate` via Hibernate envers-lite auditing |
| Config | `application.yml` + env overrides (`DB_URL`, `JWT_SECRET`, `AI_BASE_URL`) |
| Tests | JUnit 5 + Mockito + `@SpringBootTest` against real PostgreSQL |

## Repository layout

```
.
├── README.md
├── docs/
│   ├── 01-architecture.md
│   ├── 02-database-design.md
│   └── 03-implementation-plan.md
├── backend/                 # Spring Boot — the primary backend
│   ├── pom.xml
│   └── src/main/java/com/smarthire/...
│   └── src/main/resources/application.yml, db/migration/V*.sql
├── ai-service/              # FastAPI — resume intelligence only
│   ├── requirements.txt
│   ├── app/main.py
│   └── tests/test_analysis.py
├── frontend/                # React + Vite + Tailwind
│   ├── package.json, vite.config.js, tailwind.config.js
│   └── src/...
├── scripts/                 # dev helper scripts (db, run, seed)
└── data/uploads/            # resume files (git-ignored)
```
