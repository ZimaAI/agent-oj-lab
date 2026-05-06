package com.oj.agent.security.permission.service.impl;

import com.oj.agent.security.permission.mapper.PermissionMapper;
import com.oj.agent.security.permission.model.entity.Permission;
import com.oj.agent.security.permission.model.query.PermissionIdsQuery;
import com.oj.agent.security.permission.model.result.PermissionSummaryResult;
import com.oj.agent.security.permission.service.PermissionLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionLookupServiceImpl implements PermissionLookupService {

    private final PermissionMapper permissionMapper;

    @Override
    public List<PermissionSummaryResult> listPermissionSummaries(PermissionIdsQuery query) {
        if (query == null || query.getPermissionIds() == null || query.getPermissionIds().isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Permission> permissionById = permissionMapper.selectBatchIds(query.getPermissionIds()).stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
        return query.getPermissionIds().stream()
                .map(permissionById::get)
                .filter(permission -> permission != null)
                .map(permission -> new PermissionSummaryResult(
                        permission.getId(),
                        permission.getPermissionCode(),
                        permission.getPermissionName()
                ))
                .toList();
    }
}
