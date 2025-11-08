// ProctoringViolationEvent.java
package com.example.notificationservice.event.inbound;

import com.example.notificationservice.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProctoringViolationEvent extends BaseEvent {
    private Integer userId;
    private String username;
    private String sessionId;
    private String violationType;
    private String severity;
    private List<Integer> proctorIds;
}
