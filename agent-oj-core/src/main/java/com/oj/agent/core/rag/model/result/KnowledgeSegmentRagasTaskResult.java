package com.oj.agent.core.rag.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSegmentRagasTaskResult {

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

    private List<KnowledgeSegmentRagasTaskDetailItemResult> details;
}
