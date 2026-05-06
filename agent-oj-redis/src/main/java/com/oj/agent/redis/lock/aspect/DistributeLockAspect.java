package com.oj.agent.redis.lock.aspect;

import com.oj.agent.redis.lock.annotation.DistributeLock;
import com.oj.agent.redis.lock.exception.DistributedLockException;
import com.oj.agent.redis.lock.support.DistributeLockKeyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributeLockAspect {

    private final RedissonClient redissonClient;

    private final DistributeLockKeyResolver keyResolver;

    /**
     * 在方法执行前后织入分布式锁控制
     */
    @Around("@annotation(distributeLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributeLock distributeLock) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        String lockKey = keyResolver.resolveKey(method, joinPoint.getArgs(), distributeLock);

        RLock lock = redissonClient.getLock(lockKey);
        boolean lockSuccess = acquireLock(lock, lockKey, distributeLock);
        if (!lockSuccess) {
            throw new DistributedLockException("获取分布式锁失败 lockKey=" + lockKey);
        }

        try {
            return joinPoint.proceed();
        } finally {
            releaseLock(lock, lockKey);
        }
    }

    /**
     * 根据注解参数执行加锁
     */
    private boolean acquireLock(RLock lock, String lockKey, DistributeLock distributeLock) {
        long waitTime = distributeLock.waitTime();
        long expireTime = distributeLock.expireTime();

        try {
            if (waitTime < 0) {
                if (expireTime < 0) {
                    lock.lock();
                } else {
                    lock.lock(expireTime, TimeUnit.MILLISECONDS);
                }
                return true;
            }

            if (expireTime < 0) {
                return lock.tryLock(waitTime, TimeUnit.MILLISECONDS);
            }
            return lock.tryLock(waitTime, expireTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockException("获取分布式锁被中断 lockKey=" + lockKey, e);
        } catch (Exception e) {
            throw new DistributedLockException("获取分布式锁异常 lockKey=" + lockKey, e);
        }
    }

    /**
     * 仅在当前线程持锁时执行解锁
     */
    private void releaseLock(RLock lock, String lockKey) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            return;
        }
        log.debug("当前线程未持有分布式锁 跳过解锁 lockKey={}", lockKey);
    }
}
