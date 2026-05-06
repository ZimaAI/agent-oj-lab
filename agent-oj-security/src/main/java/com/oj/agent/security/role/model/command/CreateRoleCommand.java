package com.oj.agent.security.role.model.command;

import lombok.Data;

@Data
public class CreateRoleCommand {

    private String roleCode;

    private String roleName;

    private String description;
}
