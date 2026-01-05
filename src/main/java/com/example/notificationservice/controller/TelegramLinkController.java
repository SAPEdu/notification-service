package com.example.notificationservice.controller;

import com.example.notificationservice.dto.TelegramLinkResponse;
import com.example.notificationservice.service.TelegramLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for Telegram account linking operations.
 */
@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true", matchIfMissing = false)
public class TelegramLinkController {

    private final TelegramLinkService linkService;

    /**
     * Generate a Telegram deep link and QR code for the authenticated user.
     * The link expires after the configured TTL (default: 5 minutes).
     *
     * @param jwt The authenticated user's JWT
     * @return TelegramLinkResponse with deep link and QR code
     */
    @GetMapping("/link")
    public ResponseEntity<TelegramLinkResponse> generateLink(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        log.info("Generating Telegram link for user: {}", userId);

        TelegramLinkResponse response = linkService.generateLink(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Unlink Telegram from the authenticated user's account.
     *
     * @param jwt The authenticated user's JWT
     * @return 204 No Content on success
     */
    @DeleteMapping("/link")
    public ResponseEntity<Void> unlinkTelegram(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        log.info("Unlinking Telegram for user: {}", userId);

        linkService.unlinkUser(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Check if the authenticated user has Telegram linked.
     *
     * @param jwt The authenticated user's JWT
     * @return Status information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        boolean isLinked = linkService.isLinked(userId);
        String chatId = linkService.getChatIdForUser(userId);

        return ResponseEntity.ok(Map.of(
                "linked", isLinked,
                "chatId", chatId != null ? chatId : ""));
    }
}
