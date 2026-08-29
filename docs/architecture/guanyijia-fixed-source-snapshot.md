# 管伊佳五源固定扫描快照

## 结论

管伊佳 Demo 使用三层固定资料，所有可交付资料都在其对应的受控仓库中版本化：

1. 冻结源输入：MySQL、GitHub 和官方文档的 Evidence/locator Fixture，以及制度 Markdown 和术语图的确定性输入。
2. `PinnedSourceSnapshotBundle`：由维护命令预先生成并提交的五份读取摘要、九段 Markdown、结构化识别项、断言、证据映射、trace links 和冲突投影。
3. `FrozenDemoEvidenceBundle`：EvidenceFactory 读取的公开、安全投影 Bundle；读取前会验证 Bundle SHA、来源身份、公开编译投影、trace links、原始制品回执和证据片段引用。

在这三层之上，真实多源治理 Demo 可以叠加一个只读 `CandidateEvidenceBundle`：它只从已经冻结的本地 MySQL/GitHub 资料选取少量、可定位的片段，投影互证、冲突、时间漂移和 GAP。它不是正式 V2，也不替换 `PinnedSourceSnapshotBundle`；其完整治理边界、信任分级和升级路径见 [多源证据治理参考架构](multi-source-evidence-governance.md)。

当前五源工作台绑定用户已批准、最近一次已冻结的富内容 sidecar `guanyijia-demo-content-v7-20260827`
（`sha256:b570297c59fd19538970985eacf60dd1bc9f46c7dbda550ecf46e2b43f706ca4`）。它是与正式 Bundle 并列、不可变的
人类阅读投影：MySQL 继续显示真实结构节选，GitHub 使用固定 commit 的精确源码行段，业务资料与制度明确标为
演示编写，术语图明确标为派生资料。`DemoContentReview` 在运行创建时把内容版本、内容 SHA 与五项正式来源
身份绑定；校验失败只关闭受影响来源，绝不退回旧模板正文或重新扫描来源。sidecar 不成为 Candidate、正式
Claim、Assertion、Conflict 或 V1 的输入。V7 是从精确 V6 追加的阅读投影；V6 保持只读前序，已绑定 V5/V6 的运行不会静默升级。

浏览器不会负责编译第二层。它只把 Bundle 写入本次运行所需的来源文档 revision；`localStorage` 是可随时丢弃的镜像，不是 Demo 的唯一来源。

## 运行规则

首次建立管伊佳五源运行时，Story 直接读取仓库内 `PinnedSourceSnapshotBundle` 的五份 `SourceDocumentCompilation`，验证 Bundle SHA 和来源身份后写入来源文档 runtime。浏览器可以把同一内容镜像到 `localStorage`，但镜像键只用于加速：

- story key；
- 五个 sourceId 的固定顺序；
- 五个 snapshotId；
- `content-first-v1` 编译器版本号。

后续刷新页面、重新进入数据标准化、重复演示或只修改 React/CSS/文案/交互代码时，读取的是同一 Bundle；绝不重新读取 DDL、GitHub 代码、制度 Markdown，也不重新派生 Semantica 三元组。镜像损坏、版本不符、浏览器存储不可用或配额不足时，直接回到仓库 Bundle；不会把存储故障伪装成新的源扫描成功。

Bundle 读取时会校验 schema、编译器版本、来源身份、snapshotId、payload 数组和 SHA。Bundle 自身缺失、身份不一致或校验失败时 Demo fail closed 并提示“演示快照不可用”；它绝不能回源扫描。

## 私有原始证据与公开 Bundle 的边界

当前 Demo 不准入 Tenant 153 的业务行、样本、profile、查询结果或绑定参数；它使用零行数据库逻辑材料和经过审查的安全摘录。未来在另行授权后，完整数据库/代码/文档抓取内容和可选的脱敏业务样本才可留在私有、Git LFS 管理的证据仓库。原型仓库和浏览器只消费 `FrozenDemoEvidenceBundle` 中经过脱敏的 `EvidenceFragment`、来源身份、编译结果和追踪信息；公开 Bundle 不携带原始行或可用于重新扫描的访问能力。每个 `OBSERVED` Fragment 都保留不透明的私有 `artifactRef`，并以精确的 SQL 对象、文件行范围、文档章节/页码或源代码符号 locator 指向其原始制品；公开 Bundle 只保留该引用和安全摘录，不保留原始内容。`GENERATED_TARGET` 必须带有 `PENDING_HUMAN_CONFIRMATION` 标签，不能伪装为观察到的原始证据，也不能支持 `FACT` block、`OBSERVED` assertion 或 `USER_CONFIRMED` assertion。

第三层不是私有 `SourceDocumentCompilation` 的 clone：它只能携带明确的 `PublicSourceDocumentCompilation` 投影。公开 read summary、九个具名 sections、block（含稳定 code、语义、label、受影响对象引用和仅 `{ normalized, text }` 的可读值）、assertion statement、Markdown 与 SHA、locator、delta 和冲突 ID 都有逐层字段白名单；未知字段和 `rawRow` 等私有载荷会 fail closed。`EvidenceFragment.excerpt` 的大小限制和明显结构化 raw 载荷拒绝只是纵深防御，不能被描述为完整的脱敏证明；完整 Bundle 的人工脱敏批准及其受信摘要才是隐私边界。

