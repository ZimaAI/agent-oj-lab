package com.oj.agent.security.user.service;

import com.oj.agent.security.user.model.command.UserLoginLogCommand;
import com.oj.agent.security.user.model.command.UserUsernameOnlyCreateCommand;
import com.oj.agent.security.user.model.query.UserAuthByIdQuery;
import com.oj.agent.security.user.model.query.UserAuthByUsernameQuery;
import com.oj.agent.security.user.model.query.UserPermissionQuery;
import com.oj.agent.security.user.model.result.UserAuthResult;
import com.oj.agent.security.user.model.result.UserPermissionResult;

public interface UserAuthService {

    UserAuthResult getUserByUsername(UserAuthByUsernameQuery query);

    UserAuthResult getUserById(UserAuthByIdQuery query);

    UserAuthResult createUsernameOnlyUser(UserUsernameOnlyCreateCommand command);

    UserPermissionResult getUserPermissions(UserPermissionQuery query);

    void createLoginLog(UserLoginLogCommand command);
}
