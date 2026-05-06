package com.oj.agent.core.workflow.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RagJudgeLlmOutputDTO {

    @JsonProperty("is_matched")
    @JsonPropertyDescription("是否找到完全符合用户需求的题目")
    private boolean matched;

    @JsonProperty("selected_question_id")
    @JsonPropertyDescription("选中的题目 ID，如果 isMatched 为 false 则为 null")
    private Long selectedQuestionId;

    @JsonProperty("reason")
    @JsonPropertyDescription("判断理由，用于日志调试")
    private String reason;
}
