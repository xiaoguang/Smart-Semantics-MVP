# 数据标准化（M3）与 AI 建模（M4）

## 目的

M3 和 M4 通过不可变的标准建模文档协作，解决“多来源证据处理”和“从文档生成模型”混在同一页面、同一状态机中的问题。

```text
来源配置 → 证据快照 → 冻结批次 → Agent 流水线
→ 每个来源独立九段文档 → 结构化章节审阅
→ 来源语义对齐 → 人工决定写回语义载荷
→ 选择来源文档集合或合并文档
→ 作者确认并定版
→ AI 建模 → 候选对象 → 个人草稿 → 审核 → Catalog
```

## M3：数据标准化

M3 负责来源设置、共享连接、YAML、文件、配置版本、测试、范围发现、激活、快照、批次、Evidence、Claim、血缘、来源比较和差异决定。用户只看到一条连续工作流：

```text
设置来源 → 生成来源文档 → 审阅来源文档
→ 自动比较来源 → 处理来源差异 → 生成输出文档
→ 作者确认并定版 → 交给 AI 建模
```

内部仍执行读取来源、解析结构、提取业务语义、比较来源、生成文档和质量检查六步。运行时只显示当前一步；全部完成后收成一条“资料处理完成 · 6/6”，详细记录按需展开，不长期铺满六张完成卡。

确定性 Fixture 负责 DDL、RDF、代码和文档解析。Agent 只提出业务语义建议；冲突、低依据和最终冻结都需要用户确认。M3 的命令不能生成候选模型或写入个人草稿。

### 演示恢复与本地存储

管理员的“恢复演示”是两阶段、可重试的本地恢复协议，不是单纯重置页面状态。第一阶段只写入 `linguan:demo-recovery:v1` 标记并重载页面，使旧 React Runtime 持有的 IndexedDB 连接全部释放；第二阶段在任何 Provider 创建前读取该标记，依次删除标准化运行、来源文档、正文、审阅索引和交付物的五个 IndexedDB 数据库，重建协作与来源管理演示种子，并删除管伊佳活动运行指针、旧 M3 metadata 与 Review Surface 位置。只有所有步骤成功才移除恢复标记并挂载应用。

`onblocked` 不是删除成功：数据库仍被占用时保持标记并展示唯一“继续恢复”动作，应用不得挂载到半恢复工作台。协议只枚举本 Demo 所有的标准化键；它保留 `linguan:demo-auth:session:v1` 当前登录、仓库固定来源快照及其浏览器缓存，也不清除未知项目或外部来源的存储。活动指针存在但 Run、Document metadata 或正文缺失时同样由该协议恢复。恢复后的协作和来源种子只能重建演示起点；管伊佳正式 V1、黄金 Artifact、Markdown／语义 SHA、Catalog fingerprint 和对象计数不在清理范围内。

## 标准化运行编排

`src/features/standardization-run/` 是 M3 逐源读取、文档审阅、冲突门禁、交付冻结和 M4 交接的唯一运行编排 seam。`StandardizationRun` 以 `runId` 为读取边界，并持有有序来源步骤与不可变顺序的时间线；页面和后续投影不能按 `projectId` 猜测最新运行。元数据固定使用 `linguan:standardization-runs:v1`，事件只持有 `payloadRef`，正文通过现有 `ContentAddressedStore` 按引用读取。页面另从这些不可变事件投影业务时间线：每份已读取资料、已生成／已审阅文档、已保存来源决定、标准化结果、定版结果和建模交接都能重新打开精确的只读内容；当前步骤才承担继续操作。在作者定版前的`READY_FOR_OUTPUT`阶段，`REPLACE_SOURCE_CONFLICT_DECISION`追加不可变的`CONFLICT_DECISION_REPLACED`事件，而不是覆盖历史 Artifact；最新 Artifact 是下一份交付物的有效决定，旧 Artifact 继续可回看。若已有交付物，命令会同时追加`DELIVERABLE_SUPERSEDED`、将该交付物 ID 记录为历史、清空当前交付物并要求重新生成；因此旧文档不被悄然改写，新交付物必定绑定新决定集合。定版或交接后的交付物不可替换。

运行由带 `commandId`、`expectedRevision` 和操作者的命令推进，并可注入公共 `StandardizationMetadataStore`。这个 seam 只暴露 `read()` 与 `compareAndSet(expectedVersion, nextRaw)`：Runtime 先读取并校验快照，按需把不可变正文写入内容存储，最后以原快照版本执行一次原子 CAS；CAS 失败统一报告“运行已被其他窗口更新”，不得覆盖胜者，失败命令最多留下可按 SHA 去重的无引用正文。浏览器默认 metadata adapter 使用 IndexedDB 单条 state record，并在同一个 `readwrite` transaction 内复读版本、比对和写入，因此跨 Runtime、跨 tab 不依赖租约或 Web Locks。Node 和测试显式使用 Storage adapter，其复读与 `setItem` 只在按 Storage 对象共享的模块级队列内执行；浏览器不会选择这个非跨上下文 adapter。`commandId` 绑定规范化命令指纹；只有指纹完全相同的重试才幂等，同一 ID 改变命令类型、运行或 payload 会失败。命令索引使用无原型字典和自有属性读取，因此 `toString`、`constructor`、`__proto__` 也是普通合法 ID。旧版仅保存 `commandId → runId` 的记录可以安全读取，但因无法证明原命令内容而拒绝用原 ID 重试。同一 `projectId + batchId` 只创建一个运行；revision 错误、越序和错来源的命令在写入元数据前失败。已对齐与未决差异来源共同构成已审阅前缀，当前只允许一个 `READING` 或 `DOCUMENT_READY` 来源，未读取来源保持后缀；来源仍严格按声明顺序经历 `PENDING → READING → DOCUMENT_READY → ALIGNED`，有差异时在审阅后保留为 `CONFLICT_BLOCKED`。未决差异不会阻断 `START_NEXT_SOURCE`，其 conflict ID 与未解决状态持续保存，并可在没有活动来源时由 `RESOLVE_SOURCE_CONFLICT` 逐项记录决定；业务上的 `REGISTER_GAP` 仍映射为合法的保留缺口决定。只有全部来源 `ALIGNED` 才进入 `READY_FOR_OUTPUT`，所以未决差异仍阻止生成与定版交付物。不同 `sourceId` 可以共享相同展示名称，身份唯一性只由 `sourceId` 约束。审阅中的当前来源还可用 `REVISE_SOURCE_DOCUMENT` 原子替换 documentId、revision 和该来源引入的冲突，并生成 `DOCUMENT_REVISED`；事件正文只保存文档身份与 changed block／section／object 摘要，不内联 Markdown。

