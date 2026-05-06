# Conversation POJO Refactor Design

## Background

`agent-oj-core/src/main/java/com/oj/agent/core/conversation` currently mixes HTTP boundary objects, service-layer intent objects, persistence entities, and presentation objects:

- `controller` directly receives `dto` and returns `vo`
- `service` receives `ConversationCreateRequest` and returns `ConversationCreateVO` / `ConversationListItemVO` / `ConversationMessageVO`
- `workflow` directly imports `conversation` request DTOs and persistence entities
- field mapping is embedded inside services instead of being isolated in `converter`

This violates the repository POJO rules in `AGENTS.md`, especially the required flows:

- `controller -> request -> converter -> command/query -> service`
- `service -> result -> converter -> response -> controller`
- `service -> entity -> mapper`

## Goal

Refactor the `conversation` domain and its direct callers so that:

- HTTP boundary only uses `request` and `response`
- service boundary only uses `command`, `query`, and `result`
- mapper boundary only uses `entity`
- `converter` performs deterministic mapping only
- `workflow` no longer depends on `conversation` HTTP request types or directly creates `Conversation` / `ConversationMessage` entities for normal conversation operations

## Scope

In scope:

- `agent-oj-core/src/main/java/com/oj/agent/core/conversation/**`
- direct callers in `agent-oj-core/src/main/java/com/oj/agent/core/workflow/**`

Out of scope:

- unrelated domain refactors outside `conversation` and its direct workflow callers
- database schema changes
- API contract changes beyond class/package renames and internal layering cleanup

## Current Problems

### 1. Controller and service boundaries are blurred

`ConversationController` uses `conversation.model.dto.*` and `conversation.model.vo.*`, while `ConversationService` and `ConversationMessageService` expose the same boundary types. This makes HTTP objects leak into the service layer.

### 2. Service returns presentation objects

`ConversationServiceImpl` and `ConversationMessageServiceImpl` build VO objects directly. Service logic and presentation shaping are coupled in the same classes.

### 3. Workflow depends on conversation-boundary DTOs

`workflow` imports `conversation.model.dto.UserMessageDTO`, even though that type is an HTTP/workflow entry object rather than a conversation-domain object.

### 4. Workflow directly creates conversation entities

`WorkflowBootstrapService` and `AssistantMessagePersistenceStrategy` instantiate `Conversation` and `ConversationMessage` directly. This bypasses the intended service boundary and spreads persistence structure outside the domain.

### 5. Missing converter layer

Conversation-related mapping is currently spread across controllers and services instead of being centralized into a dedicated converter package.

## Target Design

### Layering

- `controller` only handles HTTP concerns and delegates with `request`, `response`, and converter calls
- `converter` performs only deterministic field mapping, null handling, enum conversion, and lightweight formatting
- `service` accepts `command` and `query`, returns `result`, and owns business logic
- `mapper` only reads and writes `entity`
- `workflow` interacts with conversation services through `command`, `query`, and `result`

### Package layout

`conversation` will use this structure:

- `controller`
- `converter`
- `mapper`
- `model.command`
- `model.entity`
- `model.query`
- `model.request`
- `model.response`
- `model.result`
- `service`
- `service.impl`

`workflow` will move its user conversation stream request object into its own boundary package:

- `workflow.model.request.UserMessageRequest`

## Type Plan

### Conversation HTTP boundary

Create or rename to:

- `conversation.model.request.ConversationCreateRequest`
- `conversation.model.request.ConversationListRequest`
- `conversation.model.request.ConversationMessageListRequest`
- `conversation.model.response.ConversationCreateResponse`
- `conversation.model.response.ConversationListItemResponse`
- `conversation.model.response.ConversationMessageResponse`

These types remain controller-only.

### Conversation service boundary

Create:

- `conversation.model.command.ConversationCreateCommand`
- `conversation.model.command.ConversationDeleteCommand`
- `conversation.model.command.ConversationTouchCommand`
- `conversation.model.command.ConversationCurrentQuestionUpdateCommand`
- `conversation.model.command.ConversationTitleBackfillCommand`
- `conversation.model.command.ConversationMessageCreateCommand`
- `conversation.model.query.ConversationListQuery`
- `conversation.model.query.ConversationGetQuery`
- `conversation.model.query.ConversationOwnershipQuery`
- `conversation.model.query.ConversationMessageListQuery`
- `conversation.model.result.ConversationCreateResult`
- `conversation.model.result.ConversationListItemResult`
- `conversation.model.result.ConversationDetailResult`
- `conversation.model.result.ConversationMessageResult`
- `conversation.model.result.ConversationMessageCreateResult`

These types define service input intent, lookup conditions, and business outputs.

### Converter

Add `conversation.converter.ConversationConverter` with pure mapping methods for:

- `ConversationCreateRequest -> ConversationCreateCommand`
- `ConversationListRequest -> ConversationListQuery`
- `ConversationMessageListRequest -> ConversationMessageListQuery`
- `ConversationCreateResult -> ConversationCreateResponse`
- `ConversationListItemResult -> ConversationListItemResponse`
- `ConversationMessageResult -> ConversationMessageResponse`
- `Conversation entity -> ConversationDetailResult`
- `Conversation entity -> ConversationCreateResult`
- `Conversation entity -> ConversationListItemResult`
- `ConversationMessage entity -> ConversationMessageResult`
- `ConversationCreateCommand -> Conversation entity`
- `ConversationMessageCreateCommand -> ConversationMessage entity`

