package com.smarthire.enums;

/**
 * Shared vocabulary for the whole system. Kept together so the domain, security layer
 * and API contracts cannot drift apart.
 */
public final class Enums {
    private Enums() {
    }

    public enum RoleCode {ADMIN, RECRUITER, CANDIDATE}

    public enum UserStatus {ACTIVE, INVITED, SUSPENDED}

    public enum JobStatus {DRAFT, OPEN, ON_HOLD, CLOSED}

    public enum WorkplaceType {ONSITE, HYBRID, REMOTE}

    public enum EmploymentType {FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP}

    public enum ExperienceLevel {JUNIOR, MID, SENIOR, LEAD, EXEC}

    public enum ApplicationStage {
        APPLIED, SCREENING, SHORTLISTED, INTERVIEW, OFFER, HIRED, REJECTED, WITHDRAWN
    }

    public enum ApplicationSource {CAREER_PAGE, REFERRAL, LINKEDIN, IMPORT, AGENCY}

    public enum InterviewMode {ONLINE, PHONE, ONSITE}

    public enum InterviewStatus {SCHEDULED, COMPLETED, CANCELLED, NO_SHOW}

    public enum NotificationType {APPLICATION, STAGE, INTERVIEW, SYSTEM}

    public enum AnalysisStatus {PENDING, RUNNING, COMPLETED, FAILED}
}
