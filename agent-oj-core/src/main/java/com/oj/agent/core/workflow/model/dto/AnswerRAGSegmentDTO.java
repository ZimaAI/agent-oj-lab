package com.oj.agent.core.workflow.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerRAGSegmentDTO {

    private Long id;

    private String text;

    private String chunkId;

    private Long documentId;

    private Integer chunkOrder;

    private Double rawSimilarity;

    private Double similarityScore;

    private Double rrfScore;

    private Double finalScore;
}
