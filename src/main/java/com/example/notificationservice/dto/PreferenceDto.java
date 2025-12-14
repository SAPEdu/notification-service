package com.example.notificationservice.dto;

import com.example.notificationservice.enums.EmailFrequency;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceDto {

    private UUID id;

    @NotNull(message = "User ID is required")
    private Integer userId;

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
    private EmailFrequency emailFrequency = EmailFrequency.IMMEDIATE;

    /**
     * Per-notification-type settings
     * Structure: { "notification_type": { "enabled": bool, "emailEnabled": bool,
     * "pushEnabled": bool } }
     * Example types: "assessment_assigned", "assessment_reminders",
     * "grade_notifications", etc.
     */
    private Map<String, Map<String, Boolean>> notificationTypes;
}