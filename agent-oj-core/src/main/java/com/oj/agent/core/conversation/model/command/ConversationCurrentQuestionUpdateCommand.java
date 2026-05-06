package com.oj.agent.core.conversation.model.command;

import lombok.Data;

@Data
public class ConversationCurrentQuestionUpdateCommand {

    private String conversationId;

    private Integer requestSequenceNo;

    private Long currentQuestionId;
}
