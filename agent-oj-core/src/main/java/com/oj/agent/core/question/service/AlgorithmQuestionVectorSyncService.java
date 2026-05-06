package com.oj.agent.core.question.service;


import com.oj.agent.core.question.model.entity.AlgorithmQuestion;

public interface AlgorithmQuestionVectorSyncService {

    void sync(AlgorithmQuestion question);
}
