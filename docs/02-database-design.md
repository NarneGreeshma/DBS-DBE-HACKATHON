# SmartHire — Database Design (PostgreSQL 16)

Single database **`smarthire`**, owned entirely by the Spring Boot backend.
Schema is created and evolved by **Flyway** migrations in
`backend/src/main/resources/db/migration/`. Hibernate runs with `ddl-auto: validate`
so the ORM and the SQL schema can never silently drift.

**Naming convention:** `snake_case` tables/columns, `id BIGSERIAL` surrogate PKs,
`created_at` / `updated_at` `TIMESTAMPTZ`, FKs named `fk_<table>_<ref>`, unique
constraints named `uq_<table>_<cols>`, check constraints named `ck_<table>_<rule>`.

## Entity-Relationship overview

```
roles ─┬─< users >─ candidate_profiles
       │              │
       │              ├─< applications >─ jobs >─ job_skills >─ skills
       │              │        │
       │              │        ├─< application_events
       │              │        ├─< resume_analyses
       │              │        └─< interviews
       │              └─< notifications
       └ (users.role_id)
```

## Tables

### 1. `roles`
| column | type | notes |
|---|---|---|
| id | BIGSERIAL PK | |
| code | VARCHAR(32) UNIQUE | `ADMIN`, `RECRUITER`, `CANDIDATE` |
| name | VARCHAR(64) | display name |
| description | VARCHAR(255) | |

### 2. `users`
| column | type | notes |
|---|---|---|
| id | BIGSERIAL PK | |
| email | VARCHAR(255) UNIQUE NOT NULL | login identity, lowercased |
| password_hash | VARCHAR(100) NOT NULL | BCrypt |
| full_name | VARCHAR(120) NOT NULL | |
| phone | VARCHAR(32) | |
| role_id | BIGINT FK → roles | NOT NULL, drives RBAC |
| status | VARCHAR(16) | `ACTIVE`,`INVITED`,`SUSPENDED` (check) |
| last_login_at | TIMESTAMPTZ | |
| created_at / updated_at | TIMESTAMPTZ | |

### 3. `candidate_profiles` (1:1 with a CANDIDATE user)
| column | type | notes |
|---|---|---|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK UNIQUE → users | cascade delete |
| headline | VARCHAR(160) | e.g. "Senior Java Engineer" |
| summary | TEXT | |
| experience_years | INT | check ≥ 0 |
| location | VARCHAR(120) | |
| linkedin_url / portfolio_url | VARCHAR(255) | |
| current_ctc / expected_ctc | NUMERIC(12,2) | optional |
| resume_file_path | VARCHAR(255) | on-disk path under `data/uploads` |
| resume_original_name | VARCHAR(255) | |
| resume_content_type | VARCHAR(100) | |
| resume_text | TEXT | extracted text (input for AI) |
| created_at / updated_at | TIMESTAMPTZ | |

### 4. `skills`
| column | type | notes |
|---|---|---|
| id | BIGSERIAL PK | |
| name | VARCHAR(80) UNIQUE NOT NULL | normalized lowercase, e.g. `kubernetes` |
| category | VARCHAR(60) | `LANG`,`FRAMEWORK`,`CLOUD`,`DB`,`DEVOPS`,`SOFT`… |

### 5. `jobs`
| column | type | notes |
|---|---|---|
| id | BIGSERIAL PK | |
| title | VARCHAR(160) NOT NULL | |
| slug | VARCHAR(180) UNIQUE | auto from title |
| description | TEXT NOT NULL | markdown |
| requirements | TEXT | |
| responsibilities | TEXT | |
| location | VARCHAR(120) | |
| workplace_type | VARCHAR(24) | `ONSITE`,`HYBRID`,`REMOTE` |
| employment_type | VARCHAR(24) | `FULL_TIME`,`PART_TIME`,`CONTRACT`,`INTERNSHIP` |
| experience_level | VARCHAR(24) | `JUNIOR`,`MID`,`SENIOR`,`LEAD`,`EXEC` |
| min_years / max_years | INT | check min ≤ max when both set |
| min_salary / max_salary | NUMERIC(12,2) | |
| currency | VARCHAR(8) default `USD` | |
| openings | INT default 1 | check > 0 |
| status | VARCHAR(16) | `DRAFT`,`OPEN`,`ON_HOLD`,`CLOSED` |
| created_by | BIGINT FK → users | recruiter/admin |
| published_at | TIMESTAMPTZ | |
| closes_at | TIMESTAMPTZ | |
| created_at / updated_at | TIMESTAMPTZ | |

### 6. `job_skills` (M:N between `jobs` and `skills`)
| column | type | notes |
|---|---|---|
| job_id | BIGINT FK → jobs | PK part, cascade delete |
| skill_id | BIGINT FK → skills | PK part |
| weight | SMALLINT default 1 | 1 = nice-to-have, 2 = important, 3 = mandatory |
| is_required | BOOLEAN default false | drives "missing skill" gap analysis |

