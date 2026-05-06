package com.oj.agent.core.submission.model.command;

import lombok.Data;

@Data
public class CodeSubmissionCreateCommand {

    private Long userId;

    private Long algorithmQuestionId;

    private String code;

    private String language;

    private String conversationId;

    private Long conversationMessageId;

    private String traceId;
}
