package com.oj.agent.core.rag.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSegmentRagasTaskCreateCommand {

    private Long questionId;

    private Long documentId;

    private Integer totalCount;

    private Integer successCount;

    private Integer failureCount;

    private BigDecimal averageAnswerRelevancy;

    private BigDecimal averageFaithfulness;

    private BigDecimal averageContextPrecision;

    private BigDecimal averageContextRecall;

    private String status;

    private String errorMessage;

    private List<DetailItemCommand> details;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailItemCommand {

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
}
