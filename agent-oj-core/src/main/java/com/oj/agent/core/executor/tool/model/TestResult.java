package com.oj.agent.core.executor.tool.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class TestResult {

    @JsonPropertyDescription("是否通过")
    private boolean passed;

    @JsonPropertyDescription("实际输出")
    private Object output;

    @JsonPropertyDescription("错误信息")
    private String error;

    public TestResult() {
    }

    public TestResult(boolean passed, Object output, String error) {
        this.passed = passed;
        this.output = output;
        this.error = error;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public Object getOutput() {
        return output;
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
