package com.oj.agent.core.rag.service.impl;

import com.oj.agent.core.rag.enums.KnowledgeDocumentStatus;
import com.oj.agent.core.rag.model.dto.ConvertedDocumentResult;
import com.oj.agent.core.rag.model.dto.MineruTaskResult;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.rag.client.MineruApiClient;
import com.oj.agent.core.rag.event.KnowledgeDocumentConvertedEvent;
import com.oj.agent.core.rag.exception.KnowledgeDocumentException;
import com.oj.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentMapper;
import com.oj.agent.core.rag.model.entity.KnowledgeDocument;
import com.oj.agent.core.rag.processor.KnowledgeDocumentProcessor;
import com.oj.agent.core.rag.service.KnowledgeDocumentAsyncService;
import com.oj.agent.redis.lock.annotation.DistributeLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KnowledgeDocumentAsyncServiceImpl implements KnowledgeDocumentAsyncService {

    private static final String DOC_PIPELINE_SCENE = "knowledge:document:pipeline";
    private static final String KEY_SOURCE_FILE_TYPE = "sourceFileType";
    private static final String KEY_FAILED_REASON = "failedReason";

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeSegmentMapper knowledgeSegmentMapper;
    private final MineruApiClient mineruApiClient;
    private final KnowledgeDocumentProcessor knowledgeDocumentProcessor;
    private final ApplicationEventPublisher applicationEventPublisher;

    public KnowledgeDocumentAsyncServiceImpl(KnowledgeDocumentMapper knowledgeDocumentMapper,
                                             KnowledgeSegmentMapper knowledgeSegmentMapper,
                                             MineruApiClient mineruApiClient,
                                             KnowledgeDocumentProcessor knowledgeDocumentProcessor,
                                             ApplicationEventPublisher applicationEventPublisher) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeSegmentMapper = knowledgeSegmentMapper;
        this.mineruApiClient = mineruApiClient;
        this.knowledgeDocumentProcessor = knowledgeDocumentProcessor;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Async("knowledgeDocumentAsyncExecutor")
    @DistributeLock(scene = DOC_PIPELINE_SCENE, keyExpression = "#p0")
    public void processUploadedDocumentAsync(Long docId,
                                             Path downloadedPath,
                                             UploadedFileType uploadedFileType,
                                             Map<String, Object> traceMeta) {
        if (docId == null || docId <= 0) {
            throw new IllegalArgumentException("docId must be a positive number");
        }
        if (downloadedPath == null) {
            throw new IllegalArgumentException("downloadedPath must not be null");
        }
        if (uploadedFileType == null) {
            throw new IllegalArgumentException("uploadedFileType must not be null");
        }
        KnowledgeDocument document = requireDocument(docId);
        processUploadedDocument(document, downloadedPath, uploadedFileType, traceMeta);
    }

    @Override
    @Async("knowledgeDocumentAsyncExecutor")
    @DistributeLock(scene = DOC_PIPELINE_SCENE, keyExpression = "#p0")
    public void processUploadedDocumentCompensation(Long docId, Map<String, Object> traceMeta) {
        if (docId == null || docId <= 0) {
            throw new IllegalArgumentException("docId must be a positive number");
        }
        KnowledgeDocument document = requireDocument(docId);
        if (!StringUtils.hasText(document.getDocUrl())) {
            throw new KnowledgeDocumentException("Document source url is blank");
        }

        // 补偿流程优先使用已持久化的上传类型，避免 markdown/zip 回退到 MinerU。
        UploadedFileType uploadedFileType = resolveUploadedFileTypeForCompensation(document);
        Path downloadedPath = knowledgeDocumentProcessor.downloadRemoteFile(
                document.getDocUrl(),
                uploadedFileType.getDownloadSuffix()
        );

        Map<String, Object> compensationTraceMeta = new HashMap<>();
        if (traceMeta != null) {
            compensationTraceMeta.putAll(traceMeta);
        }
        compensationTraceMeta.putIfAbsent("source", "compensationTask");
        compensationTraceMeta.putIfAbsent("docUrl", document.getDocUrl());
        compensationTraceMeta.putIfAbsent("docTitle", document.getDocTitle());
        compensationTraceMeta.putIfAbsent(KEY_SOURCE_FILE_TYPE, uploadedFileType.name());
        processUploadedDocument(document, downloadedPath, uploadedFileType, compensationTraceMeta);
    }

    private void processUploadedDocument(KnowledgeDocument document,
                                         Path downloadedPath,
                                         UploadedFileType uploadedFileType,
                                         Map<String, Object> traceMeta) {
        try {
            // 先推进状态到 CONVERTING，便于外部感知处理进度。
            updateDocumentStatus(document, KnowledgeDocumentStatus.CONVERTING.name(), null, null);
            Map<String, Object> asyncTraceMeta = new HashMap<>();
            if (traceMeta != null) {
                asyncTraceMeta.putAll(traceMeta);
            }
            asyncTraceMeta.putIfAbsent(KEY_SOURCE_FILE_TYPE, uploadedFileType.name());

            ConvertedDocumentResult converted = switch (uploadedFileType) {
                case PDF -> processPdfUploadedDocument(document, downloadedPath, asyncTraceMeta);
                case MARKDOWN -> knowledgeDocumentProcessor.convertUploadedMarkdownAndVectorize(
                        document.getDocId(),
                        document.getDocTitle(),
                        downloadedPath,
                        asyncTraceMeta
                );
                case ZIP -> knowledgeDocumentProcessor.convertUploadedZipAndVectorize(
                        document.getDocId(),
                        document.getDocTitle(),
                        downloadedPath,
                        asyncTraceMeta
                );
            };
            persistConvertedResult(document, converted, uploadedFileType);
        } catch (RuntimeException ex) {
            // 失败时保留源文件类型并记录失败原因，方便补偿流程继续识别分支。
            markFailed(document, ex);
            throw ex;
        } finally {
            knowledgeDocumentProcessor.cleanupPathAsync(downloadedPath);
        }
    }

    private ConvertedDocumentResult processPdfUploadedDocument(KnowledgeDocument document,
                                                               Path downloadedPath,
                                                               Map<String, Object> traceMeta) {
        String batchId = mineruApiClient.submitLocalBatch(List.of(downloadedPath));
        List<MineruTaskResult> results = mineruApiClient.waitBatchResults(batchId);
        MineruTaskResult taskResult = results.isEmpty() ? null : results.get(0);
        Map<String, Object> pdfTraceMeta = new HashMap<>(traceMeta);
        pdfTraceMeta.put("batchId", batchId);
        return processMineruTaskResult(document, taskResult, pdfTraceMeta);
    }

    private ConvertedDocumentResult processMineruTaskResult(KnowledgeDocument document,
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
        return knowledgeDocumentProcessor.convertZipAndVectorize(
                document.getDocId(),
                document.getDocTitle(),
                taskResult.getFullZipUrl(),
                traceMeta
        );
    }

    private void persistConvertedResult(KnowledgeDocument document,
                                        ConvertedDocumentResult converted,
                                        UploadedFileType uploadedFileType) {
        // 持久化前补齐源文件类型，确保后续补偿流程可稳定识别分支。
        Map<String, Object> extension = new HashMap<>();
        if (converted != null && converted.getExtension() != null) {
            extension.putAll(converted.getExtension());
        }
        extension.putIfAbsent(KEY_SOURCE_FILE_TYPE, uploadedFileType.name());
        String extensionJson;
        try {
            extensionJson = JsonUtils.toJson(extension);
        } catch (IOException exception) {
            throw new KnowledgeDocumentException("Serialize document extension failed", exception);
        }
        updateDocumentStatus(document, KnowledgeDocumentStatus.CONVERTED.name(), converted.getConvertedDocUrl(), extensionJson);
        publishConvertedEvent(document.getDocId());
    }

    private KnowledgeDocument requireDocument(Long docId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(docId);
        if (document == null || !Objects.equals(document.getDeleted(), 0)) {
            throw new IllegalArgumentException("document not found: " + docId);
        }
        return document;
    }

    private UploadedFileType resolveUploadedFileTypeForCompensation(KnowledgeDocument document) {
        Map<String, Object> extension = parseExtension(document.getExtension());
        UploadedFileType persistedType =
                UploadedFileType.resolveByPersistedValue(String.valueOf(extension.get(KEY_SOURCE_FILE_TYPE)));
        if (persistedType != null) {
            return persistedType;
        }
        UploadedFileType fromDocUrl = resolveFileTypeSafely(extractFileName(document.getDocUrl()));
        if (fromDocUrl != null) {
            return fromDocUrl;
        }
        UploadedFileType fromTitle = resolveFileTypeSafely(document.getDocTitle());
        if (fromTitle != null) {
            return fromTitle;
        }
        return UploadedFileType.PDF;
    }

    private UploadedFileType resolveFileTypeSafely(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        try {
            return UploadedFileType.resolveByFilename(fileName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String extractFileName(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        String candidate = fileUrl.trim();
        int queryPos = candidate.indexOf('?');
        if (queryPos >= 0) {
            candidate = candidate.substring(0, queryPos);
        }
        int fragmentPos = candidate.indexOf('#');
        if (fragmentPos >= 0) {
            candidate = candidate.substring(0, fragmentPos);
        }
        int slashPos = candidate.lastIndexOf('/');
        if (slashPos >= 0 && slashPos < candidate.length() - 1) {
            candidate = candidate.substring(slashPos + 1);
        }
        return candidate;
    }

    private void publishConvertedEvent(Long docId) {
        if (docId == null || docId <= 0) {
            return;
        }
        applicationEventPublisher.publishEvent(new KnowledgeDocumentConvertedEvent(docId));
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

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "'").replace("\r", " ").replace("\n", " ");
    }
}
