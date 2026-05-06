# 程序题标准输入输出评测重构 PRD

Status: needs-triage

## Problem Statement

当前程序题评测仍然深度依赖函数名、参数对象和 GraalVM 内部执行模型，这与算法题常见的标准输入 / 标准输出契约不一致，也让题目本体、题目语言资产、判题资产和评测流程长期耦合在一起。用户视角下，“运行”和“提交”的边界不清晰，试运行和真实评测共享了一套不适合在线 OJ 的语义；平台视角下，正式提交仍然容易依赖运行时拼装的判题数据或参考实现，导致题目版本、提交快照、结果追溯和自动验题都不够稳定。

本次重构的技术目标不仅是替换判题语义，也包括彻底替换代码执行底座：程序题执行必须从当前依赖 GraalVM 的内嵌运行方式，完全重构为依赖 go-judge 服务完成编译、运行、资源限制和评测隔离的方式。

与此同时，平台已经确认要保留 AI 辅助出题并自动验题的能力，但不再要求兼容旧版函数调用式模型。当前需要把程序题重构为完整的标准流评测体系，并把题目模型、前端协议、workflow prompt、判题语义、发布闸门和后台管理方式一起重做，使其能够支撑：

- 用户的同步试运行
- 用户的异步正式提交
- 后台管理员对同一题目的多版本演进
- workflow 只创建新题，不修改既有题
- AI 生成草稿题目的自动验题与自动发布

## Solution

平台将程序题统一切换为标准输入 / 标准输出评测契约，并将代码执行基础设施从 GraalVM 内嵌执行完全切换为 go-judge 服务执行，同时围绕“题目版本 + 题目语言资产 + 判题包 + 提交快照”重建评测链路。

用户将获得两类清晰分离的能力：

- `试运行`：针对题面公开的示例用例做同步即时执行，不留提交记录，返回全部公开用例的逐用例明细
- `正式提交`：针对完整判题集做异步真实评测，创建提交记录并返回最终结果，只消费已固化的判题包

后台管理员将以“为既有题目创建新的不可变题目版本”的方式维护题库；workflow 只负责创建新的题库题目草稿，并通过自动发布闸门把符合要求的草稿转为可对外生效的题目版本。标准比对题采用最小文本归一化并只区分 AC / WA；需要更宽松或更复杂语义的题目必须显式使用 SPJ。正式提交不再运行语言参考解答，而是仅依赖发布期固化好的判题数据和特判程序。

## User Stories

1. As a 做题用户, I want to click `运行` and immediately see all public case results, so that I can validate my code against the same examples shown in the statement.
2. As a 做题用户, I want `运行` to execute only 示例用例, so that I can debug without touching hidden judging data.
3. As a 做题用户, I want `运行` to continue through all public cases even after a failure, so that I can inspect multiple problems in one attempt.
4. As a 做题用户, I want `提交` to produce the real official verdict against the 完整判题集, so that I know whether the solution actually passes.
5. As a 做题用户, I want `提交` to be asynchronous and persisted, so that I can leave the page and still retrieve the final result.
6. As a 做题用户, I want compile errors to be shown directly, so that I can fix syntax and environment issues quickly.
7. As a 做题用户, I want hidden-case failures to avoid leaking raw hidden data, so that the platform preserves judging fairness.
8. As a 做题用户, I want language switching to initialize from that language’s 起始模板, so that each language starts from a runnable baseline.
9. As a 做题用户, I want my editor draft to be scoped per language, so that switching languages does not overwrite unrelated code.
10. As a 做题用户, I want official judging to use the same 题目版本语义 throughout one session, so that I do not see inconsistent results caused by mid-session content drift.
11. As a 题目读者, I want the 题目本体 to describe only language-agnostic statement content, so that the problem statement stays clean and stable.
12. As a 出题管理员, I want to create a new 题目版本 under an existing 题库题目, so that I can evolve the problem without overwriting history.
13. As a 出题管理员, I want published 题目版本 to be immutable, so that submission records remain traceable and auditable.
14. As a 出题管理员, I want one unified 用例池 with clear public and hidden markers, so that sample cases and official judging data stay aligned.
15. As a 出题管理员, I want all public cases to appear before hidden cases in stable case order, so that run semantics and version semantics are deterministic.
16. As a 出题管理员, I want each supported language to carry its own 起始模板, 语言参考解答 and compile/run config, so that language differences are explicit.
17. As a 出题管理员, I want 标准比对题 to use fixed 标准流用例 outputs instead of running a reference solution at submit time, so that official judging is stable and reproducible.
18. As a 出题管理员, I want SPJ 题 to ship a fixed 特判程序 with self-check samples, so that special judging behavior can be validated before publish.
19. As a 出题管理员, I want failed AI-generated drafts to be preserved with structured failure reasons, so that I can analyze why a draft did not pass the 发布闸门.
20. As a 后台管理员, I want to see a 草稿尝试序列 for workflow generation, so that auto-validation failures are auditable instead of being silently discarded.
21. As a workflow 设计者, I want workflow to create new 题库题目草稿 only, so that AI generation never mutates an existing bank question in place.
22. As a workflow 设计者, I want the code-generation stage to produce 示例用例, 完整判题集, 语言参考解答 and optional 特判程序 / 判题参考程序, so that generated problems are fully judgeable.
23. As a workflow 设计者, I want workflow prompt semantics to target stdin/stdout problems instead of function-call problems, so that generated content matches the real platform contract.
24. As a 平台维护者, I want 正式提交 to consume a frozen 判题包 only, so that runtime evaluation does not depend on mutable authoring assets.
25. As a 平台维护者, I want `SE` to represent platform-side failures only, so that user-code problems are not misclassified as system errors.
26. As a 平台维护者, I want compile-once and case-isolated execution semantics, so that judging is efficient while remaining deterministic per case.
27. As a 平台维护者, I want 标准比对判题 to allow only minimal newline normalization, so that correctness rules stay strict and explicit.
28. As a 平台维护者, I want SPJ output to follow a fixed structured contract, so that the judge engine can interpret special verdicts safely.
29. As a 平台维护者, I want no manual review step in the current flow, so that AI-generated problems can move from draft to publish entirely through automated gates.
30. As a 平台维护者, I want ordinary users to continue operating by `questionId` rather than `questionVersionId`, so that frontend protocol changes remain controlled while backend versioning is strengthened.

