package com.oj.agent.security.auth.converter;

import com.oj.agent.security.auth.model.command.LoginCommand;
import com.oj.agent.security.auth.model.command.RefreshTokenCommand;
import com.oj.agent.security.auth.model.request.LoginRequest;
import com.oj.agent.security.auth.model.request.RefreshTokenRequest;
import com.oj.agent.security.auth.model.response.LoginResponse;
import com.oj.agent.security.auth.model.result.AuthResult;

public final class AuthConverter {

    private AuthConverter() {
    }

    public static LoginCommand toLoginCommand(LoginRequest request, String clientIp) {
        LoginCommand command = new LoginCommand();
        if (request != null) {
            command.setUsername(request.getUsername());
            command.setPassword(request.getPassword());
        }
        command.setClientIp(clientIp);
        return command;
    }

    public static RefreshTokenCommand toRefreshTokenCommand(RefreshTokenRequest request) {
        RefreshTokenCommand command = new RefreshTokenCommand();
        if (request != null) {
            command.setRefreshToken(request.getRefreshToken());
        }
        return command;
    }

    public static LoginResponse toLoginResponse(AuthResult result) {
        if (result == null) {
            return null;
        }
        LoginResponse response = new LoginResponse();
        response.setAccessToken(result.getAccessToken());
        response.setRefreshToken(result.getRefreshToken());
        response.setUserInfo(new LoginResponse.UserInfo(
                result.getUserId(),
                result.getUsername(),
                result.getUserType(),
                result.getPermissions()
        ));
        return response;
    }
}
