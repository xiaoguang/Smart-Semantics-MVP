# 流程解释

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。运行顺序只由文件名中的 `06-` 与运行目录 `steps/06-flow-interpretation/` 表达。

本文示例严格使用DESIGN §1.3的`NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN`分类；未标为strict的digest/size/ID不可复制为golden。权威字段表、enum、identity和direct-preimage合同始终exact，不能靠示例降级删除。

## 1. 为什么存在

BusinessFlows已经证明“代码发生什么”，但新客户仓库的业务词、业务主张和待确认问题不可能预先穷举。FlowInterpretation只做两个受控动作：每条可安全交给模型的Flow先独立执行一次`R0_REGISTRY_PROPOSAL`，程序验证proposal并冻结全仓唯一`RepositoryInterpretationRegistry`；随后同一Flow的R1/R2只能选择该registry的finite provisional keys。RepositoryKnowledge仍由程序决定是否准入。

数量合同固定：

- `N`：BusinessFlows全部Flow/Capsule；
- `E`：其中`modelEligibility=ELIGIBLE`的Flow；
- `I=N-E`：model-ineligible Flow；
- `R`：`E`条R0 disposition中`READY_FOR_FREEZE`的Flow。

FlowInterpretation精确规划`E`个R0 task和`2R`个R1/R2 task，总数始终为`E+2R`。成功发布的FlowInterpretation中，每个planned task恰有一个`ModelTaskDispositionV1`。R2若因同Flow R1返回typed GAP/FAILED而不调用Provider，仍保留R2 task并写`NOT_RUN_UPSTREAM_FAILED` disposition。Provider调用一旦开始却没有得到可验证响应，当前run直接`FAILED`，不自动重试、不切换Provider，也不把不确定状态伪装成业务Gap。

ineligible Flow在FlowInterpretation的task、round、candidate和disposition均为零；RepositoryKnowledge根据其BusinessFlows Gap生成唯一technical fallback。每次模型调用只见一条Flow的同一EvidenceCapsule；单Flow成功不能结束分析步骤或run。

## 2. 具体输入与 DepotHead 例子

> **示例分类：NARRATIVE_ILLUSTRATION / TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT。** 下文用未来已闭合的DepotHead Flow展示目标流程；story IDs不是wire或golden。历史pre-reset fixed-slice审计为0 Flow、0 Capsule，因此是0 R0/R1/R2 task/call回归baseline；当前SourceAnalysis尚未运行该slice。

每条eligible Flow的R0，以及其中R0-ready Flow的R1/R2，只能从该Flow唯一EvidenceCapsule取得Facts、atoms、Gaps、Outcomes和连续source excerpts。全部controls只来自fresh-reopened exact `analysis-run-request-v2`：profile、budget、toolchain、schema、prompt、artifact policy和required-nullable organization seed refs；不得由方法参数、环境或inline object覆盖。seed只是exact-match提示，不能绕过R0或Capsule basis。

~~~json
{
  "flowSliceId": "flow:post-depothead-batch-set-status",
  "r0Proposal": {
    "kind": "BUSINESS_TERM",
    "label": "批量审核或反审核",
    "purpose": "描述同一入口依据输入状态到达批量处理边界调用",
    "basisAtomIds": ["atom:input-field", "atom:boundary-invocation"],
    "basisGapIds": ["gap:external-update-effect-unproven"]
  },
  "frozenProvisionalKey": "TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "r1Selection": "TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "r2Decision": "KEEP"
}
~~~

R0 label/purpose是`MODEL_INTERPRETATION` proposal data，不是Fact。程序先验证UTF-8/NFC、字符与字节上限、kind、basis闭包和seed exact-match，再允许它进入registry。即使label含“审核”“更新”或目标技术名，也不得把generic boundary invocation解释为外部副作用；Capsule中的external-effect Gap必须原样保留。

## 3. 程序怎样工作

