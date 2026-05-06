package com.oj.agent.core.trace.model.command;

import com.oj.agent.core.trace.model.result.TraceTokenUsageResult;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TraceItemFinishCommand {

    private Long traceItemId;

    private String outputPayload;

    private String outputSummary;

    private String errorMessage;

    private String status;

    private TraceTokenUsageResult tokenUsage;

    private Long endTimestamp;
}
