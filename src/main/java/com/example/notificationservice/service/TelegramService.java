package com.example.notificationservice.service;

import com.example.notificationservice.telegram.TelegramBotHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service for sending notifications via Telegram Bot.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true", matchIfMissing = false)
public class TelegramService {

    private TelegramBotHandler telegramBotHandler;

    @Autowired(required = false)
    public void setTelegramBotHandler(TelegramBotHandler telegramBotHandler) {
        this.telegramBotHandler = telegramBotHandler;
    }

    /**
     * Send a text message to a Telegram chat.
     *
     * @param chatId  The Telegram chat ID
     * @param message The message text
     * @return CompletableFuture with success status
     */
    @Async("telegramExecutor")
    public CompletableFuture<Boolean> sendMessage(String chatId, String message) {
        if (telegramBotHandler == null) {
            log.warn("TelegramBotHandler not available, cannot send message");
            return CompletableFuture.completedFuture(false);
        }

        try {
            telegramBotHandler.sendNotification(chatId, message);
            log.info("✅ Telegram message sent to chatId: {}", chatId);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("❌ Failed to send Telegram message to {}: {}", chatId, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Check if Telegram service is available.
     */
    public boolean isAvailable() {
        return telegramBotHandler != null;
    }
}
