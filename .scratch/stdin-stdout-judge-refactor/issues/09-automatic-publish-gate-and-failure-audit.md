# 自动发布闸门与失败原因审计

Status: needs-triage

## What to build

建立面向草稿版本的自动发布闸门。在默认执行环境版本上自动校验统一用例池、固定标准答案、语言资产验收、SPJ 自验样例和判题包完整性；通过则自动发布，失败则保留失败草稿并记录结构化失败原因。整个流程不引入人工审核，也不再为旧执行模型保留兼容分支。

## Acceptance criteria

- [ ] 发布闸门会校验公开/隐藏用例数量约束、公开前置顺序、标准答案固化和判题包完整性
- [ ] 每种支持语言都必须通过语言资产验收：语言参考解答可通过正式评测，起始模板在全部公开用例上稳定得到 WA 而非 AC、CE、TLE、MLE、RE、SE
- [ ] SPJ 题必须通过至少一组判 AC 和一组判 WA 的自验样例，确保特判合同真实可执行
- [ ] 闸门失败时保留失败草稿版本与结构化失败原因，闸门成功时自动发布，不引入人工审核步骤

## Blocked by

- `.scratch/stdin-stdout-judge-refactor/issues/07-spj-judge-package-and-structured-contract.md`
- `.scratch/stdin-stdout-judge-refactor/issues/08-workflow-create-only-draft-sequence.md`
