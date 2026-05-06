package com.oj.agent.core.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oj.agent.core.rag.model.entity.KnowledgeSegmentRagas;

import java.util.List;

public interface KnowledgeSegmentRagasService extends IService<KnowledgeSegmentRagas> {

    List<KnowledgeSegmentRagas> listByQuestionAndDocument(Long questionId,
                                                          Long documentId,
                                                          List<Long> segmentIds,
                                                          List<Long> ragasIds);

    KnowledgeSegmentRagas getBySegmentId(Long questionId, Long documentId, Long segmentId);

    boolean softDeleteByIds(List<Long> ids);
}
