# 代码清理与可扩展活动覆盖设计（APPROVED DESIGN）

> 状态：**APPROVED DESIGN / implementation not started**。用户已批准本文的清理范围，以及“合法但漏项的 DRAFT 进入唯一 REVIEW”和“REVIEW 以 required `unexplainedEntries` 闭合并把具体入口送到第 9 章”两项行为变化。本轮只同步设计，不删除或修改 Java、测试、resource Prompt、配置或 schema JSON，不运行 Maven、产品模型或客户源码扫描，不提交、不推送。

## 1. 结论

本次后续实现应合成两个有先后关系的最小改动：

1. 先把仍被新测试借用的通用 Step05 fixture 移出退休的 `RegistryProposalTaskCompilerTest`，再整体退役旧 registry/proposal/model/process 解释链、旧测试及其专属注册和 Capsule 字段。
2. 在现有 `ActivityExplainer` 内修复多入口覆盖：按每个实际材料的任意正整数 `N` 生成 `E1…EN`，调用前保证输入、输出槽位和 REVIEW 容量相容；合法但漏入口的 DRAFT 进入该材料唯一一次 REVIEW，并携带程序计算的 `missingEntryKeys`；最终 REVIEW 必须覆盖全集，或把未解释入口放进显式 `unexplainedEntries`。

不新建业务流水线、行业规则、证据层、重试器或恢复系统。Step05 仍是代码上下文 owner；Builder 仍是唯一材料包装 owner；模型仍只负责业务理解；集合闭合只证明没有漏记，不证明解释质量合格。

## 2. 已核对事实基线

### 2.1 当前有效链

`BusinessAnalysisWorkflow` 当前只调用：

~~~text
BusinessMaterialBuilder
  → ActivityExplainer
  → org.sourceanalysis.app.analysis.knowledge.ProcessExplainer
  → BusinessReportPublisher
~~~

这是应保留的四个业务 Module。当前有效 Step06 模块地址仍为 `FLOW_INTERPRETATION/10/business-material-builder` 和 `FLOW_INTERPRETATION/11/activity-explainer`；清理旧 1–9 不等于把 10/11 改号。

`ModelRuntimeIdentityV1` 仍被 `StructuredModelResponse` 和 `CodexSubscriptionStructuredProvider` 使用，必须保留。`analysis.interpretation.process` 是退休实现；当前 ProcessExplainer 位于 `analysis.knowledge`，两者不可混淆。

### 2.2 旧实现规模与隔离性

当前旧生产包均在 `org.sourceanalysis.app.analysis.interpretation` 下：

| 待退役包 | Java 文件 | 行数 | 当前有效工作流直接引用 |
| --- | ---: | ---: | --- |
| `model` | 19 | 2,749 | 无 |
| `proposal` | 16 | 2,617 | 无 |
| `registry` | 7 | 1,070 | 无 |
| `process` | 36 | 6,891 | 无 |
| 合计 | 78 | 13,327 | 无 |

对应四个旧测试包共 14 个测试文件、7,180 行。它们验证 R0/R1/R2、registry freeze、旧 cross-flow 与旧 process task/checkpoint，不是四个当前业务 Module 的目标行为。

旧包仍通过三处共享合同留下维护负担：

- `AnalysisStepModuleAddress` 还注册 Step06 的 1–9 以及当前 10–11。
- `AtomicCanonicalPublicationEngine` 仍识别旧 module 文件集合和旧 artifact/schema 分支。
- `CapsuleProjection.EvidenceCapsule`、`EvidenceCapsuleProjector`、`CapsuleProjectionModulePublisher` 仍携带 `registryProposalBasisAtomIds`、`registryProposalBasisGapIds`；新 `BusinessMaterialBuilder` 不读取这两个字段，旧 proposal/process 消费者仍读取。

因此不能先从共享 Capsule 中删字段再留下旧消费者，也不能整删 Capsule 共享文件。

### 2.3 新测试对旧测试类的偶然依赖

14 个当前测试只借用 `RegistryProposalTaskCompilerTest.publishBusinessFlows(...)` 等通用发布 fixture；这不是产品依赖：

- `BusinessReportCheckpointTest`
- `ActivityExplainerDirectEntryContextTest`
- `ActivityExplainerTest`
- `ActivityExplanationBudgetTest`
- `ActivityExplanationCheckpointTest`
- `ActivityOutputSchemaTest`
- `ActivityPromptContractTest`
- `BusinessMaterialBuilderFallbackTest`
- `BusinessMaterialBuilderReplenishmentTest`
- `BusinessMaterialBuilderTest`
- `BusinessMaterialBuilderZeroEntryTest`
- `ProcessKnowledgeCheckpointTest`
- `PersistedBusinessRunExecutorTest`
- `RepositoryAnalysisRunCoordinatorTest`

第 15 个外部引用者 `BusinessFlowCoverageTest` 还有一段直接编译 R0 registry proposal task 的旧断言；同文件前半的 Flow/Capsule 覆盖、预算 Gap 与完整 Capsule 断言仍有价值。

### 2.4 E1–E4 真实失败

保存的 `.workspace/live-luna-automatic-user-account-group-v1/01-ACTIVITY_DRAFT-input.json` 是一个四入口包：

