package com.oj.agent.admin.question.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.oj.agent.admin.question.model.result.AdminBatchOperationResult;
import com.oj.agent.admin.question.model.result.AdminKnowledgeSegmentPageResult;
import com.oj.agent.admin.question.model.result.AdminKnowledgeSegmentResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDocumentResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDocumentSegmentPageResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDocumentSegmentResult;
import com.oj.agent.admin.question.model.result.AdminQuestionDualDetailResult;
import com.oj.agent.admin.question.model.result.AdminQuestionVectorProjectionResult;
import com.oj.agent.admin.question.service.AdminQuestionManagementService;
import com.oj.agent.common.constant.MetadataKeyConstant;
import com.oj.agent.core.question.enums.AlgorithmQuestionTypeEnum;
import com.oj.agent.core.question.enums.Language;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.question.model.entity.AlgorithmQuestionKnowledgeDocument;
import com.oj.agent.core.question.model.entity.*;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.rag.enums.KnowledgeDocumentStatus;
import com.oj.agent.core.rag.model.entity.*;
import com.oj.agent.core.rag.model.result.KnowledgeDocumentParseResult;
import com.oj.agent.core.question.service.AlgorithmCodeService;
import com.oj.agent.core.question.service.AlgorithmQuestionKnowledgeDocumentService;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.question.service.AlgorithmQuestionVectorSyncService;
import com.oj.agent.core.question.util.StandardCasePoolCodec;
import com.oj.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentHitkTaskMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentRagasMapper;
import com.oj.agent.core.rag.service.KnowledgeDocumentService;
import com.oj.agent.core.rag.model.VectorDocument;
import com.oj.agent.core.rag.store.AgentVectorStore;
import com.oj.agent.core.question.mapper.AlgorithmQuestionTagMapper;
import com.oj.agent.core.question.mapper.TagMapper;
import com.oj.agent.core.question.service.QuestionTagPersistenceService;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.exception.ValidationException;
import com.oj.agent.core.question.mapper.QuestionSyncCompensationTaskMapper;
import com.oj.agent.security.util.UserContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.nio.file.Files;

@Service
public class AdminQuestionManagementServiceImpl implements AdminQuestionManagementService {

    private static final String DELETE_MARK_MESSAGE = "Admin batch delete";
    private static final String DELETE_DOCUMENT_MESSAGE = "Admin question document delete";
    private static final String CODE_TEMPLATE_UPDATE_FAILURE_MESSAGE = "MySQL code template update failed";
    private static final String QUESTION_CREATE_FAILURE_MESSAGE = "MySQL question create failed";
    private static final String CODE_TEMPLATE_CREATE_FAILURE_MESSAGE = "MySQL code template create failed";
    private static final String DOCUMENT_UPLOAD_FAILURE_MESSAGE = "Question document upload failed";
    private static final int PROGRESS_UPLOADED = 10;
    private static final int PROGRESS_CONVERTING = 60;
    private static final int PROGRESS_VECTOR_STORED = 100;
    private static final int PROGRESS_FAILED = -1;
    private static final int PROGRESS_PENDING = 0;
    private static final long DEFAULT_SEGMENT_PAGE_CURRENT = 1L;
    private static final long DEFAULT_SEGMENT_PAGE_SIZE = 20L;
    private static final long MAX_SEGMENT_PAGE_SIZE = 100L;
    private static final Set<String> ALLOWED_UPLOAD_DOCUMENT_EXTENSIONS = Set.of("pdf", "md", "markdown", "zip");
    private static final String UPLOAD_DOCUMENT_EXTENSION_ERROR_MESSAGE =
            "Only PDF, MD, MARKDOWN, and ZIP files are supported";
    private static final Set<String> ALLOWED_DIFFICULTIES = Set.of("SIMPLE", "MEDIUM", "HARD");
    private static final Set<String> SEGMENT_READY_STATUSES = Set.of(
            KnowledgeDocumentStatus.CHUNKED.name(),
            KnowledgeDocumentStatus.VECTOR_STORED.name()
    );
    private static final Set<Language> REQUIRED_TEMPLATE_LANGUAGES = EnumSet.of(
            Language.JAVA,
            Language.PYTHON,
            Language.JAVASCRIPT
    );

    private final AlgorithmCodeService algorithmCodeService;
    private final AlgorithmQuestionService algorithmQuestionService;
    private final QuestionTagPersistenceService questionTagPersistenceService;
    private final AlgorithmQuestionTagMapper algorithmQuestionTagMapper;
    private final TagMapper tagMapper;
    private final QuestionSyncCompensationTaskMapper compensationTaskMapper;
    private final AgentVectorStore agentVectorStore;
    private final AgentVectorStore knowledgeSegmentAgentVectorStore;
    private final AlgorithmQuestionVectorSyncService algorithmQuestionVectorSyncService;
    private final AlgorithmQuestionKnowledgeDocumentService algorithmQuestionKnowledgeDocumentService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeSegmentMapper knowledgeSegmentMapper;
    private final KnowledgeSegmentRagasMapper knowledgeSegmentRagasMapper;
    private final KnowledgeSegmentHitkTaskMapper knowledgeSegmentHitkTaskMapper;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    public AdminQuestionManagementServiceImpl(AlgorithmCodeService algorithmCodeService,
                                              AlgorithmQuestionService algorithmQuestionService,
                                              QuestionTagPersistenceService questionTagPersistenceService,
                                              AlgorithmQuestionTagMapper algorithmQuestionTagMapper,
                                              TagMapper tagMapper,
                                              QuestionSyncCompensationTaskMapper compensationTaskMapper,
                                              AgentVectorStore agentVectorStore,
                                              @Qualifier("knowledgeSegmentAgentVectorStore") AgentVectorStore knowledgeSegmentAgentVectorStore,
                                              AlgorithmQuestionVectorSyncService algorithmQuestionVectorSyncService,
                                              AlgorithmQuestionKnowledgeDocumentService algorithmQuestionKnowledgeDocumentService,
                                              KnowledgeDocumentService knowledgeDocumentService,
                                              KnowledgeDocumentMapper knowledgeDocumentMapper,
                                              KnowledgeSegmentMapper knowledgeSegmentMapper,
                                              KnowledgeSegmentRagasMapper knowledgeSegmentRagasMapper,
                                              KnowledgeSegmentHitkTaskMapper knowledgeSegmentHitkTaskMapper) {
        this.algorithmCodeService = algorithmCodeService;
        this.algorithmQuestionService = algorithmQuestionService;
        this.questionTagPersistenceService = questionTagPersistenceService;
        this.algorithmQuestionTagMapper = algorithmQuestionTagMapper;
        this.tagMapper = tagMapper;
        this.compensationTaskMapper = compensationTaskMapper;
        this.agentVectorStore = agentVectorStore;
        this.knowledgeSegmentAgentVectorStore = knowledgeSegmentAgentVectorStore;
        this.algorithmQuestionVectorSyncService = algorithmQuestionVectorSyncService;
        this.algorithmQuestionKnowledgeDocumentService = algorithmQuestionKnowledgeDocumentService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeSegmentMapper = knowledgeSegmentMapper;
        this.knowledgeSegmentRagasMapper = knowledgeSegmentRagasMapper;
        this.knowledgeSegmentHitkTaskMapper = knowledgeSegmentHitkTaskMapper;
    }

