# 管伊佳 Demo 内容快照维护手册

## 用途与边界

当前最近一次已冻结且已接入新运行的富内容 sidecar 是
`guanyijia-demo-content-v7-20260827`，保存于：

```text
../modeling-evidence/guanyijia/demo-content/snapshots/
guanyijia-demo-content-v7-20260827/
```

它让 Demo 展示较丰富的业务说明、制度和术语图，并能以固定 commit、文件和行号定位 GitHub 源码。早期版本 `V5`、`V6` 都原样保留，供比较和回滚；`DemoContentReview`
在一次资料整理运行开始时将所选版本绑定到精确的 `runId` 和五项正式快照身份；刷新与继续审阅都会读取同一个绑定。
它不改变现有 Pinned Bundle、管伊佳正式 V1、Catalog、黄金 SHA、正式 Claim 或正式冲突定义，也不会替代正式证据
工厂的准入与治理链。

该快照不是正式 Evidence Bundle：不生成 Claim、Assertion、Conflict、模型对象或发布 receipt。文件级摘要确保内容
没有被静默改动；只有正式证据工厂的 fragment、claim、assertion、Markdown anchor 链才能作为可审计正式事实。

## 资料内容与真实性边界

| 来源 | 快照内容 | origin | 边界 |
| --- | --- | --- | --- |
| MySQL | 指向 `20260813T032528Z-abb0502c7d79` 的 manifest、对象计数与排除项 | `SNAPSHOT_REFERENCE` | 不复制、不读取业务行、samples 或 profiles。 |
| GitHub | `jishenghua/jshERP` 固定 commit `b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1` 的全部跟踪文件、tree、blob 摘要和文件索引 | `SOURCE_NATIVE` | 可由 `snapshotId + commit + path + startLine + endLine` 定位；尚未自动成为正式 Claim。 |
| 官方业务资料 | 三篇中文业务说明 | `DEMO_AUTHORED` | 演示资料，不是官网 HTML/PDF 原文，也不应在 UI 标为官方引文。 |
| ERP 管理制度 | 五篇含职责、规则、例外和操作后果的草案 | `DEMO_AUTHORED` | 讨论用目标资料；不能消除负库存、欠款字段、状态时间差异。 |
| 术语图 | 一份说明 Markdown 与 `graph.json` | `DERIVED_DEMO` | 用于导航；不增加独立根证据。 |

每个来源的阅读文档都使用相同的九章坐标：文档说明、业务目标、业务对象、业务活动、字段与维度、对象关系、
指标口径、示例问题和待确认事项。每章必须有可读的内容；材料不足时必须写明缺少什么、造成什么限制以及下一步。三个议题必须保留为待审阅语境，
而非自动结论：负库存的配置能力与目标制度、欠款字段的结构差异，以及单据状态的时间漂移。

## 冻结内容结构

```text
guanyijia-demo-content-v6-20260826/
├── manifest.json
├── generation-manifest.json
├── checksums.sha256
└── sources/
    ├── mysql/reference.json
    ├── github/repository.json
    ├── github/file-index.jsonl
    ├── github/source/…
    ├── official/*.md
    ├── policy/*.md
    └── terminology/{术语图说明.md,graph.json}
```

`repository.json` 固定远程地址、commit、tree 与 tracked-file 计数。`file-index.jsonl` 对每个 Git blob 记录相对
路径、Git mode、blob ID、SHA-256、字节数、文本编码和行数。验证器要求索引与实际 `source/` 树完全一致，拒绝
路径穿越、符号链接、重复文件、摘要错误、Git 身份不匹配和悬空术语关系。当前冻结版本仍以该索引复核现有
1,817 个冻结 GitHub 行号范围，全部在对应文件范围内；未来若重捕获源码，必须重新执行相同的范围校验，不能把
过期行号直接带入新快照。

## 当前已定版 V7 维护入口

在 `linguan-prototype-v2` 目录运行：

```bash
npm run evidence:guanyijia:content:check
npm run evidence:guanyijia:content:package
npm run evidence:guanyijia:content:package:check
```

