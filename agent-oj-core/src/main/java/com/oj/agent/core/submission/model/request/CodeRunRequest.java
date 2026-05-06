package com.oj.agent.core.submission.model.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CodeRunRequest {

    private String code;

    private Long algorithmQuestionId;

    private String language;

    private String functionName;

    private List<Map<String, Object>> testInputs;
}
