package com.oj.agent.core.submission.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CodeSubmissionDetailResponse {

    private Long id;

    private Long algorithmQuestionId;

    private String questionTitle;

    private String code;

    private String language;

    private String testResults;

    private Integer passCount;

    private Integer totalCount;

    private String codeEvaluation;

    private LocalDateTime createTime;
}
