package com.oj.agent.security.role.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleSummaryResult {

    private Long roleId;

    private String roleName;

    private String roleCode;
}
