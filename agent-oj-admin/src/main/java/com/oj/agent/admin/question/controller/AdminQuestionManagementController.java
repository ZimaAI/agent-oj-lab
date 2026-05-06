package com.oj.agent.admin.question.controller;

import com.oj.agent.admin.question.converter.AdminQuestionManagementConverter;
import com.oj.agent.admin.question.model.request.AdminQuestionBatchDeleteRequest;
import com.oj.agent.admin.question.model.request.AdminQuestionCreateRequest;
import com.oj.agent.admin.question.model.request.AdminQuestionDocumentUploadRequest;
import com.oj.agent.admin.question.model.request.AdminQuestionUpdateRequest;
import com.oj.agent.admin.question.model.response.AdminBatchOperationResponse;
import com.oj.agent.admin.question.model.response.AdminKnowledgeSegmentPageResponse;
import com.oj.agent.admin.question.model.response.AdminQuestionDocumentResponse;
import com.oj.agent.admin.question.model.response.AdminQuestionDocumentSegmentPageResponse;
import com.oj.agent.admin.question.model.response.AdminQuestionDocumentSegmentResponse;
import com.oj.agent.admin.question.model.response.AdminQuestionDualDetailResponse;
import com.oj.agent.admin.question.service.AdminQuestionManagementService;
import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionManagementController {

    private final AdminQuestionManagementService adminQuestionManagementService;
    private final AdminQuestionManagementConverter adminQuestionManagementConverter;

    /**
     * Query dual-detail data for one question.
     */
    @GetMapping("/{questionId}/dual-detail")
    @RequirePermission("admin:question:read")
    public Result<AdminQuestionDualDetailResponse> getDualDetail(@PathVariable Long questionId) {
        return Result.success(
                adminQuestionManagementConverter.toDualDetailResponse(
                        adminQuestionManagementService.getQuestionDualDetail(
                                adminQuestionManagementConverter.toDualDetailQuery(questionId)
                        )
                )
        );
    }

    /**
     * Update one question and sync dual stores.
     */
    @PutMapping("/{questionId}")
    @RequirePermission("admin:question:update")
    public Result<AdminQuestionDualDetailResponse> updateQuestion(@PathVariable Long questionId,
                                                                  @Valid @RequestBody AdminQuestionUpdateRequest request) {
        return Result.success(
                adminQuestionManagementConverter.toDualDetailResponse(
                        adminQuestionManagementService.updateQuestion(
                                adminQuestionManagementConverter.toUpdateCommand(questionId, request)
                        )
                )
        );
    }

    /**
     * Create one question and return dual-detail data.
     */
    @PostMapping
    @RequirePermission("admin:question:create")
    public Result<AdminQuestionDualDetailResponse> createQuestion(@Valid @RequestBody AdminQuestionCreateRequest request) {
        return Result.success(
                adminQuestionManagementConverter.toDualDetailResponse(
                        adminQuestionManagementService.createQuestion(
                                adminQuestionManagementConverter.toCreateCommand(request)
                        )
                )
        );
    }

    /**
     * Upload one document (pdf / md / markdown / zip) and bind it to the question.
     */
    @PostMapping(value = "/{questionId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("admin:question:update")
    public Result<AdminQuestionDocumentResponse> uploadQuestionDocument(@PathVariable Long questionId,
                                                                        @RequestPart("file") MultipartFile file) {
        return Result.success(
                adminQuestionManagementConverter.toDocumentResponse(
                        adminQuestionManagementService.uploadQuestionDocument(
                                adminQuestionManagementConverter.toUploadCommand(
                                        new AdminQuestionDocumentUploadRequest(questionId, file)
                                )
                        )
                )
        );
    }

    /**
     * List uploaded documents bound to one question.
     */
    @GetMapping("/{questionId}/documents")
    @RequirePermission("admin:question:read")
    public Result<List<AdminQuestionDocumentResponse>> listQuestionDocuments(@PathVariable Long questionId) {
        return Result.success(
                adminQuestionManagementConverter.toDocumentResponseList(
                        adminQuestionManagementService.listQuestionDocuments(
                                adminQuestionManagementConverter.toDocumentListQuery(questionId)
                        )
                )
        );
    }

    /**
     * Delete one uploaded document bound to one question.
     */
    @DeleteMapping("/{questionId}/documents/{docId}")
    @RequirePermission("admin:question:delete")
    public Result<Boolean> deleteQuestionDocument(@PathVariable Long questionId,
                                                  @PathVariable Long docId) {
        adminQuestionManagementService.deleteQuestionDocument(
                adminQuestionManagementConverter.toDocumentDeleteCommand(questionId, docId)
        );
        return Result.success(true);
    }

    /**
     * Page top-level segments of one bound document.
     */
    @GetMapping("/{questionId}/documents/{docId}/segments")
    @RequirePermission("admin:question:read")
    public Result<AdminQuestionDocumentSegmentPageResponse> pageQuestionDocumentSegments(
            @PathVariable Long questionId,
            @PathVariable Long docId,
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        return Result.success(
                adminQuestionManagementConverter.toDocumentSegmentPageResponse(
                        adminQuestionManagementService.pageQuestionDocumentSegments(
                                adminQuestionManagementConverter.toDocumentSegmentPageQuery(questionId, docId, current, size)
                        )
                )
        );
    }

    /**
     * List child segments of one parent segment.
     */
    @GetMapping("/{questionId}/documents/{docId}/segments/{parentSegmentId}/children")
    @RequirePermission("admin:question:read")
    public Result<List<AdminQuestionDocumentSegmentResponse>> listQuestionDocumentChildSegments(
            @PathVariable Long questionId,
            @PathVariable Long docId,
            @PathVariable Long parentSegmentId) {
        return Result.success(
                adminQuestionManagementConverter.toDocumentSegmentResponseList(
                        adminQuestionManagementService.listQuestionDocumentChildSegments(
                                adminQuestionManagementConverter.toDocumentChildSegmentQuery(
                                        questionId,
                                        docId,
                                        parentSegmentId
                                )
                        )
                )
        );
    }

    /**
     * 全量分页查询知识片段。
     */
    @GetMapping("/knowledge-segments")
    @RequirePermission("admin:question:read")
    public Result<AdminKnowledgeSegmentPageResponse> pageKnowledgeSegments(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "20") Long size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(
                adminQuestionManagementConverter.toKnowledgeSegmentPageResponse(
                        adminQuestionManagementService.pageKnowledgeSegments(
                                adminQuestionManagementConverter.toKnowledgeSegmentPageQuery(current, size, keyword)
                        )
                )
        );
    }

    /**
     * Batch delete questions and return per-item results.
     */
    @PostMapping("/batch-delete")
    @RequirePermission("admin:question:delete")
    public Result<AdminBatchOperationResponse> batchDelete(@Valid @RequestBody AdminQuestionBatchDeleteRequest request) {
        return Result.success(
                adminQuestionManagementConverter.toBatchOperationResponse(
                        adminQuestionManagementService.batchDeleteQuestions(
                                adminQuestionManagementConverter.toBatchDeleteCommand(request)
                        )
                )
        );
    }
}
