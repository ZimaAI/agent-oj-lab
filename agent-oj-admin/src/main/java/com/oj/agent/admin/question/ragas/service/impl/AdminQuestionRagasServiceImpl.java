package com.oj.agent.admin.question.ragas.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.admin.question.ragas.client.RagasEvaluationClient;
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
import com.oj.agent.admin.question.ragas.model.result.AdminRagasTaskDetailResult;
import com.oj.agent.admin.question.ragas.model.result.AdminRagasTaskPageResult;
import com.oj.agent.admin.question.ragas.model.result.AdminRagasTaskResult;
import com.oj.agent.admin.question.ragas.model.result.AdminQuestionDocumentSegmentRagasResult;
import com.oj.agent.admin.question.ragas.service.AdminQuestionRagasService;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.question.model.entity.AlgorithmQuestionKnowledgeDocument;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.question.service.AlgorithmQuestionKnowledgeDocumentService;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentMapper;
import com.oj.agent.core.rag.model.command.KnowledgeSegmentRagasTaskCreateCommand;
import com.oj.agent.core.rag.model.dto.RagasEvaluateResponseDTO;
import com.oj.agent.core.rag.model.dto.RagasEvaluateResultDTO;
import com.oj.agent.core.rag.model.dto.RagasEvaluateSampleDTO;
import com.oj.agent.core.rag.model.dto.SegmentRagasQaOutputDTO;
import com.oj.agent.core.rag.model.entity.KnowledgeDocument;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.rag.model.entity.KnowledgeSegmentRagas;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentRagasTaskDetailQuery;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentRagasTaskListQuery;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentRagasTaskPageQuery;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskDetailItemResult;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskDetailResult;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskPageResult;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentRagasTaskResult;
import com.oj.agent.core.rag.service.KnowledgeSegmentRagasQuestionService;
import com.oj.agent.core.rag.service.KnowledgeSegmentRagasService;
import com.oj.agent.core.rag.service.KnowledgeSegmentRagasTaskService;
import com.oj.agent.core.workflow.model.result.AnswerRagSegmentResult;
import com.oj.agent.core.workflow.model.result.AnswerRagasGraphResult;
import com.oj.agent.core.workflow.service.AnswerRagasGraphService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminQuestionRagasServiceImpl implements AdminQuestionRagasService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final long DEFAULT_TASK_PAGE_CURRENT = 1L;

    private static final long DEFAULT_TASK_PAGE_SIZE = 20L;

    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    private final AlgorithmQuestionService algorithmQuestionService;

    private final AlgorithmQuestionKnowledgeDocumentService algorithmQuestionKnowledgeDocumentService;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    private final KnowledgeSegmentMapper knowledgeSegmentMapper;

    private final KnowledgeSegmentRagasService knowledgeSegmentRagasService;

    private final KnowledgeSegmentRagasTaskService knowledgeSegmentRagasTaskService;

    private final KnowledgeSegmentRagasQuestionService knowledgeSegmentRagasQuestionService;

    private final AnswerRagasGraphService answerRagasGraphService;

    private final RagasEvaluationClient ragasEvaluationClient;

    public AdminQuestionRagasServiceImpl(AlgorithmQuestionService algorithmQuestionService,
                                         AlgorithmQuestionKnowledgeDocumentService algorithmQuestionKnowledgeDocumentService,
                                         KnowledgeDocumentMapper knowledgeDocumentMapper,
                                         KnowledgeSegmentMapper knowledgeSegmentMapper,
                                         KnowledgeSegmentRagasService knowledgeSegmentRagasService,
                                         KnowledgeSegmentRagasTaskService knowledgeSegmentRagasTaskService,
                                         KnowledgeSegmentRagasQuestionService knowledgeSegmentRagasQuestionService,
                                         AnswerRagasGraphService answerRagasGraphService,
                                         RagasEvaluationClient ragasEvaluationClient) {
        this.algorithmQuestionService = algorithmQuestionService;
        this.algorithmQuestionKnowledgeDocumentService = algorithmQuestionKnowledgeDocumentService;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeSegmentMapper = knowledgeSegmentMapper;
        this.knowledgeSegmentRagasService = knowledgeSegmentRagasService;
        this.knowledgeSegmentRagasTaskService = knowledgeSegmentRagasTaskService;
        this.knowledgeSegmentRagasQuestionService = knowledgeSegmentRagasQuestionService;
        this.answerRagasGraphService = answerRagasGraphService;
        this.ragasEvaluationClient = ragasEvaluationClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AdminQuestionDocumentSegmentRagasResult> generateRagas(AdminRagasGenerateCommand command) {
        validateQuestionAndDocument(command.getQuestionId(), command.getDocId());
        List<Long> segmentIds = normalizeRequiredIds(command.getSegmentIds(), "segmentIds");
        Map<Long, KnowledgeSegment> segmentMap = loadSegmentMap(command.getDocId(), segmentIds, true);
        List<AdminQuestionDocumentSegmentRagasResult> results = new ArrayList<>();
        for (Long segmentId : segmentIds) {
            KnowledgeSegment segment = segmentMap.get(segmentId);
            SegmentRagasQaOutputDTO generated = knowledgeSegmentRagasQuestionService.generateRagasQA(segment);
            KnowledgeSegmentRagas entity = upsertRagasEntity(
                    command.getQuestionId(),
                    command.getDocId(),
                    segmentId,
                    generated.getQuestion(),
                    generated.getStandardAnswer()
            );
            results.add(toRagasResult(entity));
        }
        return results;
    }

    @Override
    public List<AdminQuestionDocumentSegmentRagasResult> listRagas(AdminRagasListQuery query) {
        validateQuestionAndDocument(query.getQuestionId(), query.getDocId());
        List<Long> normalizedSegmentIds = normalizeOptionalIds(query.getSegmentIds());
        List<Long> normalizedRagasIds = mergeRagasIdFilters(query.getRagasIds(), query.getIds());
        return knowledgeSegmentRagasService.listByQuestionAndDocument(
                query.getQuestionId(),
                query.getDocId(),
                normalizedSegmentIds,
                normalizedRagasIds
        ).stream().map(this::toRagasResult).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AdminQuestionDocumentSegmentRagasResult> updateRagas(AdminRagasUpdateCommand command) {
        validateQuestionAndDocument(command.getQuestionId(), command.getDocId());
        if (command.getUpdates() == null || command.getUpdates().isEmpty()) {
            throw new IllegalArgumentException("updates must not be empty");
        }
        List<Long> segmentIds = normalizeRequiredIds(
                command.getUpdates().stream().map(AdminRagasUpdateCommand.UpdateItem::getSegmentId).toList(),
                "segmentIds"
        );
        Map<Long, KnowledgeSegment> segmentMap = loadSegmentMap(command.getDocId(), segmentIds, true);

        List<AdminQuestionDocumentSegmentRagasResult> results = new ArrayList<>();
        for (AdminRagasUpdateCommand.UpdateItem updateItem : command.getUpdates()) {
            Long segmentId = updateItem.getSegmentId();
            if (!segmentMap.containsKey(segmentId)) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "segment " + segmentId + " does not exist");
            }

            KnowledgeSegmentRagas entity = resolveRagasEntity(command.getQuestionId(), command.getDocId(), updateItem.getRagasId(), segmentId);
            if (entity == null) {
                entity = new KnowledgeSegmentRagas();
                entity.setQuestionId(command.getQuestionId());
                entity.setDocumentId(command.getDocId());
                entity.setSegmentId(segmentId);
                entity.setIsDelete(0);
            }
            entity.setRagasQuestion(updateItem.getQuestion().trim());
            entity.setStandardAnswer(updateItem.getStandardAnswer().trim());
            clearAnswerAndScoreFields(entity);
            entity.setStatus("INIT");
            entity.setMetadata(null);

            if (entity.getId() == null) {
                if (!knowledgeSegmentRagasService.save(entity)) {
                    throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "save ragas failed");
                }
            } else if (!knowledgeSegmentRagasService.updateById(entity)) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "update ragas failed");
            }

            KnowledgeSegmentRagas latest = knowledgeSegmentRagasService.getBySegmentId(command.getQuestionId(), command.getDocId(), segmentId);
            results.add(toRagasResult(latest == null ? entity : latest));
        }
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRagas(AdminRagasDeleteCommand command) {
        validateQuestionAndDocument(command.getQuestionId(), command.getDocId());
        List<KnowledgeSegmentRagas> targetRecords = resolveTargetRagasRecords(
                command.getQuestionId(),
                command.getDocId(),
                command.getSegmentIds(),
                command.getRagasIds(),
                command.getIds(),
                true
        );
        List<Long> ids = targetRecords.stream().map(KnowledgeSegmentRagas::getId).filter(Objects::nonNull).toList();
        if (!knowledgeSegmentRagasService.softDeleteByIds(ids)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "delete ragas failed");
        }
    }

    @Override
    public List<AdminQuestionDocumentSegmentRagasResult> generateAnswers(AdminRagasAnswerGenerateCommand command) {
        AlgorithmQuestionResult question = validateQuestionAndDocument(command.getQuestionId(), command.getDocId());
        List<KnowledgeSegmentRagas> targetRecords = resolveTargetRagasRecords(
                command.getQuestionId(),
                command.getDocId(),
                command.getSegmentIds(),
                command.getRagasIds(),
                command.getIds(),
                true
        );

        for (KnowledgeSegmentRagas targetRecord : targetRecords) {
            if (!StringUtils.hasText(targetRecord.getRagasQuestion())) {
                markRecordFailed(targetRecord, "ragasQuestion is blank");
                continue;
            }
            try {
                AnswerRagasGraphResult graphResult = answerRagasGraphService.execute(targetRecord.getRagasQuestion(), question);
                List<Long> retrievedSegmentIds = graphResult.segments().stream()
                        .map(AnswerRagSegmentResult::getId)
                        .filter(Objects::nonNull)
                        .toList();
                List<String> retrievedContexts = graphResult.segments().stream()
                        .map(AnswerRagSegmentResult::getText)
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .toList();

                targetRecord.setGeneratedAnswer(graphResult.assistantAnswer());
                targetRecord.setRetrievedSegmentIdsJson(writeJsonQuietly(retrievedSegmentIds));
                targetRecord.setRetrievedContextsJson(writeJsonQuietly(retrievedContexts));
                targetRecord.setRewrittenQuestionsJson(writeJsonQuietly(graphResult.rewrittenQuestions()));
                targetRecord.setStatus("ANSWER_GENERATED");
                targetRecord.setMetadata(mergeMetadata(targetRecord.getMetadata(), Map.of(
                        "answerGeneratedAt", LocalDateTime.now().toString()
                )));
                knowledgeSegmentRagasService.updateById(targetRecord);
            } catch (Exception exception) {
                markRecordFailed(targetRecord, exception.getMessage());
            }
        }

        List<Long> ragasIds = targetRecords.stream().map(KnowledgeSegmentRagas::getId).toList();
        return knowledgeSegmentRagasService.listByQuestionAndDocument(command.getQuestionId(), command.getDocId(), null, ragasIds).stream()
                .map(this::toRagasResult)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AdminQuestionDocumentSegmentRagasResult> evaluateRagas(AdminRagasEvaluateCommand command) {
        validateQuestionAndDocument(command.getQuestionId(), command.getDocId());
        List<KnowledgeSegmentRagas> targetRecords = resolveTargetRagasRecords(
                command.getQuestionId(),
                command.getDocId(),
                command.getSegmentIds(),
                command.getRagasIds(),
                command.getIds(),
                true
        );
        evaluateRagasRecords(targetRecords);
        return listRagasByRecords(command.getQuestionId(), command.getDocId(), targetRecords);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminRagasTaskResult createRagasTask(AdminRagasTaskCreateCommand command) {
        validateQuestionAndDocument(command.getQuestionId(), command.getDocId());
        List<KnowledgeSegmentRagas> targetRecords = resolveTargetRagasRecords(
                command.getQuestionId(),
                command.getDocId(),
                command.getSegmentIds(),
                null,
                null,
                true
        );
        RagasTaskExecutionSnapshot snapshot = evaluateRagasRecords(targetRecords);
        KnowledgeSegmentRagasTaskResult task = knowledgeSegmentRagasTaskService.createTask(
                new KnowledgeSegmentRagasTaskCreateCommand(
                        command.getQuestionId(),
                        command.getDocId(),
                        snapshot.totalCount(),
                        snapshot.successCount(),
                        snapshot.failureCount(),
                        snapshot.averageAnswerRelevancy(),
                        snapshot.averageFaithfulness(),
                        snapshot.averageContextPrecision(),
                        snapshot.averageContextRecall(),
                        snapshot.status(),
                        snapshot.errorMessage(),
                        snapshot.details()
                )
        );
        return toTaskResult(task);
    }

    @Override
    public List<AdminRagasTaskResult> listRagasTasks(AdminRagasTaskListQuery query) {
        validateQuestionAndDocument(query.getQuestionId(), query.getDocId());
        return knowledgeSegmentRagasTaskService.listTasks(
                        new KnowledgeSegmentRagasTaskListQuery(query.getQuestionId(), query.getDocId())
                ).stream()
                .map(this::toTaskResult)
                .toList();
    }

    @Override
    public AdminRagasTaskPageResult pageRagasTasks(AdminRagasTaskPageQuery query) {
        if (query.getStartTime() != null && query.getEndTime() != null && query.getStartTime().isAfter(query.getEndTime())) {
            throw new IllegalArgumentException("startTime must be earlier than or equal to endTime");
        }
        long current = normalizeTaskPageCurrent(query.getCurrent());
        long size = normalizeTaskPageSize(query.getSize());
        String status = normalizeStatus(query.getStatus());
        KnowledgeSegmentRagasTaskPageResult pageResult = knowledgeSegmentRagasTaskService.pageTasks(
                new KnowledgeSegmentRagasTaskPageQuery(
                        current,
                        size,
                        query.getStartTime(),
                        query.getEndTime(),
                        status
                )
        );
        List<AdminRagasTaskResult> records = CollectionUtils.isEmpty(pageResult.getRecords())
                ? List.of()
                : pageResult.getRecords().stream().map(this::toTaskResult).toList();
        return new AdminRagasTaskPageResult(current, size, pageResult.getTotal(), records);
    }

    @Override
    public AdminRagasTaskDetailResult getRagasTaskDetail(AdminRagasTaskDetailQuery query) {
        validatePositiveId(query.getTaskId(), "taskId");
        KnowledgeSegmentRagasTaskDetailResult task = knowledgeSegmentRagasTaskService.getTaskDetail(
                new KnowledgeSegmentRagasTaskDetailQuery(query.getTaskId())
        );
        if (task == null) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "ragas task does not exist");
        }
        return toTaskDetailResult(task);
    }

    private List<AdminQuestionDocumentSegmentRagasResult> listRagasByRecords(Long questionId,
                                                                             Long docId,
                                                                             List<KnowledgeSegmentRagas> targetRecords) {
        List<Long> ragasIds = targetRecords.stream().map(KnowledgeSegmentRagas::getId).toList();
        return knowledgeSegmentRagasService.listByQuestionAndDocument(questionId, docId, null, ragasIds).stream()
                .map(this::toRagasResult)
                .toList();
    }

    private RagasTaskExecutionSnapshot evaluateRagasRecords(List<KnowledgeSegmentRagas> targetRecords) {
        Map<Long, KnowledgeSegment> segmentMap = loadSegmentsBySegmentIds(targetRecords.stream()
                .map(KnowledgeSegmentRagas::getSegmentId)
                .filter(Objects::nonNull)
                .toList());
        List<RagasEvaluateSampleDTO> samples = targetRecords.stream()
                .map(record -> toEvaluateSample(record, segmentMap.get(record.getSegmentId())))
                .toList();
        RagasEvaluateResponseDTO evaluateResponse;
        try {
            evaluateResponse = ragasEvaluationClient.evaluate(samples);
        } catch (Exception exception) {
            String errorMessage = exception.getMessage();
            List<KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand> details = new ArrayList<>(targetRecords.size());
            for (KnowledgeSegmentRagas targetRecord : targetRecords) {
                markRecordFailed(targetRecord, errorMessage);
                details.add(toTaskDetail(targetRecord, errorMessage));
            }
            return buildTaskExecutionSnapshot("FAILED", errorMessage, details);
        }
        if (evaluateResponse == null) {
            String errorMessage = "ragas evaluation response is null";
            List<KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand> details = new ArrayList<>(targetRecords.size());
            for (KnowledgeSegmentRagas targetRecord : targetRecords) {
                markRecordFailed(targetRecord, errorMessage);
                details.add(toTaskDetail(targetRecord, errorMessage));
            }
            return buildTaskExecutionSnapshot("FAILED", errorMessage, details);
        }
        Map<Long, RagasEvaluateResultDTO> resultMap = CollectionUtils.isEmpty(evaluateResponse.getResults())
                ? Map.of()
                : evaluateResponse.getResults().stream()
                .filter(result -> result.getId() != null)
                .collect(Collectors.toMap(RagasEvaluateResultDTO::getId, result -> result, (left, right) -> left, LinkedHashMap::new));

        boolean degraded = Boolean.TRUE.equals(evaluateResponse.getDegraded());
        List<KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand> details = new ArrayList<>(targetRecords.size());
        for (KnowledgeSegmentRagas targetRecord : targetRecords) {
            RagasEvaluateResultDTO evaluateResult = resultMap.get(targetRecord.getId());
            if (evaluateResult == null) {
                String errorMessage = "ragas result is missing for id " + targetRecord.getId();
                markRecordFailed(targetRecord, errorMessage);
                details.add(toTaskDetail(targetRecord, errorMessage));
                continue;
            }
            targetRecord.setAnswerRelevancy(evaluateResult.getAnswerRelevancy());
            targetRecord.setFaithfulness(evaluateResult.getFaithfulness());
            targetRecord.setContextPrecision(evaluateResult.getContextPrecision());
            targetRecord.setContextRecall(evaluateResult.getContextRecall());
            targetRecord.setOverallScore(evaluateResult.getOverallScore());
            targetRecord.setStatus(StringUtils.hasText(evaluateResult.getErrorMessage()) ? "FAILED" : "EVALUATED");
            Map<String, Object> metadataPatch = new LinkedHashMap<>();
            metadataPatch.put("degraded", degraded);
            metadataPatch.put("evaluateError", evaluateResult.getErrorMessage());
            metadataPatch.put("evaluatedAt", LocalDateTime.now().toString());
            targetRecord.setMetadata(mergeMetadata(targetRecord.getMetadata(), metadataPatch));
            knowledgeSegmentRagasService.updateById(targetRecord);
            details.add(toTaskDetail(targetRecord, evaluateResult.getErrorMessage()));
        }
        return buildTaskExecutionSnapshot("COMPLETED", null, details);
    }

    private RagasTaskExecutionSnapshot buildTaskExecutionSnapshot(String status,
                                                                  String errorMessage,
                                                                  List<KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand> details) {
        int totalCount = details == null ? 0 : details.size();
        int successCount = details == null ? 0 : (int) details.stream()
                .filter(detail -> "EVALUATED".equals(detail.getStatus()))
                .count();
        int failureCount = Math.max(totalCount - successCount, 0);
        return new RagasTaskExecutionSnapshot(
                totalCount,
                successCount,
                failureCount,
                averageMetric(details, KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand::getAnswerRelevancy),
                averageMetric(details, KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand::getFaithfulness),
                averageMetric(details, KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand::getContextPrecision),
                averageMetric(details, KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand::getContextRecall),
                status,
                errorMessage,
                details == null ? List.of() : details
        );
    }

    private KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand toTaskDetail(KnowledgeSegmentRagas record, String errorMessage) {
        return new KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand(
                record.getId(),
                record.getSegmentId(),
                record.getQuestionId(),
                record.getDocumentId(),
                record.getStatus(),
                StringUtils.hasText(errorMessage) ? errorMessage : null,
                record.getAnswerRelevancy(),
                record.getFaithfulness(),
                record.getContextPrecision(),
                record.getContextRecall(),
                record.getOverallScore(),
                record.getRagasQuestion(),
                record.getStandardAnswer(),
                record.getGeneratedAnswer()
        );
    }

    private BigDecimal averageMetric(List<KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand> details,
                                     java.util.function.Function<KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand, BigDecimal> extractor) {
        if (CollectionUtils.isEmpty(details)) {
            return null;
        }
        List<BigDecimal> values = details.stream()
                .filter(detail -> "EVALUATED".equals(detail.getStatus()))
                .map(extractor)
                .filter(Objects::nonNull)
                .toList();
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private AlgorithmQuestionResult validateQuestionAndDocument(Long questionId, Long docId) {
        validatePositiveId(questionId, "questionId");
        validatePositiveId(docId, "docId");
        AlgorithmQuestionResult question;
        try {
            question = algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(questionId));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "question does not exist");
        }
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
        return question;
    }

    private void validatePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be a positive number");
        }
    }

    private List<Long> normalizeRequiredIds(List<Long> ids, String fieldName) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        List<Long> normalizedIds = ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(normalizedIds)) {
            throw new IllegalArgumentException(fieldName + " must contain positive numbers");
        }
        return normalizedIds;
    }

    private List<Long> normalizeOptionalIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        List<Long> normalizedIds = ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        return CollectionUtils.isEmpty(normalizedIds) ? null : normalizedIds;
    }

    private List<Long> mergeRagasIdFilters(List<Long> ragasIds, List<Long> ids) {
        List<Long> merged = new ArrayList<>();
        if (!CollectionUtils.isEmpty(ragasIds)) {
            merged.addAll(ragasIds);
        }
        if (!CollectionUtils.isEmpty(ids)) {
            merged.addAll(ids);
        }
        return normalizeOptionalIds(merged);
    }

    private Map<Long, KnowledgeSegment> loadSegmentMap(Long docId, List<Long> segmentIds, boolean requireAllSegments) {
        if (CollectionUtils.isEmpty(segmentIds)) {
            return Map.of();
        }
        List<KnowledgeSegment> segments = knowledgeSegmentMapper.selectList(new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(KnowledgeSegment::getDocumentId, docId)
                .in(KnowledgeSegment::getId, segmentIds)
                .eq(KnowledgeSegment::getDeleted, 0));
        Map<Long, KnowledgeSegment> segmentMap = segments.stream()
                .collect(Collectors.toMap(KnowledgeSegment::getId, segment -> segment, (left, right) -> left, HashMap::new));
        if (requireAllSegments) {
            for (Long segmentId : segmentIds) {
                if (!segmentMap.containsKey(segmentId)) {
                    throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "segment " + segmentId + " does not exist");
                }
            }
        }
        return segmentMap;
    }

    private Map<Long, KnowledgeSegment> loadSegmentsBySegmentIds(List<Long> segmentIds) {
        if (CollectionUtils.isEmpty(segmentIds)) {
            return Map.of();
        }
        List<KnowledgeSegment> segments = knowledgeSegmentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeSegment>()
                        .in(KnowledgeSegment::getId, segmentIds)
                        .eq(KnowledgeSegment::getDeleted, 0)
        );
        return segments.stream()
                .collect(Collectors.toMap(KnowledgeSegment::getId, segment -> segment, (left, right) -> left, LinkedHashMap::new));
    }

    private KnowledgeSegmentRagas upsertRagasEntity(Long questionId,
                                                    Long docId,
                                                    Long segmentId,
                                                    String question,
                                                    String standardAnswer) {
        KnowledgeSegmentRagas existing = knowledgeSegmentRagasService.getBySegmentId(questionId, docId, segmentId);
        if (existing == null) {
            existing = new KnowledgeSegmentRagas();
            existing.setQuestionId(questionId);
            existing.setDocumentId(docId);
            existing.setSegmentId(segmentId);
            existing.setIsDelete(0);
        }
        existing.setRagasQuestion(question);
        existing.setStandardAnswer(standardAnswer);
        clearAnswerAndScoreFields(existing);
        existing.setStatus("INIT");
        existing.setMetadata(null);

        if (existing.getId() == null) {
            if (!knowledgeSegmentRagasService.save(existing)) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "save ragas failed");
            }
        } else if (!knowledgeSegmentRagasService.updateById(existing)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "update ragas failed");
        }
        KnowledgeSegmentRagas latest = knowledgeSegmentRagasService.getBySegmentId(questionId, docId, segmentId);
        return latest == null ? existing : latest;
    }

    private KnowledgeSegmentRagas resolveRagasEntity(Long questionId, Long docId, Long ragasId, Long segmentId) {
        if (ragasId != null && ragasId > 0) {
            KnowledgeSegmentRagas existingById = knowledgeSegmentRagasService.getById(ragasId);
            if (existingById != null
                    && Integer.valueOf(0).equals(existingById.getIsDelete())
                    && Objects.equals(existingById.getQuestionId(), questionId)
                    && Objects.equals(existingById.getDocumentId(), docId)) {
                return existingById;
            }
        }
        return knowledgeSegmentRagasService.getBySegmentId(questionId, docId, segmentId);
    }

    private List<KnowledgeSegmentRagas> resolveTargetRagasRecords(Long questionId,
                                                                  Long docId,
                                                                  List<Long> segmentIds,
                                                                  List<Long> ragasIds,
                                                                  List<Long> ids,
                                                                  boolean requireNonEmpty) {
        List<Long> normalizedSegmentIds = normalizeOptionalIds(segmentIds);
        List<Long> normalizedRagasIds = mergeRagasIdFilters(ragasIds, ids);
        if (CollectionUtils.isEmpty(normalizedSegmentIds) && CollectionUtils.isEmpty(normalizedRagasIds)) {
            if (requireNonEmpty) {
                throw new IllegalArgumentException("segmentIds or ragasIds must not be empty");
            }
            return List.of();
        }

        List<KnowledgeSegmentRagas> targetRecords = knowledgeSegmentRagasService.listByQuestionAndDocument(
                questionId,
                docId,
                normalizedSegmentIds,
                normalizedRagasIds
        );
        if (requireNonEmpty && CollectionUtils.isEmpty(targetRecords)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "ragas records do not exist");
        }
        return targetRecords;
    }

    private RagasEvaluateSampleDTO toEvaluateSample(KnowledgeSegmentRagas ragasRecord, KnowledgeSegment segment) {
        RagasEvaluateSampleDTO sampleDTO = new RagasEvaluateSampleDTO();
        sampleDTO.setId(ragasRecord.getId());
        sampleDTO.setQuestion(defaultString(ragasRecord.getRagasQuestion()));
        sampleDTO.setAnswer(defaultString(ragasRecord.getGeneratedAnswer()));
        sampleDTO.setGroundTruth(defaultString(ragasRecord.getStandardAnswer()));

        List<String> contexts = parseStringListJson(ragasRecord.getRetrievedContextsJson());
        if (CollectionUtils.isEmpty(contexts) && segment != null && StringUtils.hasText(segment.getText())) {
            contexts = List.of(segment.getText().trim());
        }
        sampleDTO.setContexts(contexts == null ? List.of() : contexts);
        return sampleDTO;
    }

    private void clearAnswerAndScoreFields(KnowledgeSegmentRagas ragasEntity) {
        ragasEntity.setGeneratedAnswer(null);
        ragasEntity.setRetrievedSegmentIdsJson(null);
        ragasEntity.setRetrievedContextsJson(null);
        ragasEntity.setRewrittenQuestionsJson(null);
        ragasEntity.setContextPrecision(null);
        ragasEntity.setContextRecall(null);
        ragasEntity.setFaithfulness(null);
        ragasEntity.setAnswerRelevancy(null);
        ragasEntity.setAnswerSimilarity(null);
        ragasEntity.setAnswerCorrectness(null);
        ragasEntity.setOverallScore(null);
    }

    private void markRecordFailed(KnowledgeSegmentRagas ragasRecord, String errorMessage) {
        ragasRecord.setStatus("FAILED");
        ragasRecord.setMetadata(mergeMetadata(ragasRecord.getMetadata(), Map.of(
                "errorMessage", defaultString(errorMessage),
                "failedAt", LocalDateTime.now().toString()
        )));
        knowledgeSegmentRagasService.updateById(ragasRecord);
    }

    private String writeJsonQuietly(Object value) {
        try {
            return JsonUtils.toJson(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private List<String> parseStringListJson(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, STRING_LIST_TYPE_REFERENCE);
            if (CollectionUtils.isEmpty(list)) {
                return List.of();
            }
            return list.stream().filter(StringUtils::hasText).map(String::trim).toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String mergeMetadata(String existingMetadata, Map<String, Object> patches) {
        Map<String, Object> metadataMap = new LinkedHashMap<>();
        if (StringUtils.hasText(existingMetadata)) {
            try {
                Map<String, Object> existingMap = objectMapper.readValue(existingMetadata, new TypeReference<>() {
                });
                if (existingMap != null) {
                    metadataMap.putAll(existingMap);
                }
            } catch (Exception ignored) {
                // 旧元数据不是JSON时，直接覆盖为新结构。
            }
        }
        if (patches != null && !patches.isEmpty()) {
            metadataMap.putAll(patches);
        }
        try {
            return objectMapper.writeValueAsString(metadataMap);
        } catch (Exception exception) {
            return existingMetadata;
        }
    }

    private long normalizeTaskPageCurrent(Long current) {
        return current == null || current <= 0 ? DEFAULT_TASK_PAGE_CURRENT : current;
    }

    private long normalizeTaskPageSize(Long size) {
        return size == null || size <= 0 ? DEFAULT_TASK_PAGE_SIZE : size;
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : null;
    }

    private AdminRagasTaskResult toTaskResult(KnowledgeSegmentRagasTaskResult task) {
        AdminRagasTaskResult result = new AdminRagasTaskResult();
        result.setTaskId(task.getTaskId());
        result.setQuestionId(task.getQuestionId());
        result.setDocumentId(task.getDocumentId());
        result.setTotalCount(task.getTotalCount());
        result.setSuccessCount(task.getSuccessCount());
        result.setFailureCount(task.getFailureCount());
        result.setAverageAnswerRelevancy(task.getAverageAnswerRelevancy());
        result.setAverageFaithfulness(task.getAverageFaithfulness());
        result.setAverageContextPrecision(task.getAverageContextPrecision());
        result.setAverageContextRecall(task.getAverageContextRecall());
        result.setStatus(task.getStatus());
        result.setErrorMessage(task.getErrorMessage());
        result.setCreateTime(task.getCreateTime());
        result.setDetails(CollectionUtils.isEmpty(task.getDetails())
                ? List.of()
                : task.getDetails().stream().map(this::toTaskResultDetailItem).toList());
        return result;
    }

    private AdminRagasTaskDetailResult toTaskDetailResult(KnowledgeSegmentRagasTaskDetailResult task) {
        AdminRagasTaskDetailResult result = new AdminRagasTaskDetailResult();
        result.setTaskId(task.getTaskId());
        result.setQuestionId(task.getQuestionId());
        result.setDocumentId(task.getDocumentId());
        result.setTotalCount(task.getTotalCount());
        result.setSuccessCount(task.getSuccessCount());
        result.setFailureCount(task.getFailureCount());
        result.setAverageAnswerRelevancy(task.getAverageAnswerRelevancy());
        result.setAverageFaithfulness(task.getAverageFaithfulness());
        result.setAverageContextPrecision(task.getAverageContextPrecision());
        result.setAverageContextRecall(task.getAverageContextRecall());
        result.setStatus(task.getStatus());
        result.setErrorMessage(task.getErrorMessage());
        result.setCreateTime(task.getCreateTime());
        result.setDetails(CollectionUtils.isEmpty(task.getDetails())
                ? List.of()
                : task.getDetails().stream().map(this::toTaskDetailResultItem).toList());
        return result;
    }

    private AdminRagasTaskResult.DetailItemResult toTaskResultDetailItem(KnowledgeSegmentRagasTaskDetailItemResult detail) {
        return new AdminRagasTaskResult.DetailItemResult(
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
        );
    }

    private AdminRagasTaskDetailResult.DetailItemResult toTaskDetailResultItem(KnowledgeSegmentRagasTaskDetailItemResult detail) {
        return new AdminRagasTaskDetailResult.DetailItemResult(
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
        );
    }

    private AdminQuestionDocumentSegmentRagasResult toRagasResult(KnowledgeSegmentRagas entity) {
        AdminQuestionDocumentSegmentRagasResult result = new AdminQuestionDocumentSegmentRagasResult();
        result.setId(entity.getId());
        result.setRagasId(entity.getId());
        result.setSegmentId(entity.getSegmentId());
        result.setQuestion(entity.getRagasQuestion());
        result.setStandardAnswer(entity.getStandardAnswer());
        result.setGeneratedAnswer(entity.getGeneratedAnswer());
        result.setContextPrecision(entity.getContextPrecision());
        result.setContextRecall(entity.getContextRecall());
        result.setFaithfulness(entity.getFaithfulness());
        result.setAnswerRelevancy(entity.getAnswerRelevancy());
        result.setAnswerSimilarity(entity.getAnswerSimilarity());
        result.setAnswerCorrectness(entity.getAnswerCorrectness());
        result.setOverallScore(entity.getOverallScore());
        result.setStatus(entity.getStatus());
        result.setMetadata(entity.getMetadata());
        result.setCreateTime(entity.getCreateTime());
        result.setUpdateTime(entity.getUpdateTime());
        return result;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record RagasTaskExecutionSnapshot(int totalCount,
                                              int successCount,
                                              int failureCount,
                                              BigDecimal averageAnswerRelevancy,
                                              BigDecimal averageFaithfulness,
                                              BigDecimal averageContextPrecision,
                                              BigDecimal averageContextRecall,
                                              String status,
                                              String errorMessage,
                                              List<KnowledgeSegmentRagasTaskCreateCommand.DetailItemCommand> details) {
    }
}
