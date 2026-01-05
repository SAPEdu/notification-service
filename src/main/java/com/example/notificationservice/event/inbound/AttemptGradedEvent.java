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
public class AttemptGradedEvent extends BaseEvent {

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

    @JsonProperty("graded_at")
    private String gradedAt;

    @JsonProperty("score")
    private Double score;

    @JsonProperty("max_score")
    private Double maxScore;

    @JsonProperty("percentage")
    private Double percentage;

    @JsonProperty("passed")
    private Boolean passed;

    @JsonProperty("grader_id")
    private String graderId;
}
