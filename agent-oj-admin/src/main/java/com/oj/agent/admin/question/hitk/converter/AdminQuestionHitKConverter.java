package com.oj.agent.admin.question.hitk.converter;

import com.oj.agent.admin.question.hitk.model.command.AdminHitKQuestionGenerateCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKQuestionUpdateCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKTaskBatchDeleteCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKTaskRemarkUpdateCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKTestCreateCommand;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTaskDetailQuery;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTaskPageQuery;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTaskStatisticsQuery;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTestListQuery;
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
import com.oj.agent.admin.question.hitk.model.result.AdminHitKQuestionBatchResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskBatchDeleteResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskDetailResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskPageResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskStatisticsResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
public class AdminQuestionHitKConverter {

    public AdminHitKQuestionGenerateCommand toGenerateCommand(Long questionId,
                                                              Long docId,
                                                              AdminHitKQuestionGenerateRequest request) {
        return new AdminHitKQuestionGenerateCommand(
                questionId,
                docId,
                request == null ? null : request.getSegmentIds()
        );
    }

    public AdminHitKQuestionUpdateCommand toUpdateCommand(Long questionId,
                                                          Long docId,
                                                          AdminHitKQuestionUpdateRequest request) {
        AdminHitKQuestionUpdateCommand command = new AdminHitKQuestionUpdateCommand();
        command.setQuestionId(questionId);
        command.setDocId(docId);
        if (request != null && request.getUpdates() != null) {
            command.setUpdates(request.getUpdates().stream().map(item -> {
                AdminHitKQuestionUpdateCommand.UpdateItem updateItem = new AdminHitKQuestionUpdateCommand.UpdateItem();
                updateItem.setSegmentId(item.getSegmentId());
                updateItem.setHitKQuestion(item.getHitkQuestion());
                return updateItem;
            }).toList());
        }
        return command;
    }

    public AdminHitKTestCreateCommand toCreateCommand(Long questionId,
                                                      Long docId,
                                                      AdminHitKTestCreateRequest request) {
        return new AdminHitKTestCreateCommand(
                questionId,
                docId,
                request == null ? null : request.getSegmentIds()
        );
    }

    public AdminHitKTestListQuery toTestListQuery(Long questionId, Long docId) {
        return new AdminHitKTestListQuery(questionId, docId);
    }

    public AdminHitKTaskDetailQuery toTaskDetailQuery(Long questionId, Long docId, Long taskId) {
        return new AdminHitKTaskDetailQuery(questionId, docId, taskId);
    }

    public AdminHitKTaskPageQuery toTaskPageQuery(Long current,
                                                  Long size,
                                                  LocalDateTime startTime,
                                                  LocalDateTime endTime,
                                                  String status) {
        return new AdminHitKTaskPageQuery(current, size, startTime, endTime, status);
    }

    public AdminHitKTaskStatisticsQuery toTaskStatisticsQuery(AdminHitKTaskBatchStatisticsRequest request) {
        return new AdminHitKTaskStatisticsQuery(request == null ? null : request.getTaskIds());
    }

    public AdminHitKTaskBatchDeleteCommand toTaskBatchDeleteCommand(AdminHitKTaskBatchDeleteRequest request) {
        return new AdminHitKTaskBatchDeleteCommand(request == null ? null : request.getTaskIds());
    }

    public AdminHitKTaskRemarkUpdateCommand toTaskRemarkUpdateCommand(Long taskId,
                                                                      AdminHitKTaskRemarkUpdateRequest request) {
        return new AdminHitKTaskRemarkUpdateCommand(taskId, request == null ? null : request.getRemark());
    }

    public AdminHitKQuestionBatchResponse toQuestionBatchResponse(AdminHitKQuestionBatchResult result) {
        if (result == null) {
            return null;
        }
        List<AdminHitKQuestionBatchResponse.ItemResponse> responses = result.getResults() == null
                ? Collections.emptyList()
                : result.getResults().stream()
                .map(item -> new AdminHitKQuestionBatchResponse.ItemResponse(
                        item.getSegmentId(),
                        item.getSuccess(),
                        item.getMessage(),
                        item.getHitKQuestion()
                ))
                .toList();
        return new AdminHitKQuestionBatchResponse(result.getSuccessCount(), result.getFailureCount(), responses);
    }

    public List<AdminHitKTaskResponse> toTaskResponseList(List<AdminHitKTaskResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(this::toTaskResponse).toList();
    }

