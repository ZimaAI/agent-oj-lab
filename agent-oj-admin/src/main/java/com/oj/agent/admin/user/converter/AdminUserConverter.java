package com.oj.agent.admin.user.converter;

import com.oj.agent.admin.user.model.command.AdminUserDisableCommand;
import com.oj.agent.admin.user.model.query.AdminLoginLogPageQuery;
import com.oj.agent.admin.user.model.query.AdminUserPageQuery;
import com.oj.agent.admin.user.model.request.AdminUserStatusUpdateRequest;
import com.oj.agent.admin.user.model.response.AdminLoginLogItemResponse;
import com.oj.agent.admin.user.model.response.AdminLoginLogPageResponse;
import com.oj.agent.admin.user.model.response.AdminUserItemResponse;
import com.oj.agent.admin.user.model.response.AdminUserPageResponse;
import com.oj.agent.admin.user.model.result.AdminLoginLogItemResult;
import com.oj.agent.admin.user.model.result.AdminLoginLogPageResult;
import com.oj.agent.admin.user.model.result.AdminUserItemResult;
import com.oj.agent.admin.user.model.result.AdminUserPageResult;
import com.oj.agent.security.user.model.command.UserStatusUpdateCommand;
import com.oj.agent.security.user.model.query.UserListQuery;
import com.oj.agent.security.user.model.query.UserLoginLogPageQuery;
import com.oj.agent.security.user.model.result.UserLoginLogItemResult;
import com.oj.agent.security.user.model.result.UserLoginLogPageResult;
import com.oj.agent.security.user.model.result.UserPageResult;
import com.oj.agent.security.user.model.result.UserResult;

import java.util.Collections;
import java.util.List;

public final class AdminUserConverter {

    private static final int DISABLED = 0;

    private AdminUserConverter() {
    }

    public static AdminUserDisableCommand toDisableCommand(Long userId, AdminUserStatusUpdateRequest request) {
        AdminUserDisableCommand command = new AdminUserDisableCommand();
        command.setUserId(userId);
        return command;
    }

    public static AdminUserPageQuery toUserPageQuery(Long current, Long size) {
        AdminUserPageQuery query = new AdminUserPageQuery();
        query.setCurrent(current);
        query.setSize(size);
        return query;
    }

    public static AdminLoginLogPageQuery toLoginLogPageQuery(Long current, Long size) {
        AdminLoginLogPageQuery query = new AdminLoginLogPageQuery();
        query.setCurrent(current);
        query.setSize(size);
        return query;
    }

    public static UserStatusUpdateCommand toSecurityCommand(AdminUserDisableCommand command) {
        UserStatusUpdateCommand securityCommand = new UserStatusUpdateCommand();
        securityCommand.setUserId(command.getUserId());
        securityCommand.setIsActive(DISABLED);
        return securityCommand;
    }

    public static UserListQuery toSecurityQuery(AdminUserPageQuery query) {
        UserListQuery securityQuery = new UserListQuery();
        if (query != null) {
            securityQuery.setCurrent(query.getCurrent());
            securityQuery.setSize(query.getSize());
        }
        return securityQuery;
    }

    public static UserLoginLogPageQuery toSecurityQuery(AdminLoginLogPageQuery query) {
        UserLoginLogPageQuery securityQuery = new UserLoginLogPageQuery();
        if (query != null) {
            securityQuery.setCurrent(query.getCurrent());
            securityQuery.setSize(query.getSize());
        }
        return securityQuery;
    }

    public static AdminUserPageResult toResult(UserPageResult result) {
        AdminUserPageResult adminResult = new AdminUserPageResult();
        if (result == null) {
            adminResult.setRecords(Collections.emptyList());
            return adminResult;
        }
        adminResult.setCurrent(result.getCurrent());
        adminResult.setSize(result.getSize());
        adminResult.setTotal(result.getTotal());
        adminResult.setRecords(toUserItemResults(result.getRecords()));
        return adminResult;
    }

    public static AdminLoginLogPageResult toResult(UserLoginLogPageResult result) {
        AdminLoginLogPageResult adminResult = new AdminLoginLogPageResult();
        if (result == null) {
            adminResult.setRecords(Collections.emptyList());
            return adminResult;
        }
        adminResult.setCurrent(result.getCurrent());
        adminResult.setSize(result.getSize());
        adminResult.setTotal(result.getTotal());
        adminResult.setRecords(toLoginLogItemResults(result.getRecords()));
        return adminResult;
    }

    public static AdminUserPageResponse toUserPageResponse(AdminUserPageResult result) {
        if (result == null) {
            return null;
        }
        return new AdminUserPageResponse(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                toUserItemResponses(result.getRecords())
        );
    }

    public static AdminLoginLogPageResponse toLoginLogPageResponse(AdminLoginLogPageResult result) {
        if (result == null) {
            return null;
        }
        return new AdminLoginLogPageResponse(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                toLoginLogItemResponses(result.getRecords())
        );
    }

    private static List<AdminUserItemResult> toUserItemResults(List<UserResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(AdminUserConverter::toUserItemResult).toList();
    }

    private static AdminUserItemResult toUserItemResult(UserResult result) {
        AdminUserItemResult itemResult = new AdminUserItemResult();
        itemResult.setId(result.getId());
        itemResult.setUsername(result.getUsername());
        itemResult.setUserType(result.getUserType());
        itemResult.setNeedPassword(result.getNeedPassword());
        itemResult.setIsActive(result.getIsActive());
        itemResult.setValidFrom(result.getValidFrom());
        itemResult.setValidUntil(result.getValidUntil());
        itemResult.setTrialCount(result.getTrialCount());
        itemResult.setCreateTime(result.getCreateTime());
        itemResult.setUpdateTime(result.getUpdateTime());
        return itemResult;
    }

    private static List<AdminLoginLogItemResult> toLoginLogItemResults(List<UserLoginLogItemResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(AdminUserConverter::toLoginLogItemResult).toList();
    }

    private static AdminLoginLogItemResult toLoginLogItemResult(UserLoginLogItemResult result) {
        AdminLoginLogItemResult itemResult = new AdminLoginLogItemResult();
        itemResult.setId(result.getId());
        itemResult.setUserId(result.getUserId());
        itemResult.setUsername(result.getUsername());
        itemResult.setLoginIp(result.getLoginIp());
        itemResult.setLoginTime(result.getLoginTime());
        return itemResult;
    }

    private static List<AdminUserItemResponse> toUserItemResponses(List<AdminUserItemResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream()
                .map(result -> new AdminUserItemResponse(
                        result.getId(),
                        result.getUsername(),
                        result.getUserType(),
                        result.getNeedPassword(),
                        result.getIsActive(),
                        result.getValidFrom(),
                        result.getValidUntil(),
                        result.getTrialCount(),
                        result.getCreateTime(),
                        result.getUpdateTime()
                ))
                .toList();
    }

    private static List<AdminLoginLogItemResponse> toLoginLogItemResponses(List<AdminLoginLogItemResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream()
                .map(result -> new AdminLoginLogItemResponse(
                        result.getId(),
                        result.getUserId(),
                        result.getUsername(),
                        result.getLoginIp(),
                        result.getLoginTime()
                ))
                .toList();
    }
}
