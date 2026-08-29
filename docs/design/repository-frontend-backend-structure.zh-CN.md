# 仓库前端与后端 Agent 结构

本文与[英文版架构文档](./repository-frontend-backend-structure.md)是同一份获批架构的两种并行表述；两者承载相同的事实、边界与实施契约，并非两套不同方案。

- 状态：目标架构已获批准；本次设计工作尚未实施该架构
- 决策日期：2026-08-29
- 实施分支：`codex/backend-agents-import`
- 计划推送的远端分支：`origin/codex/backend-agents-import`
- 生命周期前置条件：开始实施迁移前，必须将本规范与两个由各自任务负责、仍位于旧路径的进度记录一并提交

## 1. 目标与当前事实

本次调整为仓库建立清晰的前端、后端 Agent 与共享契约结构，但不移动、也不改动现有前端应用。仓库根目录继续承担前端应用与部署边界的职责；Java/Maven 来源 Agent（Source Agent）工作区迁为由其独立拥有的后端模块 `backend-agents/`；语言无关的来源 Agent 契约则成为唯一共享接缝，固定在 `shared/source-agent-contracts/README.md`。

实施前的当前事实如下：

- 前端应用已经位于仓库根目录：实现代码在 `src/`，静态资源在 `public/`，前端维护脚本在 `scripts/`，部署文件在 `deploy/`；npm 命令由根目录的 `package.json` 与 `package-lock.json` 定义。
- 当前前端命令为 `npm run dev`、`npm run lint` 和 `npm run build`。生产构建仍输出到 `dist/`，独立部署配置仍位于 `deploy/`。
- 后端来源 Agent 工作区当前仅在本地以 `source-to-standard-markdown/` 存在，其中包括来源 Agent 的共享说明与上下文、五个分别归属其来源的目录、一个 Java 17 Maven 模块、测试、设计文档、进度文件以及占位 README。
- 在规定的设计规范检查点之前，整个 `source-to-standard-markdown/` 最初均未被 Git 跟踪。该检查点只提交三个属于本任务的文件：本规范；由 Sol 架构作者拥有且已经为 `COMPLETE` 的 `source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md`；以及由根集成/实施 Agent 拥有、状态为 `IN_PROGRESS` 的 `source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md`。
- 因而，后续实施迁移开始时，只有上述两个旧路径进度文件已受跟踪，其余后端工作区文件仍未受跟踪。两个进度记录都迁到 `backend-agents/sources/github-code/progress/`；Git 既可能把任一迁移显示为重命名，也可能显示为等价的旧路径删除加新路径新增。其他所有未被忽略的后端工作区文件，都会在实施提交中以 `backend-agents/` 或 `shared/source-agent-contracts/` 下的新增文件进入版本库，不存在需要保留的旧路径 Git 历史。
- 当前 GitHub Code Agent 自带本地 `.gitignore`，会排除 `.workspace/` 和 `target/`。其被忽略的本地状态包括已捕获的 jSHERP 快照、生成的任务包、候选内容、回执、Markdown 输出、Maven 类文件、JAR 与测试报告。
- `source-to-standard-markdown/.github/modernize/java-upgrade/.gitignore` 及其被忽略的钩子文件属于工具本地的现代化改造状态，不属于应提交的后端 Agent 内容，也没有目标路径。
- 设计时所在分支为 `codex/backend-agents-import`。已有两个受跟踪文件包含与本迁移无关的工作区修改：`docs/design/data-standardization-review-experience.md` 与 `docs/handoffs/2026-08-27-data-standardization-review-ia.md`。迁移必须保留这些修改，且不得暂存后一个文件。

这是一项仓库布局导入，不是前端重写、后端行为变更、依赖升级、来源捕获、候选生成、部署或发布操作。

## 2. 目标目录树

目标仓库结构如下：

```text
linguan-prototype-v2/
├── AGENTS.md
├── README.md
├── .gitignore
├── package.json
├── package-lock.json
├── src/                              # 现有前端实现；不变
├── public/                           # 现有前端静态资源；不变
├── scripts/                          # 现有前端维护/构建脚本；不变
├── deploy/                           # 现有前端部署配置；不变
├── tests/                            # 现有前端/浏览器测试；不变
├── backend-agents/
│   ├── AGENTS.md
│   ├── CONTEXT.md
│   ├── README.md
│   └── sources/
│       ├── mysql/
│       │   └── README.md
│       ├── github-code/
│       │   ├── .gitignore
│       │   ├── AGENTS.md
│       │   ├── DESIGN.md
│       │   ├── README.md
│       │   ├── pom.xml
│       │   ├── docs/
│       │   │   └── stages/
│       │   │       └── 00-mvp.md
│       │   ├── progress/
│       │   │   └── repository-structure-zh-cn.md  # 本任务普通新增进度记录
│       │   ├── src/
│       │   │   ├── main/java/
│       │   │   └── test/java/
│       │   ├── .workspace/           # 仅本地存在且被忽略；绝不提交
│       │   └── target/               # 生成目录且被忽略；绝不提交
│       ├── business-docs/
│       │   └── README.md
│       ├── erp-policy/
│       │   └── README.md
│       └── terminology-graph/
│           └── README.md
├── shared/
│   └── source-agent-contracts/
│       └── README.md
└── docs/
    ├── repository-structure.md
    ├── design/
    │   ├── repository-frontend-backend-structure.md
    │   ├── repository-frontend-backend-structure.zh-CN.md
    │   └── data-standardization-review-experience.md
    └── handoffs/
        └── 2026-08-29-backend-agents-import.md
```

