package com.oj.agent.core.workflow.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

@Data
public class CodeQuestionLLMOutputDTO {
    private String title;
    private String description;
    @JsonPropertyDescription("难度：EASY/MEDIUM/HARD")
    private String difficulty;
    private String sharedFunctionName;
    private String sharedCodeSkeleton;
    private List<SharedTestCaseDTO> sharedTestCases;
    private List<String> tags;

}
