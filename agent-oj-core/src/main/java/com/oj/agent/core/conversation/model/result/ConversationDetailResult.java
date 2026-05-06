package com.oj.agent.core.conversation.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationDetailResult {

    private String conversationId;

    private String title;

    private Long currentQuestionId;

    private String sessionStatus;

    private LocalDateTime lastMessageTime;

    private LocalDateTime createTime;

    private String latestMessagePreview;
}