| 局部 key | HTTP 入口 | ref | 源码可安全表述的行为 |
| --- | --- | --- | --- |
| E1 | `DELETE /user/delete` | S487 | 接收用户标识，调用删除处理并包装返回；不证明实际删除成功 |
| E2 | `GET /user/getUserSession` | S722 | 从会话取用户标识、查询用户、清空密码字段；正常/异常分别构造返回 |
| E3 | `POST /user/registerUser` | S731 | 建立标准成功返回，复制登录名、校验验证码和登录名、发起注册调用；不证明数据库注册成功，也不据 `manageRoleId` 猜岗位 |
| E4 | `GET /user/logout` | S898 | 在 try 中发起清理 `userId`、`clientIp` 会话项，异常时返回退出失败；不证明 Redis 已实际删除 |

实际 DRAFT response 只有 A1/E1、A2/E2。`LiveLunaAutomaticMaterialIT` 使用 `ActivityExplanationProfile(20000, 12000, 2, 24, 1000)`；输出 schema 的 `activities.maxItems` 因而为 2，而材料实际 `N=4`。Prompt 同时要求所有 key 都被覆盖。

Schema 不是逻辑上绝对矛盾：一个活动允许覆盖多个 key；但配置没有保证“每个入口各自表达”这一合法最坏形状。`ActivityExplainer.validateResponse` 又在 DRAFT 后、REVIEW 前要求已覆盖 key 集等于全集，所以该次 response 直接以 DRAFT invalid 终止，REVIEW 未启动。

这次已开始并失败的产品调用不能被重命名为可重放请求。本文示例中的修复结果都是未来新执行的验收期望，不是原 response，也不是兼容读取旧 response。

### 2.5 当前持久化与下游缺口

`ActivityExplainer` 和 `ProcessExplainer` 都在循环全部结束后才 publish。设计要求的“每包 REVIEW 完成立即保存”尚未实现；把 publisher 直接移进循环会反复安装同一 module 地址并因不同内容发生 collision，所以它是独立持久化缺口，不是本次覆盖修复中的一行移动。

当前 `ProcessExplainer.repositoryInput` 与 `BusinessReportPublisher.cleanKnowledge` 只向模型投影 NOT_ANALYZED 数量，不投影具体入口及原因。若接受本文的显式未解释入口合同，必须同步下游，不能在 activity coverage 保存后又从第九章静默丢失。

## 3. 范围与保持不变项

### 3.1 本设计包含

- 退役四个旧解释包及其旧测试、专属 module/artifact/schema 注册。
- 先迁移通用测试 fixture，再删除旧测试类。
- 有限清理 Capsule 的两个 registry-proposal 专属字段，并升级真正受影响的 owning schemas/readers。
- 修复任意 `N` 的局部 key、容量预检、DRAFT 缺口交 REVIEW、最终完整或显式未解释。
- 将未解释入口从 Activity coverage 传到 Process/Report 输入和九章第 9 章。
- 为 E1–E4、跨包 N=9、单包 N≥12、零入口、超预算、非法返回和 REVIEW 仍遗漏定义定向验收。

### 3.2 明确保留

- 八步骤、固定 keys、四个深业务 Module、唯一 `RepositoryAnalysisAgent` 公共接口。
- Step01–05 技术产物、五图、严格 Fact/Proof、真实 source/ref/basis/hash 核验与原子安装。
- Step05 `EntryContext` 和 Capsule；Builder 只包装，不扫描 Java、不重建图、不猜调用。
- 每个 activity/process/report 任务最多一 DRAFT 加一完整 REVIEW；started 后 transport/schema/runtime 失败仍 fatal。
- scope-local key 与 global entry ID 分工、活动与入口多对多、活动与过程多对多。
- 当前 10/11 模块地址与现有 `activityLocalId` 等有效命名；不为“看起来连续”改持久化身份。
- 历史 artifacts、Git history 和 progress 文档；新版本不覆盖旧版本，也不承诺跨版本 canonical bytes/hash 相同。

### 3.3 明确排除

- 自动重试、Provider switch、API-key fallback、第三次修复调用、旧失败 replay、same-run takeover。
- 新 journal、逐记录状态机、修复账本、复杂恢复或第二套 publication 协议。
- Java 业务词典、语义质量关键词判定、每入口必须恰好一个活动。
- 本轮真实 Provider、客户仓库重扫、客户 Maven/应用、全仓测试或 POM 生命周期重做。
- 删除历史 worktree、构建输出或任何未先确认的磁盘目录。

### 3.4 四个深 Module 的实施接口

