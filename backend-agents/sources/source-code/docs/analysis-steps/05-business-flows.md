# 业务流程

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。运行顺序只由文件名中的 `05-` 与运行目录 `steps/05-business-flows/` 表达。

本文示例严格使用DESIGN §1.3的`NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN`分类；未标为strict的digest/size/ID不可复制为golden。权威字段表、enum、identity和direct-preimage合同始终exact，不能靠示例降级删除。

## 1. 为什么存在

Fact 说明一件事成立，却没有说明一个请求从哪里开始、经过哪些条件、在哪些终点结束。模型若直接收到 Fact 列表，仍可能把多个入口、分支和副作用拼成一段不存在的故事。

分析步骤“业务流程” 由程序处理 ApplicationDiscovery **全入口 inventory**：每个支持入口编译成 FlowSlice，并为每条 Flow 生成唯一、最小、预算内的 EvidenceCapsule；不能闭合的入口保留有证据的 GAP，明确不在目标 profile 的入口保存 EXCLUDED。Flow 是业务过程边界；Outcome 是同一过程的一种结束方式，不能把每个终点误拆成独立 Flow。DepotHead 只是仓库 `N` 个 FlowSlice 候选中的一个例子。

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

目标 Flow 应以 HTTP entry 为根，保留每个 guard polarity 和 terminal。**但当前结果不是成功 Flow**：固定 jshERP slice 仍是 Gap、0 Flow、0 Capsule。

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

## 3. 程序怎样工作

1. 重验上游 roots、Facts、Proof、Gaps 和五图 reference closure。
2. 从 ApplicationDiscovery `repositoryEntryCoverage.entryIds` 取得完整入口分母，按 entryId 排序/分片，每个入口创建一个 flow ownership scope。
3. 从 entry method 沿 EXACT call edge、显式 CFG edge 和 call/return pair 遍历。
4. 每个 guard 记录 guardId、conditionAtomId、TRUE/FALSE polarity；不从行号猜极性。
5. 到 return、throw 或 profile 支持的 stop 封闭 OutcomePath；同终点不同 decision sequence 仍是不同 Outcome。
6. 所有 Outcome 求最长公共入口前缀，形成 sharedSteps；分支保留在 Outcomes。
7. 将 Fact/atom/Gap 分配给唯一 Flow；共享子流程只有通过版本化 parent/child ownership rule 才可拆分。
8. 从 Proof root 选择直接表达入口、条件、读取、写入和终点的 source spans；同时确定 `registryProposalBasisAtomIds` 与 `registryProposalBasisGapIds`，它们是 R0 可引用的完整、闭合 basis 分母。
9. 建 projection obligations；每个 atom/Outcome 至少有一个 span，且删除任一 span 都会损失义务。R0、R1、R2 必须读取同一 Capsule artifact，不能各自选择不同源码切片。
10. 对每个 entry 写 COMPILED/GAP/EXCLUDED；验证 entry shard denominator 两两不交叠且 union 等于 ApplicationDiscovery 全 entry IDs。
11. 重算 entry/Outcome/Fact/atom/Gap/capsule/omitted coverage 后原子安装 分析步骤“业务流程”；任意一条 Flow 成功都不能关闭尚未处置的 entry。

## 4. 生成的可观察产物

| 文件 | 唯一职责 |
| --- | --- |
| flow-slices.json | FlowSlice、OutcomePath、steps、facts/atoms/gaps 和 parent/child refs |
| flow-coverage.json | 仓库 entry/outcome/call/mapper/fact/atom/evidence、shard 与 unsupported/failed/omitted 的分母与分子；同时保存全部 Flow 的 model eligibility 严格分区和逐 ineligible Flow 的 Gap 映射 |
| entry-dispositions.jsonl | 每个发现入口恰一条 COMPILED/GAP/EXCLUDED |
| evidence-capsules.jsonl | 每个 COMPILED Flow 恰一个模型阅读包 |
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

Artifact cardinality 固定：若完整仓库编译出 `N` 个 FlowSlice，则必有 `N` 个 EvidenceCapsule，且每对保留相同 `flowSliceId`；BusinessFlows 不生成解释或 Markdown。每个 Flow/Capsule独立持久化并可被下游按identity读取，最终 public files是所有 shard 的 canonical ID-set union。

### 4.1 人类 walkthrough：模块用什么文件接力

**示例分类：NARRATIVE_ILLUSTRATION。** 下面只解释未来 Fact/graph closure 已补齐后的模块接力，不是wire record、identity preimage或跨analysis step replay fixture；当前 implementation audit 的 0/0 不变。

