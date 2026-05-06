package com.oj.agent.core.conversation.util;


import com.oj.agent.core.conversation.model.entity.Conversation;

import java.util.Objects;

public final class ConversationAccessUtil {

    private ConversationAccessUtil() {
    }

    // 校验会话存在且归属当前用户。
    public static Conversation requireOwnership(Conversation conversation, Long userId) {
        return requireOwnership(conversation, userId,
                IllegalArgumentException::new,
                IllegalArgumentException::new);
    }

    // 校验会话存在且归属当前用户，并允许调用方自定义异常类型。
    public static <M extends RuntimeException, P extends RuntimeException> Conversation requireOwnership(
            Conversation conversation,
            Long userId,
            java.util.function.Function<String, M> missingExceptionFactory,
            java.util.function.Function<String, P> permissionExceptionFactory) {
        Objects.requireNonNull(missingExceptionFactory, "missingExceptionFactory 不能为空");
        Objects.requireNonNull(permissionExceptionFactory, "permissionExceptionFactory 不能为空");
        if (conversation == null) {
            throw missingExceptionFactory.apply("conversationId 不存在");
        }
        if (!userId.equals(conversation.getUserId())) {
            throw permissionExceptionFactory.apply("conversationId 无权访问");
        }
        return conversation;
    }
}
