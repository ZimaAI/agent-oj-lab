# workflow 仅创建新草稿题并产出可评测资产

Status: needs-triage

## What to build

重写 AI 出题 workflow 的写入边界和输出结构，让 workflow 只负责创建新的 `题库题目草稿` 或新的 `草稿版本`，并直接产出可供 go-judge 评测的标准流题面、统一用例池、语言资产、判题参考程序和按需存在的特判程序。失败的生成尝试要保留为独立草稿，而不是覆盖旧现场。

## Acceptance criteria

- [ ] workflow 只创建新的题库题目草稿或新的草稿版本，不修改既有题目或既有已发布版本
- [ ] workflow 输出直接面向标准流题目和 go-judge 判题资产，不再生成函数调用式题目结构
- [ ] workflow 失败时保留 `失败草稿版本` 和结构化失败原因，不覆盖同题此前的失败尝试
- [ ] 后台可查看同一题库题目下的 `草稿尝试序列`，用于审计生成和修复过程

## Blocked by

- `.scratch/stdin-stdout-judge-refactor/issues/01-standard-flow-question-contract-and-case-pool.md`
- `.scratch/stdin-stdout-judge-refactor/issues/04-immutable-version-and-frozen-judge-package-publication.md`
- `.scratch/stdin-stdout-judge-refactor/issues/06-multi-language-assets-and-session-drafts.md`
- `.scratch/stdin-stdout-judge-refactor/issues/07-spj-judge-package-and-structured-contract.md`
