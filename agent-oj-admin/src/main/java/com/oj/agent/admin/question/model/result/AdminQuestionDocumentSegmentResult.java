package com.oj.agent.admin.question.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminQuestionDocumentSegmentResult {

    private Long segmentId;

    private Long documentId;

    private Integer chunkOrder;

    private String chunkId;

    private String parentChunkId;

    private String status;

    private Integer skipEmbedding;

    private String hitkQuestion;

    private String text;

    private String metadata;

    private Boolean parentSegment;

    private Integer childSegmentCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
