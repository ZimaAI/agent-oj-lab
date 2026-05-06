# Security Access Domain Refactor Design

## Background

The second full-repository refactor round will focus on the access-management code in `agent-oj-security`.

Current issues in scope:

- `role`, `permission`, and endpoint-scan logic still live in root technical-layer packages such as `controller`, `service`, `mapper`, `dto`, and `model`.
- controller request and response DTOs are passed directly into service methods and returned directly from service methods.
- deterministic field mapping is embedded in services and controllers instead of dedicated `converter` packages.
- `RoleService` directly depends on `security.user` persistence types.
- `UserServiceImpl` still directly imports role persistence types to build user role projections.
- `EndpointScanController` mixes HTTP boundary concerns with Spring MVC introspection logic.

This violates the repository rules in `AGENTS.md`, especially:

- domain-first package structure
- `controller -> request -> converter -> command/query -> service`
- `service -> result -> converter -> response -> controller`
- cross-domain collaboration should prefer public service capabilities over direct imports of another domain's `entity` and boundary types

## Goal

Refactor the access-management slice so that:

- `security.role` becomes a dedicated subdomain
- `security.permission` becomes a dedicated subdomain
- `security.endpoint` becomes a dedicated subdomain for endpoint catalog scanning
- root-level `dto` and `model` usage in these flows is removed
- controllers only see HTTP `request` and `response`
- services only see `command`, `query`, and `result`
- deterministic mapping moves into `converter`
- user-domain role projection stops importing role persistence types directly
- current HTTP routes, guarded permission codes, and response shapes remain compatible

## Scope

In scope:

- `agent-oj-security` role controller, service, mapper placement, and role-related models
- `agent-oj-security` permission controller, service, mapper placement, and permission-related models
- `agent-oj-security` endpoint scan controller extraction into a dedicated domain
- minimal cross-domain support services needed to remove direct `user -> role.entity` imports and to reduce `role -> user.entity` coupling
- focused tests for role, permission, endpoint, and touched user-domain support seams

Out of scope for this round:

- `security.auth` request/response/service refactor
- `security.annotation`, `security.aspect`, `security.filter`, `security.util`, and JWT claim structure changes
- 401/403 error payload redesign
- `agent-oj-admin` refactors beyond compiling against moved security packages
- broader question, workflow, rag, or admin domains

## Compatibility Constraints

This round is internal layering cleanup, not an external API redesign. The following contracts must remain stable:

- routes:
  - `POST /api/roles`
  - `PUT /api/roles/{id}`
  - `GET /api/roles`
  - `GET /api/roles/{id}`
  - `DELETE /api/roles/{id}`
  - `POST /api/roles/{id}/permissions`
  - `POST /api/permissions`
  - `PUT /api/permissions/{id}`
  - `GET /api/permissions/tree`
  - `GET /api/permissions/{id}`
  - `DELETE /api/permissions/{id}`
  - `GET /api/endpoints/scan`
- permission codes used by guards:
  - `role:create`
  - `role:update`
  - `role:list`
  - `role:read`
  - `role:delete`
  - `permission:create`
  - `permission:update`
  - `permission:list`
  - `permission:read`
  - `permission:delete`
- endpoint-scan semantics:
  - still requires `permission:create`
  - still returns one record per route and HTTP method combination
  - still reports `ALL` when no explicit method is declared
  - still exposes only the first `@RequirePermission` value in `boundPermission`
- role list paging inputs stay `page` and `size` with defaults `1` and `10`
- role list paging output may move from MyBatis `Page` to a dedicated `RolePageResponse`, but it must keep the compatible JSON fields `records`, `total`, `size`, `current`, and `pages`
- permission tree ordering stays `permissionType asc, id asc`
- role list ordering stays `createTime desc`
- role-permission binding stays replace-all, and empty input still clears bindings
- current token snapshot behavior remains unchanged:
  - permission changes do not affect an existing access token until login or refresh

## Target Design

### Security role domain

Move role management into:

- `com.oj.agent.security.role.controller`
- `com.oj.agent.security.role.service`
- `com.oj.agent.security.role.service.impl`
- `com.oj.agent.security.role.mapper`
- `com.oj.agent.security.role.converter`
- `com.oj.agent.security.role.model.entity`
- `com.oj.agent.security.role.model.request`
- `com.oj.agent.security.role.model.response`
- `com.oj.agent.security.role.model.command`
- `com.oj.agent.security.role.model.query`
- `com.oj.agent.security.role.model.result`

`RoleService` becomes an interface with a one-to-one `RoleServiceImpl`.

The HTTP boundary becomes:

- `CreateRoleRequest`
- `UpdateRoleRequest`
- `BindRolePermissionsRequest`
- `RolePageResponse`
- `RoleResponse`

The service contract becomes:

- create, update, delete, and bind operations use `command`
- list and detail operations use `query`
- service outputs use `RoleResult` and `RolePageResult`

The role domain owns:

- `Role`
- `RolePermission`
- role CRUD
- role-permission binding
- role response assembly

### Security permission domain

Move permission management into:

- `com.oj.agent.security.permission.controller`
- `com.oj.agent.security.permission.service`
- `com.oj.agent.security.permission.service.impl`
- `com.oj.agent.security.permission.mapper`
- `com.oj.agent.security.permission.converter`
- `com.oj.agent.security.permission.model.entity`
- `com.oj.agent.security.permission.model.request`
- `com.oj.agent.security.permission.model.response`
- `com.oj.agent.security.permission.model.command`
- `com.oj.agent.security.permission.model.query`
- `com.oj.agent.security.permission.model.result`

`PermissionService` becomes an interface with a one-to-one `PermissionServiceImpl`.

The HTTP boundary becomes:

