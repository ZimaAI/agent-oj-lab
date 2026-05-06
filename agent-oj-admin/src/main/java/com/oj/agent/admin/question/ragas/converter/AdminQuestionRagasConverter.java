package com.oj.agent.admin.question.ragas.converter;

import com.oj.agent.admin.question.ragas.model.command.AdminRagasAnswerGenerateCommand;
import com.oj.agent.admin.question.ragas.model.command.AdminRagasDeleteCommand;
import com.oj.agent.admin.question.ragas.model.command.AdminRagasEvaluateCommand;
import com.oj.agent.admin.question.ragas.model.command.AdminRagasGenerateCommand;
import com.oj.agent.admin.question.ragas.model.command.AdminRagasTaskCreateCommand;
import com.oj.agent.admin.question.ragas.model.command.AdminRagasUpdateCommand;
import com.oj.agent.admin.question.ragas.model.query.AdminRagasListQuery;
import com.oj.agent.admin.question.ragas.model.query.AdminRagasTaskDetailQuery;
import com.oj.agent.admin.question.ragas.model.query.AdminRagasTaskListQuery;
import com.oj.agent.admin.question.ragas.model.query.AdminRagasTaskPageQuery;
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
import com.oj.agent.admin.question.ragas.model.result.AdminRagasTaskDetailResult;
import com.oj.agent.admin.question.ragas.model.result.AdminRagasTaskPageResult;
import com.oj.agent.admin.question.ragas.model.result.AdminRagasTaskResult;
import com.oj.agent.admin.question.ragas.model.result.AdminQuestionDocumentSegmentRagasResult;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class AdminQuestionRagasConverter {

    public AdminRagasGenerateCommand toGenerateCommand(Long questionId, Long docId, AdminRagasGenerateRequest request) {
        return new AdminRagasGenerateCommand(
                questionId,
                docId,
                request == null ? null : request.getSegmentIds()
        );
    }

    public AdminRagasTaskCreateCommand toTaskCreateCommand(Long questionId, Long docId, AdminRagasTaskCreateRequest request) {
        return new AdminRagasTaskCreateCommand(
                questionId,
                docId,
                request == null ? null : request.getSegmentIds()
        );
    }

    public AdminRagasUpdateCommand toUpdateCommand(Long questionId, Long docId, AdminRagasUpdateRequest request) {
        AdminRagasUpdateCommand command = new AdminRagasUpdateCommand();
        command.setQuestionId(questionId);
        command.setDocId(docId);
        if (request != null && request.getUpdates() != null) {
            command.setUpdates(request.getUpdates().stream().map(item -> {
                AdminRagasUpdateCommand.UpdateItem updateItem = new AdminRagasUpdateCommand.UpdateItem();
                updateItem.setRagasId(item.getRagasId());
                updateItem.setSegmentId(item.getSegmentId());
                updateItem.setQuestion(item.getQuestion());
                updateItem.setStandardAnswer(item.getStandardAnswer());
                return updateItem;
            }).toList());
        }
        return command;
    }

    public AdminRagasDeleteCommand toDeleteCommand(Long questionId, Long docId, AdminRagasDeleteRequest request) {
        return new AdminRagasDeleteCommand(
                questionId,
                docId,
                request == null ? null : request.getIds(),
                request == null ? null : request.getRagasIds(),
                request == null ? null : request.getSegmentIds()
        );
    }

    public AdminRagasAnswerGenerateCommand toAnswerGenerateCommand(Long questionId, Long docId, AdminRagasAnswerGenerateRequest request) {
        return new AdminRagasAnswerGenerateCommand(
                questionId,
                docId,
                request == null ? null : request.getIds(),
                request == null ? null : request.getRagasIds(),
                request == null ? null : request.getSegmentIds()
        );
    }

    public AdminRagasEvaluateCommand toEvaluateCommand(Long questionId, Long docId, AdminRagasEvaluateRequest request) {
        return new AdminRagasEvaluateCommand(
                questionId,
                docId,
                request == null ? null : request.getIds(),
                request == null ? null : request.getRagasIds(),
                request == null ? null : request.getSegmentIds()
        );
    }

    public AdminRagasListQuery toListQuery(Long questionId, Long docId, List<Long> segmentIds, List<Long> ragasIds, List<Long> ids) {
        return new AdminRagasListQuery(questionId, docId, segmentIds, ragasIds, ids);
    }

    public AdminRagasTaskListQuery toTaskListQuery(Long questionId, Long docId) {
        return new AdminRagasTaskListQuery(questionId, docId);
    }

    public AdminRagasTaskPageQuery toTaskPageQuery(Long current,
                                                   Long size,
                                                   LocalDateTime startTime,
                                                   LocalDateTime endTime,
                                                   String status) {
        return new AdminRagasTaskPageQuery(current, size, startTime, endTime, status);
    }

    public AdminRagasTaskDetailQuery toTaskDetailQuery(Long taskId) {
        return new AdminRagasTaskDetailQuery(taskId);
    }

    public List<AdminQuestionDocumentSegmentRagasResponse> toResponseList(List<AdminQuestionDocumentSegmentRagasResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(this::toResponse).toList();
    }

    public AdminQuestionDocumentSegmentRagasResponse toResponse(AdminQuestionDocumentSegmentRagasResult result) {
        if (result == null) {
            return null;
        }
        AdminQuestionDocumentSegmentRagasResponse response = new AdminQuestionDocumentSegmentRagasResponse();
        response.setId(result.getId());
        response.setRagasId(result.getRagasId());
        response.setSegmentId(result.getSegmentId());
        response.setQuestion(result.getQuestion());
        response.setStandardAnswer(result.getStandardAnswer());
        response.setGeneratedAnswer(result.getGeneratedAnswer());
        response.setContextPrecision(result.getContextPrecision());
        response.setContextRecall(result.getContextRecall());
        response.setFaithfulness(result.getFaithfulness());
        response.setAnswerRelevancy(result.getAnswerRelevancy());
        response.setAnswerSimilarity(result.getAnswerSimilarity());
        response.setAnswerCorrectness(result.getAnswerCorrectness());
        response.setOverallScore(result.getOverallScore());
        response.setStatus(result.getStatus());
        response.setMetadata(result.getMetadata());
        response.setCreateTime(result.getCreateTime());
        response.setUpdateTime(result.getUpdateTime());
        return response;
    }

    public List<AdminRagasTaskResponse> toTaskResponseList(List<AdminRagasTaskResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(this::toTaskResponse).toList();
    }

    public AdminRagasTaskResponse toTaskResponse(AdminRagasTaskResult result) {
        if (result == null) {
            return null;
        }
        AdminRagasTaskResponse response = new AdminRagasTaskResponse();
        response.setTaskId(result.getTaskId());
        response.setQuestionId(result.getQuestionId());
        response.setDocumentId(result.getDocumentId());
        response.setTotalCount(result.getTotalCount());
        response.setSuccessCount(result.getSuccessCount());
        response.setFailureCount(result.getFailureCount());
        response.setAverageAnswerRelevancy(result.getAverageAnswerRelevancy());
        response.setAverageFaithfulness(result.getAverageFaithfulness());
        response.setAverageContextPrecision(result.getAverageContextPrecision());
        response.setAverageContextRecall(result.getAverageContextRecall());
        response.setStatus(result.getStatus());
        response.setErrorMessage(result.getErrorMessage());
        response.setCreateTime(result.getCreateTime());
        response.setDetails(result.getDetails() == null
                ? Collections.emptyList()
                : result.getDetails().stream()
                .map(item -> new AdminRagasTaskResponse.DetailItemResponse(
                        item.getRagasId(),
                        item.getSegmentId(),
                        item.getQuestionId(),
                        item.getDocumentId(),
                        item.getStatus(),
                        item.getErrorMessage(),
                        item.getAnswerRelevancy(),
                        item.getFaithfulness(),
                        item.getContextPrecision(),
                        item.getContextRecall(),
                        item.getOverallScore(),
                        item.getRagasQuestion(),
                        item.getStandardAnswer(),
                        item.getGeneratedAnswer()
                ))
                .toList());
        return response;
    }

    public AdminRagasTaskDetailResponse toTaskDetailResponse(AdminRagasTaskDetailResult result) {
        if (result == null) {
            return null;
        }
        AdminRagasTaskDetailResponse response = new AdminRagasTaskDetailResponse();
        response.setTaskId(result.getTaskId());
        response.setQuestionId(result.getQuestionId());
        response.setDocumentId(result.getDocumentId());
        response.setTotalCount(result.getTotalCount());
        response.setSuccessCount(result.getSuccessCount());
        response.setFailureCount(result.getFailureCount());
        response.setAverageAnswerRelevancy(result.getAverageAnswerRelevancy());
        response.setAverageFaithfulness(result.getAverageFaithfulness());
        response.setAverageContextPrecision(result.getAverageContextPrecision());
        response.setAverageContextRecall(result.getAverageContextRecall());
        response.setStatus(result.getStatus());
        response.setErrorMessage(result.getErrorMessage());
        response.setCreateTime(result.getCreateTime());
        response.setDetails(result.getDetails() == null
                ? Collections.emptyList()
                : result.getDetails().stream()
                .map(item -> new AdminRagasTaskDetailResponse.DetailItemResponse(
                        item.getRagasId(),
                        item.getSegmentId(),
                        item.getQuestionId(),
                        item.getDocumentId(),
                        item.getStatus(),
                        item.getErrorMessage(),
                        item.getAnswerRelevancy(),
                        item.getFaithfulness(),
                        item.getContextPrecision(),
                        item.getContextRecall(),
                        item.getOverallScore(),
                        item.getRagasQuestion(),
                        item.getStandardAnswer(),
                        item.getGeneratedAnswer()
                ))
                .toList());
        return response;
    }

    public AdminRagasTaskPageResponse toTaskPageResponse(AdminRagasTaskPageResult result) {
        if (result == null) {
            return null;
        }
        return new AdminRagasTaskPageResponse(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                toTaskResponseList(result.getRecords())
        );
    }
}
