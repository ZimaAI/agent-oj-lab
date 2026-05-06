package com.oj.agent.core.workflow.model.result;

import com.oj.agent.core.evaluation.model.result.CodeEvaluationResult;
import lombok.Data;

@Data
public class CodeEvaluationNodeResult {
    /**
     * 节点是否完成了预期评审流程
     */
    private boolean success;

    /**
     * 结构化评审结果
     */
    private CodeEvaluationResult codeEvaluation;

    /**
     * 失败原因
     */
    private String errorMessage;
}
