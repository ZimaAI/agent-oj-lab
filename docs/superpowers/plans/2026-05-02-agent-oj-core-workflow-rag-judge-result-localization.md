# Agent OJ Core Workflow Rag Judge Result Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `workflow`'s dependency on `rag.model.dto.RAGJudgeOutputDTO` by introducing a workflow-local RAG judge result type for graph state and downstream consumers.

**Architecture:** Keep this round tightly scoped to the workflow state/result boundary. Add a workflow-owned `RagJudgeResult`, switch only the workflow producer and consumers that currently read/write `Constant.RAG_JUDGE_NODE_OUTPUT`, and preserve the existing serialized field names so dispatcher, persistence, and stream payload behavior stay stable. `RAGJudgeLLMOutputDTO` remains rag-owned and out of scope for this round.

**Tech Stack:** Java 17, Spring Boot 3, Spring AI Graph, JUnit 5, AssertJ, Maven

---

### Task 1: Localize Workflow RAG Judge State Result

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/result/RagJudgeResult.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/RAGJudgeNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/edge/JudgeResultDispatcher.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/OJAssistantNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/CurrentQuestionPersistenceStrategy.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/RAGJudgeNodeTest.java`
- Verify-no-change: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/GeneratedQuestionPersistenceStrategy.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/edge/JudgeResultDispatcherTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/OJAssistantNodeTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/persistence/impl/CurrentQuestionPersistenceStrategyTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/persistence/impl/GeneratedQuestionPersistenceStrategyTest.java`

- [ ] **Step 1: Write the failing boundary test**

```java
@Test
void apply_shouldRouteToAssistantWhenWorkflowLocalJudgeResultMatched() throws Exception {
    JudgeResultDispatcher dispatcher = new JudgeResultDispatcher();
    AlgorithmQuestionResult question = new AlgorithmQuestionResult();
    question.setId(101L);
    question.setTitle("Two Sum");
    OverAllState state = new OverAllState(Map.of(
            Constant.RAG_JUDGE_NODE_OUTPUT,
            new RagJudgeResult(true, question)
    ));

    assertThat(dispatcher.apply(state)).isEqualTo(Constant.OJ_ASSISTANT_NODE);
}
```

Add four more focused RED tests in the same step:

```java
@Test
void serializeJudgeOutput_shouldKeepSnakeCaseFieldsForWorkflowLocalJudgeResult() {
    RAGJudgeNode node = new RAGJudgeNode(null, null, null, null);
    AlgorithmQuestionResult question = new AlgorithmQuestionResult();
    question.setId(101L);
    question.setTitle("Two Sum");

    String json = invokeSerializeJudgeOutput(node, new RagJudgeResult(true, question));

    assertThat(json).contains("\"is_matched\":true");
    assertThat(json).contains("\"selected_question\"");
}

@Test
void extractAlgorithmQuestion_shouldPreferMatchedWorkflowLocalJudgeResultFromMapState() {
    OJAssistantNode node = new OJAssistantNode(null, null, null);
    OverAllState state = new OverAllState(Map.of(
            Constant.RAG_JUDGE_NODE_OUTPUT,
            Map.of(
                    "is_matched", true,
                    "selected_question", Map.of("id", 101L, "title", "Two Sum")
            )
    ));

    assertThat(node.extractAlgorithmQuestion(state)).extracting(AlgorithmQuestionResult::getId).isEqualTo(101L);
}

@Test
void persist_shouldBackfillCurrentQuestionAndTitleFromMatchedWorkflowLocalJudgeResult() {
    OverAllState state = new OverAllState(Map.of(
            Constant.CONVERSATION_ID, "conv-1",
            Constant.CURRENT_REQUEST_SEQUENCE_NO, 7,
            Constant.RAG_JUDGE_NODE_OUTPUT, Map.of(
                    "is_matched", true,
                    "selected_question", Map.of("id", 101L, "title", "Two Sum")
            )
    ));
    WorkflowPersistenceContext context = new WorkflowPersistenceContext();

    strategy.persist(state, context);

    verify(conversationService).updateCurrentQuestionIfLatestUserMessageMatches(any());
    verify(conversationService).backfillTitleIfLatestUserMessageMatches(any());
}

@Test
void persist_shouldProceedWhenGeneratedQuestionStrategyReadsWorkflowLocalJudgeResultObject() {
    OverAllState state = new OverAllState(Map.of(
            Constant.INTENT_RECOGNITION_NODE_OUTPUT, Map.of("intent", "NEW_QUESTION"),
            Constant.RAG_JUDGE_NODE_OUTPUT, new RagJudgeResult(false, null),
            Constant.CODE_QUESTION_NODE_OUTPUT, Map.of(
                    "success", true,
                    "question", Map.of("title", "Generated", "description", "desc", "difficulty", "EASY")
            ),
            "multiLanguageCodeAssembleNodeOutput", validAssembleOutput()
    ));

    strategy.persist(state, new WorkflowPersistenceContext());

    verify(generatedQuestionPersistenceService).persistGeneratedQuestion(any());
}
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RAGJudgeNodeTest,JudgeResultDispatcherTest,OJAssistantNodeTest,CurrentQuestionPersistenceStrategyTest,GeneratedQuestionPersistenceStrategyTest test`
Expected: FAIL in test compile because `RagJudgeResult` does not exist and producer/consumers still expect `RAGJudgeOutputDTO`.

- [ ] **Step 3: Implement the workflow-local result migration**

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagJudgeResult {
    @JsonProperty("is_matched")
    private boolean matched;

    @JsonProperty("selected_question")
    private AlgorithmQuestionResult selectedQuestion;
}
```

Implementation notes:
- Keep `RagJudgeResult` under `workflow.model.result` because it is a workflow state/business result, not a rag-domain DTO.
- Preserve compatibility for serialized output and reflective field access by keeping `is_matched` JSON naming and bean readability for `matched`, `isMatched`, and `is_matched`.
- Switch all workflow producer/consumer touchpoints for `Constant.RAG_JUDGE_NODE_OUTPUT` from `RAGJudgeOutputDTO` to `RagJudgeResult`.
- Keep `GeneratedQuestionPersistenceStrategy` unchanged, but verify with a unit test that its `matched`/`isMatched`/`is_matched` field probing still works with `RagJudgeResult`.
- Do not move or delete `rag.model.dto.RAGJudgeOutputDTO` in this round.
- Do not touch `RAGJudgeLLMOutputDTO` in this round.

- [ ] **Step 4: Run targeted verification**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RAGJudgeNodeTest,JudgeResultDispatcherTest,OJAssistantNodeTest,CurrentQuestionPersistenceStrategyTest,GeneratedQuestionPersistenceStrategyTest test`
Expected: PASS

Run: `mvn -pl agent-oj-core -am --% -DskipTests compile`
Expected: PASS

Run: `rg -n "rag\\.model\\.dto\\.RAGJudgeOutputDTO" agent-oj-core/src/main/java/com/oj/agent/core/workflow`
Expected: no matches

- [ ] **Step 5: Run module verification**

Run: `mvn -pl agent-oj-core -am test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/result/RagJudgeResult.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/RAGJudgeNode.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/edge/JudgeResultDispatcher.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/OJAssistantNode.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/persistence/impl/CurrentQuestionPersistenceStrategy.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/edge/JudgeResultDispatcherTest.java docs/superpowers/plans/2026-05-02-agent-oj-core-workflow-rag-judge-result-localization.md
git commit -m "refactor: localize workflow rag judge result"
```
