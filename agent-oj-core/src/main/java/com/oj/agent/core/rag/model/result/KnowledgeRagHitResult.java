package com.oj.agent.core.rag.model.result;

import lombok.Data;

@Data
public class KnowledgeRagHitResult {

    private Long docId;

    private String docTitle;

    private String convertedDocUrl;

    private Long segmentId;

    private Integer chunkOrder;

    private Double score;

    private String text;
}
