package com.oj.agent.security.permission.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {

    private Long id;

    private String permissionCode;

    private String permissionName;

    private Integer permissionType;

    private Long parentId;

    private String description;

    private List<PermissionResponse> children;

    private LocalDateTime createTime;
}
