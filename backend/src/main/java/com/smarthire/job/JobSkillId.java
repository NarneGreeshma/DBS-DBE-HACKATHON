package com.smarthire.job;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key of {@link JobSkill} ({@code job_id}, {@code skill_id}). */
public class JobSkillId implements Serializable {

    private Long job;
    private Long skill;

    public JobSkillId() {
    }

    public JobSkillId(Long job, Long skill) {
        this.job = job;
        this.skill = skill;
    }

    public Long getJob() {
        return job;
    }

    public void setJob(Long job) {
        this.job = job;
    }

    public Long getSkill() {
        return skill;
    }

    public void setSkill(Long skill) {
        this.skill = skill;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JobSkillId id)) return false;
        return Objects.equals(job, id.job) && Objects.equals(skill, id.skill);
    }

    @Override
    public int hashCode() {
        return Objects.hash(job, skill);
    }
}
