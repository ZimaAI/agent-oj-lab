package com.oj.agent.core.executor.model;

import com.oj.agent.core.question.enums.Language;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ExecutionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String functionName;

    private Language language;

    private boolean success;

    private String result;

    private String errorMessage;

    private String stackTrace;

    private LocalDateTime executedAt;

    private long durationMs;

    private Map<String, Object> metadata;

    private List<ToolCallRecord> callTrace;

    private List<ToolCallRecord> replyToUserTrace;

    public ExecutionRecord() {
        this.executedAt = LocalDateTime.now();
        this.callTrace = new ArrayList<>();
        this.replyToUserTrace = new ArrayList<>();
    }

    public ExecutionRecord(String functionName, Language language) {
        this();
        this.functionName = functionName;
        this.language = language;
    }

    public void addToolCall(String toolName) {
        if (this.callTrace == null) {
            this.callTrace = new ArrayList<>();
        }
        this.callTrace.add(new ToolCallRecord(this.callTrace.size() + 1, toolName));
    }

    @Override
    public String toString() {
        return "ExecutionRecord{" +
                "functionName='" + functionName + '\'' +
                ", language=" + language +
                ", success=" + success +
                ", result='" + result + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", executedAt=" + executedAt +
                ", durationMs=" + durationMs +
                ", callTrace=" + callTrace +
                ", replyToUserTrace=" + replyToUserTrace +
                '}';
    }
}
