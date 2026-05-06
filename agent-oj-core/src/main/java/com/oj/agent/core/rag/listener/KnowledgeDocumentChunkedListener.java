package com.oj.agent.core.rag.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.core.rag.enums.KnowledgeBaseType;
import com.oj.agent.core.rag.enums.KnowledgeDocumentStatus;
import com.oj.agent.core.rag.enums.KnowledgeSegmentStatus;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.rag.model.entity.KnowledgeDocument;
import com.oj.agent.core.rag.model.entity.KnowledgeSegment;
import com.oj.agent.core.aimodel.prompt.PromptHelper;
import com.oj.agent.core.rag.event.KnowledgeDocumentChunkedEvent;
import com.oj.agent.core.rag.exception.KnowledgeDocumentException;
import com.oj.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.oj.agent.core.rag.mapper.KnowledgeSegmentMapper;
import com.oj.agent.core.rag.model.VectorDocument;
import com.oj.agent.core.rag.store.AgentVectorStore;
import com.oj.agent.redis.lock.annotation.DistributeLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class KnowledgeDocumentChunkedListener {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentChunkedListener.class);
    private static final String KEY_FAILED_REASON = "failedReason";
    private static final String DOC_PIPELINE_SCENE = "knowledge:document:pipeline";
    private static final String CODE_DESCRIPTION_PREFIX = "代码描述：";
    private static final String CODE_DESCRIPTION_SEPARATOR = System.lineSeparator() + System.lineSeparator();
    private static final List<Pattern> CODE_FEATURE_PATTERNS = List.of(
            Pattern.compile("(?s)```.+?```|~~~.+?~~~"),
            Pattern.compile("(?m)^\\s*[{}]\\s*$"),
            Pattern.compile("(?m);\\s*$"),
            Pattern.compile("(?i)\\b(class|interface|enum|record|struct)\\b"),
            Pattern.compile("(?i)\\b(public|private|protected|static|final|const|let|var|def|function|func|void)\\b"),
            Pattern.compile("(?i)\\b(if|else|for|while|switch|case|try|catch|return|break|continue)\\b"),
            Pattern.compile("(?i)\\b(import|package|include|using|namespace|require|from)\\b"),
            Pattern.compile("==|!=|<=|>=|=>|->|::"),
            Pattern.compile("(?i)\\b(select\\b.+\\bfrom\\b|insert\\b.+\\binto\\b|update\\b.+\\bset\\b|delete\\b.+\\bfrom\\b|create\\s+table)"),
            Pattern.compile("(?m)^( {4,}|\\t+).+")
    );

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeSegmentMapper knowledgeSegmentMapper;
    private final AgentVectorStore knowledgeSegmentAgentVectorStore;
    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;
    private final int dimension;
    private final int codeDescriptionThreshold;
    private final int codeDescriptionMaxChars;

    public KnowledgeDocumentChunkedListener(KnowledgeDocumentMapper knowledgeDocumentMapper,
                                            KnowledgeSegmentMapper knowledgeSegmentMapper,
                                            @Qualifier("knowledgeSegmentAgentVectorStore") AgentVectorStore knowledgeSegmentAgentVectorStore,
                                            AiModelRegistry aiModelRegistry,
                                            @Value("${app.vector.embedding.dimension:1024}") int dimension,
                                            @Value("${app.rag.code-segment.description-threshold:5}") int codeDescriptionThreshold,
                                            @Value("${app.rag.code-segment.description-max-chars:200}") int codeDescriptionMaxChars) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeSegmentMapper = knowledgeSegmentMapper;
        this.knowledgeSegmentAgentVectorStore = knowledgeSegmentAgentVectorStore;
        this.embeddingModel = aiModelRegistry.getDefaultEmbeddingModel();
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
        this.dimension = dimension;
        this.codeDescriptionThreshold = Math.max(codeDescriptionThreshold, 1);
        this.codeDescriptionMaxChars = Math.max(codeDescriptionMaxChars, 1);
    }

    @Async("knowledgeDocumentAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @DistributeLock(scene = DOC_PIPELINE_SCENE, keyExpression = "#p0.docId")
    public void onDocumentChunked(KnowledgeDocumentChunkedEvent event) {
        if (event == null || event.docId() == null || event.docId() <= 0) {
            return;
        }
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(event.docId());
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            return;
        }
        if (KnowledgeDocumentStatus.VECTOR_STORED.name().equalsIgnoreCase(document.getStatus())
                || KnowledgeDocumentStatus.STORED.name().equalsIgnoreCase(document.getStatus())) {
            return;
        }
        if (!needVectorize(document)) {
            document.setStatus(KnowledgeDocumentStatus.STORED.name());
            knowledgeDocumentMapper.updateById(document);
            return;
        }
        try {
            List<KnowledgeSegment> segments = listSegmentsNeedEmbedding(document.getDocId());
            for (KnowledgeSegment segment : segments) {
                upsertSegmentVector(document, segment);
            }
            document.setStatus(KnowledgeDocumentStatus.VECTOR_STORED.name());
            knowledgeDocumentMapper.updateById(document);
        } catch (RuntimeException exception) {
            markFailed(document, exception);
            log.error("knowledge document vectorize failed, docId={}", document.getDocId(), exception);
            throw exception;
        }
    }

    private boolean needVectorize(KnowledgeDocument document) {
        return KnowledgeBaseType.DOCUMENT_SEARCH.name().equalsIgnoreCase(document.getKnowledgeBaseType());
    }

    private List<KnowledgeSegment> listSegmentsNeedEmbedding(Long docId) {
        LambdaQueryWrapper<KnowledgeSegment> queryWrapper = new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(KnowledgeSegment::getDocumentId, docId)
                .eq(KnowledgeSegment::getDeleted, 0)
                .eq(KnowledgeSegment::getStatus, KnowledgeSegmentStatus.STORED.name())
                .and(wrapper -> wrapper
                        .isNull(KnowledgeSegment::getSkipEmbedding)
                        .or()
                        .eq(KnowledgeSegment::getSkipEmbedding, 0))
                .orderByAsc(KnowledgeSegment::getChunkOrder);
        return knowledgeSegmentMapper.selectList(queryWrapper);
    }

    private void upsertSegmentVector(KnowledgeDocument document, KnowledgeSegment segment) {
        if (segment == null || segment.getId() == null || !StringUtils.hasText(segment.getText())) {
            throw new KnowledgeDocumentException("Knowledge segment is invalid for embedding");
        }
        // 命中代码特征阈值时，先为代码补充自然语言描述。
        String embeddingText = buildEmbeddingText(segment.getText());
        if (!embeddingText.equals(segment.getText())) {
            segment.setText(embeddingText);
        }
        float[] embedding = generateEmbedding(segment.getText().trim());
        validateDimension(embedding);
        String embeddingId = buildEmbeddingId(segment);
        VectorDocument vectorDocument = toVectorDocument(document, segment);
        knowledgeSegmentAgentVectorStore.upsert(vectorDocument, embedding);
        segment.setEmbeddingId(embeddingId);
        segment.setStatus(KnowledgeSegmentStatus.VECTOR_STORED.name());
        knowledgeSegmentMapper.updateById(segment);
    }

    private String buildEmbeddingText(String segmentText) {
        String normalizedText = segmentText == null ? "" : segmentText.trim();
        if (!StringUtils.hasText(normalizedText)) {
            throw new KnowledgeDocumentException("Knowledge segment text is blank");
        }
        if (normalizedText.startsWith(CODE_DESCRIPTION_PREFIX)) {
            return normalizedText;
        }
        if (!shouldDescribeCodeSegment(normalizedText)) {
            return normalizedText;
        }

        // 通过提示词生成一段短描述，增强代码片段的检索语义。
        String description = generateCodeDescription(normalizedText);
        return CODE_DESCRIPTION_PREFIX + description + CODE_DESCRIPTION_SEPARATOR + normalizedText;
    }

    private boolean shouldDescribeCodeSegment(String text) {
        return countCodeFeatureMatches(text) >= codeDescriptionThreshold;
    }

    private int countCodeFeatureMatches(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int matchedCount = 0;
        for (Pattern pattern : CODE_FEATURE_PATTERNS) {
            if (pattern.matcher(text).find()) {
                matchedCount++;
            }
        }
        return matchedCount;
    }

    private String generateCodeDescription(String segmentText) {
        if (chatClient == null) {
            throw new KnowledgeDocumentException("ChatClient is not initialized");
        }
        String prompt = PromptHelper.buildSegmentCodeDescriptionPrompt(segmentText, codeDescriptionMaxChars);
        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        if (!StringUtils.hasText(content)) {
            throw new KnowledgeDocumentException("Generated code description is blank");
        }
        return normalizeCodeDescription(content);
    }

    private String normalizeCodeDescription(String content) {
        // 统一压缩空白字符，并截断到配置上限以内。
        String normalized = content.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.startsWith(CODE_DESCRIPTION_PREFIX)) {
            normalized = normalized.substring(CODE_DESCRIPTION_PREFIX.length()).trim();
        }
        normalized = normalized.replaceAll("\\s+", " ");
        if (!StringUtils.hasText(normalized)) {
            throw new KnowledgeDocumentException("Generated code description is blank");
        }
        if (normalized.length() <= codeDescriptionMaxChars) {
            return normalized;
        }
        return normalized.substring(0, codeDescriptionMaxChars).trim();
    }

    private float[] generateEmbedding(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new KnowledgeDocumentException("Embedding response is empty");
        }
        if (response.getResults().get(0) == null || response.getResults().get(0).getOutput() == null) {
            throw new KnowledgeDocumentException("Embedding output is empty");
        }
        return response.getResults().get(0).getOutput();
    }

    private void validateDimension(float[] embedding) {
        if (embedding == null || embedding.length != dimension) {
            int actualDimension = embedding == null ? 0 : embedding.length;
            throw new KnowledgeDocumentException(
                    "Embedding dimension mismatch, expected=" + dimension + ", actual=" + actualDimension
            );
        }
    }

    private String buildEmbeddingId(KnowledgeSegment segment) {
        if (StringUtils.hasText(segment.getChunkId())) {
            return segment.getChunkId().trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private VectorDocument toVectorDocument(KnowledgeDocument document, KnowledgeSegment segment) {
        return VectorDocument.builder()
                .id(String.valueOf(segment.getId()))
                .segmentId(segment.getId())
                .documentId(segment.getDocumentId())
                .chunkId(segment.getChunkId())
                .title(document.getDocTitle())
                .content(segment.getText())
                .deleted(false)
                .build();
    }

    private void markFailed(KnowledgeDocument document, RuntimeException exception) {
        document.setStatus(KnowledgeDocumentStatus.FAILED.name());
        document.setExtension(mergeFailedReason(document.getExtension(), exception.getMessage()));
        knowledgeDocumentMapper.updateById(document);
    }

    private String mergeFailedReason(String extensionJson, String reason) {
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
            }
        }
        extensionMap.put(KEY_FAILED_REASON, sanitize(reason));
        return toJson(extensionMap);
    }

    private String toJson(Map<String, Object> extensionMap) {
        try {
            return JsonUtils.toJson(extensionMap);
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