其中 `.workspace/` 与 `target/` 的注释描述的是本地文件系统状态，不表示需要提交空目录占位。Git 不保存空目录，也不得接收这两棵目录中的任何文件。

## 3. 所有权与深模块接缝

### 3.1 前端模块

仓库根目录下的前端是一个完整模块。它对外的接口包括已记录的 npm 命令、浏览器路由与行为、生成的 `dist/` 输出以及部署契约；实现继续位于既有的根目录 `src/`、`public/`、`scripts/` 与 `deploy/` 路径。

前端负责人仍从仓库根目录工作。任何前端导入（import）、Vite 别名、TypeScript 项目引用、npm 工作区、package 脚本、部署路径或运行时查找，都不得指向 `backend-agents/`。迁移不得编辑 `src/`、`public/`、`package.json`、`package-lock.json`、`scripts/`、`deploy/`、前端测试文件或前端构建配置。

### 3.2 后端 Agents 模块

`backend-agents/` 是后端来源 Agent 的工作区与所有权根。其作用域内的 `AGENTS.md`、`CONTEXT.md` 和 `README.md` 定义后端 Agent 的共享规则与导航。`backend-agents/sources/{mysql,github-code,business-docs,erp-policy,terminology-graph}/` 下的每个目录仍分别归属相应来源，任何一个来源 Agent 都不得导入另一个来源 Agent 的实现。

GitHub Code Agent 继续采用 Java 17 的单模块 Maven 实现。其深模块接口仍是现有 `CodeToMarkdownAgent` 接缝及其 CLI；Java 实现、来源解析、测试、本地工作区与构建输出都封装在 `backend-agents/sources/github-code/` 内。目录移动不改变 Java 包名、Maven 坐标、行为、成熟度声明或安全规则。

### 3.3 来源 Agent 共享契约接缝

本次重构引入的唯一跨来源、跨边界接缝是 `shared/source-agent-contracts/README.md`。这是一个语言无关的文档接口，包含 `NineSectionProfile`、最小化的候选/校验概念、身份、轮次以及显式 Selection；它不包含 Java 实现，也不容纳任何来源特定的解析、提示词、证据、渲染或存储逻辑。

后端 Agent 文档必须链接到该共享契约。前端架构与所有权文档可以链接它，以说明九节结构与身份词汇；前端运行时代码不得把后端 Java 源码、后端 Maven 输出或该 Markdown 文件当作可执行配置导入。

未来若要跨边界交换内容，只能使用另行批准、显式且不可变、并符合共享契约的候选与回执工件。这些未来工件是跨越接缝的数据，不是源码导入。本次导入不会创建、选择、冻结、打包、发布或激活任何候选或回执。

### 3.4 依赖方向

允许的依赖方向只有：

```text
前端文档 ───────────────────┐
                            ├──> shared/source-agent-contracts/README.md
后端 Agent 文档 ────────────┘

后端 Agent 实现 ─────────────> 仅指向来源自有的 Java/Maven 实现
前端运行时 ──────────────────> 仅指向前端实现
未来前端摄取 ────────────────> 仅接收显式、不可变的候选/回执工件
```

不存在前端到后端源码的导入，也不存在后端到前端运行时的导入。共享 Markdown 契约是刻意设置在边界上的小接口，绝不能演变为堆放来源特定实现细节的杂物间。

## 4. 旧路径到新路径的精确映射

下表所有路径均相对于仓库根目录。

| 当前路径 | 目标路径 | 迁移处理 |
| --- | --- | --- |
| `source-to-standard-markdown/AGENTS.md` | `backend-agents/AGENTS.md` | 迁移后更新其作用域名称与共享接缝引用。 |
| `source-to-standard-markdown/CONTEXT.md` | `backend-agents/CONTEXT.md` | 迁移，不改变其中的领域定义。 |
| `source-to-standard-markdown/README.md` | `backend-agents/README.md` | 迁移，并更新导航、目录树与共享契约链接。 |
| `source-to-standard-markdown/contracts/README.md` | `shared/source-agent-contracts/README.md` | 迁移为语言无关的共享接缝，保留其契约内容。 |
| `source-to-standard-markdown/sources/mysql/` | `backend-agents/sources/mysql/` | 迁移该来源 Agent 的占位 README。 |
| `source-to-standard-markdown/sources/github-code/` | `backend-agents/sources/github-code/` | 迁移完整 Java/Maven Agent，包括源码、测试、设计、README、作用域说明、进度与被忽略的本地状态。除下两行所列的两个检查点已跟踪进度文件外，其所有提交文件均为新增。 |
| `source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md` | `backend-agents/sources/github-code/progress/backend-agents-repository-import.md` | 逐字节迁移。这是 Sol 架构作者的 `COMPLETE` 记录。设计检查点跟踪旧路径，因此实施 diff 可将其显示为 Git 重命名；显示为删除/新增也有效。 |
| `source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md` | `backend-agents/sources/github-code/progress/backend-agents-import-orchestration.md` | 迁移根集成/实施负责人的现有 `IN_PROGRESS` 记录。实施期间仅根负责人更新它。由于设计检查点跟踪旧路径，实施 diff 可将迁移显示为 Git 重命名或删除/新增对。 |
| `source-to-standard-markdown/sources/business-docs/` | `backend-agents/sources/business-docs/` | 迁移该来源 Agent 的占位 README。 |
| `source-to-standard-markdown/sources/erp-policy/` | `backend-agents/sources/erp-policy/` | 迁移该来源 Agent 的占位 README。 |
| `source-to-standard-markdown/sources/terminology-graph/` | `backend-agents/sources/terminology-graph/` | 迁移该来源 Agent 的占位 README。 |
| `source-to-standard-markdown/sources/github-code/.workspace/` | `backend-agents/sources/github-code/.workspace/` | 随来源目录迁移以保持本地连续性；继续被忽略且不暂存。 |
| `source-to-standard-markdown/sources/github-code/target/` | `backend-agents/sources/github-code/target/` | 随来源目录迁移；继续被忽略且不暂存。后续 Maven 测试可在同一目标路径重新生成它。 |
| `source-to-standard-markdown/.github/modernize/java-upgrade/` | 无 | 作为被忽略的工具本地状态留在原处；不复制、不暂存，也不将其列入后端 Agent 源码。 |

