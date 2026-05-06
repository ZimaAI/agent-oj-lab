package com.oj.agent.core.conversation.model.command;

import lombok.Data;

@Data
public class ConversationTitleBackfillCommand {

    private String conversationId;

    private Integer requestSequenceNo;

    private String title;
}
