package com.oj.agent.core.submission.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class CodeExecutionResponse {

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
