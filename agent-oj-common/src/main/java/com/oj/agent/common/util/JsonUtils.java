package com.oj.agent.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Pattern CODE_BLOCK_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private JsonUtils() {
    }

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    public static <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
        String extractedJson = extractJson(json);
        try {
            return OBJECT_MAPPER.readValue(extractedJson, clazz);
        }
        catch (JsonProcessingException originalException) {
            String repairedJson = repairJsonLikePayload(extractedJson);
            if (repairedJson.equals(extractedJson)) {
                throw originalException;
            }
            return OBJECT_MAPPER.readValue(repairedJson, clazz);
        }
    }

    public static String toJson(Object obj) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(obj);
    }

    public static String extractJson(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return trimmed;
    }

    private static String repairJsonLikePayload(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return json;
        }
        String normalizedLiterals = normalizePythonLiterals(json);
        return expandPythonListMultiplication(normalizedLiterals);
    }

    private static String normalizePythonLiterals(String json) {
        StringBuilder result = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        boolean changed = false;
        int index = 0;

        while (index < json.length()) {
            char current = json.charAt(index);

            if (escaped) {
                result.append(current);
                escaped = false;
                index++;
                continue;
            }
            if (current == '\\' && inString) {
                result.append(current);
                escaped = true;
                index++;
                continue;
            }
            if (current == '"') {
                result.append(current);
                inString = !inString;
                index++;
                continue;
            }
            if (!inString && Character.isLetter(current)) {
                int tokenStart = index;
                int tokenEnd = index + 1;
                while (tokenEnd < json.length() && Character.isLetter(json.charAt(tokenEnd))) {
                    tokenEnd++;
                }

                String token = json.substring(tokenStart, tokenEnd);
                String replacement = switch (token) {
                    case "None" -> "null";
                    case "True" -> "true";
                    case "False" -> "false";
                    default -> null;
                };
                if (replacement != null && isStandaloneToken(json, tokenStart, tokenEnd)) {
                    result.append(replacement);
                    changed = true;
                } else {
                    result.append(token);
                }
                index = tokenEnd;
                continue;
            }

            result.append(current);
            index++;
        }

        return changed ? result.toString() : json;
    }

    private static String expandPythonListMultiplication(String json) throws JsonProcessingException {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        boolean changed = false;
        int index = 0;

        while (index < json.length()) {
            char current = json.charAt(index);

            if (escaped) {
                result.append(current);
                escaped = false;
                index++;
                continue;
            }
            if (current == '\\' && inString) {
                result.append(current);
                escaped = true;
                index++;
                continue;
            }
            if (current == '"') {
                result.append(current);
                inString = !inString;
                index++;
                continue;
            }
            if (!inString && current == '[') {
                ListMultiplicationMatch match = tryExpandListMultiplication(json, index);
                if (match != null) {
                    result.append(match.expandedJson());
                    index = match.endExclusive();
                    changed = true;
                    continue;
                }
            }

            result.append(current);
            index++;
        }

        return changed ? result.toString() : json;
    }

    private static ListMultiplicationMatch tryExpandListMultiplication(String json, int startIndex)
            throws JsonProcessingException {
        int closingBracketIndex = findMatchingSquareBracket(json, startIndex);
        if (closingBracketIndex < 0) {
            return null;
        }

        int starIndex = skipWhitespace(json, closingBracketIndex + 1);
        if (starIndex >= json.length() || json.charAt(starIndex) != '*') {
            return null;
        }

        int repeatTokenStart = skipWhitespace(json, starIndex + 1);
        if (repeatTokenStart >= json.length()) {
            return null;
        }

        RepeatCountResolution repeatCountResolution = resolveRepeatCount(json, repeatTokenStart, startIndex);
        if (repeatCountResolution == null || repeatCountResolution.repeatCount() < 0) {
            return null;
        }

        String listJson = json.substring(startIndex, closingBracketIndex + 1);
        String repairedListJson = expandPythonListMultiplication(listJson);
        List<?> values = OBJECT_MAPPER.readValue(repairedListJson, List.class);

        List<Object> expandedValues = new ArrayList<>(values.size() * repeatCountResolution.repeatCount());
        for (int i = 0; i < repeatCountResolution.repeatCount(); i++) {
            expandedValues.addAll(values);
        }

        return new ListMultiplicationMatch(
                OBJECT_MAPPER.writeValueAsString(expandedValues),
                repeatCountResolution.endExclusive()
        );
    }

    private static int findMatchingSquareBracket(String json, int startIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = startIndex; index < json.length(); index++) {
            char current = json.charAt(index);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '[') {
                depth++;
            }
            else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }

        return -1;
    }

    private static int skipWhitespace(String json, int startIndex) {
        int index = startIndex;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        return index;
    }

    private static RepeatCountResolution resolveRepeatCount(String json, int repeatTokenStart, int expressionStartIndex) {
        char first = json.charAt(repeatTokenStart);
        if (Character.isDigit(first)) {
            int endIndex = repeatTokenStart + 1;
            while (endIndex < json.length() && Character.isDigit(json.charAt(endIndex))) {
                endIndex++;
            }
            return new RepeatCountResolution(
                    Integer.parseInt(json.substring(repeatTokenStart, endIndex)),
                    endIndex
            );
        }

        if (Character.isLetter(first) || first == '_') {
            int endIndex = repeatTokenStart + 1;
            while (endIndex < json.length()) {
                char current = json.charAt(endIndex);
                if (!Character.isLetterOrDigit(current) && current != '_') {
                    break;
                }
                endIndex++;
            }

            String identifier = json.substring(repeatTokenStart, endIndex);
            Integer resolvedValue = resolveIntegerBinding(json.substring(0, expressionStartIndex), identifier);
            if (resolvedValue == null) {
                return null;
            }
            return new RepeatCountResolution(resolvedValue, endIndex);
        }

        return null;
    }

    private static Integer resolveIntegerBinding(String prefix, String identifier) {
        Pattern bindingPattern = Pattern.compile("\"" + Pattern.quote(identifier) + "\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = bindingPattern.matcher(prefix);
        Integer resolvedValue = null;
        while (matcher.find()) {
            resolvedValue = Integer.parseInt(matcher.group(1));
        }
        return resolvedValue;
    }

    private static boolean isStandaloneToken(String text, int tokenStart, int tokenEndExclusive) {
        return !isIdentifierCharAt(text, tokenStart - 1) && !isIdentifierCharAt(text, tokenEndExclusive);
    }

    private static boolean isIdentifierCharAt(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return false;
        }
        char current = text.charAt(index);
        return Character.isLetterOrDigit(current) || current == '_';
    }

    private record ListMultiplicationMatch(String expandedJson, int endExclusive) {
    }

    private record RepeatCountResolution(int repeatCount, int endExclusive) {
    }
}
