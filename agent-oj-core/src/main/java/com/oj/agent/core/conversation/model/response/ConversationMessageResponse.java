package com.oj.agent.core.conversation.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationMessageResponse {

    private String sender;

    private String content;

    private String messageType;

    private Integer sequenceNo;

    private LocalDateTime createTime;

    private String resultType;

    private String resultSummary;
}
