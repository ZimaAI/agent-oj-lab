package com.oj.agent.core.trace.model.request;

import lombok.Data;

@Data
public class TracePageRequest {

    private long pageNum = 1;

    private long pageSize = 10;

    private String traceId;

    private String conversationId;

    private Long userId;

    private String requestMessage;

    private String status;
}