~~~jsonl
{"module":"flow-compiler","artifact":"modules/01-flow-compiler/flow-compilation.json","takesFrom":["ApplicationDiscoveryReference","ProgramGraphsReference","ProvenCodeFactsReference"],"says":{"entryId":"entry:post-depothead-batch-set-status","flowSliceId":"flow:post-depothead-batch-set-status","factId":"fact:depothead-mapper-boundary-invocation","outcomes":["outcome:no-eligible-document","outcome:boundary-invoked"],"externalEffectGapId":"gap:depothead-external-update-effect-unproven"}}
{"module":"capsule-projector","artifact":"modules/02-capsule-projector/capsule-projection.json","takesFrom":["flow-compilation.json","Proof/Evidence/source"],"says":{"flowSliceId":"flow:post-depothead-batch-set-status","evidenceCapsuleId":"capsule:post-depothead-batch-set-status","covers":["entry route","dhIds guard","generic boundary target","ordered arguments and origins","both terminals","external-effect Gap"],"registryProposalBasisAtomIds":["atom:input-field","atom:boundary-invocation"],"registryProposalBasisGapIds":["gap:depothead-external-update-effect-unproven"],"usedBy":["R0","R1","R2"]}}
{"module":"publish","artifacts":"modules/03-publish/<five-semantic-files>","receipt":"modules/03-publish/module-receipt.json","takesFrom":["flow-compilation.json","capsule-projection.json"],"says":{"flowCount":1,"capsuleCount":1,"modelEligibleFlowSliceIds":["flow:post-depothead-batch-set-status"],"modelIneligibleFlowSliceIds":[],"modelIneligibilityGapIds":[],"modelIneligibilityByFlow":[],"semanticFiles":["flow-slices.json","flow-coverage.json","entry-dispositions.jsonl","evidence-capsules.jsonl","flow-gaps.jsonl"],"analysisStepReceipt":null,"nextStep":"CanonicalAnalysisStepArtifactStore"}}
~~~

M1 的 `entryId/factId/outcomePathIds` 原样进入 M2；M2 只新增 `evidenceCapsuleId`、直接语义 spans和可重验的model eligibility。上例假设DepotHead Capsule符合模型输入预算，所以进入eligible集合；若它只能安全持久化、却不能安全交给模型，则同一Flow和Capsule仍保留，只移动到`modelIneligibleFlowSliceIds`，并在`modelIneligibilityByFlow`增加该Flow及非空Gap。当前缺 Proof 时 M1 产生 GAP/0 Flow，M2 产生0 Capsule，M3仍发布完整0/0 artifact set，四项eligibility字段都显式为空数组。

## 5. 下游怎样消费而不返工

分析步骤“流程解释” 每次按 flowSliceId 读取恰好一个 FlowSlice 和一个 EvidenceCapsule。R0、R1、R2 的源码语义都必须来自这同一个 persisted Capsule；R0 只能引用 Capsule 明列的 registry proposal basis，R1/R2 不能旁路读取 R0 raw output 之外的源码。它看不到：

- 仓库 root 或任意 Path；
- 其他 Flow/Capsule；
- 未准入 Fact；
- Proof-only dependency nodes；
- 五张完整 graph；
- inventory 外文件。

分析步骤“流程解释” 无权发现 Flow、补 Outcome、修改条件或请求“再读一点仓库”。Capsule 不充分时回到新版本 分析步骤“业务流程”/上游 run，而不是模型侧扩展。

### 下游前置条件与后置保证

| 分析步骤“流程解释” 开始前必须成立 | 分析步骤“业务流程” 成功后保证 |
| --- | --- |
| 分析步骤“应用发现” entries、分析步骤“程序图” graph roots、分析步骤“已证明代码事实” Fact/Proof/Gaps 与 分析步骤“业务流程” controls 全一致 | 每个发现 entry 恰一条 COMPILED/GAP/EXCLUDED disposition，入口与 Outcome 分母守恒 |
| 六个 output files、receipt root、Flow/Outcome/Fact/atom/Gap/capsule refs 全闭合 | 每个 COMPILED entry 恰一个入口根 Flow，每个 Flow 恰一个最小 Capsule；GAP/EXCLUDED 没有 Capsule |
| Capsule span hashes、projection obligations、R0 basis allowlist 和预算重验通过 | 分析步骤“流程解释” 可从同一Capsule先建一个R0 task，程序freeze registry后再建R1/R2 tasks；0 Flow可确定0 Provider call |
| entry shard receipts不交叠且union精确等于ApplicationDiscovery全部entryIds；每entry有唯一disposition | FlowInterpretation只得到`modelEligibleFlowSliceIds`并且不会为ineligible Flow建task；RepositoryKnowledge仍从`flowSliceIds`与`modelIneligibilityByFlow`得到完整全Flow分母；一Flow PASS不足以结束BusinessFlows/run |

