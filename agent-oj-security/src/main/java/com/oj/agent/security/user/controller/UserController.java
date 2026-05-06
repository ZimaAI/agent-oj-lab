package com.oj.agent.security.user.controller;

import com.oj.agent.common.api.Result;
import com.oj.agent.security.annotation.RequirePermission;
import com.oj.agent.security.user.converter.UserConverter;
import com.oj.agent.security.user.model.request.CreateUserRequest;
import com.oj.agent.security.user.model.request.UpdateUserRequest;
import com.oj.agent.security.user.model.request.UpdateUserStatusRequest;
import com.oj.agent.security.user.model.response.CurrentUserResponse;
import com.oj.agent.security.user.model.response.UserPageResponse;
import com.oj.agent.security.user.model.response.UserResponse;
import com.oj.agent.security.user.model.result.CurrentUserResult;
import com.oj.agent.security.user.model.result.UserPageResult;
import com.oj.agent.security.user.model.result.UserResult;
import com.oj.agent.security.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @RequirePermission("user:create")
    public Result<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        UserResult result = userService.createUser(UserConverter.toCreateCommand(request));
        return Result.success(UserConverter.toUserResponse(result));
    }

    @PutMapping("/{id}")
    @RequirePermission("user:update")
    public Result<UserResponse> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        UserResult result = userService.updateUser(UserConverter.toUpdateCommand(id, request));
        return Result.success(UserConverter.toUserResponse(result));
    }

    @GetMapping
    @RequirePermission("user:list")
    public Result<UserPageResponse> listUsers(
            @RequestParam(value = "userType", required = false) Integer userType,
            @RequestParam(value = "isActive", required = false) Integer isActive,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        UserPageResult result = userService.listUsers(UserConverter.toUserListQuery(userType, isActive, page, size));
        return Result.success(UserConverter.toUserPageResponse(result));
    }

    @GetMapping("/{id}")
    @RequirePermission("user:read")
    public Result<UserResponse> getUserById(@PathVariable Long id) {
        UserResult result = userService.getUserById(UserConverter.toUserGetQuery(id));
        return Result.success(UserConverter.toUserResponse(result));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("user:delete")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(UserConverter.toDeleteCommand(id));
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @RequirePermission("user:update")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestBody UpdateUserStatusRequest request) {
        userService.updateUserStatus(UserConverter.toStatusUpdateCommand(id, request));
        return Result.success();
    }

    @GetMapping("/current")
    public Result<CurrentUserResponse> getCurrentUser() {
        CurrentUserResult result = userService.getCurrentUser(UserConverter.toCurrentUserQuery());
        return Result.success(UserConverter.toCurrentUserResponse(result));
    }
}
