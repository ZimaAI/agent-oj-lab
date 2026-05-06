# Agent OJ Core Refactor Design

## Background

`agent-oj-core` has already started a boundary cleanup in `conversation`, but the rest of the module still contains broad violations of the POJO, package, and dependency rules defined in `AGENTS.md`.

The main problems found in the current audit are:

- `controller` methods still receive `dto` types and return `vo` types in multiple domains
- service interfaces and implementations still expose `dto` / `vo` instead of `command` / `query` / `result`
- `workflow` directly depends on other domains' `entity`, `dto`, `vo`, and mapper types
- `response` objects are not consistently placed under `model.response`
- `utils` plural is still present in `rag`
- some workflow entry and orchestration objects are placed in mismatched packages, such as `workflow.model.dto.WorkflowStartRequest`

Representative examples from the current codebase:

- `workflow.controller.WorkflowController` receives `CodeEvaluationRequestDTO` and creates `WorkflowStartRequest` from `workflow.model.dto`
- `question.controller.AlgorithmQuestionController` receives `AlgorithmQuestionPageRequest` and `TagPageRequest` from `dto`, and returns `AlgorithmQuestionVO` / `TagVO`
- `submission.controller.CodeSubmissionController` returns `CodeExecutionResultVO`, `CodeSubmissionListItemVO`, and `CodeSubmissionDetailVO`
- `trace.controller.TraceController` receives `TracePageRequest` from `dto` and returns `TraceListItemVO`, `TraceDetailVO`, and `TraceItemVO`
- `workflow.service.WorkflowBootstrapService` directly imports `AlgorithmQuestion` and `CodeSubmission` entities and assembles `AlgorithmQuestionDTO`
- `workflow.node.QuestionRAGNode` directly depends on `AlgorithmQuestionMapper`
- `workflow.trace` currently owns trace-facing components and trace model carriers even though trace is its own business domain
- `question` and `rag` currently depend on each other's `dto`, `vo`, `entity`, and service types in both directions
- `conversation`, `rag`, and `submission` currently reverse-depend on `workflow` helpers, which turns `workflow` into a shared toolbox

These issues are structural rather than cosmetic. A suffix-only rename would leave the layering violations intact.

## Goal

Refactor `agent-oj-core` so that touched domains comply with the repository rules:

- `controller -> request -> converter -> command/query -> service`
- `service -> result -> converter -> response -> controller`
- `service -> entity -> mapper`
- `converter` contains only deterministic mapping logic
- no new `VO`, no new mixed `model` root dumping, no cross-domain direct dependency on another domain's `request` / `response` / `entity` / internal `dto`

The refactor must preserve runtime behavior and external API semantics as much as possible. The expected change is internal structure, type ownership, and dependency direction, not feature expansion.

## Scope

In scope:

- `agent-oj-core/src/main/java/com/oj/agent/core/workflow/**`
- `agent-oj-core/src/main/java/com/oj/agent/core/question/**`
- `agent-oj-core/src/main/java/com/oj/agent/core/rag/**`
- `agent-oj-core/src/main/java/com/oj/agent/core/submission/**`
- `agent-oj-core/src/main/java/com/oj/agent/core/trace/**`
- `agent-oj-core/src/main/java/com/oj/agent/core/file/**`
- `agent-oj-core/src/main/java/com/oj/agent/core/evaluation/**`
- direct callers and direct callees inside `agent-oj-core` that must change to restore boundary correctness

Out of scope:

- unrelated modules outside `agent-oj-core`
- database schema changes unless a compile blocker forces a trivial mapper XML adjustment
- endpoint contract redesign beyond class/package relocation and boundary cleanup

## Refactor Approaches

### Option 1: Big-bang module-wide rename and signature rewrite

Change all violating domains in one pass and remove obsolete types immediately.

Pros:

- shortest conceptual migration path
- avoids temporary compatibility shims

Cons:

- highest compile break surface
- hard to isolate regressions
- poor fit for multi-round sub-agent execution

### Option 2: Domain-by-domain convergence with compatibility only where needed

Refactor one or two tightly coupled domains per round, keep each round buildable, and allow short-lived compatibility inside the active round only.

Pros:

- smallest safe batch size
- aligns with sub-agent execution
- easier verification and rollback reasoning

Cons:

- requires disciplined sequencing
- some temporary adapters may exist for one round

### Option 3: Wrapper-first migration with façade preservation

