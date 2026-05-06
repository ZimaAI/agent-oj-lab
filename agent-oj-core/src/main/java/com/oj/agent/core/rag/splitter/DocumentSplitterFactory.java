package com.oj.agent.core.rag.splitter;

import com.oj.agent.core.rag.enums.DocumentSplitType;
import com.oj.agent.core.rag.model.dto.DocumentSplitParam;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;


/**
 * @Author Hollis
 */
public class DocumentSplitterFactory {

    private static final String DEFAULT_SPLIT_TYPE = DocumentSplitType.SMART.name();
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_OVERLAP = 100;
    private static final int DEFAULT_TITLE_LEVEL = 3;
    private static final String DEFAULT_SEPARATOR = "\\n\\n";
    private static final String DEFAULT_REGEX = "(?m)^#{1,6}\\s";

    public static DocumentSplitter getInstance(DocumentSplitParam documentSplitParam) {
        // 统一兜底分段参数，避免空值导致运行时异常。
        DocumentSplitParam safeParam = normalizeParam(documentSplitParam);
        String splitType = normalizeSplitType(safeParam.splitType());

        // 统一收敛标题层级范围，避免越界配置。
        int titleLevel = clamp(safeParam.titleLevel(), 1, 6);

        if (DocumentSplitType.TITLE.name().equals(splitType)) {
            return new MarkdownHeaderParentTextSplitter(
                    buildHeadersToSplitOn(titleLevel),
                    true,
                    false,
                    safeParam.chunkSize(),
                    safeParam.overlap()
            );
        }

        if (DocumentSplitType.LENGTH.name().equals(splitType)) {
            return new DocumentByWordSplitter(safeParam.chunkSize(), safeParam.overlap());
        }

        if (DocumentSplitType.SEPARATOR.name().equals(splitType)) {
            return new DocumentByRegexSplitter(
                    safeParam.separator(),
                    DEFAULT_SEPARATOR,
                    safeParam.chunkSize(),
                    safeParam.overlap()
            );
        }

        if (DocumentSplitType.REGEX.name().equals(splitType)) {
            return new DocumentByRegexSplitter(
                    safeParam.regex(),
                    DEFAULT_SEPARATOR,
                    safeParam.chunkSize(),
                    safeParam.overlap()
            );
        }

        // SMART 或未知类型默认走标题分段。
        return new MarkdownHeaderParentTextSplitter(
                buildHeadersToSplitOn(titleLevel),
                true,
                false,
                safeParam.chunkSize(),
                safeParam.overlap()
        );
    }

    private static DocumentSplitParam normalizeParam(DocumentSplitParam source) {
        // 统一回填默认配置，确保所有参数都可用。
        if (source == null) {
            return new DocumentSplitParam(
                    DEFAULT_SPLIT_TYPE,
                    DEFAULT_CHUNK_SIZE,
                    DEFAULT_OVERLAP,
                    DEFAULT_TITLE_LEVEL,
                    DEFAULT_SEPARATOR,
                    DEFAULT_REGEX
            );
        }
        return new DocumentSplitParam(
                normalizeSplitType(source.splitType()),
                source.chunkSize() == null || source.chunkSize() <= 0 ? DEFAULT_CHUNK_SIZE : source.chunkSize(),
                source.overlap() == null || source.overlap() < 0 ? DEFAULT_OVERLAP : source.overlap(),
                source.titleLevel() == null ? DEFAULT_TITLE_LEVEL : source.titleLevel(),
                isBlank(source.separator()) ? DEFAULT_SEPARATOR : source.separator(),
                isBlank(source.regex()) ? DEFAULT_REGEX : source.regex()
        );
    }

    private static String normalizeSplitType(String splitType) {
        // 未命中枚举时回退到 SMART，防止工厂返回空实现。
        if (isBlank(splitType)) {
            return DEFAULT_SPLIT_TYPE;
        }
        return IntStream.range(0, DocumentSplitType.values().length)
                .mapToObj(index -> DocumentSplitType.values()[index])
                .map(Enum::name)
                .filter(name -> name.equalsIgnoreCase(splitType))
                .findFirst()
                .orElse(DEFAULT_SPLIT_TYPE);
    }

    private static Map<String, String> buildHeadersToSplitOn(int titleLevel) {
        // 按配置层级生成标题切分映射。
        int safeLevel = clamp(titleLevel, 1, 6);
        Map<String, String> headers = new LinkedHashMap<>(safeLevel);
        for (int level = 1; level <= safeLevel; level++) {
            headers.put("#".repeat(level), "title" + level);
        }
        return headers;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
