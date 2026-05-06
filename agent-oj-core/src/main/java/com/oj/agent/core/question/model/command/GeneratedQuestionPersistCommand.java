package com.oj.agent.core.question.model.command;

import lombok.Data;

import java.util.List;

@Data
public class GeneratedQuestionPersistCommand {

    private Long userId;

    private String title;

    private String description;

    private String difficulty;

    private String sharedFunctionName;

    private String sharedCodeSkeleton;

    private String sharedTestCases;

    private List<String> tagNames;

    private String conversationId;

    private String traceId;

    private String agentName;

    private List<GeneratedQuestionLanguageCodeCommand> languageCodes;
}
