package com.oj.agent.admin.question.ragas.model.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRagasListQuery {

    private Long questionId;

    private Long docId;

    private List<Long> segmentIds;

    private List<Long> ragasIds;

    private List<Long> ids;
}
