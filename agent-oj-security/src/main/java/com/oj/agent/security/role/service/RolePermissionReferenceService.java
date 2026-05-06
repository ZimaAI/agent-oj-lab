package com.oj.agent.security.role.service;

import com.oj.agent.security.role.model.query.RolePermissionUsageQuery;
import com.oj.agent.security.role.model.result.RolePermissionUsageResult;

public interface RolePermissionReferenceService {

    RolePermissionUsageResult getPermissionUsage(RolePermissionUsageQuery query);
}
