# Task 2 直接测试准备

状态：DIRECT v2、historical paged-v1（含 caller closure 与 `UPSTREAM_UNAVAILABLE`）和 `sourceAnalysis.activityReading` 配置 vertical 均已由 Sol 的具名回归通过；本 worker 接获 Maven 单槽，下一条是 `ActivityReadingCoordinator.coordinate` 的公共输入回归：显式替代超限旧 scope 后，后续 READING_PLAN 输入保留历史记录但不再把旧容量告警列为当前 `unknowns`。Task 1 checkpoint 为 `585c797451412372479e63247e5ec1ceb0c4251d`（root 本地、未推送）。

当前动作：`subsequentReadingInputKeepsHistoricalCapacityDiagnosticsOutOfCurrentSupersededScopeIssues` 的最小生产 GREEN 由 Sol 报告为 session `79576`、1 test、0 failures、0 errors、1:12；本 worker 未并发复跑。随后 root 曾授权集中准备余下回归，但用户把测试所有权转给 Luna/xhigh，现已停止新增测试和 Maven（无运行命令）。保留、未验证的交接改动仅在 `ActivityLargePacketFormalEntryTest`：两处合法空 plan 响应迁至 seven-field v2、`FormalLargePacketProvider`/`ThreeSliceProvider` 的成功 response 带 final keys/finish、及新增 `executionReadingRoundLimitStopsBeforeTheNextSupplementalSelectionAndPersistsTheGap`，通过 `forExecution` 的自定义 `maxReadingRounds=1` 断言实际两次 READING_PLAN、零 DRAFT/REVIEW 和保存 current gap。该测试与其八参数 execution helper 均未编译/运行；严格 config、其余 final-scope negative、另外六类 live fixture迁移尚未写。下一位 Luna 应先审阅并补齐这组 tests，再用 root 分配的隔离 Maven 单槽跑已约定八类定向集合。

## 第一条垂直 RED（最终范围显式替代）

首选扩展 `ActivityReadingCoordinatorGuardrailTest` 的 scripted READING_PLAN 接缝，而不触及配置或生产文件。

场景应录制两次合法 reading decision：

1. 第一轮真实提出一个旧 scope；用稳定的中性 material-view fixture 使该 scope 因容量不足而**未形成可执行 packet**，并留下当前未完成义务。
2. 第二轮将该旧 `sliceKey` 放入 `supersededSlices`，以两个已定义、可执行的新 `replacementSliceKeys` 显式替代；`finalSliceKeys` 只包含这两个替代 scope，并 `finishReading=true`。

最小断言：最终不可变 plan-v2 的有效 slices 仅为两条 replacement；旧 scope 的容量诊断只留在历史 decision，不再出现在 `currentOpenScopeIssues`；协调器不会因已经被显式替代的旧 scope 多调用一次 `READING_PLAN`，也不会将它标为当前不完整。测试只使用 scripted provider 和本地临时 journal。

这条测试刻意不以源码文本、字符串相似、key 的自动消失或新 key 的出现推断替代关系；全部 replacement 均在当前或先前合法定义中存在。同一 `sliceKey` 的修订仍是允许的单独行为，distinct key 不会自动构成替代。

## 实际 RED（2026-09-23）

- 变更仅在 `ActivityReadingCoordinatorGuardrailTest`：新增 `explicitDistinctScopeReplacementClearsOnlyTheSupersededCapacityObligation` 和测试响应 helper；原有同 key 窄修订回归未修改。
- 命令：`mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingCoordinatorGuardrailTest test`。
- 结果：19 tests，1 failure，0 errors。新测试在第一份完整 v2 decision 处失败：`ACTIVITY_READING_PLAN_RESPONSE_INVALID`。现有 v1 exact-set 响应校验尚不接受 `finalSliceKeys`、`supersededSlices`、`finishReading`，所以还没有进入 distinct-key 替换逻辑。
- 该失败经 `assertDoesNotThrow` 明确为行为断言失败，而非测试错误；它证实缺少协议接纳，不能据此宣称 current issue 已清除或 Provider 不会发起第三次 decision。
- Maven slot 已归还 root/Sol。没有真实模型、JDT、Builder、源码重扫、提交或推送。

