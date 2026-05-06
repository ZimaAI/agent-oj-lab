package com.oj.agent.security.permission.controller;

import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import com.oj.agent.security.permission.converter.PermissionConverter;
import com.oj.agent.security.permission.model.request.CreatePermissionRequest;
import com.oj.agent.security.permission.model.request.UpdatePermissionRequest;
import com.oj.agent.security.permission.model.response.PermissionResponse;
import com.oj.agent.security.permission.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    @RequirePermission("permission:create")
    public Result<PermissionResponse> createPermission(@RequestBody CreatePermissionRequest request) {
        return Result.success(PermissionConverter.toPermissionResponse(
                permissionService.createPermission(PermissionConverter.toCreateCommand(request))
        ));
    }

    @PutMapping("/{id}")
    @RequirePermission("permission:update")
    public Result<PermissionResponse> updatePermission(@PathVariable Long id, @RequestBody UpdatePermissionRequest request) {
        return Result.success(PermissionConverter.toPermissionResponse(
                permissionService.updatePermission(PermissionConverter.toUpdateCommand(id, request))
        ));
    }

    @GetMapping("/tree")
    @RequirePermission("permission:list")
    public Result<List<PermissionResponse>> getPermissionTree() {
        return Result.success(PermissionConverter.toPermissionResponses(
                permissionService.getPermissionTree(PermissionConverter.toTreeQuery())
        ));
    }

    @GetMapping("/{id}")
    @RequirePermission("permission:read")
    public Result<PermissionResponse> getPermissionById(@PathVariable Long id) {
        return Result.success(PermissionConverter.toPermissionResponse(
                permissionService.getPermissionById(PermissionConverter.toGetQuery(id))
        ));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("permission:delete")
    public Result<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(PermissionConverter.toDeleteCommand(id));
        return Result.success();
    }
}
