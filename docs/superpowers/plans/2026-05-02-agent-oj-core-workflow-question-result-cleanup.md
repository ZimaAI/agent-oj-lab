# Agent OJ Core Workflow Question Result Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `workflow` and `trace` cross-domain dependence on `question.model.dto.AlgorithmQuestionDTO` by switching the current-question snapshot path to the stable `AlgorithmQuestionResult` contract.

**Architecture:** Keep this round tightly scoped to the workflow current-question snapshot boundary. `WorkflowBootstrapResult`, workflow state, prompt building, answer graph entry points, and trace snapshot serialization should all carry `AlgorithmQuestionResult` directly; do not introduce another ad-hoc workflow DTO layer. Internal workflow `model.dto` classes used as LLM output carriers remain out of scope for this round.

**Tech Stack:** Java 17, Spring Boot 3, Spring AI Graph, JUnit 5, Mockito, AssertJ, Maven

---

### Task 1: Migrate Workflow Current Question Snapshot To Question Result Contract

**Files:**
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/result/WorkflowBootstrapResult.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowBootstrapService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/WorkflowInputMapBuilder.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/trace/WorkflowTraceRecorder.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/aimodel/prompt/PromptHelper.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/AnswerHitkGraphService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/service/AnswerRagasGraphService.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/CurrentQuestionPersistenceStrategy.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/AnswerRewriteNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/AnswerRAGNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeEvaluationNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeQuestionNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/OJAssistantNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/QuestionRewriteNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/RAGJudgeNode.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/service/WorkflowBootstrapServiceTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/trace/WorkflowTraceRecorderTest.java`

- [ ] **Step 1: Align and confirm the workflow snapshot tests for the new contract**

```java
@Test
void bootstrapCodeEvaluation_shouldExposeQuestionResultSnapshot() {
    WorkflowBootstrapResult bootstrap = service.bootstrapCodeEvaluation(command, "conv-2", "trace-2");
    assertThat(bootstrap.getCurrentAlgorithmQuestionSnapshot()).isInstanceOf(AlgorithmQuestionResult.class);
}
```

```java
@Test
void startTrace_shouldSerializeQuestionResultSnapshot() throws Exception {
    AlgorithmQuestionResult currentQuestionSnapshot = new AlgorithmQuestionResult();
    currentQuestionSnapshot.setId(101L);
    recorder.startTrace("trace-1", "conversation-1", 1L, "hello", currentQuestionSnapshot);
    verify(workflowTraceService, never()).startTrace(any());
}
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `mvn -pl agent-oj-core -am -Dtest=WorkflowBootstrapServiceTest,WorkflowTraceRecorderTest test`
Expected: FAIL because workflow bootstrap and trace APIs still expose `AlgorithmQuestionDTO`.

- [ ] **Step 3: Migrate the workflow snapshot path to `AlgorithmQuestionResult`**

```java
public class WorkflowBootstrapResult {
    private ConversationDetailResult conversation;
    private AlgorithmQuestionResult currentAlgorithmQuestionSnapshot;
    private CodeSubmissionResult currentCodeSubmission;
    private UserIntentType presetIntent;
    private Integer userMessageSequenceNo;
}
```

```java
private AlgorithmQuestionResult loadRequiredCurrentQuestionSnapshot(Long currentQuestionId) {
    try {
        return algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(currentQuestionId));
    } catch (IllegalArgumentException exception) {
        throw new IllegalStateException("currentQuestion 对应题目不存在，questionId=" + currentQuestionId);
    }
}
```

```java
public void startTrace(String traceId,
                       String conversationId,
                       Long userId,
                       String requestMessage,
                       AlgorithmQuestionResult currentQuestionSnapshot) {
    // ...
}
```

Implementation notes:
- Remove `AlgorithmQuestionConverter.toAlgorithmQuestionDTO(...)` from this snapshot path.
- Update all workflow state reads of `Constant.CURRENT_ALGORITHM_QUESTION` from `AlgorithmQuestionDTO.class` to `AlgorithmQuestionResult.class`.
- Update `PromptHelper` method signatures and local formatters from `AlgorithmQuestionDTO` / `List<AlgorithmQuestionDTO>` to `AlgorithmQuestionResult` / `List<AlgorithmQuestionResult>`.
- Keep scope tight: do not refactor unrelated workflow `model.dto` LLM-output classes this round.

- [ ] **Step 4: Run the targeted tests and compile verification**

Run: `mvn -pl agent-oj-core -am -Dtest=WorkflowBootstrapServiceTest,WorkflowTraceRecorderTest test`
Expected: PASS

Run: `mvn -pl agent-oj-core -am -DskipTests compile`
Expected: PASS

- [ ] **Step 5: Run module verification**

Run: `mvn -pl agent-oj-core -am -Dtest=WorkflowBootstrapServiceTest,WorkflowTraceRecorderTest test`
Expected: PASS

Run: `mvn -pl agent-oj-core -am test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/workflow agent-oj-core/src/main/java/com/oj/agent/core/aimodel/prompt/PromptHelper.java agent-oj-core/src/test/java/com/oj/agent/core/workflow
git commit -m "refactor: remove workflow question dto snapshot coupling"
```
