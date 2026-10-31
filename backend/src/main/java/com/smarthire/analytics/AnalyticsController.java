package com.smarthire.analytics;

import com.smarthire.application.Application;
import com.smarthire.application.ApplicationRepository;
import com.smarthire.application.dto.ApplicationDtos.ApplicationResponse;
import com.smarthire.application.dto.ApplicationDtos.CandidateRef;
import com.smarthire.candidate.CandidateProfile;
import com.smarthire.candidate.CandidateProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recruiter/admin dashboard: headline KPIs, pipeline funnel, skill demand and the
 * freshest applications. Read-only.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
@Tag(name = "Analytics", description = "Dashboard aggregates")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ApplicationRepository applicationRepository;
    private final CandidateProfileRepository candidateProfileRepository;

    @GetMapping("/summary")
    @Operation(summary = "KPI card values")
    public AnalyticsService.Summary summary() {
        return analyticsService.summary();
    }

    @GetMapping("/funnel")
    @Operation(summary = "Applications per pipeline stage")
    public List<AnalyticsService.FunnelPoint> funnel() {
        return analyticsService.funnel();
    }

    @GetMapping("/skills")
    @Operation(summary = "Skill demand across OPEN postings")
    public List<AnalyticsService.SkillDemand> skills() {
        return analyticsService.topSkills();
    }

    @GetMapping("/sources")
    @Operation(summary = "Applications grouped by source")
    public Map<String, Long> sources() {
        return analyticsService.applicationsBySource();
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Everything the dashboard screen needs in one round trip")
    public AnalyticsService.DashboardSummary dashboard() {
        return analyticsService.dashboard();
    }

    @GetMapping("/recent-applications")
    @Operation(summary = "Most recent applications with candidate names")
    @Transactional(readOnly = true)
    public List<ApplicationResponse> recentApplications(
            @RequestParam(defaultValue = "8") int limit) {
        List<Application> applications = applicationRepository.findRecentGraph(
                org.springframework.data.domain.PageRequest.of(0, Math.min(Math.max(limit, 1), 25)));

        Map<Long, CandidateProfile> profiles = candidateProfileRepository
                .findByUserIds(applications.stream()
                        .map(a -> a.getCandidate().getId()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(CandidateProfile::getUserId, Function.identity(), (a, b) -> a));

        return applications.stream()
                .map(a -> ApplicationResponse.of(a, ref(a, profiles.get(a.getCandidate().getId())), null))
                .toList();
    }

    private static CandidateRef ref(Application application, CandidateProfile profile) {
        if (profile == null) {
            return new CandidateRef(
                    application.getCandidate().getId(),
                    application.getCandidate().getFullName(),
                    application.getCandidate().getEmail(),
                    null, 0, null, false);
        }
        return new CandidateRef(
                application.getCandidate().getId(),
                application.getCandidate().getFullName(),
                application.getCandidate().getEmail(),
                profile.getHeadline(),
                profile.getExperienceYears(),
                profile.getLocation(),
                profile.hasResume());
    }
}
