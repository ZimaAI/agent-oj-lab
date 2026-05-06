package com.oj.agent.security.permission.model.command;

import lombok.Data;

@Data
public class UpdatePermissionCommand {

    private Long permissionId;

    private String permissionCode;

    private String permissionName;

    private String description;
}