分析步骤“流程解释” 若看到多 Capsule、缺 Capsule、未闭合 Outcome 或越界 ref，必须拒绝整个 分析步骤“业务流程” input；不能让模型选择“看哪一份”。

## 6. 成功、Gap、fatal 与显式复用

- **成功**：完整仓库所有 entry 都有唯一 disposition；每个 COMPILED entry 恰一个入口根 Flow/Capsule；全 shard union、coverage 和 projection closure 闭合。
- **带 Gap 成功**：某 entry 为 GAP，或 Flow 带 warning Gap。0 Flow/0 Capsule 是合法 SUCCEEDED_WITH_GAPS，并显式阻止模型调用。
- **fatal**：上游 replay mismatch、graph/proof reference broken、branch polarity/terminal closure broken、accounting 不守恒、source span 漂移、projection 不可满足或 identity collision。
- **显式复用**：新FlowInterpretation execution重验upstream roots、entry shard receipts和BusinessFlows artifact root；缺/重叠shard不能发布。完全相等才读取；fatal不删除五图/Facts或已完整安装的slice artifacts。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 定义 Flow/Outcome 边界 | 是 | 否 |
| 计算 branch polarity/coverage | 是 | 否 |
| 选择 Capsule spans | 是，按 Proof/obligation | 否 |
| 阅读/命名 Flow | 否，留到 分析步骤“流程解释” | 否 |

本分析步骤产品运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块执行顺序固定为 `EntryRootedFlowCompiler` → `EvidenceCapsuleProjector` → `FlowPublicationSpecifier` → `CanonicalAnalysisStepArtifactStore`。前三个业务模块先安装 canonical artifact；后继只读 artifact，不读取前驱内存图或私有类，analysis step store不是第四个业务模块。

#### M1 EntryRootedFlowCompiler

- **解决的问题**：把每个发现 entry 的 exact graph/Fact closure 编译成一个完整 Flow、多条 Outcomes 或明确 GAP/EXCLUDED。
- **精确上游输入及前置**：valid ApplicationDiscovery complete entry inventory/coverage、ProgramGraphs call/control/data graphs、ProvenCodeFacts Facts/Proof/Gaps、flow profile/budget；所有 roots/entry/edge/fact refs及ApplicationDiscovery entry denominator已重验。
- **确定性顺序 / LLM**：固定全 entryId denominator → 按entryId shard → 建 ownership scope → 沿 EXACT call/CFG/call-return → 枚举 decision sequences/terminals → longest common prefix → 分配 Fact/atom/Gap → per-entry disposition → shard union/account；0 LLM。
- **目标输出与 DepotHead 示例**：`FlowCompilation{entryDispositions,flowSlices,outcomePaths,ownership,coverage}`；未来正例为一个POST entry Flow、多Outcome，历史回归例为该entry的GAP/0 Flow与blocking refs。
- **必须保持的不变量**：ApplicationDiscovery每 entry 恰一 disposition；COMPILED 恰一个 root Flow；每 terminal path 恰一 Outcome disposition；Fact/atom/Gap owner 唯一；entry shards不交叠且union等于完整entry denominator。
- **Gap / fatal / artifact复用**：unsupported/ambiguous/unproven path 是 entry/Outcome Gap；broken graph/Proof refs、missing polarity/terminal、duplicate owner/accounting fatal；模块每次处理完整entry denominator。
- **给下游的后置保证**：M2 得到闭合 Flow/Outcome/Fact/Proof ownership，或可解释的零 Flow，绝不需要补 path。
- **明确非目标**：不选 source spans、不调用模型、不按 terminal 拆多个 Flow、不发明业务名。
- **公共测试 seam 与验收**：`compile(entries, graphs, facts, profile)` 覆盖至少两非空entry/Flow、多 Outcome、第二 entry 隔离、其中一entry Gap、缺/重叠 shard、loop/ambiguous Gap、edge deletion fatal 与 DepotHead 0/0 baseline；ID-set accounting必须闭合且一Flow PASS不能完成analysis step。
- **Luna/xhigh 测试指南**：创建`EntryRootedFlowCompilerTest`，冻结ApplicationDiscovery、ProgramGraphs与ProvenCodeFacts files、多Outcome fixture及由历史审计提炼的DepotHead 0/0 golden于`src/test/resources/analysis/flow/flow-compiler/`。逐RED：单entry多Outcome、第二entry隔离、shared prefix、loop/ambiguous Gap、edge/polarity deletion、0/0 disposition、order determinism；首RED因seam/schema缺失。只fake artifact reader，flow/accounting不可mock。命令：`mvn -Dtest=EntryRootedFlowCompilerTest test`；禁网络/客户执行。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后只改 `analysis/flow/flow-compiler/`，实现 public `EntryRootedFlowCompiler/FlowCompilation` 与 `business-flows-flow-compilation-v1`；只读ApplicationDiscovery、ProgramGraphs与ProvenCodeFacts artifacts，entry→traversal→terminal paths→shared prefix→ownership/accounting。逐slice GREEN；不得行号补路/按terminal拆Flow/硬编码DepotHead。需新graph/Fact字段MUST STOP交Sol/ultra并按跨analysis step规则升级，完成更新审计。