元数据读取不会把损坏 JSON 当作空状态。Runtime 校验 schema version、run 身份唯一性、sequence 单调性、运行状态、有序来源步骤、冲突与文档字段，以及连续且归属正确的事件、操作者和内容引用；每个来源还必须投影为 `SOURCE_READ_STARTED → SOURCE_READ_COMPLETED → DOCUMENT_GENERATED → CONFLICT_CORROBORATED* → DOCUMENT_REVISED* → DOCUMENT_REVIEWED → CONFLICT_FOUND → CONFLICT_RESOLVED*` 的合法生命周期，未发生的尾部可按当前状态截断。`SOURCE_READ_STARTED` 必须严格按来源顺序出现；为了允许先继续读取，先前来源的 `CONFLICT_RESOLVED` 可以在后续来源审阅完成后追加，但来源事件仍不能落在交付事件之后，同一 source/conflict 的 corroboration receipt 不能重复，且生成 revision 的 receipt 不能被移动到任何 revision 事件之后。因此重排 review/found、把 receipt 提前到派生来源 START 之前、移到 `DOCUMENT_REVISED` 之后或仅重编号 eventId 都不能伪装成合法不可变时间线。Runtime 同时校验运行状态、来源状态、未解决冲突和交付身份的组合不变量，例如 `READY_FOR_OUTPUT` 必须全部来源对齐，`FROZEN` 必须已有交付物和审核。交付身份只能出现在全部来源对齐后的阶段，且必须存在唯一、顺序正确的 `DELIVERABLE_GENERATED → REVIEW_SUBMITTED → DELIVERABLE_FROZEN → MODELING_HANDOFF_COMPLETED` 事件；Runtime 根据当前 identity 的确定性 JSON 复算 SHA-256 并核对事件 `payloadRef`。嵌套 `null`、非法枚举或矛盾组合在进入状态机前就转换为中文可读损坏错误。发现损坏时保留原字节，避免下一次命令覆盖证据。事件载荷即使由自定义 `ContentAddressedStore` 返回，也会根据正文独立复算 SHA-256 并与 `payloadRef` 比对。

最后一个来源对齐后，运行进入 `READY_FOR_OUTPUT`。新的标准化文档交付按 `DELIVERABLE_GENERATED → DELIVERABLE_FROZEN → MODELING_HANDOFF_COMPLETED` 记录真实事件；冻结把运行推进为 `FROZEN`，完成交接后为 `HANDED_OFF`。历史 `REVIEW_SUBMITTED`／zero-delta 事件保持只读兼容。这个运行 seam 只登记下游产物身份和状态，不替代来源文档、冲突编辑器、冻结校验或 M4 自己的业务 Runtime。

## 来源文档与内容存储

`SourceModelingDocument` 属于一个来源快照，只引用内容寻址存储中的 blocks、章节、断言和 Markdown，不在元数据中重复保存大正文。`SourceDocumentBlock` 与 `StructuredValue` 是 `source-documents` 深模块的通用合同；项目 story 只能 re-export。正文按 `sha256:<digest>` 读取并独立复算，元数据只保存引用。每份新文档使用相同九段结构，必须明确本来源确认了什么、推断了什么、还缺什么。旧文档没有 `blocksRef` 时明确保持只读，Runtime 和 legacy UI 都不能从章节反猜结构化块。

`SourceDocumentRuntime.revise` 是结构化来源文档 revision 的单一写入口：调用方提供完整 blocks、assertions 与 sections；`structured-projection.ts` 是 block → assertion → 九段 sections → Markdown 的通用确定性投影 seam。Runtime 校验 block/assertion ID 唯一且一一对应，并逐字段核对 section、statement、evidenceRefs、provenance、完整九段 sections 与重渲染 Markdown，不能用另一份合法 SHA 正文替换当前文档正文。内容存储返回的引用也必须等于本地复算 SHA。四类正文全部成功写入后，元数据 CAS 一次生成相邻 revision、把旧 revision 标为 `SUPERSEDED`，旧正文保持字节不变；校验、内容写入或 CAS 任一失败都不会提交半套元数据。同值修改拒绝创建 revision，`SUPERSEDED` 历史 revision 不能重新标记为 ready 或分叉出重复 revision。来源文档元数据继续兼容 `linguan:source-documents:v1`，新浏览器工作台默认使用 `linguan-source-document-metadata-v1` IndexedDB 的单记录事务 CAS；Node 和测试必须显式注入原子 store 或 Storage adapter。

页面先读取章节计数、断言数、证据数和校验摘要；用户选择章节后才读取该节正文。`getRevisionDiff` 从相邻 revision 的真实 blocks、assertions、sections 和 Markdown 计算 Before／After 与行级差异，不用字符数冒充 Diff，也不修改历史 revision。

管伊佳页面把已验证 block 做成两种用户可见的只读投影：`审阅清单`与`Markdown 文档`；每条审阅结论都可在原位置按需展开来源依据。每个 block 都有 `SourceDocumentTraceLink(blockId, assertionId, section, markdownAnchor, evidenceRefs)`；该链路只在当前结论需要阅读或跳转 Markdown 时按需读取，不能为了追踪而将整份来源正文送入 React。默认阅读区展示安全的 DDL／SQL／代码／制度段落／三元组摘录、来源位置和支持结论；SHA、content ref、sourceId、内部 revision 与 `FROZEN_FILE` 等仅用于内部审计，不进入业务页面。跨来源互补、差异和时间漂移另在“本次读取发现”区域按本次运行准入状态投影。

### 管伊佳固定来源编译模块

`src/features/guanyijia-standardization-story/` 是管伊佳主演示的冻结事实编译 seam。外部只通过 `createGuanyijiaStandardizationStory({ policyDocuments? })` 获得 `listSources()`、`compileSource({ sourceId, priorCompilations })`、`reviseCompilation(...)` 和 `listConflictDefinitions()`；编译与修订都无存储副作用，不登记 `SourceDocumentRuntime` 或 `StandardizationRunRuntime`，后续编排只能把完整编译结果作为输入，不能让本模块越权推进运行状态。

固定资料顺序为部署 MySQL、固定 GitHub Commit、官方核心文档、演示制度 Markdown、演示 Semantica 派生图。前三者的 snapshot、manifest 计数和 Evidence locator 直接读取当前冻结 fixture；MySQL 的待归类表由部署 95 张表减去源码 32 张表的 manifest 差集得到 63 项，不从黄金 Artifact 反向筛选。官方来源只读取独立的 `official-documents-fixture.ts`，其中冻结 14 条官方文档 Evidence 与 locator；生产 story 的来源身份和编译路径都不得导入旧 final Artifact 或 modeling package。演示制度是第四个根来源，但必须保持 `DEMO_POLICY` 和“演示制度，不是真实生产制度”警示；Semantica 是唯一 `DERIVED` 来源，不增加根来源数。