## Implementation Decisions

- 程序题评测契约统一切换为标准输入 / 标准输出，彻底移除函数名、参数列表、返回值比对这类函数调用式判题语义。
- 程序题代码执行基础设施统一切换为 go-judge 服务，彻底移除当前 GraalVM 内嵌执行链路，不再保留双轨执行模型。
- 题目领域需要拆分为稳定的 `题库题目`、不可变的 `题目版本`、按语言组织的 `题目语言资产` 与面向评测执行的 `判题资产 / 判题包`。
- `questionVersionId` 作为当前有效的后端主语义字段，进入题目快照、版本绑定会话、提交快照、提交记录和发布结果等核心对象。
- 前端协议在当前范围内继续以 `questionId` 作为用户侧主标识；用户进入题目时由后端解析并绑定当前对外生效的 `题目版本`。
- 同一道题目允许存在多个题目版本，但版本一旦发布即不可原地修改；后台管理员只能通过“创建新版本”的方式修订题目。
- workflow 对题库的写入范围仅限“创建新的题库题目草稿”；workflow 不得修改既有题目，也不承担题目版本修订职责。
- AI 生成链路需要改写 prompt 和输出结构，要求直接生成标准流题面、公开示例、隐藏用例、语言参考解答、判题参考程序以及按需存在的特判程序。
- 题目本体只保留语言无关内容，包括题意、输入说明、输出说明、约束、示例用例、判题方式和展示规则；不再内嵌共享函数签名或共享代码骨架。
- 每种支持语言都需要独立的题目语言资产，至少包含起始模板、语言参考解答、编译运行配置，以及该语言在编辑器初始化时所需的默认内容。
- 起始模板必须是完整可运行程序，不允许只是函数片段；发布闸门要求其在全部公开用例上稳定得到 WA，不能得到 AC、CE、TLE、MLE、RE 或 SE。
- 判题资产采用“一套统一用例池 + 公开 / 隐藏标记”的建模方式；示例用例就是公开用例，完整判题集等于公开用例加隐藏用例。
- 题目版本内的用例序号和用例顺序属于版本语义的一部分，发布后不可重排；所有公开用例必须排在所有隐藏用例之前。
- 正式提交只消费发布时固化后的判题包，不直接运行语言参考解答，也不在运行时动态生成标准答案。
- 语言参考解答作为学习、讲解、模板生成和题目制作的语言资产保留，但不进入正式提交链路。
- 新增独立的判题参考程序作为题目制作期资产，用于生成或校验固定判题数据；它服务于出题和发布，不服务于正式提交执行。
- 标准比对判题只允许最小文本归一化：统一换行符为 `\n`，忽略文件末尾单个换行差异；除此之外任何差异都判为 WA。
- 当前答案匹配层只保留 AC 和 WA，不引入 PE；需要非严格文本相等语义时必须显式切换为 SPJ。
- SPJ 判题使用单一固定的特判程序，输入合同为 `stdin + userStdout + answerStdout`，成功输出合同为结构化 JSON，至少包含 `verdict` 和可选 `message`。
- 特判程序非零退出、结构化输出不合法或平台无法解释结果时，一律按平台侧失败处理并记为 SE。
- 试运行采用同步接口，执行全部公开用例，不创建后台任务，不写入提交记录，也不产生历史留档。
- 正式提交采用异步接口，先创建提交记录和提交快照，再由后台完成评测并回写结果；官方判题遇到首个非 AC 结果后短路停止。
- 单次评测对同一份提交源码只编译一次；每个用例都在隔离的新进程中运行，编译失败时不生成逐用例明细。
- go-judge 负责承接编译、运行、资源限制、输出采集和进程隔离；平台上层只负责编排题目版本、判题包、结果解释和可见性控制。
- 提交结果面向用户的终态需要区分 AC、WA、CE、TLE、MLE、RE、SE；其中 AC / WA 属于答案层结果，其余属于执行终态。
- 错误信息可见性需要按公开语境和隐藏语境区分：试运行可返回原始错误；正式提交仅公开 CE 诊断和公开用例的原始错误输出，隐藏用例失败只返回脱敏摘要。
- AI 自动发布流不引入人工审核，而是引入严格的自动发布闸门；通过则发布，失败则保留为失败草稿版本并记录结构化失败原因。
- 发布闸门至少要校验：公开 / 隐藏用例数量约束、公开前置顺序、标准答案固化、起始模板验收、各支持语言可评测、SPJ 自验样例、判题包完整性和题目资产一致性。
- 当前范围不实现批量出题任务；AI 出题以单题工作流为单位，失败草稿和草稿尝试序列需要独立保存。
- 当前范围不实现重判、迁移并重判、交互题、部分分、题目前端显式版本切换或普通用户直接访问 `questionVersionId` 的协议能力。
- 现有后台“原地修改题目”的能力需要重构为“为既有题目创建新版本”的能力，避免覆盖既有版本语义和既有提交可追溯性。

