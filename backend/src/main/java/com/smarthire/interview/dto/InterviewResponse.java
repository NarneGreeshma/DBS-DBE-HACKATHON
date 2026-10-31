package com.smarthire.interview.dto;

import com.smarthire.enums.Enums.InterviewMode;
import com.smarthire.enums.Enums.InterviewStatus;
import com.smarthire.interview.Interview;

import java.time.Instant;

/**
 * Interview wire contract. Mapped <b>inside</b> the service transaction: every field here
 * crosses a lazy association ({@code application -> job / candidate}), so mapping it in
 * the controller would throw {@code LazyInitializationException} with {@code open-in-view=false}.
 */
public record InterviewResponse(
        Long id,
        Long applicationId,
        String jobTitle,
        String jobSlug,
        String candidateName,
        String candidateEmail,
        Integer round,
        InterviewMode mode,
        Instant scheduledAt,
        Integer durationMinutes,
        String meetingLink,
        String location,
        InterviewStatus status,
        String feedback,
        Short rating,
        String scheduledByName) {

    public static InterviewResponse from(Interview interview) {
        return new InterviewResponse(
                interview.getId(),
                interview.getApplication().getId(),
                interview.getApplication().getJob().getTitle(),
                interview.getApplication().getJob().getSlug(),
                interview.getApplication().getCandidate().getFullName(),
                interview.getApplication().getCandidate().getEmail(),
                interview.getRound(),
                interview.getMode(),
                interview.getScheduledAt(),
                interview.getDurationMinutes(),
                interview.getMeetingLink(),
                interview.getLocation(),
                interview.getStatus(),
                interview.getFeedback(),
                interview.getRating(),
                interview.getScheduledBy() == null ? null : interview.getScheduledBy().getFullName());
    }
}
