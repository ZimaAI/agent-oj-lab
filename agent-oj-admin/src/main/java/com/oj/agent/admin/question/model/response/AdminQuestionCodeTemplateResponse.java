package com.oj.agent.admin.question.model.response;

import com.oj.agent.core.question.model.entity.AlgorithmCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionCodeTemplateResponse {

    private AlgorithmCode codeTemplate;
}
