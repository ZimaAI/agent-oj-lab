package com.oj.agent.security.auth.service.impl;

import com.oj.agent.security.auth.model.command.LoginCommand;
import com.oj.agent.security.auth.model.command.RefreshTokenCommand;
import com.oj.agent.security.auth.model.result.AuthResult;
import com.oj.agent.security.auth.service.AuthService;
import com.oj.agent.security.exception.AuthenticationException;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.user.model.command.UserLoginLogCommand;
import com.oj.agent.security.user.model.command.UserUsernameOnlyCreateCommand;
import com.oj.agent.security.user.model.query.UserAuthByIdQuery;
import com.oj.agent.security.user.model.query.UserAuthByUsernameQuery;
import com.oj.agent.security.user.model.query.UserPermissionQuery;
import com.oj.agent.security.user.model.result.UserAuthResult;
import com.oj.agent.security.user.model.result.UserPermissionResult;
import com.oj.agent.security.user.service.UserAuthService;
import com.oj.agent.security.util.JwtUtil;
import com.oj.agent.security.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserAuthService userAuthService;
    private final JwtUtil jwtUtil;

    @Override
    public AuthResult login(LoginCommand command) {
        UserAuthResult user = getUserByUsername(command);
        if (user == null) {
            user = autoCreateUserForUsernameOnlyLogin(command);
        }
        if (user == null) {
            throw authenticationException(SecurityErrorCode.AUTHN_001);
        }

        validatePassword(user, command != null ? command.getPassword() : null);
        validateAccountStatus(user);
        createLoginLog(user, command != null ? command.getClientIp() : null);

        List<String> permissions = getPermissionCodes(user.getId());
        return buildAuthResult(user, permissions);
    }

    @Override
    public AuthResult refreshToken(RefreshTokenCommand command) {
        String refreshToken = command != null ? command.getRefreshToken() : null;
        if (!jwtUtil.validateToken(refreshToken)) {
            throw authenticationException(SecurityErrorCode.AUTHN_005);
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        UserAuthByIdQuery userQuery = new UserAuthByIdQuery();
        userQuery.setUserId(userId);
        UserAuthResult user = userAuthService.getUserById(userQuery);
        if (user == null) {
            throw authenticationException(SecurityErrorCode.AUTHN_009);
        }

        validateAccountStatus(user);
        List<String> permissions = getPermissionCodes(user.getId());
        return buildAuthResult(user, permissions);
    }

    private UserAuthResult getUserByUsername(LoginCommand command) {
        UserAuthByUsernameQuery query = new UserAuthByUsernameQuery();
        query.setUsername(command != null ? command.getUsername() : null);
        return userAuthService.getUserByUsername(query);
    }

    private UserAuthResult autoCreateUserForUsernameOnlyLogin(LoginCommand command) {
        if (command == null || !StringUtils.hasText(command.getUsername()) || StringUtils.hasText(command.getPassword())) {
            return null;
        }

        UserUsernameOnlyCreateCommand createCommand = new UserUsernameOnlyCreateCommand();
        createCommand.setUsername(command.getUsername());
        return userAuthService.createUsernameOnlyUser(createCommand);
    }

    private void createLoginLog(UserAuthResult user, String clientIp) {
        UserLoginLogCommand command = new UserLoginLogCommand();
        command.setUserId(user.getId());
        command.setUsername(user.getUsername());
        command.setClientIp(clientIp);
        userAuthService.createLoginLog(command);
    }

    private List<String> getPermissionCodes(Long userId) {
        UserPermissionQuery query = new UserPermissionQuery();
        query.setUserId(userId);
        UserPermissionResult result = userAuthService.getUserPermissions(query);
        if (result == null || result.getPermissionCodes() == null) {
            return Collections.emptyList();
        }
        return result.getPermissionCodes();
    }

    private AuthResult buildAuthResult(UserAuthResult user, List<String> permissions) {
        AuthResult result = new AuthResult();
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setUserType(user.getUserType());
        result.setPermissions(permissions);
        result.setAccessToken(jwtUtil.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getUserType(),
                user.getTrialCount(),
                permissions
        ));
        result.setRefreshToken(jwtUtil.generateRefreshToken(
                user.getId(),
                user.getUsername(),
                user.getUserType()
        ));
        return result;
    }

    private void validatePassword(UserAuthResult user, String password) {
        if (!Integer.valueOf(1).equals(user.getNeedPassword())) {
            return;
        }
        if (!StringUtils.hasText(password)) {
            throw authenticationException(SecurityErrorCode.AUTHN_008);
        }
        if (!PasswordUtil.matches(password, user.getPassword())) {
            throw authenticationException(SecurityErrorCode.AUTHN_001);
        }
    }

    private void validateAccountStatus(UserAuthResult user) {
        if (Integer.valueOf(0).equals(user.getIsActive())) {
            throw authenticationException(SecurityErrorCode.AUTHN_002);
        }
        if (Integer.valueOf(1).equals(user.getUserType())) {
            LocalDateTime now = LocalDateTime.now();
            if (user.getValidUntil() != null && user.getValidUntil().isBefore(now)) {
                throw authenticationException(SecurityErrorCode.AUTHN_003);
            }
            if (user.getValidFrom() != null && user.getValidFrom().isAfter(now)) {
                throw authenticationException(SecurityErrorCode.AUTHN_004);
            }
        }
    }

    private AuthenticationException authenticationException(SecurityErrorCode errorCode) {
        return new AuthenticationException(errorCode.getCode(), errorCode.getMessage());
    }
}
