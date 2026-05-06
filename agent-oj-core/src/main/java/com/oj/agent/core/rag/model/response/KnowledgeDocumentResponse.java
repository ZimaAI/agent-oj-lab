package com.oj.agent.core.rag.model.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class KnowledgeDocumentResponse {

    private Long docId;

    private String docTitle;

    private String uploadUser;

    private String docUrl;

    private String convertedDocUrl;

    private LocalDate expireDate;

    private String status;

    private String accessibleBy;

    private String description;

    private String knowledgeBaseType;

    private String extension;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
