# Agent OJ Core Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `agent-oj-core` round-by-round so the touched domains comply with `AGENTS.md` layering, package, naming, and cross-domain dependency rules without breaking current behavior.

**Architecture:** Execute the refactor in three rounds. Round 1 first extracts only the minimum stable contracts that `workflow` consumes from `question`, `submission`, `evaluation`, `trace`, and `conversation`, re-homes trace and shared helper ownership, then converges `workflow` to request/command/result/response boundaries. Round 2 cleans `question` and `rag` together to remove their bidirectional leakage and misplaced model ownership, including same-round migration of known downstream consumers of moved question-owned entities. Round 3 completes the public-boundary cleanup of `submission`, `trace`, `file`, and `evaluation`, then finishes repo-wide downstream migration and verification.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, Spring AI Graph, JUnit 5, Mockito, AssertJ, Maven

---

Spec reference: `docs/superpowers/specs/2026-05-02-agent-oj-core-refactor-design.md`

### Task 1: Introduce Workflow-Owned Request Command Result Response Types

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/request/CodeEvaluationStreamRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/command/WorkflowStartCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/result/WorkflowBootstrapResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/result/AnswerRagSegmentResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/result/AnswerHitKGraphResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/result/AnswerRagasGraphResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/converter/WorkflowConverter.java`
- Move and modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/GraphNodeResponse.java` -> `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/response/GraphNodeResponse.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/controller/WorkflowController.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowInputMapBuilder.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/AnswerHitkGraphService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/AnswerRagasGraphService.java`
- Remove after migration: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/WorkflowStartRequest.java`
- Remove after migration: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/WorkflowBootstrapDTO.java`
- Remove after migration: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/model/AnswerHitkGraphResult.java`
- Remove after migration: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/model/AnswerRagasGraphResult.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/controller/WorkflowControllerTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/service/WorkflowBootstrapServiceTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/converter/WorkflowConverterTest.java`

- [ ] **Step 1: Write the failing workflow carrier tests**

```java
@Test
void toStartCommand_shouldWrapCodeEvaluationRequest() {
    WorkflowStartCommand command = WorkflowConverter.toStartCommand(new UserMessageRequest(), null);
    assertThat(command.getUserMessageRequest()).isNotNull();
}
```

- [ ] **Step 2: Run the workflow carrier tests to verify they fail**

Run: `mvn -pl agent-oj-core -Dtest=WorkflowConverterTest,WorkflowControllerTest,WorkflowBootstrapServiceTest test`
Expected: FAIL because `WorkflowStartCommand`, `WorkflowBootstrapResult`, and `workflow.model.response.GraphNodeResponse` do not exist yet.

- [ ] **Step 3: Create the workflow-owned request/command/result/response types and migrate the controller/service entry path**

```java
public class WorkflowStartCommand {
    private boolean codeEvaluation;
    private UserMessageRequest userMessageRequest;
    private CodeEvaluationStreamRequest codeEvaluationRequest;
}
```

- [ ] **Step 4: Run the workflow carrier tests to verify they pass**

Run: `mvn -pl agent-oj-core -Dtest=WorkflowConverterTest,WorkflowControllerTest,WorkflowBootstrapServiceTest test`
Expected: PASS

- [ ] **Step 5: Commit the workflow carrier migration**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/workflow/model agent-oj-core/src/main/java/com/oj/agent/core/workflow/converter/WorkflowConverter.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/controller/WorkflowController.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowService.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowInputMapBuilder.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/AnswerHitkGraphService.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/AnswerRagasGraphService.java agent-oj-core/src/test/java/com/oj/agent/core/workflow
git commit -m "refactor: normalize workflow boundary carriers"
```

### Task 2: Extract Trace Ownership From Workflow

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/command/TraceStartCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/command/TraceCompleteCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/command/TraceItemStartCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/command/TraceItemFinishCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/result/TracePayloadResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/result/TraceTokenUsageResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/result/TraceStatusResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/result/TraceItemTypeResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/service/WorkflowTraceService.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/service/impl/WorkflowTraceServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/trace/WorkflowTraceRecorder.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/trace/WorkflowTraceSupport.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/util/FluxUtil.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/util/ChatResponseUtil.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/AnswerRewriteNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/AnswerRAGNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeEvaluationNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeQuestionNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/IntentRecognitionNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/JavaCodeNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/JavaScriptCodeNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/MemoryCompressNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/OJAssistantNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/PythonCodeNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/QuestionRAGNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/QuestionRewriteNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/RAGJudgeNode.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/trace/service/WorkflowTraceServiceImplTest.java`

