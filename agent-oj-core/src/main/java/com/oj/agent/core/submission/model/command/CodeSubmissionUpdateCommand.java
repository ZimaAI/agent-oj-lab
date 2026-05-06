package com.oj.agent.core.submission.model.command;

import lombok.Data;

@Data
public class CodeSubmissionUpdateCommand {

    private Long id;

    private String executeStatus;

    private Integer executeTimeMs;

    private String errorMessage;

    private String testResults;

    private Integer passCount;

    private Integer totalCount;
}
