package com.oj.agent.core.question.model.response;

import lombok.Data;

@Data
public class StandardCaseResponse {

    private String stdin;

    private String expectedStdout;

    private boolean publicCase;

    private String description;
}
