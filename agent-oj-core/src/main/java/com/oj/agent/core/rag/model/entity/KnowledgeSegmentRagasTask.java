package com.oj.agent.core.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_segment_ragas_task")
public class KnowledgeSegmentRagasTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long documentId;

    private Integer totalCount;

    private Integer successCount;

    private Integer failureCount;

    private BigDecimal averageAnswerRelevancy;

    private BigDecimal averageFaithfulness;

    private BigDecimal averageContextPrecision;

    private BigDecimal averageContextRecall;

    private String status;

    private String errorMessage;

    private String detailsJson;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
