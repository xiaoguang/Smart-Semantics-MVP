# 业务流程

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。运行顺序只由文件名中的 `05-` 与运行目录 `steps/05-business-flows/` 表达。

本文示例严格使用DESIGN §1.3的`NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN`分类；未标为strict的digest/size/ID不可复制为golden。权威字段表、enum、identity和direct-preimage合同始终exact，不能靠示例降级删除。

## 1. 为什么存在

Fact 说明一件事成立，却没有说明一个请求从哪里开始、经过哪些条件、在哪些终点结束。模型若直接收到 Fact 列表，仍可能把多个入口、分支和副作用拼成一段不存在的故事。

分析步骤“业务流程” 由程序处理 ApplicationDiscovery **全入口 inventory**：每个支持入口编译成 FlowSlice，并为每条 Flow 生成唯一、最小、预算内的 EvidenceCapsule；不能闭合的入口保留有证据的 GAP，明确不在目标 profile 的入口保存 EXCLUDED。这里的 **Flow 是一次入口触发、可独立审计的局部业务活动**；Outcome 是同一局部活动的一种结束方式。跨多个入口才可能形成的端到端 **BusinessProcess** 不在本步骤宣布，而由后续步骤从多条 Flow 的证据连接材料重建。Flow 与 BusinessProcess 是多对多：一个流程可参与多个过程，一个过程也可包含多个流程。DepotHead 只是仓库 `N` 个 FlowSlice 候选中的一个真实例子。

本步骤在不增加文件的前提下，把每条 `FlowSlice` 和对应 `EvidenceCapsule` 升级为携带同一组 `processJoinSignals[]`。这些 signal 只记录可复验的业务对象、Java 类型、表/字段、业务标识、标识传递、状态生产/检查、显式调用/返回/事件/引用，以及可能阻断连接的条件、冲突和 Gap。它们是供下游比较的**连接材料**，不是顺序、因果或端到端过程结论。

## 2. 具体输入与 DepotHead 例子

输入是 分析步骤“应用发现” entry/capability artifacts、分析步骤“程序图” call/control/data/evidence graphs、分析步骤“已证明代码事实” Facts/Proof/Gaps、flow/evidence profiles 和预算。

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本文件可用未来闭合后的Flow/Capsule值展示模块接力；历史pre-reset fixed-slice审计曾得到Gap、0 Flow、0 Capsule，而当前SourceAnalysis尚未运行该slice。技术unknown只用nullable/UNRESOLVED/Gap/fatal表达。

DepotHead **REAL_SOURCE** 包含：

- POST /depotHead/batchSetStatus；
- status 0/1 分支和当前单据状态检查，Service :752-776；
- 强审核/负库存/出入库条件，Service :777-796；
- dhIds 非空后Java-local status赋值与boundary invocation，Service :798-803；
- 库存更新、日志和 return，Service :804-821；
- Mapper Java static target与独立Mapper/XML静态结构；外部持久化效果不在Flow事实中。

目标 Flow 应以 HTTP entry 为根，保留每个 guard polarity 和 terminal。它还可把 `DepotHead` Java type、`ids`/`status` 业务输入、已证明的状态检查、generic Java boundary invocation及其外部效果 Gap记录为本 Flow 的 join signal；这些材料仍不能证明另一入口与它有先后关系，更不能证明数据库已经更新。**但当前结果不是成功 Flow**：固定 jshERP slice 仍是历史pre-reset Gap、0 Flow、0 Capsule，Wire Reset后的当前实现尚未运行这条真实路径。

**示例分类：NARRATIVE_ILLUSTRATION。** 当前诚实 output 形状如下；它是人类可读的审计投影，不是wire record或replay fixture：

~~~json
{
  "entryDisposition": "GAP",
  "flowSliceCount": 0,
  "evidenceCapsuleCount": 0,
  "reasonCodes": ["FLOW_FACT_NOT_ADMITTED", "OUTCOME_BRANCH_UNRESOLVED"]
}
~~~

reasonCodes 表示目标可用 code 示例；实际当前 code/count 以运行 artifact 为准。

### 2.1 明确合成的端到端验收故事

下面场景是 **SYNTHETIC_ACCEPTANCE_SCENARIO（合成验收场景，绝非 jshERP 行为）**：

`提交补货申请 → 门店审批 → 区域审批并创建采购单 → 采购单审批及费用处理 → 执行采购并登记物流 → 收货并登记库存 → 生成、确认、结算月度账单`。

本步骤必须把七个入口分别编译成七条局部 Flow，而不是在这里写成一条长 Flow。每条 Flow 只发布自己能证明的材料，例如 `replenishmentRequestId` 的输入/输出、`REQUEST_SUBMITTED` 或 `STORE_APPROVED` 的状态生产/检查、`purchaseOrderId` 的返回/引用、`STORE_APPROVER` 或 `REGIONAL_APPROVER` 的有限业务角色锚点，以及对应的反向 guard/Gap。即使多个入口共享同一表，也只得到同表锚点；即使方法中文名相似，也不能得到顺序。端到端顺序、并行、备选和回退留给第六步 P1/P2 的受限假设及第七步程序准入。

## 3. 程序怎样工作

1. 重验上游 roots、Facts、Proof、Gaps 和五图 reference closure。
2. 从 ApplicationDiscovery `repositoryEntryCoverage.entryIds` 取得完整入口分母，按 entryId 排序/分片，每个入口创建一个 flow ownership scope。
3. 从 entry method 沿 EXACT call edge、显式 CFG edge 和 call/return pair 遍历。
4. 每个 guard 记录 guardId、同 entry scope 的 `JAVA_GUARD_CONDITION/CONTROL_CONDITION` atom ID、TRUE/FALSE polarity；不从行号猜极性，也不把边界调用的 `CONTROL_CONTEXT` 当条件 atom。
5. 到 return、throw 或 profile 支持的 stop 封闭 OutcomePath；同终点不同 decision sequence 仍是不同 Outcome。
6. 所有 Outcome 求最长公共入口前缀，形成 sharedSteps；分支保留在 Outcomes。
7. 将 Fact/atom/Gap 分配给唯一 Flow；共享子流程只有通过版本化 parent/child ownership rule 才可拆分。
8. 从每条 Flow 已拥有的 admitted Fact/atom、闭合 Proof、Evidence 和 `SourceLocatorV1` 确定性编译 `processJoinSignals[]`。generic audit字段、tenantId、日志、工具类、方法/中文名相似可保留为 `GENERIC_TECHNICAL` 审计材料，但不能伪装成 domain-specific handoff；任何离开 frozen Java 的外部效果同时带 `EXTERNAL_EFFECT_GAP`。
9. 从 Proof root 选择直接表达入口、条件、读取、写入、终点和 join signal 的 source spans；同时确定 `registryProposalBasisAtomIds` 与 `registryProposalBasisGapIds`，它们是 R0 可引用的完整、闭合 basis 分母。
10. 建 projection obligations；每个 atom/Outcome/signal 至少有一个 span或Gap searched-scope义务，且删除任一 span 都会损失义务。R0、R1、R2 必须读取同一 Capsule artifact；下游 P1/P2 只能读取由程序从这些已发布 signal 编译的有界 `ProcessEvidenceGroup`，不能索取其他源码。
11. 对每个 entry 写 COMPILED/GAP/EXCLUDED；验证 entry shard denominator 两两不交叠且 union 等于 ApplicationDiscovery 全 entry IDs。
12. 重算 entry/Outcome/Fact/atom/Gap/capsule/signal/omitted coverage 后原子安装 分析步骤“业务流程”；任意一条 Flow 成功都不能关闭尚未处置的 entry。

## 4. 生成的可观察产物

| 文件 | 唯一职责 |
| --- | --- |
| flow-slices.json | FlowSlice、OutcomePath、steps、facts/atoms/gaps、与 Capsule 相同的 `processJoinSignals[]` 和 parent/child refs |
| flow-coverage.json | 仓库 entry/outcome/call/mapper/fact/atom/evidence、shard 与 unsupported/failed/omitted 的分母与分子；同时保存全部 Flow 的 model eligibility 严格分区和逐 ineligible Flow 的 Gap 映射 |
| entry-dispositions.jsonl | 每个发现入口恰一条 COMPILED/GAP/EXCLUDED |
| evidence-capsules.jsonl | 每个 COMPILED Flow 恰一个完整、模型可读取的 Capsule：facts/gaps/outcomes、R0 basis、`processJoinSignals[]`、连续 source spans 与 projection obligations 一起持久化 |
| flow-gaps.jsonl | blocking/warning Flow-local Gap 及 provenance |
| business-flows-receipt.json | upstream roots、control hashes、artifact descriptors、status与Gap refs/count；Flow/Capsule分母和counts在flow-coverage.json中 |

未来 DepotHead 成功后的 Capsule 只能包含该 Flow 的必要 spans；它属于上述统一 walkthrough 声明：

~~~text
Controller :43,:178-191
Service :741-821
Mapper Java :23
DepotHead :63,:301-307
DepotHeadExample :149-151
boundary call Service :803
~~~

XML spans只可随独立静态结构Fact/Gap进入Capsule，不能证明boundary effect。是否需要更细span由projection obligations决定，不能把上面行段硬编码为golden。

目标出口不会因 0 Flow 变成空结果：六个命名文件必须全部存在；每个 分析步骤“应用发现” entry 在 `entry-dispositions.jsonl` 恰有一条记录，`flow-coverage.json` 保存完整分母、`flowSliceCount=0`、`evidenceCapsuleCount=0`及 shard receipts，`flow-gaps.jsonl` 保存 blocking reason，receipt只绑定这些semantic payload的descriptor/status/Gap refs/count。未来 DepotHead 正向出口则必须有一个入口根 Flow、完整 Outcomes 和恰一个 Capsule，但仓库 analysis step 只有在**其他所有入口也各有 COMPILED/GAP/EXCLUDED**后才闭合，而不是只把 DepotHead count 改成 1。

