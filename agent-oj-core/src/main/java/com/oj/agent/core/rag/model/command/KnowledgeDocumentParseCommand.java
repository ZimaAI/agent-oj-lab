package com.oj.agent.core.rag.model.command;

import lombok.Data;

@Data
public class KnowledgeDocumentParseCommand {

    private String fileUrl;

    private String docTitle;

    private String description;

    private String accessibleBy;

    private String knowledgeBaseType;
}