1. fresh-reopen BusinessFlows的五项semantic artifacts与receipt，重验完整`N`条Flow/Capsule双射、`N=E+I`、eligible/ineligible不交叠并集、逐ineligible Flow非空Gap mapping，以及全部Fact/Proof/Outcome/span/projection refs。
2. 按eligible `flowSliceId`为`E`条Flow编R0 tasks。每个`RegistryProposalInputV1`逐字段携带同一Capsule的完整fact/gap/outcome/span/obligation views，以及有限proposal policy和可选seed值投影；不能退化成ID摘要。
3. M2按task顺序每项调用Provider一次。发送bytes严格等于`canonicalJson(inputJson)`；收到响应后strict parse并依次验证kind、UTF-8/NFC、control/bidi字符、长度、same-Capsule nonempty basis、seed exact-match和proposal identity。有效typed响应形成round、generation receipt、proposal及`READY_FOR_FREEZE|GAP|FAILED` disposition。Provider transport/runtime失败、响应缺失或无法形成版本化response record时当前run`FAILED`，不生成FlowInterpretation publication。
4. `E`个R0 dispositions闭合后，程序按固定tuple排序并为每个valid proposal生成全长SHA-256 provisional key，原子安装恰一个`RepositoryInterpretationRegistry`。不同Flow的相同label不合并；ineligible Flow不进入registry flow denominator。
5. 只为`R`条`READY_FOR_FREEZE` Flow各编一个R1和一个R2 task。allowlist恰为同Flow frozen provisional keys；R1/R2使用逐字段相同的Capsule view和同一isolated session。
6. M5先为每条R0-ready Flow调用R1一次。R1返回`RESPONSE_ACCEPTED`时才调用R2；R1返回typed `RESPONSE_GAP|RESPONSE_FAILED`时不调用R2，但仍为已规划R2 task写`NOT_RUN_UPSTREAM_FAILED` disposition并引用同Flow R1 taskSpecId。R2只能KEEP/NARROW/DROP/NEEDS_EVIDENCE，不能新增key、遗漏proposal或扩大basis。任一Provider transport/runtime失败使当前run`FAILED`。
7. 程序为全部`E`条eligible Flow形成最终`FlowInterpretationDisposition`。R0非READY Flow没有R1/R2 tasks；R0-ready Flow有恰两个task dispositions；只有R1/R2响应都满足candidate closure时产生candidate。ineligible Flow仍不出现。
8. M6重算`N/E/I/R`、R0/R1/R2 shard union、`E+2R` task/disposition双射、proposal/candidate/Flow处置集合和全部refs，先安装九个semantic payload与module receipt。AnalysisStep store fresh-reopen M6后安装同九项并最后写analysis step receipt。reader-visible FlowInterpretation固定为九项semantic + receipt，共十个文件。

## 4. 生成的可观察产物

| 文件 | 精确数量与唯一职责 |
| --- | --- |
| `registry-proposal-tasks.jsonl` | `E`行；每eligible Flow一个R0 exact task与完整Capsule input |
| `registry-proposal-rounds.jsonl` | 成功发布时`E`行；每R0 task一个可验证Provider response round |
| `registry-proposal-dispositions.jsonl` | `E`行；每eligible Flow一条READY_FOR_FREEZE/GAP/FAILED及R0 task disposition |
| `repository-interpretation-registry.json` | 恰一个；由全部`E`个R0 dispositions冻结的finite registry |
| `flow-model-tasks.jsonl` | `2R`行；每R0-ready Flow各一个R1和R2 task |
| `model-rounds.jsonl` | `R + count(R1 RESPONSE_ACCEPTED)`行；每个实际调用的R1/R2各一round |
| `generation-receipts.jsonl` | 与两类round文件行数之和相等；记录configured/expected/observed runtime和response identity |
| `interpretation-candidates.jsonl` | R0-ready Flow的明确子集；只有R1/R2 candidate closure成立才有一行 |
| `flow-interpretation-dispositions.jsonl` | `E`行；每eligible Flow一条最终READY_FOR_ADMISSION/GAP/FAILED |
| `flow-interpretation-receipt.json` | 九项semantic descriptors、BusinessFlows publication ref、controls与M6 provenance |

精确集合关系以排序ID数组保存和校验，不能只比较count：

~~~text
businessFlowsFlowSliceIds = modelEligibleFlowSliceIds ⊎ modelIneligibleFlowSliceIds
|modelEligibleFlowSliceIds| = E
|modelIneligibleFlowSliceIds| = I
r0Task.flowSliceIds = r0Disposition.flowSliceIds = modelEligibleFlowSliceIds
r0ReadyFlowSliceIds = eligibleR1R2FlowSliceIds
flowModelTaskIds = twoTasksPer(r0ReadyFlowSliceIds)
r0TaskDisposition.taskSpecIds = r0TaskIds
r1TaskDisposition.taskSpecIds = allR1TaskIds
r2TaskDisposition.taskSpecIds = allR2TaskIds
allTaskDispositionIds ↔ allPlannedTaskIds
|allPlannedTaskIds| = E + 2R
domain(flowInterpretationDispositions) = modelEligibleFlowSliceIds
modelIneligibleFlowSliceIds ∩ everyFlowInterpretationPerFlowDomain = ∅
~~~

R0、R1、R2的shard denominator分别固定为`E/R/R`，不是`E/E/2R`：R0 shards覆盖eligible Flow IDs；R1和R2 shards各自都覆盖同一R0-ready Flow ID集。两个R1/R2 task集合合计才是`2R`。