| Module | 输入 | 输出 | 唯一职责 | 失败与停止 | 直接下游 | Luna/xhigh RED | Terra/xhigh GREEN |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `BusinessMaterialBuilder` | 同源 Step05 EntryContext/Capsule、全入口 coverage、材料 profile | `BusinessMaterialSet`、SourceRef 映射、入口到材料处置 | 选择完整预算单元、按入口边界分包并生成包内 E1…EN；不重建图或猜业务 | 来源/ref/身份损坏 fatal；单入口无法成包记具体 NOT_ANALYZED；不调用 Provider | `ActivityExplainer`，必要材料供 Step07 回查 | 任意 K 分包、global/local 映射、noFlow、预算与零入口 | 只在现有 Builder seam 修复真实失败，不新增扫描器或包装链 |
| `ActivityExplainer` | 一个完整 material、scope-local key/ref allowlist、相容 Activity profile | 完整 `ReviewedActivity`、entry coverage、程序侧 `UnexplainedActivityEntry` | 一次 DRAFT + 一次完整 REVIEW；允许仅 coverage 不足的合法 DRAFT 进入 REVIEW并最终闭合 | 调用前容量不足零请求；非法 JSON/key/ref 或 started 失败 fatal；REVIEW 仍漏项 fatal，无第三次调用 | `ProcessExplainer`、Step08 coverage | N=4 漏 E3/E4、N≥12、跨包 E1、真实 REVIEW 预算、union/disjoint | 拆分结构校验与最终闭合，落地 v2 Prompt/schema 及最小 sidecar |
| `ProcessExplainer` | 全部已审活动、召回线索、必要材料、按 material 聚合的未解释入口 | 已审过程、`RepositoryBusinessKnowledge`、process coverage | 宽松召回后解释有依据的多对多过程，保留独立活动与未解释范围 | 非法成员/ref/JSON、started 失败 fatal；0 活动零过程调用；不能把 partial 说成完成 | `BusinessReportPublisher` | 完整活动字段、多对多、保守独立过程、具体 partial 投影 | 只扩现有 knowledge 输入/保存/read seam，不改变其他过程 validator |
| `BusinessReportPublisher` | 已审 knowledge、完整活动/过程、coverage、按 material 聚合的未解释入口、SourceRefs | 九章 JSON、source refs、Markdown、validation | 一次报告 DRAFT + 完整 REVIEW，Java 仅校验并排版固定九章 | 缺章/非法 ref/虚假全量 fatal；显式空仓报告仍调用既有 DRAFT+REVIEW；PARTIAL/INCOMPLETE 不是新 runtime enum | 业务读者及 `render/inspect/artifact` | 第4章完整内容、第9章具体未解释入口、固定九章、纯 render | 仅补 knowledge→report 输入和第9章合同，不新增语义 parser 或空报告捷径 |

表中 Activity/Process/Report 的新覆盖字段与 v2 Prompt 都是已批准目标，尚未写入当前 Java、resource、Schema 或已有产物。Luna RED 和 Terra GREEN 是后续另行授权实施的交接，不是本次文档同步已执行的测试或代码结果。

## 4. 清理设计与依赖迁移顺序

清理必须按以下顺序实施；每一阶段的直接测试通过后再进入下一阶段。

### 4.1 先建立中性 testsupport

新增 `src/test/java/org/sourceanalysis/app/testsupport/BusinessFlowTestSupport.java`，从 `RegistryProposalTaskCompilerTest` 搬出并直接 typed 调用：

- `publishBusinessFlows(ProgramGraphsPublicFixture)` 及需要 profile 的 overload；
- `publishProvenFacts(...)`；
- `flowProfile()`、`capsuleProfile(...)`；
- `reference(...)`。

先将上述 14 个当前测试改为引用该 support。只是 fixture 搬家，必须保持其 Flow/Facts/SourceRef/Graph 构造和断言语义；不得把旧 registry proposal 行为一起搬过去。反射 `Class.forName`/constructor 探测可在触及的当前测试中改成 typed 构造，但必须保留 Provider 可注入、DRAFT→REVIEW 次序、非法 ref fatal 等行为断言。

### 4.2 拆掉 `BusinessFlowCoverageTest` 的 R0 尾巴

保留该测试当前关于两条 Flow、Capsule 完整性、eligible/ineligible 分区、预算 Gap 和 span/obligation closure 的断言。删除只构造 `RegistryProposalTaskProfile`、`RegistryProposalTaskCompiler` 并断言 `R0_REGISTRY_PROPOSAL` task 的尾段，以及只为它服务的 imports、`modelCapsuleView` helper。

测试方法名改为描述“预算下仍保存全部 Capsule 并准确记录模型资格”，不再含 `R0UsesOnly...`。这不是删覆盖，而是把覆盖断言停在 Step05 owner 边界。

### 4.3 原子退役旧生产链与旧测试

删除下列包中的全部生产类，不留 alias、bridge 或 compatibility reader：

- `analysis.interpretation.model`：`FiniteKeyFlowTaskCompiler`、`FlowInterpretationCandidate`、`FlowInterpretationDisposition`、`FlowInterpretationIdentity`、`FlowModelProvider`、`FlowModelProviderResponse`、`FlowModelTask`、`FlowModelTaskException`、`FlowModelTaskProfile`、`FlowModelTaskSet`、`FlowModelTaskSetModulePublisher`、`FlowModelTaskShardReceipt`、`GenerationReceipt`、`InterpretationExecutionSet`、`InterpretationExecutionSetModulePublisher`、`InterpretationProposal`、`InterpretationRunner`、`ModelRound`、`ModelTaskDisposition`。
- `analysis.interpretation.proposal`：`BusinessRegistryProposal`、`RegistryProposalExecutionSet`、`RegistryProposalExecutionSetModulePublisher`、`RegistryProposalFlowDisposition`、`RegistryProposalGenerationReceipt`、`RegistryProposalProvider`、`RegistryProposalProviderResponse`、`RegistryProposalRound`、`RegistryProposalRunner`、`RegistryProposalTask`、`RegistryProposalTaskCompilationException`、`RegistryProposalTaskCompiler`、`RegistryProposalTaskProfile`、`RegistryProposalTaskSet`、`RegistryProposalTaskSetModulePublisher`、`RegistryProposalTaskShardReceipt`。
- `analysis.interpretation.registry`：`RegistryFreezeException`、`RegistryProposalAccounting`、`RepositoryInterpretationRegistry`、`RepositoryInterpretationRegistryFlowDisposition`、`RepositoryInterpretationRegistryFreezer`、`RepositoryInterpretationRegistryItem`、`RepositoryInterpretationRegistryModulePublisher`。
- `analysis.interpretation.process`：该目录全部 36 个类，即 `BusinessProcessInterpretationException/ExecutionPublisher/ExecutionRequest/ModulePublisher/Request/Result/Runner`，`BusinessProcessTaskCompilation/CompilationException/Compiler/ModulePublisher/ShardV1`，`CrossFlowCandidateAccountingV1/Compilation/CompilationException/CompilationRequest/Compiler/ModulePublisher`，`ProcessCandidateRelationV2`、`ProcessCounterScopeIssueV1`、`ProcessEvidenceGroupV2`、`ProcessInputArtifactReader`、`ProcessInterpretationGapV1`、`ProcessMaterialLimitsV1`、`ProcessModelPacketV1`、`ProcessModelProvider/Request/Response`、`ProcessPersistedFlowViewV1`、`ProcessPersistedMaterialV1`、`ProcessRegistryItemViewV1`、`ProcessRelationCounterBasisV1`、`ProcessRelationPositivePairBasisV1`、`ProcessSemanticCueV1`、`ReaderKeyBindingV1`、`UpstreamFlowGapProjectionV1`。当前 `analysis.knowledge.ProcessExplainer` 不在删除范围。

