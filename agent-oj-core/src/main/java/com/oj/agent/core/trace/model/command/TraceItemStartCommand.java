package com.oj.agent.core.trace.model.command;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TraceItemStartCommand {

    private String traceId;

    private String nodeName;

    private String itemType;

    private String itemKey;

    private Integer roundNo;

    private String toolName;

    private String inputPayload;

    private String inputSummary;

    private Long startTimestamp;
}
