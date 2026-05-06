package com.oj.agent.core.question.service.impl;

import com.oj.agent.core.question.enums.AlgorithmQuestionTypeEnum;
import com.oj.agent.core.question.enums.AlgorithmQuestionVectorSyncStatusEnum;
import com.oj.agent.core.question.model.command.GeneratedQuestionPersistCommand;
import com.oj.agent.core.question.model.entity.AlgorithmCode;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import com.oj.agent.core.question.service.AlgorithmCodeService;
import com.oj.agent.core.question.service.AlgorithmQuestionVectorSyncBookkeepingService;
import com.oj.agent.core.question.service.AlgorithmQuestionVectorSyncService;
import com.oj.agent.core.question.service.GeneratedQuestionPersistenceService;
import com.oj.agent.core.question.service.QuestionTagPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneratedQuestionPersistenceServiceImpl implements GeneratedQuestionPersistenceService {

    private static final String ERROR_CODE_EMBEDDING_FAILED = "EMBEDDING_FAILED";
    private static final String ERROR_CODE_DIMENSION_MISMATCH = "DIMENSION_MISMATCH";
    private static final String ERROR_CODE_VECTOR_UPSERT_FAILED = "VECTOR_UPSERT_FAILED";

    private final AlgorithmQuestionVectorSyncBookkeepingService bookkeepingService;
    private final AlgorithmQuestionVectorSyncService algorithmQuestionVectorSyncService;
    private final QuestionTagPersistenceService questionTagPersistenceService;
    private final AlgorithmCodeService algorithmCodeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long persistGeneratedQuestion(GeneratedQuestionPersistCommand command) {
        validateCommand(command);

        AlgorithmQuestion question = buildQuestionEntity(command);
        boolean saved = bookkeepingService.saveQuestionWithPendingLog(question, command.getTraceId());
        if (!saved) {
            throw new IllegalStateException("Failed to persist generated question, conversationId="
                    + command.getConversationId() + ", traceId=" + command.getTraceId());
        }
        if (question.getId() == null) {
            throw new IllegalStateException("Generated question id is null after persistence, conversationId="
                    + command.getConversationId() + ", traceId=" + command.getTraceId());
        }

        List<AlgorithmCode> languageCodes = buildLanguageCodes(question.getId(), command);
        boolean languageCodesSaved = algorithmCodeService.saveBatch(languageCodes);
        if (!languageCodesSaved) {
            throw new IllegalStateException("Failed to persist generated language codes, questionId="
                    + question.getId() + ", traceId=" + command.getTraceId());
        }

        questionTagPersistenceService.persistQuestionTags(question.getId(), command.getTagNames());

        try {
            algorithmQuestionVectorSyncService.sync(question);
            try {
                bookkeepingService.markSuccess(question.getId(), command.getTraceId());
            } catch (Exception exception) {
                handleSuccessBookkeepingFailure(question.getId(), command.getTraceId(), exception);
            }
        } catch (Exception exception) {
            handleSyncFailure(question.getId(), command.getTraceId(), exception);
        }

        log.info("Generated question and language codes persisted, questionId={}, conversationId={}, traceId={}",
                question.getId(), command.getConversationId(), command.getTraceId());
        return question.getId();
    }

    private void validateCommand(GeneratedQuestionPersistCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("generated question command cannot be null");
        }
        if (!StringUtils.hasText(command.getTitle())) {
            throw new IllegalArgumentException("generated question title cannot be blank");
        }
        if (!StringUtils.hasText(command.getSharedFunctionName())
                || !StringUtils.hasText(command.getSharedCodeSkeleton())
                || !StringUtils.hasText(command.getSharedTestCases())) {
            throw new IllegalArgumentException("generated question shared artifacts are incomplete");
        }
        if (CollectionUtils.isEmpty(command.getLanguageCodes())) {
            throw new IllegalArgumentException("generated question language codes cannot be empty");
        }
    }

    private AlgorithmQuestion buildQuestionEntity(GeneratedQuestionPersistCommand command) {
        AlgorithmQuestion question = new AlgorithmQuestion();
        question.setUserId(command.getUserId());
        question.setTitle(command.getTitle());
        question.setDescription(command.getDescription());
        question.setDifficulty(command.getDifficulty());
        question.setType(AlgorithmQuestionTypeEnum.AI.name());
        question.setSharedFunctionName(command.getSharedFunctionName());
        question.setSharedCodeSkeleton(command.getSharedCodeSkeleton());
        question.setSharedTestCases(command.getSharedTestCases());
        question.setTags(command.getTagNames());
        question.setConversationId(command.getConversationId());
        question.setTraceId(command.getTraceId());
        question.setAgentName(command.getAgentName());
        question.setVectorSyncStatus(AlgorithmQuestionVectorSyncStatusEnum.PENDING.name());
        question.setVectorSyncErrorMessage(null);
        return question;
    }

    private List<AlgorithmCode> buildLanguageCodes(Long questionId, GeneratedQuestionPersistCommand command) {
        return command.getLanguageCodes().stream().map(languageCode -> {
            AlgorithmCode code = new AlgorithmCode();
            code.setQuestionId(questionId);
            code.setLanguage(languageCode.getLanguage());
            code.setFunctionName(languageCode.getFunctionName());
            code.setCodeSkeleton(languageCode.getCodeSkeleton());
            code.setReferenceAnswer(languageCode.getReferenceAnswer());
            code.setGenerateModelKey(languageCode.getGenerateModelKey());
            code.setTraceId(defaultIfBlank(languageCode.getTraceId(), command.getTraceId()));
            code.setAgentName(defaultIfBlank(languageCode.getAgentName(), command.getAgentName()));
            code.setIsDelete(0);
            return code;
        }).toList();
    }

    private void handleSuccessBookkeepingFailure(Long questionId, String traceId, Exception exception) {
        String errorMessage = buildErrorMessage(exception);
        try {
            bookkeepingService.recordSuccessBookkeepingFailure(questionId, traceId, errorMessage);
        } catch (Exception bookkeepingException) {
            try {
                bookkeepingService.recordSuccessBookkeepingFailureSnapshotOnly(questionId, traceId, errorMessage);
            } catch (Exception snapshotException) {
                throw new IllegalStateException("Failed to persist success bookkeeping anomaly, questionId="
                        + questionId, snapshotException);
            }
        }
    }

    private void handleSyncFailure(Long questionId, String traceId, Exception exception) {
        String errorCode = resolveErrorCode(exception);
        String errorMessage = buildErrorMessage(exception);
        try {
            bookkeepingService.recordFailure(questionId, traceId, errorCode, errorMessage);
        } catch (Exception bookkeepingException) {
            try {
                bookkeepingService.recordFailureSnapshotOnly(questionId, traceId, errorCode, errorMessage);
            } catch (Exception snapshotException) {
                throw new IllegalStateException("Failed to persist sync failure, questionId="
                        + questionId, snapshotException);
            }
        }
        log.warn("Generated question vector sync failed, questionId={}, traceId={}, errorCode={}, errorMessage={}",
                questionId, traceId, errorCode, errorMessage);
    }

    private String resolveErrorCode(Exception exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("dimension mismatch")) {
            return ERROR_CODE_DIMENSION_MISMATCH;
        }
        if (message != null && message.contains("Embedding response is empty")) {
            return ERROR_CODE_EMBEDDING_FAILED;
        }
        return ERROR_CODE_VECTOR_UPSERT_FAILED;
    }

    private String buildErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
