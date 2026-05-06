# User Domain Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the first-round user flows in `agent-oj-security` and `agent-oj-admin` so controller, service, and persistence boundaries comply with `AGENTS.md` without changing external API behavior.

**Architecture:** Create dedicated `security.user` and `admin.user` business subdomains, move HTTP boundary objects into `model.request` and `model.response`, move service contracts into `model.command`, `model.query`, and `model.result`, centralize deterministic mapping in `converter` classes, and route admin user capabilities through `security.user.service.UserService` instead of direct mapper/entity access.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, JUnit 5, Mockito, AssertJ, Maven

---

Spec reference: `docs/superpowers/specs/2026-05-01-user-domain-refactor-design.md`

### Task 1: Create Security User Boundary Types And Converter

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/request/CreateUserRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/request/UpdateUserRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/request/UpdateUserStatusRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/query/UserGetQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/query/UserCurrentQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/query/UserListQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/query/UserLoginLogPageQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/command/UserCreateCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/command/UserUpdateCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/command/UserStatusUpdateCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/result/UserResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/result/CurrentUserResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/result/UserPageResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/result/UserLoginLogItemResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/result/UserLoginLogPageResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/response/UserResponse.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/response/CurrentUserResponse.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/response/UserPageResponse.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/converter/UserConverter.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/user/converter/UserConverterTest.java`

- [ ] **Step 1: Create the missing test directory and write the failing converter test**

```java
@Test
void toCreateCommand_shouldCopyRoleIds() {
    CreateUserRequest request = new CreateUserRequest();
    request.setRoleIds(List.of(1L, 2L));

    UserCreateCommand command = UserConverter.toCreateCommand(request);

    assertThat(command.getRoleIds()).containsExactly(1L, 2L);
}
```

- [ ] **Step 2: Run the converter test to verify it fails**

Run: `mvn -pl agent-oj-security -am "-Dtest=UserConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the new boundary types and converter do not exist yet.

- [ ] **Step 3: Add the new security user request/query/command/result/response types and deterministic mappings**

```java
public static UserCreateCommand toCreateCommand(CreateUserRequest request) {
    UserCreateCommand command = new UserCreateCommand();
    if (request != null) {
        command.setUsername(request.getUsername());
        command.setRoleIds(request.getRoleIds());
    }
    return command;
}
```

- [ ] **Step 4: Run the converter test to verify it passes**

Run: `mvn -pl agent-oj-security -am "-Dtest=UserConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 2: Refactor Security User Service And Controller To Command Query Result

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/service/UserService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/service/impl/UserServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/controller/UserController.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/mapper/UserMapper.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/mapper/UserRoleMapper.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/mapper/UserLoginLogMapper.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/entity/User.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/entity/UserRole.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/entity/UserLoginLog.java`
- Modify: `agent-oj-security/src/main/java/com/oj/agent/security/service/AuthService.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/user/service/UserServiceTest.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/user/controller/UserControllerTest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/controller/UserController.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/service/UserService.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/mapper/UserMapper.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/mapper/UserRoleMapper.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/mapper/UserLoginLogMapper.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/model/User.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/model/UserRole.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/model/UserLoginLog.java`

- [ ] **Step 1: Write a failing service test for the new security service contract**

```java
@Test
void pageLoginLogs_shouldReturnResultRecords() {
    UserLoginLogPageQuery query = new UserLoginLogPageQuery();
    query.setCurrent(1L);
    query.setSize(10L);

    UserLoginLogPageResult result = userService.pageLoginLogs(query);

    assertThat(result).isNotNull();
}
```

- [ ] **Step 2: Write a failing controller test for request/response mapping**

```java
@Test
void updateUserStatus_shouldMapRequestToCommand() {
    UpdateUserStatusRequest request = new UpdateUserStatusRequest();
    request.setIsActive(0);

    controller.updateUserStatus(12L, request);

    verify(userService).updateUserStatus(any(UserStatusUpdateCommand.class));
}
```

- [ ] **Step 3: Run the focused security tests to verify they fail**