迁移以一次文件系统目录移动的方式处理 `source-to-standard-markdown/sources/`，使被忽略的 `.workspace/`、`target/` 与来源级 `.DS_Store` 随目录移动，却不会被复制进 Git 暂存区（index）；两个检查点已跟踪进度文件也随之迁移。架构记录逐字节保持不变，根负责人则随着实施推进维护编排记录。Git 可依据内容相似度把任一迁移识别为重命名。后端工作区的三个文档和契约 README 分别移动，并以新增文件进入 Git。由于被忽略的现代化改造存根（stub）或 `.DS_Store` 仍可能存在，旧顶层目录可以继续留在本地；任何命令都不得删除它或其中内容。

## 5. 应跟踪与应排除的内容

### 5.1 必须跟踪的内容

实施迁移前，设计规范检查点恰好跟踪以下三个属于本任务的文件：

- `docs/design/repository-frontend-backend-structure.md`；
- `source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md`，即 Sol 架构作者的 `COMPLETE` 记录；
- `source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md`，即根集成/实施负责人的 `IN_PROGRESS` 记录。

后续实施提交包括：

- `backend-agents/AGENTS.md`、`backend-agents/CONTEXT.md` 与 `backend-agents/README.md`；
- 每个来源 Agent 的占位 README；
- GitHub Code Agent 的 `.gitignore`、作用域 `AGENTS.md`、`DESIGN.md`、`README.md`、`pom.xml`、`docs/stages/00-mvp.md`、所有现有进度文件、全部 Java 生产源码与全部 Java 测试；
- `backend-agents/sources/github-code/progress/backend-agents-repository-import.md`：本架构规范任务校验后已标记为 `COMPLETE`，从检查点已跟踪的旧路径逐字节迁入；
- `backend-agents/sources/github-code/progress/backend-agents-import-orchestration.md`：从检查点已跟踪的旧路径迁入，并且直至实施完成都只由根集成/实施负责人更新；
- `backend-agents/sources/github-code/progress/repository-structure-zh-cn.md`：本中文并行版任务的普通新增后端进度记录，完成后为 `COMPLETE`；它没有旧路径，也不参与上述两个检查点进度文件的迁移生命周期；
- `shared/source-agent-contracts/README.md`；
- 根目录 `.gitignore`、`AGENTS.md` 与 `README.md` 中的导航和所有权变更；
- `docs/repository-structure.md`；
- `docs/design/repository-frontend-backend-structure.md` 中为登记双语并行版本而做的最小同步修改，以及新增的 `docs/design/repository-frontend-backend-structure.zh-CN.md`；
- `docs/handoffs/2026-08-29-backend-agents-import.md`。

除第 6 节列出的特定文档路径/链接修正外，Java 源码、测试、Maven 配置、稳定架构、阶段设计、能力 README、已完成与历史进度记录，以及各来源 Agent 的占位 README，都必须逐字节不变。在预先存在的进度文件中，根负责人拥有的编排进度记录是内容变更的唯一例外：其负责人依据作用域进度契约更新当前实施状态。文件移动并不构成改写历史进度陈述的理由。本中文文档任务新增的是普通工作单元进度记录，不是第三个迁移或编排记录。

设计规范检查点不跟踪除上述两个旧路径进度记录之外的任何后端工作区文件。不得创建第三个实施生命周期或编排进度文件：`backend-agents-import-orchestration.md` 就是根负责人的实施进度记录；`repository-structure-zh-cn.md` 是普通新增的文档工作进度记录。其他所有已批准的后端工作区文件均在目标路径以新增形式出现。

### 5.2 必须保持未跟踪的内容

实施提交必须排除：

