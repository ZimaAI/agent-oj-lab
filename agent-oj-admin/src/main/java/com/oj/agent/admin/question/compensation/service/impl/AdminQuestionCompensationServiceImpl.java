package com.oj.agent.admin.question.compensation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.admin.question.compensation.converter.AdminQuestionCompensationConverter;
import com.oj.agent.admin.question.compensation.model.result.AdminCompensationTaskResult;
import com.oj.agent.admin.question.compensation.service.AdminQuestionCompensationService;
import com.oj.agent.core.question.converter.AlgorithmQuestionConverter;
import com.oj.agent.core.question.mapper.QuestionSyncCompensationTaskMapper;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import com.oj.agent.core.question.model.entity.QuestionSyncCompensationTask;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.question.service.AlgorithmQuestionVectorSyncService;
import com.oj.agent.core.rag.store.AgentVectorStore;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AdminQuestionCompensationServiceImpl implements AdminQuestionCompensationService {

    private static final String DELETE_MARK_MESSAGE = "补偿删除";

    private final QuestionSyncCompensationTaskMapper compensationTaskMapper;
    private final AlgorithmQuestionService algorithmQuestionService;
    private final AgentVectorStore agentVectorStore;
    private final AlgorithmQuestionVectorSyncService algorithmQuestionVectorSyncService;
    private final AdminQuestionCompensationConverter adminQuestionCompensationConverter;

    public AdminQuestionCompensationServiceImpl(QuestionSyncCompensationTaskMapper compensationTaskMapper,
                                                AlgorithmQuestionService algorithmQuestionService,
                                                AgentVectorStore agentVectorStore,
                                                AlgorithmQuestionVectorSyncService algorithmQuestionVectorSyncService,
                                                AdminQuestionCompensationConverter adminQuestionCompensationConverter) {
        this.compensationTaskMapper = compensationTaskMapper;
        this.algorithmQuestionService = algorithmQuestionService;
        this.agentVectorStore = agentVectorStore;
        this.algorithmQuestionVectorSyncService = algorithmQuestionVectorSyncService;
        this.adminQuestionCompensationConverter = adminQuestionCompensationConverter;
    }

    @Override
    public List<AdminCompensationTaskResult> listPendingTasks() {
        List<QuestionSyncCompensationTask> tasks = compensationTaskMapper.selectList(
                new LambdaQueryWrapper<QuestionSyncCompensationTask>()
                        .eq(QuestionSyncCompensationTask::getIsDelete, 0)
                        .eq(QuestionSyncCompensationTask::getStatus, "PENDING")
                        .orderByAsc(QuestionSyncCompensationTask::getCreateTime)
                        .last("limit 200")
        );
        return adminQuestionCompensationConverter.toResultList(tasks);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCompensationTaskResult retryTask(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "补偿任务 ID 非法");
        }
        QuestionSyncCompensationTask task = compensationTaskMapper.selectById(taskId);
        if (task == null || Integer.valueOf(1).equals(task.getIsDelete())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "补偿任务不存在");
        }

        task.setStatus("PROCESSING");
        task.setLastAttemptTime(LocalDateTime.now());
        task.setRetryCount(Objects.requireNonNullElse(task.getRetryCount(), 0) + 1);
        compensationTaskMapper.updateById(task);

        try {
            doCompensation(task);
            task.setStatus("SUCCESS");
            task.setErrorMessage(null);
        } catch (Exception ex) {
            task.setStatus("FAILED");
            task.setErrorMessage(ex.getMessage());
        }
        compensationTaskMapper.updateById(task);
        return adminQuestionCompensationConverter.toResult(task);
    }

    private void doCompensation(QuestionSyncCompensationTask task) {
        if ("DELETE".equals(task.getOperationType())) {
            executeDeleteCompensation(task.getQuestionId());
            return;
        }
        if ("UPDATE".equals(task.getOperationType())) {
            executeUpdateCompensation(task.getQuestionId());
            return;
        }
        throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "不支持的补偿类型");
    }

    private void executeDeleteCompensation(Long questionId) {
        boolean deleted = agentVectorStore.markDeleted(questionId, DELETE_MARK_MESSAGE);
        if (!deleted) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "补偿删除失败：向量投影不存在");
        }
    }

    private void executeUpdateCompensation(Long questionId) {
        AlgorithmQuestionResult questionResult;
        try {
            questionResult = algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(questionId));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "补偿更新失败：题目不存在");
        }
        if (questionResult == null) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "补偿更新失败：题目不存在");
        }
        AlgorithmQuestion question = AlgorithmQuestionConverter.toEntity(questionResult);
        algorithmQuestionVectorSyncService.sync(question);
    }
}
