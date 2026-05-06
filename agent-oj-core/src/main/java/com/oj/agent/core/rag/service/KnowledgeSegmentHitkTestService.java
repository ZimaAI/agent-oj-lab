package com.oj.agent.core.rag.service;


import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.rag.model.entity.KnowledgeSegmentHitkTask;

import java.util.List;

public interface KnowledgeSegmentHitkTestService {

    KnowledgeSegmentHitkTask createTask(Long questionId, Long documentId, List<KnowledgeSegment> segments);

    List<KnowledgeSegmentHitkTask> listTasks(Long questionId, Long documentId);
}
