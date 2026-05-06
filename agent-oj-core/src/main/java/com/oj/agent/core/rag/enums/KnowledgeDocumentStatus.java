package com.oj.agent.core.rag.enums;

public enum KnowledgeDocumentStatus {
    INIT,
    UPLOADED,
    CONVERTING,
    CONVERTED,
    CHUNKED,
    VECTOR_STORED,
    STORED,
    FAILED
}