同步删除四个对应旧测试目录中的 14 个测试文件。删除依据是目标接口已退休，不是“测试难维护”；其中仍有价值的 Step05 fixture 已在 4.1 搬走，Flow coverage 已在 4.2 保留。

在同一原子变更中：

- `AnalysisStepModuleAddress` 删除 Step06 旧 1–9 注册，只保留 10/11。
- `AtomicCanonicalPublicationEngine` 删除旧 1–9 文件集合和 registry proposal、registry、flow task、old process task/checkpoint、old model execution artifact/schema 分支；保留 material/activity、Step05、Step07、Step08 分支。
- 扫描 active `src/main` 与非历史 `src/test`，不得再有上述旧包 import 或旧 artifact type 的运行引用。

历史 docs/progress/artifacts 中的名称不批量改写；它们不是活动兼容入口。

### 4.4 最后移除 Capsule 的旧专属字段

只有 4.3 的旧 proposal/process 消费者全部消失后，才从以下共享位置移除 `registryProposalBasisAtomIds`、`registryProposalBasisGapIds`：

- `CapsuleProjection.EvidenceCapsule` 主/兼容构造；
- `EvidenceCapsuleProjector` 的赋值；
- `CapsuleProjectionModulePublisher` 的 JSON；
- `CapsuleProjectionModulePublisherTest`、`EvidenceCapsuleProjectorTest` 对这两个字段的旧断言。

新 Builder 实际读取的是 `factViews.atoms`、`gapViews` 和 `processJoinSignals`，所以这里不删除新材料所需信息。`CapsuleProjection`、projector、publisher 文件本身保留。

该 wire 变化只升级真正受影响的 `capsule-projection` 与 `evidence-capsule` schema（按当前 v8/v6 的下一版本 v9/v7），同步 `AtomicCanonicalPublicationEngine` 和直接 readers。旧磁盘产物仍按旧身份留存，不由新 reader 假装成新格式。

### 4.5 有限命名整理边界

允许：把通用 fixture 命名为 `BusinessFlowTestSupport`；把当前测试名中的 R0/registry proposal 改为它真正验证的 Flow/Capsule 行为；删除上述两个旧字段名。

不允许：把 module 10/11 重排为 1/2；重命名四个当前业务 Module；把 Activity 内部 DRAFT/REVIEW 改叫产品 Round 1/2；给 E1–EN 加全局含义；用新包名包住旧代码继续运行。

## 5. 任意 N 入口的活动覆盖合同

### 5.1 身份与集合

对每个实际 `BusinessMaterial material`：

~~~text
globalEntries = material.entryIds() 的稳定有序列表
N             = globalEntries.size()
localKey(i)   = "E" + (i + 1), i ∈ [0, N)
localToGlobal = 本次 material 调用内的 (localKey → globalEntryId)
~~~

`N` 不写死为 4，也不从仓库总入口数推导。`BusinessMaterialProfile` 三参数构造器的默认 `maxEntriesPerMaterial=4` 只是一个可覆盖配置；不同 profile 可形成任意正数上限的包。

局部 key 每包从 E1 重新开始。程序绝不能用裸 `E1` 跨包 join，也不能按字符串前缀、substring 或词典序猜序号；持久覆盖始终映射回 global entry ID。`E10`、`E11`、`E12` 是普通完整 key，不得与 E1 混淆。

一个活动必须有至少一个 key，可覆盖多个 key；一个 key也可被多个有依据的活动覆盖。最终活动数可以小于、等于或大于入口数，但受 profile 限制。容量保证“允许每入口单独表达”，不等于强制一入口一活动。

### 5.2 调用前容量与预算闭环

对每个尚未受 `maxMaterialsToStart` 排除的材料，按以下顺序预检，任何失败均为该材料零 Provider 请求并写全入口 NOT_ANALYZED：

