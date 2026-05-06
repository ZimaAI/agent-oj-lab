package com.oj.agent.core.rag.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SegmentRagasQaOutputDTO {

    @JsonProperty("question")
    private String question;

    @JsonProperty("standardAnswer")
    private String standardAnswer;
}
