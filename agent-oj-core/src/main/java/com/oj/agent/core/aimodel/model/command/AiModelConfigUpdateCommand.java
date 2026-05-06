package com.oj.agent.core.aimodel.model.command;

import lombok.Data;

@Data
public class AiModelConfigUpdateCommand {

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
}
