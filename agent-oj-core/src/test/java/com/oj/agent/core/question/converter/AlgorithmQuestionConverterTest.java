package com.oj.agent.core.question.converter;

import com.oj.agent.core.question.model.entity.AlgorithmQuestion;
import com.oj.agent.core.question.model.response.AlgorithmQuestionResponse;
import com.oj.agent.core.question.model.result.AlgorithmQuestionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlgorithmQuestionConverterTest {

    @Test
    void detailResultShouldExposeNormalizedStandardCasePool() {
        AlgorithmQuestion question = new AlgorithmQuestion();
        question.setId(11L);
        question.setTitle("sum");
        question.setDescription("stdin/stdout sum");
        question.setDifficulty("SIMPLE");
        question.setType("SYSTEM");
        question.setStandardCasePool("""
                [
                  {"stdin":"9 9\\n","expectedStdout":"18\\n","publicCase":false,"description":"hidden"},
                  {"stdin":"1 2\\n","expectedStdout":"3\\n","publicCase":true,"description":"public"}
                ]
                """);

        AlgorithmQuestionResult result = AlgorithmQuestionConverter.toDetailQuestionResult(question, List.of(), List.of());
        AlgorithmQuestionResponse response = AlgorithmQuestionConverter.toAlgorithmQuestionResponse(result);

        assertThat(result.getStandardCasePool())
                .extracting(item -> item.getDescription())
                .containsExactly("public", "hidden");
        assertThat(response.getStandardCasePool())
                .extracting(item -> item.getDescription())
                .containsExactly("public", "hidden");
    }
}
