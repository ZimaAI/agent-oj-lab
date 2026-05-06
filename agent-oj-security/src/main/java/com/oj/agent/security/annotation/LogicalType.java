package com.oj.agent.security.annotation;

/**
 * 逻辑类型枚举
 */
public enum LogicalType {
    /**
     * 或：满足任一权限即可
     */
    OR,

    /**
     * 且：必须满足所有权限
     */
    AND
}
