package com.oj.agent.admin.question.compensation.service;

import com.oj.agent.admin.question.compensation.model.result.AdminCompensationTaskResult;

import java.util.List;

public interface AdminQuestionCompensationService {

    /**
     * 查询待补偿任务。
     */
    List<AdminCompensationTaskResult> listPendingTasks();

    /**
     * 手动触发补偿任务。
     */
    AdminCompensationTaskResult retryTask(Long taskId);
}
