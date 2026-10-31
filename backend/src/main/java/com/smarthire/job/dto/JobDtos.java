package com.smarthire.job.dto;

import com.smarthire.enums.Enums.EmploymentType;
import com.smarthire.enums.Enums.ExperienceLevel;
import com.smarthire.enums.Enums.JobStatus;
import com.smarthire.enums.Enums.WorkplaceType;
import com.smarthire.job.Job;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Request/response contract of the jobs module. */
public final class JobDtos {

    private JobDtos() {
    }

    public record JobSkillRequest(Long skillId, String name, Integer weight, Boolean required) {
    }

    public record JobRequest(
            String title,
            String description,
            String requirements,
            String responsibilities,
            String location,
            WorkplaceType workplaceType,
            EmploymentType employmentType,
            ExperienceLevel experienceLevel,
            Integer minYears,
            Integer maxYears,
            BigDecimal minSalary,
            BigDecimal maxSalary,
            String currency,
            Integer openings,
            Instant closesAt,
            List<JobSkillRequest> skills) {

        public List<JobSkillRequest> safeSkills() {
            return skills == null ? List.of() : skills;
        }
    }

    public record SkillRef(Long id, String name, String category, Integer weight, Boolean required) {
    }

    public record JobResponse(
            Long id,
            String title,
            String slug,
            String description,
            String requirements,
            String responsibilities,
            String location,
            WorkplaceType workplaceType,
            EmploymentType employmentType,
            ExperienceLevel experienceLevel,
            Integer minYears,
            Integer max_years,
            BigDecimal minSalary,
            BigDecimal maxSalary,
            String currency,
            Integer openings,
            JobStatus status,
            Long createdById,
            String createdByName,
            Instant publishedAt,
            Instant closesAt,
            Instant createdAt,
            Instant updatedAt,
            List<SkillRef> skills,
            Long applicationCount) {

        public static JobResponse of(Job job, String createdByName, Long applicationCount) {
            List<SkillRef> skills = new ArrayList<>();
            if (job.getJobSkills() != null) {
                job.getJobSkills().forEach(js -> skills.add(new SkillRef(
                        js.getSkill().getId(),
                        js.getSkill().getName(),
                        js.getSkill().getCategory(),
                        js.getWeight(),
                        js.getRequired())));
            }
            return new JobResponse(
                    job.getId(),
                    job.getTitle(),
                    job.getSlug(),
                    job.getDescription(),
                    job.getRequirements(),
                    job.getResponsibilities(),
                    job.getLocation(),
                    job.getWorkplaceType(),
                    job.getEmploymentType(),
                    job.getExperienceLevel(),
                    job.getMinYears(),
                    job.getMaxYears(),
                    job.getMinSalary(),
                    job.getMaxSalary(),
                    job.getCurrency(),
                    job.getOpenings(),
                    job.getStatus(),
                    job.getCreatedBy() == null ? null : job.getCreatedBy().getId(),
                    createdByName,
                    job.getPublishedAt(),
                    job.getClosesAt(),
                    job.getCreatedAt(),
                    job.getUpdatedAt(),
                    skills,
                    applicationCount);
        }
    }
}
