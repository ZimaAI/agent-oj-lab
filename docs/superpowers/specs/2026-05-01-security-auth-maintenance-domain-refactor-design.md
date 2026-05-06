# Security Auth And Maintenance Domain Refactor Design

## Background

After the completed user-domain and access-domain refactors, the main remaining `agent-oj-security` structure violations are concentrated in the root technical-layer packages:

- `security.controller.AuthController`
- `security.service.AuthService`
- `security.dto.LoginRequest`
- `security.dto.LoginResponse`
- `security.dto.RefreshTokenRequest`
- `security.controller.TableMaintenanceController`
- `security.service.TableMaintenanceService`
- `security.dto.TableClearRequest`
- `security.dto.TableClearResponse`
- `security.dto.TableOptionResponse`

Current issues in scope:

- auth and table-maintenance flows still live in root `controller`, `service`, `dto`, and `provider` packages instead of stable business subdomains
- controller request and response types are passed directly into service methods and returned directly from service methods
- auth still reaches into `security.user` internals through `UserMapper`, `UserLoginLogMapper`, and `user` entities
- `TrialCountFilter` still mutates user persistence state directly through `UserMapper`
- deterministic mapping is missing dedicated `converter` packages for these flows

This violates the repository rules in `AGENTS.md`, especially:

- domain-first package structure
- `controller -> request -> converter -> command/query -> service`
- `service -> result -> converter -> response -> controller`
- cross-domain collaboration should prefer another domain's public service capability instead of direct `mapper` and `entity` imports

## Goal

Refactor the remaining root-level security flows so that:

- `security.auth` becomes a dedicated subdomain
- `security.maintenance` becomes a dedicated subdomain
- root `dto` usage for these flows is removed
- controllers only see HTTP `request` and `response`
- services only see `command`, `query`, and `result`
- deterministic mapping moves into dedicated `converter` packages
- auth and trial-count runtime code stop importing `security.user` persistence types directly
- current routes, payload shapes, token claims, and validation behavior remain compatible

## Scope

In scope:

- auth login and refresh flows in `agent-oj-security`
- table-maintenance list and clear flows in `agent-oj-security`
- new public user-domain support services needed by auth and trial-count runtime code
- focused tests for auth, maintenance, and touched user/filter seams

Out of scope for this round:

- `security.role`, `security.permission`, `security.endpoint`, and `security.user` controller APIs beyond the new support seams
- `security.annotation`, `security.aspect`, `security.exception`, and JWT payload redesign
- admin question-domain refactors and broader admin/core boundary cleanup
- replacing `JwtUtil`, `PasswordUtil`, or `UserContext`
- changing existing 401/403 response codes or payload format

## Compatibility Constraints

This round is internal boundary cleanup, not an external API redesign. The following contracts must remain stable:

- routes:
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
  - `GET /api/admin/table-maintenance/tables`
  - `POST /api/admin/table-maintenance/clear`
- auth behavior:
  - username-only login without password still auto-creates a temporary passwordless user when the username does not already exist
  - password-required users still reject blank or mismatched passwords
  - inactive users still fail login and refresh
  - temporary-account validity window checks remain unchanged
  - refresh still validates the refresh token, reloads the user, and returns fresh access and refresh tokens
  - permission changes still take effect only after login or refresh because permissions are still token snapshots
- auth payload shape:
  - login and refresh still return `accessToken`, `refreshToken`, and `userInfo`
  - `userInfo` still contains `userId`, `username`, `userType`, and `permissions`
- JWT runtime behavior:
  - `JwtAuthenticationFilter` whitelist remains `/api/auth/login` and `/api/auth/refresh`
  - token claim structure used by `JwtUtil` remains unchanged
- trial-count behavior:
  - the filter still blocks when `trialCount == 0`
  - requests that complete successfully still decrement persistent `trial_count` when the in-context value is greater than zero
- table-maintenance behavior:
  - both endpoints still require `table:clear`
  - provider absence still throws the same provider-unavailable error
  - null, empty, blank, duplicate, and non-whitelisted table names are still handled the same way
  - response payload fields for table list and clear results remain compatible

## Target Design

### Security auth domain

Move auth into:

- `com.oj.agent.security.auth.controller`
- `com.oj.agent.security.auth.service`
- `com.oj.agent.security.auth.service.impl`
- `com.oj.agent.security.auth.converter`
- `com.oj.agent.security.auth.model.request`
- `com.oj.agent.security.auth.model.response`
- `com.oj.agent.security.auth.model.command`
- `com.oj.agent.security.auth.model.result`

`AuthService` becomes an interface with a one-to-one `AuthServiceImpl`.

The HTTP boundary becomes:

- `LoginRequest`
- `RefreshTokenRequest`
- `LoginResponse`

The service contract becomes:

- `LoginCommand`
- `RefreshTokenCommand`
- `AuthResult`

The controller keeps HTTP-only work:

- extract client IP from `HttpServletRequest`
- map `request -> command` through `AuthConverter`
- map `AuthResult -> LoginResponse` through `AuthConverter`

The auth domain owns:

- login orchestration
- refresh orchestration
- password and account-status validation
- token generation

It does not own user persistence. That responsibility stays in `security.user`.

### User-domain auth and trial support seams

Add focused public service seams in `security.user`:

- `UserAuthService`
- `UserTrialService`

Recommended contracts:

