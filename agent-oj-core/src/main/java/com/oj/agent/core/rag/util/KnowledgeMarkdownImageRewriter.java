package com.oj.agent.core.rag.util;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KnowledgeMarkdownImageRewriter {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[(.*?)]\\((.*?)\\)");

    private KnowledgeMarkdownImageRewriter() {
    }

    public static String rewrite(String markdown, Function<String, RewrittenImage> resolver) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        Objects.requireNonNull(resolver, "resolver must not be null");

        Matcher matcher = IMAGE_PATTERN.matcher(markdown);
        StringBuilder rewritten = new StringBuilder(markdown.length() + 64);
        while (matcher.find()) {
            String originalPath = matcher.group(2);
            RewrittenImage image = resolver.apply(originalPath);
            if (image == null || isBlank(image.description()) || isBlank(image.url())) {
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String replaced = "![" + image.description().trim() + "](" + image.url().trim() + ")";
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replaced));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    public record RewrittenImage(String description, String url) {
    }
}
