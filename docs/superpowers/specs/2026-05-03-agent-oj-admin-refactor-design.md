# Agent OJ Admin Refactor Design

## Background

`agent-oj-admin` is only partially aligned with the repository rules in `AGENTS.md`.

The current module is split into two very different shapes:

- `com.oj.agent.admin.user` already follows a domain-first structure with `controller`, `service`, `converter`, and `model.request|response|command|query|result`
- the rest of the admin module still uses root-level technical layering such as `controller`, `service`, `service.impl`, `model.dto`, and `model.vo`

The main structural problems currently in scope are:

- root-level question-related controllers still receive `dto` types and return `vo` types directly
- root-level service interfaces and implementations still expose `dto` / `vo` instead of `command` / `query` / `result`
- deterministic mapping is not isolated into dedicated converters for the question-related flows
- tests for question-related services still mirror legacy root packages, which means package migration will require coordinated test relocation
- some domain responsibilities are oversized, especially `AdminQuestionManagementServiceImpl`

Representative examples from the current codebase:

- `controller.AdminQuestionManagementController` receives `AdminQuestionCreateRequest`, `AdminQuestionUpdateRequest`, and `AdminQuestionBatchDeleteRequest` from `model.dto` and returns `AdminQuestionDualDetailResponse`, `AdminQuestionDocumentVO`, `AdminQuestionDocumentSegmentVO`, `AdminKnowledgeSegmentPageResponse`, and `AdminBatchOperationResponse` from `model.vo`
- `service.AdminQuestionManagementService` exposes HTTP-shaped request and response types instead of service-layer contracts
- `service.impl.AdminQuestionManagementServiceImpl` is a 1300+ line mixed-responsibility class covering question CRUD, code-template validation, vector sync, document upload, segment paging, and batch delete
- `controller.AdminQuestionRagasController` and `service.AdminQuestionRagasService` still use root-level `model.dto` and `model.vo`
- `controller.AdminQuestionHitkController` and `service.AdminQuestionHitkService` still use root-level `model.dto` and `model.vo`
- `controller.AdminQuestionCompensationController`, `service.AdminQuestionCompensationService`, and `model.vo.AdminCompensationTaskResponse` still live in root-level technical packages instead of a dedicated compensation domain
- `controller.AdminHealthController` and `service.AdminHealthService` still live in root-level technical packages and return `model.vo.AdminModuleInfo`

The current audit also shows that `user` is already the best local example for how this module should look after convergence, while `question`-related flows remain the main cleanup target.

## Goal

Refactor `agent-oj-admin` so that the admin domains touched by this effort comply with the repository rules:

- `controller -> request -> converter -> command/query -> service`
- `service -> result -> converter -> response -> controller`
- `service -> entity -> mapper/client`
- `converter` contains only deterministic mapping logic
- package ownership is domain-first rather than root technical layering
- no new `VO`, no new mixed `model` dumping, and no new cross-domain leakage of HTTP-boundary types

The refactor must preserve:

- existing HTTP routes
- existing request field names
- existing response JSON field names and payload structure
- current validation and permission semantics unless a bug fix is required to preserve behavior

The refactor is an internal boundary cleanup and package convergence effort, not an API redesign.

## Scope

In scope:

