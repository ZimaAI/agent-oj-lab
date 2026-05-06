package com.oj.agent.security.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oj.agent.security.user.mapper.UserLoginLogMapper;
import com.oj.agent.security.user.mapper.UserMapper;
import com.oj.agent.security.user.model.command.UserLoginLogCommand;
import com.oj.agent.security.user.model.command.UserUsernameOnlyCreateCommand;
import com.oj.agent.security.user.model.entity.User;
import com.oj.agent.security.user.model.entity.UserLoginLog;
import com.oj.agent.security.user.model.query.UserAuthByIdQuery;
import com.oj.agent.security.user.model.query.UserAuthByUsernameQuery;
import com.oj.agent.security.user.model.query.UserPermissionQuery;
import com.oj.agent.security.user.model.result.UserAuthResult;
import com.oj.agent.security.user.model.result.UserPermissionResult;
import com.oj.agent.security.user.service.UserAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private final UserMapper userMapper;
    private final UserLoginLogMapper userLoginLogMapper;

    @Override
    public UserAuthResult getUserByUsername(UserAuthByUsernameQuery query) {
        if (query == null || !StringUtils.hasText(query.getUsername())) {
            return null;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, query.getUsername()));
        return toUserAuthResult(user);
    }

    @Override
    public UserAuthResult getUserById(UserAuthByIdQuery query) {
        if (query == null || query.getUserId() == null) {
            return null;
        }
        return toUserAuthResult(userMapper.selectById(query.getUserId()));
    }

    @Override
    public UserAuthResult createUsernameOnlyUser(UserUsernameOnlyCreateCommand command) {
        if (command == null || !StringUtils.hasText(command.getUsername())) {
            return null;
        }

        User user = new User();
        user.setUsername(command.getUsername());
        user.setUserType(1);
        user.setNeedPassword(0);
        user.setIsActive(1);
        user.setTrialCount(-1);

        try {
            userMapper.insert(user);
            return toUserAuthResult(user);
        } catch (DuplicateKeyException ex) {
            User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, command.getUsername()));
            return toUserAuthResult(existingUser);
        }
    }

    @Override
    public UserPermissionResult getUserPermissions(UserPermissionQuery query) {
        UserPermissionResult result = new UserPermissionResult();
        if (query == null || query.getUserId() == null) {
            return result;
        }
        result.setUserId(query.getUserId());
        List<String> permissionCodes = userMapper.selectUserPermissions(query.getUserId());
        result.setPermissionCodes(permissionCodes != null ? permissionCodes : Collections.emptyList());
        return result;
    }

    @Override
    public void createLoginLog(UserLoginLogCommand command) {
        if (command == null) {
            return;
        }

        UserLoginLog log = new UserLoginLog();
        log.setUserId(command.getUserId());
        log.setUsername(command.getUsername());
        log.setLoginIp(StringUtils.hasText(command.getClientIp()) ? command.getClientIp().trim() : "unknown");
        log.setLoginTime(LocalDateTime.now());
        log.setIsDelete(0);
        userLoginLogMapper.insert(log);
    }

    private UserAuthResult toUserAuthResult(User user) {
        if (user == null) {
            return null;
        }

        UserAuthResult result = new UserAuthResult();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setPassword(user.getPassword());
        result.setUserType(user.getUserType());
        result.setNeedPassword(user.getNeedPassword());
        result.setIsActive(user.getIsActive());
        result.setValidFrom(user.getValidFrom());
        result.setValidUntil(user.getValidUntil());
        result.setTrialCount(user.getTrialCount());
        return result;
    }
}