每个来源先生成结构化 `SourceDocumentBlock`，再通过来源文档共享投影 seam 一对一生成同身份 `StructuredModelingAssertion`、九段 `sections`、标准 Markdown 与 SHA-256。`stableCode + normalized value + evidenceRefs` 在单份文档内去重，技术 ID 只进入 value 明细、对象引用、Evidence 与 locator，不进入默认业务叙述。`delta` 按先前 compilation 的 `stableCode` 与规范 value 计算新增、变更和新增缺口；相同冻结输入必须产生字节稳定的 Markdown。`reviseCompilation` 只接受指定 block 的 label/value 变化；wrapper 会把当前 compilation 与每份 prior compilation 对照 story 的 canonical base，校验 source identity、authority、lineage、read summary、locator，以及 label/value 之外的所有 block 身份字段，不能由调用方同步伪造来源、章节、Evidence 或影响对象。它从新 blocks 重建 assertion、sections、Markdown、SHA、delta 与冲突 variant，并返回真实 compilation diff。无变化修订拒绝；制度负库存或状态 9 修改只影响对应制度投影和冲突，不改变三个真实来源或黄金 V1。

三份演示制度以结构化 `{ path, version, content }` 输入。默认输入在模块边界冻结，实例创建时重新复制；路径、版本和内容共同参与 policy snapshot，文件内容另有 SHA-256 locator。调用方创建实例后再修改原对象不会改变已创建故事。Semantica snapshot 从 policy snapshot 派生，并要求 `priorCompilations` 中存在同 snapshot 的 `guanyijia_demo_policy`；该上游还必须通过 blocks/assertions 一对一投影、sections 重建、Markdown 重渲染与 SHA 校验，并完整保留四项必需制度 block、Evidence 引用和与当前文件 path/version/content SHA 一致的 locator。任一引用缺失、正文损坏或版本不匹配时只生成 `UPSTREAM_MISSING` gap，不能输出有效派生事实。有效派生的每个 block 与 assertion 同时携带 `semantica://` statement Evidence 和具体 `demo-policy://文件#章节` 上游 Evidence，locator 也登记上游引用。

冲突定义保持三个稳定身份：GitHub 在 MySQL 基准之后引入 `gyj-conflict-debt-schema`；演示制度在 GitHub 实现事实之后引入 `gyj-conflict-negative-stock` 与 `gyj-conflict-status-nine`；Semantica 只能 corroborate 后两项。触发点不能只看 prior `sourceId`：每个 current variant 分别核对冻结 snapshot、唯一 stableCode、normalized value、对应 assertion、Evidence 和 locator，因此空壳、错快照或只损坏某一规则的 prior 不会制造无依据冲突，也不会压掉另一项仍完整的冲突。制度中的库存时点条款只是建议，不得覆盖 MySQL 的 `current_stock_as_of` gap。冲突解决命令与结构化 patch 不属于本模块。

本模块不会修改管伊佳正式 V1。正式 Artifact 仍只有 MySQL、GitHub、官方文档三个来源，身份和签名保持 `artifact-guanyijia-v1-40c8572864bd`、Markdown `5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210`、语义载荷 `c55c2ebaf3e6cdc92d4ba5115692253a7f6b7e4442eeb44f5c2360a887594248`；演示 policy 与 Semantica 不得进入正式 Evidence Context。

### 管伊佳连续时间线工作台

`src/features/data-standardization/guanyijia-workbench-runtime.ts` 是管伊佳固定资料故事的页面编排 seam。页面只调用 `read(actorUserId)`、`readAssistantHistory(...)`、`previewCurrentConflict(...)` 与 `execute(command)`；Runtime 在内部组合 `StandardizationRunRuntime`、`GuanyijiaStandardizationStory` 和 `SourceDocumentRuntime`，接受运行生命周期、结构化文档修订、Git 式冲突决定与审阅助手命令。逐源读取会先编译该来源、连同 blocks 登记一份独立九段来源文档，再以小摘要完成运行事件；事件 payload 只保存 documentId、revision、标题、九段数量与 block 计数，不内联 Markdown 或 Evidence 正文。

活动运行指针使用 `linguan:guanyijia-workbench:active:v1:<canonical-batch-id>`，记录 project、故事版本、canonical batchId、稳定 runId 与工作台命令指纹。后续恢复只按该 runId 读取；指针无法解析、身份不匹配或运行不存在时必须显式失败，不按 project 回退到“最新”运行。创建复用与恢复都会逐项校验 run 的 sourceId、sourceName 与 order 正好等于当前固定资料集，不能接受同 batch 的外部或残缺运行。命令指纹读取只接受自有属性，因此 `constructor` 等合法稳定 commandId 不会与对象原型混淆。

snapshot 将持久运行投影为连续时间线、当前来源／文档、revision 历史、真实相邻 diff、唯一下一动作和轻量事实检查器。每个 `DOCUMENT_GENERATED`／`DOCUMENT_REVISED` 都从自己的事件 payload 读取当时的 document identity，因此 r1 生成回执不会被当前 r3 覆盖；同一来源最新的 generated/revised 事件才是 `CURRENT`，较早事件保持 `RECEIPT`。已完成读取与文档生成项是紧凑回执；只有真正未完成的读取、文档审阅或冲突项展开为当前项。时间线不携带 block 或 Evidence 正文；九段文档按 FACT／INFERENCE／GAP／CONFLICT 分组，每页最多 20 个 block。审阅导航由纯状态 seam 选择当前 documentId、章节、block 与稳定 focus target；apply 后切换新 documentId，但保留当前章节和 block 焦点。

所有继续审阅入口都通过同一个 `openSourceDocument({ sourceId, documentId, revision, section?, blockId? })` 路径：当前任务卡、`DOCUMENT_GENERATED`／`DOCUMENT_REVISED` 时间线回执，以及助手的受限打开目标。它读取同一 active run 内登记的精确 document identity；当前待审来源定位首个未完成章节，历史来源只读打开。右侧“来源资料”的来源行不调用该路径：它只选择并定位同一来源的业务检查点，保留主审阅区当前文档、结论选择、滚动和焦点。页头唯一的“来源资料／收起来源资料”控件开合资料区；内联区没有重复标题或关闭盒，抽屉和全屏层才使用图标关闭。资料区在 `≥1280px` 内联、`900–1279px` 抽屉、`<900px` 全屏，并由同一状态机转换。

Preview 是纯投影，不写文档或运行：它返回 Block／Assertion Before／After、当前章节 Markdown 行级 diff、受影响冲突和对象。Apply 依次调用 story 修订、`SourceDocumentRuntime.revise`、运行 `REVISE_SOURCE_DOCUMENT`，再从持久状态重建 snapshot。跨 Runtime 使用可恢复 saga：文档 r2 创建后先把同一命令指纹与 before/after documentId 记入活动指针；若运行 CAS 失败，运行仍指向 r1，重试同一 commandId 校验并复用该 r2，不创建 r3，成功后清除 pending 记录。刷新严格校验运行步骤登记的 project、documentCode、source、snapshot、revision，再从该 revision 的 blocks 重建 compilation，不回退默认冻结 compilation。旧文档没有 `blocksRef` 时只读取并展示已持久化 sections/assertions/Markdown，不暴露 compilation，不允许 apply 或完成审阅，也不以同 snapshot 的默认 story 文档覆盖旧文档。

