# 05 编译完整业务流程

> 总体设计权威：[GitHub Code Agent 总体设计](../DESIGN.md)。

## 1. 为什么存在

Fact 说明一件事成立，却没有说明一个请求从哪里开始、经过哪些条件、在哪些终点结束。模型若直接收到 Fact 列表，仍可能把多个入口、分支和副作用拼成一段不存在的故事。

Stage 05 由程序处理 Stage02 **全入口 inventory**：每个支持入口编译成 FlowSlice，并为每条 Flow 生成唯一、最小、预算内的 EvidenceCapsule；不能闭合的入口保留有证据的 GAP，明确不在目标 profile 的入口保存 EXCLUDED。Flow 是业务过程边界；Outcome 是同一过程的一种结束方式，不能把每个终点误拆成独立 Flow。DepotHead 只是仓库 `N` 个 FlowSlice 候选中的一个例子。

## 2. 具体输入与 DepotHead 例子

输入是 Stage 02 entry/capability artifacts、Stage 03 call/control/data/evidence graphs、Stage 04 Facts/Proof/Gaps、flow/evidence profiles 和预算。

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本文件可用未来闭合后的 Flow/Capsule 值展示模块接力，但当前 fixed slice 仍是 Gap、0 Flow、0 Capsule；技术 unknown 只用 nullable/UNRESOLVED/Gap/fatal 表达。

DepotHead **REAL_SOURCE** 包含：

- POST /depotHead/batchSetStatus；
- status 0/1 分支和当前单据状态检查，Service :752-776；
- 强审核/负库存/出入库条件，Service :777-796；
- dhIds 非空后 status/update，Service :798-803；
- 库存更新、日志和 return，Service :804-821；
- Mapper/XML status 持久化路径。

目标 Flow 应以 HTTP entry 为根，保留每个 guard polarity 和 terminal。**但当前结果不是成功 Flow**：固定 jshERP slice 仍是 Gap、0 Flow、0 Capsule。

当前诚实 output 形状：

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
2. 从 Stage02 `repositoryEntryCoverage.entryIds` 取得完整入口分母，按 entryId 排序/分片，每个入口创建一个 flow ownership scope。
3. 从 entry method 沿 EXACT call edge、显式 CFG edge 和 call/return pair 遍历。
4. 每个 guard 记录 guardId、conditionAtomId、TRUE/FALSE polarity；不从行号猜极性。
5. 到 return、throw 或 profile 支持的 stop 封闭 OutcomePath；同终点不同 decision sequence 仍是不同 Outcome。
6. 所有 Outcome 求最长公共入口前缀，形成 sharedSteps；分支保留在 Outcomes。
7. 将 Fact/atom/Gap 分配给唯一 Flow；共享子流程只有通过版本化 parent/child ownership rule 才可拆分。
8. 从 Proof root 选择直接表达入口、条件、读取、写入和终点的 source spans；同时确定 `registryProposalBasisAtomIds` 与 `registryProposalBasisGapIds`，它们是 R0 可引用的完整、闭合 basis 分母。
9. 建 projection obligations；每个 atom/Outcome 至少有一个 span，且删除任一 span 都会损失义务。R0、R1、R2 必须读取同一 Capsule artifact，不能各自选择不同源码切片。
10. 对每个 entry 写 COMPILED/GAP/EXCLUDED；验证 entry shard denominator 两两不交叠且 union 等于 Stage02 全 entry IDs。
11. 重算 entry/Outcome/Fact/atom/Gap/capsule/omitted coverage 后原子安装 Stage 05；任意一条 Flow 成功都不能关闭尚未处置的 entry。

## 4. 生成的可观察产物

| 文件 | 唯一职责 |
| --- | --- |
| flow-slices.json | FlowSlice、OutcomePath、steps、facts/atoms/gaps 和 parent/child refs |
| flow-coverage.json | 仓库 entry/outcome/call/mapper/fact/atom/evidence、shard 与 unsupported/failed/omitted 的分母与分子 |
| entry-dispositions.jsonl | 每个发现入口恰一条 COMPILED/GAP/EXCLUDED |
| evidence-capsules.jsonl | 每个 COMPILED Flow 恰一个模型阅读包 |
| flow-gaps.jsonl | blocking/warning Flow-local Gap 及 provenance |
| stage-receipt.json | upstream roots、control hashes、artifact set、Flow/Capsule counts |

未来 DepotHead 成功后的 Capsule 只能包含该 Flow 的必要 spans；它属于上述统一 walkthrough 声明：

~~~text
Controller :43,:178-191
Service :741-821
Mapper Java :23
DepotHead :63,:301-307
DepotHeadExample :149-151
Mapper XML :3,:70-93,:385-497
~~~

是否需要更细 span 由 projection obligations 决定，不能把上面行段硬编码为 golden。

目标出口不会因 0 Flow 变成空结果：六个命名文件必须全部存在；每个 Stage 02 entry 在 `entry-dispositions.jsonl` 恰有一条记录，`flow-coverage.json` 保存完整分母及 shard receipts，`flow-gaps.jsonl` 保存 blocking reason，receipt 明确 `flowSliceCount=0`、`evidenceCapsuleCount=0`。未来 DepotHead 正向出口则必须有一个入口根 Flow、完整 Outcomes 和恰一个 Capsule，但仓库 stage 只有在**其他所有入口也各有 COMPILED/GAP/EXCLUDED**后才闭合，而不是只把 DepotHead count 改成 1。

