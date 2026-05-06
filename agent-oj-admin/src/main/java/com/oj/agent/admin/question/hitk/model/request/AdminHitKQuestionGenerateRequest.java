package com.oj.agent.admin.question.hitk.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AdminHitKQuestionGenerateRequest {

    @NotEmpty(message = "segmentIds must not be empty")
    private List<Long> segmentIds;
}
