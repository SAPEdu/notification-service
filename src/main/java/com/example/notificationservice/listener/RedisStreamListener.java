package com.example.notificationservice.listener;

import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.event.inbound.AssessmentPublishedEvent;
import com.example.notificationservice.event.inbound.ProctoringViolationEvent;
import com.example.notificationservice.event.inbound.SessionCompletedEvent;
import com.example.notificationservice.event.inbound.UserRegisteredEvent;
import com.example.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisStreamListener implements StreamListener<String, ObjectRecord<String, Object>> {

    private final NotificationService notificationService;
    private final StreamMessageListenerContainer<String, ObjectRecord<String, Object>> listenerContainer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.streams.user-events}")
    private String userEventsStream;

    @Value("${app.redis.streams.assessment-events}")
    private String assessmentEventsStream;

    @Value("${app.redis.streams.proctoring-events}")
    private String proctoringEventsStream;

    @Value("${app.redis.consumer.group-id}")
    private String consumerGroup;

    @Value("${app.redis.consumer.name}")
    private String consumerName;

    @PostConstruct
    public void subscribeToStreams() throws InterruptedException {
        // Create consumer groups if they don't exist
        createConsumerGroupIfNotExists(userEventsStream);
        createConsumerGroupIfNotExists(assessmentEventsStream);
        createConsumerGroupIfNotExists(proctoringEventsStream);

        // Subscribe to streams
        subscribeToStream(userEventsStream);
        subscribeToStream(assessmentEventsStream);
        subscribeToStream(proctoringEventsStream);

        log.info("Subscribed to Redis Streams: {}, {}, {}",
                userEventsStream, assessmentEventsStream, proctoringEventsStream);
    }

    private void createConsumerGroupIfNotExists(String streamKey) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, consumerGroup);
            log.info("Created consumer group '{}' for stream '{}'", consumerGroup, streamKey);
        } catch (Exception e) {
            // Group likely already exists, which is fine
            log.debug("Consumer group '{}' may already exist for stream '{}': {}",
                    consumerGroup, streamKey, e.getMessage());
        }
    }

    private void subscribeToStream(String streamKey) throws InterruptedException {
        Subscription subscription = listenerContainer.receive(
                Consumer.from(consumerGroup, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this
        );
        subscription.await(java.time.Duration.ofSeconds(2));
    }

    @Override
    public void onMessage(ObjectRecord<String, Object> message) {
        String streamKey = message.getStream();
        Object value = message.getValue();

        try {
            log.debug("Received message from stream '{}': {}", streamKey, value);

            // Route to appropriate handler based on stream
            if (streamKey.equals(userEventsStream)) {
                handleUserEvent(value);
            } else if (streamKey.equals(assessmentEventsStream)) {
                handleAssessmentEvent(value);
            } else if (streamKey.equals(proctoringEventsStream)) {
                handleProctoringEvent(value);
            }

            // Acknowledge the message
            redisTemplate.opsForStream().acknowledge(consumerGroup, message);

        } catch (Exception e) {
            log.error("Error processing message from stream '{}': {}", streamKey, e.getMessage(), e);
            // In production, you might want to move failed messages to a dead-letter queue
        }
    }

    private void handleUserEvent(Object value) {
        try {
            // Convert to UserRegisteredEvent
            UserRegisteredEvent event = objectMapper.convertValue(value, UserRegisteredEvent.class);

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
                    List.of(NotificationChannel.EMAIL)
            );
        } catch (Exception e) {
            log.error("Failed to handle user event: {}", e.getMessage(), e);
        }
    }

    private void handleAssessmentEvent(Object value) {
        try {
            Map<String, Object> eventMap = objectMapper.convertValue(value, Map.class);

            // Check if it's a SessionCompletedEvent or AssessmentPublishedEvent
            if (eventMap.containsKey("sessionId")) {
                handleSessionCompleted(value);
            } else if (eventMap.containsKey("assignedUsers")) {
                handleAssessmentPublished(value);
            }
        } catch (Exception e) {
            log.error("Failed to handle assessment event: {}", e.getMessage(), e);
        }
    }

    private void handleSessionCompleted(Object value) {
        try {
            SessionCompletedEvent event = objectMapper.convertValue(value, SessionCompletedEvent.class);

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
                    List.of(NotificationChannel.EMAIL)
            );
        } catch (Exception e) {
            log.error("Failed to handle session completed event: {}", e.getMessage(), e);
        }
    }

    private void handleAssessmentPublished(Object value) {
        try {
            AssessmentPublishedEvent event = objectMapper.convertValue(value, AssessmentPublishedEvent.class);

            log.info("Processing assessment.published event for assessment: {}", event.getAssessmentId());

            Map<String, Object> data = new HashMap<>();
            data.put("assessmentName", event.getAssessmentName());
            data.put("duration", event.getDuration());
            data.put("dueDate", event.getDueDate());

            // Send notification to all assigned users
            for (AssessmentPublishedEvent.UserInfo user : event.getAssignedUsers()) {
                data.put("username", user.getUsername());

                notificationService.processNotification(
                        "assessment.published",
                        user.getUserId(),
                        user.getEmail(),
                        data,
                        List.of(NotificationChannel.SSE, NotificationChannel.EMAIL)
                );
            }
        } catch (Exception e) {
            log.error("Failed to handle assessment published event: {}", e.getMessage(), e);
        }
    }

    private void handleProctoringEvent(Object value) {
        try {
            ProctoringViolationEvent event = objectMapper.convertValue(value, ProctoringViolationEvent.class);

            log.info("Processing proctoring.violation event for session: {}", event.getSessionId());

            Map<String, Object> data = new HashMap<>();
            data.put("username", event.getUsername());
            data.put("sessionId", event.getSessionId());
            data.put("violationType", event.getViolationType());
            data.put("timestamp", event.getTimestamp().toString());
            data.put("severity", event.getSeverity());

            // Notify proctors via SSE and Email
            for (Integer proctorId : event.getProctorIds()) {
                notificationService.processNotification(
                        "proctoring.violation",
                        proctorId,
                        null,
                        data,
                        List.of(NotificationChannel.SSE, NotificationChannel.EMAIL)
                );
            }
        } catch (Exception e) {
            log.error("Failed to handle proctoring event: {}", e.getMessage(), e);
        }
    }
}