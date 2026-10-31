package com.smarthire.job;

import com.smarthire.application.ApplicationRepository;
import com.smarthire.common.BusinessException;
import com.smarthire.common.ConflictException;
import com.smarthire.common.PageResponse;
import com.smarthire.common.ResourceNotFoundException;
import com.smarthire.enums.Enums.JobStatus;
import com.smarthire.job.dto.JobDtos.JobRequest;
import com.smarthire.job.dto.JobDtos.JobResponse;
import com.smarthire.job.dto.JobDtos.JobSkillRequest;
import com.smarthire.job.dto.JobDtos.SkillRef;
import com.smarthire.security.AuthenticatedUser;
import com.smarthire.skill.Skill;
import com.smarthire.skill.SkillRepository;
import com.smarthire.user.User;
import com.smarthire.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;

    public record JobSearch(
            String q,
            String location,
            JobStatus status,
            com.smarthire.enums.Enums.ExperienceLevel level,
            com.smarthire.enums.Enums.WorkplaceType workplace,
            List<Long> skillIds,
            Long createdBy,
            boolean openOnly) {
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> search(JobSearch search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Job> result = jobRepository.findAll(toSpecification(search), pageable);
        java.util.Map<Long, Long> counts = applicationCounts(result.getContent());
        return PageResponse.from(result, job -> JobResponse.of(
                job,
                job.getCreatedBy() == null ? null : job.getCreatedBy().getFullName(),
                counts.getOrDefault(job.getId(), 0L)));
    }

    /** One grouped query instead of one count per row. */
    private java.util.Map<Long, Long> applicationCounts(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return java.util.Map.of();
        }
        List<Long> ids = jobs.stream().map(Job::getId).toList();
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        for (Object[] row : applicationRepository.countGroupedByJob(ids)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public JobResponse get(Long id) {
        return toResponse(requireGraph(id));
    }

    @Transactional(readOnly = true)
    public JobResponse getBySlug(String slug) {
        Job job = jobRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Job", slug));
        return toResponse(job);
    }

    @Transactional
    public JobResponse create(JobRequest request, AuthenticatedUser actor) {
        validate(request);
        Job job = new Job();
        job.setTitle(request.title().trim());
        job.setSlug(uniqueSlug(request.title()));
        job.setDescription(request.description().trim());
        job.setRequirements(blankToNull(request.requirements()));
        job.setResponsibilities(blankToNull(request.responsibilities()));
        job.setLocation(blankToNull(request.location()));
        job.setWorkplaceType(orDefault(request.workplaceType(), com.smarthire.enums.Enums.WorkplaceType.ONSITE));
        job.setEmploymentType(orDefault(request.employmentType(), com.smarthire.enums.Enums.EmploymentType.FULL_TIME));
        job.setExperienceLevel(orDefault(request.experienceLevel(), com.smarthire.enums.Enums.ExperienceLevel.MID));
        job.setMinYears(request.minYears());
        job.setMaxYears(request.maxYears());
        job.setMinSalary(request.minSalary());
        job.setMaxSalary(request.maxSalary());
        job.setCurrency(request.currency() == null || request.currency().isBlank()
                ? "USD" : request.currency().trim().toUpperCase(Locale.ROOT));
        job.setOpenings(request.openings() == null || request.openings() < 1 ? 1 : request.openings());
        job.setStatus(JobStatus.DRAFT);
        job.setClosesAt(request.closesAt());
        job.setCreatedBy(loadUser(actor.id()));
        applySkills(job, request.safeSkills());

        Job saved = jobRepository.save(job);
        log.info("Recruiter {} created job {} ({})", actor.email(), saved.getId(), saved.getSlug());
        return toResponse(saved);
    }

    @Transactional
    public JobResponse update(Long id, JobRequest request, AuthenticatedUser actor) {
        validate(request);
        Job job = requireGraph(id);
        assertCanManage(job, actor);

        boolean titleChanged = !job.getTitle().equals(request.title().trim());
        job.setTitle(request.title().trim());
        if (titleChanged) {
            String slug = uniqueSlug(request.title());
            if (!slug.equals(job.getSlug())) {
                job.setSlug(slug);
            }
        }
        job.setDescription(request.description().trim());
        job.setRequirements(blankToNull(request.requirements()));
        job.setResponsibilities(blankToNull(request.responsibilities()));
        job.setLocation(blankToNull(request.location()));
        job.setWorkplaceType(orDefault(request.workplaceType(), job.getWorkplaceType()));
        job.setEmploymentType(orDefault(request.employmentType(), job.getEmploymentType()));
        job.setExperienceLevel(orDefault(request.experienceLevel(), job.getExperienceLevel()));
        job.setMinYears(request.minYears());
        job.setMaxYears(request.maxYears());
        job.setMinSalary(request.minSalary());
        job.setMaxSalary(request.maxSalary());
        if (request.currency() != null && !request.currency().isBlank()) {
            job.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        }
        if (request.openings() != null && request.openings() > 0) {
            job.setOpenings(request.openings());
        }
        job.setClosesAt(request.closesAt());

        job.getJobSkills().clear();
        applySkills(job, request.safeSkills());

        return toResponse(jobRepository.save(job));
    }

    @Transactional
    public JobResponse changeStatus(Long id, JobStatus target, AuthenticatedUser actor) {
        Job job = requireGraph(id);
        assertCanManage(job, actor);

        JobStatus current = job.getStatus();
        if (current == target) {
            return toResponse(job);
        }
        if (!isAllowedTransition(current, target)) {
            throw new BusinessException("Cannot move a job from %s to %s".formatted(current, target));
        }
        if (job.getJobSkills().isEmpty()) {
            throw new ConflictException("Attach at least one skill before publishing a job");
        }
        job.setStatus(target);
        if (target == JobStatus.OPEN && job.getPublishedAt() == null) {
            job.setPublishedAt(Instant.now());
        }
        if (target == JobStatus.OPEN && job.getClosesAt() != null && !job.getClosesAt().isAfter(Instant.now())) {
            throw new ConflictException("Closing date must be in the future to publish");
        }
        log.info("Job {} moved {} -> {} by {}", id, current, target, actor.email());
        return toResponse(jobRepository.save(job));
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser actor) {
        if (!actor.isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only administrators may delete job postings");
        }
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Job", id));
        jobRepository.delete(job);
        log.warn("Job {} deleted by {}", id, actor.email());
    }

    // ------------------------------------------------------------------ helpers

    private Specification<Job> toSpecification(JobSearch search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search.openOnly()) {
                predicates.add(cb.equal(root.get("status"), JobStatus.OPEN));
            } else if (search.status() != null) {
                predicates.add(cb.equal(root.get("status"), search.status()));
            }
            if (search.q() != null && !search.q().isBlank()) {
                String like = "%" + search.q().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (search.location() != null && !search.location().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("location")),
                        "%" + search.location().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (search.level() != null) {
                predicates.add(cb.equal(root.get("experienceLevel"), search.level()));
            }
            if (search.workplace() != null) {
                predicates.add(cb.equal(root.get("workplaceType"), search.workplace()));
            }
            if (search.createdBy() != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), search.createdBy()));
            }

            // when a closed-date is set and the job is OPEN, hide postings past their deadline
            predicates.add(cb.or(
                    cb.notEqual(root.get("status"), JobStatus.OPEN),
                    cb.isNull(root.get("closesAt")),
                    cb.greaterThan(root.<Instant>get("closesAt"), Instant.now())));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void applySkills(Job job, List<JobSkillRequest> skills) {
        Set<String> seen = new LinkedHashSet<>();
        for (JobSkillRequest request : skills) {
            Skill skill = resolveSkill(request);
            if (!seen.add(skill.getName())) {
                continue;
            }
            JobSkill link = new JobSkill();
            link.setJob(job);
            link.setSkill(skill);
            link.setWeight(clampWeight(request.weight()));
            link.setRequired(Boolean.TRUE.equals(request.required()));
            job.getJobSkills().add(link);
        }
    }

    private Skill resolveSkill(JobSkillRequest request) {
        if (request.skillId() != null) {
            return skillRepository.findById(request.skillId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Skill", request.skillId()));
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException("Each skill needs either skillId or name");
        }
        String name = request.name().trim().toLowerCase(Locale.ROOT);
        return skillRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Skill skill = new Skill();
                    skill.setName(name);
                    skill.setCategory("OTHER");
                    return skillRepository.save(skill);
                });
    }

    private void validate(JobRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BusinessException("Job title is required");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new BusinessException("Job description is required");
        }
        if (request.minYears() != null && request.maxYears() != null && request.minYears() > request.maxYears()) {
            throw new BusinessException("minYears cannot exceed maxYears");
        }
        if (request.minSalary() != null && request.maxSalary() != null
                && request.minSalary().compareTo(request.maxSalary()) > 0) {
            throw new BusinessException("minSalary cannot exceed maxSalary");
        }
    }

    private static boolean isAllowedTransition(JobStatus from, JobStatus to) {
        return switch (from) {
            case DRAFT -> to == JobStatus.OPEN || to == JobStatus.CLOSED;
            case OPEN -> to == JobStatus.ON_HOLD || to == JobStatus.CLOSED;
            case ON_HOLD -> to == JobStatus.OPEN || to == JobStatus.CLOSED;
            case CLOSED -> false;
        };
    }

    private void assertCanManage(Job job, AuthenticatedUser actor) {
        if (actor.isAdmin()) {
            return;
        }
        if (job.getCreatedBy() == null || !job.getCreatedBy().getId().equals(actor.id())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only manage job postings you created");
        }
    }

    private Job requireGraph(Long id) {
        return jobRepository.findById(id)
                .map(job -> {
                    job.getJobSkills().size(); // force-initialise for the DTO mapper
                    return job;
                })
                .orElseThrow(() -> ResourceNotFoundException.of("Job", id));
    }

    private JobResponse toResponse(Job job) {
        Long count = applicationRepository.countByJobId(job.getId());
        return JobResponse.of(job, job.getCreatedBy() == null ? null : job.getCreatedBy().getFullName(), count);
    }

    private User loadUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    private String uniqueSlug(String title) {
        String base = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "job";
        }
        String candidate = base;
        int suffix = 2;
        while (jobRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static int clampWeight(Integer weight) {
        if (weight == null) return 1;
        return Math.min(3, Math.max(1, weight));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T orDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
