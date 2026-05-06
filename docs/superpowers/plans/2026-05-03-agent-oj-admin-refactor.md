# Agent OJ Admin Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `agent-oj-admin` into domain-first packages that satisfy `AGENTS.md` while keeping every external HTTP route, request field, response field, and permission behavior unchanged.

**Architecture:** Move the remaining admin flows out of the root technical packages and into domain-owned packages. Each round introduces or relocates `request`, `command`, `query`, `result`, `response`, and `converter` types so controllers stop talking to `dto`/`vo` directly and services stop returning HTTP-shaped objects. Keep each round buildable, verify it, then stop and report before starting the next round.

**Tech Stack:** Java 17, Spring Boot 3, Spring MVC, MyBatis-Plus, JUnit 5, Mockito, AssertJ, Maven

---

Spec reference: `docs/superpowers/specs/2026-05-03-agent-oj-admin-refactor-design.md`

Execution prerequisite: create and use an isolated worktree before implementation (`superpowers:using-git-worktrees`).

Shared verification gates:

- Focused round tests: `mvn -pl agent-oj-admin -am "-Dtest=<round tests>" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- Module compile gate: `mvn -pl agent-oj-admin -am -DskipTests compile`
- Final module gate: `mvn -pl agent-oj-admin -am test`

### Round Ownership Inventory

Round 1, `question.compensation`:

- `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminQuestionCompensationController.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminQuestionCompensationService.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionCompensationServiceImpl.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminCompensationTaskResponse.java`
- `agent-oj-admin/src/test/java/com/oj/agent/admin/service/impl/AdminQuestionCompensationServiceImplTest.java`

Round 2, `health`:

- `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminHealthController.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminHealthService.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminModuleInfo.java`

Round 3, `question` core management:

- `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminQuestionManagementController.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminQuestionManagementService.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionManagementServiceImpl.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/config/AdminQuestionManagementExceptionHandler.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminQuestionCreateRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminQuestionUpdateRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminQuestionBatchDeleteRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionDualDetailResponse.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionDocumentVO.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionDocumentSegmentVO.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionDocumentSegmentPageResponse.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminKnowledgeSegmentVO.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminKnowledgeSegmentPageResponse.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminBatchOperationResponse.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionVectorProjection.java`
- `agent-oj-admin/src/test/java/com/oj/agent/admin/service/impl/AdminQuestionManagementServiceImplTest.java`

Round 4, `question.ragas`:

- `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminQuestionRagasController.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminQuestionRagasService.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionRagasServiceImpl.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/config/RagasServiceProperties.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/client/RagasEvaluationClient.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminRagasGenerateRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminRagasUpdateRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminRagasDeleteRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminRagasAnswerGenerateRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminRagasEvaluateRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionDocumentSegmentRagasVO.java`
Round 5, `question.hitk`:

- `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminQuestionHitkController.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminQuestionHitkService.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionHitkServiceImpl.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkQuestionGenerateRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkQuestionUpdateRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkTestCreateRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkTaskBatchStatisticsRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkTaskBatchDeleteRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkTaskRemarkUpdateRequest.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkQuestionBatchResponse.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkTaskPageResponse.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkTaskStatisticsResponse.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkTaskBatchDeleteResponse.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkTaskVO.java`
- `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkTaskDetailVO.java`
- `agent-oj-admin/src/test/java/com/oj/agent/admin/service/impl/AdminQuestionHitkServiceImplTest.java`

Final cleanup:

- `agent-oj-admin/src/main/java/com/oj/agent/admin/config/AdminUserManagementExceptionHandler.java`
- `agent-oj-admin/src/test/java/com/oj/agent/admin/user/**` only if the package move in this plan changes imports

### Task 1: Compensation Domain Convergence

**Files:**
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/compensation/model/result/AdminCompensationTaskResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/compensation/model/response/AdminCompensationTaskResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/compensation/converter/AdminQuestionCompensationConverter.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminQuestionCompensationController.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminQuestionCompensationService.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionCompensationServiceImpl.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminCompensationTaskResponse.java`
- Move: `agent-oj-admin/src/test/java/com/oj/agent/admin/service/impl/AdminQuestionCompensationServiceImplTest.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/compensation/converter/AdminQuestionCompensationConverterTest.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/compensation/controller/AdminQuestionCompensationControllerTest.java`

- [ ] **Step 1: Write the failing converter and controller tests**

```java
@Test
void toResponse_shouldCopyTaskFields() {
    // mapper-style mapping from result to response
}
```

- [ ] **Step 2: Run the focused compensation tests to verify they fail**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionCompensationConverterTest,AdminQuestionCompensationServiceImplTest,AdminQuestionCompensationControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the new compensation domain package and converter/result types do not exist yet.

- [ ] **Step 3: Implement the compensation converter, result, response, package moves, and service/controller boundary changes**

- [ ] **Step 4: Re-run the focused compensation tests**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionCompensationConverterTest,AdminQuestionCompensationServiceImplTest,AdminQuestionCompensationControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

- [ ] **Step 5: Run the module compile gate**

Run: `mvn -pl agent-oj-admin -am -DskipTests compile`
Expected: PASS

### Task 2: Health Domain Convergence

**Files:**
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/health/model/result/AdminModuleInfoResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/health/model/response/AdminModuleInfoResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/health/converter/AdminHealthConverter.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminHealthController.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminHealthService.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminModuleInfo.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/health/converter/AdminHealthConverterTest.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/health/controller/AdminHealthControllerTest.java`

- [ ] **Step 1: Write the failing health converter and controller tests**

```java
@Test
void toResponse_shouldCopyModuleInfo() {
    // health mapping test
}
```

- [ ] **Step 2: Run the focused health tests to verify they fail**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminHealthConverterTest,AdminHealthControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the new health domain package and response types do not exist yet.

- [ ] **Step 3: Implement the health converter, result, response, and package moves**

- [ ] **Step 4: Re-run the focused health tests**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminHealthConverterTest,AdminHealthControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

- [ ] **Step 5: Run the module compile gate**

Run: `mvn -pl agent-oj-admin -am -DskipTests compile`
Expected: PASS

### Task 3: Question Management Boundary Extraction

**Files:**
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/request/AdminQuestionCreateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/request/AdminQuestionUpdateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/request/AdminQuestionBatchDeleteRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/request/AdminQuestionDocumentUploadRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/command/AdminQuestionCreateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/command/AdminQuestionUpdateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/command/AdminQuestionBatchDeleteCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/command/AdminQuestionDocumentUploadCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/command/AdminQuestionDocumentDeleteCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/query/AdminQuestionDualDetailQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/query/AdminQuestionDocumentListQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/query/AdminQuestionDocumentSegmentPageQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/query/AdminQuestionDocumentChildSegmentQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/query/AdminKnowledgeSegmentPageQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/result/AdminQuestionResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/result/AdminQuestionCodeTemplateResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/result/AdminQuestionVectorProjectionResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/result/AdminQuestionDualDetailResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/result/AdminQuestionDocumentResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/result/AdminQuestionDocumentSegmentResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/result/AdminQuestionDocumentSegmentPageResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/result/AdminKnowledgeSegmentResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/result/AdminKnowledgeSegmentPageResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/result/AdminBatchOperationResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/response/AdminQuestionResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/response/AdminQuestionCodeTemplateResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/response/AdminQuestionVectorProjectionResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/response/AdminQuestionDualDetailResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/response/AdminQuestionDocumentResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/response/AdminQuestionDocumentSegmentResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/response/AdminQuestionDocumentSegmentPageResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/response/AdminKnowledgeSegmentResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/response/AdminKnowledgeSegmentPageResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/model/response/AdminBatchOperationResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/converter/AdminQuestionManagementConverter.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminQuestionManagementController.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminQuestionManagementService.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionManagementServiceImpl.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/config/AdminQuestionManagementExceptionHandler.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminQuestionCreateRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminQuestionUpdateRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminQuestionBatchDeleteRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionDualDetailResponse.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionDocumentVO.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionDocumentSegmentVO.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionDocumentSegmentPageResponse.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminKnowledgeSegmentVO.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminKnowledgeSegmentPageResponse.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminBatchOperationResponse.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionVectorProjection.java`
- Move: `agent-oj-admin/src/test/java/com/oj/agent/admin/service/impl/AdminQuestionManagementServiceImplTest.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/converter/AdminQuestionManagementConverterTest.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/controller/AdminQuestionManagementControllerTest.java`

- [ ] **Step 1: Write the failing converter and controller tests for the question-management boundary types**

```java
@Test
void toDualDetailResponse_shouldMapNestedQuestionData() {
    // mapping test for result-to-response boundary
}
```

- [ ] **Step 2: Run the focused question-management tests to verify they fail**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionManagementConverterTest,AdminQuestionManagementServiceImplTest,AdminQuestionManagementControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the new question package and service contracts do not exist yet.

- [ ] **Step 3: Implement the question-management command/query/result/response layer, package moves, converter, and exception-handler split**

The upload endpoint must stop passing `MultipartFile` into the service boundary directly. Convert the multipart request into `AdminQuestionDocumentUploadRequest` at the controller edge, then map it into `AdminQuestionDocumentUploadCommand` with non-web fields such as filename, content type, and binary content. Likewise, replace raw `(questionId, docId)` delete arguments with `AdminQuestionDocumentDeleteCommand`.

- [ ] **Step 4: Re-run the focused question-management tests**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionManagementConverterTest,AdminQuestionManagementServiceImplTest,AdminQuestionManagementControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

- [ ] **Step 5: Run the module compile gate**

Run: `mvn -pl agent-oj-admin -am -DskipTests compile`
Expected: PASS

### Task 4: Ragas Domain Convergence

**Files:**
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/request/AdminRagasGenerateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/request/AdminRagasUpdateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/request/AdminRagasDeleteRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/request/AdminRagasAnswerGenerateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/request/AdminRagasEvaluateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/command/AdminRagasGenerateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/command/AdminRagasUpdateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/command/AdminRagasDeleteCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/command/AdminRagasAnswerGenerateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/command/AdminRagasEvaluateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/query/AdminRagasListQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/result/AdminQuestionDocumentSegmentRagasResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/response/AdminQuestionDocumentSegmentRagasResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/converter/AdminQuestionRagasConverter.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminQuestionRagasController.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminQuestionRagasService.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionRagasServiceImpl.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/config/RagasServiceProperties.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/client/RagasEvaluationClient.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminRagasGenerateRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminRagasUpdateRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminRagasDeleteRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminRagasAnswerGenerateRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminRagasEvaluateRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminQuestionDocumentSegmentRagasVO.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/ragas/converter/AdminQuestionRagasConverterTest.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/ragas/controller/AdminQuestionRagasControllerTest.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/ragas/service/impl/AdminQuestionRagasServiceImplTest.java`

- [ ] **Step 1: Write the failing ragas converter and controller tests**

```java
@Test
void toResponse_shouldMapRagasPayload() {
    // mapping test
}
```

- [ ] **Step 2: Run the focused ragas tests to verify they fail**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionRagasConverterTest,AdminQuestionRagasServiceImplTest,AdminQuestionRagasControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the new ragas package and contracts do not exist yet.

- [ ] **Step 3: Implement the ragas command/query/result/response layer, package moves, converter, and client/config relocation**

- [ ] **Step 4: Re-run the focused ragas tests**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionRagasConverterTest,AdminQuestionRagasServiceImplTest,AdminQuestionRagasControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

- [ ] **Step 5: Run the module compile gate**

Run: `mvn -pl agent-oj-admin -am -DskipTests compile`
Expected: PASS

### Task 5: HitK Domain Convergence

**Files:**
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKQuestionGenerateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKQuestionUpdateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKTestCreateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKTaskBatchStatisticsRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKTaskBatchDeleteRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKTaskRemarkUpdateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/command/AdminHitKQuestionGenerateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/command/AdminHitKQuestionUpdateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/command/AdminHitKTestCreateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/command/AdminHitKTaskBatchDeleteCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/command/AdminHitKTaskRemarkUpdateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/query/AdminHitKTaskPageQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/query/AdminHitKTaskDetailQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/query/AdminHitKTestListQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/query/AdminHitKTaskStatisticsQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/result/AdminHitKQuestionBatchResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/result/AdminHitKTaskResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/result/AdminHitKTaskDetailResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/result/AdminHitKTaskPageResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/result/AdminHitKTaskStatisticsResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/result/AdminHitKTaskBatchDeleteResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKQuestionBatchResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKTaskResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKTaskDetailResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKTaskPageResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKTaskStatisticsResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKTaskBatchDeleteResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/converter/AdminQuestionHitKConverter.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/exception/AdminQuestionHitKExceptionHandler.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/controller/AdminQuestionHitkController.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/controller/AdminQuestionHitKController.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/AdminQuestionHitkService.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/service/AdminQuestionHitKService.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionHitkServiceImpl.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/service/impl/AdminQuestionHitKServiceImpl.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkQuestionGenerateRequest.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKQuestionGenerateRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkQuestionUpdateRequest.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKQuestionUpdateRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkTestCreateRequest.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKTestCreateRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkTaskBatchStatisticsRequest.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKTaskBatchStatisticsRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkTaskBatchDeleteRequest.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKTaskBatchDeleteRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/dto/AdminHitkTaskRemarkUpdateRequest.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/request/AdminHitKTaskRemarkUpdateRequest.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkQuestionBatchResponse.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKQuestionBatchResponse.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkTaskPageResponse.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKTaskPageResponse.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkTaskStatisticsResponse.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKTaskStatisticsResponse.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkTaskBatchDeleteResponse.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKTaskBatchDeleteResponse.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkTaskVO.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKTaskResponse.java`
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/model/vo/AdminHitkTaskDetailVO.java` -> `agent-oj-admin/src/main/java/com/oj/agent/admin/question/hitk/model/response/AdminHitKTaskDetailResponse.java`
- Move: `agent-oj-admin/src/test/java/com/oj/agent/admin/service/impl/AdminQuestionHitkServiceImplTest.java` -> `agent-oj-admin/src/test/java/com/oj/agent/admin/question/hitk/service/impl/AdminQuestionHitKServiceImplTest.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/hitk/converter/AdminQuestionHitKConverterTest.java`
- Create: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/hitk/controller/AdminQuestionHitKControllerTest.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/exception/AdminQuestionManagementExceptionHandler.java` to remove `AdminQuestionHitkController` from `@RestControllerAdvice(assignableTypes = ...)` once `AdminQuestionHitKExceptionHandler` exists

- [ ] **Step 1: Write the failing hitk converter and controller tests**

```java
@Test
void toResponse_shouldMapHitkTaskPayload() {
    // mapping test
}
```

- [ ] **Step 2: Run the focused hitk tests to verify they fail**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionHitKConverterTest,AdminQuestionHitKServiceImplTest,AdminQuestionHitKControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: FAIL because the new hitk package and contracts do not exist yet.

- [ ] **Step 3: Implement the hitk command/query/result/response layer, package moves, converter, and exception-handler split**

- [ ] **Step 4: Re-run the focused hitk tests**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionHitKConverterTest,AdminQuestionHitKServiceImplTest,AdminQuestionHitKControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: PASS

- [ ] **Step 5: Run the module compile gate**

Run: `mvn -pl agent-oj-admin -am -DskipTests compile`
Expected: PASS

### Task 6: Final Root Cleanup And Compatibility Sweep

**Files:**
- Move: `agent-oj-admin/src/main/java/com/oj/agent/admin/config/AdminUserManagementExceptionHandler.java`
- Create if needed: `agent-oj-admin/src/main/java/com/oj/agent/admin/user/config/AdminUserManagementExceptionHandler.java`
- Modify any remaining imports in `agent-oj-admin/src/main/java/com/oj/agent/admin/**` that still point at root `model.dto`, `model.vo`, root `controller`, or root `service.impl`
- Delete any emptied root packages only if they are empty after the above moves

- [ ] **Step 1: Run a repository-wide search for remaining admin root DTO/VO leaks**

Run: `rg -n "com\\.oj\\.agent\\.admin\\.(model\\.(dto|vo)|controller\\.|service\\.impl\\.)" agent-oj-admin/src/main/java agent-oj-admin/src/test/java`
Expected: only intentional legacy references during the active round, or no matches after the final cleanup.

- [ ] **Step 2: Move the remaining user exception handler into the user domain**

- [ ] **Step 3: Run the full agent-oj-admin test suite**

Run: `mvn -pl agent-oj-admin -am test`
Expected: PASS

- [ ] **Step 4: Confirm the module has no remaining root-level admin domain packages except shared technical packages that are intentionally generic**

Run: `Get-ChildItem 'agent-oj-admin/src/main/java/com/oj/agent/admin' -Directory | Select-Object -ExpandProperty Name`
Expected: domain-owned packages only, plus shared technical packages that are still intentionally generic and empty of domain POJOs.