Artifact cardinality 固定：若完整仓库编译出 `N` 个 FlowSlice，则必有 `N` 个 EvidenceCapsule，且每对保留相同 `flowSliceId` 与逐字相同、按ID排序的 `processJoinSignals[]`；BusinessFlows 不生成 BusinessProcess、解释或 Markdown。每个 Flow/Capsule独立持久化并可被下游按identity读取，最终 public files是所有 shard 的 canonical ID-set union。本步骤仍是五个semantic payload加一个receipt，共六项正式文件。

### 4.1 人类 walkthrough：模块用什么文件接力

**示例分类：NARRATIVE_ILLUSTRATION。** 下面只解释未来 Fact/graph closure 已补齐后的模块接力，不是wire record、identity preimage或跨analysis step replay fixture；当前 implementation audit 的 0/0 不变。

~~~jsonl
{"module":"flow-compiler","artifact":"modules/01-flow-compiler/flow-compilation.json","takesFrom":["ApplicationDiscoveryReference","ProgramGraphsReference","ProvenCodeFactsReference"],"says":{"entryId":"entry:post-depothead-batch-set-status","flowSliceId":"flow:post-depothead-batch-set-status","guard":{"guardNodeId":"node:status-null","conditionAtomId":"atom:status-null-control-condition","trueOutcome":"outcome:reject-empty-status","falseOutcome":"outcome:boundary-invoked"},"factId":"fact:depothead-mapper-boundary-invocation","processJoinSignalKinds":["JAVA_TYPE_ANCHOR","BUSINESS_IDENTIFIER_ANCHOR","STATE_CHECK","EXPLICIT_CALL","EXTERNAL_EFFECT_GAP"],"externalEffectGapId":"gap:depothead-external-update-effect-unproven"}}
{"module":"capsule-projector","artifact":"modules/02-capsule-projector/capsule-projection.json","takesFrom":["flow-compilation.json","Proof/Evidence/source"],"says":{"flowSliceId":"flow:post-depothead-batch-set-status","evidenceCapsuleId":"capsule:post-depothead-batch-set-status","covers":["entry route","dhIds guard","generic boundary target","ordered arguments and origins","both terminals","join signals","external-effect Gap"],"registryProposalBasisAtomIds":["atom:input-field","atom:boundary-invocation"],"registryProposalBasisGapIds":["gap:depothead-external-update-effect-unproven"],"processJoinSignalIds":["process-join-signal:depothead-ids","process-join-signal:external-effect-gap"],"usedBy":["R0","R1","R2","CrossFlowCandidateCompiler"]}}
{"module":"publish","artifacts":"modules/03-publish/<five-semantic-files>","receipt":"modules/03-publish/module-receipt.json","takesFrom":["flow-compilation.json","capsule-projection.json"],"says":{"flowCount":1,"capsuleCount":1,"modelEligibleFlowSliceIds":["flow:post-depothead-batch-set-status"],"modelIneligibleFlowSliceIds":[],"modelIneligibilityGapIds":[],"modelIneligibilityByFlow":[],"semanticFiles":["flow-slices.json","flow-coverage.json","entry-dispositions.jsonl","evidence-capsules.jsonl","flow-gaps.jsonl"],"analysisStepReceipt":null,"nextStep":"CanonicalAnalysisStepArtifactStore"}}
~~~

M1 的 `entryId/factId/outcomePathIds/processJoinSignals` 原样进入 M2；M2 只新增 `evidenceCapsuleId`、直接语义 spans和可重验的model eligibility，并不得改写signal的anchor、direction或basis。上例假设DepotHead Capsule符合模型输入预算，所以进入eligible集合；若它只能安全持久化、却不能安全交给模型，则同一Flow和Capsule仍保留，只移动到`modelIneligibleFlowSliceIds`，并在`modelIneligibilityByFlow`增加该Flow及非空Gap。当前缺 Proof 时 M1 产生 GAP/0 Flow，M2 产生0 Capsule，M3仍发布完整0/0 artifact set，四项eligibility字段和signal集合都显式为空。

## 5. 下游怎样消费而不返工

分析步骤“流程解释” 的本地 R0、R1、R2 每次按 flowSliceId 读取恰好一个 FlowSlice 和一个 EvidenceCapsule。三轮源码语义都必须来自这同一个 persisted Capsule；R0 只能引用 Capsule 明列的 registry proposal basis，R1/R2 不能旁路读取 R0 raw output 之外的源码。只有后续 P1/P2 是批准的 bounded multi-Flow 例外：它们不直接读取多份 Capsule，而只读 `CrossFlowCandidateCompiler` 从全仓已发布 `processJoinSignals` 确定性编成的有界 `ProcessEvidenceGroup`。本地轮次看不到：

- 仓库 root 或任意 Path；
- 其他 Flow/Capsule；
- 未准入 Fact；
- Proof-only dependency nodes；
- 五张完整 graph；
- inventory 外文件。

分析步骤“流程解释” 无权发现 Flow、补 Outcome、修改条件或请求“再读一点仓库”。它也不能用共享表、tenantId、用户审计字段、日志、工具类、方法名/中文名相似来补造流程顺序。Capsule 或 signal 不充分时形成 Gap，或回到新版本 分析步骤“业务流程”/上游 run，而不是模型侧扩展。

### 下游前置条件与后置保证

| 分析步骤“流程解释” 开始前必须成立 | 分析步骤“业务流程” 成功后保证 |
| --- | --- |
| 分析步骤“应用发现” entries、分析步骤“程序图” graph roots、分析步骤“已证明代码事实” Fact/Proof/Gaps 与 分析步骤“业务流程” controls 全一致 | 每个发现 entry 恰一条 COMPILED/GAP/EXCLUDED disposition，入口与 Outcome 分母守恒 |
| 六个 output files、receipt root、Flow/Outcome/Fact/atom/Gap/capsule refs 全闭合 | 每个 COMPILED entry 恰一个入口根 Flow，每个 Flow 恰一个最小 Capsule；GAP/EXCLUDED 没有 Capsule |
| Capsule span hashes、projection obligations、R0 basis allowlist、`processJoinSignals` basis和预算重验通过 | 分析步骤“流程解释” 可从同一Capsule先建一个R0 task，程序freeze registry后再建R1/R2 tasks；另可由程序跨Flow编候选关系/有界组；0 Flow可确定0 Provider call |
| entry shard receipts不交叠且union精确等于ApplicationDiscovery全部entryIds；每entry有唯一disposition | FlowInterpretation只得到`modelEligibleFlowSliceIds`并且不会为ineligible Flow建task；RepositoryKnowledge仍从`flowSliceIds`与`modelIneligibilityByFlow`得到完整全Flow分母；一Flow PASS不足以结束BusinessFlows/run |

分析步骤“流程解释” 若看到多 Capsule、缺 Capsule、未闭合 Outcome 或越界 ref，必须拒绝整个 分析步骤“业务流程” input；不能让模型选择“看哪一份”。

## 6. 成功、Gap、fatal 与显式复用

- **成功**：完整仓库所有 entry 都有唯一 disposition；每个 COMPILED entry 恰一个入口根 Flow/Capsule；两者的 `processJoinSignals` 逐字相同且每条signal都闭合到Fact/Proof/Evidence/精确源码位置或显式Gap；全 shard union、coverage 和 projection closure 闭合。
- **带 Gap 成功**：某 entry 为 GAP，Flow带warning Gap，或某潜在连接只有`EXTERNAL_EFFECT_GAP/COUNTER_CONDITION`。0 Flow/0 Capsule 是合法 SUCCEEDED_WITH_GAPS，并显式推出本地与过程模型调用都为0。
- **fatal**：上游 replay mismatch、graph/proof reference broken、branch polarity/terminal closure broken、signal无owner/无basis/跨Flow偷取/Flow-Capsule不一致、accounting 不守恒、source span 漂移、projection 不可满足或 identity collision。
- **显式复用**：新FlowInterpretation execution重验upstream roots、entry shard receipts和BusinessFlows artifact root；缺/重叠shard不能发布。完全相等才读取；fatal不删除五图/Facts或已完整安装的slice artifacts。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 定义 Flow/Outcome 边界 | 是 | 否 |
| 计算 branch polarity/coverage | 是 | 否 |
| 选择 Capsule spans | 是，按 Proof/obligation | 否 |
| 编译有证据的 `processJoinSignals` | 是，逐Fact/Proof/Evidence | 否 |
| 阅读/命名 Flow | 否，留到 分析步骤“流程解释” | 否 |

本分析步骤产品运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块执行顺序固定为 `EntryRootedFlowCompiler` → `EvidenceCapsuleProjector` → `FlowPublicationSpecifier` → `CanonicalAnalysisStepArtifactStore`。前三个业务模块先安装 canonical artifact；后继只读 artifact，不读取前驱内存图或私有类，analysis step store不是第四个业务模块。

#### M1 EntryRootedFlowCompiler