Provider started-call数为`E + R + count(R1 RESPONSE_ACCEPTED)`；只有所有R1都accepted时等于`E+2R`，再加`R=E`才等于`3E`。业务typed GAP/FAILED可减少R2调用，但不能删除planned R2 task或disposition。FlowInterpretation不生成Markdown。

## 5. 下游怎样消费而不返工

RepositoryKnowledge只读取ProvenCodeFacts Facts、BusinessFlows Flows/Capsules、FlowInterpretation九项semantic artifacts及各自receipt，验证`registryProposalId → provisionalKey → interpretationProposalId → selectedKey`后决定`meaningId`。它不调用Provider、不回读源码、不读取runtime journal，也不从seed或label猜Fact。

如果原RepositoryKnowledge执行失败，调用者可通过`executeStep(targetAnalysisStepKey=repository-knowledge)`创建新run，显式传入validated前六个分析步骤publications。新执行fresh-reopen FlowInterpretation十文件和RepositoryKnowledge所需ProvenCodeFacts、BusinessFlows inputs，只运行RepositoryKnowledge；六个上游分析步骤不运行，Provider调用为零。这是业务artifact复用，不是同一run恢复。

| RepositoryKnowledge开始前必须成立 | FlowInterpretation成功后保证 |
| --- | --- |
| BusinessFlows完整`N`条Flow/Capsule双射与`ELIGIBLE ⊎ INELIGIBLE`分区 | FlowInterpretation只消费`E`条eligible Flow；三轮源码证据均来自对应Capsule |
| 九项semantic + receipt、六个module publications完整 | 每eligible Flow有R0及最终disposition；ineligible Flow没有FlowInterpretation per-Flow对象；恰一registry |
| 全部`E`个R0 dispositions闭合后才freeze，freeze后才建`2R` tasks | 每个selectedKey存在于同Flow allowlist并可追到R0 proposal与Capsule basis |
| R0/R1/R2 shards分别精确覆盖`E/R/R` | `E+2R` planned tasks与task dispositions双射；单Flow PASS不冒充全仓完成 |

## 6. 成功、Gap、fatal 与显式复用

- **成功**：`N/E/I/R`、registry、九项semantic、六个module publications、task/disposition双射和AnalysisStep receipt全部闭合；`E=0`合法，仍产生0-item registry和十文件。
- **带Gap成功**：Provider返回schema-valid typed GAP/FAILED业务处置，且全分母、refs和identity仍闭合。R0 GAP/FAILED不创建R1/R2 tasks；R1 GAP/FAILED保留R2 task并写`NOT_RUN_UPSTREAM_FAILED`。
- **run失败**：Provider调用开始后transport/runtime中断、无可验证response、configured/observed runtime不符，或Provider前的task/session/policy无法执行。当前run为`FAILED`，不自动重试或切换；已安装VerifiedSourceInventory、ApplicationDiscovery、ProgramGraphs、ProvenCodeFacts、BusinessFlows及FlowInterpretation前驱module artifacts/诊断保留，但没有伪FlowInterpretation success publication。
- **fatal**：Capsule/basis/reference不闭合、R0 open field越界、非法Unicode、seed mismatch、freeze前启动R1、registry collision、跨Flow key/basis、R2扩张、accounting/canonical/install错误。fatal同样使当前run失败。
- **显式复用**：RepositoryKnowledge或后续analysis step的新执行可消费完整FlowInterpretation publication；若调用者明确从BusinessFlows新开FlowInterpretation，则那是新的模型工作和新run，不继承旧Provider调用状态。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 冻结Flow/Capsule、task、schema、prompt、runtime | 是 | 否 |
| R0提出bounded label/purpose及Capsule basis refs | 验证并冻结 | 是，仅本Flow |
| 生成provisional key和唯一registry | 是 | 否 |
| R1/R2选择和审查finite provisional keys | schema/ref验证 | 是，仅本Flow |
| 发现Fact/locator/Flow或写Markdown | 上游/下游负责 | 禁止 |
| 最终准入meaning | RepositoryKnowledge程序 | 否 |
| 自动retry、换Provider/API key、续接旧session | 禁止 | 无权 |

模型目标为gpt-5.6-luna / xhigh / read-only。真实调用需要另行授权；本文档工作没有调用模型。

## 8. 技术合同

### 8.0 固定模块合同

顺序固定为`RegistryProposalTaskCompiler → RegistryProposalRunner → RepositoryInterpretationRegistryFreezer → FiniteKeyFlowTaskCompiler → InterpretationRunner → InterpretationPublicationSpecifier → CanonicalAnalysisStepArtifactStore`。只有M2/M5持有Provider Interface；每个task最多调用一次。持久化module keys仍固定为`registry-task-compiler / registry-proposal-runner / registry-freezer / flow-task-compiler / interpretation-runner / publish`。

