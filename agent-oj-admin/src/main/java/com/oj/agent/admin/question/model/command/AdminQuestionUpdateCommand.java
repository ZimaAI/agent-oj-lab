package com.oj.agent.admin.question.model.command;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class AdminQuestionUpdateCommand {

    private Long questionId;

    private String title;

    private String description;

    private String difficulty;

    private String sharedFunctionName;

    private String sharedCodeSkeleton;

    private String sharedTestCases;

    private List<String> tags;

    @Valid
    private List<CodeTemplateUpdateCommand> codeTemplates;

    @Data
    public static class CodeTemplateUpdateCommand {
        private String language;
        private String functionName;
        private String codeSkeleton;
        private String referenceAnswer;
    }
}
