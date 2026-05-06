package com.oj.agent.core.conversation.model.command;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationTouchCommand {

    private String conversationId;

    private LocalDateTime lastMessageTime;
}
