// SessionCompletedEvent.java
package com.example.notificationservice.event.inbound;

import com.example.notificationservice.event.BaseEvent;
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
public class SessionCompletedEvent extends BaseEvent {
    private Integer userId;
    private String username;
    private String email;
    private String sessionId;
    private String assessmentName;
    private String completionTime;
    private Double score;
    private String status;
}