- `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/**`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/**`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/**`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/config/AdminQuestionManagementExceptionHandler.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/config/RagasServiceProperties.java`
- matching tests under `agent-oj-admin/src/test/java/com/oj/agent/admin/**`
- minimal compatibility updates to imports in the same module caused by package relocation

Out of scope:

- `agent-oj-admin` user-domain redesign beyond minimal compatibility updates
- unrelated modules outside `agent-oj-admin`
- endpoint contract redesign
- database schema changes
- broad logic rewrites that are not necessary for boundary cleanup

## Refactor Approaches

### Option 1: Big-bang admin-module rewrite

Move all root-level question and health flows into new domain packages in one pass.

Pros:

- shortest migration window
- no temporary overlap between old and new packages

Cons:

- highest compile-break surface
- highest regression risk around `AdminQuestionManagementServiceImpl`, `AdminQuestionRagasServiceImpl`, and `AdminQuestionHitkServiceImpl`
- poor fit for staged sub-agent execution and per-round verification

### Option 2: Round-based domain convergence

Refactor one admin subdomain at a time, keep each round buildable, and let each round migrate its own tests and compatibility seams.

Pros:

- smallest safe batch size
- aligns with the user's request for multi-round execution with sub-agents
- easier to verify route compatibility after every round

Cons:

- requires strict sequencing
- some legacy root packages will coexist temporarily until their owning rounds are finished

### Option 3: Model-wrapper-first migration

Add new request/response/command/query/result types first, leave root services and controllers in place, and move packages later.

Pros:

- low immediate compile risk

Cons:

- prolongs dual-model lifetime
- normalizes the legacy root package layout instead of eliminating it
- increases mapping noise before package ownership is corrected

### Recommendation

Use Option 2.

`agent-oj-admin` already contains one domain that matches the desired target shape, namely `user`. The remaining violations cluster naturally into `health`, `question`, `question.compensation`, `question.ragas`, and `question.hitk`. A round-based migration is the best fit for safe refactoring, test stability, and sub-agent task isolation.

## Target Design

### Shared Boundary Rules

For every migrated admin domain:

- HTTP input types live under `model.request`
- HTTP output types live under `model.response`
- service write intent lives under `model.command`
- service read conditions live under `model.query`
- service business output lives under `model.result`
- `converter` owns only deterministic mapping between these layers
- controller methods do not assemble service-layer objects inline except trivial query primitives that are immediately converted
- service interfaces do not expose `request` / `response`
- test packages mirror the final production package paths

Naming convergence is part of the refactor, not optional cleanup:

- root `*VO` types must be renamed to `*Response` or `*Result` according to their real boundary
- root request-like `*dto` types used by controllers must become `*Request`
- service-layer input and output models must become `*Command`, `*Query`, and `*Result`
- temporary compatibility wrappers are allowed only inside the active round and must be removed before that round is closed

### Target Domain Layout

The final admin module should converge toward these primary package roots:

- `com.oj.agent.admin.user` for existing user management
- `com.oj.agent.admin.health` for module health status
- `com.oj.agent.admin.question` for core question-management flows
- `com.oj.agent.admin.question.compensation` for compensation tasks
- `com.oj.agent.admin.question.ragas` for Ragas generation, answer generation, evaluation, and maintenance
- `com.oj.agent.admin.question.hitk` for HitK question generation, task management, statistics, and remark updates

Package structure inside each of these domains should use the repository standard as needed:

- `controller`
- `service`
- `service.impl`
- `converter`
- `config`
- `exception`
- `client`
- `model.request`
- `model.response`
- `model.command`
- `model.query`
- `model.result`

`model.dto` may remain only if a type is truly a domain-internal orchestration carrier or a third-party binding object and does not cross a controller or service boundary.

### Question Management Domain Shape

The primary question-management flow should converge to `com.oj.agent.admin.question`.

Expected ownership:

- question detail query
- question create
- question update
- question batch delete
- question document upload and deletion
- question document segment paging and child segment listing
- knowledge-segment global paging

Required boundary changes:

- `AdminQuestionManagementController` stops receiving root `model.dto` types directly and uses `question.converter`
- `AdminQuestionManagementService` exposes `command` / `query` / `result` rather than HTTP-boundary types
- `AdminQuestionManagementServiceImpl` is allowed to remain a large implementation during early rounds if package migration alone is safer, but its service boundary must stop leaking request/response types
- `AdminQuestionManagementExceptionHandler` moves into `com.oj.agent.admin.question.exception`
- the advice that is truly question-management-specific moves in Round 3; any advice branch that only serves HitK moves or is split in Round 5 so that the final advice ownership is domain-correct

### Compensation Domain Shape

The compensation flow should converge to `com.oj.agent.admin.question.compensation`.

Expected ownership:

- list pending compensation tasks
- retry one compensation task

Required boundary changes:

- move controller, service interface, implementation, response model, and matching test into the compensation domain
- replace `AdminCompensationTaskResponse` root `model.vo` placement with `question.compensation.model.response`
- keep current routes and JSON fields unchanged

This domain is intentionally the smallest safe first slice and should serve as the migration template for the later question subdomains.

### Ragas Domain Shape

The Ragas flow should converge to `com.oj.agent.admin.question.ragas`.

Expected ownership:

- Ragas record generation
- Ragas record listing
- Ragas record update and delete
- answer generation
- evaluation
- Ragas service property ownership and external client configuration

Required boundary changes:

- `AdminQuestionRagasController` uses `request` and `response` under the Ragas domain
- `AdminQuestionRagasService` exposes service-layer `command` / `query` / `result`
- `RagasEvaluationClient` moves to `com.oj.agent.admin.question.ragas.client.RagasEvaluationClient`
- `RagasServiceProperties` moves to `com.oj.agent.admin.question.ragas.config.RagasServiceProperties`

### HitK Domain Shape

The HitK flow should converge to `com.oj.agent.admin.question.hitk`.

Expected ownership:

- HitK question generation and update
- test task creation and listing
- task detail query
- global task paging
- statistics
- batch delete
- remark update

Required boundary changes:

- move all HitK request and response types out of root `model.dto` and `model.vo`
- move service and controller into the HitK domain
- move matching tests so they mirror the new package structure

### Health Domain Shape

The health flow should converge to `com.oj.agent.admin.health`.

Expected ownership:

- module health endpoint
- module info response model

Required boundary changes:

- move `AdminHealthController` out of root `controller`
- move `AdminHealthService` out of root `service`
- replace root `model.vo.AdminModuleInfo` placement with `health.model.response.AdminModuleInfoResponse` or an equivalent domain-owned response type while preserving JSON
- the concrete target type is `com.oj.agent.admin.health.model.response.AdminModuleInfoResponse`

## Execution Rounds

### Round 1: Compensation Vertical Slice

Primary targets:

- `controller.AdminQuestionCompensationController`
- `service.AdminQuestionCompensationService`
- `service.impl.AdminQuestionCompensationServiceImpl`
- `model.vo.AdminCompensationTaskResponse`
- `test.service.impl.AdminQuestionCompensationServiceImplTest`

Objectives:

- establish the package-migration template for root-level admin question flows
- move the full compensation path into `question.compensation`
- rename `AdminCompensationTaskResponse` into a compensation-domain `model.response` type
- move the matching test to mirror the new production package
- keep compile and test stability after the round

This round is intentionally narrow. It should not include `AdminQuestionManagementServiceImpl`, `AdminQuestionRagasServiceImpl`, or `AdminQuestionHitkServiceImpl`.

### Round 2: Health Vertical Slice

Primary targets:

- `controller.AdminHealthController`
- `service.AdminHealthService`
- `model.vo.AdminModuleInfo`

Objectives:

- remove the remaining trivial root health flow
- create the admin `health` domain layout
- keep current route and response payload unchanged

This round is small enough to validate the pattern again before entering the heavy question-management refactor.

### Round 3: Question Management Boundary Convergence

Primary targets:

- `controller.AdminQuestionManagementController`
- `service.AdminQuestionManagementService`
- `service.impl.AdminQuestionManagementServiceImpl`
- all root question-management request and response models
- `config.AdminQuestionManagementExceptionHandler`
- matching tests, especially `AdminQuestionManagementServiceImplTest`

Objectives:

- move the core question-management flow into `com.oj.agent.admin.question`
- replace root request/response usage with `request`, `command`, `query`, `result`, and `response`
- introduce deterministic converters
- keep implementation logic stable where possible while correcting service boundaries
- relocate tests to mirror the final package
- rename migrated `VO` classes in this domain to `*Response` or `*Result` before the round is closed

This round is expected to be the largest and may itself be split into sub-rounds during planning if the exact file inventory proves too large for a single safe implementation batch.

### Round 4: Ragas Domain Convergence

Primary targets:

- `controller.AdminQuestionRagasController`
- `service.AdminQuestionRagasService`
- `service.impl.AdminQuestionRagasServiceImpl`
- root Ragas request and response models
- `service.client.RagasEvaluationClient`
- `config.RagasServiceProperties`

Objectives:

- isolate the Ragas flow into `question.ragas`
- correct service boundaries and model placement
- rename migrated `VO` classes in this domain to `*Response` or `*Result` before the round is closed
- keep external route, request, and response compatibility

### Round 5: HitK Domain Convergence

Primary targets:

- `controller.AdminQuestionHitkController`
- `service.AdminQuestionHitkService`
- `service.impl.AdminQuestionHitkServiceImpl`
- all root HitK request and response models
- matching tests, especially `AdminQuestionHitkServiceImplTest`

Objectives:

- isolate the HitK flow into `question.hitk`
- correct request/response placement and service contracts
- move tests to mirror the final package structure
- rename migrated `VO` classes in this domain to `*Response` or `*Result` before the round is closed
- preserve current endpoint contracts

HitK is placed after Ragas because it spans a wider set of request/response types and task-oriented outputs and benefits from the prior migration patterns.

## Compatibility Strategy

Every round must preserve:

- current endpoint URLs
- current permission annotations and semantics
- current request field names
- current response JSON field names
- current validation rules unless a fix is required to keep the existing behavior consistent

Compatibility is measured at the HTTP boundary, not by preserving old Java package names or class names.

## Testing Strategy

Each round must finish with:

- the module compiling successfully
- tests for the touched admin slice passing
- any moved tests relocated so they mirror the new production packages

Round-specific expectations:

- Round 1 must move and keep `AdminQuestionCompensationServiceImplTest` passing
- Round 2 should add a focused health test if the new package move is not already covered by existing tests
- Round 3 should preserve and relocate `AdminQuestionManagementServiceImplTest` and may need new converter or controller tests if service-boundary changes introduce mapping logic
- Round 4 and Round 5 should add or relocate tests as needed so migrated request/response mapping is explicitly covered

The `user` domain tests are already in a good shape and should remain untouched unless a later round introduces an unavoidable compatibility dependency.

## Execution Model

Implementation should happen in a dedicated worktree and use sub-agents to keep context bounded.

The preferred flow is:

1. write the implementation plan for the approved design
2. create or switch to an isolated worktree before code changes
3. execute one round at a time with sub-agents
4. verify compile and relevant tests after every round
5. stop and report after each round before starting the next one
