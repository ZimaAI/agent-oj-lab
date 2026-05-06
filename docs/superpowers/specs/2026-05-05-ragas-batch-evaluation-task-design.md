# RAGAS Batch Evaluation Task Design

## Background

The admin-side RAGAS flow currently supports batch operations at the segment-record level, but it does not persist a task-level batch execution result.

Today the system can:

- generate RAGAS question and standard answer records into `knowledge_segment_ragas`
- generate model answers and retrieved contexts for those records
- call the external RAGAS service and write metric fields back onto each `knowledge_segment_ragas` row

However, it cannot yet:

- submit one admin-side batch evaluation as a first-class task
- view those batch executions in a dedicated task list
- show batch-level average values for the four main RAGAS metrics

The admin UI already has a strong reference implementation for task-oriented workflows in the Hit@K domain:

- `QuestionDocumentSegmentsView.vue` shows current-document task history
- `KnowledgeSegmentHitkTaskView.vue` shows global task paging, statistics, and detail dialogs
- `knowledge_segment_hitk_task` persists task-level snapshots and summary fields

This feature should add the equivalent task capability for RAGAS without collapsing the RAGAS flow into the Hit@K task model.

## Goal

Add admin-side RAGAS batch evaluation tasks so that administrators can:

1. submit a batch RAGAS evaluation task from the knowledge-segment page and the document-segment page
2. view RAGAS batch execution records in a dedicated RAGAS task list
3. see batch-level average values for the four RAGAS metrics:
   - `answerRelevancy`
   - `faithfulness`
   - `contextPrecision`
   - `contextRecall`

The average values must include only successful records from the current batch. Failed records do not participate in the averages.

## Confirmed Product Decisions

The following choices were explicitly confirmed during brainstorming:

- RAGAS batch executions are shown in a dedicated RAGAS task list, not merged into the existing Hit@K task list
- partial failures do not contribute to metric averages
- the global admin page keeps the current route and adds a new RAGAS task block alongside the existing Hit@K task block
- when no successful record exists for a metric in a batch, the backend returns `null` and the frontend displays `-`

## Scope

In scope:

- backend schema and model additions for RAGAS task persistence
- admin-side controller, service, converter, request, query, result, and response types for RAGAS task submission and querying
- reuse of existing record-level RAGAS evaluation logic when possible
- admin UI API types and request functions for RAGAS tasks
- `QuestionDocumentSegmentsView.vue` changes so the current RAGAS evaluation action submits a task
- `KnowledgeSegmentHitkTaskView.vue` changes so it renders a new RAGAS task area beside the existing Hit@K task area
- backend and frontend tests for the new task flow

Out of scope:

- merging Hit@K and RAGAS into a shared generic task framework
- changing the meaning of existing Hit@K APIs
- changing the current RAGAS per-record table into a batch-only model
- asynchronous queue-based execution
- retry-from-task workflow

## Approaches

### Option 1: Dedicated RAGAS task table and task APIs

Persist one row per batch execution and store task-level summary fields plus a batch detail snapshot.

Pros:

- matches the proven Hit@K task pattern
- keeps RAGAS record data and task data separate
- makes task paging, details, and averages straightforward
- leaves room for future task remark, retry, and task-level filtering features

Cons:

- requires a new table, entity, mapper, and API surface

### Option 2: No task table, derive batches from `knowledge_segment_ragas`

Mark records with batch metadata and reconstruct task history by grouping record rows.

Pros:

- fewer initial schema additions

Cons:

- task history becomes metadata-driven rather than domain-driven
- grouping, pagination, and detail reconstruction become awkward
- encourages overloading `metadata` as a dumping ground
- much harder to extend cleanly later

### Option 3: Shared generic evaluation task framework for Hit@K and RAGAS

Generalize both domains into a common task abstraction.

Pros:

- long-term architectural neatness

Cons:

- significantly larger scope than the requested feature
- higher regression risk
- forces an infrastructure refactor before delivering user-visible value

## Recommendation

Use Option 1.

This is the best fit for the current codebase because:

- Hit@K already establishes the preferred task-oriented shape
- the user asked for a dedicated RAGAS task list rather than a unified task abstraction
- the feature is naturally incremental and does not require a cross-domain refactor

## Target Domain Model

### Existing Per-Record Model Remains

`knowledge_segment_ragas` keeps its current role:

- one row per segment RAGAS record
- stores generated question, standard answer, generated answer, retrieved contexts, status, and detailed metric fields

This table remains the source of truth for per-segment RAGAS data.

### New Task Model

Add a new core RAG table named `knowledge_segment_ragas_task`.

Suggested fields:

- `id`
- `question_id`
- `document_id`
- `total_count`
- `success_count`
- `failure_count`
- `average_answer_relevancy`
- `average_faithfulness`
- `average_context_precision`
- `average_context_recall`
- `status`
- `error_message`
- `details_json`
- `is_delete`
- `create_time`
- `update_time`

Suggested semantics:

