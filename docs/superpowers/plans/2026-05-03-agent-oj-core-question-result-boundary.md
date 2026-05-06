# Agent OJ Core Question Result Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `conversation` from directly depending on `question` domain entities by routing title lookup through the existing `question` query/result service boundary.

**Architecture:** Reuse `AlgorithmQuestionService#getQuestion(AlgorithmQuestionGetQuery)` instead of `IService#getById(...)`. Keep question-domain contracts unchanged; only change `conversation` consumption and tests so the cross-domain caller stops importing `AlgorithmQuestion`.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, JUnit 5, Mockito, AssertJ, Maven

---

### Task 1: Route Conversation Title Lookup Through Question Result Boundary

**Files:**
- Modify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/impl/ConversationServiceImpl.java`
- Modify: `agent-oj-core/src/test/java/com/oj/agent/core/conversation/service/ConversationServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void createConversation_shouldUseQuestionResultBoundaryInsteadOfQuestionEntity() {
    ConversationCreateCommand command = new ConversationCreateCommand();
    command.setCurrentQuestionId(1001L);
    AlgorithmQuestionResult question = new AlgorithmQuestionResult();
    question.setId(1001L);
    question.setTitle("Two Sum");
    when(algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(1001L))).thenReturn(question);
    when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

    ConversationCreateResult result = service.createConversation(command);

    assertThat(result.getCurrentQuestionId()).isEqualTo(1001L);
    verify(algorithmQuestionService).getQuestion(new AlgorithmQuestionGetQuery(1001L));
    verify(algorithmQuestionService, never()).getById(any());
}
```

- [ ] **Step 2: Run test to verify RED**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ConversationServiceImplTest test`
Expected: FAIL because `ConversationServiceImpl` still calls `algorithmQuestionService.getById(...)`.

- [ ] **Step 3: Implement the minimal change**

Implementation notes:
- Remove `AlgorithmQuestion` import from `ConversationServiceImpl`.
- In `resolveConversationTitle(...)`, call `algorithmQuestionService.getQuestion(new AlgorithmQuestionGetQuery(currentQuestionId))`.
- Use `AlgorithmQuestionResult#getTitle()` to resolve the title.
- Preserve existing behavior when the question is missing: still throw `IllegalArgumentException("currentQuestionId 对应题目不存在")`.

- [ ] **Step 4: Run test to verify GREEN**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ConversationServiceImplTest test`
Expected: PASS

---

### Task 2: Verify Cross-Domain Entity Leak Is Removed

**Files:**
- Verify: `agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/impl/ConversationServiceImpl.java`
- Verify: `docs/superpowers/plans/2026-05-03-agent-oj-core-question-result-boundary.md`

- [ ] **Step 1: Run targeted verification**

Run: `mvn -pl agent-oj-core -am --% -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ConversationServiceImplTest,AlgorithmQuestionServiceImplTest test`
Expected: PASS

Run: `rg -n "import com\\.oj\\.agent\\.core\\.question\\.model\\.entity\\.AlgorithmQuestion" agent-oj-core/src/main/java/com/oj/agent/core/conversation`
Expected: no matches

Run: `mvn -pl agent-oj-core -am --% -DskipTests compile`
Expected: PASS

- [ ] **Step 2: Run module verification**

Run: `mvn -pl agent-oj-core -am test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add agent-oj-core/src/main/java/com/oj/agent/core/conversation/service/impl/ConversationServiceImpl.java agent-oj-core/src/test/java/com/oj/agent/core/conversation/service/ConversationServiceImplTest.java docs/superpowers/plans/2026-05-03-agent-oj-core-question-result-boundary.md
git commit -m "refactor: route conversation question reads through result boundary"
```
