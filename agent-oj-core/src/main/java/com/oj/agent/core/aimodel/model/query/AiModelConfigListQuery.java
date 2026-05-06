package com.oj.agent.core.aimodel.model.query;

import lombok.Data;

@Data
public class AiModelConfigListQuery {

    private String modelType;

    private String provider;

    private Integer enabled;

    private String keyword;
}
