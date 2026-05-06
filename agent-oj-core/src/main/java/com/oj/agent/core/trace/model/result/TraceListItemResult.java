package com.oj.agent.core.trace.model.result;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TraceListItemResult {

    private Long id;

    private String traceId;

    private String conversationId;

    private Long userId;

    private String requestMessage;

    private Long currentAlgorithmQuestionId;

    private String status;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
