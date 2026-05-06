# 项目概览

- 项目定位：`agent-oj` 是一个面向算法题场景的 Agent OJ 平台，覆盖题目检索、AI 对话、代码评测、知识库 RAG、链路追踪和后台管理等能力。
- 后端形态：采用 `Java 17 + Spring Boot 3` 的多模块 Maven 工程，主启动入口为 `agent-oj-start` 模块中的 `AgentOjWorkflowApplication`。
- 前后端组成：仓库同时包含用户端 `oj-ui`、管理端 `oj-admin-ui`，以及用于 RAG 评估 / 辅助处理的 `ragas` Python 子项目。
- 核心模块：
  - `agent-oj-core`：算法题、提交评测、工作流、RAG、文件、Trace 等核心业务
  - `agent-oj-security`：认证鉴权、用户、角色、权限、端点等安全域能力
  - `agent-oj-admin`：管理端用户管理、题目管理、RAG 评估等后台能力
  - `agent-oj-common` / `agent-oj-redis`：公共能力与 Redis 基础设施能力
  - `agent-oj-start`：应用装配、配置加载与启动入口
- 开发入口：
  - 改后端业务时，先确认所属领域模块，再在该模块内按 `controller / service / mapper / converter / model.*` 结构增量修改
  - 改用户端页面或交互时，进入 `oj-ui`
  - 改管理端页面或后台交互时，进入 `oj-admin-ui`
  - 改 RAG 评估脚本或 Python 辅助逻辑时，进入 `ragas`
- 变更原则：开始编码前，先判断新增对象应归属 `request`、`response`、`command`、`query`、`result`、`entity` 还是 `dto`，再按下述规范落位，避免跨层复用和职责混放。

# 开发规范

## POJO 设计规范

本仓库后端代码中的 POJO 必须一类一职责、按边界分层：
- `entity` 只表示持久化结构，`request`/`response` 只用于 controller 输入输出，`command`/`query`/`result` 只作为 service 的写入意图、查询条件和业务返回，`dto` 仅在这些类型都不合适时使用
- 推荐流向为 `controller -> request -> converter -> command/query -> service`、`service -> result -> converter -> response -> controller`、`service -> entity -> mapper`，禁止 `request`/`response`/`entity` 跨层滥用。
- `converter` 必须放在专门包中，只做确定性的字段映射、空值处理、枚举转换、轻量格式化和脱敏，不得查库、调远程接口或承载业务决策。
- 包管理规范：entity、request、response、command、query、result 等都放置于各领域的 model 包下

| 类型 | 面向谁 | 解决什么问题 | 典型内容 |
| --- | --- | --- | --- |
| request | Controller / HTTP 边界 | 接住外部输入 | @RequestBody、@RequestParam、校验注解 |
| response | Controller / HTTP 边界 | 返回给前端或调用方 | 脱敏字段、展示字段、接口输出结构 |
| command | 应用服务 / 写操作 | 表达“要做什么” | 创建、更新、启用、禁用、设默认 |
| query | 应用服务 / 读操作 | 表达“要查什么” | 条件、分页、排序、筛选 |
| result | 应用服务 / 业务输出 | 表达“业务得到什么” | 业务结果、聚合数据、非 HTTP 专属结构 |

## 模块与包结构规范

- 一级包优先表达业务域，而不是统一按 `controller`、`service`、`mapper` 对整个模块做技术分层；包名使用全小写英文单词，表达稳定业务语义，不使用复数、临时技术概念或含糊缩写。
- 领域内按职责拆分子包，推荐使用 `controller`、`service`、`service.impl`、`mapper`、`converter`、`client`、`config`、`exception`、`util`，以及 `model.entity`、`model.request`、`model.response`、`model.command`、`model.query`、`model.result`；`model.dto` 仅在这些类型都不合适时使用。
- `model` 是容器层，不是兜底层；禁止长期把不同边界对象直接混放在 `model` 根包中。
- `event`、`listener`、`factory`、`registry`、`strategy`、`task`、`processor`、`bridge`、`store` 等条件型包，只在职责稳定、语义明确时使用，不能作为“暂时不好归类”的收纳箱。

## 组件职责与依赖规范

- 推荐依赖方向为 `controller -> service -> mapper/client/converter`；控制器不得直接依赖 `mapper`、其他领域的 `entity`、其他领域内部 `dto` 或流程对象。
- 跨领域调用优先依赖对方 `service` 接口，其次才是稳定共享模型；禁止直接依赖他域的 `request`、`response`、`entity`、内部 `dto`、流程状态对象。
- 被多个领域长期共用的模型，必须明确归属到单一领域并通过公开能力暴露，或抽取为独立共享子域；禁止长期通过互相 `import` 对方内部模型维持协作。
- `service.impl` 只放服务接口的实现类，并与接口一一对应；外部调用封装放 `client`，多步骤处理组件放 `processor`，不要把非服务实现类塞进 `service.impl`。
- `util` 全仓统一使用单数形式，只承载纯静态、无状态、非领域核心的辅助逻辑；依赖复杂上下文或需要注入多个依赖的对象，不得继续放入 `util`。
- `workflow` 一类编排域负责组织流程，不作为其他普通业务域的公共工具箱；其他领域不得反向依赖其节点、边或内部执行对象。

## 命名规范

- 包表达边界，类表达语义；在 `model.entity` 包中默认不再叠加 `Entity` 后缀，避免出现 `ConversationEntity` 一类冗余命名。
- Controller 入参统一使用 `*Request`，Service 写入意图使用 `*Command`，查询条件使用 `*Query`，业务结果使用 `*Result`，Controller 返回统一使用 `*Response`；分页对象按真实边界使用 `*PageRequest` 或 `*PageQuery`。
- `DTO` 只用于内部编排、跨模块共享但不直接暴露为 HTTP 输出的对象，或与第三方结构绑定的转换对象；不得把 `DTO` 当作中间对象默认后缀，不再新增 `VO`。
- 有接口时实现类使用一一对应的 `*Impl`；没有接口的具体组件直接使用职责名，不为了形式统一滥加 `Impl`。
- 组件后缀必须和职责一致，统一使用 `Controller`、`Service`、`Mapper`、`Client`、`Converter`、`Processor`、`Factory`、`Registry`、`Strategy`、`Task`、`Bridge` 等后缀表达角色。
- 缩写按普通单词驼峰化处理，如 `AiModel`、`RagJudge`、`LlmOutput`、`TopK`、`HitK`；禁止混用 `RAG`、`LLM`、`Hitk` 等不一致写法。

## 测试与增量治理规范

- 测试包路径必须镜像生产包路径，允许测试类名表达具体场景，但不允许出现与生产路径不一致的临时包名、错误拼写或随意缩写。
- 新增代码必须遵守上述规范；修改历史代码时，优先在触达范围内做局部收敛，不一次性做无边界的大规模重命名。
- 不再新增 `utils`、混合 `model` 包、`VO`、跨领域直接依赖他域 `entity/request/internal dto` 的实现，以及语义不清的兜底包。

## Agent skills

### Issue tracker

Issues and PRDs for this repo are tracked as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

This repo uses the default triage labels `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

This repo is configured as a single-context repo; skills should consult the root `CONTEXT.md` and `docs/adr/` when present. See `docs/agents/domain.md`.
