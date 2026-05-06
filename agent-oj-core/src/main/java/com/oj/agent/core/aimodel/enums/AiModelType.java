package com.oj.agent.core.aimodel.enums;

public enum AiModelType {

    CHAT,
    EMBEDDING;

    public static AiModelType fromCode(String code) {
        for (AiModelType value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported ai model type: " + code);
    }
}
