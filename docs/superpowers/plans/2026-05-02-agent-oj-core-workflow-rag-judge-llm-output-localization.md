# Agent OJ Core Workflow Rag Judge Llm Output Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `workflow` and `aimodel.prompt` from depending on `rag.model.dto.RAGJudgeLLMOutputDTO` by introducing a workflow-local LLM binding DTO for rag-judge prompt formatting and node parsing.

**Architecture:** Keep this round narrowly scoped to the rag-judge LLM binding shape. Add a workflow-owned `RagJudgeLlmOutputDTO`, let `RAGJudgeNode` own the `BeanOutputConverter` format string and parsing, and update `PromptHelper` to accept the format string instead of depending on a workflow internal DTO. Leave `RagJudgeResult` plus all downstream workflow state consumers unchanged, and remove the old unreferenced `rag.model.dto.RAGJudgeLLMOutputDTO` to avoid split ownership.

**Tech Stack:** Java 17, Spring Boot 3, Spring AI BeanOutputConverter, JUnit 5, AssertJ, Maven

---

### Task 1: Localize Workflow Rag Judge Llm Output DTO

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/RagJudgeLlmOutputDTO.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/aimodel/prompt/PromptHelper.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/RAGJudgeNode.java`
- Delete: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/dto/RAGJudgeLLMOutputDTO.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/aimodel/prompt/PromptHelperTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/model/dto/RagJudgeLlmOutputDTOTest.java`
- Test: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/RAGJudgeNodeTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void convert_shouldReadSnakeCaseRagJudgeLlmOutput() {
    BeanOutputConverter<RagJudgeLlmOutputDTO> converter =
            new BeanOutputConverter<>(RagJudgeLlmOutputDTO.class);

    RagJudgeLlmOutputDTO output = converter.convert("""
            {"is_matched":true,"selected_question_id":101,"reason":"matched"}
            """);

    assertThat(output.isMatched()).isTrue();
    assertThat(output.getSelectedQuestionId()).isEqualTo(101L);
    assertThat(output.getReason()).isEqualTo("matched");
}
```

```java
@Test
void findSelectedQuestion_shouldUseWorkflowLocalLlmOutputSelectedQuestionId() throws Exception {
    RAGJudgeNode node = new RAGJudgeNode(null, null, null, null);
    QuestionRAGOutputDTO ragOutput = new QuestionRAGOutputDTO(List.of(question));
    RagJudgeLlmOutputDTO llmOutput = new RagJudgeLlmOutputDTO(true, 101L, "matched");

    AlgorithmQuestionResult selectedQuestion = invokeFindSelectedQuestion(node, ragOutput, llmOutput);

    assertThat(selectedQuestion).isNotNull();
    assertThat(selectedQuestion.getId()).isEqualTo(101L);
}
```

```java
@Test
void buildRagJudgePrompt_shouldUseWorkflowLocalLlmOutputFormat() {
    String format = new BeanOutputConverter<>(RagJudgeLlmOutputDTO.class).getFormat();
    String prompt = PromptHelper.buildRagJudgePrompt("帮我找一道数组题", null, List.of(question), format);

    assertThat(format).contains("selected_question_id");
    assertThat(prompt).contains("selected_question_id");
}
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PromptHelperTest,RagJudgeLlmOutputDTOTest,RAGJudgeNodeTest test`
Expected: FAIL in test compile because `RagJudgeLlmOutputDTO` does not exist and `RAGJudgeNode` still exposes the old rag-domain LLM DTO signature.

- [ ] **Step 3: Implement the workflow-local LLM binding migration**

```java
@Data
@AllArgsConstructor
public class RagJudgeLlmOutputDTO {
    @JsonProperty("is_matched")
    private boolean matched;

    @JsonProperty("selected_question_id")
    private Long selectedQuestionId;

    @JsonProperty("reason")
    private String reason;
}
```

Implementation notes:
- Keep the new type under `workflow.model.dto` because it is an internal LLM binding structure, not a workflow business result.
- Keep `PromptHelper` free of workflow internal DTO imports by passing the format string from `RAGJudgeNode`.
- Switch every `RAGJudgeNode` parse-only usage from `RAGJudgeLLMOutputDTO` to `RagJudgeLlmOutputDTO`.
- Do not change `RagJudgeResult`, `Constant.RAG_JUDGE_NODE_OUTPUT`, or downstream workflow state consumers in this round.
- Delete `rag.model.dto.RAGJudgeLLMOutputDTO` once repo-wide search confirms it is unused.

- [ ] **Step 4: Run targeted verification**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PromptHelperTest,RagJudgeLlmOutputDTOTest,RAGJudgeNodeTest test`
Expected: PASS

Run: `mvn -pl agent-oj-core -am --% -DskipTests compile`
Expected: PASS

Run: `rg -n "RAGJudgeLLMOutputDTO" agent-oj-core/src/main/java agent-oj-core/src/test/java`
Expected: no matches

- [ ] **Step 5: Run module verification**

Run: `mvn -pl agent-oj-core -am test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/RagJudgeLlmOutputDTO.java agent-oj-core/src/main/java/com/oj/agent/core/aimodel/prompt/PromptHelper.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/RAGJudgeNode.java agent-oj-core/src/test/java/com/oj/agent/core/aimodel/prompt/PromptHelperTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/model/dto/RagJudgeLlmOutputDTOTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/RAGJudgeNodeTest.java docs/superpowers/plans/2026-05-02-agent-oj-core-workflow-rag-judge-llm-output-localization.md
git rm agent-oj-core/src/main/java/com/oj/agent/core/rag/model/dto/RAGJudgeLLMOutputDTO.java
git commit -m "refactor: localize workflow rag judge llm output"
```