- [ ] **Step 1: Write the failing trace extraction test**

```java
@Test
void startTrace_shouldPersistThroughTraceDomainCommand() {
    workflowTraceService.startTrace(new TraceStartCommand());
    verify(traceService).save(any());
}
```

- [ ] **Step 2: Run the trace extraction test to verify it fails**

Run: `mvn -pl agent-oj-core -Dtest=WorkflowTraceServiceImplTest test`
Expected: FAIL because trace commands and `WorkflowTraceService` do not exist yet.

- [ ] **Step 3: Move trace command/value ownership into the trace domain and update workflow callers to use the trace service**

```java
public interface WorkflowTraceService {
    void startTrace(TraceStartCommand command);
    void completeTrace(TraceCompleteCommand command);
    void startTraceItem(TraceItemStartCommand command);
    void finishTraceItem(TraceItemFinishCommand command);
}
```

- [ ] **Step 4: Run the trace extraction test and workflow smoke tests**

Run: `mvn -pl agent-oj-core -Dtest=WorkflowTraceServiceImplTest,WorkflowBootstrapServiceTest test`
Expected: PASS

- [ ] **Step 5: Commit the trace ownership extraction**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/trace agent-oj-core/src/main/java/com/oj/agent/core/workflow/trace agent-oj-core/src/main/java/com/oj/agent/core/workflow/util/FluxUtil.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/util/ChatResponseUtil.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/node agent-oj-core/src/test/java/com/oj/agent/core/trace/service/WorkflowTraceServiceImplTest.java
git commit -m "refactor: rehome workflow trace contracts"
```

### Task 3: Remove Reverse Imports On Workflow Helpers And Prompt Utilities

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/util/ConversationAccessUtil.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/enums/DocumentSplitType.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/aimodel/prompt/PromptHelper.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/aimodel/prompt/PromptLoader.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/aimodel/prompt/PromptConstant.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/impl/ConversationServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/submission/service/impl/CodeSubmissionServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/config/KnowledgeDocumentSplitProperties.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/splitter/DocumentSplitterFactory.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/listener/KnowledgeDocumentChunkedListener.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentHitkQuestionServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentRagasQuestionServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/JavaCodeNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/JavaScriptCodeNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/PythonCodeNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/QuestionRewriteNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/MemoryCompressNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/RAGJudgeNode.java`
- Remove after migration: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/util/ConversationAccessUtil.java`
- Remove after migration: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/util/UserAccessUtil.java`
- Remove after migration: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/enums/SplitType.java`
- Remove after migration: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/util/prompt/PromptHelper.java`
- Remove after migration: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/util/prompt/PromptLoader.java`
- Remove after migration: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/util/prompt/PromptConstant.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/conversation/service/ConversationServiceImplTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/service/WorkflowBootstrapServiceTest.java`

- [ ] **Step 1: Write the failing helper ownership test**

```java
@Test
void conversationService_shouldNotImportWorkflowConversationAccessUtil() {
    assertThat(ConversationAccessUtil.class.getPackageName()).startsWith("com.oj.agent.core.conversation");
}
```

- [ ] **Step 2: Run the helper ownership tests to verify they fail**

Run: `mvn -pl agent-oj-core -Dtest=ConversationServiceImplTest,WorkflowBootstrapServiceTest test`
Expected: FAIL because the helper and prompt classes still live under `workflow`.

- [ ] **Step 3: Re-home workflow-exported helpers into the correct owning domain and update all reverse consumers**