- **解决的问题**：把每个发现 entry 的 exact graph/Fact closure 编译成一个完整 Flow、多条 Outcomes 或明确 GAP/EXCLUDED。
- **精确上游输入及前置**：valid ApplicationDiscovery complete entry inventory/coverage、ProgramGraphs call/control/data graphs、ProvenCodeFacts **v2** Facts/Proof/Gaps、flow profile/budget；所有 roots/entry/edge/fact refs及ApplicationDiscovery entry denominator已重验。每个分支 edge 的 `guardNodeId` 必须在同 entry scope 匹配恰一条 admitted `JAVA_GUARD_CONDITION` 的 `CONTROL_CONDITION` atom；不匹配不是可由 M1 补写的空字段。
- **确定性顺序 / LLM**：固定全 entryId denominator → 按entryId shard → 建 ownership scope → 沿 EXACT call/CFG/call-return → 枚举 decision sequences/terminals → longest common prefix → 分配 Fact/atom/Gap → 从本Flow闭合Fact/Proof/Evidence编译并去重`processJoinSignals` → per-entry disposition → shard union/account；0 LLM。
- **目标输出与 DepotHead 示例**：`FlowCompilation{entryDispositions,flowSlices,outcomePaths,processJoinSignals,ownership,coverage}`；未来正例为一个POST entry Flow、多Outcome，以及只证明`DepotHead`/`ids`/状态检查/boundary call/外部效果Gap的signal；历史回归例为该entry的GAP/0 Flow与blocking refs。
- **必须保持的不变量**：ApplicationDiscovery每 entry 恰一 disposition；COMPILED 恰一个 root Flow；每 terminal path 恰一 Outcome disposition；每个 TRUE/FALSE pair 共享同一个已证明 condition atom；Fact/atom/Gap/signal owner 唯一；每条positive signal闭合到本Flow Fact/Proof/Evidence/source locator，每条counter/gap signal闭合到本Flow Gap或反证Proof；entry shards不交叠且union等于完整entry denominator。
- **Gap / fatal / artifact复用**：unsupported/ambiguous/unproven path 是 entry/Outcome/connection Gap；缺少或多个同 scope `CONTROL_CONDITION` atom、broken graph/Proof refs、missing polarity/terminal、signal跨Flow或无basis、duplicate owner/accounting fatal；模块每次处理完整entry denominator。
- **给下游的后置保证**：M2 得到闭合 Flow/Outcome/Fact/Proof/signal ownership，或可解释的零 Flow，绝不需要补 path；本保证只提供连接材料，不宣布BusinessProcess。
- **明确非目标**：不选 source spans、不调用模型、不按 terminal 拆多个 Flow、不发明业务名、不比较其他Flow或推断先后。
- **公共测试 seam 与验收**：`compile(entries, graphs, facts, profile)` 覆盖至少两非空entry/Flow、多 Outcome、第二 entry 隔离、其中一entry Gap、缺/重叠 shard、loop/ambiguous Gap、edge deletion fatal、domain-specific/generic/counter signal与 DepotHead 0/0 baseline。guard fixture 必须断言 TRUE/FALSE 两条 Outcome 都引用唯一、相同的 `CONTROL_CONDITION` atom；删除该 Fact、其 Proof、guard Evidence 或复制第二个同scope atom分别 fail closed。共享表/同名方法只形成各自anchor或无signal，绝不能在M1形成顺序。ID-set accounting必须闭合且一Flow PASS不能完成analysis step。
- **Luna/xhigh 测试指南**：创建`EntryRootedFlowCompilerTest`，冻结ApplicationDiscovery、ProgramGraphs与 **v2** ProvenCodeFacts files、多Outcome fixture及由历史审计提炼的DepotHead 0/0 golden于`src/test/resources/analysis/flow/flow-compiler/`。逐RED：单entry多Outcome、同 guard 的 true/false shared condition atom、缺/重复/foreign condition atom、第二entry隔离、shared prefix、loop/ambiguous Gap、join signal的Fact/Proof/Evidence/source closure、generic audit字段、共享表/同名方法不得产生顺序、external-effect Gap、edge/polarity deletion、0/0 disposition、order determinism；首RED因v2 seam/schema缺失。只fake artifact reader，flow/signal/accounting不可mock。命令：`mvn -Dtest=EntryRootedFlowCompilerTest test`；禁网络/客户执行。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后只改 Java package `analysis/flow/compiler/`（`org.sourceanalysis.app.analysis.flow.compiler`），实现 public `EntryRootedFlowCompiler/FlowCompilation` 与 `business-flows-flow-compilation-v2`；只读ApplicationDiscovery、ProgramGraphs与 **v2** ProvenCodeFacts artifacts，entry→traversal→terminal paths→shared prefix→ownership→signal extraction/accounting。`conditionAtomId` 必须逐字来自 reopened `JAVA_GUARD_CONDITION/CONTROL_CONDITION`；不得以guard ID、文本、哈希或`CONTROL_CONTEXT`替代。signal只可从closed Fact/Proof/Evidence/Gap产生；不得行号补路、按terminal拆Flow、比较其他Flow、硬编码DepotHead或推断external effect。需新graph/Fact字段MUST STOP交Sol/ultra并按跨analysis step规则升级，完成更新审计。持久化 module key、module artifact 目录及 fixture 的末段仍严格是 `flow-compiler`；它是 wire key，不是 Java package 名称。

#### M2 EvidenceCapsuleProjector

- **解决的问题**：为每个 compiled Flow 从 Proof roots 选择唯一、最小、预算内、模型可读的 source投影。
- **精确上游输入及前置**：M1 FlowCompilation artifact、ProvenCodeFacts ProofPack、ProgramGraphs Evidence graph、VerifiedSourceInventory source handles、projection profile/budget；每 Flow/Outcome/atom/proof ref 闭合。
- **确定性顺序 / LLM**：按 flowSliceId → 枚举 ATOM/OUTCOME obligations → 从 Proof roots取 direct-semantic spans → stable set cover → exact excerpt/hash → deletion minimality/closure check；0 LLM。
- **目标输出与 DepotHead 示例**：`CapsuleProjection{capsules,modelEvidenceSpans,projectionObligations,flowShardReceipts,budgetUsage}`；每个Capsule内嵌`factViews/gapViews/outcomePathViews/processJoinSignals`。DepotHead Flow给出入口、guards、Java-local write、generic boundary invocation、ordered arguments、terminals、join anchors和external-effect Gap；不能给出数据库write/where outcome。
- **必须保持的不变量**：`N` compiled Flow↔`N` Capsule 一一对应；每对的`processJoinSignals`逐字段、逐顺序相等；flow shards不交叠且union等于M1 flowSliceIds；每 obligation有支持且每 span不可冗余；每个 view/signal 都是同 Flow 已持久化 Fact/Gap/Outcome/Proof 的逐字段受控投影，不能用摘要、模型文本或外部查找替代；span只来自 own Flow Proof、原始 bytes不 trim。
- **Gap / fatal / artifact复用**：无安全 split 的预算超限使对应 Flow blocking Gap；source drift、cross-Flow span、unsatisfied/redundant projection、ref broken fatal；projector只从完整affected Flow及其verified inputs计算。
- **给下游的后置保证**：M3/FlowInterpretation 可按 flowSliceId 得到恰一个 closed、evidence-complete Capsule：它已经携带该 Flow 的 facts、gaps、outcomes、signals、spans、obligations 与 R0 basis allowlist；R0/R1/R2共享该artifact，`CrossFlowCandidateCompiler`只读取已发布signals；模型不需Path/graphs/未界定的其他Flow，也不得从别的artifact补内容。
- **明确非目标**：不缩减 ProofPack本身、不解释业务、不让 Provider请求额外源码。
- **公共测试 seam 与验收**：`project(flowCompilation, proofs, evidence, source)` 使用至少两Flow，对每span deletion、proof-only injection、cross-Flow借用、source mutation、缺/重叠shard和预算边界；另断言R0 basis非空、闭合、同Flow且R0/R1/R2 sourceArtifactId完全相等。正例每Flow独立obligations闭合且删除任一span失败。
- **Luna/xhigh 测试指南**：创建 `EvidenceCapsuleProjectorTest`，fixtures/goldens在 `src/test/resources/analysis/flow/capsule-projector/`。RED顺序：一个Flow完整obligations/spans→signal逐字复制/basis closure→R0 basis closure→R0/R1/R2同artifact→逐span deletion→proof-only冗余→cross-Flow借用→source drift→budget no-safe-split Gap→root determinism；首RED因v5 projector/schema缺失。仅fake source handle，set-cover/obligation/signal/canonical不可mock。命令：`mvn -Dtest=EvidenceCapsuleProjectorTest test`；无Provider/network。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 Java package `analysis/flow/capsule/`（`org.sourceanalysis.app.analysis.flow.capsule`），实现 public `EvidenceCapsuleProjector/CapsuleProjection` 与 `business-flows-capsule-projection-v5`；消费M1 v2+Proof/Evidence/source，obligation→eligible spans→stable set cover→连续 `SourceExcerptV1`→完整 Fact/Gap/Outcome/signal view→R0 basis closure。逐RED GREEN；不得多给源码、跨Flow、trim bytes、合成不连续excerpt、以摘要替代view、改写signal，或给R0/R1/R2不同source。持久化 module key、module artifact 目录及 fixture 的末段仍严格是 `capsule-projector`；它是 wire key，不是 Java package 名称。缺Proof语义/需跨analysis step变更MUST STOP交Sol/ultra并按跨analysis step规则升级，完成更新审计。

#### M3 FlowPublicationSpecifier