Add new request/response/command/query/result types first, keep old DTO/VO signatures temporarily, and migrate controllers and services later.

Pros:

- lowest immediate compile risk

Cons:

- extends dual-model lifetime
- increases maintenance noise
- risks normalizing the old boundary instead of removing it

### Recommendation

Use Option 2.

This codebase already has a recent example of staged cleanup in `conversation`, and the current violations are concentrated in several business domains with clear hotspots. Round-based convergence gives the best balance between correctness, verification, and context control for sub-agents.

## Target Design

### Shared Boundary Rules

For each refactored domain:

- HTTP input types live under `model.request`
- HTTP output types live under `model.response`
- service write intent lives under `model.command`
- service read conditions live under `model.query`
- service business output lives under `model.result`
- `model.dto` is allowed only when all of the following are true:
  - the type is not a controller parameter or controller return value
  - the type is not a public service interface parameter or return value
  - the type represents either third-party binding data, LLM structured output, or a domain-internal orchestration carrier that has no stable command/query/result meaning
  - the type has a single owning domain and is not used as a cross-domain shortcut
  - if introduced for migration, it must be removed before the end of the same round
- `converter` becomes the only place for deterministic object mapping between these layers

### Cross-domain Dependency Rules

- `workflow` may depend on other domains' `service` interfaces and stable `command` / `query` / `result`
- `workflow` must not directly depend on `question` / `submission` / `trace` / `conversation` entities except where a domain explicitly exposes stable business output
- other domains must not reverse-depend on `workflow` helper classes as a shared utility layer; any such helper must move to its owning domain or to an explicit shared subdomain
- controllers must not depend on mapper, other domains' entities, or internal workflow state carriers
- `rag.utils` must be converged to `rag.util`
- `trace` ownership belongs to the `trace` domain; `workflow` may coordinate trace through trace-domain services and stable models, but `workflow.trace` must not remain the public home of trace behavior or trace command models
- touched classes must converge acronym casing toward repository naming rules such as `Rag`, `Llm`, `HitK`, and `Oj`

### Workflow-specific Design

`workflow` is not a shared toolbox. It is an orchestration domain.

The workflow round must:

- move controller input types to `workflow.model.request`
- replace request-shaped or HTTP-shaped DTOs used in service signatures
- reduce direct imports of other domains' entities, VO types, and mapper types
- isolate graph bootstrap and node state transport into explicit workflow-owned `request`, `command`, `result`, or narrowly justified `dto`
- keep persistence strategies depending on domain services, not direct persistence structures from other domains
- stop exporting workflow-owned helper classes as general-purpose dependencies for other domains

### Domain Target Shapes

`question` target shape:

- `model.request`: page/detail HTTP inputs
- `model.response`: tag/question HTTP outputs
- `model.query`: page/detail service query conditions
- `model.result`: tag/question business outputs
- `converter`: request/query/result/response mapping
- question-owned relation entities and sync records stay in the question-owned boundary rather than being imported from `rag.model.entity`

`submission` target shape:

- `model.request`: code run and submission page inputs
- `model.response`: execution result, submission list item, submission detail
- `model.query`: submission page/detail lookups as needed
- `model.result`: business outputs returned by service

`trace` target shape:

- `model.request`: trace page input
- `model.response`: trace detail, trace item, trace list item
- `model.query`: trace page/detail/item queries
- `model.result`: service outputs

`file` target shape:

- replace `FileRecordVO` with `model.response.FileRecordResponse` or `model.result.FileRecordResult` depending on caller boundary

`evaluation` target shape:

- preserve `model.dto` only where the type is genuinely an orchestration or LLM-binding structure
- move HTTP-facing request objects under `model.request` when exposed by controller or workflow entry

`rag` target shape:

- `model.request`: HTTP-facing rag document/search inputs
- `model.response`: HTTP-facing rag document/search outputs
- `model.query` / `model.result`: stable service contracts
- `model.dto`: only LLM-binding and domain-internal carriers that satisfy the DTO admission rule
- no `question` VO or request types embedded in rag-facing outputs

## Execution Rounds

### Round 1A: Workflow Dependency Contract Extraction

Primary targets:

- direct `question`, `submission`, `evaluation`, `trace`, and `conversation` contracts consumed by `workflow`

Objectives:

