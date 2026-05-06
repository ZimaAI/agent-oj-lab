package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.question.enums.Language;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.workflow.model.dto.CodeQuestionOutputDTO;
import com.oj.agent.core.workflow.model.dto.LanguageCodeOutputDTO;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.executor.tool.ExecutionComparisonPolicy;
import com.oj.agent.core.executor.tool.ExecuteCodeTool;
import com.oj.agent.core.executor.tool.model.CodeExecutionRequest;
import com.oj.agent.core.executor.tool.model.CodeExecutionResult;
import com.oj.agent.core.executor.tool.model.TestResult;
import com.oj.agent.core.aimodel.prompt.PromptConstant;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.core.workflow.trace.WorkflowTraceSupport;
import com.oj.agent.core.trace.model.command.TraceItemFinishCommand;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.result.TraceItemTypeResult;
import com.oj.agent.core.trace.model.result.TraceStatusResult;
import com.oj.agent.core.trace.model.result.TraceTokenUsageResult;
import com.oj.agent.core.workflow.util.ChatResponseUtil;
import com.oj.agent.core.workflow.util.StateUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractLanguageCodeNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getObjectMapper();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_LANGUAGE_GENERATION_ROUNDS = Constant.CODE_QUESTION_MAX_ROUNDS;
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?s)//|/\\*|\\*/|#");
    private static final Pattern PYTHON_SIGNATURE_PATTERN =
            Pattern.compile("^def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^\\n\\r]*\\)\\s*:\\s*$");
    private static final Pattern JAVASCRIPT_EMPTY_FUNCTION_PATTERN =
            Pattern.compile("(?s)^function\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\([^\\n\\r]*\\)\\s*\\{\\s*\\}\\s*$");
    private static final Pattern JAVA_EMPTY_METHOD_PATTERN = Pattern.compile(
            "(?s)^(?:public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?"
                    + "[A-Za-z_][A-Za-z0-9_<>\\[\\],.?\\s]*\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^\\n\\r]*\\)\\s*\\{\\s*\\}\\s*$"
    );
    private static final Pattern JAVA_EMPTY_METHOD_EXTRACT_PATTERN = Pattern.compile(
            "(?s)(?:public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?"
                    + "[A-Za-z_][A-Za-z0-9_<>\\[\\],.?\\s]*\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^\\n\\r]*\\)\\s*\\{\\s*\\}"
    );
    private static final Pattern JAVA_METHOD_OPENING_ONLY_PATTERN = Pattern.compile(
            "(?s)^(?:public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?"
                    + "[A-Za-z_][A-Za-z0-9_<>\\[\\],.?\\s]*\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^\\n\\r]*\\)\\s*\\{\\s*$"
    );
    private static final String[] FUNCTION_NAME_FIELDS = {"functionName", "function_name"};
    private static final String[] CODE_SKELETON_FIELDS = {"codeSkeleton", "code_skeleton"};
    private static final String[] REFERENCE_ANSWER_FIELDS = {"referenceAnswer", "reference_answer"};

    private final ChatClient chatClient;
    private final ExecuteCodeTool executeCodeTool;
    private final Language language;
    private final String outputKey;
    private final WorkflowTraceRecorder workflowTraceRecorder;
    private final WorkflowTraceSupport workflowTraceSupport;

    protected AbstractLanguageCodeNode(AiModelRegistry aiModelRegistry,
                                       ExecuteCodeTool executeCodeTool,
                                       Language language,
                                       String outputKey) {
        this(aiModelRegistry, executeCodeTool, language, outputKey, null, null);
    }

    protected AbstractLanguageCodeNode(AiModelRegistry aiModelRegistry,
                                       ExecuteCodeTool executeCodeTool,
                                       Language language,
                                       String outputKey,
                                       WorkflowTraceRecorder workflowTraceRecorder,
                                       WorkflowTraceSupport workflowTraceSupport) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
        this.executeCodeTool = executeCodeTool;
        this.language = language;
        this.outputKey = outputKey;
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        LanguageCodeOutputDTO output = new LanguageCodeOutputDTO();
        output.setSuccess(false);
        output.setLanguage(language.getName());

        CodeQuestionOutputDTO questionOutput = StateUtil.getObjectValueOrNull(
                state,
                Constant.CODE_QUESTION_NODE_OUTPUT,
                CodeQuestionOutputDTO.class
        );
        if (questionOutput == null || !questionOutput.isSuccess() || questionOutput.getQuestion() == null) {
            output.setErrorMessage("Missing shared question output");
            return Map.of(outputKey, output);
        }

        AlgorithmQuestionResult question = questionOutput.getQuestion();
        if (!StringUtils.hasText(question.getSharedFunctionName())
                || !StringUtils.hasText(question.getSharedCodeSkeleton())
                || !StringUtils.hasText(question.getSharedTestCases())) {
            output.setErrorMessage("Shared question artifacts are incomplete");
            return Map.of(outputKey, output);
        }

        if (executeCodeTool == null) {
            output.setErrorMessage("ExecuteCodeTool is not initialized");
            return Map.of(outputKey, output);
        }
        if (!executeCodeTool.getSupportedLanguages().contains(language)) {
            output.setErrorMessage("Execution language is not enabled: " + language.getName());
            return Map.of(outputKey, output);
        }

        try {
            SharedTestCaseBundle sharedTestCaseBundle = parseSharedTestCases(question.getSharedTestCases());
            ConversationContext conversationContext = extractConversationContext(state);
            String systemPrompt = buildSystemPrompt();
            String feedback = null;
            String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
            boolean ignoreCollectionOrder = ExecutionComparisonPolicy.shouldIgnoreCollectionOrder(question.getDescription());

            for (int round = 1; round <= MAX_LANGUAGE_GENERATION_ROUNDS; round++) {
                String userPrompt = buildUserPrompt(conversationContext, question, feedback, round);
                Long llmTraceItemId = startLlmTraceItem(traceId, round, systemPrompt, userPrompt, feedback);

                LlmRoundResponse llmRoundResponse;
                try {
                    llmRoundResponse = requestLanguageCodeResponse(systemPrompt, userPrompt);
                } catch (Exception exception) {
                    failLlmTraceItem(llmTraceItemId, round, null, exception.getMessage(), null);
                    throw exception;
                }
                String content = llmRoundResponse.content();
                TraceTokenUsageResult tokenUsage = llmRoundResponse.tokenUsage();

                LanguageCodeOutputDTO parsed;
                try {
                    parsed = parseLanguageCodeOutput(content);
                    parsed = sanitizeParsedOutput(parsed);
                } catch (Exception exception) {
                    failLlmTraceItem(llmTraceItemId, round, content, exception.getMessage(), tokenUsage);
                    throw exception;
                }

                String validationError = validateParsedOutput(parsed);
                if (validationError != null) {
                    failLlmTraceItem(llmTraceItemId, round, content, validationError, tokenUsage);
                    feedback = "Round %d output validation failed: %s".formatted(round, validationError);
                    continue;
                }
                completeLlmTraceItem(llmTraceItemId, round, content, parsed, null, tokenUsage);

                ExecutionValidationResult executionValidation = executeCandidate(
                        traceId,
                        round,
                        parsed,
                        sharedTestCaseBundle,
                        ignoreCollectionOrder
                );
                if (executionValidation.success()) {
                    output.setSuccess(true);
                    output.setFunctionName(parsed.getFunctionName().trim());
                    output.setCodeSkeleton(parsed.getCodeSkeleton());
                    output.setReferenceAnswer(parsed.getReferenceAnswer());
                    return Map.of(outputKey, output);
                }
                feedback = executionValidation.feedback();
                if (!executionValidation.retryable()) {
                    break;
                }
            }

            output.setErrorMessage(StringUtils.hasText(feedback)
                    ? "Language code generation failed after retries: " + feedback
                    : "Language code generation failed after retries");
        } catch (Exception exception) {
            output.setErrorMessage("Language code generation failed: " + exception.getMessage());
        }
        return Map.of(outputKey, output);
    }

    protected LlmRoundResponse requestLanguageCodeResponse(String systemPrompt, String userPrompt) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not initialized");
        }
        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();
        return new LlmRoundResponse(
                ChatResponseUtil.getText(chatResponse),
                ChatResponseUtil.extractTokenUsage(chatResponse)
        );
    }

    protected String callExecuteCodeTool(String requestJson) {
        return executeCodeTool.call(requestJson);
    }

    protected abstract String resolveExecutionRulesPrompt();

    private String validateParsedOutput(LanguageCodeOutputDTO output) {
        if (output == null) {
            return "Model output is empty";
        }
        if (!StringUtils.hasText(output.getFunctionName())) {
            return "Model output missing functionName";
        }
        if (!StringUtils.hasText(output.getCodeSkeleton())) {
            return "Model output missing codeSkeleton";
        }
        if (!StringUtils.hasText(output.getReferenceAnswer())) {
            return "Model output missing referenceAnswer";
        }
        String codeSkeletonError = validateCodeSkeleton(output.getCodeSkeleton(), output.getFunctionName());
        if (codeSkeletonError != null) {
            return codeSkeletonError;
        }
        return null;
    }

    private String validateCodeSkeleton(String codeSkeleton, String functionName) {
        if (!StringUtils.hasText(codeSkeleton)) {
            return "codeSkeleton cannot be empty";
        }
        if (containsComments(codeSkeleton)) {
            return "codeSkeleton must not contain comments";
        }

        String normalizedFunctionName = functionName == null ? "" : functionName.trim();
        return switch (language) {
            case PYTHON -> validatePythonSkeleton(codeSkeleton, normalizedFunctionName);
            case JAVASCRIPT -> validateJavaScriptSkeleton(codeSkeleton, normalizedFunctionName);
            case JAVA -> validateJavaSkeleton(codeSkeleton, normalizedFunctionName);
        };
    }

    private boolean containsComments(String code) {
        return code != null && COMMENT_PATTERN.matcher(code).find();
    }

    private String validatePythonSkeleton(String codeSkeleton, String functionName) {
        List<String> nonEmptyLines = codeSkeleton.lines()
                .map(String::stripTrailing)
                .filter(StringUtils::hasText)
                .toList();
        if (nonEmptyLines.size() != 2) {
            return "python codeSkeleton must contain only function signature and pass";
        }

        Matcher signatureMatcher = PYTHON_SIGNATURE_PATTERN.matcher(nonEmptyLines.get(0).trim());
        if (!signatureMatcher.matches()) {
            return "python codeSkeleton must start with def <functionName>(...):";
        }
        if (!signatureMatcher.group(1).equals(functionName)) {
            return "codeSkeleton function name must match functionName";
        }
        if (!"pass".equals(nonEmptyLines.get(1).trim())) {
            return "python codeSkeleton body must only contain pass";
        }
        return null;
    }

    private String validateJavaScriptSkeleton(String codeSkeleton, String functionName) {
        Matcher matcher = JAVASCRIPT_EMPTY_FUNCTION_PATTERN.matcher(codeSkeleton.trim());
        if (!matcher.matches()) {
            return "javascript codeSkeleton must be a single empty function body";
        }
        if (!matcher.group(1).equals(functionName)) {
            return "codeSkeleton function name must match functionName";
        }
        return null;
    }

    private String validateJavaSkeleton(String codeSkeleton, String functionName) {
        String trimmed = codeSkeleton.trim();
        if (trimmed.contains(" class ") || trimmed.startsWith("class ")) {
            return "java codeSkeleton must only contain method signature, not class wrapper";
        }
        Matcher matcher = JAVA_EMPTY_METHOD_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return "java codeSkeleton must be a single method with empty body";
        }
        if (!matcher.group(1).equals(functionName)) {
            return "codeSkeleton function name must match functionName";
        }
        return null;
    }

    private ConversationContext extractConversationContext(OverAllState state) {
        Object rawMessages = state.value(Constant.MESSAGES).orElse(null);
        if (!(rawMessages instanceof List<?> rawList) || rawList.isEmpty()) {
            return new ConversationContext(List.of(), "");
        }

        List<Message> messages = new ArrayList<>();
        for (Object raw : rawList) {
            if (raw instanceof Message message) {
                messages.add(message);
            }
        }
        if (messages.isEmpty()) {
            return new ConversationContext(List.of(), "");
        }

        Message lastMessage = messages.get(messages.size() - 1);
        List<Message> history = messages.size() > 1 ? messages.subList(0, messages.size() - 1) : List.of();
        return new ConversationContext(history, lastMessage.getText());
    }

    private record ConversationContext(List<Message> history, String userInputText) {
    }

    private String buildSystemPrompt() {
        Map<String, Object> params = new HashMap<>();
        params.put("language", language == null ? "" : language.getName());
        params.put("execution_rules", safeExecutionRulesPrompt());
        params.put("format", new BeanOutputConverter<>(LanguageCodeOutputDTO.class).getFormat());
        return PromptConstant.getLanguageCodeGenerationPromptTemplate().render(params);
    }

    private String buildUserPrompt(ConversationContext context,
                                   AlgorithmQuestionResult question,
                                   String feedback,
                                   int round) {
        String basePrompt = buildLanguageCodeUserPrompt(context, question);
        if (!StringUtils.hasText(feedback)) {
            return basePrompt + "\n\nRound: " + round;
        }
        return """
                %s

                Round: %d
                Previous execution feedback:
                %s

                Please fix the code according to feedback and regenerate all required fields.
                """.formatted(basePrompt, round, feedback);
    }

    private String buildLanguageCodeUserPrompt(ConversationContext context, AlgorithmQuestionResult question) {
        Map<String, Object> params = new HashMap<>();
        params.put("history", formatHistory(context.history()));
        params.put("input", context.userInputText() == null ? "" : context.userInputText());
        params.put("language", language == null ? "" : language.getName());
        params.put("question", formatSharedQuestion(question));
        return PromptConstant.getLanguageCodeUserPromptTemplate().render(params);
    }

    private String formatHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "- <empty>\n";
        }
        return messages.stream()
                .map(message -> "- " + message.getMessageType() + ": " + safeText(message.getText()))
                .reduce("", (left, right) -> left + right + "\n");
    }

    private String formatSharedQuestion(AlgorithmQuestionResult question) {
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

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private SharedTestCaseBundle parseSharedTestCases(String sharedTestCasesJson) throws Exception {
        if (!StringUtils.hasText(sharedTestCasesJson)) {
            throw new IllegalArgumentException("Shared test cases cannot be empty");
        }
        JsonNode testCasesNode = OBJECT_MAPPER.readTree(sharedTestCasesJson);
        if (!testCasesNode.isArray()) {
            throw new IllegalArgumentException("Shared test cases must be an array");
        }

        List<Map<String, Object>> testInputs = new ArrayList<>();
        List<Object> expectedOutputs = new ArrayList<>();
        for (JsonNode testCaseNode : testCasesNode) {
            JsonNode inputNode = testCaseNode.get("input");
            if (inputNode == null || !inputNode.isObject()) {
                throw new IllegalArgumentException("Each shared test case must contain object input");
            }
            testInputs.add(OBJECT_MAPPER.convertValue(inputNode, STRING_OBJECT_MAP_TYPE));

            JsonNode expectedOutputNode = testCaseNode.get("expectedOutput");
            expectedOutputs.add(expectedOutputNode == null || expectedOutputNode.isNull()
                    ? null
                    : OBJECT_MAPPER.convertValue(expectedOutputNode, Object.class));
        }
        return new SharedTestCaseBundle(testInputs, expectedOutputs);
    }

    private ExecutionValidationResult executeCandidate(String traceId,
                                                       int round,
                                                       LanguageCodeOutputDTO parsed,
                                                       SharedTestCaseBundle testCaseBundle,
                                                       boolean ignoreCollectionOrder) {
        String requestJson = null;
        String toolResponse = null;
        Long toolTraceItemId = null;
        try {
            CodeExecutionRequest request = new CodeExecutionRequest();
            request.setLanguage(language.getName());
            request.setCode(parsed.getReferenceAnswer());
            request.setFunctionName(parsed.getFunctionName().trim());
            request.setTestInputs(testCaseBundle.testInputs());
            request.setExpectedOutputs(testCaseBundle.expectedOutputs());
            if (ignoreCollectionOrder) {
                request.setIgnoreCollectionOrder(true);
            }

            requestJson = OBJECT_MAPPER.writeValueAsString(request);
            toolTraceItemId = startToolTraceItem(traceId, round, requestJson, parsed);
            toolResponse = callExecuteCodeTool(requestJson);

            CodeExecutionResult executionResult = OBJECT_MAPPER.readValue(toolResponse, CodeExecutionResult.class);
            if (executionResult != null && executionResult.isSuccess() && allTestsPassed(executionResult.getResults())) {
                completeToolTraceItem(toolTraceItemId, round, requestJson, toolResponse, executionResult, null, TraceStatusResult.SUCCESS);
                return ExecutionValidationResult.passed();
            }

            String feedback = buildExecutionFeedback(executionResult);
            completeToolTraceItem(toolTraceItemId, round, requestJson, toolResponse, executionResult, feedback, TraceStatusResult.SUCCESS);
            if (isEnvironmentFatal(feedback)) {
                return ExecutionValidationResult.fatal(feedback);
            }
            return ExecutionValidationResult.failed(feedback);
        } catch (Exception exception) {
            String feedback = "Execution tool invocation failed: " + exception.getMessage();
            failToolTraceItem(toolTraceItemId, round, requestJson, toolResponse, feedback);
            if (isEnvironmentFatal(feedback)) {
                return ExecutionValidationResult.fatal(feedback);
            }
            return ExecutionValidationResult.failed(feedback);
        }
    }

    private Long startLlmTraceItem(String traceId,
                                   int round,
                                   String systemPrompt,
                                   String userPrompt,
                                   String previousFeedback) {
        if (!canRecordTrace(traceId)) {
            return null;
        }
        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("round", round);
        inputData.put("language", language.getName());
        inputData.put("systemPrompt", systemPrompt);
        inputData.put("userPrompt", userPrompt);
        inputData.put("previousFeedback", previousFeedback);
        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.LLM)
                .itemKey("llm-" + round)
                .roundNo(round)
                .inputPayload(workflowTraceSupport.toLlmInputPayload(inputData))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    private void completeLlmTraceItem(Long traceItemId,
                                      int round,
                                      String rawOutput,
                                      LanguageCodeOutputDTO parsedOutput,
                                      String validationError,
                                      TraceTokenUsageResult tokenUsage) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceItemId == null) {
            return;
        }
        Map<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("round", round);
        outputData.put("language", language.getName());
        outputData.put("rawOutput", rawOutput);
        outputData.put("parsedOutput", parsedOutput);
        outputData.put("validationError", validationError);
        workflowTraceRecorder.completeTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(workflowTraceSupport.toLlmOutputPayload(outputData))
                .status(TraceStatusResult.SUCCESS)
                .tokenUsage(tokenUsage)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private void failLlmTraceItem(Long traceItemId,
                                  int round,
                                  String rawOutput,
                                  String errorMessage,
                                  TraceTokenUsageResult tokenUsage) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceItemId == null) {
            return;
        }
        Map<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("round", round);
        outputData.put("language", language.getName());
        outputData.put("rawOutput", rawOutput);
        outputData.put("errorMessage", safeErrorMessage(errorMessage));
        workflowTraceRecorder.failTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(workflowTraceSupport.toLlmOutputPayload(outputData))
                .errorMessage(safeErrorMessage(errorMessage))
                .status(TraceStatusResult.FAILED)
                .tokenUsage(tokenUsage)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private Long startToolTraceItem(String traceId,
                                    int round,
                                    String requestJson,
                                    LanguageCodeOutputDTO parsedOutput) {
        if (!canRecordTrace(traceId)) {
            return null;
        }
        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("round", round);
        inputData.put("language", language.getName());
        inputData.put("functionName", parsedOutput == null ? null : parsedOutput.getFunctionName());
        inputData.put("requestJson", requestJson);
        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.TOOL)
                .itemKey("tool-execute-code-" + round)
                .roundNo(round)
                .toolName(executeCodeTool == null ? "ExecuteCodeTool" : executeCodeTool.getClass().getSimpleName())
                .inputPayload(workflowTraceSupport.toToolInputPayload(inputData))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    private void completeToolTraceItem(Long traceItemId,
                                       int round,
                                       String requestJson,
                                       String toolResponse,
                                       CodeExecutionResult executionResult,
                                       String feedback,
                                       String status) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceItemId == null) {
            return;
        }
        Map<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("round", round);
        outputData.put("requestJson", requestJson);
        outputData.put("toolResponse", toolResponse);
        outputData.put("executionResult", executionResult);
        outputData.put("feedback", feedback);
        workflowTraceRecorder.completeTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(workflowTraceSupport.toToolOutputPayload(outputData))
                .status(status)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private void failToolTraceItem(Long traceItemId,
                                   int round,
                                   String requestJson,
                                   String toolResponse,
                                   String errorMessage) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceItemId == null) {
            return;
        }
        Map<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("round", round);
        outputData.put("requestJson", requestJson);
        outputData.put("toolResponse", toolResponse);
        outputData.put("errorMessage", safeErrorMessage(errorMessage));
        workflowTraceRecorder.failTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(workflowTraceSupport.toToolOutputPayload(outputData))
                .errorMessage(safeErrorMessage(errorMessage))
                .status(TraceStatusResult.FAILED)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private boolean canRecordTrace(String traceId) {
        return workflowTraceRecorder != null && workflowTraceSupport != null && StringUtils.hasText(traceId);
    }

    private String safeErrorMessage(String errorMessage) {
        return StringUtils.hasText(errorMessage) ? errorMessage : "unknown error";
    }

    private boolean allTestsPassed(List<TestResult> results) {
        if (results == null || results.isEmpty()) {
            return true;
        }
        for (TestResult result : results) {
            if (result == null || !result.isPassed()) {
                return false;
            }
        }
        return true;
    }

    private String buildExecutionFeedback(CodeExecutionResult executionResult) {
        if (executionResult == null) {
            return "Execution validation failed: empty execution result";
        }
        StringBuilder feedback = new StringBuilder("Execution validation failed.");
        if (StringUtils.hasText(executionResult.getErrorMessage())) {
            feedback.append(" errorMessage=").append(executionResult.getErrorMessage()).append('.');
        }
        List<TestResult> results = executionResult.getResults();
        if (results == null || results.isEmpty()) {
            return feedback.toString();
        }

        int failedCount = 0;
        for (int i = 0; i < results.size(); i++) {
            TestResult result = results.get(i);
            if (result == null || result.isPassed()) {
                continue;
            }
            failedCount++;
            feedback.append(" case#").append(i + 1);
            if (StringUtils.hasText(result.getError())) {
                feedback.append(" error=").append(result.getError());
            }
            if (result.getOutput() != null) {
                feedback.append(" output=").append(result.getOutput());
            }
            feedback.append('.');
            if (failedCount >= 3) {
                break;
            }
        }
        return feedback.toString();
    }

    private boolean isEnvironmentFatal(String feedback) {
        if (!StringUtils.hasText(feedback)) {
            return false;
        }
        String normalized = feedback.toLowerCase();
        return normalized.contains("no java compiler found")
                || normalized.contains("unsupported language");
    }

    private String safeExecutionRulesPrompt() {
        try {
            return resolveExecutionRulesPrompt();
        } catch (Exception exception) {
            return "";
        }
    }

    private LanguageCodeOutputDTO parseLanguageCodeOutput(String content) throws Exception {
        Exception jsonParseException = null;
        try {
            JsonNode root = parseOutputJson(content);
            JsonNode payload = locatePayloadNode(root);
            return buildLanguageCodeOutput(payload);
        } catch (Exception exception) {
            jsonParseException = exception;
        }

        LanguageCodeOutputDTO lenientOutput = parseLanguageCodeOutputLenient(content);
        if (hasAnyLanguageCodeField(lenientOutput)) {
            return lenientOutput;
        }
        throw jsonParseException;
    }

    private LanguageCodeOutputDTO buildLanguageCodeOutput(JsonNode payload) {
        LanguageCodeOutputDTO output = new LanguageCodeOutputDTO();
        output.setFunctionName(readText(payload, FUNCTION_NAME_FIELDS));
        output.setCodeSkeleton(readText(payload, CODE_SKELETON_FIELDS));
        output.setReferenceAnswer(readText(payload, REFERENCE_ANSWER_FIELDS));
        return output;
    }

    private LanguageCodeOutputDTO parseLanguageCodeOutputLenient(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        String trimmed = rawContent.trim();
        addCandidate(candidates, trimmed);
        addCandidate(candidates, stripMarkdownFence(trimmed));
        String extracted = extractJsonSegment(trimmed);
        addCandidate(candidates, extracted);
        addCandidate(candidates, stripMarkdownFence(extracted));

        for (String candidate : candidates) {
            LanguageCodeOutputDTO output = new LanguageCodeOutputDTO();
            output.setFunctionName(extractJsonLikeField(candidate, FUNCTION_NAME_FIELDS));
            output.setCodeSkeleton(extractJsonLikeField(candidate, CODE_SKELETON_FIELDS));
            output.setReferenceAnswer(extractJsonLikeField(candidate, REFERENCE_ANSWER_FIELDS));
            if (hasAnyLanguageCodeField(output)) {
                return output;
            }
        }
        return null;
    }

    private boolean hasAnyLanguageCodeField(LanguageCodeOutputDTO output) {
        if (output == null) {
            return false;
        }
        return StringUtils.hasText(output.getFunctionName())
                || StringUtils.hasText(output.getCodeSkeleton())
                || StringUtils.hasText(output.getReferenceAnswer());
    }

    private String extractJsonLikeField(String text, String... fieldNames) {
        if (!StringUtils.hasText(text) || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            String extracted = extractJsonLikeStringValue(text, fieldName);
            if (StringUtils.hasText(extracted)) {
                return extracted;
            }
        }
        return null;
    }

    private String extractJsonLikeStringValue(String text, String fieldName) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(fieldName)) {
            return null;
        }
        String key = "\"" + fieldName + "\"";
        int fromIndex = 0;
        while (fromIndex < text.length()) {
            int keyIndex = text.indexOf(key, fromIndex);
            if (keyIndex < 0) {
                return null;
            }
            int colonIndex = text.indexOf(':', keyIndex + key.length());
            if (colonIndex < 0) {
                return null;
            }
            int valueStart = skipWhitespace(text, colonIndex + 1);
            if (valueStart >= text.length() || text.charAt(valueStart) != '"') {
                fromIndex = keyIndex + key.length();
                continue;
            }
            StringReadResult parsed = readJsonLikeString(text, valueStart + 1);
            if (parsed != null) {
                return parsed.value();
            }
            fromIndex = keyIndex + key.length();
        }
        return null;
    }

    private StringReadResult readJsonLikeString(String text, int startIndex) {
        if (text == null || startIndex < 0 || startIndex >= text.length()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int index = startIndex; index < text.length(); index++) {
            char current = text.charAt(index);
            if (escaping) {
                builder.append(unescapeJsonCharacter(current));
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"' && isLikelyStringTerminator(text, index)) {
                return new StringReadResult(builder.toString(), index + 1);
            }
            builder.append(current);
        }
        return new StringReadResult(builder.toString(), text.length());
    }

    private boolean isLikelyStringTerminator(String text, int quoteIndex) {
        int next = skipWhitespace(text, quoteIndex + 1);
        if (next >= text.length()) {
            return true;
        }
        char current = text.charAt(next);
        if (current == '}') {
            return true;
        }
        if (current != ',') {
            return false;
        }
        int afterComma = skipWhitespace(text, next + 1);
        if (afterComma >= text.length()) {
            return true;
        }
        if (text.charAt(afterComma) == '}') {
            return true;
        }
        if (text.charAt(afterComma) != '"') {
            return false;
        }
        int keyEnd = text.indexOf('"', afterComma + 1);
        if (keyEnd < 0) {
            return false;
        }
        int afterKey = skipWhitespace(text, keyEnd + 1);
        return afterKey < text.length() && text.charAt(afterKey) == ':';
    }

    private int skipWhitespace(String text, int index) {
        int cursor = Math.max(0, index);
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private char unescapeJsonCharacter(char escaped) {
        return switch (escaped) {
            case 'n' -> '\n';
            case 'r' -> '\n';
            case 't' -> '\t';
            case '"' -> '"';
            case '\\' -> '\\';
            default -> escaped;
        };
    }

    private LanguageCodeOutputDTO sanitizeParsedOutput(LanguageCodeOutputDTO output) {
        if (output == null) {
            return null;
        }
        if (StringUtils.hasText(output.getFunctionName())) {
            output.setFunctionName(output.getFunctionName().trim());
        }
        output.setCodeSkeleton(sanitizeCodeField(output.getCodeSkeleton(), output.getFunctionName()));
        output.setReferenceAnswer(sanitizeCodeField(output.getReferenceAnswer(), output.getFunctionName()));
        return output;
    }

    private String sanitizeCodeField(String value, String functionName) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String sanitized = stripMarkdownFence(value).trim();
        sanitized = normalizeEscapedControlChars(sanitized);
        if (language == Language.JAVA) {
            sanitized = normalizeJavaOpeningOnlySkeleton(sanitized, functionName);
            String extractedMethod = extractJavaEmptyMethodSkeleton(sanitized, functionName);
            if (StringUtils.hasText(extractedMethod)) {
                return extractedMethod;
            }
        }
        return sanitized;
    }

    private String normalizeEscapedControlChars(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\\t", "\t");
    }

    private String normalizeJavaOpeningOnlySkeleton(String code, String functionName) {
        if (!StringUtils.hasText(code)) {
            return code;
        }
        Matcher matcher = JAVA_METHOD_OPENING_ONLY_PATTERN.matcher(code.trim());
        if (!matcher.matches()) {
            return code;
        }
        String normalizedFunctionName = functionName == null ? "" : functionName.trim();
        String candidateName = matcher.group(1);
        if (StringUtils.hasText(normalizedFunctionName) && !normalizedFunctionName.equals(candidateName)) {
            return code;
        }
        return code.trim() + "\n}";
    }

    private String extractJavaEmptyMethodSkeleton(String code, String functionName) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        Matcher fullMatcher = JAVA_EMPTY_METHOD_PATTERN.matcher(code);
        if (fullMatcher.matches()) {
            return code;
        }

        String normalizedFunctionName = functionName == null ? "" : functionName.trim();
        Matcher matcher = JAVA_EMPTY_METHOD_EXTRACT_PATTERN.matcher(code);
        while (matcher.find()) {
            String candidateName = matcher.group(1);
            if (!StringUtils.hasText(normalizedFunctionName) || normalizedFunctionName.equals(candidateName)) {
                return matcher.group().trim();
            }
        }
        return null;
    }

    private JsonNode parseOutputJson(String rawContent) throws Exception {
        if (!StringUtils.hasText(rawContent)) {
            throw new IllegalArgumentException("Model output is empty");
        }

        String trimmed = rawContent.trim();
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, trimmed);
        addCandidate(candidates, stripMarkdownFence(trimmed));

        String extractedFromTrimmed = extractJsonSegment(trimmed);
        addCandidate(candidates, extractedFromTrimmed);
        addCandidate(candidates, stripMarkdownFence(extractedFromTrimmed));

        Exception lastException = null;
        for (String candidate : candidates) {
            try {
                return OBJECT_MAPPER.readTree(candidate);
            } catch (Exception exception) {
                lastException = exception;
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalArgumentException("Unable to parse model output as JSON");
    }

    private JsonNode locatePayloadNode(JsonNode root) {
        if (containsLanguageCodeFields(root)) {
            return root;
        }
        if (root == null || !root.isObject()) {
            return root;
        }

        String[] wrapperFields = {"data", "result", "output", "payload"};
        for (String wrapperField : wrapperFields) {
            JsonNode candidate = root.get(wrapperField);
            if (containsLanguageCodeFields(candidate)) {
                return candidate;
            }
        }
        return root;
    }

    private boolean containsLanguageCodeFields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        return node.has("functionName")
                || node.has("function_name")
                || node.has("codeSkeleton")
                || node.has("code_skeleton")
                || node.has("referenceAnswer")
                || node.has("reference_answer");
    }

    private void addCandidate(List<String> candidates, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isEmpty() && !candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private String stripMarkdownFence(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstLineBreak = trimmed.indexOf('\n');
        if (firstLineBreak < 0) {
            return trimmed;
        }
        int closingFence = trimmed.lastIndexOf("```");
        if (closingFence <= firstLineBreak) {
            return trimmed;
        }
        return trimmed.substring(firstLineBreak + 1, closingFence).trim();
    }

    private String extractJsonSegment(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String trimmed = text.trim();

        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }

        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }

        return trimmed;
    }

    private String readText(JsonNode root, String... fieldNames) {
        if (root == null || fieldNames == null || fieldNames.length == 0) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (!StringUtils.hasText(fieldName)) {
                continue;
            }
            JsonNode fieldNode = root.get(fieldName);
            if (fieldNode == null || fieldNode.isNull()) {
                continue;
            }
            String value = fieldNode.asText();
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected record LlmRoundResponse(String content, TraceTokenUsageResult tokenUsage) {
    }

    private record StringReadResult(String value, int nextIndex) {
    }

    private record SharedTestCaseBundle(List<Map<String, Object>> testInputs, List<Object> expectedOutputs) {
    }

    private record ExecutionValidationResult(boolean success, String feedback, boolean retryable) {
        private static ExecutionValidationResult passed() {
            return new ExecutionValidationResult(true, null, false);
        }

        private static ExecutionValidationResult failed(String feedback) {
            return new ExecutionValidationResult(false, feedback, true);
        }

        private static ExecutionValidationResult fatal(String feedback) {
            return new ExecutionValidationResult(false, feedback, false);
        }
    }
}
