# Conversation POJO Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the `conversation` domain and its direct `workflow` callers so controller, service, and persistence boundaries use the POJO types required by `AGENTS.md` without changing conversation behavior.

**Architecture:** Keep `conversation` as the domain owner for conversation persistence and business rules, but move HTTP-only objects to `model.request` and `model.response`, move service contracts to `model.command`, `model.query`, and `model.result`, and centralize deterministic field mapping in `conversation.converter.ConversationConverter`. Update `workflow` to use its own request object and call `conversation` only through command/query/result interfaces so persistence entities stop leaking across the boundary.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, JUnit 5, Mockito, AssertJ, Maven

---

Spec reference: `docs/superpowers/specs/2026-05-01-conversation-pojo-refactor-design.md`

### Task 1: Add Conversation Boundary POJOs And Converter

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/request/ConversationCreateRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/request/ConversationListRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/request/ConversationMessageListRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/response/ConversationCreateResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/response/ConversationListItemResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/response/ConversationMessageResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/command/ConversationCreateCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/command/ConversationDeleteCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/command/ConversationTouchCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/command/ConversationCurrentQuestionUpdateCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/command/ConversationTitleBackfillCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/command/ConversationMessageCreateCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/query/ConversationListQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/query/ConversationGetQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/query/ConversationOwnershipQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/query/ConversationMessageListQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/result/ConversationCreateResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/result/ConversationListItemResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/result/ConversationDetailResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/result/ConversationMessageResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/result/ConversationMessageCreateResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/converter/ConversationConverter.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/conversation/converter/ConversationConverterTest.java`

- [ ] **Step 1: Write the failing converter test**

```java
@Test
void toCreateCommand_shouldCopyCurrentQuestionId() {
    ConversationCreateRequest request = new ConversationCreateRequest();
    request.setCurrentQuestionId(1001L);

    ConversationCreateCommand command = ConversationConverter.toCreateCommand(request);

    assertThat(command.getCurrentQuestionId()).isEqualTo(1001L);
}
```

- [ ] **Step 2: Run the converter test to verify it fails**

Run: `mvn -pl agent-oj-core -Dtest=ConversationConverterTest test`
Expected: FAIL with `ConversationConverter` or new POJO classes missing.

- [ ] **Step 3: Add the new request/response/command/query/result classes and implement deterministic mappings in `ConversationConverter`**

```java
public static ConversationCreateCommand toCreateCommand(ConversationCreateRequest request) {
    ConversationCreateCommand command = new ConversationCreateCommand();
    if (request != null) {
        command.setCurrentQuestionId(request.getCurrentQuestionId());
    }
    return command;
}
```

- [ ] **Step 4: Run the converter test to verify it passes**

Run: `mvn -pl agent-oj-core -Dtest=ConversationConverterTest test`
Expected: PASS

- [ ] **Step 5: Commit the boundary POJO scaffolding**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/conversation/model agent-oj-core/src/main/java/com/oj/agent/core/conversation/converter agent-oj-core/src/test/java/com/oj/agent/core/conversation/converter/ConversationConverterTest.java
git commit -m "refactor: add conversation boundary pojos"
```

### Task 2: Refactor ConversationController To Request/Response Boundary

**Files:**
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/controller/ConversationController.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/converter/ConversationConverter.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/conversation/controller/ConversationControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

```java
@Test
void create_shouldReturnResponseMappedFromServiceResult() {
    ConversationCreateResponse response = controller.create(new ConversationCreateRequest()).getData();

    assertThat(response.getConversationId()).isEqualTo("conv-1");
}
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run: `mvn -pl agent-oj-core -Dtest=ConversationControllerTest test`
Expected: FAIL because controller still returns VO types and does not map through the converter.

- [ ] **Step 3: Update the controller to use only `model.request`, `model.response`, and converter methods**

```java
@PostMapping
public Result<ConversationCreateResponse> create(@RequestBody(required = false) ConversationCreateRequest request) {
    ConversationCreateResult result = conversationService.createConversation(
            ConversationConverter.toCreateCommand(request));
    return Result.success(ConversationConverter.toResponse(result));
}
```

- [ ] **Step 4: Run the controller test to verify it passes**

Run: `mvn -pl agent-oj-core -Dtest=ConversationControllerTest test`
Expected: PASS

- [ ] **Step 5: Commit the controller boundary change**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/conversation/controller/ConversationController.java agent-oj-core/src/main/java/com/oj/agent/core/conversation/converter/ConversationConverter.java agent-oj-core/src/test/java/com/oj/agent/core/conversation/controller/ConversationControllerTest.java
git commit -m "refactor: isolate conversation controller boundary"
```

### Task 3: Refactor ConversationService To Command/Query/Result

