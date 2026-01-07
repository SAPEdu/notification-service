package com.example.notificationservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SSE Configuration
 * Note: CORS is handled globally by SecurityConfig, no need to configure here
 */
@Configuration
@EnableScheduling
public class SseConfig {
    // CORS configuration removed - handled by
    // SecurityConfig.corsConfigurationSource()
    // WebMvcConfigurer.addCorsMappings was causing duplicate CORS headers
}