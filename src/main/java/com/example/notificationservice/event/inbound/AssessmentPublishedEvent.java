// AssessmentPublishedEvent.java
package com.example.notificationservice.event.inbound;

import com.example.notificationservice.event.BaseEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssessmentPublishedEvent extends BaseEvent {
    private String assessmentId;
    private String assessmentName;
    private Integer duration;
    private String dueDate;
    private List<UserInfo> assignedUsers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String userId;
        private String username;
        private String email;
    }
}
