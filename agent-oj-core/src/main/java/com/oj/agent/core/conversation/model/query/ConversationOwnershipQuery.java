package com.oj.agent.core.conversation.model.query;

import lombok.Data;

@Data
public class ConversationOwnershipQuery {

    private String conversationId;

    private Long userId;
}