## Testing Decisions

- 测试优先验证外部行为，不验证内部实现细节；重点关注输入合同、输出合同、持久化边界、状态流转和错误可见性是否符合业务语义。
- 核心判题语义需要独立测试，包括最小文本归一化、标准比对 AC/WA 规则、SPJ 合同解释、SE 归因边界和短路判题行为。
- 试运行服务与正式提交服务都需要服务层测试，覆盖同步 / 异步差异、公开 / 隐藏用例差异、编译失败、运行失败、资源限制和结果聚合。
- 判题包装配与提交快照固化需要测试，确保正式提交只依赖冻结数据而非运行时读取可变出题资产。
- 发布闸门需要深度测试，覆盖标准比对题、SPJ 题、缺失公开用例、缺失隐藏用例、模板错误、SPJ 自验失败和多语言资产不完整等场景。
- 管理端题目版本发布流程需要测试，重点验证“新建版本而非覆盖版本”的行为，以及失败草稿版本与草稿尝试序列的留存规则。
- workflow 生成链路需要测试 create-only 约束，确保其只能新增题库题目草稿，不能改写既有题目或既有题目版本。
- 前后端接口契约需要 controller 层测试，重点验证 `运行`、`提交`、题目详情、管理员版本创建与发布接口的输入输出边界。
- 题目语言资产和起始模板校验需要测试，保证每种支持语言都能在公开用例上稳定得到 WA，而不是其它执行终态。
- 提交可见明细与错误信息可见性需要测试，确保公开用例允许展示原始细节，隐藏用例严格返回脱敏摘要。
- 测试组织方式应延续仓库现有的 controller / service / converter / workflow node 分层测试习惯，在核心业务模块和管理模块中分别建立行为测试。
- 对外部 go-judge 交互建议使用适配层契约测试和必要的集成测试，避免把第三方接口细节扩散到上层业务测试中。

## Out of Scope

- 兼容旧版函数调用式评测模型
- 交互题
- PE 状态与宽松文本比较
- 部分分、子任务分组和 OI 记分制
- 重判功能
- 迁移并重判功能
- 批量出题任务
- workflow 修改既有题库题目
- 普通用户直接选择或感知 `questionVersionId`
- 人工审核发布流

## Further Notes

- 这次是一次允许破坏性调整的彻底重构，数据库模型、业务流程、前端协议和 workflow prompt 都按新契约重做，不要求兼容旧字段或旧评测语义。
- 旧有函数调用式字段和流程虽然可以作为迁移参考，但不应继续保留为长期双轨能力，否则会破坏题目本体、题目语言资产与判题资产之间的新边界。
- 当前普通用户总是使用某个 `questionId` 下的当前对外生效版本；历史版本主要服务于后台演进、提交追溯和审计。
- AI 辅助出题的价值不在于直接把参考解答拿去正式判题，而在于帮助生成可固化、可验证、可发布的判题资产和题目语言资产。
