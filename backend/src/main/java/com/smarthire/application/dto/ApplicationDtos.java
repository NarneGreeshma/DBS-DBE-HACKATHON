package com.smarthire.application.dto;

import com.smarthire.application.Application;
import com.smarthire.application.ApplicationEvent;
import com.smarthire.enums.Enums.AnalysisStatus;
import com.smarthire.enums.Enums.ApplicationSource;
import com.smarthire.enums.Enums.ApplicationStage;
import com.smarthire.enums.Enums.JobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request/response contract of the applications (pipeline) module. */
public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record CreateApplicationRequest(
            Long jobId,
            String coverLetter,
            ApplicationSource source) {
    }

    public record StageRequest(ApplicationStage stage, String note) {
    }

    public record JobRef(Long id, String title, String slug, String location, JobStatus status) {
        public static JobRef from(Application application) {
            return new JobRef(
                    application.getJob().getId(),
                    application.getJob().getTitle(),
                    application.getJob().getSlug(),
                    application.getJob().getLocation(),
                    application.getJob().getStatus());
        }
    }

    public record CandidateRef(Long id, String fullName, String email, String headline,
                               Integer experienceYears, String location, boolean hasResume) {
    }

    public record AnalysisSummary(Long id, AnalysisStatus status, BigDecimal matchScore,
                                  BigDecimal atsScore, List<String> missingSkills, String model,
                                  Instant createdAt) {
    }

    public record AnalysisDetail(Long id, AnalysisStatus status, BigDecimal matchScore,
                                 BigDecimal atsScore, List<String> extractedSkills,
                                 List<String> missingSkills, List<String> strengths,
                                 List<String> gaps, String summary, List<String> recommendations,
                                 String model, String error, Instant createdAt) {

        public static AnalysisDetail from(com.smarthire.ai.ResumeAnalysis analysis) {
            return new AnalysisDetail(
                    analysis.getId(),
                    analysis.getStatus(),
                    analysis.getMatchScore(),
                    analysis.getAtsScore(),
                    analysis.getExtractedSkills(),
                    analysis.getMissingSkills(),
                    analysis.getStrengths(),
                    analysis.getGaps(),
                    analysis.getSummary(),
                    analysis.getRecommendations(),
                    analysis.getModel(),
                    analysis.getError(),
                    analysis.getCreatedAt());
        }
    }

    public record ApplicationResponse(
            Long id,
            JobRef job,
            CandidateRef candidate,
            String coverLetter,
            ApplicationSource source,
            ApplicationStage stage,
            BigDecimal overallScore,
            Instant appliedAt,
            Instant stageUpdatedAt,
            AnalysisSummary analysis) {

        public static ApplicationResponse of(Application application, CandidateRef candidate,
                                             AnalysisSummary analysis) {
            return new ApplicationResponse(
                    application.getId(),
                    JobRef.from(application),
                    candidate,
                    application.getCoverLetter(),
                    application.getSource(),
                    application.getStage(),
                    application.getOverallScore(),
                    application.getAppliedAt(),
                    application.getStageUpdatedAt(),
                    analysis);
        }
    }

    public record EventResponse(Long id, ApplicationStage fromStage, ApplicationStage toStage,
                                String changedBy, String note, Instant createdAt) {
        public static EventResponse from(ApplicationEvent event) {
            return new EventResponse(
                    event.getId(),
                    event.getFromStage(),
                    event.getToStage(),
                    event.getChangedBy() == null ? null : event.getChangedBy().getFullName(),
                    event.getNote(),
                    event.getCreatedAt());
        }
    }
}
