package com.oj.agent.core.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchRequest {

    private List<Float> queryVector;
    private Integer topK;
    private Double scoreThreshold;
    private String difficultyFilter;
    private String languageFilter;
    private String preferredDifficulty;
    private String preferredLanguage;
    private Double preferenceWeight;
}

