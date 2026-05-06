package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.state.ReplaceAllWith;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.UserIntentType;
import com.oj.agent.core.workflow.model.dto.IntentRecognitionOutputDTO;
import com.oj.agent.core.workflow.model.dto.MemoryCompressOutputDTO;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.aimodel.prompt.PromptHelper;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.core.workflow.trace.WorkflowTraceSupport;
import com.oj.agent.core.trace.model.command.TraceItemFinishCommand;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.result.TraceItemTypeResult;
import com.oj.agent.core.trace.model.result.TraceStatusResult;
import com.oj.agent.core.trace.model.result.TraceTokenUsageResult;
import com.oj.agent.core.workflow.util.ChatResponseUtil;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MemoryCompressNode implements NodeAction {

    private static final String MEMORY_SUMMARY_PREFIX = "[MEMORY_SUMMARY]";
    private static final String NEW_QUESTION_COMPRESS_MODE = "NEW_QUESTION_COMPRESS";
    private static final String NON_NEW_QUESTION_MODE = "SLIDING_WINDOW_TRIM";
    private static final int NON_NEW_WINDOW_SIZE = 20;

    private final ChatClient chatClient;
    private final WorkflowTraceRecorder workflowTraceRecorder;
    private final WorkflowTraceSupport workflowTraceSupport;

    public MemoryCompressNode(AiModelRegistry aiModelRegistry,
                              WorkflowTraceRecorder workflowTraceRecorder,
                              WorkflowTraceSupport workflowTraceSupport) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        List<Message> messages = getMessages(state);
        IntentRecognitionOutputDTO intentOutput = StateUtil.getObjectValue(
                state,
                Constant.INTENT_RECOGNITION_NODE_OUTPUT,
                IntentRecognitionOutputDTO.class,
                (IntentRecognitionOutputDTO) null
        );
        UserIntentType intent = intentOutput == null ? null : intentOutput.getIntent();

        if (intent != UserIntentType.NEW_QUESTION && intent != UserIntentType.CHANGE_DIFFICULT) {
            return applySlidingWindowForNonNewQuestion(state, messages);
        }

        int beforeCount = messages.size();
        String prompt = PromptHelper.buildMemoryCompressPrompt(messages);
        Long traceItemId = startNewQuestionTrace(state, prompt, beforeCount, messages);
        TraceTokenUsageResult tokenUsage = null;

        try {
            ChatResponse chatResponse = chatClient.prompt().user(prompt).call().chatResponse();
            tokenUsage = ChatResponseUtil.extractTokenUsage(chatResponse);
            String summary = ChatResponseUtil.getText(chatResponse);
            if (summary == null || summary.isBlank()) {
                throw new IllegalStateException("LLM returned empty summary");
            }

            Message summaryMessage = buildSummaryMessage(summary);
            completeNewQuestionTrace(traceItemId, summaryMessage.getText(), tokenUsage);
            MemoryCompressOutputDTO output = buildOutput(
                    NEW_QUESTION_COMPRESS_MODE, beforeCount, 1, true, summaryMessage.getText(), 0);
            return Map.of(
                    Constant.MESSAGES, ReplaceAllWith.of(List.of(summaryMessage)),
                    Constant.MEMORY_COMPRESS_NODE_OUTPUT, output
            );
        } catch (Exception e) {
            log.warn("Memory compression failed, using fallback summary", e);
            failNewQuestionTrace(traceItemId, e, tokenUsage);

            String fallbackSummary = buildFallbackSummary(messages);
            Message summaryMessage = buildSummaryMessage(fallbackSummary);
            MemoryCompressOutputDTO output = buildOutput(
                    NEW_QUESTION_COMPRESS_MODE, beforeCount, 1, true, summaryMessage.getText(), 0);
            return Map.of(
                    Constant.MESSAGES, ReplaceAllWith.of(List.of(summaryMessage)),
                    Constant.MEMORY_COMPRESS_NODE_OUTPUT, output
            );
        }
    }

    private Map<String, Object> applySlidingWindowForNonNewQuestion(OverAllState state, List<Message> messages) {
        int beforeCount = messages.size();
        SlidingWindowResult windowResult = buildSlidingWindowResult(messages);
        Long traceItemId = null;

        try {
            traceItemId = startNonNewQuestionTrace(state, beforeCount, messages, windowResult.summaryMessage() != null);
            completeNonNewQuestionTrace(traceItemId, windowResult.trimmedMessages(), windowResult.finalMessages().size());
        } catch (Exception e) {
            log.warn("Failed to record non-new memory compress trace", e);
            failNonNewQuestionTrace(traceItemId, e);
        }

        String summaryText = windowResult.summaryMessage() == null ? "" : windowResult.summaryMessage().getText();
        MemoryCompressOutputDTO output = buildOutput(
                NON_NEW_QUESTION_MODE,
                beforeCount,
                windowResult.finalMessages().size(),
                windowResult.summaryMessage() != null,
                summaryText,
                windowResult.trimmedMessages().size()
        );

        return Map.of(
                Constant.MESSAGES, ReplaceAllWith.of(windowResult.finalMessages()),
                Constant.MEMORY_COMPRESS_NODE_OUTPUT, output
        );
    }

    private List<Message> getMessages(OverAllState state) {
        List<Object> rawMessages = StateUtil.getListValue(state, Constant.MESSAGES);
        if (rawMessages == null || rawMessages.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>(rawMessages.size());
        for (Object rawMessage : rawMessages) {
            if (rawMessage instanceof Message message) {
                messages.add(message);
            }
        }
        return messages;
    }

    private Long startNewQuestionTrace(OverAllState state, String prompt, int beforeCount, List<Message> messages) {
        try {
            return doStartNewQuestionTrace(state, prompt, beforeCount, messages);
        } catch (Exception e) {
            log.warn("Failed to start memory-compress trace, continue without trace", e);
            return null;
        }
    }

    private Long doStartNewQuestionTrace(OverAllState state, String prompt, int beforeCount, List<Message> messages) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null) {
            return null;
        }

        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        String firstMessage = messages.isEmpty() ? "" : safeText(messages.get(0));
        String lastMessage = messages.isEmpty() ? "" : safeText(messages.get(messages.size() - 1));
        String inputPayload = workflowTraceSupport.toLlmInputPayload(Map.of(
                "mode", UserIntentType.NEW_QUESTION.name(),
                "beforeCount", beforeCount,
                "firstMessage", firstMessage,
                "lastMessage", lastMessage,
                "prompt", prompt
        ));

        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.LLM)
                .itemKey("llm-1")
                .inputPayload(inputPayload)
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    private void completeNewQuestionTrace(Long traceItemId, String compressedMessage, TraceTokenUsageResult tokenUsage) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceItemId == null) {
            return;
        }

        String outputPayload = workflowTraceSupport.toLlmOutputPayload(Map.of(
                "compressedMessage", compressedMessage,
                "afterCount", 1
        ));

        workflowTraceRecorder.completeTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(outputPayload)
                .status(TraceStatusResult.SUCCESS)
                .tokenUsage(tokenUsage)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private void failNewQuestionTrace(Long traceItemId, Exception exception, TraceTokenUsageResult tokenUsage) {
        if (workflowTraceRecorder == null || traceItemId == null) {
            return;
        }
        String errorMessage = exception == null ? null : exception.getMessage();
        workflowTraceRecorder.failTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .errorMessage(errorMessage)
                .status(TraceStatusResult.FAILED)
                .tokenUsage(tokenUsage)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private Message buildSummaryMessage(String summary) {
        return new AssistantMessage(MEMORY_SUMMARY_PREFIX + summary);
    }

    private SlidingWindowResult buildSlidingWindowResult(List<Message> messages) {
        int latestSummaryIndex = findLatestSummaryIndex(messages);
        Message summaryMessage = latestSummaryIndex >= 0 ? messages.get(latestSummaryIndex) : null;

        List<Message> originalMessages = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            if (i != latestSummaryIndex) {
                originalMessages.add(messages.get(i));
            }
        }

        int keepFrom = Math.max(0, originalMessages.size() - NON_NEW_WINDOW_SIZE);
        List<Message> trimmedMessages = new ArrayList<>(originalMessages.subList(0, keepFrom));
        List<Message> keptOriginalMessages = new ArrayList<>(originalMessages.subList(keepFrom, originalMessages.size()));

        List<Message> finalMessages = new ArrayList<>();
        if (summaryMessage != null) {
            finalMessages.add(summaryMessage);
        }
        finalMessages.addAll(keptOriginalMessages);

        return new SlidingWindowResult(finalMessages, trimmedMessages, summaryMessage);
    }

    private int findLatestSummaryIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (isSummaryMessage(messages.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSummaryMessage(Message message) {
        return safeText(message).startsWith(MEMORY_SUMMARY_PREFIX);
    }

    private Long startNonNewQuestionTrace(OverAllState state,
                                          int beforeCount,
                                          List<Message> messages,
                                          boolean hasSummary) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null) {
            return null;
        }

        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        List<String> firstTwoMessages = messages.stream()
                .limit(2)
                .map(this::safeText)
                .toList();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("mode", NON_NEW_QUESTION_MODE);
        input.put("beforeCount", beforeCount);
        input.put("firstTwoMessages", firstTwoMessages);
        input.put("hasSummary", hasSummary);

        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.NODE)
                .itemKey("node-1")
                .inputPayload(workflowTraceSupport.toNodeInputPayload(input))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    private void completeNonNewQuestionTrace(Long traceItemId, List<Message> trimmedMessages, int afterCount) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceItemId == null) {
            return;
        }

        List<String> trimmedTexts = trimmedMessages.stream()
                .map(this::safeText)
                .toList();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("mode", NON_NEW_QUESTION_MODE);
        output.put("trimmedMessages", trimmedTexts);
        output.put("trimmedCount", trimmedTexts.size());
        output.put("afterCount", afterCount);

        workflowTraceRecorder.completeTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(workflowTraceSupport.toNodeOutputPayload(output))
                .status(TraceStatusResult.SUCCESS)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private void failNonNewQuestionTrace(Long traceItemId, Exception exception) {
        if (workflowTraceRecorder == null || traceItemId == null) {
            return;
        }

        String errorMessage = exception == null ? null : exception.getMessage();
        workflowTraceRecorder.failTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .errorMessage(errorMessage)
                .status(TraceStatusResult.FAILED)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private String buildFallbackSummary(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return " Conversation compressed with fallback: no prior messages.";
        }
        Message lastMessage = messages.get(messages.size() - 1);
        return " Conversation compressed with fallback. Latest message: " + safeText(lastMessage);
    }

    private MemoryCompressOutputDTO buildOutput(String mode,
                                                int beforeCount,
                                                int afterCount,
                                                boolean summaryGenerated,
                                                String summaryText,
                                                int trimmedCount) {
        MemoryCompressOutputDTO output = new MemoryCompressOutputDTO();
        output.setMode(mode);
        output.setBeforeCount(beforeCount);
        output.setAfterCount(afterCount);
        output.setSummaryGenerated(summaryGenerated);
        output.setTrimmedCount(trimmedCount);
        output.setSummaryPreview(buildSummaryPreview(summaryText));
        return output;
    }

    private String buildSummaryPreview(String summaryText) {
        if (summaryText == null || summaryText.isBlank()) {
            return "";
        }
        int previewLength = Math.min(120, summaryText.length());
        return summaryText.substring(0, previewLength);
    }

    private String safeText(Message message) {
        if (message == null || message.getText() == null) {
            return "";
        }
        return message.getText();
    }

    private record SlidingWindowResult(List<Message> finalMessages,
                                       List<Message> trimmedMessages,
                                       Message summaryMessage) {
    }
}
