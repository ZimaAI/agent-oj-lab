package com.oj.agent.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.util.JwtUtil;
import com.oj.agent.security.util.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 白名单路径（不需要认证）
     */
    private static final List<String> WHITELIST = Arrays.asList(
            "/api/auth/login",
            "/api/auth/refresh"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestPath = request.getRequestURI();

        // 跳过 OPTIONS 预检请求（CORS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 检查是否在白名单中
        if (isWhitelisted(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取 Token
        String token = extractToken(request);
        log.debug("Request path: {}, Token: {}", requestPath, token != null ? "***" + token.substring(Math.max(0, token.length() - 10)) : "null");

        // Token 为空
        if (!StringUtils.hasText(token)) {
            log.warn("Token is missing for path: {}", requestPath);
            sendErrorResponse(response, SecurityErrorCode.AUTHN_007);
            return;
        }

        // 验证 Token
        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("Token validation failed for path: {}", requestPath);
                sendErrorResponse(response, SecurityErrorCode.AUTHN_005);
                return;
            }

            // Token 已过期
            if (jwtUtil.isTokenExpired(token)) {
                log.warn("Token expired for path: {}", requestPath);
                sendErrorResponse(response, SecurityErrorCode.AUTHN_006);
                return;
            }

            // 提取用户信息并设置到上下文
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            Integer userType = jwtUtil.getUserTypeFromToken(token);
            Integer trialCount = jwtUtil.getTrialCountFromToken(token);
            List<String> permissions = jwtUtil.getPermissionsFromToken(token);

            log.debug("Token validated successfully for user: {} (id: {})", username, userId);
            UserContext.setUser(userId, username, userType, trialCount, permissions);

        } catch (Exception e) {
            log.error("Token validation error for path: {}, error: {}", requestPath, e.getMessage(), e);
            sendErrorResponse(response, SecurityErrorCode.AUTHN_005);
            return;
        }

        try {
            // Token 校验通过后继续执行过滤链
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // 过滤链内部异常统一转换为服务端异常，避免前端误判为登录态失效
            log.error("Filter chain execution error for path: {}, error: {}", requestPath, e.getMessage(), e);
            if (!response.isCommitted()) {
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, SecurityErrorCode.SERVER_001);
            } else {
                rethrowFilterException(e);
            }
        } finally {
            // 清理上下文
            UserContext.clear();
        }
    }

    /**
     * 从请求头中提取 Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhitelisted(String requestPath) {
        return WHITELIST.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, SecurityErrorCode errorCode) throws IOException {
        sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, errorCode);
    }

    /**
     * 发送指定状态码的错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int status, SecurityErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", errorCode.getCode());
        errorResponse.put("message", errorCode.getMessage());

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * 透传已提交响应场景下的原始异常
     */
    private void rethrowFilterException(Exception exception) throws ServletException, IOException {
        if (exception instanceof ServletException servletException) {
            throw servletException;
        }
        if (exception instanceof IOException ioException) {
            throw ioException;
        }
        throw new ServletException(exception);
    }
}
