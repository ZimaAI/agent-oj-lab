package com.oj.agent.core.rag.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSegmentRagasTaskDetailItemResult {

    private Long ragasId;

    private Long segmentId;

    private Long questionId;

    private Long documentId;

    private String status;

    private String errorMessage;

    private BigDecimal answerRelevancy;

    private BigDecimal faithfulness;

    private BigDecimal contextPrecision;

    private BigDecimal contextRecall;

    private BigDecimal overallScore;

    private String ragasQuestion;

    private String standardAnswer;

    private String generatedAnswer;
}
