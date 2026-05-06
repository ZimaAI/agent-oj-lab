package com.oj.agent.admin.question.hitk.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminHitKTaskBatchDeleteResponse {

    private Integer successCount;

    private Integer failureCount;

    private List<ItemResponse> results;

    /**
     * 批量删除单条结果。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResponse {

        private Long taskId;

        private Boolean success;

        private String message;
    }
}
