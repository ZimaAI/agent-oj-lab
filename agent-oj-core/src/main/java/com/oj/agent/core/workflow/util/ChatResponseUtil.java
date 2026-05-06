package com.oj.agent.core.workflow.util;

import com.oj.agent.core.trace.model.result.TraceTokenUsageResult;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

public final class ChatResponseUtil {

    private ChatResponseUtil() {
    }

    public static ChatResponse createResponse(String statusMessage) {
        return createPureResponse(statusMessage + "\n");
    }

    public static ChatResponse createPureResponse(String message) {
        AssistantMessage assistantMessage = new AssistantMessage(message);
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }

    public static ChatResponse createPureResponseWithUsage(String message, TraceTokenUsageResult tokenUsage) {
        AssistantMessage assistantMessage = new AssistantMessage(message);
        Generation generation = new Generation(assistantMessage);
        if (tokenUsage == null
                || (tokenUsage.promptTokens() == null
                && tokenUsage.completionTokens() == null
                && tokenUsage.totalTokens() == null)) {
            return new ChatResponse(List.of(generation));
        }
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(tokenUsage.promptTokens(), tokenUsage.completionTokens(), tokenUsage.totalTokens()))
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    public static String getText(ChatResponse chatResponse) {
        Generation result = chatResponse.getResult();
        if (result == null) {
            return "";
        }
        AssistantMessage output = result.getOutput();
        if (output == null) {
            return "";
        }
        return output.getText() == null ? "" : output.getText();
    }

    public static TraceTokenUsageResult extractTokenUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return new TraceTokenUsageResult(null, null, null);
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return new TraceTokenUsageResult(null, null, null);
        }
        return new TraceTokenUsageResult(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }
}
