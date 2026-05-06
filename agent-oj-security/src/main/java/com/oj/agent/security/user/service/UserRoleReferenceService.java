package com.oj.agent.security.user.service;

import com.oj.agent.security.user.model.query.UserRoleUsageQuery;
import com.oj.agent.security.user.model.result.UserRoleUsageResult;

public interface UserRoleReferenceService {

    UserRoleUsageResult getRoleUsage(UserRoleUsageQuery query);
}
