package com.oj.agent.admin.user.service;

import com.oj.agent.admin.user.model.command.AdminUserDisableCommand;
import com.oj.agent.admin.user.model.query.AdminLoginLogPageQuery;
import com.oj.agent.admin.user.model.query.AdminUserPageQuery;
import com.oj.agent.admin.user.model.result.AdminLoginLogPageResult;
import com.oj.agent.admin.user.model.result.AdminUserPageResult;

public interface AdminUserManagementService {

    void updateUserStatus(AdminUserDisableCommand command);

    AdminLoginLogPageResult pageLoginLogs(AdminLoginLogPageQuery query);

    AdminUserPageResult pageUsers(AdminUserPageQuery query);
}
