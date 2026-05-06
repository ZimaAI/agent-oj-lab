package com.oj.agent.admin.question.ragas.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AdminRagasGenerateRequest {

    @NotEmpty(message = "segmentIds must not be empty")
    private List<Long> segmentIds;
}