- `total_count`: total number of target RAGAS records in the batch
- `success_count`: number of records that produced usable evaluation values
- `failure_count`: number of records that failed or did not return a usable evaluation result
- average fields: calculated independently and only from successful records with non-null values for that metric
- `details_json`: batch-level snapshot for UI detail rendering

### Task Detail Snapshot

`details_json` should capture enough data for the task detail dialog without forcing multiple follow-up joins.

Each detail item should include at least:

- `ragasId`
- `segmentId`
- `questionId`
- `documentId`
- `status`
- `errorMessage`
- `answerRelevancy`
- `faithfulness`
- `contextPrecision`
- `contextRecall`
- `overallScore`

Optionally include:

- `ragasQuestion`
- `standardAnswer`
- `generatedAnswer`

If payload size becomes a concern, the first version may omit these longer text fields from the task snapshot and load them from the record table in the detail query. The preferred first implementation is still to keep the detail response self-sufficient.

## Backend Design

### Package Ownership

Follow the repository rules and keep the new task contracts inside the RAGAS admin domain:

- `com.oj.agent.admin.question.ragas.controller`
- `com.oj.agent.admin.question.ragas.service`
- `com.oj.agent.admin.question.ragas.service.impl`
- `com.oj.agent.admin.question.ragas.converter`
- `com.oj.agent.admin.question.ragas.model.request`
- `com.oj.agent.admin.question.ragas.model.response`
- `com.oj.agent.admin.question.ragas.model.command`
- `com.oj.agent.admin.question.ragas.model.query`
- `com.oj.agent.admin.question.ragas.model.result`

Core persistence types belong in the core RAG domain:

- `agent-oj-core/.../rag/model/entity`
- `agent-oj-core/.../rag/mapper`
- `agent-oj-core/.../rag/service`
- `agent-oj-core/.../rag/service/impl`

### New Backend Contracts

Suggested admin-side request/query/result/response types:

- submit task:
  - `AdminRagasTaskCreateRequest`
  - `AdminRagasTaskCreateCommand`
  - `AdminRagasTaskResult`
  - `AdminRagasTaskResponse`
- page global tasks:
  - `AdminRagasTaskPageQuery`
  - `AdminRagasTaskPageResult`
  - `AdminRagasTaskPageResponse`
- get task detail:
  - `AdminRagasTaskDetailQuery`
  - `AdminRagasTaskDetailResult`
  - `AdminRagasTaskDetailResponse`
- list current-document tasks:
  - `AdminRagasTaskListQuery`
  - `List<AdminRagasTaskResult>`

### HTTP APIs

Add new admin routes:

- `POST /api/admin/questions/{questionId}/documents/{docId}/segments/ragas/tasks`
  - submit a batch RAGAS evaluation task for the selected segments
- `GET /api/admin/questions/{questionId}/documents/{docId}/segments/ragas/tasks`
  - list RAGAS tasks for one document
- `GET /api/admin/questions/ragas-tasks`
  - page global RAGAS tasks with `current`, `size`, `status`, `startTime`, `endTime`
- `GET /api/admin/questions/ragas-tasks/{taskId}`
  - get one RAGAS task detail

The existing route:

- `POST /api/admin/questions/{questionId}/documents/{docId}/segments/ragas/evaluate`

should not remain the primary UI entry for batch execution. To preserve compatibility and avoid an abrupt contract break, the preferred implementation is:

- keep the old route for existing callers if needed
- add the new `/ragas/tasks` route for the admin UI task-based workflow

If no compatibility consumers exist, the old route may be internally redirected to the new task-submission logic, but the external behavior change should still be explicit and tested.

### Service Flow

Task submission should execute in this order:

1. validate `questionId`, `docId`, and `segmentIds`
2. resolve target `knowledge_segment_ragas` records by selected segments
3. execute record-level RAGAS evaluation using the existing evaluation path
4. update per-record metric fields and statuses in `knowledge_segment_ragas`
5. calculate task summary fields from the post-evaluation results
6. persist one `knowledge_segment_ragas_task`
7. return the persisted task summary

The task flow should reuse existing evaluation code where possible, but the task summary calculation must be explicit and testable rather than embedded as UI-side aggregation.

### Status Rules

Task-level status should stay simple:

- `COMPLETED`: the batch finished execution and produced a summary, even if some items failed
- `FAILED`: the entire batch failed before a meaningful summary could be produced

Record-level status remains in `knowledge_segment_ragas`:

- `EVALUATED`
- `FAILED`

There is no need for a separate `PARTIAL_SUCCESS` task status. Partial failure is fully represented by:

- `successCount`
- `failureCount`
- detail item `status`
- detail item `errorMessage`

### Average Calculation Rules

The four task-level averages are:

- `averageAnswerRelevancy`
- `averageFaithfulness`
- `averageContextPrecision`
- `averageContextRecall`

Rules:

- only successful records participate
- each metric is averaged independently
- records with `null` for a metric do not contribute to that metric's divisor
- values use `BigDecimal`
- round to 4 decimal places, matching the Hit@K task display style
- if a metric has no successful non-null values in the batch, store and return `null`

### Error Handling

#### Pre-submission validation

- empty `segmentIds` is rejected
- segments outside the current question/document are rejected
- missing RAGAS records are rejected at the batch boundary

#### Record-level failures

If a record cannot be evaluated because of missing question, standard answer, generated answer, or missing service result:

- mark the record as failed
- include failure information in the task detail snapshot
- do not abort the whole batch

#### Batch-level failures

If the external RAGAS call fails for the whole request:

- mark the task as `FAILED`
- set task `errorMessage`
- mark all detail items as failed
- return the failed task summary

## Frontend Design

### API Layer

Extend `oj-admin-ui/src/api/adminQuestion.ts` with:

- `AdminRagasTaskVO`
- `AdminRagasTaskDetailVO`
- `AdminRagasTaskPageRequest`
- `AdminRagasTaskPageResponse`
- `AdminRagasTaskCreateRequest`
- API methods for create/list/page/detail

Naming should match the existing file conventions first, even if the view models are later cleaned up in a broader refactor.

### Document Segment Page

Update `QuestionDocumentSegmentsView.vue`.

Current behavior:

- the RAGAS evaluation button directly runs evaluation and shows a completion message

Target behavior:

- the button submits a batch RAGAS task
- success message becomes task-oriented, for example:
  - `Submitted RAGAS batch evaluation task #123.`
- after success:
  - refresh current segment RAGAS records
  - refresh current-document RAGAS task history

Add a current-document RAGAS task history block that mirrors the existing document-level Hit@K task section:

- latest task summary
- task history table
- task detail modal entry

### Global Knowledge Segment Page

Update `KnowledgeSegmentHitkTaskView.vue`.

Keep the current route and page purpose, but add a new RAGAS task area as a peer block beside the existing Hit@K task area.

Recommended behavior:

- keep the top segment list and batch-operation toolbar
- keep the existing Hit@K task area
- add a new RAGAS task area with:
  - filters: `status`, `startTime`, `endTime`
  - actions: search, reset, refresh
  - summary cards: task count and the four averages
  - task table: task id, question/document, status, total count, success count, failure count, four averages, created time
  - actions: detail, open segment page

### Task Detail Dialog

Add a new RAGAS task detail modal similar to the Hit@K task detail modal.

The dialog should show:

- task metadata:
  - task id
  - question id
  - document id
  - status
  - created time
  - total count
  - success count
  - failure count
- per-item execution details:
  - segment id
  - optional RAGAS texts
  - four metrics
  - overall score
  - failure message

### Empty and Null Rendering

Frontend rendering rules:

- average fields returned as `null` display as `-`
- failed detail items display metric placeholders rather than `0`
- task-level failure shows both status and error message

## Testing Strategy

### Backend Tests

Add focused tests around the new task workflow:

- `AdminQuestionRagasServiceImplTest`
  - creates a task summary from batch evaluation results
  - averages only successful records
  - handles whole-batch external failure as task `FAILED`
  - handles missing per-record evaluation results without aborting the batch
- `AdminQuestionRagasControllerTest`
  - verifies the new routes
  - verifies request-to-command and result-to-response conversion
- converter tests
  - task request/query/response mapping

Core persistence and helper tests may be added if task aggregation logic becomes large enough to warrant extraction.

### Frontend Tests

Add or extend tests under `oj-admin-ui/src/views/__tests__` to cover:

- submitting a RAGAS task from segment selection
- rendering the new RAGAS task section
- showing `-` for `null` averages
- opening task detail
- navigating from a task row back to the relevant segment page

## Incremental Delivery Order

1. add schema and core persistence support for `knowledge_segment_ragas_task`
2. add backend RAGAS task models, converter paths, service logic, and APIs
3. extend frontend API bindings and types
4. update `QuestionDocumentSegmentsView.vue` to submit and display document-level RAGAS tasks
5. update `KnowledgeSegmentHitkTaskView.vue` to render the new global RAGAS task area
6. verify focused backend tests, frontend tests, and type/build gates

## Risks and Mitigations

- Risk: task detail payload becomes too large
  - Mitigation: keep the first version focused on the fields needed by the dialog and avoid unrelated snapshot data
- Risk: record-level evaluation logic and task-level summary logic drift apart
  - Mitigation: centralize aggregation in one helper method with direct unit coverage
- Risk: UI complexity grows inside already-large Vue files
  - Mitigation: prefer extracting RAGAS task helpers or composable logic if the diff starts to overload the current files

## Expected Outcome

After this feature:

- admins can submit RAGAS batch evaluation as a first-class task from both relevant admin pages
- the system preserves both record-level scores and task-level batch history
- the global admin page shows Hit@K tasks and RAGAS tasks as separate peer blocks
- batch averages are trustworthy because they are computed only from successful records