Java 包名不复刻带连字符的持久化 module key。为防止实现者另造编号或泛型 helper root，R0 proposal task compiler 与 runner 位于`org.sourceanalysis.app.analysis.interpretation.proposal`，唯一 registry freezer 位于`org.sourceanalysis.app.analysis.interpretation.registry`；后续 R1/R2 compiler 与 runner 位于`org.sourceanalysis.app.analysis.interpretation.model`，最终发布器位于`org.sourceanalysis.app.analysis.interpretation.publish`。这些包只表达实现职责；artifact/module key、schema、运行目录和输出文件仍严格使用本章既定 wire 名称。

| 模块 | exact direct upstream | exact输出与失败 | 下游保证 |
| --- | --- | --- | --- |
| M1 RegistryProposalTaskCompiler | BusinessFlows五项semantic refs（其中`evidence-capsules.jsonl`必须为`business-flows-evidence-capsule-v2`）、BusinessFlows receipt、run-request、prompt/schema/budget refs | `flow-interpretation-registry-proposal-task-set-v2`：`E` tasks、完整input JSON、R0 shard denominator=`E`；分区/ref/hash错则run失败 | M2无需读取BusinessFlows或内存draft |
| M2 RegistryProposalRunner | M1 task-set ref、冻结Provider/runtime policy | `flow-interpretation-registry-proposal-execution-set-v3`：`E` rounds/receipts/dispositions及validated proposals；Provider每task一次，transport/runtime失败使run失败 | M3只读canonical R0业务结果 |
| M3 RepositoryInterpretationRegistryFreezer | M1、M2、BusinessFlows五项refs | `flow-interpretation-repository-interpretation-registry-v2`：唯一registry、`E` flow dispositions、proposal accounting；缺/重/碰撞fatal | M4获得finite same-Flow keys |
| M4 FiniteKeyFlowTaskCompiler | M3、BusinessFlows五项、run-request、R1/R2 prompt/schema/budget refs | `flow-interpretation-flow-task-set-v4`：`2R` tasks；R1和R2 shard denominator各=`R` | M5无需决定allowlist/session/input |
| M5 InterpretationRunner | M2、M3、M4 refs、冻结Provider/runtime policy | `flow-interpretation-model-execution-set-v5`：actual rounds/receipts/proposals/candidates、`E` final dispositions和`2R` task dispositions；Provider失败使run失败 | M6无需Provider或runtime state |
| M6 InterpretationPublicationSpecifier | M1–M5 refs | 一次module install恰九项public semantic bytes；closure/canonical/collision错误fatal；不得含analysis step root/receipt | AnalysisStep store可原样安装九项并最后写receipt |

DepotHead walkthrough在每个模块的投影固定为：M1一个完整Capsule R0 task；M2一个有basis的“批量审核或反审核”proposal/处置；M3一个`TERM_P_<hex64>` registry item；M4同Flow的R1/R2两个finite-key tasks；M5一个KEEP candidate或typed Gap；M6发布九项semantic并由AnalysisStep store补第十个receipt。若未来新实现重放后得到0-Capsule Gap，这六个per-Flow投影都为零，但0-item registry和十文件仍存在。

每个模块都必须满足以下实现brief：

- **M1测试/实现**：public seam `compileRegistryProposalTasks(businessFlows, taskProfile)`；`taskProfile`不是调用者可任意编造的模型配置，而是core在已经验证完整`analysis-run-request-v2`后投影出的不可变`RegistryProposalTaskProfile`。它恰含`promptBundleRef`、`outputSchemaRef`、`expectedRuntimeRef`、`resourceBudgetRef`及`maxTasks/maxProposalsPerTask/maxResponseUtf8Bytes/maxLabelUtf8Bytes/maxPurposeUtf8Bytes`；其前3个digest必须分别与BusinessFlows receipt中的prompt/schema/profile controls相符，其他request来源校验由后续run-core完成。Luna selector `RegistryProposalTaskCompilerTest`覆盖`N=0,E=0`、`N>0,E=0`、mixed eligibility、完整Capsule input、seed、partition/shard/hash反例。完整Capsule input只从fresh-reopened BusinessFlows public `evidence-capsules.jsonl` v2得到，其中span/obligation对象与其ID列表逐字闭合；v1、private M2 module引用、重新打开源码或省略内容都必须fail closed。Terra只改`analysis/interpretation/proposal/`。
- **M2测试/实现**：public seam `runRegistryProposals(taskSet, provider)`；Luna selector `RegistryProposalRunnerTest`覆盖0call、valid term、typed GAP/FAILED、basis/Unicode/seed反例、single-call、transport failure no retry、双Flow隔离。Terra只改`analysis/interpretation/proposal/`。
- **M3测试/实现**：public seam `freeze(taskSet, executionSet, businessFlows)`；Luna selector `RepositoryInterpretationRegistryFreezerTest`覆盖empty、one item、same-label two-flow、seed lineage、shuffle、missing/duplicate/collision、fresh reopen。Terra只改`analysis/interpretation/registry/`。
- **M4测试/实现**：public seam `compileFiniteKeyTasks(businessFlows, registry, requestV2)`；Luna selector `FiniteKeyFlowTaskCompilerTest`覆盖0ready、single、multi-flow key isolation、READY missing key、`R/R` shard denominators和input hashes。Terra只改`analysis/interpretation/model/`。
- **M5测试/实现**：public seam `runInterpretations(taskSet, registry, r0Dispositions, provider)`；Luna selector `InterpretationRunnerTest`覆盖R1/R2 success、R1 typed GAP/FAILED→R2 NOT_RUN、unknown/cross-flow key、R2 expansion、transport failure no retry、双Flow隔离。Terra只改`analysis/interpretation/model/`。
- **M6测试/实现**：public seam `specify(m1,m2,m3,m4,m5,controls)`；Luna selector `FlowInterpretationPublicationSpecifierTest`覆盖`E=0`十文件、`E=1,R=1`、`E=2,R=1`、`E=2,R=2`、`E/R/R` shards、task/disposition ID-set equality、ineligible leakage、partial-install/collision/fresh-reopen。Terra只改`analysis/interpretation/publish/`。

