# Task 7：交付物定版、M4 零变化交接与黄金 V1 保护

## 目标

基于真实 `StandardizationRun`、精确来源文档 revisions 和 `ConflictResolutionArtifact`，生成标准化交付物，完成作者确认、独立审核和不可变定版。交给 M4 后，与管伊佳受保护正式 V1 做两层语义比较；固定故事必须显示零模型变化，不创建个人草稿或 V2，只追加资料治理、证据和决定记录。

## 前置硬门禁

- 只接受 CP5 单项、内容寻址、完整性可复算的冲突决定事件；旧批量 `{conflictId}` 不能作为交付依据。
- 不复用旧 `modeling-document-bridge/runtime.ts` 或 `document-alignment` 生成管伊佳交付物；它们的 localStorage、projectId 分支和 Fixture fallback 会绕过真实 run。
- `modeling-document-projector` 的纯投影能力可复用，但所有项目身份和映射通过注入 `ProjectionContext` 提供，不能硬编码管伊佳。
- 零变化交接不能进入现有 `ensureDraft/startModeling/applyCandidate/saveDraft` 路径。

## 深模块

新增 `standardization-deliverable`：

```ts
interface StandardizationDeliverableRuntime {
  read(input: { runId: string; actorUserId: string }): Promise<DeliverableSnapshot>;
  readContent(input: { contentRef: string; actorUserId: string; cursor?: string; limit?: number }): Promise<ContentPage>;
  execute(command: DeliverableCommand): Promise<DeliverableSnapshot>;
}

type DeliverableCommand =
  | { type: 'GENERATE_DELIVERABLE'; commandId: string; runId: string; actorUserId: string; expectedRunRevision: number }
  | { type: 'AUTHOR_CONFIRM'; commandId: string; runId: string; deliverableId: string; actorUserId: string; expectedDeliverableRevision: number }
  | { type: 'REVIEW_AND_FREEZE'; commandId: string; runId: string; deliverableId: string; actorUserId: string; expectedDeliverableRevision: number }
  | { type: 'HANDOFF_ZERO_DELTA_TO_M4'; commandId: string; runId: string; deliverableId: string; actorUserId: string; expectedDeliverableRevision: number };
```

内部 adapter 只依赖 `StandardizationRunReader`、`SourceDocumentReader`、`ConflictResolutionReader`、`ProtectedBaselineProvider`、`ModelingProjector`、`MembershipReader`、clock、crypto 和原子 stores；禁止导入旧 runtime 或 Fixture。

状态仅允许：

```text
GENERATED_AWAITING_AUTHOR
→ AWAITING_INDEPENDENT_REVIEW
→ FROZEN
→ HANDED_OFF
```

另用 `linkState` 表示 run 事件待补登；禁止 update/delete/unfreeze 和手工“标零变化”。

## 确定性生成

`GENERATE_DELIVERABLE` 只接受 `READY_FOR_OUTPUT` run，并从 run 事件读取：

- 五个 run-linked 精确来源文档 identities/revisions/refs，按 canonical source order。
- 每个已引入冲突对应且仅对应一个完整 CP5 决定 Artifact。
- Semantica/演示制度为 non-root/non-formal，不能增加正式根 Evidence。

生成四个不可变内容：

1. `SourceCollectionManifest`
2. 九段 `MergedStandardizationDocument`
3. `ResolutionDecisionManifest`
4. `GovernanceEvidenceAppendix`

CP5 semantic patches 作用于注入的受保护 baseline semantic payload，形成新的 `ModelingDocumentArtifact(origin=STANDARDIZATION)`。新 Artifact 有独立 deterministic ID/Markdown SHA，不冒充黄金 Artifact；固定决定下 semantic payload SHA 必须等于黄金 semantic SHA。

Canonical hash core 只含排序后的业务内容，不含 actor、now 或审批时间。作者确认绑定所有核心 hash；审核定版前重新读取并校验全部 refs、来源 revision、决定、九段结构、lineage、baseline descriptor 和零变化报告。

## 黄金基线

通过 `ProtectedBaselineProvider.resolve(run.scenarioKey)` 注入并 fail closed 校验：

- Artifact：`artifact-guanyijia-v1-40c8572864bd`
- Markdown SHA：`5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210`
- Semantic SHA：`c55c2ebaf3e6cdc92d4ba5115692253a7f6b7e4442eeb44f5c2360a887594248`
- Catalog：`catalog_guanyijia_v1`
- Counts：`14/9/296/30/4/5/2/8/10/9/63/2`

任一身份、SHA、Fingerprint 或 Count 漂移均停止生成、定版和交接。

## 权限、CAS 和可恢复定版

- Read：当前项目 ACTIVE member。
- Generate/author confirm：run author 且角色 Editor/Admin。
- Review：不同于作者的 active Reviewer/Admin。
- Handoff：作者或 active Admin。
- 每条命令在 runtime 重查成员和角色。

