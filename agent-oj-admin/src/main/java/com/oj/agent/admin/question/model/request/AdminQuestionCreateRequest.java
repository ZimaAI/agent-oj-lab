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

    @Valid
    @NotEmpty(message = "Standard case pool cannot be empty")
    @Size(max = 200, message = "Standard case pool size cannot exceed 200")
    private List<StandardCaseCreateRequest> standardCasePool;

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

        @Size(max = 128, message = "Template entry method name length cannot exceed 128")
        private String entryMethodName;

        @NotBlank(message = "Template starter code cannot be blank")
        @Size(max = 20000, message = "Template starter code length cannot exceed 20000")
        private String starterCode;

        @NotBlank(message = "Template reference answer cannot be blank")
        @Size(max = 20000, message = "Template reference answer length cannot exceed 20000")
        private String referenceAnswer;
    }

    @Data
    public static class StandardCaseCreateRequest {

        @NotBlank(message = "Case stdin cannot be blank")
        @Size(max = 20000, message = "Case stdin length cannot exceed 20000")
        private String stdin;

        @NotBlank(message = "Case expected stdout cannot be blank")
        @Size(max = 20000, message = "Case expected stdout length cannot exceed 20000")
        private String expectedStdout;

        @NotNull(message = "Case visibility cannot be null")
        private Boolean publicCase;

        @Size(max = 2000, message = "Case description length cannot exceed 2000")
        private String description;
    }
}
