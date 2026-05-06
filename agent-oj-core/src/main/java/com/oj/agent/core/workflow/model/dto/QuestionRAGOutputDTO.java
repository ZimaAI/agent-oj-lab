package com.oj.agent.core.workflow.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;


@Data
@AllArgsConstructor
public class QuestionRAGOutputDTO {
    @JsonProperty("candidates")
    @JsonPropertyDescription("候选算法题列表（相似度 > 0.7，最多 5 道）")
    private List<AlgorithmQuestionResult> candidates;
}
