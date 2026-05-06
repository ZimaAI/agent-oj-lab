package com.oj.agent.security.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.security.role.mapper.RolePermissionMapper;
import com.oj.agent.security.role.model.entity.RolePermission;
import com.oj.agent.security.role.model.query.RolePermissionUsageQuery;
import com.oj.agent.security.role.model.result.RolePermissionUsageResult;
import com.oj.agent.security.role.service.RolePermissionReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RolePermissionReferenceServiceImpl implements RolePermissionReferenceService {

    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public RolePermissionUsageResult getPermissionUsage(RolePermissionUsageQuery query) {
        RolePermissionUsageResult result = new RolePermissionUsageResult();
        Long permissionId = query == null ? null : query.getPermissionId();
        result.setPermissionId(permissionId);
        if (permissionId == null) {
            result.setRoleCount(0L);
            return result;
        }
        Long roleCount = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getPermissionId, permissionId));
        result.setRoleCount(roleCount == null ? 0L : roleCount);
        return result;
    }
}
