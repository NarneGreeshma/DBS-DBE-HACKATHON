package com.smarthire.ai;

import com.smarthire.application.Application;
import com.smarthire.application.ApplicationRepository;
import com.smarthire.candidate.CandidateProfile;
import com.smarthire.candidate.CandidateProfileRepository;
import com.smarthire.common.ResourceNotFoundException;
import com.smarthire.enums.Enums.AnalysisStatus;
import com.smarthire.job.Job;
import com.smarthire.job.JobSkill;
import com.smarthire.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates resume screening: builds the prompt-free payload the Python service needs,
 * calls it, and persists the result on {@code resume_analyses}.
 *
 * <p>Python is an <b>enhancement, never a dependency</b>: when it is switched off or
 * unreachable, a {@code FAILED} analysis row is recorded and the application itself is
 * completely unaffected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnalysisService {

    private static final String DISABLED_MODEL = "disabled";

    private final AiClient aiClient;
    private final ApplicationRepository applicationRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final ResumeAnalysisRepository resumeAnalysisRepository;

    @Transactional
    public ResumeAnalysis analyze(Long applicationId, boolean force) {
        Application application = applicationRepository.findByIdGraph(applicationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Application", applicationId));

        if (!force) {
            Optional<ResumeAnalysis> existing = latest(applicationId);
            if (existing.isPresent() && existing.get().getStatus() == AnalysisStatus.COMPLETED) {
                return existing.get();
            }
        }
        return run(application);
    }

    @Transactional
    public ResumeAnalysis analyzeOnApply(Application application) {
        return run(application);
    }

    @Transactional(readOnly = true)
    public Optional<ResumeAnalysis> latest(Long applicationId) {
        return resumeAnalysisRepository.findTopByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    @Transactional(readOnly = true)
    public List<ResumeAnalysis> history(Long applicationId) {
        return resumeAnalysisRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    private ResumeAnalysis run(Application application) {
        Job job = application.getJob();
        User candidate = application.getCandidate();

        ResumeAnalysis analysis = new ResumeAnalysis();
        analysis.setApplication(application);
        analysis.setStatus(AnalysisStatus.RUNNING);
        analysis = resumeAnalysisRepository.save(analysis);

        if (!aiClient.isEnabled()) {
            return fail(analysis, DISABLED_MODEL, "AI analysis disabled (smarthire.ai.enabled=false)");
        }

        String resumeText = candidateProfileRepository.findByUserId(candidate.getId())
                .map(CandidateProfile::getResumeText)
                .orElse(null);
        if (resumeText == null || resumeText.isBlank()) {
            return fail(analysis, DISABLED_MODEL, "No resume text available - ask the candidate to upload a resume");
        }

        try {
            AiDtos.AnalyzeResponse response = aiClient.analyze(buildRequest(job, candidate, resumeText));
            return apply(analysis, response);
        } catch (RuntimeException ex) {
            log.warn("Resume analysis failed for application {}: {}", application.getId(), ex.getMessage());
            return fail(analysis, "smarthire-rules-v1",
                    truncate("Resume intelligence service unavailable: " + ex.getMessage(), 500));
        }
    }

    private AiDtos.AnalyzeRequest buildRequest(Job job, User candidate, String resumeText) {
        List<String> required = new ArrayList<>();
        List<String> preferred = new ArrayList<>();
        for (JobSkill link : job.getJobSkills()) {
            (Boolean.TRUE.equals(link.getRequired()) ? required : preferred)
                    .add(link.getSkill().getName());
        }

        int experienceYears = candidateProfileRepository.findByUserId(candidate.getId())
                .map(CandidateProfile::getExperienceYears)
                .filter(y -> y != null)
                .orElse(0);

        String jobText = String.join("\n\n",
                nullToEmpty(job.getTitle()),
                nullToEmpty(job.getDescription()),
                nullToEmpty(job.getRequirements()),
                nullToEmpty(job.getResponsibilities()));

        return AiDtos.AnalyzeRequest.builder()
                .resumeText(resumeText)
                .jobText(jobText)
                .jobTitle(job.getTitle())
                .requiredSkills(required)
                .preferredSkills(preferred)
                .minYears(job.getMinYears())
                .experienceYears(experienceYears)
                .experienceLevel(job.getExperienceLevel() == null ? null : job.getExperienceLevel().name())
                .candidateName(candidate.getFullName())
                .build();
    }

    private ResumeAnalysis apply(ResumeAnalysis analysis, AiDtos.AnalyzeResponse response) {
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setModel(response.getModel() == null ? "smarthire-rules-v1" : response.getModel());
        analysis.setMatchScore(scale(response.getMatchScore()));
        analysis.setAtsScore(scale(response.getAtsScore()));
        analysis.setExtractedSkills(orEmpty(response.getExtractedSkills()));
        analysis.setMissingSkills(orEmpty(response.getMissingSkills()));
        analysis.setStrengths(orEmpty(response.getStrengths()));
        analysis.setGaps(orEmpty(response.getGaps()));
        analysis.setRecommendations(orEmpty(response.getRecommendations()));
        analysis.setSummary(response.getSummary());
        analysis.setError(null);

        ResumeAnalysis saved = resumeAnalysisRepository.save(analysis);

        Application application = saved.getApplication();
        if (saved.getMatchScore() != null) {
            application.setOverallScore(saved.getMatchScore());
            applicationRepository.save(application);
        }
        return saved;
    }

    private ResumeAnalysis fail(ResumeAnalysis analysis, String model, String message) {
        analysis.setStatus(AnalysisStatus.FAILED);
        analysis.setModel(model);
        analysis.setError(message);
        analysis.setMatchScore(null);
        analysis.setAtsScore(null);
        return resumeAnalysisRepository.save(analysis);
    }

    private static List<String> orEmpty(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .sorted(Comparator.naturalOrder())
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static BigDecimal scale(Double value) {
        if (value == null) {
            return null;
        }
        double clamped = Math.max(0d, Math.min(100d, value));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}
