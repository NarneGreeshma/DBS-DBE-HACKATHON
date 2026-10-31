-- =====================================================================
-- SmartHire :: V2__seed_data.sql
-- Reference data + a demo tenant so every screen has realistic content.
-- All demo accounts share the password: Password123!
-- =====================================================================

-- ---------------------------------------------------------------------
-- roles
-- ---------------------------------------------------------------------
INSERT INTO roles (id, code, name, description) VALUES
  (1, 'ADMIN',      'Administrator',      'Full platform control: users, jobs, reports'),
  (2, 'RECRUITER',  'Recruiter',          'Owns job postings, pipelines and interviews'),
  (3, 'CANDIDATE',  'Candidate',          'Applies to jobs and tracks applications');

SELECT setval('roles_id_seq', (SELECT MAX(id) FROM roles));

-- ---------------------------------------------------------------------
-- skills
-- ---------------------------------------------------------------------
INSERT INTO skills (id, name, category) VALUES
  (1,  'java',            'LANG'),
  (2,  'python',          'LANG'),
  (3,  'javascript',      'LANG'),
  (4,  'typescript',      'LANG'),
  (5,  'spring boot',     'FRAMEWORK'),
  (6,  'hibernate',       'FRAMEWORK'),
  (7,  'react',           'FRONTEND'),
  (8,  'nextjs',          'FRONTEND'),
  (9,  'postgresql',      'DB'),
  (10, 'mysql',           'DB'),
  (11, 'redis',           'CACHE'),
  (12, 'docker',          'DEVOPS'),
  (13, 'kubernetes',      'DEVOPS'),
  (14, 'aws',             'CLOUD'),
  (15, 'gcp',             'CLOUD'),
  (16, 'kafka',           'MESSAGING'),
  (17, 'elasticsearch',   'SEARCH'),
  (18, 'graphql',         'API'),
  (19, 'rest apis',       'API'),
  (20, 'machine learning', 'AI'),
  (21, 'nlp',             'AI'),
  (22, 'pytest',          'TESTING'),
  (23, 'junit',           'TESTING'),
  (24, 'git',             'DEVOPS'),
  (25, 'communication',   'SOFT'),
  (26, 'leadership',      'SOFT');

SELECT setval('skills_id_seq', (SELECT MAX(id) FROM skills));

-- ---------------------------------------------------------------------
-- demo users (password: Password123!)
--   $2a$10$TkjrbUQJkgjedUYhteGbvObTpy6GcgwNSWlfaLNQm2Yg8mOvTGICm
-- ---------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name, phone, role_id, status, last_login_at) VALUES
  (1, 'admin@smarthire.io',
      '$2a$10$TkjrbUQJkgjedUYhteGbvObTpy6GcgwNSWlfaLNQm2Yg8mOvTGICm',
      'Ava Patel', '+1-415-555-0100', 1, 'ACTIVE', now()),
  (2, 'recruiter@smarthire.io',
      '$2a$10$TkjrbUQJkgjedUYhteGbvObTpy6GcgwNSWlfaLNQm2Yg8mOvTGICm',
      'Marcus Lee', '+1-415-555-0142', 2, 'ACTIVE', now()),
  (3, 'candidate@smarthire.io',
      '$2a$10$TkjrbUQJkgjedUYhteGbvObTpy6GcgwNSWlfaLNQm2Yg8mOvTGICm',
      'Alex Rivera', '+1-415-555-0177', 3, 'ACTIVE', now()),
  (4, 'candidate2@smarthire.io',
      '$2a$10$TkjrbUQJkgjedUYhteGbvObTpy6GcgwNSWlfaLNQm2Yg8mOvTGICm',
      'Priya Sharma', '+1-415-555-0193', 3, 'ACTIVE', now());

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- ---------------------------------------------------------------------
-- candidate profiles
-- ---------------------------------------------------------------------
INSERT INTO candidate_profiles (id, user_id, headline, summary, experience_years, location,
                                linkedin_url, portfolio_url, resume_text, created_at, updated_at) VALUES
  (1, 3, 'Senior Java / Spring Boot Engineer',
       'Backend engineer with 7 years building event-driven services in Java, Spring Boot and PostgreSQL.',
       7, 'Bengaluru, IN', 'https://linkedin.com/in/alexrivera', 'https://alexrivera.dev',
       'Alex Rivera Senior Java Engineer Experience 7 years building microservices with Java 21 Spring Boot Hibernate and PostgreSQL. Skills: Java, Spring Boot, Hibernate, PostgreSQL, Docker, Kafka, REST APIs, JUnit, Git, AWS. Education B.Tech Computer Science.',
       now(), now()),
  (2, 4, 'Frontend Engineer - React & Design Systems',
       'Product-minded frontend engineer, 5 years of React/TypeScript, strong on accessibility and performance.',
       5, 'Lisbon, PT', 'https://linkedin.com/in/priyasharma', 'https://priyasharma.dev',
       'Priya Sharma Frontend Engineer Experience 5 years shipping React and TypeScript applications. Skills: React, TypeScript, JavaScript, Next.js, GraphQL, REST APIs, Jest, Git, Figma, Tailwind CSS, communication. Education M.Sc Human-Computer Interaction.',
       now(), now());

