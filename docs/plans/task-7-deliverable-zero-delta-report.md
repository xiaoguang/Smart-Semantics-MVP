# Task 7：交付物定版、M4 零变化交接与黄金 V1 保护实施报告

## 结论

Checkpoint 7 已把管伊佳真实五源 `StandardizationRun` 收敛为独立的标准化交付物：从精确来源文档 revision 与 CP5 单项决定生成来源清单、九段合并文档、决定清单、治理附录和新的 `ModelingDocumentArtifact(origin=STANDARDIZATION)`，再经过作者确认、异人审核自动定版，最终用显式 `ZERO_DELTA_MODELING_HANDOFF` Receipt 交给 M4。

固定故事对受保护 V1 的 canonical semantic payload 与正式 Catalog 投影均为零变化；正式 `catalog_guanyijia_v1`、黄金 Artifact／Markdown／semantic payload、Counts、Seed 和正式 Sidecar 均未修改，也没有创建个人草稿或 V2。新路径没有复用旧 `document-alignment` 交付物或 `modeling-document-bridge/runtime.ts`，没有按 `projectId` 选择 Fixture 结果。

## 深模块与生成合同

- `src/features/standardization-deliverable/` 是唯一交付 seam，只暴露 `read`、`readContent` 和四条 `execute` 命令。内部 adapter 只读取 Run、SourceDocument、CP5 Artifact、受保护 baseline、ModelingProjector、成员、clock、内容寻址 store 和独立 metadata CAS；Run 的四条原始交付事件命令另要求该 adapter 闭包持有的 capability，普通调用方不能直接伪造交付链。
- `GENERATE_DELIVERABLE` 只接受 `READY_FOR_OUTPUT` 且命令 actor 必须是 Run 作者及 active Editor/Admin。来源按 Story canonical 顺序逐项回链 `documentId + revision + snapshot + sourceType + sections/assertions/blocks/Markdown refs`；少一项、顺序变化、legacy 无 blocks 或 SHA 不一致均 fail closed。
- 每个 Run 引入冲突必须且只能有一个 CP5 `ConflictResolutionArtifact`。生产 adapter 从内容寻址 `CONFLICT_RESOLVED` 事件读取，Run reader 已按持久化 revisions 深校验；Deliverable 再核对 conflict/source/resolved 投影、完整 Preview SHA 和决定清单，旧 bulk、重复、缺失或自洽篡改不能成为交付依据。
- 生成四个不可变内容：`SourceCollectionManifest`、九段 `MergedStandardizationDocument`、`ResolutionDecisionManifest`、`GovernanceEvidenceAppendix`。MySQL、GitHub 与官方文档是 formal roots；演示制度是 non-formal，Semantica 只作 derived corroboration。治理说明和决定只进入 appendix／Receipt，不写入正式语义。
- canonical core 只包含业务 refs 与受保护 descriptor，不包含 actor、clock 或审批时间。同一 Run 在不同时钟生成相同 deliverable ID、core SHA、Artifact ref 和顺序。大内容只存内容寻址正文；`readContent` 按 UTF-8 字节切分最大 4 KiB chunk，不拆 code point，并以稳定 cursor 和最多 1000 chunk/page 按需读取，metadata 不内联正文。

## 两层零变化与黄金保护

- 纯 `createStandardizationModelingProjector` 从 baseline semantic payload 应用 CP5 结构化 object dispositions。固定 debt `KEEP_CURRENT`、negative-stock `MERGE`、status 9 `DEFER_AS_GAP` 保持 semantic SHA `c55c2ebaf3e6cdc92d4ba5115692253a7f6b7e4442eeb44f5c2360a887594248`；alternate debt disposition 会在 `exclusions` 输出具体 path diff。
- 新 Artifact 使用独立 deterministic `artifact-standardization-*` ID、九段 Markdown SHA、真实 Run 来源批次和 `derivedFromArtifactId`，不会冒充 `artifact-guanyijia-v1-40c8572864bd`。Artifact 正文不可变；是否可交接由 Deliverable 审批状态而非正文中的兼容投影标志决定。
- 第一层递归比较完整 semantic payload，对对象 key 与数组稳定排序并输出字段 path；第二层通过显式注入的 `ProjectionContext` 投影到正式 Catalog V1，再以 `deriveDraftObjectChanges` 计算结构化变化。固定故事两层严格为 `[]`，Counts 严格为 `14/9/296/30/4/5/2/8/10/9/63/2`。
- production provider 只在 scenarioKey、独立声明的黄金 Artifact ID、Artifact／Markdown／semantic SHA、Catalog ID／fingerprint 和 Counts 全部匹配时返回 baseline。作者确认、异人审核、Receipt 读取和 handoff 都会将 Run `DOCUMENT_GENERATED/REVISED` 事件冻结的五个内容 refs／SHA 与五源 revision 对账，重新读取 sections／assertions／blocks／Markdown并执行结构化全投影，再从 CP5 决定重建来源清单和九段 merged document，复算 Artifact、baseline 和两层 delta。同步改来源 refs 或 merged 正文并重算全部下游 refs／Artifact／core 仍会失败；任一层非零禁止定版或交接。