1. `N > 0`；零入口不会构造 BusinessMaterial，也不会调用 Activity Provider。
2. `profile.maxActivitiesPerMaterial >= N`，保证 N 个独立活动可表达。
3. `profile.maxValuesPerField >= N`，保证一个合法活动也能覆盖任意子集直至全集。
4. 用当前 schema 的最小非空字段和一个 allowlisted ref 构造 N 个独立活动的最小合法 REVIEW skeleton；其 canonical bytes 不得超过 `maxModelOutputBytes`。
5. `cleanPacket` 实际 canonical bytes 不得超过 `maxModelInputBytes`。
6. 在首次调用前为 REVIEW 预留：`cleanPacket bytes + maxModelOutputBytes + actualDraft/missingEntryKeys canonical envelope overhead(N)` 不得超过 `maxModelInputBytes`。

第 2–4 项失败使用具体 reason `NOT_ANALYZED_ACTIVITY_OUTPUT_CAPACITY`；第 5–6 项使用 `NOT_ANALYZED_BUDGET` 或更具体的 `NOT_ANALYZED_REVIEW_INPUT_BUDGET`。沿用 `ActivityEntryCoverage` 的 `NOT_ANALYZED + reasonCode`，不新增 disposition enum。

DRAFT 返回后仍须把真实 `actualDraft` 和 `missingEntryKeys` 装入 REVIEW packet 后重新 canonical serialize，并精确检查实际 bytes。前置上界成立却实际超限表示实现的预算不变量损坏，应 fatal；不能在已开始 DRAFT 后伪装成普通零请求容量缺口。

已有材料若 `N` 大于 Activity profile 的表达能力，`ActivityExplainer` 不临时拆材料、不裁源码。调用方应使用相容 profile，或在下一次材料规划中由 Builder 按入口边界产生新材料；Builder 仍是唯一包装 owner。

### 5.3 DRAFT、唯一 REVIEW 与最终闭合

把当前 validator 分成“响应结构/范围有效”与“最终覆盖闭合”两层：

- DRAFT：JSON、完整字段、文本/数组限制、local ID 唯一、key/ref allowlist 都必须有效；越界 ref、未知 key、非法 JSON、超输出 bytes 仍立即 fatal。仅 `coveredKeys != expectedKeys` 不再在 REVIEW 前 fatal。
- 程序计算 `missingEntryKeys = expectedKeys − draftCoveredKeys`，把完整真实 `actualDraft` 与该列表加入唯一一次 REVIEW 输入。它是诊断/修订提示，不是第三轮任务。
- REVIEW：重复全部结构/范围校验，再要求 `coveredKeys ∪ unexplainedEntries = expectedKeys` 且 `coveredKeys ∩ unexplainedEntries = ∅`；`unexplainedEntries` 内 key 唯一。
- REVIEW 若仍从两边漏 key、返回越界 key/ref 或非法结构，`ACTIVITY_REVIEW_INVALID` fatal；不得发第三次请求。
- REVIEW 可删除无依据活动并把相应 key 显式标为未解释。这样覆盖账可闭合但 semantic delivery 为 PARTIAL，不能作为该真实样本的功能验收成功。

内部 Activity DRAFT+REVIEW 仍只产生一个 Reader Candidate。产品 Candidate Round 2 仍需针对已命名问题另行授权；本修复不会自动生成 Round 2，也不会重放旧 E1–E4 失败。

## 6. 已批准的最小 `unexplainedEntries` 协议

> **批准的目标变化：**现行“DRAFT 在 REVIEW 前必须全覆盖”改为“结构与范围合法但 coverage 不足时进入唯一 REVIEW”；REVIEW output 增加 required `unexplainedEntries`。设计已批准，代码与 wire 尚未实施。

为避免模型自由文字被误当技术 Gap，DRAFT output 仍为 `{ "activities": [...] }`；REVIEW output 改为闭合对象：

~~~json
{
  "activities": ["完整活动对象，结构沿用当前合同"],
  "unexplainedEntries": ["E3", "E4"]
}
~~~

模型 REVIEW 响应字段 `unexplainedEntries` 是 required 的 scope-local key 数组：允许 `[]`，禁止 null 或省略，`uniqueItems=true`、`maxItems=N`，items enum 来自该包实际 `E1…EN`。不让模型返回 reasonCode 或自由原因；程序对每项固定映射：

~~~text
ActivityEntryCoverage(entryId, "NOT_ANALYZED", [], "MODEL_NOT_EXPLAINED")
~~~

新增一个程序侧值类型，冻结为：

~~~java
record UnexplainedActivityEntry(
    String entryId,
    String materialId,
    String entryKey,
    String materialContext,
    String reasonCode) {}
~~~

字段来源全部由程序持有：`entryId/materialId` 来自材料，`entryKey` 来自本次映射，`materialContext` 必须逐字等于原 `modelPacket.context()`，`reasonCode` 固定为 `MODEL_NOT_EXPLAINED`。模型只选择 key，不能创建 identity、context 或技术缺口。

`ActivityExplanationResult` 增加程序侧字段 `List<UnexplainedActivityEntry> unexplainedActivityEntries`；`activity-coverage.json` v2 顶层也以 `unexplainedActivityEntries` 保存这些完整 record 对象，不新增文件。该完整记录数组与模型响应中只含 key 的 `unexplainedEntries` 不是同一 shape。程序可继续逐入口保存；下游模型投影则按 `materialId` 聚合，删除 `entryId/materialId`，只给已有可见的 `{materialContext, unexplainedEntryKeys, reasonCode}`，无需正则反解析中文 context，也不暴露全局身份。

同步范围：

