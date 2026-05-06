package com.oj.agent.core.workflow.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerRewriteOutputDTO {

    @JsonProperty("rewrittenQueries")
    @JsonPropertyDescription("将用户答疑问题改写后的子问题列表")
    private List<String> rewrittenQueries;
}

