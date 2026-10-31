package com.smarthire.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long>,
        JpaSpecificationExecutor<Application> {

    boolean existsByJobIdAndCandidateId(Long jobId, Long candidateId);

    Optional<Application> findByJobIdAndCandidateId(Long jobId, Long candidateId);

    List<Application> findByCandidateIdOrderByAppliedAtDesc(Long candidateId);

    long countByJobId(Long jobId);

    long countByStage(com.smarthire.enums.Enums.ApplicationStage stage);

    @Query("select a from Application a join fetch a.job j join fetch a.candidate c where a.id = :id")
    Optional<Application> findByIdGraph(@Param("id") Long id);

    @Query("""
            select distinct a from Application a
              join fetch a.job j
              join fetch a.candidate c
              join fetch c.role
            where a.candidate.id = :candidateId
            order by a.appliedAt desc
            """)
    List<Application> findByCandidateGraph(@Param("candidateId") Long candidateId);

    @Query("select count(a) from Application a where a.job.createdBy.id = :recruiterId")
    long countByRecruiter(@Param("recruiterId") Long recruiterId);

    @Query("select avg(a.overallScore) from Application a where a.overallScore is not null")
    Double averageScore();

    @Query("select a from Application a join fetch a.job join fetch a.candidate c join fetch c.role "
            + "order by a.appliedAt desc")
    List<Application> findRecentGraph(org.springframework.data.domain.Pageable pageable);

    /** Batch application counts for a list of jobs - avoids N+1 on the job board. */
    @Query("select a.job.id as jobId, count(a) as cnt from Application a where a.job.id in :jobIds group by a.job.id")
    java.util.List<Object[]> countGroupedByJob(@Param("jobIds") List<Long> jobIds);

    @Query("select cast(a.source as string), count(a) from Application a group by a.source order by count(a) desc")
    List<Object[]> countGroupedBySource();
}
