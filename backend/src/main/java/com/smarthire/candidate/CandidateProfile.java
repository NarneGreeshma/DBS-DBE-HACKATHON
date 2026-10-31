package com.smarthire.candidate;

import com.smarthire.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 1:1 profile extension of a CANDIDATE user. Holds the uploaded resume file,
 * the extracted plain text (input for the Python resume-intelligence service)
 * and the free-form career details.
 */
@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "headline", length = 160)
    private String headline;

    @Column(name = "summary")
    private String summary;

    @Column(name = "experience_years", nullable = false)
    @Builder.Default
    private Integer experienceYears = 0;

    @Column(name = "location", length = 120)
    private String location;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    @Column(name = "current_ctc", precision = 12, scale = 2)
    private BigDecimal currentCtc;

    @Column(name = "expected_ctc", precision = 12, scale = 2)
    private BigDecimal expectedCtc;

    @Column(name = "resume_file_path")
    private String resumeFilePath;

    @Column(name = "resume_original_name")
    private String resumeOriginalName;

    @Column(name = "resume_content_type")
    private String resumeContentType;

    @Column(name = "resume_text")
    private String resumeText;

    public boolean hasResume() {
        return resumeFilePath != null && !resumeFilePath.isBlank();
    }
}
