package com.oj.agent.core.submission.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CodeSubmissionListItemResponse {

    private Long id;

    private Long algorithmQuestionId;

    private String questionTitle;

    private Integer passCount;

    private Integer totalCount;

    private String language;

    private LocalDateTime createTime;
}
