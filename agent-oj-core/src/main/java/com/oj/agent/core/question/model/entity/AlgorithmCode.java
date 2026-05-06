package com.oj.agent.core.question.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("algorithm_code")
public class AlgorithmCode {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private String language;

    private String functionName;

    private String codeSkeleton;

    private String referenceAnswer;

    private String generateModelKey;

    private String traceId;

    private String agentName;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
