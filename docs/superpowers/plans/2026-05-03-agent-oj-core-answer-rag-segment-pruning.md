# Agent OJ Core Answer Rag Segment Pruning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Shrink the workflow-owned `AnswerRAGSegmentDTO` to only the fields actually consumed by workflow and graph-result callers, so workflow state no longer mirrors `rag.model.entity.KnowledgeSegment`.

**Architecture:** Keep the current `AnswerRAG` state key, output wrapper, retrieval logic, and graph-result contracts unchanged. Only prune persistence-only fields from `AnswerRAGSegmentDTO`, stop `WorkflowConverter` from copying those entity attributes, and add tests that lock both the serialized workflow shape and the assistant-side dependency on the minimal field set.

**Tech Stack:** Java 17, Spring Boot 3, Jackson, JUnit 5, AssertJ, Maven

---

### Task 1: Prune Workflow Answer Rag Segment Fields

**Files:**
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/AnswerRAGSegmentDTO.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/converter/WorkflowConverter.java`
- Modify: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/converter/WorkflowConverterTest.java`
- Modify: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/OJAssistantNodeTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void toAnswerRagSegmentDto_shouldNotExposePersistenceOnlyFieldsInWorkflowJson() throws Exception {
    KnowledgeSegment entity = new KnowledgeSegment();
    entity.setId(11L);
    entity.setText("segment body");
    entity.setChunkId("chunk-1");
    entity.setMetadata("{\"parent_chunk_id\":\"parent-1\"}");
    entity.setDocumentId(22L);
    entity.setChunkOrder(3);
    entity.setEmbeddingId("emb-1");
    entity.setStatus("VECTOR_STORED");
    entity.setSkipEmbedding(0);
    entity.setHitkQuestion("question");
    entity.setLockVersion(7);
    entity.setDeleted(0);

    AnswerRAGSegmentDTO dto = WorkflowConverter.toAnswerRagSegmentDto(entity, 0.91D, 0.92D, 0.93D, 0.94D);
    String json = new ObjectMapper().writeValueAsString(dto);

    assertThat(json).contains("\"id\":11");
    assertThat(json).contains("\"text\":\"segment body\"");
    assertThat(json).contains("\"chunkId\":\"chunk-1\"");
    assertThat(json).contains("\"documentId\":22");
    assertThat(json).contains("\"chunkOrder\":3");
    assertThat(json).contains("\"rawSimilarity\":0.91");
    assertThat(json).contains("\"similarityScore\":0.92");
    assertThat(json).contains("\"rrfScore\":0.93");
    assertThat(json).contains("\"finalScore\":0.94");
    assertThat(json).doesNotContain("metadata");
    assertThat(json).doesNotContain("embeddingId");
    assertThat(json).doesNotContain("status");
    assertThat(json).doesNotContain("skipEmbedding");
    assertThat(json).doesNotContain("hitkQuestion");
    assertThat(json).doesNotContain("createdAt");
    assertThat(json).doesNotContain("updatedAt");
    assertThat(json).doesNotContain("lockVersion");
    assertThat(json).doesNotContain("deleted");
}
```

```java
@Test
void buildMessages_shouldInjectAnswerReferencePromptFromMinimalSegmentFields() {
    OJAssistantNode node = new OJAssistantNode(null, null, null);
    OverAllState state = new OverAllState(Map.of(
            Constant.INTENT_RECOGNITION_NODE_OUTPUT, Map.of("intent", "ANSWER"),
            Constant.ANSWER_RAG_NODE_OUTPUT, Map.of(
                    "segments", List.of(Map.of(
                            "id", 11L,
                            "documentId", 22L,
                            "chunkOrder", 3,
                            "text", "segment body"
                    ))
            ),
            Constant.MESSAGES, List.of(new UserMessage("解释一下这个做法"))
    ));

    List<Message> messages = node.buildMessages(state);

    assertThat(messages).hasSize(3);
    assertThat(messages.get(1).getText()).contains("segmentId: 11");
    assertThat(messages.get(1).getText()).contains("documentId: 22");
    assertThat(messages.get(1).getText()).contains("chunkOrder: 3");
    assertThat(messages.get(1).getText()).contains("content: segment body");
}
```

- [ ] **Step 2: Run the targeted tests to verify RED**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=WorkflowConverterTest,OJAssistantNodeTest test`
Expected: FAIL because `WorkflowConverter.toAnswerRagSegmentDto(...)` still serializes persistence-only fields such as `metadata`, `embeddingId`, `status`, `createdAt`, and `deleted`. The new `OJAssistantNode` test is a regression guard and may already pass before the refactor.

- [ ] **Step 3: Implement the minimal field-pruning**

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerRAGSegmentDTO {

    private Long id;
    private String text;
    private String chunkId;
    private Long documentId;
    private Integer chunkOrder;
    private Double rawSimilarity;
    private Double similarityScore;
    private Double rrfScore;
    private Double finalScore;
}
```

Implementation notes:
- Keep `AnswerRAGSegmentDTO` in `workflow.model.dto` this round; the problem being fixed is overexposed entity shape, not ownership.
- Remove only fields that are not consumed by workflow callers: `metadata`, `embeddingId`, `status`, `skipEmbedding`, `hitkQuestion`, `createdAt`, `updatedAt`, `lockVersion`, `deleted`.
- Update `WorkflowConverter.toAnswerRagSegmentDto(...)` to stop copying the removed fields.
- Do not change `AnswerRAGOutputDTO`, `AnswerRAGNode` retrieval logic, `Constant.ANSWER_RAG_NODE_OUTPUT`, `AnswerHitkGraphService`, `AnswerRagasGraphService`, or `AnswerRagSegmentResult` in this round.

- [ ] **Step 4: Run targeted verification**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=WorkflowConverterTest,OJAssistantNodeTest test`
Expected: PASS

Run: `rg -n "private (String metadata|String embeddingId|String status|Integer skipEmbedding|String hitkQuestion|LocalDateTime createdAt|LocalDateTime updatedAt|Integer lockVersion|Integer deleted)|dto\\.set(Metadata|EmbeddingId|Status|SkipEmbedding|HitkQuestion|CreatedAt|UpdatedAt|LockVersion|Deleted)" agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/AnswerRAGSegmentDTO.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/converter/WorkflowConverter.java`
Expected: no matches

Run: `mvn -pl agent-oj-core -am --% -DskipTests compile`
Expected: PASS

- [ ] **Step 5: Run module verification**

Run: `mvn -pl agent-oj-core -am test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/workflow/model/dto/AnswerRAGSegmentDTO.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/converter/WorkflowConverter.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/converter/WorkflowConverterTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/OJAssistantNodeTest.java docs/superpowers/plans/2026-05-03-agent-oj-core-answer-rag-segment-pruning.md
git commit -m "refactor: prune workflow answer rag segment fields"
```
