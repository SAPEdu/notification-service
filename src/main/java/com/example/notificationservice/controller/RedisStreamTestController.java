package com.example.notificationservice.controller;

import com.example.notificationservice.config.CasdoorAuthenticationContext;
import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.event.inbound.AssessmentPublishedEvent;
import com.example.notificationservice.event.inbound.ProctoringViolationEvent;
import com.example.notificationservice.event.inbound.SessionCompletedEvent;
import com.example.notificationservice.event.inbound.UserRegisteredEvent;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.service.NotificationService;
import com.example.notificationservice.service.RedisStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Test controller for Redis Streams
 * Only active in development/test profiles
 * Remove or disable in production!
 */
@RestController
@RequestMapping("/api/v1/redis/test")
@RequiredArgsConstructor
@Slf4j
@Profile({ "dev", "test", "local" })
@PreAuthorize("hasRole('ADMIN')")
public class RedisStreamTestController {

        private final RedisStreamService redisStreamService;
        private final RedisTemplate<String, Object> redisTemplate;
        private final CasdoorAuthenticationContext authContext;
        private final NotificationService notificationService;
        private final NotificationRepository notificationRepository;

        @Value("${app.redis.streams.user-events}")
        private String userEventsStream;

        @Value("${app.redis.streams.assessment-events}")
        private String assessmentEventsStream;

        @Value("${app.redis.streams.proctoring-events}")
        private String proctoringEventsStream;

        @Value("${app.redis.streams.notification-events}")
        private String notificationEventsStream;

        /**
         * Publish User Registered Event
         */
        @PostMapping("/events/user-registered")
        public ResponseEntity<Map<String, Object>> publishUserRegistered(@RequestBody UserRegisteredEvent event) {
                log.info("Publishing user registered event via API: {}", event.getUserId());

                event.init(); // Initialize eventId and timestamp
                String messageId = redisStreamService.publish(userEventsStream, event);

                return ResponseEntity.ok(Map.of(
                                "success", true,
                                "messageId", messageId,
                                "stream", userEventsStream,
                                "event", event));
        }

        /**
         * Publish Session Completed Event
         */
        @PostMapping("/events/session-completed")
        public ResponseEntity<Map<String, Object>> publishSessionCompleted(@RequestBody SessionCompletedEvent event) {
                log.info("Publishing session completed event via API: {}", event.getSessionId());

                event.init();
                String messageId = redisStreamService.publish(assessmentEventsStream, event);

                return ResponseEntity.ok(Map.of(
                                "success", true,
                                "messageId", messageId,
                                "stream", assessmentEventsStream,
                                "event", event));
        }

        /**
         * Publish Proctoring Violation Event
         */
        @PostMapping("/events/proctoring-violation")
        public ResponseEntity<Map<String, Object>> publishProctoringViolation(
                        @RequestBody ProctoringViolationEvent event) {
                log.info("Publishing proctoring violation event via API: {}", event.getSessionId());

                event.init();
                String messageId = redisStreamService.publish(proctoringEventsStream, event);

                return ResponseEntity.ok(Map.of(
                                "success", true,
                                "messageId", messageId,
                                "stream", proctoringEventsStream,
                                "event", event));
        }

        /**
         * Publish Assessment Published Event
         */
        @PostMapping("/events/assessment-published")
        public ResponseEntity<Map<String, Object>> publishAssessmentPublished(
                        @RequestBody AssessmentPublishedEvent event) {
                log.info("Publishing assessment published event via API: {}", event.getAssessmentId());

                event.init();
                String messageId = redisStreamService.publish(assessmentEventsStream, event);

                return ResponseEntity.ok(Map.of(
                                "success", true,
                                "messageId", messageId,
                                "stream", assessmentEventsStream,
                                "event", event));
        }

        /**
         * Quick test endpoint - publish sample user registration
         */
        @PostMapping("/quick/user-registered")
        public ResponseEntity<Map<String, Object>> quickUserRegistered(
                        @RequestParam(defaultValue = "123") Integer userId,
                        @RequestParam(defaultValue = "john.doe") String username,
                        @RequestParam(defaultValue = "john.doe@example.com") String email) {

                UserRegisteredEvent event = UserRegisteredEvent.builder()
                                .userId(userId)
                                .username(username)
                                .email(email)
                                .firstName("John")
                                .lastName("Doe")
                                .build();
                event.init();

                String messageId = redisStreamService.publish(userEventsStream, event);

                return ResponseEntity.ok(Map.of(
                                "success", true,
                                "messageId", messageId,
                                "message", "User registration event published",
                                "checkMailHog", "http://localhost:8025"));
        }

