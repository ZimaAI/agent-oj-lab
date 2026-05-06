# Security Auth And Maintenance Domain Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the remaining root-level security auth and table-maintenance flows into dedicated domains, introduce user-domain support seams for auth and trial-count runtime behavior, and keep current API behavior compatible with `AGENTS.md`.

**Architecture:** Create dedicated `security.auth` and `security.maintenance` subdomains; move HTTP models into `model.request` and `model.response`; move service contracts into `model.command`, `model.query`, and `model.result`; centralize deterministic mapping in converters; add focused user-domain support services so auth and trial-count runtime code no longer depend on `security.user` persistence internals.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, JUnit 5, Mockito, AssertJ, Maven

---

Spec reference: `docs/superpowers/specs/2026-05-01-security-auth-maintenance-domain-refactor-design.md`

Execution note:

- Task 3 must complete before Task 4 because auth refactoring depends on the new `UserAuthService` and `UserTrialService` seams.
- Task 4 and Task 5 must not be implemented in parallel because both delete and replace files in the shared legacy root `security.controller`, `security.service`, and `security.dto` packages.

### Task 1: Create Auth Boundary Types And Converter

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/auth/model/request/LoginRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/auth/model/request/RefreshTokenRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/auth/model/response/LoginResponse.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/auth/model/command/LoginCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/auth/model/command/RefreshTokenCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/auth/model/result/AuthResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/auth/converter/AuthConverter.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/auth/converter/AuthConverterTest.java`

- [ ] **Step 1: Write the failing auth converter test**

```java
@Test
void toLoginCommand_shouldCopyCredentialsAndClientIp() {
    LoginRequest request = new LoginRequest();
    request.setUsername("trial-user");
    request.setPassword("secret");

    LoginCommand command = AuthConverter.toLoginCommand(request, "127.0.0.1");

    assertThat(command.getUsername()).isEqualTo("trial-user");
    assertThat(command.getPassword()).isEqualTo("secret");
    assertThat(command.getClientIp()).isEqualTo("127.0.0.1");
}
```

- [ ] **Step 2: Run the converter test to verify it fails**

Run: `mvn -pl agent-oj-security -am "-Dtest=AuthConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the new auth boundary types and converter do not exist yet.

- [ ] **Step 3: Add the auth request, response, command, result, and converter types**

```java
public static LoginCommand toLoginCommand(LoginRequest request, String clientIp) {
    LoginCommand command = new LoginCommand();
    if (request != null) {
        command.setUsername(request.getUsername());
        command.setPassword(request.getPassword());
    }
    command.setClientIp(clientIp);
    return command;
}
```

- [ ] **Step 4: Run the converter test to verify it passes**

Run: `mvn -pl agent-oj-security -am "-Dtest=AuthConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 2: Create Maintenance Boundary Types And Converter

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/model/request/TableClearRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/model/response/TableClearResponse.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/model/response/TableOptionResponse.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/model/query/TableListQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/model/command/TableClearCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/model/result/TableClearResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/model/result/TableOptionResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/converter/TableMaintenanceConverter.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/maintenance/converter/TableMaintenanceConverterTest.java`

- [ ] **Step 1: Write the failing maintenance converter test**

```java
@Test
void toClearCommand_shouldCopyTableNames() {
    TableClearRequest request = new TableClearRequest();
    request.setTableNames(List.of("user", "role"));

    TableClearCommand command = TableMaintenanceConverter.toClearCommand(request);

    assertThat(command.getTableNames()).containsExactly("user", "role");
}
```

- [ ] **Step 2: Run the converter test to verify it fails**

Run: `mvn -pl agent-oj-security -am "-Dtest=TableMaintenanceConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the new maintenance boundary types and converter do not exist yet.

- [ ] **Step 3: Add the maintenance request, response, command, query, result, and converter types**

```java
public static TableClearCommand toClearCommand(TableClearRequest request) {
    TableClearCommand command = new TableClearCommand();
    if (request != null) {
        command.setTableNames(request.getTableNames());
    }
    return command;
}
```

- [ ] **Step 4: Run the converter test to verify it passes**

Run: `mvn -pl agent-oj-security -am "-Dtest=TableMaintenanceConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 3: Add User-Domain Auth And Trial Support Seams

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/service/UserAuthService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/service/UserTrialService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/service/impl/UserAuthServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/service/impl/UserTrialServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/query/UserAuthByUsernameQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/query/UserAuthByIdQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/query/UserPermissionQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/command/UserUsernameOnlyCreateCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/command/UserLoginLogCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/command/UserTrialCountDecrementCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/result/UserAuthResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/result/UserPermissionResult.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/user/service/UserAuthServiceTest.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/user/service/UserTrialServiceTest.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/filter/TrialCountFilterTest.java`
- Modify: `agent-oj-security/src/main/java/com/oj/agent/security/filter/TrialCountFilter.java`

- [ ] **Step 1: Write failing user support service tests**

```java
@Test
void createUsernameOnlyUser_shouldReloadUserWhenInsertHitsDuplicateKey() {
    UserUsernameOnlyCreateCommand command = new UserUsernameOnlyCreateCommand();
    command.setUsername("trial-user");

    UserAuthResult result = userAuthService.createUsernameOnlyUser(command);

    assertThat(result.getUsername()).isEqualTo("trial-user");
    assertThat(result.getNeedPassword()).isEqualTo(0);
}

