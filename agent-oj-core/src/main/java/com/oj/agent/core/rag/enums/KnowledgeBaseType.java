package com.oj.agent.core.rag.enums;

public enum KnowledgeBaseType {
    DOCUMENT_SEARCH,
    DATA_QUERY;

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return DOCUMENT_SEARCH.name();
        }
        for (KnowledgeBaseType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type.name();
            }
        }
        return DOCUMENT_SEARCH.name();
    }
}