        /**
         * Get stream information
         */
        @GetMapping("/streams/{streamName}/info")
        public ResponseEntity<Map<String, Object>> getStreamInfo(@PathVariable String streamName) {
                String fullStreamName = resolveStreamName(streamName);

                Long length = redisTemplate.opsForStream().size(fullStreamName);

                return ResponseEntity.ok(Map.of(
                                "stream", fullStreamName,
                                "length", length != null ? length : 0,
                                "timestamp", Instant.now()));
        }

        /**
         * Read messages from stream
         */
        @GetMapping("/streams/{streamName}/messages")
        public ResponseEntity<Map<String, Object>> readMessages(
                        @PathVariable String streamName,
                        @RequestParam(defaultValue = "10") int count) {

                String fullStreamName = resolveStreamName(streamName);

                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                                .read(StreamReadOptions.empty().count(count),
                                                StreamOffset.create(fullStreamName, ReadOffset.from("0")));

                List<Map<String, Object>> result = new ArrayList<>();
                if (messages != null) {
                        for (MapRecord<String, Object, Object> message : messages) {
                                result.add(Map.of(
                                                "id", message.getId().getValue(),
                                                "stream", message.getStream(),
                                                "data", message.getValue()));
                        }
                }

                return ResponseEntity.ok(Map.of(
                                "stream", fullStreamName,
                                "count", result.size(),
                                "messages", result));
        }

        /**
         * Get all available streams
         */
        @GetMapping("/streams")
        public ResponseEntity<Map<String, Object>> getAllStreams() {
                List<Map<String, Object>> streams = new ArrayList<>();

                streams.add(getStreamDetails(userEventsStream, "user-events"));
                streams.add(getStreamDetails(assessmentEventsStream, "assessment-events"));
                streams.add(getStreamDetails(proctoringEventsStream, "proctoring-events"));
                streams.add(getStreamDetails(notificationEventsStream, "notification-events"));

                return ResponseEntity.ok(Map.of(
                                "streams", streams,
                                "timestamp", Instant.now()));
        }

        /**
         * Clear a stream (careful!)
         */
        @DeleteMapping("/streams/{streamName}")
        public ResponseEntity<Map<String, Object>> clearStream(@PathVariable String streamName) {
                String fullStreamName = resolveStreamName(streamName);

                Integer adminId = authContext.getCurrentUserId().orElse(null);
                log.warn("Admin {} is clearing stream: {}", adminId, fullStreamName);

                Boolean deleted = redisTemplate.delete(fullStreamName);

                return ResponseEntity.ok(Map.of(
                                "success", deleted != null && deleted,
                                "stream", fullStreamName,
                                "message", "Stream cleared",
                                "warning", "Consumer groups may need to be recreated"));
        }

        /**
         * Bulk publish events
         */
        @PostMapping("/bulk/user-registered")
        public ResponseEntity<Map<String, Object>> bulkPublishUserRegistered(
                        @RequestParam(defaultValue = "10") int count) {

                List<String> messageIds = new ArrayList<>();

                for (int i = 1; i <= count; i++) {
                        UserRegisteredEvent event = UserRegisteredEvent.builder()
                                        .userId(1000 + i)
                                        .username("user" + i)
                                        .email("user" + i + "@example.com")
                                        .firstName("User")
                                        .lastName("Number" + i)
                                        .build();
                        event.init();

                        String messageId = redisStreamService.publish(userEventsStream, event);
                        messageIds.add(messageId);
                }

                return ResponseEntity.ok(Map.of(
                                "success", true,
                                "count", count,
                                "messageIds", messageIds,
                                "stream", userEventsStream,
                                "checkMailHog", "http://localhost:8025"));
        }

