package com.oj.agent.security.exception;

/**
 * 授权异常 - 用于权限验证失败场景
 */
public class AuthorizationException extends RuntimeException {

    private String errorCode;

    public AuthorizationException(String message) {
        super(message);
        this.errorCode = "AUTHZ_001";
    }

    public AuthorizationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
