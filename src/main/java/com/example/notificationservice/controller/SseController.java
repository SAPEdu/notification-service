package com.example.notificationservice.controller;

import com.example.notificationservice.config.CasdoorAuthenticationContext;
import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.enums.NotificationStatus;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
@Slf4j
// REMOVED: @CrossOrigin - sử dụng global CORS config từ SecurityConfig
public class SseController {

        private final SseEmitterService sseEmitterService;
        private final CasdoorAuthenticationContext authContext;
        private final JwtDecoder jwtDecoder;
        private final NotificationRepository notificationRepository;

        /**
         * Establish SSE connection for user notifications
         * Accepts JWT token as query parameter for EventSource compatibility
         */
        @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public ResponseEntity<SseEmitter> connect() {
                String userId = authContext.getCurrentUserId()
                                .orElseThrow(() -> new IllegalStateException("Authentication required"));
                String username = authContext.getCurrentUsername().orElse("unknown");

                log.info("SSE connection request from user: {} ({})", username, userId);

                SseEmitter emitter = sseEmitterService.createEmitterForUser(userId);

                return ResponseEntity.ok()
                                .header("Cache-Control", "no-store")
                                .header("X-Accel-Buffering", "no")
                                // Thêm CORS headers explicitly cho SSE
                                .header("Access-Control-Allow-Origin", "*")
                                .body(emitter);
        }

        /**
         * Subscribe to a specific topic for broadcast messages
         */
        @GetMapping(value = "/subscribe/{topic}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public ResponseEntity<SseEmitter> subscribeToTopic(@PathVariable String topic) {
                String userId = authContext.getCurrentUserId()
                                .orElseThrow(() -> new IllegalStateException("Authentication required"));
                String username = authContext.getCurrentUsername().orElse("unknown");

                log.info("User {} ({}) subscribing to topic: {}", username, userId, topic);

                SseEmitter emitter = sseEmitterService.subscribeToTopic(topic, userId);

                return ResponseEntity.ok()
                                .header("Cache-Control", "no-store")
                                .header("X-Accel-Buffering", "no")
                                .header("Access-Control-Allow-Origin", "*")
                                .body(emitter);
        }

        /**
         * Send a notification to a specific user (Admin only)
         */
        @PreAuthorize("hasRole('ADMIN')")
        @PostMapping("/test/send-to-user")
        public ResponseEntity<Map<String, Object>> sendTestNotificationToUser(
                        @RequestParam String targetUserId,
                        @RequestParam String message,
                        @RequestParam(defaultValue = "test_notification") String type,
                        @RequestParam(defaultValue = "Test Notification") String subject) {

                Notification notification = Notification.builder()
                                .recipientId(targetUserId)
                                .type(type)
                                .subject(subject)
                                .content(message)
                                .channel(NotificationChannel.PUSH)
                                .status(NotificationStatus.SENT)
                                .isRead(false)
                                .build();

                Notification saved = notificationRepository.save(notification);
                log.info("Saved notification to DB: {} for user: {}", saved.getId(), targetUserId);

                boolean sent = sseEmitterService.sendNotificationToUser(targetUserId, type, message);

                return ResponseEntity.ok(Map.of(
                                "success", true,
                                "notificationId", saved.getId(),
                                "savedToDb", true,
                                "sentViaSse", sent,
                                "targetUserId", targetUserId,
                                "message", message,
                                "timestamp", System.currentTimeMillis()));
        }

        /**
         * Broadcast to a topic (Admin only)
         */
        @PreAuthorize("hasRole('ADMIN')")
        @PostMapping("/test/broadcast")
        public ResponseEntity<Map<String, Object>> broadcastToTopic(
                        @RequestParam String topic,
                        @RequestParam String message,
                        @RequestParam(defaultValue = "broadcast") String type,
                        @RequestParam(defaultValue = "Broadcast Notification") String subject) {

                sseEmitterService.broadcastToTopic(topic, type, Map.of(
                                "message", message,
                                "timestamp", System.currentTimeMillis(),
                                "topic", topic));

                return ResponseEntity.ok(Map.of(
                                "success", true,
                                "topic", topic,
                                "message", message,
                                "note", "Broadcast sent to connected subscribers.",
                                "timestamp", System.currentTimeMillis()));
        }

        /**
         * Get SSE connection statistics (Admin only)
         */
        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/stats")
        public ResponseEntity<Map<String, Object>> getConnectionStats() {
                return ResponseEntity.ok(Map.of(
                                "activeUserConnections", sseEmitterService.getActiveUserConnections(),
                                "timestamp", System.currentTimeMillis()));
        }

        /**
         * Check if a specific user is connected
         */
        @PreAuthorize("@casdoorAuthenticationContext.isAdmin() || #userId == @casdoorAuthenticationContext.getCurrentUserIdOrNull()")
        @GetMapping("/status/{userId}")
        public ResponseEntity<Map<String, Object>> checkUserConnection(@PathVariable String userId) {
                boolean connected = sseEmitterService.isUserConnected(userId);
                return ResponseEntity.ok(Map.of(
                                "userId", userId,
                                "connected", connected,
                                "timestamp", System.currentTimeMillis()));
        }

        /**
         * Manually disconnect a user (self or admin)
         */
        @PostMapping("/disconnect/{userId}")
        public ResponseEntity<Map<String, Object>> disconnectUser(@PathVariable String userId) {
                String currentUserId = authContext.getCurrentUserId().orElse(null);
                boolean isAdmin = authContext.isAdmin();

                if (!isAdmin && !userId.equals(currentUserId)) {
                        return ResponseEntity.status(403).body(Map.of(
                                        "error", "Forbidden",
                                        "message", "You can only disconnect your own connection"));
                }

                sseEmitterService.disconnectUser(userId);

                return ResponseEntity.ok(Map.of(
                                "userId", userId,
                                "disconnected", true,
                                "timestamp", System.currentTimeMillis()));
        }
}