MySQL 审阅完成后运行进入下一来源；GitHub 审阅完成后在主区展开欠款字段 Hunk，保存决定后才继续。Policy 两项依序逐项阻断，Semantica 只追加佐证回执。本 seam 同时编排来源文档内部修订与跨来源决定，但不创建 deliverable、模型草稿或 M4 交接，也不读取或修改管伊佳正式 V1 Artifact。

### Git 式来源冲突决定（Checkpoint 5 当前合同）

Checkpoint 5 取代上文 Checkpoint 3 中“下一 Checkpoint 实现／一次解决全部冲突”的历史边界。`GuanyijiaWorkbenchRuntime` 现在是页面唯一 seam：页面只调用 `read`、`previewCurrentConflict` 和 `execute`，不能直接调用 Story、Run 或上传自造 Patch。Workbench 每次从 Run 登记的精确 `documentId + revision` 读取 blocks，以冲突引入时已有的 revision 前缀调用 Story；刷新和 apply 前都会重新投影，不能回退默认 Fixture。

`GuanyijiaStandardizationStory.buildConflictHunk` 以 `sourceId + stableCode + normalizedValue + evidenceRefs` 精确定位 Current、Incoming 和已经读取的 Corroborating block/assertion；`previewConflictResolution` 是 KEEP／ACCEPT／MERGE／DEFER 四种策略的无副作用单一投影，同时生成 result blocks、结构化 Patch、`USER_CONFIRMED` assertions、对象处置、真实 Markdown 行级 diff、provenance 和 SHA。未采纳 claim 只留 provenance；DEMO_POLICY／DERIVED 即使被 ACCEPT 也不改变来源等级。主演示固定为 debt `KEEP_CURRENT`、negative stock `MERGE` 并新增制度未落地 GAP、status 9 `DEFER_AS_GAP` 且不发布枚举成员。

`StandardizationRunRuntime.RESOLVE_SOURCE_CONFLICT` 每次只保存第一项未决冲突的完整 `ConflictResolutionArtifact` 到内容寻址事件。元数据只保存引用和稳定冲突 ID；`resolvedConflictIds` 必须逐项等于已经通过 SHA、Hunk、Preview、Patch/result、block/assertion、真实 diff 与 provenance 复算的 `CONFLICT_RESOLVED` 事件投影。Run 的结构校验不信任 Artifact 自带 Hunk：管伊佳注入的 validator 会读取 Run 登记的每个精确 `documentId + revision`，从其 blocks 重建 Story compilation 与 Preview，并逐字核对完整 Artifact，因此同步伪造 Hunk 后重算整条 SHA／Patch／Diff 链仍在 CAS 前失败。仍有未决项时 source/run 继续 `CONFLICT_BLOCKED`，最后一项才恢复 `READY` 或 `READY_FOR_OUTPUT`。空理由、未知／错来源／重复冲突、陈旧 Hunk 和伪幂等均在 CAS 前失败；两个 Runtime 从同 revision 决定时只有一个胜者。Workbench 先以真实应用时间和命令指纹保存 pending resolution，再提交 Run CAS，最后收尾指针；Run 成功而指针写失败时，同命令重试复用原 Artifact 和 `decidedAt`，不会重复事件。CAS 输家可能留下按 SHA 去重的无引用正文，但不能覆盖胜者。

GitHub 审阅后只出现 debt；官方文档不引入冲突；Policy 审阅后按 negative stock、status 9 顺序逐项阻断。Semantica 不引入或改判冲突，只为已经解决的后两项追加内容寻址 `CONFLICT_CORROBORATED` 回执；加载时回执必须回链时间线上此前已经验证的 resolution 投影，且来源必须是 Story 为该冲突声明的 DERIVED Corroborating source，不能依赖最终 `resolvedConflictIds` 或伪造为其他已读取来源。Receipt 明确绑定该 source 首次 `DOCUMENT_GENERATED` revision：Run 按来源汇总 receipt，管伊佳 validator 沿当前文档 `derivedFromDocumentId` 血缘回到生成 revision 并重建 compilation，要求 receipt conflict ID 列表逐项等于当时的 `corroboratedConflictIds`；后续 `DOCUMENT_REVISED` 不追加、撤回或重写历史 read-time receipt。删除、增加或重复任一回执都会失败；含 receipt 的完成命令必须在 CAS 前同时具备单条来源 validator 与集合 validator，不能先提交一个随后无法读取的状态。主区直接展开 Current／Incoming／Corroborating／Result、四种策略、中文理由、对象处置和红删绿增 diff，唯一 primary 是“应用本项决定”；右侧检查器仍只显示当前 Hunk 的来源身份、authority、snapshot/commit、Evidence locator、lineage 和影响对象。冲突命令不会由自由文本触发，也不创建 deliverable、M4 handoff 或发布。

### 审阅助手（Checkpoint 6 当前合同）

管伊佳审阅助手是 `GuanyijiaWorkbenchRuntime` 的子能力，不是平行 Conversation Runtime。`review-assistant.ts` 只负责 NFKC／空白规范化、固定优先级 intent 和确定性回答投影；来源、冲突、Evidence、影响对象和打开目标都由当前 Run 登记的精确持久化 revision 构建。目标必须属于当前 run allowlist，多目标最多返回 3 个候选。自由文本中的“确认／应用／解决冲突／冻结／交付／M4”只返回边界说明，不执行命令。

`ASK_REVIEW_ASSISTANT` 在一次 Run CAS 中写入内容寻址 `ASSISTANT_TURN_RECORDED`。普通 turn 只包含纯文本回答和 allowlisted targets；Patch turn 只持有 Proposal SHA 引用，完整 Proposal 单独内容寻址。Turn 同时冻结当时已生成来源文档的有序 `{sourceId, documentId, revision}` 前缀；Run 的小型事件索引把该前缀、turnId 和可选 proposal/document 身份绑定到事件序号，因而无需展开历史正文也能拒绝把 Turn 重排到来源生成之前。Story validator 在写入或读取对应分页时从这些历史 revision 重建 compilation 的冲突集，并从 Turn 之前的事件前缀重建 resolved IDs／source status，逐字节核对 intent、回答、targets、Before Block、Task 4 结构化 diff 与 SHA；后续 revision 新增或移除冲突不能污染历史回答。初始历史只展开最近 20 turn 和本页 Proposal，更早内容使用稳定 sequence cursor，页外已确认 Proposal 不会被首屏深读；本页已确认 Proposal 则定向读取其确认 Artifact 和相邻 revision，复算内容引用、身份与 after document 后才显示 `CONFIRMED`。如果当前未确认 Proposal 已落在 20 条之外，接口另返回至多一条轻量 pending card，并以它替换一个普通可见 Turn，仍保持 20 条渲染预算和唯一 primary。只有最新且仍绑定 Run 当前 revision 的未确认 Proposal 是 `PENDING`，旧 revision 或旧建议一律是 `SUPERSEDED`。界面将主审阅滚动区和底部 composer 放在工作台的两行：composer 是普通流内的第二行，不固定、不覆盖，也不要求主区为它预留动态高度。

