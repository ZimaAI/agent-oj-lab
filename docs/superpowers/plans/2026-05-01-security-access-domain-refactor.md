# Security Access Domain Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the `agent-oj-security` role, permission, and endpoint-scan flows so package structure, service contracts, and cross-domain boundaries comply with `AGENTS.md` while preserving current external API behavior.

**Architecture:** Create dedicated `security.role`, `security.permission`, and `security.endpoint` subdomains; move HTTP models into `model.request` and `model.response`; move service contracts into `model.command`, `model.query`, and `model.result`; centralize deterministic mapping in converters; introduce small read-only support services so user, role, and permission collaboration does not rely on direct cross-domain entity imports.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, JUnit 5, Mockito, AssertJ, Maven

---

Spec reference: `docs/superpowers/specs/2026-05-01-security-access-domain-refactor-design.md`

### Task 1: Create Permission Boundary Types And Converter

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/request/CreatePermissionRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/request/UpdatePermissionRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/command/CreatePermissionCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/command/UpdatePermissionCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/command/DeletePermissionCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/query/PermissionGetQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/query/PermissionTreeQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/query/PermissionIdsQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/result/PermissionResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/result/PermissionSummaryResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/response/PermissionResponse.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/converter/PermissionConverter.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/permission/converter/PermissionConverterTest.java`

- [ ] **Step 1: Write the failing permission converter test**

```java
@Test
void toCreateCommand_shouldCopyParentIdAndDescription() {
    CreatePermissionRequest request = new CreatePermissionRequest();
    request.setPermissionCode("question:read");
    request.setParentId(9L);
    request.setDescription("read questions");

    CreatePermissionCommand command = PermissionConverter.toCreateCommand(request);

    assertThat(command.getPermissionCode()).isEqualTo("question:read");
    assertThat(command.getParentId()).isEqualTo(9L);
    assertThat(command.getDescription()).isEqualTo("read questions");
}
```

- [ ] **Step 2: Run the converter test to verify it fails**

Run: `mvn -pl agent-oj-security -am "-Dtest=PermissionConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the new permission boundary types and converter do not exist yet.

- [ ] **Step 3: Add the permission request, command, query, result, response, and converter types**

```java
public static CreatePermissionCommand toCreateCommand(CreatePermissionRequest request) {
    CreatePermissionCommand command = new CreatePermissionCommand();
    if (request != null) {
        command.setPermissionCode(request.getPermissionCode());
        command.setPermissionName(request.getPermissionName());
        command.setPermissionType(request.getPermissionType());
        command.setParentId(request.getParentId());
        command.setDescription(request.getDescription());
    }
    return command;
}
```

- [ ] **Step 4: Run the converter test to verify it passes**

Run: `mvn -pl agent-oj-security -am "-Dtest=PermissionConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 2: Create Role Boundary Types And Converter

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/request/CreateRoleRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/request/UpdateRoleRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/request/BindRolePermissionsRequest.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/command/CreateRoleCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/command/UpdateRoleCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/command/DeleteRoleCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/command/BindRolePermissionsCommand.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/query/RoleGetQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/query/RolePageQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/query/RoleIdsQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/query/RolePermissionUsageQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/result/RoleResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/result/RolePageResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/result/RoleSummaryResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/result/RolePermissionUsageResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/response/RoleResponse.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/response/RolePageResponse.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/converter/RoleConverter.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/role/converter/RoleConverterTest.java`

- [ ] **Step 1: Write the failing role converter test**

```java
@Test
void toBindPermissionsCommand_shouldCopyRoleIdAndPermissionIds() {
    BindRolePermissionsRequest request = new BindRolePermissionsRequest();
    request.setPermissionIds(List.of(3L, 4L));

    BindRolePermissionsCommand command = RoleConverter.toBindPermissionsCommand(8L, request);

    assertThat(command.getRoleId()).isEqualTo(8L);
    assertThat(command.getPermissionIds()).containsExactly(3L, 4L);
}
```

- [ ] **Step 2: Run the converter test to verify it fails**

