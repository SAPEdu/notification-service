package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceDto {

    private UUID id;

    /**
     * User ID - populated from JWT token in controller, not from request body
     */
    private String userId;

    /**
     * Global toggle to enable/disable all notifications
     */
    @Builder.Default
    private Boolean notificationsEnabled = true;

    @Builder.Default
    private Boolean emailEnabled = true;

    @Builder.Default
    private Boolean pushEnabled = true;

    @Builder.Default
    private Boolean telegramEnabled = false;

    private String telegramChatId;

    private Instant telegramLinkedAt;

    /**
     * Per-notification-type settings
     * Structure: { "notification_type": { "enabled": bool, "emailEnabled": bool,
     * "pushEnabled": bool, "telegramEnabled": bool } }
     */
    private Map<String, Map<String, Boolean>> notificationTypes;
}