本轮的最终、可发布内容是 `guanyijia-demo-content-v7-20260827`，内容 SHA 为
`sha256:b570297c59fd19538970985eacf60dd1bc9f46c7dbda550ecf46e2b43f706ca4`。上述通用命令先以
V7 校验器复核该快照；`package` 才生成浏览器安全的投影，`package:check` 只重算并对照已提交
投影。它们不联网、扫描或调用 Codex。若需复核历史 V6，必须显式运行：

```bash
node scripts/evidence/guanyijia-demo-content-v6-generate.mjs --check \
  ../modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v6-20260826
```

### V6 完整性基线

V6 的发布门禁保护的是**内容集合和可追踪关系**，不是篇幅。它固定核验五个来源及其正式快照身份、每个来源的九个章节、
每条结论的完整业务文字、全部来源材料、结论到材料及 Markdown 锚点的关联、合并文档和术语图。当前基线为：115 条结论、
140 份材料、8 项明确缺口；MySQL 的 30 张表和 6 个过程；GitHub 的 43 条结论和 69 份源码材料；术语图的 56 个术语和
80 条关系。任何删减、篡改正文或破坏关联都会拒绝打包；字数、Markdown 行数和文件大小不参与判断。

因此，V6 既不会因文案更精炼而被误拒，也不会因保留了相同 ID 却删除说明、材料或关联而被误放行。

### 已批准的 V7 九章阅读内容

`guanyijia-demo-content-v7-20260827` 是从精确 V6 追加的已生效阅读投影。用户在审阅数据库样稿和
五源 Selection 后明确批准
`../modeling-evidence/guanyijia/demo-content/candidates/V7/selections/v7-five-source-review-20260829.json`；
冻结过程只复制 V6 原始材料并新增经验证的人类可读九章正文。它不重新捕获来源、不替换 V6，也不改写原始
证据、正式 Claim、正式冲突或管伊佳正式 V1。

本轮早期曾出现一次 Codex 本地状态数据库 `~/.codex/state_5.sqlite` 只读的预检失败；它发生在
`thread.started` 前，因此没有消耗候选轮次，也没有产生产品内容。能力修复与回归测试通过后才重启同一轮，随后形成并
验证了本次批准的五源 Selection 与 V7 快照。这不是模型、配额、来源内容或 Schema 的失败。该状态目录的写入能力
仍必须在未来每次维护调用前预检；预检被拒绝时保留诊断回执，明确报告被阻断的 `CODEX_STATE_DB_WRITE` 能力和路径。

候选维护始终是显式、逐来源的流程；本次已完成的 V7 不授权后续重跑，未来维护必须创建新 snapshot：

```bash
npm run evidence:guanyijia:content:candidates:generate -- guanyijia_mysql
npm run evidence:guanyijia:content:candidates:selection -- /private/tmp/v7-selection.json \
  guanyijia_mysql=<candidate-id> guanyijia_github=<candidate-id> \
  guanyijia_official_docs=<candidate-id> guanyijia_demo_policy=<candidate-id> \
  guanyijia_semantica_demo=<candidate-id>
npm run evidence:guanyijia:content:candidates:check -- /private/tmp/v7-selection.json
npm run evidence:guanyijia:content:freeze -- --selection /private/tmp/v7-selection.json
```

V7 的 `generate` 只接受一个明确的 source ID，因而只会创建或重新生成那一个来源的候选；它绝不把五次调用、候选选择和
冻结合并为一次命令。它要求已登录的 ChatGPT/Codex session，拒绝 `OPENAI_API_KEY` 和任何其他按量 API 凭据；只允许指定的
Luna 模型与 Schema 输出。GitHub 原始源码必须已由经过授权的固定 commit 捕获到该 snapshot 目录。生成失败没有自动重试、
模型切换或旧模板补位；是否为同一来源再次发起调用由维护者明确决定。

Codex 结构化输出 Schema 中的常量字段必须同时声明显式 JSON 类型；来源身份、九章、非空可读正文与 GAP 结构由本地生成器和
冻结校验器执行，不依赖模型端的可选 Schema 关键字。CLI 失败时必须保留 JSONL 中的 `task_complete.error`，先按
服务端错误修正契约，再由维护者决定是否发起唯一一次正式生成；插件警告或退出码本身不能作为自动重试依据。

