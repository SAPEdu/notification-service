package com.example.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.username:}")
    private String redisUsername;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${spring.data.redis.ssl.verify-peer:true}")
    private boolean sslVerifyPeer;

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

        // Set username for Redis 6+ ACL
        if (redisUsername != null && !redisUsername.isEmpty()) {
            config.setUsername(redisUsername);
        }

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(RedisPassword.of(redisPassword));
        }

        // Build Lettuce client configuration
        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder = LettuceClientConfiguration
                .builder();

        if (sslEnabled) {
            log.info("Redis SSL/TLS is ENABLED - connecting to {}:{} with TLS, verify-peer: {}",
                    redisHost, redisPort, sslVerifyPeer);
            if (sslVerifyPeer) {
                // Use default SSL context with proper certificate verification
                clientConfigBuilder.useSsl();
            } else {
                // Disable peer verification (NOT recommended for production)
                log.warn("SSL peer verification is DISABLED - this is insecure!");
                clientConfigBuilder.useSsl().disablePeerVerification();
            }
        } else {
            log.info("Redis SSL/TLS is DISABLED - connecting to {}:{} without TLS", redisHost, redisPort);
        }

        LettuceClientConfiguration clientConfig = clientConfigBuilder.build();

        return new LettuceConnectionFactory(config, clientConfig);
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
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * RedisTemplate for Stream operations.
     * Uses StringRedisSerializer for hash values because Go producer sends plain
     * strings, not JSON.
     */
    @Bean(name = "redisStreamTemplate")
    public RedisTemplate<String, String> redisStreamTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
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