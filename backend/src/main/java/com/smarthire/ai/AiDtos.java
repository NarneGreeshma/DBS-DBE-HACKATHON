package com.smarthire.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Wire contract of the Python resume-intelligence service.
 * These records are the <em>only</em> place the two backends agree.
 */
public final class AiDtos {

    private AiDtos() {
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzeRequest {
        /** Plain text of the candidate resume (extracted by Apache Tika in Java). */
        private String resumeText;
        /** Concatenated job posting text used as the target profile. */
        private String jobText;
        private String jobTitle;
        /** Skills the posting requires - required ones first. */
        private List<String> requiredSkills;
        private List<String> preferredSkills;
        private Integer minYears;
        private Integer experienceYears;
        private String experienceLevel;
        private String candidateName;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzeResponse {
        private Double matchScore;
        private Double atsScore;
        private List<String> extractedSkills;
        private List<String> missingSkills;
        private List<String> strengths;
        private List<String> gaps;
        private String summary;
        private List<String> recommendations;
        private String model;
    }

    public record HealthResponse(String status, String service, String version) {
    }
}
