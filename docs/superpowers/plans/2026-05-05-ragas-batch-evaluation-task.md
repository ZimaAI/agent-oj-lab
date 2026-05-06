# RAGAS Batch Evaluation Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add admin-side RAGAS batch evaluation tasks so administrators can submit batch evaluation jobs, browse a dedicated RAGAS task list, and see four-metric averages calculated only from successful records.

**Architecture:** Keep `knowledge_segment_ragas` as the per-record source of truth and introduce a separate `knowledge_segment_ragas_task` persistence model for batch snapshots. Extend the existing admin RAGAS domain with task-focused request/query/result/response contracts and reuse the current record-level evaluation logic behind a task-submission API. Update the two existing admin views in place so they stay aligned with the repo's established page structure.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, Maven, JUnit 5, Mockito, AssertJ, Vue 3, TypeScript, Vitest

---

Spec reference: `docs/superpowers/specs/2026-05-05-ragas-batch-evaluation-task-design.md`

Execution prerequisite: create and use an isolated worktree before implementation (`superpowers:using-git-worktrees`).

Shared verification gates:

- Backend focused tests: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionRagasConverterTest,AdminQuestionRagasControllerTest,AdminQuestionRagasServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- Backend compile gate: `mvn -pl agent-oj-admin -am -DskipTests compile`
- Frontend focused tests: `npm --prefix oj-admin-ui run test:unit -- src/views/__tests__/questionDocumentSegments.ragas-task.contract.test.ts src/views/__tests__/knowledgeSegmentHitkTask.ragas-task.contract.test.ts src/views/__tests__/questionDocumentSegments.selection.contract.test.ts src/views/__tests__/knowledgeSegmentHitkTask.selection.contract.test.ts`
- Frontend type gate: `npm --prefix oj-admin-ui run type-check`

## File Map

### Persistence and Core RAG Files

- Modify: `docs/script/mysql/agent_oj_workflow_ddl.sql`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/entity/KnowledgeSegmentRagasTask.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/dto/KnowledgeSegmentRagasTaskDetailDTO.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/mapper/KnowledgeSegmentRagasTaskMapper.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/KnowledgeSegmentRagasTaskService.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentRagasTaskServiceImpl.java`

### Admin Backend RAGAS Files

- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/controller/AdminQuestionRagasController.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/converter/AdminQuestionRagasConverter.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/service/AdminQuestionRagasService.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/service/impl/AdminQuestionRagasServiceImpl.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/request/AdminRagasTaskCreateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/command/AdminRagasTaskCreateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/query/AdminRagasTaskListQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/query/AdminRagasTaskPageQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/query/AdminRagasTaskDetailQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/result/AdminRagasTaskResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/result/AdminRagasTaskPageResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/result/AdminRagasTaskDetailResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/response/AdminRagasTaskResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/response/AdminRagasTaskPageResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/response/AdminRagasTaskDetailResponse.java`
- Modify: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/ragas/converter/AdminQuestionRagasConverterTest.java`
- Modify: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/ragas/controller/AdminQuestionRagasControllerTest.java`
- Modify: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/ragas/service/impl/AdminQuestionRagasServiceImplTest.java`

### Frontend API and View Files

- Modify: `oj-admin-ui/src/api/adminQuestion.ts`
- Modify: `oj-admin-ui/src/views/QuestionDocumentSegmentsView.vue`
- Modify: `oj-admin-ui/src/views/KnowledgeSegmentHitkTaskView.vue`
- Create: `oj-admin-ui/src/views/__tests__/questionDocumentSegments.ragas-task.contract.test.ts`
- Create: `oj-admin-ui/src/views/__tests__/knowledgeSegmentHitkTask.ragas-task.contract.test.ts`

### Responsibilities

- `KnowledgeSegmentRagasTask*` owns task persistence and current-document task lookup
- admin `AdminRagasTask*` contracts own HTTP and service boundaries for create/list/page/detail flows
- `AdminQuestionRagasServiceImpl` owns orchestration, average calculation, batch status rules, and task snapshot creation
- `QuestionDocumentSegmentsView.vue` owns submitting a document-scoped task and showing document task history
- `KnowledgeSegmentHitkTaskView.vue` owns the global RAGAS task list, summary cards, and task detail dialog

### Task 1: Add RAGAS Task Persistence and Admin APIs

**Files:**

- Modify: `docs/script/mysql/agent_oj_workflow_ddl.sql`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/entity/KnowledgeSegmentRagasTask.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/dto/KnowledgeSegmentRagasTaskDetailDTO.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/mapper/KnowledgeSegmentRagasTaskMapper.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/KnowledgeSegmentRagasTaskService.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentRagasTaskServiceImpl.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/controller/AdminQuestionRagasController.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/converter/AdminQuestionRagasConverter.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/service/AdminQuestionRagasService.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/service/impl/AdminQuestionRagasServiceImpl.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/request/AdminRagasTaskCreateRequest.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/command/AdminRagasTaskCreateCommand.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/query/AdminRagasTaskListQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/query/AdminRagasTaskPageQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/query/AdminRagasTaskDetailQuery.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/result/AdminRagasTaskResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/result/AdminRagasTaskPageResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/result/AdminRagasTaskDetailResult.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/response/AdminRagasTaskResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/response/AdminRagasTaskPageResponse.java`
- Create: `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/model/response/AdminRagasTaskDetailResponse.java`
- Modify: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/ragas/converter/AdminQuestionRagasConverterTest.java`
- Modify: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/ragas/controller/AdminQuestionRagasControllerTest.java`
- Modify: `agent-oj-admin/src/test/java/com/oj/agent/admin/question/ragas/service/impl/AdminQuestionRagasServiceImplTest.java`

