# User Domain Refactor Design

## Background

The first full-repository refactor round will focus on the user-related flows in `agent-oj-security` and `agent-oj-admin`.

Current issues in scope:

- `agent-oj-security` still uses root-level technical layering such as `controller/service/mapper/dto/model`.
- `security` user APIs use `dto` directly in both controller and service layers.
- `security` user service methods return HTTP response objects and assemble presentation payloads inline.
- `agent-oj-admin` user management directly depends on `security` mappers and entities.
- `admin` user management also uses `dto/vo` directly as service contracts.

This violates the repository rules in `AGENTS.md`, especially:

- `controller -> request -> converter -> command/query -> service`
- `service -> result -> converter -> response -> controller`
- `service -> entity -> mapper`
- cross-domain dependencies should prefer stable service capabilities over direct imports of another domain's `entity`, `request`, `response`, or internal `dto`

## Goal

Refactor the first-round user flows so that:

- `security.user` becomes a dedicated business subdomain under `agent-oj-security`
- `admin.user` becomes a dedicated business subdomain under `agent-oj-admin`
- HTTP boundary types are isolated into `model.request` and `model.response`
- service contracts use `model.command`, `model.query`, and `model.result`
- deterministic field mapping moves into dedicated `converter` classes
- `admin.user` no longer depends directly on `security` mappers or entities
- current external routes and response fields remain compatible

## Scope

In scope:

- `agent-oj-security` user controller, service, mapper placement, and user-related models
- `agent-oj-security` login-log paging support used by admin user management
- minimal compatibility updates in `security.auth` caused by moving user and login-log entities or mappers
- minimal role-projection support required to keep current user response fields unchanged
- `agent-oj-admin` admin user management controller, service, and models
- targeted new tests for the new boundary and service contract behavior

Out of scope for this round:

- `security.auth` API contract refactor
- `security.role` write APIs and broader role-domain refactor
- `security.permission`
- `security.tablemaintenance`
- `admin.question`, `admin.question.hitk`, `admin.question.ragas`, `admin.question.compensation`
- large `workflow`, `rag`, or `question` refactors

## Target Design

### Security user domain

Move the user flow into:

- `com.oj.agent.security.user.controller`
- `com.oj.agent.security.user.service`
- `com.oj.agent.security.user.service.impl`
- `com.oj.agent.security.user.mapper`
- `com.oj.agent.security.user.converter`
- `com.oj.agent.security.user.model.entity`
- `com.oj.agent.security.user.model.request`
- `com.oj.agent.security.user.model.response`
- `com.oj.agent.security.user.model.command`
- `com.oj.agent.security.user.model.query`
- `com.oj.agent.security.user.model.result`

`UserService` will become an interface and `UserServiceImpl` its one-to-one implementation.

The service contract will become:

- create, update, and status operations use `command`
- list, get, current-user, and login-log paging operations use `query`
- service outputs use `result`
- controller maps `request -> command/query` and `result -> response`

`security.user` owns:

- `User`
- `UserRole`
- `UserLoginLog`
- user list paging support
- current-user queries
- admin-facing login-log paging support

To preserve current user response fields, `security.user` may use the existing role persistence types as a transitional read dependency inside the same module in this round. Broader role-domain boundary cleanup is explicitly deferred.

### Admin user domain

Move the admin user flow into:

- `com.oj.agent.admin.user.controller`
- `com.oj.agent.admin.user.service`
- `com.oj.agent.admin.user.service.impl`
- `com.oj.agent.admin.user.converter`
- `com.oj.agent.admin.user.model.request`
- `com.oj.agent.admin.user.model.response`
- `com.oj.agent.admin.user.model.command`
- `com.oj.agent.admin.user.model.query`
- `com.oj.agent.admin.user.model.result`

The admin service contract will become:

- disable-user operations use `AdminUserDisableCommand`
- user paging uses `AdminUserPageQuery`
- login-log paging uses `AdminLoginLogPageQuery`
- service outputs use `AdminUserPageResult` and `AdminLoginLogPageResult`

`admin.user` will stop calling `UserMapper` and `UserLoginLogMapper` directly. Instead, it will depend on `security.user.service.UserService`.

## Data Flow

### Security user APIs

- `UserController`
  - receives `CreateUserRequest`, `UpdateUserRequest`, `UpdateUserStatusRequest`, and request params for paging
  - uses `UserConverter` to create commands and queries
  - calls `UserService`
  - maps `UserResult`, `CurrentUserResult`, `UserPageResult` to HTTP responses

- `UserService`
  - owns business validation
  - reads and writes `User`, `UserRole`, and `UserLoginLog` through mappers
  - returns `result` objects only

### Admin user APIs

- `AdminUserManagementController`
  - keeps the current request shape where the body contains `status`
  - accepts only `status="DISABLED"` for the admin disable endpoint
  - uses `AdminUserConverter`
  - calls `AdminUserManagementService`
  - returns HTTP responses only

- `AdminUserManagementService`
  - no longer sees `dto/vo`
  - delegates disable, user-page, and login-log-page capabilities to `security.user.service.UserService`
  - maps security results into admin results, then controller responses

## Dependency Rules For This Round

- `admin.user` may depend on `security.user.service.UserService` and the small set of `security.user` command/query/result types required by that service interface
- `admin.user` must not import `security` mappers or entities
- `security.auth` may be updated only to follow moved user/login-log package paths
- controllers must not return service-layer result objects directly
- converters must not query the database or make business decisions

## Compatibility Strategy

This round preserves external behavior as follows:

- keep existing endpoint URLs
- keep existing response fields for security user and admin user APIs
- keep admin disable request semantics: request body still uses `status`, and only `DISABLED` is accepted
- keep security user status endpoint semantics unchanged

The refactor is internal layering cleanup, not an external API redesign.

## Testing Strategy

Because these modules currently have little or no direct test coverage, this round must create test directories and add focused tests first:

- converter tests for deterministic field mapping
- service tests for command/query/result contracts and validation paths
- controller tests for request/response mapping and compatibility

The first round should leave behind reusable testing patterns for later domain migrations.
