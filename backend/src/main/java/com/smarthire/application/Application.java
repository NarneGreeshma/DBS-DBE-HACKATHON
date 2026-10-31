package com.smarthire.application;

import com.smarthire.common.BaseEntity;
import com.smarthire.enums.Enums.ApplicationSource;
import com.smarthire.enums.Enums.ApplicationStage;
import com.smarthire.job.Job;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(name = "uq_applications_job_candidate",
                columnNames = {"job_id", "candidate_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    /** The applicant (a CANDIDATE user). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private com.smarthire.user.User candidate;

    @Column(name = "cover_letter")
    private String coverLetter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    @Builder.Default
    private ApplicationSource source = ApplicationSource.CAREER_PAGE;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 24)
    @Builder.Default
    private ApplicationStage stage = ApplicationStage.APPLIED;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "stage_updated_at", nullable = false)
    @Builder.Default
    private Instant stageUpdatedAt = Instant.now();

    @Column(name = "applied_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant appliedAt = Instant.now();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApplicationEvent> events = new ArrayList<>();

    public void addEvent(ApplicationEvent event) {
        events.add(event);
        event.setApplication(this);
    }
}
