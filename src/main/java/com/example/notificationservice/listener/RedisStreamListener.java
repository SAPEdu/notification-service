package com.example.notificationservice.listener;

import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.event.inbound.*;
import com.example.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
public class RedisStreamListener {

    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisStreamTemplate;
    private final ObjectMapper objectMapper;

    public RedisStreamListener(NotificationService notificationService,
            @Qualifier("redisStreamTemplate") RedisTemplate<String, String> redisStreamTemplate,
            ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.redisStreamTemplate = redisStreamTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${app.redis.streams.user-events}")
    private String userEventsStream;

    @Value("${app.redis.streams.assessment-events}")
    private String assessmentEventsStream;

    @Value("${app.redis.streams.proctoring-events}")
    private String proctoringEventsStream;

    @Value("${app.redis.streams.notifications}")
    private String notificationsStream;

    @Value("${app.redis.consumer.group-id}")
    private String consumerGroup;

    @Value("${app.redis.consumer.name}")
    private String consumerName;

    private volatile boolean running = true;

    @PostConstruct
    public void initialize() {
        // Create consumer groups for all streams
        createConsumerGroupIfNotExists(userEventsStream);
        createConsumerGroupIfNotExists(assessmentEventsStream);
        createConsumerGroupIfNotExists(proctoringEventsStream);
        createConsumerGroupIfNotExists(notificationsStream);

        log.info("Redis Stream Listener initialized for streams: {}, {}, {}, {}",
                userEventsStream, assessmentEventsStream, proctoringEventsStream, notificationsStream);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        log.info("Redis Stream Listener shutting down...");
    }

    /**
     * Poll messages from all streams every second
     */
    @Scheduled(fixedDelay = 1000)
    public void pollMessages() {
        if (!running) {
            return;
        }

        try {
            // Poll from each stream
            pollFromStream(userEventsStream, this::handleUserEvent);
            pollFromStream(assessmentEventsStream, this::handleAssessmentEvent);
            pollFromStream(proctoringEventsStream, this::handleProctoringEvent);
            pollFromStream(notificationsStream, this::handleNotificationEvent);
        } catch (Exception e) {
            log.error("Error polling messages from Redis streams: {}", e.getMessage(), e);
        }
    }

    private void pollFromStream(String streamKey, MessageHandler handler) {
        try {
            log.debug("Polling stream: '{}' | group: '{}' | consumer: '{}'",
                    streamKey, consumerGroup, consumerName);

            // Read messages from the stream for this consumer group
            List<MapRecord<String, Object, Object>> messages = redisStreamTemplate.opsForStream()
                    .read(
                            Consumer.from(consumerGroup, consumerName),
                            StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                            StreamOffset.create(streamKey, ReadOffset.lastConsumed()));

            if (messages != null && !messages.isEmpty()) {
                for (MapRecord<String, Object, Object> message : messages) {
                    try {
                        log.debug("Processing message from stream '{}': {}", streamKey, message.getId());

                        // Convert map to object
                        Map<Object, Object> value = message.getValue();
                        handler.handle(value);

                        // Acknowledge the message
                        redisStreamTemplate.opsForStream().acknowledge(streamKey, consumerGroup, message.getId());

                    } catch (Exception e) {
                        log.error("Error processing message from stream '{}': {}", streamKey, e.getMessage(), e);
                    }
                }
            } else {
                log.trace("No new messages in stream '{}'", streamKey);
            }
        } catch (Exception e) {
            // Real error - log properly for debugging
            log.error("STREAM READ ERROR from '{}': {} (group: {}, consumer: {})",
                    streamKey, e.getMessage(), consumerGroup, consumerName, e);
        }
    }

    private void createConsumerGroupIfNotExists(String streamKey) {
        try {
            // Try to create the consumer group
            redisStreamTemplate.opsForStream().createGroup(streamKey, consumerGroup);
            log.info("Created consumer group '{}' for stream '{}'", consumerGroup, streamKey);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("BUSYGROUP")) {
                // Group already exists - this is fine
                log.debug("Consumer group '{}' already exists for stream '{}'", consumerGroup, streamKey);
            } else if (errorMsg != null && errorMsg.contains("ERR no such key")) {
                // Stream doesn't exist, create it with offset 0
                try {
                    redisStreamTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), consumerGroup);
                    log.info("Created stream '{}' and consumer group '{}'", streamKey, consumerGroup);
                } catch (Exception ex) {
                    log.error("Failed to create stream '{}' with group '{}': {}",
                            streamKey, consumerGroup, ex.getMessage());
                }
            } else {
                log.warn("Consumer group setup issue for stream '{}': {}", streamKey, errorMsg);
            }
        }
    }

    @FunctionalInterface
    private interface MessageHandler {
        void handle(Map<Object, Object> value) throws Exception;
    }

    // ==================== NOTIFICATION STREAM HANDLER (NEW) ====================

    /**
     * Handle events from the 'notifications' stream (from Assessment Service)
     * Routes events based on 'type' field in the envelope
     */
    private void handleNotificationEvent(Map<Object, Object> value) {
        try {
            log.debug("📥 RAW notification event keys: {}", value.keySet());
            log.debug("📥 RAW notification event values: {}", value);

            // Clean and parse the event envelope
            Map<String, Object> cleanedValue = cleanMap(value);
            log.debug("🧹 Cleaned event keys: {}", cleanedValue.keySet());
            log.debug("🧹 Cleaned event: {}", cleanedValue);

            // Get event type from envelope
            String eventType = (String) cleanedValue.get("type");
            if (eventType == null) {
                log.warn("No 'type' field in notification event: {}", cleanedValue.keySet());
                return;
            }

            // Get data payload
            Object dataObj = cleanedValue.get("data");
            log.debug("📦 Data object type: {}, value: {}",
                    dataObj != null ? dataObj.getClass().getSimpleName() : "null", dataObj);

            Map<String, Object> data;
            if (dataObj instanceof String dataStr) {
                log.debug("📦 Parsing data as JSON string");
                Map<String, Object> parsed = objectMapper.readValue(dataStr, Map.class);

                // Check if the parsed JSON is double-wrapped (contains another "data" field
                // with the payload)
                Object innerData = parsed.get("data");
                if (innerData instanceof Map) {
                    log.debug("📦 Found nested 'data' field, extracting inner payload");
                    data = (Map<String, Object>) innerData;
                } else {
                    data = parsed;
                }
            } else if (dataObj instanceof Map) {
                log.debug("📦 Data is already a Map");
                data = (Map<String, Object>) dataObj;
            } else {
                // Data might be flattened in the root
                log.debug("📦 Data not found, using cleaned root");
                data = cleanedValue;
            }

            log.debug("📦 Final data for handler: {}", data);

            log.info("📨 Processing notification event type: {}", eventType);

            switch (eventType) {
                case "assessment.published" -> handleAssessmentPublishedNew(data);
                case "assessment.expiring" -> handleAssessmentExpiring(data);
                case "attempt.started" -> handleAttemptStarted(data);
                case "attempt.submitted" -> handleAttemptSubmitted(data);
                case "attempt.graded" -> handleAttemptGraded(data);
                default -> log.warn("⚠️ Unknown notification event type: {}", eventType);
            }

        } catch (Exception e) {
            log.error("❌ Failed to handle notification event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle assessment.published event (new format from Assessment Service)
     */
    private void handleAssessmentPublishedNew(Map<String, Object> data) {
        try {
            AssessmentPublishedEvent event = objectMapper.convertValue(data, AssessmentPublishedEvent.class);
            log.info("🔵 Processing assessment.published: {} for {} students",
                    event.getAssessmentTitle(),
                    event.getStudentIds() != null ? event.getStudentIds().size() : 0);

            if (event.getStudentIds() == null || event.getStudentIds().isEmpty()) {
                log.warn("⚠️ No student IDs in assessment.published event");
                return;
            }

            Map<String, Object> templateData = new HashMap<>();
            templateData.put("assessmentName", event.getAssessmentTitle());
            templateData.put("assessmentTitle", event.getAssessmentTitle());
            templateData.put("duration", event.getDuration());
            templateData.put("dueDate", event.getDueDate());
            templateData.put("groupName", event.getGroupName());

            for (String studentId : event.getStudentIds()) {
                Map<String, Object> userData = new HashMap<>(templateData);
                userData.put("studentId", studentId);

                notificationService.processNotification(
                        "assessment.published",
                        studentId,
                        null, // Email will be fetched if needed
                        userData,
                        List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.TELEGRAM));
            }

            log.info("✅ Sent assessment.published notifications to {} students", event.getStudentIds().size());

        } catch (Exception e) {
            log.error("❌ Failed to handle assessment.published: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle assessment.expiring event
     */
    private void handleAssessmentExpiring(Map<String, Object> data) {
        try {
            AssessmentExpiringEvent event = objectMapper.convertValue(data, AssessmentExpiringEvent.class);
            log.info("⏰ Processing assessment.expiring: {} ({} hours remaining) for {} students",
                    event.getAssessmentTitle(),
                    event.getHoursRemaining(),
                    event.getStudentIds() != null ? event.getStudentIds().size() : 0);

            if (event.getStudentIds() == null || event.getStudentIds().isEmpty()) {
                log.warn("⚠️ No student IDs in assessment.expiring event");
                return;
            }

            Map<String, Object> templateData = new HashMap<>();
            templateData.put("assessmentName", event.getAssessmentTitle());
            templateData.put("assessmentTitle", event.getAssessmentTitle());
            templateData.put("hoursRemaining", event.getHoursRemaining());
            templateData.put("dueDate", event.getDueDate());
            templateData.put("groupName", event.getGroupName());

            for (String studentId : event.getStudentIds()) {
                Map<String, Object> userData = new HashMap<>(templateData);
                userData.put("studentId", studentId);

                notificationService.processNotification(
                        "assessment.expiring",
                        studentId,
                        null,
                        userData,
                        List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.TELEGRAM));
            }

            log.info("✅ Sent assessment.expiring notifications to {} students", event.getStudentIds().size());

        } catch (Exception e) {
            log.error("❌ Failed to handle assessment.expiring: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle attempt.started event - notify teacher
     */
    private void handleAttemptStarted(Map<String, Object> data) {
        try {
            AttemptStartedEvent event = objectMapper.convertValue(data, AttemptStartedEvent.class);
            log.info("▶️ Processing attempt.started: student {} started {} (teacher: {})",
                    event.getStudentId(), event.getAssessmentTitle(), event.getCreatorId());

            if (event.getCreatorId() == null) {
                log.warn("⚠️ No creator_id in attempt.started event, skipping teacher notification");
                return;
            }

            Map<String, Object> templateData = new HashMap<>();
            templateData.put("assessmentName", event.getAssessmentTitle());
            templateData.put("assessmentTitle", event.getAssessmentTitle());
            templateData.put("studentId", event.getStudentId());
            templateData.put("attemptId", event.getAttemptId());
            templateData.put("startedAt", event.getStartedAt());
            templateData.put("timeLimit", event.getTimeLimit());

            notificationService.processNotification(
                    "attempt.started",
                    event.getCreatorId(),
                    null,
                    templateData,
                    List.of(NotificationChannel.PUSH)); // Only push for started (optional notification)

            log.info("✅ Sent attempt.started notification to teacher: {}", event.getCreatorId());

        } catch (Exception e) {
            log.error("❌ Failed to handle attempt.started: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle attempt.submitted event - notify teacher
     */
    private void handleAttemptSubmitted(Map<String, Object> data) {
        try {
            AttemptSubmittedEvent event = objectMapper.convertValue(data, AttemptSubmittedEvent.class);
            log.info("📤 Processing attempt.submitted: student {} submitted {} (pending grade: {})",
                    event.getStudentId(), event.getAssessmentTitle(), event.getIsPendingGrade());

            if (event.getCreatorId() == null) {
                log.warn("⚠️ No creator_id in attempt.submitted event, skipping teacher notification");
                return;
            }

            Map<String, Object> templateData = new HashMap<>();
            templateData.put("assessmentName", event.getAssessmentTitle());
            templateData.put("assessmentTitle", event.getAssessmentTitle());
            templateData.put("studentId", event.getStudentId());
            templateData.put("attemptId", event.getAttemptId());
            templateData.put("submittedAt", event.getSubmittedAt());
            templateData.put("score", event.getScore());
            templateData.put("maxScore", event.getMaxScore());
            templateData.put("passed", event.getPassed());
            templateData.put("isPendingGrade", event.getIsPendingGrade());

            // Notify teacher about submission
            List<NotificationChannel> channels = Boolean.TRUE.equals(event.getIsPendingGrade())
                    ? List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.TELEGRAM) // Need
                                                                                                                 // manual
                                                                                                                 // grading
                    : List.of(NotificationChannel.PUSH); // Auto-graded, just push

            notificationService.processNotification(
                    "attempt.submitted",
                    event.getCreatorId(),
                    null,
                    templateData,
                    channels);

            log.info("✅ Sent attempt.submitted notification to teacher: {}", event.getCreatorId());

        } catch (Exception e) {
            log.error("❌ Failed to handle attempt.submitted: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle attempt.graded event - notify student
     */
    private void handleAttemptGraded(Map<String, Object> data) {
        try {
            AttemptGradedEvent event = objectMapper.convertValue(data, AttemptGradedEvent.class);
            log.info("📝 Processing attempt.graded: student {} scored {}/{} ({}%)",
                    event.getStudentId(), event.getScore(), event.getMaxScore(), event.getPercentage());

            if (event.getStudentId() == null) {
                log.warn("⚠️ No student_id in attempt.graded event");
                return;
            }

            Map<String, Object> templateData = new HashMap<>();
            templateData.put("assessmentName", event.getAssessmentTitle());
            templateData.put("assessmentTitle", event.getAssessmentTitle());
            templateData.put("attemptId", event.getAttemptId());
            templateData.put("gradedAt", event.getGradedAt());
            templateData.put("score", event.getScore());
            templateData.put("maxScore", event.getMaxScore());
            templateData.put("percentage", event.getPercentage());
            templateData.put("passed", event.getPassed());
            templateData.put("graderId", event.getGraderId());

            notificationService.processNotification(
                    "attempt.graded",
                    event.getStudentId(),
                    null,
                    templateData,
                    List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.TELEGRAM));

            log.info("Sent attempt.graded notification to student: {}", event.getStudentId());

        } catch (Exception e) {
            log.error("Failed to handle attempt.graded: {}", e.getMessage(), e);
        }
    }

    // ==================== LEGACY HANDLERS ====================

    private void handleUserEvent(Map<Object, Object> value) {
        try {
            // Convert map to clean Map<String, Object>
            log.debug("RAW Redis data: {}", value);

            Map<String, Object> cleanedValue = cleanMap(value);
            log.debug("CLEANED data: {}", cleanedValue);

            UserRegisteredEvent event = objectMapper.convertValue(cleanedValue, UserRegisteredEvent.class);
            log.debug("PARSED event: userId={}, email={}, firstName={}",
                    event.getUserId(), event.getEmail(), event.getFirstName());

            log.info("Processing user.registered event for user: {}", event.getUserId());

            Map<String, Object> data = new HashMap<>();
            data.put("username", event.getUsername());
            data.put("email", event.getEmail());
            data.put("firstName", event.getFirstName());
            data.put("lastName", event.getLastName());

            notificationService.processNotification(
                    "user.registered",
                    event.getUserId(),
                    event.getEmail(),
                    data,
                    List.of(NotificationChannel.EMAIL));
        } catch (Exception e) {
            log.error("Failed to handle user event: {}", e.getMessage(), e);
        }
    }

    private void handleAssessmentEvent(Map<Object, Object> value) {
        try {
            log.debug("📩 Received assessment event, checking type...");
            log.debug("Event keys: {}", value.keySet());

            // Check for nested assignedUsers keys (Redis flattens them)
            boolean hasAssignedUsers = value.keySet().stream()
                    .anyMatch(key -> key.toString().startsWith("assignedUsers"));

            boolean hasSessionId = value.containsKey("sessionId");

            log.debug("hasAssignedUsers: {}, hasSessionId: {}", hasAssignedUsers, hasSessionId);

            if (hasSessionId && !hasAssignedUsers) {
                log.debug("→ Routing to handleSessionCompleted");
                handleSessionCompleted(value);
            } else if (hasAssignedUsers) {
                log.debug("→ Routing to handleAssessmentPublished (legacy)");
                handleAssessmentPublishedLegacy(value);
            } else {
                log.warn("⚠️ Unknown assessment event type. Keys: {}", value.keySet());
            }
        } catch (Exception e) {
            log.error("❌ Failed to handle assessment event: {}", e.getMessage(), e);
        }
    }

    private void handleSessionCompleted(Map<Object, Object> value) {
        try {
            Map<String, Object> cleanedValue = cleanMap(value);
            SessionCompletedEvent event = objectMapper.convertValue(cleanedValue, SessionCompletedEvent.class);

            log.info("Processing session.completed event for user: {}", event.getUserId());

            Map<String, Object> data = new HashMap<>();
            data.put("username", event.getUsername());
            data.put("assessmentName", event.getAssessmentName());
            data.put("completionTime", event.getCompletionTime());
            data.put("score", event.getScore());
            data.put("status", event.getStatus());

            notificationService.processNotification(
                    "session.completed",
                    event.getUserId(),
                    event.getEmail(),
                    data,
                    List.of(NotificationChannel.EMAIL));
        } catch (Exception e) {
            log.error("Failed to handle session completed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Legacy handler for old assessment.published format (with assignedUsers)
     */
    private void handleAssessmentPublishedLegacy(Map<Object, Object> value) {
        try {
            log.info("🔵 Starting to process assessment.published event (legacy format)");

            // Clean the map first
            Map<String, Object> cleanedValue = cleanMap(value);
            log.debug("Cleaned event data keys: {}", cleanedValue.keySet());

            // Reconstruct assignedUsers from flattened keys
            cleanedValue = reconstructNestedObjects(cleanedValue);
            log.debug("Reconstructed event data: {}", cleanedValue);

            // Parse the event
            AssessmentPublishedEvent event;
            try {
                event = objectMapper.convertValue(cleanedValue, AssessmentPublishedEvent.class);
                log.info("✅ Parsed AssessmentPublishedEvent: assessmentId={}, name={}, users={}",
                        event.getAssessmentId(),
                        event.getAssessmentTitle(),
                        event.getAssignedUsers() != null ? event.getAssignedUsers().size() : 0);
            } catch (Exception e) {
                log.error("❌ Failed to parse AssessmentPublishedEvent from data: {}", cleanedValue, e);
                return;
            }

            // Validate event
            if (event.getAssignedUsers() == null || event.getAssignedUsers().isEmpty()) {
                log.warn("⚠️ No assigned users in assessment event, skipping");
                return;
            }

            // Prepare template data
            Map<String, Object> data = new HashMap<>();
            data.put("assessmentName", event.getAssessmentTitle());
            data.put("duration", event.getDuration());
            data.put("dueDate", event.getDueDate());

            log.info("Template data prepared: {}", data);

            // Send notification to each assigned user
            for (AssessmentPublishedEvent.UserInfo user : event.getAssignedUsers()) {
                try {
                    log.info("📤 Sending notification to user: {} (ID: {})",
                            user.getUsername(), user.getUserId());

                    // Add username to data
                    Map<String, Object> userData = new HashMap<>(data);
                    userData.put("username", user.getUsername());

                    notificationService.processNotification(
                            "assessment.published",
                            user.getUserId(),
                            user.getEmail(),
                            userData,
                            List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL));

                    log.info("✅ Notification sent successfully to user: {}", user.getUserId());
                } catch (Exception e) {
                    log.error("❌ Failed to send notification to user {}: {}",
                            user.getUserId(), e.getMessage(), e);
                }
            }

            log.info("🟢 Completed processing assessment.published event (legacy)");

        } catch (Exception e) {
            log.error("❌ Failed to handle assessment published event", e);
        }
    }

    /**
     * Reconstruct nested objects from Redis flattened keys
     * Example: "assignedUsers.[0].userId" -> assignedUsers: [{userId: ...}]
     */
    private Map<String, Object> reconstructNestedObjects(Map<String, Object> flatMap) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Map<Integer, Map<String, Object>>> arrays = new HashMap<>();

        for (Map.Entry<String, Object> entry : flatMap.entrySet()) {
            String key = entry.getKey();

            // Check if key contains array notation like "assignedUsers.[0].userId"
            if (key.contains(".[") && key.contains("].")) {
                // Parse: "assignedUsers.[0].userId" -> arrayName="assignedUsers", index=0,
                // field="userId"
                String[] parts = key.split("\\.");
                String arrayName = parts[0]; // "assignedUsers"
                String indexPart = parts[1]; // "[0]"
                String fieldName = parts[2]; // "userId"

                int index = Integer.parseInt(indexPart.substring(1, indexPart.length() - 1));

                arrays.computeIfAbsent(arrayName, k -> new HashMap<>())
                        .computeIfAbsent(index, k -> new HashMap<>())
                        .put(fieldName, entry.getValue());
            } else {
                // Regular field
                result.put(key, entry.getValue());
            }
        }

        // Convert arrays map back to List
        for (Map.Entry<String, Map<Integer, Map<String, Object>>> arrayEntry : arrays.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            Map<Integer, Map<String, Object>> indexedMap = arrayEntry.getValue();

            // Sort by index and add to list
            indexedMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> list.add(e.getValue()));

            result.put(arrayEntry.getKey(), list);
        }

        return result;
    }

    private void handleProctoringEvent(Map<Object, Object> value) {
        try {
            Map<String, Object> cleanedValue = cleanMap(value);
            ProctoringViolationEvent event = objectMapper.convertValue(cleanedValue, ProctoringViolationEvent.class);

            log.info("Processing proctoring.violation event for session: {}", event.getSessionId());

            Map<String, Object> data = new HashMap<>();
            data.put("username", event.getUsername());
            data.put("sessionId", event.getSessionId());
            data.put("violationType", event.getViolationType());
            data.put("timestamp",
                    event.getTimestamp() != null ? event.getTimestamp().toString() : Instant.now().toString());
            data.put("severity", event.getSeverity());

            if (event.getProctorIds() != null && !event.getProctorIds().isEmpty()) { // ← Added null check
                for (String proctorId : event.getProctorIds()) {
                    notificationService.processNotification(
                            "proctoring.violation",
                            proctorId,
                            null,
                            data,
                            List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL));
                }
            } else {
                log.warn("No proctor IDs found for proctoring violation event");
            }
        } catch (Exception e) {
            log.error("Failed to handle proctoring event: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean map by converting all keys to strings and filtering out unwanted fields
     */
    private Map<String, Object> cleanMap(Map<Object, Object> original) {
        Map<String, Object> cleaned = new HashMap<>();
        Base64.Decoder decoder = Base64.getDecoder();

        for (Map.Entry<Object, Object> entry : original.entrySet()) {
            String key = entry.getKey().toString();
            if (!key.equals("init") && !key.startsWith("_")) {
                Object rawValue = entry.getValue();

                // Try to decode as base64, fall back to original value
                try {
                    if (rawValue instanceof String) {
                        String decoded = new String(decoder.decode((String) rawValue), StandardCharsets.UTF_8);
                        cleaned.put(key, decoded);
                    } else {
                        cleaned.put(key, rawValue);
                    }
                } catch (IllegalArgumentException e) {
                    // Not base64 encoded, use as-is
                    cleaned.put(key, rawValue);
                }
            }
        }
        return cleaned;
    }
}