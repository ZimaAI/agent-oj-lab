package com.oj.agent.core.aimodel.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model_config")
public class AiModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelKey;

    private String modelName;

    private String modelType;

    private String provider;

    private String baseUrl;

    private String apiKey;

    private Integer enabled;

    private String configJson;

    private String remark;

    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
