package com.oj.agent.admin.health.service;

import com.oj.agent.admin.health.model.result.AdminModuleInfoResult;
import org.springframework.stereotype.Service;


@Service
public class AdminHealthService {

    /**
     * 构建管理端模块信息。
     */
    public AdminModuleInfoResult getModuleInfo() {
        return new AdminModuleInfoResult(
                "agent-oj-admin",
                "UP",
                "管理员模块骨架已加载"
        );
    }
}
