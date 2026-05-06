package com.oj.agent.admin.question.hitk.model.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminHitKTaskRemarkUpdateRequest {

    @Size(max = 1024, message = "remark length must be less than or equal to 1024")
    private String remark;
}