Artifact cardinality 固定：若完整仓库编译出 `N` 个 FlowSlice，则必有 `N` 个 EvidenceCapsule，且每对保留相同 `flowSliceId`；Stage05 不生成解释或 Markdown。每个 Flow/Capsule独立持久化并可单独恢复，最终 public files是所有 shard 的 canonical ID-set union。

### 4.1 人类 walkthrough：模块用什么文件接力

下面继续采用未来 Fact/graph closure 已补齐的目标分支；当前 implementation audit 的 0/0 不变。

~~~jsonl
{"module":"EntryRootedFlowCompiler","artifact":"modules/01-flow-compiler/flow-compilation.json","takesFrom":["Stage02Reference","Stage03Reference","Stage04Reference"],"says":{"entryId":"entry:post-depothead-batch-set-status","flowSliceId":"flow:post-depothead-batch-set-status","factId":"fact:depothead-status-persistence","outcomes":["outcome:no-eligible-document","outcome:status-updated"]}}
{"module":"EvidenceCapsuleProjector","artifact":"modules/02-capsule-projector/capsule-projection.json","takesFrom":["flow-compilation.json","Proof/Evidence/source"],"says":{"flowSliceId":"flow:post-depothead-batch-set-status","evidenceCapsuleId":"capsule:post-depothead-batch-set-status","covers":["entry route","dhIds guard","status value path","id IN where","both terminals"],"registryProposalBasisAtomIds":["atom:input-field","atom:value-source"],"registryProposalBasisGapIds":["gap:runtime-status-policy"],"usedBy":["R0","R1","R2"]}}
{"module":"FlowArtifactPublisher","artifact":"modules/03-publish/stage05-publication.json","takesFrom":["flow-compilation.json","capsule-projection.json"],"says":{"flowCount":1,"capsuleCount":1,"publicFiles":["flow-slices.json","flow-coverage.json","entry-dispositions.jsonl","evidence-capsules.jsonl","flow-gaps.jsonl","stage-receipt.json"],"nextStage":"06-interpret-one-flow-at-a-time"}}
~~~

M1 的 `entryId/factId/outcomePathIds` 原样进入 M2；M2 只新增 `evidenceCapsuleId` 和直接语义 spans。当前缺 Proof 时 M1 产生 GAP/0 Flow，M2 产生0 Capsule，M3仍发布完整0/0 artifact set。

## 5. 下游怎样消费而不返工

Stage 06 每次按 flowSliceId 读取恰好一个 FlowSlice 和一个 EvidenceCapsule。R0、R1、R2 的源码语义都必须来自这同一个 persisted Capsule；R0 只能引用 Capsule 明列的 registry proposal basis，R1/R2 不能旁路读取 R0 raw output 之外的源码。它看不到：

- 仓库 root 或任意 Path；
- 其他 Flow/Capsule；
- 未准入 Fact；
- Proof-only dependency nodes；
- 五张完整 graph；
- inventory 外文件。

Stage 06 无权发现 Flow、补 Outcome、修改条件或请求“再读一点仓库”。Capsule 不充分时回到新版本 Stage 05/上游 run，而不是模型侧扩展。

### 下游前置条件与后置保证

| Stage 06 开始前必须成立 | Stage 05 成功后保证 |
| --- | --- |
| Stage 02 entries、Stage 03 graph roots、Stage 04 Fact/Proof/Gaps 与 Stage 05 controls 全一致 | 每个发现 entry 恰一条 COMPILED/GAP/EXCLUDED disposition，入口与 Outcome 分母守恒 |
| 六个 output files、receipt root、Flow/Outcome/Fact/atom/Gap/capsule refs 全闭合 | 每个 COMPILED entry 恰一个入口根 Flow，每个 Flow 恰一个最小 Capsule；GAP/EXCLUDED 没有 Capsule |
| Capsule span hashes、projection obligations、R0 basis allowlist 和预算重验通过 | Stage 06 可从同一Capsule先建一个R0 slot，程序freeze registry后再建R1/R2；0 Flow可确定0 Provider call |
| entry shard receipts不交叠且union精确等于Stage02全部entryIds；每entry有唯一disposition | Stage06得到完整的eligible Flow集合和全部Gap/排除accounting；一Flow PASS不足以结束Stage05/run |

Stage 06 若看到多 Capsule、缺 Capsule、未闭合 Outcome 或越界 ref，必须拒绝整个 Stage 05 input；不能让模型选择“看哪一份”。

## 6. 成功、Gap、fatal 与恢复

- **成功**：完整仓库所有 entry 都有唯一 disposition；每个 COMPILED entry 恰一个入口根 Flow/Capsule；全 shard union、coverage 和 projection closure 闭合。
- **带 Gap 成功**：某 entry 为 GAP，或 Flow 带 warning Gap。0 Flow/0 Capsule 是合法 SUCCEEDED_WITH_GAPS，并显式阻止模型调用。
- **fatal**：上游 replay mismatch、graph/proof reference broken、branch polarity/terminal closure broken、accounting 不守恒、source span 漂移、projection 不可满足或 identity collision。
- **恢复**：重验 upstream roots、entry shard receipts 和 Stage 05 artifact root。已完成 deterministic Flow/Capsule shard保留，未开始 shard可重跑；缺/重叠 shard不能发布。完全相等才让 Stage 06 读取；fatal 不删除五图/Facts或已完成slice artifacts。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 定义 Flow/Outcome 边界 | 是 | 否 |
| 计算 branch polarity/coverage | 是 | 否 |
| 选择 Capsule spans | 是，按 Proof/obligation | 否 |
| 阅读/命名 Flow | 否，留到 Stage 06 | 否 |

