package com.oj.agent.core.workflow.persistence.impl;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.evaluation.converter.CodeEvaluationConverter;
import com.oj.agent.core.evaluation.model.result.CodeEvaluationResult;
import com.oj.agent.core.evaluation.service.CodeEvaluationService;
import com.oj.agent.core.submission.model.command.CodeSubmissionUpdateCommand;
import com.oj.agent.core.submission.model.result.CodeSubmissionResult;
import com.oj.agent.core.submission.service.CodeSubmissionService;
import com.oj.agent.core.workflow.model.result.CodeEvaluationNodeResult;
import com.oj.agent.core.workflow.persistence.WorkflowPersistenceStrategy;
import com.oj.agent.core.workflow.persistence.context.WorkflowPersistenceContext;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodeEvaluationPersistenceStrategy implements WorkflowPersistenceStrategy {

    private static final String SUBMISSION_STATUS_SUCCESS = "SUCCESS";
    private static final String SUBMISSION_STATUS_FAILED = "FAILED";

    private final CodeEvaluationService codeEvaluationService;
    private final CodeSubmissionService codeSubmissionService;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    public void persist(OverAllState state, WorkflowPersistenceContext context) {
        CodeSubmissionResult submission = StateUtil.getOptionalObjectValueOrNull(
                state,
                Constant.CURRENT_CODE_SUBMISSION,
                CodeSubmissionResult.class
        );
        CodeEvaluationNodeResult output = StateUtil.getOptionalObjectValueOrNull(
                state,
                Constant.CODE_EVALUATION_NODE_OUTPUT,
                CodeEvaluationNodeResult.class
        );
        if (submission == null || output == null) {
            log.debug("Skip code evaluation persistence: submission or output missing");
            return;
        }

        CodeSubmissionUpdateCommand submissionUpdate = new CodeSubmissionUpdateCommand();
        submissionUpdate.setId(submission.getId());

        if (output.isSuccess() && output.getCodeEvaluation() != null) {
            CodeEvaluationResult evaluation = output.getCodeEvaluation();
            codeEvaluationService.createEvaluation(CodeEvaluationConverter.toCreateCommand(evaluation));

            submissionUpdate.setExecuteStatus(SUBMISSION_STATUS_SUCCESS);
            submissionUpdate.setTestResults(evaluation.getTestResults());
            submissionUpdate.setErrorMessage(null);
            backfillPassAndTotalCount(submissionUpdate, evaluation.getTestResults());
        } else {
            submissionUpdate.setExecuteStatus(SUBMISSION_STATUS_FAILED);
            submissionUpdate.setErrorMessage(output.getErrorMessage());
            if (output.getCodeEvaluation() != null && output.getCodeEvaluation().getTestResults() != null) {
                submissionUpdate.setTestResults(output.getCodeEvaluation().getTestResults());
                backfillPassAndTotalCount(submissionUpdate, output.getCodeEvaluation().getTestResults());
            }
        }

        codeSubmissionService.updateSubmission(submissionUpdate);
    }

    // 从执行结果 JSON 中尽量回填通过数与总数，解析失败时保持静默跳过。
    private void backfillPassAndTotalCount(CodeSubmissionUpdateCommand submissionUpdate, String testResults) {
        if (!StringUtils.hasText(testResults)) {
            return;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(testResults);
            JsonNode resultsNode = rootNode.get("results");
            if (resultsNode == null || !resultsNode.isArray()) {
                return;
            }
            submissionUpdate.setTotalCount(resultsNode.size());
            int passCount = 0;
            for (JsonNode resultNode : resultsNode) {
                if (resultNode.path("passed").asBoolean(false)) {
                    passCount++;
                }
            }
            submissionUpdate.setPassCount(passCount);
        } catch (Exception e) {
            log.debug("Skip count backfill because testResults is not executable result JSON", e);
        }
    }

    @Override
    public int getOrder() {
        return 25;
    }
}