Metadata 用 IndexedDB 单记录 CAS；测试 store 使用相同 CAS 语义。commandId+规范化 fingerprint 幂等，改变 payload 拒绝。跨 content、deliverable metadata 和 run event 用可恢复 saga：先写不可变内容，再 CAS record，再 append run event；append 失败设置 pending linkState，同命令重试只补事件，不复制产物或解冻。`FROZEN_EVENT_PENDING` 禁止 handoff。

Run 事件保持：

```text
DELIVERABLE_GENERATED
→ REVIEW_SUBMITTED
→ DELIVERABLE_FROZEN
→ MODELING_HANDOFF_COMPLETED
```

Payload 必须携带 deliverable、内容、审批、zero-delta、receipt refs 与 hashes，不能只有裸 ID。

## M4 两层零变化判定

1. Canonical deep compare 候选与黄金 semantic payload：系统、来源、实体、事件、字段、关系、维度、指标、层级、规则、同义词、时间语义、技术映射、待归类、排除和 Evidence bindings/context；按稳定键排序并输出字段 path diff。只排除新 Artifact 身份、标题、Markdown、审计时间和外置治理附录。
2. 用注入 ProjectionContext 投影到正式 Catalog V1；`deriveDraftObjectChanges(...)` 必须严格为 `[]`，且 Counts 与保护值一致。

任一层非零都禁止定版/交接。禁止直接 clone 黄金 payload 伪造零变化；mutation 测试必须让状态 9、负库存或 debt Patch 变化产生具体 path。

治理、证据和决定只进入不可变 appendix/receipt，不写 Catalog Data、正式 Evidence Sidecar、黄金 Seed 或 semantic payload。

新增显式 `ZeroDeltaModelingHandoffReceipt`，M4 通过 discriminated prop 展示，不复用旧 ModelingDocument start flow。必须以 spy 证明从未调用 `openDraft/saveDraft/submitDraft/publishDraft`，Catalog identity/fingerprint 与草稿数量不变。

## UI

主区每状态唯一 Primary：

- `READY_FOR_OUTPUT`：生成标准化交付物。
- `GENERATED_AWAITING_AUTHOR`：确认内容并提交独立审核。
- `AWAITING_INDEPENDENT_REVIEW`：作者仅等待；合格审核者显示“审核通过并自动定版”。
- `FROZEN`：交给 AI 建模。
- `HANDED_OFF`：无写动作，显示 Receipt 和“查看正式 V1”次级链接。

右检查器只展示 Hash、来源、决定、完整性和审批人，不放业务动作。M4 零变化首屏明确：

```text
语义变化 0
正式 Catalog V1 未修改
未创建个人草稿或 V2
本次仅追加资料治理、证据与决定记录
```

大文档保存在内容寻址 store 并按 section/cursor 读取；窄屏单列，底部仍只保留一个 Primary。

## TDD

先逐项观察 RED：

1. 非 READY_FOR_OUTPUT、缺五来源或精确 revision/hash 仍可生成。
2. 旧 bulk/缺失/重复/篡改冲突决定仍可生成。
3. Demo/Derived 被计为 formal root Evidence。
4. 同输入跨时钟/刷新产生不同 ID/hash/顺序。
5. 九段合并没有准确应用 debt、negative-stock、status9、current-stock-as-of 决定。
6. 新 Artifact 语义 SHA 不等于黄金，或冒充黄金 Artifact ID。
7. 作者确认未绑定所有核心 hashes。
8. 作者自审、错角色、inactive member 可通过。
9. 定版前任一 ref 被篡改未拒绝。
10. command replay、ID collision、并发 CAS 或 saga failpoint 复制产物/解冻。
11. baseline Artifact/SHA/Count/Catalog 漂移未 fail closed。
12. 固定输入两层 delta 非零；语义 mutant 未给具体 path。
13. handoff 创建草稿/V2或修改 Catalog/Seed/Sidecar。
14. receipt 混入正式语义而非仅治理附录。
15. M4 zero-delta 路由仍显示“开始建模/加入个人草稿”。
16. 页面多 Primary、右检查器有动作、大内容未 lazy load。
17. 新模块导入 legacy runtime/Fixture 或按 projectId 分支。

## 验证

仅运行直接覆盖：

```bash
npm run test:standardization-deliverable
npm run test:standardization-run
npm run test:source-documents
node --experimental-strip-types --test src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts
npm run test:modeling-document-projector
npm run test:ai-modeling
npm run test:collaboration
npm run test:data-standardization
npm run test:document-alignment
npm run fixture:check
npm run build
git diff --check
```

旧 `document-alignment` 仅作为零售边界回归。更新架构、README、UI动作契约和 `docs/plans/task-7-deliverable-zero-delta-report.md`；独立 reviewer 必须 Spec PASS / Quality PASS，Critical/Important 0。

## 不可修改

- 黄金 JSON/Fixture、Artifact ID、SHA、Counts、`catalog_guanyijia_v1`、Seed/Fingerprint/正式 Catalog。
- 不创建或发布 Draft/V2。
- 不扩展旧 `document-alignment` 或 legacy bridge runtime，不调用 fixture generator。
- 不把 demo/Semantica 提升为正式根证据。
- 不改 CP1–5 历史 event/hash，只追加 CP7 事件。
- 不改备份、远端服务。