本阶段产品运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块执行顺序固定为 `EntryRootedFlowCompiler` → `EvidenceCapsuleProjector` → `FlowArtifactPublisher`。每个模块先安装 canonical artifact；后继只读 artifact，不读取前驱内存图或私有类。

#### M1 EntryRootedFlowCompiler

- **解决的问题**：把每个发现 entry 的 exact graph/Fact closure 编译成一个完整 Flow、多条 Outcomes 或明确 GAP/EXCLUDED。
- **精确上游输入及前置**：valid Stage02 complete entry inventory/coverage、Stage03 call/control/data graphs、Stage04 Facts/Proof/Gaps、flow profile/budget；所有 roots/entry/edge/fact refs及Stage02 entry denominator已重验。
- **确定性顺序 / LLM**：固定全 entryId denominator → 按entryId shard → 建 ownership scope → 沿 EXACT call/CFG/call-return → 枚举 decision sequences/terminals → longest common prefix → 分配 Fact/atom/Gap → per-entry disposition → shard union/account；0 LLM。
- **目标输出与 DepotHead 示例**：`FlowCompilation{entryDispositions,flowSlices,outcomePaths,ownership,coverage}`；未来例为一个 POST entry Flow、多 Outcome，当前例为该 entry 的 GAP/0 Flow与 blocking refs。
- **必须保持的不变量**：Stage02每 entry 恰一 disposition；COMPILED 恰一个 root Flow；每 terminal path 恰一 Outcome disposition；Fact/atom/Gap owner 唯一；entry shards不交叠且union等于完整entry denominator。
- **Gap / fatal / 恢复**：unsupported/ambiguous/unproven path 是 entry/Outcome Gap；broken graph/Proof refs、missing polarity/terminal、duplicate owner/accounting fatal；恢复重放完整 entry set。
- **给下游的后置保证**：M2 得到闭合 Flow/Outcome/Fact/Proof ownership，或可解释的零 Flow，绝不需要补 path。
- **明确非目标**：不选 source spans、不调用模型、不按 terminal 拆多个 Flow、不发明业务名。
- **公共测试 seam 与验收**：`compile(entries, graphs, facts, profile)` 覆盖至少两非空entry/Flow、多 Outcome、第二 entry 隔离、其中一entry Gap/恢复、缺/重叠 shard、loop/ambiguous Gap、edge deletion fatal 与 DepotHead 0/0 baseline；ID-set accounting必须闭合且一Flow PASS不能完成stage。
- **Luna/xhigh 测试指南**：创建 `Stage05EntryRootedFlowCompilerTest`，冻结Stage02–04 files、多Outcome fixture及当前DepotHead 0/0 golden于 `src/test/resources/target/stage05/flow-compiler/`。逐RED：单entry多Outcome、第二entry隔离、shared prefix、loop/ambiguous Gap、edge/polarity deletion、0/0 disposition、order determinism；首RED因seam/schema缺失。只fakeartifact reader，flow/accounting不可mock。命令：`mvn -Dtest=Stage05EntryRootedFlowCompilerTest test`；禁网络/客户执行。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后只改 `target/stage05/flowcompiler/`，实现 public `EntryRootedFlowCompiler/FlowCompilation` 与 `stage05-flow-compilation-v1`；只读Stage02–04 artifacts，entry→traversal→terminal paths→shared prefix→ownership/accounting。逐slice GREEN；不得行号补路/按terminal拆Flow/硬编码DepotHead。需新graph/Fact字段MUST STOP交Sol/ultra并按跨stage规则升级，完成更新审计。

#### M2 EvidenceCapsuleProjector

