package com.oj.agent.security.role.service;

import com.oj.agent.security.role.model.query.RoleIdsQuery;
import com.oj.agent.security.role.model.result.RoleSummaryResult;

import java.util.List;

public interface RoleLookupService {

    List<RoleSummaryResult> listRoleSummaries(RoleIdsQuery query);
}
