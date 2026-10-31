package com.smarthire.job;

import com.smarthire.enums.Enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    Optional<Job> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Job> findByStatusOrderByPublishedAtDesc(JobStatus status);

    long countByStatus(JobStatus status);
}
