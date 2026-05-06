package com.oj.agent.core.evaluation.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CodeEvaluationResult {

    private Long id;

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
