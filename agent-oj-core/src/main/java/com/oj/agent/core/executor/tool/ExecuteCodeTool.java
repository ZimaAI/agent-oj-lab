package com.oj.agent.core.executor.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.core.question.enums.Language;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.executor.CodeExecutor;
import com.oj.agent.core.executor.GraalPythonCodeExecutor;
import com.oj.agent.core.executor.model.ExecutionRecord;
import com.oj.agent.core.executor.model.GeneratedCode;
import com.oj.agent.core.executor.tool.model.CodeExecutionRequest;
import com.oj.agent.core.executor.tool.model.CodeExecutionResult;
import com.oj.agent.core.executor.tool.model.TestResult;
import org.graalvm.polyglot.PolyglotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ExecuteCodeTool implements ToolCallback {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteCodeTool.class);
    private static final String TOOL_NAME = "execute_python_code";
    private static final String CONTEXT_ARGS = "args";
    private static final String TOOL_EXECUTION_ERROR_PREFIX = "Tool execution error: ";
    private static final String FALLBACK_SERIALIZATION_ERROR_JSON = "{\"success\":false,\"errorMessage\":\"Failed to serialize error result\"}";
    private static final String TREE_ROOT_PARAM = "root";
    private static final String TREE_NODE_VAL_KEY = "val";
    private static final String TREE_NODE_LEFT_KEY = "left";
    private static final String TREE_NODE_RIGHT_KEY = "right";
    private static final String LINKED_LIST_HEAD_PARAM = "head";
    private static final String LINKED_LIST_HEAD_A_PARAM = "headA";
    private static final String LINKED_LIST_HEAD_B_PARAM = "headB";
    private static final String LINKED_LIST_INTERSECT_VAL_PARAM = "intersectVal";
    private static final String LINKED_LIST_POS_PARAM = "pos";
    private static final String LINKED_LIST_NODE_VAL_KEY = "val";
    private static final String LINKED_LIST_NODE_NEXT_KEY = "next";
    private static final String LINKED_LIST_NODE_RANDOM_KEY = "random";
    private static final String MATCHER_ANY_OF_KEY = "$anyOf";
    private static final String MATCHER_UNORDERED_KEY = "$unordered";
    private static final String METADATA_RESULT_KEY = "result";
    private static final String METADATA_POST_ARGS_KEY = "postArgs";

    private final ObjectMapper objectMapper;
    private final Map<Language, CodeExecutor> executorsByLanguage;
    private final CodeExecutor fallbackExecutor;
    private final Set<Language> supportedLanguages;

    public ExecuteCodeTool(GraalPythonCodeExecutor executor) {
        this(List.of(executor), executor);
    }

    public ExecuteCodeTool(List<CodeExecutor> executors) {
        this(executors, null);
    }

    private ExecuteCodeTool(List<CodeExecutor> executors, CodeExecutor fallbackExecutor) {
        this.objectMapper = JsonUtils.getObjectMapper();
        this.fallbackExecutor = fallbackExecutor;

        List<CodeExecutor> effectiveExecutors = executors == null
                ? List.of()
                : executors.stream().filter(java.util.Objects::nonNull).toList();
        if (effectiveExecutors.isEmpty() && fallbackExecutor == null) {
            throw new IllegalArgumentException("At least one code executor must be provided");
        }

        Map<Language, CodeExecutor> executorRegistry = new LinkedHashMap<>();
        for (CodeExecutor executor : effectiveExecutors) {
            for (Language language : Language.values()) {
                if (executor.supports(language)) {
                    executorRegistry.putIfAbsent(language, executor);
                }
            }
        }
        this.executorsByLanguage = Map.copyOf(executorRegistry);
        this.supportedLanguages = Set.copyOf(executorRegistry.keySet());
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(TOOL_NAME)
                .description(getDescription())
                .inputSchema(buildInputSchema())
                .build();
    }

    private String buildInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "language": {
                      "type": "string",
                      "description": "Programming language. Defaults to python when omitted."
                    },
                    "code": {
                      "type": "string",
                      "description": "Code to execute"
                    },
                    "functionName": {
                      "type": "string",
                      "description": "Name of the function to call"
                    },
                    "testInputs": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "description": "Test input parameters as key-value pairs"
                      },
                      "description": "Array of test input objects, each containing function parameters"
                    },
                    "expectedOutputs": {
                      "type": "array",
                      "description": "Optional expected outputs for each test input, used for pass/fail verification"
                    },
                    "ignoreCollectionOrder": {
                      "type": "boolean",
                      "description": "Optional flag. When true, JSON array order is ignored during output comparison."
                    }
                  },
                  "required": ["code", "functionName", "testInputs"]
                }
                """;
    }

    @Override
    public String call(String functionArgs) {
        try {
            CodeExecutionRequest request = parseRequest(functionArgs);
            CodeExecutionResult result = executeCode(request);
            return serializeResult(result);
        } catch (Exception e) {
            logger.error("Error executing code", e);
            CodeExecutionResult errorResult = new CodeExecutionResult(
                    false,
                    new ArrayList<>(),
                    TOOL_EXECUTION_ERROR_PREFIX + e.getMessage()
            );
            try {
                return serializeResult(errorResult);
            } catch (JsonProcessingException ex) {
                return FALLBACK_SERIALIZATION_ERROR_JSON;
            }
        }
    }

    public Set<Language> getSupportedLanguages() {
        return supportedLanguages;
    }

    private String getDescription() {
        String supported = getSupportedLanguages().isEmpty()
                ? "none"
                : getSupportedLanguages().stream()
                .map(Language::name)
                .sorted()
                .collect(Collectors.joining(", "));
        return "Execute code with test inputs and return results. " +
                "Supported languages: " + supported + ". " +
                "Input: JSON with optional 'language', required 'code', 'functionName', 'testInputs'. " +
                "Optional input: 'expectedOutputs'. " +
                "Output: JSON with 'success', 'results', 'errorMessage'.";
    }

    private CodeExecutionRequest parseRequest(String functionArgs) throws JsonProcessingException {
        BeanOutputConverter<CodeExecutionRequest> requestConverter = new BeanOutputConverter<>(CodeExecutionRequest.class);
        return requestConverter.convert(functionArgs);
    }

    private CodeExecutionResult executeCode(CodeExecutionRequest request) {
        Language language = resolveLanguage(request);
        CodeExecutor selectedExecutor = selectExecutor(language);
        GeneratedCode generatedCode = createGeneratedCode(request, language);

        List<TestResult> testResults = new ArrayList<>();
        boolean allPassed = true;
        String overallError = null;

        List<Map<String, Object>> testInputs = request.getTestInputs() == null
                ? List.of()
                : request.getTestInputs();
        List<Object> expectedOutputs = request.getExpectedOutputs() == null
                ? List.of()
                : request.getExpectedOutputs();
        boolean ignoreCollectionOrder = Boolean.TRUE.equals(request.getIgnoreCollectionOrder());
        boolean linkedListMode = shouldUseLinkedListShape(request.getCode());
        for (int i = 0; i < testInputs.size(); i++) {
            Map<String, Object> testInput = testInputs.get(i);
            int testCaseIndex = i + 1;
            Object expectedOutput = i < expectedOutputs.size() ? expectedOutputs.get(i) : null;
            boolean shouldCompareOutput = i < expectedOutputs.size();
            try {
                Map<String, Object> context = createExecutionContext(
                        testInput,
                        linkedListMode,
                        language,
                        expectedOutput,
                        shouldCompareOutput
                );
                ExecutionRecord record = selectedExecutor.execute(generatedCode, context);

                TestResult testResult = new TestResult();
                Object normalizedExpectedOutput = shouldCompareOutput
                        ? normalizeExpectedOutput(expectedOutput, linkedListMode)
                        : expectedOutput;
                Object normalizedActualOutput = resolveComparableActualOutput(
                        record,
                        testInput,
                        normalizedExpectedOutput,
                        shouldCompareOutput,
                        linkedListMode,
                        ignoreCollectionOrder
                );
                boolean outputMatched = !shouldCompareOutput
                        || sameJsonValue(normalizedExpectedOutput, normalizedActualOutput, ignoreCollectionOrder);
                boolean passed = record.isSuccess() && outputMatched;

                testResult.setPassed(passed);
                testResult.setOutput(normalizedActualOutput);
                String caseError;
                if (!record.isSuccess()) {
                    caseError = formatCaseError(testCaseIndex, "execution failed", record.getErrorMessage());
                } else if (!outputMatched) {
                    caseError = formatOutputMismatchError(testCaseIndex, normalizedExpectedOutput, normalizedActualOutput);
                } else {
                    caseError = record.getErrorMessage();
                }
                testResult.setError(caseError);

                testResults.add(testResult);

                if (!passed) {
                    allPassed = false;
                    if (overallError == null) {
                        overallError = caseError;
                    }
                }
            } catch (PolyglotException e) {
                String caseError = formatCaseError(testCaseIndex, "polyglot exception", e.getMessage());
                TestResult testResult = new TestResult();
                testResult.setPassed(false);
                testResult.setOutput(null);
                testResult.setError(caseError);
                testResults.add(testResult);
                allPassed = false;
                if (overallError == null) {
                    overallError = caseError;
                }
            } catch (Exception e) {
                String caseError = formatCaseError(testCaseIndex, "execution exception", e.getMessage());
                TestResult testResult = new TestResult();
                testResult.setPassed(false);
                testResult.setOutput(null);
                testResult.setError(caseError);
                testResults.add(testResult);
                allPassed = false;
                if (overallError == null) {
                    overallError = caseError;
                }
            }
        }

        return new CodeExecutionResult(allPassed, testResults, overallError);
    }

    private Language resolveLanguage(CodeExecutionRequest request) {
        String language = request == null ? null : request.getLanguage();
        if (!StringUtils.hasText(language)) {
            return Language.PYTHON;
        }
        return Language.fromName(language);
    }

    private CodeExecutor selectExecutor(Language language) {
        CodeExecutor selectedExecutor = executorsByLanguage.get(language);
        if (selectedExecutor != null) {
            return selectedExecutor;
        }
        if (fallbackExecutor != null) {
            return fallbackExecutor;
        }
        throw new IllegalArgumentException("Unsupported language: " + language.getName());
    }

    private String formatCaseError(int testCaseIndex, String errorType, String rawErrorMessage) {
        String normalizedError = rawErrorMessage == null || rawErrorMessage.isBlank()
                ? "unknown error"
                : rawErrorMessage;
        return "testCase#%d %s: %s".formatted(testCaseIndex, errorType, normalizedError);
    }

    private String formatOutputMismatchError(int testCaseIndex, Object expectedOutput, Object actualOutput) {
        return "testCase#%d output mismatch: expected=%s, actual=%s"
                .formatted(testCaseIndex, toDebugText(expectedOutput), toDebugText(actualOutput));
    }

    private String toDebugText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private boolean sameJsonValue(Object expected, Object actual, boolean ignoreCollectionOrder) {
        return jsonEquals(normalizeJsonValue(expected), normalizeJsonValue(actual), ignoreCollectionOrder);
    }

    private boolean jsonEquals(JsonNode expected, JsonNode actual, boolean ignoreCollectionOrder) {
        if (expected == null || expected.isNull()) {
            return actual == null || actual.isNull();
        }
        if (actual == null || actual.isNull()) {
            return false;
        }
        // 仅当对象只包含单个保留键时，才按 matcher 语义处理。
        if (isMatcherNode(expected)) {
            return matcherEquals(expected, actual, ignoreCollectionOrder);
        }
        if (expected.isNumber() && actual.isNumber()) {
            return numbersEquivalent(expected, actual);
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            return false;
        }

        if (expected.isObject()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            var fieldNames = expected.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                if (!actual.has(field)) {
                    return false;
                }
                if (!jsonEquals(expected.get(field), actual.get(field), ignoreCollectionOrder)) {
                    return false;
                }
            }
            return true;
        }

        if (expected.isArray()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            if (!ignoreCollectionOrder) {
                for (int i = 0; i < expected.size(); i++) {
                    if (!jsonEquals(expected.get(i), actual.get(i), false)) {
                        return false;
                    }
                }
                return true;
            }

            boolean[] matched = new boolean[actual.size()];
            for (int i = 0; i < expected.size(); i++) {
                JsonNode expectedItem = expected.get(i);
                boolean foundMatch = false;
                for (int j = 0; j < actual.size(); j++) {
                    if (matched[j]) {
                        continue;
                    }
                    if (jsonEquals(expectedItem, actual.get(j), true)) {
                        matched[j] = true;
                        foundMatch = true;
                        break;
                    }
                }
                if (!foundMatch) {
                    return false;
                }
            }
            return true;
        }

        return expected.equals(actual);
    }

    private boolean isMatcherNode(JsonNode expected) {
        if (!expected.isObject() || expected.size() != 1) {
            return false;
        }
        return expected.has(MATCHER_ANY_OF_KEY) || expected.has(MATCHER_UNORDERED_KEY);
    }

    private boolean matcherEquals(JsonNode expected, JsonNode actual, boolean ignoreCollectionOrder) {
        if (expected.has(MATCHER_ANY_OF_KEY)) {
            JsonNode anyOfCandidates = expected.get(MATCHER_ANY_OF_KEY);
            if (!anyOfCandidates.isArray()) {
                return false;
            }
            for (JsonNode candidate : anyOfCandidates) {
                if (jsonEquals(candidate, actual, ignoreCollectionOrder)) {
                    return true;
                }
            }
            return false;
        }
        if (expected.has(MATCHER_UNORDERED_KEY)) {
            JsonNode unorderedExpected = expected.get(MATCHER_UNORDERED_KEY);
            if (jsonEquals(unorderedExpected, actual, true)) {
                return true;
            }
            return unorderedMatcherEquals(unorderedExpected, actual);
        }
        return false;
    }

    private boolean unorderedMatcherEquals(JsonNode unorderedExpected, JsonNode actual) {
        if (!unorderedExpected.isArray() || !actual.isArray()) {
            return false;
        }
        // 兼容把 $unordered 当作“候选列表”使用的场景：[[candidate1], [candidate2]]。
        if (containsNestedArrayItem(unorderedExpected) && !containsNestedArrayItem(actual)) {
            for (JsonNode candidate : unorderedExpected) {
                if (jsonEquals(candidate, actual, true)) {
                    return true;
                }
                if (candidate.isArray() && jsonEquals(removeNullArrayElements(candidate), removeNullArrayElements(actual), true)) {
                    return true;
                }
            }
        }
        // 标量数组在无序匹配下忽略 null 占位数量差异，适配树层序不同表示方式。
        return jsonEquals(removeNullArrayElements(unorderedExpected), removeNullArrayElements(actual), true);
    }

    private boolean containsNestedArrayItem(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return false;
        }
        for (JsonNode item : arrayNode) {
            if (item.isArray() || item.isObject()) {
                return true;
            }
        }
        return false;
    }

    private JsonNode removeNullArrayElements(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return arrayNode;
        }
        var filtered = objectMapper.createArrayNode();
        for (JsonNode item : arrayNode) {
            if (item == null || item.isNull()) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    private boolean numbersEquivalent(JsonNode expected, JsonNode actual) {
        BigDecimal expectedValue = expected.decimalValue();
        BigDecimal actualValue = actual.decimalValue();
        return expectedValue.compareTo(actualValue) == 0;
    }

    private JsonNode normalizeJsonValue(Object value) {
        if (value instanceof String stringValue) {
            try {
                return objectMapper.readTree(stringValue);
            } catch (Exception ignored) {
                return objectMapper.valueToTree(stringValue);
            }
        }
        return objectMapper.valueToTree(value);
    }

    private GeneratedCode createGeneratedCode(CodeExecutionRequest request, Language language) {
        GeneratedCode code = new GeneratedCode();
        code.setCode(request.getCode());
        code.setFunctionName(request.getFunctionName());
        code.setLanguage(language);
        return code;
    }

    private Map<String, Object> createExecutionContext(Map<String, Object> testInput,
                                                       boolean linkedListMode,
                                                       Language language,
                                                       Object expectedOutput,
                                                       boolean hasExpectedOutput) {
        Map<String, Object> context = new HashMap<>();
        context.put(
                CONTEXT_ARGS,
                normalizeTestInput(testInput, linkedListMode, language, expectedOutput, hasExpectedOutput)
        );
        return context;
    }

    private Map<String, Object> normalizeTestInput(Map<String, Object> testInput,
                                                   boolean linkedListMode,
                                                   Language language,
                                                   Object expectedOutput,
                                                   boolean hasExpectedOutput) {
        if (testInput == null || testInput.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : testInput.entrySet()) {
            normalized.put(entry.getKey(), normalizeInputValue(entry.getKey(), entry.getValue(), linkedListMode));
        }
        // 树题中若 p/q 以数值给出，自动映射为 root 中对应节点引用，兼容 LCA 等题型。
        applyTreeReferenceInputAdjustments(normalized);
        if (linkedListMode) {
            // 链表题统一补齐 head/lists 的结构化输入，降低语言间参数结构不一致导致的执行失败。
            applyLinkedListInputAdjustments(normalized, expectedOutput, hasExpectedOutput);
        }
        // Python 代码生成常出现 snake_case 与 camelCase 混用，这里补别名以降低参数名不匹配。
        if (Language.PYTHON == language) {
            applyPythonParameterAliases(normalized);
        }
        return normalized;
    }

    private void applyLinkedListInputAdjustments(Map<String, Object> normalized,
                                                 Object expectedOutput,
                                                 boolean hasExpectedOutput) {
        Object listsValue = normalized.get("lists");
        if (listsValue instanceof List<?> rawLists
                && isLinkedListRootCollection(rawLists)) {
            normalized.put("lists", buildLinkedListCollection(rawLists));
        }

        // copyRandomList 常用 [val, randomIndex] 输入格式，这里统一转换为节点引用结构。
        applyRandomPointerPairInputAdjustments(normalized, LINKED_LIST_HEAD_PARAM);

        // 统一处理单链表头节点参数（head/headA/headB/list1/list2/l1/l2 等），兼容节点语义代码。
        canonicalizeLinkedListHeadParameter(normalized, LINKED_LIST_HEAD_PARAM);
        canonicalizeLinkedListHeadParameter(normalized, "list1");
        canonicalizeLinkedListHeadParameter(normalized, "list2");
        canonicalizeLinkedListHeadParameter(normalized, "l1");
        canonicalizeLinkedListHeadParameter(normalized, "l2");
        canonicalizeLinkedListHeadParameter(normalized, LINKED_LIST_HEAD_A_PARAM);
        canonicalizeLinkedListHeadParameter(normalized, LINKED_LIST_HEAD_B_PARAM);

        // 相交链表题优先用 intersectVal，其次用 expectedOutput 推断交点，避免错误猜测公共后缀。
        applyIntersectionLinkedListAdjustments(normalized, expectedOutput, hasExpectedOutput);
    }

    // 规范化指定参数对应的链表头节点结构，并按 pos 注入环（仅 head 参数需要）。
    private void canonicalizeLinkedListHeadParameter(Map<String, Object> normalized, String parameterName) {
        Object headValue = getValueIgnoreCase(normalized, parameterName);
        if (!(headValue instanceof Map<?, ?> headMapCandidate)) {
            return;
        }
        Map<String, Object> headNode = castToLinkedMap(headMapCandidate);
        Map<String, Object> canonicalizedHead = canonicalizeRandomPointerLinkedList(headNode);
        if (LINKED_LIST_HEAD_PARAM.equals(parameterName)) {
            Integer cyclePos = parseLinkedListCyclePos(getValueIgnoreCase(normalized, LINKED_LIST_POS_PARAM));
            if (cyclePos != null) {
                canonicalizedHead = attachCycleToLinkedList(canonicalizedHead, cyclePos);
            }
        }
        putValueIgnoreCase(normalized, parameterName, canonicalizedHead);
    }

    // 按 intersectVal 将 headA/headB 连接到同一尾部，模拟“共享节点引用”的输入语义。
    private void applyIntersectionLinkedListAdjustments(Map<String, Object> normalized,
                                                        Object expectedOutput,
                                                        boolean hasExpectedOutput) {
        Object headAToken = getValueIgnoreCase(normalized, LINKED_LIST_HEAD_A_PARAM);
        Object headBToken = getValueIgnoreCase(normalized, LINKED_LIST_HEAD_B_PARAM);
        if (!(headAToken instanceof Map<?, ?> headAMapCandidate) || !(headBToken instanceof Map<?, ?> headBMapCandidate)) {
            return;
        }
        boolean hasExplicitIntersectToken = containsKeyIgnoreCase(normalized, LINKED_LIST_INTERSECT_VAL_PARAM)
                || containsKeyIgnoreCase(normalized, "intersect_val");
        Object intersectToken = firstNonNull(
                getValueIgnoreCase(normalized, LINKED_LIST_INTERSECT_VAL_PARAM),
                getValueIgnoreCase(normalized, "intersect_val")
        );
        if (intersectToken == null && hasExpectedOutput) {
            intersectToken = resolveIntersectionTokenFromExpectedOutput(expectedOutput);
        }
        Object normalizedIntersect = normalizeIntersectionToken(intersectToken);

        Map<String, Object> headA = castToLinkedMap(headAMapCandidate);
        Map<String, Object> headB = castToLinkedMap(headBMapCandidate);
        List<Map<String, Object>> nodesA = collectLinkedListNodes(headA);
        List<Map<String, Object>> nodesB = collectLinkedListNodes(headB);
        if (nodesA.isEmpty() || nodesB.isEmpty()) {
            return;
        }

        // 缺少显式/推断交点时不做公共后缀猜测，避免将重复值误判成交点。
        if (normalizedIntersect == null) {
            if (hasExplicitIntersectToken || hasExpectedOutput) {
                return;
            }
            return;
        }

        int intersectIndexA = findLinkedListNodeIndexByValue(nodesA, normalizedIntersect);
        int intersectIndexB = findLinkedListNodeIndexByValue(nodesB, normalizedIntersect);
        if (intersectIndexA < 0 || intersectIndexB < 0) {
            return;
        }

        // 交点前若存在与共享尾部或另一前缀重复的值，按唯一值重写，降低“按值误判交点”的概率。
        stabilizeIntersectionPrefixValues(nodesA, intersectIndexA, nodesB, intersectIndexB);

        Map<String, Object> sharedStart = nodesA.get(intersectIndexA);
        if (intersectIndexB == 0) {
            putValueIgnoreCase(normalized, LINKED_LIST_HEAD_B_PARAM, sharedStart);
            return;
        }
        nodesB.get(intersectIndexB - 1).put(LINKED_LIST_NODE_NEXT_KEY, sharedStart);
    }

    // 将交点前缀中的冲突值替换为唯一整数，避免错误解法把“值相同”误判为“节点相交”。
    private void stabilizeIntersectionPrefixValues(List<Map<String, Object>> nodesA,
                                                   int intersectIndexA,
                                                   List<Map<String, Object>> nodesB,
                                                   int intersectIndexB) {
        Set<Object> usedValues = new HashSet<>();
        for (int i = intersectIndexA; i < nodesA.size(); i++) {
            usedValues.add(nodesA.get(i).get(LINKED_LIST_NODE_VAL_KEY));
        }

        for (int i = 0; i < intersectIndexA; i++) {
            Map<String, Object> node = nodesA.get(i);
            Object value = node.get(LINKED_LIST_NODE_VAL_KEY);
            if (usedValues.contains(value)) {
                value = allocateUniqueLinkedListValue(usedValues);
                node.put(LINKED_LIST_NODE_VAL_KEY, value);
            }
            usedValues.add(value);
        }

        for (int i = 0; i < intersectIndexB; i++) {
            Map<String, Object> node = nodesB.get(i);
            Object value = node.get(LINKED_LIST_NODE_VAL_KEY);
            if (usedValues.contains(value)) {
                value = allocateUniqueLinkedListValue(usedValues);
                node.put(LINKED_LIST_NODE_VAL_KEY, value);
            }
            usedValues.add(value);
        }
    }

    // 生成不与当前链表值冲突的整数节点值，保障用例可比性与可执行性。
    private Integer allocateUniqueLinkedListValue(Set<Object> usedValues) {
        int candidate = 1_000_000;
        while (usedValues.contains(candidate) || usedValues.contains((long) candidate)) {
            candidate++;
        }
        return candidate;
    }

    // 归一化相交值，兼容数字/字符串输入。
    private Object normalizeIntersectionToken(Object intersectToken) {
        Object parsed = tryParseJsonValue(intersectToken);
        if (parsed == null) {
            return null;
        }
        if (parsed instanceof Number) {
            return parsed;
        }
        if (parsed instanceof String textToken) {
            String trimmed = textToken.trim();
            if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
                return null;
            }
            return trimmed;
        }
        return parsed;
    }

    // 当测试输入缺少 intersectVal 时，允许用期望输出中的交点值补齐引用语义。
    private Object resolveIntersectionTokenFromExpectedOutput(Object expectedOutput) {
        Object parsed = tryParseJsonValue(expectedOutput);
        if (parsed == null) {
            return null;
        }
        if (parsed instanceof Map<?, ?> || parsed instanceof List<?>) {
            return null;
        }
        return parsed;
    }

    // 大小写不敏感判断字段是否存在，兼容 intersectVal/intersect_val 等命名差异。
    private boolean containsKeyIgnoreCase(Map<String, Object> source, String key) {
        if (source == null || !StringUtils.hasText(key)) {
            return false;
        }
        if (source.containsKey(key)) {
            return true;
        }
        for (String existingKey : source.keySet()) {
            if (existingKey != null && existingKey.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    // 在链表节点序列中按 val 定位第一个目标节点。
    private int findLinkedListNodeIndexByValue(List<Map<String, Object>> nodes, Object targetValue) {
        for (int i = 0; i < nodes.size(); i++) {
            if (Objects.equals(nodes.get(i).get(LINKED_LIST_NODE_VAL_KEY), targetValue)) {
                return i;
            }
        }
        return -1;
    }

    // 返回首个非空值，用于兼容不同命名风格的输入字段。
    private Object firstNonNull(Object... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Object candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    // 大小写不敏感读取参数，兼容 headA/head_a 等字段命名差异。
    private Object getValueIgnoreCase(Map<String, Object> source, String key) {
        if (source == null || !StringUtils.hasText(key)) {
            return null;
        }
        if (source.containsKey(key)) {
            return source.get(key);
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // 大小写不敏感回写参数，优先复用已有键名，避免破坏原始字段顺序。
    private void putValueIgnoreCase(Map<String, Object> source, String key, Object value) {
        if (source == null || !StringUtils.hasText(key)) {
            return;
        }
        if (source.containsKey(key)) {
            source.put(key, value);
            return;
        }
        for (String existingKey : source.keySet()) {
            if (existingKey != null && existingKey.equalsIgnoreCase(key)) {
                source.put(existingKey, value);
                return;
            }
        }
        source.put(key, value);
    }

    private boolean isLinkedListRootCollection(List<?> listValue) {
        for (Object item : listValue) {
            if (item == null) {
                continue;
            }
            if (!(item instanceof List<?> nestedList) || !isLinearLinkedListValues(nestedList)) {
                return false;
            }
        }
        return true;
    }

    private List<Object> buildLinkedListCollection(List<?> rawLists) {
        List<Object> normalizedLists = new ArrayList<>(rawLists.size());
        for (Object item : rawLists) {
            if (item == null) {
                normalizedLists.add(null);
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Object> linearValues = (List<Object>) item;
            normalizedLists.add(buildLinkedListFromLinearValues(linearValues));
        }
        return normalizedLists;
    }

    // 将 [val, randomIndex] 结构转换为带 random 引用的链表节点，兼容 copyRandomList 输入。
    private void applyRandomPointerPairInputAdjustments(Map<String, Object> normalized, String parameterName) {
        Object rawValue = getValueIgnoreCase(normalized, parameterName);
        if (!(rawValue instanceof List<?> randomPairs)) {
            return;
        }
        if (!isRandomPointerPairList(randomPairs)) {
            return;
        }
        Map<String, Object> headNode = buildRandomPointerLinkedListFromPairs(randomPairs);
        putValueIgnoreCase(normalized, parameterName, headNode);
    }

    // 判断输入是否为随机链表二元组序列。
    private boolean isRandomPointerPairList(List<?> listValue) {
        for (Object item : listValue) {
            if (!(item instanceof List<?> pairValue) || pairValue.size() != 2) {
                return false;
            }
            if (pairValue.get(0) instanceof Map<?, ?> || pairValue.get(0) instanceof List<?>) {
                return false;
            }
            Object randomIndexToken = pairValue.get(1);
            if (randomIndexToken == null) {
                continue;
            }
            if (randomIndexToken instanceof Number) {
                continue;
            }
            if (randomIndexToken instanceof String textToken) {
                String trimmed = textToken.trim();
                if ("null".equalsIgnoreCase(trimmed)) {
                    continue;
                }
                try {
                    Integer.parseInt(trimmed);
                    continue;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            return false;
        }
        return true;
    }

    // 构建随机指针链表节点结构，randomIndex 越界时按 null 处理。
    private Map<String, Object> buildRandomPointerLinkedListFromPairs(List<?> randomPairs) {
        if (randomPairs == null || randomPairs.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> nodes = new ArrayList<>(randomPairs.size());
        for (Object item : randomPairs) {
            @SuppressWarnings("unchecked")
            List<Object> pair = (List<Object>) item;
            Map<String, Object> node = createLinkedListNode(pair.get(0));
            node.put(LINKED_LIST_NODE_RANDOM_KEY, null);
            nodes.add(node);
        }
        for (int index = 0; index < nodes.size() - 1; index++) {
            nodes.get(index).put(LINKED_LIST_NODE_NEXT_KEY, nodes.get(index + 1));
        }
        for (int index = 0; index < randomPairs.size(); index++) {
            @SuppressWarnings("unchecked")
            List<Object> pair = (List<Object>) randomPairs.get(index);
            Integer randomIndex = parseLinkedListCyclePos(pair.get(1));
            if (randomIndex != null && randomIndex >= 0 && randomIndex < nodes.size()) {
                nodes.get(index).put(LINKED_LIST_NODE_RANDOM_KEY, nodes.get(randomIndex));
            }
        }
        return nodes.get(0);
    }

    private void applyTreeReferenceInputAdjustments(Map<String, Object> normalized) {
        Object rootToken = normalized.get(TREE_ROOT_PARAM);
        if (!(rootToken instanceof Map<?, ?> rootMapCandidate)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) rootMapCandidate;

        if (normalized.containsKey("p")) {
            normalized.put("p", resolveTreeReferenceNode(root, normalized.get("p")));
        }
        if (normalized.containsKey("q")) {
            normalized.put("q", resolveTreeReferenceNode(root, normalized.get("q")));
        }
    }

    private Object resolveTreeReferenceNode(Map<String, Object> root, Object token) {
        if (token == null) {
            return null;
        }
        // 优先按原始 token 尝试回绑节点引用，覆盖 p/q 直接给标量值的场景。
        Map<String, Object> reboundNode = resolveTreeNodeByToken(root, token);
        if (reboundNode != null) {
            return reboundNode;
        }
        Object normalizedToken = tryParseJsonValue(token);
        // 若 token 是 JSON 字符串包装，再按解析后的值二次回绑。
        if (!Objects.equals(normalizedToken, token)) {
            Map<String, Object> reboundByNormalized = resolveTreeNodeByToken(root, normalizedToken);
            if (reboundByNormalized != null) {
                return reboundByNormalized;
            }
        }
        return token;
    }

    private Map<String, Object> resolveTreeNodeByToken(Map<String, Object> root, Object token) {
        if (token == null) {
            return null;
        }
        if (token instanceof Map<?, ?> mapToken) {
            if (!mapToken.containsKey(TREE_NODE_VAL_KEY)) {
                return null;
            }
            return findTreeNodeByValue(root, mapToken.get(TREE_NODE_VAL_KEY));
        }
        if (token instanceof Number || token instanceof String) {
            return findTreeNodeByValue(root, token);
        }
        return null;
    }

    private Map<String, Object> findTreeNodeByValue(Map<String, Object> root, Object targetValue) {
        if (root == null) {
            return null;
        }
        Deque<Map<String, Object>> queue = new ArrayDeque<>();
        queue.add(root);
        Set<Map<String, Object>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!queue.isEmpty()) {
            Map<String, Object> current = queue.removeFirst();
            if (current == null || !visited.add(current)) {
                continue;
            }
            if (treeValueEquals(current.get(TREE_NODE_VAL_KEY), targetValue)) {
                return current;
            }
            Object left = current.get(TREE_NODE_LEFT_KEY);
            if (left instanceof Map<?, ?> leftNode) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castedLeftNode = (Map<String, Object>) leftNode;
                queue.addLast(castedLeftNode);
            }
            Object right = current.get(TREE_NODE_RIGHT_KEY);
            if (right instanceof Map<?, ?> rightNode) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castedRightNode = (Map<String, Object>) rightNode;
                queue.addLast(castedRightNode);
            }
        }
        return null;
    }

    private boolean treeValueEquals(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return BigDecimal.valueOf(leftNumber.doubleValue())
                    .compareTo(BigDecimal.valueOf(rightNumber.doubleValue())) == 0;
        }
        return Objects.equals(left, right);
    }

    private void applyPythonParameterAliases(Map<String, Object> normalized) {
        List<Map.Entry<String, Object>> snapshot = new ArrayList<>(normalized.entrySet());
        for (Map.Entry<String, Object> entry : snapshot) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String snakeCase = toSnakeCase(key);
            if (StringUtils.hasText(snakeCase) && !normalized.containsKey(snakeCase)) {
                normalized.put(snakeCase, value);
            }

            String camelCase = toCamelCase(key);
            if (StringUtils.hasText(camelCase) && !normalized.containsKey(camelCase)) {
                normalized.put(camelCase, value);
            }
        }
    }

    private String toSnakeCase(String source) {
        if (!StringUtils.hasText(source)) {
            return source;
        }
        String replaced = source.replace('-', '_');
        StringBuilder builder = new StringBuilder(replaced.length() + 8);
        for (int i = 0; i < replaced.length(); i++) {
            char current = replaced.charAt(i);
            if (Character.isUpperCase(current)) {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private String toCamelCase(String source) {
        if (!StringUtils.hasText(source)) {
            return source;
        }
        String[] segments = source.split("[_\\-]+");
        if (segments.length == 0) {
            return source;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(segments[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            builder.append(Character.toUpperCase(segment.charAt(0)));
            if (segment.length() > 1) {
                builder.append(segment.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private Integer parseLinkedListCyclePos(Object token) {
        if (token == null) {
            return null;
        }
        if (token instanceof Number number) {
            return number.intValue();
        }
        if (token instanceof String textToken) {
            String trimmed = textToken.trim();
            if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
                return null;
            }
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> canonicalizeRandomPointerLinkedList(Map<String, Object> headNode) {
        if (headNode == null) {
            return null;
        }
        List<Map<String, Object>> originalNodes = collectLinkedListNodes(headNode);
        if (originalNodes.isEmpty() || !containsRandomPointerField(originalNodes)) {
            return headNode;
        }

        Map<Map<String, Object>, Integer> indexByIdentity = new IdentityHashMap<>();
        List<Map<String, Object>> canonicalNodes = new ArrayList<>(originalNodes.size());
        for (int index = 0; index < originalNodes.size(); index++) {
            Map<String, Object> originalNode = originalNodes.get(index);
            indexByIdentity.put(originalNode, index);

            Map<String, Object> canonicalNode = new LinkedHashMap<>();
            canonicalNode.put(LINKED_LIST_NODE_VAL_KEY, originalNode.get(LINKED_LIST_NODE_VAL_KEY));
            canonicalNode.put(LINKED_LIST_NODE_NEXT_KEY, null);
            canonicalNode.put(LINKED_LIST_NODE_RANDOM_KEY, null);
            canonicalNodes.add(canonicalNode);
        }

        for (int index = 0; index < canonicalNodes.size() - 1; index++) {
            canonicalNodes.get(index).put(LINKED_LIST_NODE_NEXT_KEY, canonicalNodes.get(index + 1));
        }

        for (int index = 0; index < originalNodes.size(); index++) {
            Object randomToken = originalNodes.get(index).get(LINKED_LIST_NODE_RANDOM_KEY);
            Integer targetIndex = resolveRandomTargetIndex(randomToken, indexByIdentity, originalNodes);
            canonicalNodes.get(index).put(
                    LINKED_LIST_NODE_RANDOM_KEY,
                    targetIndex == null ? null : canonicalNodes.get(targetIndex)
            );
        }
        return canonicalNodes.get(0);
    }

    private boolean containsRandomPointerField(List<Map<String, Object>> originalNodes) {
        for (Map<String, Object> node : originalNodes) {
            if (node.containsKey(LINKED_LIST_NODE_RANDOM_KEY)) {
                return true;
            }
        }
        return false;
    }

    private Integer resolveRandomTargetIndex(Object randomToken,
                                             Map<Map<String, Object>, Integer> indexByIdentity,
                                             List<Map<String, Object>> originalNodes) {
        if (!(randomToken instanceof Map<?, ?> randomCandidate)) {
            return null;
        }
        Map<String, Object> randomNode = castToLinkedMap(randomCandidate);
        Integer byIdentity = indexByIdentity.get(randomNode);
        if (byIdentity != null) {
            return byIdentity;
        }
        if (!randomNode.containsKey(LINKED_LIST_NODE_VAL_KEY)) {
            return null;
        }
        Object randomValue = randomNode.get(LINKED_LIST_NODE_VAL_KEY);
        for (int index = 0; index < originalNodes.size(); index++) {
            if (Objects.equals(randomValue, originalNodes.get(index).get(LINKED_LIST_NODE_VAL_KEY))) {
                return index;
            }
        }
        return null;
    }

    private Map<String, Object> attachCycleToLinkedList(Map<String, Object> headNode, int cyclePos) {
        List<Map<String, Object>> nodes = collectLinkedListNodes(headNode);
        if (nodes.isEmpty()) {
            return headNode;
        }
        if (cyclePos >= 0 && cyclePos < nodes.size()) {
            nodes.get(nodes.size() - 1).put(LINKED_LIST_NODE_NEXT_KEY, nodes.get(cyclePos));
        }
        return nodes.get(0);
    }

    private List<Map<String, Object>> collectLinkedListNodes(Map<String, Object> headNode) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<Map<String, Object>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Object cursor = headNode;
        while (cursor instanceof Map<?, ?> nodeCandidate) {
            Map<String, Object> node = castToLinkedMap(nodeCandidate);
            if (!isLinkedListNodeMap(node) || !visited.add(node)) {
                break;
            }
            nodes.add(node);
            cursor = node.get(LINKED_LIST_NODE_NEXT_KEY);
        }
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToLinkedMap(Map<?, ?> mapValue) {
        return (Map<String, Object>) mapValue;
    }

    private Object normalizeInputValue(String parameterName, Object value, boolean linkedListMode) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> listValue
                && isRootParameter(parameterName)
                && isLevelOrderTreeList(listValue)) {
            return buildTreeFromLevelOrder(listValue);
        }
        if (value instanceof List<?> listValue
                && linkedListMode
                && isLinkedListRootParameter(parameterName)
                && isLinearLinkedListValues(listValue)) {
            return buildLinkedListFromLinearValues(listValue);
        }
        if (value instanceof Map<?, ?> mapValue) {
            return normalizeMapValues(mapValue, linkedListMode);
        }
        if (value instanceof List<?> listValue) {
            return normalizeListValues(listValue, linkedListMode);
        }
        return value;
    }

    private Map<String, Object> normalizeMapValues(Map<?, ?> mapValue, boolean linkedListMode) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
            String key = String.valueOf(entry.getKey());
            normalized.put(key, normalizeInputValue(key, entry.getValue(), linkedListMode));
        }
        return normalized;
    }

    private List<Object> normalizeListValues(List<?> listValue, boolean linkedListMode) {
        List<Object> normalized = new ArrayList<>(listValue.size());
        for (Object item : listValue) {
            if (item instanceof Map<?, ?> mapItem) {
                normalized.add(normalizeMapValues(mapItem, linkedListMode));
                continue;
            }
            if (item instanceof List<?> listItem) {
                normalized.add(normalizeListValues(listItem, linkedListMode));
                continue;
            }
            normalized.add(item);
        }
        return normalized;
    }

    private boolean isRootParameter(String parameterName) {
        if (!StringUtils.hasText(parameterName)) {
            return false;
        }
        return TREE_ROOT_PARAM.equals(parameterName.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isLinkedListRootParameter(String parameterName) {
        if (!StringUtils.hasText(parameterName)) {
            return false;
        }
        String normalizedName = parameterName.trim().toLowerCase(Locale.ROOT);
        if (LINKED_LIST_HEAD_PARAM.equals(normalizedName)
                || LINKED_LIST_HEAD_A_PARAM.toLowerCase(Locale.ROOT).equals(normalizedName)
                || LINKED_LIST_HEAD_B_PARAM.toLowerCase(Locale.ROOT).equals(normalizedName)) {
            return true;
        }
        if ("list".equals(normalizedName)
                || "list1".equals(normalizedName)
                || "list2".equals(normalizedName)
                || "l1".equals(normalizedName)
                || "l2".equals(normalizedName)) {
            return true;
        }
        if (normalizedName.startsWith("head") || normalizedName.startsWith("list")) {
            return normalizedName.length() > 4;
        }
        return false;
    }

    private boolean isLevelOrderTreeList(List<?> listValue) {
        for (Object item : listValue) {
            if (item == null) {
                continue;
            }
            if (item instanceof Map<?, ?> || item instanceof List<?>) {
                return false;
            }
        }
        return true;
    }

    private boolean isLinearLinkedListValues(List<?> listValue) {
        for (Object item : listValue) {
            if (item == null) {
                continue;
            }
            if (item instanceof Map<?, ?> || item instanceof List<?>) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> buildTreeFromLevelOrder(List<?> listValue) {
        if (listValue.isEmpty() || isNullTreeToken(listValue.get(0))) {
            return null;
        }

        Map<String, Object> root = createTreeNode(listValue.get(0));
        Deque<Map<String, Object>> queue = new ArrayDeque<>();
        queue.add(root);

        int index = 1;
        while (!queue.isEmpty() && index < listValue.size()) {
            Map<String, Object> current = queue.removeFirst();

            Object leftToken = listValue.get(index++);
            if (!isNullTreeToken(leftToken)) {
                Map<String, Object> leftNode = createTreeNode(leftToken);
                current.put(TREE_NODE_LEFT_KEY, leftNode);
                queue.addLast(leftNode);
            }

            if (index >= listValue.size()) {
                break;
            }
            Object rightToken = listValue.get(index++);
            if (!isNullTreeToken(rightToken)) {
                Map<String, Object> rightNode = createTreeNode(rightToken);
                current.put(TREE_NODE_RIGHT_KEY, rightNode);
                queue.addLast(rightNode);
            }
        }
        return root;
    }

    private Map<String, Object> createTreeNode(Object valueToken) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put(TREE_NODE_VAL_KEY, valueToken);
        node.put(TREE_NODE_LEFT_KEY, null);
        node.put(TREE_NODE_RIGHT_KEY, null);
        return node;
    }

    private Map<String, Object> buildLinkedListFromLinearValues(List<?> listValue) {
        Map<String, Object> head = null;
        Map<String, Object> current = null;
        for (Object item : listValue) {
            Map<String, Object> node = createLinkedListNode(item);
            if (head == null) {
                head = node;
            } else {
                current.put(LINKED_LIST_NODE_NEXT_KEY, node);
            }
            current = node;
        }
        return head;
    }

    private Map<String, Object> createLinkedListNode(Object valueToken) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put(LINKED_LIST_NODE_VAL_KEY, valueToken);
        node.put(LINKED_LIST_NODE_NEXT_KEY, null);
        return node;
    }

    private boolean isNullTreeToken(Object token) {
        if (token == null) {
            return true;
        }
        if (token instanceof String textToken) {
            return "null".equalsIgnoreCase(textToken.trim());
        }
        return false;
    }

    private boolean shouldUseLinkedListShape(String sourceCode) {
        if (!StringUtils.hasText(sourceCode)) {
            return false;
        }
        String normalizedCode = sourceCode.toLowerCase(Locale.ROOT);
        return normalizedCode.contains("listnode") || normalizedCode.contains(".next");
    }

    private Object normalizeExpectedOutput(Object expectedOutput, boolean linkedListMode) {
        if (!linkedListMode) {
            return expectedOutput;
        }
        return tryParseJsonValue(expectedOutput);
    }

    private Object normalizeActualOutput(Object actualOutput, Object expectedOutput, boolean linkedListMode) {
        Object parsedExpected = normalizeComparableToken(expectedOutput);
        Object parsedActual = normalizeComparableToken(actualOutput);
        // 树题在期望为标量时，允许节点对象输出按 val 字段比较。
        parsedActual = normalizeTreeNodeActualOutput(parsedActual, parsedExpected);
        // 树题在期望为层序数组时，统一将节点对象展开为层序数组比较。
        parsedActual = normalizeTreeCollectionActualOutput(parsedActual, parsedExpected);
        if (!linkedListMode) {
            return parsedActual;
        }
        // 链表语义下，空链表与空序列保持等价，避免 null 与 [] 误判。
        if (parsedActual == null && isEmptyLinkedListSequenceExpectation(parsedExpected)) {
            return List.of();
        }
        // 链表语义下，部分执行器会将空链表序列化为 [null]，这里统一规整为空序列。
        if (parsedActual instanceof List<?> actualList
                && actualList.size() == 1
                && actualList.get(0) == null
                && isEmptyLinkedListSequenceExpectation(parsedExpected)) {
            return List.of();
        }
        if (parsedActual instanceof Map<?, ?> actualMap
                && isLinkedListNodeMap(actualMap)
                && isLinkedListSequenceExpectation(parsedExpected)) {
            // 期望是 [val, randomIndex] 结构时，优先按随机链表格式展开。
            if (isRandomPointerPairExpectation(parsedExpected) && isRandomPointerLinkedListActual(actualMap)) {
                Object flattenedRandomList = flattenRandomPointerLinkedList(castToLinkedMap(actualMap));
                if (flattenedRandomList != null) {
                    return flattenedRandomList;
                }
            }
            Object flattened = flattenLinkedListNode(actualMap);
            if (flattened != null) {
                return flattened;
            }
        }
        if (parsedActual instanceof Map<?, ?> actualMap
                && isLinkedListNodeMap(actualMap)
                && !(parsedExpected instanceof Map<?, ?>)
                && !(parsedExpected instanceof List<?>)) {
            return actualMap.get(LINKED_LIST_NODE_VAL_KEY);
        }
        return parsedActual;
    }

    private Object normalizeComparableToken(Object value) {
        Object normalized = tryParseJsonValue(value);
        // 执行器可能返回双重 JSON 编码字符串，这里最多解包两层确保比较一致。
        for (int i = 0; i < 2; i++) {
            if (!(normalized instanceof String textValue)) {
                break;
            }
            Object reparsed = tryParseJsonValue(textValue);
            if (Objects.equals(reparsed, normalized)) {
                break;
            }
            normalized = reparsed;
        }
        // 标量序列统一裁剪尾部 null，避免 [1] 与 [1, null] 这类语义等价值误判。
        normalized = trimTrailingNullScalars(normalized);
        return normalized;
    }

    private Object trimTrailingNullScalars(Object value) {
        if (!(value instanceof List<?> listValue) || !isScalarSequence(listValue)) {
            return value;
        }
        int trimIndex = listValue.size() - 1;
        while (trimIndex >= 0 && listValue.get(trimIndex) == null) {
            trimIndex--;
        }
        if (trimIndex < 0) {
            return List.of();
        }
        if (trimIndex == listValue.size() - 1) {
            return value;
        }
        return new ArrayList<>(listValue.subList(0, trimIndex + 1));
    }

    private boolean isScalarSequence(List<?> values) {
        for (Object item : values) {
            if (item == null || isScalarValue(item)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private Object normalizeTreeNodeActualOutput(Object actualOutput, Object expectedOutput) {
        if (!isTreeNodeLikeValue(actualOutput)) {
            return actualOutput;
        }
        if (!isScalarExpectation(expectedOutput)) {
            return actualOutput;
        }
        return extractTreeNodeScalarValue(actualOutput);
    }

    // 当期望为层序数组时，将树节点对象归一化为层序数组，避免对象结构直接比较导致误判。
    private Object normalizeTreeCollectionActualOutput(Object actualOutput, Object expectedOutput) {
        if (!isTreeLevelOrderExpectation(expectedOutput)) {
            return actualOutput;
        }
        if (actualOutput == null) {
            return isEmptyTreeLevelOrderExpectation(expectedOutput) ? List.of() : null;
        }
        if (!(actualOutput instanceof Map<?, ?> actualMap) || !isTreeNodeLikeValue(actualOutput)) {
            return actualOutput;
        }
        return flattenTreeNodeToLevelOrder(castToLinkedMap(actualMap));
    }

    // 判断期望是否为树题层序数组（含 matcher 包装）。
    private boolean isTreeLevelOrderExpectation(Object expectedOutput) {
        Object normalizedExpected = normalizeComparableToken(expectedOutput);
        if (normalizedExpected instanceof List<?>) {
            return true;
        }
        if (!(normalizedExpected instanceof Map<?, ?> expectedMap)) {
            return false;
        }
        if (expectedMap.containsKey(MATCHER_UNORDERED_KEY)) {
            return isTreeLevelOrderExpectation(expectedMap.get(MATCHER_UNORDERED_KEY));
        }
        if (!expectedMap.containsKey(MATCHER_ANY_OF_KEY)) {
            return false;
        }
        Object anyOfToken = normalizeComparableToken(expectedMap.get(MATCHER_ANY_OF_KEY));
        if (!(anyOfToken instanceof List<?> candidates)) {
            return false;
        }
        for (Object candidate : candidates) {
            if (isTreeLevelOrderExpectation(candidate)) {
                return true;
            }
        }
        return false;
    }

    // 判断层序期望是否为空数组（含 matcher 包装）。
    private boolean isEmptyTreeLevelOrderExpectation(Object expectedOutput) {
        Object normalizedExpected = normalizeComparableToken(expectedOutput);
        if (normalizedExpected instanceof List<?> expectedList) {
            return expectedList.isEmpty();
        }
        if (!(normalizedExpected instanceof Map<?, ?> expectedMap)) {
            return false;
        }
        if (expectedMap.containsKey(MATCHER_UNORDERED_KEY)) {
            return isEmptyTreeLevelOrderExpectation(expectedMap.get(MATCHER_UNORDERED_KEY));
        }
        if (!expectedMap.containsKey(MATCHER_ANY_OF_KEY)) {
            return false;
        }
        Object anyOfToken = normalizeComparableToken(expectedMap.get(MATCHER_ANY_OF_KEY));
        if (!(anyOfToken instanceof List<?> candidates)) {
            return false;
        }
        for (Object candidate : candidates) {
            if (isEmptyTreeLevelOrderExpectation(candidate)) {
                return true;
            }
        }
        return false;
    }

    // 将树节点对象转换为层序数组表示，并裁剪尾部冗余 null。
    private List<Object> flattenTreeNodeToLevelOrder(Map<String, Object> rootNode) {
        List<Object> levelOrderValues = new ArrayList<>();
        // 允许入队 null 占位，避免 ArrayDeque 不支持 null 导致 NPE。
        Deque<Object> queue = new LinkedList<>();
        queue.add(rootNode);
        Set<Map<?, ?>> visitedNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!queue.isEmpty()) {
            Object current = queue.removeFirst();
            if (current == null) {
                levelOrderValues.add(null);
                continue;
            }
            if (!(current instanceof Map<?, ?> currentNode) || !currentNode.containsKey(TREE_NODE_VAL_KEY)) {
                levelOrderValues.add(null);
                continue;
            }
            if (!visitedNodes.add(currentNode)) {
                continue;
            }
            levelOrderValues.add(currentNode.get(TREE_NODE_VAL_KEY));
            queue.addLast(currentNode.get(TREE_NODE_LEFT_KEY));
            queue.addLast(currentNode.get(TREE_NODE_RIGHT_KEY));
        }
        int trimIndex = levelOrderValues.size() - 1;
        while (trimIndex >= 0 && levelOrderValues.get(trimIndex) == null) {
            trimIndex--;
        }
        if (trimIndex < 0) {
            return List.of();
        }
        return new ArrayList<>(levelOrderValues.subList(0, trimIndex + 1));
    }

    private boolean isTreeNodeLikeValue(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            return false;
        }
        if (!mapValue.containsKey(TREE_NODE_VAL_KEY)) {
            return false;
        }
        return mapValue.containsKey(TREE_NODE_LEFT_KEY) || mapValue.containsKey(TREE_NODE_RIGHT_KEY);
    }

    private Object extractTreeNodeScalarValue(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            return value;
        }
        if (!mapValue.containsKey(TREE_NODE_VAL_KEY)) {
            return value;
        }
        return mapValue.get(TREE_NODE_VAL_KEY);
    }

    private boolean isScalarExpectation(Object expectedOutput) {
        Object normalizedExpected = normalizeComparableToken(expectedOutput);
        if (isScalarValue(normalizedExpected)) {
            return true;
        }
        if (!(normalizedExpected instanceof Map<?, ?> expectedMap)) {
            return false;
        }
        if (!expectedMap.containsKey(MATCHER_ANY_OF_KEY)) {
            return false;
        }
        Object anyOfToken = normalizeComparableToken(expectedMap.get(MATCHER_ANY_OF_KEY));
        if (!(anyOfToken instanceof List<?> candidates)) {
            return false;
        }
        for (Object candidate : candidates) {
            if (isScalarValue(normalizeComparableToken(candidate))) {
                return true;
            }
        }
        return false;
    }

    private boolean isScalarValue(Object value) {
        return value instanceof Number
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Character;
    }

    private Object resolveComparableActualOutput(ExecutionRecord record,
                                                 Map<String, Object> testInput,
                                                 Object normalizedExpectedOutput,
                                                 boolean shouldCompareOutput,
                                                 boolean linkedListMode,
                                                 boolean ignoreCollectionOrder) {
        Object directActualOutput = extractExecutionResult(record);
        if (!shouldCompareOutput) {
            return directActualOutput;
        }
        Object normalizedActualOutput = normalizeActualOutput(directActualOutput, normalizedExpectedOutput, linkedListMode);
        // operations 题兼容“仅断言查询操作输出”的期望格式，自动剔除多余 null 槽位。
        normalizedActualOutput = normalizeOperationsActualOutput(
                testInput,
                normalizedActualOutput,
                normalizedExpectedOutput,
                ignoreCollectionOrder
        );
        boolean nullLikeActualOutput = isNullLikeOutput(normalizedActualOutput) || isNullLikeOutput(directActualOutput);
        // 原地修改类题目返回值为 null 时，尝试读取执行后参数作为候选输出。
        if (!nullLikeActualOutput || normalizedExpectedOutput == null) {
            return normalizedActualOutput;
        }
        Object fallbackOutput = resolveActualOutputFromPostArgs(
                record,
                testInput,
                normalizedExpectedOutput,
                linkedListMode,
                ignoreCollectionOrder
        );
        return fallbackOutput != null ? fallbackOutput : normalizedActualOutput;
    }

    private Object resolveActualOutputFromPostArgs(ExecutionRecord record,
                                                   Map<String, Object> testInput,
                                                   Object normalizedExpectedOutput,
                                                   boolean linkedListMode,
                                                   boolean ignoreCollectionOrder) {
        Object postArgs = extractPostArgs(record);
        if (postArgs == null) {
            return null;
        }
        List<Object> candidates = collectPostArgsCandidates(postArgs, testInput);
        if (candidates.isEmpty()) {
            return null;
        }
        Object firstCandidate = null;
        for (Object candidate : candidates) {
            Object normalizedCandidate = normalizeActualOutput(candidate, normalizedExpectedOutput, linkedListMode);
            if (firstCandidate == null) {
                firstCandidate = normalizedCandidate;
            }
            if (sameJsonValue(normalizedExpectedOutput, normalizedCandidate, ignoreCollectionOrder)) {
                return normalizedCandidate;
            }
        }
        return firstCandidate;
    }

    private boolean isNullLikeOutput(Object value) {
        if (value == null) {
            return true;
        }
        return tryParseJsonValue(value) == null;
    }

    private Object normalizeOperationsActualOutput(Map<String, Object> testInput,
                                                   Object normalizedActualOutput,
                                                   Object normalizedExpectedOutput,
                                                   boolean ignoreCollectionOrder) {
        if (!isOperationStyleInput(testInput)) {
            return normalizedActualOutput;
        }
        Object parsedExpected = normalizeComparableToken(normalizedExpectedOutput);
        Object parsedActual = normalizeComparableToken(normalizedActualOutput);
        if (!(parsedExpected instanceof List<?>)
                || !(parsedActual instanceof List<?> actualList)
                || actualList.isEmpty()) {
            return normalizedActualOutput;
        }
        @SuppressWarnings("unchecked")
        List<Object> expectedList = (List<Object>) parsedExpected;
        // operations 场景下，期望中的 null 通常是占位，允许实际返回任意值（如 pop 返回被弹元素）。
        if (matchOperationOutputsWithNullWildcard(expectedList, actualList, ignoreCollectionOrder)) {
            return expectedList;
        }
        if (expectedList.size() == actualList.size() + 1
                && !expectedList.isEmpty()
                && isNullLikeOutput(expectedList.get(0))
                && matchOperationOutputsWithNullWildcard(expectedList.subList(1, expectedList.size()), actualList, ignoreCollectionOrder)) {
            return expectedList;
        }
        if (actualList.size() == expectedList.size() + 1
                && !actualList.isEmpty()
                && isNullLikeOutput(actualList.get(0))
                && matchOperationOutputsWithNullWildcard(expectedList, actualList.subList(1, actualList.size()), ignoreCollectionOrder)) {
            return expectedList;
        }
        if (sameJsonValue(parsedExpected, parsedActual, ignoreCollectionOrder)) {
            return parsedActual;
        }
        // 对包含副作用操作的输出序列，移除 null-like 占位后再做一次匹配。
        List<Object> compactedOutputs = new ArrayList<>(actualList.size());
        for (Object item : actualList) {
            Object normalizedItem = normalizeComparableToken(item);
            if (isNullLikeOutput(normalizedItem)) {
                continue;
            }
            compactedOutputs.add(normalizedItem);
        }
        if (sameJsonValue(parsedExpected, compactedOutputs, ignoreCollectionOrder)) {
            return compactedOutputs;
        }
        // 兼容“构造器是否记入输出”差异：若两侧仅相差一个 null 占位，则按期望形态对齐。
        Object alignedOutput = alignOperationOutputsBySingleNullGap(parsedExpected, parsedActual, ignoreCollectionOrder);
        if (alignedOutput != null) {
            return alignedOutput;
        }
        return parsedActual;
    }

    private boolean matchOperationOutputsWithNullWildcard(List<?> expectedList,
                                                          List<?> actualList,
                                                          boolean ignoreCollectionOrder) {
        if (expectedList == null || actualList == null || expectedList.size() != actualList.size()) {
            return false;
        }
        for (int index = 0; index < expectedList.size(); index++) {
            Object expectedItem = normalizeComparableToken(expectedList.get(index));
            if (isNullLikeOutput(expectedItem)) {
                continue;
            }
            Object actualItem = normalizeComparableToken(actualList.get(index));
            if (!sameJsonValue(expectedItem, actualItem, ignoreCollectionOrder)) {
                return false;
            }
        }
        return true;
    }

    private boolean isOperationStyleInput(Map<String, Object> testInput) {
        if (testInput == null || testInput.isEmpty()) {
            return false;
        }
        Object operationsToken = getValueIgnoreCase(testInput, "operations");
        return operationsToken instanceof List<?>;
    }

    private Object alignOperationOutputsBySingleNullGap(Object expectedOutput,
                                                        Object actualOutput,
                                                        boolean ignoreCollectionOrder) {
        if (!(expectedOutput instanceof List<?> expectedList) || !(actualOutput instanceof List<?> actualList)) {
            return null;
        }
        if (expectedList.size() == actualList.size() + 1) {
            for (int index = 0; index < expectedList.size(); index++) {
                if (!isNullLikeOutput(expectedList.get(index))) {
                    continue;
                }
                List<Object> reducedExpected = new ArrayList<>(expectedList);
                reducedExpected.remove(index);
                if (sameJsonValue(reducedExpected, actualList, ignoreCollectionOrder)) {
                    return expectedList;
                }
            }
        }
        if (actualList.size() == expectedList.size() + 1) {
            for (int index = 0; index < actualList.size(); index++) {
                if (!isNullLikeOutput(actualList.get(index))) {
                    continue;
                }
                List<Object> reducedActual = new ArrayList<>(actualList);
                reducedActual.remove(index);
                if (sameJsonValue(expectedList, reducedActual, ignoreCollectionOrder)) {
                    return reducedActual;
                }
            }
        }
        return null;
    }

    private Object extractPostArgs(ExecutionRecord record) {
        if (record == null || record.getMetadata() == null) {
            // 无 metadata 时，尝试从执行器统一封装的 result payload 中读取 postArgs。
            Object resultPayload = tryParseJsonValue(record == null ? null : record.getResult());
            if (resultPayload instanceof Map<?, ?> payloadMap) {
                return tryParseJsonValue(payloadMap.get(METADATA_POST_ARGS_KEY));
            }
            return null;
        }
        Object metadataPostArgs = record.getMetadata().get(METADATA_POST_ARGS_KEY);
        if (metadataPostArgs != null) {
            return tryParseJsonValue(metadataPostArgs);
        }
        Object resultPayload = tryParseJsonValue(record.getResult());
        if (resultPayload instanceof Map<?, ?> payloadMap) {
            return tryParseJsonValue(payloadMap.get(METADATA_POST_ARGS_KEY));
        }
        return null;
    }

    private Object extractExecutionResult(ExecutionRecord record) {
        if (record == null) {
            return null;
        }
        Object resultPayload = tryParseJsonValue(record.getResult());
        if (resultPayload instanceof Map<?, ?> payloadMap && payloadMap.containsKey(METADATA_RESULT_KEY)) {
            return payloadMap.get(METADATA_RESULT_KEY);
        }
        return record.getResult();
    }

    private List<Object> collectPostArgsCandidates(Object postArgs, Map<String, Object> testInput) {
        Object parsedPostArgs = tryParseJsonValue(postArgs);
        if (parsedPostArgs == null) {
            return List.of();
        }
        if (parsedPostArgs instanceof Map<?, ?> postArgsMap) {
            return collectPostArgMapCandidates(postArgsMap, testInput);
        }
        if (parsedPostArgs instanceof List<?> postArgsList) {
            if (postArgsList.isEmpty()) {
                return List.of();
            }
            List<Object> candidates = new ArrayList<>(postArgsList.size());
            for (Object candidate : postArgsList) {
                candidates.add(tryParseJsonValue(candidate));
            }
            return candidates;
        }
        return List.of(parsedPostArgs);
    }

    private List<Object> collectPostArgMapCandidates(Map<?, ?> postArgsMap, Map<String, Object> testInput) {
        if (postArgsMap.isEmpty()) {
            return List.of();
        }
        List<Object> candidates = new ArrayList<>();
        if (testInput != null && !testInput.isEmpty()) {
            for (String argumentName : testInput.keySet()) {
                if (!postArgsMap.containsKey(argumentName)) {
                    continue;
                }
                candidates.add(tryParseJsonValue(postArgsMap.get(argumentName)));
            }
        }
        for (Map.Entry<?, ?> entry : postArgsMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (testInput != null && testInput.containsKey(key)) {
                continue;
            }
            candidates.add(tryParseJsonValue(entry.getValue()));
        }
        return candidates;
    }

    private Object tryParseJsonValue(Object value) {
        if (!(value instanceof String stringValue)) {
            return value;
        }
        String trimmed = stringValue.trim();
        if (trimmed.isEmpty()) {
            return value;
        }
        try {
            return objectMapper.readValue(trimmed, Object.class);
        } catch (Exception ignored) {
            return value;
        }
    }

    private boolean isLinkedListSequenceExpectation(Object expectedOutput) {
        if (expectedOutput instanceof List<?>) {
            return true;
        }
        if (!(expectedOutput instanceof Map<?, ?> expectedMap)) {
            return false;
        }
        if (expectedMap.containsKey(MATCHER_UNORDERED_KEY)) {
            return isLinkedListSequenceExpectation(tryParseJsonValue(expectedMap.get(MATCHER_UNORDERED_KEY)));
        }
        if (!expectedMap.containsKey(MATCHER_ANY_OF_KEY)) {
            return false;
        }
        Object anyOfToken = tryParseJsonValue(expectedMap.get(MATCHER_ANY_OF_KEY));
        if (!(anyOfToken instanceof List<?> candidates)) {
            return false;
        }
        for (Object candidate : candidates) {
            if (isLinkedListSequenceExpectation(tryParseJsonValue(candidate))) {
                return true;
            }
        }
        return false;
    }

    private boolean isEmptyLinkedListSequenceExpectation(Object expectedOutput) {
        if (expectedOutput instanceof List<?> expectedList) {
            return expectedList.isEmpty();
        }
        if (!(expectedOutput instanceof Map<?, ?> expectedMap)) {
            return false;
        }
        if (expectedMap.containsKey(MATCHER_UNORDERED_KEY)) {
            return isEmptyLinkedListSequenceExpectation(tryParseJsonValue(expectedMap.get(MATCHER_UNORDERED_KEY)));
        }
        if (!expectedMap.containsKey(MATCHER_ANY_OF_KEY)) {
            return false;
        }
        Object anyOfToken = tryParseJsonValue(expectedMap.get(MATCHER_ANY_OF_KEY));
        if (!(anyOfToken instanceof List<?> candidates)) {
            return false;
        }
        for (Object candidate : candidates) {
            if (isEmptyLinkedListSequenceExpectation(tryParseJsonValue(candidate))) {
                return true;
            }
        }
        return false;
    }

    // 判断期望是否为随机链表 [val, randomIndex] 结构（支持 matcher 包装）。
    private boolean isRandomPointerPairExpectation(Object expectedOutput) {
        Object normalizedExpected = tryParseJsonValue(expectedOutput);
        if (normalizedExpected instanceof List<?> expectedList) {
            return isRandomPointerPairList(expectedList);
        }
        if (!(normalizedExpected instanceof Map<?, ?> expectedMap)) {
            return false;
        }
        if (expectedMap.containsKey(MATCHER_UNORDERED_KEY)) {
            return isRandomPointerPairExpectation(expectedMap.get(MATCHER_UNORDERED_KEY));
        }
        if (!expectedMap.containsKey(MATCHER_ANY_OF_KEY)) {
            return false;
        }
        Object anyOfToken = tryParseJsonValue(expectedMap.get(MATCHER_ANY_OF_KEY));
        if (!(anyOfToken instanceof List<?> candidates)) {
            return false;
        }
        for (Object candidate : candidates) {
            if (isRandomPointerPairExpectation(candidate)) {
                return true;
            }
        }
        return false;
    }

    // 判断实际链表节点结构是否包含 random 字段。
    private boolean isRandomPointerLinkedListActual(Map<?, ?> nodeMap) {
        if (nodeMap == null) {
            return false;
        }
        List<Map<String, Object>> nodes = collectLinkedListNodes(castToLinkedMap(nodeMap));
        return !nodes.isEmpty() && containsRandomPointerField(nodes);
    }

    // 将随机链表节点展开为 [val, randomIndex] 序列，便于与测试期望对齐比较。
    private Object flattenRandomPointerLinkedList(Map<String, Object> headNode) {
        List<Map<String, Object>> nodes = collectLinkedListNodes(headNode);
        if (nodes.isEmpty() || !containsRandomPointerField(nodes)) {
            return null;
        }
        Map<Map<String, Object>, Integer> indexByIdentity = new IdentityHashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            indexByIdentity.put(nodes.get(index), index);
        }
        List<Object> flattened = new ArrayList<>(nodes.size());
        for (Map<String, Object> node : nodes) {
            Integer randomIndex = resolveRandomTargetIndex(
                    node.get(LINKED_LIST_NODE_RANDOM_KEY),
                    indexByIdentity,
                    nodes
            );
            List<Object> pair = new ArrayList<>(2);
            pair.add(node.get(LINKED_LIST_NODE_VAL_KEY));
            pair.add(randomIndex);
            flattened.add(pair);
        }
        return flattened;
    }

    private Object flattenLinkedListNode(Map<?, ?> nodeMap) {
        if (!nodeMap.containsKey(LINKED_LIST_NODE_VAL_KEY) || !nodeMap.containsKey(LINKED_LIST_NODE_NEXT_KEY)) {
            return null;
        }
        List<Object> values = new ArrayList<>();
        Set<Map<?, ?>> visitedNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        Object cursor = nodeMap;
        while (cursor instanceof Map<?, ?> currentNode) {
            if (!visitedNodes.add(currentNode)) {
                return values;
            }
            if (!currentNode.containsKey(LINKED_LIST_NODE_VAL_KEY)) {
                return null;
            }
            values.add(currentNode.get(LINKED_LIST_NODE_VAL_KEY));
            Object nextNode = currentNode.get(LINKED_LIST_NODE_NEXT_KEY);
            if (nextNode == null) {
                return values;
            }
            cursor = nextNode;
        }
        return null;
    }

    private boolean isLinkedListNodeMap(Map<?, ?> nodeMap) {
        return nodeMap != null
                && nodeMap.containsKey(LINKED_LIST_NODE_VAL_KEY)
                && nodeMap.containsKey(LINKED_LIST_NODE_NEXT_KEY);
    }

    private String serializeResult(CodeExecutionResult result) throws JsonProcessingException {
        return objectMapper.writeValueAsString(result);
    }
}