所有Luna tests使用scripted provider，禁止live Provider、网络、客户Maven或mock canonical/accounting。Terra观察对应RED后只实现最小GREEN；任何field/model/failure/cross-analysis step变化按DESIGN 13.11 STOP交Sol/ultra。

### 8.0.1 模块 artifact wire schemas

M1–M5使用DESIGN 13.3 `ModuleArtifact<T>` envelope；M6直接安装九个registered analysis step bytes。unknown field/version拒绝。

| module artifact / schema | exact payload与排序 |
| --- | --- |
| `modules/01-registry-task-compiler/registry-proposal-task-set.json` / `flow-interpretation-registry-proposal-task-set-v2` | `taskSetId,businessFlowsPublicationRef,flowCount=E,eligibleFlowSliceIds,tasks[E],taskShardReceipts,runtimePolicy,organizationRegistrySeedRef`；tasks按flowSliceId，每个含完整`RegistryProposalInputV1`及input/schema/prompt/runtime hashes |
| `modules/02-registry-proposal-runner/registry-proposal-execution-set.json` / `flow-interpretation-registry-proposal-execution-set-v3` | `executionSetId,taskSetId,rounds[E],receipts[E],validatedProposals,flowDispositions[E],shardReceipts,callCount=E`；round/receipt/disposition按flow，proposal按proposalId |
| `modules/03-registry-freezer/repository-interpretation-registry.json` / `flow-interpretation-repository-interpretation-registry-v2` | `repositoryInterpretationRegistryId,eligibleFlowSliceIds[E],items,flowDispositions[E],proposalAccounting,organizationRegistrySeedRef,closed=true`；items按proposalKind/flow/provisionalKey |
| `modules/04-flow-task-compiler/flow-task-set.json` / `flow-interpretation-flow-task-set-v4` | `flowTaskSetId,repositoryInterpretationRegistryId,eligibleR1R2FlowSliceIds[R],tasks[2R],r1ShardReceipts,r2ShardReceipts,runtimePolicy`；tasks按flow后R1/R2，两个shard union分别等于R个Flow IDs |
| `modules/05-interpretation-runner/model-execution-set.json` / `flow-interpretation-model-execution-set-v5` | `executionSetId,flowTaskSetId,repositoryInterpretationRegistryId,rounds,receipts,interpretationProposals,candidates,flowDispositions[E],r1ShardReceipts,r2ShardReceipts,callCounts`；round/receipt按task，candidate/disposition按flow |
| `modules/06-publish/<nine registered filenames>` | exact九项public bytes与§4同名；M6 receipt的direct upstream恰为M1–M5 payload refs，不添加runtime carrier |

九项public schema registry固定为：

| filename | schema |
| --- | --- |
| `registry-proposal-tasks.jsonl` | `flow-interpretation-registry-proposal-task-v2` |
| `registry-proposal-rounds.jsonl` | `flow-interpretation-registry-proposal-round-v2` |
| `registry-proposal-dispositions.jsonl` | `flow-interpretation-registry-proposal-disposition-v2` |
| `repository-interpretation-registry.json` | `flow-interpretation-repository-interpretation-registry-v2` |
| `flow-model-tasks.jsonl` | `flow-interpretation-flow-model-task-v3` |
| `model-rounds.jsonl` | `flow-interpretation-model-round-v2` |
| `generation-receipts.jsonl` | `flow-interpretation-generation-receipt-v2` |
| `interpretation-candidates.jsonl` | `flow-interpretation-interpretation-candidate-v2` |
| `flow-interpretation-dispositions.jsonl` | `flow-interpretation-flow-interpretation-disposition-v2` |

