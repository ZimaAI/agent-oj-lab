package com.oj.agent.core.executor.tool.model;

import java.util.List;
import java.util.Map;

public class CodeExecutionRequest {

    private static final String ERROR_CODE_EMPTY = "Code cannot be null or empty";
    private static final String ERROR_FUNCTION_NAME_EMPTY = "Function name cannot be null or empty";
    private static final String ERROR_TEST_INPUTS_NULL = "Test inputs cannot be null";

    private String language;
    private String code;
    private String functionName;
    private List<Map<String, Object>> testInputs;
    private List<Object> expectedOutputs;
    private Boolean ignoreCollectionOrder;

    public CodeExecutionRequest() {
    }

    public CodeExecutionRequest(String language, String code, String functionName, List<Map<String, Object>> testInputs) {
        this.language = language;
        this.code = code;
        this.functionName = functionName;
        this.testInputs = testInputs;
    }

    public CodeExecutionRequest(String language,
                                String code,
                                String functionName,
                                List<Map<String, Object>> testInputs,
                                List<Object> expectedOutputs) {
        this.language = language;
        this.code = code;
        this.functionName = functionName;
        this.testInputs = testInputs;
        this.expectedOutputs = expectedOutputs;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException(ERROR_CODE_EMPTY);
        }
        this.code = code;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        if (functionName == null || functionName.trim().isEmpty()) {
            throw new IllegalArgumentException(ERROR_FUNCTION_NAME_EMPTY);
        }
        this.functionName = functionName;
    }

    public List<Map<String, Object>> getTestInputs() {
        return testInputs;
    }

    public void setTestInputs(List<Map<String, Object>> testInputs) {
        if (testInputs == null) {
            throw new IllegalArgumentException(ERROR_TEST_INPUTS_NULL);
        }
        this.testInputs = testInputs;
    }

    public List<Object> getExpectedOutputs() {
        return expectedOutputs;
    }

    public void setExpectedOutputs(List<Object> expectedOutputs) {
        this.expectedOutputs = expectedOutputs;
    }

    public Boolean getIgnoreCollectionOrder() {
        return ignoreCollectionOrder;
    }

    public void setIgnoreCollectionOrder(Boolean ignoreCollectionOrder) {
        this.ignoreCollectionOrder = ignoreCollectionOrder;
    }
}
