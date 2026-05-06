package com.oj.agent.security.exception;

/**
 * 安全错误码枚举
 */
public enum SecurityErrorCode {

    // 认证相关错误码
    AUTHN_001("AUTHN_001", "用户名或密码错误"),
    AUTHN_002("AUTHN_002", "账号已失效"),
    AUTHN_003("AUTHN_003", "账号已过期"),
    AUTHN_004("AUTHN_004", "账号尚未生效"),
    AUTHN_005("AUTHN_005", "Token无效"),
    AUTHN_006("AUTHN_006", "Token已过期"),
    AUTHN_007("AUTHN_007", "用户未登录"),
    AUTHN_008("AUTHN_008", "密码不能为空"),
    AUTHN_009("AUTHN_009", "用户不存在"),

    // 授权相关错误码
    AUTHZ_001("AUTHZ_001", "权限不足"),
    AUTHZ_002("AUTHZ_002", "角色编码已存在"),
    AUTHZ_003("AUTHZ_003", "权限编码已存在"),
    AUTHZ_004("AUTHZ_004", "角色已分配给用户，无法删除"),
    AUTHZ_005("AUTHZ_005", "权限已分配给角色，无法删除"),

    // 用户管理相关错误码
    USER_001("USER_001", "用户名已存在"),
    USER_002("USER_002", "有效期设置错误"),

    // 表清理相关错误码
    TABLE_001("TABLE_001", "表清理请求参数错误"),
    TABLE_002("TABLE_002", "表清理服务暂不可用"),

    // 服务端异常错误码
    SERVER_001("SERVER_001", "服务端异常，请稍后重试");

    private final String code;
    private final String message;

    SecurityErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
