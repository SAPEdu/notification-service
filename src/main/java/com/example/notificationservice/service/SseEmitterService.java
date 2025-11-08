package com.example.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseEmitterService {

    private final Map<String, SseEmitter> userEmitters = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> topicEmitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    private static final Long DEFAULT_TIMEOUT = 24 * 60 * 60 * 1000L; // 24 hours

    /**
     * Create SSE connection for a specific user
     */
    public SseEmitter createEmitterForUser(Integer userId) {
        String key = getUserKey(userId);

        // Remove existing emitter if present
        removeUserEmitter(key);

        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        // Set up callbacks
        emitter.onCompletion(() -> {
            log.debug("SSE connection completed for user: {}", userId);
            removeUserEmitter(key);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out for user: {}", userId);
            removeUserEmitter(key);
        });

        emitter.onError((ex) -> {
            log.error("SSE connection error for user {}: {}", userId, ex.getMessage());
            removeUserEmitter(key);
        });

        // Store emitter
        userEmitters.put(key, emitter);

        // Send initial connection event
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name("connect")
                    .data(Map.of(
                            "message", "Connected to notification service",
                            "userId", userId,
                            "timestamp", Instant.now()
                    ));
            emitter.send(event);
            log.info("SSE connection established for user: {}", userId);
        } catch (IOException e) {
            log.error("Failed to send initial SSE event to user {}: {}", userId, e.getMessage());
        }

        return emitter;
    }

    /**
     * Subscribe to a topic for broadcast messages
     */
    public SseEmitter subscribeToTopic(String topic, Integer userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        emitter.onCompletion(() -> {
            log.debug("SSE topic subscription completed for topic: {}, user: {}", topic, userId);
            removeTopicEmitter(topic, emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE topic subscription timed out for topic: {}, user: {}", topic, userId);
            removeTopicEmitter(topic, emitter);
        });

        emitter.onError((ex) -> {
            log.error("SSE topic subscription error for topic {}, user {}: {}", topic, userId, ex.getMessage());
            removeTopicEmitter(topic, emitter);
        });

        // Add to topic subscribers
        topicEmitters.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Send subscription confirmation
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name("subscribed")
                    .data(Map.of(
                            "topic", topic,
                            "message", "Subscribed to topic: " + topic,
                            "timestamp", Instant.now()
                    ));
            emitter.send(event);
            log.info("User {} subscribed to topic: {}", userId, topic);
        } catch (IOException e) {
            log.error("Failed to send subscription confirmation: {}", e.getMessage());
        }

        return emitter;
    }

    /**
     * Send notification to a specific user
     */
    public boolean sendToUser(Integer userId, String eventName, Object data) {
        String key = getUserKey(userId);
        SseEmitter emitter = userEmitters.get(key);

        if (emitter != null) {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name(eventName)
                        .data(data);

                emitter.send(event);
                log.debug("SSE notification sent to user {}: {}", userId, eventName);
                return true;
            } catch (IOException e) {
                log.error("Failed to send SSE notification to user {}: {}", userId, e.getMessage());
                removeUserEmitter(key);
                return false;
            }
        } else {
            log.warn("No active SSE connection for user: {}", userId);
            return false;
        }
    }

    /**
     * Send notification to a specific user with structured data
     */
    public boolean sendNotificationToUser(Integer userId, String type, String content) {
        Map<String, Object> notification = Map.of(
                "type", type,
                "content", content,
                "timestamp", Instant.now(),
                "id", System.currentTimeMillis()
        );

        return sendToUser(userId, "notification", notification);
    }

    /**
     * Broadcast to all subscribers of a topic
     */
    public void broadcastToTopic(String topic, String eventName, Object data) {
        List<SseEmitter> emitters = topicEmitters.get(topic);

        if (emitters != null && !emitters.isEmpty()) {
            List<SseEmitter> deadEmitters = new ArrayList<>();

            emitters.forEach(emitter -> {
                try {
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .id(String.valueOf(System.currentTimeMillis()))
                            .name(eventName)
                            .data(data);

                    emitter.send(event);
                } catch (IOException e) {
                    log.error("Failed to send broadcast to topic {}: {}", topic, e.getMessage());
                    deadEmitters.add(emitter);
                }
            });

            // Remove dead emitters
            emitters.removeAll(deadEmitters);

            log.debug("Broadcasted to {} subscribers on topic: {}", emitters.size(), topic);
        } else {
            log.debug("No subscribers for topic: {}", topic);
        }
    }

    /**
     * Broadcast to all connected users
     */
    public void broadcastToAll(String eventName, Object data) {
        List<String> deadConnections = new ArrayList<>();

        userEmitters.forEach((key, emitter) -> {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name(eventName)
                        .data(data);

                emitter.send(event);
            } catch (IOException e) {
                log.error("Failed to broadcast to user {}: {}", key, e.getMessage());
                deadConnections.add(key);
            }
        });

        // Remove dead connections
        deadConnections.forEach(this::removeUserEmitter);

        log.debug("Broadcasted to {} users", userEmitters.size());
    }

    /**
     * Send heartbeat to keep connections alive
     */
    @Scheduled(fixedDelay = 300000) // Every 30 seconds
    public void sendHeartbeat() {
        Map<String, Object> heartbeat = Map.of(
                "timestamp", Instant.now(),
                "type", "heartbeat"
        );

        // Send heartbeat to all user connections
        List<String> deadConnections = new ArrayList<>();

        userEmitters.forEach((key, emitter) -> {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .name("heartbeat")
                        .data(heartbeat)
                        .comment("keep-alive");

                emitter.send(event);
            } catch (IOException e) {
                log.debug("Heartbeat failed for {}, removing connection", key);
                deadConnections.add(key);
            }
        });

        deadConnections.forEach(this::removeUserEmitter);

        // Send heartbeat to topic subscribers
        topicEmitters.forEach((topic, emitters) -> {
            List<SseEmitter> deadEmitters = new ArrayList<>();

            emitters.forEach(emitter -> {
                try {
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .comment("keep-alive");
                    emitter.send(event);
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                }
            });

            emitters.removeAll(deadEmitters);
        });

        if (!userEmitters.isEmpty() || !topicEmitters.isEmpty()) {
            log.trace("Heartbeat sent to {} user connections and {} topics",
                    userEmitters.size(), topicEmitters.size());
        }
    }

    /**
     * Get active connection count
     */
    public int getActiveUserConnections() {
        return userEmitters.size();
    }

    /**
     * Get topic subscriber count
     */
    public int getTopicSubscriberCount(String topic) {
        List<SseEmitter> emitters = topicEmitters.get(topic);
        return emitters != null ? emitters.size() : 0;
    }

    /**
     * Check if user is connected
     */
    public boolean isUserConnected(Integer userId) {
        return userEmitters.containsKey(getUserKey(userId));
    }

    /**
     * Disconnect a user
     */
    public void disconnectUser(Integer userId) {
        String key = getUserKey(userId);
        SseEmitter emitter = userEmitters.get(key);

        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.error("Error completing SSE connection for user {}: {}", userId, e.getMessage());
            }
            removeUserEmitter(key);
            log.info("User {} disconnected from SSE", userId);
        }
    }

    private String getUserKey(Integer userId) {
        return "user_" + userId;
    }

    private void removeUserEmitter(String key) {
        userEmitters.remove(key);
    }

    private void removeTopicEmitter(String topic, SseEmitter emitter) {
        List<SseEmitter> emitters = topicEmitters.get(topic);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                topicEmitters.remove(topic);
            }
        }
    }
}