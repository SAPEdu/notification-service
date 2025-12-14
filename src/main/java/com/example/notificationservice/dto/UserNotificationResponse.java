package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for user-facing notification responses (notification inbox)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationResponse {

    private UUID id;
    private String type;
    private String subject;
    private String content;
    private boolean isRead;
    private Instant createdAt;
}
