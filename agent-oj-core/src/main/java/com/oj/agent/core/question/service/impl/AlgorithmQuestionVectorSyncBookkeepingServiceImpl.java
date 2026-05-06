package com.oj.agent.core.question.service.impl;

import com.oj.agent.core.question.enums.AlgorithmQuestionVectorSyncStatusEnum;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import com.oj.agent.core.question.model.entity.AlgorithmQuestionVectorSyncLog;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.question.service.AlgorithmQuestionVectorSyncBookkeepingService;
import com.oj.agent.core.question.service.AlgorithmQuestionVectorSyncLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlgorithmQuestionVectorSyncBookkeepingServiceImpl implements AlgorithmQuestionVectorSyncBookkeepingService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final AlgorithmQuestionService algorithmQuestionService;
    private final AlgorithmQuestionVectorSyncLogService algorithmQuestionVectorSyncLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveQuestionWithPendingLog(AlgorithmQuestion question, String traceId) {
        // 先持久化算法题主数据，并写入待同步日志。
        boolean saved = algorithmQuestionService.save(question);
        if (!saved) {
            return false;
        }
        algorithmQuestionVectorSyncLogService.save(buildSyncLog(
                question.getId(),
                AlgorithmQuestionVectorSyncStatusEnum.PENDING.name(),
                null,
                null,
                traceId
        ));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(Long questionId, String traceId) {
        // 同步成功后记录成功日志并刷新快照状态。
        algorithmQuestionVectorSyncLogService.save(buildSyncLog(
                questionId,
                AlgorithmQuestionVectorSyncStatusEnum.SUCCESS.name(),
                null,
                null,
                traceId
        ));
        updateQuestionSnapshot(questionId, AlgorithmQuestionVectorSyncStatusEnum.SUCCESS.name(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(Long questionId, String traceId, String errorCode, String errorMessage) {
        // 优先记录失败日志，再更新失败快照。
        algorithmQuestionVectorSyncLogService.save(buildSyncLog(
                questionId,
                AlgorithmQuestionVectorSyncStatusEnum.FAILED.name(),
                errorCode,
                errorMessage,
                traceId
        ));
        updateQuestionSnapshot(questionId, AlgorithmQuestionVectorSyncStatusEnum.FAILED.name(), errorMessage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordFailureSnapshotOnly(Long questionId, String traceId, String errorCode, String errorMessage) {
        // 失败日志无法写入时，至少保留题目快照中的失败状态。
        updateQuestionSnapshot(questionId, AlgorithmQuestionVectorSyncStatusEnum.FAILED.name(),
                buildFallbackErrorMessage(errorCode, errorMessage, traceId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessBookkeepingFailure(Long questionId, String traceId, String errorMessage) {
        // PostgreSQL 已成功但 MySQL 成功记账失败时，补写异常日志。
        algorithmQuestionVectorSyncLogService.save(buildSyncLog(
                questionId,
                AlgorithmQuestionVectorSyncStatusEnum.FAILED.name(),
                "SUCCESS_BOOKKEEPING_FAILED",
                errorMessage,
                traceId
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordSuccessBookkeepingFailureSnapshotOnly(Long questionId, String traceId, String errorMessage) {
        // 成功记账异常日志也失败时，至少在快照中标记异常。
        updateQuestionSnapshot(questionId, AlgorithmQuestionVectorSyncStatusEnum.FAILED.name(),
                buildFallbackErrorMessage("SUCCESS_BOOKKEEPING_FAILED", errorMessage, traceId));
    }

    private void updateQuestionSnapshot(Long questionId, String syncStatus, String errorMessage) {
        AlgorithmQuestion question = new AlgorithmQuestion();
        question.setId(questionId);
        question.setVectorSyncStatus(syncStatus);
        question.setVectorSyncErrorMessage(truncate(errorMessage));
        boolean updated = algorithmQuestionService.updateById(question);
        if (!updated) {
            throw new IllegalStateException("Failed to update algorithm question sync snapshot, questionId=" + questionId);
        }
    }

    private AlgorithmQuestionVectorSyncLog buildSyncLog(Long questionId,
                                                        String syncStatus,
                                                        String errorCode,
                                                        String errorMessage,
                                                        String traceId) {
        AlgorithmQuestionVectorSyncLog log = new AlgorithmQuestionVectorSyncLog();
        log.setQuestionId(questionId);
        log.setSyncStatus(syncStatus);
        log.setErrorCode(errorCode);
        log.setErrorMessage(truncate(errorMessage));
        log.setTraceId(traceId);
        log.setIsDelete(0);
        return log;
    }

    private String buildFallbackErrorMessage(String errorCode, String errorMessage, String traceId) {
        return truncate("code=" + errorCode + ", traceId=" + traceId + ", message=" + errorMessage);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
