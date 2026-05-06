package com.oj.agent.core.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oj.agent.core.rag.model.entity.KnowledgeSegmentHitkTask;

import java.util.List;

public interface KnowledgeSegmentHitkTaskService extends IService<KnowledgeSegmentHitkTask> {

    List<KnowledgeSegmentHitkTask> listTasks(Long questionId, Long documentId);
}
