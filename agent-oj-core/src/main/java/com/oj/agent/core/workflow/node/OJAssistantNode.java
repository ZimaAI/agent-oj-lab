package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.TextType;
import com.oj.agent.core.workflow.enums.UserIntentType;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.workflow.model.dto.AnswerRAGOutputDTO;
import com.oj.agent.core.workflow.model.dto.AnswerRAGSegmentDTO;
import com.oj.agent.core.evaluation.model.result.CodeEvaluationResult;
import com.oj.agent.core.workflow.model.dto.IntentRecognitionOutputDTO;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.workflow.model.dto.CodeQuestionOutputDTO;
import com.oj.agent.core.workflow.model.result.CodeEvaluationNodeResult;
import com.oj.agent.core.workflow.model.result.RagJudgeResult;
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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OJAssistantNode implements NodeAction {

    private final ChatClient chatClient;

    private final WorkflowTraceRecorder workflowTraceRecorder;

    private final WorkflowTraceSupport workflowTraceSupport;

    public OJAssistantNode(AiModelRegistry aiModelRegistry,
                           WorkflowTraceRecorder workflowTraceRecorder,
                           WorkflowTraceSupport workflowTraceSupport) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        return Map.of(Constant.OJ_ASSISTANT_NODE_OUTPUT, createGenerator(state));
    }

    // assistant 执行上下文，封装消息与 trace 项标识。
    private record AssistantExecutionContext(List<Message> messages, Long traceItemId) {
    }

    UserIntentType getIntent(OverAllState state) {
        IntentRecognitionOutputDTO intentOutput = StateUtil.getObjectValue(
                state,
                Constant.INTENT_RECOGNITION_NODE_OUTPUT,
                IntentRecognitionOutputDTO.class,
                (IntentRecognitionOutputDTO) null
        );

        if (intentOutput == null || intentOutput.getIntent() == null) {
            log.warn("Intent is null, defaulting to OTHER");
            return UserIntentType.OTHER;
        }

        return intentOutput.getIntent();
    }

    AlgorithmQuestionResult getCurrentQuestionSnapshot(OverAllState state) {
        return StateUtil.getOptionalObjectValueOrNull(
                state,
                Constant.CURRENT_ALGORITHM_QUESTION,
                AlgorithmQuestionResult.class
        );
    }

    AlgorithmQuestionResult extractAlgorithmQuestion(OverAllState state) {
        // 优先从 RAGJudge 获取。
        RagJudgeResult judgeOutput = StateUtil.getObjectValue(
                state,
                Constant.RAG_JUDGE_NODE_OUTPUT,
                RagJudgeResult.class,
                (RagJudgeResult) null
        );

        if (judgeOutput != null && judgeOutput.isMatched() && judgeOutput.getSelectedQuestion() != null) {
            log.info("Using algorithm question from RAGJudge");
            AlgorithmQuestionResult selectedQuestion = judgeOutput.getSelectedQuestion();
            return selectedQuestion;
        }

        // 再从 CodeQuestion 获取。
        var codeOutput = StateUtil.getObjectValueOrNull(
                state,
                Constant.CODE_QUESTION_NODE_OUTPUT,
                CodeQuestionOutputDTO.class
        );

        if (codeOutput != null && codeOutput.isSuccess() && codeOutput.getQuestion() != null) {
            log.info("Using algorithm question from CodeQuestion");
            AlgorithmQuestionResult generatedQuestion = codeOutput.getQuestion();
            return generatedQuestion;
        }

        log.warn("No algorithm question found in state");
        return null;
    }

    CodeEvaluationResult extractCodeEvaluation(OverAllState state) {
        CodeEvaluationNodeResult evalOutput = StateUtil.getObjectValue(
                state,
                Constant.CODE_EVALUATION_NODE_OUTPUT,
                CodeEvaluationNodeResult.class,
                (CodeEvaluationNodeResult) null
        );

        if (evalOutput != null && evalOutput.isSuccess() && evalOutput.getCodeEvaluation() != null) {
            log.info("Using code evaluation from CodeEvaluationNode");
            return evalOutput.getCodeEvaluation();
        }

        log.warn("No code evaluation found in state");
        return null;
    }

    Flux<GraphResponse<StreamingOutput>> createGenerator(OverAllState state) {
        if (chatClient == null) {
            log.error("ChatClient is not initialized");
            return createErrorResult(state, "ChatClient 未初始化");
        }

        log.info("Starting OJAssistantNode");

        try {
            AssistantExecutionContext context = resolveAssistantExecutionContext(state);
            Flux<ChatResponse> chatResponseFlux = requestAssistantResponseFlux(context.messages());
            return createAssistantStreamingResult(state, context.traceItemId(), chatResponseFlux);
        } catch (Exception e) {
            log.error("Error in OJAssistantNode", e);
            return createErrorResult(state, "处理失败：" + e.getMessage());
        }
    }

    // 组装 assistant 执行上下文并启动 trace。
    private AssistantExecutionContext resolveAssistantExecutionContext(OverAllState state) {
        List<Message> messages = buildMessages(state);
        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        Long traceItemId = startAssistantTrace(traceId, messages);
        return new AssistantExecutionContext(messages, traceItemId);
    }

    // 请求 assistant 流式响应。
    private Flux<ChatResponse> requestAssistantResponseFlux(List<Message> messages) {
        return chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse();
    }

    // 构建 assistant 节点流式输出。
    private Flux<GraphResponse<StreamingOutput>> createAssistantStreamingResult(OverAllState state,
                                                                                 Long traceItemId,
                                                                                 Flux<ChatResponse> chatResponseFlux) {
        return FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                chatResponseFlux,
                Flux.just(ChatResponseUtil.createPureResponse(TextType.MARK_DOWN.getStartSign())),
                Flux.just(ChatResponseUtil.createPureResponse(TextType.MARK_DOWN.getEndSign())),
                this::buildAssistantNodeOutput,
                workflowTraceRecorder,
                traceItemId,
                result -> workflowTraceSupport == null
                        ? null
                        : workflowTraceSupport.toLlmOutputPayload(Map.of("assistantMessage", result))
        );
    }

    // 组装 assistant 节点输出状态。
    private Map<String, Object> buildAssistantNodeOutput(String result) {
        AssistantMessage assistantMsg = new AssistantMessage(result);
        return Map.of(
                Constant.MESSAGES, assistantMsg,
                Constant.OJ_ASSISTANT_NODE_OUTPUT, result
        );
    }

    private Flux<GraphResponse<StreamingOutput>> createErrorResult(OverAllState state, String errorMessage) {
        log.error("OJAssistantNode error: {}", errorMessage);

        return FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                Flux.just(ChatResponseUtil.createPureResponse(TextType.MARK_DOWN.getStartSign())),
                Flux.just(ChatResponseUtil.createPureResponse(TextType.MARK_DOWN.getEndSign())),
                Flux.just(ChatResponseUtil.createResponse("抱歉，我暂时无法回复：" + errorMessage)),
                result -> Map.of(Constant.OJ_ASSISTANT_NODE_OUTPUT, "抱歉，我暂时无法回复：" + errorMessage)
        );
    }

    private Long startAssistantTrace(String traceId, List<Message> messages) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceId == null || messages == null || messages.isEmpty()) {
            return null;
        }
        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.LLM)
                .itemKey("llm-1")
                .inputPayload(workflowTraceSupport.toLlmInputPayload(buildTraceInput(messages)))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    Map<String, Object> buildTraceInput(List<Message> messages) {
        String systemPrompt = "";
        String latestUserMessage = "";

        if (messages != null && !messages.isEmpty()) {
            Message firstMessage = messages.get(0);
            if (firstMessage instanceof SystemMessage systemMessage) {
                systemPrompt = systemMessage.getText();
            }
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message message = messages.get(i);
                if (message instanceof UserMessage userMessage) {
                    latestUserMessage = userMessage.getText();
                    break;
                }
            }
        }

        return Map.of(
                "systemPrompt", systemPrompt,
                "latestUserMessage", latestUserMessage
        );
    }

    List<Message> buildMessages(OverAllState state) {
        List<Message> history = StateUtil.getHistory(state);
        if (history == null) {
            history = new ArrayList<>();
        }

        UserIntentType intent = getIntent(state);
        // 生成新题场景不注入旧题快照，避免旧上下文干扰新题请求。
        AlgorithmQuestionResult currentQuestionSnapshot = intent == UserIntentType.NEW_QUESTION
                ? null
                : getCurrentQuestionSnapshot(state);
        log.info("Building messages for intent: {}", intent);

        List<Message> result = new ArrayList<>();
        String systemPrompt = PromptHelper.buildOjAssistantSystemPrompt(intent, currentQuestionSnapshot);
        result.add(new SystemMessage(systemPrompt));

        if (intent == UserIntentType.OTHER) {
            result.addAll(history);
            UserMessage latestUserMessage = StateUtil.getUserInput(state);
            if (latestUserMessage != null) {
                result.add(latestUserMessage);
            }
            log.info("Using original messages for OTHER intent");
            return result;
        }

        if (intent == UserIntentType.ANSWER) {
            // 答疑场景保留历史消息，并在最后一条用户消息前注入检索参考。
            if (!history.isEmpty()) {
                result.addAll(history);
            }
            appendAnswerRagReferenceSystemMessage(state, result);
            UserMessage latestUserMessage = StateUtil.getUserInput(state);
            if (latestUserMessage != null) {
                result.add(latestUserMessage);
            }
            log.info("Using RAG references for ANSWER intent");
            return result;
        }

        if (!history.isEmpty()) {
            result.addAll(history);
        }
        result.add(rewriteLastUserMessage(state, intent));
        log.info("Rewritten last UserMessage for intent: {}", intent);
        return result;
    }

    // 将答疑检索结果转换为系统参考消息并插入消息链。
    private void appendAnswerRagReferenceSystemMessage(OverAllState state, List<Message> messages) {
        AnswerRAGOutputDTO answerRagOutputDTO = StateUtil.getObjectValue(
                state,
                Constant.ANSWER_RAG_NODE_OUTPUT,
                AnswerRAGOutputDTO.class,
                (AnswerRAGOutputDTO) null
        );
        if (answerRagOutputDTO == null || answerRagOutputDTO.getSegments() == null || answerRagOutputDTO.getSegments().isEmpty()) {
            return;
        }
        String referencePrompt = buildAnswerRagReferencePrompt(answerRagOutputDTO.getSegments());
        if (referencePrompt == null || referencePrompt.isBlank()) {
            return;
        }
        messages.add(new SystemMessage(referencePrompt));
    }

    private String buildAnswerRagReferencePrompt(List<AnswerRAGSegmentDTO> segments) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are answering the user's question about the current algorithm problem.")
                .append(" Use the retrieved references below as primary evidence. ")
                .append("If references are insufficient, explicitly say uncertain and do not fabricate information.")
                .append('\n')
                .append('\n');

        int index = 1;
        for (AnswerRAGSegmentDTO segment : segments) {
            if (segment == null || segment.getText() == null || segment.getText().trim().isEmpty()) {
                continue;
            }
            promptBuilder.append("Reference Segment ").append(index).append(':').append('\n');
            promptBuilder.append("- segmentId: ").append(segment.getId()).append('\n');
            promptBuilder.append("- documentId: ").append(segment.getDocumentId()).append('\n');
            promptBuilder.append("- chunkOrder: ").append(segment.getChunkOrder()).append('\n');
            promptBuilder.append("- content: ").append(segment.getText().trim()).append('\n').append('\n');
            index++;
        }

        if (index == 1) {
            return "";
        }
        return promptBuilder.toString().trim();
    }

    private boolean isQuestionIntent(UserIntentType intent) {
        return intent == UserIntentType.NEW_QUESTION || intent == UserIntentType.CHANGE_DIFFICULT;
    }

    UserMessage rewriteLastUserMessage(OverAllState state, UserIntentType intent) {
        UserMessage originalInput = StateUtil.getUserInput(state);
        String originalQuestion = originalInput != null ? originalInput.getText() : "用户发起了对话";

        if (isQuestionIntent(intent)) {
            AlgorithmQuestionResult question = extractAlgorithmQuestion(state);
            var codeOutput = StateUtil.getObjectValueOrNull(
                    state,
                    Constant.CODE_QUESTION_NODE_OUTPUT,
                    CodeQuestionOutputDTO.class
            );
            String fallbackErrorMessage = codeOutput == null ? null : codeOutput.getErrorMessage();
            String content = PromptHelper.buildAssistantContextForQuestionResult(
                    intent,
                    originalQuestion,
                    question,
                    fallbackErrorMessage
            );
            return new UserMessage(content);
        }

        if (intent == UserIntentType.CODE_EVALUATION) {
            CodeEvaluationResult evaluation = extractCodeEvaluation(state);
            String content = PromptHelper.buildAssistantContextForCodeEvaluation(originalQuestion, evaluation);
            return new UserMessage(content);
        }

        log.warn("Unhandled intent type: {}, using original input", intent);
        return originalInput != null ? originalInput : new UserMessage("用户发起了对话");
    }
}
