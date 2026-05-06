package com.oj.agent.core.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSegmentHitkTaskDetailDTO {

    private Long segmentId;

    private String hitkQuestion;

    private List<String> rewrittenQuestions;

    private List<Long> retrievedSegmentIds;

    private List<RetrievedSegmentScoreDTO> retrievedSegments;

    private Boolean hit;

    // 兼容旧调用方只记录召回分段 ID 的明细构造方式。
    public KnowledgeSegmentHitkTaskDetailDTO(Long segmentId,
                                             String hitkQuestion,
                                             List<String> rewrittenQuestions,
                                             List<Long> retrievedSegmentIds,
                                             Boolean hit) {
        this.segmentId = segmentId;
        this.hitkQuestion = hitkQuestion;
        this.rewrittenQuestions = rewrittenQuestions;
        this.retrievedSegmentIds = retrievedSegmentIds;
        this.hit = hit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievedSegmentScoreDTO {

        private Long segmentId;

        private Double rawSimilarity;

        private Double similarityScore;

        private Double rrfScore;

        private Double finalScore;
    }
}