- `ActivityExplainer`、`ActivityExplanationResult`、`ActivityExplanationCheckpointPublisher/Reader`；
- `ActivityPromptCatalog` 及 versioned DRAFT/REVIEW prompt（新执行使用 v2；旧 v1 response 不兼容读取）；
- `activity-coverage` schema v2；`activity-explanations` 记录未变则保持 v1；
- `RepositoryBusinessKnowledge`、`ProcessExplainer.repositoryInput`、`ProcessKnowledgeCheckpointPublisher/Reader`，并升级实际增加该数组的 process-coverage / repository-knowledge schemas；
- `BusinessReportPublisher.cleanKnowledge` 与报告 prompt，让第 9 章收到具体 context/key/reason，而非只有数量；报告输出九章 shape 不变则不为字段未变的输出强行升版；
- `AtomicCanonicalPublicationEngine` 中对应新 schema contract，以及直接 checkpoint/read/reopen tests。

`MODEL_NOT_EXPLAINED` 只表示模型在一次 DRAFT+REVIEW 内未形成可用活动，不表示源码丢失、Step05 context 损坏或技术 Gap。若 source/hash/ref 本身错误，仍走原 fatal，不得转成 unexplained。

当前 REVIEW 为空时统一写 `MODEL_NO_ACTIVITY` 的分支改为同一逐 key 规则：空 activities 只有在 `unexplainedEntries` 恰好列出全集时才是合法 PARTIAL。下游 prompt 根据每项 `materialContext + unexplainedEntryKeys` 指出对应 HTTP 入口，但正文不得只输出 E3 这类局部 key。切到 v2 Prompt 后，未被 catalog 引用的 v1 resource 可删除；Git/history artifacts 保留，不设兼容 fallback。

程序侧仍可逐入口保存 `UnexplainedActivityEntry`；送给 Process/Report 模型前按 `materialId` 分组为一项 `{materialContext, unexplainedEntryKeys, reasonCode}`，删除 `materialId/global entryId`，同一 `materialContext` 只发送一次。该投影以程序持有的 local-key 映射聚合，不解析 context 字符串。

本文所称 PARTIAL、INCOMPLETE 是文档语义完整度和验收结论；本次不新增 runtime/report 状态枚举。已有 coverage 文件中的语义字段按其 owning schema 演进，但当前 runtime result 和 report result 不被假定已有这两个状态。

## 7. 真实 E1–E4 推演

未来新执行必须先把 Activity profile 的活动槽位调到至少 4，并通过 REVIEW 输入预留；这与原失败使用的上限 2 是不同配置和 fingerprint。

合法但不完整的 DRAFT 可以仍是实际形状 A1/E1、A2/E2。唯一 REVIEW 收到四入口原材料、完整 actualDraft 和 `missingEntryKeys=[E3,E4]`。推荐完整修订至少能形成下表语义；不强制 activity ID 或恰好四项：

| 覆盖 | 期望业务解释边界 |
| --- | --- |
| E1 | 处理用户删除请求，调用删除服务并包装处理结果；实际删除、权限和范围待确认 |
| E2 | 读取当前会话用户信息并在返回前清空密码字段，保留正常/异常分支 |
| E3 | 校验注册输入并发起注册处理；不声称数据库已写入，不把参数猜成岗位 |
| E4 | 发起清理会话信息，异常时返回退出失败；不声称外部会话存储已成功删除 |

一条完整 REVIEW 活动示例（其余字段/活动按同一现有 schema）：

~~~json
{
  "activityLocalId": "A3",
  "entryKeys": ["E3"],
  "name": "校验并发起用户注册",
  "businessPurpose": "校验注册请求中的验证码和登录名，并把通过校验的注册信息交给注册处理。",
  "participants": [],
  "businessObjects": ["用户注册信息"],
  "triggerOrInput": ["HTTP POST /user/registerUser 提交的注册信息"],
  "conditions": ["验证码校验与登录名检查通过后才继续发起注册处理"],
  "activitySteps": ["建立标准返回内容", "将登录名写入用户名", "校验验证码和登录名", "调用注册处理并返回结果"],
  "codeDefinedResults": ["代码发起注册处理并返回预先建立的结果对象"],
  "businessRules": [],
  "formulasOrMetrics": [],
  "terms": ["登录名", "验证码"],
  "certainty": "DIRECT_CODE_BEHAVIOR",
  "sourceRefs": ["S731"],
  "questions": ["注册处理实际持久化哪些信息？"],
  "scopeLimitations": ["静态源码片段不证明本次注册已成功持久化"]
}
~~~

若 REVIEW 产生完整活动覆盖 E1–E4，则四个 global entry coverage 均有非空 activity IDs。若 REVIEW 选择 `unexplainedEntries=[E3,E4]`，则 E1/E2 analyzed、E3/E4 `MODEL_NOT_EXPLAINED`，整条知识/报告链是 PARTIAL；账闭合但该四入口语义验收仍失败。若 REVIEW 既不解释也不列 E3/E4，则 fatal，且 Provider 调用数仍恰为 2。

Step07 可以把这些活动一起召回阅读，但不得仅因同一 Controller 或都与用户有关而强串为“注册→登录→退出→删除”。两个保守过程、四个独立局部过程或有依据的多对多关系都可能正确。Step08 的九章至少应在第 4 章保留已审活动，在第 9 章保留外部执行效果和任何 `MODEL_NOT_EXPLAINED` 入口；第 7 章没有已审公式时明确未识别指标。

## 8. 可扩展性与边界推演

### 8.1 跨包 N=9

