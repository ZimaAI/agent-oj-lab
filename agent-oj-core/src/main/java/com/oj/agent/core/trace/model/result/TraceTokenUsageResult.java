package com.oj.agent.core.trace.model.result;

public record TraceTokenUsageResult(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
