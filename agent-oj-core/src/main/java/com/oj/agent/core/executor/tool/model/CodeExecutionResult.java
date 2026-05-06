package com.oj.agent.core.executor.tool.model;

import java.util.List;

public class CodeExecutionResult {

    private boolean success;
    private List<TestResult> results;
    private String errorMessage;

    public CodeExecutionResult() {
    }

    public CodeExecutionResult(boolean success, List<TestResult> results, String errorMessage) {
        this.success = success;
        this.results = results;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<TestResult> getResults() {
        return results;
    }

    public void setResults(List<TestResult> results) {
        this.results = results;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
