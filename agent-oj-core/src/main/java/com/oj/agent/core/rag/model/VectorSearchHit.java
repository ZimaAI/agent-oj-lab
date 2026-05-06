package com.oj.agent.core.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchHit {

    private String documentId;
    private Long questionId;
    private String title;
    private String difficulty;
    private String language;
    private Double similarity;
    private Double finalScore;
}

