package com.example.notificationservice.dto;

import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private UUID id;
    private Integer recipientId;
    private String recipientEmail;
    private String recipientPhone;
    private String type;
    private NotificationChannel channel;
    private String subject;
    private String content;
    private NotificationStatus status;
    private Instant sentAt;
    private Instant deliveredAt;
    private String errorMessage;
    private Integer retryCount;
}