package com.oj.agent.admin.question.hitk.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminHitKTaskDetailResponse {

    private Long taskId;

    private Long questionId;

    private Long documentId;

    private Integer totalCount;

    private Integer hitCount;

    private Integer missCount;

    private BigDecimal hitRate;

    private String status;

    private String errorMessage;

    private String remark;

    private LocalDateTime createTime;

    private List<SegmentResultItemResponse> segmentResults;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentResultItemResponse {

        private Long segmentId;

        private String segmentText;

        @JsonProperty("hitkQuestion")
        private String hitKQuestion;

        private List<String> rewrittenQuestions;

        private List<RetrievedSegmentItemResponse> retrievedSegments;

        private Boolean hit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievedSegmentItemResponse {

        private Long segmentId;

        private Long documentId;

        private Integer chunkOrder;

        private String text;

        private Double rawSimilarity;

        private Double similarityScore;

        private Double rrfScore;

        private Double finalScore;
    }
}
