package com.oj.agent.security.config;

import com.oj.agent.security.filter.JwtAuthenticationFilter;
import com.oj.agent.security.filter.TrialCountFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全配置类
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TrialCountFilter trialCountFilter;

    /**
     * 注册 JWT 认证过滤器
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(jwtAuthenticationFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    /**
     * 注册体验次数过滤器（在 JWT 过滤器之后执行）
     */
    @Bean
    public FilterRegistrationBean<TrialCountFilter> trialCountFilterRegistration() {
        FilterRegistrationBean<TrialCountFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(trialCountFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        return registration;
    }
}
