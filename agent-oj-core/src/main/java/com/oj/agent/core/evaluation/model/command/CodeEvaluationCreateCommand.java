package com.oj.agent.core.evaluation.model.command;

import lombok.Data;

@Data
public class CodeEvaluationCreateCommand {

    private Long userId;

    private Long submissionId;

    private Long algorithmQuestionId;

    private Long conversationMessageId;

    private Integer correctnessScore;

    private Integer timeComplexityScore;

    private Integer spaceComplexityScore;

    private Integer overallScore;

    private String testResults;

    private String timeComplexityAnalysis;

    private String spaceComplexityAnalysis;

    private String codeQualityAnalysis;

    private String suggestions;

    private String summary;

    private String evaluateModelKey;

    private String conversationId;

    private String traceId;

    private String agentName;
}
