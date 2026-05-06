package com.oj.agent.security.role.model.request;

import lombok.Data;

import java.util.List;

@Data
public class BindRolePermissionsRequest {

    private List<Long> permissionIds;
}
