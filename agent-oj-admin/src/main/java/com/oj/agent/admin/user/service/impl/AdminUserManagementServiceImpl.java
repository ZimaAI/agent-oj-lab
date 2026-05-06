package com.oj.agent.admin.user.service.impl;

import com.oj.agent.admin.user.converter.AdminUserConverter;
import com.oj.agent.admin.user.model.command.AdminUserDisableCommand;
import com.oj.agent.admin.user.model.query.AdminLoginLogPageQuery;
import com.oj.agent.admin.user.model.query.AdminUserPageQuery;
import com.oj.agent.admin.user.model.result.AdminLoginLogPageResult;
import com.oj.agent.admin.user.model.result.AdminUserPageResult;
import com.oj.agent.admin.user.service.AdminUserManagementService;
import com.oj.agent.security.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminUserManagementServiceImpl implements AdminUserManagementService {

    private final UserService userService;

    @Override
    public void updateUserStatus(AdminUserDisableCommand command) {
        userService.updateUserStatus(AdminUserConverter.toSecurityCommand(command));
    }

    @Override
    public AdminLoginLogPageResult pageLoginLogs(AdminLoginLogPageQuery query) {
        return AdminUserConverter.toResult(
                userService.pageLoginLogs(AdminUserConverter.toSecurityQuery(query))
        );
    }

    @Override
    public AdminUserPageResult pageUsers(AdminUserPageQuery query) {
        return AdminUserConverter.toResult(
                userService.listUsers(AdminUserConverter.toSecurityQuery(query))
        );
    }
}
