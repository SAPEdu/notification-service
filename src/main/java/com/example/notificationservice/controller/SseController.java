package com.example.notificationservice.controller;

import com.example.notificationservice.config.CasdoorAuthenticationContext;
import com.example.notificationservice.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class SseController {

    private final SseEmitterService sseEmitterService;
    private final CasdoorAuthenticationContext authContext;

    /**
     * Establish SSE connection for user notifications
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() {
        Integer userId = authContext.getCurrentUserId().orElse(null);
        String username = authContext.getCurrentUsername().orElse("unknown");

        if (userId == null) {
            log.error("No userId found in authentication context");
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }

        log.info("SSE connection request from user: {} ({})", username, userId);
        return sseEmitterService.createEmitterForUser(userId);
    }

    /**
     * Subscribe to a specific topic for broadcast messages
     */
    @GetMapping(value = "/subscribe/{topic}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToTopic(@PathVariable String topic) {
        Integer userId = authContext.getCurrentUserId().orElse(null);
        String username = authContext.getCurrentUsername().orElse("unknown");

        if (userId == null) {
            log.error("No userId found in authentication context");
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new IllegalStateException("Authentication required"));
            return emitter;
        }

        log.info("User {} ({}) subscribing to topic: {}", username, userId, topic);
        return sseEmitterService.subscribeToTopic(topic, userId);
    }

    /**
     * Test endpoint to send a notification to a specific user (Admin only)
     */
    @PostMapping("/test/send-to-user")
    public ResponseEntity<Map<String, Object>> sendTestNotificationToUser(
            @RequestParam Integer targetUserId,
            @RequestParam String message) {

        if (!authContext.isAdmin()) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Forbidden",
                    "message", "Only admins can send test notifications"
            ));
        }

        boolean sent = sseEmitterService.sendNotificationToUser(
                targetUserId,
                "test",
                message
        );

        return ResponseEntity.ok(Map.of(
                "success", sent,
                "targetUserId", targetUserId,
                "message", message,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Test endpoint to broadcast to a topic (Admin only)
     */
    @PostMapping("/test/broadcast")
    public ResponseEntity<Map<String, Object>> broadcastToTopic(
            @RequestParam String topic,
            @RequestParam String message) {

        if (!authContext.isAdmin()) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Forbidden",
                    "message", "Only admins can send broadcast messages"
            ));
        }

        sseEmitterService.broadcastToTopic(topic, "broadcast", Map.of(
                "message", message,
                "timestamp", System.currentTimeMillis(),
                "topic", topic
        ));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "topic", topic,
                "message", message,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Get SSE connection statistics (Admin only)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getConnectionStats() {
        if (!authContext.isAdmin()) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Forbidden",
                    "message", "Only admins can view connection statistics"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "activeUserConnections", sseEmitterService.getActiveUserConnections(),
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Check if a specific user is connected
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<Map<String, Object>> checkUserConnection(@PathVariable Integer userId) {
        Integer currentUserId = authContext.getCurrentUserId().orElse(null);
        boolean isAdmin = authContext.isAdmin();

        if (!isAdmin && !userId.equals(currentUserId)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Forbidden",
                    "message", "You can only check your own connection status"
            ));
        }

        boolean connected = sseEmitterService.isUserConnected(userId);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "connected", connected,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Manually disconnect a user (self or admin)
     */
    @PostMapping("/disconnect/{userId}")
    public ResponseEntity<Map<String, Object>> disconnectUser(@PathVariable Integer userId) {
        Integer currentUserId = authContext.getCurrentUserId().orElse(null);
        boolean isAdmin = authContext.isAdmin();

        if (!isAdmin && !userId.equals(currentUserId)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Forbidden",
                    "message", "You can only disconnect your own connection"
            ));
        }

        sseEmitterService.disconnectUser(userId);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "disconnected", true,
                "timestamp", System.currentTimeMillis()
        ));
    }
}