Run: `mvn -pl agent-oj-security -am "-Dtest=RoleConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the role boundary types and converter do not exist yet.

- [ ] **Step 3: Add the role request, command, query, result, response, and converter types**

```java
public static BindRolePermissionsCommand toBindPermissionsCommand(Long roleId, BindRolePermissionsRequest request) {
    BindRolePermissionsCommand command = new BindRolePermissionsCommand();
    command.setRoleId(roleId);
    command.setPermissionIds(request == null ? List.of() : request.getPermissionIds());
    return command;
}
```

- [ ] **Step 4: Run the converter test to verify it passes**

Run: `mvn -pl agent-oj-security -am "-Dtest=RoleConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 3: Refactor Permission Domain And Introduce The Role-Permission Usage Seam

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/controller/PermissionController.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/service/PermissionService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/service/PermissionLookupService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/service/impl/PermissionServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/service/impl/PermissionLookupServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/mapper/PermissionMapper.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/permission/model/entity/Permission.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/service/RolePermissionReferenceService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/service/impl/RolePermissionReferenceServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/mapper/RolePermissionMapper.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/entity/RolePermission.java`
- Modify: `agent-oj-security/src/main/java/com/oj/agent/security/service/RoleService.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/permission/service/PermissionServiceTest.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/permission/controller/PermissionControllerTest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/controller/PermissionController.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/service/PermissionService.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/mapper/PermissionMapper.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/model/Permission.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/CreatePermissionRequest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/UpdatePermissionRequest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/PermissionResponse.java`

- [ ] **Step 1: Write failing permission service tests for duplicate-code handling, delete guard, detail mapping, and tree behavior**

```java
@Test
void deletePermission_shouldFailWhenStillBoundToRoles() {
    DeletePermissionCommand command = new DeletePermissionCommand();
    command.setPermissionId(5L);

    assertThatThrownBy(() -> permissionService.deletePermission(command))
            .isInstanceOf(AuthorizationException.class);
}

@Test
void getPermissionById_shouldReturnCompatibleDetailFields() {
    PermissionGetQuery query = new PermissionGetQuery();
    query.setPermissionId(9L);

    PermissionResult result = permissionService.getPermissionById(query);

    assertThat(result.getId()).isEqualTo(9L);
    assertThat(result.getPermissionCode()).isNotBlank();
}
```

- [ ] **Step 2: Write a failing permission controller test for request and response mapping**

```java
@Test
void getPermissionTree_shouldMapServiceResultsToCompatibleTreeResponse() {
    when(permissionService.getPermissionTree(any(PermissionTreeQuery.class)))
            .thenReturn(List.of(new PermissionResult()));

    Result<List<PermissionResponse>> result = controller.getPermissionTree();

    assertThat(result.getData()).hasSize(1);
}
```

- [ ] **Step 3: Run the focused permission tests to verify they fail**

Run: `mvn -pl agent-oj-security -am "-Dtest=PermissionConverterTest,PermissionServiceTest,PermissionControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because permission is still implemented in root packages with HTTP DTO contracts.

- [ ] **Step 4: Implement the new permission interface plus `PermissionServiceImpl`, add `RolePermissionReferenceService` with `RolePermissionUsageQuery` and `RolePermissionUsageResult`, move the permission controller, mapper, entity, and role-permission persistence types into their domain packages, update the current root `RoleService` imports temporarily so the module still compiles before Task 4, and delete the replaced root permission classes**

- [ ] **Step 5: Re-run the focused permission tests**

Run: `mvn -pl agent-oj-security -am "-Dtest=PermissionConverterTest,PermissionServiceTest,PermissionControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 4: Refactor Role Domain And User-Domain Role Seam

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/controller/RoleController.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/service/RoleService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/service/RoleLookupService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/service/impl/RoleServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/service/impl/RoleLookupServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/mapper/RoleMapper.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/role/model/entity/Role.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/service/UserRoleReferenceService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/service/impl/UserRoleReferenceServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/query/UserRoleUsageQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/user/model/result/UserRoleUsageResult.java`
- Modify: `agent-oj-security/src/main/java/com/oj/agent/security/user/service/impl/UserServiceImpl.java`
- Modify: `agent-oj-security/src/test/java/com/oj/agent/security/user/service/UserServiceTest.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/role/service/RoleServiceTest.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/role/controller/RoleControllerTest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/controller/RoleController.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/service/RoleService.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/mapper/RoleMapper.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/mapper/RolePermissionMapper.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/model/Role.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/model/RolePermission.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/CreateRoleRequest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/UpdateRoleRequest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/RolePermissionRequest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/RoleResponse.java`

