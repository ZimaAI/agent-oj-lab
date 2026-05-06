package com.oj.agent.security.role.model.command;

import lombok.Data;

import java.util.List;

@Data
public class BindRolePermissionsCommand {

    private Long roleId;

    private List<Long> permissionIds;
}