SELECT setval('candidate_profiles_id_seq', (SELECT MAX(id) FROM candidate_profiles));

-- ---------------------------------------------------------------------
-- jobs
-- ---------------------------------------------------------------------
INSERT INTO jobs (id, title, slug, description, requirements, responsibilities,
                  location, workplace_type, employment_type, experience_level,
                  min_years, max_years, min_salary, max_salary, currency, openings,
                  status, created_by, published_at, created_at, updated_at) VALUES
  (1, 'Senior Backend Engineer', 'senior-backend-engineer',
       'Own the core hiring platform services. You will design REST APIs, model the recruitment domain in PostgreSQL and keep p99 latency low under heavy load.',
       '5+ years Java and Spring Boot. Deep PostgreSQL knowledge. Production experience with Docker and AWS. Strong testing culture with JUnit.',
       'Design and ship REST APIs. Model complex recruitment data. Mentor mid-level engineers. Drive observability and performance work.',
       'Bengaluru, IN', 'HYBRID', 'FULL_TIME', 'SENIOR', 5, 10, 120000, 180000, 'USD', 2,
       'OPEN', 2, now() - interval '12 days', now() - interval '14 days', now()),

  (2, 'Frontend Engineer (React)', 'frontend-engineer-react',
       'Build the SmartHire recruiter experience: a dark-themed, fast, accessible React application used by thousands of hiring teams.',
       '3+ years React. TypeScript fluency. Eye for craft: spacing, motion, states. Familiarity with Vite and Tailwind CSS.',
       'Ship product features end to end. Own component library. Partner with design on interaction quality. Keep bundle size honest.',
       'Remote', 'REMOTE', 'FULL_TIME', 'MID', 3, 6, 90000, 140000, 'USD', 1,
       'OPEN', 2, now() - interval '9 days', now() - interval '11 days', now()),

  (3, 'DevOps Engineer', 'devops-engineer',
       'Industrialise our delivery pipeline: Kubernetes, immutable infrastructure and zero-drama deploys.',
       '4+ years on Kubernetes and Docker. IaC with Terraform. AWS or GCP certification a plus. Solid PostgreSQL operational skills.',
       'Own CI/CD. Automate everything. Keep clusters boring. Define SLOs and alerting.',
       'Hyderabad, IN', 'ONSITE', 'FULL_TIME', 'SENIOR', 4, 9, 110000, 160000, 'USD', 1,
       'OPEN', 2, now() - interval '6 days', now() - interval '8 days', now()),

  (4, 'Machine Learning Engineer', 'machine-learning-engineer',
       'Power resume intelligence: extraction, matching and scoring models that actually help recruiters rather than hype them.',
       '3+ years applied ML in Python. NLP experience (NER, embeddings, transformers). Comfortable deploying models behind an API (FastAPI).',
       'Own the resume-parsing pipeline. Evaluate matching quality continuously. Ship model updates behind flags.',
       'Remote', 'REMOTE', 'FULL_TIME', 'SENIOR', 3, 8, 130000, 190000, 'USD', 1,
       'OPEN', 1, now() - interval '4 days', now() - interval '6 days', now()),

  (5, 'Full Stack Developer', 'full-stack-developer',
       'A generalist role spanning Spring Boot services and the React client for teams that like to own a feature outright.',
       '3+ years across Java and JavaScript. Comfortable in both worlds. REST and relational modelling.',
       'Deliver vertical slices. Write the tests. Talk to users.',
       'Pune, IN', 'HYBRID', 'FULL_TIME', 'MID', 3, 7, 85000, 130000, 'USD', 2,
       'DRAFT', 2, NULL, now() - interval '2 days', now()),

  (6, 'QA Automation Engineer', 'qa-automation-engineer',
       'Build the safety net: end-to-end coverage that lets the team deploy on Fridays without fear.',
       '3+ years test automation. Playwright or Selenium. API testing with Postman/Newman. SQL comfortable.',
       'Own E2E suite. Automate regression. Shift-left with developers.',
       'Chennai, IN', 'HYBRID', 'FULL_TIME', 'MID', 3, 7, 70000, 105000, 'USD', 1,
       'CLOSED', 2, now() - interval '30 days', now() - interval '34 days', now());