- **解决的问题**：组合 Flow 与 Capsule artifacts，守恒全部 entry/outcome/fact/atom/Gap并形成五个semantic payload；analysis step store独占root/receipt与BusinessFlowsReference。
- **精确上游输入及前置**：M1全仓 FlowCompilation/shard receipts、M2全 Flow CapsuleProjection/shard receipts、ApplicationDiscovery、ProgramGraphs与ProvenCodeFacts roots和BusinessFlows controls；完整entry dispositions与Flow/Capsule双射或明确0/0已局部验证；每个Capsule已有唯一`modelEligibility`和本Flow内可重验的`modelIneligibilityGapIds[]`。
- **确定性顺序 / LLM**：验证entry/flow shard disjoint union → join by flowSliceId并逐字比较Flow/Capsule signal集合 → 由Capsule逐项重算`modelEligibleFlowSliceIds`、`modelIneligibleFlowSliceIds`和`modelIneligibilityByFlow` → 重算全仓 coverage/dispositions/unsupported/failed/omitted/signal ownership → canonical五个semantic payload → M3 install/receipt → analysis step store fresh reopen/root/receipt-last；0 LLM。
- **目标输出与 DepotHead 示例**：M3恰五个semantic files；analysis step store形成五项+receipt的六文件reader-visible set。历史回归例要求entry-dispositions有DepotHead GAP、flow/capsule及eligibility分区均为空、Gap/coverage/receipt非空；未来正例在`flow-coverage.json`中把每个Flow精确分到eligible或ineligible，并为后者保存非空Gap映射。
- **必须保持的不变量**：每 COMPILED Flow恰一个 Capsule，GAP/EXCLUDED无 Capsule；`flowSliceIds`必须是eligible与ineligible的互斥并集，mapping domain必须逐字等于ineligible集合且mapping Gap union逐字等于`modelIneligibilityGapIds`；每个公开 Capsule line 必须内嵌其`modelEvidenceSpanIds[]`对应的完整`modelEvidenceSpans[]`以及`projectionObligationIds[]`对应的完整`projectionObligations[]`，两个 ID-set 分别严格相等。这样FlowInterpretation只重开BusinessFlows五项public artifacts就能得到完整、同Flow、可重验的模型材料；它不得读取M2 private module或重开源码补内容。所有ApplicationDiscovery entry唯一处置，single Flow PASS不等于analysis step/run完成；semantic files只引用M1/M2 IDs；M3不含或预报analysis step root/receipt且不改语义。
- **Gap / fatal / artifact复用**：合法0/0为SUCCEEDED_WITH_GAPS；orphan/multiple Capsule、coverage/ref/canonical/install/collision错误 fatal；下游只认完整receipt。
- **给下游的后置保证**：FlowInterpretation能纯 artifact-driven 地从eligible集合得到task cardinality，RepositoryKnowledge能从全Flow集合与逐Flow mapping得到total decision denominator；0 Flow严格推出0 task/call。
- **明确非目标**：不修 Flow/Capsule、不重读 source、不调用 Provider。
- **公共测试 seam 与验收**：`specify(flowCompilation, capsuleProjection, controls)` 覆盖0/0、至少2 Flow/2 Capsule且一eligible一ineligible、one-to-one、一个Flow Gap、single-PASS/other-omitted、eligibility overlap/omission、mapping空Gap/foreign Gap/union drift、缺/重叠 shard、orphan/duplicate、乱序、partial-install/collision；只有M3 exact-five、analysis-step-store exact-six和仓库ledger闭合才返回BusinessFlowsReference。
- **Luna/xhigh 测试指南**：创建 `BusinessFlowsPublicationSpecifierTest`，module files/goldens放 `src/test/resources/analysis/flow/publish/`。逐RED：M3 exact-five、analysis step exact-six、0/0、one Flow/one Capsule、eligible/ineligible exact partition、mapping domain/非空Gap/union closure、orphan/duplicate、coverage count spoof、receipt-last/partial-install/collision/fresh-reopen；使用真实module/analysis step stores，join/accounting/canonical/root不mock。命令：`mvn -Dtest=BusinessFlowsPublicationSpecifierTest test`；禁网络/Provider/customer Maven。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后只改 `analysis/flow/publish/`，实现 public `FlowPublicationSpecifier/BusinessFlowsReference`；只读M1/M2 module artifacts，join→eligibility partition/mapping→coverage→five semantic files→M3 install/receipt→typed analysis-step-store receipt-last。不得生成旧single publication summary、从Gap猜eligibility、预报root/receipt或补Flow/Capsule；0/0必须让direct Provider seam 0调用。跨analysis step contract改变MUST STOP交Sol/ultra/用户，更新审计。

### 8.0.1 模块 artifact wire schemas

M1/M2使用 DESIGN 13.3 `ModuleArtifact<T>` envelope；M3直接安装五个analysis step schema注册的JSON/JSONL semantic bytes而无summary envelope。`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-flow-compiler/flow-compilation.json` | `business-flows-flow-compilation-v2` / `BUSINESS_FLOWS_FLOW_COMPILATION` | exact ApplicationDiscovery `capability-report/entry-points`、ProgramGraphs七项、ProvenCodeFacts `fact-accounting/gap-ledger/proof-pack/proven-facts` ArtifactReferences，以及 content-addressed `flowCompilationProfile` | `flowCompilationId!`、`flowCompilationProfile!{profileRef!,maxFlows!,maxOutcomesPerFlow!,maxFlowNodes!,maxFlowEdges!,maxTraversalDepth!,maxProcessJoinSignalsPerFlow!,maxProcessJoinSignalBasisRefs!}`、`entryDispositions[]!{entryId!,disposition!,flowSliceId?,gapIds[]!,reasonCode?,evidenceRefs[]!}`、`flowSlices[]!`（每条含完整`processJoinSignals[]!:ProcessJoinSignalV1`）、`flowGaps[]!{gapId!,scope!,reasonCode!,affectedSemanticIds[]!,evidenceNodeIds[]!}`、`entryShardReceipts[]!{shardId!,denominatorEntryIds[]!,dispositionEntryIds[]!,flowSliceIds[]!,status!,gapIds[]!}`、`coverage!`；entries/flows/signals/gaps/shards按ID，steps/decisions保持语义顺序，outcomes按outcomePathId |
| `modules/02-capsule-projector/capsule-projection.json` | `business-flows-capsule-projection-v5` / `BUSINESS_FLOWS_CAPSULE_PROJECTION` | exact M1 v2、VerifiedSourceInventory两项、ProgramGraphs七项、ProvenCodeFacts四项 ArtifactReferences | `capsuleProjectionId!`、`flowCompilationRef!`、`proofPackRef!`、`capsuleProjectionProfile!`、`capsules[]!{evidenceCapsuleId!,flowSliceId!,proofPackId!,modelEligibility!,modelIneligibilityGapIds[]!,entryView!:FlowEntryViewV1,factViews[]!:FlowFactViewV1,gapViews[]!:FlowGapViewV1,outcomePathViews[]!:FlowOutcomePathViewV1,processJoinSignals[]!:ProcessJoinSignalV1,registryProposalBasisAtomIds[]!,registryProposalBasisGapIds[]!,modelEvidenceSpanIds[]!,projectionObligationIds[]!,budgetUsage!}`、`modelEvidenceSpans[]!{spanId!,sourceExcerpt!:SourceExcerptV1,supportedAtomIds[]!,supportedOutcomePathIds[]!,supportedProcessJoinSignalIds[]!}`、`projectionObligations[]!{obligationId!,kind!,semanticItemId!,satisfyingSpanIds[]!}`、`budgetUsage!`；capsules/signals/spans/obligations按ID，fact/gap/outcome views按其stable ID，basis IDs按UTF-8 byte order |
| `modules/03-publish/<five registered semantic filenames>` | 各public schema/type；无summary envelope | M1+M2 IDs/SHAs | 一次module install恰`flow-slices.json/flow-coverage.json/entry-dispositions.jsonl/evidence-capsules.jsonl/flow-gaps.jsonl`；其中`flow-coverage.json`的`RepositoryFlowCoverage`必须包含8.1列出的四项model eligibility字段及完整mapping；module receipt绑定五descriptors；禁止analysis step root/receipt或六项published list；AnalysisStep store provenance绑定M3 reference |

M3的完整module fixture必须用一次install request/receipt绑定表中M1/M2两个ArtifactReferences；五个standalone payload不得重复envelope。只给payload而省略该排序upstream集合，不是完整M3 fixture。

`flow-slices.json`升级为`business-flows-flow-slices-v2` / `BUSINESS_FLOWS_FLOW_SLICES`，其中每条Flow必须含完整`processJoinSignals[]`。`evidence-capsules.jsonl`升级为`business-flows-evidence-capsule-v3` / `BUSINESS_FLOWS_EVIDENCE_CAPSULE`。除已有的`EvidenceCapsule`字段外，line必须逐字复制同Flow的`processJoinSignals[]!`，按`spanId`包含`ModelEvidenceSpanV4`的`modelEvidenceSpans[]!{spanId!,sourceExcerpt!:SourceExcerptV1,supportedAtomIds[]!,supportedOutcomePathIds[]!,supportedProcessJoinSignalIds[]!}`，并按`obligationId`包含`projectionObligations[]!{obligationId!,kind!,semanticItemId!,satisfyingSpanIds[]!}`。每个embedded signal/span/obligation只能由该line的ID列表引用；每个列表ID必须有且只有一个完整embedded value。它们是同一Flow的public evidence handoff，不是额外的semantic文件，也不改变BusinessFlows的五项payload/六项reader-visible输出数量。因为line携带冻结源码摘录，后续Artifact API必须按其raw-source policy提供metadata-only访问；FlowInterpretation本地轮次内部可fresh-reopen同一Capsule，CrossFlowCandidateCompiler保存这份path-bearing persisted material供程序重验，M7再按Step 06的精确字段映射生成独立、path-free的`ProcessModelPacketV1`。模型绝不直接接收本行或`SourceLocatorV1`。

