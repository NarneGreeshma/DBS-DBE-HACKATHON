package com.smarthire.job;

import com.smarthire.skill.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/** Job &lt;-&gt; Skill association with a weight (1..3) and a required flag. */
@Entity
@Table(name = "job_skills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(JobSkillId.class)
public class JobSkill {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "weight", nullable = false)
    @Builder.Default
    private Integer weight = 1;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean required = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JobSkill other)) return false;
        return Objects.equals(job != null ? job.getId() : null, other.job != null ? other.job.getId() : null)
                && Objects.equals(skill != null ? skill.getId() : null, other.skill != null ? other.skill.getId() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(job != null ? job.getId() : null, skill != null ? skill.getId() : null);
    }
}
