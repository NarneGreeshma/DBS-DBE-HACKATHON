package com.smarthire.interview;

import com.smarthire.enums.Enums.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Only concrete-argument queries here: a null-guarded {@code (:from is null or ...)}
 * predicate makes PostgreSQL unable to infer the parameter type
 * ("could not determine data type of parameter $1").
 */
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByApplicationIdOrderByScheduledAtAsc(Long applicationId);

    List<Interview> findAllByOrderByScheduledAtAsc();

    List<Interview> findByStatusOrderByScheduledAtAsc(InterviewStatus status);

    List<Interview> findByStatusAndScheduledAtAfterOrderByScheduledAtAsc(InterviewStatus status, Instant now);

    long countByStatus(InterviewStatus status);
}