- 所有 `.workspace/` 目录；
- 已捕获的 jSHERP 仓库快照以及任何其他已捕获来源快照；
- 本地工作区内的任务包、已记录模型输入、模型响应、生成候选、生成 Markdown、回执、跟踪索引（trace index）、证据包（evidence pack）、选择结果（selection），以及其他模型/运行时输出；
- 所有 Maven `target/` 目录；
- JAR、class 文件、生成源码、Maven 元数据、Surefire 报告，以及其他构建/测试报告；
- `.DS_Store` 与编辑器本地文件；
- 机密信息、凭据、令牌、本地环境文件与 API 密钥；
- 仓库本地 Codex 状态以及其他本地 Agent/会话状态；
- `node_modules/`、`dist/`、`dist-ssr/`、前端测试结果、Playwright 报告、blob 报告与日志文件；
- `source-to-standard-markdown/.github/modernize/java-upgrade/` 及其钩子。

此次导入期间，本地候选与回执输出必须留在 `.workspace/` 之下。不能通过放宽忽略规则或强制添加当前输出来实现未来的不可变候选/回执接缝；该接缝需要另行批准的工件路径、身份契约和校验门禁。

### 5.3 忽略规则契约

根 `.gitignore` 保留现有前端排除项，并为后端/本地状态增加全仓库保护。实施必须加入与下列规则等价的内容：

```gitignore
# Backend Agent local inputs and generated outputs
/backend-agents/**/.workspace/
/backend-agents/**/target/
/backend-agents/**/*.jar
/backend-agents/**/*.class
/backend-agents/**/reports/
/backend-agents/**/surefire-reports/

# Local agent state and secrets
/.codex/
.env
.env.*
!.env.example
!*.env.example
!.env.*.example

# Tool-local legacy modernization state
/source-to-standard-markdown/.github/modernize/java-upgrade/
```

迁移后的 `backend-agents/sources/github-code/.gitignore` 继续保留原有 `.workspace/` 与 `target/` 规则。这种重复保护是有意为之：作用域文件保障模块独立使用时的安全，根文件则保护未来的后端 Agent 目录与此次仓库导入。

## 6. 文档与链接更新

### 6.1 根目录导航与所有权

实施将更新以下描述当前事实的文档：

- `README.md` 在开篇说明之后紧接一个简洁的仓库导航章节：明确仓库根目录是前端应用，链接 `./backend-agents/README.md` 与 `./shared/source-agent-contracts/README.md`，并说明前端命令和后端命令各自的工作目录。
- `AGENTS.md` 增加对 `backend-agents/`、其各来源自有子目录以及 `shared/source-agent-contracts/` 的作用域路由；它继续把前端工作路由到现有前端说明，不会把后端 Agent 工作误归为前端工作。
- `docs/repository-structure.md` 成为持久的仓库所有权地图，记录目标目录树、命令根目录、所有权、依赖方向、忽略范围以及实施细则所在位置。从该文件指向后端导航、共享契约、英文设计与中文设计的链接依次为 `../backend-agents/README.md`、`../shared/source-agent-contracts/README.md`、`./design/repository-frontend-backend-structure.md` 和 `./design/repository-frontend-backend-structure.zh-CN.md`。
- `docs/handoffs/2026-08-29-backend-agents-import.md` 是第 11 节规定的前端通知。从该文件指向后端导航、共享契约、仓库结构、英文设计和中文设计的链接依次为 `../../backend-agents/README.md`、`../../shared/source-agent-contracts/README.md`、`../repository-structure.md`、`../design/repository-frontend-backend-structure.md` 和 `../design/repository-frontend-backend-structure.zh-CN.md`。

英文设计 `docs/design/repository-frontend-backend-structure.md` 与本中文设计 `docs/design/repository-frontend-backend-structure.zh-CN.md` 必须在各自顶部互相链接，并始终表述同一套架构、迁移行为与验收契约。

### 6.2 后端文档链接

下列链接变更是精确要求：

| 目标文档 | 必需的目标链接或文本 |
| --- | --- |
| `backend-agents/README.md` | 共享契约链接改为 `../shared/source-agent-contracts/README.md`；工作区目录树使用 `backend-agents/` 与 `shared/source-agent-contracts/`。 |
| `backend-agents/AGENTS.md` | 作用域文本命名 `backend-agents/`；唯一共享接缝为 `shared/source-agent-contracts/`。 |
| `backend-agents/sources/github-code/README.md` | `NineSectionProfile` 链接改为 `../../../shared/source-agent-contracts/README.md`。 |
| `backend-agents/sources/github-code/DESIGN.md` | `NineSectionProfile` 链接改为 `../../../shared/source-agent-contracts/README.md`。下游评审链接仍为 `../../../docs/design/data-standardization-review-experience.md`，因为目录深度不变。 |
| `backend-agents/sources/github-code/docs/stages/00-mvp.md` | `NineSectionProfile` 链接改为 `../../../../../shared/source-agent-contracts/README.md`；指向 `../../DESIGN.md`、`../../src/` 与 `../../.workspace/` 的链接保持原有相对形式。 |
| 既有进度文件 | 移动时不改写历史路径陈述。Sol 架构作者的记录为 `COMPLETE` 且保持不变。根编排记录在检查点时为 `IN_PROGRESS`，它就是实施进度记录，仅由根负责人更新；不得创建第三个实施生命周期或编排进度文件。 |
| `backend-agents/sources/github-code/progress/repository-structure-zh-cn.md` | 作为本并行中文文档任务新增的普通后端进度记录进入实施清单；任务结束时标记 `COMPLETE`。它没有旧路径，也不参与两个检查点已跟踪进度文件的迁移生命周期。 |

### 6.3 现有脏文件