```java
public enum DocumentSplitType {
    LENGTH,
    TITLE,
    REGEX,
    SMART,
    SEPARATOR
}
```

- [ ] **Step 4: Run the helper ownership tests to verify they pass**

Run: `mvn -pl agent-oj-core -Dtest=ConversationServiceImplTest,WorkflowBootstrapServiceTest test`
Expected: PASS

Run: `mvn -pl agent-oj-core -DskipTests compile`
Expected: PASS, and `CodeSubmissionServiceImpl` no longer imports `workflow.util.UserAccessUtil`.

- [ ] **Step 5: Commit the helper and prompt relocation**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/conversation/util agent-oj-core/src/main/java/com/oj/agent/core/rag/enums/DocumentSplitType.java agent-oj-core/src/main/java/com/oj/agent/core/aimodel/prompt agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/impl/ConversationServiceImpl.java agent-oj-core/src/main/java/com/oj/agent/core/submission/service/impl/CodeSubmissionServiceImpl.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java agent-oj-core/src/main/java/com/oj/agent/core/rag agent-oj-core/src/main/java/com/oj/agent/core/workflow/node agent-oj-core/src/test/java/com/oj/agent/core/conversation/service/ConversationServiceImplTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/service/WorkflowBootstrapServiceTest.java
git commit -m "refactor: rehome workflow helper ownership"
```

### Task 4: Extract Question Contracts Consumed By Workflow And Admin

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/model/query/AlgorithmQuestionGetQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/model/query/AlgorithmQuestionPageQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/model/query/TagPageQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/model/result/AlgorithmQuestionResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/model/result/AlgorithmCodeTemplateResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/model/result/TagResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/converter/AlgorithmQuestionConverter.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/AlgorithmQuestionService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/AlgorithmCodeService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/TagService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/impl/AlgorithmQuestionServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/impl/AlgorithmCodeServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/impl/TagServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/AnswerHitkGraphService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/AnswerRagasGraphService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/QuestionRewriteNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/QuestionRAGNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/RAGJudgeNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeEvaluationNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/OJAssistantNode.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionRagasServiceImpl.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/question/converter/AlgorithmQuestionConverterTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/question/service/AlgorithmQuestionServiceImplTest.java`

- [ ] **Step 1: Write the failing question contract tests**

```java
@Test
void getQuestion_shouldReturnResultInsteadOfDtoOrVo() {
    AlgorithmQuestionResult result = service.getQuestion(new AlgorithmQuestionGetQuery(1L));
    assertThat(result.getId()).isEqualTo(1L);
}
```

- [ ] **Step 2: Run the question contract tests to verify they fail**

Run: `mvn -pl agent-oj-core -Dtest=AlgorithmQuestionConverterTest,AlgorithmQuestionServiceImplTest test`
Expected: FAIL because question services still expose `AlgorithmQuestionDTO`, `AlgorithmQuestionVO`, and `TagVO`.

- [ ] **Step 3: Introduce stable question query/result contracts and update workflow/admin callers to consume them**

```java
public interface AlgorithmQuestionService {
    AlgorithmQuestionResult getQuestion(AlgorithmQuestionGetQuery query);
    Page<AlgorithmQuestionResult> pageQuestions(AlgorithmQuestionPageQuery query);
}
```

- [ ] **Step 4: Run the question contract tests and admin compile check**

Run: `mvn -pl agent-oj-core,agent-oj-admin -am -Dtest=AlgorithmQuestionConverterTest,AlgorithmQuestionServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit the extracted question contracts**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/question agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/AnswerHitkGraphService.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/AnswerRagasGraphService.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/node agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionRagasServiceImpl.java agent-oj-core/src/test/java/com/oj/agent/core/question
git commit -m "refactor: extract question contracts for workflow"
```

