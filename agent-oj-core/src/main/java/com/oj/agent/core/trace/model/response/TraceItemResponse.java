package com.oj.agent.core.trace.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TraceItemResponse {

    private Long id;

    private String traceId;

    private String nodeName;

    private String itemType;

    private String itemKey;

    private Integer roundNo;

    private String toolName;

    private String inputPayload;

    private String outputPayload;

    private String inputSummary;

    private String outputSummary;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private String status;

    private String errorMessage;

    private Long startTimestamp;

    private Long endTimestamp;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
