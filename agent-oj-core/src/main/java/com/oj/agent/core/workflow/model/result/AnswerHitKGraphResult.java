package com.oj.agent.core.workflow.model.result;

import java.util.List;

public record AnswerHitKGraphResult(List<AnswerRagSegmentResult> segments, List<String> rewrittenQuestions) {

    public AnswerHitKGraphResult {
        segments = segments == null ? List.of() : List.copyOf(segments);
        rewrittenQuestions = rewrittenQuestions == null ? List.of() : List.copyOf(rewrittenQuestions);
    }

    public AnswerHitKGraphResult(List<AnswerRagSegmentResult> segments) {
        this(segments, List.of());
    }
}