### Task 5: Extract Submission And Evaluation Contracts Consumed By Workflow

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/command/CodeSubmissionCreateCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/command/CodeRunCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/query/CodeSubmissionGetQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/query/CodeSubmissionPageQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/result/CodeSubmissionResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/result/CodeExecutionResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/converter/CodeSubmissionConverter.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/evaluation/model/command/CodeEvaluationCreateCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/evaluation/model/result/CodeEvaluationResult.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/submission/service/CodeSubmissionService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/submission/service/impl/CodeSubmissionServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/evaluation/service/CodeEvaluationService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/evaluation/service/impl/CodeEvaluationServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/CodeEvaluationPersistenceStrategy.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeEvaluationNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/aimodel/prompt/PromptHelper.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/submission/service/CodeSubmissionServiceImplTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/service/WorkflowBootstrapServiceTest.java`

- [ ] **Step 1: Write the failing submission and evaluation contract tests**

```java
@Test
void createPendingSubmission_shouldReturnSubmissionResult() {
    CodeSubmissionResult result = service.createPendingSubmission(new CodeSubmissionCreateCommand());
    assertThat(result.getId()).isNotNull();
}
```

- [ ] **Step 2: Run the submission and evaluation tests to verify they fail**

Run: `mvn -pl agent-oj-core -Dtest=CodeSubmissionServiceImplTest,WorkflowBootstrapServiceTest test`
Expected: FAIL because `CodeSubmissionService` and `CodeEvaluationService` still expose entities, DTOs, and `IService`.

- [ ] **Step 3: Replace workflow-facing submission/evaluation entity usage with command/result contracts**

```java
public interface CodeSubmissionService {
    CodeSubmissionResult createPendingSubmission(CodeSubmissionCreateCommand command);
    CodeExecutionResult executeCode(CodeRunCommand command);
}
```

- [ ] **Step 4: Run the submission and evaluation tests to verify they pass**

Run: `mvn -pl agent-oj-core -Dtest=CodeSubmissionServiceImplTest,WorkflowBootstrapServiceTest test`
Expected: PASS

- [ ] **Step 5: Commit the submission and evaluation contract extraction**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/submission agent-oj-core/src/main/java/com/oj/agent/core/evaluation agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/CodeEvaluationPersistenceStrategy.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeEvaluationNode.java agent-oj-core/src/main/java/com/oj/agent/core/aimodel/prompt/PromptHelper.java agent-oj-core/src/test/java/com/oj/agent/core/submission/service/CodeSubmissionServiceImplTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/service/WorkflowBootstrapServiceTest.java
git commit -m "refactor: extract submission evaluation contracts"
```

### Task 6: Converge Workflow Nodes Persistence And External Consumers

**Files:**
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/AbstractLanguageCodeNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/AnswerRAGNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeEvaluationNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/MultiLanguageCodeAssembleNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/OJAssistantNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/QuestionRAGNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/RAGJudgeNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/AssistantMessagePersistenceStrategy.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/CurrentQuestionPersistenceStrategy.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/GeneratedQuestionPersistenceStrategy.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/edge/RAGResultDispatcher.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/config/AgentConfiguration.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentHitkTestServiceImpl.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionRagasServiceImpl.java`
- Modify: `agent-oj-core/src/test/java/com/oj/agent/core/nodel/IntentRecognitionNodeAccuracyTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/persistence/impl/AssistantMessagePersistenceStrategyTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/controller/WorkflowControllerTest.java`

- [ ] **Step 1: Write the failing workflow convergence tests**

```java
@Test
void persistAssistantMessage_shouldUseConversationCommandAndWorkflowResultTypes() {
    strategy.persist(state, context);
    verify(conversationMessageService).createMessage(any());
}
```

- [ ] **Step 2: Run the workflow convergence tests to verify they fail**

Run: `mvn -pl agent-oj-core -Dtest=AssistantMessagePersistenceStrategyTest,WorkflowControllerTest,IntentRecognitionNodeAccuracyTest test`
Expected: FAIL because workflow nodes and persistence still import foreign entities, VO types, and old acronym-cased classes.

- [ ] **Step 3: Finish Round 1B by migrating all touched workflow nodes and external consumers to the stable contracts and normalized names**

```java
public record AnswerHitKGraphResult(List<AnswerRagSegmentResult> segments, List<String> rewrittenQuestions) {
}
```

- [ ] **Step 4: Run the workflow convergence tests and cross-module compile**

Run: `mvn -pl agent-oj-core,agent-oj-admin -am -Dtest=AssistantMessagePersistenceStrategyTest,WorkflowControllerTest,IntentRecognitionNodeAccuracyTest test`
Expected: PASS

- [ ] **Step 5: Commit the workflow convergence round**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/workflow agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentHitkTestServiceImpl.java agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionRagasServiceImpl.java agent-oj-core/src/test/java/com/oj/agent/core/workflow agent-oj-core/src/test/java/com/oj/agent/core/nodel/IntentRecognitionNodeAccuracyTest.java
git commit -m "refactor: converge workflow dependency boundaries"
```

