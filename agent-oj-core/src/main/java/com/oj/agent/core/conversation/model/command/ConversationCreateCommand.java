package com.oj.agent.core.conversation.model.command;

import lombok.Data;

@Data
public class ConversationCreateCommand {

    private String conversationId;

    private Long currentQuestionId;
}
