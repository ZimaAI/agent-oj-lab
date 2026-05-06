package com.oj.agent.admin.question.hitk.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminHitKTaskBatchDeleteResult {

    private Integer successCount;

    private Integer failureCount;

    private List<ItemResult> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResult {

        private Long taskId;

        private Boolean success;

        private String message;
    }
}
