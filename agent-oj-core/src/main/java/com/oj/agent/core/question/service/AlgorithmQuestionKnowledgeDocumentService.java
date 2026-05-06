package com.oj.agent.core.question.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oj.agent.core.question.model.entity.AlgorithmQuestionKnowledgeDocument;

import java.util.List;

public interface AlgorithmQuestionKnowledgeDocumentService extends IService<AlgorithmQuestionKnowledgeDocument> {

    void bind(Long questionId, Long docId);

    List<AlgorithmQuestionKnowledgeDocument> listByQuestionId(Long questionId);
}
