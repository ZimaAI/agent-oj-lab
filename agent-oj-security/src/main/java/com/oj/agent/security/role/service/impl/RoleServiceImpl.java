package com.oj.agent.security.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.security.exception.AuthorizationException;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.permission.model.query.PermissionIdsQuery;
import com.oj.agent.security.permission.model.result.PermissionSummaryResult;
import com.oj.agent.security.permission.service.PermissionLookupService;
import com.oj.agent.security.role.mapper.RoleMapper;
import com.oj.agent.security.role.mapper.RolePermissionMapper;
import com.oj.agent.security.role.model.command.BindRolePermissionsCommand;
import com.oj.agent.security.role.model.command.CreateRoleCommand;
import com.oj.agent.security.role.model.command.DeleteRoleCommand;
import com.oj.agent.security.role.model.command.UpdateRoleCommand;
import com.oj.agent.security.role.model.entity.Role;
import com.oj.agent.security.role.model.entity.RolePermission;
import com.oj.agent.security.role.model.query.RoleGetQuery;
import com.oj.agent.security.role.model.query.RolePageQuery;
import com.oj.agent.security.role.model.result.RolePageResult;
import com.oj.agent.security.role.model.result.RoleResult;
import com.oj.agent.security.role.service.RoleService;
import com.oj.agent.security.user.model.query.UserRoleUsageQuery;
import com.oj.agent.security.user.model.result.UserRoleUsageResult;
import com.oj.agent.security.user.service.UserRoleReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionLookupService permissionLookupService;
    private final UserRoleReferenceService userRoleReferenceService;

    @Override
    public RoleResult createRole(CreateRoleCommand command) {
        Role existingRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, command.getRoleCode()));
        if (existingRole != null) {
            throw new AuthorizationException(
                    SecurityErrorCode.AUTHZ_002.getCode(),
                    SecurityErrorCode.AUTHZ_002.getMessage()
            );
        }

        Role role = new Role();
        role.setRoleCode(command.getRoleCode());
        role.setRoleName(command.getRoleName());
        role.setDescription(command.getDescription());
        role.setIsDefault(0);
        roleMapper.insert(role);

        RoleGetQuery query = new RoleGetQuery();
        query.setRoleId(role.getId());
        return getRoleById(query);
    }

    @Override
    public RoleResult updateRole(UpdateRoleCommand command) {
        Role role = getRequiredRole(command.getRoleId());
        if (command.getRoleCode() != null && !command.getRoleCode().equals(role.getRoleCode())) {
            Role existingRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                    .eq(Role::getRoleCode, command.getRoleCode()));
            if (existingRole != null) {
                throw new AuthorizationException(
                        SecurityErrorCode.AUTHZ_002.getCode(),
                        SecurityErrorCode.AUTHZ_002.getMessage()
                );
            }
            role.setRoleCode(command.getRoleCode());
        }
        if (command.getRoleName() != null) {
            role.setRoleName(command.getRoleName());
        }
        if (command.getDescription() != null) {
            role.setDescription(command.getDescription());
        }
        roleMapper.updateById(role);

        RoleGetQuery query = new RoleGetQuery();
        query.setRoleId(role.getId());
        return getRoleById(query);
    }

    @Override
    public RolePageResult listRoles(RolePageQuery query) {
        long current = query == null || query.getCurrent() == null || query.getCurrent() <= 0 ? 1L : query.getCurrent();
        long size = query == null || query.getSize() == null || query.getSize() <= 0 ? 10L : query.getSize();
        Page<Role> page = new Page<>(current, size);
        Page<Role> rolePage = roleMapper.selectPage(page, new LambdaQueryWrapper<Role>()
                .orderByDesc(Role::getCreateTime));
        if (rolePage == null) {
            rolePage = page;
        }

        RolePageResult result = new RolePageResult();
        result.setCurrent(rolePage.getCurrent());
        result.setSize(rolePage.getSize());
        result.setTotal(rolePage.getTotal());
        result.setPages(rolePage.getPages());
        result.setRecords(rolePage.getRecords().stream()
                .map(role -> toResult(role, getRolePermissions(role.getId())))
                .toList());
        return result;
    }

    @Override
    public RoleResult getRoleById(RoleGetQuery query) {
        Role role = getRequiredRole(query.getRoleId());
        return toResult(role, getRolePermissions(role.getId()));
    }

    @Override
    public void deleteRole(DeleteRoleCommand command) {
        Role role = getRequiredRole(command.getRoleId());
        UserRoleUsageQuery usageQuery = new UserRoleUsageQuery();
        usageQuery.setRoleId(role.getId());
        UserRoleUsageResult usage = userRoleReferenceService.getRoleUsage(usageQuery);
        if (usage.getUserCount() != null && usage.getUserCount() > 0) {
            throw new AuthorizationException(
                    SecurityErrorCode.AUTHZ_004.getCode(),
                    SecurityErrorCode.AUTHZ_004.getMessage()
            );
        }
        roleMapper.deleteById(role.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindPermissions(BindRolePermissionsCommand command) {
        Role role = getRequiredRole(command.getRoleId());
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, role.getId()));
        if (command.getPermissionIds() == null || command.getPermissionIds().isEmpty()) {
            return;
        }
        for (Long permissionId : command.getPermissionIds()) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(role.getId());
            rolePermission.setPermissionId(permissionId);
            rolePermissionMapper.insert(rolePermission);
        }
    }

    private Role getRequiredRole(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new AuthorizationException(
                    SecurityErrorCode.AUTHZ_001.getCode(),
                    "角色不存在"
            );
        }
        return role;
    }

    private List<RoleResult.PermissionInfoResult> getRolePermissions(Long roleId) {
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        if (rolePermissions == null || rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        PermissionIdsQuery query = new PermissionIdsQuery();
        query.setPermissionIds(rolePermissions.stream()
                .map(RolePermission::getPermissionId)
                .toList());
        List<PermissionSummaryResult> permissions = permissionLookupService.listPermissionSummaries(query);
        return permissions.stream()
                .map(permission -> new RoleResult.PermissionInfoResult(
                        permission.getPermissionId(),
                        permission.getPermissionCode(),
                        permission.getPermissionName()
                ))
                .toList();
    }

    private RoleResult toResult(Role role, List<RoleResult.PermissionInfoResult> permissions) {
        RoleResult result = new RoleResult();
        result.setId(role.getId());
        result.setRoleCode(role.getRoleCode());
        result.setRoleName(role.getRoleName());
        result.setDescription(role.getDescription());
        result.setIsDefault(role.getIsDefault());
        result.setPermissions(permissions);
        result.setCreateTime(role.getCreateTime());
        return result;
    }
}
