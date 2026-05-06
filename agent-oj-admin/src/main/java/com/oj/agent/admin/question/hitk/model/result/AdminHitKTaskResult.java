package com.oj.agent.admin.question.hitk.model.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminHitKTaskResult {

    private Long taskId;

    private Long questionId;

    private Long documentId;

    private Integer totalCount;

    private Integer hitCount;

    private Integer missCount;

    private BigDecimal hitRate;

    private String status;

    private String errorMessage;

    private String remark;

    private LocalDateTime createTime;

    private List<DetailItemResult> details;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailItemResult {

        private Long segmentId;

        @JsonProperty("hitkQuestion")
        private String hitKQuestion;

        private List<Long> retrievedSegmentIds;

        private Boolean hit;
    }
}
