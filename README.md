# agent-oj

`agent-oj` 是一个面向算法题场景的 Agent OJ 平台，覆盖题目检索、AI 对话、代码评测、知识库 RAG、链路追踪和后台管理等能力。

仓库是单一上下文的多项目工作区：

- 后端是 `Java 17 + Spring Boot 3` 的多模块 Maven 工程
- 用户端和管理端前端使用 `Vue 3 + TypeScript + Vite`
- `ragas` 子项目提供独立的 Python RAG 评估服务

## 核心能力

- 算法题检索、标签分页、题目详情查询
- AI 工作流对话与流式返回
- 代码试运行、正式提交、提交结果查询
- 知识文档上传、解析、分段、RAG 检索与评估
- 管理端题目管理、用户管理、RAGAS / HitK 任务管理
- 认证鉴权、角色权限、接口扫描、链路追踪

## 技术栈

| 层次 | 方案 |
| --- | --- |
| 后端 | Java 17, Spring Boot 3, Spring Web, Spring Validation |
| 数据访问 | MyBatis, MyBatis-Plus, MySQL, Druid |
| AI / RAG | Spring AI, Spring AI Alibaba, LangChain4j, Milvus, MinIO |
| 基础设施 | Redis, Redisson, OpenTelemetry |
| 前端 | Vue 3, TypeScript, Vite, Pinia, Vue Router |
| Python 子项目 | FastAPI, Uvicorn, pytest，可选 `ragas` / `datasets` |

## 仓库结构

| 路径 | 说明 |
| --- | --- |
| `agent-oj-start` | 应用装配与启动入口，主类为 `AgentOjWorkflowApplication` |
| `agent-oj-core` | 核心业务域：题目、提交评测、会话、工作流、RAG、文件、Trace、AI 模型等 |
| `agent-oj-security` | 认证鉴权、用户、角色、权限、端点、表维护等安全域能力 |
| `agent-oj-admin` | 管理端后端能力：题目管理、RAGAS / HitK、用户后台接口、模块健康检查 |
| `agent-oj-common` | 公共常量、基础模型、通用工具与共享能力 |
| `agent-oj-redis` | Redis 与 Redisson 基础设施封装 |
| `oj-ui` | 用户端前端，默认本地端口 `3001` |
| `oj-admin-ui` | 管理端前端，默认本地端口 `3002` |
| `ragas` | Python RAG 评估服务，默认端口 `8000` |
| `docs` | SQL 脚本、ADR、参考资料、设计文档 |
| `CONTEXT.md` | 项目领域词汇与系统语义说明 |
| `AGENTS.md` | 仓库开发约定与对象分层规范 |

## 后端模块地图

后端最终以一个 Spring Boot 进程运行，`agent-oj-start` 负责组装其余模块。

- `agent-oj-core`
  - `aimodel`：AI 模型配置与默认模型管理
  - `conversation`：会话与消息
  - `evaluation` / `executor` / `submission`：试运行、正式提交、执行与判题
  - `question`：算法题、标签、题目查询
  - `rag`：知识文档、分段、向量检索
  - `trace`：链路追踪查询
  - `workflow`：AI 工作流编排与流式输出
- `agent-oj-security`
  - `auth`：登录与令牌刷新
  - `user` / `role` / `permission`：用户、角色、权限
  - `endpoint`：端点扫描
  - `maintenance`：表维护类管理接口
- `agent-oj-admin`
  - `question`：题目后台管理、文档、分段、补偿任务、RAGAS / HitK
  - `user`：后台用户管理
  - `health`：管理模块健康检查

## 关键设计约束

- 评测契约基于标准输入 / 标准输出，而不是函数签名调用。
- 题目版本是不可变快照，正式提交依赖冻结的判题包与默认执行环境版本。
- AI workflow 是 create-only 的生成链路；既有题目版本的修订由后台发布流程负责。
- 仓库对边界对象分层较严格：`request / response / command / query / result / entity / dto` 各自承担不同职责。

更完整的领域语言与约束请先看：

- [`CONTEXT.md`](./CONTEXT.md)
- [`docs/adr/0001-question-version-binds-default-environment-and-judge-package.md`](./docs/adr/0001-question-version-binds-default-environment-and-judge-package.md)
- [`AGENTS.md`](./AGENTS.md)

## 快速开始

### 1. 环境准备

建议准备以下本地依赖：

