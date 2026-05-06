package com.oj.agent.redis.lock.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁注解。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributeLock {

    /**
     * 加锁场景，作为锁 key 前缀。
     */
    String scene();

    /**
     * 静态锁 key。
     */
    String key() default "";

    /**
     * 动态锁 key 的 SpEL 表达式。
     */
    String keyExpression() default "";

    /**
     * 锁过期时间，单位毫秒，-1 表示不过期。
     */
    long expireTime() default -1L;

    /**
     * 获取锁等待时间，单位毫秒，-1 表示一直等待。
     */
    long waitTime() default -1L;
}
