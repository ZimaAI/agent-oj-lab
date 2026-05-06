package com.oj.agent.admin.health.controller;

import com.oj.agent.admin.health.converter.AdminHealthConverter;
import com.oj.agent.admin.health.model.response.AdminModuleInfoResponse;
import com.oj.agent.admin.health.service.AdminHealthService;
import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端模块健康检查控制器。
 */
@RestController
@RequestMapping("/api/admin/module")
@RequiredArgsConstructor
public class AdminHealthController {

    private final AdminHealthService adminHealthService;
    private final AdminHealthConverter adminHealthConverter;

    /**
     * 查询管理端模块基础状态。
     */
    @GetMapping("/health")
    @RequirePermission("admin:read")
    public Result<AdminModuleInfoResponse> health() {
        return Result.success(adminHealthConverter.toResponse(adminHealthService.getModuleInfo()));
    }
}