- [ ] **Step 1: Write the failing backend tests for the new task boundaries**

```java
@Test
void createRagasTask_shouldAverageOnlySuccessfulRecords() {
    // given two successful records and one failed record
    // when createRagasTask runs
    // then averageAnswerRelevancy and the other three averages
    // are computed only from the successful record values
}

@Test
void taskRoutes_shouldExposeCreateListPageAndDetailEndpoints() throws Exception {
    assertThat(create.getAnnotation(PostMapping.class).value())
            .containsExactly("/{questionId}/documents/{docId}/segments/ragas/tasks");
    assertThat(page.getAnnotation(GetMapping.class).value())
            .containsExactly("/ragas-tasks");
}
```

- [ ] **Step 2: Run the focused backend tests to verify they fail**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionRagasConverterTest,AdminQuestionRagasControllerTest,AdminQuestionRagasServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL with missing `AdminRagasTask*` classes, missing controller methods, or missing service methods.

- [ ] **Step 3: Add the new DDL and core persistence classes**

```sql
create table knowledge_segment_ragas_task
(
    id                         bigint auto_increment primary key,
    question_id                bigint not null,
    document_id                bigint not null,
    total_count                int default 0 not null,
    success_count              int default 0 not null,
    failure_count              int default 0 not null,
    average_answer_relevancy   decimal(10, 4) null,
    average_faithfulness       decimal(10, 4) null,
    average_context_precision  decimal(10, 4) null,
    average_context_recall     decimal(10, 4) null,
    status                     varchar(32) not null,
    error_message              varchar(2048) null,
    details_json               longtext null,
    is_delete                  int default 0 not null,
    create_time                datetime default CURRENT_TIMESTAMP null,
    update_time                datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
);
```

- [ ] **Step 4: Implement the admin RAGAS task contracts, controller methods, and orchestration**

```java
public interface AdminQuestionRagasService {
    AdminRagasTaskResult createRagasTask(AdminRagasTaskCreateCommand command);
    List<AdminRagasTaskResult> listRagasTasks(AdminRagasTaskListQuery query);
    AdminRagasTaskPageResult pageRagasTasks(AdminRagasTaskPageQuery query);
    AdminRagasTaskDetailResult getRagasTaskDetail(AdminRagasTaskDetailQuery query);
}
```

Implementation notes:

- reuse the existing record-level evaluation path rather than duplicating the external RAGAS call
- extract one private helper that:
  - evaluates the selected record set
  - computes `successCount` and `failureCount`
  - computes the four independent averages
  - builds `detailsJson`