- [ ] **Step 1: Write failing role service tests for duplicate-code handling, delete guard, bind replace-all semantics, empty-input clear behavior, and role-not-found handling**

```java
@Test
void deleteRole_shouldFailWhenUsersAreStillAssigned() {
    DeleteRoleCommand command = new DeleteRoleCommand();
    command.setRoleId(6L);

    assertThatThrownBy(() -> roleService.deleteRole(command))
            .isInstanceOf(AuthorizationException.class);
}

@Test
void bindPermissions_shouldClearBindingsWhenInputIsEmpty() {
    BindRolePermissionsCommand command = new BindRolePermissionsCommand();
    command.setRoleId(6L);
    command.setPermissionIds(List.of());

    roleService.bindPermissions(command);

    verify(rolePermissionMapper).delete(any());
}
```

- [ ] **Step 2: Write a failing role controller test for query mapping and page compatibility**

```java
@Test
void listRoles_shouldKeepCompatiblePageFields() {
    RolePageResult pageResult = new RolePageResult();
    pageResult.setCurrent(3L);
    pageResult.setSize(7L);
    pageResult.setTotal(12L);
    pageResult.setPages(2L);
    when(roleService.listRoles(any(RolePageQuery.class))).thenReturn(pageResult);

    Result<RolePageResponse> result = controller.listRoles(3, 7);

    verify(roleService).listRoles(any(RolePageQuery.class));
    assertThat(result.getData().getCurrent()).isEqualTo(3L);
    assertThat(result.getData().getSize()).isEqualTo(7L);
    assertThat(result.getData().getTotal()).isEqualTo(12L);
    assertThat(result.getData().getPages()).isEqualTo(2L);
    assertThat(result.getData().getRecords()).isNotNull();
}

@Test
void bindPermissions_shouldKeepRoutePermissionAndDelegateCommand() throws Exception {
    Method method = RoleController.class.getMethod("bindPermissions", Long.class, BindRolePermissionsRequest.class);
    RequirePermission annotation = method.getAnnotation(RequirePermission.class);

    assertThat(annotation.value()).containsExactly("role:update");
}
```

- [ ] **Step 3: Run the focused role and touched user tests to verify they fail**

Run: `mvn -pl agent-oj-security -am "-Dtest=RoleConverterTest,RoleServiceTest,RoleControllerTest,UserServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because role still uses root DTOs, the current root `RoleService` is still in place, and `UserServiceImpl` still imports role persistence types directly.

- [ ] **Step 4: Implement the new role interfaces, move role controller and remaining role persistence code into `security.role`, reuse the `RolePermissionReferenceService` seam added in Task 3, add `RoleLookupService` and `UserRoleReferenceService`, use `PermissionLookupService` only for permission-summary assembly rather than new bind validation, update `UserServiceImpl` to resolve role summaries through the new lookup seam, preserve the current replace-all bind behavior without adding new permission-ID validation, and delete the replaced root role classes**

- [ ] **Step 5: Re-run the focused role and touched user tests**

Run: `mvn -pl agent-oj-security -am "-Dtest=RoleConverterTest,RoleServiceTest,RoleControllerTest,UserServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 5: Refactor Endpoint Scan Into Its Own Domain

