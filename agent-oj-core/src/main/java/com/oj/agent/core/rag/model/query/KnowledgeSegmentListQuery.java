package com.oj.agent.core.rag.model.query;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeSegmentListQuery {

    private List<Long> segmentIds;

    private List<String> chunkIds;
}
