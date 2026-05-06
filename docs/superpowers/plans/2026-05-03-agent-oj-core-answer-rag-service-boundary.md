# Agent OJ Core Answer Rag Service Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `workflow` from directly depending on `rag.mapper.KnowledgeSegmentMapper` and `rag.model.entity.KnowledgeSegment` by introducing a rag-domain read service that returns a stable `KnowledgeSegmentResult`.

**Architecture:** Keep vector retrieval, embedding generation, RRF scoring, and workflow output contracts unchanged. Introduce `KnowledgeSegmentService#listSegments(KnowledgeSegmentListQuery)` as the rag-domain read boundary, convert `KnowledgeSegment` to `KnowledgeSegmentResult{id,text,chunkId,parentChunkId,documentId,chunkOrder}`, and update `AnswerRAGNode` plus `WorkflowConverter` to consume that result instead of rag entities. Leave `AnswerRAGSegmentDTO`, `AnswerRagSegmentResult`, and graph/result contracts unchanged in this round.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, Jackson, JUnit 5, Mockito, AssertJ, Maven

---

### Task 1: Replace Answer Rag Mapper/Entity Usage With Rag Service Result

**Files:**
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/query/KnowledgeSegmentListQuery.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/model/result/KnowledgeSegmentResult.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/converter/KnowledgeSegmentConverter.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/KnowledgeSegmentService.java`
- Create: `agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentServiceImpl.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/AnswerRAGNode.java`
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/workflow/converter/WorkflowConverter.java`
- Create: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/AnswerRAGNodeTest.java`
- Create: `agent-oj-core/src/test/java/com/oj/agent/core/rag/converter/KnowledgeSegmentConverterTest.java`
- Create: `agent-oj-core/src/test/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentServiceImplTest.java`
- Modify: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/converter/WorkflowConverterTest.java`
- Modify: `agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/OJAssistantNodeTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void toResult_shouldExposeParentChunkIdWithoutLeakingMetadataField() throws Exception {
    KnowledgeSegment entity = new KnowledgeSegment();
    entity.setId(11L);
    entity.setText("child");
    entity.setChunkId("child-1");
    entity.setMetadata("{\"parentChunkId\":\"parent-1\"}");
    entity.setDocumentId(22L);
    entity.setChunkOrder(3);

    KnowledgeSegmentResult result = KnowledgeSegmentConverter.toResult(entity);
    String json = JsonUtils.getObjectMapper().writeValueAsString(result);

    assertThat(result.getParentChunkId()).isEqualTo("parent-1");
    assertThat(json).contains("\"parentChunkId\":\"parent-1\"");
    assertThat(json).doesNotContain("metadata");
}
```

```java
@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {"{}", "{\"other\":\"value\"}", "{not-json}"})
void toResult_shouldReturnNullParentChunkIdWhenMetadataDoesNotContainUsableParent(String metadata) {
    KnowledgeSegment entity = new KnowledgeSegment();
    entity.setMetadata(metadata);

    KnowledgeSegmentResult result = KnowledgeSegmentConverter.toResult(entity);

    assertThat(result.getParentChunkId()).isNull();
}
```

```java
@Test
void listSegments_shouldFilterDeletedRecordsAndConvertParentChunkId() {
    when(knowledgeSegmentMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(activeChildEntity(), deletedEntity()));

    List<KnowledgeSegmentResult> results = service.listSegments(queryWithSegmentIds(101L, 102L));

    assertThat(results).extracting(KnowledgeSegmentResult::getId).containsExactly(101L);
    assertThat(results.get(0).getParentChunkId()).isEqualTo("parent-1");
}
```

```java
@Test
void resolveSegments_shouldPreferParentSegmentFromKnowledgeSegmentServiceResult() throws Exception {
    when(knowledgeSegmentService.listSegments(argThat(query ->
            query.getSegmentIds() != null && query.getSegmentIds().contains(101L))))
            .thenReturn(List.of(childResult()));
    when(knowledgeSegmentService.listSegments(argThat(query ->
            query.getChunkIds() != null && query.getChunkIds().contains("parent-1"))))
            .thenReturn(List.of(parentResult()));

    List<AnswerRAGSegmentDTO> segments = invokeResolveSegments(node, List.of(
            scoredSegment(101L, 0.9D, 0.8D, 0.7D, 0.82D)
    ));

    assertThat(segments).hasSize(1);
    assertThat(segments.get(0).getId()).isEqualTo(201L);
    assertThat(segments.get(0).getText()).isEqualTo("parent");
    assertThat(segments.get(0).getChunkId()).isEqualTo("parent-1");
    assertThat(segments.get(0).getDocumentId()).isEqualTo(22L);
    assertThat(segments.get(0).getChunkOrder()).isEqualTo(1);
    assertThat(segments.get(0).getFinalScore()).isEqualTo(0.82D);
}
```

