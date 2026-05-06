package com.oj.agent.core.aimodel.model.request;

import lombok.Data;

@Data
public class AiModelConfigListRequest {

    private String modelType;

    private String provider;

    private Integer enabled;

    private String keyword;
}
