package com.oj.agent.core.conversation.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationCreateResponse {

    private String conversationId;

    private String title;

    private LocalDateTime lastMessageTime;

    private String sessionStatus;

    private Long currentQuestionId;

    private LocalDateTime createTime;
}
