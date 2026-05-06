package com.oj.agent.core.question.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("algorithm_question_vector_sync_log")
public class AlgorithmQuestionVectorSyncLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private String syncStatus;

    private String errorCode;

    private String errorMessage;

    private String traceId;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
