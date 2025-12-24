package com.example.notificationservice.event.outbound;

import com.example.notificationservice.event.BaseEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationSentEvent extends BaseEvent {
    private UUID notificationId;
    private String recipientId;
    private String channel;
    private String type;
    private String status;
    private String deliveryTime;
}