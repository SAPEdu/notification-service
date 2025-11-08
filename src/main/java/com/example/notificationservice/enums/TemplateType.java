package com.example.notificationservice.enums;

public enum TemplateType {
    EMAIL("email"),
    SSE("sse"),
    PUSH("push");

    private final String value;

    TemplateType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TemplateType fromString(String value) {
        for (TemplateType type : TemplateType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid template type: " + value);
    }
}