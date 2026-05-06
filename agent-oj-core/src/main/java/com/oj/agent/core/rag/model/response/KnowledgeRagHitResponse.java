package com.oj.agent.core.rag.model.response;

import lombok.Data;

@Data
public class KnowledgeRagHitResponse {

    private Long docId;

    private String docTitle;

    private String convertedDocUrl;

    private Long segmentId;

    private Integer chunkOrder;

    private Double score;

    private String text;
}
