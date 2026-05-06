package com.oj.agent.core.rag.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.common.constant.MetadataKeyConstant;
import com.oj.agent.core.rag.enums.KnowledgeDocumentStatus;
import com.oj.agent.core.rag.enums.KnowledgeSegmentStatus;
import com.oj.agent.core.rag.model.dto.DocumentSplitParam;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.rag.config.KnowledgeDocumentSplitProperties;
import com.oj.agent.core.rag.event.KnowledgeDocumentChunkedEvent;
import com.oj.agent.core.rag.event.KnowledgeDocumentConvertedEvent;
import com.oj.agent.core.rag.exception.KnowledgeDocumentException;
import com.oj.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentMapper;
import com.oj.agent.core.rag.model.entity.KnowledgeDocument;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.rag.splitter.DocumentSplitterFactory;
import com.oj.agent.redis.lock.annotation.DistributeLock;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 文档转换完成后执行分段并落库。
 */
@Component
public class KnowledgeDocumentConvertedListener {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentConvertedListener.class);
    private static final String KEY_MD_URLS = "mdUrls";
    private static final String KEY_FAILED_REASON = "failedReason";
    private static final String DOC_PIPELINE_SCENE = "knowledge:document:pipeline";
    private static final Pattern MARKDOWN_TITLE_LINE_PATTERN = Pattern.compile("^#{1,6}(\\s+.*)?$");

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeSegmentMapper knowledgeSegmentMapper;
    private final HttpClient knowledgeHttpClient;
    private final KnowledgeDocumentSplitProperties splitProperties;
    private final ApplicationEventPublisher applicationEventPublisher;

    public KnowledgeDocumentConvertedListener(KnowledgeDocumentMapper knowledgeDocumentMapper,
                                              KnowledgeSegmentMapper knowledgeSegmentMapper,
                                              @Qualifier("knowledgeHttpClient") HttpClient knowledgeHttpClient,
                                              KnowledgeDocumentSplitProperties splitProperties,
                                              ApplicationEventPublisher applicationEventPublisher) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeSegmentMapper = knowledgeSegmentMapper;
        this.knowledgeHttpClient = knowledgeHttpClient;
        this.splitProperties = splitProperties;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Async("knowledgeDocumentAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @DistributeLock(scene = DOC_PIPELINE_SCENE, keyExpression = "#p0.docId")
    public void onDocumentConverted(KnowledgeDocumentConvertedEvent event) {
        // 非法事件参数直接忽略，避免无效数据库访问。
        if (event == null || event.docId() == null || event.docId() <= 0) {
            return;
        }
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(event.docId());
        // 文档不存在或已删除时直接返回。
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            return;
        }
        // 状态已处理完成时直接返回，保证监听逻辑幂等。
        if (KnowledgeDocumentStatus.CHUNKED.name().equalsIgnoreCase(document.getStatus())
                || KnowledgeDocumentStatus.VECTOR_STORED.name().equalsIgnoreCase(document.getStatus())
                || KnowledgeDocumentStatus.STORED.name().equalsIgnoreCase(document.getStatus())) {
            return;
        }
        try {
            // 读取 markdown 并执行分段。
            List<KnowledgeSegment> segments = buildSegments(document);
            if (segments.isEmpty()) {
                throw new KnowledgeDocumentException("No segments produced from markdown document");
            }
            // 先清理历史分段，再写入最新分段结果。
            clearExistingSegments(document.getDocId());
            persistSegments(segments);
            // 分段成功后推进文档状态为 CHUNKED。
            document.setStatus(KnowledgeDocumentStatus.CHUNKED.name());
            knowledgeDocumentMapper.updateById(document);
            publishChunkedEvent(document.getDocId());
        } catch (RuntimeException exception) {
            // 分段失败时写入失败状态，便于外部排查。
            markFailed(document, exception);
            log.error("knowledge document chunk failed, docId={}", document.getDocId(), exception);
            throw exception;
        }
    }

    private void publishChunkedEvent(Long docId) {
        if (docId == null || docId <= 0) {
            return;
        }
        applicationEventPublisher.publishEvent(new KnowledgeDocumentChunkedEvent(docId));
    }

    private List<KnowledgeSegment> buildSegments(KnowledgeDocument document) {
        // 解析 markdown 地址并创建分段器。
        List<String> markdownUrls = resolveMarkdownUrls(document);
        DocumentSplitter splitter = createSplitter();
        List<KnowledgeSegment> segments = new ArrayList<>();
        int chunkOrder = 0;

        for (String markdownUrl : markdownUrls) {
            // 下载 markdown 文本并附带基础元数据。
            String markdown = downloadMarkdown(markdownUrl);
            if (!StringUtils.hasText(markdown)) {
                continue;
            }
            Map<String, Object> baseMetadata = buildBaseMetadata(document, markdownUrl);
            List<TextSegment> textSegments = splitter.split(Document.from(markdown, Metadata.from(baseMetadata)));

            for (TextSegment textSegment : textSegments) {
                if (textSegment == null || !StringUtils.hasText(textSegment.text()) || isTitleOnlySegment(textSegment.text())) {
                    continue;
                }
                chunkOrder++;
                segments.add(toSegmentEntity(document.getDocId(), chunkOrder, textSegment));
            }
        }
        return segments;
    }

    private boolean isTitleOnlySegment(String text) {
        // 仅当分块中所有非空行都是 markdown 标题时，判定为纯标题块并跳过。
        String[] lines = text.replace("\r", "").split("\n");
        boolean hasNonEmptyLine = false;
        for (String line : lines) {
            String trimmedLine = line == null ? "" : line.trim();
            if (!StringUtils.hasText(trimmedLine)) {
                continue;
            }
            hasNonEmptyLine = true;
            if (!MARKDOWN_TITLE_LINE_PATTERN.matcher(trimmedLine).matches()) {
                return false;
            }
        }
        return hasNonEmptyLine;
    }

    private List<String> resolveMarkdownUrls(KnowledgeDocument document) {
        // 从 extension 提取 mdUrls，同时回退 convertedDocUrl。
        Set<String> urls = new LinkedHashSet<>();
        if (StringUtils.hasText(document.getExtension())) {
            try {
                Map<?, ?> extensionMap = JsonUtils.getObjectMapper().readValue(document.getExtension(), Map.class);
                Object mdUrlsObj = extensionMap.get(KEY_MD_URLS);
                if (mdUrlsObj instanceof List<?> mdList) {
                    for (Object item : mdList) {
                        if (item instanceof String value && StringUtils.hasText(value)) {
                            urls.add(value.trim());
                        }
                    }
                }
            } catch (IOException ignored) {
                // extension 解析失败时交给 convertedDocUrl 兜底。
            }
        }
        if (StringUtils.hasText(document.getConvertedDocUrl())) {
            urls.add(document.getConvertedDocUrl().trim());
        }
        if (urls.isEmpty()) {
            throw new KnowledgeDocumentException("No markdown url found for document: " + document.getDocId());
        }
        return new ArrayList<>(urls);
    }

    private DocumentSplitter createSplitter() {
        // 通过统一配置构建分段参数并获取分段器。
        DocumentSplitParam splitParam = splitProperties.toSplitParam();
        DocumentSplitter splitter = DocumentSplitterFactory.getInstance(splitParam);
        if (splitter == null) {
            throw new KnowledgeDocumentException("No document splitter available");
        }
        return splitter;
    }

    private String downloadMarkdown(String markdownUrl) {
        // 通过统一 HTTP 客户端读取 markdown 内容。
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(markdownUrl))
                    .GET()
                    .build();
            HttpResponse<String> response = knowledgeHttpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new KnowledgeDocumentException("Download markdown failed, status=" + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new KnowledgeDocumentException("Download markdown failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KnowledgeDocumentException("Download markdown interrupted", exception);
        }
    }

    private Map<String, Object> buildBaseMetadata(KnowledgeDocument document, String markdownUrl) {
        // 注入基础元数据，便于后续检索和诊断。
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(MetadataKeyConstant.DOC_ID, document.getDocId());
        metadata.put(MetadataKeyConstant.FILE_NAME, document.getDocTitle());
        metadata.put(MetadataKeyConstant.URL, markdownUrl);
        if (StringUtils.hasText(document.getAccessibleBy())) {
            metadata.put(MetadataKeyConstant.ACCESSIBLE_BY, document.getAccessibleBy());
        }
        return metadata;
    }

    private KnowledgeSegment toSegmentEntity(Long docId, int chunkOrder, TextSegment textSegment) {
        // 构建知识分段实体并回填默认元数据字段。
        Map<String, Object> metadata = new HashMap<>();
        if (textSegment.metadata() != null) {
            metadata.putAll(textSegment.metadata().toMap());
        }
        String chunkId = readChunkId(metadata);
        metadata.putIfAbsent(MetadataKeyConstant.CHUNK_ID, chunkId);

        KnowledgeSegment segment = new KnowledgeSegment();
        segment.setDocumentId(docId);
        segment.setChunkOrder(chunkOrder);
        segment.setText(textSegment.text());
        segment.setChunkId(chunkId);
        segment.setMetadata(toJson(metadata));
        segment.setEmbeddingId(null);
        segment.setStatus(KnowledgeSegmentStatus.STORED.name());
        segment.setSkipEmbedding(parseSkipEmbedding(metadata.get(MetadataKeyConstant.SKIP_EMBEDDING)));
        segment.setLockVersion(0);
        segment.setDeleted(0);
        return segment;
    }

    private String readChunkId(Map<String, Object> metadata) {
        // 优先复用分段器元数据中的 chunkId，缺失时生成新值。
        Object chunkIdObj = metadata.get(MetadataKeyConstant.CHUNK_ID);
        if (chunkIdObj instanceof String value && StringUtils.hasText(value)) {
            return value.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Integer parseSkipEmbedding(Object skipEmbeddingValue) {
        // 兼容数字、布尔和字符串三种配置来源。
        if (skipEmbeddingValue instanceof Number number) {
            return number.intValue() > 0 ? 1 : 0;
        }
        if (skipEmbeddingValue instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (skipEmbeddingValue instanceof String value && StringUtils.hasText(value)) {
            if ("true".equalsIgnoreCase(value.trim())) {
                return 1;
            }
            if ("false".equalsIgnoreCase(value.trim())) {
                return 0;
            }
            try {
                return Integer.parseInt(value.trim()) > 0 ? 1 : 0;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String toJson(Map<String, Object> metadata) {
        // 统一元数据序列化，失败时直接抛业务异常。
        try {
            return JsonUtils.toJson(metadata);
        } catch (IOException exception) {
            throw new KnowledgeDocumentException("Serialize segment metadata failed", exception);
        }
    }

    private void clearExistingSegments(Long docId) {
        // 清理历史分段，防止重复事件造成脏数据。
        LambdaQueryWrapper<KnowledgeSegment> queryWrapper = new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(KnowledgeSegment::getDocumentId, docId);
        knowledgeSegmentMapper.delete(queryWrapper);
    }

    private void persistSegments(List<KnowledgeSegment> segments) {
        // 顺序写入分段，保证 chunkOrder 与存储顺序一致。
        for (KnowledgeSegment segment : segments) {
            knowledgeSegmentMapper.insert(segment);
        }
    }

    private void markFailed(KnowledgeDocument document, RuntimeException exception) {
        // 写回失败状态并保留失败原因。
        document.setStatus(KnowledgeDocumentStatus.FAILED.name());
        document.setExtension(mergeFailedReason(document.getExtension(), exception.getMessage()));
        knowledgeDocumentMapper.updateById(document);
    }

    private String mergeFailedReason(String extensionJson, String reason) {
        // 合并失败原因到 extension，避免覆盖已有字段。
        Map<String, Object> extensionMap = new HashMap<>();
        if (StringUtils.hasText(extensionJson)) {
            try {
                Map<?, ?> parsed = JsonUtils.getObjectMapper().readValue(extensionJson, Map.class);
                for (Map.Entry<?, ?> entry : parsed.entrySet()) {
                    if (entry.getKey() != null) {
                        extensionMap.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
            } catch (IOException ignored) {
                // 旧扩展字段解析失败时继续写入失败原因。
            }
        }
        extensionMap.put(KEY_FAILED_REASON, sanitize(reason));
        return toJson(extensionMap);
    }

    private String sanitize(String text) {
        // 规避引号和换行导致的展示问题。
        if (text == null) {
            return "";
        }
        return text.replace("\"", "'").replace("\r", " ").replace("\n", " ");
    }
}
