package com.oj.agent.core.workflow.persistence.impl;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.question.converter.AlgorithmQuestionConverter;
import com.oj.agent.core.question.enums.Language;
import com.oj.agent.core.question.model.command.GeneratedQuestionLanguageCodeCommand;
import com.oj.agent.core.question.model.command.GeneratedQuestionPersistCommand;
import com.oj.agent.core.question.service.GeneratedQuestionPersistenceService;
import com.oj.agent.core.workflow.enums.UserIntentType;
import com.oj.agent.core.workflow.model.dto.CodeQuestionOutputDTO;
import com.oj.agent.core.workflow.persistence.WorkflowPersistenceStrategy;
import com.oj.agent.core.workflow.persistence.context.WorkflowPersistenceContext;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeneratedQuestionPersistenceStrategy implements WorkflowPersistenceStrategy {

    private static final int EXPECTED_LANGUAGE_COUNT = 3;
    private static final Set<Language> EXPECTED_LANGUAGES = EnumSet.of(
            Language.PYTHON,
            Language.JAVA,
            Language.JAVASCRIPT
    );

    private static final List<String> ASSEMBLE_OUTPUT_STATE_KEYS = resolveAssembleOutputStateKeys();
    private static final String[] ASSEMBLE_SUCCESS_FIELDS = {"success", "isSuccess"};
    private static final String[] ASSEMBLE_ERROR_MESSAGE_FIELDS = {"errorMessage", "message"};
    private static final String[] ASSEMBLE_CODES_FIELDS = {"codes", "languageCodes", "languageCodeOutputs", "results", "outputs"};
    private static final String[] ITEM_LANGUAGE_FIELDS = {"language", "languageCode", "lang"};
    private static final String[] ITEM_SUCCESS_FIELDS = {"success", "isSuccess"};
    private static final String[] ITEM_ERROR_FIELDS = {"errorMessage", "message"};
    private static final String[] ITEM_FUNCTION_NAME_FIELDS = {"functionName", "function_name"};
    private static final String[] ITEM_CODE_SKELETON_FIELDS = {"codeSkeleton", "code_skeleton"};
    private static final String[] ITEM_REFERENCE_ANSWER_FIELDS = {"referenceAnswer", "reference_answer"};
    private static final String[] ITEM_MODEL_KEY_FIELDS = {"generateModelKey", "modelKey", "generate_model_key"};
    private static final String[] ITEM_TRACE_ID_FIELDS = {"traceId", "trace_id"};
    private static final String[] ITEM_AGENT_NAME_FIELDS = {"agentName", "agent_name"};

    private final GeneratedQuestionPersistenceService generatedQuestionPersistenceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persist(OverAllState state, WorkflowPersistenceContext context) {
        if (!shouldPersist(state)) {
            log.debug("Skip generated question persistence: conditions not met");
            return;
        }

        CodeQuestionOutputDTO output = StateUtil.getObjectValueOrNull(
                state,
                Constant.CODE_QUESTION_NODE_OUTPUT,
                CodeQuestionOutputDTO.class
        );
        String conversationId = (String) state.value(Constant.CONVERSATION_ID).orElse(null);
        String traceId = (String) state.value(Constant.TRACE_ID).orElse(null);
        Long userId = (Long) state.value(Constant.USER_ID).orElse(null);

        if (output == null || output.getQuestion() == null) {
            log.warn("CodeQuestionOutput or question is null after shouldPersist check, conversationId={}, traceId={}",
                    conversationId, traceId);
            return;
        }

        Object assembleOutput = resolveAssembleOutput(state, context);
        context.setAssembleOutput(assembleOutput);
        AssembleSnapshot assembleSnapshot = validateAndBuildAssembleSnapshot(assembleOutput, traceId);

        GeneratedQuestionPersistCommand command = buildPersistCommand(output, assembleSnapshot, conversationId, traceId, userId);
        Long generatedQuestionId = generatedQuestionPersistenceService.persistGeneratedQuestion(command);
        context.setGeneratedQuestionId(generatedQuestionId);

        log.info("Generated question persisted via question domain service, questionId={}, conversationId={}, traceId={}",
                generatedQuestionId, conversationId, traceId);
    }

    @Override
    public int getOrder() {
        return 20;
    }

    private Object resolveAssembleOutput(OverAllState state, WorkflowPersistenceContext context) {
        Object assembleOutput = context.getAssembleOutput(Object.class);
        if (assembleOutput != null) {
            return assembleOutput;
        }
        for (String key : ASSEMBLE_OUTPUT_STATE_KEYS) {
            Object candidate = StateUtil.getOptionalObjectValueOrNull(state, key, Object.class);
            if (candidate != null) {
                return candidate;
            }
        }
        throw new IllegalStateException("Missing multi-language assemble output for generated question persistence");
    }

    private AssembleSnapshot validateAndBuildAssembleSnapshot(Object assembleOutput, String traceId) {
        Boolean assembleSuccess = readBooleanField(assembleOutput, ASSEMBLE_SUCCESS_FIELDS);
        if (!Boolean.TRUE.equals(assembleSuccess)) {
            String errorMessage = readStringField(assembleOutput, ASSEMBLE_ERROR_MESSAGE_FIELDS);
            throw new IllegalStateException("multi-language assemble failed, traceId=" + traceId
                    + ", error=" + defaultIfBlank(errorMessage, "unknown"));
        }

        List<?> rawCodes = readListField(assembleOutput, ASSEMBLE_CODES_FIELDS);
        if (rawCodes.size() != EXPECTED_LANGUAGE_COUNT) {
            throw new IllegalStateException("multi-language assemble output language count mismatch, expected="
                    + EXPECTED_LANGUAGE_COUNT + ", actual=" + rawCodes.size() + ", traceId=" + traceId);
        }

        List<String> failedLanguages = new ArrayList<>();
        Set<Language> seenLanguages = EnumSet.noneOf(Language.class);
        List<LanguageCodeSnapshot> languageSnapshots = new ArrayList<>(EXPECTED_LANGUAGE_COUNT);

        for (Object rawCode : rawCodes) {
            Language language = parseLanguage(rawCode, traceId);
            if (!seenLanguages.add(language)) {
                throw new IllegalStateException("multi-language assemble output has duplicated language="
                        + language.name() + ", traceId=" + traceId);
            }

            Boolean languageSuccess = readBooleanField(rawCode, ITEM_SUCCESS_FIELDS);
            if (Boolean.FALSE.equals(languageSuccess)) {
                String itemError = readStringField(rawCode, ITEM_ERROR_FIELDS);
                failedLanguages.add(language.name() + ":" + defaultIfBlank(itemError, "unknown"));
                continue;
            }

            String functionName = readStringField(rawCode, ITEM_FUNCTION_NAME_FIELDS);
            if (!StringUtils.hasText(functionName)) {
                throw new IllegalStateException("multi-language assemble output missing functionName, language="
                        + language.name() + ", traceId=" + traceId);
            }

            String codeSkeleton = readStringField(rawCode, ITEM_CODE_SKELETON_FIELDS);
            String referenceAnswer = readStringField(rawCode, ITEM_REFERENCE_ANSWER_FIELDS);
            String generateModelKey = readStringField(rawCode, ITEM_MODEL_KEY_FIELDS);
            String codeTraceId = readStringField(rawCode, ITEM_TRACE_ID_FIELDS);
            String agentName = readStringField(rawCode, ITEM_AGENT_NAME_FIELDS);

            languageSnapshots.add(new LanguageCodeSnapshot(
                    language,
                    functionName,
                    codeSkeleton,
                    referenceAnswer,
                    generateModelKey,
                    codeTraceId,
                    agentName
            ));
        }

        if (!failedLanguages.isEmpty()) {
            throw new IllegalStateException("multi-language assemble failed languages="
                    + String.join(", ", failedLanguages) + ", traceId=" + traceId);
        }

        if (!seenLanguages.equals(EXPECTED_LANGUAGES)) {
            Set<Language> missingLanguages = EnumSet.copyOf(EXPECTED_LANGUAGES);
            missingLanguages.removeAll(seenLanguages);
            Set<Language> unexpectedLanguages = EnumSet.copyOf(seenLanguages);
            unexpectedLanguages.removeAll(EXPECTED_LANGUAGES);
            throw new IllegalStateException("multi-language assemble output language set mismatch, missing="
                    + missingLanguages + ", unexpected=" + unexpectedLanguages + ", traceId=" + traceId);
        }

        if (languageSnapshots.size() != EXPECTED_LANGUAGE_COUNT) {
            throw new IllegalStateException("multi-language assemble output successful language count mismatch, expected="
                    + EXPECTED_LANGUAGE_COUNT + ", actual=" + languageSnapshots.size() + ", traceId=" + traceId);
        }

        return new AssembleSnapshot(languageSnapshots);
    }

    private GeneratedQuestionPersistCommand buildPersistCommand(CodeQuestionOutputDTO output,
                                                                AssembleSnapshot assembleSnapshot,
                                                                String conversationId,
                                                                String traceId,
                                                                Long userId) {
        GeneratedQuestionPersistCommand command = new GeneratedQuestionPersistCommand();
        command.setUserId(userId);
        command.setTitle(output.getQuestion().getTitle());
        command.setDescription(output.getQuestion().getDescription());
        command.setDifficulty(output.getQuestion().getDifficulty());
        command.setSharedFunctionName(output.getQuestion().getSharedFunctionName());
        command.setSharedCodeSkeleton(output.getQuestion().getSharedCodeSkeleton());
        command.setSharedTestCases(output.getQuestion().getSharedTestCases());
        command.setTagNames(AlgorithmQuestionConverter.toTagNames(output.getQuestion().getTags()));
        command.setConversationId(conversationId);
        command.setTraceId(traceId);
        command.setAgentName(output.getQuestion().getAgentName());
        command.setLanguageCodes(buildLanguageCodeCommands(assembleSnapshot));
        return command;
    }

    private List<GeneratedQuestionLanguageCodeCommand> buildLanguageCodeCommands(AssembleSnapshot assembleSnapshot) {
        return assembleSnapshot.languageCodes().stream().map(codeSnapshot -> {
            GeneratedQuestionLanguageCodeCommand command = new GeneratedQuestionLanguageCodeCommand();
            command.setLanguage(codeSnapshot.language().name());
            command.setFunctionName(codeSnapshot.functionName());
            command.setCodeSkeleton(codeSnapshot.codeSkeleton());
            command.setReferenceAnswer(codeSnapshot.referenceAnswer());
            command.setGenerateModelKey(codeSnapshot.generateModelKey());
            command.setTraceId(codeSnapshot.traceId());
            command.setAgentName(codeSnapshot.agentName());
            return command;
        }).toList();
    }

    private Language parseLanguage(Object rawCode, String traceId) {
        String rawLanguage = readStringField(rawCode, ITEM_LANGUAGE_FIELDS);
        if (!StringUtils.hasText(rawLanguage)) {
            throw new IllegalStateException("multi-language assemble output missing language field, traceId=" + traceId);
        }
        try {
            return Language.fromName(rawLanguage);
        } catch (Exception exception) {
            throw new IllegalStateException("multi-language assemble output contains unsupported language="
                    + rawLanguage + ", traceId=" + traceId, exception);
        }
    }

    private boolean shouldPersist(OverAllState state) {
        UserIntentType intent = StateUtil.getFieldValue(
                state,
                Constant.INTENT_RECOGNITION_NODE_OUTPUT,
                UserIntentType.class,
                "intent"
        );
        Boolean matched = StateUtil.getFieldValue(
                state,
                Constant.RAG_JUDGE_NODE_OUTPUT,
                Boolean.class,
                "matched",
                "isMatched",
                "is_matched"
        );
        CodeQuestionOutputDTO codeQuestionOutput = StateUtil.getObjectValueOrNull(
                state,
                Constant.CODE_QUESTION_NODE_OUTPUT,
                CodeQuestionOutputDTO.class
        );

        return (intent == UserIntentType.NEW_QUESTION || intent == UserIntentType.CHANGE_DIFFICULT)
                && Boolean.FALSE.equals(matched)
                && codeQuestionOutput != null
                && codeQuestionOutput.isSuccess()
                && codeQuestionOutput.getQuestion() != null;
    }

    private Object readField(Object source, String... candidateFields) {
        if (source == null || candidateFields == null || candidateFields.length == 0) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            for (String candidateField : candidateFields) {
                if (map.containsKey(candidateField)) {
                    return map.get(candidateField);
                }
            }
            return null;
        }
        BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(source);
        for (String candidateField : candidateFields) {
            if (beanWrapper.isReadableProperty(candidateField)) {
                return beanWrapper.getPropertyValue(candidateField);
            }
        }
        return null;
    }

    private Boolean readBooleanField(Object source, String... candidateFields) {
        Object value = readField(source, candidateFields);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String readStringField(Object source, String... candidateFields) {
        Object value = readField(source, candidateFields);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private List<?> readListField(Object source, String... candidateFields) {
        Object value = readField(source, candidateFields);
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            return Arrays.asList((Object[]) value);
        }
        return List.of();
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static List<String> resolveAssembleOutputStateKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add("multiLanguageCodeAssembleNodeOutput");
        keys.add("multiLanguageCodeAssembleOutput");
        keys.add("codeAssembleNodeOutput");
        keys.add("languageCodeAssembleNodeOutput");

        try {
            for (Field field : Constant.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!String.class.equals(field.getType())) {
                    continue;
                }
                if (!field.getName().contains("ASSEMBLE")) {
                    continue;
                }
                Object value = field.get(null);
                if (value instanceof String key && StringUtils.hasText(key)) {
                    keys.add(key);
                }
            }
        } catch (Exception exception) {
            log.debug("Failed to resolve assemble output state keys from Constant", exception);
        }

        return List.copyOf(keys);
    }

    private record AssembleSnapshot(List<LanguageCodeSnapshot> languageCodes) {
    }

    private record LanguageCodeSnapshot(Language language,
                                        String functionName,
                                        String codeSkeleton,
                                        String referenceAnswer,
                                        String generateModelKey,
                                        String traceId,
                                        String agentName) {
    }
}
