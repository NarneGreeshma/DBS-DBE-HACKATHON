package com.smarthire.notification;

import com.smarthire.enums.Enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fire-and-forget notifications. Never throws: a notification failure must not roll back
 * the business operation that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyUser(Long userId, String title, String body, NotificationType type, String link) {
        if (userId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(truncate(title, 160));
        notification.setBody(truncate(body, 500));
        notification.setType(type);
        notification.setLink(link);
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyUsers(List<Long> userIds, String title, String body, NotificationType type, String link) {
        if (userIds == null) {
            return;
        }
        userIds.forEach(id -> notifyUser(id, title, body, type, link));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
