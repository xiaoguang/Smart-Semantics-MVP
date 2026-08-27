# Task 1：标准化运行、事件和逐源状态机

先读 `docs/plans/guanyijia-five-source-codex-review-workbench.md`。本任务只实现 Checkpoint 1，不做 UI、Fixture、冲突编辑器或 M4。

## 目标

在 `src/features/standardization-run/` 新建深模块，成为页面后续唯一的运行编排 seam。使用严格 TDD：先写真实行为测试并观察 RED，再写最小生产代码。

## 公共接口

- `types.ts` 导出 `StandardizationRun`、`StandardizationSourceStep`、`StandardizationTimelineEvent`、`SourceReadSummary`、`StandardizationRunCommand`、`StandardizationRunRuntime`。
- `runtime.ts` 导出 `createStandardizationRunRuntime({ metadataStorage, contentStore, now? })`。
- 元数据键使用 `linguan:standardization-runs:v1`；大 payload 只进入现有 `ContentAddressedStore`，事件仅保存 `payloadRef`。
- 所有读取必须以 `runId` 为边界；不得按 projectId 猜“最新运行”。

## 状态与命令

运行至少支持：`READY`、`READING_SOURCE`、`REVIEWING_DOCUMENT`、`CONFLICT_BLOCKED`、`READY_FOR_OUTPUT`、`FROZEN`、`HANDED_OFF`。

来源步骤固定支持：`PENDING`、`READING`、`DOCUMENT_READY`、`REVIEWED`、`CONFLICT_BLOCKED`、`ALIGNED`。

命令至少支持：

1. `CREATE_RUN`：写入有序 sources；同 `commandId` 幂等；同 projectId+batchId 的重复创建返回同一运行。
2. `START_NEXT_SOURCE`：只允许读取首个 PENDING 来源，写 `SOURCE_READ_STARTED`。
3. `COMPLETE_SOURCE_DOCUMENT`：为当前 READING 来源登记 readSummary、documentId、documentRevision、introducedConflictIds；依次写 `SOURCE_READ_COMPLETED`、`DOCUMENT_GENERATED`；进入 DOCUMENT_READY/REVIEWING_DOCUMENT。
4. `MARK_DOCUMENT_REVIEWED`：无冲突则来源进入 ALIGNED；有冲突则进入 CONFLICT_BLOCKED；写 `DOCUMENT_REVIEWED`，有冲突再写 `CONFLICT_FOUND`。
5. `RESOLVE_SOURCE_CONFLICTS`：必须精确覆盖该步骤全部未解决 conflictIds；进入 ALIGNED并写每项 `CONFLICT_RESOLVED`。
6. `MARK_DELIVERABLE_GENERATED`、`MARK_REVIEW_SUBMITTED`、`MARK_DELIVERABLE_FROZEN`、`MARK_MODELING_HANDOFF_COMPLETED`：为后续 Checkpoint 提供真实事件与状态转换。

每条命令携带 `commandId`、`runId`（CREATE除外）、`expectedRevision`和actor；错误revision、越序、错来源、漏解冲突必须抛出中文可读错误且不写半状态。

## TDD 验收

新增 `standardization-run.test.ts`，至少覆盖：

- 五来源严格顺序，未审阅上一份时不能开始下一份。
- 读完来源后事件顺序是 STARTED→COMPLETED→DOCUMENT_GENERATED。
- 无冲突审阅后可继续；有冲突必须阻断并精确解决后才可继续。
- 重复 commandId 与重复 projectId+batchId 不产生重复运行、revision或事件。
- 两个项目、两个runId完全隔离；读不存在runId不能回落到项目最新值。
- payload能从contentStore按ref读取，metadata不包含大正文。
- 最后一来源ALIGNED后运行进入READY_FOR_OUTPUT；后续四个交付状态事件按顺序合法。

只运行本任务新增测试和 `npx tsc -p tsconfig.app.json --noEmit`，不要运行全仓测试。不要修改现有 Fixture、Catalog或UI。更新相关架构文档当前事实，并把RED/GREEN命令和结果写到 `docs/plans/task-1-standardization-run-report.md`。
