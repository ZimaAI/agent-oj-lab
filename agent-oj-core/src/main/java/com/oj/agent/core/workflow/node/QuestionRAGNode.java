package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.TextType;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.workflow.model.dto.QuestionRAGOutputDTO;
import com.oj.agent.core.workflow.model.dto.QuestionRewriteFilterDTO;
import com.oj.agent.core.workflow.model.dto.QuestionRewriteOutputDTO;
import com.oj.agent.core.rag.config.RAGProperties;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.rag.model.VectorSearchHit;
import com.oj.agent.core.rag.model.VectorSearchRequest;
import com.oj.agent.core.rag.model.VectorSearchResult;
import com.oj.agent.core.rag.store.AgentVectorStore;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.core.workflow.trace.WorkflowTraceSupport;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.result.TraceItemTypeResult;
import com.oj.agent.core.workflow.util.ChatResponseUtil;
import com.oj.agent.core.workflow.util.FluxUtil;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QuestionRAGNode implements NodeAction {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final AgentVectorStore agentVectorStore;
    private final AlgorithmQuestionService algorithmQuestionService;
    private final RAGProperties ragProperties;
    private final WorkflowTraceRecorder workflowTraceRecorder;
    private final WorkflowTraceSupport workflowTraceSupport;

    public QuestionRAGNode(AiModelRegistry aiModelRegistry,
                           AgentVectorStore agentVectorStore,
                           AlgorithmQuestionService algorithmQuestionService,
                           RAGProperties ragProperties,
                           WorkflowTraceRecorder workflowTraceRecorder,
                           WorkflowTraceSupport workflowTraceSupport) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
        this.embeddingModel = aiModelRegistry == null ? null : aiModelRegistry.getDefaultEmbeddingModel();
        this.agentVectorStore = agentVectorStore;
        this.algorithmQuestionService = algorithmQuestionService;
        this.ragProperties = ragProperties;
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        return Map.of(Constant.QUESTION_RAG_NODE_OUTPUT, createGenerator(state));
    }

    Flux<GraphResponse<StreamingOutput>> createGenerator(OverAllState state) {
        validateClients();
        log.info("Starting RAG retrieval for question");

        QuestionRewriteOutputDTO rewriteOutput = StateUtil.getObjectValue(
                state,
                Constant.QUESTION_REWRITE_NODE_OUTPUT,
                QuestionRewriteOutputDTO.class,
                (QuestionRewriteOutputDTO) null
        );
        Long traceItemId = startRagTraceItem(state, rewriteOutput);

        AtomicReference<QuestionRAGOutputDTO> outputRef = new AtomicReference<>(createEmptyOutput());
        Flux<ChatResponse> sourceFlux = Mono.fromRunnable(() -> outputRef.set(executeRagRetrieval(rewriteOutput)))
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(Flux.empty());

        Flux<ChatResponse> preFlux = Flux.just(
                ChatResponseUtil.createResponse("正在检索相关算法题..."),
                ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())
        );
        Flux<ChatResponse> sufFlux = Flux.defer(() -> Flux.just(
                ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())
        ));

        return FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                sourceFlux,
                preFlux,
                sufFlux,
                result -> Map.of(Constant.QUESTION_RAG_NODE_OUTPUT, outputRef.get()),
                workflowTraceRecorder,
                traceItemId,
                result -> workflowTraceSupport == null
                        ? null
                        : workflowTraceSupport.toNodeOutputPayload(outputRef.get())
        );
    }

    private QuestionRAGOutputDTO executeRagRetrieval(QuestionRewriteOutputDTO rewriteOutput) {
        try {
            RetrievalContext retrievalContext = resolveRetrievalContext(rewriteOutput);
            if (retrievalContext == null) {
                return createEmptyOutput();
            }
            List<AlgorithmQuestionResult> candidateDetails = retrieveCandidateDetails(retrievalContext);
            return new QuestionRAGOutputDTO(candidateDetails);
        } catch (Exception e) {
            log.error("Error during RAG retrieval", e);
            return createEmptyOutput();
        }
    }

    private QuestionRAGOutputDTO createEmptyOutput() {
        return new QuestionRAGOutputDTO(Collections.emptyList());
    }

    private int getCandidateSize(QuestionRAGOutputDTO outputDTO) {
        if (outputDTO == null || outputDTO.getCandidates() == null) {
            return 0;
        }
        return outputDTO.getCandidates().size();
    }

    private void validateClients() {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not initialized");
        }
        if (embeddingModel == null) {
            throw new IllegalStateException("EmbeddingModel is not initialized");
        }
    }

    private RetrievalContext resolveRetrievalContext(QuestionRewriteOutputDTO rewriteOutput) {
        if (rewriteOutput == null || rewriteOutput.getRewrittenQuery() == null) {
            log.warn("QuestionRewriteOutputDTO is null or rewrittenQuery is null");
            return null;
        }

        String rewrittenQuery = rewriteOutput.getRewrittenQuery();
        QuestionRewriteFilterDTO explicitFilters = rewriteOutput.getExplicitFilters();
        QuestionRewriteFilterDTO inferredPreferences = rewriteOutput.getInferredPreferences();

        log.info("Rewritten query: {}", rewrittenQuery);
        log.info("Explicit filters: {}", explicitFilters);
        log.info("Inferred preferences: {}", inferredPreferences);

        float[] queryEmbedding = generateEmbedding(rewrittenQuery);
        if (queryEmbedding == null) {
            log.error("Failed to generate embedding for query");
            return null;
        }

        return new RetrievalContext(rewrittenQuery, queryEmbedding, explicitFilters, inferredPreferences);
    }

    private List<AlgorithmQuestionResult> retrieveCandidateDetails(RetrievalContext context) {
        VectorSearchRequest request = buildVectorSearchRequest(context);
        List<VectorSearchHit> searchResults = searchSimilarQuestions(request);
        log.info("Found {} similar questions from vector search", searchResults.size());

        List<VectorSearchHit> candidates = selectCandidates(searchResults);
        log.info("Selected {} candidates for RAG judge", candidates.size());

        List<Long> candidateIds = candidates.stream()
                .map(VectorSearchHit::getQuestionId)
                .collect(Collectors.toList());
        return fetchQuestionDetails(candidateIds);
    }

    private Long startRagTraceItem(OverAllState state, QuestionRewriteOutputDTO rewriteOutput) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null) {
            return null;
        }
        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        Map<String, Object> traceInput = new LinkedHashMap<>();
        traceInput.put("questionRewriteOutput", rewriteOutput);
        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.NODE)
                .itemKey("node-1")
                .inputPayload(workflowTraceSupport.toNodeInputPayload(traceInput))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    private float[] generateEmbedding(String text) {
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            if (response == null || response.getResults().isEmpty()) {
                log.error("Embedding response is null or empty");
                return null;
            }
            return response.getResults().get(0).getOutput();
        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            return null;
        }
    }

    private VectorSearchRequest buildVectorSearchRequest(RetrievalContext context) {
//        QuestionRewriteFilterDTO explicitFilters = context.explicitFilters();
//        QuestionRewriteFilterDTO inferredPreferences = context.inferredPreferences();
        // TODO: 暂时忽略其他过滤条件
        return VectorSearchRequest.builder()
                .queryVector(toFloatList(context.queryEmbedding()))
                .topK(ragProperties.getTopK())
                .scoreThreshold(ragProperties.getSimilarity().getReferenceThreshold())
//                .difficultyFilter(explicitFilters == null ? null : explicitFilters.getDifficulty())
//                .languageFilter(explicitFilters == null ? null : explicitFilters.getLanguage())
//                .preferredDifficulty(inferredPreferences == null ? null : inferredPreferences.getDifficulty())
//                .preferredLanguage(inferredPreferences == null ? null : inferredPreferences.getLanguage())
                .preferenceWeight(ragProperties.getPreferenceWeight())
                .build();
    }

    private List<Float> toFloatList(float[] vector) {
        if (vector == null) {
            return List.of();
        }
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    List<VectorSearchHit> searchSimilarQuestions(VectorSearchRequest request) {
        VectorSearchResult searchResult = agentVectorStore.search(request);
        if (searchResult == null || searchResult.getHits() == null) {
            return List.of();
        }
        return searchResult.getHits().stream()
                .map(this::normalizeSearchHit)
                .collect(Collectors.toList());
    }

    private VectorSearchHit normalizeSearchHit(VectorSearchHit hit) {
        if (hit == null) {
            return null;
        }
        return VectorSearchHit.builder()
                .documentId(hit.getDocumentId())
                .questionId(hit.getQuestionId())
                .title(hit.getTitle())
                .difficulty(hit.getDifficulty())
                .language(hit.getLanguage())
                .similarity(hit.getSimilarity())
                .finalScore(hit.getFinalScore() == null ? hit.getSimilarity() : hit.getFinalScore())
                .build();
    }

    void applyPreferenceBoost(List<VectorSearchHit> results, QuestionRewriteFilterDTO preferences) {
        if (preferences == null || ragProperties.getPreferenceWeight() == null || ragProperties.getPreferenceWeight() == 0) {
            return;
        }

        results.forEach(result -> {
            double boost = 0.0;
            if (preferences.getDifficulty() != null && preferences.getDifficulty().equals(result.getDifficulty())) {
                boost += ragProperties.getPreferenceWeight();
            }
            if (preferences.getLanguage() != null && preferences.getLanguage().equals(result.getLanguage())) {
                boost += ragProperties.getPreferenceWeight();
            }
            result.setFinalScore(score(result.getSimilarity()) + boost);
        });

        results.sort(Comparator.comparing(VectorSearchHit::getFinalScore).reversed());
    }

    List<VectorSearchHit> selectCandidates(List<VectorSearchHit> results) {
        double threshold = ragProperties.getSimilarity().getReferenceThreshold();
        return results.stream()
                .filter(result -> result != null && result.getQuestionId() != null)
                .filter(result -> score(result.getFinalScore()) > threshold)
                .limit(ragProperties.getTopK())
                .collect(Collectors.toList());
    }

    List<AlgorithmQuestionResult> fetchQuestionDetails(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }

        try {
            List<AlgorithmQuestionResult> details = new ArrayList<>();
            for (Long questionId : questionIds) {
                if (questionId == null) {
                    continue;
                }
                try {
                    AlgorithmQuestionResult question = algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(questionId));
                    if (question != null) {
                        details.add(question);
                    }
                } catch (IllegalArgumentException exception) {
                    log.debug("Skip missing question candidate, questionId={}", questionId, exception);
                }
            }
            return details;
        } catch (Exception e) {
            log.error("Failed to fetch question details", e);
            return List.of();
        }
    }

    private double score(Double value) {
        return value == null ? 0D : value;
    }

    private record RetrievalContext(String rewrittenQuery,
                                    float[] queryEmbedding,
                                    QuestionRewriteFilterDTO explicitFilters,
                                    QuestionRewriteFilterDTO inferredPreferences) {
    }
}
