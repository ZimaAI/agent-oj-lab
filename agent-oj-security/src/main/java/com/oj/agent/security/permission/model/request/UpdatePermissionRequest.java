package com.oj.agent.security.permission.model.request;

import lombok.Data;

@Data
public class UpdatePermissionRequest {

    private String permissionCode;

    private String permissionName;

    private String description;
}
