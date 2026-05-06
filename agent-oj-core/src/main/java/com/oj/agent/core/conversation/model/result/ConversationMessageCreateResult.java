package com.oj.agent.core.conversation.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationMessageCreateResult {

    private Long id;

    private String conversationId;

    private Integer sequenceNo;

    private LocalDateTime createTime;
}
