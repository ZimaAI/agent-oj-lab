package com.oj.agent.core.rag.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class RAGJudgeOutputDTO {
    @JsonProperty("is_matched")
    @JsonPropertyDescription("是否找到完全符合用户需求的题目")
    private boolean isMatched;

    @JsonProperty("selected_question")
    @JsonPropertyDescription("选中的完整题目信息，如果 isMatched 为 false 则为 null")
    private AlgorithmQuestionResult selectedQuestion;
}
