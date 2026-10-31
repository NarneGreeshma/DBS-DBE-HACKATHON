package com.smarthire.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Skill> findAllByOrderByCategoryAscNameAsc();

    @Query("select s from Skill s where lower(s.name) in :names")
    List<Skill> findByNormalizedNames(List<String> names);

    /** How often each skill appears on OPEN postings - drives the skill-demand chart. */
    @Query("""
            select s.id, s.name, s.category, count(js),
                   sum(case when js.required = true then 1 else 0 end)
            from JobSkill js
              join js.skill s
              join js.job j
            where j.status = com.smarthire.enums.Enums.JobStatus.OPEN
            group by s.id, s.name, s.category
            order by count(js) desc, s.name asc
            """)
    List<Object[]> demandOnOpenJobs();
}
