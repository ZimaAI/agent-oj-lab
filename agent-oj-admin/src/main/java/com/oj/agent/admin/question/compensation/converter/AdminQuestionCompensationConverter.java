package com.oj.agent.admin.question.compensation.converter;

import com.oj.agent.admin.question.compensation.model.response.AdminCompensationTaskResponse;
import com.oj.agent.admin.question.compensation.model.result.AdminCompensationTaskResult;
import com.oj.agent.core.question.model.entity.QuestionSyncCompensationTask;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AdminQuestionCompensationConverter {

    public List<AdminCompensationTaskResult> toResultList(List<QuestionSyncCompensationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream().map(this::toResult).toList();
    }

    public AdminCompensationTaskResult toResult(QuestionSyncCompensationTask task) {
        if (task == null) {
            return null;
        }
        return new AdminCompensationTaskResult(
                task.getId(),
                task.getQuestionId(),
                task.getOperationType(),
                task.getStatus(),
                task.getErrorMessage(),
                task.getRetryCount()
        );
    }

    public List<AdminCompensationTaskResponse> toResponseList(List<AdminCompensationTaskResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(this::toResponse).toList();
    }

    public AdminCompensationTaskResponse toResponse(AdminCompensationTaskResult result) {
        if (result == null) {
            return null;
        }
        return new AdminCompensationTaskResponse(
                result.getTaskId(),
                result.getQuestionId(),
                result.getOperationType(),
                result.getStatus(),
                result.getErrorMessage(),
                result.getRetryCount()
        );
    }
}
