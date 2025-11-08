package com.example.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${app.redis.streams.user-events}")
    private String userEventsStream;

    @Value("${app.redis.streams.assessment-events}")
    private String assessmentEventsStream;

    @Value("${app.redis.streams.proctoring-events}")
    private String proctoringEventsStream;

    @Value("${app.redis.streams.notification-events}")
    private String notificationEventsStream;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        return new LettuceConnectionFactory(config);
    }

    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, Object>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainerOptions<String, ObjectRecord<String, Object>> options =
                StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        StreamMessageListenerContainer<String, ObjectRecord<String, Object>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.start();
        return container;
    }

    // Stream names as beans for easy injection
    @Bean
    public String userEventsStream() {
        return userEventsStream;
    }

    @Bean
    public String assessmentEventsStream() {
        return assessmentEventsStream;
    }

    @Bean
    public String proctoringEventsStream() {
        return proctoringEventsStream;
    }

    @Bean
    public String notificationEventsStream() {
        return notificationEventsStream;
    }

    // Channel topics for pub/sub (alternative to streams)
    @Bean
    public ChannelTopic userEventsTopic() {
        return new ChannelTopic(userEventsStream);
    }

    @Bean
    public ChannelTopic assessmentEventsTopic() {
        return new ChannelTopic(assessmentEventsStream);
    }

    @Bean
    public ChannelTopic proctoringEventsTopic() {
        return new ChannelTopic(proctoringEventsStream);
    }

    @Bean
    public ChannelTopic notificationEventsTopic() {
        return new ChannelTopic(notificationEventsStream);
    }
}