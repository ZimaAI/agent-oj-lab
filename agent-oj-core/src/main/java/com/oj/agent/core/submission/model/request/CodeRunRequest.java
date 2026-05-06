package com.oj.agent.core.submission.model.request;

import lombok.Data;

@Data
public class CodeRunRequest {

    private String code;

    private Long algorithmQuestionId;

    private String language;
}