- `CreatePermissionRequest`
- `UpdatePermissionRequest`
- `PermissionResponse`

The service contract becomes:

- create, update, and delete operations use `command`
- tree and detail operations use `query`
- service outputs use `PermissionResult`

The permission domain owns:

- `Permission`
- permission CRUD
- permission tree assembly

### Security endpoint domain

Endpoint scanning is not permission CRUD and should not stay under the same root controller package. Move it into:

- `com.oj.agent.security.endpoint.controller`
- `com.oj.agent.security.endpoint.service`
- `com.oj.agent.security.endpoint.service.impl`
- `com.oj.agent.security.endpoint.converter`
- `com.oj.agent.security.endpoint.model.query`
- `com.oj.agent.security.endpoint.model.result`
- `com.oj.agent.security.endpoint.model.response`

The endpoint domain keeps `/api/endpoints/scan`, but the controller only maps HTTP. Spring MVC handler introspection moves into `EndpointServiceImpl`.

### Cross-domain support seams

This round needs a small set of focused support services so that moved domains do not fall back to direct `entity` coupling.

Add public read-only seams:

- `security.role.service.RoleLookupService`
  - used by `security.user`
  - resolves role summaries by role ID collection
- `security.permission.service.PermissionLookupService`
  - used by `security.role`
  - resolves permission summaries by permission ID collection
- `security.role.service.RolePermissionReferenceService`
  - used by `security.permission`
  - reports whether a permission is still bound to roles
- `security.user.service.UserRoleReferenceService`
  - used by `security.role`
  - reports whether a role is still assigned to users

Recommended service contracts:

- `RoleLookupService`
  - input: `RoleIdsQuery`
  - output: `List<RoleSummaryResult>`
  - required summary fields: `roleId`, `roleName`, `roleCode`
- `PermissionLookupService`
  - input: `PermissionIdsQuery`
  - output: `List<PermissionSummaryResult>`
  - required summary fields: `permissionId`, `permissionCode`, `permissionName`
- `RolePermissionReferenceService`
  - input: `RolePermissionUsageQuery`
  - output: `RolePermissionUsageResult`
- `UserRoleReferenceService`
  - input: `UserRoleUsageQuery`
  - output: `UserRoleUsageResult`

Implementation rule:

- support service implementations may use only their own domain persistence types
- application services may depend on another domain's public support service plus that service's `query` and `result` models
- application services must not import another domain's `request`, `response`, or `entity`

### Transitional cleanup kept for a later round

The auth token issuance path remains as-is in this round:

- `AuthService` still uses `security.user.mapper.UserMapper.selectUserPermissions`
- SQL still joins `permission`, `role_permission`, and `user_role` tables directly
- `RequirePermission`, `PermissionAspect`, `JwtAuthenticationFilter`, `JwtUtil`, and `UserContext` stay in their current packages

This is intentional. Refactoring auth and authorization runtime behavior together with role and permission would expand scope too far and increase regression risk.

## Data Flow

### Role APIs

- `RoleController`
  - accepts request bodies and paging params
  - uses `RoleConverter`
  - calls `RoleService`
  - maps `RoleResult` and `RolePageResult` into HTTP responses
- `RoleService`
  - validates duplicate role codes
  - validates delete preconditions through `UserRoleReferenceService`
  - uses `PermissionLookupService` to assemble permission summaries for role detail and list responses
  - reads and writes `Role` and `RolePermission` through role mappers
  - returns only result types

### Permission APIs

- `PermissionController`
  - accepts request bodies and path variables
  - uses `PermissionConverter`
  - calls `PermissionService`
  - maps service results into HTTP responses
- `PermissionService`
  - validates duplicate permission codes
  - validates delete preconditions through `RolePermissionReferenceService`
  - reads and writes `Permission` through `PermissionMapper`
  - returns only result types

### Endpoint APIs

- `EndpointController`
  - calls `EndpointService`
  - maps `EndpointResult` into `EndpointResponse`
- `EndpointService`
  - scans `RequestMappingHandlerMapping`
  - converts handler metadata into result objects
  - preserves current `boundPermission` first-value behavior

### Touched user-domain seam

- `UserServiceImpl`
  - keeps ownership of `UserRole`
  - reads assigned role IDs from `UserRoleMapper`
  - delegates role summary lookup to `RoleLookupService`
  - no longer imports role `entity` or role `mapper`

## Validation Rules

This round should keep and tighten current business behavior:

- role create and update still reject duplicate `roleCode`
- permission create and update still reject duplicate `permissionCode`
- role delete still fails when users are assigned
- permission delete still fails when roles are bound
- role bind keeps current replace-all semantics and must not introduce new request validation behavior in this round
- permission update keeps current compatibility and does not add new writable fields beyond the current API

## Testing Strategy

Add focused tests before or together with refactor changes:

- `role.converter` tests for request/query/result/response mapping
- `role.service` tests for duplicate role code, delete guard, bind replace-all semantics, empty-input clear behavior, role-not-found handling, and page mapping
- `role.controller` tests for route-shape compatibility and command/query mapping
- `permission.converter` tests for deterministic mapping
- `permission.service` tests for duplicate permission code, delete guard, tree building, and detail mapping
- `permission.controller` tests for request/response compatibility
- `endpoint.service` tests for method expansion, `ALL` fallback, unannotated handlers, and first-permission behavior
- `endpoint.controller` tests for response mapping
- user-domain regression tests for the new `RoleLookupService` integration seam

## Round Boundary

At the end of this round:

- root role, permission, and endpoint-scan classes are removed
- touched code in `security.user` no longer imports role persistence types directly
- no new `dto`, `vo`, or mixed root `model` classes are introduced for these flows
- auth runtime code remains compatible and intentionally deferred to a later round
