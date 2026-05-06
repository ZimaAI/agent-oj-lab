package com.oj.agent.admin.question.ragas.model.result;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminQuestionDocumentSegmentRagasResult {

    private Long id;

    private Long ragasId;

    private Long segmentId;

    private String question;

    private String standardAnswer;

    private String generatedAnswer;

    private BigDecimal contextPrecision;

    private BigDecimal contextRecall;

    private BigDecimal faithfulness;

    private BigDecimal answerRelevancy;

    private BigDecimal answerSimilarity;

    private BigDecimal answerCorrectness;

    private BigDecimal overallScore;

    private String status;

    private String metadata;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