- **解决的问题**：为每个 compiled Flow 从 Proof roots 选择唯一、最小、预算内、模型可读的 source投影。
- **精确上游输入及前置**：M1 FlowCompilation artifact、Stage04 ProofPack、Stage03 Evidence graph、Stage01 source handles、projection profile/budget；每 Flow/Outcome/atom/proof ref 闭合。
- **确定性顺序 / LLM**：按 flowSliceId → 枚举 ATOM/OUTCOME obligations → 从 Proof roots取 direct-semantic spans → stable set cover → exact excerpt/hash → deletion minimality/closure check；0 LLM。
- **目标输出与 DepotHead 示例**：`CapsuleProjection{capsules,modelEvidenceSpans,projectionObligations,flowShardReceipts,budgetUsage}`；未来一个 DepotHead Flow 对应一个 Capsule，含 route、guards、status write、id where 与 terminals 的非空 spans；multi-flow fixture为每个其他Flow另有独立Capsule。
- **必须保持的不变量**：`N` compiled Flow↔`N` Capsule 一一对应；flow shards不交叠且union等于M1 flowSliceIds；每 obligation有支持且每 span不可冗余；span只来自 own Flow Proof、原始 bytes不 trim。
- **Gap / fatal / 恢复**：无安全 split 的预算超限使对应 Flow blocking Gap；source drift、cross-Flow span、unsatisfied/redundant projection、ref broken fatal；恢复重算整个 affected Flow projection。
- **给下游的后置保证**：M3/Stage06 可按 flowSliceId 得到恰一个 closed Capsule，且 `registryProposalBasisAtomIds/GapIds` 是 R0 唯一可用basis；R0/R1/R2共享该artifact，模型不需Path/graphs/其他Flow。
- **明确非目标**：不缩减 ProofPack本身、不解释业务、不让 Provider请求额外源码。
- **公共测试 seam 与验收**：`project(flowCompilation, proofs, evidence, source)` 使用至少两Flow，对每span deletion、proof-only injection、cross-Flow借用、source mutation、缺/重叠shard和预算边界；另断言R0 basis非空、闭合、同Flow且R0/R1/R2 sourceArtifactId完全相等。正例每Flow独立obligations闭合且删除任一span失败。
- **Luna/xhigh 测试指南**：创建 `Stage05EvidenceCapsuleProjectorTest`，fixtures/goldens在 `src/test/resources/target/stage05/capsule-projector/`。RED顺序：一个Flow完整obligations/spans→R0 basis closure→R0/R1/R2同artifact→逐span deletion→proof-only冗余→cross-Flow借用→source drift→budget no-safe-split Gap→root determinism；首RED因projector/schema缺失。仅fake source handle，set-cover/obligation/canonical不可mock。命令：`mvn -Dtest=Stage05EvidenceCapsuleProjectorTest test`；无Provider/network。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `target/stage05/capsuleprojector/`，实现 public `EvidenceCapsuleProjector/CapsuleProjection` 与 `stage05-capsule-projection-v2`；消费M1+Proof/Evidence/source，obligation→eligible spans→stable minimal cover→exact excerpts/hash→R0 basis closure。逐RED GREEN；不得多给源码、跨Flow、trim bytes或给R0/R1/R2不同source。缺Proof语义/需跨stage变更MUST STOP，完成更新审计。

#### M3 FlowArtifactPublisher

- **解决的问题**：组合 Flow 与 Capsule artifacts，守恒全部 entry/outcome/fact/atom/Gap后形成 Stage05Reference。
- **精确上游输入及前置**：M1全仓 FlowCompilation/shard receipts、M2全 Flow CapsuleProjection/shard receipts、Stage02–04 roots和Stage05 controls；完整entry dispositions与Flow/Capsule双射或明确0/0已局部验证。
- **确定性顺序 / LLM**：验证entry/flow shard disjoint union → join by flowSliceId → 重算全仓 coverage/dispositions/unsupported/failed/omitted → canonical六文件 → staging force/SHA/reference validation → atomic install；0 LLM。
- **目标输出与 DepotHead 示例**：六个 exact files；当前例 entry-dispositions 有 DepotHead GAP、flow/capsule arrays零记录、Gap/coverage/receipt非空；未来正例有同 entryId 的 Flow/Capsule。
- **必须保持的不变量**：每 COMPILED Flow恰一个 Capsule，GAP/EXCLUDED无 Capsule；所有Stage02 entry唯一处置，single Flow PASS不等于stage/run完成；public files只引用M1/M2 IDs；publisher不改语义。
- **Gap / fatal / 恢复**：合法0/0为SUCCEEDED_WITH_GAPS；orphan/multiple Capsule、coverage/ref/canonical/install/collision错误 fatal；恢复只认完整receipt。
- **给下游的后置保证**：Stage06能纯 artifact-driven 地得到 task cardinality；0 Flow严格推出0 task/call。
- **明确非目标**：不修 Flow/Capsule、不重读 source、不调用 Provider。
- **公共测试 seam 与验收**：`publish(flowCompilation, capsuleProjection, controls)` 覆盖0/0、至少2 Flow/2 Capsule、one-to-one、一个Flow失败后恢复、single-PASS/other-omitted、缺/重叠 shard、orphan/duplicate、乱序、crash/collision；只有完整六文件和仓库ledger闭合返回Stage05Reference。
- **Luna/xhigh 测试指南**：创建 `Stage05FlowArtifactPublisherTest`，module files/goldens放 `src/test/resources/target/stage05/flow-artifact-publisher/`。逐RED：0/0 six-file set、one Flow/one Capsule、orphan/duplicate、coverage count spoof、乱序、crash/collision/resume；首RED因publisher缺失。只mockartifact-store，join/accounting/canonical不mock。命令：`mvn -Dtest=Stage05FlowArtifactPublisherTest test`；禁网络/Provider/customer Maven。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后只改 `target/stage05/flowartifactpublisher/`，实现 public `FlowArtifactPublisher/Stage05Reference` 与 `stage05-flow-publication-v1`；只读M1/M2 module artifacts，join→coverage→six files→atomic. 每RED GREEN，0/0必须让direct Provider seam 0调用；禁止补Flow/Capsule。跨stage contract改变MUST STOP交Sol/ultra/用户，更新审计。

### 8.0.1 模块 artifact wire schemas

