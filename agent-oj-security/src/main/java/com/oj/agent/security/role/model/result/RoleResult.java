package com.oj.agent.security.role.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleResult {

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    private Integer isDefault;

    private List<PermissionInfoResult> permissions;

    private LocalDateTime createTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionInfoResult {
        private Long permissionId;
        private String permissionCode;
        private String permissionName;
    }
}
