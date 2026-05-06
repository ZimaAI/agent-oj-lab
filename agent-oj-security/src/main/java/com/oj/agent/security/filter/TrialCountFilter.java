package com.oj.agent.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.agent.security.user.model.command.UserTrialCountDecrementCommand;
import com.oj.agent.security.user.service.UserTrialService;
import com.oj.agent.security.util.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 体验次数过滤器 - 检查用户剩余体验次数，并在请求通过后扣减
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialCountFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final UserTrialService userTrialService;
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${security.trial-filter.enabled:true}")
    private boolean enabled = true;

//    @Value("${security.trial-filter.patterns:/api/agent/**}")
    private List<String> filterPatterns = List.of("/api/conversation/*/chat/stream", "/api/code/submit/stream");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestPath = request.getRequestURI();

        // 只拦截匹配的路径
        if (!matchesFilterPattern(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 检查体验次数
        Integer trialCount = UserContext.getTrialCount();
        if (trialCount != null && trialCount == 0) {
            log.warn("Trial count exhausted for user: {}, path: {}", UserContext.getUsername(), requestPath);
            sendForbiddenResponse(response);
            return;
        }

        // trialCount > 0 或 trialCount == -1（无限制）均放行
        filterChain.doFilter(request, response);

        // 请求处理完成后，若 trialCount > 0 则扣减
        if (trialCount != null && trialCount > 0) {
            Long userId = UserContext.getUserId();
            if (userId != null) {
                UserTrialCountDecrementCommand command = new UserTrialCountDecrementCommand();
                command.setUserId(userId);
                userTrialService.decrementTrialCount(command);
                UserContext.decrementTrialCount();
                log.debug("Trial count decremented for user: {}, path: {}", UserContext.getUsername(), requestPath);
            }
        }
    }

    private boolean matchesFilterPattern(String requestPath) {
        return filterPatterns.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
    }

    private void sendForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", 403);
        errorResponse.put("message", "体验次数已用完");

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