## v2 fixture migration and current RED

- Sol 的首批协议实现后，新的 distinct-key supersession 用例单测通过；原 Guardrail 的四字段 response fixture 必须迁移至正式七字段 v2，不能以生产 v1 fallback 绕过。迁移后的 named Guardrail baseline 曾为 19/19 green。
- 两条旧私有反射验证已改为公开 `coordinate` 行为：同 key 的 M2→M3 修订，以及同 key 超限修订的当前容量缺口。原 `addSlices`/`rejectOversizedSlices` 测试专用兼容 seam 已无 test caller，Sol 已删除。
- Root 复核后恢复了旧的实质合同：超限 same-key revision 仍必须保留前一可执行 M3 slice，同时记录新 revision 的 `INPUT_CAPACITY_EXCEEDED` current issue 与 `requiredScopeIncomplete=true`。
- 该恢复断言的 actual run 为 19 tests，2 failures，0 errors：
  1. `oversizedSameKeyRevisionRetainsPreviouslyUsableScopeAndRecordsCurrentCapacityIssue` 得到空 slices，未保留 M3；
  2. 原有 `selectingTheSameUnitTwiceDoesNotDuplicateItsFullSavedBody` 保存顺序回归，`selectedUnitKeys` 预期 `[M1,M2]`、实际 `[M2,M1]`。
- 这两项均未改弱，已交 Sol；Maven slot 已释放，等待最小 GREEN 后才进入 saved-plan reopen RED。

## Saved v2 reopen RED

- `ActivityReadingPlanPersistenceTest` 的既有 SelectionProvider 已迁移到正式七字段 v2；原持久化回归现在验证 v2 schema 与 final `slice-validation` key。
- 新 `reopenRejectsMissingOrTamperedV2FinalScopeState` 使用真实两阶段 decision：容量超限的 `scope-too-broad` 被明确 supersede 成两个可执行 scope。保存的 v2 record 实际包含两个 final key、一个 supersession 和空 `currentOpenScopeIssues`，且未修改 record 的 reopen 可用；fixture 不调用 Provider 进行 reopen。
- 命令：`mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingPlanPersistenceTest test`。
- 结果：2 tests，1 failure，0 errors。删除 `finalSliceKeys` 后当前 `reopen` 静默接受，未抛要求的 `ACTIVITY_READING_PLAN_REUSE_INVALID`（test line 76）。之后的 supersession reason 篡改和 `currentOpenScopeIssues` 删除断言未到达，不能说已实测。
- Maven slot 已释放给 Sol。Root 裁决的旧 v1 范围已记录：完整 slices/readingPacket 的分页 v1 可有限重开且保留原 v1 record；缺/坏已声明 packet 必须拒绝；只有 header/sliceKeys 的 DIRECT v1 由 private store 保留至 Task 3，不伪造 v2。

## DIRECT v2 whole-packet roundtrip RED

- 新 `reopensDirectV2PlanWithTheExactOriginalWholePacketIncludingCalls` 用现有 large view 的四个完整方法及 source refs，并只在本测试复制的 packet 加入一个真实 entry-owned call。profile 放大到可直接处理整包；coordinate 和 reopen 的 Provider 均会立即抛出，证明该路径没有模型调用。
- 测试在 re-open 后要求 `modelInputJson` 的 `ImmutableBytes` 逐字节相等，并在输入层确认 M1--M4、S1--S4 以及 call 的 `position`、`navigationPosition`、target argument associations 均在原 DIRECT packet 内。随后删除已保存 whole-packet 的 M4，要求 `ACTIVITY_READING_PLAN_REUSE_INVALID` 且 Provider 调用数为零。
- 命令：`mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingPlanPersistenceTest#reopensDirectV2PlanWithTheExactOriginalWholePacketIncludingCalls test`。
- 实测结果：1 test，0 failures，1 error，22.760s。未篡改的合法 DIRECT v2 record 即在 `ActivityReadingCoordinator.reopen` 以 `ACTIVITY_READING_PLAN_REUSE_INVALID` 失败（`ActivityReadingCoordinator.java:298`），所以 byte-equality 与缺 M4 负例尚未到达；不能声明它们已实测。根因是 reopen 把 whole packet 重建为 `selectedPacket`，后者会压缩实际 call 的导航字段，无法与保存的完整 packet 对应。
- RED 已精确交 Sol/root，Maven slot 已释放。历史 paged-v1 fixture 尚未写入，避免与该 GREEN 同时扩大持久化合同。

