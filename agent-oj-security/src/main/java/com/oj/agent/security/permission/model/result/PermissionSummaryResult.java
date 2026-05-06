package com.oj.agent.security.permission.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionSummaryResult {

    private Long permissionId;

    private String permissionCode;

    private String permissionName;
}
