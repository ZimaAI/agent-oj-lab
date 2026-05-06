# `正式提交` 主路径切到 go-judge 异步评测

Status: needs-triage

## What to build

把用户点击 `提交` 的正式评测主路径直接重构为 go-judge 异步评测，不再走 GraalVM，也不再依赖运行时拼装的旧式判题数据。提交时先创建提交记录与后台任务，再由 go-judge 对完整判题集执行真实评测并回写最终判定。

## Acceptance criteria

- [ ] `提交` 为异步流程：接口先创建提交记录并返回提交标识，再由后台完成评测并回写最终结果
- [ ] 正式提交通过 go-judge 对完整判题集执行真实评测，同一份源码只编译一次，每个用例在独立进程中运行，并在首个非 AC 结果后短路停止
- [ ] 用户侧结果区分 AC、WA、CE、TLE、MLE、RE、SE；隐藏用例失败不泄露原始隐藏数据，只返回脱敏摘要
- [ ] 正式提交链路不再依赖 GraalVM、函数调用式执行模型或运行时执行语言参考解答来生成官方结果

## Blocked by

- `.scratch/stdin-stdout-judge-refactor/issues/01-standard-flow-question-contract-and-case-pool.md`
- `.scratch/stdin-stdout-judge-refactor/issues/02-go-judge-sync-run-cutover.md`
