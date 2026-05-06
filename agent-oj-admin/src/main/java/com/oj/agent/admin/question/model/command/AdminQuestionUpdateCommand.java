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

    private List<StandardCaseUpdateCommand> standardCasePool;

    private List<String> tags;

    @Valid
    private List<CodeTemplateUpdateCommand> codeTemplates;

    @Data
    public static class CodeTemplateUpdateCommand {
        private String language;
        private String entryMethodName;
        private String starterCode;
        private String referenceAnswer;
    }

    @Data
    public static class StandardCaseUpdateCommand {
        private String stdin;
        private String expectedStdout;
        private Boolean publicCase;
        private String description;
    }
}
