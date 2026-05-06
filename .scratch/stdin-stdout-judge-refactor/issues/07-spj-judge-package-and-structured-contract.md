# SPJ 判题包与结构化特判合同

Status: needs-triage

## What to build

在 go-judge 执行主路径和冻结判题包之上增加 SPJ 支持。题目采用 SPJ 时，试运行和正式提交都执行发布期固化的同一份特判程序，按 `stdin + userStdout + answerStdout` 合同输入，并以结构化 JSON 输出判定结果。

## Acceptance criteria

- [ ] SPJ 题的试运行与正式提交都执行同一份已固化到判题包中的特判程序，不在运行时动态生成特判逻辑
- [ ] 特判程序输入合同固定为 `stdin + userStdout + answerStdout`，成功输出合同固定为包含 `verdict` 和可选 `message` 的 JSON
- [ ] 特判程序非零退出、输出不合法或平台无法解释结果时统一记为 `SE`
- [ ] 用户侧结果可见性遵循公开/隐藏边界：公开语境可返回原始特判消息，隐藏语境只能返回脱敏摘要

## Blocked by

- `.scratch/stdin-stdout-judge-refactor/issues/03-go-judge-async-submit-cutover.md`
- `.scratch/stdin-stdout-judge-refactor/issues/04-immutable-version-and-frozen-judge-package-publication.md`
- `.scratch/stdin-stdout-judge-refactor/issues/06-multi-language-assets-and-session-drafts.md`
