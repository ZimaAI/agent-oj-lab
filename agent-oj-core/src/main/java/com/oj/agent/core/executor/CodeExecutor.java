package com.oj.agent.core.executor;

import com.oj.agent.core.question.enums.Language;
import com.oj.agent.core.executor.model.ExecutionRecord;
import com.oj.agent.core.executor.model.GeneratedCode;

import java.util.Map;

public interface CodeExecutor {

    ExecutionRecord execute(GeneratedCode code, Map<String, Object> context);

    boolean supports(Language language);
}
