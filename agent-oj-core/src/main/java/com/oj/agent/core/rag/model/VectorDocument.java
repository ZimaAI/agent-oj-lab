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
public class VectorDocument {

    private String id;
    private Long questionId;
    private Long segmentId;
    private Long documentId;
    private String chunkId;
    private String title;
    private String content;
    private String difficulty;
    private String language;
    private List<Float> embedding;
    private boolean deleted;
}
