package com.oj.agent.security.role.model.command;

import lombok.Data;

@Data
public class UpdateRoleCommand {

    private Long roleId;

    private String roleCode;

    private String roleName;

    private String description;
}
