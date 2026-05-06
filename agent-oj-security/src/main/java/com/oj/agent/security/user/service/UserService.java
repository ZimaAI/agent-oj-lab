package com.oj.agent.security.user.service;

import com.oj.agent.security.user.model.command.UserCreateCommand;
import com.oj.agent.security.user.model.command.UserDeleteCommand;
import com.oj.agent.security.user.model.command.UserStatusUpdateCommand;
import com.oj.agent.security.user.model.command.UserUpdateCommand;
import com.oj.agent.security.user.model.query.UserCurrentQuery;
import com.oj.agent.security.user.model.query.UserGetQuery;
import com.oj.agent.security.user.model.query.UserListQuery;
import com.oj.agent.security.user.model.query.UserLoginLogPageQuery;
import com.oj.agent.security.user.model.result.CurrentUserResult;
import com.oj.agent.security.user.model.result.UserLoginLogPageResult;
import com.oj.agent.security.user.model.result.UserPageResult;
import com.oj.agent.security.user.model.result.UserResult;

public interface UserService {

    UserResult createUser(UserCreateCommand command);

    UserResult updateUser(UserUpdateCommand command);

    UserPageResult listUsers(UserListQuery query);

    UserResult getUserById(UserGetQuery query);

    void deleteUser(UserDeleteCommand command);

    void updateUserStatus(UserStatusUpdateCommand command);

    CurrentUserResult getCurrentUser(UserCurrentQuery query);

    UserLoginLogPageResult pageLoginLogs(UserLoginLogPageQuery query);
}