`docs/design/data-standardization-review-experience.md` 当前只在预先存在的未暂存新增内容中包含旧路径链接与工作区目录树。迁移负责人必须只做以下工作副本修正：

- 将 `../../source-to-standard-markdown/sources/github-code/DESIGN.md` 改为 `../../backend-agents/sources/github-code/DESIGN.md`；
- 将 `../../source-to-standard-markdown/sources/github-code/docs/stages/00-mvp.md` 改为 `../../backend-agents/sources/github-code/docs/stages/00-mvp.md`；
- 将工作区名称改为 `backend-agents/`；
- 将共享接缝改为 `shared/source-agent-contracts/`；
- 将所有权句子中的 `sources/github-code/` 改为 `backend-agents/sources/github-code/`；
- 将展示的目录树改为第 2 节所示目标 `backend-agents/` 加 `shared/source-agent-contracts/` 目录树。

由于这些行在已提交的 `HEAD` 版本中并不存在，该文件必须整体保持未暂存。这样既保留原负责人的工作，也避免迁移提交吸收无关设计变更。因此，远端迁移提交不会从该文件的已提交版本带入陈旧链接；修正后的链接文本仍留在原负责人的本地未暂存修改中，等待其后续归属提交。

`docs/handoffs/2026-08-27-data-standardization-review-ia.md` 必须逐字节不变且保持未暂存。`docs/handoffs/2026-08-29-v7-publication-and-deployment.md` 中关于某次历史提交未触及 `source-to-standard-markdown/` 的句子也保持不变，因为它记录的是过去事件，而不是当前导航。

## 7. 对脏工作区安全的迁移算法

只有在单独的设计规范检查点已经提交英文规范与两个旧工作区进度记录后，才按以下顺序实施。整个过程不使用 `git stash`、`git reset`、`git checkout --`、宽泛清理命令或 `git add .`。根集成/实施负责人继续维护既有的 `source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md`，让它随 `sources/` 一并迁移，并保持唯一更新权；已完成的架构作者记录不得改动，也不得创建第三个实施生命周期或编排进度文件。新增的 `repository-structure-zh-cn.md` 只是本双语文档工作单元的普通后端进度记录，不替代也不改变这两个检查点记录的生命周期。

### 7.1 预检与保全

1. 运行 `git status --short --branch`，并要求当前分支为 `codex/backend-agents-import`。
2. 记录 `git rev-parse HEAD`、`git diff --name-only`、`git diff --check`、`git ls-files source-to-standard-markdown` 和 `git ls-files --others --exclude-standard source-to-standard-markdown`。受跟踪清单必须恰好包含 `source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md` 与 `source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md`；其他已批准且未被忽略的后端文件仍应出现在未跟踪清单中。
3. 以 `mktemp -d /private/tmp/backend-agents-import-20260829.XXXXXX` 创建唯一保全目录，记录返回路径，并在其中保存两个预先存在脏文件的只读补丁（patch）副本。这些副本只作恢复证据，绝不暂存。
4. 验证 `backend-agents/` 与 `shared/source-agent-contracts/` 尚不存在；若任一已存在，必须在移动任何内容前停止，禁止覆盖或合并未知目标。
5. 核对预期来源 Agent 文件、`.workspace/`、`target/` 与现代化改造存根（stub）是否符合第 1 节和第 5 节的清单。发现新的未知且未被忽略文件时，必须先评审再继续导入。

### 7.2 迁移但不删除本地状态

1. 只创建空的目标父目录 `backend-agents/` 与 `shared/source-agent-contracts/`。
2. 将 `source-to-standard-markdown/AGENTS.md`、`CONTEXT.md` 和 `README.md` 迁到 `backend-agents/`。
3. 将完整的 `source-to-standard-markdown/sources/` 目录迁到 `backend-agents/sources/`。这样源码、测试、文档、进度、占位文件、`.workspace/`、`target/` 与作用域 ignore 规则会保持在一起，两个检查点已跟踪进度文件也包括在内。
4. 将 `source-to-standard-markdown/contracts/README.md` 迁到 `shared/source-agent-contracts/README.md`。
5. 将 `source-to-standard-markdown/.github/modernize/java-upgrade/`、旧顶层可能存在的 `.DS_Store` 和空本地父目录留在原处，不得删除。
6. 若迁移前存在 `.workspace/` 与 `target/`，则确认迁移后它们仍在本地、仍被忽略，并且没有出现在 `git ls-files --others --exclude-standard backend-agents` 中。

### 7.3 只应用已批准的文本变更

1. 应用第 6 节规定的精确后端文档链接与作用域修正。
2. 按规范更新根 `.gitignore`、`AGENTS.md` 与 `README.md`。
3. 创建 `docs/repository-structure.md` 与前端交接文档（handoff）。
4. 保持英文、中文两份设计为同一架构的并行表述；仅应用第 6 节、第 7.4 节、第 8 节、第 11 节与第 13 节规定的登记、导航、清单、校验、通知导航与验收同步，不得改变迁移行为。
5. 在脏的数据标准化设计中，只修正第 6.3 节明确列出的五类旧路径形式，并保持该文件未暂存。
6. 不得编辑脏的 2026-08-27 交接文档（handoff）。
7. 每个可验证阶段之后，只允许根集成/实施负责人更新 `backend-agents/sources/github-code/progress/backend-agents-import-orchestration.md`。初次完成第 8 节校验前保持 `IN_PROGRESS`；第 8 节所有检查通过后改为 `COMPLETE`，只重新暂存该精确目标路径，再次执行第 8.1 节 Git 与清单检查，然后进入第 9 节。不可变提交身份写入 handoff 与带外通知，而不是通过提交后追加进度修改来记录。不得编辑已完成的架构作者进度文件。

