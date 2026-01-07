package com.example.notificationservice.service;

import com.example.notificationservice.dto.TelegramLinkResponse;
import com.example.notificationservice.entity.NotificationPreference;
import com.example.notificationservice.repository.NotificationPreferenceRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing Telegram account linking with secure token verification.
 * Tokens are stored in Redis with TTL for security.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true", matchIfMissing = false)
public class TelegramLinkService {

    private final StringRedisTemplate redisTemplate;
    private final NotificationPreferenceRepository preferenceRepository;

    @Value("${app.telegram.bot-username}")
    private String botUsername;

    @Value("${app.telegram.link-token-ttl-seconds:300}")
    private long tokenTtlSeconds;

    private static final String TOKEN_PREFIX = "telegram:link:";
    private static final int QR_CODE_SIZE = 300;

    /**
     * Generate a deep link and QR code for user to link their Telegram account.
     *
     * @param userId The user ID to generate link for
     * @return TelegramLinkResponse containing deep link and QR code
     */
    public TelegramLinkResponse generateLink(String userId) {
        // Generate secure random token
        String token = generateSecureToken();
        String redisKey = TOKEN_PREFIX + token;

        // Store token -> userId mapping with TTL
        redisTemplate.opsForValue().set(redisKey, userId, Duration.ofSeconds(tokenTtlSeconds));
        log.info("Generated Telegram link token for user: {}, expires in {}s", userId, tokenTtlSeconds);

        // Create deep link
        String deepLink = String.format("https://t.me/%s?start=%s", botUsername, token);

        // Generate QR code
        String qrCodeBase64 = generateQrCode(deepLink);

        return TelegramLinkResponse.builder()
                .deepLink(deepLink)
                .qrCode(qrCodeBase64)
                .expiresInSeconds(tokenTtlSeconds)
                .build();
    }

    /**
     * Verify token and link Telegram chat to user account.
     *
     * @param token  The token from /start command
     * @param chatId The Telegram chat ID
     * @return true if successfully linked, false if token invalid/expired
     */
    @Transactional
    public boolean verifyAndLink(String token, String chatId) {
        String redisKey = TOKEN_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(redisKey);

        if (userId == null) {
            log.warn("Invalid or expired Telegram link token: {}", token);
            return false;
        }

        // Link chatId to user's preferences
        Optional<NotificationPreference> prefOpt = preferenceRepository.findByUserId(userId);

        if (prefOpt.isPresent()) {
            NotificationPreference pref = prefOpt.get();
            pref.setTelegramChatId(chatId);
            pref.setTelegramEnabled(true);
            pref.setTelegramLinkedAt(Instant.now());
            preferenceRepository.save(pref);
        } else {
            // Create new preference if not exists
            NotificationPreference pref = NotificationPreference.builder()
                    .userId(userId)
                    .telegramChatId(chatId)
                    .telegramEnabled(true)
                    .telegramLinkedAt(Instant.now())
                    .build();
            preferenceRepository.save(pref);
        }

        // Delete used token (one-time use)
        redisTemplate.delete(redisKey);
        log.info("Successfully linked Telegram chatId {} to user {}", chatId, userId);

        return true;
    }

    /**
     * Unlink Telegram from user account.
     *
     * @param userId The user ID to unlink
     * @return true if unlinked, false if no Telegram was linked
     */
    @Transactional
    public boolean unlinkUser(String userId) {
        return preferenceRepository.findByUserId(userId)
                .map(pref -> {
                    pref.setTelegramChatId(null);
                    pref.setTelegramEnabled(false);
                    pref.setTelegramLinkedAt(null);
                    preferenceRepository.save(pref);
                    log.info("Unlinked Telegram for user: {}", userId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Get Telegram chat ID for a user.
     *
     * @param userId The user ID
     * @return Chat ID if linked and enabled, null otherwise
     */
    public String getChatIdForUser(String userId) {
        return preferenceRepository.findByUserId(userId)
                .filter(pref -> Boolean.TRUE.equals(pref.getTelegramEnabled()))
                .map(NotificationPreference::getTelegramChatId)
                .orElse(null);
    }

    /**
     * Check if user has Telegram linked.
     *
     * @param userId The user ID
     * @return true if Telegram is linked and enabled
     */
    public boolean isLinked(String userId) {
        return preferenceRepository.findByUserId(userId)
                .map(pref -> Boolean.TRUE.equals(pref.getTelegramEnabled())
                        && pref.getTelegramChatId() != null)
                .orElse(false);
    }

    private String generateSecureToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateQrCode(String content) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.MARGIN, 1,
                    EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE,
                    QR_CODE_SIZE, QR_CODE_SIZE, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code: {}", e.getMessage());
            return null;
        }
    }
}