### 7. `applications`
| column | type | notes |
|---|---|---|
| id | BIGSERIAL PK | |
| job_id | BIGINT FK → jobs | NOT NULL |
| candidate_id | BIGINT FK → users | NOT NULL (the applicant) |
| cover_letter | TEXT | |
| source | VARCHAR(32) | `CAREER_PAGE`,`REFERRAL`,`LINKEDIN`,`IMPORT` |
| stage | VARCHAR(24) | `APPLIED`,`SCREENING`,`SHORTLISTED`,`INTERVIEW`,`OFFER`,`HIRED`,`REJECTED`,`WITHDRAWN` |
| overall_score | NUMERIC(5,2) | 0–100, written by AI screening |
| stage_updated_at | TIMESTAMPTZ | |
| applied_at | TIMESTAMPTZ | default now |
| updated_at | TIMESTAMPTZ | |
| **UQ** `uq_applications_job_candidate` | | one application per candidate per job |

### 8. `application_events` (append-only audit trail of the pipeline)
| column | type | notes |
|---|---|---|
| id | BIGSERIAL PK | |
| application_id | BIGINT FK → applications | cascade delete |
| from_stage / to_stage | VARCHAR(24) | |
| changed_by | BIGINT FK → users | |
| note | TEXT | |
| created_at | TIMESTAMPTZ | |

### 9. `resume_analyses` (AI output, persisted by Java)
| column | type | notes |
|---|---|---|
| id | BIGSERIAL PK | |
| application_id | BIGINT FK → applications | cascade delete |
| status | VARCHAR(16) | `PENDING`,`COMPLETED`,`FAILED` |
| match_score | NUMERIC(5,2) | overall 0–100 |
| ats_score | NUMERIC(5,2) | keyword/ATS readability 0–100 |
| extracted_skills | JSONB | `["java","spring boot",…]` |
| missing_skills | JSONB | skills required by job but not found |
| strengths | JSONB | array of strings |
| gaps | JSONB | array of strings |
| summary | TEXT | model-written summary |
| recommendations | JSONB | array of strings |
| model | VARCHAR(64) | e.g. `smarthire-rules-v1` |
| error | VARCHAR(500) | when status = FAILED |
| created_at | TIMESTAMPTZ | |

### 10. `interviews`
| column | type | notes |
|---|---|---|
| id | BIGSERIAL PK | |
| application_id | BIGINT FK → applications | cascade delete |
| scheduled_by | BIGINT FK → users | |
| round | INT default 1 | |
| mode | VARCHAR(24) | `ONLINE`,`PHONE`,`ONSITE` |
| scheduled_at | TIMESTAMPTZ NOT NULL | |
| duration_minutes | INT default 45 | check 5–600 |
| meeting_link | VARCHAR(500) | |
| location | VARCHAR(255) | |
| status | VARCHAR(24) | `SCHEDULED`,`COMPLETED`,`CANCELLED`,`NO_SHOW` |
| feedback | TEXT | interviewer notes |
| rating | SMALLINT | check 1–5 |
| created_at / updated_at | TIMESTAMPTZ | |

### 11. `notifications`
| column | type | notes |
|---|---|---|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users | cascade delete |
| title | VARCHAR(160) | |
| body | VARCHAR(500) | |
| type | VARCHAR(32) | `APPLICATION`,`STAGE`,`INTERVIEW`,`SYSTEM` |
| link | VARCHAR(255) | frontend route |
| read | BOOLEAN default false | |
| created_at | TIMESTAMPTZ | |

## Indexes
- `ix_users_email` (unique, implicit), `ix_users_role_id`
- `ix_jobs_status_published` on `(status, published_at DESC)`
- `ix_jobs_created_by`
- `ix_applications_job_stage` on `(job_id, stage)`
- `ix_applications_candidate` on `(candidate_id, applied_at DESC)`
- `ix_app_events_application` on `(application_id, created_at)`
- `ix_resume_analyses_application` on `(application_id, created_at DESC)`
- `ix_interviews_scheduled_at`, `ix_interviews_status`
- `ix_notifications_user_unread` on `(user_id) WHERE read = false` (partial index)
- GIN index `ix_resume_analyses_skills` on `extracted_skills` for JSONB lookups

## Seed data (V2__seed_data.sql)
- 3 roles
- Demo accounts (BCrypt-hashed, password `Password123!`):
  `admin@smarthire.io` (ADMIN), `recruiter@smarthire.io` (RECRUITER),
  `candidate@smarthire.io` (CANDIDATE), `candidate2@smarthire.io` (CANDIDATE)
- 18 curated skills across categories
- 6 realistic open jobs with mapped skills
- Demo applications + events so the pipeline/analytics screens are populated

## Migration versioning
| file | purpose |
|---|---|
| `V1__schema.sql` | all tables, constraints, indexes |
| `V2__seed_data.sql` | roles, skills, demo users, jobs, applications |
