package com.oj.agent.security.aspect;

import com.oj.agent.security.annotation.LogicalType;
import com.oj.agent.security.annotation.RequirePermission;
import com.oj.agent.security.exception.AuthorizationException;
import com.oj.agent.security.exception.SecurityErrorCode;
import com.oj.agent.security.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 权限验证切面
 */
@Aspect
@Component
@Slf4j
public class PermissionAspect {

    /**
     * 权限验证
     */
    @Before("@annotation(com.oj.agent.security.annotation.RequirePermission)")
    public void checkPermission(JoinPoint joinPoint) {
        // 1. 获取用户信息
        UserContext.UserInfo userInfo = UserContext.getUser();
        if (userInfo == null) {
            log.warn("权限验证失败：用户未登录");
            throw new AuthorizationException(
                    SecurityErrorCode.AUTHN_007.getCode(),
                    SecurityErrorCode.AUTHN_007.getMessage()
            );
        }

        // 2. 超级管理员绕过权限检查
        if (userInfo.getUserType() == 0) {
            log.debug("超级管理员访问，绕过权限检查");
            return;
        }

        // 3. 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        String[] requiredPermissions = annotation.value();
        LogicalType logicalType = annotation.logical();

        // 4. 获取用户权限
        List<String> userPermissions = userInfo.getPermissions();
        if (userPermissions == null || userPermissions.isEmpty()) {
            log.warn("权限验证失败：用户 {} 没有任何权限", userInfo.getUsername());
            throw new AuthorizationException(
                    SecurityErrorCode.AUTHZ_001.getCode(),
                    SecurityErrorCode.AUTHZ_001.getMessage()
            );
        }

        // 5. 验证权限
        boolean hasPermission = false;
        if (logicalType == LogicalType.OR) {
            // OR 逻辑：满足任一权限即可
            hasPermission = Arrays.stream(requiredPermissions)
                    .anyMatch(userPermissions::contains);
        } else {
            // AND 逻辑：必须满足所有权限
            hasPermission = Arrays.stream(requiredPermissions)
                    .allMatch(userPermissions::contains);
        }

        // 6. 权限验证失败
        if (!hasPermission) {
            log.warn("权限验证失败：用户 {} 缺少权限 {}（逻辑：{}）",
                    userInfo.getUsername(),
                    Arrays.toString(requiredPermissions),
                    logicalType);
            throw new AuthorizationException(
                    SecurityErrorCode.AUTHZ_001.getCode(),
                    SecurityErrorCode.AUTHZ_001.getMessage()
            );
        }

        log.debug("权限验证通过：用户 {} 访问 {}",
                userInfo.getUsername(),
                method.getName());
    }
}
