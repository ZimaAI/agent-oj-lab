package com.oj.agent.core.trace.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TraceDetailResponse {

    private Long id;

    private String traceId;

    private String conversationId;

    private Long userId;

    private String requestMessage;

    private Long currentAlgorithmQuestionId;

    private String currentAlgorithmQuestionSnapshot;

    private String status;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