- define the minimum stable `command` / `query` / `result` contracts that `workflow` will consume in the same round
- remove the most direct service-boundary leakage required to support workflow migration, especially in `submission`, `evaluation`, `trace`, and `conversation`
- move trace-facing command/result ownership out of `workflow.trace` and into the `trace` domain or another explicit stable boundary owned outside workflow
- re-home reverse-imported workflow helpers such as access, prompt, split, or similar cross-domain utilities into the correct owning domain or an explicit shared subdomain
- identify any repo-wide Java consumers of rewritten service signatures and either migrate them in the same round or add temporary compatibility wrappers with same-round removal checkpoints

This sub-round is contract-first. It exists to prevent Round 1B from turning into a disguised big-bang rewrite.

### Round 1B: Workflow Boundary and Dependency Convergence

Primary targets:

- `workflow`

Objectives:

- fix `workflow` entry/request package ownership
- remove HTTP-boundary misuse in `WorkflowController` and `WorkflowService`
- replace direct workflow usage of foreign `entity` / `vo` / mapper types with the stable contracts extracted in Round 1A
- relocate or rename mismatched workflow-owned carriers such as request-shaped DTOs
- keep only domain-internal orchestration DTOs that satisfy the DTO admission rule

This sub-round remains the highest priority because `workflow` is the largest cross-domain consumer and currently amplifies violations across the module.

### Round 2: Question and RAG Domain Convergence

Primary targets:

- `question`
- `rag`

Objectives:

- replace controller `dto` / `vo` contracts with `request` / `response`
- introduce service `query` / `result` where missing
- move `rag.utils` to `rag.util`
- remove bidirectional `question` <-> `rag` imports of foreign `dto` / `vo` / `entity` / service types where a stable ownership boundary is missing
- re-home misplaced objects such as `TagPageRequest` and question-owned relation entities or sync records that currently live under `rag`
- clean up cross-domain leakage that remains after Round 1

These two domains are tightly coupled and should be cleaned together to avoid repeating conversion work.

### Round 3: Submission, Trace, File, and Evaluation Boundary Cleanup

Primary targets:

- `submission`
- `trace`
- `file`
- `evaluation`

Objectives:

- replace `PageRequest DTO + VO response` patterns with proper boundary types
- align services to `command` / `query` / `result`
- converge leftover response package placement issues
- reduce any remaining workflow-side coupling introduced by these domains

## Verification Strategy

Each round must end with:

- source-level import cleanup for the active domains
- `agent-oj-core` compilation success
- targeted tests for touched domains when they exist
- at minimum, smoke verification on affected controllers and services

Search-based verification must confirm no remaining active references in the completed round to:

- `model.vo`
- HTTP boundary `dto` types that should now be `request`
- controllers returning service `result` directly
- direct imports of foreign-domain `entity` from controller and workflow entry boundaries
- public service signatures that expose `request` / `response` / `vo` or disallowed `dto`
- non-interface helper classes stranded under `service.impl`
- plural `utils` package names in touched domains
- boundary objects stranded in `model` root without a justified subpackage
- test packages that no longer mirror the touched production package structure
- newly touched classes that still use disallowed acronym casing where a rename is part of the active refactor scope

Repo-wide verification must also confirm either:

- no downstream Java consumer outside `agent-oj-core` imports the rewritten types

or

- all such consumers are migrated in the same round and no temporary compatibility wrapper survives past that round

## Risks and Mitigations

### Risk 1: Workflow refactor exposes hidden contract coupling

Mitigation:

- treat Round 1 as a boundary-first round
- allow only minimal transitional adapters inside the same round
- verify compile and workflow tests before continuing

### Risk 2: Question and submission services may rely on MyBatis entity shape in public APIs

Mitigation:

- introduce result objects before removing old outputs
- centralize mapping in converter
- delete obsolete outputs only after all callers are migrated

### Risk 3: DTO removal may accidentally change JSON field names

Mitigation:

- preserve field names and serialization annotations during request/response migration
- keep endpoint behavior stable unless a bug requires a deliberate fix

## Success Criteria

The refactor is successful when:

- the active rounds complete without compile regression
- refactored controllers use only `request` and `response`
- refactored services use only `command` / `query` / `result` at their public boundary
- converters carry deterministic mapping only
- no new `VO` or plural `utils` remain in touched domains
- `workflow` no longer acts as a cross-domain bypass around service boundaries
- no ordinary business domain still depends on `workflow` as a generic utility source
