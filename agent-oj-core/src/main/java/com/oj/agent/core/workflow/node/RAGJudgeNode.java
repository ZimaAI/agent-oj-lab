package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.TextType;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.question.model.query.AlgorithmQuestionGetQuery;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.workflow.model.dto.QuestionRAGOutputDTO;
import com.oj.agent.core.workflow.model.dto.RagJudgeLlmOutputDTO;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.aimodel.prompt.PromptHelper;
import com.oj.agent.core.question.service.AlgorithmQuestionService;
import com.oj.agent.core.workflow.model.result.RagJudgeResult;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.core.workflow.trace.WorkflowTraceSupport;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.result.TraceItemTypeResult;
import com.oj.agent.core.trace.model.result.TraceTokenUsageResult;
import com.oj.agent.core.workflow.util.ChatResponseUtil;
import com.oj.agent.core.workflow.util.FluxUtil;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
@Component
public class RAGJudgeNode implements NodeAction {

    private static final String RAG_JUDGE_LLM_OUTPUT_FORMAT =
            new BeanOutputConverter<>(RagJudgeLlmOutputDTO.class).getFormat();

    private final ChatClient chatClient;

    private final WorkflowTraceRecorder workflowTraceRecorder;

    private final WorkflowTraceSupport workflowTraceSupport;

    private final AlgorithmQuestionService algorithmQuestionService;