### 8.1 Interface 与 records

~~~java
interface PerFlowInterpretationEngine {
    FlowInterpretationReference interpret(BusinessFlowsReference flows,
                               AnalysisRunRequestReference requestV2);
}
~~~

~~~text
RegistryProposalTask
  taskSpecId, taskKind=R0_REGISTRY_PROPOSAL
  flowSliceId, evidenceCapsuleId, isolatedSessionKey
  inputJson: RegistryProposalInputV1
  inputJsonSha256, outputSchemaSha256, promptBundleSha256, expectedRuntime

RegistryProposalTaskProfile
  promptBundleRef, outputSchemaRef, expectedRuntimeRef, resourceBudgetRef
  maxTasks, maxProposalsPerTask, maxResponseUtf8Bytes
  maxLabelUtf8Bytes, maxPurposeUtf8Bytes

  A package-level immutable projection of a previously verified analysis-run-request-v2.
  It is never an adapter request, path/config carrier, or substitute for run-request admission.
  prompt/schema/runtime digest values must agree with the BusinessFlows receipt controls.

RegistryProposalInputV1
  schemaVersion=flow-interpretation-registry-proposal-input-v1
  kind=R0_REGISTRY_PROPOSAL_INPUT
  flowSliceId, evidenceCapsuleId
  capsuleView: ModelEvidenceCapsuleViewV1
  permittedProposalKinds[]: BUSINESS_TERM | CLAIM | QUESTION
  proposalLimits
  seedEntries[]: OrganizationSeedEntryViewV1

BusinessRegistryProposal
  registryProposalId, taskSpecId, flowSliceId, evidenceCapsuleId
  proposalKind: BUSINESS_TERM | CLAIM | QUESTION
  normalizedLabel, normalizedPurpose
  basisAtomIds[], basisGapIds[], sourceSeedKey?

ModelTaskDispositionV1
  taskSpecId, flowSliceId, round: R0 | R1 | R2
  state: RESPONSE_ACCEPTED | RESPONSE_GAP | RESPONSE_FAILED | NOT_RUN_UPSTREAM_FAILED
  modelRoundId?, generationReceiptId?, upstreamTaskSpecId?
  gapIds[], failureRef?, reasonCode?

RegistryProposalDisposition
  registryProposalDispositionId, flowSliceId
  disposition: READY_FOR_FREEZE | GAP | FAILED
  r0TaskDisposition: ModelTaskDispositionV1
  registryProposalIds[], gapIds[], failureRef?, reasonCode?

RepositoryInterpretationRegistryItem
  provisionalKey, registryProposalId, flowSliceId, evidenceCapsuleId, proposalKind
  normalizedLabel, normalizedPurpose
  basisAtomIds[], basisGapIds[], sourceSeedKey?

RepositoryInterpretationRegistry
  repositoryInterpretationRegistryId
  eligibleFlowSliceIds[], items[], flowDispositions[]
  proposalAccounting, organizationRegistrySeedRef, closed

FlowModelTask
  taskSpecId, taskKind, flowSliceId, evidenceCapsuleId, isolatedSessionKey
  round: R1 | R2
  allowedKeys[]
  inputJson: FlowModelInputV1
  inputJsonSha256, outputSchemaSha256, promptBundleSha256, expectedRuntime

FlowModelInputV1
  R1_INTERPRETATION_INPUT:
    flowSliceId, evidenceCapsuleId, repositoryInterpretationRegistryId
    capsuleView, allowedRegistryItems[]
  R2_REVIEW_INPUT:
    flowSliceId, evidenceCapsuleId, repositoryInterpretationRegistryId
    capsuleView, allowedRegistryItems[]
    reviewTarget {protocol=SAME_SESSION_PRIOR_R1_RESPONSE,r1TaskSpecId}

ModelRound
  modelRoundId, taskSpecId
  canonicalResponseSha256, semanticResponseSha256
  generationReceiptId

GenerationReceipt
  generationReceiptId, taskSpecId
  configuredRuntime, expectedRuntime, observedRuntime
  providerCallStarted=true
  canonicalRequestSha256, canonicalResponseSha256

InterpretationProposal
  interpretationProposalId, registryProposalId, flowSliceId
  provisionalKey, selectedKey
  basisAtomIds[], basisGapIds[], r2Decision

FlowInterpretationCandidate
  candidateId, flowSliceId, evidenceCapsuleId
  r1RoundId, r2RoundId, interpretationProposalIds[]