SELECT setval('jobs_id_seq', (SELECT MAX(id) FROM jobs));

-- ---------------------------------------------------------------------
-- job <-> skill requirements
-- ---------------------------------------------------------------------
INSERT INTO job_skills (job_id, skill_id, weight, is_required) VALUES
  -- job 1: Senior Backend Engineer
  (1, 1, 3, true),   -- java
  (1, 5, 3, true),   -- spring boot
  (1, 9, 3, true),   -- postgresql
  (1, 6, 2, false),  -- hibernate
  (1, 12, 1, false), -- docker
  (1, 14, 1, false), -- aws
  (1, 23, 1, false), -- junit
  -- job 2: Frontend Engineer
  (2, 7, 3, true),   -- react
  (2, 3, 3, true),   -- javascript
  (2, 4, 2, false),  -- typescript
  (2, 19, 2, false), -- rest apis
  (2, 8, 1, false),  -- nextjs
  (2, 25, 1, false), -- communication
  -- job 3: DevOps Engineer
  (3, 13, 3, true),  -- kubernetes
  (3, 12, 3, true),  -- docker
  (3, 14, 3, true),  -- aws
  (3, 9, 2, false),  -- postgresql
  (3, 24, 1, false), -- git
  -- job 4: ML Engineer
  (4, 2, 3, true),   -- python
  (4, 20, 3, true),  -- machine learning
  (4, 21, 2, false), -- nlp
  (4, 22, 1, false), -- pytest
  (4, 14, 1, false), -- aws
  -- job 5: Full Stack
  (5, 1, 3, true),
  (5, 5, 2, false),
  (5, 7, 3, true),
  (5, 9, 2, false),
  (5, 19, 1, false),
  -- job 6: QA
  (6, 19, 3, true),
  (6, 24, 2, false),
  (6, 9, 1, false);

-- ---------------------------------------------------------------------
-- applications
-- ---------------------------------------------------------------------
INSERT INTO applications (id, job_id, candidate_id, cover_letter, source, stage,
                          overall_score, stage_updated_at, applied_at, created_at, updated_at) VALUES
  (1, 1, 3, 'I have spent the last four years on high-throughput Spring Boot services and would love to own your hiring core.', 'CAREER_PAGE', 'SHORTLISTED', 82.50,
   now() - interval '3 days', now() - interval '7 days', now() - interval '7 days', now()),
  (2, 3, 3, 'Platform engineer at heart - I run Kubernetes in production and enjoy a quiet cluster.', 'CAREER_PAGE', 'APPLIED', NULL,
   now() - interval '2 days', now() - interval '2 days', now() - interval '2 days', now()),
  (3, 2, 4, 'Design-systems work is my favourite kind of frontend work.', 'REFERRAL', 'INTERVIEW', 74.00,
   now() - interval '1 day', now() - interval '8 days', now() - interval '8 days', now()),
  (4, 1, 4, 'Shifting towards backend after five years of React - happy to prove it.', 'LINKEDIN', 'SCREENING', 61.00,
   now() - interval '4 days', now() - interval '6 days', now() - interval '6 days', now()),
  (5, 4, 3, 'NLP pipelines are exactly what I want to work on next.', 'CAREER_PAGE', 'APPLIED', NULL,
   now() - interval '1 day', now() - interval '1 day', now() - interval '1 day', now());