- `UserAuthService`
  - `UserAuthResult getUserByUsername(UserAuthByUsernameQuery query)`
  - `UserAuthResult getUserById(UserAuthByIdQuery query)`
  - `UserAuthResult createUsernameOnlyUser(UserUsernameOnlyCreateCommand command)`
  - `UserPermissionResult getUserPermissions(UserPermissionQuery query)`
  - `void createLoginLog(UserLoginLogCommand command)`
- `UserTrialService`
  - `void decrementTrialCount(UserTrialCountDecrementCommand command)`

Recommended support models:

- `security.user.model.query`
  - `UserAuthByUsernameQuery`
  - `UserAuthByIdQuery`
  - `UserPermissionQuery`
- `security.user.model.command`
  - `UserUsernameOnlyCreateCommand`
  - `UserLoginLogCommand`
  - `UserTrialCountDecrementCommand`
- `security.user.model.result`
  - `UserAuthResult`
  - `UserPermissionResult`

Implementation rules:

- support service implementations may use only `security.user` persistence types
- auth services and filters may depend on the support service interfaces plus their public `command`, `query`, and `result` models
- auth services and filters must not import `security.user.mapper.*` or `security.user.model.entity.*`
- these support services are internal collaboration seams inside the module, not new controller APIs

### Security maintenance domain

Move table-maintenance into:

- `com.oj.agent.security.maintenance.controller`
- `com.oj.agent.security.maintenance.service`
- `com.oj.agent.security.maintenance.service.impl`
- `com.oj.agent.security.maintenance.provider`
- `com.oj.agent.security.maintenance.converter`
- `com.oj.agent.security.maintenance.model.request`
- `com.oj.agent.security.maintenance.model.response`
- `com.oj.agent.security.maintenance.model.command`
- `com.oj.agent.security.maintenance.model.query`
- `com.oj.agent.security.maintenance.model.result`

`TableMaintenanceService` becomes an interface with a one-to-one `TableMaintenanceServiceImpl`.

The HTTP boundary becomes:

- `TableClearRequest`
- `TableClearResponse`
- `TableOptionResponse`

The service contract becomes:

- `TableListQuery`
- `TableClearCommand`
- `TableClearResult`
- `TableOptionResult`

`TableMaintenanceProvider` moves under the maintenance domain and stops using HTTP response types directly. It should expose domain results instead.

The maintenance domain owns:

- provider lookup
- table-name normalization
- whitelist validation
- response/result mapping

## Data Flow

### Auth APIs

- `AuthController`
  - accepts `LoginRequest` and `RefreshTokenRequest`
  - extracts request IP when needed
  - uses `AuthConverter`
  - calls `AuthService`
  - maps `AuthResult` into `LoginResponse`
- `AuthService`
  - resolves user identity through `UserAuthService`
  - auto-creates username-only users through `UserAuthService`
  - validates password and account status
  - records login logs through `UserAuthService`
  - loads permission codes through `UserAuthService`
  - generates tokens through `JwtUtil`
  - returns only `AuthResult`

### Trial-count runtime seam

- `TrialCountFilter`
  - keeps request-path matching and response writing behavior
  - uses `UserContext` to read in-memory user information
  - delegates persistent decrement to `UserTrialService`
  - no longer imports `UserMapper` or `User`

### Maintenance APIs

- `TableMaintenanceController`
  - accepts HTTP request bodies only
  - uses `TableMaintenanceConverter`
  - calls `TableMaintenanceService`
  - maps service results into HTTP responses
- `TableMaintenanceService`
  - loads provider through dependency injection
  - validates and normalizes table names
  - verifies requested names against provider-listed enabled tables
  - returns only result types
- `TableMaintenanceProvider`
  - returns domain `result` types
  - remains the extension point for actual table-clear execution

## Validation Rules

This round should keep current business behavior:

- blank password is still allowed only for passwordless users
- username-only login auto-create remains limited to requests that provide a username and no password
- duplicate auto-create races still resolve by reloading the user after duplicate-key failure
- login logs still persist `unknown` when client IP is blank
- refresh still fails when the token is invalid, expired, or points to a deleted user
- trial-count decrement still affects only users whose current value is greater than zero
- maintenance clear still rejects any request containing null, blank, or non-whitelisted table names

## Testing Strategy

Add focused tests before or together with refactor changes:

- `auth.converter` tests for request, command, result, and response mapping
- `auth.service` tests for username-only auto-create, invalid password, inactive user, refresh failure, permission loading, and response/result assembly
- `auth.controller` tests for route compatibility, client-IP extraction, and converter/service delegation
- `maintenance.converter` tests for request/query/result/response mapping
- `maintenance.service` tests for provider-missing, normalization, whitelist validation, duplicate table de-duplication, and clear/list behavior
- `maintenance.controller` tests for route and permission-code compatibility
- `user.service` tests for the new `UserAuthService` and `UserTrialService` seams
- `filter` regression test ensuring `TrialCountFilter` delegates persistent decrement through `UserTrialService`

## Round Boundary

At the end of this round:

- root `security.controller` is empty
- root `security.service` is empty
- root `security.dto` is empty
- auth and maintenance flows live in domain-first packages
- auth runtime code no longer imports `security.user` mappers or entities directly
- `TrialCountFilter` no longer imports `UserMapper` or `User`
- touched code introduces no new `dto`, `vo`, or mixed root `model` classes
- admin question-domain cleanup and broader cross-domain query/result decoupling remain intentionally deferred
