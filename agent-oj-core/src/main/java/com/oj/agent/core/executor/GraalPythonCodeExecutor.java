/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraalPythonCodeExecutor implements CodeExecutor, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(GraalPythonCodeExecutor.class);

    private static final String PYTHON_LANGUAGE = "python";
    private static final String ENGINE_COMPILATION_OPTION = "engine.Compilation";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;
    private static final long DEFAULT_MEMORY_LIMIT_BYTES = 512 * 1024 * 1024; // 512MB
    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getObjectMapper();
    private static final String CONTEXT_EXECUTION_RECORD = "executionRecord";
    private static final String CONTEXT_ARGS = "args";
    private static final String CONTEXT_TOOLS = "tools";
    private static final String CONTEXT_TOOL_CONTEXT = "toolContext";
    private static final String CONTEXT_TOOL_BRIDGE = "toolBridge";
    private static final String CONTEXT_STATE = "state";
    // Python 执行入口前置脚本，负责参数转换与结果序列化。
    private static final String PYTHON_ENTRY_PRELUDE = """
            # Execution entry point
            import inspect as _inspect
            from collections import deque as _deque

            # 兜底节点定义，避免模型代码直接 new ListNode/TreeNode/Node 时缺少类型。
            if "ListNode" not in globals():
                class ListNode:
                    def __init__(self, val=0, next=None, random=None):
                        self.val = val
                        self.next = next
                        self.random = random

            if "TreeNode" not in globals():
                class TreeNode:
                    def __init__(self, val=0, left=None, right=None):
                        self.val = val
                        self.left = left
                        self.right = right

            if "Node" not in globals():
                class Node:
                    def __init__(self, val=0, next=None, random=None):
                        self.val = val
                        self.next = next
                        self.random = random

            class _AttrDict(dict):
                def __getattr__(self, name):
                    if name in self:
                        return self[name]
                    raise AttributeError(name)

                def __setattr__(self, name, value):
                    self[name] = value

            def _to_runtime_value(value):
                if isinstance(value, dict):
                    converted = _AttrDict()
                    for key, item in value.items():
                        converted[key] = _to_runtime_value(item)
                    return converted
                if isinstance(value, list):
                    return [_to_runtime_value(item) for item in value]
                if isinstance(value, tuple):
                    return tuple(_to_runtime_value(item) for item in value)
                return value

            def _to_cycle_scalar(value):
                if value is None or isinstance(value, (str, int, float, bool)):
                    return value
                return None

            def _to_cycle_summary(value):
                if isinstance(value, dict):
                    return {str(key): _to_cycle_scalar(item) for key, item in value.items()}
                if isinstance(value, (list, tuple, set)):
                    return []
                if hasattr(value, '__dict__'):
                    return {str(key): _to_cycle_scalar(item) for key, item in vars(value).items()}
                return None

            def _collect_linked_list_nodes(head):
                nodes = []
                seen = set()
                cursor = head
                while isinstance(cursor, dict) and "val" in cursor and id(cursor) not in seen:
                    seen.add(id(cursor))
                    nodes.append(cursor)
                    cursor = cursor.get("next")
                return nodes

            def _resolve_random_target_index(random_token, index_by_identity, nodes):
                if not isinstance(random_token, dict):
                    return None
                token_id = id(random_token)
                if token_id in index_by_identity:
                    return index_by_identity[token_id]
                if "val" not in random_token:
                    return None
                random_val = random_token.get("val")
                for index, node in enumerate(nodes):
                    if node.get("val") == random_val:
                        return index
                return None

            def _canonicalize_random_pointer_head(head):
                if not isinstance(head, dict):
                    return head
                nodes = _collect_linked_list_nodes(head)
                if len(nodes) == 0:
                    return head
                if not any("random" in node for node in nodes):
                    return head
                index_by_identity = {id(node): index for index, node in enumerate(nodes)}
                for index, node in enumerate(nodes):
                    target_index = _resolve_random_target_index(node.get("random"), index_by_identity, nodes)
                    node["random"] = None if target_index is None else nodes[target_index]
                return head

            def _normalize_linked_list_args(args):
                if not isinstance(args, dict):
                    return args

                def _get_arg_case_insensitive(target):
                    if target in args:
                        return args[target]
                    normalized = str(target).lower()
                    for key, value in args.items():
                        if isinstance(key, str) and key.lower() == normalized:
                            return value
                    return None

                def _set_arg_case_insensitive(target, value):
                    if target in args:
                        args[target] = value
                        return
                    normalized = str(target).lower()
                    for key in list(args.keys()):
                        if isinstance(key, str) and key.lower() == normalized:
                            args[key] = value
                            return
                    args[target] = value

                def _parse_cycle_pos(token):
                    if token is None:
                        return None
                    if isinstance(token, bool):
                        return int(token)
                    if isinstance(token, (int, float)):
                        return int(token)
                    if isinstance(token, str):
                        trimmed = token.strip()
                        if trimmed == "" or trimmed.lower() == "null":
                            return None
                        try:
                            return int(trimmed)
                        except ValueError:
                            return None
                    return None

                def _attach_cycle_by_pos(head, cycle_pos):
                    if not isinstance(head, dict) or cycle_pos is None or cycle_pos < 0:
                        return head
                    nodes = _collect_linked_list_nodes(head)
                    if len(nodes) == 0 or cycle_pos >= len(nodes):
                        return head
                    nodes[-1]["next"] = nodes[cycle_pos]
                    return head

                head_value = _get_arg_case_insensitive("head")
                if isinstance(head_value, dict):
                    cycle_pos = _parse_cycle_pos(_get_arg_case_insensitive("pos"))
                    head_value = _canonicalize_random_pointer_head(head_value)
                    head_value = _attach_cycle_by_pos(head_value, cycle_pos)
                    _set_arg_case_insensitive("head", head_value)

                def _find_node_index_by_val(nodes, target_val):
                    for index, node in enumerate(nodes):
                        if isinstance(node, dict) and node.get("val") == target_val:
                            return index
                    return None

                head_a = _get_arg_case_insensitive("headA")
                head_b = _get_arg_case_insensitive("headB")
                if isinstance(head_a, dict) and isinstance(head_b, dict):
                    nodes_a = _collect_linked_list_nodes(head_a)
                    nodes_b = _collect_linked_list_nodes(head_b)
                    if len(nodes_a) > 0 and len(nodes_b) > 0:
                        intersect_token = _get_arg_case_insensitive("intersectVal")
                        index_a = None
                        index_b = None
                        if intersect_token is not None and str(intersect_token).strip() != "":
                            index_a = _find_node_index_by_val(nodes_a, intersect_token)
                            index_b = _find_node_index_by_val(nodes_b, intersect_token)
                        if index_a is not None and index_b is not None:
                            shared_start = nodes_a[index_a]
                            if index_b == 0:
                                _set_arg_case_insensitive("headB", shared_start)
                            else:
                                previous = nodes_b[index_b - 1]
                                if isinstance(previous, dict):
                                    previous["next"] = shared_start
                return args

            def _is_tree_node_like(node):
                return isinstance(node, dict) and "val" in node and ("left" in node or "right" in node)

            def _tree_value_equals(left, right):
                if left == right:
                    return True
                if isinstance(left, (int, float)) and isinstance(right, (int, float)):
                    return left == right
                if left is None or right is None:
                    return False
                return str(left) == str(right)

            def _find_tree_node_by_value(root, target_value):
                if not _is_tree_node_like(root):
                    return None
                queue = _deque([root])
                visited = set()
                while queue:
                    current = queue.popleft()
                    current_id = id(current)
                    if current_id in visited:
                        continue
                    visited.add(current_id)
                    if _tree_value_equals(current.get("val"), target_value):
                        return current
                    left = current.get("left")
                    if _is_tree_node_like(left):
                        queue.append(left)
                    right = current.get("right")
                    if _is_tree_node_like(right):
                        queue.append(right)
                return None

            def _resolve_tree_reference(root, token):
                if not _is_tree_node_like(root):
                    return token
                if isinstance(token, dict) and "val" in token:
                    rebound = _find_tree_node_by_value(root, token.get("val"))
                    return rebound if rebound is not None else token
                if isinstance(token, (int, float, str)):
                    rebound = _find_tree_node_by_value(root, token)
                    return rebound if rebound is not None else token
                return token

            def _normalize_tree_reference_args(args):
                if not isinstance(args, dict):
                    return args
                root = args.get("root")
                if not _is_tree_node_like(root):
                    return args
                if "p" in args:
                    args["p"] = _resolve_tree_reference(root, args.get("p"))
                if "q" in args:
                    args["q"] = _resolve_tree_reference(root, args.get("q"))
                return args

            def _to_snake_case(name):
                if not isinstance(name, str):
                    return name
                replaced = name.replace("-", "_")
                chars = []
                for index, ch in enumerate(replaced):
                    if ch.isupper():
                        if index > 0 and chars and chars[-1] != "_":
                            chars.append("_")
                        chars.append(ch.lower())
                    else:
                        chars.append(ch)
                return "".join(chars)

            def _to_camel_case(name):
                if not isinstance(name, str):
                    return name
                parts = [part for part in name.replace("-", "_").split("_") if part]
                if len(parts) == 0:
                    return name
                return parts[0].lower() + "".join(part[:1].upper() + part[1:].lower() for part in parts[1:])

            def _resolve_arg_value(args, name):
                if name in args:
                    return args[name], True
                snake_name = _to_snake_case(name)
                if snake_name in args:
                    return args[snake_name], True
                camel_name = _to_camel_case(name)
                if camel_name in args:
                    return args[camel_name], True
                for key, value in args.items():
                    if not isinstance(key, str):
                        continue
                    if _to_snake_case(key) == snake_name or _to_camel_case(key) == camel_name:
                        return value, True
                return None, False

            def _to_plain_value(value, _path=None):
                if _path is None:
                    _path = set()
                if value is None or isinstance(value, (str, int, float, bool)):
                    return value
                value_id = id(value)
                if value_id in _path:
                    return _to_cycle_summary(value)
                _path.add(value_id)
                try:
                    if isinstance(value, dict):
                        return {str(key): _to_plain_value(item, _path) for key, item in value.items()}
                    if isinstance(value, (list, tuple, set)):
                        return [_to_plain_value(item, _path) for item in value]
                    if hasattr(value, '__dict__'):
                        return {str(key): _to_plain_value(item, _path) for key, item in vars(value).items()}
                finally:
                    _path.remove(value_id)
                return value

            def _call_target(target, args):
                if not isinstance(args, dict):
                    return target(args)
                try:
                    signature = _inspect.signature(target)
                except (TypeError, ValueError):
                    return target(**args)

                parameters = signature.parameters
                allows_var_kwargs = any(
                    parameter.kind == _inspect.Parameter.VAR_KEYWORD
                    for parameter in parameters.values()
                )
                if allows_var_kwargs:
                    return target(**args)

                accepted_kwargs = {}
                for name, parameter in parameters.items():
                    resolved_value, found = _resolve_arg_value(args, name)
                    if parameter.kind in (
                            _inspect.Parameter.POSITIONAL_OR_KEYWORD,
                            _inspect.Parameter.KEYWORD_ONLY
                    ) and found:
                        accepted_kwargs[name] = resolved_value

                try:
                    return target(**accepted_kwargs)
                except TypeError:
                    positional_args = []
                    for name, parameter in parameters.items():
                        resolved_value, found = _resolve_arg_value(args, name)
                        if parameter.kind in (
                                _inspect.Parameter.POSITIONAL_ONLY,
                                _inspect.Parameter.POSITIONAL_OR_KEYWORD
                        ) and found:
                            positional_args.append(resolved_value)
                    return target(*positional_args)

            def _parse_operation(operation):
                if isinstance(operation, (list, tuple)) and len(operation) > 0:
                    return str(operation[0]), list(operation[1:])
                if isinstance(operation, dict):
                    name = operation.get("method") or operation.get("name") or operation.get("op")
                    args = operation.get("args", operation.get("arguments", []))
                    if args is None:
                        args = []
                    elif not isinstance(args, list):
                        args = [args]
                    return str(name), args
                return None, []

            def _is_constructor_operation(operation, function_name):
                if not isinstance(operation, (list, tuple)) or len(operation) == 0:
                    return False
                return str(operation[0]) == function_name

            def _sanitize_callable_args(callable_obj, raw_args):
                if raw_args is None:
                    args = []
                elif isinstance(raw_args, list):
                    args = list(raw_args)
                elif isinstance(raw_args, tuple):
                    args = list(raw_args)
                else:
                    args = [raw_args]

                try:
                    signature = _inspect.signature(callable_obj)
                except (TypeError, ValueError):
                    return args

                positional_params = [
                    parameter
                    for parameter in signature.parameters.values()
                    if parameter.kind in (
                        _inspect.Parameter.POSITIONAL_ONLY,
                        _inspect.Parameter.POSITIONAL_OR_KEYWORD
                    )
                ]
                required_args = sum(
                    1
                    for parameter in positional_params
                    if parameter.default is _inspect._empty
                )
                allows_varargs = any(
                    parameter.kind == _inspect.Parameter.VAR_POSITIONAL
                    for parameter in signature.parameters.values()
                )

                if len(args) == 1 and isinstance(args[0], list):
                    wrapped_args = list(args[0])
                    if len(positional_params) > 1 and not allows_varargs:
                        args = wrapped_args
                    elif (
                        len(positional_params) == 1
                        and len(wrapped_args) == 1
                        and not isinstance(wrapped_args[0], (list, tuple, dict, set))
                    ):
                        args = [wrapped_args[0]]

                while args and args[-1] is None and len(args) - 1 >= required_args:
                    args.pop()

                if not allows_varargs and len(args) > len(positional_params):
                    args = args[:len(positional_params)]
                return args

            def _run_operation_sequence(function_name, target, args):
                operations = args.get("operations")
                if not isinstance(operations, list):
                    return _call_target(target, args)

                start_index = 0
                instance = None
                if operations and _is_constructor_operation(operations[0], function_name):
                    constructor_args = _sanitize_callable_args(target, operations[0][1:])
                    instance = target(*constructor_args)
                    start_index = 1
                else:
                    ctor_kwargs = {key: value for key, value in args.items() if key != "operations"}
                    instance = _call_target(target, ctor_kwargs)

                outputs = []
                for operation in operations[start_index:]:
                    method_name, method_args = _parse_operation(operation)
                    if not method_name:
                        continue
                    method = getattr(instance, method_name)
                    invocation_args = _sanitize_callable_args(method, method_args)
                    value = method(*invocation_args)
                    outputs.append(value)

                return outputs
            """;

    private final long timeoutSeconds;
    private final long memoryLimitBytes;
    private final GraalRuntimeDiagnostics runtimeDiagnostics;
    private final Engine sharedEngine;

    public GraalPythonCodeExecutor() {
        this(DEFAULT_TIMEOUT_SECONDS, DEFAULT_MEMORY_LIMIT_BYTES);
    }

    public GraalPythonCodeExecutor(long timeoutSeconds, long memoryLimitBytes) {
        this.timeoutSeconds = timeoutSeconds;
        this.memoryLimitBytes = memoryLimitBytes;
        this.sharedEngine = createSharedEngineWithOptionalCompilationDisabled();
        this.runtimeDiagnostics = GraalRuntimeDiagnostics.capture(sharedEngine);
        logger.info("GraalCodeExecutor initialized with timeout={}s, memoryLimit={}MB",
                timeoutSeconds, memoryLimitBytes / (1024 * 1024));
    }

    @Override
    public boolean supports(Language language) {
        return Language.PYTHON == language;
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
            logger.info("python 代码执行工具执行代码：\n{}", executableCode);
            Object result = executeInGraalVM(executableCode, context);

            record.setSuccess(true);
            record.setResult(result != null ? result.toString() : null);
            logger.info("Code execution successful: function={}", code.getFunctionName());
        } catch (PolyglotException e) {
            handlePolyglotException(record, e);
        } catch (Exception e) {
            handleGenericException(record, e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            record.setDurationMs(duration);
            record.setExecutedAt(LocalDateTime.now());
        }

        return record;
    }

    private String prepareCode(GeneratedCode code, Map<String, Object> context) {
        String originalCode = sanitizeShadowingFactoryFunction(code.getCode(), code.getFunctionName());
        String entryCode = generateEntryCode(code, context);
        return originalCode + "\n\n" + entryCode;
    }

    // 清理“类与同名工厂函数并存且工厂函数直接递归调用自身”的无效包装，避免运行时递归爆栈。
    private String sanitizeShadowingFactoryFunction(String sourceCode, String functionName) {
        if (sourceCode == null || sourceCode.isBlank() || functionName == null || functionName.isBlank()) {
            return sourceCode;
        }
        Pattern classPattern = Pattern.compile("(?m)^\\s*class\\s+" + Pattern.quote(functionName) + "\\b");
        if (!classPattern.matcher(sourceCode).find()) {
            return sourceCode;
        }
        Pattern factoryPattern = Pattern.compile(
                "(?ms)^\\s*def\\s+" + Pattern.quote(functionName)
                        + "\\s*\\([^\\n]*\\)\\s*:\\s*\\n(?:[ \\t]+.*(?:\\n|$))+"
        );
        Pattern recursiveReturnPattern = Pattern.compile(
                "(?m)^\\s*return\\s+" + Pattern.quote(functionName) + "\\s*\\("
        );
        Matcher matcher = factoryPattern.matcher(sourceCode);
        StringBuilder sanitized = new StringBuilder();
        int lastEnd = 0;
        boolean removed = false;
        while (matcher.find()) {
            String candidateBlock = matcher.group();
            if (!recursiveReturnPattern.matcher(candidateBlock).find()) {
                continue;
            }
            sanitized.append(sourceCode, lastEnd, matcher.start());
            lastEnd = matcher.end();
            removed = true;
        }
        if (!removed) {
            return sourceCode;
        }
        sanitized.append(sourceCode.substring(lastEnd));
        return sanitized.toString();
    }

    private String generateEntryCode(GeneratedCode code, Map<String, Object> context) {
        String functionName = code.getFunctionName();
        Map<String, Object> args = (Map<String, Object>) context.getOrDefault(CONTEXT_ARGS, Map.of());
        String pythonArgsLiteral = toPythonLiteral(args);

        StringBuilder entryCode = new StringBuilder();
        entryCode.append(PYTHON_ENTRY_PRELUDE).append("\n");
        entryCode.append("_args = _to_runtime_value(").append(pythonArgsLiteral).append(")\n");
        entryCode.append("_args = _normalize_linked_list_args(_args)\n");
        entryCode.append("_args = _normalize_tree_reference_args(_args)\n");
        entryCode.append("_target = ").append(functionName).append("\n");
        entryCode.append("_result = _run_operation_sequence(")
                .append(quotePythonString(functionName))
                .append(", _target, _args)\n");
        entryCode.append("_result = _to_plain_value(_result)\n");
        entryCode.append("_post_args = _to_plain_value(_args)\n");
        entryCode.append("_payload = {\"result\": _result, \"postArgs\": _post_args}\n");
        entryCode.append("import json as _json\n");
        entryCode.append("_json.dumps(_payload, ensure_ascii=False, sort_keys=True)");

        return entryCode.toString();
    }

    private String toPythonLiteral(Object value) {
        return toPythonLiteral(value, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private String toPythonLiteral(Object value, Set<Object> visited) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return quotePythonString(text);
        }
        if (value instanceof Character character) {
            return quotePythonString(character.toString());
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> mapValue) {
            if (!visited.add(mapValue)) {
                return toPythonCycleSummary(mapValue);
            }
            StringBuilder builder = new StringBuilder("{");
            try {
                Iterator<? extends Map.Entry<?, ?>> iterator = mapValue.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<?, ?> entry = iterator.next();
                    builder.append(toPythonLiteral(String.valueOf(entry.getKey()), visited))
                            .append(": ")
                            .append(toPythonLiteral(entry.getValue(), visited));
                    if (iterator.hasNext()) {
                        builder.append(", ");
                    }
                }
                return builder.append("}").toString();
            } finally {
                visited.remove(mapValue);
            }
        }
        if (value instanceof Iterable<?> iterableValue) {
            if (!visited.add(iterableValue)) {
                return "[]";
            }
            StringBuilder builder = new StringBuilder("[");
            try {
                Iterator<?> iterator = iterableValue.iterator();
                while (iterator.hasNext()) {
                    builder.append(toPythonLiteral(iterator.next(), visited));
                    if (iterator.hasNext()) {
                        builder.append(", ");
                    }
                }
                return builder.append("]").toString();
            } finally {
                visited.remove(iterableValue);
            }
        }
        if (value.getClass().isArray()) {
            if (!visited.add(value)) {
                return "[]";
            }
            StringBuilder builder = new StringBuilder("[");
            try {
                int length = Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    if (index > 0) {
                        builder.append(", ");
                    }
                    builder.append(toPythonLiteral(Array.get(value, index), visited));
                }
                return builder.append("]").toString();
            } finally {
                visited.remove(value);
            }
        }
        return quotePythonString(String.valueOf(value));
    }

    private String toPythonCycleSummary(Map<?, ?> mapValue) {
        StringBuilder builder = new StringBuilder("{");
        Iterator<? extends Map.Entry<?, ?>> iterator = mapValue.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            builder.append(quotePythonString(String.valueOf(entry.getKey())))
                    .append(": ")
                    .append(toPythonShallowScalar(entry.getValue()));
            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        return builder.append("}").toString();
    }

    private String toPythonShallowScalar(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return quotePythonString(text);
        }
        if (value instanceof Character character) {
            return quotePythonString(character.toString());
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof Enum<?> enumValue) {
            return quotePythonString(enumValue.name());
        }
        return "None";
    }

    private String quotePythonString(String value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize python string literal", e);
        }
    }

    private Object executeInGraalVM(String code, Map<String, Object> context) {
        ensurePythonLanguageAvailable(runtimeDiagnostics);

        try (
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ByteArrayOutputStream errorStream = new ByteArrayOutputStream()) {
            // 构建 Python 执行上下文。
            Context.Builder contextBuilder = Context.newBuilder(PYTHON_LANGUAGE)
                    .engine(sharedEngine)
                    .allowHostAccess(HostAccess.ALL)
                    .allowAllAccess(true)
                    .out(new PrintStream(outputStream, true, StandardCharsets.UTF_8))
                    .err(new PrintStream(errorStream, true, StandardCharsets.UTF_8));
            try (Context graalContext = contextBuilder.build()) {
                injectBridges(graalContext, context);

                Value result = graalContext.eval(PYTHON_LANGUAGE, code);

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

    private void ensurePythonLanguageAvailable(GraalRuntimeDiagnostics diagnostics) {
        if (diagnostics.hasLanguage(PYTHON_LANGUAGE)) {
            logger.debug("Graal runtime ready: {}", diagnostics.summary());
            return;
        }
        throw new IllegalStateException(diagnostics.missingLanguageMessage(PYTHON_LANGUAGE));
    }

    private void injectBridges(Context graalContext, Map<String, Object> context) {
        Map<String, ToolCallback> tools = (Map<String, ToolCallback>) context.get(CONTEXT_TOOLS);
        ToolContext toolContext = (ToolContext) context.get(CONTEXT_TOOL_CONTEXT);

        if (tools != null && toolContext != null) {
            AgentToolBridge toolBridge = new AgentToolBridge(tools, toolContext);
            context.put(CONTEXT_TOOL_BRIDGE, toolBridge);
            graalContext.getBindings(PYTHON_LANGUAGE).putMember("agent_tool", toolBridge);
            logger.debug("Injected AgentToolBridge with {} tools", tools.size());
        }

        LoggerBridge loggerBridge = new LoggerBridge();
        graalContext.getBindings(PYTHON_LANGUAGE).putMember("logger", loggerBridge);
        logger.debug("Injected LoggerBridge");

        Object state = context.get(CONTEXT_STATE);
        if (state != null) {
            StateBridge stateBridge = new StateBridge((com.alibaba.cloud.ai.graph.OverAllState) state);
            graalContext.getBindings(PYTHON_LANGUAGE).putMember("state", stateBridge);
            logger.debug("Injected StateBridge");
        }
    }

    // 构建可复用的 Graal 引擎实例，避免每次执行都重新创建引擎导致元空间持续增长。
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
            logger.error("Python syntax error: {}", e.getMessage());
        } else if (e.isGuestException()) {
            record.setErrorMessage("Runtime error: " + e.getMessage());
            record.setStackTrace(e.getStackTrace() != null ? formatStackTrace(e.getStackTrace()) : null);
            logger.error("Python runtime error: {}", e.getMessage(), e);
        } else if (e.isCancelled()) {
            record.setErrorMessage("Execution timeout after " + timeoutSeconds + " seconds");
            logger.error("Python execution timeout");
        } else {
            record.setErrorMessage("Execution error: " + e.getMessage());
            logger.error("Python execution error: {}", e.getMessage(), e);
        }
    }

    private void handleGenericException(ExecutionRecord record, Exception e) {
        record.setSuccess(false);
        record.setErrorMessage("Unexpected error: " + e.getMessage());
        record.setStackTrace(formatStackTrace(e.getStackTrace()));
        logger.error("Unexpected execution error: {}", e.getMessage(), e);
    }

    private String formatStackTrace(StackTraceElement[] stackTrace) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public void destroy() {
        try {
            sharedEngine.close();
            logger.info("Shared Graal engine closed");
        } catch (Exception e) {
            logger.warn("Failed to close shared Graal engine cleanly", e);
        }
    }

}