## 审批、CAS 与可恢复 Saga

- 状态只能按 `GENERATED_AWAITING_AUTHOR → AWAITING_INDEPENDENT_REVIEW → FROZEN → HANDED_OFF` 前进；没有 update、delete、unfreeze 或手工“标零变化”命令。
- 作者且 active Editor/Admin 才能生成和确认；active Reviewer/Admin 必须异于作者，审核命令完成重验并自动定版；只有作者或 active Admin 可 handoff。Read／正文读取也逐次重查 ACTIVE membership。
- metadata 使用专用 IndexedDB 单记录事务 CAS，不回落 localStorage。命令以 `commandId + canonical fingerprint` 幂等；改变 actor/payload 的同 ID 被拒绝，两个窗口从同 revision 只有一个 CAS 胜者。
- 跨内容、metadata 和 Run 采用可恢复 Saga：先写不可变内容，再 CAS metadata，再追加 Run event。事件追加失败留下 `*_EVENT_PENDING` 与原命令 identity；UI 只给原 actor 一个“恢复交付事件”动作。同 commandId 恢复重新校验当前角色，并逐字核对已存在 Run event 的 actor／payload 与 metadata，不能被同 deliverableId/reviewId 的伪事件劫持，也不复制产物。`FROZEN_EVENT_PENDING` 明确阻止 handoff。
- Run 追加并在加载时验证唯一有序的 `DELIVERABLE_GENERATED → REVIEW_SUBMITTED → DELIVERABLE_FROZEN → MODELING_HANDOFF_COMPLETED`，逐级绑定 author/reviewer、core、delta 与 semantic SHA。事件内容携带完整 core／内容／审批／zero-delta／receipt refs 与 hashes，不是裸 ID。

## M4 与 UI 合同

- `HANDOFF_ZERO_DELTA_TO_M4` 生成内容寻址 `ZeroDeltaModelingHandoffReceipt`，绑定新 Artifact、受保护 Artifact／Catalog、semantic SHA、治理附录和 zero-delta report。M4 通过 discriminated prop 进入专用分支，不进入 `ensureDraft/startModeling/applyCandidate/saveDraft`。
- spy 覆盖证明 zero-delta route 不调用 `openDraft`、`saveDraft`、`submitDraft` 或 `publishDraft`，且保持正式 Catalog object identity／fingerprint 与既有草稿数量。
- 交付阶段隐藏审阅助手和来源修改动作。主区按当前状态最多一个 Primary：生成、作者确认、异人审核自动定版、恢复 pending 事件或交给 AI 建模；等待审核与已交接均无写 Primary。merged、决定、治理附录与 zero-delta 正文通过次级分页入口审阅；右侧事实检查器只显示 refs、hash、来源／决定完整性和审批人。
- M4 首屏明确显示“语义变化 0／正式 Catalog V1 未修改／未创建个人草稿或 V2／本次仅追加资料治理、证据与决定记录”，只提供“查看正式 V1”次级入口。该入口显式显示正式 Catalog inspector，不继续展示新 Artifact；handoff 立即切到 FORMAL/V1，切换模型项目会清除 Artifact／Receipt。窄屏交付区为单列，底部不产生第二个 Primary。

## RED → GREEN 证据

