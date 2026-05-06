package com.oj.agent.core.question.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StandardCasePoolCodecTest {

    @Test
    void normalizeShouldPlacePublicCasesBeforeHiddenCasesWithStableOrder() {
        String rawCasePool = """
                [
                  {"stdin":"3\\n1 2\\n","expectedStdout":"3\\n","publicCase":false,"description":"hidden-1"},
                  {"stdin":"2\\n1 1\\n","expectedStdout":"2\\n","publicCase":true,"description":"public-1"},
                  {"stdin":"4\\n2 2\\n","expectedStdout":"4\\n","publicCase":false,"description":"hidden-2"},
                  {"stdin":"1\\n9\\n","expectedStdout":"9\\n","publicCase":true,"description":"public-2"}
                ]
                """;

        List<StandardCasePoolCodec.StandardCaseItem> normalized =
                StandardCasePoolCodec.parseAndNormalize(rawCasePool);

        assertThat(normalized).extracting(StandardCasePoolCodec.StandardCaseItem::getDescription)
                .containsExactly("public-1", "public-2", "hidden-1", "hidden-2");
        assertThat(normalized).extracting(StandardCasePoolCodec.StandardCaseItem::isPublicCase)
                .containsExactly(true, true, false, false);
    }
}
