package com.smarthire.notification;

import com.smarthire.common.ResourceNotFoundException;
import com.smarthire.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public record NotificationResponse(Long id, String title, String body, String type,
                                       String link, boolean read, Instant createdAt) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getTitle(), n.getBody(),
                    n.getType().name(), n.getLink(), Boolean.TRUE.equals(n.getRead()), n.getCreatedAt());
        }
    }

    @GetMapping
    @Operation(summary = "Own notifications, newest first")
    public List<NotificationResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(CurrentUser.requireId(),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", notificationRepository.countByUserIdAndReadFalse(CurrentUser.requireId()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    @Transactional
    public NotificationResponse markRead(@PathVariable Long id) {
        Notification notification = notificationRepository
                .findByIdForUser(id, CurrentUser.requireId())
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark every notification as read")
    @Transactional
    public Map<String, Object> markAllRead() {
        return Map.of("updated", notificationRepository.markAllRead(CurrentUser.requireId()));
    }
}
