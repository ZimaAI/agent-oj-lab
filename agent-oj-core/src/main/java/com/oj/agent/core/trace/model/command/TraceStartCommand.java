package com.oj.agent.core.trace.model.command;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TraceStartCommand {

    private String traceId;

    private String conversationId;

    private Long userId;

    private String requestMessage;

    private Long currentAlgorithmQuestionId;

    private String currentAlgorithmQuestionSnapshot;
}
