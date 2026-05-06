package com.oj.agent.admin.question.hitk.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminHitKQuestionBatchResponse {

    private Integer successCount;

    private Integer failureCount;

    private List<ItemResponse> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResponse {

        private Long segmentId;

        private Boolean success;

        private String message;

        @JsonProperty("hitkQuestion")
        private String hitKQuestion;
    }
}