### Task 7: Clean Question Controller Boundary And Re-Home Question-Owned Models

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/model/request/AlgorithmQuestionPageRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/model/request/TagPageRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/model/response/AlgorithmQuestionResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/question/model/response/TagResponse.java`
- Move: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/entity/AlgorithmQuestionKnowledgeDocument.java` -> `agent-oj-core/src/main/java/com/oj/agent/core/question/model/entity/AlgorithmQuestionKnowledgeDocument.java`
- Move: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/entity/AlgorithmQuestionVectorSyncLog.java` -> `agent-oj-core/src/main/java/com/oj/agent/core/question/model/entity/AlgorithmQuestionVectorSyncLog.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/controller/AlgorithmQuestionController.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/mapper/AlgorithmQuestionKnowledgeDocumentMapper.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/mapper/AlgorithmQuestionVectorSyncLogMapper.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/AlgorithmQuestionKnowledgeDocumentService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/AlgorithmQuestionVectorSyncLogService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/impl/AlgorithmQuestionKnowledgeDocumentServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/impl/AlgorithmQuestionVectorSyncLogServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/service/impl/AlgorithmQuestionVectorSyncBookkeepingServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/question/converter/AlgorithmQuestionConverter.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionHitkServiceImpl.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionManagementServiceImpl.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionRagasServiceImpl.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/question/controller/AlgorithmQuestionControllerTest.java`

- [ ] **Step 1: Write the failing question controller test**

```java
@Test
void pageQuestions_shouldReturnResponseNotVo() {
    Result<Page<AlgorithmQuestionResponse>> result = controller.pageQuestions(new AlgorithmQuestionPageRequest());
    assertThat(result.getData()).isNotNull();
}
```

- [ ] **Step 2: Run the question controller test to verify it fails**

Run: `mvn -pl agent-oj-core -Dtest=AlgorithmQuestionControllerTest test`
Expected: FAIL because the controller still accepts DTO requests and returns VO outputs.

- [ ] **Step 3: Move question-owned models back under `question`, switch the controller to request/response, and map through the converter**

```java
@PostMapping("/algorithm-question/page")
public Result<Page<AlgorithmQuestionResponse>> pageQuestions(@RequestBody(required = false) AlgorithmQuestionPageRequest request) {
    return Result.success(AlgorithmQuestionConverter.toPageResponse(
            algorithmQuestionService.pageQuestions(AlgorithmQuestionConverter.toPageQuery(request))));
}
```

- [ ] **Step 4: Run the question controller test and module compile**

Run: `mvn -pl agent-oj-core -Dtest=AlgorithmQuestionControllerTest test`
Expected: PASS

- [ ] **Step 5: Commit the question boundary cleanup**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/question agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionHitkServiceImpl.java agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionManagementServiceImpl.java agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionRagasServiceImpl.java agent-oj-core/src/test/java/com/oj/agent/core/question/controller/AlgorithmQuestionControllerTest.java
git commit -m "refactor: isolate question controller boundary"
```