1. 非 `READY_FOR_OUTPUT` 的 Run 首个测试直接失败；加入 generation gate 后仅 ready Run 可进入生成。
2. 真实五源准备完成后，生成测试从缺少模块／命令转绿：四类内容、新 Artifact、三项决定和 formal／non-formal／derived roles 均来自 Run 真实事件与 revisions。
3. 固定策略最初只生成候选资料，没有保护语义的纯投影；加入结构化 disposition projector 后 semantic SHA 等于黄金，两层 delta 为零。把 debt 从 `EXCLUDED` 变成 `KEEP` 的 mutant 输出具体 `exclusions[*]` path 且 SHA 改变。
4. canonical core 初版包含生成时间，跨时钟产生不同 ID；移除 actor／clock／审批字段后，不同时钟得到同一 deliverable ID、core SHA 与 Artifact ref。
5. 作者确认、异人审核、冻结与 handoff 首次无状态 seam；逐条加入命令、角色和状态门禁后，作者自审被拒绝，异人审核自动定版，handoff 生成显式 Receipt。
6. 定版前篡改 `modelingArtifactRef` 正文最初可能只在 M4 读取时暴露；审核命令改为重读全部 refs 并纯重算 Artifact／delta 后，在冻结 CAS 前拒绝且状态保持等待审核。
7. 两个 Runtime 同 revision 并发生成首次都可能继续；metadata 单记录 CAS 后只有一个成功。同 commandId 重放复用原结果，改变 fingerprint 被拒绝。
8. 注入 `DELIVERABLE_GENERATED` Run append 失败后，metadata 首次留下不可恢复中间态；加入 linkState 和同命令恢复后只补登一次事件。冻结 append 失败同样保持 `FROZEN_EVENT_PENDING`，恢复前 handoff 明确拒绝。
9. M4 旧建模入口仍可执行草稿路径；新增 discriminated zero-delta projector 与 UI 分支后，四个 mutation ports 调用数为 0，并删除该分支中的“开始建模／加入个人草稿”Primary。
10. Workbench 初版仍在交付阶段显示助手和来源动作；交付投影接入后隐藏竞争写入口，主区按状态只呈现唯一 Primary，右侧检查器保持只读。
11. 浏览器 metadata 初版可能借用 localStorage；独立 IndexedDB CAS probe 证明缺失／失败时 fail closed，不回落可覆盖的 legacy key。
12. Projector 为避免无 Claim 的 legacy payload 误报曾暂时忽略 Sidecar delta；最终把无受信 Claim 的 binding 规范化为 `NEEDS_CONFIRMATION + empty refs`，并恢复完整 Sidecar 参与第二层 diff。黄金与通用 projector 回归都通过，真实 Sidecar 变化不再被屏蔽。
13. 首轮 M4 回归为 `72/73`：共享数据标准化页丢失既有“互证”契约。reconcile 阶段恢复为“互证来源”后 `73/73`，零售 legacy 页面仍走原工作区。
14. 首轮生产 build 暴露 strip-types 测试不会发现的三个 RED：页面回调漏用 `props`、Artifact deterministic core 误读不存在的顶层 `conflictId`、Run 留有未使用交接 local。逐项从类型定义和数据流定位并最小修复后，app tsc 与 Vite build 转绿；Artifact core 现在明确绑定 `hunk.conflictId`。
15. 读路径自审构造另一 Run 的合法内容寻址 Receipt 并替换 handed-off metadata 引用，初始 `11/12` 仍可读取。`receiptFor` 增加 deterministic receiptId、run／deliverable、artifact hashes、appendix／delta refs 与操作者时间的完整 identity 绑定后 `12/12`，跨 Run Receipt 不能凭自身合法 SHA 串线。
16. 独立 reviewer 以同 commandId 在 actor 降权后恢复 pending freeze，初始仍可补事件；恢复分支加入每条命令的实时角色／异人校验后，Viewer 不能完成旧 Reviewer 命令。生产 UI 同时从 metadata command fingerprint 重建原 pending 命令，隐藏下一阶段 Primary，只允许精确恢复。
17. Reviewer 对 Run 四事件逐项同步换 core／delta／semantic refs 后 reload 曾可接受；Run 加入逐事件 actor 与跨事件 core／delta／semantic 审计链，并以 Deliverable 私有 capability 封闭原始 `MARK_*` 命令后，直接 Run 伪交付在首事件和 CAS 前拒绝。
18. Reviewer 将 Receipt 的 protected/new Artifact identity 与 SHA 全部改为另一组自洽值后仍可读取；读路径改为重读 baseline、实际新 Artifact 和 zero-delta core，逐字段核对 receipt actor/time/refs 后拒绝。
19. M4 “查看正式 V1”最初只改变 viewMode，右栏仍优先展示 standardization Artifact；显式选择 Catalog inspector、handoff 时切 FORMAL/V1、项目切换清理 handoff state 后，正式 V1 路由与项目隔离转绿。
20. 生成后的作者／审核者最初只能看到 refs；主区加入 merged／decision／appendix／delta 四个次级 lazy viewer，并把分页从 UTF-16 字符改为 UTF-8 字节预算。中文 chunk 现在不超过 4096 bytes，跨页拼回原 JSON。
21. Artifact lineage 曾把 `sourceId` 写进 `addedSnapshotIds`；改为 merged document 的真实五个 snapshot identities，并与 SourceManifest／sourceBatch 逐项比对。
22. reviewer 构造同 deliverableId 但伪造 refs/core 的 Run event 劫持 pending generation，初始重试会误判已链接；四阶段恢复现在读取唯一既存 event payload 并与 metadata 的 canonical expected bytes 逐字比较，伪事件保持 pending 且不能完成恢复。
23. reviewer 同步修改九段正文、重算 merged／Artifact／delta／全部 refs 与 core 后，旧审核只从伪造 merged 重投影而可自洽通过；生成与 read/confirm/review/handoff 现在共用五源读取和 deterministic builder。直接整链攻击在作者确认前因 merged 不是持久化五源 revision 的唯一投影而拒绝。
24. handed-off metadata 的审批人或 Receipt actor 单边变化最初仍可读取；所有 LINKED 阶段现在统一以 canonical event payload 对账 generated／author／reviewer／handoff actor、time、core 与 refs，handoff payload 也显式携带 `handedOffBy/At`。单边修改 metadata 或 Receipt 会与 Run 审计链分叉并被拒绝。
25. GovernanceAppendix 初版只比较来源 ID 与 decision refs，任意 notes 可随 core 一起重算；生成与校验改用同一个 exact appendix builder 后，“Semantica 为正式根证据”等替换即使同步更新 Run generated event 和 core 也在作者确认前失败。
26. metadata 初版允许 GENERATED 提前带 author/reviewer 或 FROZEN 提前带 Receipt；状态解析现在精确要求 GENERATED 无审批、AWAITING 仅作者、FROZEN 作者+审核且无 Receipt、HANDED 三者齐全，并核对对应 Run 阶段。
27. SourceDocument 各 ref 过去只各自校验内容地址；Run 文档事件现在冻结当前 revision 的 sections/assertions/blocks/Markdown refs 与 SHA，交付读取再执行共享结构化投影和 Markdown 重渲染。直接测试同步改 SourceDocument metadata、Run event 和 blocks ref 后仍因 blocks/assertions 投影不一致而拒绝。
28. pending 阶段最初只接受 Run event 追加前状态；若 freeze／handoff event 已成功而最终 `LINKED` metadata CAS 失败，reload 会误拒绝并阻断同命令恢复。阶段投影现在接受严格前态或 canonical exact event 后态：freeze 覆盖 `READY_FOR_OUTPUT → FROZEN`，handoff 覆盖 `FROZEN → HANDED_OFF`；两个 metadata failpoint 都能以原 commandId 只完成收尾，而正文不匹配的伪后态继续拒绝。

