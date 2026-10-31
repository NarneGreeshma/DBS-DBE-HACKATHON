package com.smarthire.candidate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;

    @Transactional(readOnly = true)
    public CandidateProfile findByUserId(Long userId) {
        return candidateProfileRepository.findByUserId(userId).orElse(null);
    }

    /** Bulk load for list screens; avoids N+1 when rendering applicant tables. */
    @Transactional(readOnly = true)
    public Map<Long, CandidateProfile> findByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return candidateProfileRepository.findByUserIds(userIds).stream()
                .collect(Collectors.toMap(CandidateProfile::getUserId, Function.identity(), (a, b) -> a));
    }
}
