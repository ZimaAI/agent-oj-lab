package com.oj.agent.core.aimodel.prompt;

import com.oj.agent.core.question.enums.Language;
import com.oj.agent.core.workflow.enums.UserIntentType;
import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.evaluation.model.result.CodeEvaluationResult;
import com.oj.agent.core.submission.model.result.CodeSubmissionResult;
import com.oj.agent.core.workflow.model.dto.AnswerRewriteOutputDTO;
import com.oj.agent.core.evaluation.model.dto.CodeEvaluationLLMOutputDTO;
import com.oj.agent.core.workflow.model.dto.CodeQuestionLLMOutputDTO;
import com.oj.agent.core.workflow.model.dto.IntentRecognitionOutputDTO;
import com.oj.agent.core.workflow.model.dto.LanguageCodeOutputDTO;
import com.oj.agent.core.workflow.model.dto.QuestionRewriteOutputDTO;
import com.oj.agent.core.rag.model.dto.SegmentRagasQaOutputDTO;
import com.oj.agent.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class PromptHelper {

    private PromptHelper() {
    }

    public static String buildIntentRecognitionPrompt(List<Message> messages, String input) {
        Map<String, Object> params = new HashMap<>();
        params.put("history", formatHistory(messages));
        params.put("input", safeText(input));
        params.put("format", new BeanOutputConverter<>(IntentRecognitionOutputDTO.class).getFormat());
        return PromptConstant.getIntentRecognitionPromptTemplate().render(params);
    }

    public static String buildQuestionRewritePrompt(List<Message> messages,
                                                    String input,
                                                    UserIntentType intent,
                                                    AlgorithmQuestionResult currentQuestion) {
        Map<String, Object> params = new HashMap<>();
        params.put("history", formatHistory(messages));
        params.put("input", safeText(input));
        params.put("intent", intent == null ? UserIntentType.OTHER.name() : intent.name());
        params.put("current_question", formatCurrentQuestion(currentQuestion));
        params.put("format", new BeanOutputConverter<>(QuestionRewriteOutputDTO.class).getFormat());
        return PromptConstant.getQuestionRewritePromptTemplate().render(params);
    }

    public static String buildAnswerRewritePrompt(List<Message> messages,
                                                  String input,
                                                  AlgorithmQuestionResult currentQuestion) {
        Map<String, Object> params = new HashMap<>();
        params.put("history", formatHistory(messages));
        params.put("input", safeText(input));
        params.put("current_question", formatCurrentQuestion(currentQuestion));
        params.put("format", new BeanOutputConverter<>(AnswerRewriteOutputDTO.class).getFormat());
        return PromptConstant.getAnswerRewritePromptTemplate().render(params);
    }

    public static String buildOjAssistantSystemPrompt(UserIntentType intent, AlgorithmQuestionResult currentQuestion) {
        Map<String, Object> params = new HashMap<>();
        params.put("intent", intent == null ? UserIntentType.OTHER.name() : intent.name());
        params.put("current_question", formatCurrentQuestion(currentQuestion));
        return PromptConstant.getOjAssistantPromptTemplate().render(params);
    }

    public static String buildPythonCodeExecutionRulesPrompt() {
        return PromptLoader.loadPrompt("python-code-execution-rules");
    }

    public static String buildJavaCodeExecutionRulesPrompt() {
        return PromptLoader.loadPrompt("java-code-execution-rules");
    }

    public static String buildJavaScriptCodeExecutionRulesPrompt() {
        return PromptLoader.loadPrompt("javascript-code-execution-rules");
    }

    public static String buildLanguageCodeGenerationPrompt(Language language, String executionRules) {
        Map<String, Object> params = new HashMap<>();
        params.put("language", language == null ? "" : language.getName());
        params.put("execution_rules", safeText(executionRules));
        params.put("format", new BeanOutputConverter<>(LanguageCodeOutputDTO.class).getFormat());
        return PromptConstant.getLanguageCodeGenerationPromptTemplate().render(params);
    }

    public static String buildLanguageCodeUserPrompt(List<Message> messages,
                                                     String input,
                                                     AlgorithmQuestion question,
                                                     Language language) {
        Map<String, Object> params = new HashMap<>();
        params.put("history", formatHistory(messages));
        params.put("input", safeText(input));
        params.put("language", language == null ? "" : language.getName());
        params.put("question", formatSharedQuestion(question));
        return PromptConstant.getLanguageCodeUserPromptTemplate().render(params);
    }

    public static String buildMemoryCompressPrompt(List<Message> messages) {
        return PromptConstant.getMemoryCompressPromptTemplate().render(
                Map.of("history", formatHistory(messages))
        );
    }

    public static String buildSegmentHitkQuestionPrompt(String segmentText) {
        return PromptConstant.getSegmentHitkQuestionPromptTemplate().render(
                Map.of("segment_text", safeText(segmentText))
        );
    }

    public static String buildSegmentRagasQaPrompt(String segmentText) {
        Map<String, Object> params = new HashMap<>();
        params.put("segment_text", safeText(segmentText));
        params.put("format", new BeanOutputConverter<>(SegmentRagasQaOutputDTO.class).getFormat());
        return PromptConstant.getSegmentRagasQaPromptTemplate().render(params);
    }

    public static String buildSegmentCodeDescriptionPrompt(String segmentText, int maxChars) {
        return PromptConstant.getSegmentCodeDescriptionPromptTemplate().render(
                Map.of(
                        "segment_text", safeText(segmentText),
                        "max_chars", Math.max(maxChars, 1)
                )
        );
    }

    public static String buildRagJudgePrompt(String userQuestion,
                                             AlgorithmQuestionResult currentQuestion,
                                             List<AlgorithmQuestionResult> candidates,
                                             String format) {
        Map<String, Object> params = new HashMap<>();
        params.put("userQuestion", safeText(userQuestion));
        params.put("currentQuestion", formatCurrentQuestion(currentQuestion));
        params.put("questionList", formatCandidateQuestions(candidates));
        params.put("format", safeText(format));
        return PromptConstant.getRagJudgePromptTemplate().render(params);
    }

    public static String buildCodeQuestionPrompt(UserIntentType intent,
                                                 AlgorithmQuestionResult currentQuestion,
                                                 List<AlgorithmQuestionResult> referenceQuestions) {
        Map<String, Object> params = new HashMap<>();
        params.put("intent", intent == null ? UserIntentType.NEW_QUESTION.name() : intent.name());
        params.put("current_question", formatCurrentQuestion(currentQuestion));
        params.put("reference_question", formatReferenceQuestions(referenceQuestions));
        params.put("format", new BeanOutputConverter<>(CodeQuestionLLMOutputDTO.class).getFormat());
        return PromptConstant.getCodeQuestionGenerationTemplate().render(params);
    }

    public static String buildCodeQuestionUserPrompt(List<Message> messages,
                                                     String input,
                                                     String rewrittenQuestion,
                                                     UserIntentType intent,
                                                     AlgorithmQuestionResult currentQuestion) {
        List<Message> userAssistantMessages = messages == null ? List.of() : messages.stream()
                .filter(msg -> msg instanceof UserMessage || msg instanceof AssistantMessage)
                .toList();

        Map<String, Object> params = new HashMap<>();
        params.put("history", formatHistory(userAssistantMessages));
        params.put("input", safeText(input));
        params.put("rewritten_question", safeText(rewrittenQuestion));
        params.put("intent", intent == null ? UserIntentType.NEW_QUESTION.name() : intent.name());
        params.put("current_question", formatCurrentQuestion(currentQuestion));
        return PromptConstant.getCodeQuestionUserPromptTemplate().render(params);
    }

    public static String buildCodeEvaluationPrompt(AlgorithmQuestionResult question,
                                                   CodeSubmissionResult submission,
                                                   String executionResult,
                                                   String functionName) {
        Map<String, Object> params = new HashMap<>();
        params.put("title", question == null ? "" : safeText(question.getTitle()));
        params.put("description", question == null ? "" : safeText(question.getDescription()));
        params.put("difficulty", question == null ? "" : safeText(question.getDifficulty()));
        params.put("functionName", safeText(functionName));
        params.put("testCases", question == null ? "" : safeText(question.getSharedTestCases()));
        params.put("code", submission == null ? "" : safeText(submission.getCode()));
        params.put("executionResult", safeText(executionResult));
        params.put("format", new BeanOutputConverter<>(CodeEvaluationLLMOutputDTO.class).getFormat());
        return PromptConstant.getCodeEvaluationPromptTemplate().render(params);
    }

    public static String loadPrompt(String fileName) {
        return PromptLoader.loadPrompt(fileName);
    }

    public static String buildAssistantContextForQuestionResult(UserIntentType intent,
                                                                String userQuestion,
                                                                AlgorithmQuestionResult question,
                                                                String fallbackErrorMessage) {
        if (question == null) {
            String reason = safeText(fallbackErrorMessage);
            if (reason.isBlank()) {
                reason = "No suitable question was found/generated.";
            }
            return """
                    User request: %s
                    Intent: %s
                    Result: failed
                    Reason: %s
                    Please explain the failure and suggest a clearer retry request.
                    """.formatted(
                    safeText(userQuestion),
                    intent == null ? UserIntentType.OTHER.name() : intent.name(),
                    reason
            );
        }

        try {
            String questionJson = JsonUtils.getObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(buildQuestionSnapshotView(question));
            return """
                    User request: %s
                    Intent: %s
                    Generated/selected question:
                    %s
                    Please explain the question to the user clearly.
                    """.formatted(
                    safeText(userQuestion),
                    intent == null ? UserIntentType.OTHER.name() : intent.name(),
                    questionJson
            );
        } catch (Exception exception) {
            log.warn("Failed to serialize question context", exception);
            return """
                    User request: %s
                    Intent: %s
                    Question title: %s
                    Difficulty: %s
                    Please explain the question to the user clearly.
                    """.formatted(
                    safeText(userQuestion),
                    intent == null ? UserIntentType.OTHER.name() : intent.name(),
                    safeText(question.getTitle()),
                    safeText(question.getDifficulty())
            );
        }
    }

    private static Map<String, Object> buildQuestionSnapshotView(AlgorithmQuestionResult question) {
        Map<String, Object> snapshotView = new LinkedHashMap<>();
        snapshotView.put("id", question.getId());
        snapshotView.put("title", question.getTitle());
        snapshotView.put("description", question.getDescription());
        snapshotView.put("difficulty", question.getDifficulty());
        snapshotView.put("type", question.getType());
        snapshotView.put("sharedFunctionName", question.getSharedFunctionName());
        snapshotView.put("sharedCodeSkeleton", question.getSharedCodeSkeleton());
        snapshotView.put("sharedTestCases", question.getSharedTestCases());
        snapshotView.put("codeTemplates", question.getCodeTemplates());
        snapshotView.put("similarity", question.getSimilarity());
        return snapshotView;
    }

    public static String buildAssistantContextForCodeEvaluation(String userQuestion, CodeEvaluationResult evaluation) {
        if (evaluation == null) {
            return """
                    User request: %s
                    Code evaluation is unavailable.
                    Please explain that evaluation failed and suggest retry.
                    """.formatted(safeText(userQuestion));
        }

        try {
            String evaluationJson = JsonUtils.getObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(evaluation);
            return """
                    User request: %s
                    Evaluation result:
                    %s
                    Please summarize the score and improvement suggestions.
                    """.formatted(safeText(userQuestion), evaluationJson);
        } catch (Exception exception) {
            log.warn("Failed to serialize evaluation context", exception);
            return """
                    User request: %s
                    Overall score: %s
                    Summary: %s
                    Please summarize the score and improvement suggestions.
                    """.formatted(
                    safeText(userQuestion),
                    String.valueOf(evaluation.getOverallScore()),
                    safeText(evaluation.getSummary())
            );
        }
    }

    private static String formatHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "- <empty>\n";
        }
        return messages.stream()
                .map(message -> "- " + message.getMessageType() + ": " + safeText(message.getText()))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private static String formatCurrentQuestion(AlgorithmQuestionResult question) {
        if (question == null) {
            return "(none)";
        }
        return """
                ID: %s
                Title: %s
                Description: %s
                Difficulty: %s
                SharedFunctionName: %s
                """.formatted(
                Objects.toString(question.getId(), "(none)"),
                safeText(question.getTitle()),
                safeText(question.getDescription()),
                safeText(question.getDifficulty()),
                safeText(question.getSharedFunctionName())
        );
    }

    private static String formatReferenceQuestions(List<AlgorithmQuestionResult> referenceQuestions) {
        if (referenceQuestions == null || referenceQuestions.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < referenceQuestions.size(); i++) {
            builder.append("Reference #").append(i + 1).append(":\n")
                    .append(formatReferenceQuestion(referenceQuestions.get(i)))
                    .append('\n');
        }
        return builder.toString();
    }

    private static String formatReferenceQuestion(AlgorithmQuestionResult question) {
        if (question == null) {
            return "(none)";
        }
        return """
                ID: %s
                Title: %s
                Description: %s
                Difficulty: %s
                """.formatted(
                Objects.toString(question.getId(), "(none)"),
                safeText(question.getTitle()),
                safeText(question.getDescription()),
                safeText(question.getDifficulty())
        );
    }

    private static String formatCandidateQuestions(List<AlgorithmQuestionResult> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            AlgorithmQuestionResult q = candidates.get(i);
            builder.append(i + 1).append(". ID=").append(q.getId())
                    .append(", Title=").append(safeText(q.getTitle()))
                    .append(", Difficulty=").append(safeText(q.getDifficulty()))
                    .append('\n');
        }
        return builder.toString();
    }

    private static String formatSharedQuestion(AlgorithmQuestion question) {
        if (question == null) {
            return "(none)";
        }
        return """
                Question ID: %s
                Title: %s
                Description: %s
                Difficulty: %s
                Shared Function Name: %s
                Shared Code Skeleton:
                %s
                Shared Test Cases:
                %s
                """.formatted(
                Objects.toString(question.getId(), "(none)"),
                safeText(question.getTitle()),
                safeText(question.getDescription()),
                safeText(question.getDifficulty()),
                safeText(question.getSharedFunctionName()),
                safeText(question.getSharedCodeSkeleton()),
                safeText(question.getSharedTestCases())
        );
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }
}
