package com.oj.agent.core.workflow.model.request;

import lombok.Data;

@Data
public class UserMessageRequest {

    private String conversationId;

    private String message;
}
