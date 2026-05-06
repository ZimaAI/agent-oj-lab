package com.oj.agent.core.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResult {

    @Builder.Default
    private List<VectorSearchHit> hits = Collections.emptyList();
}

