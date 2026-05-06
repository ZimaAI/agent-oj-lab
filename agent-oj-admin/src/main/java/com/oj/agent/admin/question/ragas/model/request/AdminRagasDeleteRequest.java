package com.oj.agent.admin.question.ragas.model.request;

import lombok.Data;

import java.util.List;

@Data
public class AdminRagasDeleteRequest {

    private List<Long> ids;

    private List<Long> ragasIds;

    private List<Long> segmentIds;
}
