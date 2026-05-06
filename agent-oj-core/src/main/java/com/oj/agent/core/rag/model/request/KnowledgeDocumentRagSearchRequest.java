package com.oj.agent.core.rag.model.request;

import lombok.Data;

@Data
public class KnowledgeDocumentRagSearchRequest {

    private String query;

    private Integer topK = 5;
}
