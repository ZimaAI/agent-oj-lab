package com.oj.agent.security.auth.service;

import com.oj.agent.security.auth.model.command.LoginCommand;
import com.oj.agent.security.auth.model.command.RefreshTokenCommand;
import com.oj.agent.security.auth.model.result.AuthResult;

public interface AuthService {

    AuthResult login(LoginCommand command);

    AuthResult refreshToken(RefreshTokenCommand command);
}
