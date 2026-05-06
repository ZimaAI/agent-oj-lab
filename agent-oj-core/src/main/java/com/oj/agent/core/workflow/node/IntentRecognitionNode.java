package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.TextType;
import com.oj.agent.core.workflow.model.dto.IntentRecognitionOutputDTO;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
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
public class IntentRecognitionNode implements NodeAction {

    private final ChatClient chatClient;

    private final WorkflowTraceRecorder workflowTraceRecorder;

    private final WorkflowTraceSupport workflowTraceSupport;

    public IntentRecognitionNode(AiModelRegistry aiModelRegistry,
                                 WorkflowTraceRecorder workflowTraceRecorder,
                                 WorkflowTraceSupport workflowTraceSupport) {
        chatClient = aiModelRegistry.getChatClient(Constant.MODEL_QWEN_FLASH);
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        UserMessage userInput = StateUtil.getUserInput(state);
        log.info("User input for intent recognition: {}", userInput);

        List<Message> history = StateUtil.getHistory(state);
        String prompt = PromptHelper.buildIntentRecognitionPrompt(history, userInput.getText());

        Flux<ChatResponse> chatResponseFlux = chatClient.prompt()
                .user(prompt)
                .stream()
                .chatResponse();

        // 为意图识别节点记录 LLM 输入。
        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        Long traceItemId = workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.LLM)
                .itemKey("llm-1")
                .inputPayload(workflowTraceSupport.toLlmInputPayload(Map.of("prompt", prompt)))
                .startTimestamp(System.currentTimeMillis())
                .build());

        Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
                chatResponseFlux,
                Flux.just(ChatResponseUtil.createResponse("正在进行意图识别..."),
                        ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
                Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())),
                result -> {
                    BeanOutputConverter<IntentRecognitionOutputDTO> beanOutputConverter = new BeanOutputConverter<>(
                            IntentRecognitionOutputDTO.class);
                    IntentRecognitionOutputDTO intentRecognitionOutputDTO = beanOutputConverter.convert(result);
                    return Map.of(Constant.INTENT_RECOGNITION_NODE_OUTPUT, intentRecognitionOutputDTO);
                },
                workflowTraceRecorder,
                traceItemId,
                result -> {
                    BeanOutputConverter<IntentRecognitionOutputDTO> beanOutputConverter = new BeanOutputConverter<>(
                            IntentRecognitionOutputDTO.class);
                    IntentRecognitionOutputDTO intentRecognitionOutputDTO = beanOutputConverter.convert(result);
                    return workflowTraceSupport.toLlmOutputPayload(intentRecognitionOutputDTO);
                });
        return Map.of(Constant.INTENT_RECOGNITION_NODE_OUTPUT, generator);
    }
}
