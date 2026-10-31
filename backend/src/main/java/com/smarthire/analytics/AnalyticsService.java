package com.smarthire.analytics;

import com.smarthire.application.ApplicationRepository;
import com.smarthire.enums.Enums.ApplicationStage;
import com.smarthire.enums.Enums.InterviewStatus;
import com.smarthire.enums.Enums.JobStatus;
import com.smarthire.enums.Enums.RoleCode;
import com.smarthire.interview.InterviewRepository;
import com.smarthire.job.JobRepository;
import com.smarthire.skill.SkillRepository;
import com.smarthire.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only dashboard aggregates. Everything is computed by PostgreSQL - no in-memory
 * full-table scans - so the numbers stay correct as the dataset grows.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final InterviewRepository interviewRepository;
    private final SkillRepository skillRepository;

    public record Summary(
            long totalJobs,
            long openJobs,
            long draftJobs,
            long closedJobs,
            long totalCandidates,
            long totalRecruiters,
            long totalApplications,
            long activePipeline,
            long hired,
            long rejected,
            long offers,
            Double averageScore,
            long scheduledInterviews,
            long completedInterviews) {
    }

    public record FunnelPoint(String stage, long count) {
    }

    public record SkillDemand(Long skillId, String name, String category,
                              long jobCount, long requiredCount) {
    }

    public record DashboardSummary(Summary summary,
                                   List<FunnelPoint> funnel,
                                   List<SkillDemand> topSkills,
                                   Map<String, Long> applicationsBySource) {
    }

    @Transactional(readOnly = true)
    public DashboardSummary dashboard() {
        Summary summary = summary();
        return new DashboardSummary(summary, funnel(), topSkills(), applicationsBySource());
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        long totalApplications = applicationRepository.count();
        long hired = applicationRepository.countByStage(ApplicationStage.HIRED);
        long rejected = applicationRepository.countByStage(ApplicationStage.REJECTED)
                + applicationRepository.countByStage(ApplicationStage.WITHDRAWN);

        return new Summary(
                jobRepository.count(),
                jobRepository.countByStatus(JobStatus.OPEN),
                jobRepository.countByStatus(JobStatus.DRAFT),
                jobRepository.countByStatus(JobStatus.CLOSED),
                userRepository.countByRole_Code(RoleCode.CANDIDATE.name()),
                userRepository.countByRole_Code(RoleCode.RECRUITER.name())
                        + userRepository.countByRole_Code(RoleCode.ADMIN.name()),
                totalApplications,
                Math.max(totalApplications - hired - rejected, 0),
                hired,
                rejected,
                applicationRepository.countByStage(ApplicationStage.OFFER),
                applicationRepository.averageScore(),
                interviewRepository.countByStatus(InterviewStatus.SCHEDULED),
                interviewRepository.countByStatus(InterviewStatus.COMPLETED));
    }

    @Transactional(readOnly = true)
    public List<FunnelPoint> funnel() {
        List<FunnelPoint> points = new ArrayList<>();
        for (ApplicationStage stage : ApplicationStage.values()) {
            if (stage == ApplicationStage.WITHDRAWN) {
                continue;
            }
            points.add(new FunnelPoint(stage.name(), applicationRepository.countByStage(stage)));
        }
        return points;
    }

    @Transactional(readOnly = true)
    public List<SkillDemand> topSkills() {
        List<SkillDemand> demand = new ArrayList<>();
        for (Object[] row : skillRepository.demandOnOpenJobs()) {
            Number jobCount = (Number) row[3];
            Number requiredCount = (Number) row[4];
            demand.add(new SkillDemand(
                    (Long) row[0],
                    (String) row[1],
                    (String) row[2],
                    jobCount == null ? 0 : jobCount.longValue(),
                    requiredCount == null ? 0 : requiredCount.longValue()));
        }
        return demand;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> applicationsBySource() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : applicationRepository.countGroupedBySource()) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }
}