**Files:**
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/endpoint/controller/EndpointController.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/endpoint/service/EndpointService.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/endpoint/service/impl/EndpointServiceImpl.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/endpoint/converter/EndpointConverter.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/endpoint/model/query/EndpointScanQuery.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/endpoint/model/result/EndpointResult.java`
- Create: `agent-oj-security/src/main/java/com/oj/agent/security/endpoint/model/response/EndpointResponse.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/endpoint/service/EndpointServiceTest.java`
- Create: `agent-oj-security/src/test/java/com/oj/agent/security/endpoint/controller/EndpointControllerTest.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/controller/EndpointScanController.java`
- Delete after migration: `agent-oj-security/src/main/java/com/oj/agent/security/dto/EndpointVO.java`

- [ ] **Step 1: Write a failing endpoint service test for HTTP method expansion, unannotated handlers, and first-permission behavior**

```java
@Test
void scanEndpoints_shouldPreserveAllFallbackAndFirstBoundPermission() {
    List<EndpointResult> results = endpointService.scanEndpoints(new EndpointScanQuery());

    assertThat(results).extracting(EndpointResult::getHttpMethod).contains("ALL");
    assertThat(results).anyMatch(item -> item.getBoundPermission() == null);
    assertThat(results).anyMatch(item -> "permission:create".equals(item.getBoundPermission()));
}
```

- [ ] **Step 2: Write a failing endpoint controller test for response compatibility**

```java
@Test
void scanEndpoints_shouldMapResultsToCompatibleResponseFields() {
    EndpointResult resultItem = new EndpointResult();
    resultItem.setUrl("/api/demo");
    resultItem.setHttpMethod("GET");
    resultItem.setControllerName("DemoController");
    resultItem.setMethodName("scan");
    resultItem.setBoundPermission("permission:create");
    when(endpointService.scanEndpoints(any(EndpointScanQuery.class))).thenReturn(List.of(resultItem));

    Result<List<EndpointResponse>> result = controller.scanEndpoints();

    assertThat(result.getData()).singleElement()
            .extracting(
                    EndpointResponse::getUrl,
                    EndpointResponse::getHttpMethod,
                    EndpointResponse::getControllerName,
                    EndpointResponse::getMethodName,
                    EndpointResponse::getBoundPermission
            )
            .containsExactly("/api/demo", "GET", "DemoController", "scan", "permission:create");
}

@Test
void scanEndpoints_shouldKeepPermissionCodeOnMethodAnnotation() throws Exception {
    Method method = EndpointController.class.getMethod("scanEndpoints");
    RequirePermission annotation = method.getAnnotation(RequirePermission.class);

    assertThat(annotation.value()).containsExactly("permission:create");
}
```

- [ ] **Step 3: Run the focused endpoint tests to verify they fail**

Run: `mvn -pl agent-oj-security -am "-Dtest=EndpointServiceTest,EndpointControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because endpoint scanning is still implemented directly in the root controller with `EndpointVO`.

- [ ] **Step 4: Implement the endpoint domain, move scan logic into `EndpointServiceImpl`, preserve guard and response semantics, and delete the replaced root endpoint files**

- [ ] **Step 5: Re-run the focused endpoint tests**

Run: `mvn -pl agent-oj-security -am "-Dtest=EndpointServiceTest,EndpointControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

### Task 6: Run Second-Round Verification

**Files:**
- Verify only; no planned file creation

- [ ] **Step 1: Run all focused second-round tests**

Run: `mvn -pl agent-oj-security -am "-Dtest=PermissionConverterTest,PermissionServiceTest,PermissionControllerTest,RoleConverterTest,RoleServiceTest,RoleControllerTest,EndpointServiceTest,EndpointControllerTest,UserServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

- [ ] **Step 2: Run module tests for the changed security module**

Run: `mvn -pl agent-oj-security -am test`
Expected: PASS

- [ ] **Step 3: Run the previously completed user and admin focused tests as regression coverage**

Run: `mvn -pl agent-oj-security,agent-oj-admin -am "-Dtest=UserConverterTest,UserServiceTest,UserControllerTest,AdminUserConverterTest,AdminUserManagementServiceTest,AdminUserManagementControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

- [ ] **Step 4: Verify preserved routes and guard permission codes in source after migration**

Run: `rg -n '@RequestMapping\\(\"/api/roles\"\\)|@PostMapping\\(\"/\\{id\\}/permissions\"\\)|@RequirePermission\\(\"role:update\"\\)|@RequestMapping\\(\"/api/endpoints\"\\)|@GetMapping\\(\"/scan\"\\)|@RequirePermission\\(\"permission:create\"\\)' agent-oj-security/src/main/java/com/oj/agent/security/role/controller/RoleController.java agent-oj-security/src/main/java/com/oj/agent/security/endpoint/controller/EndpointController.java`
Expected: matches showing `/api/roles`, `/{id}/permissions`, `role:update`, `/api/endpoints`, `/scan`, and `permission:create`
