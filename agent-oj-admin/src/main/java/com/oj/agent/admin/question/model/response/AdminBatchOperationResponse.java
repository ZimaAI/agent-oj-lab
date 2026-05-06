package com.oj.agent.admin.question.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminBatchOperationResponse {

    private Integer successCount;

    private Integer failureCount;

    private List<ItemResult> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResult {
        private Long questionId;
        private Boolean success;
        private String message;
    }
}
