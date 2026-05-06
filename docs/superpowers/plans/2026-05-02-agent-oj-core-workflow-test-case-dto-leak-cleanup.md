# Agent OJ Core Workflow Test Case DTO Leak Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `workflow`'s dependency on `question.model.dto.TestCaseDTO` by introducing a workflow-local test-case draft carrier for code-question generation.

**Architecture:** Keep this round tightly scoped to the `workflow -> question.model.dto.TestCaseDTO` leak. Add a workflow-owned DTO for draft shared test cases, switch `CodeQuestionLLMOutputDTO` and `CodeQuestionNode` to that type, and add a narrow regression test that locks the field boundary. Do not delete the old question DTO in this round.

**Tech Stack:** Java 17, Spring Boot 3, JUnit 5, AssertJ, Maven

---

### Task 1: Localize Workflow Shared Test Case Draft Type

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/SharedTestCaseDTO.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/CodeQuestionLLMOutputDTO.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeQuestionNode.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/model/dto/CodeQuestionLLMOutputDTOTest.java`

- [ ] **Step 1: Write the failing boundary test**

```java
@Test
void sharedTestCases_shouldUseWorkflowLocalDtoType() throws Exception {
    Field field = CodeQuestionLLMOutputDTO.class.getDeclaredField("sharedTestCases");
    ParameterizedType type = (ParameterizedType) field.getGenericType();
    assertThat(type.getActualTypeArguments()).containsExactly(SharedTestCaseDTO.class);
}
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CodeQuestionLLMOutputDTOTest test`
Expected: FAIL in test compile or assertion because `SharedTestCaseDTO` does not exist yet and `CodeQuestionLLMOutputDTO` still points at `question.model.dto.TestCaseDTO`.

- [ ] **Step 3: Implement the workflow-local DTO migration**

```java
@Data
public class SharedTestCaseDTO {
    private Map<String, Object> input;
    private Object expectedOutput;
    private String description;
}
```

Implementation notes:
- Keep the new type under `workflow.model.dto` because it is only a workflow-local LLM draft carrier.
- Switch `CodeQuestionLLMOutputDTO.sharedTestCases` from `List<TestCaseDTO>` to `List<SharedTestCaseDTO>`.
- Update all helper methods and local variables in `CodeQuestionNode` that currently use `TestCaseDTO`.
- Do not change question result/persistence contracts in this round.

- [ ] **Step 4: Run targeted verification**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CodeQuestionLLMOutputDTOTest test`
Expected: PASS

Run: `mvn -pl agent-oj-core -am --% -DskipTests compile`
Expected: PASS

Run: `rg -n "question\\.model\\.dto\\.TestCaseDTO" agent-oj-core/src/main/java/com/oj/agent/core/workflow`
Expected: no matches

- [ ] **Step 5: Run module verification**

Run: `mvn -pl agent-oj-core -am test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/SharedTestCaseDTO.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/CodeQuestionLLMOutputDTO.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/CodeQuestionNode.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/model/dto/CodeQuestionLLMOutputDTOTest.java docs/superpowers/plans/2026-05-02-agent-oj-core-workflow-test-case-dto-leak-cleanup.md
git commit -m "refactor: localize workflow test case dto draft"
```
