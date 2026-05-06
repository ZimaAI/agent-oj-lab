package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.question.enums.Language;
import com.oj.agent.core.workflow.enums.TextType;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.evaluation.converter.CodeEvaluationConverter;
import com.oj.agent.core.evaluation.model.dto.CodeEvaluationLLMOutputDTO;
import com.oj.agent.core.evaluation.model.result.CodeEvaluationResult;
import com.oj.agent.core.executor.tool.ExecutionComparisonPolicy;
import com.oj.agent.core.executor.tool.ExecuteCodeTool;
import com.oj.agent.core.executor.tool.model.CodeExecutionRequest;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.question.model.result.AlgorithmCodeTemplateResult;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.submission.model.result.CodeSubmissionResult;
import com.oj.agent.core.aimodel.prompt.PromptHelper;
import com.oj.agent.core.question.service.AlgorithmCodeService;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.core.workflow.trace.WorkflowTraceSupport;
import com.oj.agent.core.trace.model.command.TraceItemFinishCommand;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.result.TraceItemTypeResult;
import com.oj.agent.core.trace.model.result.TraceStatusResult;
import com.oj.agent.core.workflow.model.result.CodeEvaluationNodeResult;
import com.oj.agent.core.workflow.util.ChatResponseUtil;
import com.oj.agent.core.workflow.util.FluxUtil;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CodeEvaluationNode implements NodeAction {

    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP_TYPE = new TypeReference<>() {
    };

    private final ChatClient chatClient;
    private final ExecuteCodeTool executeCodeTool;
    private final AlgorithmCodeService algorithmCodeService;
    private final ObjectMapper objectMapper;
    private final WorkflowTraceRecorder workflowTraceRecorder;
    private final WorkflowTraceSupport workflowTraceSupport;

    public CodeEvaluationNode(AiModelRegistry aiModelRegistry,
                              ExecuteCodeTool executeCodeTool,
                              AlgorithmCodeService algorithmCodeService,
                              WorkflowTraceRecorder workflowTraceRecorder,
                              WorkflowTraceSupport workflowTraceSupport) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
        this.executeCodeTool = executeCodeTool;
        this.algorithmCodeService = algorithmCodeService;
        this.objectMapper = JsonUtils.getObjectMapper();
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        return Map.of(Constant.CODE_EVALUATION_NODE_OUTPUT, createGenerator(state));
    }

    Flux<GraphResponse<StreamingOutput>> createGenerator(OverAllState state) {
        try {
            EvaluationContext context = resolveEvaluationContext(state);
            if (context.errorMessage() != null) {
                return createErrorResult(state, context.errorMessage());
            }

            String executionResult = executeCodeSafely(context);
            String prompt = PromptHelper.buildCodeEvaluationPrompt(
                    context.question(),
                    context.submission(),
                    executionResult,
                    context.codeMetadata().getFunctionName()
            );
            String llmContent = requestEvaluationContent(context.traceId(), prompt);
            Flux<ChatResponse> sourceFlux = Flux.just(ChatResponseUtil.createPureResponse(llmContent));

            return FluxUtil.createStreamingGenerator(
                    this.getClass(),
                    state,
                    sourceFlux,
                    Flux.just(
                            ChatResponseUtil.createResponse("开始代码评测"),
                            ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())
                    ),
                    Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())),
                    result -> Map.of(
                            Constant.CODE_EVALUATION_NODE_OUTPUT,
                            parseEvaluationResult(result, executionResult, context.submission())
                    )
            );
        } catch (Exception exception) {
            log.error("Code evaluation failed", exception);
            return createErrorResult(state, "Code evaluation failed: " + exception.getMessage());
        }
    }

    private EvaluationContext resolveEvaluationContext(OverAllState state) {
        AlgorithmQuestionResult question = StateUtil.getObjectValue(
                state,
                Constant.CURRENT_ALGORITHM_QUESTION,
                AlgorithmQuestionResult.class,
                (AlgorithmQuestionResult) null
        );
        if (question == null || question.getId() == null || question.getId() <= 0) {
            return EvaluationContext.error("Current algorithm question is missing");
        }
        if (!StringUtils.hasText(question.getSharedTestCases())) {
            return EvaluationContext.error("Shared test cases are missing");
        }

        CodeSubmissionResult submission = StateUtil.getObjectValue(
                state,
                Constant.CURRENT_CODE_SUBMISSION,
                CodeSubmissionResult.class,
                (CodeSubmissionResult) null
        );
        if (submission == null) {
            return EvaluationContext.error("Current code submission is missing");
        }

        Language language;
        try {
            language = resolveSubmissionLanguage(submission.getLanguage());
        } catch (IllegalArgumentException exception) {
            return EvaluationContext.error(exception.getMessage());
        }
        if (!executeCodeTool.getSupportedLanguages().contains(language)) {
            return EvaluationContext.error("Execution language is not enabled: " + language.getName());
        }

        AlgorithmCodeTemplateResult codeMetadata = algorithmCodeService.getCodeTemplateByQuestionIdAndLanguage(
                question.getId(),
                language.getName()
        );
        if (codeMetadata == null || !StringUtils.hasText(codeMetadata.getFunctionName())) {
            return EvaluationContext.error("Language code metadata does not exist: " + language.getName());
        }

        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);
        return EvaluationContext.success(question, submission, codeMetadata, traceId);
    }

    private String executeCodeSafely(EvaluationContext context) {
        Long traceItemId = null;
        String requestJson = null;
        String toolResponse = null;
        try {
            CodeExecutionRequest request = buildExecutionRequest(context);
            requestJson = objectMapper.writeValueAsString(request);
            traceItemId = startToolTraceItem(context.traceId(), requestJson, request);
            toolResponse = executeCodeTool.call(requestJson);
            completeToolTraceItem(traceItemId, requestJson, toolResponse);
            return toolResponse;
        } catch (Exception exception) {
            String fallbackResult = buildToolFailureExecutionResult(exception);
            failToolTraceItem(traceItemId, requestJson, toolResponse, exception.getMessage(), fallbackResult);
            return fallbackResult;
        }
    }

    private CodeExecutionRequest buildExecutionRequest(EvaluationContext context) throws JsonProcessingException {
        CodeExecutionRequest request = new CodeExecutionRequest();
        request.setLanguage(resolveSubmissionLanguage(context.submission().getLanguage()).getName());
        request.setCode(context.submission().getCode());
        request.setFunctionName(context.codeMetadata().getFunctionName());
        request.setTestInputs(parseTestInputs(context.question().getSharedTestCases()));
        request.setExpectedOutputs(parseExpectedOutputs(context.question().getSharedTestCases()));
        if (ExecutionComparisonPolicy.shouldIgnoreCollectionOrder(context.question().getDescription())) {
            request.setIgnoreCollectionOrder(true);
        }
        return request;
    }

    private List<Map<String, Object>> parseTestInputs(String sharedTestCasesJson) throws JsonProcessingException {
        JsonNode testCasesNode = objectMapper.readTree(sharedTestCasesJson);
        if (!testCasesNode.isArray()) {
            throw new IllegalArgumentException("sharedTestCases must be an array");
        }

        List<Map<String, Object>> testInputs = new ArrayList<>();
        for (JsonNode testCaseNode : testCasesNode) {
            JsonNode inputNode = testCaseNode.get("input");
            if (inputNode == null || !inputNode.isObject()) {
                throw new IllegalArgumentException("Each test case must have an object input");
            }
            testInputs.add(objectMapper.convertValue(inputNode, STRING_OBJECT_MAP_TYPE));
        }
        return testInputs;
    }

    private List<Object> parseExpectedOutputs(String sharedTestCasesJson) throws JsonProcessingException {
        JsonNode testCasesNode = objectMapper.readTree(sharedTestCasesJson);
        if (!testCasesNode.isArray()) {
            throw new IllegalArgumentException("sharedTestCases must be an array");
        }
        List<Object> expectedOutputs = new ArrayList<>();
        for (JsonNode testCaseNode : testCasesNode) {
            JsonNode expectedNode = testCaseNode.get("expectedOutput");
            expectedOutputs.add(expectedNode == null || expectedNode.isNull()
                    ? null
                    : objectMapper.convertValue(expectedNode, Object.class));
        }
        return expectedOutputs;
    }

    private String requestEvaluationContent(String traceId, String prompt) {
        Long traceItemId = startLlmTraceItem(traceId, prompt);
        if (chatClient == null) {
            String reason = "ChatClient is not initialized";
            String fallback = defaultEvaluationJson(reason);
            failLlmTraceItem(traceItemId, fallback, reason);
            return fallback;
        }
        try {
            String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (StringUtils.hasText(content)) {
                completeLlmTraceItem(traceItemId, content);
                return content;
            }
            String reason = "LLM returned empty content";
            String fallback = defaultEvaluationJson(reason);
            failLlmTraceItem(traceItemId, fallback, reason);
            return fallback;
        } catch (Exception exception) {
            String reason = "LLM evaluation failed: " + exception.getMessage();
            String fallback = defaultEvaluationJson(reason);
            failLlmTraceItem(traceItemId, fallback, reason);
            return fallback;
        }
    }

    private CodeEvaluationNodeResult parseEvaluationResult(String llmResult,
                                                           String executionResult,
                                                           CodeSubmissionResult submission) {
        try {
            BeanOutputConverter<CodeEvaluationLLMOutputDTO> converter =
                    new BeanOutputConverter<>(CodeEvaluationLLMOutputDTO.class);
            CodeEvaluationLLMOutputDTO llmOutput = converter.convert(llmResult);

            CodeEvaluationResult evaluation = CodeEvaluationConverter.toResult(
                    llmOutput,
                    submission,
                    executionResult,
                    "CodeEvaluationNode"
            );

            CodeEvaluationNodeResult output = new CodeEvaluationNodeResult();
            output.setSuccess(true);
            output.setCodeEvaluation(evaluation);
            return output;
        } catch (Exception exception) {
            return createErrorOutput("LLM evaluation parsing failed: " + exception.getMessage());
        }
    }

    private Long startToolTraceItem(String traceId, String requestJson, CodeExecutionRequest request) {
        if (!canRecordTrace(traceId)) {
            return null;
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("round", 1);
        input.put("requestJson", requestJson);
        input.put("request", request);
        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.TOOL)
                .itemKey("tool-execute-code-1")
                .roundNo(1)
                .toolName(executeCodeTool == null ? "ExecuteCodeTool" : executeCodeTool.getClass().getSimpleName())
                .inputPayload(workflowTraceSupport.toToolInputPayload(input))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    private void completeToolTraceItem(Long traceItemId, String requestJson, String toolResponse) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceItemId == null) {
            return;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("round", 1);
        output.put("requestJson", requestJson);
        output.put("toolResponse", toolResponse);
        workflowTraceRecorder.completeTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(workflowTraceSupport.toToolOutputPayload(output))
                .status(TraceStatusResult.SUCCESS)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private void failToolTraceItem(Long traceItemId,
                                   String requestJson,
                                   String toolResponse,
                                   String errorMessage,
                                   String fallbackResult) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceItemId == null) {
            return;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("round", 1);
        output.put("requestJson", requestJson);
        output.put("toolResponse", toolResponse);
        output.put("fallbackResult", fallbackResult);
        output.put("errorMessage", errorMessage);
        workflowTraceRecorder.failTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(workflowTraceSupport.toToolOutputPayload(output))
                .errorMessage(errorMessage)
                .status(TraceStatusResult.FAILED)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private Long startLlmTraceItem(String traceId, String prompt) {
        if (!canRecordTrace(traceId)) {
            return null;
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("round", 1);
        input.put("prompt", prompt);
        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.LLM)
                .itemKey("llm-1")
                .roundNo(1)
                .inputPayload(workflowTraceSupport.toLlmInputPayload(input))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    private void completeLlmTraceItem(Long traceItemId, String content) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceItemId == null) {
            return;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("round", 1);
        output.put("content", content);
        workflowTraceRecorder.completeTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(workflowTraceSupport.toLlmOutputPayload(output))
                .status(TraceStatusResult.SUCCESS)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private void failLlmTraceItem(Long traceItemId, String content, String errorMessage) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceItemId == null) {
            return;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("round", 1);
        output.put("content", content);
        output.put("errorMessage", errorMessage);
        workflowTraceRecorder.failTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(workflowTraceSupport.toLlmOutputPayload(output))
                .errorMessage(errorMessage)
                .status(TraceStatusResult.FAILED)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private boolean canRecordTrace(String traceId) {
        return workflowTraceRecorder != null && workflowTraceSupport != null && StringUtils.hasText(traceId);
    }

    private Language resolveSubmissionLanguage(String rawLanguage) {
        if (!StringUtils.hasText(rawLanguage)) {
            throw new IllegalArgumentException("Submission language cannot be empty");
        }
        return Language.fromName(rawLanguage);
    }

    private String buildToolFailureExecutionResult(Exception exception) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", false);
            payload.put("results", List.of());
            payload.put("errorMessage", exception == null ? null : exception.getMessage());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception serializationException) {
            throw new IllegalStateException("Failed to serialize tool failure result", serializationException);
        }
    }

    private String defaultEvaluationJson(String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("correctnessScore", 0);
        payload.put("timeComplexityScore", 0);
        payload.put("spaceComplexityScore", 0);
        payload.put("overallScore", 0);
        payload.put("timeComplexityAnalysis", reason);
        payload.put("spaceComplexityAnalysis", reason);
        payload.put("codeQualityAnalysis", reason);
        payload.put("suggestions", "Please retry after fixing execution/runtime issues.");
        payload.put("summary", reason);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"overallScore\":0,\"summary\":\"" + reason + "\"}";
        }
    }

    private CodeEvaluationNodeResult createErrorOutput(String errorMessage) {
        CodeEvaluationNodeResult output = new CodeEvaluationNodeResult();
        output.setSuccess(false);
        output.setErrorMessage(errorMessage);
        output.setCodeEvaluation(CodeEvaluationConverter.toErrorResult(errorMessage));
        return output;
    }

    private Flux<GraphResponse<StreamingOutput>> createErrorResult(OverAllState state, String errorMessage) {
        CodeEvaluationNodeResult errorOutput = createErrorOutput(errorMessage);
        return FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                Flux.empty(),
                Flux.empty(),
                Flux.empty(),
                result -> Map.of(Constant.CODE_EVALUATION_NODE_OUTPUT, errorOutput)
        );
    }

    private record EvaluationContext(AlgorithmQuestionResult question,
                                     CodeSubmissionResult submission,
                                     AlgorithmCodeTemplateResult codeMetadata,
                                     String traceId,
                                     String errorMessage) {

        private static EvaluationContext success(AlgorithmQuestionResult question,
                                                 CodeSubmissionResult submission,
                                                 AlgorithmCodeTemplateResult codeMetadata,
                                                 String traceId) {
            return new EvaluationContext(question, submission, codeMetadata, traceId, null);
        }

        private static EvaluationContext error(String errorMessage) {
            return new EvaluationContext(null, null, null, null, errorMessage);
        }
    }
}