@Test
void decrementTrialCount_shouldUpdateOnlyPositiveTrialCounts() {
    UserTrialCountDecrementCommand command = new UserTrialCountDecrementCommand();
    command.setUserId(12L);

    userTrialService.decrementTrialCount(command);

    verify(userMapper).update(any(), any());
}
```

- [ ] **Step 2: Write a failing filter regression test**

```java
@Test
void doFilterInternal_shouldDelegatePersistentDecrementThroughUserTrialService() throws Exception {
    when(request.getRequestURI()).thenReturn("/api/code/submit/stream");
    UserContext.setUser(9L, "trial", 1, 2, List.of());

    filter.doFilterInternal(request, response, chain);

    verify(userTrialService).decrementTrialCount(any(UserTrialCountDecrementCommand.class));
}
```

- [ ] **Step 3: Run the focused user support and filter tests to verify they fail**

Run: `mvn -pl agent-oj-security -am "-Dtest=UserAuthServiceTest,UserTrialServiceTest,TrialCountFilterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the support seams and filter delegation do not exist yet.

- [ ] **Step 4: Implement `UserAuthService`, `UserTrialService`, their public models, and update `TrialCountFilter` to depend on `UserTrialService` instead of `UserMapper`**

- [ ] **Step 5: Re-run the focused user support and filter tests**

Run: `mvn -pl agent-oj-security -am "-Dtest=UserAuthServiceTest,UserTrialServiceTest,TrialCountFilterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 4: Refactor Auth Domain

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/auth/controller/AuthController.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/auth/service/AuthService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/auth/service/impl/AuthServiceImpl.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/auth/service/AuthServiceTest.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/auth/controller/AuthControllerTest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/controller/AuthController.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/service/AuthService.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/LoginRequest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/LoginResponse.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/RefreshTokenRequest.java`

- [ ] **Step 1: Write failing auth service tests for login, refresh, auto-create, and validation behavior**

```java
@Test
void login_shouldAutoCreateUsernameOnlyUserWhenPasswordIsBlankAndUserMissing() {
    LoginCommand command = new LoginCommand();
    command.setUsername("trial-user");
    command.setClientIp("127.0.0.1");

    AuthResult result = authService.login(command);

    assertThat(result.getUserId()).isNotNull();
    assertThat(result.getPermissions()).isNotNull();
}

@Test
void refreshToken_shouldFailWhenRefreshTokenIsInvalid() {
    RefreshTokenCommand command = new RefreshTokenCommand();
    command.setRefreshToken("bad-token");

    assertThatThrownBy(() -> authService.refreshToken(command))
            .isInstanceOf(AuthenticationException.class);
}
```

- [ ] **Step 2: Write a failing auth controller test for route compatibility and IP extraction**

```java
@Test
void login_shouldExtractForwardedIpAndReturnCompatiblePayload() {
    LoginRequest request = new LoginRequest();
    request.setUsername("trial-user");
    when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
    when(authService.login(any(LoginCommand.class))).thenReturn(buildAuthResult());

    Result<LoginResponse> result = controller.login(request, httpRequest);

    verify(authService).login(argThat(command -> "10.0.0.1".equals(command.getClientIp())));
    assertThat(result.getData().getUserInfo().getUsername()).isEqualTo("trial-user");
}
```

- [ ] **Step 3: Run the focused auth tests to verify they fail**

