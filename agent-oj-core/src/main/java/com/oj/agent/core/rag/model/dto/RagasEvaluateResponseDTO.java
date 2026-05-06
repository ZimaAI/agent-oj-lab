package com.oj.agent.core.rag.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class RagasEvaluateResponseDTO {

    private Boolean degraded;

    private List<RagasEvaluateResultDTO> results;
}
