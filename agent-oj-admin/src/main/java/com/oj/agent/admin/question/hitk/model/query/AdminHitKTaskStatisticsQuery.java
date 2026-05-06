package com.oj.agent.admin.question.hitk.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminHitKTaskStatisticsQuery {

    private List<Long> taskIds;
}
