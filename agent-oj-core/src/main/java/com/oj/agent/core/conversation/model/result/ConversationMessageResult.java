package com.oj.agent.core.conversation.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationMessageResult {

    private String sender;

    private String content;

    private String messageType;

    private Integer sequenceNo;

    private LocalDateTime createTime;

    private String resultType;

    private String resultSummary;
}
