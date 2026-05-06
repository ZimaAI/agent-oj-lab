package com.oj.agent.core.trace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trace")
public class Trace {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String traceId;

    private String conversationId;

    private Long userId;

    private String requestMessage;

    private Long currentAlgorithmQuestionId;

    private String currentAlgorithmQuestionSnapshot;

    private String status;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
