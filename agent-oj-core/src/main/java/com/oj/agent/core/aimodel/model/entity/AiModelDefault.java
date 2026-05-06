package com.oj.agent.core.aimodel.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model_default")
public class AiModelDefault {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelType;

    private String modelKey;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