FlowSlice/Outcome/BranchDecision字段按8.1；COMPILED entry的flowSliceId non-null且gapIds可为空，GAP/EXCLUDED的flowSliceId必须null并有reason/Gap。技术示例的静态unknown必须成为GAP disposition，不能用示例Outcome补齐。`ModelEvidenceSpanV4.sourceExcerpt`必须是DESIGN §13.2的完整`SourceExcerptV1`，其`rawUtf8`恰为locator半开连续区间的原始bytes；不连续证据必须拆成多个span，禁止使用`+`、`...`或重排后的合成excerpt。任何schema/ownership/projection/sort/identity变化先设计+升version，下游不可回读graph/source补字段。

`repositoryFlowCoverage.closed` 只有在 ApplicationDiscovery repositoryEntryCoverage已闭合、全部entry/flow shards守恒且每entry处置完成时为true；DepotHead bounded示例固定为false，即使局部一Flow/一Capsule双射成立。

**阅读示意，不是 wire、schema 或 replay fixture。** 早期长 JSON 包络曾把 Capsule 写成 ID 列表，无法保证模型实际读到 Fact、Gap 和 Outcome 的值，现已从目标设计移除。实现者只能遵循上表的 v5 结构、§8.1 records 与 FlowInterpretation 的逐字复制规则。

~~~text
DepotHead flow capsule (illustrative):
  fact view: Java invokes DepotHeadMapper.updateByExampleSelective(record, example)
             with recorded ordered arguments and Java-local origins
  outcome view A: eligible document IDs empty → return before boundary
  outcome view B: eligible IDs non-empty → boundary invocation reached
  gap view: external database update/filter effect is not statically proved
  evidence: route, id collection/guard, setter, Java invocation locator/rule
~~~

**示例分类：STRUCTURAL_WIRE_SPECIMEN（isolated `ModelEvidenceSpanV4` variants）。** 下列两条分别表示同一文件中不连续的class/method annotation；每条字段和类型完整、半开byte区间与所示ASCII `rawUtf8`长度一致，并按locator排序。digest只满足grammar且未从展示bytes重算，所以不能replay；二者不得合并为带` + `的伪raw excerpt。为完整展示v4，两个record都显式携带`supportedProcessJoinSignalIds`。

~~~jsonl
{"spanId":"span:controller-class-route","sourceExcerpt":{"locator":{"fileId":"file:2222222222222222222222222222222222222222222222222222222222222222","path":"src/main/java/example/DepotHeadController.java","startByte":1000,"endByteExclusive":1029,"startLine":43,"startColumn":1,"endLine":43,"endColumn":30},"rawUtf8":"@RequestMapping(\"/depotHead\")","rawUtf8Sha256":"1111111111111111111111111111111111111111111111111111111111111111"},"supportedAtomIds":["atom:entry-route"],"supportedOutcomePathIds":["outcome:boundary-invoked"],"supportedProcessJoinSignalIds":[]}
{"spanId":"span:controller-method-route","sourceExcerpt":{"locator":{"fileId":"file:2222222222222222222222222222222222222222222222222222222222222222","path":"src/main/java/example/DepotHeadController.java","startByte":5000,"endByteExclusive":5031,"startLine":178,"startColumn":1,"endLine":178,"endColumn":32},"rawUtf8":"@PostMapping(\"/batchSetStatus\")","rawUtf8Sha256":"2222222222222222222222222222222222222222222222222222222222222222"},"supportedAtomIds":["atom:entry-route"],"supportedOutcomePathIds":["outcome:boundary-invoked"],"supportedProcessJoinSignalIds":["process-join-signal:1111111111111111111111111111111111111111111111111111111111111111"]}
~~~

### 8.1 Interface 与 records

~~~java
interface EntryRootedFlowCompiler {
    FlowCompilation compile(ApplicationDiscoveryReference entries,
                            ProgramGraphsReference graphs,
                            ProvenCodeFactsReference facts,
                            FlowCompilationProfile profile);
}

record FlowCompilationProfile(
    ArtifactReference profileRef,
    int maxFlows,
    int maxOutcomesPerFlow,
    int maxFlowNodes,
    int maxFlowEdges,
    int maxTraversalDepth,
    int maxProcessJoinSignalsPerFlow,
    int maxProcessJoinSignalBasisRefs) {}
~~~

这里的 `FlowCompilation` 是 M1 的不可变编译结果；M1 publisher 将它立即安装为
`flow-compilation.json`，M2 只能重新打开该文件，不能读取这个内存对象。
`BusinessFlowsReference` 只由 M3 `FlowPublicationSpecifier` 在五个正式语义文件和
receipt 均安装后返回。`profileRef` 是调用者提供、内容寻址的分析配置身份；五个既有预算字段及两个新增signal上限均为正整数，并原样写入 M1 artifact，因此相同输入与 profile 才可得到相同结果。
任何超限都形成对应 Flow 的 Gap 或明确 fatal，绝不截断遍历后把残余路径当作完整 Flow。

~~~text
FlowSlice
  flowSliceId
  entryId
  trigger
  rootNodeId
  sharedSteps[]
  factIds[]
  atomIds[]
  outcomePaths[]
  gapIds[]
  processJoinSignals[]: ProcessJoinSignalV1
  parentFlowSliceId?
  childFlowSliceIds[]

OutcomePath
  outcomePathId
  decisions[]
  terminalNodeId
  terminalKind
  terminalFactIds[]
  requiredAtomIds[]
  requiredProofIds[]

BranchDecision
  guardNodeId
  conditionAtomId                     // exactly one same-entry JAVA_GUARD_CONDITION / CONTROL_CONDITION atom
  polarity
  normalizedCondition

EvidenceCapsule
  evidenceCapsuleId
  flowSliceId
  proofPackId
  modelEligibility: ELIGIBLE | INELIGIBLE
  modelIneligibilityGapIds[]               // empty iff ELIGIBLE; nonempty iff INELIGIBLE
  entryView: FlowEntryViewV1
  factViews[]: FlowFactViewV1
  gapViews[]: FlowGapViewV1
  outcomePathViews[]: FlowOutcomePathViewV1
  processJoinSignals[]: ProcessJoinSignalV1
  registryProposalBasisAtomIds[]
  registryProposalBasisGapIds[]
  modelEvidenceSpanIds[]
  projectionObligationIds[]
  budgetUsage

FlowEntryViewV1                           // exact bounded projection of the owning entry/Flow root
  entryId, trigger, rootNodeId
  routeEvidenceRefs[]

FlowFactViewV1                            // exact bounded projection of one admitted ProvenCodeFacts CodeFact
  factId, kind, subjectNodeIds[]
  atoms[] {atomId, role, name, value{type,canonical}, proofId}
  originFactArtifactRef

FlowGapViewV1                             // exact bounded projection of one Flow-local or fact-owned Gap
  gapId, scope=FLOW|OUTCOME|FACT|ATOM
  reasonCode, affectedSemanticIds[]
  evidenceRefs[], originGapLedgerRef

FlowOutcomePathViewV1                     // exact copy of the owning FlowSlice outcome
  outcomePathId, decisions[]
  terminalNodeId, terminalKind
  terminalFactIds[], requiredAtomIds[], requiredProofIds[]

ModelEvidenceSpanV4
  spanId
  sourceExcerpt: SourceExcerptV1
  supportedAtomIds[]
  supportedOutcomePathIds[]
  supportedProcessJoinSignalIds[]

ProcessJoinSignalV1
  processJoinSignalId
  flowSliceId
  signalKind: BUSINESS_OBJECT_ANCHOR | JAVA_TYPE_ANCHOR | SQL_TABLE_ANCHOR |
              FIELD_ANCHOR | BUSINESS_IDENTIFIER_ANCHOR | IDENTIFIER_OUTPUT |
              IDENTIFIER_INPUT | STATE_PRODUCTION | STATE_CHECK | EXPLICIT_CALL |
              RETURN_TRANSFER | EVENT_REFERENCE | OBJECT_REFERENCE |
              COUNTER_CONDITION | CONFLICT_STATE | EXTERNAL_EFFECT_GAP
  anchorKind: BUSINESS_OBJECT | JAVA_TYPE | SQL_TABLE | FIELD |
              BUSINESS_IDENTIFIER | CALL_TARGET | RETURN_VALUE | EVENT |
              STATE | CONDITION | GAP
  anchorKey
  direction: PRODUCES | CONSUMES | CHECKS | REFERENCES | INVOKES |
             RETURNS | BLOCKS | UNKNOWN
  specificity: DOMAIN_SPECIFIC | GENERIC_TECHNICAL
  claimScope: FROZEN_JAVA | STATIC_STRUCTURE | GAP_ONLY
  factIds[]
  atomIds[]
  proofIds[]
  evidenceNodeIds[]
  sourceLocators[]: SourceLocatorV1
  gapIds[]

RepositoryFlowCoverage
  entryIds[]
  compiledEntryIds[]
  gappedEntryIds[]
  excludedEntryIds[]
  flowSliceIds[]
  capsuleIds[]
  processJoinSignalIds[]
  modelEligibleFlowSliceIds[]
  modelIneligibleFlowSliceIds[]
  modelIneligibilityGapIds[]
  modelIneligibilityByFlow[] {flowSliceId, gapIds[]}
  entryShardReceiptIds[]
  flowShardReceiptIds[]
  closed
~~~

