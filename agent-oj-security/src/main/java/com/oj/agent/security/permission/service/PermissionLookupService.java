package com.oj.agent.security.permission.service;

import com.oj.agent.security.permission.model.query.PermissionIdsQuery;
import com.oj.agent.security.permission.model.result.PermissionSummaryResult;

import java.util.List;

public interface PermissionLookupService {

    List<PermissionSummaryResult> listPermissionSummaries(PermissionIdsQuery query);
}
