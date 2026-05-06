package com.oj.agent.core.conversation.model.query;

import lombok.Data;

@Data
public class ConversationMessageListQuery {

    private String conversationId;

    private long current = 1;

    private long pageSize = 20;
}
