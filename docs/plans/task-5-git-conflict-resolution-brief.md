# Task 5：Git 式来源冲突解决与语义 Patch

## 目标

在管伊佳 Codex 主工作区内逐项解决来源差异，并让每个决定真实生成：

```text
持久化来源文档 revision
→ Current / Incoming / Corroborating Hunk
→ 人工策略与中文理由
→ 结构化语义 Patch
→ USER_CONFIRMED Assertion
→ 真实 Markdown 行级 Diff
→ 内容寻址的决定 Artifact
→ CONFLICT_RESOLVED 事件
→ 继续读取下一来源
```

禁止只把 conflictId 标记为已解决。不得把管伊佳新流程接回旧零售 `document-alignment`，也不得创建第三套冲突 Runtime。

## 单一深模块边界

- 页面唯一调用 `GuanyijiaWorkbenchRuntime`。
- `GuanyijiaStandardizationStory` 只做纯函数 Hunk、策略结果、Patch、Assertion、Diff 和完整性投影。
- `StandardizationRunRuntime` 的内容寻址事件 payload 是唯一持久化决定真相。
- `SourceDocumentRuntime` 提供不可变、已经审阅的来源事实；冲突决定不回写来源文档。
- 旧 `document-alignment` 原字节、Runtime、零售页面和测试保持不变；管伊佳新模块禁止 import 它。

## 公共接口

```ts
type ConflictResolutionStrategy =
  | 'KEEP_CURRENT'
  | 'ACCEPT_INCOMING'
  | 'MERGE'
  | 'DEFER_AS_GAP';

type ConflictHunkSide = {
  role: 'CURRENT' | 'INCOMING' | 'CORROBORATING';
  sourceId: string;
  sourceName: string;
  sourceClass: StorySourceClass;
  authority: StorySourceAuthority;
  documentId: string;
  documentRevision: number;
  block: SourceDocumentBlock;
  assertion: StructuredModelingAssertion;
};

type ConflictHunk = {
  conflictId: string;
  title: string;
  sections: ModelingDocumentSection[];
  current: ConflictHunkSide;
  incoming: ConflictHunkSide;
  corroborating: ConflictHunkSide[];
  affectedObjectRefs: ModelBrowserObjectRef[];
  hunkSha256: string;
};

type ConflictResolutionPreview = {
  hunk: ConflictHunk;
  strategy: ConflictResolutionStrategy;
  result: {
    blocks: SourceDocumentBlock[];
    assertions: StructuredModelingAssertion[];
    objectDispositions: Array<{
      objectRef: ModelBrowserObjectRef;
      disposition:
        | 'KEEP'
        | 'EXCLUDED'
        | 'CANDIDATE_ONLY'
        | 'KEEP_WITHOUT_ENUM_MEMBER'
        | 'DEFERRED';
    }>;
  };
  structuredPatch: SemanticPatch;
  markdownDiff: MarkdownLineDiff[];
  provenanceSources: ConflictProvenanceSource[];
  previewSha256: string;
};

type ConflictResolutionArtifact = ConflictResolutionPreview & {
  schemaVersion: 1;
  resolutionId: string;
  runId: string;
  sourceId: string;
  reason: string;
  actorUserId: string;
  decidedAt: string;
};
```

Workbench 增加：

```ts
previewCurrentConflict(input: {
  runId: string;
  conflictId: string;
  strategy: ConflictResolutionStrategy;
}): Promise<ConflictResolutionPreview>;

execute({
  type: 'RESOLVE_CURRENT_CONFLICT',
  commandId,
  expectedRevision,
  actorUserId,
  conflictId,
  strategy,
  reason,
  expectedHunkSha256,
}): Promise<GuanyijiaWorkbenchSnapshot>;
```

UI 只提交策略、理由和已预览 Hunk SHA，不能上传自造 Patch。Workbench 提交前必须从当前 Run 和持久化来源文档重新生成 Preview 并核对 SHA。

## 冲突定义

冲突定义必须显式声明 `introducedBySourceId`、current、incoming 和 corroborating；每个 variant 通过 `sourceId + stableCode + normalizedValue + evidenceRefs` 精确定位实际文档 block/assertion，禁止依赖数组顺序或默认 Fixture。

```ts
type SemanticConflictDefinition = {
  conflictId: string;
  title: string;
  introducedBySourceId: string;
  current: ConflictVariantDefinition;
  incoming: ConflictVariantDefinition;
  corroborating: ConflictCorroboratorDefinition[];
  sections: ModelingDocumentSection[];
  affectedObjectRefs: ModelBrowserObjectRef[];
  allowedStrategies: ConflictResolutionStrategy[];
  defaultStrategy: ConflictResolutionStrategy;
};
```

- Patch、Result Blocks、Assertions 和 Markdown Diff 必须由同一个纯函数生成并复算完整性。
- 决定后的 assertion 使用 `USER_CONFIRMED`。
- 未采纳的 claim 继续留在 provenance，但不得进入 canonical 结果正文。
- `ACCEPT_INCOMING` 不能把 DEMO_POLICY 或 DERIVED 来源提升为正式生产事实。

