package com.oj.agent.admin.question.ragas.controller;

import com.oj.agent.admin.question.ragas.converter.AdminQuestionRagasConverter;
import com.oj.agent.admin.question.ragas.model.request.AdminRagasAnswerGenerateRequest;
import com.oj.agent.admin.question.ragas.model.request.AdminRagasDeleteRequest;
import com.oj.agent.admin.question.ragas.model.request.AdminRagasEvaluateRequest;
import com.oj.agent.admin.question.ragas.model.request.AdminRagasGenerateRequest;
import com.oj.agent.admin.question.ragas.model.request.AdminRagasTaskCreateRequest;
import com.oj.agent.admin.question.ragas.model.request.AdminRagasUpdateRequest;
import com.oj.agent.admin.question.ragas.model.response.AdminRagasTaskDetailResponse;
import com.oj.agent.admin.question.ragas.model.response.AdminRagasTaskPageResponse;
import com.oj.agent.admin.question.ragas.model.response.AdminRagasTaskResponse;
import com.oj.agent.admin.question.ragas.model.response.AdminQuestionDocumentSegmentRagasResponse;
import com.oj.agent.admin.question.ragas.service.AdminQuestionRagasService;
import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class AdminQuestionRagasController {

    private final AdminQuestionRagasService adminQuestionRagasService;
    private final AdminQuestionRagasConverter adminQuestionRagasConverter;

    /**
     * 批量生成知识分片 RAGAS 问题与标准答案。
     */
    @PostMapping("/{questionId}/documents/{docId}/segments/ragas/generate")
    @RequirePermission("admin:question:update")
    public Result<List<AdminQuestionDocumentSegmentRagasResponse>> generateRagas(@PathVariable Long questionId,
                                                                                 @PathVariable Long docId,
                                                                                 @Valid @RequestBody AdminRagasGenerateRequest request) {
        return Result.success(
                adminQuestionRagasConverter.toResponseList(
                        adminQuestionRagasService.generateRagas(
                                adminQuestionRagasConverter.toGenerateCommand(questionId, docId, request)
                        )
                )
        );
    }

    /**
     * 查询知识分片 RAGAS 记录列表。
     */
    @GetMapping("/{questionId}/documents/{docId}/segments/ragas")
    @RequirePermission("admin:question:read")
    public Result<List<AdminQuestionDocumentSegmentRagasResponse>> listRagas(@PathVariable Long questionId,
                                                                             @PathVariable Long docId,
                                                                             @RequestParam(required = false) List<Long> segmentIds,
                                                                             @RequestParam(required = false) List<Long> ragasIds,
                                                                             @RequestParam(required = false) List<Long> ids) {
        return Result.success(
                adminQuestionRagasConverter.toResponseList(
                        adminQuestionRagasService.listRagas(
                                adminQuestionRagasConverter.toListQuery(questionId, docId, segmentIds, ragasIds, ids)
                        )
                )
        );
    }

    /**
     * 批量新增或更新知识分片 RAGAS 记录。
     */
    @PutMapping("/{questionId}/documents/{docId}/segments/ragas")
    @RequirePermission("admin:question:update")
    public Result<List<AdminQuestionDocumentSegmentRagasResponse>> updateRagas(@PathVariable Long questionId,
                                                                               @PathVariable Long docId,
                                                                               @Valid @RequestBody AdminRagasUpdateRequest request) {
        return Result.success(
                adminQuestionRagasConverter.toResponseList(
                        adminQuestionRagasService.updateRagas(
                                adminQuestionRagasConverter.toUpdateCommand(questionId, docId, request)
                        )
                )
        );
    }

    /**
     * 批量删除知识分片 RAGAS 记录。
     */
    @DeleteMapping("/{questionId}/documents/{docId}/segments/ragas")
    @RequirePermission("admin:question:update")
    public Result<Boolean> deleteRagas(@PathVariable Long questionId,
                                       @PathVariable Long docId,
                                       @RequestBody(required = false) AdminRagasDeleteRequest request) {
        adminQuestionRagasService.deleteRagas(
                adminQuestionRagasConverter.toDeleteCommand(questionId, docId, request)
        );
        return Result.success(true);
    }

    /**
     * 批量生成知识分片 RAGAS 的模型回答与检索上下文。
     */
    @PostMapping("/{questionId}/documents/{docId}/segments/ragas/answers/generate")
    @RequirePermission("admin:question:update")
    public Result<List<AdminQuestionDocumentSegmentRagasResponse>> generateAnswers(@PathVariable Long questionId,
                                                                                   @PathVariable Long docId,
                                                                                   @RequestBody(required = false) AdminRagasAnswerGenerateRequest request) {
        return Result.success(
                adminQuestionRagasConverter.toResponseList(
                        adminQuestionRagasService.generateAnswers(
                                adminQuestionRagasConverter.toAnswerGenerateCommand(questionId, docId, request)
                        )
                )
        );
    }

    /**
     * 批量执行知识分片 RAGAS 评估并回写结果。
     */
    @PostMapping("/{questionId}/documents/{docId}/segments/ragas/evaluate")
    @RequirePermission("admin:question:update")
    public Result<List<AdminQuestionDocumentSegmentRagasResponse>> evaluateRagas(@PathVariable Long questionId,
                                                                                 @PathVariable Long docId,
                                                                                 @RequestBody(required = false) AdminRagasEvaluateRequest request) {
        return Result.success(
                adminQuestionRagasConverter.toResponseList(
                        adminQuestionRagasService.evaluateRagas(
                                adminQuestionRagasConverter.toEvaluateCommand(questionId, docId, request)
                        )
                )
        );
    }

    /**
     * 创建知识分片 RAGAS 批量评估任务。
     */
    @PostMapping("/{questionId}/documents/{docId}/segments/ragas/tasks")
    @RequirePermission("admin:question:update")
    public Result<AdminRagasTaskResponse> createRagasTask(@PathVariable Long questionId,
                                                          @PathVariable Long docId,
                                                          @Valid @RequestBody AdminRagasTaskCreateRequest request) {
        return Result.success(
                adminQuestionRagasConverter.toTaskResponse(
                        adminQuestionRagasService.createRagasTask(
                                adminQuestionRagasConverter.toTaskCreateCommand(questionId, docId, request)
                        )
                )
        );
    }

    /**
     * 查询文档维度的 RAGAS 任务列表。
     */
    @GetMapping("/{questionId}/documents/{docId}/segments/ragas/tasks")
    @RequirePermission("admin:question:read")
    public Result<List<AdminRagasTaskResponse>> listRagasTasks(@PathVariable Long questionId,
                                                               @PathVariable Long docId) {
        return Result.success(
                adminQuestionRagasConverter.toTaskResponseList(
                        adminQuestionRagasService.listRagasTasks(
                                adminQuestionRagasConverter.toTaskListQuery(questionId, docId)
                        )
                )
        );
    }

    /**
     * 分页查询全局 RAGAS 任务。
     */
    @GetMapping("/ragas-tasks")
    @RequirePermission("admin:question:read")
    public Result<AdminRagasTaskPageResponse> pageRagasTasks(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "20") Long size,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(
                adminQuestionRagasConverter.toTaskPageResponse(
                        adminQuestionRagasService.pageRagasTasks(
                                adminQuestionRagasConverter.toTaskPageQuery(current, size, startTime, endTime, status)
                        )
                )
        );
    }

    /**
     * 查询单个 RAGAS 任务详情。
     */
    @GetMapping("/ragas-tasks/{taskId}")
    @RequirePermission("admin:question:read")
    public Result<AdminRagasTaskDetailResponse> getRagasTaskDetail(@PathVariable Long taskId) {
        return Result.success(
                adminQuestionRagasConverter.toTaskDetailResponse(
                        adminQuestionRagasService.getRagasTaskDetail(
                                adminQuestionRagasConverter.toTaskDetailQuery(taskId)
                        )
                )
        );
    }
}
