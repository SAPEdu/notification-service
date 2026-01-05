package com.example.notificationservice.config;

import com.example.notificationservice.telegram.TelegramBotHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.Executor;

/**
 * Configuration for Telegram Bot integration.
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true", matchIfMissing = false)
public class TelegramBotConfig {

    @Autowired(required = false)
    private TelegramBotHandler telegramBotHandler;

    /**
     * Register the Telegram bot on application startup.
     */
    @PostConstruct
    public void registerBot() {
        if (telegramBotHandler == null) {
            log.warn("TelegramBotHandler not available, skipping bot registration");
            return;
        }

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBotHandler);
            log.info("✅ Telegram Bot registered successfully: {}", telegramBotHandler.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("❌ Failed to register Telegram Bot: {}", e.getMessage(), e);
        }
    }

    /**
     * Executor for async Telegram message sending.
     */
    @Bean(name = "telegramExecutor")
    public Executor telegramExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("telegram-");
        executor.initialize();
        log.info("Telegram executor initialized");
        return executor;
    }
}