The converter must not query databases, call remote services, or make business decisions.

## Service Contract Changes

### ConversationService

Replace `IService<Conversation>` style boundary usage with explicit domain operations:

- `ConversationCreateResult createConversation(ConversationCreateCommand command)`
- `Page<ConversationListItemResult> listConversations(ConversationListQuery query)`
- `ConversationDetailResult getConversation(ConversationGetQuery query)`
- `ConversationDetailResult validateConversationOwnership(ConversationOwnershipQuery query)`
- `boolean touchConversation(ConversationTouchCommand command)`
- `boolean updateCurrentQuestionIfLatestUserMessageMatches(ConversationCurrentQuestionUpdateCommand command)`
- `boolean backfillTitleIfLatestUserMessageMatches(ConversationTitleBackfillCommand command)`
- `boolean deleteConversation(ConversationDeleteCommand command)`

### ConversationMessageService

Expose explicit operations instead of `IService<ConversationMessage>`:

- `ConversationMessageCreateResult createMessage(ConversationMessageCreateCommand command)`
- `Page<ConversationMessageResult> listMessages(ConversationMessageListQuery query)`

`createMessage` is responsible for allocating `sequenceNo` and returning the persisted result needed by callers.

## Workflow Changes

### Request ownership

Move `UserMessageDTO` out of `conversation` and replace it with:

- `workflow.model.request.UserMessageRequest`

Update:

- `WorkflowController`
- `WorkflowService`
- `WorkflowStartRequest`

so that workflow HTTP entry objects live in the workflow boundary, not the conversation domain.

### Bootstrap and persistence boundary

Update `WorkflowBootstrapService` and `AssistantMessagePersistenceStrategy` so they stop constructing `Conversation` and `ConversationMessage` entities directly.

Instead they will:

- assemble `ConversationCreateCommand` for new conversation bootstrap
- assemble `ConversationMessageCreateCommand` for user and assistant message persistence
- use `ConversationDetailResult` and `ConversationMessageCreateResult` as returned business objects

### WorkflowBootstrapDTO

Replace the `Conversation` entity field with `ConversationDetailResult`:

- before: `private Conversation conversation;`
- after: `private ConversationDetailResult conversation;`

This keeps workflow dependent on business output, not persistence structure.

## Migration Strategy

### Step 1. Introduce new POJO packages

Add the new `request/response/command/query/result` types and `ConversationConverter` without deleting old classes yet.

### Step 2. Refactor controller boundary

Update `ConversationController` to:

- receive only `model.request`
- call converter to build `command` or `query`
- call service
- convert `result` to `response`

### Step 3. Refactor conversation services

Update `ConversationService`, `ConversationMessageService`, and both implementations so:

- request and response classes disappear from service signatures
- entity-to-result mapping moves to converter
- command-to-entity mapping moves to converter

### Step 4. Refactor workflow direct callers

Update:

- `WorkflowController`
- `WorkflowService`
- `WorkflowBootstrapService`
- `WorkflowStartRequest`
- `WorkflowBootstrapDTO`
- `AssistantMessagePersistenceStrategy`
- any other direct caller found by search

so that they use `workflow` request types plus `conversation` command/query/result interfaces only.

### Step 5. Remove obsolete classes and imports

Delete:

- `conversation.model.dto.*` that are replaced by request/query/command types
- `conversation.model.vo.*`

Keep `entity` only for mapper and conversation-service internal persistence work.

## Verification Plan

### Build verification

Run module compilation for `agent-oj-core` and ensure no references remain to:

- `conversation.model.vo`
- `conversation.model.dto.UserMessageDTO`
- request/response types inside conversation service signatures

### Structural verification

Search-based checks:

- `conversation.controller` must not import `entity`
- `conversation.service` must not import `model.request` or `response`
- `workflow` must not import `conversation.model.dto.UserMessageDTO`
- direct entity construction outside conversation services should be removed for conversation operations

### Behavioral verification

Confirm no behavior regression in these flows:

- create conversation
- list conversations
- list conversation messages
- bootstrap normal workflow conversation
- persist assistant message through workflow chain
- bootstrap code evaluation conversation path

## Risks

- `ConversationMessageService.createMessage` must preserve current sequence allocation semantics under transaction and row lock
- changing workflow bootstrap return types may require small ripple updates where entity fields were accessed directly
- removing `IService` inheritance from public interfaces may expose hidden caller assumptions; all callers must be searched before cleanup

## Non-Goals

- redesigning conversation business rules
- changing page parameter defaults
- changing persistence SQL or table structure
- refactoring unrelated domains to the same pattern in this task

## Recommendation

Implement this refactor as a boundary-cleanup change focused on `conversation` plus its direct workflow callers. Keep logic behavior stable and avoid opportunistic redesign outside the required POJO layering fixes.
