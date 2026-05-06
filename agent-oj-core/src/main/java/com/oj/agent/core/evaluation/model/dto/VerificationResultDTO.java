package com.oj.agent.core.evaluation.model.dto;

import lombok.Data;

@Data
public class VerificationResultDTO {
    private int testCaseIndex;
    private boolean passed;
    private Object actualOutput;
    private Object expectedOutput;
    private String error;
}