SELECT setval('applications_id_seq', (SELECT MAX(id) FROM applications));

-- ---------------------------------------------------------------------
-- application pipeline audit
-- ---------------------------------------------------------------------
INSERT INTO application_events (application_id, from_stage, to_stage, changed_by, note, created_at) VALUES
  (1, NULL,     'APPLIED',     3, 'Application submitted', now() - interval '7 days'),
  (1, 'APPLIED', 'SCREENING',  2, 'Resume parsed, match score 82.5', now() - interval '5 days'),
  (1, 'SCREENING', 'SHORTLISTED', 2, 'Strong Java + PostgreSQL overlap', now() - interval '3 days'),
  (2, NULL,     'APPLIED',     3, 'Application submitted', now() - interval '2 days'),
  (3, NULL,     'APPLIED',     4, 'Application submitted', now() - interval '8 days'),
  (3, 'APPLIED', 'SCREENING',  2, 'Portfolio reviewed', now() - interval '5 days'),
  (3, 'SCREENING', 'SHORTLISTED', 2, 'Excellent React craft', now() - interval '3 days'),
  (3, 'SHORTLISTED', 'INTERVIEW', 2, 'Round 1 scheduled', now() - interval '1 day'),
  (4, NULL,     'APPLIED',     4, 'Application submitted', now() - interval '6 days'),
  (4, 'APPLIED', 'SCREENING',  2, 'Backend exposure is thin', now() - interval '4 days'),
  (5, NULL,     'APPLIED',     3, 'Application submitted', now() - interval '1 day');

SELECT setval('application_events_id_seq', (SELECT MAX(id) FROM application_events));

-- ---------------------------------------------------------------------
-- one completed AI resume analysis (shape produced by the FastAPI service)
-- ---------------------------------------------------------------------
INSERT INTO resume_analyses (application_id, status, match_score, ats_score,
                             extracted_skills, missing_skills, strengths, gaps,
                             summary, recommendations, model, created_at) VALUES
  (1, 'COMPLETED', 82.50, 88.00,
   '["java","spring boot","hibernate","postgresql","docker","kafka","rest apis","junit","git","aws"]'::jsonb,
   '["kubernetes","redis"]'::jsonb,
   '["Seven years of hands-on Java and Spring Boot experience","Production PostgreSQL and Hibernate depth","Clear ownership of event-driven services with Kafka","Solid automated testing habit with JUnit"]'::jsonb,
   '["No Kubernetes exposure listed on the resume","Redis/caching experience not evidenced"]'::jsonb,
   'Alex is a strong match for Senior Backend Engineer: the core required stack (Java, Spring Boot, PostgreSQL) is covered with senior-level depth, and the resume reads clearly for ATS parsers.',
   '["Add one line quantifying throughput or latency improvements","Mention Kubernetes in the platform section even if usage was incidental","Highlight mentoring or tech-lead duties to support the SENIOR level"]'::jsonb,
   'smarthire-rules-v1', now() - interval '7 days');

-- ---------------------------------------------------------------------
-- an upcoming interview
-- ---------------------------------------------------------------------
INSERT INTO interviews (application_id, scheduled_by, round, mode, scheduled_at, duration_minutes,
                        meeting_link, status, created_at, updated_at) VALUES
  (3, 2, 1, 'ONLINE', now() + interval '2 days', 60,
   'https://meet.smarthire.io/int-3', 'SCHEDULED', now() - interval '1 day', now() - interval '1 day');

SELECT setval('interviews_id_seq', (SELECT MAX(id) FROM interviews));

-- ---------------------------------------------------------------------
-- notifications
-- ---------------------------------------------------------------------
INSERT INTO notifications (user_id, title, body, type, link, is_read, created_at) VALUES
  (3, 'Application shortlisted', 'Your application for Senior Backend Engineer moved to SHORTLISTED.', 'STAGE', '/applications', false, now() - interval '3 days'),
  (4, 'Interview scheduled', 'Round 1 for Frontend Engineer (React) is confirmed.', 'INTERVIEW', '/interviews', false, now() - interval '1 day'),
  (2, '5 new applications', 'Five candidates applied in the last 7 days.', 'APPLICATION', '/applicants', false, now() - interval '1 day');

SELECT setval('notifications_id_seq', (SELECT MAX(id) FROM notifications));