使用 DESIGN 13.3 envelope；`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-flow-compiler/flow-compilation.json` | `stage05-flow-compilation-v1` / `STAGE05_FLOW_COMPILATION` | Stage02+Stage03+Stage04 IDs/SHAs | `flowCompilationId!`、`entryDispositions[]!{entryId!,disposition!,flowSliceId?,gapIds[]!,reasonCode?,evidenceRefs[]!}`、`flowSlices[]!`、`entryShardReceipts[]!{shardId!,denominatorEntryIds[]!,dispositionEntryIds[]!,flowSliceIds[]!,status!,gapIds[]!}`、`coverage!`；entries/flows/shards按ID，steps/decisions保持语义顺序，outcomes按outcomePathId |
| `modules/02-capsule-projector/capsule-projection.json` | `stage05-capsule-projection-v2` / `STAGE05_CAPSULE_PROJECTION` | M1+Stage01+Stage03+Stage04 IDs/SHAs | `capsuleProjectionId!`、`flowCompilationId!`、`capsules[]!{evidenceCapsuleId!,flowSliceId!,proofPackId!,outcomePathIds[]!,allowedFacts[]!,allowedGaps[]!,registryProposalBasisAtomIds[]!,registryProposalBasisGapIds[]!,modelEvidenceSpanIds[]!,projectionObligationIds[]!,budgetUsage!}`、`modelEvidenceSpans[]!{spanId!,locator!,sourceFileSha256!,excerpt!,excerptSha256!,supportedAtomIds[]!,supportedOutcomePathIds[]!}`、`projectionObligations[]!{obligationId!,kind!,semanticItemId!,satisfyingSpanIds[]!}`、`flowShardReceipts[]!`、`budgetUsage!`；capsules/spans/obligations/shards按ID，basis IDs按UTF-8 byte order |
| `modules/03-publish/stage05-publication.json` | `stage05-flow-publication-v1` / `STAGE05_FLOW_PUBLICATION` | M1+M2 IDs/SHAs | `stageStatus!`、`flowCompilationId!`、`capsuleProjectionId!`、`flowCount!`、`capsuleCount!`、`repositoryFlowCoverage!{entryIds[]!,compiledEntryIds[]!,gappedEntryIds[]!,excludedEntryIds[]!,flowSliceIds[]!,capsuleIds[]!,entryShardReceiptIds[]!,flowShardReceiptIds[]!,closed!}`、`stageArtifactRoot!`、`publishedArtifacts[6]!`、`nextStage!`；ID arrays/files按canonical ID/path |

FlowSlice/Outcome/BranchDecision字段按8.1；COMPILED entry的flowSliceId non-null且gapIds可为空，GAP/EXCLUDED的flowSliceId必须null并有reason/Gap。技术示例的静态unknown必须成为GAP disposition，不能用示例Outcome补齐。Capsule excerpt必须是locator原始bytes；任何schema/ownership/projection/sort/identity变化先设计+升version，下游不可回读graph/source补字段。

`repositoryFlowCoverage.closed` 只有在 Stage02 repositoryEntryCoverage已闭合、全部entry/flow shards守恒且每entry处置完成时为true；DepotHead bounded示例固定为false，即使局部一Flow/一Capsule双射成立。

