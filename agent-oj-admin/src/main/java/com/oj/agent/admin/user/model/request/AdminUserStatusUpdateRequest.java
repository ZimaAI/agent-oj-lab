package com.oj.agent.admin.user.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminUserStatusUpdateRequest {

    @NotNull(message = "用户状态不能为空")
    @Pattern(regexp = "DISABLED", message = "用户状态只支持 DISABLED")
    private String status;
}
