package com.oj.agent.core.workflow.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerRagSegmentResult {

    private Long id;

    private String text;

    private String chunkId;

    private Double rawSimilarity;

    private Double similarityScore;

    private Double rrfScore;

    private Double finalScore;
}