#### M2 EvidenceCapsuleProjector

- **解决的问题**：为每个 compiled Flow 从 Proof roots 选择唯一、最小、预算内、模型可读的 source投影。
- **精确上游输入及前置**：M1 FlowCompilation artifact、ProvenCodeFacts ProofPack、ProgramGraphs Evidence graph、VerifiedSourceInventory source handles、projection profile/budget；每 Flow/Outcome/atom/proof ref 闭合。
- **确定性顺序 / LLM**：按 flowSliceId → 枚举 ATOM/OUTCOME obligations → 从 Proof roots取 direct-semantic spans → stable set cover → exact excerpt/hash → deletion minimality/closure check；0 LLM。
- **目标输出与 DepotHead 示例**：`CapsuleProjection{capsules,modelEvidenceSpans,projectionObligations,flowShardReceipts,budgetUsage}`；每个Capsule内嵌`factViews/gapViews/outcomePathViews`。DepotHead Flow给出入口、guards、Java-local write、generic boundary invocation、ordered arguments、terminals和external-effect Gap；不能给出数据库write/where outcome。
- **必须保持的不变量**：`N` compiled Flow↔`N` Capsule 一一对应；flow shards不交叠且union等于M1 flowSliceIds；每 obligation有支持且每 span不可冗余；每个 view 都是同 Flow 已持久化 Fact/Gap/Outcome 的逐字段受控投影，不能用摘要、模型文本或外部查找替代；span只来自 own Flow Proof、原始 bytes不 trim。
- **Gap / fatal / artifact复用**：无安全 split 的预算超限使对应 Flow blocking Gap；source drift、cross-Flow span、unsatisfied/redundant projection、ref broken fatal；projector只从完整affected Flow及其verified inputs计算。
- **给下游的后置保证**：M3/FlowInterpretation 可按 flowSliceId 得到恰一个 closed、evidence-complete Capsule：它已经携带该 Flow 的 facts、gaps、outcomes、spans、obligations 与 R0 basis allowlist；R0/R1/R2共享该artifact，模型不需Path/graphs/其他Flow，也不得从别的artifact补内容。
- **明确非目标**：不缩减 ProofPack本身、不解释业务、不让 Provider请求额外源码。
- **公共测试 seam 与验收**：`project(flowCompilation, proofs, evidence, source)` 使用至少两Flow，对每span deletion、proof-only injection、cross-Flow借用、source mutation、缺/重叠shard和预算边界；另断言R0 basis非空、闭合、同Flow且R0/R1/R2 sourceArtifactId完全相等。正例每Flow独立obligations闭合且删除任一span失败。
- **Luna/xhigh 测试指南**：创建 `EvidenceCapsuleProjectorTest`，fixtures/goldens在 `src/test/resources/analysis/flow/capsule-projector/`。RED顺序：一个Flow完整obligations/spans→R0 basis closure→R0/R1/R2同artifact→逐span deletion→proof-only冗余→cross-Flow借用→source drift→budget no-safe-split Gap→root determinism；首RED因projector/schema缺失。仅fake source handle，set-cover/obligation/canonical不可mock。命令：`mvn -Dtest=EvidenceCapsuleProjectorTest test`；无Provider/network。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `analysis/flow/capsule-projector/`，实现 public `EvidenceCapsuleProjector/CapsuleProjection` 与 `business-flows-capsule-projection-v4`；消费M1+Proof/Evidence/source，obligation→eligible spans→stable minimal cover→连续 `SourceExcerptV1`→完整 Fact/Gap/Outcome view→R0 basis closure。逐RED GREEN；不得多给源码、跨Flow、trim bytes、合成不连续excerpt、以摘要替代view，或给R0/R1/R2不同source。缺Proof语义/需跨analysis step变更MUST STOP，完成更新审计。

