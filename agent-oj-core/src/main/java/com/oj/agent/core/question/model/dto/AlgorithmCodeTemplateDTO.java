package com.oj.agent.core.question.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmCodeTemplateDTO {

    private String language;

    private String functionName;

    private String codeSkeleton;
}
