package com.oj.agent.core.workflow.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

@Data
public class QuestionRewriteFilterDTO {

    @JsonProperty("difficulty")
    @JsonPropertyDescription("题目难度")
    private String difficulty;

    @JsonProperty("language")
    @JsonPropertyDescription("编程语言")
    private String language;

    @JsonProperty("questionType")
    @JsonPropertyDescription("题目类型")
    private String questionType;

    @JsonProperty("tags")
    @JsonPropertyDescription("题目标签")
    private List<String> tags;

    @JsonProperty("keywords")
    @JsonPropertyDescription("检索关键词")
    private List<String> keywords;
}
