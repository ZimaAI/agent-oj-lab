package com.oj.agent.core.rag.model.result;

import lombok.Data;

@Data
public class KnowledgeDocumentParseResult {

    private Long docId;

    private String docTitle;

    private String status;

    private String docUrl;

    private String convertedDocUrl;
}
