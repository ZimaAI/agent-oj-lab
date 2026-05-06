package com.oj.agent.core.question.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmCodeTemplateResult {

    private String language;

    private String functionName;

    private String codeSkeleton;
}
