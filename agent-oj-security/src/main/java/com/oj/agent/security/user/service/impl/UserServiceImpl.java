package com.oj.agent.security.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oj.agent.security.exception.AuthenticationException;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.role.model.query.RoleIdsQuery;
import com.oj.agent.security.role.model.result.RoleSummaryResult;
import com.oj.agent.security.role.service.RoleLookupService;
import com.oj.agent.security.user.mapper.UserLoginLogMapper;
import com.oj.agent.security.user.mapper.UserMapper;
import com.oj.agent.security.user.mapper.UserRoleMapper;
import com.oj.agent.security.user.model.command.UserCreateCommand;
import com.oj.agent.security.user.model.command.UserDeleteCommand;
import com.oj.agent.security.user.model.command.UserStatusUpdateCommand;
import com.oj.agent.security.user.model.command.UserUpdateCommand;
import com.oj.agent.security.user.model.entity.User;
import com.oj.agent.security.user.model.entity.UserLoginLog;
import com.oj.agent.security.user.model.entity.UserRole;
import com.oj.agent.security.user.model.query.UserCurrentQuery;
import com.oj.agent.security.user.model.query.UserGetQuery;
import com.oj.agent.security.user.model.query.UserListQuery;
import com.oj.agent.security.user.model.query.UserLoginLogPageQuery;
import com.oj.agent.security.user.model.result.CurrentUserResult;
import com.oj.agent.security.user.model.result.UserLoginLogItemResult;
import com.oj.agent.security.user.model.result.UserLoginLogPageResult;
import com.oj.agent.security.user.model.result.UserPageResult;
import com.oj.agent.security.user.model.result.UserResult;
import com.oj.agent.security.user.service.UserService;
import com.oj.agent.security.util.PasswordUtil;
import com.oj.agent.security.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleLookupService roleLookupService;
    private final UserRoleMapper userRoleMapper;
    private final UserLoginLogMapper userLoginLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResult createUser(UserCreateCommand command) {
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, command.getUsername()));
        if (existingUser != null) {
            throw new AuthenticationException(
                    SecurityErrorCode.USER_001.getCode(),
                    SecurityErrorCode.USER_001.getMessage()
            );
        }

        validateValidity(command.getValidFrom(), command.getValidUntil());

        User user = new User();
        user.setUsername(command.getUsername());
        user.setUserType(1);
        user.setNeedPassword(command.getNeedPassword());
        user.setIsActive(1);
        user.setValidFrom(command.getValidFrom());
        user.setValidUntil(command.getValidUntil());
        user.setTrialCount(command.getTrialCount() != null ? command.getTrialCount() : -1);
        if (StringUtils.hasText(command.getPassword())) {
            user.setPassword(PasswordUtil.encode(command.getPassword()));
        }
        userMapper.insert(user);

        replaceUserRoles(user.getId(), command.getRoleIds(), false);
        return getUserById(buildGetQuery(user.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResult updateUser(UserUpdateCommand command) {
        User user = getRequiredUser(command.getUserId());
        validateValidity(command.getValidFrom(), command.getValidUntil());

        if (StringUtils.hasText(command.getPassword())) {
            user.setPassword(PasswordUtil.encode(command.getPassword()));
        }
        if (command.getNeedPassword() != null) {
            user.setNeedPassword(command.getNeedPassword());
        }
        if (command.getValidFrom() != null) {
            user.setValidFrom(command.getValidFrom());
        }
        if (command.getValidUntil() != null) {
            user.setValidUntil(command.getValidUntil());
        }
        if (command.getTrialCount() != null) {
            user.setTrialCount(command.getTrialCount());
        }
        userMapper.updateById(user);

        replaceUserRoles(command.getUserId(), command.getRoleIds(), true);
        return getUserById(buildGetQuery(command.getUserId()));
    }

    @Override
    public UserPageResult listUsers(UserListQuery query) {
        long current = normalizeCurrent(query == null ? null : query.getCurrent());
        long size = normalizeSize(query == null ? null : query.getSize());
        Page<User> page = new Page<>(current, size);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (query != null && query.getUserType() != null) {
            wrapper.eq(User::getUserType, query.getUserType());
        }
        if (query != null && query.getIsActive() != null) {
            wrapper.eq(User::getIsActive, query.getIsActive());
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> userPage = userMapper.selectPage(page, wrapper);
        if (userPage == null) {
            userPage = page;
        }

        UserPageResult result = new UserPageResult();
        result.setCurrent(userPage.getCurrent());
        result.setSize(userPage.getSize());
        result.setTotal(userPage.getTotal());
        result.setRecords(userPage.getRecords().stream()
                .map(user -> toUserResult(user, getUserRoles(user.getId())))
                .toList());
        return result;
    }

    @Override
    public UserResult getUserById(UserGetQuery query) {
        User user = getRequiredUser(query.getUserId());
        return toUserResult(user, getUserRoles(query.getUserId()));
    }

    @Override
    public void deleteUser(UserDeleteCommand command) {
        getRequiredUser(command.getUserId());
        userMapper.deleteById(command.getUserId());
    }

    @Override
    public void updateUserStatus(UserStatusUpdateCommand command) {
        User user = getRequiredUser(command.getUserId());
        user.setIsActive(command.getIsActive());
        userMapper.updateById(user);
    }

    @Override
    public CurrentUserResult getCurrentUser(UserCurrentQuery query) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthenticationException(
                    SecurityErrorCode.AUTHN_001.getCode(),
                    SecurityErrorCode.AUTHN_001.getMessage()
            );
        }

        User user = getRequiredUser(userId);
        CurrentUserResult result = new CurrentUserResult();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setUserType(user.getUserType());
        result.setIsActive(user.getIsActive());
        result.setValidFrom(user.getValidFrom());
        result.setValidUntil(user.getValidUntil());
        result.setTrialCount(user.getTrialCount());
        return result;
    }

    @Override
    public UserLoginLogPageResult pageLoginLogs(UserLoginLogPageQuery query) {
        long current = normalizeCurrent(query == null ? null : query.getCurrent());
        long size = normalizeSize(query == null ? null : query.getSize());
        Page<UserLoginLog> page = new Page<>(current, size);
        LambdaQueryWrapper<UserLoginLog> wrapper = new LambdaQueryWrapper<UserLoginLog>()
                .eq(UserLoginLog::getIsDelete, 0)
                .orderByDesc(UserLoginLog::getLoginTime)
                .orderByDesc(UserLoginLog::getId);
        Page<UserLoginLog> logPage = userLoginLogMapper.selectPage(page, wrapper);
        if (logPage == null) {
            logPage = page;
        }

        UserLoginLogPageResult result = new UserLoginLogPageResult();
        result.setCurrent(logPage.getCurrent());
        result.setSize(logPage.getSize());
        result.setTotal(logPage.getTotal());
        result.setRecords(logPage.getRecords().stream()
                .map(this::toLoginLogItemResult)
                .toList());
        return result;
    }

    private UserGetQuery buildGetQuery(Long userId) {
        UserGetQuery query = new UserGetQuery();
        query.setUserId(userId);
        return query;
    }

    private void validateValidity(LocalDateTime validFrom, LocalDateTime validUntil) {
        if (validFrom != null && validUntil != null && !validFrom.isBefore(validUntil)) {
            throw new AuthenticationException(
                    SecurityErrorCode.USER_002.getCode(),
                    SecurityErrorCode.USER_002.getMessage()
            );
        }
    }

    private User getRequiredUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AuthenticationException(
                    SecurityErrorCode.AUTHN_009.getCode(),
                    SecurityErrorCode.AUTHN_009.getMessage()
            );
        }
        return user;
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds, boolean deleteExisting) {
        if (deleteExisting) {
            userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, userId));
        }

        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        for (Long roleId : roleIds) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

    private List<UserResult.RoleInfoResult> getUserRoles(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));
        if (userRoles == null || userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        RoleIdsQuery query = new RoleIdsQuery();
        query.setRoleIds(userRoles.stream()
                .map(UserRole::getRoleId)
                .toList());
        List<RoleSummaryResult> roles = roleLookupService.listRoleSummaries(query);
        return roles.stream()
                .map(role -> new UserResult.RoleInfoResult(
                        role.getRoleId(),
                        role.getRoleName(),
                        role.getRoleCode()
                ))
                .toList();
    }

    private UserResult toUserResult(User user, List<UserResult.RoleInfoResult> roles) {
        UserResult result = new UserResult();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setUserType(user.getUserType());
        result.setNeedPassword(user.getNeedPassword());
        result.setIsActive(user.getIsActive());
        result.setValidFrom(user.getValidFrom());
        result.setValidUntil(user.getValidUntil());
        result.setTrialCount(user.getTrialCount());
        result.setRoles(roles);
        result.setCreateTime(user.getCreateTime());
        result.setUpdateTime(user.getUpdateTime());
        return result;
    }

    private UserLoginLogItemResult toLoginLogItemResult(UserLoginLog log) {
        UserLoginLogItemResult result = new UserLoginLogItemResult();
        result.setId(log.getId());
        result.setUserId(log.getUserId());
        result.setUsername(log.getUsername());
        result.setLoginIp(log.getLoginIp());
        result.setLoginTime(log.getLoginTime());
        return result;
    }

    private long normalizeCurrent(Long current) {
        return current == null || current <= 0 ? 1L : current;
    }

    private long normalizeSize(Long size) {
        if (size == null || size <= 0) {
            return 10L;
        }
        return Math.min(size, 100L);
    }
}
