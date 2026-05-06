package com.oj.agent.core.workflow.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class QuestionRewriteOutputDTO {

    @JsonProperty("rewrittenQuery")
    @JsonPropertyDescription("面向算法题检索的增强查询")
    private String rewrittenQuery;

    @JsonProperty("explicitFilters")
    @JsonPropertyDescription("用户明确提出的硬过滤条件")
    private QuestionRewriteFilterDTO explicitFilters;

    @JsonProperty("inferredPreferences")
    @JsonPropertyDescription("从上下文适度推断出的软偏好")
    private QuestionRewriteFilterDTO inferredPreferences;
}
