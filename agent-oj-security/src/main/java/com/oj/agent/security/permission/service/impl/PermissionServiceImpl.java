package com.oj.agent.security.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.security.exception.AuthorizationException;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.permission.mapper.PermissionMapper;
import com.oj.agent.security.permission.model.command.CreatePermissionCommand;
import com.oj.agent.security.permission.model.command.DeletePermissionCommand;
import com.oj.agent.security.permission.model.command.UpdatePermissionCommand;
import com.oj.agent.security.permission.model.entity.Permission;
import com.oj.agent.security.permission.model.query.PermissionGetQuery;
import com.oj.agent.security.permission.model.query.PermissionTreeQuery;
import com.oj.agent.security.permission.model.result.PermissionResult;
import com.oj.agent.security.permission.service.PermissionService;
import com.oj.agent.security.role.model.query.RolePermissionUsageQuery;
import com.oj.agent.security.role.model.result.RolePermissionUsageResult;
import com.oj.agent.security.role.service.RolePermissionReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionReferenceService rolePermissionReferenceService;

    @Override
    public PermissionResult createPermission(CreatePermissionCommand command) {
        Permission existingPermission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermissionCode, command.getPermissionCode()));
        if (existingPermission != null) {
            throw new AuthorizationException(
                    SecurityErrorCode.AUTHZ_003.getCode(),
                    SecurityErrorCode.AUTHZ_003.getMessage()
            );
        }

        Permission permission = new Permission();
        permission.setPermissionCode(command.getPermissionCode());
        permission.setPermissionName(command.getPermissionName());
        permission.setPermissionType(command.getPermissionType());
        permission.setParentId(command.getParentId() != null ? command.getParentId() : 0L);
        permission.setDescription(command.getDescription());
        permissionMapper.insert(permission);
        return toResult(permission);
    }

    @Override
    public PermissionResult updatePermission(UpdatePermissionCommand command) {
        Permission permission = getRequiredPermission(command.getPermissionId());
        if (command.getPermissionCode() != null && !command.getPermissionCode().equals(permission.getPermissionCode())) {
            Permission existingPermission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                    .eq(Permission::getPermissionCode, command.getPermissionCode()));
            if (existingPermission != null) {
                throw new AuthorizationException(
                        SecurityErrorCode.AUTHZ_003.getCode(),
                        SecurityErrorCode.AUTHZ_003.getMessage()
                );
            }
            permission.setPermissionCode(command.getPermissionCode());
        }
        if (command.getPermissionName() != null) {
            permission.setPermissionName(command.getPermissionName());
        }
        if (command.getDescription() != null) {
            permission.setDescription(command.getDescription());
        }
        permissionMapper.updateById(permission);
        return toResult(permission);
    }

    @Override
    public List<PermissionResult> getPermissionTree(PermissionTreeQuery query) {
        List<Permission> allPermissions = permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getPermissionType)
                .orderByAsc(Permission::getId));
        return buildTree(allPermissions, 0L);
    }

    @Override
    public PermissionResult getPermissionById(PermissionGetQuery query) {
        return toResult(getRequiredPermission(query.getPermissionId()));
    }

    @Override
    public void deletePermission(DeletePermissionCommand command) {
        Permission permission = getRequiredPermission(command.getPermissionId());
        RolePermissionUsageQuery usageQuery = new RolePermissionUsageQuery();
        usageQuery.setPermissionId(command.getPermissionId());
        RolePermissionUsageResult usage = rolePermissionReferenceService.getPermissionUsage(usageQuery);
        if (usage.getRoleCount() != null && usage.getRoleCount() > 0) {
            throw new AuthorizationException(
                    SecurityErrorCode.AUTHZ_005.getCode(),
                    SecurityErrorCode.AUTHZ_005.getMessage()
            );
        }
        permissionMapper.deleteById(permission.getId());
    }

    private Permission getRequiredPermission(Long permissionId) {
        Permission permission = permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw new AuthorizationException(
                    SecurityErrorCode.AUTHZ_001.getCode(),
                    "权限不存在"
            );
        }
        return permission;
    }

    private List<PermissionResult> buildTree(List<Permission> allPermissions, Long parentId) {
        List<PermissionResult> tree = new ArrayList<>();
        for (Permission permission : allPermissions) {
            if (permission.getParentId().equals(parentId)) {
                PermissionResult node = toResult(permission);
                node.setChildren(buildTree(allPermissions, permission.getId()));
                tree.add(node);
            }
        }
        return tree;
    }

    private PermissionResult toResult(Permission permission) {
        PermissionResult result = new PermissionResult();
        result.setId(permission.getId());
        result.setPermissionCode(permission.getPermissionCode());
        result.setPermissionName(permission.getPermissionName());
        result.setPermissionType(permission.getPermissionType());
        result.setParentId(permission.getParentId());
        result.setDescription(permission.getDescription());
        result.setCreateTime(permission.getCreateTime());
        return result;
    }
}