`ProcessJoinSignalV1`是单Flow事实投影，不是跨Flow边。positive kind的exact闭集是`BUSINESS_OBJECT_ANCHOR | JAVA_TYPE_ANCHOR | SQL_TABLE_ANCHOR | FIELD_ANCHOR | BUSINESS_IDENTIFIER_ANCHOR | IDENTIFIER_OUTPUT | IDENTIFIER_INPUT | STATE_PRODUCTION | STATE_CHECK | EXPLICIT_CALL | RETURN_TRANSFER | EVENT_REFERENCE | OBJECT_REFERENCE`；**这十三种中的每一种**都必须至少有一个同Flow `factId → atomId → proofId → evidenceNodeId → sourceLocator`闭合链，任何一个数组为空或任一hop不属于同一Proof closure都fatal。`COUNTER_CONDITION | CONFLICT_STATE`必须有同样闭合的反证链或非空`gapIds`加searched source locator；`EXTERNAL_EFFECT_GAP`必须有非空`gapIds`并以boundary调用位置作为searched source locator。`claimScope=STATIC_STRUCTURE`只允许陈述类型/表/字段/XML结构相关，不能表达已执行效果；`claimScope=GAP_ONLY`不能作为positive handoff。`GENERIC_TECHNICAL`明确覆盖tenantId、用户审计字段、日志、通用工具类等审计材料，下游不得仅凭它生成候选关系。方法名或中文名相似从来不是本record的合法basis。

direction是可验证语义，不是下游自行解释的提示：`COUNTER_CONDITION | CONFLICT_STATE | EXTERNAL_EFFECT_GAP`三种counter kind的`direction`必须逐字为`BLOCKS`，十三种positive kind禁止`BLOCKS`。因此Step 06可把进入某候选关系scope的全部counter signal确定性地视为blocking，而不需要在本record新增或猜测`blocking`字段；kind/direction组合不合法即`PROCESS_JOIN_SIGNAL_BASIS_INVALID`。

`BUSINESS_OBJECT_ANCHOR | OBJECT_REFERENCE`仍是本Flow内部的positive material，Step 05不比较两个Flow、也不把它改写成`BLOCKS`。但是每个Flow必须分别保留其全部`DOMAIN_SPECIFIC`、Proof闭合的业务对象signal；Step 06在一条已由其他合法positive pair形成的候选关系上比较两端**完整对象anchorKey集合**。若两端集合都非空且交集为空，两个完整signal-ID集合的规范union就是该关系的`DIFFERENT_BUSINESS_OBJECT` relation-level `COUNTER_SIGNAL` basis并进入blocking集合。该派生不新增或改写Step 05 signal、不凭对象不同单独成边，也不得从不完整抽样或任意一对对象作结论。

#### 8.1.1 当前 ProvenCodeFacts v2 的有限 signal 提取表

下表是当前已持久化 `JAVA_BOUNDARY_INVOCATION` 与 `JAVA_GUARD_CONDITION` taxonomy 的**完整**提取集合，不是十六种kind的猜测性实现。每个 admitted boundary Fact 独立产生表中适用的record；不同Fact即使key相同也不合并basis。M1只可扩展自己的private typed view并重开既有ProgramGraphs/ProvenCodeFacts bytes，不得读取Fact步骤的内存对象或重解析源码。

| 可用且必须逐字段验证的 persisted basis | 输出 `signalKind / anchorKind / anchorKey` | direction / specificity / claimScope | signal basis与额外门 |
| --- | --- | --- | --- |
| 一个 admitted `JAVA_BOUNDARY_INVOCATION` Fact的唯一`STATIC_TARGET_TYPE{role=ATTRIBUTE,type=STRING}` atom；typed data-flow boundary的`staticTargetType`必须逐字相等 | `JAVA_TYPE_ANCHOR / JAVA_TYPE / STATIC_TARGET_TYPE.canonical` | `REFERENCES / GENERIC_TECHNICAL / STATIC_STRUCTURE` | `factIds`只含该Fact，`atomIds`只含该atom，proof/evidence/locator按下述exact closure；仓库内声明type也不能仅凭“属于本仓库”升级为domain-specific |
| 同一个boundary Fact的唯一`INVOCATION_CALL_ID{RELATIONSHIP,SYMBOL_REF}`、`STATIC_TARGET_TYPE{ATTRIBUTE,STRING}`、`STATIC_TARGET_METHOD{ATTRIBUTE,STRING}`、`STATIC_TARGET_SIGNATURE{ATTRIBUTE,STRING}`；四值须逐字等于同subject typed boundary，且signature必须以`method + "("`开头并以`)`结束 | `EXPLICIT_CALL / CALL_TARGET / STATIC_TARGET_TYPE.canonical + "#" + STATIC_TARGET_SIGNATURE.canonical` | `INVOKES / GENERIC_TECHNICAL / FROZEN_JAVA` | basis恰为该Fact及上述四atom的closed Proof union；它证明冻结Java的确切调用，不证明callee外部效果。Step 06仅在该key逐字等于某entry exact target时才可将其作为direct-call `PROVEN_HANDOFF` |
| 上一行boundary的typed `controlContext`含non-null guard ID/polarity；唯一同Flow `JAVA_GUARD_CONDITION/CONTROL_CONDITION{CONDITION,STRING}` Fact逐字匹配该guard；完整TraversalPath集合同时含该guard TRUE/FALSE outcomes，boundary control block只出现在记录polarity一侧且不出现在另一侧 | `COUNTER_CONDITION / CALL_TARGET / 与该boundary EXPLICIT_CALL相同的key` | `BLOCKS / GENERIC_TECHNICAL / FROZEN_JAVA` | basis恰为guard Fact的`CONTROL_CONDITION`以及boundary Fact的`INVOCATION_CALL_ID,STATIC_TARGET_TYPE,STATIC_TARGET_METHOD,STATIC_TARGET_SIGNATURE,CONTROL_CONTEXT` atoms及其closed Proof union；任一双极性/唯一性/路径门不成立则不发此signal，不能把裸guard或任意状态词改写成counter/state signal |
| `gap-ledger.json`中唯一`kind=EXTERNAL_EFFECT,code=DATA_FLOW_BINDING_UNPROVEN` Gap的singleton `affectedCandidateDenominatorKeys`逐字等于一个admitted boundary Fact的`candidateDenominatorKey`，且其Evidence closure含typed boundary的确切invocation locator | `EXTERNAL_EFFECT_GAP / CALL_TARGET / 与该boundary EXPLICIT_CALL相同的key` | `BLOCKS / GENERIC_TECHNICAL / GAP_ONLY` | 使用该boundary的四个call atoms/Proof、该Gap ID及Gap Evidence；`gapIds`恰含该Gap。缺失、重复、foreign candidate或没有boundary locator均为`PROCESS_JOIN_SIGNAL_EXTERNAL_EFFECT_UNPROVEN`，不得声称外部写入/状态变化 |

当前`ProgramGraphsPublicFixture.createWithGuardedApprove`的early-return guard只标注`guard → continuation block`边；`continuation block → approvalClient.record` call-site是无guard的`NEXT`边，所以该boundary持久化的`controlContext.guardNodeId/polarity`均为null。独立guard Fact和TRUE/FALSE Outcomes仍必须保留并闭合，但它们不能替代typed-boundary link；因此该fixture按上一表必须不发`COUNTER_CONDITION`。counter正例另由后续public stored-artifact fixture/rule同时证明non-null boundary context、唯一matching guard Fact和双极性path gate，不得为挽救fixture而弱化规则或改ProgramGraphs合同。

表中每条signal的数组都按以下同一规则物化：`factIds`、`atomIds`为该行列出的exact set；`proofIds`恰为这些FactAtom的`proofId` set，且每个Proof必须`status=CLOSED`并反向逐字指向同一`factId/atomId`；`evidenceNodeIds`恰为这些Proof的`requiredEvidenceNodeIds`规范union，`EXTERNAL_EFFECT_GAP`再并入Gap的`evidenceNodeIds`；`sourceLocators`恰为该evidence set中所有`SOURCE_EXCERPT` node的完整`SourceLocatorV1`规范union，rule-application node没有locator但仍留在evidence集合。任何丢失、foreign或额外hop，以及atom role/type/name不符，是malformed proven basis并fatal，不能按“不支持”静默省略。

当前taxonomy对`BUSINESS_OBJECT_ANCHOR | SQL_TABLE_ANCHOR | FIELD_ANCHOR | BUSINESS_IDENTIFIER_ANCHOR | IDENTIFIER_OUTPUT | IDENTIFIER_INPUT | STATE_PRODUCTION | STATE_CHECK | RETURN_TRANSFER | EVENT_REFERENCE | OBJECT_REFERENCE | CONFLICT_STATE`没有可用atom，因而这些family在当前Flow中是exact absence；裸参数名、条件文本、ordered argument/origin node ID、repository-owned type、Mapper/XML文本或方法名都不能补造它们。无候选语义时不为每个缺失family制造Gap；已有上游Gap仍逐字保留。若某upstream record自称提供表中支持的basis却closure malformed，则按上一段fatal，而不是降级成absence。

因此当前有限规则最多交付四种family的可信结构材料和counter/Gap，但首个3/3纵切只覆盖`JAVA_TYPE_ANCHOR`、`EXPLICIT_CALL`和`EXTERNAL_EFFECT_GAP`，不构成counter正例验收。它没有任何可证明的`DOMAIN_SPECIFIC` anchor，不能单独产生Step 06 `SHARED_ANCHOR` relation，也不满足本步骤最终的domain/generic/counter综合验收。仓库内声明的exact user type仍可能被`EXPLICIT_CALL → exact entry target`规则使用，但“在仓库中声明”无法区分业务类型与repository-local logger/util，所以不能作为domain specificity。完整过程重建仍必须在已批准计划内补齐Proof闭合的domain Fact/classification能力，且这是Step 05完整验收及Step 06有效process reconstruction的前置条件；当前有限纵切不替它设计新字段或schema，后续由Sol/ultra在既有范围内作有界合同决定。

`processJoinSignalId`覆盖全部上述字段，排除且只排除self ID：

~~~text
processJoinSignalId = "process-join-signal:" + lowercaseHex(SHA-256(
    frame(UTF8("business-flows-process-join-signal-id-v1")) ||
    frame(canonicalJson(signalWithoutProcessJoinSignalId))))
