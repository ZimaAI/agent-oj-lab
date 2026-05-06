package com.oj.agent.core.aimodel.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiModelConfigUpdateRequest {

    @NotBlank(message = "modelKey must not be blank")
    private String modelKey;

    @NotBlank(message = "modelName must not be blank")
    private String modelName;

    @NotBlank(message = "modelType must not be blank")
    private String modelType;

    @NotBlank(message = "provider must not be blank")
    private String provider;

    @NotBlank(message = "baseUrl must not be blank")
    private String baseUrl;

    @NotBlank(message = "apiKey must not be blank")
    private String apiKey;

    @NotNull(message = "enabled must not be null")
    private Integer enabled;

    private String configJson;

    private String remark;
}
