package com.oj.agent.admin.question.ragas.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRagasTaskDetailResponse {

    private Long taskId;

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

    private LocalDateTime createTime;

    private List<DetailItemResponse> details;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailItemResponse {

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
