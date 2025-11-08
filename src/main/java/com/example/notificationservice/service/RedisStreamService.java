package com.example.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for publishing events to Redis Streams
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisStreamService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publish an event to a Redis Stream
     *
     * @param streamName The name of the stream
     * @param event The event object to publish
     * @return The message ID assigned by Redis
     */
    public String publish(String streamName, Object event) {
        try {
            ObjectRecord<String, Object> record = StreamRecords.newRecord()
                    .ofObject(event)
                    .withStreamKey(streamName);

            var recordId = redisTemplate.opsForStream().add(record);

            log.debug("Published event to stream {}: {} (ID: {})",
                    streamName, event.getClass().getSimpleName(), recordId);

            return recordId.getValue();
        } catch (Exception e) {
            log.error("Failed to publish event to stream {}: {}", streamName, e.getMessage(), e);
            throw new RuntimeException("Failed to publish event to Redis Stream", e);
        }
    }

    /**
     * Publish an event to a Redis Stream with a specific key
     *
     * @param streamName The name of the stream
     * @param key The key for the event (for partitioning)
     * @param event The event object to publish
     * @return The message ID assigned by Redis
     */
    public String publishWithKey(String streamName, String key, Object event) {
        try {
            ObjectRecord<String, Object> record = StreamRecords.newRecord()
                    .ofObject(event)
                    .withStreamKey(streamName);

            var recordId = redisTemplate.opsForStream().add(record);

            log.debug("Published event to stream {} with key {}: {} (ID: {})",
                    streamName, key, event.getClass().getSimpleName(), recordId);

            return recordId.getValue();
        } catch (Exception e) {
            log.error("Failed to publish event to stream {} with key {}: {}",
                    streamName, key, e.getMessage(), e);
            throw new RuntimeException("Failed to publish event to Redis Stream", e);
        }
    }
}