package com.oj.agent.core.aimodel.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiModelConfigResponse {

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
