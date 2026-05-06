package com.oj.agent.start.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration globalConfig = createGlobalCorsConfiguration();
        CorsConfiguration apiConfig = createApiCorsConfiguration();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", apiConfig);
        source.registerCorsConfiguration("/**", globalConfig);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    private CorsConfiguration createGlobalCorsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowedPatterns = normalizeOriginPatterns(corsProperties.getAllowedOriginPatterns());
        if (allowedPatterns.isEmpty()) {
            config.addAllowedOriginPattern("*");
        } else {
            allowedPatterns.forEach(config::addAllowedOriginPattern);
        }
        applyCommonSettings(config);
        return config;
    }

    private CorsConfiguration createApiCorsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        applyCommonSettings(config);
        return config;
    }

    private List<String> normalizeOriginPatterns(List<String> patterns) {
        return patterns.stream()
                .filter(StringUtils::hasText)
                .flatMap(pattern -> Arrays.stream(pattern.split(",")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private void applyCommonSettings(CorsConfiguration config) {
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
    }
}
