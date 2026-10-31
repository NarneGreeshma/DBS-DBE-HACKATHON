package com.smarthire.application;

import com.smarthire.ai.ResumeAnalysis;
import com.smarthire.ai.ResumeAnalysisService;
import com.smarthire.application.dto.ApplicationDtos.AnalysisDetail;
import com.smarthire.application.dto.ApplicationDtos.AnalysisSummary;
import com.smarthire.application.dto.ApplicationDtos.ApplicationResponse;
import com.smarthire.application.dto.ApplicationDtos.CandidateRef;
import com.smarthire.application.dto.ApplicationDtos.EventResponse;
import com.smarthire.application.dto.ApplicationDtos.StageRequest;
import com.smarthire.candidate.CandidateProfile;
import com.smarthire.candidate.CandidateProfileRepository;
import com.smarthire.common.BusinessException;
import com.smarthire.common.ConflictException;
import com.smarthire.common.PageResponse;
import com.smarthire.common.ResourceNotFoundException;
import com.smarthire.enums.Enums.ApplicationSource;
import com.smarthire.enums.Enums.ApplicationStage;
import com.smarthire.enums.Enums.NotificationType;
import com.smarthire.enums.Enums.RoleCode;
import com.smarthire.job.Job;
import com.smarthire.job.JobRepository;
import com.smarthire.notification.NotificationService;
import com.smarthire.security.AuthenticatedUser;
import com.smarthire.storage.FileStorageService;
import com.smarthire.storage.ResumeTextExtractor;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The hiring pipeline: apply, browse, move stages (with an audit trail), withdraw and
 * re-run AI screening.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    /** Legal pipeline moves. Terminal states allow no further transitions. */
    private static final Map<ApplicationStage, Set<ApplicationStage>> ALLOWED = Map.of(
            ApplicationStage.APPLIED, EnumSet.of(ApplicationStage.SCREENING, ApplicationStage.SHORTLISTED,
                    ApplicationStage.INTERVIEW, ApplicationStage.REJECTED, ApplicationStage.WITHDRAWN),
            ApplicationStage.SCREENING, EnumSet.of(ApplicationStage.SHORTLISTED,
                    ApplicationStage.INTERVIEW, ApplicationStage.REJECTED, ApplicationStage.WITHDRAWN),
            ApplicationStage.SHORTLISTED, EnumSet.of(ApplicationStage.INTERVIEW,
                    ApplicationStage.REJECTED, ApplicationStage.WITHDRAWN),
            ApplicationStage.INTERVIEW, EnumSet.of(ApplicationStage.OFFER,
                    ApplicationStage.REJECTED, ApplicationStage.WITHDRAWN),
            ApplicationStage.OFFER, EnumSet.of(ApplicationStage.HIRED, ApplicationStage.REJECTED),
            ApplicationStage.HIRED, EnumSet.noneOf(ApplicationStage.class),
            ApplicationStage.REJECTED, EnumSet.noneOf(ApplicationStage.class),
            ApplicationStage.WITHDRAWN, EnumSet.noneOf(ApplicationStage.class));

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final ResumeAnalysisService resumeAnalysisService;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final ResumeTextExtractor resumeTextExtractor;

    public record ApplicationSearch(
            Long jobId,
            ApplicationStage stage,
            Long candidateId,
            String q,
            boolean sortByScore) {
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public ApplicationResponse apply(Long jobId, String coverLetter, ApplicationSource source,
                                     MultipartFile resume, AuthenticatedUser actor) throws Exception {
        if (jobId == null) {
            throw new BusinessException("jobId is required");
        }
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> ResourceNotFoundException.of("Job", jobId));
        if (!job.isOpenForApplications()) {
            throw new ConflictException("This job is not accepting applications (status: " + job.getStatus() + ")");
        }
        if (applicationRepository.existsByJobIdAndCandidateId(jobId, actor.id())) {
            throw new ConflictException("You have already applied to this job");
        }

        User candidate = userRepository.findById(actor.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", actor.id()));
        CandidateProfile profile = candidateProfileRepository.findByUserId(actor.id())
                .orElseGet(() -> newProfile(actor.id()));

        if (resume != null && !resume.isEmpty()) {
            String text = resumeTextExtractor.extract(resume.getBytes(), resume.getOriginalFilename());
            if (profile.hasResume()) {
                fileStorageService.deleteIfExists(profile.getResumeFilePath());
            }
            profile.setResumeFilePath(fileStorageService.store(resume, "resume-" + actor.id()));
            profile.setResumeOriginalName(resume.getOriginalFilename());
            profile.setResumeContentType(resume.getContentType());
            profile.setResumeText(text);
            candidateProfileRepository.save(profile);
        }

        Application application = new Application();
        application.setJob(job);
        application.setCandidate(candidate);
        application.setCoverLetter(blankToNull(coverLetter));
        application.setSource(source == null ? ApplicationSource.CAREER_PAGE : source);
        application.setStage(ApplicationStage.APPLIED);
        application.setStageUpdatedAt(Instant.now());
        application.setAppliedAt(Instant.now());
        application.addEvent(ApplicationEvent.builder()
                .fromStage(null)
                .toStage(ApplicationStage.APPLIED)
                .changedBy(candidate)
                .note("Application submitted")
                .build());

        Application saved = applicationRepository.save(application);
        log.info("Candidate {} applied to job {}", actor.id(), jobId);

        notifyStaff(saved, "New application",
                "%s applied for %s".formatted(candidate.getFullName(), job.getTitle()),
                "/applications/" + saved.getId());
        notifyUser(candidate.getId(), "Application submitted",
                "Your application for %s was received".formatted(job.getTitle()),
                NotificationType.APPLICATION, "/applications");

        // AI screening runs in the same transaction; it never throws (see ResumeAnalysisService)
        resumeAnalysisService.analyzeOnApply(saved);

        return toResponse(saved, toRef(candidate, profile), latestAnalysis(saved.getId()));
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long id, AuthenticatedUser actor) {
        Application application = requireGraph(id);
        assertCanRead(application, actor);
        return toResponse(application, profileOf(application), latestAnalysis(id));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> mine(Long candidateId) {
        List<Application> applications = applicationRepository.findByCandidateGraph(candidateId);
        Map<Long, CandidateProfile> profiles = profileMap(List.of(candidateId));
        return applications.stream()
                .map(a -> toResponse(a, toRef(a.getCandidate(), profiles.get(candidateId)), latestAnalysis(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> search(ApplicationSearch search, int page, int size,
                                                     AuthenticatedUser actor) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                search.sortByScore()
                        ? Sort.by(Sort.Direction.DESC, "overallScore").and(Sort.by(Sort.Direction.DESC, "appliedAt"))
                        : Sort.by(Sort.Direction.DESC, "appliedAt"));

        Page<Application> result = applicationRepository.findAll(toSpecification(search, actor), pageable);

        List<Long> candidateIds = result.getContent().stream()
                .map(a -> a.getCandidate().getId()).distinct().toList();
        Map<Long, CandidateProfile> profiles = profileMap(candidateIds);

        Map<Long, AnalysisSummary> analyses = new java.util.HashMap<>();
        result.getContent().forEach(a -> {
            AnalysisSummary summary = latestAnalysis(a.getId());
            if (summary != null) {
                analyses.put(a.getId(), summary);
            }
        });

        return PageResponse.from(result, a -> toResponse(
                a,
                toRef(a.getCandidate(), profiles.get(a.getCandidate().getId())),
                analyses.get(a.getId())));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> timeline(Long id, AuthenticatedUser actor) {
        Application application = requireGraph(id);
        assertCanRead(application, actor);
        return application.getEvents().stream()
                .sorted(java.util.Comparator.comparing(ApplicationEvent::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalysisDetail> analysisHistory(Long id, AuthenticatedUser actor) {
        Application application = requireGraph(id);
        assertCanRead(application, actor);
        return resumeAnalysisService.history(id).stream()
                .map(AnalysisDetail::from)
                .toList();
    }

    // ------------------------------------------------------------------ mutations

    @Transactional
    public ApplicationResponse changeStage(Long id, StageRequest request, AuthenticatedUser actor) {
        if (request.stage() == null) {
            throw new BusinessException("stage is required");
        }
        if (actor == null || !actor.isStaff()) {
            throw new AccessDeniedException("Only recruiters and admins can move applications");
        }
        Application application = requireGraph(id);
        ApplicationStage from = application.getStage();
        ApplicationStage to = request.stage();

        if (from == to) {
            return toResponse(application, profileOf(application), latestAnalysis(id));
        }
        if (from == ApplicationStage.WITHDRAWN) {
            throw new ConflictException("This application was withdrawn by the candidate");
        }
        Set<ApplicationStage> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessException("Illegal stage transition: %s -> %s".formatted(from, to));
        }

        application.setStage(to);
        application.setStageUpdatedAt(Instant.now());
        application.addEvent(ApplicationEvent.builder()
                .fromStage(from)
                .toStage(to)
                .changedBy(userRepository.findById(actor.id()).orElse(null))
                .note(blankToNull(request.note()))
                .build());

        Application saved = applicationRepository.save(application);
        notifyUser(saved.getCandidate().getId(),
                "Application update",
                "Your application for %s moved to %s".formatted(saved.getJob().getTitle(), to),
                NotificationType.STAGE, "/applications");
        log.info("Application {} moved {} -> {} by {}", id, from, to, actor.email());

        return toResponse(saved, profileOf(saved), latestAnalysis(id));
    }

    @Transactional
    public ApplicationResponse withdraw(Long id, AuthenticatedUser actor) {
        Application application = requireGraph(id);
        if (!application.getCandidate().getId().equals(actor.id())) {
            throw new AccessDeniedException("You can only withdraw your own application");
        }
        ApplicationStage from = application.getStage();
        if (from == ApplicationStage.WITHDRAWN) {
            return toResponse(application, profileOf(application), latestAnalysis(id));
        }
        if (!ALLOWED.get(from).contains(ApplicationStage.WITHDRAWN)) {
            throw new ConflictException("This application can no longer be withdrawn (stage: " + from + ")");
        }

        application.setStage(ApplicationStage.WITHDRAWN);
        application.setStageUpdatedAt(Instant.now());
        application.addEvent(ApplicationEvent.builder()
                .fromStage(from)
                .toStage(ApplicationStage.WITHDRAWN)
                .changedBy(application.getCandidate())
                .note("Withdrawn by candidate")
                .build());

        return toResponse(applicationRepository.save(application), profileOf(application), latestAnalysis(id));
    }

    @Transactional
    public ResumeAnalysis reanalyze(Long id, AuthenticatedUser actor) {
        Application application = requireGraph(id);
        if (!actor.isStaff()) {
            throw new AccessDeniedException("Only recruiters and admins can run screening");
        }
        return resumeAnalysisService.analyze(id, true);
    }

    // ------------------------------------------------------------------ helpers

    private Specification<Application> toSpecification(ApplicationSearch search, AuthenticatedUser actor) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!actor.isStaff()) {
                // candidates only ever see their own rows
                predicates.add(cb.equal(root.get("candidate").get("id"), actor.id()));
            }
            if (search.jobId() != null) {
                predicates.add(cb.equal(root.get("job").get("id"), search.jobId()));
            }
            if (search.stage() != null) {
                predicates.add(cb.equal(root.get("stage"), search.stage()));
            }
            if (search.candidateId() != null) {
                predicates.add(cb.equal(root.get("candidate").get("id"), search.candidateId()));
            }
            if (search.q() != null && !search.q().isBlank()) {
                String like = "%" + search.q().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("candidate").<String>get("fullName")), like),
                        cb.like(cb.lower(root.get("candidate").<String>get("email")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Application requireGraph(Long id) {
        return applicationRepository.findByIdGraph(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Application", id));
    }

    private void assertCanRead(Application application, AuthenticatedUser actor) {
        if (actor.isStaff()) {
            return;
        }
        if (!application.getCandidate().getId().equals(actor.id())) {
            throw new AccessDeniedException("You can only view your own applications");
        }
    }

    private ApplicationResponse toResponse(Application application, CandidateRef candidate,
                                           AnalysisSummary analysis) {
        return ApplicationResponse.of(application, candidate, analysis);
    }

    private CandidateRef profileOf(Application application) {
        Long candidateId = application.getCandidate().getId();
        return toRef(application.getCandidate(), profileMap(List.of(candidateId)).get(candidateId));
    }

    /** Maps a user + optional profile onto the DTO, tolerating a missing profile row. */
    private CandidateRef toRef(User user, CandidateProfile profile) {
        if (profile == null) {
            return basicRef(user);
        }
        return new CandidateRef(user.getId(), user.getFullName(), user.getEmail(),
                profile.getHeadline(), profile.getExperienceYears(), profile.getLocation(),
                profile.hasResume());
    }

    private CandidateRef basicRef(User user) {
        return new CandidateRef(user.getId(), user.getFullName(), user.getEmail(), null, 0, null, false);
    }

    private Map<Long, CandidateProfile> profileMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return candidateProfileRepository.findByUserIds(userIds).stream()
                .collect(Collectors.toMap(CandidateProfile::getUserId, Function.identity(), (a, b) -> a));
    }

    private CandidateProfile newProfile(Long userId) {
        CandidateProfile profile = new CandidateProfile();
        profile.setUserId(userId);
        profile.setExperienceYears(0);
        return profile;
    }

    private AnalysisSummary latestAnalysis(Long applicationId) {
        return resumeAnalysisService.latest(applicationId)
                .map(a -> new AnalysisSummary(
                        a.getId(), a.getStatus(), a.getMatchScore(), a.getAtsScore(),
                        a.getMissingSkills(), a.getModel(), a.getCreatedAt()))
                .orElse(null);
    }

    private void notifyStaff(Application application, String title, String body, String link) {
        List<Long> staff = new ArrayList<>(userRepository.findActiveIdsByRole(RoleCode.RECRUITER.name()));
        staff.addAll(userRepository.findActiveIdsByRole(RoleCode.ADMIN.name()));
        notificationService.notifyUsers(staff, title, body, NotificationType.APPLICATION, link);
    }

    private void notifyUser(Long userId, String title, String body, NotificationType type, String link) {
        notificationService.notifyUser(userId, title, body, type, link);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
