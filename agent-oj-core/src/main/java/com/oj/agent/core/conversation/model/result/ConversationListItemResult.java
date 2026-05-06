package com.oj.agent.core.conversation.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationListItemResult {

    private String conversationId;

    private String title;

    private LocalDateTime lastMessageTime;

    private String sessionStatus;

    private Long currentQuestionId;

    private String latestMessagePreview;

    private LocalDateTime createTime;
}
