package com.oj.agent.core.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.core.question.enums.Language;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.executor.bridge.AgentToolBridge;
import com.oj.agent.core.executor.bridge.LoggerBridge;
import com.oj.agent.core.executor.bridge.StateBridge;
import com.oj.agent.core.executor.model.ExecutionRecord;
import com.oj.agent.core.executor.model.GeneratedCode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.DisposableBean;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraalJavaScriptCodeExecutor implements CodeExecutor, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(GraalJavaScriptCodeExecutor.class);
    private static final String JAVASCRIPT_LANGUAGE = "js";
    private static final String ENGINE_COMPILATION_OPTION = "engine.Compilation";
    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getObjectMapper();
    private static final String CONTEXT_EXECUTION_RECORD = "executionRecord";
    private static final String CONTEXT_ARGS = "args";
    private static final String CONTEXT_TOOLS = "tools";
    private static final String CONTEXT_TOOL_CONTEXT = "toolContext";
    private static final String CONTEXT_TOOL_BRIDGE = "toolBridge";
    private static final String CONTEXT_STATE = "state";
    private static final String JAVASCRIPT_ENTRY_PRELUDE = """
            const __isClass = (target) => {
              if (typeof target !== "function") {
                return false;
              }
              const source = Function.prototype.toString.call(target);
              return /^class\\s/.test(source);
            };

            // 兜底节点构造器，避免模型代码直接 new ListNode/TreeNode/Node 时缺少定义。
            if (typeof globalThis.ListNode === "undefined") {
              globalThis.ListNode = function ListNode(val = 0, next = null, random = null) {
                this.val = val;
                this.next = next;
                this.random = random;
              };
            }
            if (typeof globalThis.TreeNode === "undefined") {
              globalThis.TreeNode = function TreeNode(val = 0, left = null, right = null) {
                this.val = val;
                this.left = left;
                this.right = right;
              };
            }
            if (typeof globalThis.Node === "undefined") {
              globalThis.Node = function Node(val = 0, next = null, random = null) {
                this.val = val;
                this.next = next;
                this.random = random;
              };
            }

            const __normalizeToArray = (value) => {
              if (Array.isArray(value)) {
                return value;
              }
              if (value === undefined || value === null) {
                return [];
              }
              return [value];
            };

            const __toCycleScalar = (value) => {
              if (value === null || value === undefined) {
                return null;
              }
              const valueType = typeof value;
              if (valueType === "string" || valueType === "number" || valueType === "boolean") {
                return value;
              }
              return null;
            };

            const __toCycleSummary = (value) => {
              if (Array.isArray(value)) {
                return [];
              }
              if (value instanceof Map) {
                const obj = {};
                for (const [key, item] of value.entries()) {
                  obj[String(key)] = __toCycleScalar(item);
                }
                return obj;
              }
              if (value && typeof value === "object") {
                const obj = {};
                for (const [key, item] of Object.entries(value)) {
                  obj[String(key)] = __toCycleScalar(item);
                }
                return obj;
              }
              return null;
            };

            const __isNodeLike = (value) => {
              return value
                && typeof value === "object"
                && Object.prototype.hasOwnProperty.call(value, "val")
                && (Object.prototype.hasOwnProperty.call(value, "next")
                  || Object.prototype.hasOwnProperty.call(value, "random"));
            };

            const __collectLinkedListNodes = (head) => {
              const nodes = [];
              const visited = new Set();
              let cursor = head;
              while (__isNodeLike(cursor) && !visited.has(cursor)) {
                visited.add(cursor);
                nodes.push(cursor);
                cursor = cursor.next;
              }
              return nodes;
            };

            const __resolveRandomTargetIndex = (randomToken, indexByIdentity, nodes) => {
              if (!randomToken || typeof randomToken !== "object") {
                return null;
              }
              if (indexByIdentity.has(randomToken)) {
                return indexByIdentity.get(randomToken);
              }
              if (!Object.prototype.hasOwnProperty.call(randomToken, "val")) {
                return null;
              }
              const randomVal = randomToken.val;
              for (let index = 0; index < nodes.length; index++) {
                if (nodes[index].val === randomVal) {
                  return index;
                }
              }
              return null;
            };

            const __canonicalizeRandomPointerHead = (head) => {
              if (!__isNodeLike(head)) {
                return head;
              }
              const nodes = __collectLinkedListNodes(head);
              if (nodes.length === 0 || !nodes.some((node) => Object.prototype.hasOwnProperty.call(node, "random"))) {
                return head;
              }
              const indexByIdentity = new Map();
              nodes.forEach((node, index) => {
                indexByIdentity.set(node, index);
              });
              nodes.forEach((node) => {
                const targetIndex = __resolveRandomTargetIndex(node.random, indexByIdentity, nodes);
                node.random = targetIndex === null ? null : nodes[targetIndex];
              });
              return head;
            };

            const __normalizeLinkedListArgs = (argsObject) => {
              if (!argsObject || typeof argsObject !== "object") {
                return argsObject;
              }
              const __getArgIgnoreCase = (key) => {
                if (Object.prototype.hasOwnProperty.call(argsObject, key)) {
                  return argsObject[key];
                }
                const lowerKey = String(key).toLowerCase();
                for (const [candidateKey, candidateValue] of Object.entries(argsObject)) {
                  if (String(candidateKey).toLowerCase() === lowerKey) {
                    return candidateValue;
                  }
                }
                return undefined;
              };
              const __setArgIgnoreCase = (key, value) => {
                if (Object.prototype.hasOwnProperty.call(argsObject, key)) {
                  argsObject[key] = value;
                  return;
                }
                const lowerKey = String(key).toLowerCase();
                for (const candidateKey of Object.keys(argsObject)) {
                  if (String(candidateKey).toLowerCase() === lowerKey) {
                    argsObject[candidateKey] = value;
                    return;
                  }
                }
                argsObject[key] = value;
              };
              const __parseCyclePos = (token) => {
                if (token === null || token === undefined) {
                  return null;
                }
                if (typeof token === "number" && Number.isFinite(token)) {
                  return Math.trunc(token);
                }
                if (typeof token === "string") {
                  const trimmed = token.trim();
                  if (!trimmed || trimmed.toLowerCase() === "null") {
                    return null;
                  }
                  const parsed = Number(trimmed);
                  return Number.isFinite(parsed) ? Math.trunc(parsed) : null;
                }
                return null;
              };
              const __attachCycleByPos = (head, cyclePos) => {
                if (!__isNodeLike(head) || cyclePos === null || cyclePos < 0) {
                  return head;
                }
                const nodes = __collectLinkedListNodes(head);
                if (nodes.length === 0 || cyclePos >= nodes.length) {
                  return head;
                }
                nodes[nodes.length - 1].next = nodes[cyclePos];
                return head;
              };
              const headValue = __getArgIgnoreCase("head");
              if (__isNodeLike(headValue)) {
                const cyclePos = __parseCyclePos(__getArgIgnoreCase("pos"));
                const normalizedHead = __attachCycleByPos(
                  __canonicalizeRandomPointerHead(headValue),
                  cyclePos
                );
                __setArgIgnoreCase("head", normalizedHead);
              }
              const __findLinkedListNodeIndexByVal = (nodes, targetVal) => {
                for (let i = 0; i < nodes.length; i++) {
                  if (nodes[i] && nodes[i].val === targetVal) {
                    return i;
                  }
                }
                return -1;
              };
              const __headA = __getArgIgnoreCase("headA");
              const __headB = __getArgIgnoreCase("headB");
              if (__isNodeLike(__headA) && __isNodeLike(__headB)) {
                const nodesA = __collectLinkedListNodes(__headA);
                const nodesB = __collectLinkedListNodes(__headB);
                if (nodesA.length > 0 && nodesB.length > 0) {
                  const intersectToken = __getArgIgnoreCase("intersectVal");
                  let indexA = -1;
                  let indexB = -1;
                  if (intersectToken !== undefined && intersectToken !== null && String(intersectToken).trim() !== "") {
                    indexA = __findLinkedListNodeIndexByVal(nodesA, intersectToken);
                    indexB = __findLinkedListNodeIndexByVal(nodesB, intersectToken);
                  }
                  if (indexA >= 0 && indexB >= 0) {
                    const sharedStart = nodesA[indexA];
                    if (indexB === 0) {
                      __setArgIgnoreCase("headB", sharedStart);
                    } else if (nodesB[indexB - 1]) {
                      nodesB[indexB - 1].next = sharedStart;
                    }
                  }
                }
              }
              return argsObject;
            };

            const __isTreeNodeLike = (value) => {
              return value
                && typeof value === "object"
                && Object.prototype.hasOwnProperty.call(value, "val")
                && (Object.prototype.hasOwnProperty.call(value, "left")
                  || Object.prototype.hasOwnProperty.call(value, "right"));
            };

            const __treeValEquals = (leftValue, rightValue) => {
              if (leftValue === rightValue) {
                return true;
              }
              if (typeof leftValue === "number" && typeof rightValue === "number") {
                return leftValue === rightValue;
              }
              if (leftValue === null || leftValue === undefined || rightValue === null || rightValue === undefined) {
                return false;
              }
              return String(leftValue) === String(rightValue);
            };

            const __findTreeNodeByVal = (root, targetValue) => {
              if (!__isTreeNodeLike(root)) {
                return null;
              }
              const queue = [root];
              const visited = new Set();
              while (queue.length > 0) {
                const current = queue.shift();
                if (!__isTreeNodeLike(current) || visited.has(current)) {
                  continue;
                }
                visited.add(current);
                if (__treeValEquals(current.val, targetValue)) {
                  return current;
                }
                if (__isTreeNodeLike(current.left)) {
                  queue.push(current.left);
                }
                if (__isTreeNodeLike(current.right)) {
                  queue.push(current.right);
                }
              }
              return null;
            };

            const __resolveTreeReference = (root, token) => {
              if (!__isTreeNodeLike(root)) {
                return token;
              }
              if (token && typeof token === "object" && Object.prototype.hasOwnProperty.call(token, "val")) {
                const rebound = __findTreeNodeByVal(root, token.val);
                return rebound ?? token;
              }
              if (typeof token === "number" || typeof token === "string") {
                const rebound = __findTreeNodeByVal(root, token);
                return rebound ?? token;
              }
              return token;
            };

            const __normalizeTreeArgs = (argsObject) => {
              if (!argsObject || typeof argsObject !== "object") {
                return argsObject;
              }
              const root = argsObject.root;
              if (!__isTreeNodeLike(root)) {
                return argsObject;
              }
              if (Object.prototype.hasOwnProperty.call(argsObject, "p")) {
                argsObject.p = __resolveTreeReference(root, argsObject.p);
              }
              if (Object.prototype.hasOwnProperty.call(argsObject, "q")) {
                argsObject.q = __resolveTreeReference(root, argsObject.q);
              }
              return argsObject;
            };

            const __toPlainValue = (value, path = new Set()) => {
              if (value === null || value === undefined) {
                return null;
              }
              const valueType = typeof value;
              if (valueType === "string" || valueType === "number" || valueType === "boolean") {
                return value;
              }
              if (valueType === "function") {
                return String(value);
              }
              if (path.has(value)) {
                return __toCycleSummary(value);
              }
              path.add(value);
              try {
                if (Array.isArray(value)) {
                  return value.map((item) => __toPlainValue(item, path));
                }
                if (value instanceof Map) {
                  const obj = {};
                  for (const [key, item] of value.entries()) {
                    obj[String(key)] = __toPlainValue(item, path);
                  }
                  return obj;
                }
                const obj = {};
                for (const [key, item] of Object.entries(value)) {
                  obj[String(key)] = __toPlainValue(item, path);
                }
                return obj;
              } finally {
                path.delete(value);
              }
            };

            const __callTarget = (target, argsObject) => {
              if (typeof target !== "function") {
                return target;
              }
              const values = argsObject && typeof argsObject === "object" ? Object.values(argsObject) : [];
              if (__isClass(target)) {
                return new target(...values);
              }
              const expectedArity = target.length;
              const invocationArgs = expectedArity > 0 ? values.slice(0, expectedArity) : values;
              return target(...invocationArgs);
            };

            const __parseOperation = (operation) => {
              if (Array.isArray(operation) && operation.length > 0) {
                return { name: String(operation[0]), args: operation.slice(1) };
              }
              if (operation && typeof operation === "object") {
                const name = operation.method || operation.name || operation.op;
                const rawArgs = operation.args ?? operation.arguments ?? [];
                const args = rawArgs === null ? [] : __normalizeToArray(rawArgs);
                return { name: name ? String(name) : "", args };
              }
              return { name: "", args: [] };
            };

            const __shouldSkipConstructorOperation = (operations, functionName) => {
              if (!Array.isArray(operations) || operations.length === 0) {
                return false;
              }
              const first = operations[0];
              return Array.isArray(first) && first.length > 0 && String(first[0]) === functionName;
            };

            const __sanitizeInvocationArgs = (method, args) => {
              let invocationArgs = __normalizeToArray(args).slice();
              if (invocationArgs.length === 1 && Array.isArray(invocationArgs[0])) {
                const wrappedArgs = invocationArgs[0].slice();
                if (method.length > 1) {
                  invocationArgs = wrappedArgs;
                } else if (
                  method.length === 1
                  && wrappedArgs.length === 1
                  && (wrappedArgs[0] === null || typeof wrappedArgs[0] !== "object")
                ) {
                  invocationArgs = [wrappedArgs[0]];
                }
              }
              while (
                invocationArgs.length > 0
                && invocationArgs[invocationArgs.length - 1] === null
                && invocationArgs.length - 1 >= method.length
              ) {
                invocationArgs.pop();
              }
              if (method.length >= 0 && invocationArgs.length > method.length) {
                return invocationArgs.slice(0, method.length);
              }
              return invocationArgs;
            };

            const __runOperationSequence = (functionName, target, argsObject) => {
              const operations = argsObject && typeof argsObject === "object" ? argsObject.operations : undefined;
              if (!Array.isArray(operations)) {
                return __callTarget(target, argsObject);
              }

              const constructorArgs = [];
              if (argsObject && typeof argsObject === "object") {
                for (const [key, value] of Object.entries(argsObject)) {
                  if (key === "operations") {
                    continue;
                  }
                  constructorArgs.push(value);
                }
              }

              const instantiate = (ctorArgs) => {
                if (typeof target !== "function") {
                  return target;
                }
                try {
                  return new target(...ctorArgs);
                } catch (constructorError) {
                  return target(...ctorArgs);
                }
              };

              let startIndex = 0;
              let instance = null;
              if (__shouldSkipConstructorOperation(operations, functionName)) {
                const ctorOperation = operations[0];
                const ctorArgs = __sanitizeInvocationArgs(target, ctorOperation.slice(1));
                instance = instantiate(ctorArgs);
                startIndex = 1;
              } else {
                instance = instantiate(__sanitizeInvocationArgs(target, constructorArgs));
              }

              const outputs = [];
              for (let index = startIndex; index < operations.length; index++) {
                const parsed = __parseOperation(operations[index]);
                if (!parsed.name) {
                  continue;
                }
                const method = instance[parsed.name];
                if (typeof method !== "function") {
                  continue;
                }
                const invocationArgs = __sanitizeInvocationArgs(method, parsed.args);
                const value = method.apply(instance, invocationArgs);
                outputs.push(value);
              }

              return outputs;
            };
            """;

    private final long timeoutSeconds;
    private final long memoryLimitBytes;
    private final GraalRuntimeDiagnostics runtimeDiagnostics;
    private final Engine sharedEngine;

    public GraalJavaScriptCodeExecutor() {
        this(30, 512 * 1024 * 1024L);
    }

    public GraalJavaScriptCodeExecutor(long timeoutSeconds, long memoryLimitBytes) {
        this.timeoutSeconds = timeoutSeconds;
        this.memoryLimitBytes = memoryLimitBytes;
        this.sharedEngine = createSharedEngineWithOptionalCompilationDisabled();
        this.runtimeDiagnostics = GraalRuntimeDiagnostics.capture(sharedEngine);
        logger.info("GraalJavaScriptCodeExecutor initialized with timeout={}s, memoryLimit={}MB",
                timeoutSeconds, memoryLimitBytes / (1024 * 1024));
    }

    @Override
    public boolean supports(Language language) {
        return Language.JAVASCRIPT == language;
    }

    @Override
    public ExecutionRecord execute(GeneratedCode code, Map<String, Object> context) {
        logger.info("Executing code: function={}, language={}", code.getFunctionName(), code.getLanguage());
        ExecutionRecord record = (ExecutionRecord) context.get(CONTEXT_EXECUTION_RECORD);
        if (record == null) {
            record = new ExecutionRecord(code.getFunctionName(), code.getLanguage());
            context.put(CONTEXT_EXECUTION_RECORD, record);
        }
        long startTime = System.currentTimeMillis();

        try {
            String executableCode = prepareCode(code, context);
            Object result = executeInGraalVM(executableCode, context);
            record.setSuccess(true);
            record.setResult(result != null ? result.toString() : null);
        } catch (PolyglotException e) {
            handlePolyglotException(record, e);
        } catch (Exception e) {
            handleGenericException(record, e);
        } finally {
            record.setDurationMs(System.currentTimeMillis() - startTime);
            record.setExecutedAt(LocalDateTime.now());
        }
        return record;
    }

    private String prepareCode(GeneratedCode code, Map<String, Object> context) {
        return code.getCode() + "\n\n" + generateEntryCode(code, context);
    }

    @SuppressWarnings("unchecked")
    private String generateEntryCode(GeneratedCode code, Map<String, Object> context) {
        Map<String, Object> args = (Map<String, Object>) context.getOrDefault(CONTEXT_ARGS, Map.of());
        String argsLiteral = toJsonLiteral(args);
        String functionNameLiteral = toJsonLiteral(code.getFunctionName());
        return """
                %s
                const __args = __normalizeLinkedListArgs(%s);
                const __normalizedArgs = __normalizeTreeArgs(__args);
                const __target = %s;
                const __result = __runOperationSequence(%s, __target, __normalizedArgs);
                const __plainResult = __toPlainValue(__result);
                const __postArgs = __toPlainValue(__normalizedArgs);
                const __payload = { result: __plainResult, postArgs: __postArgs };
                JSON.stringify(__payload);
                """.formatted(
                JAVASCRIPT_ENTRY_PRELUDE,
                argsLiteral,
                code.getFunctionName(),
                functionNameLiteral
        );
    }

    private String toJsonLiteral(Object value) {
        try {
            Object safeValue = toJsonSafeValue(value, Collections.newSetFromMap(new IdentityHashMap<>()));
            return OBJECT_MAPPER.writeValueAsString(safeValue);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize javascript input arguments", e);
        }
    }

    private Object toJsonSafeValue(Object value, Set<Object> path) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character) {
            return value;
        }
        if (value.getClass().isArray()) {
            if (!path.add(value)) {
                return List.of();
            }
            try {
                int length = Array.getLength(value);
                List<Object> converted = new ArrayList<>(length);
                for (int index = 0; index < length; index++) {
                    converted.add(toJsonSafeValue(Array.get(value, index), path));
                }
                return converted;
            } finally {
                path.remove(value);
            }
        }
        if (value instanceof Iterable<?> iterableValue) {
            if (!path.add(iterableValue)) {
                return List.of();
            }
            try {
                List<Object> converted = new ArrayList<>();
                for (Object item : iterableValue) {
                    converted.add(toJsonSafeValue(item, path));
                }
                return converted;
            } finally {
                path.remove(iterableValue);
            }
        }
        if (value instanceof Map<?, ?> mapValue) {
            if (!path.add(mapValue)) {
                return toJsonCycleSummary(mapValue);
            }
            try {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                    converted.put(String.valueOf(entry.getKey()), toJsonSafeValue(entry.getValue(), path));
                }
                return converted;
            } finally {
                path.remove(mapValue);
            }
        }
        return value;
    }

    private Object toJsonCycleSummary(Map<?, ?> mapValue) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
            summary.put(String.valueOf(entry.getKey()), toJsonShallowScalar(entry.getValue()));
        }
        return summary;
    }

    private Object toJsonShallowScalar(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return null;
    }

    private Object executeInGraalVM(String code, Map<String, Object> context) {
        ensureJavaScriptLanguageAvailable(runtimeDiagnostics);
        try (
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ByteArrayOutputStream errorStream = new ByteArrayOutputStream()
        ) {
            Context.Builder contextBuilder = Context.newBuilder(JAVASCRIPT_LANGUAGE)
                    .engine(sharedEngine)
                    .allowHostAccess(HostAccess.ALL)
                    .allowAllAccess(true)
                    .out(new PrintStream(outputStream, true, StandardCharsets.UTF_8))
                    .err(new PrintStream(errorStream, true, StandardCharsets.UTF_8));
            try (Context graalContext = contextBuilder.build()) {
                injectBridges(graalContext, context);
                Value result = graalContext.eval(JAVASCRIPT_LANGUAGE, code);

                AgentToolBridge toolBridge = (AgentToolBridge) context.get(CONTEXT_TOOL_BRIDGE);
                if (toolBridge != null) {
                    ExecutionRecord record = (ExecutionRecord) context.get(CONTEXT_EXECUTION_RECORD);
                    if (record != null) {
                        record.setCallTrace(toolBridge.getToolCallRecords());
                    }
                }
                if (result == null) {
                    return null;
                }
                if (result.isString()) {
                    return result.asString();
                }
                return result.toString();
            }
        } catch (IOException e) {
            logger.warn("IOException", e);
            return null;
        }
    }

    private void ensureJavaScriptLanguageAvailable(GraalRuntimeDiagnostics diagnostics) {
        if (diagnostics.hasLanguage(JAVASCRIPT_LANGUAGE)) {
            logger.debug("Graal runtime ready: {}", diagnostics.summary());
            return;
        }
        throw new IllegalStateException(diagnostics.missingLanguageMessage(JAVASCRIPT_LANGUAGE));
    }

    @SuppressWarnings("unchecked")
    private void injectBridges(Context graalContext, Map<String, Object> context) {
        Map<String, ToolCallback> tools = (Map<String, ToolCallback>) context.get(CONTEXT_TOOLS);
        ToolContext toolContext = (ToolContext) context.get(CONTEXT_TOOL_CONTEXT);
        if (tools != null && toolContext != null) {
            AgentToolBridge toolBridge = new AgentToolBridge(tools, toolContext);
            context.put(CONTEXT_TOOL_BRIDGE, toolBridge);
            graalContext.getBindings(JAVASCRIPT_LANGUAGE).putMember("agent_tool", toolBridge);
        }

        LoggerBridge loggerBridge = new LoggerBridge();
        graalContext.getBindings(JAVASCRIPT_LANGUAGE).putMember("logger", loggerBridge);

        Object state = context.get(CONTEXT_STATE);
        if (state != null) {
            StateBridge stateBridge = new StateBridge((com.alibaba.cloud.ai.graph.OverAllState) state);
            graalContext.getBindings(JAVASCRIPT_LANGUAGE).putMember("state", stateBridge);
        }
    }

    private Engine createSharedEngineWithOptionalCompilationDisabled() {
        try {
            try {
                return Engine.newBuilder()
                        .option(ENGINE_COMPILATION_OPTION, "false")
                        .build();
            } catch (IllegalArgumentException unsupported) {
                logger.debug("Graal engine option is not supported: {}", ENGINE_COMPILATION_OPTION);
                return Engine.newBuilder().build();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize shared Graal engine", e);
        }
    }

    private void handlePolyglotException(ExecutionRecord record, PolyglotException e) {
        record.setSuccess(false);
        if (e.isSyntaxError()) {
            record.setErrorMessage("Syntax error: " + e.getMessage());
        } else if (e.isGuestException()) {
            record.setErrorMessage("Runtime error: " + e.getMessage());
            record.setStackTrace(e.getStackTrace() != null ? formatStackTrace(e.getStackTrace()) : null);
        } else if (e.isCancelled()) {
            record.setErrorMessage("Execution timeout after " + timeoutSeconds + " seconds");
        } else {
            record.setErrorMessage("Execution error: " + e.getMessage());
        }
    }

    private void handleGenericException(ExecutionRecord record, Exception e) {
        record.setSuccess(false);
        record.setErrorMessage("Unexpected error: " + e.getMessage());
        record.setStackTrace(formatStackTrace(e.getStackTrace()));
    }

    private String formatStackTrace(StackTraceElement[] stackTrace) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            sb.append(element).append("\n");
        }
        return sb.toString();
    }

    @Override
    public void destroy() {
        try {
            sharedEngine.close();
            logger.info("Shared Graal JavaScript engine closed");
        } catch (Exception e) {
            logger.warn("Failed to close shared Graal JavaScript engine cleanly", e);
        }
    }
}
