package com.oj.agent.core.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.core.workflow.enums.UserIntentType;
import com.oj.agent.core.question.converter.AlgorithmQuestionConverter;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import com.oj.agent.core.workflow.model.dto.CodeQuestionLLMOutputDTO;
import com.oj.agent.core.workflow.model.dto.CodeQuestionOutputDTO;
import com.oj.agent.core.workflow.model.dto.IntentRecognitionOutputDTO;
import com.oj.agent.core.workflow.model.dto.QuestionRAGOutputDTO;
import com.oj.agent.core.workflow.model.dto.QuestionRewriteOutputDTO;
import com.oj.agent.core.workflow.model.dto.SharedTestCaseDTO;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.aimodel.runtime.registry.AiModelRegistry;
import com.oj.agent.core.aimodel.prompt.PromptHelper;
import com.oj.agent.core.workflow.trace.WorkflowTraceRecorder;
import com.oj.agent.core.workflow.trace.WorkflowTraceSupport;
import com.oj.agent.core.trace.model.command.TraceItemFinishCommand;
import com.oj.agent.core.trace.model.command.TraceItemStartCommand;
import com.oj.agent.core.trace.model.result.TracePayloadResult;
import com.oj.agent.core.trace.model.result.TraceItemTypeResult;
import com.oj.agent.core.trace.model.result.TraceStatusResult;
import com.oj.agent.core.trace.model.result.TraceTokenUsageResult;
import com.oj.agent.core.workflow.util.ChatResponseUtil;
import com.oj.agent.core.workflow.util.FluxUtil;
import com.oj.agent.core.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CodeQuestionNode implements NodeAction {

    private static final String GENERATION_SYSTEM_PROMPT = """
            ## 角色
            你是一个专业的算法题生成助手。
            ## 任务
            只生成共享题目信息，不生成任何语言专属代码内容。
            ## 约束
            1. 只输出完整 JSON。
            2. 不调用任何工具。
            3. 必须输出字段：title、description、difficulty、sharedFunctionName、sharedCodeSkeleton、sharedTestCases、tags。
            4. 不要输出 language、referenceAnswer 或任何语言代码实现。
            5. sharedCodeSkeleton 只能是单行函数签名伪代码，格式必须为：FUNCTION <sharedFunctionName>(...)。
            6. sharedCodeSkeleton 不能包含方法体、注释、控制流或返回语句。
            7. 若 expectedOutput 存在多个正确答案，必须使用 {"$anyOf":[...]}，且 $anyOf 必须为非空数组。
            8. 若 expectedOutput 是顺序无关集合，必须使用 {"$unordered": ...}，且 $unordered 的值不能为空。
            9. 保留键 $anyOf / $unordered 必须单独出现，不能与其他字段混用；嵌套结构同样适用。
            """;
    private static final Pattern SHARED_SIGNATURE_PATTERN =
            Pattern.compile("^FUNCTION\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^\\n\\r]*\\)\\s*$");
    private static final Pattern SHARED_FORBIDDEN_TOKEN_PATTERN =
            Pattern.compile("(?i)(//|#|/\\*|\\*/|\\bRETURN\\b|\\bIF\\b|\\bELSE\\b|\\bFOR\\b|\\bWHILE\\b|\\bSWITCH\\b|\\bCASE\\b|\\bTRY\\b|\\bCATCH\\b|\\{|\\}|;|\\bdef\\b|\\bpublic\\b|\\bclass\\b)");
    private static final int MAX_SHARED_GENERATION_ATTEMPTS = 3;
    private static final String INTERSECTION_FUNCTION_NAME = "getIntersectionNode";
    private static final String LRU_CACHE_FUNCTION_NAME = "LRUCache";
    private static final String PATH_SUM_THREE_FUNCTION_NAME = "pathSum";
    private static final String NUMBER_OF_ISLANDS_FUNCTION_NAME = "numIslands";
    private static final String PARTITION_LABELS_FUNCTION_NAME = "partitionLabels";
    private static final String DETECT_CYCLE_FUNCTION_NAME = "detectCycle";
    private static final String REMOVE_NTH_FROM_END_FUNCTION_NAME = "removeNthFromEnd";
    private static final String COPY_RANDOM_LIST_FUNCTION_NAME = "copyRandomList";
    private static final String SORTED_ARRAY_TO_BST_FUNCTION_NAME = "sortedArrayToBST";
    private static final String BUILD_TREE_FUNCTION_NAME = "buildTree";
    private static final String MIN_STACK_FUNCTION_NAME = "MinStack";
    private static final List<String> LIKELY_MUTATION_OPERATION_PREFIXES = List.of(
            "add", "put", "set", "update", "insert", "delete", "remove",
            "clear", "push", "pop", "enqueue", "dequeue", "append"
    );

    private final ChatClient chatClient;
    private final WorkflowTraceRecorder workflowTraceRecorder;
    private final WorkflowTraceSupport workflowTraceSupport;
    private ObjectMapper objectMapper = JsonUtils.getObjectMapper();

    public CodeQuestionNode(AiModelRegistry aiModelRegistry,
                            WorkflowTraceRecorder workflowTraceRecorder,
                            WorkflowTraceSupport workflowTraceSupport) {
        this.chatClient = aiModelRegistry == null ? null : aiModelRegistry.getDefaultChatClient();
        this.workflowTraceRecorder = workflowTraceRecorder;
        this.workflowTraceSupport = workflowTraceSupport;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        return Map.of(Constant.CODE_QUESTION_NODE_OUTPUT, createGenerator(state));
    }

    Flux<GraphResponse<StreamingOutput>> createGenerator(OverAllState state) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not initialized");
        }

        log.info("Starting shared-only code question generation process");

        try {
            QuestionGenerationContext context = resolveQuestionGenerationContext(state);
            if (context.errorMessage() != null) {
                return createErrorResult(state, context.errorMessage());
            }

            GenerationAttemptResult generationResult = generateSharedQuestionJson(
                    context.traceId(),
                    context.history(),
                    context.userInputText(),
                    context.intent(),
                    context.currentQuestion(),
                    context.rewrittenQuestion(),
                    context.referenceQuestions()
            );
            if (!generationResult.success()) {
                return createErrorResult(state, generationResult.errorMessage());
            }

            return createSuccessStreamingResult(state, generationResult.finalJson());
        } catch (Exception e) {
            log.error("Error during code question generation", e);
            return createErrorResult(state, "Error: " + e.getMessage());
        }
    }

    private QuestionGenerationContext resolveQuestionGenerationContext(OverAllState state) {
        Map<String, Object> stateData = extractStateData(state);
        if (stateData == null) {
            return QuestionGenerationContext.error("Missing required state data");
        }

        String precheckError = (String) stateData.get("errorMessage");
        if (precheckError != null) {
            return QuestionGenerationContext.error(precheckError);
        }

        UserIntentType intent = (UserIntentType) stateData.get("intent");
        AlgorithmQuestionResult currentQuestion = (AlgorithmQuestionResult) stateData.get("currentQuestion");
        String rewrittenQuestion = (String) stateData.get("rewrittenQuestion");
        @SuppressWarnings("unchecked")
        List<AlgorithmQuestionResult> referenceQuestions =
                (List<AlgorithmQuestionResult>) stateData.get("referenceQuestions");

        List<Message> history = StateUtil.getHistory(state);
        UserMessage userInput = StateUtil.getUserInput(state);
        String userInputText = userInput == null ? "" : userInput.getText();
        String traceId = StateUtil.getStringValue(state, Constant.TRACE_ID, null);

        return QuestionGenerationContext.success(
                intent,
                currentQuestion,
                rewrittenQuestion,
                referenceQuestions,
                history,
                userInputText,
                traceId
        );
    }

    private Flux<GraphResponse<StreamingOutput>> createSuccessStreamingResult(OverAllState state,
                                                                               String finalQuestionJson) {
        return FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                Flux.empty(),
                Flux.just(ChatResponseUtil.createResponse("正在生成算法题")),
                Flux.empty(),
                ignored -> parseAndBuildResult(finalQuestionJson)
        );
    }

    private GenerationAttemptResult generateSharedQuestionJson(
            String traceId,
            List<Message> history,
            String userInputText,
            UserIntentType intent,
            AlgorithmQuestionResult currentQuestion,
            String rewrittenQuestion,
            List<AlgorithmQuestionResult> referenceQuestions) {

        List<Message> baseMessages = new ArrayList<>();
        baseMessages.add(new SystemMessage(GENERATION_SYSTEM_PROMPT));
        baseMessages.add(new SystemMessage(PromptHelper.buildCodeQuestionPrompt(
                intent,
                currentQuestion,
                referenceQuestions
        )));
        baseMessages.add(new UserMessage(PromptHelper.buildCodeQuestionUserPrompt(
                history,
                userInputText,
                rewrittenQuestion,
                intent,
                currentQuestion
        )));

        String latestErrorMessage = null;
        for (int round = 1; round <= MAX_SHARED_GENERATION_ATTEMPTS; round++) {
            List<Message> attemptMessages = new ArrayList<>(baseMessages);
            if (hasText(latestErrorMessage)) {
                attemptMessages.add(new SystemMessage(buildRetryCorrectionPrompt(latestErrorMessage)));
            }
            Long llmTraceItemId = startLlmTrace(traceId, attemptMessages, round);

            String draftJson;
            TraceTokenUsageResult tokenUsage = null;
            try {
                ChatResponse llmResponse = chatClient.prompt()
                        .messages(attemptMessages)
                        .call()
                        .chatResponse();
                tokenUsage = ChatResponseUtil.extractTokenUsage(llmResponse);
                draftJson = ChatResponseUtil.getText(llmResponse);
            } catch (Exception e) {
                latestErrorMessage = "题目生成失败: " + e.getMessage();
                failLlmTrace(llmTraceItemId, null, latestErrorMessage, tokenUsage);
                continue;
            }

            if (!hasText(draftJson)) {
                latestErrorMessage = "模型未返回题目草稿";
                failLlmTrace(llmTraceItemId, draftJson, latestErrorMessage, tokenUsage);
                continue;
            }

            try {
                CodeQuestionLLMOutputDTO draft = parseCodeQuestionOutput(draftJson);
                // 对高频不稳定题型做用例标准化，避免异常 matcher 结构导致后续语言生成失败。
                normalizeSpecialQuestionDraft(draft);
                String validationError = validateRequiredSharedFields(draft);
                if (validationError != null) {
                    latestErrorMessage = validationError;
                    failLlmTrace(llmTraceItemId, draftJson, validationError, tokenUsage);
                    continue;
                }

                completeLlmTraceSuccess(llmTraceItemId, draftJson, draft, tokenUsage);
                return GenerationAttemptResult.success(toNormalizedQuestionJson(draft));
            } catch (Exception e) {
                latestErrorMessage = "共享题目 JSON 解析失败: " + e.getMessage();
                failLlmTrace(llmTraceItemId, draftJson, latestErrorMessage, tokenUsage);
            }
        }
        if (!hasText(latestErrorMessage)) {
            latestErrorMessage = "共享题目生成失败：超过最大重试次数";
        }
        return GenerationAttemptResult.failure(latestErrorMessage);
    }

    private String validateRequiredSharedFields(CodeQuestionLLMOutputDTO draft) {
        if (draft == null) {
            return "共享题目输出为空";
        }
        if (!hasText(draft.getTitle())) {
            return "共享题目缺少 title";
        }
        if (!hasText(draft.getDescription())) {
            return "共享题目缺少 description";
        }
        if (!hasText(draft.getDifficulty())) {
            return "共享题目缺少 difficulty";
        }
        if (!hasText(draft.getSharedFunctionName())) {
            return "共享题目缺少 sharedFunctionName";
        }
        if (!hasText(draft.getSharedCodeSkeleton())) {
            return "共享题目缺少 sharedCodeSkeleton";
        }
        String skeletonValidationError = validateSharedCodeSkeleton(
                draft.getSharedFunctionName(),
                draft.getSharedCodeSkeleton()
        );
        if (skeletonValidationError != null) {
            return skeletonValidationError;
        }
        if (draft.getSharedTestCases() == null || draft.getSharedTestCases().isEmpty()) {
            return "共享题目缺少 sharedTestCases";
        }
        // 校验 expectedOutput matcher 结构，避免非法结构进入后续流程。
        String matcherValidationError = validateMatcherSharedTestCases(draft.getSharedTestCases());
        if (matcherValidationError != null) {
            return matcherValidationError;
        }
        // 保留并继续执行现有 operations 题型规则校验。
        String operationsValidationError = validateOperationsSharedTestCases(
                draft.getSharedFunctionName(),
                draft.getSharedCodeSkeleton(),
                draft.getSharedTestCases()
        );
        if (operationsValidationError != null) {
            return operationsValidationError;
        }
        return null;
    }

    // 识别并标准化特殊题型的测试用例，降低模型输出抖动对后续节点的影响。
    private void normalizeSpecialQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        if (draft == null) {
            return;
        }
        if (isIntersectionLinkedListQuestion(draft)) {
            normalizeIntersectionQuestionDraft(draft);
            return;
        }
        if (isLruCacheQuestion(draft)) {
            normalizeLruCacheQuestionDraft(draft);
            return;
        }
        if (isDetectCycleQuestion(draft)) {
            normalizeDetectCycleQuestionDraft(draft);
            return;
        }
        if (isRemoveNthFromEndQuestion(draft)) {
            normalizeRemoveNthFromEndQuestionDraft(draft);
            return;
        }
        if (isCopyRandomListQuestion(draft)) {
            normalizeCopyRandomListQuestionDraft(draft);
            return;
        }
        if (isPathSumThreeQuestion(draft)) {
            normalizePathSumThreeQuestionDraft(draft);
            return;
        }
        if (isSortedArrayToBstQuestion(draft)) {
            normalizeSortedArrayToBstQuestionDraft(draft);
            return;
        }
        if (isBuildTreeQuestion(draft)) {
            normalizeBuildTreeQuestionDraft(draft);
            return;
        }
        if (isMinStackQuestion(draft)) {
            normalizeMinStackQuestionDraft(draft);
            return;
        }
        if (isNumberOfIslandsQuestion(draft)) {
            normalizeNumberOfIslandsQuestionDraft(draft);
            return;
        }
        if (isPartitionLabelsQuestion(draft)) {
            normalizePartitionLabelsQuestionDraft(draft);
        }
    }

    // 标准化相交链表题，强制使用稳定函数签名与固定测试集。
    private void normalizeIntersectionQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(INTERSECTION_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + INTERSECTION_FUNCTION_NAME + "(headA, headB)");
        draft.setSharedTestCases(buildCanonicalIntersectionTestCases());
        if (!hasText(draft.getTitle())) {
            draft.setTitle("相交链表");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        draft.setTags(mergeIntersectionTags(draft.getTags()));
    }

    // 识别 LRU 缓存题型。
    private boolean isLruCacheQuestion(CodeQuestionLLMOutputDTO draft) {
        String functionName = safeText(draft.getSharedFunctionName());
        if (LRU_CACHE_FUNCTION_NAME.equalsIgnoreCase(functionName)) {
            return true;
        }
        String title = safeText(draft.getTitle()).toLowerCase();
        String description = safeText(draft.getDescription()).toLowerCase();
        return title.contains("lru") || description.contains("lru");
    }

    // 标准化 LRU 缓存测试集，修复常见 expectedOutput 漏 null 和错误淘汰顺序问题。
    private void normalizeLruCacheQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(LRU_CACHE_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + LRU_CACHE_FUNCTION_NAME + "(capacity)");
        draft.setSharedTestCases(buildCanonicalLruCacheTestCases());
        if (!hasText(draft.getTitle())) {
            draft.setTitle("LRU 缓存");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        List<String> tags = new ArrayList<>(normalizeTags(draft.getTags()));
        ensureTagExists(tags, "哈希表");
        ensureTagExists(tags, "双向链表");
        ensureTagExists(tags, "设计");
        draft.setTags(tags);
    }

    // 构造 LRU 缓存固定测试用例，覆盖更新、淘汰和容量边界。
    private List<SharedTestCaseDTO> buildCanonicalLruCacheTestCases() {
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        testCases.add(buildOperationsTestCase(
                2,
                List.of(
                        List.of("put", List.of(1, 1)),
                        List.of("put", List.of(2, 2)),
                        List.of("get", List.of(1)),
                        List.of("put", List.of(3, 3)),
                        List.of("get", List.of(2)),
                        List.of("put", List.of(4, 4)),
                        List.of("get", List.of(1)),
                        List.of("get", List.of(3)),
                        List.of("get", List.of(4))
                ),
                listWithNullableValues(null, null, 1, null, -1, null, -1, 3, 4),
                "基础淘汰顺序校验"
        ));
        testCases.add(buildOperationsTestCase(
                2,
                List.of(
                        List.of("put", List.of(2, 1)),
                        List.of("put", List.of(2, 2)),
                        List.of("get", List.of(2)),
                        List.of("put", List.of(1, 1)),
                        List.of("put", List.of(4, 1)),
                        List.of("get", List.of(2))
                ),
                listWithNullableValues(null, null, 2, null, null, -1),
                "更新已存在键并验证淘汰"
        ));
        testCases.add(buildOperationsTestCase(
                3,
                List.of(
                        List.of("put", List.of(1, 1)),
                        List.of("put", List.of(2, 2)),
                        List.of("get", List.of(1)),
                        List.of("put", List.of(3, 3)),
                        List.of("get", List.of(2)),
                        List.of("put", List.of(4, 4)),
                        List.of("get", List.of(1)),
                        List.of("get", List.of(3)),
                        List.of("get", List.of(4))
                ),
                listWithNullableValues(null, null, 1, null, 2, null, -1, 3, 4),
                "容量为 3 的淘汰顺序校验"
        ));
        return testCases;
    }

    // 识别环形链表 II 题型。
    private boolean isDetectCycleQuestion(CodeQuestionLLMOutputDTO draft) {
        String title = safeText(draft.getTitle()).toLowerCase();
        String functionName = safeText(draft.getSharedFunctionName()).toLowerCase();
        String description = safeText(draft.getDescription()).toLowerCase();
        return functionName.equals(DETECT_CYCLE_FUNCTION_NAME.toLowerCase())
                || functionName.equals("detect_cycle")
                || title.contains("环形链表 ii")
                || title.contains("linked list cycle ii")
                || description.contains("返回入环节点");
    }

    // 标准化环形链表 II 测试集，统一以入环节点值作为期望输出。
    private void normalizeDetectCycleQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(DETECT_CYCLE_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + DETECT_CYCLE_FUNCTION_NAME + "(head, pos)");
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        testCases.add(buildSimpleTestCase(
                Map.of(
                        "head", List.of(3, 2, 0, -4),
                        "pos", 1
                ),
                2,
                "入环节点值为 2"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of(
                        "head", List.of(1, 2),
                        "pos", 0
                ),
                1,
                "入环节点值为 1"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of(
                        "head", List.of(1),
                        "pos", -1
                ),
                null,
                "无环时返回 null"
        ));
        draft.setSharedTestCases(testCases);
        if (!hasText(draft.getTitle())) {
            draft.setTitle("环形链表 II");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        List<String> tags = new ArrayList<>(normalizeTags(draft.getTags()));
        ensureTagExists(tags, "链表");
        ensureTagExists(tags, "双指针");
        ensureTagExists(tags, "快慢指针");
        draft.setTags(tags);
    }

    // 识别路径总和 III 题型。
    private boolean isPathSumThreeQuestion(CodeQuestionLLMOutputDTO draft) {
        String title = safeText(draft.getTitle()).toLowerCase();
        String functionName = safeText(draft.getSharedFunctionName()).toLowerCase();
        String description = safeText(draft.getDescription()).toLowerCase();
        return title.contains("路径总和 iii")
                || title.contains("path sum iii")
                || functionName.contains("pathsum")
                || functionName.contains("path_sum")
                || description.contains("路径总和 iii");
    }

    // 标准化路径总和 III 用例，修复常见树节点值和 expectedOutput 偏差。
    private void normalizePathSumThreeQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(PATH_SUM_THREE_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + PATH_SUM_THREE_FUNCTION_NAME + "(root, targetSum)");
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        testCases.add(buildSimpleTestCase(
                Map.of(
                        "root", listWithNullableValues(10, 5, -3, 3, 2, null, 11, 3, -2, null, 1),
                        "targetSum", 8
                ),
                3,
                "经典样例：目标和为 8 的路径条数"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of(
                        "root", listWithNullableValues(5, 4, 8, 11, null, 13, 4, 7, 2, null, null, 5, 1),
                        "targetSum", 22
                ),
                3,
                "包含多条可行路径的中等树结构"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of(
                        "root", List.of(1, 2, 3),
                        "targetSum", 5
                ),
                0,
                "无可行路径时返回 0"
        ));
        draft.setSharedTestCases(testCases);
        if (!hasText(draft.getTitle())) {
            draft.setTitle("路径总和 III");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        List<String> tags = new ArrayList<>(normalizeTags(draft.getTags()));
        ensureTagExists(tags, "二叉树");
        ensureTagExists(tags, "前缀和");
        ensureTagExists(tags, "深度优先搜索");
        draft.setTags(tags);
    }

    // 识别岛屿数量题型。
    private boolean isNumberOfIslandsQuestion(CodeQuestionLLMOutputDTO draft) {
        String title = safeText(draft.getTitle()).toLowerCase();
        String functionName = safeText(draft.getSharedFunctionName()).toLowerCase();
        return title.contains("岛屿数量")
                || title.contains("number of islands")
                || functionName.contains("numislands")
                || functionName.contains("num_islands");
    }

    // 标准化岛屿数量测试集，修复常见连通分量 expectedOutput 漏算问题。
    private void normalizeNumberOfIslandsQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(NUMBER_OF_ISLANDS_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + NUMBER_OF_ISLANDS_FUNCTION_NAME + "(grid)");
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        testCases.add(buildSimpleTestCase(
                Map.of("grid", List.of(
                        List.of("1", "1", "1", "1", "0"),
                        List.of("1", "1", "0", "1", "0"),
                        List.of("1", "1", "0", "0", "0"),
                        List.of("0", "0", "0", "0", "0")
                )),
                1,
                "单连通块场景"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of("grid", List.of(
                        List.of("1", "1", "0", "0", "0"),
                        List.of("1", "1", "0", "0", "0"),
                        List.of("0", "0", "1", "0", "0"),
                        List.of("0", "0", "0", "1", "1")
                )),
                3,
                "多个独立岛屿场景"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of("grid", List.of(
                        List.of("0", "0", "0"),
                        List.of("0", "0", "0"),
                        List.of("0", "0", "0")
                )),
                0,
                "全水域场景"
        ));
        draft.setSharedTestCases(testCases);
        if (!hasText(draft.getTitle())) {
            draft.setTitle("岛屿数量");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        List<String> tags = new ArrayList<>(normalizeTags(draft.getTags()));
        ensureTagExists(tags, "图");
        ensureTagExists(tags, "深度优先搜索");
        ensureTagExists(tags, "广度优先搜索");
        draft.setTags(tags);
    }

    // 识别划分字母区间题型。
    private boolean isPartitionLabelsQuestion(CodeQuestionLLMOutputDTO draft) {
        String title = safeText(draft.getTitle()).toLowerCase();
        String functionName = safeText(draft.getSharedFunctionName()).toLowerCase();
        return title.contains("划分字母区间")
                || title.contains("partition labels")
                || functionName.contains("partitionlabels")
                || functionName.contains("partition_labels");
    }

    // 标准化划分字母区间测试集，并允许长度/字符串分段两种合法返回风格。
    private void normalizePartitionLabelsQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(PARTITION_LABELS_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + PARTITION_LABELS_FUNCTION_NAME + "(s)");
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        testCases.add(buildSimpleTestCase(
                Map.of("s", "ababcbacadefegdehijhklij"),
                buildAnyOfExpected(
                        List.of(9, 7, 8),
                        List.of("ababcbaca", "defegde", "hijhklij")
                ),
                "官方经典样例"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of("s", "eccbbbbdec"),
                buildAnyOfExpected(
                        List.of(10),
                        List.of("eccbbbbdec")
                ),
                "单分段场景"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of("s", "caedbdedda"),
                buildAnyOfExpected(
                        List.of(1, 9),
                        List.of("c", "aedbdedda")
                ),
                "两分段场景"
        ));
        draft.setSharedTestCases(testCases);
        if (!hasText(draft.getTitle())) {
            draft.setTitle("划分字母区间");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        List<String> tags = new ArrayList<>(normalizeTags(draft.getTags()));
        ensureTagExists(tags, "贪心");
        ensureTagExists(tags, "字符串");
        draft.setTags(tags);
    }

    // 识别删除链表的倒数第 N 个结点题型。
    private boolean isRemoveNthFromEndQuestion(CodeQuestionLLMOutputDTO draft) {
        String title = safeText(draft.getTitle()).toLowerCase();
        String functionName = safeText(draft.getSharedFunctionName()).toLowerCase();
        String description = safeText(draft.getDescription()).toLowerCase();
        return functionName.equals(REMOVE_NTH_FROM_END_FUNCTION_NAME.toLowerCase())
                || functionName.equals("remove_nth_from_end")
                || title.contains("删除链表的倒数第")
                || title.contains("remove nth")
                || description.contains("倒数第 n");
    }

    // 标准化删除链表倒数节点测试集，保留头删场景并规避空链表语义抖动。
    private void normalizeRemoveNthFromEndQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(REMOVE_NTH_FROM_END_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + REMOVE_NTH_FROM_END_FUNCTION_NAME + "(head, n)");
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        // 按函数签名顺序写入参数，避免部分执行器按位置绑定时发生 head/n 交换。
        Map<String, Object> caseOneInput = new LinkedHashMap<>();
        caseOneInput.put("head", List.of(1, 2, 3, 4, 5));
        caseOneInput.put("n", 2);
        testCases.add(buildSimpleTestCase(
                caseOneInput,
                List.of(1, 2, 3, 5),
                "删除中间节点"
        ));
        // 按函数签名顺序写入参数，避免部分执行器按位置绑定时发生 head/n 交换。
        Map<String, Object> caseTwoInput = new LinkedHashMap<>();
        caseTwoInput.put("head", List.of(1, 2, 3));
        caseTwoInput.put("n", 3);
        testCases.add(buildSimpleTestCase(
                caseTwoInput,
                List.of(2, 3),
                "删除头节点但保留非空链表"
        ));
        // 按函数签名顺序写入参数，避免部分执行器按位置绑定时发生 head/n 交换。
        Map<String, Object> caseThreeInput = new LinkedHashMap<>();
        caseThreeInput.put("head", List.of(1, 2));
        caseThreeInput.put("n", 1);
        testCases.add(buildSimpleTestCase(
                caseThreeInput,
                List.of(1),
                "删除尾节点"
        ));
        draft.setSharedTestCases(testCases);
        if (!hasText(draft.getTitle())) {
            draft.setTitle("删除链表的倒数第 N 个结点");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        List<String> tags = new ArrayList<>(normalizeTags(draft.getTags()));
        ensureTagExists(tags, "链表");
        ensureTagExists(tags, "双指针");
        draft.setTags(tags);
    }

    // 识别随机链表复制题型。
    private boolean isCopyRandomListQuestion(CodeQuestionLLMOutputDTO draft) {
        String title = safeText(draft.getTitle()).toLowerCase();
        String functionName = safeText(draft.getSharedFunctionName()).toLowerCase();
        String description = safeText(draft.getDescription()).toLowerCase();
        return functionName.equals(COPY_RANDOM_LIST_FUNCTION_NAME.toLowerCase())
                || functionName.equals("copy_random_list")
                || title.contains("随机链表的复制")
                || title.contains("copy list with random pointer")
                || description.contains("random")
                || description.contains("随机指针");
    }

    // 标准化随机链表复制测试集，统一采用 [val, randomIndex] 结构化表示。
    private void normalizeCopyRandomListQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(COPY_RANDOM_LIST_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + COPY_RANDOM_LIST_FUNCTION_NAME + "(head)");
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        List<Object> sampleCaseOne = List.of(
                listWithNullableValues(7, null),
                listWithNullableValues(13, 0),
                listWithNullableValues(11, 4),
                listWithNullableValues(10, 2),
                listWithNullableValues(1, 0)
        );
        testCases.add(buildSimpleTestCase(
                Map.of("head", sampleCaseOne),
                sampleCaseOne,
                "官方经典样例"
        ));
        List<Object> sampleCaseTwo = List.of(
                listWithNullableValues(1, 1),
                listWithNullableValues(2, 1)
        );
        testCases.add(buildSimpleTestCase(
                Map.of("head", sampleCaseTwo),
                sampleCaseTwo,
                "双节点互相关联场景"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of("head", List.of()),
                List.of(),
                "空链表场景"
        ));
        draft.setSharedTestCases(testCases);
        if (!hasText(draft.getTitle())) {
            draft.setTitle("随机链表的复制");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        List<String> tags = new ArrayList<>(normalizeTags(draft.getTags()));
        ensureTagExists(tags, "链表");
        ensureTagExists(tags, "哈希表");
        ensureTagExists(tags, "深拷贝");
        draft.setTags(tags);
    }

    // 识别有序数组转平衡 BST 题型。
    private boolean isSortedArrayToBstQuestion(CodeQuestionLLMOutputDTO draft) {
        String title = safeText(draft.getTitle()).toLowerCase();
        String functionName = safeText(draft.getSharedFunctionName()).toLowerCase();
        String description = safeText(draft.getDescription()).toLowerCase();
        return functionName.equals(SORTED_ARRAY_TO_BST_FUNCTION_NAME.toLowerCase())
                || functionName.equals("sorted_array_to_bst")
                || title.contains("将有序数组转换为二叉搜索树")
                || title.contains("sorted array to binary search tree")
                || description.contains("平衡二叉搜索树");
    }

    // 标准化有序数组转 BST 测试集，优先使用奇数长度数组避免结构歧义。
    private void normalizeSortedArrayToBstQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(SORTED_ARRAY_TO_BST_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + SORTED_ARRAY_TO_BST_FUNCTION_NAME + "(nums)");
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        testCases.add(buildSimpleTestCase(
                Map.of("nums", List.of(-10, -3, 0, 5, 9, 12, 15)),
                List.of(5, -3, 12, -10, 0, 9, 15),
                "长度为 7 的平衡树结构"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of("nums", List.of(-1, 0, 1)),
                List.of(0, -1, 1),
                "长度为 3 的平衡树结构"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of("nums", List.of(1)),
                List.of(1),
                "单节点场景"
        ));
        draft.setSharedTestCases(testCases);
        if (!hasText(draft.getTitle())) {
            draft.setTitle("将有序数组转换为二叉搜索树");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        List<String> tags = new ArrayList<>(normalizeTags(draft.getTags()));
        ensureTagExists(tags, "二叉树");
        ensureTagExists(tags, "二叉搜索树");
        ensureTagExists(tags, "分治");
        draft.setTags(tags);
    }

    // 识别前序+中序构建二叉树题型。
    private boolean isBuildTreeQuestion(CodeQuestionLLMOutputDTO draft) {
        String title = safeText(draft.getTitle()).toLowerCase();
        String functionName = safeText(draft.getSharedFunctionName()).toLowerCase();
        String description = safeText(draft.getDescription()).toLowerCase();
        return functionName.equals(BUILD_TREE_FUNCTION_NAME.toLowerCase())
                || functionName.equals("build_tree")
                || title.contains("从前序与中序遍历序列构造二叉树")
                || title.contains("construct binary tree from preorder and inorder")
                || description.contains("前序")
                || description.contains("中序");
    }

    // 标准化前序+中序构树测试集，修正单链偏斜树期望层序表示。
    private void normalizeBuildTreeQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(BUILD_TREE_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + BUILD_TREE_FUNCTION_NAME + "(preorder, inorder)");
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        // 按函数签名顺序写入参数，避免部分执行器按位置绑定时发生 preorder/inorder 交换。
        Map<String, Object> caseOneInput = new LinkedHashMap<>();
        caseOneInput.put("preorder", List.of(3, 9, 20, 15, 7));
        caseOneInput.put("inorder", List.of(9, 3, 15, 20, 7));
        testCases.add(buildSimpleTestCase(
                caseOneInput,
                listWithNullableValues(3, 9, 20, null, null, 15, 7),
                "官方经典样例"
        ));
        // 按函数签名顺序写入参数，避免部分执行器按位置绑定时发生 preorder/inorder 交换。
        Map<String, Object> caseTwoInput = new LinkedHashMap<>();
        caseTwoInput.put("preorder", List.of(-1));
        caseTwoInput.put("inorder", List.of(-1));
        testCases.add(buildSimpleTestCase(
                caseTwoInput,
                List.of(-1),
                "单节点场景"
        ));
        // 按函数签名顺序写入参数，避免部分执行器按位置绑定时发生 preorder/inorder 交换。
        Map<String, Object> caseThreeInput = new LinkedHashMap<>();
        caseThreeInput.put("preorder", List.of(1, 2, 3));
        caseThreeInput.put("inorder", List.of(3, 2, 1));
        testCases.add(buildSimpleTestCase(
                caseThreeInput,
                listWithNullableValues(1, 2, null, 3),
                "左斜树场景"
        ));
        draft.setSharedTestCases(testCases);
        if (!hasText(draft.getTitle())) {
            draft.setTitle("从前序与中序遍历序列构造二叉树");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        List<String> tags = new ArrayList<>(normalizeTags(draft.getTags()));
        ensureTagExists(tags, "二叉树");
        ensureTagExists(tags, "递归");
        ensureTagExists(tags, "分治");
        draft.setTags(tags);
    }

    // 识别最小栈题型。
    private boolean isMinStackQuestion(CodeQuestionLLMOutputDTO draft) {
        String title = safeText(draft.getTitle()).toLowerCase();
        String functionName = safeText(draft.getSharedFunctionName()).toLowerCase();
        String description = safeText(draft.getDescription()).toLowerCase();
        return functionName.equals(MIN_STACK_FUNCTION_NAME.toLowerCase())
                || functionName.equals("min_stack")
                || title.contains("最小栈")
                || title.contains("min stack")
                || description.contains("getmin");
    }

    // 标准化最小栈测试集，避免空栈查询并覆盖重复最小值回退场景。
    private void normalizeMinStackQuestionDraft(CodeQuestionLLMOutputDTO draft) {
        draft.setSharedFunctionName(MIN_STACK_FUNCTION_NAME);
        draft.setSharedCodeSkeleton("FUNCTION " + MIN_STACK_FUNCTION_NAME + "()");
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        testCases.add(buildSimpleTestCase(
                Map.of("operations", List.of(
                        List.of("MinStack", List.of()),
                        List.of("push", List.of(-2)),
                        List.of("push", List.of(0)),
                        List.of("push", List.of(-3)),
                        List.of("getMin", List.of()),
                        List.of("pop", List.of()),
                        List.of("top", List.of()),
                        List.of("getMin", List.of())
                )),
                listWithNullableValues(null, null, null, null, -3, null, 0, -2),
                "基础功能与最小值回退"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of("operations", List.of(
                        List.of("MinStack", List.of()),
                        List.of("push", List.of(2)),
                        List.of("push", List.of(0)),
                        List.of("push", List.of(3)),
                        List.of("push", List.of(0)),
                        List.of("getMin", List.of()),
                        List.of("pop", List.of()),
                        List.of("getMin", List.of()),
                        List.of("pop", List.of()),
                        List.of("getMin", List.of()),
                        List.of("pop", List.of()),
                        List.of("getMin", List.of())
                )),
                listWithNullableValues(null, null, null, null, null, 0, null, 0, null, 0, null, 2),
                "重复最小值场景"
        ));
        testCases.add(buildSimpleTestCase(
                Map.of("operations", List.of(
                        List.of("MinStack", List.of()),
                        List.of("push", List.of(5)),
                        List.of("push", List.of(1)),
                        List.of("top", List.of()),
                        List.of("getMin", List.of()),
                        List.of("pop", List.of()),
                        List.of("top", List.of()),
                        List.of("getMin", List.of())
                )),
                listWithNullableValues(null, null, null, 1, 1, null, 5, 5),
                "栈顶与最小值联动校验"
        ));
        draft.setSharedTestCases(testCases);
        if (!hasText(draft.getTitle())) {
            draft.setTitle("最小栈");
        }
        if (!hasText(draft.getDifficulty())) {
            draft.setDifficulty("MEDIUM");
        }
        List<String> tags = new ArrayList<>(normalizeTags(draft.getTags()));
        ensureTagExists(tags, "栈");
        ensureTagExists(tags, "设计");
        ensureTagExists(tags, "数据结构");
        draft.setTags(tags);
    }

    // 构建操作序列类测试用例（如 LRU/最小栈）。
    private SharedTestCaseDTO buildOperationsTestCase(int capacity,
                                                      List<List<Object>> operations,
                                                      List<Object> expectedOutputs,
                                                      String description) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("capacity", capacity);
        input.put("operations", operations);
        return buildSimpleTestCase(input, expectedOutputs, description);
    }

    // 构建普通输入输出测试用例。
    private SharedTestCaseDTO buildSimpleTestCase(Map<String, Object> input,
                                                  Object expectedOutput,
                                                  String description) {
        SharedTestCaseDTO testCase = new SharedTestCaseDTO();
        testCase.setInput(new LinkedHashMap<>(input));
        testCase.setExpectedOutput(expectedOutput);
        testCase.setDescription(description);
        return testCase;
    }

    // 构建 $anyOf matcher，兼容同题多种合法输出形态。
    private Map<String, Object> buildAnyOfExpected(Object... candidates) {
        List<Object> candidateList = new ArrayList<>();
        if (candidates != null) {
            for (Object candidate : candidates) {
                candidateList.add(candidate);
            }
        }
        Map<String, Object> matcher = new LinkedHashMap<>();
        matcher.put("$anyOf", candidateList);
        return matcher;
    }

    // 构建允许包含 null 的列表，避免 List.of(null) 触发运行时异常。
    private List<Object> listWithNullableValues(Object... values) {
        List<Object> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (Object value : values) {
            result.add(value);
        }
        return result;
    }

    // 识别“相交链表”题型，优先使用函数名，其次用标题/描述关键词兜底。
    private boolean isIntersectionLinkedListQuestion(CodeQuestionLLMOutputDTO draft) {
        if (draft == null) {
            return false;
        }
        if (INTERSECTION_FUNCTION_NAME.equalsIgnoreCase(safeText(draft.getSharedFunctionName()))) {
            return true;
        }
        String title = safeText(draft.getTitle()).toLowerCase();
        String description = safeText(draft.getDescription()).toLowerCase();
        boolean titleMatched = title.contains("相交链表") || title.contains("intersection");
        boolean descriptionMatched = description.contains("相交")
                || description.contains("intersection")
                || (description.contains("heada") && description.contains("headb"));
        return titleMatched || descriptionMatched;
    }

    // 构造稳定且无歧义的相交链表测试用例，统一补充 intersectVal。
    private List<SharedTestCaseDTO> buildCanonicalIntersectionTestCases() {
        List<SharedTestCaseDTO> testCases = new ArrayList<>();
        testCases.add(buildIntersectionTestCase(
                List.of(4, 1, 8, 4, 5),
                List.of(5, 6, 8, 4, 5),
                8,
                "两链表在值为 8 的节点处相交"
        ));
        testCases.add(buildIntersectionTestCase(
                List.of(2, 6, 4),
                List.of(1, 5),
                null,
                "两链表不相交，返回 null"
        ));
        testCases.add(buildIntersectionTestCase(
                List.of(1, 9, 1, 2, 4),
                List.of(3, 2, 4),
                2,
                "两链表在值为 2 的节点处相交"
        ));
        return testCases;
    }

    // 构建单条相交链表测试用例，并将 intersectVal 显式写入 input。
    private SharedTestCaseDTO buildIntersectionTestCase(List<Integer> headA,
                                                        List<Integer> headB,
                                                        Object intersectVal,
                                                        String description) {
        SharedTestCaseDTO testCase = new SharedTestCaseDTO();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("headA", new ArrayList<>(headA));
        input.put("headB", new ArrayList<>(headB));
        input.put("intersectVal", intersectVal);
        testCase.setInput(input);
        testCase.setExpectedOutput(intersectVal);
        testCase.setDescription(description);
        return testCase;
    }

    // 合并相交链表基础标签，避免覆盖模型已生成的其他业务标签。
    private List<String> mergeIntersectionTags(List<String> existingTags) {
        List<String> merged = new ArrayList<>(normalizeTags(existingTags));
        ensureTagExists(merged, "链表");
        ensureTagExists(merged, "双指针");
        ensureTagExists(merged, "相交链表");
        return merged;
    }

    // 按精确值补齐标签，保持标签集合去重。
    private void ensureTagExists(List<String> tags, String expectedTag) {
        if (!hasText(expectedTag)) {
            return;
        }
        for (String tag : tags) {
            if (expectedTag.equals(tag)) {
                return;
            }
        }
        tags.add(expectedTag);
    }

    // 空安全字符串读取，避免空值判断分散在各业务分支。
    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    // 逐条校验 expectedOutput 中 matcher 结构是否符合约束。
    private String validateMatcherSharedTestCases(List<SharedTestCaseDTO> sharedTestCases) {
        if (sharedTestCases == null || sharedTestCases.isEmpty()) {
            return null;
        }
        for (int index = 0; index < sharedTestCases.size(); index++) {
            SharedTestCaseDTO testCase = sharedTestCases.get(index);
            if (testCase == null) {
                continue;
            }
            String validationError = validateMatcherNode(
                    testCase.getExpectedOutput(),
                    "sharedTestCases[%d].expectedOutput".formatted(index)
            );
            if (validationError != null) {
                return validationError;
            }
        }
        return null;
    }

    // 递归校验 matcher 节点，支持对象、数组和字符串 JSON。
    private String validateMatcherNode(Object matcherNode, String path) {
        Object normalizedNode = tryParseMatcherValue(matcherNode);
        if (normalizedNode instanceof Map<?, ?> matcherObject) {
            return validateMatcherObject(matcherObject, path);
        }
        if (normalizedNode instanceof List<?> matcherList) {
            for (int index = 0; index < matcherList.size(); index++) {
                String childError = validateMatcherNode(
                        matcherList.get(index),
                        path + "[" + index + "]"
                );
                if (childError != null) {
                    return childError;
                }
            }
            return null;
        }
        if (normalizedNode != null && normalizedNode.getClass().isArray()) {
            int arrayLength = Array.getLength(normalizedNode);
            for (int index = 0; index < arrayLength; index++) {
                String childError = validateMatcherNode(
                        Array.get(normalizedNode, index),
                        path + "[" + index + "]"
                );
                if (childError != null) {
                    return childError;
                }
            }
        }
        return null;
    }

    // 校验 matcher 对象中的保留键规则，并继续递归校验嵌套值。
    private String validateMatcherObject(Map<?, ?> rawMatcherObject, String path) {
        Map<String, Object> matcherObject = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMatcherObject.entrySet()) {
            matcherObject.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        boolean hasAnyOf = matcherObject.containsKey("$anyOf");
        boolean hasUnordered = matcherObject.containsKey("$unordered");
        if (hasAnyOf || hasUnordered) {
            if (matcherObject.size() != 1) {
                return "%s 使用 matcher 保留键时必须单独出现，不能与其他字段混用"
                        .formatted(path);
            }
            if (hasAnyOf) {
                return validateAnyOfMatcher(matcherObject.get("$anyOf"), path + ".$anyOf");
            }
            return validateUnorderedMatcher(matcherObject.get("$unordered"), path + ".$unordered");
        }

        for (Map.Entry<String, Object> entry : matcherObject.entrySet()) {
            String childError = validateMatcherNode(
                    entry.getValue(),
                    path + "." + entry.getKey()
            );
            if (childError != null) {
                return childError;
            }
        }
        return null;
    }

    // 校验 $anyOf 必须是非空数组，并递归校验每个候选值。
    private String validateAnyOfMatcher(Object anyOfToken, String path) {
        Object normalizedAnyOf = tryParseMatcherValue(anyOfToken);
        if (!(normalizedAnyOf instanceof List<?> candidates) || candidates.isEmpty()) {
            return "%s 必须是非空数组".formatted(path);
        }
        for (int index = 0; index < candidates.size(); index++) {
            String childError = validateMatcherNode(
                    candidates.get(index),
                    path + "[" + index + "]"
            );
            if (childError != null) {
                return childError;
            }
        }
        return null;
    }

    // 校验 $unordered 值不能为空，并递归校验其嵌套结构。
    private String validateUnorderedMatcher(Object unorderedToken, String path) {
        Object normalizedUnordered = tryParseMatcherValue(unorderedToken);
        if (isEmptyMatcherValue(normalizedUnordered)) {
            return "%s 的值不能为空".formatted(path);
        }
        return validateMatcherNode(normalizedUnordered, path);
    }

    // 将字符串形式的 JSON matcher 转换为结构化对象，便于统一递归校验。
    private Object tryParseMatcherValue(Object value) {
        if (!(value instanceof String textValue)) {
            return value;
        }
        String trimmedValue = textValue.trim();
        if (!hasText(trimmedValue)) {
            return textValue;
        }
        if (!trimmedValue.startsWith("{") && !trimmedValue.startsWith("[")) {
            return textValue;
        }
        try {
            return objectMapper.readValue(trimmedValue, Object.class);
        } catch (Exception ignored) {
            return textValue;
        }
    }

    // 统一判定 matcher 值是否为空。
    private boolean isEmptyMatcherValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String textValue) {
            String trimmedValue = textValue.trim();
            return !hasText(trimmedValue) || "null".equalsIgnoreCase(trimmedValue);
        }
        if (value instanceof List<?> listValue) {
            return listValue.isEmpty();
        }
        if (value instanceof Map<?, ?> mapValue) {
            return mapValue.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) == 0;
        }
        return false;
    }

    private String validateOperationsSharedTestCases(String sharedFunctionName,
                                                     String sharedCodeSkeleton,
                                                     List<SharedTestCaseDTO> sharedTestCases) {
        if (!hasText(sharedFunctionName) || sharedTestCases == null || sharedTestCases.isEmpty()) {
            return null;
        }
        String normalizedFunctionName = sharedFunctionName.trim();
        boolean parameterlessConstructorAllowed = isParameterlessSharedConstructor(
                normalizedFunctionName,
                sharedCodeSkeleton
        );
        for (int index = 0; index < sharedTestCases.size(); index++) {
            SharedTestCaseDTO testCase = sharedTestCases.get(index);
            if (testCase == null || testCase.getInput() == null || testCase.getInput().isEmpty()) {
                continue;
            }
            Map<String, Object> input = testCase.getInput();
            Object operationsToken = input.get("operations");
            if (!(operationsToken instanceof List<?> operations) || operations.isEmpty()) {
                continue;
            }
            for (int operationIndex = 0; operationIndex < operations.size(); operationIndex++) {
                if (!isValidOperationToken(operations.get(operationIndex))) {
                    return "sharedTestCases[%d].input.operations[%d] 格式非法，必须是数组/对象形式的操作定义"
                            .formatted(index, operationIndex);
                }
            }
            boolean hasConstructorArgsOutsideOperations = input.entrySet().stream()
                    .anyMatch(entry -> !"operations".equals(entry.getKey()) && entry.getValue() != null);
            boolean hasConstructorOperation = isConstructorOperation(operations.get(0), normalizedFunctionName);
            if (!hasConstructorArgsOutsideOperations && !hasConstructorOperation && !parameterlessConstructorAllowed) {
                return "sharedTestCases[%d] 包含 operations 但缺少构造参数，请在 input 中提供构造参数字段或将第一条 operation 设为构造调用"
                        .formatted(index);
            }
            boolean expectedOutputPresent = hasExpectedOutput(testCase.getExpectedOutput());
            int queryOperationCount = countLikelyQueryOperations(operations, normalizedFunctionName);
            if (expectedOutputPresent && queryOperationCount == 0) {
                return "sharedTestCases[%d] 的 expectedOutput 非空，但 operations 中缺少可返回结果的查询操作"
                        .formatted(index);
            }
            int queryBeforeMutationIndex = findQueryBeforeMutationOperationIndex(
                    operations,
                    normalizedFunctionName
            );
            if (expectedOutputPresent
                    && parameterlessConstructorAllowed
                    && queryBeforeMutationIndex >= 0) {
                return "sharedTestCases[%d] 在无参构造题中，operations[%d] 在任何写操作前执行了查询；请先执行 add/put/push 等写操作，再执行查询操作"
                        .formatted(index, queryBeforeMutationIndex);
            }
            if (expectedOutputPresent
                    && queryOperationCount > 1
                    && !isCollectionLikeExpectedOutput(testCase.getExpectedOutput())) {
                return "sharedTestCases[%d] 的 expectedOutput 非空且 operations 中包含多个查询操作时，expectedOutput 必须是数组"
                        .formatted(index);
            }
        }
        return null;
    }

    private boolean isValidOperationToken(Object operationToken) {
        if (operationToken instanceof List<?> operationList) {
            return !operationList.isEmpty() && operationList.get(0) != null;
        }
        if (operationToken instanceof Map<?, ?> operationMap) {
            Object operationName = operationMap.get("method");
            if (operationName == null) {
                operationName = operationMap.get("name");
            }
            if (operationName == null) {
                operationName = operationMap.get("op");
            }
            return operationName != null && hasText(String.valueOf(operationName));
        }
        return false;
    }

    private boolean isParameterlessSharedConstructor(String sharedFunctionName, String sharedCodeSkeleton) {
        if (!hasText(sharedFunctionName) || !hasText(sharedCodeSkeleton)) {
            return false;
        }
        Matcher matcher = SHARED_SIGNATURE_PATTERN.matcher(sharedCodeSkeleton.trim());
        if (!matcher.matches()) {
            return false;
        }
        String skeletonFunctionName = matcher.group(1);
        if (!sharedFunctionName.equals(skeletonFunctionName)) {
            return false;
        }
        int leftParenthesisIndex = sharedCodeSkeleton.indexOf('(');
        int rightParenthesisIndex = sharedCodeSkeleton.lastIndexOf(')');
        if (leftParenthesisIndex < 0 || rightParenthesisIndex <= leftParenthesisIndex) {
            return false;
        }
        String parameterSegment = sharedCodeSkeleton.substring(leftParenthesisIndex + 1, rightParenthesisIndex).trim();
        return parameterSegment.isEmpty();
    }

    private boolean hasExpectedOutput(Object expectedOutput) {
        if (expectedOutput == null) {
            return false;
        }
        if (expectedOutput instanceof String textOutput) {
            return hasText(textOutput) && !"null".equalsIgnoreCase(textOutput.trim());
        }
        return true;
    }

    private boolean isCollectionLikeExpectedOutput(Object expectedOutput) {
        Object normalizedExpectedOutput = tryParseMatcherValue(expectedOutput);
        if (normalizedExpectedOutput instanceof List<?>) {
            return true;
        }
        if (normalizedExpectedOutput != null && normalizedExpectedOutput.getClass().isArray()) {
            return true;
        }
        if (normalizedExpectedOutput instanceof Map<?, ?> expectedObject) {
            if (expectedObject.containsKey("$unordered")) {
                return isCollectionLikeExpectedOutput(expectedObject.get("$unordered"));
            }
            if (expectedObject.containsKey("$anyOf")) {
                Object anyOfToken = tryParseMatcherValue(expectedObject.get("$anyOf"));
                if (!(anyOfToken instanceof List<?> candidates) || candidates.isEmpty()) {
                    return false;
                }
                for (Object candidate : candidates) {
                    if (!isCollectionLikeExpectedOutput(candidate)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private boolean containsLikelyQueryOperation(List<?> operations, String sharedFunctionName) {
        return countLikelyQueryOperations(operations, sharedFunctionName) > 0;
    }

    private int countLikelyQueryOperations(List<?> operations, String sharedFunctionName) {
        if (operations == null || operations.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Object operationToken : operations) {
            String operationName = extractOperationName(operationToken);
            if (!hasText(operationName)) {
                continue;
            }
            String normalizedName = operationName.trim();
            if (normalizedName.equals(sharedFunctionName)) {
                continue;
            }
            if (!isLikelyMutationOperation(normalizedName)) {
                count++;
            }
        }
        return count;
    }

    private int findQueryBeforeMutationOperationIndex(List<?> operations, String sharedFunctionName) {
        if (operations == null || operations.isEmpty()) {
            return -1;
        }
        boolean mutationSeen = false;
        for (int index = 0; index < operations.size(); index++) {
            String operationName = extractOperationName(operations.get(index));
            if (!hasText(operationName)) {
                continue;
            }
            String normalizedName = operationName.trim();
            if (normalizedName.equals(sharedFunctionName)) {
                continue;
            }
            if (isLikelyMutationOperation(normalizedName)) {
                mutationSeen = true;
                continue;
            }
            if (!mutationSeen) {
                return index;
            }
        }
        return -1;
    }

    private boolean isLikelyMutationOperation(String operationName) {
        String normalizedName = operationName.toLowerCase();
        for (String prefix : LIKELY_MUTATION_OPERATION_PREFIXES) {
            if (normalizedName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String extractOperationName(Object operationToken) {
        if (operationToken instanceof List<?> operationList) {
            if (operationList.isEmpty()) {
                return null;
            }
            Object first = operationList.get(0);
            return first == null ? null : String.valueOf(first).trim();
        }
        if (operationToken instanceof Map<?, ?> operationMap) {
            Object operationName = operationMap.get("method");
            if (operationName == null) {
                operationName = operationMap.get("name");
            }
            if (operationName == null) {
                operationName = operationMap.get("op");
            }
            return operationName == null ? null : String.valueOf(operationName).trim();
        }
        return null;
    }

    private String buildRetryCorrectionPrompt(String validationError) {
        return """
                上一版输出校验失败，请仅重写完整 JSON 并修复以下问题：
                %s

                必须继续满足所有系统规则：
                1. 只输出 JSON，不要 Markdown 或解释。
                2. sharedTestCases 若包含 operations，必须提供合法的构造调用或构造参数。
                3. operations 每一项必须是数组或对象，禁止使用字符串形式（如 "addNum(1)"）。
                4. 若 operations 中有多个查询操作且 expectedOutput 非空，expectedOutput 必须使用数组并按查询顺序对齐。
                5. 对于无参构造的 operations 题型，不要在任何写操作前执行查询（避免空状态查询）。
                6. 多个正确答案必须使用 {"$anyOf":[...]}，且 $anyOf 必须是非空数组。
                7. 顺序无关集合必须使用 {"$unordered": ...}，且 $unordered 的值不能为空。
                8. matcher 保留键（$anyOf / $unordered）必须单独出现，不能与其他字段混用，嵌套结构同样遵守。
                """.formatted(validationError);
    }

    private boolean isConstructorOperation(Object operationToken, String sharedFunctionName) {
        if (!hasText(sharedFunctionName) || operationToken == null) {
            return false;
        }
        if (operationToken instanceof List<?> operationList) {
            if (operationList.isEmpty()) {
                return false;
            }
            return sharedFunctionName.equals(String.valueOf(operationList.get(0)).trim());
        }
        if (operationToken instanceof Map<?, ?> operationMap) {
            Object operationName = operationMap.get("method");
            if (operationName == null) {
                operationName = operationMap.get("name");
            }
            if (operationName == null) {
                operationName = operationMap.get("op");
            }
            return operationName != null && sharedFunctionName.equals(String.valueOf(operationName).trim());
        }
        return false;
    }

    private String validateSharedCodeSkeleton(String sharedFunctionName, String sharedCodeSkeleton) {
        if (!hasText(sharedCodeSkeleton)) {
            return "共享题目缺少 sharedCodeSkeleton";
        }
        if (sharedCodeSkeleton.contains("\n") || sharedCodeSkeleton.contains("\r")) {
            return "sharedCodeSkeleton 必须是单行函数签名";
        }
        Matcher forbiddenMatcher = SHARED_FORBIDDEN_TOKEN_PATTERN.matcher(sharedCodeSkeleton);
        if (forbiddenMatcher.find()) {
            return "sharedCodeSkeleton 不能包含实现细节或注释";
        }
        Matcher signatureMatcher = SHARED_SIGNATURE_PATTERN.matcher(sharedCodeSkeleton.trim());
        if (!signatureMatcher.matches()) {
            return "sharedCodeSkeleton 必须匹配 FUNCTION <name>(...) 签名格式";
        }
        String skeletonFunctionName = signatureMatcher.group(1);
        if (!skeletonFunctionName.equals(sharedFunctionName == null ? "" : sharedFunctionName.trim())) {
            return "sharedCodeSkeleton 函数名必须与 sharedFunctionName 完全一致";
        }
        return null;
    }

    private Long startLlmTrace(String traceId, List<Message> messages, int round) {
        if (workflowTraceRecorder == null || workflowTraceSupport == null || traceId == null) {
            return null;
        }
        return workflowTraceRecorder.startTraceItem(TraceItemStartCommand.builder()
                .traceId(traceId)
                .nodeName(this.getClass().getSimpleName())
                .itemType(TraceItemTypeResult.LLM)
                .itemKey("llm-" + round)
                .roundNo(round)
                .inputPayload(workflowTraceSupport.toLlmInputPayload(buildLlmTraceInputPayload(messages, round)))
                .startTimestamp(System.currentTimeMillis())
                .build());
    }

    private void completeLlmTraceSuccess(Long traceItemId,
                                         String draftJson,
                                         CodeQuestionLLMOutputDTO structuredDraft,
                                         TraceTokenUsageResult tokenUsage) {
        if (workflowTraceRecorder == null || traceItemId == null) {
            return;
        }
        workflowTraceRecorder.completeTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(toLlmTracePayload(draftJson, structuredDraft, null))
                .status(TraceStatusResult.SUCCESS)
                .tokenUsage(tokenUsage)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private void failLlmTrace(Long traceItemId, String draftJson, String errorMessage, TraceTokenUsageResult tokenUsage) {
        if (workflowTraceRecorder == null || traceItemId == null) {
            return;
        }
        workflowTraceRecorder.failTraceItem(TraceItemFinishCommand.builder()
                .traceItemId(traceItemId)
                .outputPayload(toLlmTracePayload(draftJson, null, errorMessage))
                .errorMessage(errorMessage)
                .status(TraceStatusResult.FAILED)
                .tokenUsage(tokenUsage)
                .endTimestamp(System.currentTimeMillis())
                .build());
    }

    private Map<String, Object> buildLlmTraceInputPayload(List<Message> messages, int round) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageScope", Map.of(
                "scope", "FULL_INITIAL_ROUND",
                "policy", "INITIAL_MESSAGES_FULL",
                "isSubset", false
        ));
        payload.put("round", round);
        payload.put("messages", toTraceMessages(messages));
        return payload;
    }

    private List<Map<String, Object>> toTraceMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> traceMessages = new ArrayList<>();
        for (Message message : messages) {
            traceMessages.add(Map.of(
                    "messageType", message.getMessageType().name(),
                    "text", Objects.toString(message.getText(), "")
            ));
        }
        return traceMessages;
    }

    private String toLlmTracePayload(String draftJson,
                                     CodeQuestionLLMOutputDTO structuredDraft,
                                     String errorMessage) {
        Map<String, Object> llmOutput = new LinkedHashMap<>();
        llmOutput.put("draftJson", draftJson);
        llmOutput.put("draft", structuredDraft);
        llmOutput.put("errorMessage", errorMessage);
        return toTracePayload("llm_output", llmOutput);
    }

    private String toTracePayload(String kind, Object data) {
        if (workflowTraceSupport == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new TracePayloadResult(kind, normalizeTraceData(data)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize trace payload", e);
        }
    }

    private Object normalizeTraceData(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            return text;
        }
    }

    private String toNormalizedQuestionJson(CodeQuestionLLMOutputDTO draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize shared question output", e);
        }
    }

    private CodeQuestionOutputDTO createErrorOutput(String errorMessage) {
        CodeQuestionOutputDTO output = new CodeQuestionOutputDTO();
        output.setSuccess(false);
        output.setErrorMessage(errorMessage);
        return output;
    }

    private CodeQuestionLLMOutputDTO parseCodeQuestionOutput(String payload) throws com.fasterxml.jackson.core.JsonProcessingException {
        return JsonUtils.fromJson(payload, CodeQuestionLLMOutputDTO.class);
    }

    private Map<String, Object> parseAndBuildResult(String result) {
        try {
            CodeQuestionLLMOutputDTO llmOutput = parseCodeQuestionOutput(result);
            AlgorithmQuestionResult question = convertToAlgorithmQuestionResult(llmOutput);

            CodeQuestionOutputDTO output = new CodeQuestionOutputDTO();
            output.setSuccess(true);
            output.setQuestion(question);
            return Map.of(Constant.CODE_QUESTION_NODE_OUTPUT, output);
        } catch (Exception e) {
            log.error("Error parsing code question output", e);
            return Map.of(Constant.CODE_QUESTION_NODE_OUTPUT,
                    createErrorOutput("解析失败: " + e.getMessage()));
        }
    }

    private AlgorithmQuestionResult convertToAlgorithmQuestionResult(CodeQuestionLLMOutputDTO llmOutput) {
        String sharedTestCases;
        try {
            sharedTestCases = objectMapper.writeValueAsString(llmOutput.getSharedTestCases());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize shared test cases", e);
        }
        return AlgorithmQuestionConverter.toGeneratedQuestionResult(
                llmOutput.getTitle(),
                llmOutput.getDescription(),
                llmOutput.getDifficulty(),
                llmOutput.getSharedFunctionName(),
                llmOutput.getSharedCodeSkeleton(),
                sharedTestCases,
                normalizeTags(llmOutput.getTags())
        );
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }

    private List<AlgorithmQuestionResult> toReferenceQuestionResults(QuestionRAGOutputDTO ragOutput) {
        if (ragOutput == null || ragOutput.getCandidates() == null || ragOutput.getCandidates().isEmpty()) {
            return List.of();
        }
        return ragOutput.getCandidates();
    }

    private Map<String, Object> extractStateData(OverAllState state) {
        QuestionRewriteOutputDTO rewriteOutput = StateUtil.getObjectValue(
                state,
                Constant.QUESTION_REWRITE_NODE_OUTPUT,
                QuestionRewriteOutputDTO.class,
                (QuestionRewriteOutputDTO) null
        );

        if (rewriteOutput == null) {
            log.warn("QuestionRewriteOutputDTO is null");
            return null;
        }

        String rewrittenQuestion = rewriteOutput.getRewrittenQuery();
        if (!hasText(rewrittenQuestion)) {
            log.warn("Rewritten question is empty");
            return null;
        }

        IntentRecognitionOutputDTO intentOutput = StateUtil.getOptionalObjectValueOrNull(
                state,
                Constant.INTENT_RECOGNITION_NODE_OUTPUT,
                IntentRecognitionOutputDTO.class
        );
        UserIntentType intent = intentOutput == null || intentOutput.getIntent() == null
                ? UserIntentType.NEW_QUESTION
                : intentOutput.getIntent();

        AlgorithmQuestionResult currentQuestion = StateUtil.getOptionalObjectValueOrNull(
                state,
                Constant.CURRENT_ALGORITHM_QUESTION,
                AlgorithmQuestionResult.class
        );

        if (intent == UserIntentType.CHANGE_DIFFICULT && currentQuestion == null) {
            Map<String, Object> data = new HashMap<>();
            data.put("intent", intent);
            data.put("currentQuestion", null);
            data.put("rewrittenQuestion", rewrittenQuestion);
            data.put("referenceQuestions", List.of());
            data.put("errorMessage", "当前没有可调整难度的题目上下文，请先获取或选择一道题");
            return data;
        }

        QuestionRAGOutputDTO ragOutput = StateUtil.getObjectValue(
                state,
                Constant.QUESTION_RAG_NODE_OUTPUT,
                QuestionRAGOutputDTO.class,
                (QuestionRAGOutputDTO) null
        );

        List<AlgorithmQuestionResult> referenceQuestions = toReferenceQuestionResults(ragOutput);

        Map<String, Object> data = new HashMap<>();
        data.put("intent", intent);
        data.put("currentQuestion", currentQuestion);
        data.put("rewrittenQuestion", rewrittenQuestion);
        data.put("referenceQuestions", referenceQuestions);
        return data;
    }

    private Flux<GraphResponse<StreamingOutput>> createErrorResult(OverAllState state, String errorMessage) {
        log.error("CodeQuestionNode error: {}", errorMessage);

        CodeQuestionOutputDTO errorOutput = new CodeQuestionOutputDTO();
        errorOutput.setSuccess(false);
        errorOutput.setErrorMessage(errorMessage);

        return FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                Flux.empty(),
                Flux.empty(),
                Flux.empty(),
                result -> Map.of(Constant.CODE_QUESTION_NODE_OUTPUT, errorOutput)
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record QuestionGenerationContext(UserIntentType intent,
                                             AlgorithmQuestionResult currentQuestion,
                                             String rewrittenQuestion,
                                             List<AlgorithmQuestionResult> referenceQuestions,
                                             List<Message> history,
                                             String userInputText,
                                             String traceId,
                                             String errorMessage) {

        private static QuestionGenerationContext success(UserIntentType intent,
                                                         AlgorithmQuestionResult currentQuestion,
                                                         String rewrittenQuestion,
                                                         List<AlgorithmQuestionResult> referenceQuestions,
                                                         List<Message> history,
                                                         String userInputText,
                                                         String traceId) {
            return new QuestionGenerationContext(
                    intent,
                    currentQuestion,
                    rewrittenQuestion,
                    referenceQuestions,
                    history,
                    userInputText,
                    traceId,
                    null
            );
        }

        private static QuestionGenerationContext error(String errorMessage) {
            return new QuestionGenerationContext(
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    "",
                    null,
                    errorMessage
            );
        }
    }

    private static class GenerationAttemptResult {
        private final boolean success;
        private final String finalJson;
        private final String errorMessage;

        private GenerationAttemptResult(boolean success, String finalJson, String errorMessage) {
            this.success = success;
            this.finalJson = finalJson;
            this.errorMessage = errorMessage;
        }

        private static GenerationAttemptResult success(String finalJson) {
            return new GenerationAttemptResult(true, finalJson, null);
        }

        private static GenerationAttemptResult failure(String errorMessage) {
            return new GenerationAttemptResult(false, null, errorMessage);
        }

        private boolean success() {
            return success;
        }

        private String finalJson() {
            return finalJson;
        }

        private String errorMessage() {
            return errorMessage;
        }
    }
}
