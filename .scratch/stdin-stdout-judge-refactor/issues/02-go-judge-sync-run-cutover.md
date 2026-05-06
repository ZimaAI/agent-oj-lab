# `试运行` 主路径切到 go-judge 并下线 GraalVM

Status: needs-triage

## What to build

把用户点击 `运行` 的在线执行主路径直接重构为 go-judge 标准流执行，不再沿用 GraalVM、函数名调用、参数对象用例和共享函数骨架。用户输入完整源码后，平台基于题目的公开标准流用例，通过 go-judge 完成编译、运行、资源限制和结果采集，并同步返回全部公开用例的逐用例明细。

## Acceptance criteria

- [ ] `运行` 只接受完整源码、`questionId` 和语言信息，按标准输入 / 标准输出契约执行公开用例，不再使用 `functionName`、`testInputs` 或函数返回值比对
- [ ] 试运行通过 go-judge 完成编译与运行，返回全部公开用例结果，即使中途 WA 或 RE 也不中断剩余公开用例
- [ ] 编译失败直接返回 CE 与原始编译诊断，不生成逐用例明细，也不创建提交记录
- [ ] 在线试运行链路不再依赖 GraalVM 执行器、Graal 相关 Spring Bean 或旧的函数调用式执行工具

## Blocked by

- `.scratch/stdin-stdout-judge-refactor/issues/01-standard-flow-question-contract-and-case-pool.md`
