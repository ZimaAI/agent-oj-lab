package com.oj.agent.security.exception;

/**
 * 认证异常 - 用于登录、Token 验证等认证失败场景
 */
public class AuthenticationException extends RuntimeException {

    private String errorCode;

    public AuthenticationException(String message) {
        super(message);
        this.errorCode = "AUTHN_001";
    }

    public AuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
