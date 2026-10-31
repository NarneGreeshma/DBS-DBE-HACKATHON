package com.smarthire.job;

import com.smarthire.common.BaseEntity;
import com.smarthire.enums.Enums.EmploymentType;
import com.smarthire.enums.Enums.ExperienceLevel;
import com.smarthire.enums.Enums.JobStatus;
import com.smarthire.enums.Enums.WorkplaceType;
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
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "slug", nullable = false, unique = true, length = 180)
    private String slug;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "requirements")
    private String requirements;

    @Column(name = "responsibilities")
    private String responsibilities;

    @Column(name = "location", length = 120)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "workplace_type", nullable = false, length = 24)
    @Builder.Default
    private WorkplaceType workplaceType = WorkplaceType.ONSITE;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 24)
    @Builder.Default
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 24)
    @Builder.Default
    private ExperienceLevel experienceLevel = ExperienceLevel.MID;

    @Column(name = "min_years")
    private Integer minYears;

    @Column(name = "max_years")
    private Integer maxYears;

    @Column(name = "min_salary", precision = 14, scale = 2)
    private BigDecimal minSalary;

    @Column(name = "max_salary", precision = 14, scale = 2)
    private BigDecimal maxSalary;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "openings", nullable = false)
    @Builder.Default
    private Integer openings = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private JobStatus status = JobStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private com.smarthire.user.User createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JobSkill> jobSkills = new ArrayList<>();

    public boolean isOpenForApplications() {
        if (status != JobStatus.OPEN) {
            return false;
        }
        return closesAt == null || closesAt.isAfter(Instant.now());
    }
}
