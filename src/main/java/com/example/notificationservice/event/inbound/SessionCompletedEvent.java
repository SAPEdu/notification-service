package com.example.notificationservice.event.inbound;

import com.example.notificationservice.event.BaseEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionCompletedEvent extends BaseEvent {
    private String userId;
    private String username;
    private String email;
    private String sessionId;
    private String assessmentName;
    private String completionTime;
    private Double score;
    private String status;
}
