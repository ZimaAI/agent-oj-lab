package com.oj.agent.security.permission.model.request;

import lombok.Data;

@Data
public class CreatePermissionRequest {

    private String permissionCode;

    private String permissionName;

    private Integer permissionType;

    private Long parentId;

    private String description;
}
