# Task 3：Codex 式主时间线与事实检查器

## 目标

把管伊佳“数据标准化”从静态状态卡＋右侧大工作区改为真实连续主线。只做管伊佳五源新路径；零售现有流程保持兼容。

```text
主区：运行事件、逐源读取、结构化来源文档、当前任务
右侧：当前选中来源／证据／血缘／影响对象
```

本 Task 不实现 Git 式解决策略、结构化编辑、助手 composer、产物冻结或 M4 交接。它必须让用户真实看见：读取 MySQL、生成 MySQL 文档、完成审阅、读取 GitHub、生成 GitHub 文档、出现第一项冲突。

## 深模块与页面 seam

新增：

```text
src/features/data-standardization/guanyijia-workbench-runtime.ts
src/features/data-standardization/guanyijia-standardization-workbench.tsx
src/features/data-standardization/standardization-timeline.tsx
src/features/data-standardization/standardization-facts-inspector.tsx
```

页面不得直接编排三个 Runtime。新增小接口：

```ts
type GuanyijiaWorkbenchRuntime = {
  read(actorUserId: string): Promise<GuanyijiaWorkbenchSnapshot>;
  execute(command: GuanyijiaWorkbenchCommand): Promise<GuanyijiaWorkbenchSnapshot>;
};
```

命令只包括本 Task 真实支持的动作：`START_RUN`、`READ_NEXT_SOURCE`、`COMPLETE_CURRENT_DOCUMENT_REVIEW`。选中时间线项若仅属 UI 本地态，不必进 Runtime。

Runtime 深化内容：

1. 用 `StandardizationRunRuntime` 保存运行和事件。
2. 用 `GuanyijiaStandardizationStory.compileSource()` 逐源编译。
3. 用 `SourceDocumentRuntime.register()` 保存该来源九段文档。
4. `COMPLETE_SOURCE_DOCUMENT.payload`只保存小摘要与 documentId，不内联 Markdown。
5. 完成审阅先标记来源文档 READY，再推进 run；出现冲突时进入 `CONFLICT_BLOCKED`。
6. snapshot 聚合当前 run、timeline、current source/document、章节摘要、inspector facts、唯一 next action。
7. active run 指针按 project+story version 持久化为稳定 runId；后续只按 runId 读取，不按项目取“最新”。损坏或错项目指针必须给可读错误，不得回落。
8. 命令使用稳定 commandId 与 expectedRevision；重复点击幂等。

运行只绑定管伊佳五源故事版本。不得修改正式 Artifact 或创建模型草稿。

## Workbench Snapshot

至少包含：

```ts
type GuanyijiaWorkbenchSnapshot = {
  run: StandardizationRun | null;
  timeline: WorkbenchTimelineItem[];
  current?: {
    source: StorySource;
    sourceStep: StandardizationSourceStep;
    document?: SourceModelingDocument;
    compilation?: SourceDocumentCompilation;
  };
  nextAction:
    | { type: 'START_RUN'; label: '开始整理五个来源' }
    | { type: 'READ_NEXT_SOURCE'; label: string }
    | { type: 'REVIEW_DOCUMENT'; label: string }
    | { type: 'COMPLETE_REVIEW'; label: '完成本份审阅' }
    | { type: 'RESOLVE_CONFLICT'; label: string }
    | { type: 'NONE'; label: string };
  inspector?: StandardizationFactsInspectorModel;
};
```

Timeline item 是人类可读投影，不暴露 raw JSON；至少区分运行开始、正在读取、读取完成、文档生成、文档审阅完成、发现冲突。读取完成展示真实统计；文档生成展示标题、revision、九章节和 FACT/INFERENCE/GAP/CONFLICT 计数；冲突展示具体标题与影响对象。已完成 item 折叠成紧凑回执，当前 item 展开。

## 页面结构

### 主区