FlowInterpretationDisposition
  flowInterpretationDispositionId, flowSliceId
  disposition: READY_FOR_ADMISSION | GAP | FAILED
  r1TaskDisposition?, r2TaskDisposition?
  candidateId?, gapIds[], failureRef?, reasonCode?
~~~

`ModelTaskDispositionV1`闭集规则：

1. `RESPONSE_ACCEPTED|RESPONSE_GAP|RESPONSE_FAILED`必须有matching `modelRoundId`和`generationReceiptId`，且round/task/flow/round-kind逐字相等。
2. `NOT_RUN_UPSTREAM_FAILED`只允许R2；round/receipt IDs必须null，`upstreamTaskSpecId`必须指向同Flow R1，其state只能为`RESPONSE_GAP|RESPONSE_FAILED`。
3. 每eligible Flow恰一R0 task disposition。R0 READY Flow的final disposition恰有R1/R2两个task dispositions；R0非READY Flow二者都显式null且没有R1/R2 tasks。
4. 所有taskSpecId与M1/M4 planned task IDs双射，不能靠count自证。

`capsuleView`是对应BusinessFlows Capsule的完整封闭投影：fact atoms及Proof IDs、Gap reason/evidence、Outcome guard/terminal、连续source spans、projection obligations和R0 basis allowlist逐字段复制。R0/R1/R2同Flow都使用相同view；`allowedRegistryItems`按provisionalKey排序。发送bytes恰为`canonicalJson(inputJson)`，runner发送前重算SHA。R2的same-session target必须显式绑定R1 task和round，不能靠Provider“latest session”。

`FlowInterpretationDisposition` identity固定：

~~~text
flowInterpretationDispositionId = "flow-interpretation-disposition:" + lowercaseHex(SHA-256(
    frame(UTF8("flow-interpretation-disposition-id-v2")) ||
    frame(canonicalJson(dispositionWithoutFlowInterpretationDispositionId))))
~~~

### 8.2 R0、freeze、R1/R2 精确边界

- R0 raw response每项只允许`proposalKind,label,purpose,basisAtomIds,basisGapIds,sourceSeedKey`；extra field拒绝。程序验证后才发布normalized values。
- registry freeze等待全部`E`个R0 dispositions；不同Flow不按label/purpose合并，seed未被proposal引用的项不进入registry。
- R1 selectedKey必须逐字等于同Flow provisionalKey；R2逐项覆盖R1，只能KEEP/NARROW/DROP/NEEDS_EVIDENCE。`NARROW`可收窄basis或meaning eligibility但不能换key。
- R0-ready Flow有R0/R1/R2三个planned tasks；R0非READY eligible Flow只有R0；ineligible Flow三者都没有。ReaderCandidateRound不是第四次Flow解释。

### 8.3 Provider 与运行失败语义

- 每个task最多一次Provider调用；没有preflight retry、started replay或Provider switching。
- runner在调用前验证task/request/runtime identity；调用开始后若transport中断、进程结束、响应缺失或observed runtime不符，当前run`FAILED`并保留安全诊断。该失败不进入`ModelTaskDispositionV1`，也不安装FlowInterpretation success set。
- 只有完整收到并通过版本化response envelope校验的响应，才能写`ModelRound`、`GenerationReceipt`和task disposition。
- 已完整安装的M1/M3/M4等业务module artifacts继续可inspect；调用者可显式从BusinessFlows创建新的FlowInterpretation execution，但系统不把它当旧调用续接，也不提供exactly-once保证。
- capability manifest仅声明`RUNTIME_RESUME=CAPABILITY_NOT_ENABLED`。

### 8.4 预算和安全

Profile分别固定R0 tasks/response bytes/proposals/label codepoints+UTF-8 bytes/purpose codepoints+UTF-8 bytes/basis refs/registry items，以及R1/R2 tasks/response/proposals/总Provider wall-clock。超限不截断JSON或偷偷少Flow。

模型只读单Flow Capsule canonical JSON；无本机绝对Path、文件工具、客户执行、网络source或secret。R0文本strict UTF-8/NFC，拒绝C0/C1 controls、unpaired surrogate、bidi override/isolate、NUL和换行注入；值始终是quoted JSON data。prompt/system/schema不接受Capsule excerpt或seed改变指令优先级。

### 8.5 稳定 failure codes

