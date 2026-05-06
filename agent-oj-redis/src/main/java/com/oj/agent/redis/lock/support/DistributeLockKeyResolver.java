package com.oj.agent.redis.lock.support;

import com.oj.agent.redis.lock.annotation.DistributeLock;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 分布式锁 key 解析器
 */
@Component
public class DistributeLockKeyResolver {

    private static final String KEY_DELIMITER = ":";

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 解析并构建最终锁 key
     */
    public String resolveKey(Method method, Object[] args, DistributeLock distributeLock) {
        String scene = trimToNull(distributeLock.scene());
        if (!StringUtils.hasText(scene)) {
            throw new IllegalArgumentException("分布式锁 scene 不能为空");
        }

        String businessKey = resolveBusinessKey(method, args, distributeLock);
        return scene + KEY_DELIMITER + businessKey;
    }

    /**
     * 解析业务 key
     */
    private String resolveBusinessKey(Method method, Object[] args, DistributeLock distributeLock) {
        String staticKey = trimToNull(distributeLock.key());
        if (StringUtils.hasText(staticKey)) {
            return staticKey;
        }

        String keyExpression = trimToNull(distributeLock.keyExpression());
        if (!StringUtils.hasText(keyExpression)) {
            throw new IllegalArgumentException("分布式锁 key 和 keyExpression 不能同时为空");
        }

        Object[] methodArgs = args == null ? new Object[0] : args;
        MethodBasedEvaluationContext context =
                new MethodBasedEvaluationContext(null, method, methodArgs, parameterNameDiscoverer);
        Expression expression = expressionParser.parseExpression(keyExpression);
        Object value = expression.getValue(context);
        String dynamicKey = formatExpressionValue(value);
        if (!StringUtils.hasText(dynamicKey)) {
            throw new IllegalArgumentException("分布式锁 keyExpression 解析结果不能为空");
        }
        return dynamicKey;
    }

    /**
     * 格式化 SpEL 表达式结果
     */
    private String formatExpressionValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining(KEY_DELIMITER));
        }

        if (value.getClass().isArray()) {
            return formatArrayValue(value);
        }

        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(KEY_DELIMITER));
        }

        return trimToNull(value.toString());
    }

    /**
     * 去除首尾空白并转换空串
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 格式化数组表达式值
     */
    private String formatArrayValue(Object value) {
        int length = Array.getLength(value);
        return IntStream.range(0, length)
                .mapToObj(index -> Array.get(value, index))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(KEY_DELIMITER));
    }
}
