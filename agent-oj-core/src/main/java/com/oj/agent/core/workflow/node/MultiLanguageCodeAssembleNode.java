package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.TextType;
import com.oj.agent.core.question.converter.AlgorithmQuestionConverter;
import com.oj.agent.core.question.model.result.AlgorithmCodeTemplateResult;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.workflow.model.dto.CodeQuestionOutputDTO;
import com.oj.agent.core.workflow.model.dto.LanguageCodeOutputDTO;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.workflow.util.ChatResponseUtil;
import com.oj.agent.core.workflow.util.FluxUtil;
import lombok.Data;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class MultiLanguageCodeAssembleNode implements NodeAction {

    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    @Override
    public Map<String, Object> apply(OverAllState state) {
        return Map.of(Constant.MULTI_LANGUAGE_CODE_ASSEMBLE_NODE_OUTPUT, createGenerator(state));
    }

    Flux<GraphResponse<StreamingOutput>> createGenerator(OverAllState state) {
        MultiLanguageCodeAssembleOutput assembleOutput = buildAssembleOutput(state);

        Flux<ChatResponse> sourceFlux = Flux.empty();
        Flux<ChatResponse> preFlux = Flux.empty();
        Flux<ChatResponse> sufFlux = Flux.empty();

        if (assembleOutput.isSuccess() && assembleOutput.getQuestion() != null) {
            sourceFlux = Flux.just(ChatResponseUtil.createPureResponse(toQuestionJson(assembleOutput.getQuestion())));
            preFlux = Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()));
            sufFlux = Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()));
        }

        return FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                sourceFlux,
                preFlux,
                sufFlux,
                ignored -> Map.of(Constant.MULTI_LANGUAGE_CODE_ASSEMBLE_NODE_OUTPUT, assembleOutput)
        );
    }

    private MultiLanguageCodeAssembleOutput buildAssembleOutput(OverAllState state) {
        List<LanguageCodeOutputDTO> outputs = List.of(
                readOutput(state, Constant.PYTHON_CODE_NODE_OUTPUT),
                readOutput(state, Constant.JAVA_CODE_NODE_OUTPUT),
                readOutput(state, Constant.JAVASCRIPT_CODE_NODE_OUTPUT)
        );

        MultiLanguageCodeAssembleOutput assembleOutput = new MultiLanguageCodeAssembleOutput();
        assembleOutput.setLanguageCodes(outputs.stream().filter(Objects::nonNull).toList());

        CodeQuestionOutputDTO questionOutput = readQuestionOutput(state);
        if (questionOutput == null || !questionOutput.isSuccess() || questionOutput.getQuestion() == null) {
            assembleOutput.setSuccess(false);
            assembleOutput.setFailedLanguages(List.of("shared-question"));
            assembleOutput.setErrorMessage("multi-language generation failed: missing shared question output");
            return assembleOutput;
        }

        List<String> failedLanguages = new ArrayList<>();
        List<String> failedDetails = new ArrayList<>();
        for (LanguageCodeOutputDTO output : outputs) {
            if (output == null) {
                failedLanguages.add("unknown");
                failedDetails.add("unknown: missing language node output");
                continue;
            }

            if (!output.isSuccess()) {
                String language = normalizeLanguage(output.getLanguage());
                failedLanguages.add(language);
                String detail = StringUtils.hasText(output.getErrorMessage())
                        ? output.getErrorMessage()
                        : "unknown";
                failedDetails.add(language + ": " + detail);
            }
        }

        if (!failedLanguages.isEmpty()) {
            assembleOutput.setSuccess(false);
            assembleOutput.setFailedLanguages(failedLanguages);
            assembleOutput.setErrorMessage("multi-language generation failed: " + String.join(", ", failedDetails));
            return assembleOutput;
        }

        assembleOutput.setSuccess(true);
        assembleOutput.setFailedLanguages(List.of());
        assembleOutput.setQuestion(buildQuestionResult(questionOutput.getQuestion(), assembleOutput.getLanguageCodes()));
        return assembleOutput;
    }

    private CodeQuestionOutputDTO readQuestionOutput(OverAllState state) {
        Object value = state.value(Constant.CODE_QUESTION_NODE_OUTPUT).orElse(null);
        if (value == null) {
            return null;
        }
        if (value instanceof CodeQuestionOutputDTO output) {
            return output;
        }
        return convertValue(value, CodeQuestionOutputDTO.class);
    }

    private LanguageCodeOutputDTO readOutput(OverAllState state, String key) {
        Object value = state.value(key).orElse(null);
        if (value == null) {
            return null;
        }
        if (value instanceof LanguageCodeOutputDTO output) {
            return output;
        }
        return convertValue(value, LanguageCodeOutputDTO.class);
    }

    private <T> T convertValue(Object value, Class<T> targetType) {
        try {
            return objectMapper.convertValue(value, targetType);
        } catch (Exception ignored) {
            return null;
        }
    }

    private AlgorithmQuestionResult buildQuestionResult(AlgorithmQuestionResult question,
                                                        List<LanguageCodeOutputDTO> languageCodes) {
        List<AlgorithmCodeTemplateResult> codeTemplates = languageCodes.stream()
                .filter(Objects::nonNull)
                .filter(LanguageCodeOutputDTO::isSuccess)
                .map(this::toCodeTemplate)
                .toList();
        return AlgorithmQuestionConverter.withCodeTemplates(question, codeTemplates);
    }

    private AlgorithmCodeTemplateResult toCodeTemplate(LanguageCodeOutputDTO output) {
        return AlgorithmQuestionConverter.toCodeTemplateResult(
                normalizeLanguage(output.getLanguage()),
                output.getFunctionName(),
                output.getCodeSkeleton()
        );
    }

    private String toQuestionJson(AlgorithmQuestionResult question) {
        try {
            return objectMapper.writeValueAsString(question);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize assembled question payload", e);
        }
    }

    private String normalizeLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            return "unknown";
        }
        return language.trim();
    }

    @Data
    public static class MultiLanguageCodeAssembleOutput {
        private boolean success;
        private String errorMessage;
        private List<String> failedLanguages = List.of();
        private List<LanguageCodeOutputDTO> languageCodes = List.of();
        private AlgorithmQuestionResult question;
    }
}