候选生成必须由维护者明确发起 **五个** 已登录的 ChatGPT/Codex 订阅会话，每个来源一个独立的
`gpt-5.6-luna` / `high` / `read-only` 调用。每一次调用都在
`../modeling-evidence/guanyijia/demo-content/candidates/V7/<candidateId>/` 追加保存不可改写的
`raw-output.txt`、`receipt.json`、`normalized.json` 和 `review-report.json`。receipt 同时绑定 source ID、V6
content digest、会话生成元数据和 raw-output digest；review report 的 issue 使用稳定的 `class`：
`NORMALIZED` 或 `CRITICAL`。

`NORMALIZED` 记录已经确定性修正的章节顺序、额外字段、空白或内部传输词，并形成可选择的 `READY_WITH_WARNINGS` 候选。JSON、来源身份、canonical 九章、GAP 结构、receipt
或 V6 身份错误形成 `CRITICAL` / `BLOCKED`，但该来源的四个候选制品仍保留，其他四个来源不会被丢弃。维护者只能显式
重新生成被阻塞的 source，生成器拒绝 `OPENAI_API_KEY` 等 API 凭据，不得回退按量 API、自动重试或切换模型。

五份候选文档都必须使用完全相同的九个 canonical `ModelingDocumentSection`：文档说明、业务目标、业务对象、
业务活动、字段与维度、对象关系、指标口径、示例问题和待确认事项。完整性门禁要求每章具有非空、可读的正文或完整 GAP。GAP 必须说明缺少什么、产生何种限制和下一步；不得重复结论或补造事实。

维护者随后创建一个显式 selection：它必须正好列出五个不同 candidate ID，并绑定当前 V6 content digest。selection
预校验每个 receipt、raw output、normalized 与 review report 后才允许冻结；任一 `BLOCKED` 候选都会拒绝冻结，但不会
删除选择或已就绪候选。没有“生成后立即冻结”的 CLI。通过来源身份、引用、章节和 GAP 完整性校验的 selection 才会
复制 V6 原始材料、仅新增 V7 阅读投影、重算 manifest 与 checksums，并以目录 rename 发布。目标目录已存在、V6
继承材料字节发生漂移或引用校验失败时都必须失败；V6 及更早目录始终只读保留。

V7 已通过其专用校验，复核 lineage、generation receipt、九章和最终阅读文档完整性；该校验不联网、不调用 Codex，
也不能由通用快照检查给出伪绿。通用 `content:check`、`package` 和 `package:check` 现在验证和发布 V7。
`package` 是明确的本地发布步骤：它只读取已成功冻结的 sidecar、校验每份标准审阅文档的九章、结论、依据和锚点，再写入浏览器静态投影
`src/features/guanyijia-demo-content/pinned-demo-content.generated.ts`。它不会扫描、联网或调用 Codex；浏览器包只
包含当前阅读所需的已冻结摘要与精确摘录，完整 719 个文件继续只保留在本地冻结快照。每个富内容快照都按其
`previousSnapshotId` 递归保留已发布的历史投影；当前已形成精确的 `V7 → V6 → V5` 链，已绑定的运行不会被静默升级。
`package:check` 只复核 sidecar 与已提交的浏览器投影是否相同。

日常开发、测试、构建、演示、恢复和热更新只读取已提交的浏览器投影；它们不得调用上述维护命令、下载源码
或重新生成演示资料。

## 运行时阅读投影

`DemoContentReview` 是内容 sidecar 与资料审阅工作台之间唯一的深模块。它的接口先
`bindRun(runId, formalSources)`，随后以 `readSource(binding, sourceId)` 返回只读投影。模块在内部完成来源顺序、
正式 snapshotId、内容 snapshotId、内容 SHA、Markdown 摘要、摘录摘要和 trace anchor 校验；调用方不需要知道
sidecar 的目录、Git 树或文件索引。

用户可见的审阅分组、术语与交互遵循[管伊佳数据标准化审阅体验设计](design/data-standardization-review-experience.md)；该设计不改变本节描述的冻结内容身份。

每个来源固定投影为同样的三个页签；来源依据仍只在所属结论内按需展开：

```text
审阅事项：待确认事项（本来源建议与已准入跨来源比较）、逐项待补充资料与已处理事项
审阅结论：核心业务结论与默认展开的完整对象目录
标准化文档：同一 Revision 的 V7 九章阅读版与只读 Markdown 源文
结论内依据：来源材料 → 审阅结论 → Markdown 段落
```