假设 Builder 因入口/字节边界形成 M1={G1,G2,G3}、M2={G4,G5,G6}、M3={G7,G8,G9}。每包都合法使用 E1/E2/E3；程序分别用 `(M1,E1)→G1`、`(M2,E1)→G4`、`(M3,E1)→G7` 映射。

模型可以在 M1 用一个活动覆盖 E1+E2、另一个覆盖 E3；M2 可以三个独立活动；M3 可以一个活动覆盖三项。最终 global coverage 的 key 集必须恰为 G1…G9，无重复 coverage row、无遗漏、无跨包 E1 串接。活动数量不用于推算入口数量。

### 8.2 单包 N≥12

另用容量足够的 profile 构造一个至少 12 入口的单包，明确断言 schema enum、输入、DRAFT/REVIEW 与映射都包含 E10、E11、E12；输出可用一个多入口活动加若干独立活动，不要求 12 项。此例专门防止词典序、前缀和一位数字假设，不能被 N=9 三包替代。

### 8.3 零入口

零入口 material set 生成空 activity result、Activity Provider 调用数为 0 和闭合的空 coverage；Step07 的 Process Provider 调用数也为 0。若调用方仍显式生成空仓九章范围报告，当前 `BusinessReportPublisher` 继续执行报告 DRAFT+REVIEW；本设计不新增“零模型空报告”分支。该报告的文档语义完整度和验收结论必须为 INCOMPLETE，不要求新增运行状态枚举。不得制造 E1、空活动或虚假成功过程。

### 8.4 超预算或配置不相容

若 N=9 但活动槽位只有 8，或 REVIEW 最坏输入预留超过 input bytes，整份材料在 DRAFT 前标 NOT_ANALYZED，调用数为 0。已经保存的 BusinessMaterial 不被 Explainer 拆包或截断。其他相容材料仍可按 `maxMaterialsToStart` 处理，且每个 global entry 恰一 coverage row。

### 8.5 REVIEW 仍遗漏与非法响应

- 合法 DRAFT 漏 key：进入唯一 REVIEW。
- REVIEW 用活动或 `unexplainedEntries` 闭合：返回完整或 PARTIAL。
- REVIEW 两边仍漏 key：fatal，不发布伪完整结果。
- DRAFT/REVIEW 非法 JSON、未知 key、未知 ref、重复 local ID、超 schema/bytes：保持 fatal；不借 repair 通道放宽。

## 9. 下游过程与一份九章链

一条 scripted acceptance 应使用上述四入口材料走真实四 Module 顺序：Builder 保存材料；Activity REVIEW 完整覆盖四入口；ProcessExplainer 接收全部 ReviewedActivity 字段；过程 REVIEW 可保留两个或多个互不强串的过程；BusinessReportPublisher DRAFT+完整 REVIEW；renderer 输出恰好固定九个 H2。

验收读取链为：

~~~text
E1…E4 global entries
  → material 内 E1…E4
  → ReviewedActivity.entryIds / ActivityEntryCoverage
  → RepositoryBusinessKnowledge.activities + conservative processes
  → report input activities/processes/coverage
  → 第4章业务活动 + 第9章待确认事项 + source refs
~~~

PARTIAL 变体把 E3/E4 放入 `unexplainedEntries`。Process repository input 和 report input 必须按 material 合并，包含一次原 material context、`unexplainedEntryKeys=[E3,E4]` 和 `MODEL_NOT_EXPLAINED`；第 9 章应具体说哪些 HTTP 入口本次未形成活动解释及原因类别。只传 `notAnalyzedEntries=2` 或为每个 key 重复整包 context 都不合格。

程序 set closure 只验证 global entry denominator；业务语言是否把注册、删除和会话行为解释正确，仍由完整 REVIEW 与授权人工样本判断。两个独立过程不是失败，强造一个“用户生命周期”才是错误。

## 10. 删并保测试矩阵

| 行为 | 未来直接测试 | 处置/关键断言 |
| --- | --- | --- |
| 通用 Flow fixture | 14 个现行测试 + 新 `BusinessFlowTestSupport` | 先迁移；typed 构造；语义不变 |
| Step05 Capsule 覆盖 | `BusinessFlowCoverageTest` | 保留 Flow/Capsule/budget Gap；删 R0 task 尾段 |
| 旧 R0/R1/R2/registry/process seams | 四个旧测试目录 14 文件 | 随 78 个旧生产类删除，不搬旧行为 |
| Capsule 字段减法 | `EvidenceCapsuleProjectorTest`, `CapsuleProjectionModulePublisherTest` | 新 v9/v7 不含两个 registryProposal 字段；facts/gaps/signals/context 仍在 |
| N=4 漏两项后修复 | `ActivityExplainerTest` | DRAFT 2 项；REVIEW 收完整 actualDraft + E3/E4；最终覆盖四入口或显式 partial |
| N≥12 单包 | `ActivityExplainerTest`, `ActivityOutputSchemaTest` | E10/E11/E12 精确 enum/映射；多对多合法 |
| N=9 三包 | `ActivityExplainerTest` | 各包 E1 重用不串 global IDs；union G1…G9 |
| 输出/REVIEW 预算 | `ActivityExplanationBudgetTest` | 不相容和预留不足均 0 请求；实际 review packet 再核 bytes |
| 非法 JSON/ref/key | `ActivityExplainerTest` | 首次非法立即 fatal；无 REVIEW/重试 |
| REVIEW 仍漏 | `ActivityExplainerTest` | 两次调用后 fatal；不生成第三次 |
| unexplained sidecar reopen | `ActivityExplanationCheckpointTest`, reader test | global ID、material context、reasonCode 完整且未冒充技术 Gap |
| Prompt/schema | `ActivityPromptContractTest`, `ActivityOutputSchemaTest` | DRAFT 与 REVIEW shape 区分；missing keys；unexplained union/disjoint |
| 下游 PARTIAL | `ProcessMaterialRecallTest`, `ProcessKnowledgeCheckpointTest`, `BusinessReportPublisherTest` | 具体入口/理由到第9章，不只计数 |
| 四入口九章闭环 | `PersistedBusinessRunExecutorTest` | 完整 activity 字段→保守 process→固定九章，renderer 不缩水 |
| 零入口 | `BusinessMaterialBuilderZeroEntryTest` 和直接 workflow/report test | Activity/Process Provider 为 0；显式报告仍走既有 DRAFT+REVIEW；九章语义验收 INCOMPLETE |

