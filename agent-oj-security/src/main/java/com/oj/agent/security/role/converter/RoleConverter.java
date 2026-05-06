package com.oj.agent.security.role.converter;

import com.oj.agent.security.role.model.command.BindRolePermissionsCommand;
import com.oj.agent.security.role.model.command.CreateRoleCommand;
import com.oj.agent.security.role.model.command.DeleteRoleCommand;
import com.oj.agent.security.role.model.command.UpdateRoleCommand;
import com.oj.agent.security.role.model.query.RoleGetQuery;
import com.oj.agent.security.role.model.query.RolePageQuery;
import com.oj.agent.security.role.model.request.BindRolePermissionsRequest;
import com.oj.agent.security.role.model.request.CreateRoleRequest;
import com.oj.agent.security.role.model.request.UpdateRoleRequest;
import com.oj.agent.security.role.model.response.RolePageResponse;
import com.oj.agent.security.role.model.response.RoleResponse;
import com.oj.agent.security.role.model.result.RolePageResult;
import com.oj.agent.security.role.model.result.RoleResult;

import java.util.Collections;
import java.util.List;

public final class RoleConverter {

    private RoleConverter() {
    }

    public static CreateRoleCommand toCreateCommand(CreateRoleRequest request) {
        CreateRoleCommand command = new CreateRoleCommand();
        if (request != null) {
            command.setRoleCode(request.getRoleCode());
            command.setRoleName(request.getRoleName());
            command.setDescription(request.getDescription());
        }
        return command;
    }

    public static UpdateRoleCommand toUpdateCommand(Long roleId, UpdateRoleRequest request) {
        UpdateRoleCommand command = new UpdateRoleCommand();
        command.setRoleId(roleId);
        if (request != null) {
            command.setRoleCode(request.getRoleCode());
            command.setRoleName(request.getRoleName());
            command.setDescription(request.getDescription());
        }
        return command;
    }

    public static DeleteRoleCommand toDeleteCommand(Long roleId) {
        DeleteRoleCommand command = new DeleteRoleCommand();
        command.setRoleId(roleId);
        return command;
    }

    public static BindRolePermissionsCommand toBindPermissionsCommand(Long roleId, BindRolePermissionsRequest request) {
        BindRolePermissionsCommand command = new BindRolePermissionsCommand();
        command.setRoleId(roleId);
        command.setPermissionIds(request == null ? Collections.emptyList() : request.getPermissionIds());
        return command;
    }

    public static RoleGetQuery toGetQuery(Long roleId) {
        RoleGetQuery query = new RoleGetQuery();
        query.setRoleId(roleId);
        return query;
    }

    public static RolePageQuery toPageQuery(Integer page, Integer size) {
        RolePageQuery query = new RolePageQuery();
        query.setCurrent(page == null || page <= 0 ? 1L : page.longValue());
        query.setSize(size == null || size <= 0 ? 10L : size.longValue());
        return query;
    }

    public static RoleResponse toRoleResponse(RoleResult result) {
        if (result == null) {
            return null;
        }
        RoleResponse response = new RoleResponse();
        response.setId(result.getId());
        response.setRoleCode(result.getRoleCode());
        response.setRoleName(result.getRoleName());
        response.setDescription(result.getDescription());
        response.setIsDefault(result.getIsDefault());
        response.setPermissions(toPermissionInfos(result.getPermissions()));
        response.setCreateTime(result.getCreateTime());
        return response;
    }

    public static RolePageResponse toRolePageResponse(RolePageResult result) {
        RolePageResponse response = new RolePageResponse();
        if (result != null) {
            response.setCurrent(result.getCurrent());
            response.setSize(result.getSize());
            response.setTotal(result.getTotal());
            response.setPages(result.getPages());
            response.setRecords(toRoleResponses(result.getRecords()));
        }
        return response;
    }

    private static List<RoleResponse> toRoleResponses(List<RoleResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream()
                .map(RoleConverter::toRoleResponse)
                .toList();
    }

    private static List<RoleResponse.PermissionInfo> toPermissionInfos(List<RoleResult.PermissionInfoResult> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptyList();
        }
        return permissions.stream()
                .map(permission -> new RoleResponse.PermissionInfo(
                        permission.getPermissionId(),
                        permission.getPermissionCode(),
                        permission.getPermissionName()
                ))
                .toList();
    }
}