        /**
         * Test complete workflow
         */
        @PostMapping("/workflow/complete")
        public ResponseEntity<Map<String, Object>> testCompleteWorkflow() {
                Map<String, Object> results = new LinkedHashMap<>();

                // 1. User Registration
                UserRegisteredEvent userEvent = UserRegisteredEvent.builder()
                                .userId(999)
                                .username("workflow.test")
                                .email("workflow@example.com")
                                .firstName("Workflow")
                                .lastName("Test")
                                .build();
                userEvent.init();
                results.put("userRegistered", redisStreamService.publish(userEventsStream, userEvent));

                // 2. Session Completed
                SessionCompletedEvent sessionEvent = SessionCompletedEvent.builder()
                                .userId(999)
                                .username("workflow.test")
                                .email("workflow@example.com")
                                .sessionId("SESSION-WORKFLOW-001")
                                .assessmentName("Test Assessment")
                                .completionTime("30 minutes")
                                .score(95.0)
                                .status("PASSED")
                                .build();
                sessionEvent.init();
                results.put("sessionCompleted", redisStreamService.publish(assessmentEventsStream, sessionEvent));

                // 3. Proctoring Violation
                ProctoringViolationEvent violationEvent = ProctoringViolationEvent.builder()
                                .userId(999)
                                .username("workflow.test")
                                .sessionId("SESSION-WORKFLOW-001")
                                .violationType("TEST_VIOLATION")
                                .severity("LOW")
                                .proctorIds(List.of(1, 2, 3))
                                .build();
                violationEvent.init();
                results.put("proctoringViolation", redisStreamService.publish(proctoringEventsStream, violationEvent));

                return ResponseEntity.ok(Map.of(
                                "success", true,
                                "message", "Complete workflow published",
                                "events", results,
                                "checkMailHog", "http://localhost:8025",
                                "checkSSE", "Connect SSE to see real-time notifications"));
        }

        // Helper methods

        private String resolveStreamName(String shortName) {
                return switch (shortName.toLowerCase()) {
                        case "user-events", "user" -> userEventsStream;
                        case "assessment-events", "assessment" -> assessmentEventsStream;
                        case "proctoring-events", "proctoring" -> proctoringEventsStream;
                        case "notification-events", "notification" -> notificationEventsStream;
                        default -> shortName.startsWith("notification:") ? shortName : "notification:" + shortName;
                };
        }

        private Map<String, Object> getStreamDetails(String streamName, String shortName) {
                Long length = redisTemplate.opsForStream().size(streamName);

                return Map.of(
                                "name", streamName,
                                "shortName", shortName,
                                "length", length != null ? length : 0);
        }

        /**
         * Debug endpoint - manually trigger assessment processing
         */
        @PostMapping("/debug/process-assessment-directly")
        public ResponseEntity<Map<String, Object>> debugProcessAssessment(
                        @RequestParam Integer userId,
                        @RequestParam String username,
                        @RequestParam(required = false) String email) {

                log.info("🐛 DEBUG: Manually processing assessment notification");

                try {
                        Map<String, Object> data = new HashMap<>();
                        data.put("username", username);
                        data.put("assessmentName", "Debug Test Assessment");
                        data.put("duration", 60);
                        data.put("dueDate", "2025-02-01T00:00:00Z");

                        notificationService.processNotification(
                                        "assessment.published",
                                        userId,
                                        email,
                                        data,
                                        List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL));

                        // Wait a bit
                        Thread.sleep(2000);

                        // Check database
                        List<Notification> notifications = notificationRepository.findByRecipientId(
                                        userId,
                                        PageRequest.of(0, 1, Sort.by("createdAt").descending())).getContent();

                        Notification lastNotification = notifications.isEmpty() ? null : notifications.get(0);

                        return ResponseEntity.ok(Map.of(
                                        "success", true,
                                        "userId", userId,
                                        "notificationCreated", lastNotification != null,
                                        "notificationStatus",
                                        lastNotification != null ? lastNotification.getStatus() : "NONE",
                                        "templateCheck", lastNotification != null ? Map.of(
                                                        "hasUsername", lastNotification.getContent().contains(username),
                                                        "contentPreview", lastNotification.getContent().substring(0,
                                                                        Math.min(200, lastNotification.getContent()
                                                                                        .length())))
                                                        : "No notification"));

                } catch (Exception e) {
                        log.error("Debug test failed", e);
                        return ResponseEntity.ok(Map.of(
                                        "success", false,
                                        "error", e.getMessage()));
                }
        }
}