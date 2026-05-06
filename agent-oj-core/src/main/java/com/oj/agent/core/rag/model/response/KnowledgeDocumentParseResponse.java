package com.oj.agent.core.rag.model.response;

import lombok.Data;

@Data
public class KnowledgeDocumentParseResponse {

    private Long docId;

    private String docTitle;

    private String status;

    private String docUrl;

    private String convertedDocUrl;
}