~~~

数组按UTF-8 ID排序去重，`sourceLocators[]`按`(path,startByte,endByteExclusive)`排序；同一`processJoinSignalId`必须在`FlowSlice.processJoinSignals[]`和对应`EvidenceCapsule.processJoinSignals[]`逐字一致。任何新增或改义signal字段须升级BusinessFlows schemas。

**示例分类：STRUCTURAL_WIRE_SPECIMEN。** 下例字段完整地表达真实DepotHead切片中“Java以`ids`参数到达一个边界调用”这一有限材料。ID/digest/byte offset只是grammar-valid specimen、未重算，不能replay；它不声称数据库更新成功，也没有指向另一个Flow：

~~~json
{
  "processJoinSignalId": "process-join-signal:1111111111111111111111111111111111111111111111111111111111111111",
  "flowSliceId": "flow-slice:2222222222222222222222222222222222222222222222222222222222222222",
  "signalKind": "BUSINESS_IDENTIFIER_ANCHOR",
  "anchorKind": "BUSINESS_IDENTIFIER",
  "anchorKey": "depot-head-ids",
  "direction": "CONSUMES",
  "specificity": "DOMAIN_SPECIFIC",
  "claimScope": "FROZEN_JAVA",
  "factIds": ["fact:3333333333333333333333333333333333333333333333333333333333333333"],
  "atomIds": ["atom:4444444444444444444444444444444444444444444444444444444444444444"],
  "proofIds": ["proof:5555555555555555555555555555555555555555555555555555555555555555"],
  "evidenceNodeIds": ["evidence:6666666666666666666666666666666666666666666666666666666666666666"],
  "sourceLocators": [{
    "fileId": "file:7777777777777777777777777777777777777777777777777777777777777777",
    "path": "jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java",
    "startByte": 100,
    "endByteExclusive": 120,
    "startLine": 181,
    "startColumn": 1,
    "endLine": 181,
    "endColumn": 21
  }],
  "gapIds": []
}
~~~

`processJoinSignalIds[]`及四项model eligibility字段都是`flow-coverage.json`的required wire字段，不是FlowInterpretation或RepositoryKnowledge临时重算的view。`modelIneligibilityByFlow`按`flowSliceId`严格排序且key唯一；每个`gapIds[]`非空、按UTF-8 ID排序去重。0 Flow时这些集合全部是exact empty array，不能省略或写null。

FlowStep kind registry至少有ENTRY、GUARD、READ、CALCULATE、WRITE、RESULT、CALL_CHILD；`WRITE`只表示由Fact证明的frozen-Java local/field write，不能表示boundary外部副作用。boundary invocation使用已有call/Fact引用表达，不新增技术专用FlowStep kind。

### 8.2 Coverage 与 dispositions

每个 entry 恰一条：

- COMPILED：恰一个入口根 Flow；
- GAP：有 blocking unsupported/ambiguous/unproven behavior；
- EXCLUDED：程序证明不可达或 profile 明确排除，并保存 reason。

~~~text
discoveredEntries = compiled + gapped + excluded
supportedOutcomeCandidates = compiledOutcomes + gappedOutcomes + excludedOutcomes
admittedFacts = flowOwned + gapOwned + reasonedUnassigned
admittedAtoms = flowOwned + gapOwned + reasonedUnassigned
processJoinSignalIds = exactUnion(FlowSlice.processJoinSignals[].processJoinSignalId)
processJoinSignalIds = exactUnion(EvidenceCapsule.processJoinSignals[].processJoinSignalId)
everyProcessJoinSignal = sameFlow(Fact/Proof/Evidence/Source) | sameFlow(Counter/Gap/Source)
allEntryShardIds = exactDisjointUnion(entryShardDenominatorIds) = discoveredEntries
allFlowShardIds = exactDisjointUnion(flowShardDenominatorIds) = compiledFlowSliceIds
compiledFlowSliceIds = evidenceCapsuleFlowSliceIds
compiledFlowSliceIds = modelEligibleFlowSliceIds ⊎ modelIneligibleFlowSliceIds
modelEligibleFlowSliceIds = { capsule.flowSliceId | capsule.modelEligibility = ELIGIBLE }
modelIneligibleFlowSliceIds = { capsule.flowSliceId | capsule.modelEligibility = INELIGIBLE }
domain(modelIneligibilityByFlow) = modelIneligibleFlowSliceIds
modelIneligibilityGapIds = exactUnion(modelIneligibilityByFlow[*].gapIds)
modelIneligibilityByFlow[flowId].gapIds = capsule(flowId).modelIneligibilityGapIds
~~~

0/0 不能用来抹掉已知 outcome candidate；denominator 在遍历前由 entry/CFG/profile固定。`modelEligibility=INELIGIBLE` 只允许在 Capsule 的`modelIneligibilityGapIds[]`非空且每个Gap都在本 Flow/Capsule scope可重验时出现；`ELIGIBLE` 时该数组必须为空且该Flow不得出现在mapping。coverage的mapping不能加入Flow本身没有声明的foreign Gap，也不能遗漏Capsule声明的Gap。这个分类只禁止把 Capsule 交给 Provider，不改变 Flow、Capsule、Outcome 或 RepositoryKnowledge的全仓库决策分母。

### 8.3 Capsule 最小性

`ModelEvidenceSpanV4`保存`spanId`、完整`SourceExcerptV1`、`supportedAtomIds[]`、`supportedOutcomePathIds[]`和`supportedProcessJoinSignalIds[]`。`sourceExcerpt.rawUtf8`是locator半开连续区间的原始UTF-8 bytes，不trim/格式化；同一语义需要不连续位置时创建多个span并分别进入obligation，不得合成raw。

ProjectionObligation kind只允许`ATOM_DIRECT_SEMANTICS | OUTCOME_TERMINAL | PROCESS_JOIN_SIGNAL_BASIS`。boundary target、每个argument/origin与control atom各自需要direct obligation；每个positive signal至少一项signal-basis obligation；external-effect Gap也须有自己的Gap view和`PROCESS_JOIN_SIGNAL_BASIS`，不能用XML/SQL span创建effect atom obligation。每个obligation至少一个satisfying span；删除任一span后至少一个obligation失去全部支持，否则该span冗余。

ProofPack 回答事实为何成立；Capsule 回答模型最少读什么。Capsule 不能删除 Proof dependency，也不能自证 Fact。

`factViews[]`按`factId`排序，且其 ID 集必须逐字等于 owning `FlowSlice.factIds[]`；每个 view 的 atom array 必须逐字段等于对应 ProvenCodeFacts `CodeFact.atoms[]`，并逐项指向同一 `proofId`。`gapViews[]`按`gapId`排序，且 ID 集必须逐字等于该 Flow/Outcome/Fact/Atom ownership scope 内、允许向模型显示的 Gap 集；不得把其他 Flow 的 Gap 或无证据的业务猜测放进来。`outcomePathViews[]`按`outcomePathId`排序，且 ID 集必须逐字等于 owning `FlowSlice.outcomePaths[]`；guard、polarity、terminal 与required atom/proof都不得摘要或改写。`processJoinSignals[]`按signal ID排序，ID集和每个字段必须逐字等于owning FlowSlice；`modelEvidenceSpanIds[]`与`projectionObligationIds[]`分别按ID排序，并必须引用本次projection同一payload中的完整value；每个 atom/Outcome/signal 都必须被至少一项 obligation 覆盖。上述任一集合、字段或源artifact reference不等，M2不得发布 Capsule。

### 8.4 Identity、预算与安全

合法identity顺序固定为 Outcome ID → Flow ID → ProcessJoinSignal ID → `flowCompilationId`/M1 artifact → span/obligation ID → Capsule ID → M2 projection/artifact → public coverage和BusinessFlows result/root。Flow ID沿用entry/root/profile ref与拥有的Fact/atom/Gap/Outcome ID preimage，明确排除`processJoinSignals`，否则signal所需`flowSliceId`会成环；signal ID随后覆盖该Flow ID及signal其余全部字段。M1 `flowCompilationId`/artifact、M2 Capsule/projection与M3 public artifact bytes都必须覆盖完整signal values；Capsule ID的直接preimage至少包含其排序signal/span/obligation IDs，不能只靠Flow ID间接假定signal未变化。预算进入 identity，即使没有触顶。

`FlowCompilationProfile`拥有`maxFlows`、`maxOutcomesPerFlow`、`maxFlowNodes/Edges`、`maxTraversalDepth`、`maxProcessJoinSignalsPerFlow`与`maxProcessJoinSignalBasisRefs`；后者逐signal限制`factIds + atomIds + proofIds + evidenceNodeIds + sourceLocators + gapIds`六个数组的元素总数。`CapsuleProjectionProfile`只拥有`maxCapsules`、`maxSpansPerCapsule`、`maxSpanBytes`、`maxCapsuleUtf8Bytes`。超限不截断 paths/spans/signals或删除basis；可安全隔离时把受影响signal/Flow明确置Gap，否则fatal。

只读 persisted artifacts/verified handles；不执行客户代码、模型或网络。Capsule 中的 prompt injection 文本只是 data。

### 8.5 Gap 与 failure codes

局部 Gap：

UNSUPPORTED_REACHABLE_SITE、AMBIGUOUS_CALL_TARGET、OUTCOME_BRANCH_UNRESOLVED、FLOW_FACT_NOT_ADMITTED、UNBOUNDED_RECURSION_OR_LOOP、CAPSULE_BUDGET_NO_SAFE_SPLIT、EXPECTATION_GAP_IN_FLOW_SCOPE。

Fatal：

