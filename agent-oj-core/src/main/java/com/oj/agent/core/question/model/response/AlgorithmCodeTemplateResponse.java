package com.oj.agent.core.question.model.response;

import lombok.Data;

@Data
public class AlgorithmCodeTemplateResponse {

    private String language;

    private String functionName;

    private String codeSkeleton;
}
