package com.oj.agent.core.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(value = "doc_id", type = IdType.AUTO)
    private Long docId;

    private String docTitle;

    private String uploadUser;

    private String docUrl;

    private String convertedDocUrl;

    private LocalDate expireDate;

    private String status;

    private String accessibleBy;

    private String description;

    private String knowledgeBaseType;

    private String extension;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer lockVersion;

    private Integer deleted;
}