#### M3 FlowPublicationSpecifier

- **解决的问题**：组合 Flow 与 Capsule artifacts，守恒全部 entry/outcome/fact/atom/Gap并形成五个semantic payload；analysis step store独占root/receipt与BusinessFlowsReference。
- **精确上游输入及前置**：M1全仓 FlowCompilation/shard receipts、M2全 Flow CapsuleProjection/shard receipts、ApplicationDiscovery、ProgramGraphs与ProvenCodeFacts roots和BusinessFlows controls；完整entry dispositions与Flow/Capsule双射或明确0/0已局部验证；每个Capsule已有唯一`modelEligibility`和本Flow内可重验的`modelIneligibilityGapIds[]`。
- **确定性顺序 / LLM**：验证entry/flow shard disjoint union → join by flowSliceId → 由Capsule逐项重算`modelEligibleFlowSliceIds`、`modelIneligibleFlowSliceIds`和`modelIneligibilityByFlow` → 重算全仓 coverage/dispositions/unsupported/failed/omitted → canonical五个semantic payload → M3 install/receipt → analysis step store fresh reopen/root/receipt-last；0 LLM。
- **目标输出与 DepotHead 示例**：M3恰五个semantic files；analysis step store形成五项+receipt的六文件reader-visible set。历史回归例要求entry-dispositions有DepotHead GAP、flow/capsule及eligibility分区均为空、Gap/coverage/receipt非空；未来正例在`flow-coverage.json`中把每个Flow精确分到eligible或ineligible，并为后者保存非空Gap映射。
- **必须保持的不变量**：每 COMPILED Flow恰一个 Capsule，GAP/EXCLUDED无 Capsule；`flowSliceIds`必须是eligible与ineligible的互斥并集，mapping domain必须逐字等于ineligible集合且mapping Gap union逐字等于`modelIneligibilityGapIds`；所有ApplicationDiscovery entry唯一处置，single Flow PASS不等于analysis step/run完成；semantic files只引用M1/M2 IDs；M3不含或预报analysis step root/receipt且不改语义。
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
| `modules/01-flow-compiler/flow-compilation.json` | `business-flows-flow-compilation-v1` / `BUSINESS_FLOWS_FLOW_COMPILATION` | exact ApplicationDiscovery `capability-report/entry-points`、ProgramGraphs七项、ProvenCodeFacts `fact-accounting/gap-ledger/proof-pack/proven-facts` ArtifactReferences | `flowCompilationId!`、`entryDispositions[]!{entryId!,disposition!,flowSliceId?,gapIds[]!,reasonCode?,evidenceRefs[]!}`、`flowSlices[]!`、`entryShardReceipts[]!{shardId!,denominatorEntryIds[]!,dispositionEntryIds[]!,flowSliceIds[]!,status!,gapIds[]!}`、`coverage!`；entries/flows/shards按ID，steps/decisions保持语义顺序，outcomes按outcomePathId |
| `modules/02-capsule-projector/capsule-projection.json` | `business-flows-capsule-projection-v4` / `BUSINESS_FLOWS_CAPSULE_PROJECTION` | exact M1、VerifiedSourceInventory两项、ProgramGraphs七项、ProvenCodeFacts四项 ArtifactReferences | `capsuleProjectionId!`、`flowCompilationId!`、`capsules[]!{evidenceCapsuleId!,flowSliceId!,proofPackId!,modelEligibility!,modelIneligibilityGapIds[]!,entryView!:FlowEntryViewV1,factViews[]!:FlowFactViewV1,gapViews[]!:FlowGapViewV1,outcomePathViews[]!:FlowOutcomePathViewV1,registryProposalBasisAtomIds[]!,registryProposalBasisGapIds[]!,modelEvidenceSpanIds[]!,projectionObligationIds[]!,budgetUsage!}`、`modelEvidenceSpans[]!{spanId!,sourceExcerpt!:SourceExcerptV1,supportedAtomIds[]!,supportedOutcomePathIds[]!}`、`projectionObligations[]!{obligationId!,kind!,semanticItemId!,satisfyingSpanIds[]!}`、`flowShardReceipts[]!`、`budgetUsage!`；capsules/spans/obligations/shards按ID，fact/gap/outcome views按其stable ID，basis IDs按UTF-8 byte order |
| `modules/03-publish/<five registered semantic filenames>` | 各public schema/type；无summary envelope | M1+M2 IDs/SHAs | 一次module install恰`flow-slices.json/flow-coverage.json/entry-dispositions.jsonl/evidence-capsules.jsonl/flow-gaps.jsonl`；其中`flow-coverage.json`的`RepositoryFlowCoverage`必须包含8.1列出的四项model eligibility字段及完整mapping；module receipt绑定五descriptors；禁止analysis step root/receipt或六项published list；AnalysisStep store provenance绑定M3 reference |

