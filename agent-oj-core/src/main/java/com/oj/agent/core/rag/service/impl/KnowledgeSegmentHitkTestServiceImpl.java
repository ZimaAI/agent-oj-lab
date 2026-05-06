package com.oj.agent.core.rag.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.oj.agent.common.constant.MetadataKeyConstant;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.rag.model.entity.KnowledgeSegmentHitkTask;
import com.oj.agent.core.rag.model.dto.KnowledgeSegmentHitkTaskDetailDTO;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.rag.service.KnowledgeSegmentHitkTaskService;
import com.oj.agent.core.rag.service.KnowledgeSegmentHitkTestService;
import com.oj.agent.core.workflow.model.result.AnswerHitKGraphResult;
import com.oj.agent.core.workflow.model.result.AnswerRagSegmentResult;
import com.oj.agent.core.workflow.service.AnswerHitkGraphService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KnowledgeSegmentHitkTestServiceImpl implements KnowledgeSegmentHitkTestService {

    private final AnswerHitkGraphService answerHitkGraphService;

    private final KnowledgeSegmentHitkTaskService knowledgeSegmentHitkTaskService;

    private final AlgorithmQuestionService algorithmQuestionService;

    public KnowledgeSegmentHitkTestServiceImpl(AnswerHitkGraphService answerHitkGraphService,
                                               KnowledgeSegmentHitkTaskService knowledgeSegmentHitkTaskService,
                                               AlgorithmQuestionService algorithmQuestionService) {
        this.answerHitkGraphService = answerHitkGraphService;
        this.knowledgeSegmentHitkTaskService = knowledgeSegmentHitkTaskService;
        this.algorithmQuestionService = algorithmQuestionService;
    }

    @Override
    public KnowledgeSegmentHitkTask createTask(Long questionId, Long documentId, List<KnowledgeSegment> segments) {
        if (questionId == null || questionId <= 0 || documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("questionId and documentId must be positive numbers");
        }
        if (CollectionUtils.isEmpty(segments)) {
            throw new IllegalArgumentException("segments must not be empty");
        }

        List<KnowledgeSegmentHitkTaskDetailDTO> details = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int hitCount = 0;
        AlgorithmQuestionResult currentQuestion = loadCurrentQuestion(questionId);

        // 逐个执行 Hit@K 图，收集检索结果并统计命中情况。
        for (KnowledgeSegment segment : segments) {
            try {
                AnswerHitKGraphResult graphResult = answerHitkGraphService.execute(segment.getHitkQuestion(), currentQuestion);
                List<AnswerRagSegmentResult> retrievedSegments = graphResult.segments() == null
                        ? List.of()
                        : graphResult.segments();
                List<Long> retrievedSegmentIds = retrievedSegments.stream()
                        .map(AnswerRagSegmentResult::getId)
                        .filter(Objects::nonNull)
                        .toList();
                List<KnowledgeSegmentHitkTaskDetailDTO.RetrievedSegmentScoreDTO> retrievedSegmentScores = retrievedSegments.stream()
                        .filter(retrievedSegment -> retrievedSegment != null && retrievedSegment.getId() != null)
                        .map(retrievedSegment -> new KnowledgeSegmentHitkTaskDetailDTO.RetrievedSegmentScoreDTO(
                                retrievedSegment.getId(),
                                retrievedSegment.getRawSimilarity(),
                                retrievedSegment.getSimilarityScore(),
                                retrievedSegment.getRrfScore(),
                                retrievedSegment.getFinalScore()
                        ))
                        .toList();
                // 记录问题重写节点输出，便于后续核对召回命中链路。
                List<String> rewrittenQuestions = graphResult.rewrittenQuestions() == null
                        ? List.of()
                        : graphResult.rewrittenQuestions();
                boolean hit = isSegmentHit(segment, retrievedSegments, retrievedSegmentIds);
                if (hit) {
                    hitCount++;
                }
                details.add(new KnowledgeSegmentHitkTaskDetailDTO(
                        segment.getId(),
                        segment.getHitkQuestion(),
                        rewrittenQuestions,
                        retrievedSegmentIds,
                        retrievedSegmentScores,
                        hit
                ));
            } catch (Exception exception) {
                errors.add("segmentId=" + segment.getId() + ": " + exception.getMessage());
                details.add(new KnowledgeSegmentHitkTaskDetailDTO(
                        segment.getId(),
                        segment.getHitkQuestion(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false
                ));
            }
        }

        int totalCount = segments.size();
        int missCount = totalCount - hitCount;
        KnowledgeSegmentHitkTask task = new KnowledgeSegmentHitkTask();
        task.setQuestionId(questionId);
        task.setDocumentId(documentId);
        task.setTotalCount(totalCount);
        task.setHitCount(hitCount);
        task.setMissCount(missCount);
        task.setHitRate(calculateHitRate(hitCount, totalCount));
        task.setStatus(errors.isEmpty() ? "COMPLETED" : "FAILED");
        task.setErrorMessage(errors.isEmpty() ? null : String.join("; ", errors));
        task.setDetailsJson(writeDetails(details));
        task.setIsDelete(0);

        if (!knowledgeSegmentHitkTaskService.save(task)) {
            throw new IllegalStateException("save hitk task failed");
        }
        return task;
    }

    @Override
    public List<KnowledgeSegmentHitkTask> listTasks(Long questionId, Long documentId) {
        return knowledgeSegmentHitkTaskService.listTasks(questionId, documentId);
    }

    // 统一计算命中率，保留四位小数。
    private BigDecimal calculateHitRate(int hitCount, int totalCount) {
        if (totalCount <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(hitCount)
                .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP);
    }

    // 统一命中判断：直接命中当前分段，或召回命中当前分段的父分段。
    private boolean isSegmentHit(KnowledgeSegment segment,
                                 List<AnswerRagSegmentResult> retrievedSegments,
                                 List<Long> retrievedSegmentIds) {
        if (segment == null) {
            return false;
        }
        if (segment.getId() != null && retrievedSegmentIds.contains(segment.getId())) {
            return true;
        }
        String parentChunkId = parseParentChunkId(segment.getMetadata());
        if (parentChunkId == null || parentChunkId.isBlank()) {
            return false;
        }
        return retrievedSegments.stream()
                .map(AnswerRagSegmentResult::getChunkId)
                .filter(Objects::nonNull)
                .anyMatch(parentChunkId::equals);
    }

    // 将任务明细序列化为 JSON，供后续列表查询直接回放。
    private String writeDetails(List<KnowledgeSegmentHitkTaskDetailDTO> details) {
        try {
            return JsonUtils.getObjectMapper().writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("serialize hitk task details failed", exception);
        }
    }

    // 读取当前题目快照，供答疑检索节点执行题目范围约束。
    private AlgorithmQuestionResult loadCurrentQuestion(Long questionId) {
        if (questionId == null || algorithmQuestionService == null) {
            return null;
        }
        try {
            return algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(questionId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // 从分段元数据中解析父分段 chunkId。
    private String parseParentChunkId(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> metadata = JsonUtils.getObjectMapper().readValue(metadataJson, Map.class);
            Object parentChunkId = metadata.get(MetadataKeyConstant.PARENT_CHUNK_ID);
            if (parentChunkId == null) {
                return null;
            }
            String normalized = String.valueOf(parentChunkId).trim();
            return normalized.isEmpty() ? null : normalized;
        } catch (Exception exception) {
            return null;
        }
    }
}
