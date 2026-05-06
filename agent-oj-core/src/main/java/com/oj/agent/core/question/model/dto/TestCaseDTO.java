package com.oj.agent.core.question.model.dto;

import lombok.Data;
import java.util.Map;

@Data
public class TestCaseDTO {
    private Map<String, Object> input;
    private Object expectedOutput;
    private String description;
}
