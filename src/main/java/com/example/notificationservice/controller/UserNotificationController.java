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

        Integer userId = authContext.getCurrentUserId()
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
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        Integer userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} fetching unread count", userId);

        long count = userNotificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Mark a specific notification as read
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        Integer userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} marking notification {} as read", userId, id);

        boolean success = userNotificationService.markAsRead(userId, id);

        if (success) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Mark all notifications as read for the current user
     */
    @PutMapping("/mark-all-read")
    public ResponseEntity<Map<String, Integer>> markAllAsRead() {
        Integer userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} marking all notifications as read", userId);

        int markedCount = userNotificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("markedCount", markedCount));
    }

    /**
     * Delete a specific notification
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable UUID id) {
        Integer userId = authContext.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("User ID not found in token"));

        log.info("User {} deleting notification {}", userId, id);

        boolean success = userNotificationService.deleteNotification(userId, id);

        if (success) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