其中“待确认事项”只是标题和计数：“本来源建议”与“来源差异与比较”必须渲染为两个同级
Box。运行步骤的 `introducedConflictIds` 是正式差异唯一权威；每个 ID 必须显示为来源差异
未决卡或已处理事项中的已保存卡，不能只在时间线出现。第九章的每个明确标记则由精确文档
内容逐项投影为“待补充资料”；阅读投影不得改写 V6 Markdown、revision、SHA 或复制结果。

来源材料始终不可修改。当前 Demo 只在数据库 `jsh_depot_head` 名称和代码仓库负库存说明两个精确映射的
Claim 上开放建议核对；编辑器只显示允许变更的业务名称或业务说明，确认的文字会同步进入审阅事项、审阅结论、标准化文档与结论内依据，但不会改写源码、DDL或证据位置。数据库材料默认以重点字段结构表呈现，完整字段和带行号的原始 DDL 是按需展开的
复核内容。人类阅读投影先说明业务含义，只有用户主动展开依据时才显示原始代码、DDL、文件路径和行号；冻结原文仍逐字保留，确保能被复核。

审阅页的数字只从当前人类阅读投影计算：审阅结论、来源材料、待确认事项和已准入的跨来源比较都能定位到同一组内容。未决定的剧本化核对项和所有已准入跨来源比较都只计入“待确认事项”，不会同时计入“审阅结论”；状态 9 的资料不足也作为独立“待补充资料”留在审阅事项。旧编译链中的块、推断或待归类表数量不作为业务统计显示。每个来源文档版本保存独立创建时间；即使页面重载后再次修订，新版也会晚于已保存的上一版，历史列表据此区分当前版和历史版。

来源正文保持单源纯度，跨来源的互补、结构差异、时间漂移和资料不足只在审阅事项的“待确认事项／待补充资料”投影中出现，不进入审阅结论；投影取自本次运行的所有已准入来源，因此切换到后续文档后仍持续可见。具备决定前提的正式差异在其成立来源中立即内联展开，必须在完成该来源前保存；“等待更多来源”的比较和待补充资料不阻断推进。来源完成后会自动收起并开始下一来源的读取、分析和组织，下一份 `DOCUMENT_READY` 文档自动打开；用户主动回看历史来源时，新的就绪文档不会抢走阅读位置。五源完成时，三项正式决定已经保存，工作台从同一组精确来源 revision、已保存修改与决定清单构造只读合并预览：三页签共享 Preview SHA256、完整九章和章内五来源顺序；唯一“确认并定版”会复算同一内容、生成并定版，且实际合并文档引用必须与预览一致。阅读版把可识别的连续 `GAP` 只读投影为逐项中文“待补充资料”，但 Markdown 源文和复制结果仍逐字保持冻结原文。任何
绑定、内容或身份校验失败都只让受影响来源显示“冻结内容快照校验失败”，并阻止完成该来源审阅；绝不回退旧模板
正文，也绝不重新扫描来源。

运行来源页只是固定资料的只读运行投影。它显示来源类型、脱敏位置、读取范围、正式与内容快照版本、内容性质、凭据
状态和当前运行状态；不提供添加、编辑、测试连接、重新扫描、冻结或 YAML 操作。

## 与定版的关系

内容快照只改善人类阅读和追踪，资料审阅与三项治理决定仍沿用原有 revision/CAS 链；
此 Demo 的零变化交付由作者使用“确认结果并定版”直接冻结，记录定版人、时间、输入 revision 和正式核心摘要。
它不要求审批意见或独立审核。历史 `reviewerApproval` 记录仍可读，但新运行不会产生它。

## 新版本与回滚

不可修改已冻结的 `v1-20260821`、V5、V6 或 V7 目录。当前新运行绑定已发布的 V7；已经绑定 V5 或 V6 的运行继续读取原版本，
不会静默升级。V7 以 `V7 → V6 → V5` 追加链保留历史投影。需要更新时必须创建新的
snapshotId、重新捕获（如涉及源码）并重新生成，保留旧目录作为可比较、可回滚版本。工作台已通过 `DemoContentReview`
只读加载这个 sidecar，并清晰显示每类
内容的 origin；若要把其中任何文字提升为正式事实，必须重新经过完整多源证据治理流程。
