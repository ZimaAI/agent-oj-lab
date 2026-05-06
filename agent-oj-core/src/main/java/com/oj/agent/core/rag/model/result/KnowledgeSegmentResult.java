package com.oj.agent.core.rag.model.result;

import lombok.Data;

@Data
public class KnowledgeSegmentResult {

    private Long id;

    private String text;

    private String chunkId;

    private String parentChunkId;

    private Long documentId;

    private Integer chunkOrder;
}