## Historical paged-v1 reopen RED

- 新 `reopensACompleteHistoricalPagedV1RecordAndRejectsItsMissingDeclaredPacket` 按 root 核对的实际 13-field v1 形状直接构造一个小型 paged record：`mode=SELECTED`、原有一页 M2/M3 顺序、`selectedUnitKeys`/`unreadUnitKeys`/`unitDispositions`、严格四字段 raw decision，以及六字段 slice 和完整 M1/M2 selected `readingPacket`。它不是从 v2 record 删除字段；fixture 的 M3 仅用作真正超限的导航项，以使原包进入 paged 路径而选择包仍可执行。
- 合法重开要求无 Provider 调用、恢复 `legacy-validation` slice 并逐节点保留原 v1 private record。随后删除这个已声明 `readingPacket`，要求 `ACTIVITY_READING_PLAN_REUSE_INVALID` 和零 Provider 调用。
- 命令：`mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingPlanPersistenceTest#reopensACompleteHistoricalPagedV1RecordAndRejectsItsMissingDeclaredPacket test`。
- 第一次运行在 testCompile 暴露 test-only `JsonNode.path(...).addObject()` 类型错误，已修正；不计作行为 RED。第二次实测为 1 test，0 failures，1 error，3.228s：未篡改的合法 v1 record 在 `ActivityReadingCoordinator.reopen` 的 v2-only schema 门禁（line 232）即以 `ACTIVITY_READING_PLAN_REUSE_INVALID` 拒绝，尚未到 packet/Provider 分支。RED 已交 Sol/root，Maven slot 已释放。
- Sol 随后报告这条具名回归 1/1 green（session `10158`，61s）：有限 strict v1 reopen 保留原 record，不合成 v2 字段；缺 `readingPacket` 仍为 zero-Provider hard failure。该 GREEN 为 Sol 的目标运行，本 worker 未重复并发 Maven。

## `sourceAnalysis.activityReading` configuration RED

- 新 `sourceAnalysisActivityReadingResolvesDefaultsAndCustomLimitsWithoutChangingStep05Basis` 位于既有 `SourceAnalysisModelJobsConfigurationTest`，经实际 `RepositoryRunConfiguration.load` 和 `ConfiguredSourceAnalysisRuntime.execute` 走完整读取路径，不直接调用 `ModelJobsConfiguration.load`。
- 两份真实 Step05 reading-material config 都为 `repository-run-config-v3`：一份缺省 `activityReading`，另一份明确 `{maxNavigationPages: 7, maxReadingRounds: 3, maxSlicesPerPacket: 8}`；本地 marker executable 配置为 Provider。目标断言解析后的 `ActivityReadingProfile` 分别是 128/4/32 和 7/3/8，两个 `baseConfigurationSha256` 相同，`activities` 正常到 `MATERIALS_STATE_INVALID` 且不写 marker。
- 命令：`mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=SourceAnalysisModelJobsConfigurationTest#sourceAnalysisActivityReadingResolvesDefaultsAndCustomLimitsWithoutChangingStep05Basis test`。
- 实测：1 test，1 failure，0 errors，26.795s。第一个 v3 config 在 `RepositoryRunConfiguration.load` 的 `requireText(CONFIG_SCHEMA)`（line 755）被 `CONFIGURATION_INVALID` 拒绝；当前仅接受 config-v2，尚未到 profile/hash/runtime 断言。已交 Sol/root，Maven slot 已释放。
- Sol 随后报告该配置 vertical 的具名 1/1 green（session `55848`，47.023s）；本 worker 未并发复跑。下个执行侧回归仍须证明该 resolved profile 已传入 ActivityExplainer，而非只停留在 runtime 的 materials-state 边界。

