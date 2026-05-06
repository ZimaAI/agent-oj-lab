package com.oj.agent.security.role.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    private Integer isDefault;

    private List<PermissionInfo> permissions;

    private LocalDateTime createTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionInfo {
        private Long permissionId;
        private String permissionCode;
        private String permissionName;
    }
}
