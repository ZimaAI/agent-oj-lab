package com.oj.agent.core.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentHitkTaskMapper;
import com.oj.agent.core.rag.model.entity.KnowledgeSegmentHitkTask;
import com.oj.agent.core.rag.service.KnowledgeSegmentHitkTaskService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class KnowledgeSegmentHitkTaskServiceImpl
        extends ServiceImpl<KnowledgeSegmentHitkTaskMapper, KnowledgeSegmentHitkTask>
        implements KnowledgeSegmentHitkTaskService {

    @Override
    public List<KnowledgeSegmentHitkTask> listTasks(Long questionId, Long documentId) {
        if (questionId == null || questionId <= 0 || documentId == null || documentId <= 0) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<KnowledgeSegmentHitkTask>()
                .eq(KnowledgeSegmentHitkTask::getQuestionId, questionId)
                .eq(KnowledgeSegmentHitkTask::getDocumentId, documentId)
                .eq(KnowledgeSegmentHitkTask::getIsDelete, 0)
                .orderByDesc(KnowledgeSegmentHitkTask::getId));
    }
}
