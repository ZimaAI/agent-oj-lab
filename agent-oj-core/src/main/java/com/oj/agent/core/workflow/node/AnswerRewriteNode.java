package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.TextType;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.workflow.model.dto.AnswerRewriteOutputDTO;
import com.oj.agent.common.util.JsonUtils;
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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AnswerRewriteNode implements NodeAction {

    private final ChatClient chatClient;

    private final WorkflowTraceRecorder workflowTraceRecorder;

    private final WorkflowTraceSupport workflowTraceSupport;

    public AnswerRewriteNode(AiModelRegistry aiModelRegistry,
                             WorkflowTraceRecorder workflowTraceRecorder,
                             WorkflowTraceSupport workflowTraceSupport) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        return Map.of(Constant.ANSWER_REWRITE_NODE_OUTPUT, createGenerator(state));
    }

    // 构建答疑检索问题改写的流式生成器。
    Flux<GraphResponse<StreamingOutput>> createGenerator(OverAllState state) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not initialized");
        }

        // 加载历史消息与最新用户输入，构造改写提示词。
        UserMessage userInput = StateUtil.getUserInput(state);
        List<Message> history = StateUtil.getHistory(state);
        String userInputText = userInput == null ? "" : userInput.getText();
        AlgorithmQuestionResult currentQuestion = StateUtil.getOptionalObjectValueOrNull(
                state,
                Constant.CURRENT_ALGORITHM_QUESTION,
                AlgorithmQuestionResult.class
        );
        String prompt = PromptHelper.buildAnswerRewritePrompt(
                history,
                userInputText,
                currentQuestion
        );

        Flux<ChatResponse> chatResponseFlux = chatClient.prompt()
                .user(prompt)
                .stream()
                .chatResponse();

        // 记录当前节点的 LLM 调用 trace。
        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        Long traceItemId = workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.LLM)
                .itemKey("llm-1")
                .inputPayload(workflowTraceSupport.toLlmInputPayload(Map.of("prompt", prompt)))
                .startTimestamp(System.currentTimeMillis())
                .build());

        return FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                chatResponseFlux,
                Flux.just(
                        ChatResponseUtil.createResponse("Rewriting answer retrieval queries..."),
                        ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())
                ),
                Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())),
                result -> {
                    // 解析并规范化输出：遇到截断 JSON 时做容错恢复，避免链路中断。
                    AnswerRewriteOutputDTO outputDTO = parseModelOutput(result, userInputText, currentQuestion);
                    return Map.of(
                            Constant.ANSWER_REWRITE_NODE_OUTPUT,
                            outputDTO
                    );
                },
                workflowTraceRecorder,
                traceItemId,
                result -> {
                    // trace 输出与主输出保持同一解析路径，避免回填阶段再次抛解析异常。
                    AnswerRewriteOutputDTO outputDTO = parseModelOutput(result, userInputText, currentQuestion);
                    return workflowTraceSupport.toLlmOutputPayload(outputDTO);
                });
    }

    // 解析模型输出并执行兜底，确保下游始终拿到可用的改写查询。
    AnswerRewriteOutputDTO parseModelOutput(String rawResult,
                                            String userInput,
                                            AlgorithmQuestionResult currentQuestion) {
        AnswerRewriteOutputDTO parsedOutput = null;
        try {
            // 优先走标准 JSON 解析，兼容代码块包裹等常见输出形态。
            parsedOutput = JsonUtils.fromJson(rawResult, AnswerRewriteOutputDTO.class);
        } catch (Exception exception) {
            // 解析失败时尽量从破损 JSON 中恢复已完整闭合的查询项。
            List<String> recoveredQueries = recoverQueriesFromMalformedResult(rawResult);
            if (!recoveredQueries.isEmpty()) {
                log.warn("Failed to parse answer rewrite output, recovered {} queries from malformed payload. cause={}",
                        recoveredQueries.size(), exception.getMessage());
                parsedOutput = new AnswerRewriteOutputDTO(recoveredQueries);
            } else {
                log.warn("Failed to parse answer rewrite output, fallback to synthetic queries. cause={}",
                        exception.getMessage());
                parsedOutput = new AnswerRewriteOutputDTO(List.of());
            }
        }
        return sanitizeOutput(parsedOutput, userInput, currentQuestion);
    }

    // 从异常输出中尽量提取 rewrittenQueries 数组里已完整的字符串项。
    private List<String> recoverQueriesFromMalformedResult(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            return List.of();
        }
        String extractedJson = JsonUtils.extractJson(rawResult);
        if (extractedJson == null || extractedJson.isBlank()) {
            return List.of();
        }
        int rewrittenQueriesIndex = extractedJson.indexOf("\"rewrittenQueries\"");
        if (rewrittenQueriesIndex < 0) {
            rewrittenQueriesIndex = extractedJson.indexOf("rewrittenQueries");
        }
        if (rewrittenQueriesIndex < 0) {
            return List.of();
        }
        int arrayStart = extractedJson.indexOf('[', rewrittenQueriesIndex);
        if (arrayStart < 0) {
            return List.of();
        }

        List<String> recoveredQueries = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = arrayStart + 1; i < extractedJson.length(); i++) {
            char currentChar = extractedJson.charAt(i);
            if (escaped) {
                // 保留转义后的字符内容，确保 \" 这类场景可恢复。
                if (inString) {
                    currentToken.append(currentChar);
                }
                escaped = false;
                continue;
            }
            if (currentChar == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (currentChar == '"') {
                if (inString) {
                    String recoveredQuery = currentToken.toString().trim();
                    if (!recoveredQuery.isEmpty()) {
                        recoveredQueries.add(recoveredQuery);
                    }
                    currentToken.setLength(0);
                    inString = false;
                } else {
                    inString = true;
                }
                continue;
            }
            if (currentChar == ']' && !inString) {
                break;
            }
            if (inString) {
                currentToken.append(currentChar);
            }
        }
        return recoveredQueries;
    }

    // 规范化模型输出，供下游检索节点使用。
    private AnswerRewriteOutputDTO sanitizeOutput(AnswerRewriteOutputDTO outputDTO,
                                                  String userInput,
                                                  AlgorithmQuestionResult currentQuestion) {
        List<String> candidateQueries = new ArrayList<>();
        if (outputDTO != null && outputDTO.getRewrittenQueries() != null) {
            candidateQueries.addAll(outputDTO.getRewrittenQueries());
        }

        // 增加兜底查询，避免改写结果偏离用户原始诉求。
        candidateQueries.add(userInput);

        List<String> normalizedQueries = candidateQueries.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(query -> !query.isEmpty())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));

        return new AnswerRewriteOutputDTO(List.copyOf(normalizedQueries));
    }

}
