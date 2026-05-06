package com.oj.agent.core.executor.tool;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

public final class ExecutionComparisonPolicy {

    private static final Set<String> UNORDERED_KEYWORDS = Set.of(
            "任意顺序",
            "顺序不限",
            "顺序可任意",
            "in any order",
            "any order"
    );

    private ExecutionComparisonPolicy() {
    }

    public static boolean shouldIgnoreCollectionOrder(String questionDescription) {
        if (!StringUtils.hasText(questionDescription)) {
            return false;
        }
        String normalized = questionDescription.toLowerCase(Locale.ROOT);
        for (String keyword : UNORDERED_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
