package com.oj.agent.core.conversation.model.command;

import lombok.Data;

@Data
public class ConversationMessageCreateCommand {

    private String conversationId;

    private String sender;

    private String messageType;

    private String content;

    private Long questionId;

    private Long submissionId;

    private Long evaluationId;

    private String traceId;

    private String resultType;

    private String resultData;
}
