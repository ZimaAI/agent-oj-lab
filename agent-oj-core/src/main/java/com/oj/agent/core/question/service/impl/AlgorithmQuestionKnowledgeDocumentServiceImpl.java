package com.oj.agent.core.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.question.mapper.AlgorithmQuestionKnowledgeDocumentMapper;
import com.oj.agent.core.question.model.entity.AlgorithmQuestionKnowledgeDocument;
import com.oj.agent.core.question.service.AlgorithmQuestionKnowledgeDocumentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Service
public class AlgorithmQuestionKnowledgeDocumentServiceImpl
        extends ServiceImpl<AlgorithmQuestionKnowledgeDocumentMapper, AlgorithmQuestionKnowledgeDocument>
        implements AlgorithmQuestionKnowledgeDocumentService {

    @Override
    public void bind(Long questionId, Long docId) {
        validateRelationIds(questionId, docId);
        Long existing = baseMapper.selectCount(new LambdaQueryWrapper<AlgorithmQuestionKnowledgeDocument>()
                .eq(AlgorithmQuestionKnowledgeDocument::getQuestionId, questionId)
                .eq(AlgorithmQuestionKnowledgeDocument::getDocId, docId)
                .eq(AlgorithmQuestionKnowledgeDocument::getIsDelete, 0));
        if (existing != null && existing > 0) {
            return;
        }

        AlgorithmQuestionKnowledgeDocument relation = new AlgorithmQuestionKnowledgeDocument();
        relation.setQuestionId(questionId);
        relation.setDocId(docId);
        relation.setIsDelete(0);
        try {
            if (!save(relation)) {
                throw new IllegalStateException("Bind question and document failed");
            }
        } catch (DuplicateKeyException ignored) {
            // Ignore duplicate insert when concurrent requests race on the same relation.
        }
    }

    @Override
    public List<AlgorithmQuestionKnowledgeDocument> listByQuestionId(Long questionId) {
        if (questionId == null || questionId <= 0) {
            return Collections.emptyList();
        }
        List<AlgorithmQuestionKnowledgeDocument> relations = list(new LambdaQueryWrapper<AlgorithmQuestionKnowledgeDocument>()
                .eq(AlgorithmQuestionKnowledgeDocument::getQuestionId, questionId)
                .eq(AlgorithmQuestionKnowledgeDocument::getIsDelete, 0)
                .orderByDesc(AlgorithmQuestionKnowledgeDocument::getId));
        if (CollectionUtils.isEmpty(relations)) {
            return Collections.emptyList();
        }
        return relations;
    }

    private void validateRelationIds(Long questionId, Long docId) {
        if (questionId == null || questionId <= 0) {
            throw new IllegalArgumentException("questionId must be a positive number");
        }
        if (docId == null || docId <= 0) {
            throw new IllegalArgumentException("docId must be a positive number");
        }
    }
}
