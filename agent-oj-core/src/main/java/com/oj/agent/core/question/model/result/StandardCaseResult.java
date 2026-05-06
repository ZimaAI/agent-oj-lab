package com.oj.agent.core.question.model.result;

import lombok.Data;

@Data
public class StandardCaseResult {

    private String stdin;

    private String expectedStdout;

    private boolean publicCase;

    private String description;
}