## Historical v1 caller-closure follow-up RED

- Root 对实际历史 record 发现：`selectedUnitKeys` 不是仅 entry 加原始 `requestedUnitKeys`；successful slice 的 complete-unit caller closure 也会被保存。现有 v1 fixture 因此升级为 entry→helper→target call chain：raw decision 只选择 target M3，保存的 selection 和 `readingPacket` 则合法地包含 M1 entry、M2 caller helper、M3 target；同时加入 source packet 的真实 `method:upstream` `UPSTREAM_UNAVAILABLE` disposition。
- 同一具名回归命令（3.599s）实测 1 test，0 failures，1 error。合法 record 在 `ActivityReadingCoordinator.requireHistoricalPagedV1` line 369 以 `ACTIVITY_READING_PLAN_REUSE_INVALID` 拒绝，说明当前有限历史核对只容许 entry 加 raw requested，并遗漏 caller closure。已交 Sol/root，Maven slot 已释放；缺 declared `readingPacket` 的 hard/zero-Provider 分支保持在同一测试中但本次未到达。

## 后续负向边界（首条 RED 通过后再逐项添加）

- `finalSliceKeys` 去掉仍有义务的旧 scope、却没有 `supersededSlices` disposition：必须拒绝，不能静默撤回。
- replacement key 未定义、同轮重复定义、重复 disposition 或替代环：完整响应先在临时状态校验失败，不能留下 reading SUCCESS/选择状态。
- 有明确撤回但仍存在未解释范围时，必须保留具体 current issue；`finishReading` 不能把它推成完成。

## 配置测试延后到第二步

配置合同的入口是 `sourceAnalysis.activityReading`，不是仅调用 `ModelJobsConfiguration.load`。届时应通过 `ConfiguredSourceAnalysisRuntime` 的真实配置路径，验证 config-v3 的严格全字段读取和 execution-config-v5 的有效值冻结，同时保留 v2/v4 的显式历史读取。限制值变化不能改变来自 `PersistedTechnicalRunConfiguration` 的既有 Step05 basis，也不能触发 source scan。

### 已确认的实际接缝（仅只读）

- `RepositoryRunConfiguration.load` 是配置组合根；当前仅允许 `sourceAnalysis.javaEngine/jdt/modelJobs/persistence`，且只接受 `repository-run-config-v2`。当前 `ActivityExplainer` 仍硬编码 `ActivityReadingProfile(..., 128, 4, 32)`。
- 首条配置 RED 将扩展 `SourceAnalysisModelJobsConfigurationTest` 的 `configYaml(..., schemaVersion)`、`loadConfiguration(...)` 和 `execute(...)` helpers。`loadConfiguration` 反射调用实际 `RepositoryRunConfiguration.load`；`execute` 调用实际 `ConfiguredSourceAnalysisRuntime.execute`，不直接调用 `ModelJobsConfiguration.load`。
- 两份完整 v3 YAML：缺省 `activityReading` 应解析为 `128/4/32`；custom 采用手工常量 `7/3/8`。两者必须产生相同 `baseConfigurationSha256`，因为 Step05 basis 来自持久的 technical state；custom 的 `activities` 命令应到达既有 `MATERIALS_STATE_INVALID` 停止点而非 `CONFIGURATION_INVALID`，且 fixture provider marker 不出现，证明不触发 source/model 工作。
- v5 execution-record 写入、v2/v4 历史读取、wrong/missing field 和 source-rescan 负例将在首条通过后各自单独 RED，避免本轮把配置合同合成一个大批编译失败测试。
