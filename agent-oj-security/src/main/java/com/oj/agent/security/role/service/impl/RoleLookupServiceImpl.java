package com.oj.agent.security.role.service.impl;

import com.oj.agent.security.role.mapper.RoleMapper;
import com.oj.agent.security.role.model.entity.Role;
import com.oj.agent.security.role.model.query.RoleIdsQuery;
import com.oj.agent.security.role.model.result.RoleSummaryResult;
import com.oj.agent.security.role.service.RoleLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleLookupServiceImpl implements RoleLookupService {

    private final RoleMapper roleMapper;

    @Override
    public List<RoleSummaryResult> listRoleSummaries(RoleIdsQuery query) {
        if (query == null || query.getRoleIds() == null || query.getRoleIds().isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Role> roleById = roleMapper.selectBatchIds(query.getRoleIds()).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        return query.getRoleIds().stream()
                .map(roleById::get)
                .filter(role -> role != null)
                .map(role -> new RoleSummaryResult(role.getId(), role.getRoleName(), role.getRoleCode()))
                .toList();
    }
}
