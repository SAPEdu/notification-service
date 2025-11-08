package com.example.notificationservice.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("notificationTemplateEngine")
@Slf4j
public class TemplateEngine {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /**
     * Process a template string by replacing placeholders with actual values
     *
     * @param template The template string containing {{variable}} placeholders
     * @param data Map containing variable names and their values
     * @return Processed string with placeholders replaced
     */
    public String processTemplate(String template, Map<String, Object> data) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = data.get(variableName);

            if (value != null) {
                // Escape special characters in replacement string
                String replacement = Matcher.quoteReplacement(value.toString());
                matcher.appendReplacement(result, replacement);
            } else {
                // Keep the original placeholder if no value found
                log.warn("No value found for template variable: {}", variableName);
                matcher.appendReplacement(result, matcher.group(0));
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Validate that all required variables in a template have values in the data map
     *
     * @param template The template string
     * @param data The data map
     * @return true if all variables have values, false otherwise
     */
    public boolean validateTemplateData(String template, Map<String, Object> data) {
        if (template == null || template.isEmpty()) {
            return true;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (!data.containsKey(variableName) || data.get(variableName) == null) {
                log.error("Missing required template variable: {}", variableName);
                return false;
            }
        }

        return true;
    }
}