未来只串行运行新增或直接覆盖变更的 selectors。POM 的 UT skip 与 IT opt-in 并不构成统一自动验收；live IT 只能在新的明确授权、相容 profile、命名样本和保存输入/response 条件下显式运行，且不能重放旧四入口失败。本轮不运行 Maven、产品模型或客户扫描；只读文件/Git 检查已用于事实核对。

## 11. 文档同步、持久化缺口与旧工作树保护

本次文档同步把已批准规则写入 `AGENTS.md`、`README.md`、`docs/DESIGN.md`、Step05–08、Prompt 设计、walkthrough 和当前实施衔接计划；这不表示 Java/test/resource/schema 已改变。未来每个代码/test/schema 批次仍须在同一 work unit 校准当前事实；旧链真正删除后才能把“旧模块仍注册”改成已清理，v2 真正落地后才能把覆盖修复写成当前实现。

“每包 REVIEW 随即保存”仍列为独立已知缺口。本次最小实现保持现有单 module 聚合 publication，不声称已解决即时 checkpoint。若用户另行优先处理，须先为 Activity/Process 各自确定不覆盖既有 publication 的聚合/分片语义；不得在当前固定地址循环 install，也不得借本次清理扩成恢复系统。

当前 source-code worktree 的既有 dirty 改动全部保留。本轮文档同步实际修改范围以 `progress/approved-cleanup-design-synchronization.md` 的 Changed files 为准；先前完成的 `progress/code-cleanup-and-coverage-design.md` 保持不动，不把其他 dirty 文件归为本轮所有。

另有受保护主 worktree `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`，位于 `codex/github-code-design-walkthrough`、commit `eefab1a` 且 dirty；其中仍有旧 `stage01` 51、`stage02` 24、`stage03` 68、`stage04` 23 个 Java 文件。当前 source-code 工程没有这些 stage 包。后续只能先只读盘点并请用户确认；不得 `worktree remove/reset/clean`，不得把 Maven `target` 等构建输出误当源码，也不承诺本轮清除所有磁盘历史。

## 12. 验收标准

清理完成需同时满足：

- 14 个新测试已脱离旧 test class；`BusinessFlowCoverageTest` 不再创建 R0 task。
- 四个旧生产包、四个旧测试包、旧 1–9 注册/contract 分支消失；当前 10/11 和 `ModelRuntimeIdentityV1` 保留。
- Capsule 新 schema 不再含两个 registryProposal basis 字段，但 EntryContext、facts、gaps、signals、SourceRefs 和 Builder 行为不丢。
- 每材料任意 N 使用 E1…EN；N≥12 的 E10–E12 与跨包局部 E1 映射正确。
- 所有 Provider 调用前输入/输出/REVIEW 容量闭合；不相容材料零请求并有 global-entry coverage 原因。
- 合法不完整 DRAFT 只进入一次 REVIEW；最终全集由活动覆盖或显式 unexplained 处置；非法 ref/JSON 和 REVIEW 无声遗漏仍 fatal。
- E1–E4 完整 scripted 链能保留四入口活动并允许保守独立过程；PARTIAL 链把具体未解释入口送入第 9 章。
- 没有新业务分类器、重试/replay、恢复/存储子系统、兼容 alias、固定产物总数或跨包 key 猜测。

## 13. 已批准决定与独立后续范围

1. **已批准、尚未实施：**把 DRAFT 缺入口从“REVIEW 前 fatal”改为“结构/范围合法则交唯一 REVIEW”，并采用 required `unexplainedEntries` 最终闭合合同。
2. **已批准、尚未实施：**增加程序侧 `UnexplainedActivityEntry`，升级 activity coverage 与确实承载该数组的 Step07 schemas/readers，并把具体 partial 输入送到报告第 9 章；模型不能自造 reason 或技术 Gap。
3. **保持现状：**Step06 有效 module 地址继续为 10/11，不为清理旧 1–9 而改号。重编号属于另一个持久化身份设计，不在本次范围。
4. **独立缺口、本次不解决：**Activity/Process 每包即时 checkpoint；固定地址不能循环安装不同内容，必须另行设计聚合/分片语义，不能借清理扩成恢复系统。
5. **需另行明确授权：**受保护旧主 worktree 的 stage01–04 后续如何处置；本次只记录，不删除、不清理。

设计批准只允许把这些要求交给后续 TDD 实施，不构成修改 Java/test/resource/schema、真实 Provider、客户扫描、全套测试、提交或推送授权。
