package com.oj.agent.core.rag.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RagasEvaluateResultDTO {

    private Long id;

    private BigDecimal answerRelevancy;

    private BigDecimal faithfulness;

    private BigDecimal contextPrecision;

    private BigDecimal contextRecall;

    private BigDecimal overallScore;

    private String errorMessage;
}
