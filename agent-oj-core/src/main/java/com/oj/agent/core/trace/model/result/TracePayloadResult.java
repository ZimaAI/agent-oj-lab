package com.oj.agent.core.trace.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TracePayloadResult {

    private String kind;

    private Object data;
}
