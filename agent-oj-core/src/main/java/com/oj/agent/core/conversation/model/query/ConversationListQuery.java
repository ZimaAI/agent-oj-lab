package com.oj.agent.core.conversation.model.query;

import lombok.Data;

@Data
public class ConversationListQuery {

    private long current = 1;

    private long pageSize = 10;
}