- treat whole-batch external failure as task `FAILED`
- treat partial failures as task `COMPLETED` with per-item failures in details
- return `null` averages when no successful non-null value exists for that metric

- [ ] **Step 5: Run the focused backend tests again**

Run: `mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionRagasConverterTest,AdminQuestionRagasControllerTest,AdminQuestionRagasServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS

- [ ] **Step 6: Run the backend compile gate**

Run: `mvn -pl agent-oj-admin -am -DskipTests compile`

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit the backend task work**

```bash
git add docs/script/mysql/agent_oj_workflow_ddl.sql agent-oj-core/src/main/java/com/oj/agent/core/rag agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas agent-oj-admin/src/test/java/com/oj/agent/admin/question/ragas
git commit -m "feat: add ragas batch task backend"
```

### Task 2: Submit and Display Document-Scoped RAGAS Tasks

**Files:**

- Modify: `oj-admin-ui/src/api/adminQuestion.ts`
- Modify: `oj-admin-ui/src/views/QuestionDocumentSegmentsView.vue`
- Create: `oj-admin-ui/src/views/__tests__/questionDocumentSegments.ragas-task.contract.test.ts`

- [ ] **Step 1: Write the failing document-page contract test**

```ts
it('submits ragas evaluation through the task endpoint', () => {
  expect(viewSource).toContain('createQuestionDocumentSegmentRagasTask(')
  expect(viewSource).not.toContain('evaluateQuestionDocumentSegmentRagas(')
})

it('renders document ragas task history', () => {
  expect(viewSource).toContain('RAGAS Tasks')
  expect(viewSource).toContain('loadRagasTasks')
})
```

- [ ] **Step 2: Run the focused document-page contract test to verify it fails**

Run: `npm --prefix oj-admin-ui run test:unit -- src/views/__tests__/questionDocumentSegments.ragas-task.contract.test.ts`

Expected: FAIL because the task endpoint call and document task history block do not exist yet.

- [ ] **Step 3: Extend the frontend API file with RAGAS task request and response types**

```ts
export interface AdminRagasTaskVO {
  taskId: number
  questionId: number
  documentId: number
  totalCount: number
  successCount: number
  failureCount: number
  averageAnswerRelevancy: number | null
  averageFaithfulness: number | null
  averageContextPrecision: number | null
  averageContextRecall: number | null
  status: string | null
  errorMessage: string | null
  createTime: string | null
}
```

Add methods:

- `createQuestionDocumentSegmentRagasTask`
- `listQuestionDocumentSegmentRagasTasks`
- `getRagasTaskDetail`

- [ ] **Step 4: Update `QuestionDocumentSegmentsView.vue` to submit tasks and show document task history**

Implementation notes:

- replace the current selection action so the RAGAS button submits `segmentIds` to `/segments/ragas/tasks`
- update the success message to include the returned task id
- keep reloading the segment RAGAS records after task submission
- add a document-level RAGAS task summary/history section mirroring the current Hit@K task section
- add a RAGAS task detail modal entry point from the history table

- [ ] **Step 5: Run the document-page contract test again**

Run: `npm --prefix oj-admin-ui run test:unit -- src/views/__tests__/questionDocumentSegments.ragas-task.contract.test.ts`

Expected: PASS

- [ ] **Step 6: Commit the document-page RAGAS task work**

```bash
git add oj-admin-ui/src/api/adminQuestion.ts oj-admin-ui/src/views/QuestionDocumentSegmentsView.vue oj-admin-ui/src/views/__tests__/questionDocumentSegments.ragas-task.contract.test.ts
git commit -m "feat: add document ragas task history"
```

### Task 3: Add the Global RAGAS Task Panel Beside Hit@K Tasks

**Files:**

- Modify: `oj-admin-ui/src/api/adminQuestion.ts`
- Modify: `oj-admin-ui/src/views/KnowledgeSegmentHitkTaskView.vue`
- Create: `oj-admin-ui/src/views/__tests__/knowledgeSegmentHitkTask.ragas-task.contract.test.ts`

- [ ] **Step 1: Write the failing global-page contract test**

```ts
it('adds a dedicated ragas task block beside hitk tasks', () => {
  expect(viewSource).toContain('RAGAS 任务')
  expect(viewSource).toContain('pageRagasTasks')
  expect(viewSource).toContain('ragasTaskStatistics')
})

