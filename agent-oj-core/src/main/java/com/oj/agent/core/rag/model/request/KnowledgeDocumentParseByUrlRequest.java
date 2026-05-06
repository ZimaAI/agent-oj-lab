package com.oj.agent.core.rag.model.request;

import lombok.Data;

@Data
public class KnowledgeDocumentParseByUrlRequest {

    private String fileUrl;

    private String docTitle;

    private String description;

    private String accessibleBy;

    private String knowledgeBaseType;
}
