package com.oj.agent.admin.question.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminKnowledgeSegmentResult {

    private Long segmentId;

    private Long questionId;

    private String questionTitle;

    private Long documentId;

    private String documentTitle;

    private Integer chunkOrder;

    private String chunkId;

    private String status;

    private Integer skipEmbedding;

    private String hitkQuestion;

    private String text;

    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