- JDK `17`
- Maven `3.9+`
- Node.js `20.19+` 或 `22.12+`
- Python `3.10+`
- MySQL `8.x`
- Redis
- Milvus
- MinIO
- 可用的 AI 模型服务
- 可选：MinerU 服务、RAGAS 评估依赖

### 2. 初始化数据库

创建数据库：

```sql
CREATE DATABASE agent_oj_workflow
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

然后依次执行：

- `docs/script/mysql/agent_oj_workflow_ddl.sql`
- `docs/script/mysql/ai_model_config.sql`
- `docs/script/mysql/ai_model_default.sql`

### 3. 配置后端

后端配置位于 `agent-oj-start/src/main/resources/`，重点关注：

- `application.yml`
- `application-local.yml`
- `application-docker.yml`

启动前请按本地环境覆盖以下配置，不要直接依赖仓库中的默认值：

- `spring.datasource.*`
- `spring.data.redis.*`
- `spring.ai.*`
- `app.vector.milvus.*`
- `app.file.minio.*`
- `app.mineru.*`
- `app.ragas.service.*`

建议优先通过本地 profile 或环境变量覆盖敏感信息，不要把真实密钥和内网地址提交回仓库。

### 4. 启动后端

在仓库根目录执行：

```bash
mvn -pl agent-oj-start -am spring-boot:run
```

默认端口为 `8080`。

如需先打包再运行：

```bash
mvn clean package
java -jar agent-oj-start/target/agent-oj-start-1.0.0-SNAPSHOT.jar
```

### 5. 启动用户端前端

```bash
cd oj-ui
npm install
npm run dev
```

- 本地地址：`http://127.0.0.1:3001`
- 开发代理：`/api`、`/span-record` 转发到 `http://localhost:8080`

### 6. 启动管理端前端

```bash
cd oj-admin-ui
npm install
npm run dev
```

- 本地地址：`http://127.0.0.1:3002`
- 开发代理：`/api` 转发到 `http://localhost:8080`

### 7. 启动 RAGAS 评估服务（可选）

```bash
cd ragas
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

- 健康检查：`http://127.0.0.1:8000/health`
- 评估接口：`POST /evaluate`

如果未安装 `ragas` / `datasets`，服务会以降级模式运行，接口仍可用，但不会执行真实 RAGAS 指标计算。

## 常用命令

### 后端

```bash
mvn test
mvn -pl agent-oj-start -am spring-boot:run
```

### 用户端前端

```bash
cd oj-ui
npm run build
npm run test:unit
```

### 管理端前端

```bash
cd oj-admin-ui
npm run build
npm run test:unit
```

### RAGAS 服务

```bash
cd ragas
pytest -q
```

## 主要接口分区

只列根路由，便于快速定位代码：

- `/api/auth`：登录、刷新令牌
- `/api/users`、`/api/roles`、`/api/permissions`：安全域管理
- `/api/algorithm-question`、`/api/tag`：题目与标签查询
- `/api/conversations`：会话与消息
- `/api/workflow`：AI 工作流流式接口
- `/api/code`：试运行、正式提交、提交查询
- `/api/knowledge-documents`：知识文档解析与查询
- `/api/files`：文件上传与文件分页
- `/api/trace`：链路追踪查询
- `/api/admin/questions`：题目后台管理、文档、分段、RAGAS / HitK 任务
- `/api/admin/users`：后台用户管理

## 开发入口建议

- 改后端业务：先判断落在哪个领域模块，再在该领域内按 `controller / service / mapper / converter / model.*` 结构增量修改
- 改用户端页面：进入 `oj-ui`
- 改管理端页面：进入 `oj-admin-ui`
- 改 RAG 评估脚本或 Python 辅助逻辑：进入 `ragas`

新增后端对象前，先判断它属于哪一种边界类型：

- `request`：Controller 输入
- `response`：Controller 输出
- `command`：Service 写入意图
- `query`：Service 查询条件
- `result`：Service 业务返回
- `entity`：持久化结构
- `dto`：仅在以上类型都不合适时使用

## 相关文档

- [仓库开发约定](./AGENTS.md)
- [领域上下文](./CONTEXT.md)
- [架构决策记录](./docs/adr)
- [数据库脚本](./docs/script/mysql)
- [RAGAS 子项目说明](./ragas/README.md)

## 当前仓库状态说明

- 根目录当前没有统一的容器编排文件，基础依赖需要本地自行准备
- `oj-ui` 提供了用于静态部署的 `Dockerfile`
- `ragas` 可以独立启动，也可以只在需要评估任务时启动
