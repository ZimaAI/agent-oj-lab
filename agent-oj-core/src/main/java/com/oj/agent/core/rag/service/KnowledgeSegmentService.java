package com.oj.agent.core.rag.service;

import com.oj.agent.core.rag.model.query.KnowledgeSegmentListQuery;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentResult;

import java.util.List;

public interface KnowledgeSegmentService {

    List<KnowledgeSegmentResult> listSegments(KnowledgeSegmentListQuery query);
}