### Task 8: Clean Rag Boundary Ownership Packages And Converter Layers

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/command/KnowledgeDocumentParseCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/command/KnowledgeDocumentBatchParseCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/query/KnowledgeDocumentRagSearchQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/request/KnowledgeDocumentParseByUrlRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/request/KnowledgeDocumentBatchParseByUrlRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/request/KnowledgeDocumentRagSearchRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/response/KnowledgeDocumentResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/response/KnowledgeDocumentParseResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/response/KnowledgeRagHitResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/converter/KnowledgeDocumentConverter.java`
- Move: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeDocumentProcessor.java` -> `agent-oj-core/src/main/java/com/oj/agent/core/rag/processor/KnowledgeDocumentProcessor.java`
- Move: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/MineruApiClient.java` -> `agent-oj-core/src/main/java/com/oj/agent/core/rag/client/MineruApiClient.java`
- Move: `agent-oj-core/src/main/java/com/oj/agent/core/rag/utils/KnowledgeSimilarityUtils.java` -> `agent-oj-core/src/main/java/com/oj/agent/core/rag/util/KnowledgeSimilarityUtil.java`
- Move: `agent-oj-core/src/main/java/com/oj/agent/core/rag/utils/KnowledgeMarkdownImageRewriter.java` -> `agent-oj-core/src/main/java/com/oj/agent/core/rag/util/KnowledgeMarkdownImageRewriter.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/controller/KnowledgeDocumentController.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/KnowledgeDocumentService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeDocumentServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeDocumentAsyncServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentHitkQuestionServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentRagasQuestionServiceImpl.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionManagementServiceImpl.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/rag/converter/KnowledgeDocumentConverterTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/rag/controller/KnowledgeDocumentControllerTest.java`

- [ ] **Step 1: Write the failing rag boundary tests**

```java
@Test
void parseByUrl_shouldReturnResponseNotVo() {
    Result<KnowledgeDocumentParseResponse> result = controller.parseByUrl(new KnowledgeDocumentParseByUrlRequest());
    assertThat(result.getData()).isNotNull();
}
```

- [ ] **Step 2: Run the rag boundary tests to verify they fail**

Run: `mvn -pl agent-oj-core -Dtest=KnowledgeDocumentConverterTest,KnowledgeDocumentControllerTest test`
Expected: FAIL because the rag controller/service still use DTO/VO types and `service.impl` hosts non-service classes.

- [ ] **Step 3: Move request/response types to the correct boundary packages, add a rag converter, and relocate processor/client/util classes**

```java
public interface KnowledgeDocumentService {
    KnowledgeDocumentParseResponse parseByUrl(KnowledgeDocumentParseCommand command);
    List<KnowledgeRagHitResponse> search(KnowledgeDocumentRagSearchQuery query);
}
```

- [ ] **Step 4: Run the rag boundary tests and admin compile**

Run: `mvn -pl agent-oj-core,agent-oj-admin -am -Dtest=KnowledgeDocumentConverterTest,KnowledgeDocumentControllerTest test`
Expected: PASS

- [ ] **Step 5: Commit the rag boundary cleanup**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/rag agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionManagementServiceImpl.java agent-oj-core/src/test/java/com/oj/agent/core/rag
git commit -m "refactor: clean rag boundary ownership"
```

