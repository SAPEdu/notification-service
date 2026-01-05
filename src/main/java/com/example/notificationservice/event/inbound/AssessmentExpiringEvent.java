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
public class AssessmentExpiringEvent extends BaseEvent {

    @JsonProperty("assessment_id")
    private String assessmentId;

    @JsonProperty("assessment_title")
    private String assessmentTitle;

    @JsonProperty("group_id")
    private String groupId;

    @JsonProperty("group_name")
    private String groupName;

    @JsonProperty("hours_remaining")
    private Integer hoursRemaining;

    @JsonProperty("student_ids")
    private List<String> studentIds;

    @JsonProperty("due_date")
    private String dueDate;
}
