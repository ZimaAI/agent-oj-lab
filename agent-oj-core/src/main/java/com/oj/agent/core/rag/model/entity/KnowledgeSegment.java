package com.oj.agent.core.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_segment")
public class KnowledgeSegment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String text;

    private String chunkId;

    private String metadata;

    private Long documentId;

    private Integer chunkOrder;

    private String embeddingId;

    private String status;

    private Integer skipEmbedding;

    private String hitkQuestion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer lockVersion;

    private Integer deleted;
}
