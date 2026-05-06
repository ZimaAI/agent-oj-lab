package com.oj.agent.core.rag.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.oj.agent.core.rag.enums.KnowledgeDocumentStatus;
import com.oj.agent.core.rag.enums.KnowledgeSegmentStatus;
import com.oj.agent.core.rag.event.KnowledgeDocumentChunkedEvent;
import com.oj.agent.core.rag.event.KnowledgeDocumentConvertedEvent;
import com.oj.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentMapper;
import com.oj.agent.core.rag.model.entity.KnowledgeDocument;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.rag.service.KnowledgeDocumentAsyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Component
public class KnowledgeDocumentCompensationTask {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentCompensationTask.class);
    private static final int MAX_BATCH_SIZE = 200;
    private static final int VECTOR_SCAN_FACTOR = 3;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeSegmentMapper knowledgeSegmentMapper;
    private final KnowledgeDocumentAsyncService knowledgeDocumentAsyncService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final int batchSize;

    public KnowledgeDocumentCompensationTask(KnowledgeDocumentMapper knowledgeDocumentMapper,
                                             KnowledgeSegmentMapper knowledgeSegmentMapper,
                                             KnowledgeDocumentAsyncService knowledgeDocumentAsyncService,
                                             ApplicationEventPublisher applicationEventPublisher,
                                             @Value("${app.rag.compensation.batch-size:20}") int batchSize) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeSegmentMapper = knowledgeSegmentMapper;
        this.knowledgeDocumentAsyncService = knowledgeDocumentAsyncService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.batchSize = normalizeBatchSize(batchSize);
    }

    @Scheduled(
            initialDelayString = "${app.rag.compensation.initial-delay-millis:60000}",
            fixedDelayString = "${app.rag.compensation.fixed-delay-millis:60000}"
    )
    public void compensate() {
        // 统一调度三类兜底流程，任一分支异常都不影响后续分支执行。
        compensateConvertStage();
        compensateChunkStage();
        compensateVectorStage();
    }

    private void compensateConvertStage() {
        // 查询可能未完成 MinerU 解析的文档。
        List<KnowledgeDocument> candidates = knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getDeleted, 0)
                        .in(
                                KnowledgeDocument::getStatus,
                                KnowledgeDocumentStatus.UPLOADED.name(),
                                KnowledgeDocumentStatus.CONVERTING.name(),
                                KnowledgeDocumentStatus.FAILED.name()
                        )
                        .orderByAsc(KnowledgeDocument::getDocId)
                        .last("limit " + batchSize)
        );

        for (KnowledgeDocument document : candidates) {
            // 仅对解析阶段失败或卡住的文档重跑解析。
            if (!shouldRetryConvert(document)) {
                continue;
            }
            try {
                Map<String, Object> traceMeta = new HashMap<>();
                traceMeta.put("source", "compensationTask");
                traceMeta.put("stage", "convert");
                knowledgeDocumentAsyncService.processUploadedDocumentCompensation(document.getDocId(), traceMeta);
            } catch (RuntimeException exception) {
                log.warn("knowledge convert compensate dispatch failed, docId={}", document.getDocId(), exception);
            }
        }
    }

    private void compensateChunkStage() {
        // 查询需要重跑分段事件的文档。
        List<KnowledgeDocument> candidates = knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getDeleted, 0)
                        .in(
                                KnowledgeDocument::getStatus,
                                KnowledgeDocumentStatus.CONVERTED.name(),
                                KnowledgeDocumentStatus.FAILED.name()
                        )
                        .orderByAsc(KnowledgeDocument::getDocId)
                        .last("limit " + batchSize)
        );

        for (KnowledgeDocument document : candidates) {
            // 仅对已产出 markdown 的文档重发分段事件。
            if (!shouldRetryChunk(document)) {
                continue;
            }
            applicationEventPublisher.publishEvent(new KnowledgeDocumentConvertedEvent(document.getDocId()));
        }
    }

    private void compensateVectorStage() {
        // 查询仍处于 STORED 状态且未跳过 embedding 的知识片段。
        int segmentScanSize = Math.min(MAX_BATCH_SIZE * VECTOR_SCAN_FACTOR, batchSize * VECTOR_SCAN_FACTOR);
        List<KnowledgeSegment> segments = knowledgeSegmentMapper.selectList(
                new QueryWrapper<KnowledgeSegment>()
                        .select("document_id")
                        .eq("deleted", 0)
                        .eq("status", KnowledgeSegmentStatus.STORED.name())
                        .and(wrapper -> wrapper
                                .isNull("skip_embedding")
                                .or()
                                .eq("skip_embedding", 0))
                        .orderByAsc("document_id")
                        .last("limit " + segmentScanSize)
        );

        // 去重后按批次发布向量化补偿事件。
        Set<Long> docIds = new LinkedHashSet<>();
        for (KnowledgeSegment segment : segments) {
            if (segment == null || segment.getDocumentId() == null || segment.getDocumentId() <= 0) {
                continue;
            }
            docIds.add(segment.getDocumentId());
            if (docIds.size() >= batchSize) {
                break;
            }
        }
        for (Long docId : docIds) {
            applicationEventPublisher.publishEvent(new KnowledgeDocumentChunkedEvent(docId));
        }
    }

    private boolean shouldRetryConvert(KnowledgeDocument document) {
        // 兜底条件：UPLOADED/CONVERTING，或 FAILED 且尚未产生 convertedDocUrl。
        if (document == null || document.getDocId() == null || document.getDocId() <= 0) {
            return false;
        }
        if (!StringUtils.hasText(document.getDocUrl())) {
            return false;
        }
        if (KnowledgeDocumentStatus.UPLOADED.name().equalsIgnoreCase(document.getStatus())
                || KnowledgeDocumentStatus.CONVERTING.name().equalsIgnoreCase(document.getStatus())) {
            return true;
        }
        return KnowledgeDocumentStatus.FAILED.name().equalsIgnoreCase(document.getStatus())
                && !StringUtils.hasText(document.getConvertedDocUrl());
    }

    private boolean shouldRetryChunk(KnowledgeDocument document) {
        // 兜底条件：CONVERTED，或 FAILED 且已产出 convertedDocUrl。
        if (document == null || document.getDocId() == null || document.getDocId() <= 0) {
            return false;
        }
        if (!StringUtils.hasText(document.getConvertedDocUrl())) {
            return false;
        }
        return KnowledgeDocumentStatus.CONVERTED.name().equalsIgnoreCase(document.getStatus())
                || KnowledgeDocumentStatus.FAILED.name().equalsIgnoreCase(document.getStatus());
    }

    private int normalizeBatchSize(int batchSize) {
        // 对批处理大小做边界保护，避免错误配置导致全表扫描。
        if (batchSize <= 0) {
            return 20;
        }
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }
}
