package com.oj.agent.core.workflow.util;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.constant.Constant;
import com.oj.agent.common.util.JsonUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import java.util.*;
import java.util.function.Supplier;

public final class StateUtil {

    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getObjectMapper();

    private StateUtil() {
    }

    public static String getStringValue(OverAllState state, String key) {
        return state.value(key)
                .map(String.class::cast)
                .orElseThrow(() -> new IllegalStateException("State key not found: " + key));
    }

    public static String getStringValue(OverAllState state, String key, String defaultValue) {
        return state.value(key).map(String.class::cast).orElse(defaultValue);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getListValue(OverAllState state, String key) {
        return state.value(key)
                .map(v -> (List<T>) v)
                .orElseThrow(() -> new IllegalStateException("State key not found: " + key));
    }

    public static <T> T getObjectValue(OverAllState state, String key, Class<T> type) {
        return state.value(key)
                .map(value -> deserializeIfNeeded(value, type))
                .orElseThrow(() -> new IllegalStateException("State key not found: " + key));
    }

    public static <T> T getObjectValueOrNull(OverAllState state, String key, Class<T> type) {
        return state.value(key).map(value -> deserializeIfNeeded(value, type)).orElse(null);
    }

    public static <T> T getOptionalObjectValueOrNull(OverAllState state, String key, Class<T> type) {
        return state.value(key)
                .filter(value -> !Objects.equals(value, OverAllState.MARK_FOR_REMOVAL))
                .map(value -> deserializeIfNeeded(value, type))
                .orElse(null);
    }

    public static <T> T getObjectValue(OverAllState state, String key, Class<T> type, T defaultValue) {
        return state.value(key).map(value -> deserializeIfNeeded(value, type)).orElse(defaultValue);
    }

    public static <T> T getObjectValue(OverAllState state, String key, Class<T> type, Supplier<T> defaultSupplier) {
        return state.value(key).map(value -> deserializeIfNeeded(value, type)).orElseGet(defaultSupplier);
    }

    // 兼容 state 中直接存 DTO 或 LinkedHashMap 的场景，按候选字段提取值。
    public static <T> T getFieldValue(OverAllState state, String key, Class<T> type, String... candidateFields) {
        return state.value(key).map(value -> extractFieldValue(value, type, candidateFields)).orElse(null);
    }

    public static boolean hasValue(OverAllState state, String key) {
        Optional<Object> value = state.value(key);
        if (value.isEmpty()) {
            return false;
        }
        Object raw = value.get();
        return !(raw instanceof String text) || !text.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMapValue(OverAllState state, String key) {
        return state.value(key)
                .map(v -> (Map<String, Object>) v)
                .orElseThrow(() -> new IllegalStateException("State key not found: " + key));
    }

    public static List<Message> getHistory(OverAllState state){
        List<Object> list = getListValue(state, Constant.MESSAGES);
        if (list == null || list.isEmpty()){
            return Collections.emptyList();
        }
        return list.subList(0, list.size() - 1).stream().map(Message.class::cast).toList();
    }

    @SuppressWarnings("unchecked")
    public static UserMessage getUserInput(OverAllState state){
        Optional<Object> messagesOpt = state.value(Constant.MESSAGES);
        if (messagesOpt.isEmpty()) return null;
        List<Message> messages = (List<Message>) messagesOpt.get();
        return (UserMessage) messages.get(messages.size() - 1);
    }

    // 按候选字段顺序读取 Map key 或 Bean 属性。
    private static <T> T extractFieldValue(Object source, Class<T> type, String... candidateFields) {
        if (source == null) {
            return null;
        }
        if (candidateFields == null || candidateFields.length == 0) {
            return deserializeIfNeeded(source, type);
        }
        for (String candidateField : candidateFields) {
            Object fieldValue = extractCandidateValue(source, candidateField);
            if (fieldValue != null) {
                return convertSimpleValue(fieldValue, type);
            }
        }
        return null;
    }

    // 统一处理 Map 与普通 Bean 的字段读取。
    private static Object extractCandidateValue(Object source, String candidateField) {
        if (source instanceof Map<?, ?> map) {
            return map.containsKey(candidateField) ? map.get(candidateField) : null;
        }
        BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(source);
        if (beanWrapper.isReadableProperty(candidateField)) {
            return beanWrapper.getPropertyValue(candidateField);
        }
        return null;
    }

    // 将字段值转换为目标类型，兼容枚举、布尔和普通对象。
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> T convertSimpleValue(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (type.isEnum()) {
            return (T) Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), String.valueOf(value));
        }
        if (Boolean.class.equals(type) || boolean.class.equals(type)) {
            return (T) Boolean.valueOf(String.valueOf(value));
        }
        if (String.class.equals(type)) {
            return type.cast(String.valueOf(value));
        }
        return deserializeIfNeeded(value, type);
    }

    private static <T> T deserializeIfNeeded(Object value, Class<T> type) {
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (value instanceof Map<?, ?> && !Map.class.equals(type)) {
            return OBJECT_MAPPER.convertValue(value, type);
        }
        return type.cast(value);
    }
}
