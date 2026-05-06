package com.oj.agent.security.util;

import java.util.List;

/**
 * 用户上下文 - 使用 ThreadLocal 存储当前请求的用户信息
 */
public class UserContext {

    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();

    /**
     * 设置用户信息
     */
    public static void setUser(Long userId, String username, Integer userType, Integer trialCount, List<String> permissions) {
        CONTEXT.set(new UserInfo(userId, username, userType, trialCount, permissions));
    }

    /**
     * 获取用户信息
     */
    public static UserInfo getUser() {
        return CONTEXT.get();
    }

    /**
     * 获取用户ID
     */
    public static Long getUserId() {
        UserInfo userInfo = CONTEXT.get();
        return userInfo != null ? userInfo.getUserId() : null;
    }

    /**
     * 获取用户名
     */
    public static String getUsername() {
        UserInfo userInfo = CONTEXT.get();
        return userInfo != null ? userInfo.getUsername() : null;
    }

    /**
     * 获取用户类型
     */
    public static Integer getUserType() {
        UserInfo userInfo = CONTEXT.get();
        return userInfo != null ? userInfo.getUserType() : null;
    }

    /**
     * 获取权限列表
     */
    public static List<String> getPermissions() {
        UserInfo userInfo = CONTEXT.get();
        return userInfo != null ? userInfo.getPermissions() : null;
    }

    /**
     * 获取剩余体验次数
     */
    public static Integer getTrialCount() {
        UserInfo userInfo = CONTEXT.get();
        return userInfo != null ? userInfo.getTrialCount() : null;
    }

    /**
     * 将体验次数 -1（仅当 trialCount > 0 时生效）
     */
    public static void decrementTrialCount() {
        UserInfo userInfo = CONTEXT.get();
        if (userInfo == null) return;
        Integer trialCount = userInfo.getTrialCount();
        if (trialCount == null || trialCount <= 0) return;
        CONTEXT.set(new UserInfo(
                userInfo.getUserId(),
                userInfo.getUsername(),
                userInfo.getUserType(),
                trialCount - 1,
                userInfo.getPermissions()
        ));
    }

    /**
     * 清理上下文
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 用户信息内部类
     */
    public static class UserInfo {
        private final Long userId;
        private final String username;
        private final Integer userType;
        private final Integer trialCount;
        private final List<String> permissions;

        public UserInfo(Long userId, String username, Integer userType, Integer trialCount, List<String> permissions) {
            this.userId = userId;
            this.username = username;
            this.userType = userType;
            this.trialCount = trialCount;
            this.permissions = permissions;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public Integer getUserType() {
            return userType;
        }

        public Integer getTrialCount() {
            return trialCount;
        }

        public List<String> permissions() {
            return permissions;
        }

        public List<String> getPermissions() {
            return permissions;
        }
    }
}