it('renders null ragas averages as a dash', () => {
  expect(viewSource).toContain("return value == null ? '-' :")
})
```

- [ ] **Step 2: Run the focused global-page contract test to verify it fails**

Run: `npm --prefix oj-admin-ui run test:unit -- src/views/__tests__/knowledgeSegmentHitkTask.ragas-task.contract.test.ts`

Expected: FAIL because the dedicated RAGAS task block and related state do not exist yet.

- [ ] **Step 3: Implement global RAGAS task paging, summary cards, and detail dialog**

Implementation notes:

- keep the current route and top segment list unchanged
- add a new peer block to the existing Hit@K task area rather than a new route
- add separate RAGAS task query state instead of reusing the Hit@K task query
- add display helpers for nullable averages so `null` becomes `-`
- support:
  - page/search/reset/refresh
  - view detail
  - open the corresponding document segment page

- [ ] **Step 4: Run the focused global-page contract and existing selection tests**

Run: `npm --prefix oj-admin-ui run test:unit -- src/views/__tests__/knowledgeSegmentHitkTask.ragas-task.contract.test.ts src/views/__tests__/knowledgeSegmentHitkTask.selection.contract.test.ts`

Expected: PASS

- [ ] **Step 5: Run the frontend type gate**

Run: `npm --prefix oj-admin-ui run type-check`

Expected: no TypeScript errors

- [ ] **Step 6: Commit the global-page RAGAS task work**

```bash
git add oj-admin-ui/src/api/adminQuestion.ts oj-admin-ui/src/views/KnowledgeSegmentHitkTaskView.vue oj-admin-ui/src/views/__tests__/knowledgeSegmentHitkTask.ragas-task.contract.test.ts
git commit -m "feat: add global ragas task panel"
```

### Task 4: Run Final Verification and Close Gaps

**Files:**

- Modify only if verification uncovers issues in:
  - `agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas/**`
  - `agent-oj-core/src/main/java/com/oj/agent/core/rag/**`
  - `oj-admin-ui/src/api/adminQuestion.ts`
  - `oj-admin-ui/src/views/QuestionDocumentSegmentsView.vue`
  - `oj-admin-ui/src/views/KnowledgeSegmentHitkTaskView.vue`
  - matching tests

- [ ] **Step 1: Run the full focused verification set**

Run:

```bash
mvn -pl agent-oj-admin -am "-Dtest=AdminQuestionRagasConverterTest,AdminQuestionRagasControllerTest,AdminQuestionRagasServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
npm --prefix oj-admin-ui run test:unit -- src/views/__tests__/questionDocumentSegments.ragas-task.contract.test.ts src/views/__tests__/knowledgeSegmentHitkTask.ragas-task.contract.test.ts src/views/__tests__/questionDocumentSegments.selection.contract.test.ts src/views/__tests__/knowledgeSegmentHitkTask.selection.contract.test.ts
npm --prefix oj-admin-ui run type-check
```

Expected:

- backend focused tests pass
- frontend contract tests pass
- no type errors

- [ ] **Step 2: Run one final backend compile gate**

Run: `mvn -pl agent-oj-admin -am -DskipTests compile`

Expected: BUILD SUCCESS

- [ ] **Step 3: Inspect the diff for accidental scope creep**

Run:

```bash
git diff --stat
git diff -- agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas agent-oj-core/src/main/java/com/oj/agent/core/rag oj-admin-ui/src
```

Expected: changes stay limited to the planned RAGAS task files and their tests.

- [ ] **Step 4: Commit the verification cleanup if needed**

```bash
git add agent-oj-admin/src/main/java/com/oj/agent/admin/question/ragas agent-oj-core/src/main/java/com/oj/agent/core/rag oj-admin-ui/src
git commit -m "test: verify ragas task flow"
```