```java
@Test
void resolveSegments_shouldMergeHitsResolvedToSameParentAndKeepScoringContract() throws Exception {
    when(knowledgeSegmentService.listSegments(argThat(query ->
            query.getSegmentIds() != null && query.getSegmentIds().containsAll(List.of(101L, 102L, 103L)))))
            .thenReturn(List.of(childResult(101L, "child-a", "parent-1"), childResult(102L, "child-b", "parent-1"), loneResult(103L)));
    when(knowledgeSegmentService.listSegments(argThat(query ->
            query.getChunkIds() != null && query.getChunkIds().contains("parent-1"))))
            .thenReturn(List.of(parentResult()));

    List<AnswerRAGSegmentDTO> segments = invokeResolveSegments(node, List.of(
            scoredSegment(101L, 0.91D, 0.80D, 0.70D, 0.83D),
            scoredSegment(102L, 0.87D, 0.85D, 0.72D, 0.88D),
            scoredSegment(103L, 0.89D, 0.82D, 0.73D, 0.79D)
    ));

    assertThat(segments).hasSize(2);
    assertThat(segments.get(0).getId()).isEqualTo(201L);
    assertThat(segments.get(0).getRawSimilarity()).isEqualTo(0.91D);
    assertThat(segments.get(0).getFinalScore()).isEqualTo(0.88D);
    assertThat(segments.get(1).getId()).isEqualTo(103L);
}
```

```java
@Test
void resolveSegments_shouldOrderSameFinalScoreByChunkOrderThenId() throws Exception {
    when(knowledgeSegmentService.listSegments(argThat(query ->
            query.getSegmentIds() != null && query.getSegmentIds().containsAll(List.of(301L, 302L, 303L)))))
            .thenReturn(List.of(
                    result(301L, "segment-a", "chunk-a", null, 10L, 2),
                    result(302L, "segment-b", "chunk-b", null, 10L, 1),
                    result(303L, "segment-c", "chunk-c", null, 10L, 1)
            ));
    when(knowledgeSegmentService.listSegments(argThat(query -> query.getChunkIds() != null)))
            .thenReturn(List.of());

    List<AnswerRAGSegmentDTO> segments = invokeResolveSegments(node, List.of(
            scoredSegment(301L, 0.90D, 0.80D, 0.70D, 0.85D),
            scoredSegment(302L, 0.88D, 0.79D, 0.71D, 0.85D),
            scoredSegment(303L, 0.87D, 0.78D, 0.72D, 0.85D)
    ));

    assertThat(segments).extracting(AnswerRAGSegmentDTO::getId).containsExactly(302L, 303L, 301L);
}
```

```java
@Test
void toAnswerRagSegmentDto_shouldSerializeWorkflowFieldsFromKnowledgeSegmentResult() throws Exception {
    KnowledgeSegmentResult result = new KnowledgeSegmentResult();
    result.setId(1L);
    result.setText("segment text");
    result.setChunkId("chunk-1");
    result.setParentChunkId("parent-1");
    result.setDocumentId(10L);
    result.setChunkOrder(2);

    AnswerRAGSegmentDTO dto = WorkflowConverter.toAnswerRagSegmentDto(result, 0.91, 0.81, 0.71, 0.61);

    assertThat(dto.getId()).isEqualTo(1L);
    assertThat(dto.getText()).isEqualTo("segment text");
    assertThat(dto.getChunkId()).isEqualTo("chunk-1");
    assertThat(dto.getDocumentId()).isEqualTo(10L);
    assertThat(dto.getChunkOrder()).isEqualTo(2);
}
```

```java
@Test
void buildMessages_shouldStillInjectAnswerReferencePromptFromWorkflowSegments() {
    OJAssistantNode node = new OJAssistantNode(null, null, null);
    OverAllState state = new OverAllState(Map.of(
            Constant.INTENT_RECOGNITION_NODE_OUTPUT,
            Map.of("intent", UserIntentType.ANSWER.name()),
            Constant.ANSWER_RAG_NODE_OUTPUT,
            Map.of("segments", List.of(Map.of(
                    "id", 1L,
                    "documentId", 200L,
                    "chunkOrder", 3,
                    "text", "Dynamic programming state is the minimal context."
            ))),
            Constant.MESSAGES,
            List.of(new UserMessage("Explain the dp state"))
    ));

    List<Message> messages = node.buildMessages(state);

    assertThat(messages.get(1).getText()).contains("- documentId: 200");
    assertThat(messages.get(1).getText()).contains("- chunkOrder: 3");
}
```