### Task 9: Clean Submission Trace File And Evaluation Public Boundaries

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/request/CodeRunRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/request/CodeSubmissionPageRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/response/CodeExecutionResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/response/CodeSubmissionListItemResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/model/response/CodeSubmissionDetailResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/request/TracePageRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/query/TracePageQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/query/TraceDetailQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/query/TraceItemListQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/result/TraceListItemResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/result/TraceDetailResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/result/TraceItemResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/response/TraceListItemResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/response/TraceDetailResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/model/response/TraceItemResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/file/model/request/FilePageRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/file/model/request/FileUploadRequest.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/file/model/command/FileUploadCommand.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/file/model/query/FilePageQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/file/model/query/FileGetQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/file/model/result/FileRecordResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/file/model/response/FileRecordResponse.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/file/converter/FileRecordConverter.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/trace/converter/TraceConverter.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/submission/converter/CodeSubmissionResponseConverter.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/submission/controller/CodeSubmissionController.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/submission/service/CodeSubmissionService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/submission/service/impl/CodeSubmissionServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/trace/controller/TraceController.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/trace/service/TraceService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/trace/service/TraceItemService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/trace/service/impl/TraceServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/trace/service/impl/TraceItemServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/file/controller/FileController.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/file/service/FileService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/file/service/impl/FileServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/evaluation/service/CodeEvaluationService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/evaluation/service/impl/CodeEvaluationServiceImpl.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/submission/controller/CodeSubmissionControllerTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/trace/controller/TraceControllerTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/file/controller/FileControllerTest.java`

- [ ] **Step 1: Write the failing submission trace file controller tests**

```java
@Test
void pageTraces_shouldReturnResponseNotVo() {
    Result<Page<TraceListItemResponse>> result = controller.pageTraces(new TracePageRequest());
    assertThat(result.getData()).isNotNull();
}
```

- [ ] **Step 2: Run the controller tests to verify they fail**

Run: `mvn -pl agent-oj-core -Dtest=CodeSubmissionControllerTest,TraceControllerTest,FileControllerTest test`
Expected: FAIL because these domains still expose DTO/VO types and `IService<entity>` contracts.

- [ ] **Step 3: Replace controller and service boundaries with request/response/query/result plus dedicated converters**

```java
public interface TraceService {
    Page<TraceListItemResult> pageTraces(TracePageQuery query);
    TraceDetailResult getTraceDetail(TraceDetailQuery query);
}
```

- [ ] **Step 4: Run the controller tests and module compile**

Run: `mvn -pl agent-oj-core -Dtest=CodeSubmissionControllerTest,TraceControllerTest,FileControllerTest test`
Expected: PASS

- [ ] **Step 5: Commit the submission trace file evaluation cleanup**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/submission agent-oj-core/src/main/java/com/oj/agent/core/trace agent-oj-core/src/main/java/com/oj/agent/core/file agent-oj-core/src/main/java/com/oj/agent/core/evaluation agent-oj-core/src/test/java/com/oj/agent/core/submission agent-oj-core/src/test/java/com/oj/agent/core/trace agent-oj-core/src/test/java/com/oj/agent/core/file
git commit -m "refactor: clean submission trace file evaluation boundaries"
```

### Task 10: Migrate Downstream Admin Consumers And Run Repo-Wide Verification

**Files:**
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionCompensationServiceImpl.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionHitkServiceImpl.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionManagementServiceImpl.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/impl/AdminQuestionRagasServiceImpl.java`
- Modify: `agent-oj-admin/src/main/java/com/oj/agent/admin/service/client/RagasEvaluationClient.java`
- Verify searches against: `agent-oj-core/src/main/java`, `agent-oj-core/src/test/java`, `agent-oj-admin/src/main/java`, `agent-oj-admin/src/test/java`

- [ ] **Step 1: Write the failing downstream compile expectation**

```text
External consumers must stop importing:
- workflow.model.dto.*
- workflow.service.model.*
- question.model.dto.*
- rag.model.vo.*
```

- [ ] **Step 2: Run the cross-module compile to verify it fails before the final migration**

Run: `mvn -pl agent-oj-core,agent-oj-admin -am -DskipTests compile`
Expected: FAIL until all downstream imports are migrated.

- [ ] **Step 3: Update all `agent-oj-admin` consumers and run repo-wide rule searches**

```bash
rg -n "model\\.vo|workflow\\.service\\.model|workflow\\.model\\.dto|rag\\.utils|extends IService<" agent-oj-core/src/main/java agent-oj-core/src/test/java agent-oj-admin/src/main/java agent-oj-admin/src/test/java
```

- [ ] **Step 4: Run final verification**

Run: `mvn -pl agent-oj-core,agent-oj-admin -am test`
Expected: PASS

Run: `mvn -pl agent-oj-core,agent-oj-admin -am -DskipTests compile`
Expected: PASS

- [ ] **Step 5: Commit the final migration and verification pass**

```bash
git add agent-oj-core agent-oj-admin
git commit -m "refactor: complete agent-oj-core boundary cleanup"
```
