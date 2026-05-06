package com.oj.agent.security.role.model.request;

import lombok.Data;

@Data
public class UpdateRoleRequest {

    private String roleCode;

    private String roleName;

    private String description;
}
