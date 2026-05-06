package com.oj.agent.core.question.model.command;

import lombok.Data;

@Data
public class GeneratedQuestionLanguageCodeCommand {

    private String language;

    private String functionName;

    private String codeSkeleton;

    private String referenceAnswer;

    private String generateModelKey;

    private String traceId;

    private String agentName;
}
