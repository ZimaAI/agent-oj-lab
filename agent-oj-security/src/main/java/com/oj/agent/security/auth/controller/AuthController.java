package com.oj.agent.security.auth.controller;

import com.oj.agent.common.api.Result;
import com.oj.agent.security.auth.converter.AuthConverter;
import com.oj.agent.security.auth.model.request.LoginRequest;
import com.oj.agent.security.auth.model.request.RefreshTokenRequest;
import com.oj.agent.security.auth.model.response.LoginResponse;
import com.oj.agent.security.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        return Result.success(AuthConverter.toLoginResponse(
                authService.login(AuthConverter.toLoginCommand(request, extractClientIp(httpServletRequest)))
        ));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        return Result.success(AuthConverter.toLoginResponse(authService.refreshToken(AuthConverter.toRefreshTokenCommand(request))));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
