package com.smarthire.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, Long> {

    Optional<ResumeAnalysis> findTopByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    List<ResumeAnalysis> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