~~~jsonl
{"schemaVersion":"stage05-flow-compilation-v1","artifactType":"STAGE05_FLOW_COMPILATION","artifactId":"flow-compilation:1111111111111111111111111111111111111111111111111111111111111111","producer":{"stage":5,"module":"EntryRootedFlowCompiler","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"stage02-publication:4444444444444444444444444444444444444444444444444444444444444444","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"stage03-publication:6666666666666666666666666666666666666666666666666666666666666666","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"stage04-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"flowCompilationId":"flow-compilation:depothead-v1","entryDispositions":[{"entryId":"entry:post-depothead-batch-set-status","disposition":"COMPILED","flowSliceId":"flow:post-depothead-batch-set-status","gapIds":["gap:runtime-status-policy"],"reasonCode":null,"evidenceRefs":["fact:depothead-status-persistence"]}],"flowSlices":[{"flowSliceId":"flow:post-depothead-batch-set-status","entryId":"entry:post-depothead-batch-set-status","trigger":"POST /depotHead/batchSetStatus","rootNodeId":"method:controller-batch-set-status","sharedSteps":[{"kind":"ENTRY","nodeId":"method:controller-batch-set-status"},{"kind":"CALL_CHILD","nodeId":"method:service-batch-set-status"}],"factIds":["fact:depothead-status-persistence"],"atomIds":["atom:eligible-id-set","atom:entry-route","atom:value-source","atom:where-key","atom:where-operator"],"outcomePaths":[{"outcomePathId":"outcome:no-eligible-document","decisions":[{"guardNodeId":"guard:dhids-not-empty","conditionAtomId":"atom:eligible-id-set","polarity":"FALSE","normalizedCondition":"eligible id set is empty"}],"terminalNodeId":"terminal:return-result","terminalKind":"RETURN","terminalFactIds":[],"requiredAtomIds":["atom:eligible-id-set"],"requiredProofIds":["proof:eligible-id-set"]},{"outcomePathId":"outcome:status-updated","decisions":[{"guardNodeId":"guard:dhids-not-empty","conditionAtomId":"atom:eligible-id-set","polarity":"TRUE","normalizedCondition":"eligible id set is not empty"}],"terminalNodeId":"terminal:return-result","terminalKind":"RETURN","terminalFactIds":["fact:depothead-status-persistence"],"requiredAtomIds":["atom:value-source","atom:where-key","atom:where-operator"],"requiredProofIds":["proof:value-source","proof:where-key","proof:where-operator"]}],"gapIds":["gap:runtime-status-policy"],"parentFlowSliceId":null,"childFlowSliceIds":[]}],"entryShardReceipts":[{"shardId":"entry-shard:depothead","denominatorEntryIds":["entry:post-depothead-batch-set-status"],"dispositionEntryIds":["entry:post-depothead-batch-set-status"],"flowSliceIds":["flow:post-depothead-batch-set-status"],"status":"SUCCEEDED","gapIds":[]}],"coverage":{"entryIds":["entry:post-depothead-batch-set-status"],"compiledEntryIds":["entry:post-depothead-batch-set-status"],"gappedEntryIds":[],"excludedEntryIds":[],"outcomeIds":["outcome:no-eligible-document","outcome:status-updated"],"ownedFactIds":["fact:depothead-status-persistence"],"ownedGapIds":["gap:runtime-status-policy"]}}}
{"schemaVersion":"stage05-capsule-projection-v2","artifactType":"STAGE05_CAPSULE_PROJECTION","artifactId":"capsule-projection:2222222222222222222222222222222222222222222222222222222222222222","producer":{"stage":5,"module":"EvidenceCapsuleProjector","moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"flow-compilation:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"stage01-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"artifactId":"stage04-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"capsuleProjectionId":"capsule-projection:depothead-v2","flowCompilationId":"flow-compilation:depothead-v1","capsules":[{"evidenceCapsuleId":"capsule:post-depothead-batch-set-status","flowSliceId":"flow:post-depothead-batch-set-status","proofPackId":"proof-pack:depothead-v1","outcomePathIds":["outcome:no-eligible-document","outcome:status-updated"],"allowedFacts":["fact:depothead-status-persistence"],"allowedGaps":["gap:runtime-status-policy"],"registryProposalBasisAtomIds":["atom:input-field","atom:value-source"],"registryProposalBasisGapIds":["gap:runtime-status-policy"],"modelEvidenceSpanIds":["span:controller-entry","span:example-id-in","span:service-guard","span:service-id-criterion","span:service-status","span:xml-status","span:xml-where"],"projectionObligationIds":["obligation:entry-route","obligation:no-eligible-outcome","obligation:status-updated","obligation:value-source","obligation:where-id-in"],"budgetUsage":{"spanCount":7,"utf8Bytes":284}}],"modelEvidenceSpans":[{"spanId":"span:controller-entry","locator":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:43,178-191","sourceFileSha256":"2222222222222222222222222222222222222222222222222222222222222222","excerpt":"@RequestMapping(\"/depotHead\") + @PostMapping(\"/batchSetStatus\")","excerptSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","supportedAtomIds":["atom:entry-route"],"supportedOutcomePathIds":["outcome:no-eligible-document","outcome:status-updated"]},{"spanId":"span:example-id-in","locator":"jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHeadExample.java:149-151","sourceFileSha256":"4444444444444444444444444444444444444444444444444444444444444444","excerpt":"addCriterion(\"id in\", values, \"id\")","excerptSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","supportedAtomIds":["atom:where-key","atom:where-operator"],"supportedOutcomePathIds":["outcome:status-updated"]},{"spanId":"span:service-guard","locator":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:798-821","sourceFileSha256":"6666666666666666666666666666666666666666666666666666666666666666","excerpt":"if(!dhIds.isEmpty()) { ... } return result;","excerptSha256":"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee","supportedAtomIds":["atom:eligible-id-set"],"supportedOutcomePathIds":["outcome:no-eligible-document","outcome:status-updated"]},{"spanId":"span:service-id-criterion","locator":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:798-803","sourceFileSha256":"6666666666666666666666666666666666666666666666666666666666666666","excerpt":"example.createCriteria().andIdIn(dhIds);","excerptSha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","supportedAtomIds":["atom:eligible-id-set","atom:where-key","atom:where-operator"],"supportedOutcomePathIds":["outcome:status-updated"]},{"spanId":"span:service-status","locator":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:798-803","sourceFileSha256":"6666666666666666666666666666666666666666666666666666666666666666","excerpt":"depotHead.setStatus(status);","excerptSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","supportedAtomIds":["atom:value-source"],"supportedOutcomePathIds":["outcome:status-updated"]},{"spanId":"span:xml-status","locator":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:472-473","sourceFileSha256":"8888888888888888888888888888888888888888888888888888888888888888","excerpt":"status = #{record.status}","excerptSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","supportedAtomIds":["atom:value-source"],"supportedOutcomePathIds":["outcome:status-updated"]},{"spanId":"span:xml-where","locator":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:494-495","sourceFileSha256":"8888888888888888888888888888888888888888888888888888888888888888","excerpt":"<include refid=\"Update_By_Example_Where_Clause\" />","excerptSha256":"9999999999999999999999999999999999999999999999999999999999999999","supportedAtomIds":["atom:where-key","atom:where-operator"],"supportedOutcomePathIds":["outcome:status-updated"]}],"projectionObligations":[{"obligationId":"obligation:entry-route","kind":"ATOM_DIRECT_SEMANTICS","semanticItemId":"atom:entry-route","satisfyingSpanIds":["span:controller-entry"]},{"obligationId":"obligation:no-eligible-outcome","kind":"OUTCOME_TERMINAL","semanticItemId":"outcome:no-eligible-document","satisfyingSpanIds":["span:service-guard"]},{"obligationId":"obligation:status-updated","kind":"OUTCOME_TERMINAL","semanticItemId":"outcome:status-updated","satisfyingSpanIds":["span:service-guard","span:service-status","span:xml-status"]},{"obligationId":"obligation:value-source","kind":"ATOM_DIRECT_SEMANTICS","semanticItemId":"atom:value-source","satisfyingSpanIds":["span:service-status","span:xml-status"]},{"obligationId":"obligation:where-id-in","kind":"ATOM_SET_DIRECT_SEMANTICS","semanticItemId":"atom-set:where-id-in","satisfyingSpanIds":["span:example-id-in","span:service-id-criterion","span:xml-where"]}],"flowShardReceipts":[{"shardId":"flow-shard:depothead","denominatorFlowSliceIds":["flow:post-depothead-batch-set-status"],"capsuleIds":["capsule:post-depothead-batch-set-status"],"status":"SUCCEEDED","gapIds":[]}],"budgetUsage":{"capsuleCount":1,"spanCount":7,"utf8Bytes":284}}}
{"schemaVersion":"stage05-flow-publication-v1","artifactType":"STAGE05_FLOW_PUBLICATION","artifactId":"stage05-publication:3333333333333333333333333333333333333333333333333333333333333333","producer":{"stage":5,"module":"FlowArtifactPublisher","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"capsule-projection:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"flow-compilation:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"stageStatus":"SUCCEEDED_WITH_GAPS","flowCompilationId":"flow-compilation:depothead-v1","capsuleProjectionId":"capsule-projection:depothead-v2","flowCount":1,"capsuleCount":1,"repositoryFlowCoverage":{"entryIds":["entry:post-depothead-batch-set-status"],"compiledEntryIds":["entry:post-depothead-batch-set-status"],"gappedEntryIds":[],"excludedEntryIds":[],"flowSliceIds":["flow:post-depothead-batch-set-status"],"capsuleIds":["capsule:post-depothead-batch-set-status"],"entryShardReceiptIds":["entry-shard:depothead"],"flowShardReceiptIds":["flow-shard:depothead"],"closed":false},"stageArtifactRoot":"stage-root:0505050505050505050505050505050505050505050505050505050505050505","publishedArtifacts":[{"path":"entry-dispositions.jsonl","sizeBytes":800,"sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"path":"evidence-capsules.jsonl","sizeBytes":2200,"sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"path":"flow-coverage.json","sizeBytes":1000,"sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"path":"flow-gaps.jsonl","sizeBytes":600,"sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"path":"flow-slices.json","sizeBytes":2400,"sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"path":"stage-receipt.json","sizeBytes":1200,"sha256":"6666666666666666666666666666666666666666666666666666666666666666"}],"nextStage":"06-interpret-one-flow-at-a-time"}}
~~~

