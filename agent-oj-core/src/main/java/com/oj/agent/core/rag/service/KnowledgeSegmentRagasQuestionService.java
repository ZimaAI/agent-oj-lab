package com.oj.agent.core.rag.service;

import com.oj.agent.core.rag.model.dto.SegmentRagasQaOutputDTO;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;

public interface KnowledgeSegmentRagasQuestionService {

    SegmentRagasQaOutputDTO generateRagasQA(KnowledgeSegment segment);
}
