package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.TextType;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.workflow.converter.WorkflowConverter;
import com.oj.agent.core.workflow.model.dto.AnswerRAGOutputDTO;
import com.oj.agent.core.workflow.model.dto.AnswerRAGSegmentDTO;
import com.oj.agent.core.workflow.model.dto.AnswerRewriteOutputDTO;
import com.oj.agent.core.rag.config.RAGProperties;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.rag.model.query.KnowledgeSegmentListQuery;
import com.oj.agent.core.rag.model.result.KnowledgeSegmentResult;
import com.oj.agent.core.rag.service.KnowledgeSegmentService;
import com.oj.agent.core.rag.model.VectorSearchHit;
import com.oj.agent.core.rag.model.VectorSearchRequest;
import com.oj.agent.core.rag.model.VectorSearchResult;
import com.oj.agent.core.rag.store.AgentVectorStore;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.core.workflow.trace.WorkflowTraceSupport;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.result.TraceItemTypeResult;
import com.oj.agent.core.workflow.util.ChatResponseUtil;
import com.oj.agent.core.workflow.util.FluxUtil;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AnswerRAGNode implements NodeAction {

    private static final int MIN_NORMAL_HITS_PER_QUERY = 2;
    private static final int DEFAULT_RRF_K = 60;
    private static final double SIMILARITY_WEIGHT = 0.7D;
    private static final double RRF_WEIGHT = 0.3D;

    private final EmbeddingModel embeddingModel;

    private final AgentVectorStore knowledgeSegmentAgentVectorStore;

    private final KnowledgeSegmentService knowledgeSegmentService;

    private final RAGProperties ragProperties;

    private final WorkflowTraceRecorder workflowTraceRecorder;

    private final WorkflowTraceSupport workflowTraceSupport;

    public AnswerRAGNode(AiModelRegistry aiModelRegistry,
                         @Qualifier("knowledgeSegmentAgentVectorStore") AgentVectorStore knowledgeSegmentAgentVectorStore,
                         KnowledgeSegmentService knowledgeSegmentService,
                         RAGProperties ragProperties,
                         WorkflowTraceRecorder workflowTraceRecorder,
                         WorkflowTraceSupport workflowTraceSupport) {
        this.embeddingModel = aiModelRegistry == null ? null : aiModelRegistry.getDefaultEmbeddingModel();
        this.knowledgeSegmentAgentVectorStore = knowledgeSegmentAgentVectorStore;
        this.knowledgeSegmentService = knowledgeSegmentService;
        this.ragProperties = ragProperties;
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        return Map.of(Constant.ANSWER_RAG_NODE_OUTPUT, createGenerator(state));
    }

    // 构建答疑文档检索的流式生成器。
    Flux<GraphResponse<StreamingOutput>> createGenerator(OverAllState state) {
        validateDependencies();

        // 读取改写后的子问题，并启动节点级 trace。
        AnswerRewriteOutputDTO rewriteOutput = StateUtil.getObjectValue(
                state,
                Constant.ANSWER_REWRITE_NODE_OUTPUT,
                AnswerRewriteOutputDTO.class,
                (AnswerRewriteOutputDTO) null
        );
        AlgorithmQuestionResult currentQuestion = StateUtil.getOptionalObjectValueOrNull(
                state,
                Constant.CURRENT_ALGORITHM_QUESTION,
                AlgorithmQuestionResult.class
        );
        Long traceItemId = startTraceItem(state, rewriteOutput, currentQuestion);

        AtomicReference<AnswerRAGOutputDTO> outputRef = new AtomicReference<>(createEmptyOutput());
        Flux<ChatResponse> sourceFlux = Mono.fromRunnable(() -> outputRef.set(executeAnswerRag(rewriteOutput)))
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(Flux.empty());

        return FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                sourceFlux,
                Flux.just(
                        ChatResponseUtil.createResponse("Retrieving answer reference documents..."),
                        ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())
                ),
                Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())),
                result -> Map.of(Constant.ANSWER_RAG_NODE_OUTPUT, outputRef.get()),
                workflowTraceRecorder,
                traceItemId,
                result -> workflowTraceSupport == null
                        ? null
                        : workflowTraceSupport.toNodeOutputPayload(outputRef.get())
        );
    }

    // 执行答疑检索，并在命中子分段时补充父分段上下文。
    private AnswerRAGOutputDTO executeAnswerRag(AnswerRewriteOutputDTO rewriteOutput) {
        try {
            List<ScoredSegment> scoredSegments = collectScoredSegments(rewriteOutput);
            if (scoredSegments.isEmpty()) {
                return createEmptyOutput();
            }
            List<AnswerRAGSegmentDTO> resolvedSegments = resolveSegments(scoredSegments);
            return new AnswerRAGOutputDTO(resolvedSegments);
        } catch (Exception exception) {
            log.error("Answer RAG retrieval failed", exception);
            return createEmptyOutput();
        }
    }

    // 合并多个改写子问题的命中结果，并保留每个分段的最高分。
    private List<ScoredSegment> collectScoredSegments(AnswerRewriteOutputDTO rewriteOutput) {
        if (rewriteOutput == null || rewriteOutput.getRewrittenQueries() == null || rewriteOutput.getRewrittenQueries().isEmpty()) {
            return List.of();
        }

        Map<Long, Double> bestScores = new HashMap<>();
        Map<Long, Double> rawSimilarities = new HashMap<>();
        Map<Long, Double> rrfScores = new HashMap<>();
        for (String rewrittenQuery : rewriteOutput.getRewrittenQueries()) {
            if (rewrittenQuery == null || rewrittenQuery.trim().isEmpty()) {
                continue;
            }
            float[] embedding = generateEmbedding(rewrittenQuery.trim());
            if (embedding == null || embedding.length == 0) {
                continue;
            }
            List<VectorSearchHit> hits = searchWithThresholdFallback(embedding);
            for (int rank = 0; rank < hits.size(); rank++) {
                VectorSearchHit hit = hits.get(rank);
                if (hit == null || hit.getQuestionId() == null) {
                    continue;
                }
                double currentScore = resolveScore(hit);
                bestScores.merge(hit.getQuestionId(), currentScore, Math::max);
                rawSimilarities.merge(hit.getQuestionId(), resolveRawSimilarity(hit), Math::max);
                rrfScores.merge(
                        hit.getQuestionId(),
                        1D / (resolveRrfK() + rank + 1D),
                        Double::sum
                );
            }
        }

        if (bestScores.isEmpty()) {
            return List.of();
        }

        double maxBestScore = bestScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        double maxRrfScore = rrfScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0D);

        // 融合向量分值和RRF排序分值，提升跨改写查询的稳定召回。
        return bestScores.entrySet().stream()
                .map(entry -> {
                    Long segmentId = entry.getKey();
                    double rawSimilarity = rawSimilarities.getOrDefault(segmentId, entry.getValue());
                    double similarityScore = normalizeScore(entry.getValue(), maxBestScore);
                    double rrfScore = normalizeScore(rrfScores.getOrDefault(segmentId, 0D), maxRrfScore);
                    double finalScore = similarityScore * SIMILARITY_WEIGHT + rrfScore * RRF_WEIGHT;
                    return new ScoredSegment(segmentId, rawSimilarity, similarityScore, rrfScore, finalScore);
                })
                .sorted(Comparator.comparingDouble(ScoredSegment::finalScore).reversed())
                .limit(resolveCandidateTopK())
                .toList();
    }

    // 解析最终分段：优先父分段并保持稳定排序。
    private List<AnswerRAGSegmentDTO> resolveSegments(List<ScoredSegment> scoredSegments) {
        List<Long> hitSegmentIds = scoredSegments.stream()
                .map(ScoredSegment::segmentId)
                .filter(Objects::nonNull)
                .toList();
        if (hitSegmentIds.isEmpty()) {
            return List.of();
        }

        List<KnowledgeSegmentResult> hitSegments = listSegmentsByIds(hitSegmentIds);
        if (hitSegments.isEmpty()) {
            return List.of();
        }

        // 建立命中分段索引，并收集父分段 chunkId。
        Map<Long, KnowledgeSegmentResult> hitSegmentMap = hitSegments.stream()
                .collect(Collectors.toMap(KnowledgeSegmentResult::getId, segment -> segment, (left, right) -> left));
        Set<String> parentChunkIds = hitSegments.stream()
                .map(KnowledgeSegmentResult::getParentChunkId)
                .filter(parentChunkId -> parentChunkId != null && !parentChunkId.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 批量查询父分段，供替换子分段使用。
        List<KnowledgeSegmentResult> parentSegments = listSegmentsByChunkIds(parentChunkIds);
        Map<String, KnowledgeSegmentResult> parentSegmentMap = parentSegments.stream()
                .collect(Collectors.toMap(KnowledgeSegmentResult::getChunkId, segment -> segment, (left, right) -> left));

        // 命中子分段时优先替换为父分段，并通过分段 id 去重。
        Map<Long, ScoredResolvedSegment> resolvedMap = new LinkedHashMap<>();
        for (ScoredSegment scoredSegment : scoredSegments) {
            KnowledgeSegmentResult childSegment = hitSegmentMap.get(scoredSegment.segmentId());
            if (childSegment == null) {
                continue;
            }

            // 计算当前命中分段的最终保留对象，命中父分段时直接替代子分段。
            KnowledgeSegmentResult resolvedSegment = childSegment;
            String parentChunkId = childSegment.getParentChunkId();
            if (parentChunkId != null) {
                KnowledgeSegmentResult parentSegment = parentSegmentMap.get(parentChunkId);
                if (parentSegment != null
                        && parentSegment.getId() != null
                        && !Objects.equals(parentSegment.getId(), childSegment.getId())) {
                    resolvedSegment = parentSegment;
                }
            }
            if (resolvedSegment.getId() == null) {
                continue;
            }

            // 合并同一父分段（或同一命中分段）的得分，保留最高分。
            resolvedMap.merge(
                    resolvedSegment.getId(),
                    new ScoredResolvedSegment(
                            resolvedSegment,
                            scoredSegment.rawSimilarity(),
                            scoredSegment.similarityScore(),
                            scoredSegment.rrfScore(),
                            scoredSegment.finalScore()
                    ),
                    this::mergeResolvedSegmentScore
            );
        }

        return resolvedMap.values().stream()
                .sorted(Comparator.comparingDouble(ScoredResolvedSegment::finalScore).reversed()
                        .thenComparing(item -> item.segment().getChunkOrder() == null ? Integer.MAX_VALUE : item.segment().getChunkOrder())
                        .thenComparing(item -> item.segment().getId()))
                .limit(resolveTopK())
                .map(item -> WorkflowConverter.toAnswerRagSegmentDto(
                        item.segment(),
                        item.rawSimilarity(),
                        item.similarityScore(),
                        item.rrfScore(),
                        item.finalScore()
                ))
                .toList();
    }

    // 合并同一保留分段的排序分和原始相似度，排序分保留最优，原始相似度保留最大值。
    private ScoredResolvedSegment mergeResolvedSegmentScore(ScoredResolvedSegment current,
                                                            ScoredResolvedSegment incoming) {
        double maxRawSimilarity = Math.max(current.rawSimilarity(), incoming.rawSimilarity());
        ScoredResolvedSegment selected = incoming.finalScore() > current.finalScore() ? incoming : current;
        return new ScoredResolvedSegment(
                selected.segment(),
                maxRawSimilarity,
                selected.similarityScore(),
                selected.rrfScore(),
                selected.finalScore()
        );
    }

    // 按分段 id 查询分段详情。
    private List<KnowledgeSegmentResult> listSegmentsByIds(List<Long> segmentIds) {
        if (segmentIds == null || segmentIds.isEmpty()) {
            return List.of();
        }
        KnowledgeSegmentListQuery query = new KnowledgeSegmentListQuery();
        query.setSegmentIds(segmentIds);
        return knowledgeSegmentService.listSegments(query);
    }

    // 按父分段 chunkId 查询父分段详情。
    private List<KnowledgeSegmentResult> listSegmentsByChunkIds(Set<String> parentChunkIds) {
        if (parentChunkIds == null || parentChunkIds.isEmpty()) {
            return List.of();
        }
        KnowledgeSegmentListQuery query = new KnowledgeSegmentListQuery();
        query.setChunkIds(List.copyOf(parentChunkIds));
        return knowledgeSegmentService.listSegments(query);
    }

    // 执行知识分段向量检索。
    private List<VectorSearchHit> searchSimilarSegments(VectorSearchRequest request) {
        VectorSearchResult searchResult = knowledgeSegmentAgentVectorStore.search(request);
        if (searchResult == null || searchResult.getHits() == null) {
            return List.of();
        }
        return searchResult.getHits();
    }

    // 先按主阈值检索，命中不足时降阈值补召回。
    private List<VectorSearchHit> searchWithThresholdFallback(float[] embedding) {
        List<VectorSearchHit> primaryHits = searchSimilarSegments(
                buildVectorSearchRequest(embedding, resolveCandidateTopK(), resolveReferenceThreshold())
        );
        if (primaryHits.size() >= MIN_NORMAL_HITS_PER_QUERY) {
            return primaryHits;
        }

        List<VectorSearchHit> fallbackHits = searchSimilarSegments(
                buildVectorSearchRequest(embedding, resolveCandidateTopK(), resolveFallbackThreshold())
        );
        if (fallbackHits.isEmpty()) {
            return primaryHits;
        }
        if (primaryHits.isEmpty()) {
            return fallbackHits;
        }
        return mergeHitsByBestScore(primaryHits, fallbackHits);
    }

    // 合并主检索与补召回结果，按最高分保留每个分段。
    private List<VectorSearchHit> mergeHitsByBestScore(List<VectorSearchHit> primaryHits,
                                                        List<VectorSearchHit> fallbackHits) {
        Map<Long, VectorSearchHit> mergedHits = new LinkedHashMap<>();
        for (VectorSearchHit hit : primaryHits) {
            if (hit != null && hit.getQuestionId() != null) {
                mergedHits.put(hit.getQuestionId(), hit);
            }
        }
        for (VectorSearchHit hit : fallbackHits) {
            if (hit == null || hit.getQuestionId() == null) {
                continue;
            }
            VectorSearchHit current = mergedHits.get(hit.getQuestionId());
            if (current == null || resolveScore(hit) > resolveScore(current)) {
                mergedHits.put(hit.getQuestionId(), hit);
            }
        }
        return mergedHits.values().stream()
                .sorted(Comparator.comparingDouble(this::resolveScore).reversed())
                .limit(resolveCandidateTopK())
                .toList();
    }

    // 基于统一 RAG 配置构建向量检索请求。
    private VectorSearchRequest buildVectorSearchRequest(float[] embedding, int topK, double threshold) {
        return VectorSearchRequest.builder()
                .queryVector(toFloatList(embedding))
                .topK(topK)
                .scoreThreshold(threshold)
                .build();
    }

    // 为单个查询文本生成向量。
    private float[] generateEmbedding(String text) {
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                return null;
            }
            if (response.getResults().get(0) == null) {
                return null;
            }
            return response.getResults().get(0).getOutput();
        } catch (Exception exception) {
            log.error("Generate embedding failed", exception);
            return null;
        }
    }

    // 将浮点数组转换为向量检索请求需要的 List。
    private List<Float> toFloatList(float[] vector) {
        if (vector == null || vector.length == 0) {
            return List.of();
        }
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    // 统一分值解析逻辑，优先使用 finalScore。
    private double resolveScore(VectorSearchHit hit) {
        if (hit == null) {
            return 0D;
        }
        if (hit.getFinalScore() != null) {
            return hit.getFinalScore();
        }
        if (hit.getSimilarity() != null) {
            return hit.getSimilarity();
        }
        return 0D;
    }

    // 解析检索返回的原始相似度，缺失时使用已有排序分兜底。
    private double resolveRawSimilarity(VectorSearchHit hit) {
        if (hit == null) {
            return 0D;
        }
        if (hit.getSimilarity() != null) {
            return hit.getSimilarity();
        }
        return resolveScore(hit);
    }

    // 构造空的输出载荷。
    private AnswerRAGOutputDTO createEmptyOutput() {
        return new AnswerRAGOutputDTO(List.of());
    }

    // 解析 top-k 配置，缺省时回退到默认值。
    private int resolveTopK() {
        if (ragProperties == null || ragProperties.getTopK() == null || ragProperties.getTopK() <= 0) {
            return 5;
        }
        return ragProperties.getTopK();
    }

    // 读取答疑检索主阈值配置。
    private double resolveReferenceThreshold() {
        if (ragProperties == null
                || ragProperties.getSimilarity() == null
                || ragProperties.getSimilarity().getReferenceThreshold() == null) {
            return 0.7D;
        }
        return ragProperties.getSimilarity().getReferenceThreshold();
    }

    // 计算答疑检索的降级阈值，避免高阈值导致召回过窄。
    private double resolveFallbackThreshold() {
        return Math.max(0.45D, resolveReferenceThreshold() - 0.20D);
    }

    // 扩大候选召回数量，为后续融合重排序提供空间。
    private int resolveCandidateTopK() {
        return Math.max(resolveTopK() * 3, resolveTopK() + 3);
    }

    // 读取RRF融合参数。
    private int resolveRrfK() {
        return DEFAULT_RRF_K;
    }

    // 对分数做归一化，避免不同量纲直接叠加。
    private double normalizeScore(double score, double maxScore) {
        if (maxScore <= 0D) {
            return score;
        }
        return score / maxScore;
    }

    // 校验节点运行所需依赖。
    private void validateDependencies() {
        if (embeddingModel == null) {
            throw new IllegalStateException("EmbeddingModel is not initialized");
        }
        if (knowledgeSegmentAgentVectorStore == null) {
            throw new IllegalStateException("knowledgeSegmentAgentVectorStore is not initialized");
        }
        if (knowledgeSegmentService == null) {
            throw new IllegalStateException("knowledgeSegmentService is not initialized");
        }
    }

    // 启动节点级 trace 记录。
    private Long startTraceItem(OverAllState state,
                                AnswerRewriteOutputDTO rewriteOutput,
                                AlgorithmQuestionResult currentQuestion) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null) {
            return null;
        }
        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        Map<String, Object> traceInput = new LinkedHashMap<>();
        traceInput.put("answerRewriteOutput", rewriteOutput);
        if (currentQuestion == null) {
            traceInput.put("currentQuestion", null);
        } else {
            Map<String, Object> currentQuestionPayload = new LinkedHashMap<>();
            currentQuestionPayload.put("id", currentQuestion.getId());
            currentQuestionPayload.put("title", currentQuestion.getTitle());
            traceInput.put("currentQuestion", currentQuestionPayload);
        }
        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.NODE)
                .itemKey("node-1")
                .inputPayload(workflowTraceSupport.toNodeInputPayload(traceInput))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    private record ScoredSegment(Long segmentId,
                                 double rawSimilarity,
                                 double similarityScore,
                                 double rrfScore,
                                 double finalScore) {
    }

    private record ScoredResolvedSegment(KnowledgeSegmentResult segment,
                                         double rawSimilarity,
                                         double similarityScore,
                                         double rrfScore,
                                         double finalScore) {
    }
}
