package com.oj.agent.admin.question.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdminQuestionUpdateRequest {

    @Size(max = 200, message = "Title length cannot exceed 200")
    private String title;

    @Size(max = 20000, message = "Description length cannot exceed 20000")
    private String description;

    @Pattern(regexp = "SIMPLE|MEDIUM|HARD", message = "难度只支持 SIMPLE、MEDIUM、HARD")
    private String difficulty;

    @Size(max = 128, message = "Shared function name length cannot exceed 128")
    private String sharedFunctionName;

    @Size(max = 20000, message = "Shared code skeleton length cannot exceed 20000")
    private String sharedCodeSkeleton;

    @Size(max = 100000, message = "Shared test cases length cannot exceed 100000")
    private String sharedTestCases;

    @Size(max = 20, message = "Tag count cannot exceed 20")
    private List<@Size(max = 32, message = "Tag length cannot exceed 32") String> tags;

    @Valid
    @Size(max = 20, message = "Template count cannot exceed 20")
    private List<CodeTemplateUpdate> codeTemplates;

    @Data
    public static class CodeTemplateUpdate {

        @NotBlank(message = "Template language cannot be blank")
        @Size(max = 64, message = "Template language length cannot exceed 64")
        private String language;

        @NotBlank(message = "Template function name cannot be blank")
        @Size(max = 128, message = "Template function name length cannot exceed 128")
        private String functionName;

        @Size(max = 20000, message = "Template code skeleton length cannot exceed 20000")
        private String codeSkeleton;

        @Size(max = 20000, message = "Template reference answer length cannot exceed 20000")
        private String referenceAnswer;
    }
}
