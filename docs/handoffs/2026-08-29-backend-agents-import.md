# 前端通知：仓库已加入后端 Source Agents

## 你需要知道的结果

仓库现在同时包含三块明确分工：

- 前端仍在仓库根目录，原有目录、命令、构建和部署路径没有移动；
- 后端 Source Agent 工作区位于 [`backend-agents/`](../../backend-agents/README.md)；
- 九章、候选、验证和选择的语言无关合同位于 [`shared/source-agent-contracts/`](../../shared/source-agent-contracts/README.md)。

完整所有权说明见[仓库结构](../repository-structure.md)，迁移合同见[英文结构设计](../design/repository-frontend-backend-structure.md)和[中文结构设计](../design/repository-frontend-backend-structure.zh-CN.md)。

## 旧路径与新路径

| 原本地路径 | 当前仓库路径 |
| --- | --- |
| `source-to-standard-markdown/AGENTS.md` | `backend-agents/AGENTS.md` |
| `source-to-standard-markdown/CONTEXT.md` | `backend-agents/CONTEXT.md` |
| `source-to-standard-markdown/README.md` | `backend-agents/README.md` |
| `source-to-standard-markdown/contracts/README.md` | `shared/source-agent-contracts/README.md` |
| `source-to-standard-markdown/sources/mysql/` | `backend-agents/sources/mysql/` |
| `source-to-standard-markdown/sources/github-code/` | `backend-agents/sources/github-code/` |
| `source-to-standard-markdown/sources/business-docs/` | `backend-agents/sources/business-docs/` |
| `source-to-standard-markdown/sources/erp-policy/` | `backend-agents/sources/erp-policy/` |
| `source-to-standard-markdown/sources/terminology-graph/` | `backend-agents/sources/terminology-graph/` |

设计检查点提前跟踪的两份 progress 文件随 GitHub Code Agent 一起迁移；Git 可能把这两项显示为 rename，也可能显示成等价的 delete/add。其余后端文件此前没有 Git 历史，在迁移提交中表现为新文件。

## 前端没有变化

以下前端表面均未移动或修改：

- `src/`、`public/`；
- `package.json`、`package-lock.json`；
- `scripts/`、`deploy/`；
- 前端测试、Vite 运行时、`dist/` 与服务器部署路径。

前端仍在仓库根目录运行：

```bash
npm run dev
npm run lint
npm run build
```

本次没有改变 npm 依赖或 lockfile，拉取分支后不需要额外运行 `npm install` 或 `npm ci`。

前端代码不得导入 `backend-agents/` 中的 Java 实现、Maven 产物或 `.workspace/`。未来若消费九章 Candidate，应通过另行批准、已验证且具有不可变身份的数据合同接入，而不是直接读取后端本地输出。

## 后端命令位置

从仓库根目录运行 GitHub Code Agent 测试：

```bash
mvn -q -f backend-agents/sources/github-code/pom.xml test
```

或从模块目录运行：

```bash
cd backend-agents/sources/github-code
mvn -q test
```

这些命令只运行 Agent 自身的合成 fixture/recorded-provider 测试，不执行客户代码库。

## 本次提交明确排除

`.workspace/`、冻结客户源码、模型输入输出、Candidate、Receipt、Trace、生成 Markdown、Maven `target/`、JAR、class、测试报告、`.DS_Store`、密钥、本地 Codex 状态、`node_modules/`、`dist/` 和前端测试产物均不进入提交。旧目录下的 `.github/modernize/java-upgrade/` 仍是本地工具状态，也不属于后端源码。

## 前端同学拉取后的检查

```bash
git fetch origin
git show --stat --oneline origin/codex/backend-agents-import
git diff --name-only origin/codex/backend-agents-import^ \
  origin/codex/backend-agents-import -- \
  src public package.json package-lock.json scripts deploy tests
rg -n 'backend-agents|source-agent-contracts' src public scripts package.json
```

最后两项应无输出：迁移不应修改前端路径，也不应建立运行时导入。文档区域出现指向共享合同的链接是预期行为。

## 分支和集成状态

- 分支：`codex/backend-agents-import`
- 远端分支：`origin/codex/backend-agents-import`
- 迁移提交主题：`chore: import backend agents workspace`
- 精确迁移提交是远端分支中包含本文件的那一个，可运行：

```bash
git log -1 --format=%H origin/codex/backend-agents-import -- \
  docs/handoffs/2026-08-29-backend-agents-import.md
```

该分支会被推送，但本任务不创建 PR、不合并 `main`、不部署，也不删除分支。
