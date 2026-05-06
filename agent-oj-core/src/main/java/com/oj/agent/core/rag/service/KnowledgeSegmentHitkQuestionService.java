package com.oj.agent.core.rag.service;


import com.oj.agent.core.rag.model.entity.KnowledgeSegment;

public interface KnowledgeSegmentHitkQuestionService {

    String generateHitkQuestion(KnowledgeSegment segment);
}
