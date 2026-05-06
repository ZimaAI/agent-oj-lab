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
public class AnswerRAGOutputDTO {

    @JsonProperty("segments")
    @JsonPropertyDescription("答疑场景命中的知识分段列表")
    private List<AnswerRAGSegmentDTO> segments;
}
