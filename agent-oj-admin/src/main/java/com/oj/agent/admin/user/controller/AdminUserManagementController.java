package com.oj.agent.admin.user.controller;

import com.oj.agent.admin.user.converter.AdminUserConverter;
import com.oj.agent.admin.user.model.request.AdminUserStatusUpdateRequest;
import com.oj.agent.admin.user.model.response.AdminLoginLogPageResponse;
import com.oj.agent.admin.user.model.response.AdminUserPageResponse;
import com.oj.agent.admin.user.service.AdminUserManagementService;
import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserManagementController {

    private final AdminUserManagementService adminUserManagementService;

    @PutMapping("/{userId}/status")
    @RequirePermission("admin:user:update")
    public Result<Void> updateUserStatus(@PathVariable Long userId,
                                         @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        adminUserManagementService.updateUserStatus(AdminUserConverter.toDisableCommand(userId, request));
        return Result.success();
    }

    @GetMapping("/login-logs")
    @RequirePermission("admin:user:login-log:read")
    public Result<AdminLoginLogPageResponse> pageLoginLogs(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        return Result.success(AdminUserConverter.toLoginLogPageResponse(
                adminUserManagementService.pageLoginLogs(AdminUserConverter.toLoginLogPageQuery(current, size))
        ));
    }

    @GetMapping
    @RequirePermission("admin:user:update")
    public Result<AdminUserPageResponse> pageUsers(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        return Result.success(AdminUserConverter.toUserPageResponse(
                adminUserManagementService.pageUsers(AdminUserConverter.toUserPageQuery(current, size))
        ));
    }
}
