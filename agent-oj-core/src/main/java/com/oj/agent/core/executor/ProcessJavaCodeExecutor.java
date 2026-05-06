package com.oj.agent.core.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.core.question.enums.Language;
import com.oj.agent.common.util.JsonUtils;
import com.oj.agent.core.executor.model.ExecutionRecord;
import com.oj.agent.core.executor.model.GeneratedCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessJavaCodeExecutor implements CodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessJavaCodeExecutor.class);
    private static final String DEFAULT_CLASS_NAME = "UserSolution";
    private static final String CONTEXT_EXECUTION_RECORD = "executionRecord";
    private static final String CONTEXT_ARGS = "args";
    private static final String EXTERNAL_RESULT_FIELD = "result";
    private static final String EXTERNAL_POST_ARGS_FIELD = "postArgs";
    private static final String DEFAULT_JAVA_UTIL_IMPORT = "import java.util.*;";
    private static final String EXTERNAL_RUNNER_CLASS_NAME = "GraalExternalJavaInvoker";
    private static final String EXTERNAL_RUNNER_SOURCE = """
            import java.io.FileInputStream;
            import java.io.FileOutputStream;
            import java.io.ObjectInputStream;
            import java.io.ObjectOutputStream;
            import java.io.PrintWriter;
            import java.io.Serializable;
            import java.io.StringWriter;
            import java.lang.reflect.Array;
            import java.lang.reflect.Constructor;
            import java.lang.reflect.Field;
            import java.lang.reflect.Method;
            import java.lang.reflect.Modifier;
            import java.lang.reflect.Parameter;
            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.IdentityHashMap;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Locale;
            import java.util.Map;
            import java.util.Set;

            public final class GraalExternalJavaInvoker {

                private GraalExternalJavaInvoker() {
                }

                public static void main(String[] args) {
                    if (args.length < 5) {
                        System.err.println("Expected args: <className> <functionName> <argsFile> <resultFile> <errorFile>");
                        System.exit(2);
                    }

                    String className = args[0];
                    String functionName = args[1];
                    String argsFile = args[2];
                    String resultFile = args[3];
                    String errorFile = args[4];

                    try {
                        Map<String, Object> invocationArgs = readArgs(argsFile);
                        Map<String, Object> resultPayload = invoke(className, functionName, invocationArgs);
                        writeResult(resultFile, resultPayload);
                    } catch (Throwable throwable) {
                        writeError(errorFile, throwable);
                        System.exit(1);
                    }
                }

                @SuppressWarnings("unchecked")
                private static Map<String, Object> readArgs(String argsFile) throws Exception {
                    try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(argsFile))) {
                        Object value = input.readObject();
                        if (value == null) {
                            return Map.of();
                        }
                        if (value instanceof Map<?, ?> map) {
                            return (Map<String, Object>) map;
                        }
                        throw new IllegalArgumentException("Invocation args is not a map");
                    }
                }

                private static void writeResult(String resultFile, Object result) throws Exception {
                    Object serializableResult = toSerializableValue(result, Collections.newSetFromMap(new IdentityHashMap<>()));
                    try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(resultFile))) {
                        output.writeObject(serializableResult);
                    }
                }

                private static void writeError(String errorFile, Throwable throwable) {
                    try (PrintWriter writer = new PrintWriter(errorFile)) {
                        StringWriter stackTraceWriter = new StringWriter();
                        throwable.printStackTrace(new PrintWriter(stackTraceWriter));
                        writer.println(throwable.getMessage());
                        writer.println(stackTraceWriter);
                    } catch (Exception ignored) {
                        // ignore
                    }
                }

                private static Map<String, Object> invoke(String className, String functionName, Map<String, Object> args) throws Exception {
                    Class<?> targetClass = Class.forName(className);
                    if (isOperationsPayload(args)) {
                        return invokeByOperations(targetClass, functionName, args);
                    }
                    Method method = resolveTargetMethod(targetClass, functionName, args == null ? 0 : args.size());
                    Object target = Modifier.isStatic(method.getModifiers())
                            ? null
                            : targetClass.getDeclaredConstructor().newInstance();
                    List<Object> values = resolveInvocationValues(method, args);
                    Object[] invocationArgs = convertInvocationArgs(method.getParameterTypes(), values);
                    method.setAccessible(true);
                    Object result = method.invoke(target, invocationArgs);
                    return buildInvocationResult(result, method, invocationArgs);
                }

                private static boolean isOperationsPayload(Map<String, Object> args) {
                    return args != null && args.get("operations") instanceof List<?>;
                }

                private static Map<String, Object> invokeByOperations(Class<?> targetClass,
                                                                      String functionName,
                                                                      Map<String, Object> args) throws Exception {
                    List<?> operationItems = args.get("operations") instanceof List<?> list ? list : List.of();
                    List<Object> constructorArgs = extractConstructorArgs(args, operationItems, functionName);
                    Object target = instantiateTarget(targetClass, constructorArgs);

                    int startIndex = shouldSkipConstructorOperation(operationItems, functionName) ? 1 : 0;
                    List<Object> outputs = new ArrayList<>();
                    Method lastInvokedMethod = null;
                    Object[] lastInvocationArgs = null;
                    for (int index = startIndex; index < operationItems.size(); index++) {
                        ParsedOperation operation = parseOperation(operationItems.get(index));
                        if (operation == null || operation.name == null || operation.name.isBlank()) {
                            continue;
                        }
                        Method method = resolveTargetMethod(targetClass, operation.name, operation.arguments.size());
                        Object[] invocationArgs = convertInvocationArgs(method.getParameterTypes(), operation.arguments);
                        method.setAccessible(true);
                        lastInvokedMethod = method;
                        lastInvocationArgs = invocationArgs;
                        Object result = method.invoke(target, invocationArgs);
                        outputs.add(result);
                    }
                    return buildInvocationResult(outputs, lastInvokedMethod, lastInvocationArgs);
                }

                private static Map<String, Object> buildInvocationResult(Object result,
                                                                         Method invokedMethod,
                                                                         Object[] invocationArgs) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("result", result);
                    payload.put("postArgs", extractPostArgs(invokedMethod, invocationArgs));
                    return payload;
                }

                private static Map<String, Object> extractPostArgs(Method method, Object[] invocationArgs) {
                    if (method == null || invocationArgs == null || invocationArgs.length == 0) {
                        return Map.of();
                    }
                    Parameter[] parameters = method.getParameters();
                    Map<String, Object> postArgs = new LinkedHashMap<>();
                    int argCount = Math.min(parameters.length, invocationArgs.length);
                    for (int i = 0; i < argCount; i++) {
                        postArgs.put(resolveParameterName(parameters[i], i), invocationArgs[i]);
                    }
                    return postArgs;
                }

                private static String resolveParameterName(Parameter parameter, int index) {
                    if (parameter != null && parameter.isNamePresent() && parameter.getName() != null && !parameter.getName().isBlank()) {
                        return parameter.getName();
                    }
                    return "arg" + index;
                }

                private static List<Object> extractConstructorArgs(Map<String, Object> args,
                                                                   List<?> operationItems,
                                                                   String functionName) {
                    if (shouldSkipConstructorOperation(operationItems, functionName)) {
                        ParsedOperation constructorCall = parseOperation(operationItems.get(0));
                        if (constructorCall != null) {
                            return constructorCall.arguments;
                        }
                    }
                    if (args == null || args.isEmpty()) {
                        return List.of();
                    }
                    List<Object> constructorArgs = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : args.entrySet()) {
                        if ("operations".equals(entry.getKey())) {
                            continue;
                        }
                        constructorArgs.add(entry.getValue());
                    }
                    return constructorArgs;
                }

                private static boolean shouldSkipConstructorOperation(List<?> operationItems, String functionName) {
                    if (operationItems == null || operationItems.isEmpty()) {
                        return false;
                    }
                    ParsedOperation operation = parseOperation(operationItems.get(0));
                    return operation != null
                            && operation.name != null
                            && functionName != null
                            && functionName.equals(operation.name);
                }

                private static Object instantiateTarget(Class<?> targetClass, List<Object> constructorArgs) throws Exception {
                    List<Object> args = constructorArgs == null ? List.of() : constructorArgs;
                    for (Constructor<?> constructor : targetClass.getDeclaredConstructors()) {
                        if (constructor.getParameterCount() != args.size()) {
                            continue;
                        }
                        Object[] convertedArgs = convertInvocationArgs(constructor.getParameterTypes(), args);
                        constructor.setAccessible(true);
                        return constructor.newInstance(convertedArgs);
                    }
                    if (args.isEmpty()) {
                        return targetClass.getDeclaredConstructor().newInstance();
                    }
                    throw new IllegalArgumentException(
                            "Constructor parameter count mismatch: expected " + targetClass.getDeclaredConstructors().length
                                    + " constructor signatures but got " + args.size() + " args"
                    );
                }

                private static ParsedOperation parseOperation(Object operationItem) {
                    if (operationItem instanceof List<?> list && !list.isEmpty()) {
                        String name = String.valueOf(list.get(0));
                        List<Object> args = new ArrayList<>();
                        // 兼容 ["op", [arg1, arg2]] 与 ["op", arg1, arg2] 两种调用格式。
                        if (list.size() == 2 && list.get(1) instanceof List<?> nestedArgs) {
                            args.addAll(nestedArgs);
                        } else {
                            for (int index = 1; index < list.size(); index++) {
                                args.add(list.get(index));
                            }
                        }
                        return new ParsedOperation(name, args);
                    }
                    if (operationItem instanceof Map<?, ?> map) {
                        Object nameToken = map.get("method");
                        if (nameToken == null) {
                            nameToken = map.get("name");
                        }
                        if (nameToken == null) {
                            nameToken = map.get("op");
                        }
                        if (nameToken == null) {
                            return null;
                        }
                        Object argsToken = map.get("args");
                        if (argsToken == null) {
                            argsToken = map.get("arguments");
                        }
                        List<Object> args = new ArrayList<>();
                        if (argsToken instanceof List<?> list) {
                            args.addAll(list);
                        } else if (argsToken != null) {
                            args.add(argsToken);
                        }
                        return new ParsedOperation(String.valueOf(nameToken), args);
                    }
                    return null;
                }

                private static Method resolveTargetMethod(Class<?> targetClass, String functionName, int argumentCount) {
                    Method[] methods = targetClass.getDeclaredMethods();
                    for (Method method : methods) {
                        if (method.getName().equals(functionName) && method.getParameterCount() == argumentCount) {
                            return method;
                        }
                    }
                    for (Method method : methods) {
                        if (method.getName().equals(functionName)) {
                            return method;
                        }
                    }
                    throw new IllegalArgumentException("Function '" + functionName + "' was not found in class " + targetClass.getName());
                }

                // 优先按参数名从入参 Map 中提取值，避免依赖 Map 迭代顺序导致参数错位。
                private static List<Object> resolveInvocationValues(Method method, Map<String, Object> args) {
                    if (args == null || args.isEmpty()) {
                        return List.of();
                    }
                    Parameter[] parameters = method.getParameters();
                    if (parameters == null || parameters.length == 0) {
                        return List.of();
                    }
                    List<Object> orderedValues = new ArrayList<>(parameters.length);
                    boolean allMatched = true;
                    for (int index = 0; index < parameters.length; index++) {
                        String parameterName = resolveParameterName(parameters[index], index);
                        ArgumentLookup lookup = findArgumentValue(args, parameterName, index);
                        if (!lookup.found) {
                            allMatched = false;
                            break;
                        }
                        orderedValues.add(lookup.value);
                    }
                    if (allMatched) {
                        return orderedValues;
                    }
                    return new ArrayList<>(args.values());
                }

                // 兼容大小写、snake/camel、pVal/qVal 等常见参数命名差异。
                private static ArgumentLookup findArgumentValue(Map<String, Object> args,
                                                                String parameterName,
                                                                int parameterIndex) {
                    if (args == null || args.isEmpty()) {
                        return ArgumentLookup.notFound();
                    }
                    if (parameterName != null && args.containsKey(parameterName)) {
                        return ArgumentLookup.found(args.get(parameterName));
                    }
                    String normalizedName = normalizeArgumentKey(parameterName);
                    for (Map.Entry<String, Object> entry : args.entrySet()) {
                        if (normalizeArgumentKey(entry.getKey()).equals(normalizedName)) {
                            return ArgumentLookup.found(entry.getValue());
                        }
                    }
                    if ("p".equals(normalizedName)) {
                        ArgumentLookup pAlias = findAliasArgumentValue(args, List.of("pVal", "pNode", "targetP", "nodeP"));
                        if (pAlias.found) {
                            return pAlias;
                        }
                    }
                    if ("q".equals(normalizedName)) {
                        ArgumentLookup qAlias = findAliasArgumentValue(args, List.of("qVal", "qNode", "targetQ", "nodeQ"));
                        if (qAlias.found) {
                            return qAlias;
                        }
                    }
                    String positionalFallback = "arg" + parameterIndex;
                    if (args.containsKey(positionalFallback)) {
                        return ArgumentLookup.found(args.get(positionalFallback));
                    }
                    return ArgumentLookup.notFound();
                }

                private static ArgumentLookup findAliasArgumentValue(Map<String, Object> args, List<String> aliases) {
                    if (aliases == null || aliases.isEmpty()) {
                        return ArgumentLookup.notFound();
                    }
                    for (String alias : aliases) {
                        String normalizedAlias = normalizeArgumentKey(alias);
                        for (Map.Entry<String, Object> entry : args.entrySet()) {
                            if (normalizeArgumentKey(entry.getKey()).equals(normalizedAlias)) {
                                return ArgumentLookup.found(entry.getValue());
                            }
                        }
                    }
                    return ArgumentLookup.notFound();
                }

                private static String normalizeArgumentKey(String rawKey) {
                    if (rawKey == null) {
                        return "";
                    }
                    StringBuilder builder = new StringBuilder(rawKey.length());
                    for (int index = 0; index < rawKey.length(); index++) {
                        char current = rawKey.charAt(index);
                        if (current == '_' || current == '-' || Character.isWhitespace(current)) {
                            continue;
                        }
                        builder.append(Character.toLowerCase(current));
                    }
                    return builder.toString().toLowerCase(Locale.ROOT);
                }

                private static Object[] convertInvocationArgs(Class<?>[] parameterTypes, Map<String, Object> args) {
                    List<Object> values = args == null ? List.of() : new ArrayList<>(args.values());
                    return convertInvocationArgs(parameterTypes, values);
                }

                private static Object[] convertInvocationArgs(Class<?>[] parameterTypes, List<?> values) {
                    List<?> safeValues = normalizeInvocationValues(parameterTypes, values);
                    if (safeValues.size() < parameterTypes.length) {
                        throw new IllegalArgumentException(
                                "Function parameter count mismatch: expected " + parameterTypes.length + " args but got " + safeValues.size()
                        );
                    }

                    Object[] converted = new Object[parameterTypes.length];
                    IdentityHashMap<Object, Object> conversionCache = new IdentityHashMap<>();
                    for (int index = 0; index < parameterTypes.length; index++) {
                        converted[index] = convertValue(safeValues.get(index), parameterTypes[index], conversionCache);
                    }
                    return converted;
                }

                private static List<?> normalizeInvocationValues(Class<?>[] parameterTypes, List<?> values) {
                    List<?> safeValues = values == null ? List.of() : values;
                    if (safeValues.size() != 1 || !(safeValues.get(0) instanceof List<?> wrappedArgs)) {
                        return safeValues;
                    }
                    if (parameterTypes.length > 1) {
                        return wrappedArgs;
                    }
                    // 单参数场景下，若目标参数本身是容器类型，则保留原始容器参数，不做误解包。
                    if (parameterTypes.length == 1 && isContainerParameterType(parameterTypes[0])) {
                        return safeValues;
                    }
                    if (parameterTypes.length == 1
                            && wrappedArgs.size() == 1
                            && !isNestedInvocationContainer(wrappedArgs.get(0))) {
                        return List.of(wrappedArgs.get(0));
                    }
                    return safeValues;
                }

                private static boolean isContainerParameterType(Class<?> targetType) {
                    if (targetType == null) {
                        return false;
                    }
                    return targetType.isArray()
                            || List.class.isAssignableFrom(targetType)
                            || Map.class.isAssignableFrom(targetType)
                            || Object.class.equals(targetType);
                }

                private static boolean isNestedInvocationContainer(Object value) {
                    if (value == null) {
                        return false;
                    }
                    return value instanceof List<?> || value instanceof Map<?, ?> || value.getClass().isArray();
                }

                private static Object convertValue(Object value, Class<?> targetType) {
                    return convertValue(value, targetType, new IdentityHashMap<>());
                }

                private static Object convertValue(Object value,
                                                   Class<?> targetType,
                                                   IdentityHashMap<Object, Object> conversionCache) {
                    if (value == null) {
                        return null;
                    }

                    Class<?> effectiveTargetType = targetType.isPrimitive() ? toWrapperType(targetType) : targetType;
                    if (effectiveTargetType.isInstance(value)) {
                        return value;
                    }
                    if (String.class.equals(effectiveTargetType)) {
                        return String.valueOf(value);
                    }
                    if (Number.class.isAssignableFrom(effectiveTargetType) && value instanceof Number number) {
                        return convertNumber(number, effectiveTargetType);
                    }
                    if (Boolean.class.equals(effectiveTargetType)) {
                        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
                    }
                    if (Character.class.equals(effectiveTargetType)) {
                        String text = String.valueOf(value);
                        if (text.isEmpty()) {
                            throw new IllegalArgumentException("Cannot convert empty string to Character");
                        }
                        return text.charAt(0);
                    }
                    if (effectiveTargetType.isArray()) {
                        return convertToArray(value, effectiveTargetType.getComponentType(), conversionCache);
                    }
                    if (List.class.isAssignableFrom(effectiveTargetType)) {
                        return convertToList(value);
                    }
                    if (Map.class.isAssignableFrom(effectiveTargetType)) {
                        return convertToMap(value);
                    }
                    if (value instanceof Map<?, ?> map) {
                        return convertToObject(map, effectiveTargetType, conversionCache);
                    }
                    return value;
                }

                private static Object convertToArray(Object value,
                                                     Class<?> componentType,
                                                     IdentityHashMap<Object, Object> conversionCache) {
                    if (value.getClass().isArray()) {
                        int length = Array.getLength(value);
                        Object array = Array.newInstance(componentType, length);
                        for (int i = 0; i < length; i++) {
                            Array.set(array, i, convertValue(Array.get(value, i), componentType, conversionCache));
                        }
                        return array;
                    }
                    if (value instanceof List<?> list) {
                        Object array = Array.newInstance(componentType, list.size());
                        for (int i = 0; i < list.size(); i++) {
                            Object normalizedElement = normalizeArrayElementValue(list.get(i), componentType);
                            Array.set(array, i, convertValue(normalizedElement, componentType, conversionCache));
                        }
                        return array;
                    }
                    throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to array");
                }

                private static Object normalizeArrayElementValue(Object rawElement, Class<?> componentType) {
                    if (!(rawElement instanceof List<?> nodeValues)) {
                        return rawElement;
                    }
                    if (!isLinkedNodeType(componentType) || !isLinearNodeValueList(nodeValues)) {
                        return rawElement;
                    }
                    return buildLinkedNodeMap(nodeValues);
                }

                private static boolean isLinkedNodeType(Class<?> componentType) {
                    if (componentType == null
                            || componentType.isPrimitive()
                            || componentType.isArray()
                            || List.class.isAssignableFrom(componentType)
                            || Map.class.isAssignableFrom(componentType)) {
                        return false;
                    }
                    return findField(componentType, "val") != null
                            && findField(componentType, "next") != null;
                }

                private static boolean isLinearNodeValueList(List<?> nodeValues) {
                    for (Object token : nodeValues) {
                        if (token == null) {
                            continue;
                        }
                        if (token instanceof Map<?, ?> || token instanceof List<?> || token.getClass().isArray()) {
                            return false;
                        }
                    }
                    return true;
                }

                private static Map<String, Object> buildLinkedNodeMap(List<?> nodeValues) {
                    Map<String, Object> head = null;
                    Map<String, Object> current = null;
                    for (Object token : nodeValues) {
                        Map<String, Object> node = new LinkedHashMap<>();
                        node.put("val", token);
                        node.put("next", null);
                        if (head == null) {
                            head = node;
                        } else {
                            current.put("next", node);
                        }
                        current = node;
                    }
                    return head;
                }

                private static List<Object> convertToList(Object value) {
                    if (value instanceof List<?> list) {
                        return new ArrayList<>(list);
                    }
                    if (value.getClass().isArray()) {
                        int length = Array.getLength(value);
                        List<Object> list = new ArrayList<>(length);
                        for (int i = 0; i < length; i++) {
                            list.add(Array.get(value, i));
                        }
                        return list;
                    }
                    throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to list");
                }

                private static Map<?, ?> convertToMap(Object value) {
                    if (value instanceof Map<?, ?> map) {
                        return map;
                    }
                    throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to map");
                }

                private static Object convertToObject(Map<?, ?> map,
                                                      Class<?> targetType,
                                                      IdentityHashMap<Object, Object> conversionCache) {
                    try {
                        Object cached = conversionCache.get(map);
                        if (cached != null) {
                            return cached;
                        }
                        Object target = targetType.getDeclaredConstructor().newInstance();
                        conversionCache.put(map, target);
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            String fieldName = String.valueOf(entry.getKey());
                            Field field = findField(targetType, fieldName);
                            if (field == null) {
                                continue;
                            }
                            int modifiers = field.getModifiers();
                            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                                continue;
                            }
                            field.setAccessible(true);
                            Object converted = convertValue(entry.getValue(), field.getType(), conversionCache);
                            field.set(target, converted);
                        }
                        return target;
                    } catch (Exception exception) {
                        throw new IllegalArgumentException("Cannot convert map to " + targetType.getName(), exception);
                    }
                }

                private static Field findField(Class<?> targetType, String fieldName) {
                    Class<?> current = targetType;
                    while (current != null && !Object.class.equals(current)) {
                        try {
                            return current.getDeclaredField(fieldName);
                        } catch (NoSuchFieldException ignored) {
                            current = current.getSuperclass();
                        }
                    }
                    return null;
                }

                private static Object toSerializableValue(Object value, Set<Object> visited) {
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
                    if (visited.contains(value)) {
                        return summarizeCyclicReference(value);
                    }
                    visited.add(value);
                    try {
                        if (value.getClass().isArray()) {
                            int length = Array.getLength(value);
                            List<Object> converted = new ArrayList<>(length);
                            for (int i = 0; i < length; i++) {
                                converted.add(toSerializableValue(Array.get(value, i), visited));
                            }
                            return converted;
                        }
                        if (value instanceof List<?> list) {
                            List<Object> converted = new ArrayList<>(list.size());
                            for (Object item : list) {
                                converted.add(toSerializableValue(item, visited));
                            }
                            return converted;
                        }
                        if (value instanceof Map<?, ?> map) {
                            Map<String, Object> converted = new LinkedHashMap<>();
                            for (Map.Entry<?, ?> entry : map.entrySet()) {
                                converted.put(String.valueOf(entry.getKey()), toSerializableValue(entry.getValue(), visited));
                            }
                            return converted;
                        }
                        return convertObjectToMap(value, visited);
                    } finally {
                        visited.remove(value);
                    }
                }

                private static Object summarizeCyclicReference(Object value) {
                    if (value instanceof Map<?, ?> mapValue) {
                        return summarizeMapReference(mapValue);
                    }
                    if (value.getClass().isArray() || value instanceof Iterable<?>) {
                        return List.of();
                    }
                    return summarizeObjectReference(value);
                }

                private static Map<String, Object> summarizeMapReference(Map<?, ?> mapValue) {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                        summary.put(String.valueOf(entry.getKey()), toShallowScalar(entry.getValue()));
                    }
                    return summary;
                }

                private static Map<String, Object> summarizeObjectReference(Object value) {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    Class<?> current = value.getClass();
                    while (current != null && !Object.class.equals(current)) {
                        for (Field field : current.getDeclaredFields()) {
                            if (field.isSynthetic()) {
                                continue;
                            }
                            int modifiers = field.getModifiers();
                            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                                continue;
                            }
                            field.setAccessible(true);
                            try {
                                summary.put(field.getName(), toShallowScalar(field.get(value)));
                            } catch (IllegalAccessException ignored) {
                                // ignore inaccessible field
                            }
                        }
                        current = current.getSuperclass();
                    }
                    return summary;
                }

                private static Object toShallowScalar(Object value) {
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

                private static Map<String, Object> convertObjectToMap(Object value, Set<Object> visited) {
                    Map<String, Object> converted = new LinkedHashMap<>();
                    Class<?> current = value.getClass();
                    while (current != null && !Object.class.equals(current)) {
                        Field[] fields = current.getDeclaredFields();
                        for (Field field : fields) {
                            if (field.isSynthetic()) {
                                continue;
                            }
                            int modifiers = field.getModifiers();
                            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                                continue;
                            }
                            field.setAccessible(true);
                            try {
                                Object fieldValue = field.get(value);
                                converted.put(field.getName(), toSerializableValue(fieldValue, visited));
                            } catch (IllegalAccessException ignored) {
                                // ignore inaccessible field
                            }
                        }
                        current = current.getSuperclass();
                    }
                    return converted;
                }

                private static Object convertNumber(Number number, Class<?> targetType) {
                    if (Integer.class.equals(targetType)) {
                        return number.intValue();
                    }
                    if (Long.class.equals(targetType)) {
                        return number.longValue();
                    }
                    if (Double.class.equals(targetType)) {
                        return number.doubleValue();
                    }
                    if (Float.class.equals(targetType)) {
                        return number.floatValue();
                    }
                    if (Short.class.equals(targetType)) {
                        return number.shortValue();
                    }
                    if (Byte.class.equals(targetType)) {
                        return number.byteValue();
                    }
                    return number;
                }

                private static Class<?> toWrapperType(Class<?> primitiveType) {
                    if (primitiveType == int.class) {
                        return Integer.class;
                    }
                    if (primitiveType == long.class) {
                        return Long.class;
                    }
                    if (primitiveType == double.class) {
                        return Double.class;
                    }
                    if (primitiveType == float.class) {
                        return Float.class;
                    }
                    if (primitiveType == boolean.class) {
                        return Boolean.class;
                    }
                    if (primitiveType == byte.class) {
                        return Byte.class;
                    }
                    if (primitiveType == short.class) {
                        return Short.class;
                    }
                    if (primitiveType == char.class) {
                        return Character.class;
                    }
                    return primitiveType;
                }

                private static final class ParsedOperation {
                    private final String name;
                    private final List<Object> arguments;

                    private ParsedOperation(String name, List<Object> arguments) {
                        this.name = name;
                        this.arguments = arguments == null ? List.of() : new ArrayList<>(arguments);
                    }
                }

                private static final class ArgumentLookup {
                    private final boolean found;
                    private final Object value;

                    private ArgumentLookup(boolean found, Object value) {
                        this.found = found;
                        this.value = value;
                    }

                    private static ArgumentLookup found(Object value) {
                        return new ArgumentLookup(true, value);
                    }

                    private static ArgumentLookup notFound() {
                        return new ArgumentLookup(false, null);
                    }
                }
            }
            """;
    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("\\bpublic\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("\\bpackage\\s+([A-Za-z_][A-Za-z0-9_\\.]*)\\s*;");
    private static final Pattern JAVA_UTIL_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+java\\.util(?:\\.\\*|\\.[A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+[^;]+;\\s*$");
    private static final Pattern TREE_NODE_USAGE_PATTERN =
            Pattern.compile("\\bTreeNode\\b");
    private static final Pattern TREE_NODE_CLASS_PATTERN =
            Pattern.compile("\\bclass\\s+TreeNode\\b");
    private static final Pattern LIST_NODE_USAGE_PATTERN =
            Pattern.compile("\\bListNode\\b");
    private static final Pattern LIST_NODE_CLASS_PATTERN =
            Pattern.compile("\\bclass\\s+ListNode\\b");
    private static final Pattern NODE_USAGE_PATTERN =
            Pattern.compile("\\bNode\\b");
    private static final Pattern NODE_CLASS_PATTERN =
            Pattern.compile("\\bclass\\s+Node\\b");
    private static final String TREE_NODE_HELPER_CLASS = """
            class TreeNode implements java.io.Serializable {
                public int val;
                public TreeNode left;
                public TreeNode right;

                public TreeNode() {
                }

                public TreeNode(int val) {
                    this.val = val;
                }

                public TreeNode(int val, TreeNode left, TreeNode right) {
                    this.val = val;
                    this.left = left;
                    this.right = right;
                }
            }
            """;
    private static final String LIST_NODE_HELPER_CLASS = """
            class ListNode implements java.io.Serializable {
                public int val;
                public ListNode next;

                public ListNode() {
                }

                public ListNode(int val) {
                    this.val = val;
                }

                public ListNode(int val, ListNode next) {
                    this.val = val;
                    this.next = next;
                }
            }
            """;
    private static final String NODE_HELPER_CLASS = """
            class Node implements java.io.Serializable {
                public int val;
                public Node next;
                public Node random;

                public Node() {
                }

                public Node(int val) {
                    this.val = val;
                }

                public Node(int val, Node next, Node random) {
                    this.val = val;
                    this.next = next;
                    this.random = random;
                }
            }
            """;

    private final long timeoutSeconds;
    private final ObjectMapper objectMapper;

    public ProcessJavaCodeExecutor() {
        this(30);
    }

    public ProcessJavaCodeExecutor(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = JsonUtils.getObjectMapper();
    }

    @Override
    public boolean supports(Language language) {
        return Language.JAVA == language;
    }

    @Override
    public ExecutionRecord execute(GeneratedCode code, Map<String, Object> context) {
        ExecutionRecord record = (ExecutionRecord) context.get(CONTEXT_EXECUTION_RECORD);
        if (record == null) {
            record = new ExecutionRecord(code.getFunctionName(), code.getLanguage());
            context.put(CONTEXT_EXECUTION_RECORD, record);
        }
        long startTime = System.currentTimeMillis();
        Path workspace = null;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) context.getOrDefault(CONTEXT_ARGS, Map.of());
            JavaSource javaSource = prepareJavaSource(code.getCode());
            workspace = Files.createTempDirectory("agent-oj-java-exec-");
            compileSource(javaSource, workspace);
            ExternalInvocationResult invocationResult =
                    invokeFunction(javaSource.qualifiedClassName(), code.getFunctionName(), args, workspace);
            record.setSuccess(true);
            record.setResult(toJsonLiteral(invocationResult.result()));
            applyPostArgsMetadata(record, invocationResult.postArgs());
        } catch (Exception e) {
            handleGenericException(record, e);
        } finally {
            record.setDurationMs(System.currentTimeMillis() - startTime);
            record.setExecutedAt(LocalDateTime.now());
            deleteDirectoryQuietly(workspace);
        }

        return record;
    }

    private JavaSource prepareJavaSource(String code) {
        String normalizedCode = code == null ? "" : code.trim();
        boolean hasClassDeclaration = CLASS_PATTERN.matcher(normalizedCode).find();
        if (!hasClassDeclaration) {
            String importBlock = collectImportStatements(normalizedCode);
            String classBody = removeImportStatements(normalizedCode);
            StringBuilder wrappedBuilder = new StringBuilder();
            if (!importBlock.isBlank()) {
                wrappedBuilder.append(importBlock).append("\n\n");
            }
            wrappedBuilder.append("public class ").append(DEFAULT_CLASS_NAME).append(" {\n");
            if (!classBody.isBlank()) {
                wrappedBuilder.append(classBody).append("\n");
            }
            wrappedBuilder.append("}\n");
            String wrapped = wrappedBuilder.toString();
            String sourceWithImports = ensureCommonNodeDefinitions(applyDefaultImports(wrapped));
            return new JavaSource(DEFAULT_CLASS_NAME, DEFAULT_CLASS_NAME, sourceWithImports);
        }

        String sourceWithImports = ensureCommonNodeDefinitions(applyDefaultImports(normalizedCode));
        String simpleClassName = extractPrimaryClassName(sourceWithImports);
        String packageName = extractPackageName(sourceWithImports);
        String qualifiedClassName = packageName == null
                ? simpleClassName
                : packageName + "." + simpleClassName;
        return new JavaSource(simpleClassName, qualifiedClassName, sourceWithImports);
    }

    // 收集源码中的 import 行，便于在自动包裹类时提升到类定义之外。
    private String collectImportStatements(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "";
        }
        Matcher importMatcher = IMPORT_PATTERN.matcher(sourceCode);
        StringBuilder importBuilder = new StringBuilder();
        while (importMatcher.find()) {
            String importLine = importMatcher.group().trim();
            if (importLine.isEmpty()) {
                continue;
            }
            if (importBuilder.length() > 0) {
                importBuilder.append("\n");
            }
            importBuilder.append(importLine);
        }
        return importBuilder.toString();
    }

    // 移除源码中的 import 行，仅保留类体代码，避免 import 落在类定义内部导致编译错误。
    private String removeImportStatements(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "";
        }
        return IMPORT_PATTERN.matcher(sourceCode).replaceAll("").trim();
    }

    static String ensureCommonNodeDefinitions(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return sourceCode;
        }
        String sourceWithNodes = sourceCode;
        boolean usesTreeNode = TREE_NODE_USAGE_PATTERN.matcher(sourceWithNodes).find();
        boolean hasTreeNodeClass = TREE_NODE_CLASS_PATTERN.matcher(sourceWithNodes).find();
        if (usesTreeNode && !hasTreeNodeClass) {
            sourceWithNodes = insertTopLevelType(sourceWithNodes, TREE_NODE_HELPER_CLASS);
        }
        boolean usesListNode = LIST_NODE_USAGE_PATTERN.matcher(sourceWithNodes).find();
        boolean hasListNodeClass = LIST_NODE_CLASS_PATTERN.matcher(sourceWithNodes).find();
        if (usesListNode && !hasListNodeClass) {
            sourceWithNodes = insertTopLevelType(sourceWithNodes, LIST_NODE_HELPER_CLASS);
        }
        boolean usesNode = NODE_USAGE_PATTERN.matcher(sourceWithNodes).find();
        boolean hasNodeClass = NODE_CLASS_PATTERN.matcher(sourceWithNodes).find();
        if (usesNode && !hasNodeClass) {
            sourceWithNodes = insertTopLevelType(sourceWithNodes, NODE_HELPER_CLASS);
        }
        return sourceWithNodes;
    }

    private static String insertTopLevelType(String sourceCode, String typeSource) {
        int insertPosition = 0;
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (packageMatcher.find()) {
            insertPosition = packageMatcher.end();
        }
        Matcher importMatcher = IMPORT_PATTERN.matcher(sourceCode);
        while (importMatcher.find()) {
            if (importMatcher.start() >= insertPosition) {
                insertPosition = importMatcher.end();
            }
        }
        String prefix = sourceCode.substring(0, insertPosition);
        String suffix = sourceCode.substring(insertPosition).stripLeading();
        if (!prefix.isEmpty()) {
            prefix = prefix.stripTrailing() + "\n\n";
        }
        return prefix + typeSource.strip() + "\n\n" + suffix;
    }

    private String extractPrimaryClassName(String sourceCode) {
        Matcher publicClassMatcher = PUBLIC_CLASS_PATTERN.matcher(sourceCode);
        if (publicClassMatcher.find()) {
            return publicClassMatcher.group(1);
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(sourceCode);
        if (classMatcher.find()) {
            return classMatcher.group(1);
        }

        return DEFAULT_CLASS_NAME;
    }

    private String extractPackageName(String sourceCode) {
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (packageMatcher.find()) {
            return packageMatcher.group(1);
        }
        return null;
    }

    private void compileSource(JavaSource source, Path workspace) throws IOException {
        Path sourceFile = workspace.resolve(source.className() + ".java");
        Files.writeString(sourceFile, source.sourceCode(), StandardCharsets.UTF_8);
        compileJavaFile(sourceFile, workspace, "Java compilation failed:");
    }

    static List<String> buildCompilerOptions(Path workspace) {
        return List.of(
                "-proc:none",
                "-parameters",
                "-encoding", "UTF-8",
                "-classpath", workspace.toString(),
                "-d", workspace.toString()
        );
    }

    static String applyDefaultImports(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return sourceCode;
        }
        if (JAVA_UTIL_IMPORT_PATTERN.matcher(sourceCode).find()) {
            return sourceCode;
        }
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (packageMatcher.find()) {
            int insertPos = packageMatcher.end();
            return sourceCode.substring(0, insertPos)
                    + "\n\n"
                    + DEFAULT_JAVA_UTIL_IMPORT
                    + sourceCode.substring(insertPos);
        }
        return DEFAULT_JAVA_UTIL_IMPORT + "\n" + sourceCode;
    }

    static boolean supportsEmbeddedEspresso(String osName) {
        if (osName == null || osName.isBlank()) {
            return false;
        }
        return osName.toLowerCase(Locale.ROOT).contains("linux");
    }

    static boolean isEspressoUnsupportedRuntime(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("unexpected fallback symbol: initializenativecontext")
                        || normalized.contains("couldn't find libraries with llvm bitcode")
                        || normalized.contains("expected to contain bitcode")
                        || normalized.contains("core jdk libraries with llvm bitcode are currently only available")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private ExternalInvocationResult invokeFunction(String className,
                                                    String functionName,
                                                    Map<String, Object> args,
                                                    Path workspace) throws Exception {
        compileExternalRunner(workspace);

        Path argsFile = workspace.resolve("guest-args.bin");
        Path resultFile = workspace.resolve("guest-result.bin");
        Path errorFile = workspace.resolve("guest-error.log");
        Path outputFile = workspace.resolve("guest-output.log");
        writeSerializedObject(args, argsFile);

        List<String> command = List.of(
                resolveJavaCommand().toString(),
                "-cp", workspace.toString(),
                EXTERNAL_RUNNER_CLASS_NAME,
                className,
                functionName,
                argsFile.toString(),
                resultFile.toString(),
                errorFile.toString()
        );
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workspace.toFile());
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(outputFile.toFile());

        Process process = processBuilder.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Execution timeout after " + timeoutSeconds + " seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IllegalStateException(readExternalProcessError(errorFile, outputFile, exitCode));
        }
        if (!Files.exists(resultFile)) {
            throw new IllegalStateException("Execution error: external Java process did not produce result");
        }
        Object rawResult = readSerializedObject(resultFile);
        return parseExternalInvocationResult(rawResult);
    }

    private ExternalInvocationResult parseExternalInvocationResult(Object rawResult) {
        // 兼容旧 runner：仍然只返回函数结果时，直接按旧格式处理。
        if (!(rawResult instanceof Map<?, ?> resultMap)) {
            return new ExternalInvocationResult(rawResult, null);
        }
        // 仅当包含约定字段时才按新结构解析，避免误判业务返回的普通对象。
        boolean hasStructuredPayload = resultMap.containsKey(EXTERNAL_RESULT_FIELD)
                || resultMap.containsKey(EXTERNAL_POST_ARGS_FIELD);
        if (!hasStructuredPayload) {
            return new ExternalInvocationResult(rawResult, null);
        }
        Object result = resultMap.get(EXTERNAL_RESULT_FIELD);
        Map<String, Object> postArgs = normalizePostArgs(resultMap.get(EXTERNAL_POST_ARGS_FIELD));
        return new ExternalInvocationResult(result, postArgs);
    }

    private Map<String, Object> normalizePostArgs(Object postArgsValue) {
        if (!(postArgsValue instanceof Map<?, ?> rawPostArgs) || rawPostArgs.isEmpty()) {
            return null;
        }
        // 对 postArgs 的键做字符串归一化，保持序列化和上层读取稳定。
        Map<String, Object> normalizedPostArgs = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawPostArgs.entrySet()) {
            normalizedPostArgs.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalizedPostArgs;
    }

    private void applyPostArgsMetadata(ExecutionRecord record, Map<String, Object> postArgs) {
        if (record == null || postArgs == null || postArgs.isEmpty()) {
            return;
        }
        // 将调用后的参数快照写入 metadata，供上层在 void 返回时兜底比较。
        Map<String, Object> metadata = record.getMetadata();
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
            record.setMetadata(metadata);
        }
        metadata.put(EXTERNAL_POST_ARGS_FIELD, postArgs);
    }

    private void compileExternalRunner(Path workspace) throws IOException {
        Path sourceFile = workspace.resolve(EXTERNAL_RUNNER_CLASS_NAME + ".java");
        Files.writeString(sourceFile, EXTERNAL_RUNNER_SOURCE, StandardCharsets.UTF_8);
        compileJavaFile(sourceFile, workspace, "External Java runner compilation failed:");
    }

    private Path resolveJavaCommand() {
        String executable = isWindowsHost() ? "java.exe" : "java";
        String graalVmHome = System.getenv("GRAALVM_HOME");
        if (graalVmHome != null && !graalVmHome.isBlank()) {
            Path graalJava = Path.of(graalVmHome, "bin", executable);
            if (Files.isRegularFile(graalJava)) {
                return graalJava;
            }
            logger.warn("GRAALVM_HOME is set but java executable was not found at {}", graalJava);
        }

        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            Path javaCommand = Path.of(javaHome, "bin", executable);
            if (Files.isRegularFile(javaCommand)) {
                return javaCommand;
            }
        }
        throw new IllegalStateException(
                "Java executable not found for external execution. " +
                        "Please configure GRAALVM_HOME or ensure java.home points to a JDK with java binary."
        );
    }

    private boolean isWindowsHost() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("windows");
    }

    private void writeSerializedObject(Object value, Path outputFile) throws IOException {
        Object serializableValue = value instanceof Serializable ? value : String.valueOf(value);
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(Files.newOutputStream(outputFile))) {
            objectOutputStream.writeObject(serializableValue);
        }
    }

    private Object readSerializedObject(Path inputFile) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(Files.newInputStream(inputFile))) {
            return objectInputStream.readObject();
        }
    }

    private String readExternalProcessError(Path errorFile, Path outputFile, int exitCode) {
        String errorFileContent = readTextIfExists(errorFile);
        if (hasText(errorFileContent)) {
            return "Execution error: " + errorFileContent;
        }
        String outputFileContent = readTextIfExists(outputFile);
        if (hasText(outputFileContent)) {
            return "Execution error: " + outputFileContent;
        }
        return "Execution error: external Java process exited with code " + exitCode;
    }

    private String readTextIfExists(Path file) {
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return null;
        }
    }

    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    private void compileJavaFile(Path sourceFile, Path workspace, String errorPrefix) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Java compiler is not available. Please run with a full JDK.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));
            List<String> options = buildCompilerOptions(workspace);
            Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();
            if (!Boolean.TRUE.equals(success)) {
                StringBuilder errorMessage = new StringBuilder(errorPrefix);
                diagnostics.getDiagnostics().forEach(diagnostic ->
                        errorMessage.append(System.lineSeparator())
                                .append(diagnostic.getLineNumber())
                                .append(": ")
                                .append(diagnostic.getMessage(null)));
                throw new IllegalStateException(errorMessage.toString());
            }
        }
    }

    private String toJsonLiteral(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private void handleGenericException(ExecutionRecord record, Exception e) {
        record.setSuccess(false);
        record.setErrorMessage("Execution error: " + e.getMessage());
        record.setStackTrace(formatStackTrace(e.getStackTrace()));
        logger.warn("Java execution failed", e);
    }

    private String formatStackTrace(StackTraceElement[] stackTrace) {
        if (stackTrace == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            builder.append(element).append("\n");
        }
        return builder.toString();
    }

    private void deleteDirectoryQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(currentPath -> {
                        try {
                            Files.deleteIfExists(currentPath);
                        } catch (IOException ignored) {
                            logger.debug("Failed to delete temporary path {}", currentPath, ignored);
                        }
                    });
        } catch (IOException ignored) {
            logger.debug("Failed to cleanup workspace {}", path, ignored);
        }
    }

    private record JavaSource(String className, String qualifiedClassName, String sourceCode) {
    }

    private record ExternalInvocationResult(Object result, Map<String, Object> postArgs) {
    }
}
