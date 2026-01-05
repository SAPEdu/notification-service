package com.example.notificationservice.event.inbound;

import com.example.notificationservice.event.BaseEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("assessment_id")
    private String assessmentId;

    @JsonProperty("assessment_title")
    private String assessmentTitle;

    @JsonProperty("group_id")
    private String groupId;

    @JsonProperty("group_name")
    private String groupName;

    @JsonProperty("due_date")
    private String dueDate;

    @JsonProperty("duration")
    private Integer duration;

    @JsonProperty("student_ids")
    private List<String> studentIds;

    @JsonProperty("creator_id")
    private String creatorId;

    // Legacy support for old format (assignedUsers)
    private String assessmentName; // Alias for assessmentTitle
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

    /**
     * Get assessment title with fallback to legacy field
     */
    public String getAssessmentTitle() {
        return assessmentTitle != null ? assessmentTitle : assessmentName;
    }
}
