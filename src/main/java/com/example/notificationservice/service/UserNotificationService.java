package com.example.notificationservice.service;

import com.example.notificationservice.dto.UserNotificationResponse;
import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for user-facing notification operations (notification inbox)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserNotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Get paginated notification history for a user (PUSH notifications only)
     */
    public Page<UserNotificationResponse> getNotificationHistory(String userId, int page, int size) {
        log.info("Fetching notification history for user: {}, page: {}, size: {}", userId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notifications = notificationRepository
                .findByRecipientIdAndChannelOrderByCreatedAtDesc(userId, NotificationChannel.PUSH, pageable);

        return notifications.map(this::mapToUserNotificationResponse);
    }

    /**
     * Get unread notification count for a user
     */
    public long getUnreadCount(String userId) {
        log.info("Getting unread count for user: {}", userId);
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Mark a specific notification as read
     */
    @Transactional
    public boolean markAsRead(String userId, UUID notificationId) {
        log.info("Marking notification {} as read for user: {}", notificationId, userId);

        return notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .map(notification -> {
                    if (!notification.getIsRead()) {
                        notification.setIsRead(true);
                        notificationRepository.save(notification);
                        log.info("Notification {} marked as read", notificationId);
                    }
                    return true;
                })
                .orElse(false);
    }

    /**
     * Mark all notifications as read for a user
     */
    @Transactional
    public int markAllAsRead(String userId) {
        log.info("Marking all notifications as read for user: {}", userId);
        int count = notificationRepository.markAllAsReadByUserId(userId);
        log.info("Marked {} notifications as read for user: {}", count, userId);
        return count;
    }

    /**
     * Delete a specific notification
     */
    @Transactional
    public boolean deleteNotification(String userId, UUID notificationId) {
        log.info("Deleting notification {} for user: {}", notificationId, userId);

        if (notificationRepository.existsByIdAndRecipientId(notificationId, userId)) {
            notificationRepository.deleteByIdAndRecipientId(notificationId, userId);
            log.info("Notification {} deleted", notificationId);
            return true;
        }

        log.warn("Notification {} not found for user: {}", notificationId, userId);
        return false;
    }

    private UserNotificationResponse mapToUserNotificationResponse(Notification notification) {
        return UserNotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .isRead(notification.getIsRead() != null && notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
