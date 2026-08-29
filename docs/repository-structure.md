# 仓库结构与归属

本仓库在同一 Git 历史中并列维护前端应用、后端 Source Agents 与语言无关的共享合同。前端继续位于仓库根目录；这次整理没有把前端改造成 monorepo package，也没有建立前端对 Java 源码的运行时依赖。

详细的设计、迁移算法与验收合同见[英文结构设计](./design/repository-frontend-backend-structure.md)和[中文结构设计](./design/repository-frontend-backend-structure.zh-CN.md)。

## 目录与所有权

```text
linguan-prototype-v2/
├── src/、public/、scripts/、deploy/     # 前端应用与部署
├── package.json、package-lock.json     # 前端命令与依赖
├── backend-agents/                     # 后端 Source Agent 工作区
│   ├── AGENTS.md、CONTEXT.md、README.md
│   └── sources/
│       ├── github-code/                # Java 17 + Maven Code Agent
│       ├── mysql/
│       ├── business-docs/
│       ├── erp-policy/
│       └── terminology-graph/
├── shared/
│   └── source-agent-contracts/         # 语言无关的共享合同
└── docs/                               # 产品、架构、设计与交接文档
```

| 区域 | 所有者与职责 | 明确边界 |
| --- | --- | --- |
| 仓库根目录前端 | 浏览器应用、静态资源、维护脚本与部署配置 | 不导入 `backend-agents/` 的 Java 源码、Maven 输出或本地工作区 |
| [`backend-agents/`](../backend-agents/README.md) | 从冻结来源生成未发布九章候选的后端 Agents | 每个 `sources/<source>/` 独立拥有解析、证据和生成实现 |
| [`shared/source-agent-contracts/`](../shared/source-agent-contracts/README.md) | 九章、候选身份、验证、轮次和显式选择的语言无关概念 | 不放 Java 类型、前端运行时代码或来源专用实现 |
| `docs/` | 当前设计、架构、交接与操作说明 | 文档不是运行时配置，也不绕过生成与发布门禁 |

## 命令入口

前端命令仍从仓库根目录运行：

```bash
npm run dev
npm run lint
npm run build
```

GitHub Code Agent 可以从仓库根目录运行：

```bash
mvn -q -f backend-agents/sources/github-code/pom.xml test
```

也可以进入模块目录后运行：

```bash
cd backend-agents/sources/github-code
mvn -q test
```

上述 Maven 只运行 Agent 自身使用合成 fixture 或 recorded provider 的测试；不得执行客户仓库的 Maven、插件、测试、脚本或应用。

## 依赖方向

```text
前端文档 ───────────────┐
                        ├──> shared/source-agent-contracts/
后端 Agent 文档 ────────┘

前端运行时 ─────────────> 前端实现
后端 Agent 实现 ────────> 所属 source 目录内的实现
未来前端接入 ───────────> 另行批准、不可变且通过验证的 Candidate/Receipt 数据
```

共享合同是小接口，不是共享实现目录。任何未来 Candidate 或 Receipt 接入都必须有明确的不可变身份、验证门禁与单独批准，不能直接读取当前 `.workspace/` 输出。

## 不进入 Git 的本地状态

以下内容只属于本地输入、运行状态或构建产物：

- `backend-agents/**/.workspace/`，包括冻结源码、任务包、模型记录、Candidate、Receipt、Trace 和生成 Markdown；
- `backend-agents/**/target/`、JAR、class、Surefire 与其他报告；
- 密钥、Token、`.env`、本地 Codex 状态和编辑器文件；
- `node_modules/`、`dist/`、Playwright 及其他前端测试产物；
- 旧路径下保留的 `.github/modernize/java-upgrade/` 工具本地状态。

目录移动不会授权来源访问、模型调用、候选生成、冻结、打包、合并或部署。
