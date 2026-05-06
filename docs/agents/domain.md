# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Current layout

This repo is configured as a single-context repo.

When relevant files exist, read:

- `CONTEXT.md` at the repo root
- `docs/adr/` at the repo root

If these files do not exist yet, proceed silently. Do not flag their absence and do not suggest creating them upfront.

## Expected file structure

```text
/
├── CONTEXT.md
├── docs/adr/
└── src/
```

## Use the glossary's vocabulary

When your output names a domain concept, use the term as defined in `CONTEXT.md`. Avoid inventing synonyms when the glossary already defines a preferred term.

If the concept you need is not in the glossary yet, either the term does not belong in this repo's language or the documentation has a real gap that should be captured later.

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding it.