**Files:**
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/ConversationService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/impl/ConversationServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/converter/ConversationConverter.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/mapper/ConversationMapper.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/mapper/ConversationMessageMapper.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/conversation/service/ConversationServiceImplTest.java`

- [ ] **Step 1: Write the failing service test**

```java
@Test
void createConversation_shouldReturnResultInsteadOfVo() {
    ConversationCreateCommand command = new ConversationCreateCommand();
    command.setCurrentQuestionId(1001L);

    ConversationCreateResult result = service.createConversation(command);

    assertThat(result.getCurrentQuestionId()).isEqualTo(1001L);
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run: `mvn -pl agent-oj-core -Dtest=ConversationServiceImplTest test`
Expected: FAIL because `ConversationService` still accepts request objects and returns VO objects.

- [ ] **Step 3: Change the service contract and implementation to use command/query/result plus converter-based entity mapping**

```java
public interface ConversationService {
    ConversationCreateResult createConversation(ConversationCreateCommand command);
    Page<ConversationListItemResult> listConversations(ConversationListQuery query);
    ConversationDetailResult getConversation(ConversationGetQuery query);
}
```

- [ ] **Step 4: Run the service test to verify it passes**

Run: `mvn -pl agent-oj-core -Dtest=ConversationServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit the conversation service refactor**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/ConversationService.java agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/impl/ConversationServiceImpl.java agent-oj-core/src/main/java/com/oj/agent/core/conversation/converter/ConversationConverter.java agent-oj-core/src/main/java/com/oj/agent/core/conversation/mapper/ConversationMapper.java agent-oj-core/src/main/java/com/oj/agent/core/conversation/mapper/ConversationMessageMapper.java agent-oj-core/src/test/java/com/oj/agent/core/conversation/service/ConversationServiceImplTest.java
git commit -m "refactor: convert conversation service to command query result"
```

### Task 4: Refactor ConversationMessageService To Command/Query/Result

**Files:**
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/ConversationMessageService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/impl/ConversationMessageServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/converter/ConversationConverter.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/conversation/service/ConversationMessageServiceImplTest.java`

- [ ] **Step 1: Write the failing message service test**

```java
@Test
void createMessage_shouldAllocateSequenceAndReturnResult() {
    ConversationMessageCreateCommand command = new ConversationMessageCreateCommand();
    command.setConversationId("conv-1");
    command.setSender("USER");
    command.setContent("hello");

    ConversationMessageCreateResult result = service.createMessage(command);

    assertThat(result.getSequenceNo()).isEqualTo(1);
}
```

- [ ] **Step 2: Run the message service test to verify it fails**

Run: `mvn -pl agent-oj-core -Dtest=ConversationMessageServiceImplTest test`
Expected: FAIL because the public API still exposes `saveWithAutoSequenceNo(ConversationMessage)`.

- [ ] **Step 3: Replace entity-based public methods with `ConversationMessageCreateCommand` and `ConversationMessageResult` mappings while preserving row-lock sequence allocation**

```java
public interface ConversationMessageService {
    ConversationMessageCreateResult createMessage(ConversationMessageCreateCommand command);
    Page<ConversationMessageResult> listMessages(ConversationMessageListQuery query);
}
```

- [ ] **Step 4: Run the message service test to verify it passes**

Run: `mvn -pl agent-oj-core -Dtest=ConversationMessageServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit the message service refactor**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/ConversationMessageService.java agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/impl/ConversationMessageServiceImpl.java agent-oj-core/src/main/java/com/oj/agent/core/conversation/converter/ConversationConverter.java agent-oj-core/src/test/java/com/oj/agent/core/conversation/service/ConversationMessageServiceImplTest.java
git commit -m "refactor: convert conversation message service boundary"
```

### Task 5: Move Workflow Request Ownership To Workflow Boundary

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/request/UserMessageRequest.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/controller/WorkflowController.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/WorkflowStartRequest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/controller/WorkflowControllerTest.java`

- [ ] **Step 1: Write the failing workflow controller test**

```java
@Test
void stream_shouldAcceptWorkflowOwnedUserMessageRequest() {
    UserMessageRequest request = new UserMessageRequest();
    request.setMessage("hello");

    SseEmitter emitter = controller.stream(request);

    assertThat(emitter).isNotNull();
}
```

- [ ] **Step 2: Run the workflow controller test to verify it fails**

Run: `mvn -pl agent-oj-core -Dtest=WorkflowControllerTest test`
Expected: FAIL because `/api/workflow/stream` still depends on `conversation.model.dto.UserMessageDTO`.

- [ ] **Step 3: Introduce `workflow.model.request.UserMessageRequest` and update the workflow entry path to use it end-to-end**

```java
public void stream(SseEmitter emitter, UserMessageRequest userMessageRequest) {
    WorkflowStartRequest startRequest = new WorkflowStartRequest();
    startRequest.setUserMessageRequest(userMessageRequest);
    stream(emitter, startRequest);
}
```

- [ ] **Step 4: Run the workflow controller test to verify it passes**

Run: `mvn -pl agent-oj-core -Dtest=WorkflowControllerTest test`
Expected: PASS

- [ ] **Step 5: Commit the workflow request move**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/request/UserMessageRequest.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/controller/WorkflowController.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowService.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/WorkflowStartRequest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/controller/WorkflowControllerTest.java
git commit -m "refactor: move workflow user message request"
```

### Task 6: Refactor Workflow Bootstrap And Persistence To Use Conversation Commands/Results

**Files:**
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/WorkflowBootstrapDTO.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/AssistantMessagePersistenceStrategy.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/CurrentQuestionPersistenceStrategy.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowInputMapBuilder.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/service/WorkflowBootstrapServiceTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/persistence/impl/AssistantMessagePersistenceStrategyTest.java`

- [ ] **Step 1: Write the failing bootstrap and persistence tests**

```java
@Test
void bootstrap_shouldUseConversationCommandsAndReturnConversationDetailResult() {
    WorkflowBootstrapDTO bootstrap = service.bootstrap(request, true, "conv-1", "trace-1");

    assertThat(bootstrap.getConversation().getConversationId()).isEqualTo("conv-1");
}
```

```java
@Test
void persist_shouldCreateAssistantMessageThroughServiceCommand() {
    strategy.persist(state, context);

    verify(conversationMessageService).createMessage(any(ConversationMessageCreateCommand.class));
}
```

- [ ] **Step 2: Run the workflow tests to verify they fail**

Run: `mvn -pl agent-oj-core -Dtest=WorkflowBootstrapServiceTest,AssistantMessagePersistenceStrategyTest test`
Expected: FAIL because bootstrap and persistence still construct conversation entities directly or call entity-based service methods.

- [ ] **Step 3: Update workflow callers to assemble commands and consume results only**

```java
ConversationCreateCommand command = new ConversationCreateCommand();
command.setConversationId(conversationId);
command.setUserId(userId);
command.setTitle(DEFAULT_CONVERSATION_TITLE);

ConversationCreateResult created = conversationService.createConversation(command);
ConversationGetQuery query = new ConversationGetQuery();
query.setConversationId(created.getConversationId());
ConversationDetailResult conversation = conversationService.getConversation(query);
```

- [ ] **Step 4: Run the workflow tests to verify they pass**

Run: `mvn -pl agent-oj-core -Dtest=WorkflowBootstrapServiceTest,AssistantMessagePersistenceStrategyTest test`
Expected: PASS

- [ ] **Step 5: Commit the workflow boundary refactor**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/WorkflowBootstrapDTO.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/AssistantMessagePersistenceStrategy.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/CurrentQuestionPersistenceStrategy.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowInputMapBuilder.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/service/WorkflowBootstrapServiceTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/persistence/impl/AssistantMessagePersistenceStrategyTest.java
git commit -m "refactor: route workflow conversation writes through domain services"
```

### Task 7: Remove Legacy Conversation DTO/VO Types And Verify The Module

**Files:**
- Delete: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/dto/ConversationCreateRequest.java`
- Delete: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/dto/ConversationMessageQueryRequest.java`
- Delete: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/dto/ConversationQueryRequest.java`
- Delete: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/dto/UserMessageDTO.java`
- Delete: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/vo/ConversationCreateVO.java`
- Delete: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/vo/ConversationListItemVO.java`
- Delete: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/model/vo/ConversationMessageVO.java`
- Verify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/**`
- Verify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/**`

- [ ] **Step 1: Run the structural grep checks and confirm they still fail before cleanup**

Run: `rg -n "conversation\\.model\\.(dto|vo)|UserMessageDTO" agent-oj-core/src/main/java`
Expected: output still includes legacy conversation DTO/VO imports and declarations.

- [ ] **Step 2: Delete the legacy classes and clean up remaining imports/usages**

```text
Remove conversation.model.dto/*
Remove conversation.model.vo/*
Update all remaining imports to request/response/command/query/result packages
```

- [ ] **Step 3: Run focused tests plus module verification**

Run: `mvn -pl agent-oj-core -Dtest=ConversationConverterTest,ConversationControllerTest,ConversationServiceImplTest,ConversationMessageServiceImplTest,WorkflowControllerTest,WorkflowBootstrapServiceTest,AssistantMessagePersistenceStrategyTest test`
Expected: PASS

Run: `mvn -pl agent-oj-core -am test`
Expected: PASS

Run: `rg -n "conversation\\.model\\.(dto|vo)|UserMessageDTO" agent-oj-core/src/main/java`
Expected: no output

- [ ] **Step 4: Commit the cleanup and verification pass**

```bash
git add agent-oj-core/src/main/java agent-oj-core/src/test/java
git commit -m "refactor: complete conversation pojo layering cleanup"
```
