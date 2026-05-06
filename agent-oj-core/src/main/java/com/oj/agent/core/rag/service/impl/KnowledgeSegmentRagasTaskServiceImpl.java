package com.oj.agent.core.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentRagasTaskMapper;
import com.oj.agent.core.rag.model.command.KnowledgeSegmentRagasTaskCreateCommand;
import com.oj.agent.core.rag.model.dto.KnowledgeSegmentRagasTaskDetailDTO;
import com.oj.agent.core.rag.model.entity.KnowledgeSegmentRagasTask;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentRagasTaskDetailQuery;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentRagasTaskListQuery;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentRagasTaskPageQuery;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskDetailItemResult;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskDetailResult;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskPageResult;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskResult;
import com.oj.agent.core.rag.service.KnowledgeSegmentRagasTaskService;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class KnowledgeSegmentRagasTaskServiceImpl
        extends ServiceImpl<KnowledgeSegmentRagasTaskMapper, KnowledgeSegmentRagasTask>
        implements KnowledgeSegmentRagasTaskService {

    private static final TypeReference<List<KnowledgeSegmentRagasTaskDetailDTO>> TASK_DETAIL_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final long DEFAULT_PAGE_CURRENT = 1L;

    private static final long DEFAULT_PAGE_SIZE = 20L;

    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    public KnowledgeSegmentRagasTaskResult createTask(KnowledgeSegmentRagasTaskCreateCommand command) {
        KnowledgeSegmentRagasTask task = new KnowledgeSegmentRagasTask();
        task.setQuestionId(command.getQuestionId());
        task.setDocumentId(command.getDocumentId());
        task.setTotalCount(command.getTotalCount());
        task.setSuccessCount(command.getSuccessCount());
        task.setFailureCount(command.getFailureCount());
        task.setAverageAnswerRelevancy(command.getAverageAnswerRelevancy());
        task.setAverageFaithfulness(command.getAverageFaithfulness());
        task.setAverageContextPrecision(command.getAverageContextPrecision());
        task.setAverageContextRecall(command.getAverageContextRecall());
        task.setStatus(command.getStatus());
        task.setErrorMessage(command.getErrorMessage());
        task.setDetailsJson(writeTaskDetails(command.getDetails()));
        task.setIsDelete(0);
        if (!save(task)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "save ragas task failed");
        }
        return toTaskResult(task, toTaskDetailItemResults(command.getDetails()));
    }

    @Override
    public List<KnowledgeSegmentRagasTaskResult> listTasks(KnowledgeSegmentRagasTaskListQuery query) {
        Long questionId = query == null ? null : query.getQuestionId();
        Long documentId = query == null ? null : query.getDocumentId();
        if (questionId == null || questionId <= 0 || documentId == null || documentId <= 0) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<KnowledgeSegmentRagasTask>()
                .eq(KnowledgeSegmentRagasTask::getQuestionId, questionId)
                .eq(KnowledgeSegmentRagasTask::getDocumentId, documentId)
                .eq(KnowledgeSegmentRagasTask::getIsDelete, 0)
                .orderByDesc(KnowledgeSegmentRagasTask::getId)).stream()
                .map(this::toTaskResult)
                .toList();
    }

    @Override
    public KnowledgeSegmentRagasTaskPageResult pageTasks(KnowledgeSegmentRagasTaskPageQuery query) {
        long current = query.getCurrent() == null || query.getCurrent() <= 0 ? DEFAULT_PAGE_CURRENT : query.getCurrent();
        long size = query.getSize() == null || query.getSize() <= 0 ? DEFAULT_PAGE_SIZE : query.getSize();
        String status = StringUtils.hasText(query.getStatus()) ? query.getStatus().trim() : null;
        Page<KnowledgeSegmentRagasTask> pageResult = page(
                new Page<>(current, size),
                new LambdaQueryWrapper<KnowledgeSegmentRagasTask>()
                        .eq(KnowledgeSegmentRagasTask::getIsDelete, 0)
                        .ge(query.getStartTime() != null, KnowledgeSegmentRagasTask::getCreateTime, query.getStartTime())
                        .le(query.getEndTime() != null, KnowledgeSegmentRagasTask::getCreateTime, query.getEndTime())
                        .eq(StringUtils.hasText(status), KnowledgeSegmentRagasTask::getStatus, status)
                        .orderByDesc(KnowledgeSegmentRagasTask::getId)
        );
        return new KnowledgeSegmentRagasTaskPageResult(
                current,
                size,
                pageResult.getTotal(),
                CollectionUtils.isEmpty(pageResult.getRecords())
                        ? List.of()
                        : pageResult.getRecords().stream().map(this::toTaskResult).toList()
        );
    }

    @Override
    public KnowledgeSegmentRagasTaskDetailResult getTaskDetail(KnowledgeSegmentRagasTaskDetailQuery query) {
        Long taskId = query == null ? null : query.getTaskId();
        if (taskId == null || taskId <= 0) {
            return null;
        }
        KnowledgeSegmentRagasTask task = getOne(new LambdaQueryWrapper<KnowledgeSegmentRagasTask>()
                .eq(KnowledgeSegmentRagasTask::getId, taskId)
                .eq(KnowledgeSegmentRagasTask::getIsDelete, 0)
                .last("LIMIT 1"));
        return task == null ? null : toTaskDetailResult(task);
    }

    private List<KnowledgeSegmentRagasTaskDetailItemResult> parseTaskDetails(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return List.of();
        }
        try {
            List<KnowledgeSegmentRagasTaskDetailDTO> details = objectMapper.readValue(detailsJson, TASK_DETAIL_TYPE_REFERENCE);
            if (CollectionUtils.isEmpty(details)) {
                return List.of();
            }
            List<KnowledgeSegmentRagasTaskDetailItemResult> result = new ArrayList<>(details.size());
            for (KnowledgeSegmentRagasTaskDetailDTO detail : details) {
                result.add(new KnowledgeSegmentRagasTaskDetailItemResult(
                        detail.getRagasId(),
                        detail.getSegmentId(),
                        detail.getQuestionId(),
                        detail.getDocumentId(),
                        detail.getStatus(),
                        detail.getErrorMessage(),
                        detail.getAnswerRelevancy(),
                        detail.getFaithfulness(),
                        detail.getContextPrecision(),
                        detail.getContextRecall(),
                        detail.getOverallScore(),
                        detail.getRagasQuestion(),
                        detail.getStandardAnswer(),
                        detail.getGeneratedAnswer()
                ));
            }
            return result;
        } catch (Exception exception) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "parse ragas task details failed");
        }
    }

    private String writeTaskDetails(List<KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand> details) {
        List<KnowledgeSegmentRagasTaskDetailDTO> values = CollectionUtils.isEmpty(details)
                ? List.of()
                : details.stream()
                .map(detail -> new KnowledgeSegmentRagasTaskDetailDTO(
                        detail.getRagasId(),
                        detail.getSegmentId(),
                        detail.getQuestionId(),
                        detail.getDocumentId(),
                        detail.getStatus(),
                        detail.getErrorMessage(),
                        detail.getAnswerRelevancy(),
                        detail.getFaithfulness(),
                        detail.getContextPrecision(),
                        detail.getContextRecall(),
                        detail.getOverallScore(),
                        detail.getRagasQuestion(),
                        detail.getStandardAnswer(),
                        detail.getGeneratedAnswer()
                ))
                .toList();
        try {
            return JsonUtils.toJson(values);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private List<KnowledgeSegmentRagasTaskDetailItemResult> toTaskDetailItemResults(List<KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand> details) {
        if (CollectionUtils.isEmpty(details)) {
            return List.of();
        }
        return details.stream()
                .map(detail -> new KnowledgeSegmentRagasTaskDetailItemResult(
                        detail.getRagasId(),
                        detail.getSegmentId(),
                        detail.getQuestionId(),
                        detail.getDocumentId(),
                        detail.getStatus(),
                        detail.getErrorMessage(),
                        detail.getAnswerRelevancy(),
                        detail.getFaithfulness(),
                        detail.getContextPrecision(),
                        detail.getContextRecall(),
                        detail.getOverallScore(),
                        detail.getRagasQuestion(),
                        detail.getStandardAnswer(),
                        detail.getGeneratedAnswer()
                ))
                .toList();
    }

    private KnowledgeSegmentRagasTaskResult toTaskResult(KnowledgeSegmentRagasTask task) {
        return toTaskResult(task, parseTaskDetails(task.getDetailsJson()));
    }

    private KnowledgeSegmentRagasTaskResult toTaskResult(KnowledgeSegmentRagasTask task,
                                                         List<KnowledgeSegmentRagasTaskDetailItemResult> details) {
        return new KnowledgeSegmentRagasTaskResult(
                task.getId(),
                task.getQuestionId(),
                task.getDocumentId(),
                task.getTotalCount(),
                task.getSuccessCount(),
                task.getFailureCount(),
                task.getAverageAnswerRelevancy(),
                task.getAverageFaithfulness(),
                task.getAverageContextPrecision(),
                task.getAverageContextRecall(),
                task.getStatus(),
                task.getErrorMessage(),
                task.getCreateTime(),
                details
        );
    }

    private KnowledgeSegmentRagasTaskDetailResult toTaskDetailResult(KnowledgeSegmentRagasTask task) {
        return new KnowledgeSegmentRagasTaskDetailResult(
                task.getId(),
                task.getQuestionId(),
                task.getDocumentId(),
                task.getTotalCount(),
                task.getSuccessCount(),
                task.getFailureCount(),
                task.getAverageAnswerRelevancy(),
                task.getAverageFaithfulness(),
                task.getAverageContextPrecision(),
                task.getAverageContextRecall(),
                task.getStatus(),
                task.getErrorMessage(),
                task.getCreateTime(),
                parseTaskDetails(task.getDetailsJson())
        );
    }
}
