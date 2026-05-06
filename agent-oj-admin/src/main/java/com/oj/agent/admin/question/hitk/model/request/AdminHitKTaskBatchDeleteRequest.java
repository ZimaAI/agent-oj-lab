package com.oj.agent.admin.question.hitk.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class AdminHitKTaskBatchDeleteRequest {

    @NotEmpty(message = "taskIds must not be empty")
    private List<@NotNull(message = "taskId must not be null") @Positive(message = "taskId must be positive") Long> taskIds;
}
