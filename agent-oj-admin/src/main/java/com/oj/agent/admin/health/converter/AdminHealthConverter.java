package com.oj.agent.admin.health.converter;

import com.oj.agent.admin.health.model.response.AdminModuleInfoResponse;
import com.oj.agent.admin.health.model.result.AdminModuleInfoResult;
import org.springframework.stereotype.Component;

@Component
public class AdminHealthConverter {

    public AdminModuleInfoResponse toResponse(AdminModuleInfoResult result) {
        if (result == null) {
            return null;
        }
        return new AdminModuleInfoResponse(
                result.getModule(),
                result.getStatus(),
                result.getDescription()
        );
    }
}