M3的完整module fixture必须用一次install request/receipt绑定表中M1/M2两个ArtifactReferences；五个standalone payload不得重复envelope。只给payload而省略该排序upstream集合，不是完整M3 fixture。

FlowSlice/Outcome/BranchDecision字段按8.1；COMPILED entry的flowSliceId non-null且gapIds可为空，GAP/EXCLUDED的flowSliceId必须null并有reason/Gap。技术示例的静态unknown必须成为GAP disposition，不能用示例Outcome补齐。`ModelEvidenceSpanV3.sourceExcerpt`必须是DESIGN §13.2的完整`SourceExcerptV1`，其`rawUtf8`恰为locator半开连续区间的原始bytes；不连续证据必须拆成多个span，禁止使用`+`、`...`或重排后的合成excerpt。任何schema/ownership/projection/sort/identity变化先设计+升version，下游不可回读graph/source补字段。

`repositoryFlowCoverage.closed` 只有在 ApplicationDiscovery repositoryEntryCoverage已闭合、全部entry/flow shards守恒且每entry处置完成时为true；DepotHead bounded示例固定为false，即使局部一Flow/一Capsule双射成立。

**阅读示意，不是 wire、schema 或 replay fixture。** 早期长 JSON 包络曾把 Capsule 写成 ID 列表，无法保证模型实际读到 Fact、Gap 和 Outcome 的值，现已从目标设计移除。实现者只能遵循上表的 v4 结构、§8.1 records 与 FlowInterpretation 的逐字复制规则。

~~~text
DepotHead flow capsule (illustrative):
  fact view: Java invokes DepotHeadMapper.updateByExampleSelective(record, example)
             with recorded ordered arguments and Java-local origins
  outcome view A: eligible document IDs empty → return before boundary
  outcome view B: eligible IDs non-empty → boundary invocation reached
  gap view: external database update/filter effect is not statically proved
  evidence: route, id collection/guard, setter, Java invocation locator/rule
~~~

**示例分类：STRUCTURAL_WIRE_SPECIMEN（isolated `ModelEvidenceSpanV3` variants）。** 下列两条分别表示同一文件中不连续的class/method annotation；每条字段和类型完整、半开byte区间与所示ASCII `rawUtf8`长度一致，并按locator排序。digest只满足grammar且未从展示bytes重算，所以不能replay；二者不得合并为带` + `的伪raw excerpt。

~~~jsonl
{"spanId":"span:controller-class-route","sourceExcerpt":{"locator":{"fileId":"file:2222222222222222222222222222222222222222222222222222222222222222","path":"src/main/java/example/DepotHeadController.java","startByte":1000,"endByteExclusive":1029,"startLine":43,"startColumn":1,"endLine":43,"endColumn":30},"rawUtf8":"@RequestMapping(\"/depotHead\")","rawUtf8Sha256":"1111111111111111111111111111111111111111111111111111111111111111"},"supportedAtomIds":["atom:entry-route"],"supportedOutcomePathIds":["outcome:boundary-invoked"]}
{"spanId":"span:controller-method-route","sourceExcerpt":{"locator":{"fileId":"file:2222222222222222222222222222222222222222222222222222222222222222","path":"src/main/java/example/DepotHeadController.java","startByte":5000,"endByteExclusive":5031,"startLine":178,"startColumn":1,"endLine":178,"endColumn":32},"rawUtf8":"@PostMapping(\"/batchSetStatus\")","rawUtf8Sha256":"2222222222222222222222222222222222222222222222222222222222222222"},"supportedAtomIds":["atom:entry-route"],"supportedOutcomePathIds":["outcome:boundary-invoked"]}
~~~

### 8.1 Interface 与 records

~~~java
interface BusinessFlowCompiler {
    BusinessFlowsReference compile(ApplicationDiscoveryReference entries,
                             ProgramGraphsReference graphs,
                             ProvenCodeFactsReference facts);
}
~~~

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
  conditionAtomId
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

ModelEvidenceSpanV3
  spanId
  sourceExcerpt: SourceExcerptV1
  supportedAtomIds[]
  supportedOutcomePathIds[]

