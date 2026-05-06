package com.oj.agent.admin.question.hitk.model.response;

import lombok.Data;

import java.math.BigDecimal;


@Data
public class AdminHitKTaskStatisticsResponse {

    private Integer taskCount;

    private BigDecimal averageHitRate;

    private BigDecimal averageTotalCount;

    private BigDecimal averageHitCount;

    private BigDecimal averageMissCount;

    private Long sumTotalCount;

    private Long sumHitCount;

    private Long sumMissCount;

    private BigDecimal overallHitRate;
}
