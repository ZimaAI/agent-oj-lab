package com.oj.agent.core.trace.model.command;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TraceCompleteCommand {

    private String traceId;

    private String status;

    private String errorMessage;
}