## 验证边界

最终验证只运行 brief 指定的直接测试、构建、变更文件 oxlint 与 diff／trailing-whitespace 检查；没有运行全仓测试、开发服务器或远端服务。父仓视角整个 `linguan-prototype-v2/` 是既有 untracked 目录，因此 `git diff --check -- linguan-prototype-v2` 不会枚举内部文件，另以定向 trailing-whitespace 扫描补足。

```text
brief 九组直接测试：tests 212 / pass 212 / fail 0 / exit 0
  standardization-deliverable 18/18；standardization-run 56/56；source-documents 12/12
  guanyijia-standardization-story 14/14；modeling-document-projector 4/4
  ai-modeling 73/73；collaboration 22/22；data-standardization 8/8；document-alignment 5/5
npm run fixture:check：exit 0；黄金与零售 Fixture 字节检查通过
npx tsc -b --pretty false：exit 0
npm run build：exit 0；Vite 仅报告既有单 chunk 大于 500 kB 提示
CP7 变更范围 npx oxlint：exit 0；报告 ai-modeling-page 2 条、AppLayout 1 条既有 hooks dependency warning
git diff --check -- linguan-prototype-v2：exit 0（父仓目录为 untracked，不枚举内部）
定向 trailing-whitespace rg：无匹配（rg exit 1）
```

## 独立只读复审

独立 reviewer 已完成多轮攻击审计，并在 READY-3 最终冻结快照逐项确认首轮 13 项以及后续 lifecycle、appendix、future-field、source revision 与 pending 后态恢复问题全部 closure。最终 verdict 为 `Spec PASS / Quality PASS`，Critical 0／Important 0／Minor 0；独立复跑 brief 九组 212/212，fixture、tsc、build、变更范围 oxlint、diff／trailing 检查均符合上述验证边界，只有既有 chunk 提示与 3 条 hooks warning。