FLOW_INTERPRETATION_INPUT_INVALID、FLOW_CAPSULE_SET_INVALID、CAPSULE_CLOSURE_BROKEN、REGISTRY_PROPOSAL_TASK_INVALID、REGISTRY_PROPOSAL_RESPONSE_INVALID、REGISTRY_PROPOSAL_REFERENCE_INVALID、REGISTRY_PROPOSAL_TEXT_INVALID、REGISTRY_SEED_MISMATCH、REGISTRY_FREEZE_INCOMPLETE、REGISTRY_IDENTITY_COLLISION、MODEL_TASK_INVALID、MODEL_TASK_HASH_MISMATCH、MODEL_TASK_NOT_RUN_UPSTREAM_INVALID、MODEL_RESPONSE_INVALID、MODEL_RESPONSE_IDENTITY_MISMATCH、MODEL_REFERENCE_INVALID、MODEL_REVIEW_NOT_CLOSED、MODEL_REVIEW_EXPANDED、MODEL_RUNTIME_IDENTITY_MISMATCH、PROVIDER_FAILURE_AFTER_START、FLOW_INTERPRETATION_UNAVAILABLE、INTERPRETATION_COVERAGE_BROKEN、FLOW_INTERPRETATION_RESOURCE_LIMIT_EXCEEDED。

### 8.6 测试 seam 与验收

- `N=0,E=0`和`N>0,E=0`都得到0 tasks/rounds/dispositions/calls、一个0-item registry和十文件；后者证明ineligible Flow不泄漏。
- `E=1,R=1`全accepted时精确3 planned tasks/3 calls；`E=2,R=1`精确4 planned tasks；`E=2,R=2`精确6。每个fixture验证task↔disposition的`E+2R`双射。
- R0 shard denominator=`E`，R1=`R`，R2=`R`；缺、重叠或误写`E/E/2R`均失败。
- R1 typed GAP/FAILED保留R2 task并产生exact `NOT_RUN_UPSTREAM_FAILED`，R2 Provider调用为0；跨Flow upstream task ref失败。
- Provider transport/runtime failure使run FAILED，started call count为1且retry/switch count为0；不得产生FlowInterpretation success publication。
- novel repo label可进入registry但不能产生Fact/locator/Flow/Markdown；同label双Flow产生不同key。
- optional seed exact match、illegal Unicode、unknown/cross-flow key、R2 expansion、missing/duplicate dispositions、count spoof、ineligible leakage和partial-install均有反例。
- 改变shard size或程序遍历顺序不改变registry和public FlowInterpretation bytes。

只有`E`个R0 dispositions、一个registry、`E`个final dispositions、`2R`个R1/R2 tasks以及ineligible零FlowInterpretation artifact约束同时闭合，FlowInterpretation才可交付。

### 8.7 已冻结裁决：实现者不得自由推断

- 仍只有FlowInterpretation可调用LLM；R0是内部模块，不是第九分析步骤。
- BusinessFlows EvidenceCapsule是R0/R1/R2唯一源码语义来源；organization seed仅optional hint。
- R0只能创建registry proposal data，不得创建Fact、locator、Flow、Markdown或跨Flow知识。
- 全部`E`个R0 dispositions闭合后程序恰冻结一个registry；R1/R2只能使用同Flow provisional keys。
- planned tasks恒为`E+2R`，R0/R1/R2 shard denominators恒为`E/R/R`。Provider started failure不自动重试或切换。
- RepositoryKnowledge才做meaning admission，NineSectionDocument才做全仓九章Markdown；不得perFlow渲染或拼接。
- 同一run自动恢复不在active v0；不得重引journal、terminal repair或resume Interface。未来需求只见补充TODO。
- 任何field、key identity、failure、model boundary或cross-analysis step lineage变化必须按DESIGN 13.11 STOP并交Sol/ultra Design Authority；跨分析步骤/业务目标由用户确认。
## 9. 当前实现成熟度审计

Wire Reset后的`org.sourceanalysis.app.analysis.interpretation`与provider adapter目前只有语义package骨架；当前没有Provider Interface、task compiler、runner或解释artifact。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 目标package与JDK 17 Toolchain已就位；通用wire头门禁不理解Flow、Capsule、registry或模型响应。 |
| **本步骤生产能力尚未实现** | M1–M6、R0 proposal/freezer、唯一RepositoryInterpretationRegistry、有限键R1/R2、`E+2R` accounting、三类Provider adapter和十项正式输出均不存在。 |
| **历史证据，不是当前能力** | 已删除的pre-reset路径曾验证bounded scripted R1/R2、runtime identity和有限key。它没有目标R0/freeze合同，并且已不在当前代码树。 |
| **样例边界** | 历史DepotHead 0-Capsule审计只规定未来回归时Provider调用必须为0；当前SourceAnalysis没有jshERP任务、round或调用结果。 |
| **下一实现门** | 按本章先用scripted Provider闭合`E/R/R`分母、同Flow Capsule和task/disposition双射，再实现Codex Subscription/OpenAI-compatible adapter；真实Provider仍需单独授权。 |

上述差距是后续Luna/Terra实施项；不得用已删除两轮实现或模型直读Service绕过R0、Capsule和program admission。
