package com.oj.agent.admin.question.hitk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKQuestionGenerateCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKQuestionUpdateCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKTaskBatchDeleteCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKTaskRemarkUpdateCommand;
import com.oj.agent.admin.question.hitk.model.command.AdminHitKTestCreateCommand;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTaskDetailQuery;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTaskPageQuery;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTaskStatisticsQuery;
import com.oj.agent.admin.question.hitk.model.query.AdminHitKTestListQuery;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKQuestionBatchResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskBatchDeleteResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskDetailResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskPageResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskResult;
import com.oj.agent.admin.question.hitk.model.result.AdminHitKTaskStatisticsResult;
import com.oj.agent.admin.question.hitk.service.AdminQuestionHitKService;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.question.model.entity.AlgorithmQuestionKnowledgeDocument;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.question.service.AlgorithmQuestionKnowledgeDocumentService;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentHitkTaskMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentMapper;
import com.oj.agent.core.rag.model.dto.KnowledgeSegmentHitkTaskDetailDTO;
import com.oj.agent.core.rag.model.entity.KnowledgeDocument;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.rag.model.entity.KnowledgeSegmentHitkTask;
import com.oj.agent.core.rag.service.KnowledgeSegmentHitkQuestionService;
import com.oj.agent.core.rag.service.KnowledgeSegmentHitkTestService;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AdminQuestionHitKServiceImpl implements AdminQuestionHitKService {

    private static final TypeReference<List<KnowledgeSegmentHitkTaskDetailDTO>> DETAIL_TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final long DEFAULT_TASK_PAGE_CURRENT = 1L;
    private static final long DEFAULT_TASK_PAGE_SIZE = 20L;
    private static final long MAX_TASK_PAGE_SIZE = 100L;
    private static final int REMARK_MAX_LENGTH = 1024;
    private static final BigDecimal ZERO_SCALE_RATE = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final AlgorithmQuestionService algorithmQuestionService;

    private final AlgorithmQuestionKnowledgeDocumentService algorithmQuestionKnowledgeDocumentService;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    private final KnowledgeSegmentMapper knowledgeSegmentMapper;

    private final KnowledgeSegmentHitkTaskMapper knowledgeSegmentHitkTaskMapper;

    private final KnowledgeSegmentHitkQuestionService knowledgeSegmentHitkQuestionService;

    private final KnowledgeSegmentHitkTestService knowledgeSegmentHitkTestService;

    public AdminQuestionHitKServiceImpl(AlgorithmQuestionService algorithmQuestionService,
                                        AlgorithmQuestionKnowledgeDocumentService algorithmQuestionKnowledgeDocumentService,
                                        KnowledgeDocumentMapper knowledgeDocumentMapper,
                                        KnowledgeSegmentMapper knowledgeSegmentMapper,
                                        KnowledgeSegmentHitkTaskMapper knowledgeSegmentHitkTaskMapper,
                                        KnowledgeSegmentHitkQuestionService knowledgeSegmentHitkQuestionService,
                                        KnowledgeSegmentHitkTestService knowledgeSegmentHitkTestService) {
        this.algorithmQuestionService = algorithmQuestionService;
        this.algorithmQuestionKnowledgeDocumentService = algorithmQuestionKnowledgeDocumentService;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeSegmentMapper = knowledgeSegmentMapper;
        this.knowledgeSegmentHitkTaskMapper = knowledgeSegmentHitkTaskMapper;
        this.knowledgeSegmentHitkQuestionService = knowledgeSegmentHitkQuestionService;
        this.knowledgeSegmentHitkTestService = knowledgeSegmentHitkTestService;
    }

    @Override
    public AdminHitKQuestionBatchResult generateHitKQuestions(AdminHitKQuestionGenerateCommand command) {
        validateQuestionAndDocument(command.getQuestionId(), command.getDocId(), false);
        List<Long> segmentIds = normalizeSegmentIds(command.getSegmentIds());
        Map<Long, KnowledgeSegment> segmentMap = loadSegmentMap(command.getDocId(), segmentIds);

        List<CompletableFuture<AdminHitKQuestionBatchResult.ItemResult>> futures = segmentIds.stream()
                .map(segmentId -> CompletableFuture.supplyAsync(
                        () -> generateSingleResult(segmentId, segmentMap.get(segmentId))
                ))
                .toList();

        List<AdminHitKQuestionBatchResult.ItemResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        return buildBatchResponse(results);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminHitKQuestionBatchResult updateHitKQuestions(AdminHitKQuestionUpdateCommand command) {
        validateQuestionAndDocument(command.getQuestionId(), command.getDocId(), false);
        List<Long> segmentIds = command.getUpdates().stream()
                .map(AdminHitKQuestionUpdateCommand.UpdateItem::getSegmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, KnowledgeSegment> segmentMap = loadSegmentMap(command.getDocId(), segmentIds);

        List<AdminHitKQuestionBatchResult.ItemResult> results = command.getUpdates().stream()
                .map(item -> updateSingleResult(item, segmentMap.get(item.getSegmentId())))
                .toList();
        return buildBatchResponse(results);
    }

    @Override
    public AdminHitKTaskResult createHitKTest(AdminHitKTestCreateCommand command) {
        validateQuestionAndDocument(command.getQuestionId(), command.getDocId(), true);
        List<Long> segmentIds = normalizeSegmentIds(command.getSegmentIds());
        Map<Long, KnowledgeSegment> segmentMap = loadSegmentMap(command.getDocId(), segmentIds);
        List<KnowledgeSegment> orderedSegments = requireOrderedSegments(segmentIds, segmentMap);
        for (KnowledgeSegment segment : orderedSegments) {
            if (!StringUtils.hasText(segment.getHitkQuestion())) {
                throw new ValidationException(
                        SecurityErrorCode.TABLE_001.getCode(),
                        "segment " + segment.getId() + " hitkQuestion is blank"
                );
            }
        }
        KnowledgeSegmentHitkTask task = knowledgeSegmentHitkTestService.createTask(
                command.getQuestionId(),
                command.getDocId(),
                orderedSegments
        );
        return toTaskResult(task);
    }

    @Override
    public List<AdminHitKTaskResult> listHitKTests(AdminHitKTestListQuery query) {
        validateQuestionAndDocument(query.getQuestionId(), query.getDocId(), false);
        return knowledgeSegmentHitkTestService.listTasks(query.getQuestionId(), query.getDocId()).stream()
                .map(this::toTaskResult)
                .toList();
    }

    @Override
    public AdminHitKTaskDetailResult getHitKTestDetail(AdminHitKTaskDetailQuery query) {
        validateQuestionAndDocument(query.getQuestionId(), query.getDocId(), false);
        validatePositiveId(query.getTaskId(), "taskId");
        KnowledgeSegmentHitkTask task = knowledgeSegmentHitkTaskMapper.selectOne(new LambdaQueryWrapper<KnowledgeSegmentHitkTask>()
                .eq(KnowledgeSegmentHitkTask::getId, query.getTaskId())
                .eq(KnowledgeSegmentHitkTask::getQuestionId, query.getQuestionId())
                .eq(KnowledgeSegmentHitkTask::getDocumentId, query.getDocId())
                .eq(KnowledgeSegmentHitkTask::getIsDelete, 0)
                .last("LIMIT 1"));
        if (task == null) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "hitk task does not exist");
        }
        return toTaskDetailResult(task);
    }

    @Override
    public AdminHitKTaskPageResult pageHitKTasks(AdminHitKTaskPageQuery query) {
        if (query.getStartTime() != null && query.getEndTime() != null && query.getStartTime().isAfter(query.getEndTime())) {
            throw new IllegalArgumentException("startTime must be earlier than or equal to endTime");
        }
        long normalizedCurrent = normalizeTaskPageCurrent(query.getCurrent());
        long normalizedSize = normalizeTaskPageSize(query.getSize());
        String normalizedStatus = normalizeStatus(query.getStatus());
        LambdaQueryWrapper<KnowledgeSegmentHitkTask> queryWrapper = buildTaskFilterWrapper(
                query.getStartTime(),
                query.getEndTime(),
                normalizedStatus
        );
        Page<KnowledgeSegmentHitkTask> pageResult = knowledgeSegmentHitkTaskMapper.selectPage(
                new Page<>(normalizedCurrent, normalizedSize),
                queryWrapper
        );
        List<AdminHitKTaskResult> records = CollectionUtils.isEmpty(pageResult.getRecords())
                ? List.of()
                : pageResult.getRecords().stream().map(this::toTaskResult).toList();
        return new AdminHitKTaskPageResult(normalizedCurrent, normalizedSize, pageResult.getTotal(), records);
    }

    @Override
    public AdminHitKTaskStatisticsResult statisticsHitKTasks(AdminHitKTaskStatisticsQuery query) {
        List<Long> taskIds = normalizeTaskIds(query == null ? null : query.getTaskIds());
        List<KnowledgeSegmentHitkTask> tasks = knowledgeSegmentHitkTaskMapper.selectList(
                new LambdaQueryWrapper<KnowledgeSegmentHitkTask>()
                        .in(KnowledgeSegmentHitkTask::getId, taskIds)
                        .eq(KnowledgeSegmentHitkTask::getIsDelete, 0)
        );
        return buildTaskStatistics(tasks);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminHitKTaskBatchDeleteResult batchDeleteHitKTasks(AdminHitKTaskBatchDeleteCommand command) {
        List<Long> normalizedIds = normalizeTaskIds(command.getTaskIds());
        List<AdminHitKTaskBatchDeleteResult.ItemResult> results = new ArrayList<>(normalizedIds.size());
        int successCount = 0;
        for (Long taskId : normalizedIds) {
            KnowledgeSegmentHitkTask existingTask = knowledgeSegmentHitkTaskMapper.selectById(taskId);
            if (existingTask == null || Integer.valueOf(1).equals(existingTask.getIsDelete())) {
                results.add(new AdminHitKTaskBatchDeleteResult.ItemResult(taskId, false, "task does not exist"));
                continue;
            }
            KnowledgeSegmentHitkTask updateEntity = new KnowledgeSegmentHitkTask();
            updateEntity.setId(taskId);
            updateEntity.setIsDelete(1);
            int affectedRows = knowledgeSegmentHitkTaskMapper.updateById(updateEntity);
            if (affectedRows > 0) {
                successCount++;
                results.add(new AdminHitKTaskBatchDeleteResult.ItemResult(taskId, true, "deleted"));
                continue;
            }
            results.add(new AdminHitKTaskBatchDeleteResult.ItemResult(taskId, false, "delete failed"));
        }
        return new AdminHitKTaskBatchDeleteResult(successCount, normalizedIds.size() - successCount, results);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminHitKTaskResult updateHitKTaskRemark(AdminHitKTaskRemarkUpdateCommand command) {
        validatePositiveId(command.getTaskId(), "taskId");
        String normalizedRemark = normalizeRemark(command.getRemark());
        KnowledgeSegmentHitkTask existingTask = knowledgeSegmentHitkTaskMapper.selectById(command.getTaskId());
        if (existingTask == null || Integer.valueOf(1).equals(existingTask.getIsDelete())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "hitk task does not exist");
        }

        KnowledgeSegmentHitkTask updateEntity = new KnowledgeSegmentHitkTask();
        updateEntity.setId(command.getTaskId());
        updateEntity.setRemark(normalizedRemark);
        int affectedRows = knowledgeSegmentHitkTaskMapper.updateById(updateEntity);
        if (affectedRows <= 0) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "update hitk task remark failed");
        }
        existingTask.setRemark(normalizedRemark);
        return toTaskResult(existingTask);
    }

    private AdminHitKQuestionBatchResult.ItemResult generateSingleResult(Long segmentId, KnowledgeSegment segment) {
        if (segment == null) {
            return new AdminHitKQuestionBatchResult.ItemResult(segmentId, false, "segment does not exist", null);
        }
        try {
            String hitKQuestion = knowledgeSegmentHitkQuestionService.generateHitkQuestion(segment);
            updateSegmentHitkQuestion(segment.getId(), hitKQuestion);
            return new AdminHitKQuestionBatchResult.ItemResult(segment.getId(), true, "generated", hitKQuestion);
        } catch (Exception exception) {
            return new AdminHitKQuestionBatchResult.ItemResult(segment.getId(), false, exception.getMessage(), null);
        }
    }

    private AdminHitKQuestionBatchResult.ItemResult updateSingleResult(AdminHitKQuestionUpdateCommand.UpdateItem item,
                                                                       KnowledgeSegment segment) {
        if (segment == null) {
            return new AdminHitKQuestionBatchResult.ItemResult(item.getSegmentId(), false, "segment does not exist", null);
        }
        updateSegmentHitkQuestion(segment.getId(), item.getHitKQuestion().trim());
        return new AdminHitKQuestionBatchResult.ItemResult(
                segment.getId(),
                true,
                "updated",
                item.getHitKQuestion().trim()
        );
    }

    private void updateSegmentHitkQuestion(Long segmentId, String hitKQuestion) {
        KnowledgeSegment updateEntity = new KnowledgeSegment();
        updateEntity.setId(segmentId);
        updateEntity.setHitkQuestion(hitKQuestion);
        int updatedRows = knowledgeSegmentMapper.updateById(updateEntity);
        if (updatedRows <= 0) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "update hitkQuestion failed");
        }
    }

    private AdminHitKQuestionBatchResult buildBatchResponse(List<AdminHitKQuestionBatchResult.ItemResult> results) {
        int successCount = (int) results.stream().filter(item -> Boolean.TRUE.equals(item.getSuccess())).count();
        int failureCount = results.size() - successCount;
        return new AdminHitKQuestionBatchResult(successCount, failureCount, results);
    }

    private void validateQuestionAndDocument(Long questionId, Long docId, boolean requireVectorStored) {
        validatePositiveId(questionId, "questionId");
        validatePositiveId(docId, "docId");
        ensureQuestionExists(questionId);
        KnowledgeDocument document = ensureQuestionDocumentBound(questionId, docId);
        if (requireVectorStored && !Objects.equals("VECTOR_STORED", document.getStatus())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "document is not ready for hitk test");
        }
    }

    private void validatePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be a positive number");
        }
    }

    private void ensureQuestionExists(Long questionId) {
        AlgorithmQuestionResult question;
        try {
            question = algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(questionId));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "question does not exist");
        }
        if (question == null) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "question does not exist");
        }
    }

    private KnowledgeDocument ensureQuestionDocumentBound(Long questionId, Long docId) {
        List<AlgorithmQuestionKnowledgeDocument> relations = algorithmQuestionKnowledgeDocumentService.listByQuestionId(questionId);
        boolean bound = !CollectionUtils.isEmpty(relations) && relations.stream()
                .map(AlgorithmQuestionKnowledgeDocument::getDocId)
                .anyMatch(boundDocId -> Objects.equals(boundDocId, docId));
        if (!bound) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "document does not belong to question");
        }

        KnowledgeDocument document = knowledgeDocumentMapper.selectById(docId);
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "document does not exist");
        }
        return document;
    }

    private List<Long> normalizeSegmentIds(List<Long> segmentIds) {
        if (CollectionUtils.isEmpty(segmentIds)) {
            throw new IllegalArgumentException("segmentIds must not be empty");
        }
        List<Long> normalizedIds = segmentIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(normalizedIds)) {
            throw new IllegalArgumentException("segmentIds must contain at least one positive number");
        }
        return normalizedIds;
    }

    private Map<Long, KnowledgeSegment> loadSegmentMap(Long docId, List<Long> segmentIds) {
        if (CollectionUtils.isEmpty(segmentIds)) {
            return Map.of();
        }
        List<KnowledgeSegment> segments = knowledgeSegmentMapper.selectList(new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(KnowledgeSegment::getDocumentId, docId)
                .in(KnowledgeSegment::getId, segmentIds)
                .eq(KnowledgeSegment::getDeleted, 0));
        if (CollectionUtils.isEmpty(segments)) {
            return Map.of();
        }
        return segments.stream()
                .collect(Collectors.toMap(KnowledgeSegment::getId, segment -> segment, (left, right) -> left, HashMap::new));
    }

    private List<KnowledgeSegment> requireOrderedSegments(List<Long> segmentIds, Map<Long, KnowledgeSegment> segmentMap) {
        List<KnowledgeSegment> orderedSegments = new ArrayList<>();
        for (Long segmentId : segmentIds) {
            KnowledgeSegment segment = segmentMap.get(segmentId);
            if (segment == null) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "segment " + segmentId + " does not exist");
            }
            orderedSegments.add(segment);
        }
        return orderedSegments;
    }

    private AdminHitKTaskResult toTaskResult(KnowledgeSegmentHitkTask task) {
        AdminHitKTaskResult result = new AdminHitKTaskResult();
        result.setTaskId(task.getId());
        result.setQuestionId(task.getQuestionId());
        result.setDocumentId(task.getDocumentId());
        result.setTotalCount(task.getTotalCount());
        result.setHitCount(task.getHitCount());
        result.setMissCount(task.getMissCount());
        result.setHitRate(task.getHitRate());
        result.setStatus(task.getStatus());
        result.setErrorMessage(task.getErrorMessage());
        result.setRemark(task.getRemark());
        result.setCreateTime(task.getCreateTime());
        result.setDetails(parseTaskDetails(task.getDetailsJson()).stream()
                .map(detail -> new AdminHitKTaskResult.DetailItemResult(
                        detail.getSegmentId(),
                        detail.getHitkQuestion(),
                        resolveRetrievedSegmentIds(detail),
                        detail.getHit()
                ))
                .toList());
        return result;
    }

    private AdminHitKTaskDetailResult toTaskDetailResult(KnowledgeSegmentHitkTask task) {
        List<KnowledgeSegmentHitkTaskDetailDTO> taskDetails = parseTaskDetails(task.getDetailsJson());
        Map<Long, KnowledgeSegment> segmentMap = loadDetailSegmentMap(taskDetails);

        AdminHitKTaskDetailResult result = new AdminHitKTaskDetailResult();
        result.setTaskId(task.getId());
        result.setQuestionId(task.getQuestionId());
        result.setDocumentId(task.getDocumentId());
        result.setTotalCount(task.getTotalCount());
        result.setHitCount(task.getHitCount());
        result.setMissCount(task.getMissCount());
        result.setHitRate(task.getHitRate());
        result.setStatus(task.getStatus());
        result.setErrorMessage(task.getErrorMessage());
        result.setRemark(task.getRemark());
        result.setCreateTime(task.getCreateTime());
        result.setSegmentResults(taskDetails.stream()
                .map(detail -> toSegmentResultItem(detail, segmentMap))
                .toList());
        return result;
    }

    private Map<Long, KnowledgeSegment> loadDetailSegmentMap(List<KnowledgeSegmentHitkTaskDetailDTO> taskDetails) {
        List<Long> segmentIds = taskDetails.stream()
                .flatMap(detail -> {
                    List<Long> ids = new ArrayList<>();
                    if (detail.getSegmentId() != null) {
                        ids.add(detail.getSegmentId());
                    }
                    if (!CollectionUtils.isEmpty(detail.getRetrievedSegmentIds())) {
                        ids.addAll(detail.getRetrievedSegmentIds());
                    }
                    if (!CollectionUtils.isEmpty(detail.getRetrievedSegments())) {
                        ids.addAll(detail.getRetrievedSegments().stream()
                                .map(KnowledgeSegmentHitkTaskDetailDTO.RetrievedSegmentScoreDTO::getSegmentId)
                                .filter(Objects::nonNull)
                                .toList());
                    }
                    return ids.stream();
                })
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(segmentIds)) {
            return Map.of();
        }
        List<KnowledgeSegment> segments = knowledgeSegmentMapper.selectList(new LambdaQueryWrapper<KnowledgeSegment>()
                .in(KnowledgeSegment::getId, segmentIds)
                .eq(KnowledgeSegment::getDeleted, 0));
        if (CollectionUtils.isEmpty(segments)) {
            return Map.of();
        }
        return segments.stream()
                .collect(Collectors.toMap(KnowledgeSegment::getId, segment -> segment, (left, right) -> left, HashMap::new));
    }

    private AdminHitKTaskDetailResult.SegmentResultItemResult toSegmentResultItem(KnowledgeSegmentHitkTaskDetailDTO detail,
                                                                                   Map<Long, KnowledgeSegment> segmentMap) {
        KnowledgeSegment sourceSegment = detail.getSegmentId() == null ? null : segmentMap.get(detail.getSegmentId());
        List<AdminHitKTaskDetailResult.RetrievedSegmentItemResult> retrievedSegments = toRetrievedSegmentItems(detail, segmentMap);
        List<String> rewrittenQuestions = CollectionUtils.isEmpty(detail.getRewrittenQuestions())
                ? List.of()
                : detail.getRewrittenQuestions().stream()
                .filter(StringUtils::hasText)
                .toList();
        return new AdminHitKTaskDetailResult.SegmentResultItemResult(
                detail.getSegmentId(),
                sourceSegment == null ? null : sourceSegment.getText(),
                detail.getHitkQuestion(),
                rewrittenQuestions,
                retrievedSegments,
                detail.getHit()
        );
    }

    private List<AdminHitKTaskDetailResult.RetrievedSegmentItemResult> toRetrievedSegmentItems(
            KnowledgeSegmentHitkTaskDetailDTO detail,
            Map<Long, KnowledgeSegment> segmentMap) {
        if (!CollectionUtils.isEmpty(detail.getRetrievedSegments())) {
            return detail.getRetrievedSegments().stream()
                    .filter(item -> item != null && item.getSegmentId() != null)
                    .map(item -> toRetrievedSegmentItem(item.getSegmentId(), segmentMap.get(item.getSegmentId()), item))
                    .toList();
        }
        if (CollectionUtils.isEmpty(detail.getRetrievedSegmentIds())) {
            return List.of();
        }
        return detail.getRetrievedSegmentIds().stream()
                .filter(Objects::nonNull)
                .map(retrievedSegmentId -> toRetrievedSegmentItem(retrievedSegmentId, segmentMap.get(retrievedSegmentId), null))
                .toList();
    }

    private AdminHitKTaskDetailResult.RetrievedSegmentItemResult toRetrievedSegmentItem(
            Long segmentId,
            KnowledgeSegment segment,
            KnowledgeSegmentHitkTaskDetailDTO.RetrievedSegmentScoreDTO score) {
        if (segment == null) {
            return new AdminHitKTaskDetailResult.RetrievedSegmentItemResult(
                    segmentId,
                    null,
                    null,
                    null,
                    score == null ? null : score.getRawSimilarity(),
                    score == null ? null : score.getSimilarityScore(),
                    score == null ? null : score.getRrfScore(),
                    score == null ? null : score.getFinalScore()
            );
        }
        return new AdminHitKTaskDetailResult.RetrievedSegmentItemResult(
                segmentId,
                segment.getDocumentId(),
                segment.getChunkOrder(),
                segment.getText(),
                score == null ? null : score.getRawSimilarity(),
                score == null ? null : score.getSimilarityScore(),
                score == null ? null : score.getRrfScore(),
                score == null ? null : score.getFinalScore()
        );
    }

    private List<Long> resolveRetrievedSegmentIds(KnowledgeSegmentHitkTaskDetailDTO detail) {
        if (!CollectionUtils.isEmpty(detail.getRetrievedSegments())) {
            return detail.getRetrievedSegments().stream()
                    .map(KnowledgeSegmentHitkTaskDetailDTO.RetrievedSegmentScoreDTO::getSegmentId)
                    .filter(Objects::nonNull)
                    .toList();
        }
        return CollectionUtils.isEmpty(detail.getRetrievedSegmentIds()) ? List.of() : detail.getRetrievedSegmentIds();
    }

    private List<KnowledgeSegmentHitkTaskDetailDTO> parseTaskDetails(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return List.of();
        }
        try {
            return JsonUtils.getObjectMapper().readValue(detailsJson, DETAIL_TYPE_REFERENCE);
        } catch (Exception exception) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "parse hitk task details failed");
        }
    }

    private LambdaQueryWrapper<KnowledgeSegmentHitkTask> buildTaskFilterWrapper(LocalDateTime startTime,
                                                                                LocalDateTime endTime,
                                                                                String normalizedStatus) {
        return new LambdaQueryWrapper<KnowledgeSegmentHitkTask>()
                .eq(KnowledgeSegmentHitkTask::getIsDelete, 0)
                .ge(startTime != null, KnowledgeSegmentHitkTask::getCreateTime, startTime)
                .le(endTime != null, KnowledgeSegmentHitkTask::getCreateTime, endTime)
                .eq(StringUtils.hasText(normalizedStatus), KnowledgeSegmentHitkTask::getStatus, normalizedStatus)
                .orderByDesc(KnowledgeSegmentHitkTask::getId);
    }

    private AdminHitKTaskStatisticsResult buildTaskStatistics(List<KnowledgeSegmentHitkTask> tasks) {
        AdminHitKTaskStatisticsResult result = new AdminHitKTaskStatisticsResult();
        if (CollectionUtils.isEmpty(tasks)) {
            result.setTaskCount(0);
            result.setAverageHitRate(ZERO_SCALE_RATE);
            result.setAverageTotalCount(ZERO_SCALE_RATE);
            result.setAverageHitCount(ZERO_SCALE_RATE);
            result.setAverageMissCount(ZERO_SCALE_RATE);
            result.setSumTotalCount(0L);
            result.setSumHitCount(0L);
            result.setSumMissCount(0L);
            result.setOverallHitRate(ZERO_SCALE_RATE);
            return result;
        }
        int taskCount = tasks.size();
        BigDecimal sumHitRate = tasks.stream()
                .map(task -> task.getHitRate() == null ? ZERO_SCALE_RATE : task.getHitRate())
                .reduce(ZERO_SCALE_RATE, BigDecimal::add);
        long sumTotalCount = tasks.stream().mapToLong(task -> safeCount(task.getTotalCount())).sum();
        long sumHitCount = tasks.stream().mapToLong(task -> safeCount(task.getHitCount())).sum();
        long sumMissCount = tasks.stream().mapToLong(task -> safeCount(task.getMissCount())).sum();
        BigDecimal divisor = BigDecimal.valueOf(taskCount);
        result.setTaskCount(taskCount);
        result.setAverageHitRate(sumHitRate.divide(divisor, 4, RoundingMode.HALF_UP));
        result.setAverageTotalCount(BigDecimal.valueOf(sumTotalCount).divide(divisor, 4, RoundingMode.HALF_UP));
        result.setAverageHitCount(BigDecimal.valueOf(sumHitCount).divide(divisor, 4, RoundingMode.HALF_UP));
        result.setAverageMissCount(BigDecimal.valueOf(sumMissCount).divide(divisor, 4, RoundingMode.HALF_UP));
        result.setSumTotalCount(sumTotalCount);
        result.setSumHitCount(sumHitCount);
        result.setSumMissCount(sumMissCount);
        result.setOverallHitRate(sumTotalCount <= 0
                ? ZERO_SCALE_RATE
                : BigDecimal.valueOf(sumHitCount).divide(BigDecimal.valueOf(sumTotalCount), 4, RoundingMode.HALF_UP));
        return result;
    }

    private List<Long> normalizeTaskIds(List<Long> taskIds) {
        if (CollectionUtils.isEmpty(taskIds)) {
            throw new IllegalArgumentException("taskIds must not be empty");
        }
        List<Long> normalizedIds = taskIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (CollectionUtils.isEmpty(normalizedIds)) {
            throw new IllegalArgumentException("taskIds must contain at least one positive number");
        }
        return normalizedIds;
    }

    private long normalizeTaskPageCurrent(Long current) {
        if (current == null || current <= 0) {
            return DEFAULT_TASK_PAGE_CURRENT;
        }
        return current;
    }

    private long normalizeTaskPageSize(Long size) {
        if (size == null || size <= 0) {
            return DEFAULT_TASK_PAGE_SIZE;
        }
        return Math.min(size, MAX_TASK_PAGE_SIZE);
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return status.trim().toUpperCase();
    }

    private String normalizeRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        String trimmedRemark = remark.trim();
        if (trimmedRemark.length() > REMARK_MAX_LENGTH) {
            throw new IllegalArgumentException("remark length must be less than or equal to 1024");
        }
        return trimmedRemark;
    }

    private long safeCount(Integer count) {
        return count == null ? 0L : count.longValue();
    }
}
