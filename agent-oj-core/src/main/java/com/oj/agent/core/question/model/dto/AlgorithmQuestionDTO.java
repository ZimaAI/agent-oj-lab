package com.oj.agent.core.question.model.dto;

import lombok.Data;

import java.util.List;

/**
 * Algorithm question DTO.
 */
@Data
public class AlgorithmQuestionDTO {
    private Long id;
    private String title;
    private String description;
    private String difficulty;
    private String type;
    private String sharedFunctionName;
    private String sharedCodeSkeleton;
    private String sharedTestCases;
    private List<AlgorithmCodeTemplateDTO> codeTemplates;
    private Double similarity;

    // Compatibility accessors for transitional call sites.
    @Deprecated
    public String getLanguage() {
        return null;
    }

    @Deprecated
    public void setLanguage(String language) {
        // no-op: question-level language is removed
    }

    @Deprecated
    public String getFunctionName() {
        return sharedFunctionName;
    }

    @Deprecated
    public void setFunctionName(String functionName) {
        this.sharedFunctionName = functionName;
    }

    @Deprecated
    public String getCodeSkeleton() {
        return sharedCodeSkeleton;
    }

    @Deprecated
    public void setCodeSkeleton(String codeSkeleton) {
        this.sharedCodeSkeleton = codeSkeleton;
    }

    @Deprecated
    public String getReferenceAnswer() {
        return null;
    }

    @Deprecated
    public void setReferenceAnswer(String referenceAnswer) {
        // no-op: moved to algorithm_code
    }

    @Deprecated
    public String getTestCases() {
        return sharedTestCases;
    }

    @Deprecated
    public void setTestCases(String testCases) {
        this.sharedTestCases = testCases;
    }
}
