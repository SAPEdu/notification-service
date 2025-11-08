package com.example.notificationservice.dto;

import com.example.notificationservice.enums.TemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDto {

    private UUID id;

    @NotBlank(message = "Template name is required")
    @Size(max = 100, message = "Template name must be less than 100 characters")
    private String name;

    @NotNull(message = "Template type is required")
    private TemplateType type;

    @Size(max = 255, message = "Subject must be less than 255 characters")
    private String subject;

    @NotBlank(message = "Template body is required")
    private String body;

    /**
     * Map of variable names and their descriptions/types
     * Example: {"username": "string", "email": "string", "score": "number"}
     */
    private Map<String, Object> variables;
}