    public AdminHitKTaskResponse toTaskResponse(AdminHitKTaskResult result) {
        if (result == null) {
            return null;
        }
        AdminHitKTaskResponse response = new AdminHitKTaskResponse();
        response.setTaskId(result.getTaskId());
        response.setQuestionId(result.getQuestionId());
        response.setDocumentId(result.getDocumentId());
        response.setTotalCount(result.getTotalCount());
        response.setHitCount(result.getHitCount());
        response.setMissCount(result.getMissCount());
        response.setHitRate(result.getHitRate());
        response.setStatus(result.getStatus());
        response.setErrorMessage(result.getErrorMessage());
        response.setRemark(result.getRemark());
        response.setCreateTime(result.getCreateTime());
        response.setDetails(result.getDetails() == null
                ? Collections.emptyList()
                : result.getDetails().stream()
                .map(item -> new AdminHitKTaskResponse.DetailItemResponse(
                        item.getSegmentId(),
                        item.getHitKQuestion(),
                        item.getRetrievedSegmentIds(),
                        item.getHit()
                ))
                .toList());
        return response;
    }

    public AdminHitKTaskDetailResponse toTaskDetailResponse(AdminHitKTaskDetailResult result) {
        if (result == null) {
            return null;
        }
        AdminHitKTaskDetailResponse response = new AdminHitKTaskDetailResponse();
        response.setTaskId(result.getTaskId());
        response.setQuestionId(result.getQuestionId());
        response.setDocumentId(result.getDocumentId());
        response.setTotalCount(result.getTotalCount());
        response.setHitCount(result.getHitCount());
        response.setMissCount(result.getMissCount());
        response.setHitRate(result.getHitRate());
        response.setStatus(result.getStatus());
        response.setErrorMessage(result.getErrorMessage());
        response.setRemark(result.getRemark());
        response.setCreateTime(result.getCreateTime());
        response.setSegmentResults(result.getSegmentResults() == null
                ? Collections.emptyList()
                : result.getSegmentResults().stream()
                .map(item -> new AdminHitKTaskDetailResponse.SegmentResultItemResponse(
                        item.getSegmentId(),
                        item.getSegmentText(),
                        item.getHitKQuestion(),
                        item.getRewrittenQuestions(),
                        item.getRetrievedSegments() == null
                                ? Collections.emptyList()
                                : item.getRetrievedSegments().stream()
                                .map(retrieved -> new AdminHitKTaskDetailResponse.RetrievedSegmentItemResponse(
                                        retrieved.getSegmentId(),
                                        retrieved.getDocumentId(),
                                        retrieved.getChunkOrder(),
                                        retrieved.getText(),
                                        retrieved.getRawSimilarity(),
                                        retrieved.getSimilarityScore(),
                                        retrieved.getRrfScore(),
                                        retrieved.getFinalScore()
                                ))
                                .toList(),
                        item.getHit()
                ))
                .toList());
        return response;
    }

    public AdminHitKTaskPageResponse toTaskPageResponse(AdminHitKTaskPageResult result) {
        if (result == null) {
            return null;
        }
        return new AdminHitKTaskPageResponse(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                toTaskResponseList(result.getRecords())
        );
    }

    public AdminHitKTaskStatisticsResponse toTaskStatisticsResponse(AdminHitKTaskStatisticsResult result) {
        if (result == null) {
            return null;
        }
        AdminHitKTaskStatisticsResponse response = new AdminHitKTaskStatisticsResponse();
        response.setTaskCount(result.getTaskCount());
        response.setAverageHitRate(result.getAverageHitRate());
        response.setAverageTotalCount(result.getAverageTotalCount());
        response.setAverageHitCount(result.getAverageHitCount());
        response.setAverageMissCount(result.getAverageMissCount());
        response.setSumTotalCount(result.getSumTotalCount());
        response.setSumHitCount(result.getSumHitCount());
        response.setSumMissCount(result.getSumMissCount());
        response.setOverallHitRate(result.getOverallHitRate());
        return response;
    }

    public AdminHitKTaskBatchDeleteResponse toTaskBatchDeleteResponse(AdminHitKTaskBatchDeleteResult result) {
        if (result == null) {
            return null;
        }
        List<AdminHitKTaskBatchDeleteResponse.ItemResponse> responses = result.getResults() == null
                ? Collections.emptyList()
                : result.getResults().stream()
                .map(item -> new AdminHitKTaskBatchDeleteResponse.ItemResponse(
                        item.getTaskId(),
                        item.getSuccess(),
                        item.getMessage()
                ))
                .toList();
        return new AdminHitKTaskBatchDeleteResponse(result.getSuccessCount(), result.getFailureCount(), responses);
    }
}
