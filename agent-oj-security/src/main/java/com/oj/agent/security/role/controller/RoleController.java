package com.oj.agent.security.role.controller;

import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import com.oj.agent.security.role.converter.RoleConverter;
import com.oj.agent.security.role.model.request.BindRolePermissionsRequest;
import com.oj.agent.security.role.model.request.CreateRoleRequest;
import com.oj.agent.security.role.model.request.UpdateRoleRequest;
import com.oj.agent.security.role.model.response.RolePageResponse;
import com.oj.agent.security.role.model.response.RoleResponse;
import com.oj.agent.security.role.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @RequirePermission("role:create")
    public Result<RoleResponse> createRole(@RequestBody CreateRoleRequest request) {
        return Result.success(RoleConverter.toRoleResponse(
                roleService.createRole(RoleConverter.toCreateCommand(request))
        ));
    }

    @PutMapping("/{id}")
    @RequirePermission("role:update")
    public Result<RoleResponse> updateRole(@PathVariable Long id, @RequestBody UpdateRoleRequest request) {
        return Result.success(RoleConverter.toRoleResponse(
                roleService.updateRole(RoleConverter.toUpdateCommand(id, request))
        ));
    }

    @GetMapping
    @RequirePermission("role:list")
    public Result<RolePageResponse> listRoles(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return Result.success(RoleConverter.toRolePageResponse(
                roleService.listRoles(RoleConverter.toPageQuery(page, size))
        ));
    }

    @GetMapping("/{id}")
    @RequirePermission("role:read")
    public Result<RoleResponse> getRoleById(@PathVariable Long id) {
        return Result.success(RoleConverter.toRoleResponse(
                roleService.getRoleById(RoleConverter.toGetQuery(id))
        ));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("role:delete")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(RoleConverter.toDeleteCommand(id));
        return Result.success();
    }

    @PostMapping("/{id}/permissions")
    @RequirePermission("role:update")
    public Result<Void> bindPermissions(@PathVariable Long id, @RequestBody BindRolePermissionsRequest request) {
        roleService.bindPermissions(RoleConverter.toBindPermissionsCommand(id, request));
        return Result.success();
    }
}