- [ ] **Step 2: Run the targeted tests to verify RED**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeSegmentConverterTest,KnowledgeSegmentServiceImplTest,AnswerRAGNodeTest,WorkflowConverterTest,OJAssistantNodeTest test`
Expected: FAIL in test compile because `KnowledgeSegmentListQuery`, `KnowledgeSegmentResult`, `KnowledgeSegmentService`, `KnowledgeSegmentServiceImpl`, and the new `AnswerRAGNode` / `WorkflowConverter` signatures do not exist yet.

- [ ] **Step 3: Implement the rag-domain read boundary**

```java
@Data
public class KnowledgeSegmentListQuery {

    private List<Long> segmentIds;

    private List<String> chunkIds;
}
```

```java
@Data
public class KnowledgeSegmentResult {

    private Long id;

    private String text;

    private String chunkId;

    private String parentChunkId;

    private Long documentId;

    private Integer chunkOrder;
}
```

```java
public interface KnowledgeSegmentService {

    List<KnowledgeSegmentResult> listSegments(KnowledgeSegmentListQuery query);
}
```

Implementation notes:
- `KnowledgeSegmentServiceImpl` should own `deleted = 0` filtering and entity-to-result conversion.
- `KnowledgeSegmentConverter.toResult(...)` should parse `metadata` internally and expose normalized `parentChunkId`; `metadata` itself must not cross into `workflow`.
- Update `AnswerRAGNode` to depend on `KnowledgeSegmentService`, not `KnowledgeSegmentMapper`.
- Remove `KnowledgeSegment` and `LambdaQueryWrapper` usage from `AnswerRAGNode`; its resolution algorithm should use `KnowledgeSegmentResult` with `parentChunkId`.
- Update `WorkflowConverter.toAnswerRagSegmentDto(...)` to accept `KnowledgeSegmentResult`, not `KnowledgeSegment`.
- Keep the existing assistant-side reference prompt contract covered by `OJAssistantNodeTest`; this boundary change must not drop `documentId` or `chunkOrder` from workflow state.
- Preserve `resolveSegments(...)` merge semantics: parent promotion, dedupe by retained segment id, `maxRawSimilarity`, higher `finalScore`, then sort by `finalScore desc`, `chunkOrder asc`, `id asc`.
- Do not change vector search flow, scoring, `AnswerRAGSegmentDTO`, `AnswerRagSegmentResult`, `AnswerRAGOutputDTO`, or state keys in this round.

- [ ] **Step 4: Run targeted verification**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeSegmentConverterTest,KnowledgeSegmentServiceImplTest,AnswerRAGNodeTest,WorkflowConverterTest,OJAssistantNodeTest test`
Expected: PASS

Run: `rg -n "import com\\.oj\\.agent\\.core\\.rag\\.(mapper\\.KnowledgeSegmentMapper|model\\.entity\\.KnowledgeSegment)" agent-oj-core/src/main/java/com/oj/agent/core/workflow`
Expected: no matches

Run: `mvn -pl agent-oj-core -am --% -DskipTests compile`
Expected: PASS

- [ ] **Step 5: Run module verification**

Run: `mvn -pl agent-oj-core -am test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/rag/model/query/KnowledgeSegmentListQuery.java agent-oj-core/src/main/java/com/oj/agent/core/rag/model/result/KnowledgeSegmentResult.java agent-oj-core/src/main/java/com/oj/agent/core/rag/converter/KnowledgeSegmentConverter.java agent-oj-core/src/main/java/com/oj/agent/core/rag/service/KnowledgeSegmentService.java agent-oj-core/src/main/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentServiceImpl.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/node/AnswerRAGNode.java agent-oj-core/src/main/java/com/oj/agent/core/workflow/converter/WorkflowConverter.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/AnswerRAGNodeTest.java agent-oj-core/src/test/java/com/oj/agent/core/rag/converter/KnowledgeSegmentConverterTest.java agent-oj-core/src/test/java/com/oj/agent/core/rag/service/impl/KnowledgeSegmentServiceImplTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/converter/WorkflowConverterTest.java agent-oj-core/src/test/java/com/oj/agent/core/workflow/node/OJAssistantNodeTest.java docs/superpowers/plans/2026-05-03-agent-oj-core-answer-rag-service-boundary.md
git commit -m "refactor: add rag segment service boundary"
```
