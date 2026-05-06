package com.oj.agent.admin.question.model.result;

import com.oj.agent.core.question.model.entity.AlgorithmCode;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionDualDetailResult {

    private AlgorithmQuestion mysqlQuestion;

    private List<AlgorithmCode> mysqlCodeTemplates;

    private AdminQuestionVectorProjectionResult vectorProjection;
}
