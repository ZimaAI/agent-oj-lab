package com.oj.agent.security.user.converter;

import com.oj.agent.security.user.model.command.UserCreateCommand;
import com.oj.agent.security.user.model.command.UserDeleteCommand;
import com.oj.agent.security.user.model.command.UserStatusUpdateCommand;
import com.oj.agent.security.user.model.command.UserUpdateCommand;
import com.oj.agent.security.user.model.query.UserCurrentQuery;
import com.oj.agent.security.user.model.query.UserGetQuery;
import com.oj.agent.security.user.model.query.UserListQuery;
import com.oj.agent.security.user.model.query.UserLoginLogPageQuery;
import com.oj.agent.security.user.model.request.CreateUserRequest;
import com.oj.agent.security.user.model.request.UpdateUserRequest;
import com.oj.agent.security.user.model.request.UpdateUserStatusRequest;
import com.oj.agent.security.user.model.response.CurrentUserResponse;
import com.oj.agent.security.user.model.response.UserPageResponse;
import com.oj.agent.security.user.model.response.UserResponse;
import com.oj.agent.security.user.model.result.CurrentUserResult;
import com.oj.agent.security.user.model.result.UserLoginLogItemResult;
import com.oj.agent.security.user.model.result.UserLoginLogPageResult;
import com.oj.agent.security.user.model.result.UserPageResult;
import com.oj.agent.security.user.model.result.UserResult;

import java.util.Collections;
import java.util.List;

public final class UserConverter {

    private UserConverter() {
    }

    public static UserCreateCommand toCreateCommand(CreateUserRequest request) {
        UserCreateCommand command = new UserCreateCommand();
        if (request != null) {
            command.setUsername(request.getUsername());
            command.setPassword(request.getPassword());
            command.setNeedPassword(request.getNeedPassword());
            command.setValidFrom(request.getValidFrom());
            command.setValidUntil(request.getValidUntil());
            command.setTrialCount(request.getTrialCount());
            command.setRoleIds(request.getRoleIds());
        }
        return command;
    }

    public static UserUpdateCommand toUpdateCommand(Long userId, UpdateUserRequest request) {
        UserUpdateCommand command = new UserUpdateCommand();
        command.setUserId(userId);
        if (request != null) {
            command.setPassword(request.getPassword());
            command.setNeedPassword(request.getNeedPassword());
            command.setValidFrom(request.getValidFrom());
            command.setValidUntil(request.getValidUntil());
            command.setTrialCount(request.getTrialCount());
            command.setRoleIds(request.getRoleIds());
        }
        return command;
    }

    public static UserStatusUpdateCommand toStatusUpdateCommand(Long userId, UpdateUserStatusRequest request) {
        UserStatusUpdateCommand command = new UserStatusUpdateCommand();
        command.setUserId(userId);
        if (request != null) {
            command.setIsActive(request.getIsActive());
        }
        return command;
    }

    public static UserDeleteCommand toDeleteCommand(Long userId) {
        UserDeleteCommand command = new UserDeleteCommand();
        command.setUserId(userId);
        return command;
    }

    public static UserGetQuery toUserGetQuery(Long userId) {
        UserGetQuery query = new UserGetQuery();
        query.setUserId(userId);
        return query;
    }

    public static UserCurrentQuery toCurrentUserQuery() {
        return new UserCurrentQuery();
    }

    public static UserListQuery toUserListQuery(Integer userType, Integer isActive, Integer page, Integer size) {
        UserListQuery query = new UserListQuery();
        query.setUserType(userType);
        query.setIsActive(isActive);
        query.setCurrent(page == null || page <= 0 ? 1L : page.longValue());
        query.setSize(size == null || size <= 0 ? 10L : size.longValue());
        return query;
    }

    public static UserLoginLogPageQuery toLoginLogPageQuery(Long current, Long size) {
        UserLoginLogPageQuery query = new UserLoginLogPageQuery();
        query.setCurrent(current == null || current <= 0 ? 1L : current);
        query.setSize(size == null || size <= 0 ? 10L : Math.min(size, 100L));
        return query;
    }

    public static UserResponse toUserResponse(UserResult result) {
        if (result == null) {
            return null;
        }
        UserResponse response = new UserResponse();
        response.setId(result.getId());
        response.setUsername(result.getUsername());
        response.setUserType(result.getUserType());
        response.setNeedPassword(result.getNeedPassword());
        response.setIsActive(result.getIsActive());
        response.setValidFrom(result.getValidFrom());
        response.setValidUntil(result.getValidUntil());
        response.setTrialCount(result.getTrialCount());
        response.setRoles(toRoleInfoResponses(result.getRoles()));
        response.setCreateTime(result.getCreateTime());
        return response;
    }

    public static CurrentUserResponse toCurrentUserResponse(CurrentUserResult result) {
        if (result == null) {
            return null;
        }
        CurrentUserResponse response = new CurrentUserResponse();
        response.setId(result.getId());
        response.setUsername(result.getUsername());
        response.setUserType(result.getUserType());
        response.setIsActive(result.getIsActive());
        response.setValidFrom(result.getValidFrom());
        response.setValidUntil(result.getValidUntil());
        response.setTrialCount(result.getTrialCount());
        return response;
    }

    public static UserPageResponse toUserPageResponse(UserPageResult result) {
        UserPageResponse response = new UserPageResponse();
        if (result != null) {
            response.setCurrent(result.getCurrent());
            response.setSize(result.getSize());
            response.setTotal(result.getTotal());
            response.setRecords(toUserResponses(result.getRecords()));
        }
        return response;
    }

    public static UserLoginLogPageResult toLoginLogPageResult(UserLoginLogPageResult result) {
        UserLoginLogPageResult converted = new UserLoginLogPageResult();
        if (result != null) {
            converted.setCurrent(result.getCurrent());
            converted.setSize(result.getSize());
            converted.setTotal(result.getTotal());
            converted.setRecords(result.getRecords() == null ? Collections.emptyList() : List.copyOf(result.getRecords()));
        }
        return converted;
    }

    private static List<UserResponse> toUserResponses(List<UserResult> records) {
        if (records == null) {
            return Collections.emptyList();
        }
        return records.stream()
                .map(UserConverter::toUserResponse)
                .toList();
    }

    private static List<UserResponse.RoleInfo> toRoleInfoResponses(List<UserResult.RoleInfoResult> roles) {
        if (roles == null) {
            return Collections.emptyList();
        }
        return roles.stream()
                .map(role -> new UserResponse.RoleInfo(role.getRoleId(), role.getRoleName(), role.getRoleCode()))
                .toList();
    }
}