    @Override
    public AdminQuestionDualDetailResult getQuestionDualDetail(AdminQuestionDualDetailQuery query) {
        Long questionId = query == null ? null : query.getQuestionId();
        validateQuestionId(questionId);
        AlgorithmQuestion mysqlQuestion = loadMysqlQuestionOrThrow(questionId);
        List<AlgorithmCode> mysqlCodeTemplates = loadMysqlCodeTemplates(questionId);
        populateQuestionTags(mysqlQuestion);
        AdminQuestionVectorProjectionResult vectorProjection = loadVectorProjectionSafely(questionId);
        return new AdminQuestionDualDetailResult(mysqlQuestion, mysqlCodeTemplates, vectorProjection);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminQuestionDualDetailResult createQuestion(AdminQuestionCreateCommand request) {
        // 先做请求级校验，避免非法数据入库。
        validateCreateRequest(request);
        List<StandardCasePoolCodec.StandardCaseItem> normalizedCasePool =
                normalizeCreateStandardCasePool(request.getStandardCasePool());
        List<NormalizedCodeTemplate> normalizedTemplates = normalizeCreateCodeTemplates(request.getCodeTemplates());
        // 合并系统标签与自定义标签，并按名称持久化。
        List<String> tagNames = resolveCreateTagNames(request.getTagIds(), request.getTags());

        AlgorithmQuestion question = buildCreateQuestion(request, normalizedCasePool);
        boolean saved = algorithmQuestionService.save(question);
        if (!saved || question.getId() == null) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), QUESTION_CREATE_FAILURE_MESSAGE);
        }

        List<AlgorithmCode> codeRecords = normalizedTemplates.stream()
                .map(template -> toCreateCodeRecord(question, template))
                .toList();
        if (!algorithmCodeService.saveBatch(codeRecords)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), CODE_TEMPLATE_CREATE_FAILURE_MESSAGE);
        }

        questionTagPersistenceService.persistQuestionTags(question.getId(), tagNames);
        question.setTags(tagNames);
        syncVectorProjection(question);

        return new AdminQuestionDualDetailResult(
                question,
                loadMysqlCodeTemplates(question.getId()),
                loadVectorProjectionSafely(question.getId())
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminQuestionDocumentResult uploadQuestionDocument(AdminQuestionDocumentUploadCommand command) {
        validateUploadCommand(command);
        Long questionId = command.getQuestionId();
        validateQuestionId(questionId);
        MultipartFile file = toMultipartFile(command);
        validateUploadFile(file);
        loadMysqlQuestionOrThrow(questionId);

        KnowledgeDocumentParseResult parseResult = knowledgeDocumentService.parseByUploadAsync(file);
        if (parseResult == null || parseResult.getDocId() == null || parseResult.getDocId() <= 0) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), DOCUMENT_UPLOAD_FAILURE_MESSAGE);
        }

        algorithmQuestionKnowledgeDocumentService.bind(questionId, parseResult.getDocId());
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(parseResult.getDocId());
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            return toDocumentResult(parseResult);
        }
        return toDocumentResult(document);
    }

    @Override
    public List<AdminQuestionDocumentResult> listQuestionDocuments(AdminQuestionDocumentListQuery query) {
        Long questionId = query == null ? null : query.getQuestionId();
        validateQuestionId(questionId);
        loadReadableQuestionOrThrow(questionId);

        List<AlgorithmQuestionKnowledgeDocument> relations = algorithmQuestionKnowledgeDocumentService.listByQuestionId(questionId);
        if (CollectionUtils.isEmpty(relations)) {
            return List.of();
        }

        List<Long> docIds = relations.stream()
                .map(AlgorithmQuestionKnowledgeDocument::getDocId)
                .filter(Objects::nonNull)
                .toList();
        if (CollectionUtils.isEmpty(docIds)) {
            return List.of();
        }

        List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectBatchIds(docIds);
        if (CollectionUtils.isEmpty(documents)) {
            return List.of();
        }

        Map<Long, KnowledgeDocument> documentMap = documents.stream()
                .filter(Objects::nonNull)
                .filter(document -> !Integer.valueOf(1).equals(document.getDeleted()))
                .filter(document -> document.getDocId() != null)
                .collect(Collectors.toMap(KnowledgeDocument::getDocId, Function.identity(), (left, right) -> left));

        List<AdminQuestionDocumentResult> result = new ArrayList<>(docIds.size());
        Set<Long> visitedDocIds = new HashSet<>();
        for (Long docId : docIds) {
            if (!visitedDocIds.add(docId)) {
                continue;
            }
            KnowledgeDocument document = documentMap.get(docId);
            if (document != null) {
                result.add(toDocumentResult(document));
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestionDocument(AdminQuestionDocumentDeleteCommand command) {
        Long questionId = command == null ? null : command.getQuestionId();
        Long docId = command == null ? null : command.getDocId();
        validateQuestionId(questionId);
        validateDocId(docId);
        loadMysqlQuestionOrThrow(questionId);
        ensureQuestionDocumentOwned(questionId, docId);

        // 先删除题目绑定与文档衍生数据，确保不会再被管理端读取。
        deleteQuestionDocumentRelation(questionId, docId);
        deleteDocumentRagas(docId);
        deleteDocumentHitkTasks(docId);
        deleteDocumentSegments(docId);

        // 删除向量库中的文档片段向量，失败时整体回滚。
        deleteDocumentVectors(docId);

        // 最后删除文档本体，保持错误场景下文档记录仍可见用于排查。
        deleteDocumentRecord(docId);
    }

    @Override
    public AdminQuestionDocumentSegmentPageResult pageQuestionDocumentSegments(AdminQuestionDocumentSegmentPageQuery query) {
        Long questionId = query == null ? null : query.getQuestionId();
        Long docId = query == null ? null : query.getDocId();
        Long current = query == null ? null : query.getCurrent();
        Long size = query == null ? null : query.getSize();
        validateQuestionId(questionId);
        validateDocId(docId);
        loadMysqlQuestionOrThrow(questionId);
        ensureQuestionDocumentReadyForSegmentQuery(questionId, docId);

        // 查询文档全部分段并在内存中计算父子关系。
        List<KnowledgeSegment> allSegments = listDocumentSegments(docId);
        long normalizedCurrent = normalizeCurrent(current);
        long normalizedSize = normalizeSize(size);
        if (CollectionUtils.isEmpty(allSegments)) {
            return new AdminQuestionDocumentSegmentPageResult(normalizedCurrent, normalizedSize, 0L, List.of());
        }

        // 统计每个父分段 chunkId 对应的子分段数量。
        Map<String, Integer> childCountByParentChunkId = new HashMap<>();
        Map<Long, String> parentChunkIdBySegmentId = new HashMap<>();
        for (KnowledgeSegment segment : allSegments) {
            String parentChunkId = parseParentChunkId(segment.getMetadata());
            parentChunkIdBySegmentId.put(segment.getId(), parentChunkId);
            if (StringUtils.hasText(parentChunkId)) {
                childCountByParentChunkId.merge(parentChunkId, 1, Integer::sum);
            }
        }

        // 顶层分页只保留父分段和无父的普通分段。
        List<KnowledgeSegment> topLevelSegments = allSegments.stream()
                .filter(segment -> !StringUtils.hasText(parentChunkIdBySegmentId.get(segment.getId())))
                .sorted(segmentSortComparator())
                .toList();

        long total = topLevelSegments.size();
        long offset = (normalizedCurrent - 1L) * normalizedSize;
        if (offset >= total) {
            return new AdminQuestionDocumentSegmentPageResult(normalizedCurrent, normalizedSize, total, List.of());
        }

        List<AdminQuestionDocumentSegmentResult> records = topLevelSegments.stream()
                .skip(offset)
                .limit(normalizedSize)
                .map(segment -> {
                    String chunkId = trimToNull(segment.getChunkId());
                    int childCount = !StringUtils.hasText(chunkId)
                            ? 0
                            : childCountByParentChunkId.getOrDefault(chunkId, 0);
                    boolean parentSegment = childCount > 0;
                    return toDocumentSegmentResult(
                            segment,
                            parentChunkIdBySegmentId.get(segment.getId()),
                            parentSegment,
                            childCount
                    );
                })
                .toList();
        return new AdminQuestionDocumentSegmentPageResult(normalizedCurrent, normalizedSize, total, records);
    }

    @Override
    public List<AdminQuestionDocumentSegmentResult> listQuestionDocumentChildSegments(AdminQuestionDocumentChildSegmentQuery query) {
        Long questionId = query == null ? null : query.getQuestionId();
        Long docId = query == null ? null : query.getDocId();
        Long parentSegmentId = query == null ? null : query.getParentSegmentId();
        validateQuestionId(questionId);
        validateDocId(docId);
        validateSegmentId(parentSegmentId);
        loadMysqlQuestionOrThrow(questionId);
        ensureQuestionDocumentReadyForSegmentQuery(questionId, docId);

        // 查询文档全部分段并定位父分段。
        List<KnowledgeSegment> allSegments = listDocumentSegments(docId);
        KnowledgeSegment parentSegment = allSegments.stream()
                .filter(segment -> Objects.equals(segment.getId(), parentSegmentId))
                .findFirst()
                .orElseThrow(() -> new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Parent segment does not exist"));

        String parentChunkId = trimToNull(parentSegment.getChunkId());
        if (!StringUtils.hasText(parentChunkId)) {
            return List.of();
        }

        // 按父分段 chunkId 过滤并返回子分段明细。
        return allSegments.stream()
                .filter(segment -> !Objects.equals(segment.getId(), parentSegmentId))
                .filter(segment -> Objects.equals(parentChunkId, parseParentChunkId(segment.getMetadata())))
                .sorted(segmentSortComparator())
                .map(segment -> toDocumentSegmentResult(
                        segment,
                        parseParentChunkId(segment.getMetadata()),
                        false,
                        0
                ))
                .toList();
    }

    @Override
    public AdminKnowledgeSegmentPageResult pageKnowledgeSegments(AdminKnowledgeSegmentPageQuery query) {
        Long current = query == null ? null : query.getCurrent();
        Long size = query == null ? null : query.getSize();
        String keyword = query == null ? null : query.getKeyword();
        long normalizedCurrent = normalizeCurrent(current);
        long normalizedSize = normalizeSize(size);
        String normalizedKeyword = trimToNull(keyword);
        // 统一分页查询全量知识片段，支持按 chunk/text/hitkQuestion 模糊搜索。
        LambdaQueryWrapper<KnowledgeSegment> queryWrapper = new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(KnowledgeSegment::getDeleted, 0)
                .orderByDesc(KnowledgeSegment::getUpdatedAt)
                .orderByDesc(KnowledgeSegment::getId);
        if (StringUtils.hasText(normalizedKeyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(KnowledgeSegment::getText, normalizedKeyword)
                    .or()
                    .like(KnowledgeSegment::getHitkQuestion, normalizedKeyword)
                    .or()
                    .like(KnowledgeSegment::getChunkId, normalizedKeyword));
        }

        Page<KnowledgeSegment> pageResult = knowledgeSegmentMapper.selectPage(new Page<>(normalizedCurrent, normalizedSize), queryWrapper);
        if (CollectionUtils.isEmpty(pageResult.getRecords())) {
            return new AdminKnowledgeSegmentPageResult(normalizedCurrent, normalizedSize, pageResult.getTotal(), List.of());
        }

        List<Long> docIds = pageResult.getRecords().stream()
                .map(KnowledgeSegment::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, KnowledgeDocument> documentMap = loadDocumentMap(docIds);
        Map<Long, Long> questionIdByDocId = loadQuestionIdByDocId(docIds);
        Map<Long, AlgorithmQuestion> questionMap = loadQuestionMap(questionIdByDocId.values().stream().filter(Objects::nonNull).toList());

        List<AdminKnowledgeSegmentResult> records = pageResult.getRecords().stream()
                .map(segment -> toKnowledgeSegmentResult(segment, documentMap, questionIdByDocId, questionMap))
                .toList();
        return new AdminKnowledgeSegmentPageResult(normalizedCurrent, normalizedSize, pageResult.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminQuestionDualDetailResult updateQuestion(AdminQuestionUpdateCommand request) {
        Long questionId = request == null ? null : request.getQuestionId();
        validateQuestionId(questionId);
        validateUpdateRequest(request);

        AlgorithmQuestion existing = loadMysqlQuestionOrThrow(questionId);
        applyUpdate(existing, request);
        updateMysqlQuestionOrThrow(existing);

        if (request.getTags() != null) {
            replaceQuestionTags(questionId, request.getTags());
        }
        if (request.getCodeTemplates() != null) {
            replaceQuestionCodeTemplates(questionId, request.getCodeTemplates());
        }

        syncVectorProjection(existing);
        populateQuestionTags(existing);
        return new AdminQuestionDualDetailResult(
                existing,
                loadMysqlCodeTemplates(questionId),
                loadVectorProjectionSafely(questionId)
        );
    }

    @Override
    public AdminBatchOperationResult batchDeleteQuestions(AdminQuestionBatchDeleteCommand command) {
        List<Long> questionIds = command == null ? null : command.getQuestionIds();
        List<Long> normalizedIds = normalizeBatchIds(questionIds);
        List<AdminBatchOperationResult.ItemResult> results = new ArrayList<>();

        for (Long questionId : normalizedIds) {
            results.add(deleteSingleQuestion(questionId));
        }

        int successCount = (int) results.stream().filter(item -> Boolean.TRUE.equals(item.getSuccess())).count();
        int failureCount = results.size() - successCount;
        return new AdminBatchOperationResult(successCount, failureCount, results);
    }

    private AdminBatchOperationResult.ItemResult deleteSingleQuestion(Long questionId) {
        try {
            AlgorithmQuestion question = loadMysqlQuestionOrThrow(questionId);
            // 先加载题目关联的文档与分段，用于后续级联清理。
            List<Long> documentIds = listQuestionDocumentIds(questionId);
            List<Long> segmentIds = listDocumentSegmentIds(documentIds);

            // 先标记题目和关联 MySQL 数据删除，避免前端继续读取到旧数据。
            markMysqlQuestionDeleted(question);
            markQuestionDocumentRelationsDeleted(questionId);
            markKnowledgeSegmentsDeleted(documentIds);
            markKnowledgeDocumentsDeleted(documentIds);
            // 再清理 Milvus 中的知识分段向量。
            markKnowledgeSegmentVectorsDeleted(segmentIds);

            // 最后清理题目向量；如删除失败，沿用补偿机制。
            boolean marked = markVectorProjectionDeleted(questionId);
            if (!marked) {
                String message = "Vector projection record not found";
                createCompensationTask(questionId, "DELETE", message);
                return new AdminBatchOperationResult.ItemResult(questionId, false, message);
            }
            return new AdminBatchOperationResult.ItemResult(questionId, true, "Delete succeeded");
        } catch (ValidationException ex) {
            return new AdminBatchOperationResult.ItemResult(questionId, false, ex.getMessage());
        } catch (Exception ex) {
            String message = "Delete failed: " + ex.getMessage();
            createCompensationTask(questionId, "DELETE", message);
            return new AdminBatchOperationResult.ItemResult(questionId, false, message);
        }
    }

    private void markMysqlQuestionDeleted(AlgorithmQuestion question) {
        question.setIsDelete(1);
        question.setVectorSyncStatus("FAILED");
        question.setVectorSyncErrorMessage(DELETE_MARK_MESSAGE);
        updateMysqlQuestionOrThrow(question);
    }

    private boolean markVectorProjectionDeleted(Long questionId) {
        return agentVectorStore.markDeleted(questionId, DELETE_MARK_MESSAGE);
    }

    private List<Long> listQuestionDocumentIds(Long questionId) {
        List<AlgorithmQuestionKnowledgeDocument> relations = algorithmQuestionKnowledgeDocumentService.listByQuestionId(questionId);
        if (CollectionUtils.isEmpty(relations)) {
            return List.of();
        }
        return relations.stream()
                .map(AlgorithmQuestionKnowledgeDocument::getDocId)
                .filter(Objects::nonNull)
                .filter(docId -> docId > 0L)
                .distinct()
                .toList();
    }

    private List<Long> listDocumentSegmentIds(List<Long> documentIds) {
        if (CollectionUtils.isEmpty(documentIds)) {
            return List.of();
        }
        return knowledgeSegmentMapper.selectList(new LambdaQueryWrapper<KnowledgeSegment>()
                        .in(KnowledgeSegment::getDocumentId, documentIds)
                        .eq(KnowledgeSegment::getDeleted, 0))
                .stream()
                .map(KnowledgeSegment::getId)
                .filter(Objects::nonNull)
                .filter(segmentId -> segmentId > 0L)
                .distinct()
                .toList();
    }

    private void markQuestionDocumentRelationsDeleted(Long questionId) {
        algorithmQuestionKnowledgeDocumentService.update(new UpdateWrapper<AlgorithmQuestionKnowledgeDocument>()
                .eq("question_id", questionId)
                .eq("is_delete", 0)
                .set("is_delete", 1));
    }

    private void markKnowledgeDocumentsDeleted(List<Long> documentIds) {
        if (CollectionUtils.isEmpty(documentIds)) {
            return;
        }
        knowledgeDocumentMapper.update(null, new UpdateWrapper<KnowledgeDocument>()
                .in("doc_id", documentIds)
                .eq("deleted", 0)
                .set("deleted", 1));
    }

    private void markKnowledgeSegmentsDeleted(List<Long> documentIds) {
        if (CollectionUtils.isEmpty(documentIds)) {
            return;
        }
        knowledgeSegmentMapper.update(null, new UpdateWrapper<KnowledgeSegment>()
                .in("document_id", documentIds)
                .eq("deleted", 0)
                .set("deleted", 1));
    }

    private void markKnowledgeSegmentVectorsDeleted(List<Long> segmentIds) {
        if (CollectionUtils.isEmpty(segmentIds)) {
            return;
        }
        for (Long segmentId : segmentIds) {
            try {
                knowledgeSegmentAgentVectorStore.markDeleted(segmentId, DELETE_MARK_MESSAGE);
            } catch (Exception ex) {
                throw new ValidationException(
                        SecurityErrorCode.TABLE_001.getCode(),
                        "Knowledge segment vector delete failed, segmentId=%d, reason=%s"
                                .formatted(segmentId, ex.getMessage())
                );
            }
        }
    }

    private void ensureQuestionDocumentOwned(Long questionId, Long docId) {
        // 先校验题目文档绑定关系，再校验文档本体存在。
        List<AlgorithmQuestionKnowledgeDocument> relations = algorithmQuestionKnowledgeDocumentService.listByQuestionId(questionId);
        boolean bound = !CollectionUtils.isEmpty(relations) && relations.stream()
                .map(AlgorithmQuestionKnowledgeDocument::getDocId)
                .anyMatch(boundDocId -> Objects.equals(boundDocId, docId));
        if (!bound) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Document does not belong to question");
        }

        KnowledgeDocument document = knowledgeDocumentMapper.selectById(docId);
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Document does not exist");
        }
    }

    private void deleteQuestionDocumentRelation(Long questionId, Long docId) {
        // 物理删除题目与文档的绑定关系。
        boolean removed = algorithmQuestionKnowledgeDocumentService.remove(
                new LambdaQueryWrapper<AlgorithmQuestionKnowledgeDocument>()
                        .eq(AlgorithmQuestionKnowledgeDocument::getQuestionId, questionId)
                        .eq(AlgorithmQuestionKnowledgeDocument::getDocId, docId)
        );
        if (!removed) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Question document relation delete failed");
        }
    }

    private void deleteDocumentRagas(Long docId) {
        // 物理删除文档下的 RAGAS 评估数据。
        knowledgeSegmentRagasMapper.delete(new LambdaQueryWrapper<KnowledgeSegmentRagas>()
                .eq(KnowledgeSegmentRagas::getDocumentId, docId));
    }

    private void deleteDocumentHitkTasks(Long docId) {
        // 物理删除文档下的 HitK 任务数据。
        knowledgeSegmentHitkTaskMapper.delete(new LambdaQueryWrapper<KnowledgeSegmentHitkTask>()
                .eq(KnowledgeSegmentHitkTask::getDocumentId, docId));
    }

    private void deleteDocumentSegments(Long docId) {
        // 物理删除知识片段表中的所有文档片段。
        knowledgeSegmentMapper.delete(new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(KnowledgeSegment::getDocumentId, docId));
    }

    private void deleteDocumentVectors(Long docId) {
        // 删除 Milvus 中该文档对应的所有知识片段向量。
        try {
            knowledgeSegmentAgentVectorStore.deleteByDocumentId(docId, DELETE_DOCUMENT_MESSAGE);
        } catch (Exception ex) {
            throw new ValidationException(
                    SecurityErrorCode.TABLE_001.getCode(),
                    "Knowledge segment vector delete failed: " + ex.getMessage()
            );
        }
    }

    private void deleteDocumentRecord(Long docId) {
        // 最后物理删除文档主记录。
        int deletedRows = knowledgeDocumentMapper.deleteById(docId);
        if (deletedRows <= 0) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Knowledge document delete failed");
        }
    }

    private void syncVectorProjection(AlgorithmQuestion question) {
        try {
            algorithmQuestionVectorSyncService.sync(question);
        } catch (Exception ex) {
            question.setVectorSyncStatus("FAILED");
            question.setVectorSyncErrorMessage(ex.getMessage());
            updateMysqlQuestionOrThrow(question);
            createCompensationTask(question.getId(), "UPDATE", ex.getMessage());
            throw ex;
        }
    }

    private void createCompensationTask(Long questionId, String operationType, String errorMessage) {
        QuestionSyncCompensationTask task = new QuestionSyncCompensationTask();
        task.setQuestionId(questionId);
        task.setOperationType(operationType);
        task.setStatus("PENDING");
        task.setErrorMessage(errorMessage);
        task.setRetryCount(0);
        task.setLastAttemptTime(null);
        task.setIsDelete(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        compensationTaskMapper.insert(task);
    }

    private List<Tag> listQuestionTags(Long questionId) {
        List<AlgorithmQuestionTag> relations = algorithmQuestionTagMapper.selectList(new LambdaQueryWrapper<AlgorithmQuestionTag>()
                .eq(AlgorithmQuestionTag::getQuestionId, questionId));
        if (CollectionUtils.isEmpty(relations)) {
            return List.of();
        }
        List<Long> tagIds = relations.stream().map(AlgorithmQuestionTag::getTagId).distinct().toList();
        return tagMapper.selectBatchIds(tagIds);
    }

    private void populateQuestionTags(AlgorithmQuestion question) {
        if (question == null || question.getId() == null) {
            return;
        }
        List<String> tagNames = listQuestionTags(question.getId()).stream()
                .map(Tag::getTagName)
                .filter(Objects::nonNull)
                .toList();
        question.setTags(tagNames);
    }

    private void replaceQuestionTags(Long questionId, List<String> rawTags) {
        algorithmQuestionTagMapper.delete(new LambdaQueryWrapper<AlgorithmQuestionTag>()
                .eq(AlgorithmQuestionTag::getQuestionId, questionId));
        questionTagPersistenceService.persistQuestionTags(questionId, rawTags);
    }

    private List<AlgorithmCode> loadMysqlCodeTemplates(Long questionId) {
        return algorithmCodeService.listByQuestionId(questionId);
    }

    private void replaceQuestionCodeTemplates(Long questionId,
                                              List<AdminQuestionUpdateCommand.CodeTemplateUpdateCommand> templateUpdates) {
        algorithmCodeService.remove(new LambdaQueryWrapper<AlgorithmCode>()
                .eq(AlgorithmCode::getQuestionId, questionId));
        if (CollectionUtils.isEmpty(templateUpdates)) {
            return;
        }
        if (templateUpdates.stream().anyMatch(Objects::isNull)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), CODE_TEMPLATE_UPDATE_FAILURE_MESSAGE);
        }

        List<AlgorithmCode> codeRecords = templateUpdates.stream()
                .map(template -> toCodeRecord(questionId, template))
                .toList();
        boolean saved = algorithmCodeService.saveBatch(codeRecords);
        if (!saved) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), CODE_TEMPLATE_UPDATE_FAILURE_MESSAGE);
        }
    }

    private AlgorithmCode toCodeRecord(Long questionId, AdminQuestionUpdateCommand.CodeTemplateUpdateCommand template) {
        AlgorithmCode record = new AlgorithmCode();
        record.setQuestionId(questionId);
        record.setLanguage(template.getLanguage() == null ? null : template.getLanguage().trim());
        record.setFunctionName(template.getEntryMethodName() == null ? null : template.getEntryMethodName().trim());
        record.setCodeSkeleton(template.getStarterCode());
        record.setReferenceAnswer(template.getReferenceAnswer());
        record.setIsDelete(0);
        return record;
    }

    private void validateCreateRequest(AdminQuestionCreateCommand request) {
        if (request == null) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Create request cannot be null");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Title cannot be blank");
        }
        if (!StringUtils.hasText(request.getDescription())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Description cannot be blank");
        }
        if (!StringUtils.hasText(request.getDifficulty())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Difficulty cannot be blank");
        }
        if (CollectionUtils.isEmpty(request.getStandardCasePool())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Standard case pool cannot be empty");
        }
        String normalizedDifficulty = request.getDifficulty().trim().toUpperCase();
        if (!ALLOWED_DIFFICULTIES.contains(normalizedDifficulty)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                    "Difficulty must be SIMPLE, MEDIUM or HARD");
        }
    }

    private List<NormalizedCodeTemplate> normalizeCreateCodeTemplates(
            List<AdminQuestionCreateCommand.CodeTemplateCreateCommand> templates) {
        if (CollectionUtils.isEmpty(templates)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Code templates cannot be empty");
        }
        if (templates.stream().anyMatch(Objects::isNull)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Code template item cannot be null");
        }

        Set<Language> seenLanguages = EnumSet.noneOf(Language.class);
        List<NormalizedCodeTemplate> normalizedTemplates = new ArrayList<>(templates.size());
        for (AdminQuestionCreateCommand.CodeTemplateCreateCommand template : templates) {
            Language language = parseCreateTemplateLanguage(template.getLanguage());
            if (!seenLanguages.add(language)) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "Code templates contain duplicate language: " + language.name());
            }

            String functionName = trimToNull(template.getEntryMethodName());
            if (!StringUtils.hasText(functionName)) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "Template entry method name cannot be blank, language: " + language.name());
            }
            if (!StringUtils.hasText(template.getStarterCode())) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "Template starter code cannot be blank, language: " + language.name());
            }
            if (!StringUtils.hasText(template.getReferenceAnswer())) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "Template reference answer cannot be blank, language: " + language.name());
            }

            normalizedTemplates.add(new NormalizedCodeTemplate(
                    language,
                    functionName,
                    template.getStarterCode(),
                    template.getReferenceAnswer()
            ));
        }

        if (!seenLanguages.equals(REQUIRED_TEMPLATE_LANGUAGES)) {
            Set<Language> missingLanguages = EnumSet.copyOf(REQUIRED_TEMPLATE_LANGUAGES);
            missingLanguages.removeAll(seenLanguages);
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                    "Code templates must contain JAVA, PYTHON and JAVASCRIPT exactly once, missing: "
                            + joinLanguageNames(missingLanguages));
        }
        return normalizedTemplates;
    }

    private Language parseCreateTemplateLanguage(String rawLanguage) {
        try {
            return Language.fromName(rawLanguage);
        } catch (Exception ex) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                    "Unsupported template language: " + rawLanguage);
        }
    }

    private List<String> resolveCreateTagNames(List<Long> rawTagIds, List<String> rawTagNames) {
        // 合并“系统标签（ID）”和“自定义标签（名称）”，并保证顺序去重。
        LinkedHashSet<String> mergedTagNames = new LinkedHashSet<>();
        mergedTagNames.addAll(resolveTagNamesByIds(rawTagIds));
        mergedTagNames.addAll(normalizeTagNames(rawTagNames));
        if (mergedTagNames.size() > 20) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Tag count cannot exceed 20");
        }
        return List.copyOf(mergedTagNames);
    }

    private List<String> normalizeTagNames(List<String> rawTagNames) {
        if (CollectionUtils.isEmpty(rawTagNames)) {
            return List.of();
        }
        LinkedHashSet<String> normalizedTagNames = new LinkedHashSet<>();
        for (String rawTagName : rawTagNames) {
            String tagName = trimToNull(rawTagName);
            if (!StringUtils.hasText(tagName)) {
                continue;
            }
            normalizedTagNames.add(tagName);
        }
        return List.copyOf(normalizedTagNames);
    }

    private List<String> resolveTagNamesByIds(List<Long> rawTagIds) {
        if (CollectionUtils.isEmpty(rawTagIds)) {
            return List.of();
        }

        Set<Long> normalizedTagIds = new LinkedHashSet<>();
        for (Long tagId : rawTagIds) {
            if (tagId == null || tagId <= 0) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Invalid tag ID");
            }
            normalizedTagIds.add(tagId);
        }

        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .in(Tag::getId, normalizedTagIds)
                .eq(Tag::getIsDelete, 0));
        if (tags.size() != normalizedTagIds.size()) {
            Set<Long> foundIds = tags.stream().map(Tag::getId).filter(Objects::nonNull).collect(LinkedHashSet::new, Set::add, Set::addAll);
            Long missingId = normalizedTagIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElse(null);
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Tag does not exist: " + missingId);
        }

        Map<Long, String> tagNameById = new HashMap<>(tags.size());
        for (Tag tag : tags) {
            if (tag.getId() != null) {
                tagNameById.put(tag.getId(), tag.getTagName());
            }
        }

        List<String> tagNames = new ArrayList<>(normalizedTagIds.size());
        for (Long tagId : normalizedTagIds) {
            String tagName = trimToNull(tagNameById.get(tagId));
            if (!StringUtils.hasText(tagName)) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Tag name is empty: " + tagId);
            }
            tagNames.add(tagName);
        }
        return tagNames;
    }

    private List<StandardCasePoolCodec.StandardCaseItem> normalizeCreateStandardCasePool(
            List<AdminQuestionCreateCommand.StandardCaseCreateCommand> rawCasePool) {
        if (CollectionUtils.isEmpty(rawCasePool)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Standard case pool cannot be empty");
        }

        List<StandardCasePoolCodec.StandardCaseItem> items = new ArrayList<>(rawCasePool.size());
        for (int index = 0; index < rawCasePool.size(); index++) {
            AdminQuestionCreateCommand.StandardCaseCreateCommand current = rawCasePool.get(index);
            if (current == null) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "standardCasePool[%d] cannot be null".formatted(index));
            }
            String stdin = trimToNull(current.getStdin());
            String expectedStdout = trimToNull(current.getExpectedStdout());
            if (!StringUtils.hasText(stdin)) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "standardCasePool[%d].stdin cannot be blank".formatted(index));
            }
            if (!StringUtils.hasText(expectedStdout)) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "standardCasePool[%d].expectedStdout cannot be blank".formatted(index));
            }
            if (current.getPublicCase() == null) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "standardCasePool[%d].publicCase cannot be null".formatted(index));
            }

            StandardCasePoolCodec.StandardCaseItem item = new StandardCasePoolCodec.StandardCaseItem();
            item.setStdin(stdin);
            item.setExpectedStdout(expectedStdout);
            item.setPublicCase(current.getPublicCase());
            item.setDescription(trimToNull(current.getDescription()));
            items.add(item);
        }

        return StandardCasePoolCodec.parseAndNormalize(StandardCasePoolCodec.serialize(items));
    }

    private List<StandardCasePoolCodec.StandardCaseItem> normalizeUpdateStandardCasePool(
            List<AdminQuestionUpdateCommand.StandardCaseUpdateCommand> rawCasePool) {
        if (CollectionUtils.isEmpty(rawCasePool)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Standard case pool cannot be empty");
        }

        List<StandardCasePoolCodec.StandardCaseItem> items = new ArrayList<>(rawCasePool.size());
        for (int index = 0; index < rawCasePool.size(); index++) {
            AdminQuestionUpdateCommand.StandardCaseUpdateCommand current = rawCasePool.get(index);
            if (current == null) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "standardCasePool[%d] cannot be null".formatted(index));
            }
            String stdin = trimToNull(current.getStdin());
            String expectedStdout = trimToNull(current.getExpectedStdout());
            if (!StringUtils.hasText(stdin)) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "standardCasePool[%d].stdin cannot be blank".formatted(index));
            }
            if (!StringUtils.hasText(expectedStdout)) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "standardCasePool[%d].expectedStdout cannot be blank".formatted(index));
            }
            if (current.getPublicCase() == null) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(),
                        "standardCasePool[%d].publicCase cannot be null".formatted(index));
            }

            StandardCasePoolCodec.StandardCaseItem item = new StandardCasePoolCodec.StandardCaseItem();
            item.setStdin(stdin);
            item.setExpectedStdout(expectedStdout);
            item.setPublicCase(current.getPublicCase());
            item.setDescription(trimToNull(current.getDescription()));
            items.add(item);
        }

        return StandardCasePoolCodec.parseAndNormalize(StandardCasePoolCodec.serialize(items));
    }

    private AlgorithmQuestion buildCreateQuestion(AdminQuestionCreateCommand request,
                                                  List<StandardCasePoolCodec.StandardCaseItem> normalizedCasePool) {
        AlgorithmQuestion question = new AlgorithmQuestion();
        question.setUserId(resolveCurrentUserId());
        question.setTitle(request.getTitle().trim());
        question.setDescription(request.getDescription().trim());
        question.setDifficulty(request.getDifficulty().trim().toUpperCase());
        question.setType(AlgorithmQuestionTypeEnum.SYSTEM.name());
        question.setSharedFunctionName(null);
        question.setSharedCodeSkeleton(null);
        question.setStandardCasePool(StandardCasePoolCodec.serialize(normalizedCasePool));
        question.setConversationId(UUID.randomUUID().toString());
        question.setTraceId(UUID.randomUUID().toString());
        question.setAgentName("admin-question-management");
        question.setVectorSyncStatus("PENDING");
        question.setVectorSyncErrorMessage(null);
        question.setIsDelete(0);
        return question;
    }

    private AlgorithmCode toCreateCodeRecord(AlgorithmQuestion question, NormalizedCodeTemplate template) {
        AlgorithmCode code = new AlgorithmCode();
        code.setQuestionId(question.getId());
        code.setLanguage(template.language().name());
        code.setFunctionName(template.functionName());
        code.setCodeSkeleton(template.codeSkeleton());
        code.setReferenceAnswer(template.referenceAnswer());
        code.setGenerateModelKey(question.getGenerateModelKey());
        code.setTraceId(question.getTraceId());
        code.setAgentName(question.getAgentName());
        code.setIsDelete(0);
        return code;
    }

    private void validateUploadCommand(AdminQuestionDocumentUploadCommand command) {
        if (command == null) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Upload command cannot be null");
        }
        if (!StringUtils.hasText(command.getFilename())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Upload file cannot be empty");
        }
        if (command.getBytes() == null || command.getBytes().length == 0) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Upload file cannot be empty");
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Upload file cannot be empty");
        }

        // 提取并校验上传文件扩展名，兼容 pdf、md、markdown、zip。
        String extension = resolveFileExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(extension) || !ALLOWED_UPLOAD_DOCUMENT_EXTENSIONS.contains(extension)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), UPLOAD_DOCUMENT_EXTENSION_ERROR_MESSAGE);
        }
    }

    private String resolveFileExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }

        // 仅取最后一个点后的后缀，避免文件名中包含多个点导致误判。
        String trimmedFilename = filename.trim();
        int lastDotIndex = trimmedFilename.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex >= trimmedFilename.length() - 1) {
            return null;
        }
        return trimmedFilename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private MultipartFile toMultipartFile(AdminQuestionDocumentUploadCommand command) {
        return new InMemoryMultipartFile(command.getFilename(), command.getContentType(), command.getBytes());
    }

    private AdminQuestionDocumentResult toDocumentResult(KnowledgeDocument document) {
        AdminQuestionDocumentResult result = new AdminQuestionDocumentResult();
        result.setDocId(document.getDocId());
        result.setDocTitle(document.getDocTitle());
        result.setStatus(document.getStatus());
        result.setDocUrl(document.getDocUrl());
        result.setConvertedDocUrl(document.getConvertedDocUrl());
        result.setCreatedAt(document.getCreatedAt());
        result.setUpdatedAt(document.getUpdatedAt());
        result.setProgressPercent(mapProgress(document.getStatus()));
        return result;
    }

    private AdminQuestionDocumentResult toDocumentResult(KnowledgeDocumentParseResult parseResult) {
        AdminQuestionDocumentResult result = new AdminQuestionDocumentResult();
        result.setDocId(parseResult.getDocId());
        result.setDocTitle(parseResult.getDocTitle());
        result.setStatus(parseResult.getStatus());
        result.setDocUrl(parseResult.getDocUrl());
        result.setConvertedDocUrl(parseResult.getConvertedDocUrl());
        result.setProgressPercent(mapProgress(parseResult.getStatus()));
        return result;
    }

    // 批量加载文档标题，供全量片段分页回填展示字段。
    private Map<Long, KnowledgeDocument> loadDocumentMap(List<Long> docIds) {
        if (CollectionUtils.isEmpty(docIds)) {
            return Map.of();
        }
        List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectBatchIds(docIds);
        if (CollectionUtils.isEmpty(documents)) {
            return Map.of();
        }
        return documents.stream()
                .filter(Objects::nonNull)
                .filter(document -> document.getDocId() != null)
                .filter(document -> !Integer.valueOf(1).equals(document.getDeleted()))
                .collect(Collectors.toMap(KnowledgeDocument::getDocId, Function.identity(), (left, right) -> left));
    }

    // 按文档查询题目绑定关系，优先保留最新关联。
    private Map<Long, Long> loadQuestionIdByDocId(List<Long> docIds) {
        if (CollectionUtils.isEmpty(docIds)) {
            return Map.of();
        }
        List<AlgorithmQuestionKnowledgeDocument> relations = algorithmQuestionKnowledgeDocumentService.list(
                new LambdaQueryWrapper<AlgorithmQuestionKnowledgeDocument>()
                        .in(AlgorithmQuestionKnowledgeDocument::getDocId, docIds)
                        .eq(AlgorithmQuestionKnowledgeDocument::getIsDelete, 0)
                        .orderByDesc(AlgorithmQuestionKnowledgeDocument::getId)
        );
        if (CollectionUtils.isEmpty(relations)) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (AlgorithmQuestionKnowledgeDocument relation : relations) {
            if (relation == null || relation.getDocId() == null || relation.getQuestionId() == null) {
                continue;
            }
            result.putIfAbsent(relation.getDocId(), relation.getQuestionId());
        }
        return result;
    }

    // 批量加载题目标题，避免前端再做二次查询。
    private Map<Long, AlgorithmQuestion> loadQuestionMap(List<Long> questionIds) {
        if (CollectionUtils.isEmpty(questionIds)) {
            return Map.of();
        }
        List<AlgorithmQuestion> questions = algorithmQuestionService.listByIds(questionIds);
        if (CollectionUtils.isEmpty(questions)) {
            return Map.of();
        }
        return questions.stream()
                .filter(Objects::nonNull)
                .filter(question -> question.getId() != null)
                .filter(question -> !Integer.valueOf(1).equals(question.getIsDelete()))
                .collect(Collectors.toMap(AlgorithmQuestion::getId, Function.identity(), (left, right) -> left));
    }

    // 组装全量片段列表行，附带题目与文档的展示信息。
    private AdminKnowledgeSegmentResult toKnowledgeSegmentResult(KnowledgeSegment segment,
                                                                 Map<Long, KnowledgeDocument> documentMap,
                                                                 Map<Long, Long> questionIdByDocId,
                                                                 Map<Long, AlgorithmQuestion> questionMap) {
        AdminKnowledgeSegmentResult result = new AdminKnowledgeSegmentResult();
        result.setSegmentId(segment.getId());
        result.setDocumentId(segment.getDocumentId());
        result.setChunkOrder(segment.getChunkOrder());
        result.setChunkId(segment.getChunkId());
        result.setStatus(segment.getStatus());
        result.setSkipEmbedding(segment.getSkipEmbedding());
        result.setHitkQuestion(segment.getHitkQuestion());
        result.setText(segment.getText());
        result.setMetadata(segment.getMetadata());
        result.setCreatedAt(segment.getCreatedAt());
        result.setUpdatedAt(segment.getUpdatedAt());

        if (segment.getDocumentId() != null) {
            KnowledgeDocument document = documentMap.get(segment.getDocumentId());
            if (document != null) {
                result.setDocumentTitle(document.getDocTitle());
            }
            Long questionId = questionIdByDocId.get(segment.getDocumentId());
            result.setQuestionId(questionId);
            if (questionId != null) {
                AlgorithmQuestion question = questionMap.get(questionId);
                if (question != null) {
                    result.setQuestionTitle(question.getTitle());
                }
            }
        }
        return result;
    }

    private AdminQuestionDocumentSegmentResult toDocumentSegmentResult(KnowledgeSegment segment,
                                                                       String parentChunkId,
                                                                       boolean parentSegment,
                                                                       int childSegmentCount) {
        AdminQuestionDocumentSegmentResult result = new AdminQuestionDocumentSegmentResult();
        result.setSegmentId(segment.getId());
        result.setDocumentId(segment.getDocumentId());
        result.setChunkOrder(segment.getChunkOrder());
        result.setChunkId(segment.getChunkId());
        result.setParentChunkId(parentChunkId);
        result.setStatus(segment.getStatus());
        result.setSkipEmbedding(segment.getSkipEmbedding());
        result.setHitkQuestion(segment.getHitkQuestion());
        result.setText(segment.getText());
        result.setMetadata(segment.getMetadata());
        result.setParentSegment(parentSegment);
        result.setChildSegmentCount(Math.max(childSegmentCount, 0));
        result.setCreatedAt(segment.getCreatedAt());
        result.setUpdatedAt(segment.getUpdatedAt());
        return result;
    }

    private List<KnowledgeSegment> listDocumentSegments(Long docId) {
        return knowledgeSegmentMapper.selectList(new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(KnowledgeSegment::getDocumentId, docId)
                .eq(KnowledgeSegment::getDeleted, 0));
    }

    private void ensureQuestionDocumentReadyForSegmentQuery(Long questionId, Long docId) {
        // 先校验文档是否归属于当前题目。
        List<AlgorithmQuestionKnowledgeDocument> relations = algorithmQuestionKnowledgeDocumentService.listByQuestionId(questionId);
        boolean bound = !CollectionUtils.isEmpty(relations) && relations.stream()
                .map(AlgorithmQuestionKnowledgeDocument::getDocId)
                .anyMatch(boundDocId -> Objects.equals(boundDocId, docId));
        if (!bound) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Document does not belong to question");
        }

        // 再校验文档状态必须完成分段。
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(docId);
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Document does not exist");
        }
        String status = trimToNull(document.getStatus());
        if (!StringUtils.hasText(status) || !SEGMENT_READY_STATUSES.contains(status.toUpperCase())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Document segments are not ready");
        }
    }

    private Comparator<KnowledgeSegment> segmentSortComparator() {
        return Comparator
                .comparing((KnowledgeSegment segment) -> segment.getChunkOrder() == null ? Integer.MAX_VALUE : segment.getChunkOrder())
                .thenComparing(segment -> segment.getId() == null ? Long.MAX_VALUE : segment.getId());
    }

    private String parseParentChunkId(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return null;
        }
        try {
            Map<?, ?> metadata = objectMapper.readValue(metadataJson, Map.class);
            Object value = metadata.get(MetadataKeyConstant.PARENT_CHUNK_ID);
            return value == null ? null : trimToNull(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private long normalizeCurrent(Long current) {
        if (current == null || current <= 0L) {
            return DEFAULT_SEGMENT_PAGE_CURRENT;
        }
        return current;
    }

    private long normalizeSize(Long size) {
        if (size == null || size <= 0L) {
            return DEFAULT_SEGMENT_PAGE_SIZE;
        }
        return Math.min(size, MAX_SEGMENT_PAGE_SIZE);
    }

    private int mapProgress(String status) {
        if (!StringUtils.hasText(status)) {
            return PROGRESS_PENDING;
        }
        if (KnowledgeDocumentStatus.UPLOADED.name().equalsIgnoreCase(status)) {
            return PROGRESS_UPLOADED;
        }
        if (KnowledgeDocumentStatus.CONVERTING.name().equalsIgnoreCase(status)) {
            return PROGRESS_CONVERTING;
        }
        if (KnowledgeDocumentStatus.VECTOR_STORED.name().equalsIgnoreCase(status)) {
            return PROGRESS_VECTOR_STORED;
        }
        if (KnowledgeDocumentStatus.FAILED.name().equalsIgnoreCase(status)) {
            return PROGRESS_FAILED;
        }
        return PROGRESS_PENDING;
    }

    private Long resolveCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null || userId <= 0) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Current user is required");
        }
        return userId;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String joinLanguageNames(Set<Language> languages) {
        if (CollectionUtils.isEmpty(languages)) {
            return "";
        }
        return languages.stream().map(Enum::name).sorted().reduce((left, right) -> left + ", " + right).orElse("");
    }

    private void applyUpdate(AlgorithmQuestion target, AdminQuestionUpdateCommand request) {
        if (request.getTitle() != null) {
            target.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            target.setDescription(request.getDescription().trim());
        }
        if (request.getDifficulty() != null) {
            target.setDifficulty(request.getDifficulty().trim());
        }
        if (request.getStandardCasePool() != null) {
            List<StandardCasePoolCodec.StandardCaseItem> normalizedCasePool =
                    normalizeUpdateStandardCasePool(request.getStandardCasePool());
            target.setStandardCasePool(StandardCasePoolCodec.serialize(normalizedCasePool));
        }
        target.setVectorSyncStatus("SUCCESS");
        target.setVectorSyncErrorMessage(null);
    }

    private void validateUpdateRequest(AdminQuestionUpdateCommand request) {
        if (request == null) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Update request cannot be null");
        }
        if (isNoUpdateFieldProvided(request)) {
            throw new ValidationException(
                    SecurityErrorCode.TABLE_001.getCode(),
                    "At least one updatable field is required"
            );
        }
    }

    private boolean isNoUpdateFieldProvided(AdminQuestionUpdateCommand request) {
        return request.getTitle() == null
                && request.getDescription() == null
                && request.getDifficulty() == null
                && request.getStandardCasePool() == null
                && request.getCodeTemplates() == null
                && request.getTags() == null;
    }

    private List<Long> normalizeBatchIds(List<Long> questionIds) {
        if (CollectionUtils.isEmpty(questionIds)) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Question IDs cannot be empty");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long questionId : questionIds) {
            if (questionId == null || questionId <= 0) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Invalid question ID");
            }
            uniqueIds.add(questionId);
        }
        return List.copyOf(uniqueIds);
    }

    private void validateQuestionId(Long questionId) {
        if (questionId == null || questionId <= 0) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Invalid question ID");
        }
    }

    private void validateDocId(Long docId) {
        if (docId == null || docId <= 0) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Invalid document ID");
        }
    }

    private void validateSegmentId(Long segmentId) {
        if (segmentId == null || segmentId <= 0) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Invalid segment ID");
        }
    }

    private AlgorithmQuestion loadMysqlQuestionOrThrow(Long questionId) {
        AlgorithmQuestion question = algorithmQuestionService.getById(questionId);
        if (question == null || Integer.valueOf(1).equals(question.getIsDelete())) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Question does not exist");
        }
        return question;
    }

    private AlgorithmQuestionResult loadReadableQuestionOrThrow(Long questionId) {
        try {
            AlgorithmQuestionResult question = algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(questionId));
            if (question == null) {
                throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Question does not exist");
            }
            return question;
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "Question does not exist");
        }
    }

    private void updateMysqlQuestionOrThrow(AlgorithmQuestion question) {
        boolean updated = algorithmQuestionService.updateById(question);
        if (!updated) {
            throw new ValidationException(SecurityErrorCode.TABLE_001.getCode(), "MySQL question update failed");
        }
    }

    private AdminQuestionVectorProjectionResult loadVectorProjectionSafely(Long questionId) {
        try {
            return agentVectorStore.findByQuestionId(questionId)
                    .map(this::toVectorProjection)
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private AdminQuestionVectorProjectionResult toVectorProjection(VectorDocument document) {
        return new AdminQuestionVectorProjectionResult(
                document.getId(),
                document.getQuestionId(),
                document.getTitle(),
                document.getDifficulty(),
                document.getLanguage(),
                document.getEmbedding(),
                document.isDeleted() ? 1 : 0
        );
    }

    private static final class InMemoryMultipartFile implements MultipartFile {

        private final String filename;
        private final String contentType;
        private final byte[] bytes;

        private InMemoryMultipartFile(String filename, String contentType, byte[] bytes) {
            this.filename = filename;
            this.contentType = contentType;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), bytes);
        }
    }

    private record NormalizedCodeTemplate(Language language,
                                          String functionName,
                                          String codeSkeleton,
                                          String referenceAnswer) {
    }
}
