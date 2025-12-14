package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for per-notification-type settings
 * Used in user preferences to control Email/Push per notification type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTypeSettingDto {

    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    private Boolean emailEnabled = true;

    @Builder.Default
    private Boolean pushEnabled = true;
}
