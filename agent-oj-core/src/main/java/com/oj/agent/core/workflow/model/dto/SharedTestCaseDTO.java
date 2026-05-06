package com.oj.agent.core.workflow.model.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SharedTestCaseDTO {
    private Map<String, Object> input;
    private Object expectedOutput;
    private String description;
}
