package com.oj.agent.core.workflow.model.command;

import lombok.Data;

@Data
public class WorkflowStartCommand {

    private boolean codeEvaluation;

    private String conversationId;

    private String message;

    private String code;

    private String language;
}
