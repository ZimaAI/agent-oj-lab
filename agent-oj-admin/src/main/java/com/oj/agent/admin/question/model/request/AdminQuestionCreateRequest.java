package com.oj.agent.admin.question.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdminQuestionCreateRequest {

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 200, message = "Title length cannot exceed 200")
    private String title;

    @NotBlank(message = "Description cannot be blank")
    @Size(max = 20000, message = "Description length cannot exceed 20000")
    private String description;

    @NotBlank(message = "Difficulty cannot be blank")
    @Pattern(regexp = "SIMPLE|MEDIUM|HARD", message = "Difficulty must be SIMPLE, MEDIUM or HARD")
    private String difficulty;

    @NotBlank(message = "Shared function name cannot be blank")
    @Size(max = 128, message = "Shared function name length cannot exceed 128")
    private String sharedFunctionName;

    @Size(max = 20000, message = "Shared code skeleton length cannot exceed 20000")
    private String sharedCodeSkeleton;

    @NotBlank(message = "Shared test cases cannot be blank")
    @Size(max = 100000, message = "Shared test cases length cannot exceed 100000")
    private String sharedTestCases;

    @Size(max = 20, message = "Tag count cannot exceed 20")
    private List<@NotNull(message = "Tag ID cannot be null") @Positive(message = "Tag ID must be greater than 0") Long> tagIds;

    @Size(max = 20, message = "Tag count cannot exceed 20")
    private List<@NotBlank(message = "Tag cannot be blank") @Size(max = 32, message = "Tag length cannot exceed 32") String> tags;

    @Valid
    @NotEmpty(message = "Code templates cannot be empty")
    @Size(min = 3, max = 3, message = "Code templates must contain exactly 3 items")
    private List<CodeTemplateCreateRequest> codeTemplates;

    @Data
    public static class CodeTemplateCreateRequest {

        @NotBlank(message = "Template language cannot be blank")
        @Size(max = 64, message = "Template language length cannot exceed 64")
        private String language;

        @NotBlank(message = "Template function name cannot be blank")
        @Size(max = 128, message = "Template function name length cannot exceed 128")
        private String functionName;

        @NotBlank(message = "Template code skeleton cannot be blank")
        @Size(max = 20000, message = "Template code skeleton length cannot exceed 20000")
        private String codeSkeleton;

        @NotBlank(message = "Template reference answer cannot be blank")
        @Size(max = 20000, message = "Template reference answer length cannot exceed 20000")
        private String referenceAnswer;
    }
}
