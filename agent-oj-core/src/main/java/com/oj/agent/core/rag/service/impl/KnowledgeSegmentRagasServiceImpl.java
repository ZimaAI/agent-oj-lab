package com.oj.agent.core.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentRagasMapper;
import com.oj.agent.core.rag.model.entity.KnowledgeSegmentRagas;
import com.oj.agent.core.rag.service.KnowledgeSegmentRagasService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class KnowledgeSegmentRagasServiceImpl
        extends ServiceImpl<KnowledgeSegmentRagasMapper, KnowledgeSegmentRagas>
        implements KnowledgeSegmentRagasService {

    @Override
    public List<KnowledgeSegmentRagas> listByQuestionAndDocument(Long questionId,
                                                                 Long documentId,
                                                                 List<Long> segmentIds,
                                                                 List<Long> ragasIds) {
        if (questionId == null || questionId <= 0 || documentId == null || documentId <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<KnowledgeSegmentRagas> wrapper = new LambdaQueryWrapper<KnowledgeSegmentRagas>()
                .eq(KnowledgeSegmentRagas::getQuestionId, questionId)
                .eq(KnowledgeSegmentRagas::getDocumentId, documentId)
                .eq(KnowledgeSegmentRagas::getIsDelete, 0)
                .orderByDesc(KnowledgeSegmentRagas::getId);
        if (!CollectionUtils.isEmpty(segmentIds)) {
            wrapper.in(KnowledgeSegmentRagas::getSegmentId, segmentIds);
        }
        if (!CollectionUtils.isEmpty(ragasIds)) {
            wrapper.in(KnowledgeSegmentRagas::getId, ragasIds);
        }
        return list(wrapper);
    }

    @Override
    public KnowledgeSegmentRagas getBySegmentId(Long questionId, Long documentId, Long segmentId) {
        if (questionId == null || questionId <= 0
                || documentId == null || documentId <= 0
                || segmentId == null || segmentId <= 0) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<KnowledgeSegmentRagas>()
                .eq(KnowledgeSegmentRagas::getQuestionId, questionId)
                .eq(KnowledgeSegmentRagas::getDocumentId, documentId)
                .eq(KnowledgeSegmentRagas::getSegmentId, segmentId)
                .eq(KnowledgeSegmentRagas::getIsDelete, 0)
                .last("limit 1"));
    }

    @Override
    public boolean softDeleteByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return true;
        }
        KnowledgeSegmentRagas updateEntity = new KnowledgeSegmentRagas();
        updateEntity.setIsDelete(1);
        return update(
                updateEntity,
                new LambdaQueryWrapper<KnowledgeSegmentRagas>()
                        .in(KnowledgeSegmentRagas::getId, ids.stream().filter(Objects::nonNull).toList())
                        .eq(KnowledgeSegmentRagas::getIsDelete, 0)
        );
    }
}