### 8.1 Interface 与 records

~~~java
interface BusinessFlowCompiler {
    Stage05Reference compile(Stage02Reference entries,
                             Stage03Reference graphs,
                             Stage04Reference facts);
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
  outcomePathIds[]
  allowedFacts[]
  allowedGaps[]
  registryProposalBasisAtomIds[]
  registryProposalBasisGapIds[]
  modelEvidenceSpans[]
  projectionObligations[]
  budgetUsage

RepositoryFlowCoverage
  entryIds[]
  compiledEntryIds[]
  gappedEntryIds[]
  excludedEntryIds[]
  flowSliceIds[]
  capsuleIds[]
  entryShardReceiptIds[]
  flowShardReceiptIds[]
  closed
~~~

FlowStep kind registry 至少有 ENTRY、GUARD、READ、CALCULATE、WRITE、RESULT、CALL_CHILD；不能写任意 prose。

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
~~~

0/0 不能用来抹掉已知 outcome candidate；denominator 在遍历前由 entry/CFG/profile固定。

### 8.3 Capsule 最小性

ModelEvidenceSpan 保存 locator、sourceFileSha256、exact excerpt、excerptSha256、supportedAtomIds 和 supportedOutcomePathIds。excerpt 是 locator 原始 UTF-8 bytes，不 trim/格式化。

ProjectionObligation kind 只允许 ATOM_DIRECT_SEMANTICS 或 OUTCOME_TERMINAL。每个 obligation 至少一个 satisfying span；删除任一 span 后至少一个 obligation 失去全部支持，否则该 span 冗余。

ProofPack 回答事实为何成立；Capsule 回答模型最少读什么。Capsule 不能删除 Proof dependency，也不能自证 Fact。

### 8.4 Identity、预算与安全

Outcome ID 先于 Flow ID；span/obligation ID 先于 Capsule ID；coverage 和 Stage05 result/root 最后计算。预算进入 identity，即使没有触顶。

预算至少有 maxFlows、maxOutcomesPerFlow、maxFlowNodes/Edges、maxCapsules、maxSpansPerCapsule、maxSpanBytes、maxCapsuleUtf8Bytes、maxTraversalDepth。超限不截断 paths/spans。

只读 persisted artifacts/verified handles；不执行客户代码、模型或网络。Capsule 中的 prompt injection 文本只是 data。

### 8.5 Gap 与 failure codes

局部 Gap：

UNSUPPORTED_REACHABLE_SITE、AMBIGUOUS_CALL_TARGET、OUTCOME_BRANCH_UNRESOLVED、FLOW_FACT_NOT_ADMITTED、UNBOUNDED_RECURSION_OR_LOOP、CAPSULE_BUDGET_NO_SAFE_SPLIT、EXPECTATION_GAP_IN_FLOW_SCOPE。

Fatal：

STAGE05_REQUEST_INVALID、UPSTREAM_ARTIFACT_REPLAY_MISMATCH、FLOW_GRAPH_REFERENCE_BROKEN、FLOW_BRANCH_POLARITY_MISSING、FLOW_OUTCOME_CLOSURE_BROKEN、FLOW_FACT_PROOF_REFERENCE_BROKEN、FLOW_ACCOUNTING_INVARIANT_BROKEN、EVIDENCE_SOURCE_REOPEN_MISMATCH、EVIDENCE_PROJECTION_UNSATISFIABLE、EVIDENCE_PROJECTION_INVARIANT_BROKEN、STAGE05_RESOURCE_LIMIT_EXCEEDED、STAGE05_IDENTITY_COLLISION。

### 8.6 测试 seam 与验收

- 每删一条 CFG/call/data/Proof edge，对应 Outcome/Flow 必须 Gap/fatal，不能猜回。
- 同一 entry 多 terminals 编成一个 Flow 多 Outcomes，不复制成多个 Flow。
- 第二 entry 不能借用共享 Service 的 Fact/span。
- Capsule span 必须来自 Proof roots，不按文本相似命中 decoy。
- 每删一个直接语义 span，projection obligation 失败；proof-only span 注入被拒绝。
- 0 Flow/0 Capsule 仍有完整 entry disposition/Gap/accounting，并让 Stage 06 Provider calls=0。
- 不同 root/input order 产生相同 canonical artifacts。
- multi-flow fixture 至少有 DepotHead与第二入口各自独立Flow/Capsule；一条成功、一条Gap或暂缺shard时，已完成slice保留但stage/run不得误报完成。恢复后改变shard size/order，六文件bytes必须相同。

验收必须同时覆盖至少两个非空 Flow/Capsule（其中一个可用 DepotHead讲解）、一个多 Outcome 入口、第二入口 ownership 隔离、其中一 Flow失败/恢复、缺/重叠 shard、逐 edge/span deletion mutation，以及当前 DepotHead 0/0 baseline。只有每个COMPILED入口各生成一个入口根 Flow/一个 Capsule且所有终点闭合、完整entry ledger守恒，0/0 baseline生成完整六文件/Gap/accounting并使 Provider seam调用数为0，Stage05才算可交付；单 Flow PASS不构成验收。

### 8.7 已冻结裁决：实现者不得自由推断

- Flow 边界是 entry，不是 return/throw；一个 compiled entry 恰一个入口根 Flow，多个结束方式只能是 Outcomes。
- 只沿 EXACT call、显式 CFG polarity 和闭合 call/return 遍历；行号、异常习惯或模型不能补路径。
- Fact/atom/Gap 有唯一 Flow ownership；共享子流程只能按版本化 parent/child rule 表示。
- 每个Flow恰一个最小EvidenceCapsule；Capsule只从Proof roots投影，不能用“多给模型一点”作为降级。该同一artifact是Stage06 R0/R1/R2唯一源码语义来源，且R0 basis只能来自显式`registryProposalBasis*`集合。
- GAP/EXCLUDED entry 不生成 Capsule；0 Flow/0 Capsule 是可发布给 Stage 08 的可信分析状态，并强制 Stage 06 零调用。
- Stage02全入口必须唯一处置，`N` compiled Flow严格对应`N` Capsule；分片只改变调度，不得采样/截断或让单Flow success完成stage/run。
- 本阶段只产Flow/Capsule与accounting，不产解释或Markdown；per-Flow Markdown及片段拼接均不在目标内。
- 遍历器、最小覆盖算法和内部类可自行实现；Flow/Outcome/Capsule 边界、双射、coverage、identity、Gap/fatal 不得改变。

## 9. 当前实现差距审计

| 状态 | 当前事实 |
| --- | --- |
| **已验证（有限 profile、内存态）** | 现有 Stage02Compiler 在受支持 fixture 上编译 Flow/Outcome/Capsule，并有 coverage、Gap 和 mutation tests |
| **真实 jshERP 为缺口** | 固定八文件 slice 当前是 blocking Gap、0 FlowSlice、0 EvidenceCapsule；这不是失败的文档表现，而是可信的分析结果 |
| **尚未符合目标** | Stage02Result/Flow/Capsule 主要在内存，尚无本设计 Stage05 canonical production directory；parent/child共享子流程能力仍有限 |
| **设计裁决** | 不为得到漂亮 DepotHead 文档放宽 Fact/Proof/dataflow；后续补齐上游通用能力后再做 real positive acceptance |

当前 0 Capsule 意味着 Stage 06 必须零调用，而不是让模型直接读 Service/XML。
