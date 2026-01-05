package com.example.notificationservice.event.inbound;

import com.example.notificationservice.event.BaseEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttemptStartedEvent extends BaseEvent {

    @JsonProperty("attempt_id")
    private String attemptId;

    @JsonProperty("assessment_id")
    private String assessmentId;

    @JsonProperty("assessment_title")
    private String assessmentTitle;

    @JsonProperty("group_id")
    private String groupId;

    @JsonProperty("student_id")
    private String studentId;

    @JsonProperty("started_at")
    private String startedAt;

    @JsonProperty("time_limit")
    private Integer timeLimit;

    @JsonProperty("creator_id")
    private String creatorId;
}
