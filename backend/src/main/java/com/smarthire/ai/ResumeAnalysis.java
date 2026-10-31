package com.smarthire.ai;

import com.smarthire.enums.Enums.AnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted output of the Python resume-intelligence service.
 * The backend - not Python - owns this data, so the AI service stays stateless.
 */
@Entity
@Table(name = "resume_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private com.smarthire.application.Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private AnalysisStatus status = AnalysisStatus.PENDING;

    @Column(name = "match_score", precision = 5, scale = 2)
    private BigDecimal matchScore;

    @Column(name = "ats_score", precision = 5, scale = 2)
    private BigDecimal atsScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_skills", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> extractedSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_skills", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> missingSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gaps", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> gaps = new ArrayList<>();

    @Column(name = "summary")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "error", length = 500)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
