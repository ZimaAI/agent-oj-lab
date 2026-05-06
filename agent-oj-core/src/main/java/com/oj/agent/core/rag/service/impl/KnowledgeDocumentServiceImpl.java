package com.oj.agent.core.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.file.model.command.FileUploadCommand;
import com.oj.agent.core.file.model.result.FileRecordResult;
import com.oj.agent.core.rag.client.MineruApiClient;
import com.oj.agent.core.rag.converter.KnowledgeDocumentConverter;
import com.oj.agent.core.file.service.FileService;
import com.oj.agent.core.rag.enums.KnowledgeBaseType;
import com.oj.agent.core.rag.enums.KnowledgeDocumentStatus;
import com.oj.agent.core.rag.event.KnowledgeDocumentConvertedEvent;
import com.oj.agent.core.rag.exception.KnowledgeDocumentException;
import com.oj.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentMapper;
import com.oj.agent.core.rag.model.command.KnowledgeDocumentBatchParseCommand;
import com.oj.agent.core.rag.model.command.KnowledgeDocumentParseCommand;
import com.oj.agent.core.rag.model.dto.ConvertedDocumentResult;
import com.oj.agent.core.rag.model.dto.MineruTaskResult;
import com.oj.agent.core.rag.model.entity.KnowledgeDocument;
import com.oj.agent.core.rag.model.result.KnowledgeDocumentParseResult;
import com.oj.agent.core.rag.model.result.KnowledgeDocumentResult;
import com.oj.agent.core.rag.processor.KnowledgeDocumentProcessor;
import com.oj.agent.core.rag.service.KnowledgeDocumentAsyncService;
import com.oj.agent.core.rag.service.KnowledgeDocumentService;
import com.oj.agent.redis.lock.annotation.DistributeLock;
import com.oj.agent.security.util.UserContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String USER_UPLOAD_SCENE = "knowledge:document:upload:user";
    private static final String USER_KEY_EXPRESSION = "T(String).valueOf(T(com.oj.agent.security.util.UserContext).getUserId())";
    private static final String KEY_SOURCE_FILE_TYPE = "sourceFileType";
    private static final String KEY_FAILED_REASON = "failedReason";

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeSegmentMapper knowledgeSegmentMapper;
    private final FileService fileService;
    private final MineruApiClient mineruApiClient;
    private final KnowledgeDocumentProcessor knowledgeDocumentProcessor;
    private final KnowledgeDocumentAsyncService knowledgeDocumentAsyncService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public KnowledgeDocumentServiceImpl(KnowledgeDocumentMapper knowledgeDocumentMapper,
                                        KnowledgeSegmentMapper knowledgeSegmentMapper,
                                        FileService fileService,
                                        MineruApiClient mineruApiClient,
                                        KnowledgeDocumentProcessor knowledgeDocumentProcessor,
                                        KnowledgeDocumentAsyncService knowledgeDocumentAsyncService,
                                        ApplicationEventPublisher applicationEventPublisher) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeSegmentMapper = knowledgeSegmentMapper;
        this.fileService = fileService;
        this.mineruApiClient = mineruApiClient;
        this.knowledgeDocumentProcessor = knowledgeDocumentProcessor;
        this.knowledgeDocumentAsyncService = knowledgeDocumentAsyncService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentParseResult parseByUrl(KnowledgeDocumentParseCommand command) {
        if (command == null || !StringUtils.hasText(command.getFileUrl())) {
            throw new IllegalArgumentException("fileUrl must not be blank");
        }
        Long userId = requireCurrentUserId();
        KnowledgeDocument document = createDocumentRecord(userId, command, command.getFileUrl().trim());
        try {
            updateDocumentStatus(document, KnowledgeDocumentStatus.CONVERTING.name(), null, null);
            String taskId = mineruApiClient.submitUrlTask(document.getDocUrl(), document.getDocTitle());
            MineruTaskResult taskResult = mineruApiClient.waitTaskResult(taskId);
            return processFinishedTask(document, taskResult, Map.of("taskId", taskId));
        } catch (RuntimeException ex) {
            markFailed(document, ex);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<KnowledgeDocumentParseResult> parseByUrlBatch(KnowledgeDocumentBatchParseCommand command) {
        if (command == null || command.getDocuments() == null || command.getDocuments().isEmpty()) {
            throw new IllegalArgumentException("requests must not be empty");
        }
        List<KnowledgeDocumentParseCommand> requests = command.getDocuments();
        Long userId = requireCurrentUserId();
        List<KnowledgeDocument> documents = new ArrayList<>(requests.size());
        List<MineruApiClient.UrlBatchFile> batchFiles = new ArrayList<>(requests.size());
        for (KnowledgeDocumentParseCommand request : requests) {
            if (request == null || !StringUtils.hasText(request.getFileUrl())) {
                throw new IllegalArgumentException("fileUrl must not be blank in batch");
            }
            KnowledgeDocument document = createDocumentRecord(userId, request, request.getFileUrl().trim());
            documents.add(document);
            batchFiles.add(new MineruApiClient.UrlBatchFile(String.valueOf(document.getDocId()), document.getDocUrl()));
        }

        String batchId = mineruApiClient.submitUrlBatch(batchFiles);
        List<MineruTaskResult> results = mineruApiClient.waitBatchResults(batchId);
        Map<String, MineruTaskResult> resultMap = new HashMap<>();
        for (MineruTaskResult result : results) {
            if (StringUtils.hasText(result.getDataId())) {
                resultMap.put(result.getDataId().trim(), result);
            }
        }

        List<KnowledgeDocumentParseResult> parseResults = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            KnowledgeDocument document = documents.get(i);
            MineruTaskResult taskResult = resultMap.getOrDefault(String.valueOf(document.getDocId()), i < results.size() ? results.get(i) : null);
            try {
                updateDocumentStatus(document, KnowledgeDocumentStatus.CONVERTING.name(), null, null);
                parseResults.add(processFinishedTask(document, taskResult, Map.of("batchId", batchId)));
            } catch (RuntimeException ex) {
                markFailed(document, ex);
                parseResults.add(KnowledgeDocumentConverter.toParseResult(document));
            }
        }
        return parseResults;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = USER_UPLOAD_SCENE, keyExpression = USER_KEY_EXPRESSION)
    public KnowledgeDocumentParseResult parseByUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        KnowledgeDocumentAsyncService.UploadedFileType uploadedFileType =
                KnowledgeDocumentAsyncService.UploadedFileType.resolveByFilename(file.getOriginalFilename());
        Long userId = requireCurrentUserId();
        FileRecordResult uploaded = fileService.upload(toFileUploadCommand(file));
        KnowledgeDocumentParseCommand command = buildUploadParseCommand(uploaded);
        KnowledgeDocument document = createDocumentRecord(userId, command, uploaded.getFileUrl());
        persistSourceFileType(document, uploadedFileType);

        Path downloadedPath = null;
        try {
            updateDocumentStatus(document, KnowledgeDocumentStatus.CONVERTING.name(), null, null);
            downloadedPath = knowledgeDocumentProcessor.downloadRemoteFile(
                    document.getDocUrl(),
                    uploadedFileType.getDownloadSuffix()
            );
            Map<String, Object> traceMeta = buildAsyncTraceMeta(document, uploaded, uploadedFileType);
            ConvertedDocumentResult converted;
            if (KnowledgeDocumentAsyncService.UploadedFileType.PDF.equals(uploadedFileType)) {
                String batchId = mineruApiClient.submitLocalBatch(List.of(downloadedPath));
                List<MineruTaskResult> results = mineruApiClient.waitBatchResults(batchId);
                MineruTaskResult taskResult = results.isEmpty() ? null : results.get(0);
                traceMeta.put("batchId", batchId);
                converted = processFinishedTaskConverted(document, taskResult, traceMeta);
            } else if (KnowledgeDocumentAsyncService.UploadedFileType.MARKDOWN.equals(uploadedFileType)) {
                converted = knowledgeDocumentProcessor.convertUploadedMarkdownAndVectorize(
                        document.getDocId(),
                        document.getDocTitle(),
                        downloadedPath,
                        traceMeta
                );
            } else {
                converted = knowledgeDocumentProcessor.convertUploadedZipAndVectorize(
                        document.getDocId(),
                        document.getDocTitle(),
                        downloadedPath,
                        traceMeta
                );
            }
            Map<String, Object> extension = new HashMap<>();
            if (converted != null && converted.getExtension() != null) {
                extension.putAll(converted.getExtension());
            }
            extension.putIfAbsent(KEY_SOURCE_FILE_TYPE, uploadedFileType.name());
            String extensionJson = toExtensionJson(extension);
            updateDocumentStatus(document, KnowledgeDocumentStatus.CONVERTED.name(), converted.getConvertedDocUrl(), extensionJson);
            publishConvertedEvent(document.getDocId());
            return KnowledgeDocumentConverter.toParseResult(document);
        } catch (RuntimeException ex) {
            markFailed(document, ex);
            throw ex;
        } finally {
            knowledgeDocumentProcessor.cleanupPathAsync(downloadedPath);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = USER_UPLOAD_SCENE, keyExpression = USER_KEY_EXPRESSION)
    public KnowledgeDocumentParseResult parseByUploadAsync(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        KnowledgeDocumentAsyncService.UploadedFileType uploadedFileType =
                KnowledgeDocumentAsyncService.UploadedFileType.resolveByFilename(file.getOriginalFilename());
        Long userId = requireCurrentUserId();
        KnowledgeDocument document = null;
        Path downloadedPath = null;
        try {
            // 先上传文件并创建文档记录。
            FileRecordResult uploaded = fileService.upload(toFileUploadCommand(file));
            KnowledgeDocumentParseCommand command = buildUploadParseCommand(uploaded);
            document = createDocumentRecord(userId, command, uploaded.getFileUrl());
            persistSourceFileType(document, uploadedFileType);

            // 根据文件类型选择下载后缀，并透传异步处理参数。
            downloadedPath = knowledgeDocumentProcessor.downloadRemoteFile(
                    document.getDocUrl(),
                    uploadedFileType.getDownloadSuffix()
            );
            Map<String, Object> traceMeta = buildAsyncTraceMeta(document, uploaded, uploadedFileType);
            submitUploadedDocumentAsync(document, downloadedPath, uploadedFileType, traceMeta);

            // 立即返回当前文档状态，不阻塞等待异步任务。
            return KnowledgeDocumentConverter.toParseResult(document);
        } catch (RuntimeException ex) {
            // 异常时更新失败状态并清理临时文件。
            if (document != null) {
                markFailed(document, ex);
            }
            knowledgeDocumentProcessor.cleanupPathAsync(downloadedPath);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = USER_UPLOAD_SCENE, keyExpression = USER_KEY_EXPRESSION)
    public List<KnowledgeDocumentParseResult> parseByUploadBatch(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files must not be empty");
        }
        Long userId = requireCurrentUserId();
        List<KnowledgeDocument> documents = new ArrayList<>(files.size());
        List<Path> downloadedFiles = new ArrayList<>(files.size());

        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    throw new IllegalArgumentException("file must not be empty in batch");
                }
                FileRecordResult uploaded = fileService.upload(toFileUploadCommand(file));
                KnowledgeDocumentParseCommand command = buildUploadParseCommand(uploaded);
                KnowledgeDocument document = createDocumentRecord(userId, command, uploaded.getFileUrl());
                documents.add(document);
                downloadedFiles.add(knowledgeDocumentProcessor.downloadRemoteFile(document.getDocUrl(), ".pdf"));
                updateDocumentStatus(document, KnowledgeDocumentStatus.CONVERTING.name(), null, null);
            }
            String batchId = mineruApiClient.submitLocalBatch(downloadedFiles);
            List<MineruTaskResult> results = mineruApiClient.waitBatchResults(batchId);

            List<KnowledgeDocumentParseResult> parseResults = new ArrayList<>(documents.size());
            for (int i = 0; i < documents.size(); i++) {
                KnowledgeDocument document = documents.get(i);
                MineruTaskResult taskResult = i < results.size() ? results.get(i) : null;
                try {
                    parseResults.add(processFinishedTask(document, taskResult, Map.of("batchId", batchId)));
                } catch (RuntimeException ex) {
                    markFailed(document, ex);
                    parseResults.add(KnowledgeDocumentConverter.toParseResult(document));
                }
            }
            return parseResults;
        } finally {
            downloadedFiles.forEach(knowledgeDocumentProcessor::cleanupPathAsync);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<KnowledgeDocumentResult> pageCurrentUserDocuments(long pageNum, long pageSize) {
        Long userId = requireCurrentUserId();
        long safePageNum = pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
        long safePageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        Page<KnowledgeDocument> page = new Page<>(safePageNum, safePageSize);
        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getUploadUser, String.valueOf(userId))
                .eq(KnowledgeDocument::getDeleted, 0)
                .orderByDesc(KnowledgeDocument::getDocId);
        Page<KnowledgeDocument> entityPage = knowledgeDocumentMapper.selectPage(page, queryWrapper);

        Page<KnowledgeDocumentResult> resultPage = new Page<>(safePageNum, safePageSize, entityPage.getTotal());
        resultPage.setRecords(entityPage.getRecords().stream()
                .map(KnowledgeDocumentConverter::toDocumentResult)
                .toList());
        return resultPage;
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeDocumentResult getCurrentUserDocument(Long docId) {
        Long userId = requireCurrentUserId();
        KnowledgeDocument entity = requireOwnedDocument(docId, userId);
        return KnowledgeDocumentConverter.toDocumentResult(entity);
    }

    private KnowledgeDocumentParseResult processFinishedTask(KnowledgeDocument document,
                                                             MineruTaskResult taskResult,
                                                             Map<String, Object> traceMeta) {
        ConvertedDocumentResult converted = processFinishedTaskConverted(document, taskResult, traceMeta);
        Map<String, Object> extension = new HashMap<>();
        if (converted != null && converted.getExtension() != null) {
            extension.putAll(converted.getExtension());
        }
        Object sourceType = traceMeta == null ? null : traceMeta.get(KEY_SOURCE_FILE_TYPE);
        if (sourceType instanceof String type && StringUtils.hasText(type)) {
            extension.putIfAbsent(KEY_SOURCE_FILE_TYPE, type.trim());
        }
        String extensionJson = toExtensionJson(extension);
        updateDocumentStatus(document, KnowledgeDocumentStatus.CONVERTED.name(), converted.getConvertedDocUrl(), extensionJson);
        publishConvertedEvent(document.getDocId());
        return KnowledgeDocumentConverter.toParseResult(document);
    }

    private ConvertedDocumentResult processFinishedTaskConverted(KnowledgeDocument document,
                                                                 MineruTaskResult taskResult,
                                                                 Map<String, Object> traceMeta) {
        if (taskResult == null) {
            throw new KnowledgeDocumentException("Mineru result is empty");
        }
        if ("failed".equalsIgnoreCase(taskResult.getState())) {
            throw new KnowledgeDocumentException("Mineru parse failed: " + taskResult.getErrMsg());
        }
        if (!"done".equalsIgnoreCase(taskResult.getState())) {
            throw new KnowledgeDocumentException("Mineru parse status unexpected: " + taskResult.getState());
        }
        if (!StringUtils.hasText(taskResult.getFullZipUrl())) {
            throw new KnowledgeDocumentException("Mineru parse result zip url missing");
        }

        ConvertedDocumentResult converted = knowledgeDocumentProcessor.convertZipAndVectorize(
                document.getDocId(),
                document.getDocTitle(),
                taskResult.getFullZipUrl(),
                traceMeta
        );
        return converted;
    }

    private void publishConvertedEvent(Long docId) {
        // 文档转换完成后发布事件，触发后续分段流程。
        if (docId == null || docId <= 0) {
            return;
        }
        applicationEventPublisher.publishEvent(new KnowledgeDocumentConvertedEvent(docId));
    }

    private KnowledgeDocument createDocumentRecord(Long userId,
                                                   KnowledgeDocumentParseCommand command,
                                                   String sourceDocUrl) {
        String docTitle = StringUtils.hasText(command.getDocTitle())
                ? command.getDocTitle().trim()
                : inferTitle(sourceDocUrl);
        KnowledgeDocument entity = new KnowledgeDocument();
        entity.setDocTitle(docTitle);
        entity.setUploadUser(String.valueOf(userId));
        entity.setDocUrl(sourceDocUrl);
        entity.setStatus(KnowledgeDocumentStatus.UPLOADED.name());
        entity.setAccessibleBy(command.getAccessibleBy());
        entity.setDescription(command.getDescription());
        entity.setKnowledgeBaseType(KnowledgeBaseType.normalize(command.getKnowledgeBaseType()));
        entity.setDeleted(0);
        entity.setLockVersion(0);
        knowledgeDocumentMapper.insert(entity);
        return entity;
    }

    private KnowledgeDocumentParseCommand buildUploadParseCommand(FileRecordResult uploaded) {
        KnowledgeDocumentParseCommand command = new KnowledgeDocumentParseCommand();
        command.setFileUrl(uploaded.getFileUrl());
        command.setDocTitle(uploaded.getOriginalFilename());
        command.setKnowledgeBaseType(KnowledgeBaseType.DOCUMENT_SEARCH.name());
        return command;
    }

    private void submitUploadedDocumentAsync(KnowledgeDocument document,
                                             Path downloadedPath,
                                             KnowledgeDocumentAsyncService.UploadedFileType uploadedFileType,
                                             Map<String, Object> traceMeta) {
        // 若当前存在事务，则在提交后触发异步任务。
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    knowledgeDocumentAsyncService.processUploadedDocumentAsync(
                            document.getDocId(),
                            downloadedPath,
                            uploadedFileType,
                            traceMeta
                    );
                }
            });
            return;
        }
        // 无事务上下文时直接提交异步任务。
        knowledgeDocumentAsyncService.processUploadedDocumentAsync(
                document.getDocId(),
                downloadedPath,
                uploadedFileType,
                traceMeta
        );
    }

    private Map<String, Object> buildAsyncTraceMeta(KnowledgeDocument document,
                                                    FileRecordResult uploaded,
                                                    KnowledgeDocumentAsyncService.UploadedFileType uploadedFileType) {
        // 透传必要链路信息，便于异步阶段记录来源与上下文。
        Map<String, Object> traceMeta = new HashMap<>();
        traceMeta.put("source", "uploadAsync");
        traceMeta.put("docTitle", document.getDocTitle());
        traceMeta.put("docUrl", document.getDocUrl());
        traceMeta.put("uploadedFileName", uploaded.getOriginalFilename());
        traceMeta.put(KEY_SOURCE_FILE_TYPE, uploadedFileType.name());
        return traceMeta;
    }

    private FileUploadCommand toFileUploadCommand(MultipartFile file) {
        FileUploadCommand command = new FileUploadCommand();
        command.setFile(file);
        return command;
    }

    private void updateDocumentStatus(KnowledgeDocument entity, String status, String convertedDocUrl, String extension) {
        entity.setStatus(status);
        if (convertedDocUrl != null) {
            entity.setConvertedDocUrl(convertedDocUrl);
        }
        if (extension != null) {
            entity.setExtension(extension);
        }
        knowledgeDocumentMapper.updateById(entity);
    }

    private void markFailed(KnowledgeDocument entity, RuntimeException exception) {
        entity.setStatus(KnowledgeDocumentStatus.FAILED.name());
        Map<String, Object> extension = parseExtension(entity.getExtension());
        extension.put(KEY_FAILED_REASON, sanitize(exception.getMessage()));
        entity.setExtension(toExtensionJson(extension));
        knowledgeDocumentMapper.updateById(entity);
    }

    private void persistSourceFileType(KnowledgeDocument entity,
                                       KnowledgeDocumentAsyncService.UploadedFileType uploadedFileType) {
        Map<String, Object> extension = parseExtension(entity.getExtension());
        extension.put(KEY_SOURCE_FILE_TYPE, uploadedFileType.name());
        entity.setExtension(toExtensionJson(extension));
        knowledgeDocumentMapper.updateById(entity);
    }

    private Map<String, Object> parseExtension(String extensionJson) {
        if (!StringUtils.hasText(extensionJson)) {
            return new HashMap<>();
        }
        try {
            return new HashMap<>(JsonUtils.fromJson(extensionJson, Map.class));
        } catch (IOException exception) {
            return new HashMap<>();
        }
    }

    private String toExtensionJson(Map<String, Object> extension) {
        try {
            return JsonUtils.toJson(extension);
        } catch (IOException exception) {
            throw new KnowledgeDocumentException("Serialize document extension failed", exception);
        }
    }

    private KnowledgeDocument requireOwnedDocument(Long docId, Long userId) {
        if (docId == null || docId <= 0) {
            throw new IllegalArgumentException("docId must be a positive number");
        }
        KnowledgeDocument entity = knowledgeDocumentMapper.selectById(docId);
        if (entity == null || !Objects.equals(entity.getDeleted(), 0)) {
            throw new IllegalArgumentException("document not found: " + docId);
        }
        if (!String.valueOf(userId).equals(entity.getUploadUser())) {
            throw new IllegalArgumentException("no permission to access document: " + docId);
        }
        return entity;
    }

    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("Current user login required");
        }
        return userId;
    }

    private String inferTitle(String sourceDocUrl) {
        if (!StringUtils.hasText(sourceDocUrl)) {
            return "knowledge-document";
        }
        String value = sourceDocUrl.trim();
        int slash = value.lastIndexOf('/');
        if (slash >= 0 && slash < value.length() - 1) {
            value = value.substring(slash + 1);
        }
        return value.isBlank() ? "knowledge-document" : value;
    }

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "'").replace("\r", " ").replace("\n", " ");
    }
}