RepositoryFlowCoverage
  entryIds[]
  compiledEntryIds[]
  gappedEntryIds[]
  excludedEntryIds[]
  flowSliceIds[]
  capsuleIds[]
  modelEligibleFlowSliceIds[]
  modelIneligibleFlowSliceIds[]
  modelIneligibilityGapIds[]
  modelIneligibilityByFlow[] {flowSliceId, gapIds[]}
  entryShardReceiptIds[]
  flowShardReceiptIds[]
  closed
~~~

这四项model eligibility字段是`flow-coverage.json`的required wire字段，不是RepositoryKnowledge临时重算的view。`modelIneligibilityByFlow`按`flowSliceId`严格排序且key唯一；每个`gapIds[]`非空、按UTF-8 ID排序去重。0 Flow时四项全部是exact empty array，不能省略或写null。

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

`ModelEvidenceSpanV3`保存`spanId`、完整`SourceExcerptV1`、`supportedAtomIds[]`和`supportedOutcomePathIds[]`。`sourceExcerpt.rawUtf8`是locator半开连续区间的原始UTF-8 bytes，不trim/格式化；同一语义需要不连续位置时创建多个span并分别进入obligation，不得合成raw。

ProjectionObligation kind只允许ATOM_DIRECT_SEMANTICS或OUTCOME_TERMINAL。boundary target、每个argument/origin与control atom各自需要direct obligation；external-effect Gap也须有自己的Gap view，不能用XML/SQL span创建effect atom obligation。每个obligation至少一个satisfying span；删除任一span后至少一个obligation失去全部支持，否则该span冗余。

ProofPack 回答事实为何成立；Capsule 回答模型最少读什么。Capsule 不能删除 Proof dependency，也不能自证 Fact。

`factViews[]`按`factId`排序，且其 ID 集必须逐字等于 owning `FlowSlice.factIds[]`；每个 view 的 atom array 必须逐字段等于对应 ProvenCodeFacts `CodeFact.atoms[]`，并逐项指向同一 `proofId`。`gapViews[]`按`gapId`排序，且 ID 集必须逐字等于该 Flow/Outcome/Fact/Atom ownership scope 内、允许向模型显示的 Gap 集；不得把其他 Flow 的 Gap 或无证据的业务猜测放进来。`outcomePathViews[]`按`outcomePathId`排序，且 ID 集必须逐字等于 owning `FlowSlice.outcomePaths[]`；guard、polarity、terminal 与required atom/proof都不得摘要或改写。`modelEvidenceSpanIds[]`与`projectionObligationIds[]`分别按ID排序，并必须引用本次projection同一payload中的完整value；每个 atom/Outcome view 都必须被至少一项 obligation 覆盖。上述任一集合、字段或源artifact reference不等，M2不得发布 Capsule。

### 8.4 Identity、预算与安全

Outcome ID 先于 Flow ID；span/obligation ID 先于 Capsule ID；coverage 和 BusinessFlows result/root 最后计算。预算进入 identity，即使没有触顶。

预算至少有 maxFlows、maxOutcomesPerFlow、maxFlowNodes/Edges、maxCapsules、maxSpansPerCapsule、maxSpanBytes、maxCapsuleUtf8Bytes、maxTraversalDepth。超限不截断 paths/spans。

只读 persisted artifacts/verified handles；不执行客户代码、模型或网络。Capsule 中的 prompt injection 文本只是 data。

### 8.5 Gap 与 failure codes

局部 Gap：

UNSUPPORTED_REACHABLE_SITE、AMBIGUOUS_CALL_TARGET、OUTCOME_BRANCH_UNRESOLVED、FLOW_FACT_NOT_ADMITTED、UNBOUNDED_RECURSION_OR_LOOP、CAPSULE_BUDGET_NO_SAFE_SPLIT、EXPECTATION_GAP_IN_FLOW_SCOPE。

Fatal：

BUSINESS_FLOWS_REQUEST_INVALID、UPSTREAM_ARTIFACT_REPLAY_MISMATCH、FLOW_GRAPH_REFERENCE_BROKEN、FLOW_BRANCH_POLARITY_MISSING、FLOW_OUTCOME_CLOSURE_BROKEN、FLOW_FACT_PROOF_REFERENCE_BROKEN、FLOW_ACCOUNTING_INVARIANT_BROKEN、EVIDENCE_SOURCE_REOPEN_MISMATCH、EVIDENCE_PROJECTION_UNSATISFIABLE、EVIDENCE_PROJECTION_INVARIANT_BROKEN、BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED、BUSINESS_FLOWS_IDENTITY_COLLISION。

### 8.6 测试 seam 与验收

