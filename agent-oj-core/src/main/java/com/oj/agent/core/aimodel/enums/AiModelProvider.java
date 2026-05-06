package com.oj.agent.core.aimodel.enums;

public enum AiModelProvider {

    OPENAI,
    OPENAI_COMPATIBLE,
    DASHSCOPE;

    public static AiModelProvider fromCode(String code) {
        for (AiModelProvider value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported ai model provider: " + code);
    }
}