`CONFIRM_ASSISTANT_PATCH` 不接受 UI 上传 change。Workbench 从 Run 找到最新 Proposal，重读 Before revision，重算 Preview SHA，再用 `SourceDocumentRuntime.revise` 创建相邻不可变 revision。Run 在 CAS 前独立复算 after document 的 blocks／assertions／sections／Markdown、冲突集和 diff 摘要；成功时以同一 CAS 相邻写入 `ASSISTANT_PATCH_CONFIRMED → DOCUMENT_REVISED`。文档已创建但 Run CAS 尚未成功时，pending assistant apply 允许普通解释 ASK 插入，并以当前 Run revision 恢复原 r2；除只读 Preview、普通 ASK 和原 commandId 恢复外，所有可能离开或修改 pending 文档的命令均在 Workbench seam 阻止。Run 已成功但指针收尾失败时按 proposalId 精确恢复，不重复事件或 revision。ASK 本身也先保存 command fingerprint 与确定的 `createdAt`，所以 Run 成功、指针失败后的同 commandId 重试不会因时间变化产生新 payload；如果 ASK 的 Run CAS 已输给另一命令且没有对应 Turn，则旧 expectedRevision 直接报 stale，不能把旧命令改投新事实。ACTIVE 成员可提问和查看 Proposal，只有 Editor／Admin 能明确确认。

`ReviewAssistantPanel` 位于主审阅滚动区之后，发送是普通图标动作。Patch 卡展示当前变更的 Block／Assertion Before／After、以真实增删 hunk 为中心的最多 120 行章节 diff、冲突 title／ID／before→after 和影响对象 ID；待确认时“确认并应用此修改”是唯一 primary。主审阅区是滚动拥有者，panel 是普通流内的第二行，不固定、不覆盖，也不需要动态 bottom padding；用户展开对话时，历史在受限高度的 panel 内滚动，composer 仍留在同一可视工作台内。右侧“来源资料”只提供来源和压缩的业务检查点导航，不显示独立材料正文；当前来源文档目标在打开前记录触发元素和主线 scroll，关闭后恢复；已完成的历史来源只定位并明确称为时间线回执，不伪称打开正文。草稿、滚动和返回焦点保留在 React 临时 UI state，审计真相仍只在 Run 事件。IndexedDB／localStorage 是原型持久化载体，不是抵御本机恶意篡改的安全边界。本合同不解决冲突，不创建 deliverable／M4 handoff，不冻结或发布。

### 管伊佳交付物与标准化文档交接（Checkpoint 7 当前合同）

`src/features/standardization-deliverable/` 是固定资料 Run 完成后的唯一交付 seam，只暴露 `read`、按游标读取内容和 `execute`。它不导入旧 `document-alignment`、旧 bridge runtime 或 Fixture，也不按 `projectId` 选择预制结果。生产 adapter 以 `run.scenarioKey` 注入受保护 V1 descriptor、正式 Catalog V1、`ProjectionContext`、成员权限、SourceDocument／Run／CP5 Artifact readers 与内容寻址存储。Run 的四条原始 `MARK_*` 交付命令另要求由该 adapter 闭包持有的 capability；普通 Run 调用方不能绕过 Deliverable 权限、内容和审批校验直接伪造交付链。

`GENERATE_DELIVERABLE` 只接受 `READY_FOR_OUTPUT`，并要求 canonical 顺序的所有固定来源都回链精确 `documentId + revision + blocks/sections/assertions/Markdown refs`。每个 Run 引入冲突必须且只能有一个已由 CP5 Run validator 验证的完整决定 Artifact。生成内容固定为来源清单、九段合并文档、决定清单和治理附录；REAL 来源才是 formal root，DEMO_POLICY 与 DERIVED 只进入治理说明／佐证。交付时再从完整文档建立 `ModelingEligibility`：已确认且依据充分的结论为 `ELIGIBLE`，登记缺口为 `EXCLUDED_GAP`，无法确定内容为 `EXCLUDED_UNCERTAIN`。三类都写入交付文档，只有第一类可被 M4 编译。

新 `ModelingDocumentArtifact(origin=STANDARDIZATION)` 有独立 deterministic ID、九段 Markdown SHA 与来源批次，不冒充 `artifact-guanyijia-v1-40c8572864bd`。Artifact 与完整文档允许产生可审核的候选差异；标准化阶段只记录这些候选，绝不写回正式 Catalog 或 V1。黄金 Artifact、Markdown、semantic SHA、Catalog identity/fingerprint 或 counts 任一漂移都会 fail closed；治理附录、决定和 Receipt 不写回 semantic payload、正式 Sidecar 或 Catalog。

交付状态按 `GENERATED_AWAITING_AUTHOR → FROZEN → HANDED_OFF` 前进，历史独立审核状态只兼容读取；metadata 在每个状态精确禁止未来字段提前出现，并与 Run 的 READY_FOR_OUTPUT／FROZEN／HANDED_OFF 阶段对账。作者且 Editor/Admin 生成、确认和定版；handoff 只允许作者或 Admin。每次确认、定版、读取 Receipt 和交接都从五份 SourceDocument 当前登记 revision 重新读取 sections、assertions、blocks 与 Markdown；`DOCUMENT_GENERATED/REVISED` 事件冻结该 revision 的五个内容 refs／SHA，Deliverable 再执行 blocks→assertions→sections→Markdown 的完整结构化投影，不能同步替换 metadata 和内容 refs。随后重建唯一来源清单和九段 merged document、CP5 决定、Artifact 与建模资格投影；同步改 merged 正文或资格投影并重算 refs／core 仍会失败。受保护 descriptor 另含固定 Artifact ID，不能把自洽但换 ID 的 Artifact 当作基线。

命令以 canonical fingerprint 幂等并使用独立 IndexedDB 单记录 CAS；内容先写、metadata 再 CAS、Run 事件最后追加。事件追加失败，或事件已追加但最终 `LINKED` metadata CAS 失败，都保留明确 pending `linkState` 和原命令身份。pending 阶段只接受严格的 Run 前态，或带 canonical exact event 的 Run 后态；后态不能仅凭 status／顶层 identity 完成恢复。UI 只显示“恢复交付事件”，以同一 commandId／actor／fingerprint 补登或只完成 metadata 收尾，不能显示下一阶段 Primary。恢复时重新校验当前成员角色，并逐字核对 Run 中已经存在的 event actor／payload 与 deliverable metadata；相同顶层 identity 的伪事件不能劫持 saga。`FROZEN_EVENT_PENDING` 不能交接。Run 还独立验证四事件唯一顺序、actor 与 core／delta／semantic 的逐级回链；事件正文完整携带核心 refs、hashes、审批和 Receipt，不是裸 ID。

`HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING` 生成内容寻址 `STANDARDIZATION_DOCUMENT_HANDOFF` Receipt 并追加 `MODELING_HANDOFF_COMPLETED`。Receipt 绑定 Artifact、完整 Markdown、`ModelingEligibility` 投影、actor 和时间；读路径重读这三者，不能用另一份合法内容寻址 Receipt 串线。M4 接收完整文档，在资料缺口区展示 `EXCLUDED_GAP`／`EXCLUDED_UNCERTAIN`，只把 `ELIGIBLE` 编译为候选；无可建模结论时不创建空草稿。该步骤不修改正式 V1，后续草稿审核／发布才可改变正式模型。历史 `HANDOFF_ZERO_DELTA_TO_M4` 及其 Receipt 仍只读兼容。作者可从交付卡或业务时间线打开定版文档、可建模结论与排除项；metadata 不内联正文。

### 审阅 Surface 与容量窗口（Checkpoint 8 当前合同）

`src/features/review-surface/` 是 Timeline、Document、Conflict、Assistant Patch、Deliverable 与 Inspector 的唯一表现层导航 seam。纯 navigator 只保存稳定 layer identity、anchor、cursor、相对顶部偏移、focus ID 和助手草稿选择区；React adapter 才执行 focus、scroll、body lock、background inert、focus trap 与 polite live announcement。嵌套层按栈逆序关闭，关闭 overlay 时先解除 inert／modal 再恢复底层焦点。Run revision 更新只按显式 allowlist 清除旧 preview；结构化文档 r1→r2 则通过受信 layer migration 保持当前 section、block 与返回锚点。刷新不会恢复 HTMLElement、正文或倒退 revision。

`src/features/review-window/` 是 Timeline、Issues、Evidence summaries、Assistant history、Document blocks 与 Deliverable sections 的统一分页／正文读取 seam。列表返回 summary、contentRef 和 hash；正文只能通过 `readContent` 按需读取并复算 SHA。默认页 20、Timeline 首页 40、单页硬上限 50、前端 window 80 且 overscan 6；淘汰远页以 spacer 和稳定 anchor 保持相对位置。不透明 keyset cursor 以签名绑定 schema、run、epoch、stream、filter、direction 与 stable key，跨查询、跨 revision 或正文修改都会过期。浏览器 Evidence index 使用 IndexedDB adapter；10k／100k 验收使用同 interface 的生成式 index，renderer 不构造全量数组或 Map。

错误恢复仍服从业务 seam：离线只读已校验缓存，未缓存正文只有“重新读取”；checksum 不匹配不返回正文并阻止写动作；cursor 过期重读同 filter 且保留 anchor；CAS 冲突刷新 Run、保留草稿并清除 preview，不自动重放命令；quota 不推进 metadata；freeze pending 只允许原命令继续登记。telemetry 只记录 query、body-read、bytes、duration 与 DOM 数量，不携带正文。

审阅索引属于次级导航能力。索引打开失败时 Workbench 回退到当前运行的受限 summary 投影，页面仍可读取、打开正文并审阅；兼容模式只在技术详情中记录，不显示“metadata”或无效的“重试审阅索引”按钮。只有正文内容校验失败、内容存储无法保存或 quota 才会以人类可读错误阻止对应写操作；若正文在自动打开时就校验失败，文档层仍停留在该来源并显示“正文完整性校验失败”，提供真实的“重新读取文档”校验动作，绝不收回为全局技术错误卡。

管伊佳 Demo 使用仓库内版本化的 `PinnedSourceSnapshotBundle`。Bundle 固定资料的读取摘要、九段结构、识别项、断言、Markdown、证据定位、冲突投影和 Bundle SHA；正常 Demo、刷新、热更新、测试和构建只能读取该 Bundle，再把其内容写入本次运行的来源文档 revision。浏览器 `localStorage` 只是 Bundle 的可丢弃镜像，清空浏览器数据后会从仓库 Bundle 恢复，绝不重新扫描 DDL、代码、制度文档、术语图、网络来源或 LLM。用户修订仍通过来源文档 revision 流程产生新内容，不覆盖基线快照。

只有显式维护命令 `npm run snapshot:guanyijia:refresh` 可以重建 Bundle；它只使用仓库已有冻结输入。任何外部数据库、GitHub、文档站点或 OCR 的重新扫描都需要单独、明确的维护授权，不能由普通编码、页面改动、Demo 操作或失败恢复隐式触发。Bundle 缺失、身份不一致或 SHA 校验失败时 fail closed，显示“演示快照不可用”，不得回源扫描；旧 Bundle 作为版本化文件保留，可比较和回滚。

当前前端 Demo 不再在应用入口阻断第二个浏览器标签；多个标签可以访问同一工作区。底层 metadata CAS、command fingerprint 与 revision 完整性防线继续保留，但“两个浏览器标签同时写同一 StandardizationRun 并证明胜者／输家恢复”不属于本轮 Demo 验收，记录在后续多标签协作 TODO 中。陈旧写入只提示“内容已在另一个标签更新，请刷新当前步骤后重试”。

页面断点按 visual viewport 投影：1440px 为主列加 360–420px inline Inspector，1024px 为右侧 overlay，低于 900px 的 Document／Conflict／Patch／Deliverable／Inspector 使用 `100dvh`（含 `svh` fallback）的全屏子页。移动层用运行时 visual viewport 的 width／height／offset 修正真实 200% 页面缩放，不使用 CSS zoom；safe-area 与虚拟键盘可见区同时进入 composer／Primary 的 bottom padding。每个 modal layer 都有 dialog／aria-modal、focus trap 与 Escape，desktop inline Inspector 不锁焦点。主区任一状态最多一个 `data-workflow-primary=true`，右侧 Inspector 除关闭外无业务动作。

CP8 浏览器验收只通过生产 Workbench 与这些 seam 完成固定资料的连续故事；测试容量 harness 位于独立 `/cp8/` build entry，不进入应用入口或暴露全局后门。Playwright 固定单 worker、无 retry、无条件分支和随机等待；外层 runner 独占 `127.0.0.1:5202`，开始与退出都确认端口关闭。零售继续使用原 legacy M3/domain/runtime，管伊佳黄金 Artifact、SHA、Catalog、counts、draft/V2 状态在故事前后逐项相等。

## 来源语义对齐

`DocumentAlignmentSession`把来源断言投影为稳定 topic。相同结论进入 agreement 并默认收起；值冲突、派生血缘缺口和弱依据进入问题队列。每个问题并列展示来源名称、证据权威、可读主张和按需证据入口，技术标准化值折叠显示。决定必须选择当前问题中的一个真实来源主张并填写理由；最终语义载荷只采用 agreement 与已选择主张，因此决定会改变公式、过滤、时间归属或结构化定义。

问题查询支持关键词、状态和严重级别，默认 20 条并使用不透明游标；Evidence 正文只有点击后才从查询服务读取。UI 不遍历或隐藏渲染全量 Evidence、Claim、Assertion，避免用折叠组件掩盖大 DOM。

