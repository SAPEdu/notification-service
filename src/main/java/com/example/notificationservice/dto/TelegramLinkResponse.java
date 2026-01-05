package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramLinkResponse {

    /**
     * Telegram deep link: https://t.me/bot_username?start=token
     */
    private String deepLink;

    /**
     * QR code as Base64-encoded PNG image
     */
    private String qrCode;

    /**
     * Token expiration time in seconds
     */
    private long expiresInSeconds;
}
