package com.oj.agent.security.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${security.jwt.secret:agent-oj-secret-key-for-jwt-token-generation-minimum-256-bits}")
    private String secret;

    @Value("${security.jwt.access-token-expiration:7200000}")
    private Long accessTokenExpiration; // 默认 2 小时

    @Value("${security.jwt.refresh-token-expiration:604800000}")
    private Long refreshTokenExpiration; // 默认 7 天

    /**
     * 生成 Access Token
     *
     * @param userId      用户ID
     * @param username    用户名
     * @param userType    用户类型
     * @param permissions 权限列表
     * @return Access Token
     */
    public String generateAccessToken(Long userId, String username, Integer userType, Integer trialCount, List<String> permissions) {
        return generateToken(userId, username, userType, trialCount, permissions, accessTokenExpiration);
    }

    /**
     * 生成 Refresh Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param userType 用户类型
     * @return Refresh Token
     */
    public String generateRefreshToken(Long userId, String username, Integer userType) {
        return generateToken(userId, username, userType, null, null, refreshTokenExpiration);
    }

    /**
     * 生成 Token
     */
    private String generateToken(Long userId, String username, Integer userType, Integer trialCount, List<String> permissions, Long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("userType", userType)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey());

        if (trialCount != null) {
            builder.claim("trialCount", trialCount);
        }

        if (permissions != null) {
            builder.claim("permissions", permissions);
        }

        return builder.compact();
    }

    /**
     * 验证 Token（不检查过期时间，只验证签名）
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .clockSkewSeconds(Integer.MAX_VALUE) // 使用足够大的值忽略过期检查（约68年）
                    .build()
                    .parseSignedClaims(token);
            log.debug("Token validation successful");
            return true;
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析 Token
     *
     * @param token JWT Token
     * @return Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 Token 中获取用户类型
     */
    public Integer getUserTypeFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userType", Integer.class);
    }

    /**
     * 从 Token 中获取权限列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("permissions", List.class);
    }

    /**
     * 从 Token 中获取剩余体验次数
     */
    public Integer getTrialCountFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("trialCount", Integer.class);
    }

    /**
     * 检查 Token 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
