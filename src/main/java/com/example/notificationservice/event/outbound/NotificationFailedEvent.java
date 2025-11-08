package com.example.notificationservice.event.outbound;

import com.example.notificationservice.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotificationFailedEvent extends BaseEvent {
    private UUID notificationId;
    private Integer recipientId;
    private String channel;
    private String errorMessage;
    private Integer retryCount;
    private Boolean willRetry;
}