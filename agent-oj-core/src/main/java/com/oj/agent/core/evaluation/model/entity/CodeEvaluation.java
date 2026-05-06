package com.oj.agent.core.evaluation.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("code_evaluation")
public class CodeEvaluation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long submissionId;

    private Long algorithmQuestionId;

    private Long conversationMessageId;

    private Integer correctnessScore;

    private Integer timeComplexityScore;

    private Integer spaceComplexityScore;

    private Integer overallScore;

    private String testResults;

    private String timeComplexityAnalysis;

    private String spaceComplexityAnalysis;

    private String codeQualityAnalysis;

    private String suggestions;

    private String summary;

    private String evaluateModelKey;

    private String conversationId;

    private String traceId;

    private String agentName;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
