package com.oj.agent.core.question.service;


import com.oj.agent.core.question.model.entity.AlgorithmQuestion;

public interface AlgorithmQuestionVectorSyncBookkeepingService {

    boolean saveQuestionWithPendingLog(AlgorithmQuestion question, String traceId);

    void markSuccess(Long questionId, String traceId);

    void recordFailure(Long questionId, String traceId, String errorCode, String errorMessage);

    void recordFailureSnapshotOnly(Long questionId, String traceId, String errorCode, String errorMessage);

    void recordSuccessBookkeepingFailure(Long questionId, String traceId, String errorMessage);

    void recordSuccessBookkeepingFailureSnapshotOnly(Long questionId, String traceId, String errorMessage);
}