公开 Bundle、`PublicationReceipt` 和其中所有 SHA 都是**不受信输入**。Bundle 只用预先分配的 `publicationReceiptId` 选择发布项；它的 `rawManifestDigest` 和 `artifactProvenance[]` 是便于读取的公开投影，不能自行证明来源。唯一信任根是与应用代码一同审阅、静态发布的 `TrustedPublicationRegistry` pin：它同时固定 receipt、Bundle、私有 raw-manifest set 和 redaction policy 的 SHA。Factory 先验证 Bundle/receipt 的精确形状和自摘要，再要求它们逐一匹配该 pin；同步重算公开 Bundle/receipt 的攻击不能改变已经发布的 pin。freeze 只能生成候选 receipt 和候选 pin，不能自动改写 registry；真实 V2 freeze/审批完成前不得写入虚假 pin。

一份公开 `PublicationReceipt` 有按稳定顺序排列的 raw-manifest attestations、唯一 artifact memberships、逐 observed Fragment 的 locator/excerpt approval，以及 `APPROVED + ENTIRE_PUBLIC_BUNDLE` 的 redaction approval。Factory 重建 attestation aggregate 的 `rawManifestDigest`，但不读取私有 manifest 或原始 artifact；它只接受 receipt、Bundle、approval 和 registry 四方一致的 Bundle SHA，以及 receipt policy SHA 与 registry policy pin 一致。一个 artifact membership 可以被多个获准 Fragment 使用；每个 observed Fragment 恰好对应一条 approval，且它的 artifact、来源/快照、公开 locator 与 UTF-8 excerpt SHA 都必须闭合。receipt、registry、审批和私有 attestation 只保留在 Factory 的验证实现中，`readBundle`/`readFragment` 不会返回它们。`GENERATED_TARGET` 反而不得携带 `sourceId` 或 `snapshotId`，只可支撑 `evidenceStatus: 'GAP'`、`semanticKind: 'GAP' | 'PENDING_ASSET'` 的 block，以及 `INFERRED` assertion；它不能作为普通推断或已观察事实的依据。

因此，对于新的 `FrozenDemoEvidenceBundle`，EvidenceFactory 是唯一读取 seam：接入该 Bundle 的 Demo、测试、恢复、构建和热更新只能读取并校验已提交的内容。它们既不调用捕获/生成维护命令，也不会联网、访问数据库、扫描文件或调用 LLM；校验失败必须 fail closed，绝不合成摘录或回源补齐。现有 V1 `PinnedSourceSnapshotBundle` 在 V2 freeze/integration 前继续按原有规则运行。

EvidenceFactory 将 Bundle 和 receipt 视为 `unknown`，并在使用静态 registry 前后完成 fail-closed 验证；它只接受 schema version `1`、`evidence-factory-v1` 编译器版本，以及 `ROOT` 或 `DERIVED_VALID` 血缘状态。它对来源分类、权威级别、Fragment/Generation 枚举和所有 Bundle payload 数组进行运行时校验。公开 Bundle、编译投影、trace、Fragment、回执、attestation、membership、approval 和 Generation manifest 都使用字段白名单，未知字段（包括 `rawRow` 或私有原始制品内容）会 fail closed，而不是通过 clone 回传。空值或损坏的持久化 Bundle 同样只会抛出领域的演示证据快照错误，不能泄露 `TypeError`。

每份编译中的每个 block 与 assertion 必须各被且仅被一个 trace link 覆盖；该 link 的 Markdown anchor 必须实际存在于该编译 Markdown，且每条 trace 都必须带有非空、可解析的 Fragment 引用。`FILE_LINES` locator 使用路径和闭合行范围，不要求源码 symbol；只有 `SOURCE_SYMBOL` 要求 symbol。每份公开编译的 Markdown SHA、来源身份和投影一致性也在返回前校验。`readFragment` 在查找任何 Fragment 前先完整校验全部 Bundle，因此损坏的持久化数据只会产生演示证据快照错误。所有验证在 clone 返回任何 Bundle 或 Fragment 前完成。

## 修改与失效边界

来源文档中的人工修改不覆盖基线快照。修改结构化块会进入 `SourceDocumentRuntime.revise`，生成新的文档 revision（例如 r2/r3），同步更新 blocks、assertions、sections、Markdown、SHA 和冲突投影；旧 revision 保留只读。它是审阅工作流的版本变化，不是重新扫描源头。

普通 UI、布局、按钮、文案和审阅交互修改不需要重新编译快照。只有明确执行 `npm run snapshot:guanyijia:refresh` 的维护工作才会产生新 Bundle，且通常只在以下情况发生：

- 冻结源输入内容、snapshotId 或 source 顺序变化；
- 九段结构或结构化投影契约变化；
- 编译器语义确实变化，此时显式递增 `content-first-v1` 版本号。

因此，代码开发不再以“重新读取所有源”作为 Demo 验证前置条件。UI 验证直接消费固定编译结果。维护命令也只允许使用仓库已有冻结输入；任何外部数据库、GitHub、文档站点、OCR 或 LLM 重新扫描都必须先取得单独、明确的授权。

## 与正式模型的关系

五源编译快照、来源文档 revision、冲突决定和治理附录都属于标准化资料治理层。它们不覆盖管伊佳正式 V1 的 Artifact、semantic SHA、Catalog fingerprint 或黄金对象计数；zero-delta M4 交接只追加治理与证据回执。

缓存是浏览器 Demo 的本地加速和稳定性机制，不是新的正式证据来源，也不是跨浏览器标签的协作数据库。多标签仍允许访问；同一运行的并发写入一致性继续记录为后续 TODO。
