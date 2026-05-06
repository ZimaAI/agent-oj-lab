package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.TextType;
import com.oj.agent.core.workflow.enums.UserIntentType;
import com.oj.agent.core.workflow.model.dto.IntentRecognitionOutputDTO;
import com.oj.agent.core.workflow.model.dto.QuestionRewriteOutputDTO;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.aimodel.prompt.PromptHelper;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.core.workflow.trace.WorkflowTraceSupport;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.result.TraceItemTypeResult;
import com.oj.agent.core.workflow.util.ChatResponseUtil;
import com.oj.agent.core.workflow.util.FluxUtil;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class QuestionRewriteNode implements NodeAction {

    private final ChatClient chatClient;

    private final WorkflowTraceRecorder workflowTraceRecorder;

    private final WorkflowTraceSupport workflowTraceSupport;

    public QuestionRewriteNode(AiModelRegistry aiModelRegistry,
                               WorkflowTraceRecorder workflowTraceRecorder,
                               WorkflowTraceSupport workflowTraceSupport) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        return Map.of(Constant.QUESTION_REWRITE_NODE_OUTPUT, createGenerator(state));
    }

    Flux<GraphResponse<StreamingOutput>> createGenerator(OverAllState state) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not initialized");
        }

        UserMessage userInput = StateUtil.getUserInput(state);
        log.info("User input for question rewrite: {}", userInput);

        List<Message> history = StateUtil.getHistory(state);
        IntentRecognitionOutputDTO intentOutput = StateUtil.getOptionalObjectValueOrNull(state,
                Constant.INTENT_RECOGNITION_NODE_OUTPUT,
                IntentRecognitionOutputDTO.class);
        UserIntentType intent = intentOutput == null || intentOutput.getIntent() == null
                ? UserIntentType.OTHER
                : intentOutput.getIntent();
        var currentQuestion = StateUtil.getOptionalObjectValueOrNull(state,
                Constant.CURRENT_ALGORITHM_QUESTION,
                AlgorithmQuestionResult.class);
        String prompt = PromptHelper.buildQuestionRewritePrompt(
                history,
                userInput == null ? "" : userInput.getText(),
                intent,
                currentQuestion);

        Flux<ChatResponse> chatResponseFlux = chatClient.prompt()
                .user(prompt)
                .stream()
                .chatResponse();

        // 为问题重写节点记录 LLM 输入。
        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        Long traceItemId = workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.LLM)
                .itemKey("llm-1")
                .inputPayload(workflowTraceSupport.toLlmInputPayload(Map.of("prompt", prompt)))
                .startTimestamp(System.currentTimeMillis())
                .build());

        return FluxUtil.createStreamingGenerator(this.getClass(), state,
                chatResponseFlux,
                Flux.just(ChatResponseUtil.createResponse("正在重写检索问题..."),
                        ChatResponseUtil.createPureResponse(TextType.MARK_DOWN.getStartSign())),
                Flux.just(ChatResponseUtil.createPureResponse(TextType.MARK_DOWN.getEndSign())),
                result -> {
                    BeanOutputConverter<QuestionRewriteOutputDTO> converter =
                            new BeanOutputConverter<>(QuestionRewriteOutputDTO.class);
                    QuestionRewriteOutputDTO outputDTO = converter.convert(result);
                    return Map.of(Constant.QUESTION_REWRITE_NODE_OUTPUT, outputDTO);
                },
                workflowTraceRecorder,
                traceItemId,
                result -> {
                    BeanOutputConverter<QuestionRewriteOutputDTO> converter =
                            new BeanOutputConverter<>(QuestionRewriteOutputDTO.class);
                    QuestionRewriteOutputDTO outputDTO = converter.convert(result);
                    return workflowTraceSupport.toLlmOutputPayload(outputDTO);
                });
    }
}
