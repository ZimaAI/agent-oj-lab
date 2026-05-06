package com.oj.agent.core.submission.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CodeSubmissionResult {

    private Long id;

    private Long userId;

    private Long algorithmQuestionId;

    private String questionTitle;

    private String code;

    private String language;

    private String executeStatus;

    private Integer executeTimeMs;

    private String errorMessage;

    private String testResults;

    private Integer passCount;

    private Integer totalCount;

    private String codeEvaluation;

    private String conversationId;

    private Long conversationMessageId;

    private String traceId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
