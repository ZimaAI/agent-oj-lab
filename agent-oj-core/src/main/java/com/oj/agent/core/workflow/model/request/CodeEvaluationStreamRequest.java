package com.oj.agent.core.workflow.model.request;

import lombok.Data;

@Data
public class CodeEvaluationStreamRequest {

    private String conversationId;

    private String message;

    private String code;

    private String language;
}