- 页面状态栏只保留故事名称、5源进度和次级“来源设置”。
- 删除管伊佳路径的 6×90ms 假步骤、顶部重复审阅按钮、左侧重复进度链接、右侧三阶段导航和 `SourceDocumentWorkspace` 大工作区。
- 时间线正文 `max-width: 880–920px`，主区承担长内容。
- 当前文档结构化展示九段目录、选中章节，以及按 FACT/INFERENCE/GAP/CONFLICT 分组的 block；默认不显示原始 Markdown 或 Evidence ID。
- 文档超过20 blocks按页20项，不一次渲染63个 pending。
- 每个当前任务卡最多一个主操作。
- “审阅文档”必须选择当前 documentId、打开首个未确认章节、滚动到卡片并聚焦标题；已打开时也要 focus/scroll 并显示 focus ring，不得 no-op。

### 右侧事实检查器

宽度 `clamp(360px, 30vw, 420px)`，只显示来源名称、REAL/DEMO/DERIVED标识、快照/Commit/版本、authority、读取数量、当前 block 的 locator、上游血缘和影响对象。Semantica固定显示“派生来源，不增加独立佐证数”。

不放完整 Markdown、章节编辑器、冲突处理器、产物正文或流程主按钮。

### 响应式

- `>=1280px`：主区＋360–420px inspector。
- `900–1279px`：inspector 使用 overlay；主区全宽。
- `<900px`：文档详情和 inspector 使用全屏子页；关闭恢复主线滚动、当前章节和焦点。
- 本 Task 至少守住现有 1440/1024/390，无横向溢出。

## 兼容策略

- `activeSystem === 'guanyijia_erp'` 时渲染新 Workbench。
- `group_retail_ops` 暂时走现有 legacy implementation，结果与交互不变。
- 建议把现有实现提取为 `LegacyDataStandardizationPage`，不要复制整份逻辑。
- 新路径不得读取或回写旧 final Artifact 来构建来源文档。

## 数据与运行边界

- 五源身份来自 `createGuanyijiaStandardizationStory().listSources()`，不依赖 SourceCenter 当前只含三真实源的 default batch。
- story batchId 由五个 snapshotId 的规范指纹确定；同一故事幂等恢复。
- Demo policy/Semantica 在页面醒目标记，不加入正式 V1 evidenceContext。
- 冲突解决尚未实现时，GitHub审阅后停在 `CONFLICT_BLOCKED`；页面显示“处理1项来源差异（下一Checkpoint实现）”，不能继续。

## TDD

先补 `src/features/data-standardization/guanyijia-workbench-runtime.test.ts`，观察生产 seam 缺失 RED。覆盖：

1. START_RUN创建严格五源运行，黄金 V1无变化。
2. READ_NEXT_SOURCE真实依次产生 STARTED/COMPLETED/GENERATED，只注册一份当前来源文档。
3. MySQL文档包含9段与分页摘要，事件payload不内联Markdown。
4. 完成MySQL审阅后 nextAction 明确为读取GitHub。
5. GitHub审阅后进入CONFLICT_BLOCKED，timeline显示具体欠款字段差异与影响对象，不能继续。
6. 重复点击不重复文档、事件或revision。
7. active run指针损坏或指向别项目时明确报错，不回落。
8. timeline默认不加载Evidence正文、不渲染63 pending全量。

更新或新增页面结构行为测试。不能只用源码正则验证死组件；优先抽取生产消费的 view model/导航状态机。至少覆盖“点击审阅当前文档会设置documentId/section/focus target”。

## 验证

```bash
node --experimental-strip-types --test src/features/data-standardization/guanyijia-workbench-runtime.test.ts src/features/data-standardization/data-standardization.test.ts
npx tsc -p tsconfig.app.json --noEmit
git diff --check
```

更新 `docs/architecture/m3-m4-standardization.md` 与 `docs/plans/task-3-codex-timeline-workbench-report.md`。完成后独立只读审查必须明确 Spec PASS / Quality PASS，Critical/Important为0。

## 禁止范围

- 不修改黄金 V1 JSON、Catalog seed、零售 Fixture。
- 不实现冲突 resolve、文档结构化编辑、assistant composer、deliverable/M4。
- 不删除 legacy runtime；仅让管伊佳新路径不再调用它。
- 不启动或部署远端服务。
