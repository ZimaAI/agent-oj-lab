package com.oj.agent.core.question.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.common.util.JsonUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StandardCasePoolCodec {

    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getObjectMapper();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP_TYPE = new TypeReference<>() {
    };

    private StandardCasePoolCodec() {
    }

    public static List<StandardCaseItem> parseAndNormalize(String casePoolJson) {
        List<StandardCaseItem> parsed = parse(casePoolJson);
        if (parsed.isEmpty()) {
            return List.of();
        }

        List<StandardCaseItem> publicCases = new ArrayList<>();
        List<StandardCaseItem> hiddenCases = new ArrayList<>();
        for (StandardCaseItem item : parsed) {
            if (item.isPublicCase()) {
                publicCases.add(item);
            } else {
                hiddenCases.add(item);
            }
        }

        List<StandardCaseItem> normalized = new ArrayList<>(parsed.size());
        normalized.addAll(publicCases);
        normalized.addAll(hiddenCases);
        return List.copyOf(normalized);
    }

    public static String parseNormalizeAndSerialize(String casePoolJson) {
        return serialize(parseAndNormalize(casePoolJson));
    }

    public static List<StandardCaseItem> parse(String casePoolJson) {
        if (!StringUtils.hasText(casePoolJson)) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(casePoolJson);
            if (!root.isArray()) {
                throw new IllegalArgumentException("standardCasePool must be an array");
            }
            List<StandardCaseItem> items = new ArrayList<>();
            for (JsonNode node : root) {
                if (node == null || !node.isObject()) {
                    throw new IllegalArgumentException("standardCasePool items must be objects");
                }
                items.add(toStandardCaseItem(node));
            }
            return List.copyOf(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse standardCasePool", exception);
        }
    }

    public static String serialize(List<StandardCaseItem> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize standardCasePool", exception);
        }
    }

    public static List<StandardCaseItem> resolvePublicCases(String casePoolJson) {
        return parseAndNormalize(casePoolJson).stream()
                .filter(StandardCaseItem::isPublicCase)
                .toList();
    }

    public static List<Map<String, Object>> toLegacyTestInputs(List<StandardCaseItem> casePool) {
        if (casePool == null || casePool.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> inputs = new ArrayList<>(casePool.size());
        for (StandardCaseItem item : casePool) {
            if (item.getInput() != null && !item.getInput().isEmpty()) {
                inputs.add(item.getInput());
                continue;
            }

            String stdin = item.getStdin();
            if (!StringUtils.hasText(stdin)) {
                inputs.add(Map.of());
                continue;
            }

            Map<String, Object> stdinAsObject = tryParseMap(stdin);
            if (stdinAsObject != null) {
                inputs.add(stdinAsObject);
            } else {
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put("stdin", stdin);
                inputs.add(wrapper);
            }
        }
        return List.copyOf(inputs);
    }

    public static List<Object> toLegacyExpectedOutputs(List<StandardCaseItem> casePool) {
        if (casePool == null || casePool.isEmpty()) {
            return List.of();
        }
        List<Object> expectedOutputs = new ArrayList<>(casePool.size());
        for (StandardCaseItem item : casePool) {
            if (item.getExpectedOutput() != null) {
                expectedOutputs.add(item.getExpectedOutput());
                continue;
            }
            expectedOutputs.add(item.getExpectedStdout());
        }
        return List.copyOf(expectedOutputs);
    }

    private static StandardCaseItem toStandardCaseItem(JsonNode node) {
        StandardCaseItem item = new StandardCaseItem();
        item.setPublicCase(resolvePublicCase(node));
        item.setDescription(asNullableText(node.get("description")));
        item.setStdin(resolveStdin(node));
        item.setExpectedStdout(resolveExpectedStdout(node));
        item.setInput(resolveInputObject(node));
        item.setExpectedOutput(resolveExpectedOutput(node));
        return item;
    }

    private static boolean resolvePublicCase(JsonNode node) {
        JsonNode publicCaseNode = node.get("publicCase");
        if (publicCaseNode == null) {
            publicCaseNode = node.get("isPublic");
        }
        if (publicCaseNode == null || publicCaseNode.isNull()) {
            return true;
        }
        return publicCaseNode.asBoolean(true);
    }

    private static String resolveStdin(JsonNode node) {
        JsonNode stdinNode = node.get("stdin");
        if (stdinNode != null && !stdinNode.isNull()) {
            return stdinNode.asText();
        }
        JsonNode inputNode = node.get("input");
        if (inputNode == null || inputNode.isNull()) {
            return null;
        }
        return asJsonText(inputNode);
    }

    private static String resolveExpectedStdout(JsonNode node) {
        JsonNode expectedStdoutNode = node.get("expectedStdout");
        if (expectedStdoutNode != null && !expectedStdoutNode.isNull()) {
            return expectedStdoutNode.asText();
        }
        JsonNode expectedOutputNode = node.get("expectedOutput");
        if (expectedOutputNode == null || expectedOutputNode.isNull()) {
            return null;
        }
        return asJsonText(expectedOutputNode);
    }

    private static Map<String, Object> resolveInputObject(JsonNode node) {
        JsonNode inputNode = node.get("input");
        if (inputNode == null || inputNode.isNull() || !inputNode.isObject()) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(inputNode, STRING_OBJECT_MAP_TYPE);
    }

    private static Object resolveExpectedOutput(JsonNode node) {
        JsonNode expectedOutputNode = node.get("expectedOutput");
        if (expectedOutputNode == null || expectedOutputNode.isNull()) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(expectedOutputNode, Object.class);
    }

    private static String asNullableText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private static String asJsonText(JsonNode node) {
        try {
            if (node.isTextual()) {
                return node.asText();
            }
            return OBJECT_MAPPER.writeValueAsString(OBJECT_MAPPER.convertValue(node, Object.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse standardCasePool value", exception);
        }
    }

    private static Map<String, Object> tryParseMap(String rawValue) {
        try {
            Object parsed = OBJECT_MAPPER.readValue(rawValue, Object.class);
            if (parsed instanceof Map<?, ?> parsedMap) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : parsedMap.entrySet()) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return result;
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static final class StandardCaseItem {
        private String stdin;
        private String expectedStdout;
        private boolean publicCase;
        private String description;
        private Map<String, Object> input;
        private Object expectedOutput;

        public String getStdin() {
            return stdin;
        }

        public void setStdin(String stdin) {
            this.stdin = stdin;
        }

        public String getExpectedStdout() {
            return expectedStdout;
        }

        public void setExpectedStdout(String expectedStdout) {
            this.expectedStdout = expectedStdout;
        }

        public boolean isPublicCase() {
            return publicCase;
        }

        public void setPublicCase(boolean publicCase) {
            this.publicCase = publicCase;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getInput() {
            return input;
        }

        public void setInput(Map<String, Object> input) {
            this.input = input;
        }

        public Object getExpectedOutput() {
            return expectedOutput;
        }

        public void setExpectedOutput(Object expectedOutput) {
            this.expectedOutput = expectedOutput;
        }
    }
}
