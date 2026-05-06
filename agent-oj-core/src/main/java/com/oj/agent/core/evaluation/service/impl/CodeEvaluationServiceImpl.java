package com.oj.agent.core.evaluation.service.impl;

import com.oj.agent.core.evaluation.converter.CodeEvaluationConverter;
import com.oj.agent.core.evaluation.mapper.CodeEvaluationMapper;
import com.oj.agent.core.evaluation.model.command.CodeEvaluationCreateCommand;
import com.oj.agent.core.evaluation.model.result.CodeEvaluationResult;
import com.oj.agent.core.evaluation.service.CodeEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CodeEvaluationServiceImpl implements CodeEvaluationService {

    private final CodeEvaluationMapper codeEvaluationMapper;

    @Override
    public CodeEvaluationResult createEvaluation(CodeEvaluationCreateCommand command) {
        validateCreateCommand(command);
        LocalDateTime now = LocalDateTime.now();
        var evaluation = CodeEvaluationConverter.toEntity(command, now);

        int insertedRows = codeEvaluationMapper.insert(evaluation);
        if (insertedRows <= 0) {
            throw new IllegalStateException("Failed to create code evaluation");
        }

        return CodeEvaluationConverter.toResult(evaluation);
    }

    private void validateCreateCommand(CodeEvaluationCreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        if (command.getUserId() == null || command.getUserId() <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        if (command.getSubmissionId() == null || command.getSubmissionId() <= 0) {
            throw new IllegalArgumentException("submissionId is required");
        }
        if (command.getAlgorithmQuestionId() == null || command.getAlgorithmQuestionId() <= 0) {
            throw new IllegalArgumentException("algorithmQuestionId is required");
        }
    }
}
