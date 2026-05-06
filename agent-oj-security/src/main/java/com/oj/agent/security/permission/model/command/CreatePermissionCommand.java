package com.oj.agent.security.permission.model.command;

import lombok.Data;

@Data
public class CreatePermissionCommand {

    private String permissionCode;

    private String permissionName;

    private Integer permissionType;

    private Long parentId;

    private String description;
}
