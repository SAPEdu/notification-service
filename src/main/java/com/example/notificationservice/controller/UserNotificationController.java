package com.example.notificationservice.controller;

import com.example.notificationservice.config.CasdoorAuthenticationContext;
import com.example.notificationservice.dto.UserNotificationResponse;
import com.example.notificationservice.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * User-facing notification APIs for notification inbox
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class UserNotificationController {

    private final UserNotificationService userNotificationService;
    private final CasdoorAuthenticationContext authContext;

    /**
     * Get paginated notification history for the current user
     */
    @GetMapping
    public ResponseEntity<Page<UserNotificationResponse>> getNotificationHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} fetching notification history, page: {}, size: {}", userId, page, size);

        Page<UserNotificationResponse> notifications = userNotificationService.getNotificationHistory(userId, page,
                size);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread notification count for the current user
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        String userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} fetching unread count", userId);

        long count = userNotificationService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * Mark a specific notification as read
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        String userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} marking notification {} as read", userId, id);

        boolean updated = userNotificationService.markAsRead(userId, id);

        if (updated) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Mark all notifications as read for the current user
     */
    @PutMapping("/read-all")
    public ResponseEntity<Integer> markAllAsRead() {
        String userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} marking all notifications as read", userId);

        int count = userNotificationService.markAllAsRead(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * Delete a specific notification
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable UUID id) {
        String userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} deleting notification {}", userId, id);

        boolean deleted = userNotificationService.deleteNotification(userId, id);

        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
