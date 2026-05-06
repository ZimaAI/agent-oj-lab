package com.oj.agent.security.annotation;

import java.lang.annotation.*;

/**
 * 权限验证注解
 * 用于标注需要权限验证的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 需要的权限编码
     */
    String[] value();

    /**
     * 逻辑类型：AND 或 OR
     * 默认为 OR（满足任一权限即可）
     */
    LogicalType logical() default LogicalType.OR;
}
