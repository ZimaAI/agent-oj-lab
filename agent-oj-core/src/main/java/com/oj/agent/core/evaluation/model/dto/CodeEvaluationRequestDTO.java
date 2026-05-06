package com.oj.agent.core.evaluation.model.dto;

import lombok.Data;

@Data
public class CodeEvaluationRequestDTO {

    private String conversationId;

    private String message;

    private String code;

    private String language;
}
