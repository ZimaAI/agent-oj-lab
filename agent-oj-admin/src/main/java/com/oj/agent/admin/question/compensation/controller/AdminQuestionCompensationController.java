package com.oj.agent.admin.question.compensation.controller;

import com.oj.agent.admin.question.compensation.converter.AdminQuestionCompensationConverter;
import com.oj.agent.admin.question.compensation.model.response.AdminCompensationTaskResponse;
import com.oj.agent.admin.question.compensation.service.AdminQuestionCompensationService;
import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端题目补偿控制器。
 */
@RestController
@RequestMapping("/api/admin/questions/compensation")
@RequiredArgsConstructor
public class AdminQuestionCompensationController {

    private final AdminQuestionCompensationService adminQuestionCompensationService;
    private final AdminQuestionCompensationConverter adminQuestionCompensationConverter;

    /**
     * 查询待补偿任务。
     */
    @GetMapping("/pending")
    @RequirePermission("admin:question:compensation:read")
    public Result<List<AdminCompensationTaskResponse>> listPendingTasks() {
        return Result.success(
                adminQuestionCompensationConverter.toResponseList(
                        adminQuestionCompensationService.listPendingTasks()
                )
        );
    }

    /**
     * 重试补偿任务。
     */
    @PostMapping("/{taskId}/retry")
    @RequirePermission("admin:question:compensation:retry")
    public Result<AdminCompensationTaskResponse> retryTask(@PathVariable Long taskId) {
        return Result.success(
                adminQuestionCompensationConverter.toResponse(
                        adminQuestionCompensationService.retryTask(taskId)
                )
        );
    }
}
