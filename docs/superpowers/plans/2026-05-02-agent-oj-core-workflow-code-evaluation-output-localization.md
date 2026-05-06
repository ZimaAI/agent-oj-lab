# Agent OJ Core Workflow Code Evaluation Output Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `workflow` state consumers from depending on `evaluation.model.dto.CodeEvaluationOutputDTO` by introducing a workflow-owned code-evaluation node result type and switching the node, assistant, and persistence strategy to it without changing state payload shape.

**Architecture:** Keep this round narrowly scoped to the workflow node-output wrapper around code-evaluation success and failure state. Introduce `workflow.model.result.CodeEvaluationNodeResult`, preserve the serialized fields `success`, `codeEvaluation`, and `errorMessage`, and update only the direct workflow producer and typed consumers. Leave `CodeEvaluationLLMOutputDTO`, `PromptHelper`, `CodeEvaluationConverter`, state keys, and raw JSON serialization behavior unchanged in this round.

**Tech Stack:** Java 17, Spring Boot 3, Jackson, JUnit 5, Mockito, AssertJ, Maven

---

### Task 1: Localize Workflow Code Evaluation Node Output

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/result/CodeEvaluationNodeResult.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeEvaluationNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/OJAssistantNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/CodeEvaluationPersistenceStrategy.java`
- Delete: `agent-oj-core/src/main/java/com/oj/agent/core/evaluation/model/dto/CodeEvaluationOutputDTO.java`
- Create: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/CodeEvaluationNodeTest.java`
- Modify: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/OJAssistantNodeTest.java`
- Create: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/persistence/impl/CodeEvaluationPersistenceStrategyTest.java`
- Modify: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/persistence/impl/AssistantMessagePersistenceStrategyTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void convert_shouldReadWorkflowLocalCodeEvaluationNodeResultFromStateMap() {
    OverAllState state = new OverAllState(Map.of(
            Constant.CODE_EVALUATION_NODE_OUTPUT,
            Map.of(
                    "success", true,
                    "codeEvaluation", Map.of("overallScore", 95, "summary", "good job")
            )
    ));

    CodeEvaluationResult evaluation = new OJAssistantNode(null, null, null).extractCodeEvaluation(state);

    assertThat(evaluation).isNotNull();
    assertThat(evaluation.getOverallScore()).isEqualTo(95);
    assertThat(evaluation.getSummary()).isEqualTo("good job");
}
```

```java
@Test
void parseEvaluationResult_shouldReturnWorkflowLocalNodeResultOnSuccess() throws Exception {
    CodeEvaluationNode node = new CodeEvaluationNode(null, null, null, null, null);
    CodeSubmissionResult submission = new CodeSubmissionResult();
    submission.setId(7L);
    submission.setUserId(8L);
    submission.setAlgorithmQuestionId(9L);
    submission.setConversationMessageId(10L);
    submission.setConversationId("conv-1");
    submission.setTraceId("trace-1");

    CodeEvaluationNodeResult output = invokeParseEvaluationResult(
            node,
            """
            {
              "correctnessScore": 90,
              "timeComplexityScore": 80,
              "spaceComplexityScore": 85,
              "overallScore": 88,
              "timeComplexityAnalysis": "ok",
              "spaceComplexityAnalysis": "ok",
              "codeQualityAnalysis": "good",
              "suggestions": "none",
              "summary": "solid"
            }
            """,
            "{\"results\":[{\"passed\":true}]}",
            submission
    );

    assertThat(output.isSuccess()).isTrue();
    assertThat(output.getCodeEvaluation()).isNotNull();
    assertThat(output.getCodeEvaluation().getOverallScore()).isEqualTo(88);
    assertThat(output.getCodeEvaluation().getSubmissionId()).isEqualTo(7L);
    assertThat(output.getCodeEvaluation().getSummary()).isEqualTo("solid");
}
```

```java
@Test
void persist_shouldCreateEvaluationAndMarkSubmissionSuccess() {
    OverAllState state = new OverAllState(Map.of(
            Constant.CURRENT_CODE_SUBMISSION, Map.of("id", 7L),
            Constant.CODE_EVALUATION_NODE_OUTPUT, Map.of(
                    "success", true,
                    "codeEvaluation", Map.of(
                            "submissionId", 7L,
                            "algorithmQuestionId", 9L,
                            "overallScore", 95,
                            "summary", "good",
                            "testResults", "{\"results\":[{\"passed\":true},{\"passed\":true}]}"
                    )
            )
    ));

    strategy.persist(state, new WorkflowPersistenceContext());

    verify(codeEvaluationService).createEvaluation(any());
    verify(codeSubmissionService).updateSubmission(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecuteStatus()).isEqualTo("SUCCESS");
    assertThat(commandCaptor.getValue().getErrorMessage()).isNull();
    assertThat(commandCaptor.getValue().getPassCount()).isEqualTo(2);
    assertThat(commandCaptor.getValue().getTotalCount()).isEqualTo(2);
}
```

```java
@Test
void persist_shouldBackfillSubmissionStatusFromWorkflowLocalNodeResultFailure() {
    OverAllState state = new OverAllState(Map.of(
            Constant.CURRENT_CODE_SUBMISSION, Map.of("id", 7L),
            Constant.CODE_EVALUATION_NODE_OUTPUT, Map.of(
                    "success", false,
                    "errorMessage", "runtime error",
                    "codeEvaluation", Map.of(
                            "testResults",
                            "{\"results\":[{\"passed\":true},{\"passed\":false}]}"
                    )
            )
    ));

    strategy.persist(state, new WorkflowPersistenceContext());

    verify(codeEvaluationService, never()).createEvaluation(any());
    verify(codeSubmissionService).updateSubmission(commandCaptor.capture());
    assertThat(commandCaptor.getValue().getExecuteStatus()).isEqualTo("FAILED");
    assertThat(commandCaptor.getValue().getErrorMessage()).isEqualTo("runtime error");
    assertThat(commandCaptor.getValue().getPassCount()).isEqualTo(1);
    assertThat(commandCaptor.getValue().getTotalCount()).isEqualTo(2);
}
```

```java
@Test
void persist_shouldSerializeWorkflowLocalCodeEvaluationNodeResultIntoCommandResultData() {
    CodeEvaluationNodeResult output = new CodeEvaluationNodeResult();
    output.setSuccess(false);
    output.setErrorMessage("runtime error");
    output.setCodeEvaluation(new CodeEvaluationResult());

    OverAllState state = new OverAllState(Map.of(
            Constant.OJ_ASSISTANT_NODE_OUTPUT, "evaluation",
            Constant.CONVERSATION_ID, "conv-3",
            Constant.TRACE_ID, "trace-3",
            Constant.CODE_EVALUATION_NODE_OUTPUT, output
    ));

    strategy.persist(state, new WorkflowPersistenceContext());

    verify(conversationMessageService).createMessage(captor.capture());
    assertThat(captor.getValue().getResultData()).contains("\"success\":false");
    assertThat(captor.getValue().getResultData()).contains("\"errorMessage\":\"runtime error\"");
    assertThat(captor.getValue().getResultData()).contains("\"codeEvaluation\"");
}
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CodeEvaluationNodeTest,OJAssistantNodeTest,CodeEvaluationPersistenceStrategyTest,AssistantMessagePersistenceStrategyTest test`
Expected: FAIL in test compile because `CodeEvaluationNodeResult` does not exist and the workflow node/consumers still import `evaluation.model.dto.CodeEvaluationOutputDTO`.

- [ ] **Step 3: Implement the workflow-local node result migration**

```java
@Data
public class CodeEvaluationNodeResult {

