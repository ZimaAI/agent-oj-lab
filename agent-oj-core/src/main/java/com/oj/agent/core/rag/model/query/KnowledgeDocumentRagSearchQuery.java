package com.oj.agent.core.rag.model.query;

import lombok.Data;

@Data
public class KnowledgeDocumentRagSearchQuery {

    private String query;

    private Integer topK = 5;
}
