package com.oj.agent.admin.question.model.command;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class AdminQuestionCreateCommand {

    private String title;

    private String description;

    private String difficulty;

    private List<StandardCaseCreateCommand> standardCasePool;

    private List<Long> tagIds;

    private List<String> tags;

    @Valid
    private List<CodeTemplateCreateCommand> codeTemplates;

    @Data
    public static class CodeTemplateCreateCommand {
        private String language;
        private String entryMethodName;
        private String starterCode;
        private String referenceAnswer;
    }

    @Data
    public static class StandardCaseCreateCommand {
        private String stdin;
        private String expectedStdout;
        private Boolean publicCase;
        private String description;
    }
}
