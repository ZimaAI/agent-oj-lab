package com.oj.agent.core.evaluation.service;

import com.oj.agent.core.evaluation.model.command.CodeEvaluationCreateCommand;
import com.oj.agent.core.evaluation.model.result.CodeEvaluationResult;

public interface CodeEvaluationService {

    CodeEvaluationResult createEvaluation(CodeEvaluationCreateCommand command);
}
