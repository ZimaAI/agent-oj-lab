package com.oj.agent.core.workflow.model.result;

import java.util.List;

public record AnswerRagasGraphResult(List<AnswerRagSegmentResult> segments,
                                     List<String> rewrittenQuestions,
                                     String assistantAnswer) {

    public AnswerRagasGraphResult {
        segments = segments == null ? List.of() : List.copyOf(segments);
        rewrittenQuestions = rewrittenQuestions == null ? List.of() : List.copyOf(rewrittenQuestions);
        assistantAnswer = assistantAnswer == null ? "" : assistantAnswer;
    }
}
