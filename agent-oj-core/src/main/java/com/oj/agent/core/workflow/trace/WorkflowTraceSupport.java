package com.oj.agent.core.workflow.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.core.trace.model.result.TracePayloadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowTraceSupport {

    private final ObjectMapper objectMapper;

    public String toLlmInputPayload(Object data) {
        return toPayload("llm_input", data);
    }

    public String toLlmOutputPayload(Object data) {
        return toPayload("llm_output", data);
    }

    public String toNodeInputPayload(Object data) {
        return toPayload("node_input", data);
    }

    public String toNodeOutputPayload(Object data) {
        return toPayload("node_output", data);
    }

    public String toToolInputPayload(Object data) {
        return toPayload("tool_input", data);
    }

    public String toToolOutputPayload(Object data) {
        return toPayload("tool_output", data);
    }

    private String toPayload(String kind, Object data) {
        try {
            return objectMapper.writeValueAsString(new TracePayloadResult(kind, data));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize trace payload", e);
        }
    }
}
