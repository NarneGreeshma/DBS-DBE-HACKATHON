-- =====================================================================
-- SmartHire :: V1__schema.sql
-- Core schema for the AI-assisted Smart Recruitment Management System
-- Engine: PostgreSQL 16
-- =====================================================================

-- ---------------------------------------------------------------------
-- roles
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uq_roles_code UNIQUE (code)
);

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    phone         VARCHAR(32),
    role_id       BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email     UNIQUE (email),
    CONSTRAINT fk_users_role       FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT ck_users_status     CHECK (status IN ('ACTIVE', 'INVITED', 'SUSPENDED')),
    CONSTRAINT ck_users_email_case CHECK (email = lower(email))
);

CREATE INDEX ix_users_role_id ON users (role_id);
CREATE INDEX ix_users_status  ON users (status);

-- ---------------------------------------------------------------------
-- candidate_profiles (1:1 extension of a CANDIDATE user)
-- ---------------------------------------------------------------------
CREATE TABLE candidate_profiles (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL,
    headline              VARCHAR(160),
    summary               TEXT,
    experience_years      INT         NOT NULL DEFAULT 0,
    location              VARCHAR(120),
    linkedin_url          VARCHAR(255),
    portfolio_url         VARCHAR(255),
    current_ctc           NUMERIC(12, 2),
    expected_ctc          NUMERIC(12, 2),
    resume_file_path      VARCHAR(255),
    resume_original_name  VARCHAR(255),
    resume_content_type   VARCHAR(100),
    resume_text           TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_candidate_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_candidate_profiles_user  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_candidate_profiles_years CHECK (experience_years >= 0 AND experience_years <= 60)
);

-- ---------------------------------------------------------------------
-- skills
-- ---------------------------------------------------------------------
CREATE TABLE skills (
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(80) NOT NULL,
    category VARCHAR(60) NOT NULL DEFAULT 'OTHER',
    CONSTRAINT uq_skills_name UNIQUE (name)
);

CREATE INDEX ix_skills_category ON skills (category);

-- ---------------------------------------------------------------------
-- jobs
-- ---------------------------------------------------------------------
CREATE TABLE jobs (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(160) NOT NULL,
    slug             VARCHAR(180) NOT NULL,
    description      TEXT         NOT NULL,
    requirements     TEXT,
    responsibilities TEXT,
    location         VARCHAR(120),
    workplace_type   VARCHAR(24)  NOT NULL DEFAULT 'ONSITE',
    employment_type  VARCHAR(24)  NOT NULL DEFAULT 'FULL_TIME',
    experience_level VARCHAR(24)  NOT NULL DEFAULT 'MID',
    min_years        INT,
    max_years        INT,
    min_salary       NUMERIC(14, 2),
    max_salary       NUMERIC(14, 2),
    currency         VARCHAR(8)   NOT NULL DEFAULT 'USD',
    openings         INT          NOT NULL DEFAULT 1,
    status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    created_by       BIGINT       NOT NULL,
    published_at     TIMESTAMPTZ,
    closes_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_jobs_slug     UNIQUE (slug),
    CONSTRAINT fk_jobs_creator   FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_jobs_status    CHECK (status IN ('DRAFT', 'OPEN', 'ON_HOLD', 'CLOSED')),
    CONSTRAINT ck_jobs_workplace CHECK (workplace_type IN ('ONSITE', 'HYBRID', 'REMOTE')),
    CONSTRAINT ck_jobs_employment CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP')),
    CONSTRAINT ck_jobs_level      CHECK (experience_level IN ('JUNIOR', 'MID', 'SENIOR', 'LEAD', 'EXEC')),
    CONSTRAINT ck_jobs_years      CHECK (min_years IS NULL OR max_years IS NULL OR min_years <= max_years),
    CONSTRAINT ck_jobs_salary     CHECK (min_salary IS NULL OR max_salary IS NULL OR min_salary <= max_salary),
    CONSTRAINT ck_jobs_openings   CHECK (openings > 0)
);

CREATE INDEX ix_jobs_status_published ON jobs (status, published_at DESC);
CREATE INDEX ix_jobs_created_by       ON jobs (created_by);
CREATE INDEX ix_jobs_location         ON jobs (location);
CREATE INDEX ix_jobs_level            ON jobs (experience_level);

-- ---------------------------------------------------------------------
-- job_skills (M:N)
-- ---------------------------------------------------------------------
CREATE TABLE job_skills (
    job_id     BIGINT  NOT NULL,
    skill_id   BIGINT  NOT NULL,
    weight     INTEGER NOT NULL DEFAULT 1,
    is_required BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (job_id, skill_id),
    CONSTRAINT fk_job_skills_job   FOREIGN KEY (job_id)   REFERENCES jobs (id)   ON DELETE CASCADE,
    CONSTRAINT fk_job_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT ck_job_skills_weight CHECK (weight BETWEEN 1 AND 3)
);

CREATE INDEX ix_job_skills_skill ON job_skills (skill_id);

