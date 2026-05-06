package com.oj.agent.core.conversation.model.request;

import lombok.Data;

@Data
public class ConversationListRequest {

    private long current = 1;

    private long pageSize = 10;
}
