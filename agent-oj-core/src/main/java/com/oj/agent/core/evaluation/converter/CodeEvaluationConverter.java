package com.oj.agent.core.evaluation.converter;

import com.oj.agent.core.evaluation.model.command.CodeEvaluationCreateCommand;
import com.oj.agent.core.evaluation.model.dto.CodeEvaluationLLMOutputDTO;
import com.oj.agent.core.evaluation.model.entity.CodeEvaluation;
import com.oj.agent.core.evaluation.model.result.CodeEvaluationResult;
import com.oj.agent.core.submission.model.result.CodeSubmissionResult;

import java.time.LocalDateTime;

public final class CodeEvaluationConverter {

    private CodeEvaluationConverter() {
    }

    public static CodeEvaluation toEntity(CodeEvaluationCreateCommand command, LocalDateTime now) {
        if (command == null) {
            return null;
        }
        CodeEvaluation evaluation = new CodeEvaluation();
        evaluation.setUserId(command.getUserId());
        evaluation.setSubmissionId(command.getSubmissionId());
        evaluation.setAlgorithmQuestionId(command.getAlgorithmQuestionId());
        evaluation.setConversationMessageId(command.getConversationMessageId());
        evaluation.setCorrectnessScore(command.getCorrectnessScore());
        evaluation.setTimeComplexityScore(command.getTimeComplexityScore());
        evaluation.setSpaceComplexityScore(command.getSpaceComplexityScore());
        evaluation.setOverallScore(command.getOverallScore());
        evaluation.setTestResults(command.getTestResults());
        evaluation.setTimeComplexityAnalysis(command.getTimeComplexityAnalysis());
        evaluation.setSpaceComplexityAnalysis(command.getSpaceComplexityAnalysis());
        evaluation.setCodeQualityAnalysis(command.getCodeQualityAnalysis());
        evaluation.setSuggestions(command.getSuggestions());
        evaluation.setSummary(command.getSummary());
        evaluation.setEvaluateModelKey(command.getEvaluateModelKey());
        evaluation.setConversationId(command.getConversationId());
        evaluation.setTraceId(command.getTraceId());
        evaluation.setAgentName(command.getAgentName());
        evaluation.setIsDelete(0);
        evaluation.setCreateTime(now);
        evaluation.setUpdateTime(now);
        return evaluation;
    }

    public static CodeEvaluationResult toResult(CodeEvaluation evaluation) {
        if (evaluation == null) {
            return null;
        }
        CodeEvaluationResult result = new CodeEvaluationResult();
        result.setId(evaluation.getId());
        result.setUserId(evaluation.getUserId());
        result.setSubmissionId(evaluation.getSubmissionId());
        result.setAlgorithmQuestionId(evaluation.getAlgorithmQuestionId());
        result.setConversationMessageId(evaluation.getConversationMessageId());
        result.setCorrectnessScore(evaluation.getCorrectnessScore());
        result.setTimeComplexityScore(evaluation.getTimeComplexityScore());
        result.setSpaceComplexityScore(evaluation.getSpaceComplexityScore());
        result.setOverallScore(evaluation.getOverallScore());
        result.setTestResults(evaluation.getTestResults());
        result.setTimeComplexityAnalysis(evaluation.getTimeComplexityAnalysis());
        result.setSpaceComplexityAnalysis(evaluation.getSpaceComplexityAnalysis());
        result.setCodeQualityAnalysis(evaluation.getCodeQualityAnalysis());
        result.setSuggestions(evaluation.getSuggestions());
        result.setSummary(evaluation.getSummary());
        result.setEvaluateModelKey(evaluation.getEvaluateModelKey());
        result.setConversationId(evaluation.getConversationId());
        result.setTraceId(evaluation.getTraceId());
        result.setAgentName(evaluation.getAgentName());
        result.setCreateTime(evaluation.getCreateTime());
        result.setUpdateTime(evaluation.getUpdateTime());
        return result;
    }

    public static CodeEvaluationResult toResult(CodeEvaluationLLMOutputDTO llmOutput,
                                                CodeSubmissionResult submission,
                                                String testResults,
                                                String agentName) {
        if (llmOutput == null) {
            return null;
        }
        CodeEvaluationResult result = new CodeEvaluationResult();
        if (submission != null) {
            result.setUserId(submission.getUserId());
            result.setSubmissionId(submission.getId());
            result.setAlgorithmQuestionId(submission.getAlgorithmQuestionId());
            result.setConversationMessageId(submission.getConversationMessageId());
            result.setConversationId(submission.getConversationId());
            result.setTraceId(submission.getTraceId());
        }
        result.setCorrectnessScore(llmOutput.getCorrectnessScore());
        result.setTimeComplexityScore(llmOutput.getTimeComplexityScore());
        result.setSpaceComplexityScore(llmOutput.getSpaceComplexityScore());
        result.setOverallScore(llmOutput.getOverallScore());
        result.setTestResults(testResults);
        result.setTimeComplexityAnalysis(llmOutput.getTimeComplexityAnalysis());
        result.setSpaceComplexityAnalysis(llmOutput.getSpaceComplexityAnalysis());
        result.setCodeQualityAnalysis(llmOutput.getCodeQualityAnalysis());
        result.setSuggestions(llmOutput.getSuggestions());
        result.setSummary(llmOutput.getSummary());
        result.setAgentName(agentName);
        return result;
    }

    public static CodeEvaluationCreateCommand toCreateCommand(CodeEvaluationResult result) {
        if (result == null) {
            return null;
        }
        CodeEvaluationCreateCommand command = new CodeEvaluationCreateCommand();
        command.setUserId(result.getUserId());
        command.setSubmissionId(result.getSubmissionId());
        command.setAlgorithmQuestionId(result.getAlgorithmQuestionId());
        command.setConversationMessageId(result.getConversationMessageId());
        command.setCorrectnessScore(result.getCorrectnessScore());
        command.setTimeComplexityScore(result.getTimeComplexityScore());
        command.setSpaceComplexityScore(result.getSpaceComplexityScore());
        command.setOverallScore(result.getOverallScore());
        command.setTestResults(result.getTestResults());
        command.setTimeComplexityAnalysis(result.getTimeComplexityAnalysis());
        command.setSpaceComplexityAnalysis(result.getSpaceComplexityAnalysis());
        command.setCodeQualityAnalysis(result.getCodeQualityAnalysis());
        command.setSuggestions(result.getSuggestions());
        command.setSummary(result.getSummary());
        command.setEvaluateModelKey(result.getEvaluateModelKey());
        command.setConversationId(result.getConversationId());
        command.setTraceId(result.getTraceId());
        command.setAgentName(result.getAgentName());
        return command;
    }

    public static CodeEvaluationResult toErrorResult(String errorMessage) {
        CodeEvaluationResult result = new CodeEvaluationResult();
        result.setOverallScore(0);
        result.setSummary(errorMessage);
        return result;
    }
}
