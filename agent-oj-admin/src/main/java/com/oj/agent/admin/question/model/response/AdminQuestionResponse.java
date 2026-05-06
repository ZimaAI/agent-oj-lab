package com.oj.agent.admin.question.model.response;

import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionResponse {

    private AlgorithmQuestion question;
}