### 7.4 按显式允许清单暂存

只能暂存以下路径：

```text
.gitignore
AGENTS.md
README.md
backend-agents/AGENTS.md
backend-agents/CONTEXT.md
backend-agents/README.md
backend-agents/sources/mysql/README.md
backend-agents/sources/business-docs/README.md
backend-agents/sources/erp-policy/README.md
backend-agents/sources/terminology-graph/README.md
backend-agents/sources/github-code/.gitignore
backend-agents/sources/github-code/AGENTS.md
backend-agents/sources/github-code/DESIGN.md
backend-agents/sources/github-code/README.md
backend-agents/sources/github-code/pom.xml
backend-agents/sources/github-code/docs/
backend-agents/sources/github-code/progress/
backend-agents/sources/github-code/src/
source-to-standard-markdown/sources/github-code/progress/backend-agents-repository-import.md
source-to-standard-markdown/sources/github-code/progress/backend-agents-import-orchestration.md
shared/source-agent-contracts/README.md
docs/repository-structure.md
docs/design/repository-frontend-backend-structure.md
docs/design/repository-frontend-backend-structure.zh-CN.md
docs/handoffs/2026-08-29-backend-agents-import.md
```

清单中的目录路径规格（pathspec）均限定在已批准的所有权根内。两个精确的旧进度文件路径只为把受跟踪的删除侧与新路径配对而存在；只能暂存这两个旧路径，不能暂存更宽泛的旧工作区路径规格。`backend-agents/sources/github-code/progress/` 包括作为普通新增后端进度记录的 `repository-structure-zh-cn.md`，但该文件不属于两个旧路径重命名例外。提交前，必须逐一将所有已暂存文件名与本清单比较。尤其不得让暂存区（index）包含 `docs/design/data-standardization-review-experience.md`、`docs/handoffs/2026-08-27-data-standardization-review-ia.md`、除两个进度记录删除侧之外的任何 `source-to-standard-markdown/` 路径、`.workspace/`、`target/`、`.github/modernize/`、`.DS_Store`、JAR、class 文件或生成报告。

## 8. 校验

所有校验均在本地串行执行。任何校验命令都不得调用模型提供方、来源扫描器、客户方 Maven 项目、浏览器部署或外部来源。

### 8.1 Git 与清单检查

运行并检查：

```bash
git status --short --branch
git diff --check
git diff --cached --check
git diff --cached --find-renames --name-status
git diff --cached --stat
git ls-files backend-agents shared/source-agent-contracts
git ls-files source-to-standard-markdown
git status --short --ignored --untracked-files=all -- backend-agents source-to-standard-markdown
```

验收要求：

- 分支是 `codex/backend-agents-import`；
- 已暂存路径按所有权作用域精确匹配第 7.4 节；
- 两个预先存在的脏文档仍未暂存；
- Java 生产/测试文件、稳定设计、阶段设计、进度文件或占位 README 均无缺失；
- 本地被忽略工件仍保存在原处或已随迁移保全，且无一被暂存；
- 架构作者进度记录仍为 `COMPLETE`，根负责人编排记录在最终暂存状态中为 `COMPLETE` 且全程只由根负责人更新；新增的 `repository-structure-zh-cn.md` 作为普通后端进度记录为 `COMPLETE`，不改变前两者的迁移生命周期；
- `git diff --cached --find-renames --name-status` 将两个进度记录迁移分别显示为旧路径到新路径的重命名，或显示为等价的旧路径删除加新路径新增；
- 其余每个应提交后端工作区文件都以 `backend-agents/` 或 `shared/source-agent-contracts/` 下的新增出现，不得存在其他旧路径重命名或删除；
- 暂存后 `git ls-files source-to-standard-markdown` 为空，因为两个受跟踪旧路径进度记录都已迁移；
- diff 中不出现任何前端实现、package、锁文件（lockfile）、脚本、部署或前端测试路径；
- 英文与中文设计文件均在允许清单中，顶部互链可解析，并且在所有实质架构事实、路径、所有权边界、迁移/校验/回滚契约、两个检查点进度文件生命周期、生成式内容清单与验收条件上保持一致。

使用以下已暂存文件名排除检查；它必须没有输出：

```bash
git diff --cached --name-only | rg '(^|/)(\.workspace|target|reports|surefire-reports)(/|$)|\.(jar|class)$|(^|/)\.DS_Store$|(^|/)\.github/modernize/'
```

### 8.2 链接检查

确认以下每个新活动目标都存在：

```text
backend-agents/README.md
backend-agents/AGENTS.md
backend-agents/sources/github-code/README.md
backend-agents/sources/github-code/DESIGN.md
backend-agents/sources/github-code/docs/stages/00-mvp.md
shared/source-agent-contracts/README.md
docs/repository-structure.md
docs/design/repository-frontend-backend-structure.md
docs/design/repository-frontend-backend-structure.zh-CN.md
docs/handoffs/2026-08-29-backend-agents-import.md
```

