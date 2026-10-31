package com.smarthire.interview;

import com.smarthire.application.Application;
import com.smarthire.application.ApplicationRepository;
import com.smarthire.application.ApplicationService;
import com.smarthire.application.dto.ApplicationDtos.StageRequest;
import com.smarthire.common.BusinessException;
import com.smarthire.common.ConflictException;
import com.smarthire.common.ResourceNotFoundException;
import com.smarthire.enums.Enums.ApplicationStage;
import com.smarthire.enums.Enums.InterviewMode;
import com.smarthire.enums.Enums.InterviewStatus;
import com.smarthire.enums.Enums.NotificationType;
import com.smarthire.interview.dto.InterviewResponse;
import com.smarthire.notification.NotificationService;
import com.smarthire.security.AuthenticatedUser;
import com.smarthire.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final Set<InterviewStatus> FINAL =
            EnumSet.of(InterviewStatus.COMPLETED, InterviewStatus.CANCELLED, InterviewStatus.NO_SHOW);

    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public record InterviewRequest(
            Long applicationId,
            Integer round,
            InterviewMode mode,
            Instant scheduledAt,
            Integer durationMinutes,
            String meetingLink,
            String location) {
    }

    public record InterviewUpdate(InterviewStatus status, String feedback, Integer rating) {
    }

    @Transactional
    public InterviewResponse schedule(InterviewRequest request, AuthenticatedUser actor) {
        if (request.applicationId() == null || request.scheduledAt() == null) {
            throw new BusinessException("applicationId and scheduledAt are required");
        }
        Application application = applicationRepository.findByIdGraph(request.applicationId())
                .orElseThrow(() -> ResourceNotFoundException.of("Application", request.applicationId()));

        if (application.getStage() == ApplicationStage.WITHDRAWN
                || application.getStage() == ApplicationStage.REJECTED) {
            throw new ConflictException("This application is no longer eligible for interviews");
        }
        if (request.scheduledAt().isBefore(Instant.now().minus(5, ChronoUnit.MINUTES))) {
            throw new BusinessException("Interview must be scheduled in the future");
        }

        int round = request.round() == null || request.round() < 1 ? 1 : request.round();

        Interview interview = new Interview();
        interview.setApplication(application);
        interview.setScheduledBy(userRepository.findById(actor.id()).orElse(null));
        interview.setRound(round);
        interview.setMode(request.mode() == null ? InterviewMode.ONLINE : request.mode());
        interview.setScheduledAt(request.scheduledAt());
        interview.setDurationMinutes(request.durationMinutes() == null ? 45 : request.durationMinutes());
        interview.setMeetingLink(blankToNull(request.meetingLink()));
        interview.setLocation(blankToNull(request.location()));
        interview.setStatus(InterviewStatus.SCHEDULED);

        Interview saved = interviewRepository.save(interview);

        // keep the pipeline honest: interviewing candidates sit in the INTERVIEW stage
        if (application.getStage() == ApplicationStage.SHORTLISTED
                || application.getStage() == ApplicationStage.APPLIED
                || application.getStage() == ApplicationStage.SCREENING) {
            applicationService.changeStage(application.getId(),
                    new StageRequest(ApplicationStage.INTERVIEW,
                            "Interview round " + round + " scheduled"),
                    actor);
        }

        notificationService.notifyUser(application.getCandidate().getId(),
                "Interview scheduled",
                "Round %d for %s is confirmed for %s".formatted(
                        round, application.getJob().getTitle(), saved.getScheduledAt()),
                NotificationType.INTERVIEW, "/interviews");

        log.info("Interview {} scheduled for application {} by {}",
                saved.getId(), application.getId(), actor.email());
        return InterviewResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> list(InterviewStatus status, boolean upcomingOnly,
                                        AuthenticatedUser actor) {
        List<Interview> all;
        if (upcomingOnly) {
            all = interviewRepository.findByStatusAndScheduledAtAfterOrderByScheduledAtAsc(
                    status == null ? InterviewStatus.SCHEDULED : status, Instant.now());
        } else if (status != null) {
            all = interviewRepository.findByStatusOrderByScheduledAtAsc(status);
        } else {
            all = interviewRepository.findAllByOrderByScheduledAtAsc();
        }

        List<InterviewResponse> visible = new ArrayList<>();
        for (Interview interview : all) {
            if (actor.isStaff()
                    || interview.getApplication().getCandidate().getId().equals(actor.id())) {
                visible.add(InterviewResponse.from(interview));
            }
        }
        return visible;
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> byApplication(Long applicationId, AuthenticatedUser actor) {
        Application application = applicationRepository.findByIdGraph(applicationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Application", applicationId));
        if (!actor.isStaff() && !application.getCandidate().getId().equals(actor.id())) {
            throw new AccessDeniedException("You can only view your own interviews");
        }
        return interviewRepository.findByApplicationIdOrderByScheduledAtAsc(applicationId).stream()
                .map(InterviewResponse::from)
                .toList();
    }

    @Transactional
    public InterviewResponse update(Long id, InterviewUpdate update, AuthenticatedUser actor) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Interview", id));

        if (interview.getStatus() != InterviewStatus.SCHEDULED && update.status() != null) {
            throw new ConflictException("Interview already " + interview.getStatus());
        }
        if (update.status() != null) {
            interview.setStatus(update.status());
        }
        if (update.feedback() != null) {
            interview.setFeedback(update.feedback());
        }
        if (update.rating() != null) {
            if (update.rating() < 1 || update.rating() > 5) {
                throw new BusinessException("Rating must be between 1 and 5");
            }
            interview.setRating(update.rating().shortValue());
        }
        Interview saved = interviewRepository.save(interview);

        if (saved.getStatus() == InterviewStatus.COMPLETED) {
            notificationService.notifyUser(saved.getApplication().getCandidate().getId(),
                    "Interview completed",
                    "Your interview for %s has been recorded"
                            .formatted(saved.getApplication().getJob().getTitle()),
                    NotificationType.INTERVIEW, "/interviews");
        }
        return InterviewResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser actor) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Interview", id));
        if (FINAL.contains(interview.getStatus())) {
            throw new ConflictException("Completed or cancelled interviews cannot be deleted");
        }
        interviewRepository.delete(interview);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