## StandardizationRun

以单项命令替换一次性解决全部冲突：

```text
RESOLVE_SOURCE_CONFLICT
CONFLICT_RESOLVED
CONFLICT_CORROBORATED
```

- 每次只解决一项；仍有未决项时 source/run 保持 `CONFLICT_BLOCKED`。
- 最后一项解决后 source 进入 `ALIGNED`，run 进入 `READY` 或 `READY_FOR_OUTPUT`。
- 完整 `ConflictResolutionArtifact` 写入内容寻址事件 payload；元数据只保留事件引用和稳定 ID。
- `resolvedConflictIds` 必须是已校验 resolution 事件的投影；加载时严格核对。
- Semantica 只追加 `CONFLICT_CORROBORATED` 回执，不重新打开或改判已保存决定，不增加独立根来源数。
- 重复、未知、错来源、已解决、空理由、陈旧 Hunk SHA 全部拒绝且不增 revision。
- 同 commandId 相同输入幂等；改策略、理由或 Hunk 报命令指纹冲突。
- 两窗口同 revision 只能一个 CAS 成功；输家不得覆盖胜者。

## 三项主演示决定

1. `gyj-conflict-debt-schema`
   - GitHub 审阅后出现。
   - 采用 `KEEP_CURRENT`。
   - 保留部署库无 `debt/last_debt/last_deposit`；`receivable_debt=EXCLUDED`，订金相关能力保持候选；源码差异只留溯源。

2. `gyj-conflict-negative-stock`
   - 演示制度审阅后出现，两项中的第一项。
   - 采用 `MERGE`。
   - 保留 GitHub“按租户配置控制”的实现事实；新增“统一禁止负库存制度尚未落地”的 GAP；规则继续 `CANDIDATE_ONLY`。

3. `gyj-conflict-status-nine`
   - 演示制度审阅后出现，两项中的第二项。
   - 采用 `DEFER_AS_GAP`。
   - 状态字段保留；不发布状态 9 正式枚举成员；审核规则保持候选并新增待确认 GAP。

官方文档不引入新冲突。Semantica 第五步只佐证后两项，且所有佐证必须回链演示制度文档。

## 主区 UI

冲突阻断时在主时间线直接展开当前 Hunk：

- Current：此前工作标准、来源和权威等级。
- Incoming：新来源结论、来源和权威等级。
- Corroborating：只显示已读取佐证；未读取时明确为空。
- Result：策略生成的结构化结果、受影响对象和真实红／绿行级 Diff。
- 策略选择和中文理由位于主区。
- 唯一主操作为“应用本项决定”。保存第一项后自动定位第二项，最后一项后自动返回下一来源任务。

右检查器只展示来源身份、快照／Commit、Evidence Locator、血缘和影响对象；不得放策略、理由、应用按钮、结果编辑器或完整 Markdown。

## TDD

先逐项观察 RED，再最小 GREEN：

1. 单项 resolve 命令／事件缺失。
2. Policy 第一项解决后错误进入 READY；应继续阻断。
3. Event payload 只有 ID，缺 Patch、Assertion、Diff 和 provenance。
4. 篡改 payload、preview SHA、Assertion、Diff 或 provenance 未被拒绝。
5. 同 commandId 改策略／理由未拒绝；两个 Runtime 同 revision 都成功。
6. 重复、未知、错来源、已解决、空理由、陈旧 Hunk 仍增 revision。
7. GitHub、Policy、Official 的冲突出现时点或集合错误。
8. Semantica 被当作新冲突、可选根结论或独立佐证。
9. Hunk 使用默认 Fixture 而非持久化 revision blocks。
10. Diff 仍是字符数或整段 Before／After，没有 `REMOVE/ADD` 行。
11. 三项固定策略未生成精确 dispositions 和 GAP。
12. 完整五源运行无法到达 `READY_FOR_OUTPUT`。
13. 刷新丢决定、重复决定新增事件或 pointer 回退。
14. 主区同时出现多个 primary，或右检查器出现决策控件。
15. 管伊佳新模块导入／写入 legacy `document-alignment`。
16. 黄金 Artifact ID、两个 SHA、对象计数发生变化。
17. 零售旧对齐、交付和冻结回归。

## 验证

```bash
node --experimental-strip-types --test \
  src/features/source-documents/source-documents.test.ts \
  src/features/standardization-run/standardization-run.test.ts \
  src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts \
  src/features/data-standardization/guanyijia-workbench-runtime.test.ts \
  src/features/data-standardization/data-standardization.test.ts

node --experimental-strip-types --test \
  src/features/document-alignment/document-alignment.test.ts

npx tsc -p tsconfig.app.json --noEmit
git diff --check
```

更新架构、`docs/ui-action-hierarchy.md` 和 `docs/plans/task-5-git-conflict-resolution-report.md`。独立复审必须达到 Spec PASS / Quality PASS，Critical/Important 为 0。

## 不可修改

- 黄金 V1、Catalog Seed、零售 Fixture、旧 `document-alignment` 行为。
- Deliverable、M4、助手、正式发布。
- 备份、远端服务。
