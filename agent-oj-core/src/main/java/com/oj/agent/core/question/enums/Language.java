package com.oj.agent.core.question.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

public enum Language {

    PYTHON("python", "py", "python", "py"),
    JAVASCRIPT("javascript", "js", "javascript", "js"),
    JAVA("java", "java", "java");

    private final String name;

    private final String extension;

    private final Set<String> aliases;

    Language(String name, String extension, String... aliases) {
        this.name = name;
        this.extension = extension;
        this.aliases = Arrays.stream(aliases)
                .map(Language::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return extension;
    }

    public static Language fromName(String name) {
        String normalized = normalize(name);
        for (Language lang : values()) {
            if (lang.aliases.contains(normalized)
                    || normalize(lang.name()).equals(normalized)
                    || normalize(lang.extension).equals(normalized)) {
                return lang;
            }
        }
        throw new IllegalArgumentException("Unsupported language: " + name);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
