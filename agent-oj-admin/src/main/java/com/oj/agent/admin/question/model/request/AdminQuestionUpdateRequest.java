package com.oj.agent.admin.question.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @Valid
    @Size(max = 200, message = "Standard case pool size cannot exceed 200")
    private List<StandardCaseUpdate> standardCasePool;

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

        @Size(max = 128, message = "Template entry method name length cannot exceed 128")
        private String entryMethodName;

        @Size(max = 20000, message = "Template starter code length cannot exceed 20000")
        private String starterCode;

        @Size(max = 20000, message = "Template reference answer length cannot exceed 20000")
        private String referenceAnswer;
    }

    @Data
    public static class StandardCaseUpdate {

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