-- ---------------------------------------------------------------------
-- applications
-- ---------------------------------------------------------------------
CREATE TABLE applications (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT       NOT NULL,
    candidate_id    BIGINT       NOT NULL,
    cover_letter    TEXT,
    source          VARCHAR(32)  NOT NULL DEFAULT 'CAREER_PAGE',
    stage           VARCHAR(24)  NOT NULL DEFAULT 'APPLIED',
    overall_score   NUMERIC(5, 2),
    stage_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_applications_job_candidate UNIQUE (job_id, candidate_id),
    CONSTRAINT fk_applications_job      FOREIGN KEY (job_id)       REFERENCES jobs (id)  ON DELETE CASCADE,
    CONSTRAINT fk_applications_candidate FOREIGN KEY (candidate_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_applications_stage CHECK (stage IN ('APPLIED', 'SCREENING', 'SHORTLISTED',
                                                      'INTERVIEW', 'OFFER', 'HIRED',
                                                      'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_applications_source CHECK (source IN ('CAREER_PAGE', 'REFERRAL', 'LINKEDIN', 'IMPORT', 'AGENCY')),
    CONSTRAINT ck_applications_score  CHECK (overall_score IS NULL OR (overall_score >= 0 AND overall_score <= 100))
);

CREATE INDEX ix_applications_job_stage   ON applications (job_id, stage);
CREATE INDEX ix_applications_candidate   ON applications (candidate_id, applied_at DESC);
CREATE INDEX ix_applications_stage_score ON applications (stage, overall_score DESC NULLS LAST);

-- ---------------------------------------------------------------------
-- application_events (append-only pipeline audit)
-- ---------------------------------------------------------------------
CREATE TABLE application_events (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT      NOT NULL,
    from_stage     VARCHAR(24),
    to_stage       VARCHAR(24) NOT NULL,
    changed_by     BIGINT,
    note           TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_app_events_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_app_events_actor       FOREIGN KEY (changed_by)     REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_app_events_application ON application_events (application_id, created_at);

-- ---------------------------------------------------------------------
-- resume_analyses (AI output, persisted by the Java backend)
-- ---------------------------------------------------------------------
CREATE TABLE resume_analyses (
    id               BIGSERIAL PRIMARY KEY,
    application_id   BIGINT       NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    match_score      NUMERIC(5, 2),
    ats_score        NUMERIC(5, 2),
    extracted_skills JSONB        NOT NULL DEFAULT '[]'::jsonb,
    missing_skills   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    strengths        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    gaps             JSONB        NOT NULL DEFAULT '[]'::jsonb,
    summary          TEXT,
    recommendations  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    model            VARCHAR(64),
    error            VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_resume_analyses_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT ck_resume_analyses_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_resume_analyses_match  CHECK (match_score  IS NULL OR (match_score  >= 0 AND match_score  <= 100)),
    CONSTRAINT ck_resume_analyses_ats    CHECK (ats_score    IS NULL OR (ats_score    >= 0 AND ats_score    <= 100))
);

CREATE INDEX ix_resume_analyses_application ON resume_analyses (application_id, created_at DESC);
CREATE INDEX ix_resume_analyses_skills      ON resume_analyses USING GIN (extracted_skills);

-- ---------------------------------------------------------------------
-- interviews
-- ---------------------------------------------------------------------
CREATE TABLE interviews (
    id               BIGSERIAL PRIMARY KEY,
    application_id   BIGINT       NOT NULL,
    scheduled_by     BIGINT,
    round            INT          NOT NULL DEFAULT 1,
    mode             VARCHAR(24)  NOT NULL DEFAULT 'ONLINE',
    scheduled_at     TIMESTAMPTZ  NOT NULL,
    duration_minutes INT          NOT NULL DEFAULT 45,
    meeting_link     VARCHAR(500),
    location         VARCHAR(255),
    status           VARCHAR(24)  NOT NULL DEFAULT 'SCHEDULED',
    feedback         TEXT,
    rating           SMALLINT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_interviews_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_interviews_scheduler   FOREIGN KEY (scheduled_by)   REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_interviews_mode   CHECK (mode IN ('ONLINE', 'PHONE', 'ONSITE')),
    CONSTRAINT ck_interviews_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_interviews_round  CHECK (round BETWEEN 1 AND 10),
    CONSTRAINT ck_interviews_duration CHECK (duration_minutes BETWEEN 5 AND 600),
    CONSTRAINT ck_interviews_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);

CREATE INDEX ix_interviews_scheduled_at ON interviews (scheduled_at);
CREATE INDEX ix_interviews_status       ON interviews (status);
CREATE INDEX ix_interviews_application  ON interviews (application_id);

-- ---------------------------------------------------------------------
-- notifications
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(160) NOT NULL,
    body       VARCHAR(500),
    type       VARCHAR(32)  NOT NULL DEFAULT 'SYSTEM',
    link       VARCHAR(255),
    is_read    BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_type CHECK (type IN ('APPLICATION', 'STAGE', 'INTERVIEW', 'SYSTEM'))
);

CREATE INDEX ix_notifications_user        ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE is_read = false;
