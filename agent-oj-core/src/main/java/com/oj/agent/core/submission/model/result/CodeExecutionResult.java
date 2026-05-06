package com.oj.agent.core.submission.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class CodeExecutionResult {

    private Boolean success;

    private List<TestResultItem> results;

    private String errorMessage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestResultItem {

        private Boolean passed;

        private Object output;

        private String error;
    }
}