Run: `mvn -pl agent-oj-security -am "-Dtest=UserServiceTest,UserControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the security user service still uses HTTP DTOs and does not expose login-log paging results.

- [ ] **Step 4: Implement the new security user interface plus `UserServiceImpl`, move the controller/mapper/entity classes into the `security.user` subdomain, update `AuthService` imports, and delete the replaced root-package user classes**

- [ ] **Step 5: Re-run the focused security tests**

Run: `mvn -pl agent-oj-security -am "-Dtest=UserConverterTest,UserServiceTest,UserControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 3: Create Admin User Boundary Types And Converter

**Files:**
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/request/AdminUserStatusUpdateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/query/AdminUserPageQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/query/AdminLoginLogPageQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/command/AdminUserDisableCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/result/AdminUserItemResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/result/AdminUserPageResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/result/AdminLoginLogItemResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/result/AdminLoginLogPageResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/response/AdminUserItemResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/response/AdminUserPageResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/response/AdminLoginLogItemResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/model/response/AdminLoginLogPageResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/converter/AdminUserConverter.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/user/converter/AdminUserConverterTest.java`

- [ ] **Step 1: Create the missing test directory and write the failing admin converter test**

```java
@Test
void toDisableCommand_shouldKeepDisabledCompatibility() {
    AdminUserStatusUpdateRequest request = new AdminUserStatusUpdateRequest();
    request.setStatus("DISABLED");

    AdminUserDisableCommand command = AdminUserConverter.toDisableCommand(9L, request);

    assertThat(command.getUserId()).isEqualTo(9L);
}
```

- [ ] **Step 2: Run the admin converter test to verify it fails**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminUserConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the new admin user types do not exist yet.

- [ ] **Step 3: Add the admin user query/command/result/response types and converter while preserving the `status="DISABLED"` request contract**

- [ ] **Step 4: Run the admin converter test to verify it passes**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminUserConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 4: Refactor Admin User Service And Controller To Delegate Through Security User Service

**Files:**
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/service/AdminUserManagementService.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/service/impl/AdminUserManagementServiceImpl.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/controller/AdminUserManagementController.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/user/service/AdminUserManagementServiceTest.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/user/controller/AdminUserManagementControllerTest.java`
- Delete after migration: `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminUserManagementController.java`
- Delete after migration: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminUserManagementService.java`
- Delete after migration: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminUserManagementServiceImpl.java`
- Delete after migration: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminUserStatusUpdateRequest.java`
- Delete after migration: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminUserItemResponse.java`
- Delete after migration: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminUserPageResponse.java`
- Delete after migration: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminLoginLogItemResponse.java`
- Delete after migration: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminLoginLogPageResponse.java`

- [ ] **Step 1: Write a failing admin service test for disable delegation**

```java
@Test
void updateUserStatus_shouldDelegateDisabledCommandToSecurityUserService() {
    AdminUserDisableCommand command = new AdminUserDisableCommand();
    command.setUserId(11L);

    adminUserManagementService.updateUserStatus(command);

    verify(userService).updateUserStatus(any(UserStatusUpdateCommand.class));
}
```

- [ ] **Step 2: Write a failing admin controller test for compatibility**

```java
@Test
void updateUserStatus_shouldAcceptDisabledStatusRequest() {
    AdminUserStatusUpdateRequest request = new AdminUserStatusUpdateRequest();
    request.setStatus("DISABLED");

    controller.updateUserStatus(11L, request);

    verify(adminUserManagementService).updateUserStatus(any(AdminUserDisableCommand.class));
}
```

- [ ] **Step 3: Run the focused admin tests to verify they fail**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminUserManagementServiceTest,AdminUserManagementControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because admin still uses direct mapper/entity access and old service contracts.

- [ ] **Step 4: Implement the new admin service/controller under the `admin.user` subdomain, delegate disable and paging behavior through `security.user.service.UserService`, and delete the replaced root-package admin user classes**

- [ ] **Step 5: Re-run the focused admin tests**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminUserConverterTest,AdminUserManagementServiceTest,AdminUserManagementControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 5: Run First-Round Verification

**Files:**
- Verify only; no planned file creation

- [ ] **Step 1: Run all focused first-round tests**

Run: `mvn -pl agent-oj-security,agent-oj-admin -am "-Dtest=UserConverterTest,UserServiceTest,UserControllerTest,AdminUserConverterTest,AdminUserManagementServiceTest,AdminUserManagementControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

- [ ] **Step 2: Run reactor tests for the changed modules**

Run: `mvn -pl agent-oj-security,agent-oj-admin -am test`
Expected: PASS
