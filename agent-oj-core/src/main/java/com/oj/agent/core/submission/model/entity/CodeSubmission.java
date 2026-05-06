package com.oj.agent.core.submission.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("code_submission")
public class CodeSubmission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long algorithmQuestionId;

    private String code;

    private String language;

    private String executeStatus;

    private Integer executeTimeMs;

    private String errorMessage;

    private String testResults;

    private Integer passCount;

    private Integer totalCount;

    private String conversationId;

    private Long conversationMessageId;

    private String traceId;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