    private boolean success;

    private CodeEvaluationResult codeEvaluation;

    private String errorMessage;
}
```

Implementation notes:
- Put the new type under `workflow.model.result` because it is a workflow business/state output, not an evaluation-domain request/response or LLM binding DTO.
- Keep the serialized property names exactly `success`, `codeEvaluation`, and `errorMessage` so `StateUtil` map binding and `AssistantMessagePersistenceStrategy` raw serialization remain stable.
- Switch `CodeEvaluationNode`, `OJAssistantNode`, and `CodeEvaluationPersistenceStrategy` to the workflow-local result type only.
- Delete `evaluation.model.dto.CodeEvaluationOutputDTO` after repo-wide search confirms no remaining references.
- Do not change `Constant.CODE_EVALUATION_NODE_OUTPUT`, `CodeEvaluationLLMOutputDTO`, `PromptHelper`, or `CodeEvaluationConverter` in this round.

- [ ] **Step 4: Run targeted verification**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CodeEvaluationNodeTest,OJAssistantNodeTest,CodeEvaluationPersistenceStrategyTest,AssistantMessagePersistenceStrategyTest test`
Expected: PASS

Run: `mvn -pl agent-oj-core -am --% -DskipTests compile`
Expected: PASS

Run: `rg -n "evaluation\\.model\\.dto\\.CodeEvaluationOutputDTO|CodeEvaluationOutputDTO" agent-oj-core/src/main/java agent-oj-core/src/test/java`
Expected: no matches

- [ ] **Step 5: Run module verification**

Run: `mvn -pl agent-oj-core -am test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/result/CodeEvaluationNodeResult.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeEvaluationNode.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/OJAssistantNode.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/CodeEvaluationPersistenceStrategy.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/CodeEvaluationNodeTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/OJAssistantNodeTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/persistence/impl/CodeEvaluationPersistenceStrategyTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/persistence/impl/AssistantMessagePersistenceStrategyTest.java docs/superpowers/plans/2026-05-02-agent-oj-core-workflow-code-evaluation-output-localization.md
git rm agent-oj-core/src/main/java/com/oj/agent/core/evaluation/model/dto/CodeEvaluationOutputDTO.java
git commit -m "refactor: localize workflow code evaluation output"
```
