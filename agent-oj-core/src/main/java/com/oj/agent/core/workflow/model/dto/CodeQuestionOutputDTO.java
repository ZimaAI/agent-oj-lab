package com.oj.agent.core.workflow.model.dto;

import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import lombok.Data;

@Data
public class CodeQuestionOutputDTO {
    private boolean success;
    private AlgorithmQuestionResult question;
    private String errorMessage;
}
