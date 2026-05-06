package com.oj.agent.core.rag.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_segment_ragas")
public class KnowledgeSegmentRagas {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long documentId;

    private Long segmentId;

    private String ragasQuestion;

    private String standardAnswer;

    private String generatedAnswer;

    private String retrievedSegmentIdsJson;

    private String retrievedContextsJson;

    private String rewrittenQuestionsJson;

    private BigDecimal contextPrecision;

    private BigDecimal contextRecall;

    private BigDecimal faithfulness;

    private BigDecimal answerRelevancy;

    private BigDecimal answerSimilarity;

    private BigDecimal answerCorrectness;

    private BigDecimal overallScore;

    private String status;

    private String metadata;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
