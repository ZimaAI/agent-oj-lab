package com.oj.agent.admin.question.hitk.controller;

import com.oj.agent.admin.question.hitk.converter.AdminQuestionHitKConverter;
import com.oj.agent.admin.question.hitk.model.request.AdminHitKQuestionGenerateRequest;
import com.oj.agent.admin.question.hitk.model.request.AdminHitKQuestionUpdateRequest;
import com.oj.agent.admin.question.hitk.model.request.AdminHitKTaskBatchDeleteRequest;
import com.oj.agent.admin.question.hitk.model.request.AdminHitKTaskBatchStatisticsRequest;
import com.oj.agent.admin.question.hitk.model.request.AdminHitKTaskRemarkUpdateRequest;
import com.oj.agent.admin.question.hitk.model.request.AdminHitKTestCreateRequest;
import com.oj.agent.admin.question.hitk.model.response.AdminHitKQuestionBatchResponse;
import com.oj.agent.admin.question.hitk.model.response.AdminHitKTaskBatchDeleteResponse;
import com.oj.agent.admin.question.hitk.model.response.AdminHitKTaskDetailResponse;
import com.oj.agent.admin.question.hitk.model.response.AdminHitKTaskPageResponse;
import com.oj.agent.admin.question.hitk.model.response.AdminHitKTaskResponse;
import com.oj.agent.admin.question.hitk.model.response.AdminHitKTaskStatisticsResponse;
import com.oj.agent.admin.question.hitk.service.AdminQuestionHitKService;
import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionHitKController {

    private final AdminQuestionHitKService adminQuestionHitKService;
    private final AdminQuestionHitKConverter adminQuestionHitKConverter;

    @PostMapping("/{questionId}/documents/{docId}/segments/hitk-question/generate")
    @RequirePermission("admin:question:update")
    public Result<AdminHitKQuestionBatchResponse> generateHitKQuestions(@PathVariable Long questionId,
                                                                        @PathVariable Long docId,
                                                                        @Valid @RequestBody AdminHitKQuestionGenerateRequest request) {
        return Result.success(
                adminQuestionHitKConverter.toQuestionBatchResponse(
                        adminQuestionHitKService.generateHitKQuestions(
                                adminQuestionHitKConverter.toGenerateCommand(questionId, docId, request)
                        )
                )
        );
    }

    @PutMapping("/{questionId}/documents/{docId}/segments/hitk-question")
    @RequirePermission("admin:question:update")
    public Result<AdminHitKQuestionBatchResponse> updateHitKQuestions(@PathVariable Long questionId,
                                                                      @PathVariable Long docId,
                                                                      @Valid @RequestBody AdminHitKQuestionUpdateRequest request) {
        return Result.success(
                adminQuestionHitKConverter.toQuestionBatchResponse(
                        adminQuestionHitKService.updateHitKQuestions(
                                adminQuestionHitKConverter.toUpdateCommand(questionId, docId, request)
                        )
                )
        );
    }

    @PostMapping("/{questionId}/documents/{docId}/segments/hitk-tests")
    @RequirePermission("admin:question:update")
    public Result<AdminHitKTaskResponse> createHitKTest(@PathVariable Long questionId,
                                                        @PathVariable Long docId,
                                                        @Valid @RequestBody AdminHitKTestCreateRequest request) {
        return Result.success(
                adminQuestionHitKConverter.toTaskResponse(
                        adminQuestionHitKService.createHitKTest(
                                adminQuestionHitKConverter.toCreateCommand(questionId, docId, request)
                        )
                )
        );
    }

    @GetMapping("/{questionId}/documents/{docId}/segments/hitk-tests")
    @RequirePermission("admin:question:read")
    public Result<List<AdminHitKTaskResponse>> listHitKTests(@PathVariable Long questionId,
                                                             @PathVariable Long docId) {
        return Result.success(
                adminQuestionHitKConverter.toTaskResponseList(
                        adminQuestionHitKService.listHitKTests(
                                adminQuestionHitKConverter.toTestListQuery(questionId, docId)
                        )
                )
        );
    }

    @GetMapping("/{questionId}/documents/{docId}/segments/hitk-tests/{taskId}")
    @RequirePermission("admin:question:read")
    public Result<AdminHitKTaskDetailResponse> getHitKTestDetail(@PathVariable Long questionId,
                                                                 @PathVariable Long docId,
                                                                 @PathVariable Long taskId) {
        return Result.success(
                adminQuestionHitKConverter.toTaskDetailResponse(
                        adminQuestionHitKService.getHitKTestDetail(
                                adminQuestionHitKConverter.toTaskDetailQuery(questionId, docId, taskId)
                        )
                )
        );
    }

    @GetMapping("/hitk-tasks")
    @RequirePermission("admin:question:read")
    public Result<AdminHitKTaskPageResponse> pageHitKTasks(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "20") Long size,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(
                adminQuestionHitKConverter.toTaskPageResponse(
                        adminQuestionHitKService.pageHitKTasks(
                                adminQuestionHitKConverter.toTaskPageQuery(current, size, startTime, endTime, status)
                        )
                )
        );
    }

    @PostMapping("/hitk-tasks/statistics")
    @RequirePermission("admin:question:read")
    public Result<AdminHitKTaskStatisticsResponse> statisticsHitKTasks(
            @Valid @RequestBody AdminHitKTaskBatchStatisticsRequest request) {
        return Result.success(
                adminQuestionHitKConverter.toTaskStatisticsResponse(
                        adminQuestionHitKService.statisticsHitKTasks(
                                adminQuestionHitKConverter.toTaskStatisticsQuery(request)
                        )
                )
        );
    }

    @PostMapping("/hitk-tasks/batch-delete")
    @RequirePermission("admin:question:delete")
    public Result<AdminHitKTaskBatchDeleteResponse> batchDeleteHitKTasks(
            @Valid @RequestBody AdminHitKTaskBatchDeleteRequest request) {
        return Result.success(
                adminQuestionHitKConverter.toTaskBatchDeleteResponse(
                        adminQuestionHitKService.batchDeleteHitKTasks(
                                adminQuestionHitKConverter.toTaskBatchDeleteCommand(request)
                        )
                )
        );
    }

    @PutMapping("/hitk-tasks/{taskId}/remark")
    @RequirePermission("admin:question:update")
    public Result<AdminHitKTaskResponse> updateHitKTaskRemark(@PathVariable Long taskId,
                                                              @Valid @RequestBody AdminHitKTaskRemarkUpdateRequest request) {
        return Result.success(
                adminQuestionHitKConverter.toTaskResponse(
                        adminQuestionHitKService.updateHitKTaskRemark(
                                adminQuestionHitKConverter.toTaskRemarkUpdateCommand(taskId, request)
                        )
                )
        );
    }
}
