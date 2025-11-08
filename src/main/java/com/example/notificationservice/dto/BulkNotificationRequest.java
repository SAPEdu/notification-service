package com.example.notificationservice.dto;

import com.example.notificationservice.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkNotificationRequest {

    @NotEmpty(message = "User IDs list cannot be empty")
    private List<Integer> userIds;

    @NotBlank(message = "Notification type is required")
    private String type;

    @NotNull(message = "Channels list is required")
    private List<NotificationChannel> channels;

    /**
     * Template data that will be applied to all users
     * Can include common variables like {{assessmentName}}, {{dueDate}}, etc.
     */
    private Map<String, Object> commonData;

    /**
     * User-specific data: key is userId, value is map of variables for that user
     * Example: {123: {"username": "John", "score": 85}, 124: {"username": "Jane", "score": 92}}
     */
    private Map<Integer, Map<String, Object>> userSpecificData;
}