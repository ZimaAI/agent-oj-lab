package com.oj.agent.admin.question.converter;

import com.oj.agent.admin.question.model.command.AdminQuestionBatchDeleteCommand;
import com.oj.agent.admin.question.model.command.AdminQuestionCreateCommand;
import com.oj.agent.admin.question.model.command.AdminQuestionDocumentDeleteCommand;
import com.oj.agent.admin.question.model.command.AdminQuestionDocumentUploadCommand;
import com.oj.agent.admin.question.model.command.AdminQuestionUpdateCommand;
import com.oj.agent.admin.question.model.query.AdminKnowledgeSegmentPageQuery;
import com.oj.agent.admin.question.model.query.AdminQuestionDocumentChildSegmentQuery;
import com.oj.agent.admin.question.model.query.AdminQuestionDocumentListQuery;
import com.oj.agent.admin.question.model.query.AdminQuestionDocumentSegmentPageQuery;
import com.oj.agent.admin.question.model.query.AdminQuestionDualDetailQuery;
import com.oj.agent.admin.question.model.request.AdminQuestionBatchDeleteRequest;
import com.oj.agent.admin.question.model.request.AdminQuestionCreateRequest;
import com.oj.agent.admin.question.model.request.AdminQuestionDocumentUploadRequest;
import com.oj.agent.admin.question.model.request.AdminQuestionUpdateRequest;
import com.oj.agent.admin.question.model.response.AdminBatchOperationResponse;
import com.oj.agent.admin.question.model.response.AdminKnowledgeSegmentPageResponse;
import com.oj.agent.admin.question.model.response.AdminKnowledgeSegmentResponse;
import com.oj.agent.admin.question.model.response.AdminQuestionDocumentResponse;
import com.oj.agent.admin.question.model.response.AdminQuestionDocumentSegmentPageResponse;
import com.oj.agent.admin.question.model.response.AdminQuestionDocumentSegmentResponse;
import com.oj.agent.admin.question.model.response.AdminQuestionDualDetailResponse;
import com.oj.agent.admin.question.model.response.AdminQuestionVectorProjectionResponse;
import com.oj.agent.admin.question.model.result.AdminBatchOperationResult;
import com.oj.agent.admin.question.model.result.AdminKnowledgeSegmentPageResult;
import com.oj.agent.admin.question.model.result.AdminKnowledgeSegmentResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDocumentResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDocumentSegmentPageResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDocumentSegmentResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDualDetailResult;
import com.oj.agent.admin.question.model.result.AdminQuestionVectorProjectionResult;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class AdminQuestionManagementConverter {

    public AdminQuestionCreateCommand toCreateCommand(AdminQuestionCreateRequest request) {
        if (request == null) {
            return null;
        }
        AdminQuestionCreateCommand command = new AdminQuestionCreateCommand();
        command.setTitle(request.getTitle());
        command.setDescription(request.getDescription());
        command.setDifficulty(request.getDifficulty());
        if (request.getStandardCasePool() != null) {
            command.setStandardCasePool(request.getStandardCasePool().stream().map(caseItem -> {
                AdminQuestionCreateCommand.StandardCaseCreateCommand item =
                        new AdminQuestionCreateCommand.StandardCaseCreateCommand();
                item.setStdin(caseItem.getStdin());
                item.setExpectedStdout(caseItem.getExpectedStdout());
                item.setPublicCase(caseItem.getPublicCase());
                item.setDescription(caseItem.getDescription());
                return item;
            }).toList());
        }
        command.setTagIds(request.getTagIds());
        command.setTags(request.getTags());
        if (request.getCodeTemplates() != null) {
            command.setCodeTemplates(request.getCodeTemplates().stream().map(template -> {
                AdminQuestionCreateCommand.CodeTemplateCreateCommand item =
                        new AdminQuestionCreateCommand.CodeTemplateCreateCommand();
                item.setLanguage(template.getLanguage());
                item.setEntryMethodName(template.getEntryMethodName());
                item.setStarterCode(template.getStarterCode());
                item.setReferenceAnswer(template.getReferenceAnswer());
                return item;
            }).toList());
        }
        return command;
    }

    public AdminQuestionUpdateCommand toUpdateCommand(Long questionId, AdminQuestionUpdateRequest request) {
        if (request == null) {
            return null;
        }
        AdminQuestionUpdateCommand command = new AdminQuestionUpdateCommand();
        command.setQuestionId(questionId);
        command.setTitle(request.getTitle());
        command.setDescription(request.getDescription());
        command.setDifficulty(request.getDifficulty());
        if (request.getStandardCasePool() != null) {
            command.setStandardCasePool(request.getStandardCasePool().stream().map(caseItem -> {
                AdminQuestionUpdateCommand.StandardCaseUpdateCommand item =
                        new AdminQuestionUpdateCommand.StandardCaseUpdateCommand();
                item.setStdin(caseItem.getStdin());
                item.setExpectedStdout(caseItem.getExpectedStdout());
                item.setPublicCase(caseItem.getPublicCase());
                item.setDescription(caseItem.getDescription());
                return item;
            }).toList());
        }
        command.setTags(request.getTags());
        if (request.getCodeTemplates() != null) {
            command.setCodeTemplates(request.getCodeTemplates().stream().map(template -> {
                AdminQuestionUpdateCommand.CodeTemplateUpdateCommand item =
                        new AdminQuestionUpdateCommand.CodeTemplateUpdateCommand();
                item.setLanguage(template.getLanguage());
                item.setEntryMethodName(template.getEntryMethodName());
                item.setStarterCode(template.getStarterCode());
                item.setReferenceAnswer(template.getReferenceAnswer());
                return item;
            }).toList());
        }
        return command;
    }

    public AdminQuestionBatchDeleteCommand toBatchDeleteCommand(AdminQuestionBatchDeleteRequest request) {
        if (request == null) {
            return null;
        }
        return new AdminQuestionBatchDeleteCommand(request.getQuestionIds());
    }

    public AdminQuestionDocumentUploadCommand toUploadCommand(AdminQuestionDocumentUploadRequest request) {
        if (request == null || request.getFile() == null) {
            return null;
        }
        MultipartFile file = request.getFile();
        try {
            return new AdminQuestionDocumentUploadCommand(
                    request.getQuestionId(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read upload file", exception);
        }
    }

    public AdminQuestionDocumentDeleteCommand toDocumentDeleteCommand(Long questionId, Long docId) {
        return new AdminQuestionDocumentDeleteCommand(questionId, docId);
    }

    public AdminQuestionDualDetailQuery toDualDetailQuery(Long questionId) {
        return new AdminQuestionDualDetailQuery(questionId);
    }

    public AdminQuestionDocumentListQuery toDocumentListQuery(Long questionId) {
        return new AdminQuestionDocumentListQuery(questionId);
    }

    public AdminQuestionDocumentSegmentPageQuery toDocumentSegmentPageQuery(Long questionId, Long docId, Long current, Long size) {
        return new AdminQuestionDocumentSegmentPageQuery(questionId, docId, current, size);
    }

    public AdminQuestionDocumentChildSegmentQuery toDocumentChildSegmentQuery(Long questionId, Long docId, Long parentSegmentId) {
        return new AdminQuestionDocumentChildSegmentQuery(questionId, docId, parentSegmentId);
    }

    public AdminKnowledgeSegmentPageQuery toKnowledgeSegmentPageQuery(Long current, Long size, String keyword) {
        return new AdminKnowledgeSegmentPageQuery(current, size, keyword);
    }

    public AdminQuestionDualDetailResponse toDualDetailResponse(AdminQuestionDualDetailResult result) {
        if (result == null) {
            return null;
        }
        return new AdminQuestionDualDetailResponse(
                result.getMysqlQuestion(),
                result.getMysqlCodeTemplates(),
                toVectorProjectionResponse(result.getVectorProjection())
        );
    }

    public List<AdminQuestionDocumentResponse> toDocumentResponseList(List<AdminQuestionDocumentResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(this::toDocumentResponse).toList();
    }

    public AdminQuestionDocumentResponse toDocumentResponse(AdminQuestionDocumentResult result) {
        if (result == null) {
            return null;
        }
        AdminQuestionDocumentResponse response = new AdminQuestionDocumentResponse();
        response.setDocId(result.getDocId());
        response.setDocTitle(result.getDocTitle());
        response.setStatus(result.getStatus());
        response.setDocUrl(result.getDocUrl());
        response.setConvertedDocUrl(result.getConvertedDocUrl());
        response.setCreatedAt(result.getCreatedAt());
        response.setUpdatedAt(result.getUpdatedAt());
        response.setProgressPercent(result.getProgressPercent());
        return response;
    }

    public List<AdminQuestionDocumentSegmentResponse> toDocumentSegmentResponseList(List<AdminQuestionDocumentSegmentResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(this::toDocumentSegmentResponse).toList();
    }

    public AdminQuestionDocumentSegmentPageResponse toDocumentSegmentPageResponse(AdminQuestionDocumentSegmentPageResult result) {
        if (result == null) {
            return null;
        }
        return new AdminQuestionDocumentSegmentPageResponse(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                toDocumentSegmentResponseList(result.getRecords())
        );
    }

    public AdminQuestionDocumentSegmentResponse toDocumentSegmentResponse(AdminQuestionDocumentSegmentResult result) {
        if (result == null) {
            return null;
        }
        AdminQuestionDocumentSegmentResponse response = new AdminQuestionDocumentSegmentResponse();
        response.setSegmentId(result.getSegmentId());
        response.setDocumentId(result.getDocumentId());
        response.setChunkOrder(result.getChunkOrder());
        response.setChunkId(result.getChunkId());
        response.setParentChunkId(result.getParentChunkId());
        response.setStatus(result.getStatus());
        response.setSkipEmbedding(result.getSkipEmbedding());
        response.setHitkQuestion(result.getHitkQuestion());
        response.setText(result.getText());
        response.setMetadata(result.getMetadata());
        response.setParentSegment(result.getParentSegment());
        response.setChildSegmentCount(result.getChildSegmentCount());
        response.setCreatedAt(result.getCreatedAt());
        response.setUpdatedAt(result.getUpdatedAt());
        return response;
    }

    public AdminKnowledgeSegmentPageResponse toKnowledgeSegmentPageResponse(AdminKnowledgeSegmentPageResult result) {
        if (result == null) {
            return null;
        }
        return new AdminKnowledgeSegmentPageResponse(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                toKnowledgeSegmentResponseList(result.getRecords())
        );
    }

    public List<AdminKnowledgeSegmentResponse> toKnowledgeSegmentResponseList(List<AdminKnowledgeSegmentResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(this::toKnowledgeSegmentResponse).toList();
    }

    public AdminKnowledgeSegmentResponse toKnowledgeSegmentResponse(AdminKnowledgeSegmentResult result) {
        if (result == null) {
            return null;
        }
        AdminKnowledgeSegmentResponse response = new AdminKnowledgeSegmentResponse();
        response.setSegmentId(result.getSegmentId());
        response.setQuestionId(result.getQuestionId());
        response.setQuestionTitle(result.getQuestionTitle());
        response.setDocumentId(result.getDocumentId());
        response.setDocumentTitle(result.getDocumentTitle());
        response.setChunkOrder(result.getChunkOrder());
        response.setChunkId(result.getChunkId());
        response.setStatus(result.getStatus());
        response.setSkipEmbedding(result.getSkipEmbedding());
        response.setHitkQuestion(result.getHitkQuestion());
        response.setText(result.getText());
        response.setMetadata(result.getMetadata());
        response.setCreatedAt(result.getCreatedAt());
        response.setUpdatedAt(result.getUpdatedAt());
        return response;
    }

    public AdminBatchOperationResponse toBatchOperationResponse(AdminBatchOperationResult result) {
        if (result == null) {
            return null;
        }
        List<AdminBatchOperationResponse.ItemResult> responseItems =
                result.getResults() == null ? Collections.emptyList() : result.getResults().stream()
                        .map(item -> new AdminBatchOperationResponse.ItemResult(
                                item.getQuestionId(),
                                item.getSuccess(),
                                item.getMessage()
                        ))
                        .toList();
        return new AdminBatchOperationResponse(
                result.getSuccessCount(),
                result.getFailureCount(),
                responseItems
        );
    }

    public AdminQuestionVectorProjectionResponse toVectorProjectionResponse(AdminQuestionVectorProjectionResult result) {
        if (result == null) {
            return null;
        }
        return new AdminQuestionVectorProjectionResponse(
                result.getId(),
                result.getQuestionId(),
                result.getTitle(),
                result.getDifficulty(),
                result.getLanguage(),
                result.getEmbedding(),
                result.getIsDelete()
        );
    }
}
