package com.oj.agent.core.rag.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class RagasEvaluateSampleDTO {

    private Long id;

    private String question;

    private String answer;

    private String groundTruth;

    private List<String> contexts;
}