- 每删一条 CFG/call/data/Proof edge，对应 Outcome/Flow 必须 Gap/fatal，不能猜回。
- 同一 entry 多 terminals 编成一个 Flow 多 Outcomes，不复制成多个 Flow。
- 第二 entry 不能借用共享 Service 的 Fact/span。
- Capsule span 必须来自 Proof roots，不按文本相似命中 decoy。
- 每删一个直接语义 span，projection obligation 失败；proof-only span 注入被拒绝。
- 0 Flow/0 Capsule 仍有完整 entry disposition/Gap/accounting，并让 分析步骤“流程解释” Provider calls=0。
- 双Flow fixture必须让一个Flow为ELIGIBLE、一个为INELIGIBLE；`flow-coverage.json`的两个Flow集合互斥并集等于全部Flow，mapping domain、逐Flow非空Gap和全局Gap union都与Capsule逐字相等。任一overlap、omission、empty/foreign/missing Gap mutation都必须fail closed。
- 不同 root/input order 产生相同 canonical artifacts。
- multi-flow fixture 至少有 DepotHead与第二入口各自独立Flow/Capsule；一条成功、一条Gap或缺shard时，已完整安装slice保留但analysis step/run不得误报完成。改变shard size/order，六文件bytes必须相同。

验收必须同时覆盖至少两个非空Flow/Capsule（其中一个可用DepotHead讲解）、eligible/ineligible各一Flow及完整逐Flow Gap mapping、一个多Outcome入口、第二入口ownership隔离、其中一Flow Gap、缺/重叠shard、逐edge/span deletion mutation，以及从历史审计提炼的DepotHead 0/0回归baseline。只有每个COMPILED入口各生成一个入口根Flow/一个Capsule且所有终点闭合、完整entry ledger与eligibility分区/mapping守恒，0/0 baseline生成完整六文件/Gap/accounting并使Provider seam调用数为0，BusinessFlows才算可交付；单Flow PASS不构成验收。

### 8.7 已冻结裁决：实现者不得自由推断

- Flow 边界是 entry，不是 return/throw；一个 compiled entry 恰一个入口根 Flow，多个结束方式只能是 Outcomes。
- 只沿 EXACT call、显式 CFG polarity 和闭合 call/return 遍历；行号、异常习惯或模型不能补路径。
- Fact/atom/Gap 有唯一 Flow ownership；共享子流程只能按版本化 parent/child rule 表示。
- 每个Flow恰一个最小EvidenceCapsule；Capsule只从Proof roots投影，不能用“多给模型一点”作为降级。该同一artifact是FlowInterpretation R0/R1/R2唯一源码语义来源，且R0 basis只能来自显式`registryProposalBasis*`集合。
- GAP/EXCLUDED entry 不生成 Capsule；0 Flow/0 Capsule 是可发布给 分析步骤“九章文档” 的可信分析状态，并强制 分析步骤“流程解释” 零调用。
- ApplicationDiscovery全入口必须唯一处置，`N` compiled Flow严格对应`N` Capsule；分片只改变调度，不得采样/截断或让单Flow success完成analysis step/run。
- 本分析步骤只产Flow/Capsule与accounting，不产解释或Markdown；per-Flow Markdown及片段拼接均不在目标内。
- 遍历器、最小覆盖算法和内部类可自行实现；Flow/Outcome/Capsule 边界、双射、coverage、identity、Gap/fatal 不得改变。

## 9. 当前实现成熟度审计

Wire Reset后的`org.sourceanalysis.app.analysis.flow`目前只有语义package骨架；当前没有Flow compiler、EvidenceCapsule projector或六项本步骤产物。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 目标package与JDK 17 Toolchain已就位；通用wire头门禁只判断`SOURCE_ANALYSIS/v1`，不建立Flow eligibility。 |
| **本步骤生产能力尚未实现** | M1–M3、Entry→Flow唯一处置、Outcome、Flow/Capsule双射、eligible/ineligible分区与六项正式输出均不存在。 |
| **历史证据，不是当前能力** | 已删除的pre-reset compiler曾在有限fixture上编译Flow/Outcome/Capsule；固定八文件历史审计为blocking Gap、0 Flow、0 Capsule。该结果只作为0调用和证明不足的回归baseline。 |
| **下一实现门** | 按本章消费已持久化ApplicationDiscovery、ProgramGraphs和ProvenCodeFacts，至少用双入口、双Flow、多Outcome、跨Flow隔离、eligible/ineligible和0Flow场景闭合全仓分母。 |

若新实现对某Flow没有Capsule，分析步骤“流程解释”对该Flow必须零调用，而不是让模型直接读Service/XML；历史0 Capsule本身不是当前运行结果。