    public RAGJudgeNode(AiModelRegistry aiModelRegistry,
                        WorkflowTraceRecorder workflowTraceRecorder,
                        WorkflowTraceSupport workflowTraceSupport,
                        AlgorithmQuestionService algorithmQuestionService) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
        this.algorithmQuestionService = algorithmQuestionService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        return Map.of(Constant.RAG_JUDGE_NODE_OUTPUT, createGenerator(state));
    }

    Flux<GraphResponse<StreamingOutput>> createGenerator(OverAllState state) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not initialized");
        }

        log.info("Starting RAG judge process");

        QuestionRAGOutputDTO ragOutput = StateUtil.getObjectValue(
                state,
                Constant.QUESTION_RAG_NODE_OUTPUT,
                QuestionRAGOutputDTO.class,
                (QuestionRAGOutputDTO) null
        );
        UserMessage userInput = StateUtil.getUserInput(state);
        String userQuestion = userInput == null ? null : userInput.getText();
        String prompt = buildJudgePrompt(state, ragOutput, userQuestion);
        Long traceItemId = prompt == null
                ? startFallbackRagJudgeTraceItem(state, "")
                : startPromptRagJudgeTraceItem(state, prompt);

        AtomicReference<RagJudgeResult> outputRef = new AtomicReference<>(new RagJudgeResult(false, null));
        AtomicReference<RagJudgeLlmOutputDTO> llmOutputRef = new AtomicReference<>(
                new RagJudgeLlmOutputDTO(false, null, "RAG judge fallback result")
        );

        Flux<ChatResponse> sourceFlux = Mono.fromCallable(() -> executeJudge(ragOutput, prompt))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(result -> {
                    outputRef.set(result.output());
                    llmOutputRef.set(result.llmOutput());
                })
                .map(result -> ChatResponseUtil.createPureResponseWithUsage(
                        serializeJudgeOutput(result.output()),
                        result.tokenUsage()
                ))
                .flux()
                .onErrorResume(error -> {
                    log.error("Error during RAG judge process", error);
                    RagJudgeResult fallbackOutput = new RagJudgeResult(false, null);
                    RagJudgeLlmOutputDTO fallbackLlmOutput = new RagJudgeLlmOutputDTO(
                            false,
                            null,
                            "RAG judge execution failed: " + error.getMessage()
                    );
                    outputRef.set(fallbackOutput);
                    llmOutputRef.set(fallbackLlmOutput);
                    return Flux.just(ChatResponseUtil.createPureResponse(serializeJudgeOutput(fallbackOutput)));
                });

        Flux<ChatResponse> preFlux = Flux.just(
                ChatResponseUtil.createResponse("正在判断检索结果是否符合要求"),
                ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())
        );
        Flux<ChatResponse> sufFlux = Flux.just(
                ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())
        );

        return FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                sourceFlux,
                preFlux,
                sufFlux,
                result -> Map.of(Constant.RAG_JUDGE_NODE_OUTPUT, outputRef.get()),
                workflowTraceRecorder,
                traceItemId,
                result -> workflowTraceSupport == null
                        ? null
                        : workflowTraceSupport.toLlmOutputPayload(
                        buildRagJudgeTraceData(llmOutputRef.get(), outputRef.get().getSelectedQuestion()))
        );
    }

    private JudgeExecutionResult executeJudge(QuestionRAGOutputDTO ragOutput, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return new JudgeExecutionResult(
                    new RagJudgeResult(false, null),
                    new RagJudgeLlmOutputDTO(false, null, "RAG judge prompt is empty"),
                    null
            );
        }

        TraceTokenUsageResult tokenUsage = null;
        try {
            ChatResponse llmResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();
            tokenUsage = ChatResponseUtil.extractTokenUsage(llmResponse);
            String llmResult = ChatResponseUtil.getText(llmResponse);
            if (llmResult == null || llmResult.isBlank()) {
                return new JudgeExecutionResult(
                        new RagJudgeResult(false, null),
                        new RagJudgeLlmOutputDTO(false, null, "RAG judge LLM returned empty content"),
                        tokenUsage
                );
            }

            BeanOutputConverter<RagJudgeLlmOutputDTO> converter = new BeanOutputConverter<>(RagJudgeLlmOutputDTO.class);
            RagJudgeLlmOutputDTO llmOutput = converter.convert(llmResult);
            AlgorithmQuestionResult selectedQuestion = findSelectedQuestion(ragOutput, llmOutput);
            AlgorithmQuestionResult selectedQuestionResult = resolveSelectedQuestionResult(selectedQuestion);
            return new JudgeExecutionResult(
                    new RagJudgeResult(selectedQuestionResult != null, selectedQuestionResult),
                    llmOutput,
                    tokenUsage
            );
        } catch (Exception e) {
            log.error("Error parsing LLM output in RAG judge", e);
            return new JudgeExecutionResult(
                    new RagJudgeResult(false, null),
                    new RagJudgeLlmOutputDTO(false, null, "RAG judge LLM output parse failed"),
                    tokenUsage
            );
        }
    }

    // 为成功路径启动 RAG 判断 trace，输入中只记录真实完整 prompt。
    private Long startPromptRagJudgeTraceItem(OverAllState state, String prompt) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null) {
            return null;
        }
        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.LLM)
                .itemKey("llm-1")
                .inputPayload(workflowTraceSupport.toLlmInputPayload(Map.of("prompt", prompt)))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    // 为兜底路径启动 RAG 判断 trace，保持与成功路径一致的 prompt 输入结构。
    private Long startFallbackRagJudgeTraceItem(OverAllState state, String prompt) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null) {
            return null;
        }
        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.LLM)
                .itemKey("llm-1")
                .inputPayload(workflowTraceSupport.toLlmInputPayload(Map.of("prompt", prompt)))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    // 构造 RAG 判断提示词，异常时返回 null。
    String buildJudgePrompt(OverAllState state, QuestionRAGOutputDTO ragOutput, String userQuestion) {
        try {
            log.info("Starting judge questions, user question: {}", userQuestion);

            if (ragOutput == null) {
                log.warn("QuestionRAGOutputDTO is null");
                return null;
            }
            if (userQuestion == null || userQuestion.isBlank()) {
                log.warn("User question is blank");
                return null;
            }

            List<AlgorithmQuestionResult> candidates = ragOutput.getCandidates();
            if (candidates == null || candidates.isEmpty()) {
                log.warn("No candidates found in RAG output");
                return null;
            }

            AlgorithmQuestionResult currentQuestion = StateUtil.getObjectValue(
                    state,
                    Constant.CURRENT_ALGORITHM_QUESTION,
                    AlgorithmQuestionResult.class,
                    (AlgorithmQuestionResult) null
            );
            String promptText = PromptHelper.buildRagJudgePrompt(
                    userQuestion,
                    currentQuestion,
                    candidates,
                    RAG_JUDGE_LLM_OUTPUT_FORMAT
            );
            log.debug("Built prompt for LLM: {}", promptText);
            return promptText;
        } catch (Exception e) {
            log.error("Error during judge questions", e);
            return null;
        }
    }

    // 构造固定结构的 trace data，确保 llmOutput 与 selectedQuestion 字段始终同时存在。
    private AlgorithmQuestionResult resolveSelectedQuestionResult(AlgorithmQuestionResult selectedQuestion) {
        if (selectedQuestion == null) {
            return null;
        }

        Long questionId = selectedQuestion.getId();
        if (questionId != null && algorithmQuestionService != null) {
            try {
                return algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(questionId));
            } catch (Exception e) {
                log.warn(
                        "Failed to load detail question for RAG selected question, fallback to base payload, questionId={}",
                        questionId,
                        e
                );
            }
        }

        return selectedQuestion;
    }

    private Map<String, Object> buildRagJudgeTraceData(RagJudgeLlmOutputDTO llmOutput,
                                                       AlgorithmQuestionResult selectedQuestion) {
        Map<String, Object> traceData = new LinkedHashMap<>();
        traceData.put("llmOutput", llmOutput);
        traceData.put("selectedQuestion", selectedQuestion);
        return traceData;
    }

    // 根据 LLM 输出在候选题中匹配最终选中的题目。
    private AlgorithmQuestionResult findSelectedQuestion(QuestionRAGOutputDTO ragOutput, RagJudgeLlmOutputDTO llmOutput) {
        List<AlgorithmQuestionResult> candidates = ragOutput == null ? null : ragOutput.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            log.info("Candidates list is null or empty in RAG judge");
            return null;
        }
        if (llmOutput == null || !llmOutput.isMatched()) {
            log.info("No matching question found");
            return null;
        }

        Long selectedId = llmOutput.getSelectedQuestionId();
        if (selectedId == null) {
            log.error("LLM returned isMatched=true but selectedQuestionId is null");
            return null;
        }

        AlgorithmQuestionResult selectedQuestion = candidates.stream()
                .filter(q -> selectedId.equals(q.getId()))
                .findFirst()
                .orElse(null);
        if (selectedQuestion == null) {
            log.error("Selected question ID {} not found in candidates", selectedId);
            return null;
        }

        log.info("Successfully selected question: ID={}, title={}",
                selectedQuestion.getId(), selectedQuestion.getTitle());
        return selectedQuestion;
    }

    private String serializeJudgeOutput(RagJudgeResult output) {
        try {
            return JsonUtils.getObjectMapper().writeValueAsString(output);
        } catch (Exception e) {
            log.error("Failed to serialize RAG judge output", e);
            return "{\"is_matched\":false,\"selected_question\":null}";
        }
    }

    private record JudgeExecutionResult(RagJudgeResult output,
                                        RagJudgeLlmOutputDTO llmOutput,
                                        TraceTokenUsageResult tokenUsage) {
    }
}
