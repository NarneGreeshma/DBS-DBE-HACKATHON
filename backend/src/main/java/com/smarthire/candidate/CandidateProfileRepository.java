package com.smarthire.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    Optional<CandidateProfile> findByUserId(Long userId);

    @Query("select p from CandidateProfile p where p.userId in :userIds")
    List<CandidateProfile> findByUserIds(@Param("userIds") List<Long> userIds);
}
