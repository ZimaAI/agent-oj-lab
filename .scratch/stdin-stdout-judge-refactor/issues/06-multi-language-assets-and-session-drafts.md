# 多语言资产与语言级会话草稿

Status: needs-triage

## What to build

围绕 `题目语言资产` 重建多语言做题体验。每个题目版本按语言持有独立的起始模板、语言参考解答和编译运行配置；用户首次切换到某门语言时从该语言模板初始化独立草稿，之后各语言草稿互不覆盖。运行和提交只能针对当前版本显式支持的语言集合发起。

## Acceptance criteria

- [ ] 一个题目版本可声明多种支持语言，每种语言都拥有独立的起始模板、语言参考解答和编译运行配置
- [ ] 用户首次切换到某门语言时会初始化该语言的独立会话草稿，后续切换语言不会覆盖其他语言草稿
- [ ] `运行` 和 `提交` 只能针对当前题目版本声明支持的语言发起，且都继续使用 go-judge 执行
- [ ] 用户端、后台端和核心 service 契约统一使用 `题目语言资产` 语义，不再把共享函数骨架当作多语言入口

## Blocked by

- `.scratch/stdin-stdout-judge-refactor/issues/01-standard-flow-question-contract-and-case-pool.md`
- `.scratch/stdin-stdout-judge-refactor/issues/05-current-version-resolution-and-session-binding.md`
