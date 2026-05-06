package com.oj.agent.admin.question.model.response;

import com.oj.agent.core.question.model.entity.AlgorithmCode;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionDualDetailResponse {

    private AlgorithmQuestion mysqlQuestion;

    private List<AlgorithmCode> mysqlCodeTemplates;

    private AdminQuestionVectorProjectionResponse vectorProjection;
}
