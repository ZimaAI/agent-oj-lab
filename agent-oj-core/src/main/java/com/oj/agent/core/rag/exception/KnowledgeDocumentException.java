package com.oj.agent.core.rag.exception;

public class KnowledgeDocumentException extends RuntimeException {

    public KnowledgeDocumentException(String message) {
        super(message);
    }

    public KnowledgeDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