从各自所在目录解析三个已移动 GitHub Code Agent 文档、根导航文档及中英设计互链中的相对 Markdown 链接。随后在活动导航集合中搜索陈旧路径：

```bash
rg -n 'source-to-standard-markdown|(^|[^[:alnum:]])contracts/README\.md' \
  README.md AGENTS.md docs/repository-structure.md \
  docs/design/data-standardization-review-experience.md \
  backend-agents/README.md backend-agents/AGENTS.md \
  backend-agents/sources/github-code/README.md \
  backend-agents/sources/github-code/DESIGN.md \
  backend-agents/sources/github-code/docs/stages/00-mvp.md
```

该搜索不得返回任何活动旧路径引用。中英文两份架构规范，以及历史进度/交接（handoff）记录，因其中旧路径文本用于说明迁移或历史事件而有意不纳入此搜索。

### 8.3 后端 Maven 校验

目录移动改变了 Maven 模块的工作路径，因此既要执行定向冒烟测试（smoke），也要执行完整 GitHub Code Agent 模块测试。这是对完整 `github-code` Maven 模块的明确授权，不是对整个仓库测试套件的授权。必须从仓库根目录串行运行：

```bash
mvn -q -f backend-agents/sources/github-code/pom.xml \
  -Dtest=CodeMdCliDiscoveryTest,CodeMdCliValidateTest,CandidateArchivePersistenceTest test

mvn -q -f backend-agents/sources/github-code/pom.xml test
```

这些测试只使用合成夹具（fixture）或录制提供方（recorded provider）；不得执行已捕获的 jSHERP 项目、访问网络来源或调用模型。如果 Maven 需要从网络取得尚未缓存的依赖，应停止并报告依赖解析限制，而不是扩大授权。Maven 输出仍被忽略在 `backend-agents/sources/github-code/target/` 下。

### 8.4 前端校验阈值

本计划明确不改动前端实现，也不改动构建、运行时或部署配置。验证以下命令没有输出：

```bash
git diff --cached --name-only -- src public package.json package-lock.json scripts deploy tests
```

命令无输出时，不为本迁移运行 npm install、`npm ci`、前端测试、`npm run lint` 或 `npm run build`。没有 package 或锁文件（lockfile）变更，也不预期重新安装 npm 依赖。若经授权的实施意外需要修改上述任一前端路径，必须停止本迁移并取得修订后的设计；前端校验应归入另行批准的变更。

## 9. 原子提交与推送顺序

第 8 节全部检查通过后，在已经完成的设计规范检查点之外，创建一个原子实施提交：

1. 再次运行 `git status --short --branch`，并完整检查 `git diff --cached`。
2. 确认两个预先存在的脏文档只以未暂存修改存在，且预检保存的 patch 仍可恢复。
3. 创建一个原子提交，提交主题（subject）为 `chore: import backend agents workspace`。
4. 以 `git rev-parse HEAD` 记录生成的完整 SHA。
5. 使用 `git push -u origin codex/backend-agents-import` 推送。
6. 验证本地分支跟踪 `origin/codex/backend-agents-import`，且已推送的分支顶端提交（tip）等于记录的 SHA。
7. 向前端发送包含精确分支和已推送 SHA 的通知。

实施提交包含两个进度文件迁移、根负责人编排记录获批的状态更新、其余后端新增、获批仓库文档更新、中英文设计同步登记，以及本中文文档任务的普通新增进度记录。提交推送到 `origin/codex/backend-agents-import`，而不是 `main`。本任务不创建拉取请求（pull request），不执行 merge、rebase、force-push、部署或删除分支；任何 PR 或 merge 都需要另行请求。

受跟踪交接文档（handoff）将迁移提交标识为 `origin/codex/backend-agents-import` 上“包含本 handoff 的单一提交”。Git 提交无法包含自身 SHA，因此精确 SHA 在推送后加入带外前端通知，并可用以下命令确定性恢复：

```bash
git log -1 --format=%H origin/codex/backend-agents-import -- \
  docs/handoffs/2026-08-29-backend-agents-import.md
```

这样既能为前端负责人提供精确、不可变的身份，又无需创建第二个纯文档提交或修改提交（amend）。

## 10. 回滚

### 10.1 提交之前

不得 reset 或删除。反向执行显式文件系统迁移，让 `.workspace/` 与 `target/` 随 `sources/` 一起移回；只撤销属于本任务的文本变更。如果放弃导入，应把新写的仓库文档保存在第 7.1 节创建的唯一预检目录中。将两个脏文件与已保存补丁（patch）比较，在不丢弃其内容的前提下恢复原有未暂存状态。

### 10.2 提交之后、推送之前

先把已提交的后端源码和文档复制到安全的本地保全目录，再将被忽略的本地状态迁回旧工作区，然后使用第 9 节第 4 步记录的完整迁移 SHA 创建普通 `git revert` 提交。不得使用 reset、amend 或任何重写历史的方式。确认两个预先存在的脏文档仍符合预期未暂存状态。

### 10.3 推送之后