## 兼容交接产物（零售与旧资料）

旧 `document-alignment` 的 `StandardizationDeliverable` 仅供零售和历史资料兼容，固定提供两种模式；管伊佳固定资料故事不得进入该路径：

- `SOURCE_DOCUMENT_SET`：保留多份已审阅来源 Markdown，并附带统一语义载荷、决定和溯源清单。
- `MERGED_DOCUMENT`：从相同来源文档和决定生成一份九段式合并 Markdown，同时继续保留全部来源文档。

两种模式都绑定来源文档 SHA、决定 SHA、语义载荷 SHA、Markdown 引用 SHA 和 provenance manifest。作者确认后状态进入`AWAITING_REVIEW`；审核人必须与作者不同，审核通过后 Runtime 分批校验内容并自动冻结。冻结失败保留失败批次和可重试状态；成功产物才可被 M4 读取。

这条独立审核路径属于零售与历史资料的兼容合同。管伊佳资料审阅工作台使用可带资料缺口的标准化文档交付链：作者在完成资料
审阅和治理决定后执行 `AUTHOR_CONFIRM_AND_FREEZE`，Runtime 仍复核来源 revision、决定、内容完整性、引用定位与建模资格；已登记的缺口和无法确定项会保留在 Markdown 中，但被结构化排除于建模候选。它不收集审批意见、不显示审核人、也不要求第二位审核者。历史的审核记录保持只读可见，不做数据迁移。

## 兼容建模 Artifact

`ModelingDocumentArtifact`继续承载旧 M3/M4 累积式故事和手工 Markdown 兼容入口。新的来源文档工作区会在生成 deliverable 前，为 M4 准备一个经过相同完整性校验的 Modeling Artifact；这一步不允许绕过来源文档、对齐决定或 deliverable 审核。

零售经营按 MySQL、GitHub、Semantica、SharePoint、MongoDB、Elasticsearch、MinIO、Kafka 形成 r1-r8。每版只读取当时已经存在的来源；Semantica 在 r3 缺少 SharePoint 上游时记录 Lineage gap，r4 上游到达后清除。三项跨来源决定在一次人工命令中形成 r9。

管伊佳的新正式基线由最新 MySQL 快照、固定 GitHub master Commit 与官方核心文档一次编译为不可变 r1。共享来源中心仍只有 MySQL 与 GitHub 两个已配置连接；官方文档属于该冻结 Artifact 的辅助证据，不伪装成共享连接。未绑定真实 manifest 的 SharePoint 与 Semantica 不进入基线指纹、Evidence、Claim 或 revision。基线的签名语义载荷与 Markdown 同时覆盖实体、事件、字段、关系、维度、指标、层级、规则候选、同义词、时间语义、待归类资产和排除项。

## 审核查询合同

`ReviewQueryService`固定采用“总览 → 问题 → 结论 → 证据”的读取顺序：

- `getOverview`只返回来源、独立根来源、Evidence、Claim、问题、决定和 Lineage gap 计数。
- `listIssues`默认只返回`OPEN`问题，支持状态过滤和不透明游标。
- `getConclusion`返回问题、已记录决定以及根来源级证据计数，不读取证据正文。
- `listEvidence`必须同时指定问题和根来源，先在索引上游标分页，再只向证据加载器请求本页 Evidence ID；页大小限制为 1–100。

Evidence 索引冻结 Evidence ID、来源、根来源、关联问题与规范 Evidence Record Checksum。加载本页证据时，查询服务会校验来源、根来源、对正文与 Locator 独立复算的 SHA-256以及索引 Checksum；普通审阅保持按需读取。只有执行冻结命令时才允许一次读取该 revision 的全部索引证据以完成同样的完整性校验。

M3 页面只通过这个查询服务构建人类审阅工作区。首次打开以及 artifact revision 变化时都并行读取总览与默认 20 条`OPEN`问题，并把视图过滤重置为`OPEN`；当前曾选择`DECIDED/ALL`不能污染下一 revision 的开放问题缓存。页面不遍历 artifact 中的 Evidence、Claim 或 Assertion；选择问题后才读取结论，选择结论中的根来源后才读取该“问题 + 根来源”的 20 条证据页。问题支持状态、严重级别和当前页文本筛选；证据支持根来源过滤和当前页文本搜索，界面始终同时显示“筛选后数量／当前已加载数量／结果总数”。问题“加载更多”和证据“下一页”都使用服务返回的不透明游标，不通过扩大初始页伪装分页。人工决定只能基于已经从查询服务加载的全部开放问题构建；服务仍有下一页时，决定写回保持禁用，页面不能从 artifact 内部快照旁路读取隐藏问题。分页／决定门禁只作用于`AWAITING_CONFIRMATION`；进入`AWAITING_REVIEW`后，允许保留的开放 Warning 不能阻断异人审核、冻结或交接。

容量验收 Fixture 固定构造 10,000 个问题和 100,000 条证据索引。首屏只返回 20 个问题且证据加载器调用数为 0；读取结论仍不触发正文；展开根来源后每个游标页只加载 20 个 Evidence ID。浏览器验收另外约束首屏审核内容 DOM 不超过 200 个后代节点，服务层分页和 UI 渲染预算必须同时成立。

## 标准文档审批与冻结

标准化生成物在当前 Demo 中依次处于`AWAITING_CONFIRMATION → FROZEN`，历史 `AWAITING_REVIEW → APPROVED` 仅为兼容读取。人工决定必须选择问题声明的 resolution；系统记录操作者、时间、Evidence 引用、确定结论和业务摘要，不要求用户填写中文理由。已决定事项、无法确定项和资料缺口继续写入完整 Markdown。每项结论还具有结构化建模资格：`ELIGIBLE` 可交给 M4，`EXCLUDED_GAP` 与 `EXCLUDED_UNCERTAIN` 仅在文档／排除清单中显示。

- 只有生成作者可以记录决定和执行作者确认。
- 审核批准人必须与作者不同，作者确认和审核批准都绑定同一 Markdown SHA-256。
- 冻结重新验证 Markdown、引用与来源内容完整性、每项结论的 Markdown 定位、全部必要来源差异已保存决定，以及任何缺口或无法确定项均未进入可建模候选。资料缺口和无法确定项本身可随文档冻结，必须同时保留明确描述、来源范围和排除原因；无法定位的引用、正文损坏、未决定差异或错误资格投影才会阻止冻结。
- 任一检查失败都保持非冻结状态；冻结后任何章节或决定修改都必须创建新 artifact。

人工上传的受信九段 Markdown 继续作为兼容输入：完成结构和 SHA 校验后登记为不可变`FROZEN` artifact，但同样只能按登记的精确 SHA 进入 M4。

`SemanticReviewBook`是证据审阅导出物，不能导入 M4；`ModelingDocumentArtifact`才是 M3/M4 的正式交接契约。

## M4：AI 建模