BUSINESS_FLOWS_REQUEST_INVALID、UPSTREAM_ARTIFACT_REPLAY_MISMATCH、FLOW_GRAPH_REFERENCE_BROKEN、FLOW_BRANCH_POLARITY_MISSING、FLOW_OUTCOME_CLOSURE_BROKEN、FLOW_FACT_PROOF_REFERENCE_BROKEN、PROCESS_JOIN_SIGNAL_BASIS_INVALID、PROCESS_JOIN_SIGNAL_FLOW_MISMATCH、PROCESS_JOIN_SIGNAL_EXTERNAL_EFFECT_UNPROVEN、FLOW_ACCOUNTING_INVARIANT_BROKEN、EVIDENCE_SOURCE_REOPEN_MISMATCH、EVIDENCE_PROJECTION_UNSATISFIABLE、EVIDENCE_PROJECTION_INVARIANT_BROKEN、BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED、BUSINESS_FLOWS_IDENTITY_COLLISION。

### 8.6 测试 seam 与验收

- 每删一条 CFG/call/data/Proof edge，对应 Outcome/Flow 必须 Gap/fatal，不能猜回。
- 同一 entry 多 terminals 编成一个 Flow 多 Outcomes，不复制成多个 Flow。
- 第二 entry 不能借用共享 Service 的 Fact/span。
- Capsule span 必须来自 Proof roots，不按文本相似命中 decoy。
- 每条`processJoinSignal`必须同Flow闭合到Fact/Proof/Evidence/source或Gap；删除任一basis使signal消失、转Gap或fail closed，不能继续存在。
- 首个双entry stored-artifact RED必须精确产生approve 3条、cancel 3条：每个boundary各有`JAVA_TYPE_ANCHOR + EXPLICIT_CALL + EXTERNAL_EFFECT_GAP`。approve仍须保留独立guard Fact和TRUE/FALSE Outcomes，但其typed boundary guard/polarity为null，故两Flow都明确没有`COUNTER_CONDITION`；不得跨Flow借用guard或弱化§8.1.1。两端application target不同只证明Flow隔离，不证明端到端顺序；三family全部为`GENERIC_TECHNICAL`，因此该fixture不得产生`SHARED_ANCHOR`。随后由同一compiler selector族的独立public stored-artifact正例证明non-null typed boundary guard context后，才验收`COUNTER_CONDITION`。
- tenantId、用户审计字段、日志、通用工具类、方法名或中文名相似不能单独形成domain-specific signal；共享表最多形成`SQL_TABLE_ANCHOR`，不能形成顺序或因果。
- 双Flow relation fixture须覆盖：两端完整业务对象key集合有交集时不产生对象反证；两端集合均非空且互斥时，Step 06以全部对应signal IDs形成唯一、稳定的`DIFFERENT_BUSINESS_OBJECT` counter basis。改变输入顺序或同一关系的其他positive pair不得改变该basis。
- 每删一个直接语义 span，projection obligation 失败；proof-only span 注入被拒绝。
- 0 Flow/0 Capsule 仍有完整 entry disposition/Gap/accounting，并让 分析步骤“流程解释” Provider calls=0。
- 双Flow fixture必须让一个Flow为ELIGIBLE、一个为INELIGIBLE；`flow-coverage.json`的两个Flow集合互斥并集等于全部Flow，mapping domain、逐Flow非空Gap和全局Gap union都与Capsule逐字相等。任一overlap、omission、empty/foreign/missing Gap mutation都必须fail closed。
- 不同 root/input order 产生相同 canonical artifacts。
- multi-flow fixture 至少有 DepotHead与第二入口各自独立Flow/Capsule；一条成功、一条Gap或缺shard时，已完整安装slice保留但analysis step/run不得误报完成。相同BusinessFlows输入/profile/budget下改变线程、遍历或写盘顺序，六文件bytes必须相同；下游Step 06改变自己的process partition budget不反向改变这六个已安装文件。

验收必须同时覆盖至少两个非空Flow/Capsule（其中一个可用真实DepotHead讲解）、明确标注为合成的补货到结算七入口场景、eligible/ineligible各一Flow及完整逐Flow Gap mapping、一个多Outcome入口、第二入口ownership隔离、其中一Flow Gap、domain/generic/counter/external-effect signals、共享表/同名方法不可推序、缺/重叠shard、逐edge/span/signal-basis deletion mutation，以及从历史审计提炼的DepotHead 0/0回归baseline。只有每个COMPILED入口各生成一个入口根Flow/一个Capsule且所有终点闭合、Flow/Capsule signal逐字相等、完整entry ledger与eligibility分区/mapping守恒，0/0 baseline生成完整六文件/Gap/accounting并使Provider seam调用数为0，BusinessFlows才算可交付；单Flow PASS不构成验收。

### 8.7 已冻结裁决：实现者不得自由推断

- Flow 边界是 entry，不是 return/throw；一个 compiled entry 恰一个入口根 Flow，多个结束方式只能是 Outcomes。
- 只沿 EXACT call、显式 CFG polarity 和闭合 call/return 遍历；行号、异常习惯或模型不能补路径。
- 一个 TRUE/FALSE branch pair 只消费其 guard node 所属 entry 的一个已证明 `CONTROL_CONDITION` atom；它不创建、重命名、借用或降级此 atom。
- Fact/atom/Gap 有唯一 Flow ownership；共享子流程只能按版本化 parent/child rule 表示。
- 每个Flow恰一个最小EvidenceCapsule；Capsule只从Proof roots投影，不能用“多给模型一点”作为降级。该同一artifact是FlowInterpretation R0/R1/R2唯一源码语义来源，且R0 basis只能来自显式`registryProposalBasis*`集合。
- 每个Flow/Capsule携带同一组证据闭合的`processJoinSignals`；这些材料只供后续程序比较，不在本步骤证明两个Flow的顺序、因果或同属一个BusinessProcess。
- GAP/EXCLUDED entry 不生成 Capsule；0 Flow/0 Capsule 是可发布给 分析步骤“九章文档” 的可信分析状态，并强制 分析步骤“流程解释” 零调用。
- ApplicationDiscovery全入口必须唯一处置，`N` compiled Flow严格对应`N` Capsule；分片只改变调度，不得采样/截断或让单Flow success完成analysis step/run。
- 本分析步骤只产Flow/Capsule与accounting，不产解释或Markdown；per-Flow Markdown及片段拼接均不在目标内。
- 遍历器、最小覆盖算法和内部类可自行实现；Flow/Outcome/Capsule 边界、双射、coverage、identity、Gap/fatal 不得改变。

## 9. 当前实现成熟度审计

正式 `origin/main` 尚未交付本步骤；当前开发分支已完成一个受限的 M1–M3 纵切，用它验证持久化模块接力、双入口 Flow/Capsule 归属、条件分支和模型预算处置。它仍不是完整仓库验收能力。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 目标package与JDK 17 Toolchain已就位；通用wire头门禁只判断`SOURCE_ANALYSIS/v1`，不建立Flow eligibility。 |
| **已实现（开发分支的受限纵切）** | M1 从重新打开的 ApplicationDiscovery、ProgramGraphs 与 ProvenCodeFacts v2 读取双入口 fixture，沿准确 call/return 和 TRUE/FALSE guard 生成 Flow/Outcome；每个入口都写 COMPILED 或带 reason 的 GAP。M2 从 M1 与 Proof/Evidence/source artifacts 重开后，给每个 compiled Flow 写一份 Capsule；模型预算超限时仍保留 Flow/Capsule 和证据，只写 `INELIGIBLE` 与明确 Gap。M3 已将 Flow、Capsule、入口处置、coverage 与 Flow Gap 发布为五项正式文件和 receipt；该纵切没有目标`processJoinSignals`字段，也未证明跨Flow候选材料。 |
| **应当修复（跨步骤证据交接）** | 在不新增文件的前提下，把每Flow完整`processJoinSignals[]`加入M1 Flow与M2/public Capsule，升级M1为v2、M2为v5、public flow/capsule schemas为v2/v3，并以Fact/Proof/Evidence/source closure、Flow-Capsule逐字相等、ID-set equality、跨Flow隔离和fresh-reopen测试验证。当前v2 ProvenCodeFacts只足以按§8.1.1产生四种`GENERIC_TECHNICAL` family；完成该纵切不等于具备`DOMAIN_SPECIFIC/SHARED_ANCHOR`或完整过程重建能力。当前已发布Capsule是否已经补齐旧span/obligation value应以合入commit重新审计，不能用旧v1/v2成熟度陈述冒充本次目标。 |
| **尚未交付（完整仓库能力）** | 仍缺真实完整 jshERP 从源码清单至本步骤的离线运行、0 Flow persisted fixture、循环/多实现/歧义调用的系统性处置、entry 分片与跨 Flow 的完整隔离测试，以及正式运行核心接线。该纵切不得用于仓库完成判定，也不产生可供真实模型调用的已发布 Flow。 |
| **历史证据，不是当前能力** | 已删除的pre-reset compiler曾在有限fixture上编译Flow/Outcome/Capsule；固定八文件历史审计为blocking Gap、0 Flow、0 Capsule。该结果只作为0调用和证明不足的回归baseline。 |
| **下一实现门** | Luna先以当前public stored-artifact fixture写approve 3/cancel 3的signal RED，Terra再按既定模块补最小GREEN；之后在不改ProgramGraphs合同的独立已证明fixture/rule slice中补`COUNTER_CONDITION`正例，再继续M2/M3和至少双入口、双Flow、多Outcome、跨Flow隔离、eligible/ineligible、0Flow、真实DepotHead有限材料及明确合成的补货到结算场景的全仓分母闭合。 |

若新实现对某Flow没有Capsule，分析步骤“流程解释”对该Flow必须零调用，而不是让模型直接读Service/XML；历史0 Capsule本身不是当前运行结果。