先保全本地被忽略输入，再在 `codex/backend-agents-import` 上执行 revert 撤销迁移提交，并以普通方式推送该 revert。不得 force-push，也不得改动 `main`。revert 会把两个进度文件恢复到检查点已跟踪的旧路径——架构记录恢复为 `COMPLETE`，编排记录恢复为检查点时的 `IN_PROGRESS`——并移除实施提交新增的后端/共享路径；已保全的本地后端输入仍可供修正后的导入使用。

由于前端运行时、构建与部署路径从未改变，回滚这次仓库布局提交不需要重新安装 npm 依赖，也不需要前端部署。

## 11. 前端通知契约

`docs/handoffs/2026-08-29-backend-agents-import.md` 面向前端负责人，必须将以下内容作为当前事实完整写明，并链接到英文、中文两份并行架构文档：

1. **结果。** 仓库现在包含位于 `backend-agents/` 的后端来源 Agent 工作区，以及位于 `shared/source-agent-contracts/` 的语言无关契约；前端仍位于仓库根目录。
2. **旧新路径映射。** 纳入第 4 节映射表所列的工作区文档、五个来源目录、GitHub Code Agent、两个检查点已跟踪进度文件的重命名或删除/新增例外，以及共享契约。
3. **前端范围不变。** 明确 `src/`、`public/`、`package.json`、`package-lock.json`、`scripts/`、`deploy/`、前端测试、Vite 运行时、`dist/` 与服务端部署路径均未移动、未改变。
4. **命令不变。** 前端负责人仍从仓库根目录运行 `npm run dev`、`npm run lint` 和 `npm run build`。
5. **无需重装。** npm 依赖与锁文件（lockfile）均未变化，因此拉取后不预期运行 `npm install` 或 `npm ci`。
6. **共享接缝。** 前端文档应指向 `shared/source-agent-contracts/README.md`；前端运行时不得导入 `backend-agents/` 下的 Java 实现或 Maven 输出。
7. **拉取后检查。** 请前端负责人检查提交，确认前端路径 diff 为空，解析根导航链接，并搜索 `src/`、`public/`、`scripts/` 与 package 配置中是否意外出现 `backend-agents` 导入。由于前端范围未变，前端构建是可选检查，不是迁移验收要求。
8. **后端命令位置。** Maven 可从仓库根目录以 `mvn -q -f backend-agents/sources/github-code/pom.xml test` 运行，也可在模块目录中以 `mvn -q test` 运行。
9. **排除的本地工件。** 明确 `.workspace/`、已捕获来源快照、模型输出/候选/回执、`target/`、JAR/class/report、`.DS_Store`、机密信息、本地 Codex 状态、`node_modules/`、`dist/` 与测试工件均不在提交中。
10. **身份。** 写明分支 `codex/backend-agents-import`、远端分支 `origin/codex/backend-agents-import`、提交主题（commit subject）`chore: import backend agents workspace`，以及取得精确提交 SHA 的规则；推送后的带外通知显式提供该 SHA。
11. **集成范围。** 写明分支已推送，但没有创建 PR，没有 merge 到 `main`，也没有部署。

拉取后的前端检查命令为：

```bash
git fetch origin
git show --stat --oneline origin/codex/backend-agents-import
git diff --name-only origin/codex/backend-agents-import^ \
  origin/codex/backend-agents-import -- \
  src public package.json package-lock.json scripts deploy tests
rg -n 'backend-agents|source-agent-contracts' src public scripts package.json
```

其中 diff 与运行时导入搜索都必须为空；这些运行时路径之外的文档链接到共享契约是预期行为。

## 12. 生成式内容清单

无。实施只执行确定性的文件系统迁移、忽略规则更新、导航编辑、本地链接检查，以及使用脚本化提供方（scripted provider）或录制提供方（recorded provider）的 Maven 测试。它不调用 LLM、图像生成器、OCR 系统、来源扫描器、实时模型提供方、候选生成工作流，也不执行来源捕获、冻结、打包、发布或部署流程。

## 13. 验收摘要

仅当以下陈述全部成立时，重构才算完成：

- 前端仍位于仓库根目录，运行时、构建、命令、package、测试与部署路径均未改变；
- 完整且已批准的后端 Agent 源码与文档范围已在 `backend-agents/` 下受跟踪；
- 语言无关契约只在 `shared/source-agent-contracts/README.md` 受跟踪；
- 两个检查点已跟踪进度文件是仅有的旧路径重命名（或删除/新增对）候选，其他所有后端工作区文件均显示为目标路径新增；本任务的 `repository-structure-zh-cn.md` 是普通新增后端进度记录，最终状态为 `COMPLETE`，不改变前述两文件生命周期；
- 本地工件、生成工件、后端构建工件与现代化改造存根（stub）均未进入暂存区（index）；
- 所有活动导航链接都指向新路径；
- `docs/design/repository-frontend-backend-structure.md` 与 `docs/design/repository-frontend-backend-structure.zh-CN.md` 顶部互链可解析，并且是同一架构在两种语言中的实质一致表述；
- 新路径下有针对性的 Maven 模块测试与完整 Maven 模块测试均通过；
- 两个预先存在的脏文档按规范得到保全，并排除在迁移提交之外；
- 在单独的设计规范检查点之后，一个原子实施提交已推送到 `origin/codex/backend-agents-import`；
- 前端交接文档（handoff）给出精确的推送后身份与“前端不变”指引，并提供两种语言的架构入口；
- 全程未发生 PR、merge、部署、来源访问、模型调用、生成或删除。