M4 接收两类普通冻结输入、当前标准化文档交接和一类历史显式零变化 Receipt：

- 用户上传的真实 Markdown。
- M3 已冻结并显式交接的标准建模文档。
- 管伊佳 `STANDARDIZATION_DOCUMENT_HANDOFF`；携带完整 Markdown 及结构化 `ELIGIBLE / EXCLUDED_GAP / EXCLUDED_UNCERTAIN` 投影，只为 `ELIGIBLE` 生成候选。
- 管伊佳历史 `ZERO_DELTA_MODELING_HANDOFF`；只显示正式 V1 与治理回执，不生成候选、不创建草稿或 V2。

通用解析器按九段契约生成实体、事件、字段、关系、维度和指标候选；带签名语义载荷的标准文档还通过唯一`modeling-document-projector`投影层级、只读规则候选、同义词、时间语义和待归类资产。编译和物化边界先独立复算`markdown.content`的 SHA-256，再从该正文解析 canonical 九段并拒绝与 artifact sections 不一致的输入；随后重编译并逐项比对候选内容、结构化断言和语义载荷 SHA，不能篡改 sections、载荷或仅伪造来源三元组后添加对象。同一 artifact ID + revision + SHA 已经写入草稿时返回原数据而不重复添加对象；Runtime 的交接记录也按 artifact ID + SHA 幂等，并在登记时重复执行内容 SHA 和章节一致性检查。当前标准化交接在物化前应用结构化资格过滤，排除 `EXCLUDED_GAP` 与 `EXCLUDED_UNCERTAIN`；这些内容仍完整保留在文档和资料缺口区。标准化文档一律根据编译候选与签名载荷物化，不允许按`projectId`替换为预制完整 proposal，也不能在运行时回查旧候选 JSON。DDL 能证明结构，但不能凭空证明业务目标、指标口径或规则；不充分内容必须显式为缺口或无法确定项。旧数码产品 Markdown v1–v4 继续通过兼容资料识别，其中 v3 保持质量阻断。

普通冻结文档的候选对象只有在用户点击“加入个人草稿”后才物化；随后由协作 Runtime 执行提交、四眼审核和发布。zero-delta Receipt 不进入该路径。M4 不读取连接器配置，也不直接修改正式 Catalog。

当基线含 Catalog Sidecar V2 时，通用文档物化器先按业务名称对账已有实体、事件、字段、关系、维度和指标，更新既有身份而不把整份文档重复追加。零售 R2 的证据状态从唯一 Registry 重新投影；其他新增对象若没有精确 support edge，一律标记`NEEDS_CONFIRMATION`且 evidence/claim 均为空，不能仅凭 Evidence ID 共现升级为`VERIFIED`。发布门禁要求每个`VERIFIED`对象具有支持 Claim，且每条绑定 Evidence 都确实由这些 Claim 覆盖。该 XOR 与 Catalog 浏览器发布门禁共用，不能出现“模型对象已经加入、持久证据状态仍缺席”的分叉。

## 状态边界

- 来源连接和快照属于协作空间。
- 证据批次和标准文档属于模型项目。
- 文档 Artifact revision 不等于来源 batch revision，也不等于 Catalog version。
- 个人草稿属于用户和模型项目。
- Catalog 是审核发布后的不可变模型版本。

旧 localStorage 键继续只读兼容。建模文档、来源文档元数据和对齐元数据使用独立命名空间；大正文和证据通过内容寻址 IndexedDB 保存，localStorage只保留轻量状态和引用。

## 页面边界

桌面一级导航顺序为：数据标准化、AI 建模、本体建模、指标配置、业务规则、同义词、时间语义。M4 继续使用对话与模型检查器。M3 没有伪对话输入框，所有按钮都必须改变业务状态、导航或展开真实内容。

- `activeSystem === 'guanyijia_erp'` 时，数据标准化页只渲染连续时间线工作台；`group_retail_ops` 和未识别系统继续渲染原有 `LegacyDataStandardizationPage`，其运行与交互契约保持不变。
- 管伊佳 M3 顶部只显示故事名称、`已读取 N/5 · 已审阅 N/5`进度、运行来源次操作、唯一的“来源资料／收起来源资料”资料区控件，以及由当前任务卡承载的唯一主操作。时间线、提示、结果卡与历史详情共用 `width: min(100%, 1080px)` 的内容轨道及 `clamp(14px, 2vw, 24px)` 内边距；普通正文限制在约 72 个汉字可读行宽。右侧来源资料在工作区实际可用宽度至少 1136px 时使用内联约 340–380px 区域，720–1135px 使用 overlay，低于 720px 的文档和检查器使用可关闭全屏子页并恢复主线滚动、章节选择与焦点。内联资料区没有标题或关闭盒；来源行只定位业务检查点。右侧流程压缩为每个来源、每项来源差异和一条标准化结果，详情留在主审阅结论或显式打开的文档中。业务页面不允许横向滚动，只有代码与 DDL 自身可局部滚动。
- 管伊佳当前文档只为两个已验证的剧本任务显示“采用推荐修改”次动作：数据库的 `jsh_depot_head` 业务名称和 GitHub 的负库存业务说明。编辑器只显示本次可改字段，以及确认后会同步变化的审阅清单与 Markdown 段落；来源材料、SQL／DDL、规范值和技术身份仍由内部审计保护而不作为业务文案。预览展示准确的业务名称／说明 Before／After 与 Markdown 行级 diff，应用是卡内唯一 primary；卡打开时隐藏“完成本份审阅”。新 revision 显示在标题和时间线，历史版本入口可查看每个相邻 revision 的实际 Before／After。无 `blocksRef` 的旧文档只显示持久正文与只读提示，不显示修改入口或完成审阅入口。
- 管伊佳审阅助手位于主审阅滚动区之后的第二行，只解释当前 Run 事实、打开 allowlisted 目标或为当前 block 生成真实 Task 4 Preview。发送不是 primary；待确认 Proposal 存在时隐藏完成审阅和其他主动作，只保留“确认并应用此修改”。Textarea 最多 5 行，首屏最近 20 turn；它作为普通流内 composer，不固定、不覆盖。用户展开历史时，历史区域在受限高度内滚动，composer 仍留在可视工作台内；紧凑检查器关闭后恢复草稿、滚动和焦点。
- 零售 legacy M3 继续使用左侧处理进度与“来源文档／来源差异／输出文档”右侧三阶段。证据是来源差异详情的下钻内容，作者确认、独立审核和定版合并在输出文档阶段；1440px 默认展开审阅区，内容宽度不超过 1040px 时改为全屏覆盖层。
- 数字只用于可导航进度：来源文档已审阅数和待处理来源差异数。独立根来源、Evidence 数量、批次 ID 和内容指纹收进批次或技术详情。
- M4 右侧：文档、模型。

从 M3 点击“交给 AI 建模”只完成导航和附件交接，必须由用户再次确认“开始建模”。从 M4 可返回 M3 查看完整证据，而不复制证据管理界面。