Run: `mvn -pl agent-oj-security -am "-Dtest=AuthConverterTest,AuthServiceTest,AuthControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because auth still lives in root packages and still depends on HTTP DTOs plus user persistence internals.

- [ ] **Step 4: Implement the auth domain, switch auth orchestration to `UserAuthService`, preserve the current login and refresh behavior, and delete the replaced root auth classes**

- [ ] **Step 5: Re-run the focused auth tests**

Run: `mvn -pl agent-oj-security -am "-Dtest=AuthConverterTest,AuthServiceTest,AuthControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 5: Refactor Maintenance Domain

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/controller/TableMaintenanceController.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/service/TableMaintenanceService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/service/impl/TableMaintenanceServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/maintenance/provider/TableMaintenanceProvider.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/maintenance/service/TableMaintenanceServiceTest.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/maintenance/controller/TableMaintenanceControllerTest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/controller/TableMaintenanceController.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/service/TableMaintenanceService.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/provider/TableMaintenanceProvider.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/TableClearRequest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/TableClearResponse.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/TableOptionResponse.java`

- [ ] **Step 1: Write failing maintenance service tests for provider absence, normalization, whitelist validation, and de-duplication**

```java
@Test
void clearTables_shouldRejectNamesOutsideEnabledWhitelist() {
    TableClearCommand command = new TableClearCommand();
    command.setTableNames(List.of("user", "not_allowed"));

    assertThatThrownBy(() -> tableMaintenanceService.clearTables(command))
            .isInstanceOf(ValidationException.class);
}

@Test
void listTables_shouldReturnProviderResults() {
    when(provider.listTables()).thenReturn(List.of(new TableOptionResult()));

    assertThat(tableMaintenanceService.listTables(new TableListQuery())).hasSize(1);
}
```

- [ ] **Step 2: Write a failing maintenance controller test for route and permission compatibility**

```java
@Test
void clearTables_shouldKeepTableClearPermissionCode() throws Exception {
    Method method = TableMaintenanceController.class.getMethod("clearTables", TableClearRequest.class);
    RequirePermission annotation = method.getAnnotation(RequirePermission.class);

    assertThat(annotation.value()).containsExactly("table:clear");
}
```

- [ ] **Step 3: Run the focused maintenance tests to verify they fail**

Run: `mvn -pl agent-oj-security -am "-Dtest=TableMaintenanceConverterTest,TableMaintenanceServiceTest,TableMaintenanceControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because maintenance still uses root DTOs and service classes.

- [ ] **Step 4: Implement the maintenance domain, move the provider contract into the new package, preserve current validation behavior, and delete the replaced root maintenance classes**

- [ ] **Step 5: Re-run the focused maintenance tests**

Run: `mvn -pl agent-oj-security -am "-Dtest=TableMaintenanceConverterTest,TableMaintenanceServiceTest,TableMaintenanceControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 6: Run Third-Round Verification

**Files:**
- Verify only; no planned file creation

- [ ] **Step 1: Run all focused third-round tests**

Run: `mvn -pl agent-oj-security -am "-Dtest=AuthConverterTest,AuthServiceTest,AuthControllerTest,TableMaintenanceConverterTest,TableMaintenanceServiceTest,TableMaintenanceControllerTest,UserAuthServiceTest,UserTrialServiceTest,TrialCountFilterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

- [ ] **Step 2: Run module tests for the changed security module**

Run: `mvn -pl agent-oj-security -am test`
Expected: PASS

- [ ] **Step 3: Run the previously completed security and admin focused tests as regression coverage**

Run: `mvn -pl agent-oj-security,agent-oj-admin -am "-Dtest=UserConverterTest,UserServiceTest,UserControllerTest,RoleConverterTest,RoleServiceTest,RoleControllerTest,PermissionConverterTest,PermissionServiceTest,PermissionControllerTest,EndpointServiceTest,EndpointControllerTest,AdminUserConverterTest,AdminUserManagementServiceTest,AdminUserManagementControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

- [ ] **Step 4: Verify preserved routes and permission codes in source after migration**

Run: `rg -n '@RequestMapping\\(\"/api/auth\"\\)|@PostMapping\\(\"/login\"\\)|@PostMapping\\(\"/refresh\"\\)|@RequestMapping\\(\"/api/admin/table-maintenance\"\\)|@GetMapping\\(\"/tables\"\\)|@PostMapping\\(\"/clear\"\\)|@RequirePermission\\(\"table:clear\"\\)' agent-oj-security/src/main/java/com/oj/agent/security/auth/controller/AuthController.java agent-oj-security/src/main/java/com/oj/agent/security/maintenance/controller/TableMaintenanceController.java`
Expected: matches showing `/api/auth`, `/login`, `/refresh`, `/api/admin/table-maintenance`, `/tables`, `/clear`, and `table:clear`
