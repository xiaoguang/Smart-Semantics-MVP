# 07 准入并合并仓库业务知识

> 总体设计权威：[GitHub Code Agent 总体设计](../DESIGN.md)。

## 1. 为什么存在

模型提案不自动成为业务知识。即使两个 Flow 都选择“单据”这个词，它们也可能指不同 Java type、SQL table 或请求对象；反过来，同一个 jsh_depot_head 技术锚点也可能被不同流程用不同措辞描述。

本阶段由程序重新计算**完整 FlowSlice 集合中每个解释或失败 disposition**的合法性，用技术锚点跨 Flow 合并对象、关系、指标和冲突，并把 Proven Fact、Admitted Interpretation 和 Gap 分层保存。无论仓库有多少 Flow，Stage07 只产生**一份且仅一份 RepositoryKnowledge**；最终决定属于程序，不属于模型。DepotHead只是合并输入中的一个slice。

## 2. 具体输入与 DepotHead 例子

输入：

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本文件用未来闭合 Flow 的提案展示准入/merge artifact 接力；当前 fixed slice 只有确定性范围与 Gap，技术 unknown 只用 nullable/UNRESOLVED/Gap/fatal 表达。

- Stage 04 Facts/Proof/Gaps；
- Stage 05 Flow/Capsule/coverage；
- Stage 06 canonical R0/R1/R2 tasks/rounds/receipts、`RepositoryInterpretationRegistry`、registry proposals、interpretation proposals与每Flow dispositions；
- 冻结 TechnicalDisplayRegistry，以及 Stage06程序冻结的 repository-specific BusinessTerm/Claim/Question provisional keys；
- knowledge/admission profiles 和预算。

未来 DepotHead 模型可能提出：

~~~json
{
  "flowSliceId": "flow:<hex64>",
  "registryProposalId": "registry-proposal:depothead-batch-audit",
  "provisionalKey": "TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "interpretationProposalId": "interpretation-proposal:depothead-batch-audit",
  "selectedKey": "TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "basisAtomIds": ["atom:<hex64>"]
}
~~~

Stage 07 只有在 term eligibility、basis、Flow ownership 和 priority 全部闭合时才生成 **MODEL_INTERPRETATION / ADMITTED**。否则 DROP、NARROW 或 NEEDS_EVIDENCE。

当前固定 jshERP slice 没有 Flow/Capsule/round。Stage 07 当前应合并确定性 Gaps 和技术范围说明，而不是制造 DepotHead business meaning。

## 3. 程序怎样工作

1. 重验 Stage 04–06 artifact roots、完整flow interpretation coverage、R0/R1/R2 round sets、runtime receipts和唯一 `RepositoryInterpretationRegistry`。
2. 按flowSliceId/interpretationProposalId处理每个READY candidate；对GAP/FAILED Flow建立technical fallback和Gap lineage，任何eligible Flow不得遗漏。
3. 逐 interpretation proposal 验证不可断裂的 `registryProposalId → provisionalKey → interpretationProposalId → selectedKey`：selectedKey必须等于或按lattice收窄自同Flow provisionalKey，basis完全属于同一Flow/Capsule。
4. 重新执行 decision lattice；程序结果只能与模型建议相同或更保守。
5. 没有 admitted business term 时，从 total TechnicalDisplayRegistry 解析唯一技术显示。
6. 构建 TechnicalAnchor：FLOW、REQUEST、RESULT、RECORD、OUTCOME、ACTIVITY、FIELD、FORMULA、RELATION。
7. 只按 FQN、SQL table、Flow/Outcome ID 或 Proof-backed equivalence edge 合并；simple name、中文名和 term key 相同都不是 identity。
8. 在全Flow union上建立对象同一性、跨Flow关系、指标/formula引用和conflict；每条`RegistryMeaningLineage`保留member flow/Fact/registry proposal/provisional key/interpretation proposal/meaning IDs，并从冻结registry逐字段复制`proposalKind/normalizedLabel/normalizedPurpose`，不得改写显示值。
9. 保持 facts、admitted meanings、technical fallbacks、gaps、conflicts 各自 typed。
10. 给每个 fact atom、registry proposal、provisional key、interpretation proposal、meaning、fallback和Gap唯一knowledge owner；每个conflict必须resolved或显式fatal。
11. 验证Flow/candidate/decision/knowledge shard union与完整上游denominator一致，重算knowledge accounting，原子安装 Stage07。

## 4. 生成的可观察产物

| 文件 | 唯一职责 |
| --- | --- |
| admitted-flow-meanings.jsonl | 每 Flow 的最终 KEEP/NARROW/DROP/NEEDS_EVIDENCE 与 technical fallback |
| repository-business-knowledge.json | 对象、活动、流程、Outcome、字段、关系、公式、问题和三类知识 lineage |
| knowledge-conflicts.jsonl | anchor/term/claim/owner 冲突及确定性处置 |
| knowledge-accounting.json | Fact atom、meaning、Gap、owner、conflict 守恒 |
| merged-gaps.json | 去重但不丢 provenance 的仓库级 Gap 视图 |
| stage-receipt.json | upstream roots、registry/profile hashes、artifact set |

DepotHead 目标知识项形状：

~~~json
{
  "knowledgeKind": "ACTIVITY",
  "anchor": "method:DepotHeadService.batchSetStatus",
  "technicalDisplay": "POST /depotHead/batchSetStatus → DepotHeadService.batchSetStatus",
  "businessTermKey": null,
  "factIds": ["fact:<hex64>"],
  "meaningIds": [],
  "gapIds": ["gap:<hex64>"]
}
~~~

businessTermKey=null 是合法技术回退，不表示模型失败。

目标 Stage07 出口始终是六个命名文件的非空、**单一仓库知识快照**。即使0Flow/0proposal，`repository-business-knowledge.json`也要包含source scope、technical fallbacks、deterministic Gaps和ownership/accounting，receipt明确proposal/meaning/model call均为0。若有`N` Flow，所有READY/GAP/FAILED dispositions及其Facts必须汇入同一RepositoryKnowledge；未来DepotHead正向出口可以增加admitted meaning，但不能替换或删除其Proven Facts、technical anchors和Gaps，也不能单独生成“DepotHead knowledge”旁路。

### 4.1 人类 walkthrough：模块用什么文件接力

~~~jsonl
{"module":"ProposalAdmissionEngine","artifact":"modules/01-admission/admission-decision-set.json","takesFrom":["Stage04Reference","Stage05Reference","Stage06Reference","frozen registries"],"says":{"registryProposalId":"registry-proposal:depothead-batch-audit","provisionalKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","interpretationProposalId":"interpretation-proposal:depothead-batch-audit","selectedKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","programDecision":"KEEP","meaningId":"meaning:depothead-batch-audit"}}
{"module":"AnchoredKnowledgeMerger","artifact":"modules/02-knowledge-merge/knowledge-merge.json","takesFrom":["admission-decision-set.json","Facts/Flows/Gaps","RepositoryInterpretationRegistry"],"says":{"knowledgeItemId":"knowledge:activity:depothead-status-change","anchor":"method:DepotHeadService.batchSetStatus","factId":"fact:depothead-status-persistence","lineage":"registryProposalId → provisionalKey → interpretationProposalId → selectedKey → meaningId","normalizedLabel":"批量审核或反审核","remainingGap":"gap:runtime-status-policy"}}
{"module":"KnowledgeArtifactPublisher","artifact":"modules/03-publish/stage07-publication.json","takesFrom":["admission-decision-set.json","knowledge-merge.json"],"says":{"knowledgeItemCount":1,"meaningCount":1,"gapCount":1,"publicFiles":["admitted-flow-meanings.jsonl","repository-business-knowledge.json","knowledge-conflicts.jsonl","knowledge-accounting.json","merged-gaps.json","stage-receipt.json"],"nextStage":"08-build-nine-section-document-and-archive"}}
~~~

`registryProposalId → provisionalKey → interpretationProposalId → selectedKey → meaningId` 是五个不同typed identity之间的显式准入链，不是改名；meaning保留完整R0/R1/R2/basis lineage。M2把Fact、meaning和Gap放在同一个proven anchor下但不互相替代，M3只发布。当前0 proposal时meaning链不存在，technical fallback/Gap链仍完整。

## 5. 下游怎样消费而不返工

Stage 08 只读 repository-business-knowledge.json、admitted-flow-meanings.jsonl、merged-gaps.json、knowledge accounting/conflicts 和冻结 reader registries。它不读取 raw model response，也不重新：

- 选择 term；
- 合并对象；
- 决定 Fact/Gap；
- 打开源码；
- 执行 Provider。

若 Stage 08 发现 owner 缺失，正确结果是 Stage 07/08 invariant failure，不是 renderer 临时选一章。

### 下游前置条件与后置保证

| Stage 08 开始前必须成立 | Stage 07 成功后保证 |
| --- | --- |
| Stage04–06 roots、RepositoryInterpretationRegistry、admission profiles与Stage07 controls完全一致 | 每个registry/interpretation proposal有确定性处置；每个Fact atom、registry proposal、provisional key、interpretation proposal、meaning、fallback和Gap有唯一owner/disposition |
| 六个 output files、receipt root、anchor/knowledge/conflict/accounting refs 闭合 | merge 只基于 proven anchor；同名/中文显示不会改变 identity，unresolved conflict 不会进入成功结果 |
| TechnicalDisplayRegistry 对所有缺 term 的 admitted technical anchor 恰一匹配 | Stage 08 可只读 typed knowledge/owners/Gaps 规划章节，无需重做 admission、merge 或模型调用 |
| Stage05全部Flow与Stage06全部candidate/GAP/FAILED dispositions均有Stage07 decision/owner；knowledge shard union闭合 | Stage08获得恰一个repositoryKnowledgeId，完整保存跨Flow identity/relations/metrics/conflicts；没有per-Flow孤儿knowledge |

Stage 08 不能容错式补 owner、挑 term 或合并对象；任何缺失都应使 Stage 07 replay/Stage 08 input validation 失败。

## 6. 成功、Gap、fatal 与恢复

- **成功**：完整仓库所有 Flow dispositions、proposals、Facts、atoms、meanings、fallbacks、Gaps、relations/metrics和conflicts有完整decision/owner；恰一个RepositoryKnowledge且shard/accounting闭合，阶段目录原子安装。
- **带 Gap 成功**：无业务 term、proposal DROP、needs evidence 或仓库待确认问题；technical fallback 让事实文档仍可继续。
- **fatal**：unknown key/reference、registry drift、fallback 0/2 matches、meaning basis expanded、双 owner/无 owner、unresolved equal-priority conflict、anchor merge 依据不足或 accounting 不闭合。
- **恢复**：Stage07不重新调用模型；它只重放canonical rounds和已完成merge shards。缺/重叠 shard、遗漏Flow或知识孤儿时不发布；upstream/control hashes完全相同才复用，改变shard size/order输出bytes相同。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 检查 eligibility/basis | 是 | 否 |
| 最终 KEEP/NARROW/DROP | 是 | 否 |
| 合并技术锚点/解决冲突 | 是 | 否 |
| 提出有限解释 | 否，本阶段只消费 Stage 06 | Stage 06 已完成 |
| 自我批准或改 Fact | 禁止 | 禁止 |

本阶段无 Provider 参数，运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块执行顺序固定为 `ProposalAdmissionEngine` → `AnchoredKnowledgeMerger` → `KnowledgeArtifactPublisher`。全部模块只重放 Stage04–06 artifacts；任何模块都没有 Provider、source Path 或开放文本入口。

#### M1 ProposalAdmissionEngine

- **解决的问题**：独立于模型自评，重新计算每个 proposal 的 key eligibility、basis ownership 和最终保守 decision。
- **精确上游输入及前置**：valid Stage04 Facts/Proof/Gaps、Stage05完整Flow/Capsule、Stage06完整R0/R1/R2 disposition/candidate/task/round/receipt/proposal artifacts与唯一RepositoryInterpretationRegistry、TechnicalDisplayRegistry/admission profile/budget；flow/registry/round/runtime refs已验证。
- **确定性顺序 / LLM**：固定全flowSliceId denominator → READY按interpretationProposalId验证registryProposalId/provisionalKey/selectedKey/eligibility/basis/lattice → GAP/FAILED转technical fallback/Gap decision → 每Flow与两类proposal decision accounting；0 LLM/Provider。
- **目标输出与 DepotHead 示例**：`AdmissionDecisionSet{repositoryInterpretationRegistryId,interpretationProposalDecisions,admittedMeanings,technicalFallbacks,decisionAccounting}`；未来DepotHead provisional key可KEEP并生成完整五段lineage，当前0proposal仍产生technical scope/Gap fallback inputs。
- **必须保持的不变量**：程序decision不比模型宽；basis/key不扩张；每registry proposal和interpretation proposal各有唯一处置；每admitted meaning精确保存五段lineage；每Stage05 eligible Flow恰一flow decision；fallback为0/1/2匹配中的唯一1才可继续。
- **Gap / fatal / 恢复**：NEEDS_EVIDENCE/TERM是Gap型decision；unknown key/ref、basis expansion、fallback0/2、round replay mismatch fatal；恢复纯重放相同 artifacts。
- **给下游的后置保证**：M2得到stable meaning/fallback/proposal lineage，不需访问raw response或再次决定admission。
- **明确非目标**：不改Fact、不扩registry、不合并anchor、不调用模型。
- **公共测试 seam 与验收**：`admit(facts, flows, interpretations, registries, profile)`覆盖至少双Flow、完整五段lineage、断一段/跨Flow key、全lattice、一Flowcandidate/一Flow unavailable fallback、single-flow omission、模型KEEP对抗programDROP、basis mutation、fallback0/1/2和0proposal；expected decisions独立手写。
- **Luna/xhigh 测试指南**：创建 `Stage07ProposalAdmissionEngineTest`，冻结Stage04–06 artifacts、registries和独立decision goldens于 `src/test/resources/target/stage07/proposal-admission/`。每个RED只测lineage closure或KEEP/NARROW/DROP/NEEDS_EVIDENCE/NEEDS_TERM_REGISTRY之一，再测跨Flowkey、basis mutation、fallback0/1/2、0proposal/replay；首RED因seam/schema缺失。只fakeartifact reader，decision lattice/canonical不能mock。命令：`mvn -Dtest=Stage07ProposalAdmissionEngineTest test`；禁Provider/network。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `target/stage07/proposaladmission/`，实现 public `ProposalAdmissionEngine/AdmissionDecisionSet` 与 `stage07-admission-decision-set-v2`；只读Stage04–06+registries，registryProposal→provisionalKey→interpretationProposal→selectedKey→eligibility/basis→lattice→meaning/fallback。逐decision GREEN；不得信模型KEEP、重写registry或读raw response。缺basis/跨stage字段MUST STOP交Sol/ultra，完成更新审计。

#### M2 AnchoredKnowledgeMerger

- **解决的问题**：按 proven technical anchors 合并跨Flow知识、处理冲突，并给每个semantic item唯一owner。
- **精确上游输入及前置**：M1全Flow AdmissionDecisionSet、Stage04完整Facts/Gaps、Stage05完整Flows、anchor/knowledge profiles/budget；meaning/fallback/fact/flow refs闭合。
- **确定性顺序 / LLM**：按flowSliceId shard建FLOW/REQUEST/RESULT/RECORD/OUTCOME/ACTIVITY/FIELD/FORMULA/RELATION anchors → 以M1 proposal/key IDs exact-join冻结registry并逐字段复制`proposalKind/normalizedLabel/normalizedPurpose` → 全shard按priority/equivalence union → 构建typed knowledge items/跨Flowrelations/metrics → resolve conflicts → assign owners/account → canonical单一knowledge；0 LLM。
- **目标输出与 DepotHead 示例**：`KnowledgeMerge{anchors,knowledgeItems,ownership,conflicts,mergedGaps}`；例子用method/entry/table anchors连接DepotHead activity、`jsh_depot_head` record、status field和where Gap。
- **必须保持的不变量**：merge只用proven key；每个admitted meaning保留完整R0→registry→R1/R2→meaning lineage，且三项规范业务值与对应冻结registry item逐字节相同；所有Flow shard union等于Stage05 Flow IDs；每semantic item恰一owner；relation/metric每endpoint有proven refs；alias Gap保留members；只产一个repositoryKnowledgeId。
- **Gap / fatal / 恢复**：未解决业务问题保留Gap；anchor不足/ambiguous、双/无owner、unresolved equal conflict、accounting fatal；恢复按相同排序重建全部merge。
- **给下游的后置保证**：M3/Stage08获得typed、owned、无歧义的repository knowledge；显示名不承担identity。
- **明确非目标**：不重新admit proposal、不从source/模型补anchor、不选择九章。
- **公共测试 seam 与验收**：`merge(admissions, facts, flows, profiles)`使用至少双Flow，覆盖完整registry lineage/任一hop删除、同SQL表跨Flowidentity merge、跨Flowrelation/metric、同名不同FQN、equivalence edge、conflict priority、owner mutation、缺/重叠shard、0Flowgaps；顺序不改变bytes且只产一个knowledge。
- **Luna/xhigh 测试指南**：创建 `Stage07AnchoredKnowledgeMergerTest`，fixtures/goldens放 `src/test/resources/target/stage07/knowledge-merger/`。逐RED：registry lineage/hop deletion→anchor kinds→同表merge→同名FQN隔离→proof equivalence→priority conflict→双/无owner→Gap alias→0Flow→order determinism；首RED因merger/schema缺失。artifact reader可fake，union/accounting/canonical不可mock。命令：`mvn -Dtest=Stage07AnchoredKnowledgeMergerTest test`；无网络/模型。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后只改 `target/stage07/knowledgemerger/`，实现 public `AnchoredKnowledgeMerger/KnowledgeMerge` 与 `stage07-knowledge-merge-v2`；消费M1+Facts/Flows，anchors→priority union→items+registryLineage→conflicts→owners/accounting。逐RED GREEN；禁止显示名merge、丢proposal lineage、输入顺序tie-break或补source。需改跨stage语义MUST STOP并升级用户，更新审计。

#### M3 KnowledgeArtifactPublisher

- **解决的问题**：验证admission/merge/accounting的全局闭包并安装Stage07 public knowledge artifacts。
- **精确上游输入及前置**：M1完整 AdmissionDecisionSet/flow decisions、M2唯一KnowledgeMerge/shard receipts、Stage04–06 roots、registry/profile controls；flow/proposal/semantic/owner/conflict IDs局部有效。
- **确定性顺序 / LLM**：验证全Flow/knowledge shard union → join lineage → 重算Flow/Fact/proposal/meaning/Gap/relation/metric/conflict equations → canonical六文件 → staging force/SHA → atomic install；0 LLM/Provider。
- **目标输出与 DepotHead 示例**：六个 exact files；未来例有admitted activity meaning，当前例有0 meaning但nonempty technical anchors/scope/Gaps/owners/receipt。
- **必须保持的不变量**：Stage05全Flow和Stage06全dispositions都有处置；public artifacts只含一个repositoryKnowledgeId且只引用M1/M2 IDs；所有semantic items/aliases可逆追踪；publisher不改变decision/merge。
- **Gap / fatal / 恢复**：合法DROP/NEEDS/Gaps为SUCCEEDED_WITH_GAPS；orphan/duplicate/accounting/ref/canonical/install错误 fatal；恢复只认完整receipt。
- **给下游的后置保证**：Stage08可只读repository knowledge、meanings、merged Gaps、owners/conflicts规划九章。
- **明确非目标**：不补owner/term、不开source/Provider、不渲染文档。
- **公共测试 seam 与验收**：`publish(admissions, merge, controls)`覆盖0Flow、至少双Flow→一个knowledge、registry lineage loss、single-flow omission、第二个knowledge rejection、ID-set count spoof、orphan/duplicate owner、alias loss、缺/重叠shard、乱序/crash/collision；只有六文件和仓库ledger闭合返回Stage07Reference。
- **Luna/xhigh 测试指南**：创建 `Stage07KnowledgeArtifactPublisherTest`，M1/M2 artifacts与six-file goldens放 `src/test/resources/target/stage07/artifact-publisher/`。RED顺序：0Flow nonempty knowledge、admitted meaning、count spoof、orphan/duplicate/alias loss、乱序、crash/collision/resume；首RED因publisher缺失。只mockartifact store，lineage/accounting/canonical不mock。命令：`mvn -Dtest=Stage07KnowledgeArtifactPublisherTest test`；禁network/Provider/customer build。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `target/stage07/artifactpublisher/`，实现 public `KnowledgeArtifactPublisher/Stage07Reference` 与 `stage07-knowledge-publication-v2`；只读M1/M2 files，完整registry/interpretation lineage→equations→six files→atomic。逐RED GREEN，Stage08只凭reference规划；不得补owner/meaning/lineage。跨stage contract改动MUST STOP交Sol/ultra/用户，完成更新审计。

### 8.0.1 模块 artifact wire schemas

使用 DESIGN 13.3 envelope；`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-admission/admission-decision-set.json` | `stage07-admission-decision-set-v2` / `STAGE07_ADMISSION_DECISION_SET` | Stage04+Stage05+Stage06 registry/R0/R1/R2 IDs/SHAs | `admissionDecisionSetId!`、`repositoryInterpretationRegistryId!`、`flowDecisions[]!{flowDecisionId!,flowSliceId!,stage06Disposition!,candidateId?,decision!,meaningIds[]!,fallbackIds[]!,gapIds[]!,failureRef?,reasonCode?}`、`interpretationProposalDecisions[]!{interpretationProposalId!,registryProposalId!,provisionalKey!,selectedKey!,decision!,basisAtomIds[]!,basisGapIds[]!,meaningId?,fallbackId?,reasonCode?}`、`admittedMeanings[]!{meaningId!,flowSliceId!,anchorId!,registryProposalIds[]!,provisionalKeys[]!,interpretationProposalIds[]!,selectedKeys[]!,basisAtomIds[]!,basisGapIds[]!,decision!,technicalFallbackId?}`、`technicalFallbacks[]!`、`decisionAccounting!{flowSliceIds[]!,flowDecisionIds[]!,registryProposalIds[]!,provisionalKeys[]!,interpretationProposalIds[]!,keepIds[]!,narrowIds[]!,dropIds[]!,needsEvidenceIds[]!,needsTermRegistryIds[]!,fallbackIds[]!,gapIds[]!,closed!}`；按flow/proposal/meaning/fallback IDs |
| `modules/02-knowledge-merge/knowledge-merge.json` | `stage07-knowledge-merge-v2` / `STAGE07_KNOWLEDGE_MERGE` | M1+Stage04+Stage05+Stage06 registry ID/SHA | `knowledgeMergeId!`、`repositoryKnowledgeId!`、`repositoryInterpretationRegistryId!`、`sourceFlowSliceIds[]!`、`registryLineage[]!{registryLineageId!,registryProposalId!,provisionalKey!,interpretationProposalId!,selectedKey!,meaningId!,flowSliceId!,proposalKind!,normalizedLabel!,normalizedPurpose!,basisAtomIds[]!,basisGapIds[]!}`、`flows[]!{flowSliceId!,entryId!,outcomePathIds[]!,factIds[]!,meaningIds[]!,registryLineageIds[]!,gapIds[]!}`、`outcomes[]!`、`anchors[]!`、`knowledgeItems[]!{knowledgeItemId!,knowledgeKind!,anchorId!,technicalDisplay!,factIds[]!,meaningIds[]!,registryLineageIds[]!,gapIds[]!,owningFlowIds[]!,outcomePathIds[]!}`、`relations[]!`、`metrics[]!`、`ownership[]!`、`conflicts[]!`、`mergedGaps[]!`、`knowledgeShardReceipts[]!`、`accounting!{flowSliceIds[]!,outcomePathIds[]!,factAtomIds[]!,registryProposalIds[]!,provisionalKeys[]!,interpretationProposalIds[]!,registryLineageIds[]!,meaningIds[]!,fallbackIds[]!,knowledgeItemIds[]!,relationIds[]!,metricIds[]!,gapIds[]!,ownerSemanticItemIds[]!}`；arrays按stable ID；三项规范值必须exact-copy对应registry item |
| `modules/03-publish/stage07-publication.json` | `stage07-knowledge-publication-v2` / `STAGE07_KNOWLEDGE_PUBLICATION` | M1+M2 IDs/SHAs | `stageStatus!`、`admissionDecisionSetId!`、`knowledgeMergeId!`、`repositoryKnowledgeId!`、`repositoryInterpretationRegistryId!`、counts!、`repositoryKnowledgeCoverage!{flowSliceIds[]!,flowDecisionIds[]!,outcomePathIds[]!,factAtomIds[]!,registryProposalIds[]!,provisionalKeys[]!,interpretationProposalIds[]!,registryLineageIds[]!,meaningIds[]!,gapIds[]!,knowledgeItemIds[]!,relationIds[]!,metricIds[]!,ownerSemanticItemIds[]!,shardReceiptIds[]!,closed!}`、`stageArtifactRoot!`、`publishedArtifacts[6]!`、`nextStage!` |

decision nullable组合固定：KEEP/NARROW有meaningId，DROP有reasonCode，NEEDS_EVIDENCE有Gap ref，NEEDS_TERM_REGISTRY有fallbackId；不存在的字段必须null，不能省略/填story text。anchors只用8.3 proven keys。conflict即使resolved也保存；fatal conflict不安装artifact。所有field/lattice/priority/owner/sort/identity变化先设计并升version。

`repositoryKnowledgeCoverage.closed` 只有上游完整Flow/interpretation coverage、所有Flow decisions和全semantic ownership均闭合时为true；bounded DepotHead知识投影固定false，但仍可作为诚实诊断输入供Stage08生成`INCOMPLETE_SCOPE`候选。

~~~jsonl
{"schemaVersion":"stage07-admission-decision-set-v2","artifactType":"STAGE07_ADMISSION_DECISION_SET","artifactId":"admission-decisions:1111111111111111111111111111111111111111111111111111111111111111","producer":{"stage":7,"module":"ProposalAdmissionEngine","moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"repository-interpretation-registry:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"stage04-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"artifactId":"stage05-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"stage06-publication:6666666666666666666666666666666666666666666666666666666666666666","sha256":"6666666666666666666666666666666666666666666666666666666666666666"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"admissionDecisionSetId":"admission:depothead-v2","repositoryInterpretationRegistryId":"interpretation-registry:depothead-v1","flowDecisions":[{"flowDecisionId":"flow-decision:depothead-admitted-with-gaps","flowSliceId":"flow:post-depothead-batch-set-status","stage06Disposition":"READY_FOR_ADMISSION","candidateId":"flow-interpretation:depothead-v1","decision":"ADMITTED_WITH_GAPS","meaningIds":["meaning:depothead-batch-audit"],"fallbackIds":["fallback:depothead-route-display"],"gapIds":["gap:runtime-status-policy"],"failureRef":null,"reasonCode":null}],"interpretationProposalDecisions":[{"interpretationProposalId":"interpretation-proposal:depothead-batch-audit","registryProposalId":"registry-proposal:depothead-batch-audit","provisionalKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","selectedKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","decision":"KEEP","basisAtomIds":["atom:input-field","atom:value-source"],"basisGapIds":["gap:runtime-status-policy"],"meaningId":"meaning:depothead-batch-audit","fallbackId":null,"reasonCode":null}],"admittedMeanings":[{"meaningId":"meaning:depothead-batch-audit","flowSliceId":"flow:post-depothead-batch-set-status","anchorId":"anchor:method-depothead-batch-set-status","registryProposalIds":["registry-proposal:depothead-batch-audit"],"provisionalKeys":["TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"interpretationProposalIds":["interpretation-proposal:depothead-batch-audit"],"selectedKeys":["TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"basisAtomIds":["atom:input-field","atom:value-source"],"basisGapIds":["gap:runtime-status-policy"],"decision":"KEEP","technicalFallbackId":null}],"technicalFallbacks":[{"fallbackId":"fallback:depothead-route-display","anchorId":"anchor:method-depothead-batch-set-status","displayKey":"DISPLAY_HTTP_ROUTE_TO_SERVICE","displayValue":"POST /depotHead/batchSetStatus → DepotHeadService.batchSetStatus"}],"decisionAccounting":{"registryProposalIds":["registry-proposal:depothead-batch-audit"],"provisionalKeys":["TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"interpretationProposalIds":["interpretation-proposal:depothead-batch-audit"],"keepIds":["interpretation-proposal:depothead-batch-audit"],"narrowIds":[],"dropIds":[],"needsEvidenceIds":[],"needsTermRegistryIds":[],"flowSliceIds":["flow:post-depothead-batch-set-status"],"flowDecisionIds":["flow-decision:depothead-admitted-with-gaps"],"fallbackIds":["fallback:depothead-route-display"],"gapIds":["gap:runtime-status-policy"],"closed":true}}}
{"schemaVersion":"stage07-knowledge-merge-v2","artifactType":"STAGE07_KNOWLEDGE_MERGE","artifactId":"knowledge-merge:2222222222222222222222222222222222222222222222222222222222222222","producer":{"stage":7,"module":"AnchoredKnowledgeMerger","moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"admission-decisions:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"repository-interpretation-registry:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"stage04-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"artifactId":"stage05-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"5555555555555555555555555555555555555555555555555555555555555555"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"knowledgeMergeId":"knowledge-merge:depothead-v2","repositoryKnowledgeId":"repository-knowledge:depothead-v2","repositoryInterpretationRegistryId":"interpretation-registry:depothead-v1","sourceFlowSliceIds":["flow:post-depothead-batch-set-status"],"registryLineage":[{"registryLineageId":"registry-lineage:depothead-batch-audit","registryProposalId":"registry-proposal:depothead-batch-audit","provisionalKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","interpretationProposalId":"interpretation-proposal:depothead-batch-audit","selectedKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","meaningId":"meaning:depothead-batch-audit","flowSliceId":"flow:post-depothead-batch-set-status","proposalKind":"BUSINESS_TERM","normalizedLabel":"批量审核或反审核","normalizedPurpose":"描述同一入口依据输入状态批量改变单据状态","basisAtomIds":["atom:input-field","atom:value-source"],"basisGapIds":["gap:runtime-status-policy"]}],"anchors":[{"anchorId":"anchor:method-depothead-batch-set-status","kind":"ACTIVITY","provenEndpointIds":["method:service-batch-set-status"],"owningFlowIds":["flow:post-depothead-batch-set-status"],"proofBasisAtomIds":["atom:value-source"]},{"anchorId":"anchor:table-jsh-depot-head","kind":"RECORD","provenEndpointIds":["table:jsh-depot-head"],"owningFlowIds":["flow:post-depothead-batch-set-status"],"proofBasisAtomIds":["atom:table"]}],"knowledgeItems":[{"knowledgeItemId":"knowledge:activity:depothead-status-change","knowledgeKind":"ACTIVITY","anchorId":"anchor:method-depothead-batch-set-status","technicalDisplay":"POST /depotHead/batchSetStatus → DepotHeadService.batchSetStatus","factIds":["fact:depothead-status-persistence"],"meaningIds":["meaning:depothead-batch-audit"],"registryLineageIds":["registry-lineage:depothead-batch-audit"],"gapIds":["gap:runtime-status-policy"],"owningFlowIds":["flow:post-depothead-batch-set-status"],"outcomePathIds":["outcome:no-eligible-document","outcome:status-updated"]},{"knowledgeItemId":"knowledge:record:jsh-depot-head","knowledgeKind":"RECORD","anchorId":"anchor:table-jsh-depot-head","technicalDisplay":"jsh_depot_head","factIds":["fact:depothead-status-persistence"],"meaningIds":[],"registryLineageIds":[],"gapIds":[],"owningFlowIds":["flow:post-depothead-batch-set-status"],"outcomePathIds":["outcome:no-eligible-document","outcome:status-updated"]}],"relations":[{"relationId":"relation:activity-updates-record","kind":"UPDATES","fromKnowledgeItemId":"knowledge:activity:depothead-status-change","toKnowledgeItemId":"knowledge:record:jsh-depot-head","basisAtomIds":["atom:value-source"],"owningFlowIds":["flow:post-depothead-batch-set-status"]}],"metrics":[{"metricId":"metric:depothead-status-change-count","formulaKey":null,"inputKnowledgeItemIds":["knowledge:activity:depothead-status-change","knowledge:record:jsh-depot-head"],"basisAtomIds":[],"owningFlowIds":["flow:post-depothead-batch-set-status"],"gapIds":["gap:runtime-status-policy"]}],"ownership":[{"semanticItemId":"atom:column","ownerKnowledgeItemId":"knowledge:record:jsh-depot-head","disposition":"OWNED"},{"semanticItemId":"atom:eligible-id-set","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"atom:entry-route","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"atom:input-field","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"atom:mapper-method","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"atom:service-handler","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"atom:table","ownerKnowledgeItemId":"knowledge:record:jsh-depot-head","disposition":"OWNED"},{"semanticItemId":"atom:value-source","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"atom:where-key","ownerKnowledgeItemId":"knowledge:record:jsh-depot-head","disposition":"OWNED"},{"semanticItemId":"atom:where-operator","ownerKnowledgeItemId":"knowledge:record:jsh-depot-head","disposition":"OWNED"},{"semanticItemId":"fallback:depothead-route-display","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"flow:post-depothead-batch-set-status","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"gap:runtime-status-policy","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"knowledge:activity:depothead-status-change","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"knowledge:record:jsh-depot-head","ownerKnowledgeItemId":"knowledge:record:jsh-depot-head","disposition":"OWNED"},{"semanticItemId":"meaning:depothead-batch-audit","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"registry-proposal:depothead-batch-audit","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"interpretation-proposal:depothead-batch-audit","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"metric:depothead-status-change-count","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"outcome:no-eligible-document","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"outcome:status-updated","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"},{"semanticItemId":"relation:activity-updates-record","ownerKnowledgeItemId":"knowledge:activity:depothead-status-change","disposition":"OWNED"}],"conflicts":[{"conflictId":"conflict:technical-vs-business-display","kind":"DISPLAY_PRIORITY","memberIds":["fallback:depothead-route-display","meaning:depothead-batch-audit"],"resolution":"BUSINESS_TERM_WITH_TECHNICAL_ANCHOR","winnerId":"meaning:depothead-batch-audit"}],"mergedGaps":[{"canonicalGapId":"gap:runtime-status-policy","memberGapIds":["gap:runtime-status-policy"],"missingRequirement":"runtime attestation for tenant-wide status policy","impact":"business wording remains qualified","closureRequirement":"trusted runtime or domain-owner attestation"}],"knowledgeShardReceipts":[{"shardId":"knowledge-shard:depothead","denominatorFlowSliceIds":["flow:post-depothead-batch-set-status"],"knowledgeItemIds":["knowledge:activity:depothead-status-change","knowledge:record:jsh-depot-head"],"status":"SUCCEEDED_WITH_GAPS","gapIds":["gap:runtime-status-policy"]}],"accounting":{"flowSliceIds":["flow:post-depothead-batch-set-status"],"outcomePathIds":["outcome:no-eligible-document","outcome:status-updated"],"factAtomIds":["atom:column","atom:eligible-id-set","atom:entry-route","atom:input-field","atom:mapper-method","atom:service-handler","atom:table","atom:value-source","atom:where-key","atom:where-operator"],"ownedFactAtomIds":["atom:column","atom:eligible-id-set","atom:entry-route","atom:input-field","atom:mapper-method","atom:service-handler","atom:table","atom:value-source","atom:where-key","atom:where-operator"],"registryProposalIds":["registry-proposal:depothead-batch-audit"],"provisionalKeys":["TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"interpretationProposalIds":["interpretation-proposal:depothead-batch-audit"],"meaningIds":["meaning:depothead-batch-audit"],"registryLineageIds":["registry-lineage:depothead-batch-audit"],"gapIds":["gap:runtime-status-policy"],"ownerSemanticItemIds":["atom:column","atom:eligible-id-set","atom:entry-route","atom:input-field","atom:mapper-method","atom:service-handler","atom:table","atom:value-source","atom:where-key","atom:where-operator","fallback:depothead-route-display","flow:post-depothead-batch-set-status","gap:runtime-status-policy","knowledge:activity:depothead-status-change","knowledge:record:jsh-depot-head","meaning:depothead-batch-audit","registry-proposal:depothead-batch-audit","TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","interpretation-proposal:depothead-batch-audit","metric:depothead-status-change-count","outcome:no-eligible-document","outcome:status-updated","relation:activity-updates-record"],"fallbackIds":["fallback:depothead-route-display"],"knowledgeItemIds":["knowledge:activity:depothead-status-change","knowledge:record:jsh-depot-head"],"relationIds":["relation:activity-updates-record"],"metricIds":["metric:depothead-status-change-count"]},"flows":[{"flowSliceId":"flow:post-depothead-batch-set-status","entryId":"entry:post-depothead-batch-set-status","outcomePathIds":["outcome:no-eligible-document","outcome:status-updated"],"factIds":["fact:depothead-status-persistence"],"meaningIds":["meaning:depothead-batch-audit"],"registryLineageIds":["registry-lineage:depothead-batch-audit"],"gapIds":["gap:runtime-status-policy"]}],"outcomes":[{"outcomePathId":"outcome:no-eligible-document","owningFlowIds":["flow:post-depothead-batch-set-status"],"factIds":[],"basisAtomIds":["atom:eligible-id-set"],"gapIds":[]},{"outcomePathId":"outcome:status-updated","owningFlowIds":["flow:post-depothead-batch-set-status"],"factIds":["fact:depothead-status-persistence"],"basisAtomIds":["atom:value-source","atom:where-key","atom:where-operator"],"gapIds":["gap:runtime-status-policy"]}]}}
{"schemaVersion":"stage07-knowledge-publication-v2","artifactType":"STAGE07_KNOWLEDGE_PUBLICATION","artifactId":"stage07-publication:3333333333333333333333333333333333333333333333333333333333333333","producer":{"stage":7,"module":"KnowledgeArtifactPublisher","moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"admission-decisions:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"knowledge-merge:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"stageStatus":"SUCCEEDED_WITH_GAPS","admissionDecisionSetId":"admission:depothead-v2","knowledgeMergeId":"knowledge-merge:depothead-v2","repositoryKnowledgeId":"repository-knowledge:depothead-v2","repositoryInterpretationRegistryId":"interpretation-registry:depothead-v1","knowledgeItemCount":2,"meaningCount":1,"gapCount":1,"repositoryKnowledgeCoverage":{"flowSliceIds":["flow:post-depothead-batch-set-status"],"flowDecisionIds":["flow-decision:depothead-admitted-with-gaps"],"factAtomIds":["atom:column","atom:eligible-id-set","atom:entry-route","atom:input-field","atom:mapper-method","atom:service-handler","atom:table","atom:value-source","atom:where-key","atom:where-operator"],"registryProposalIds":["registry-proposal:depothead-batch-audit"],"provisionalKeys":["TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"interpretationProposalIds":["interpretation-proposal:depothead-batch-audit"],"meaningIds":["meaning:depothead-batch-audit"],"registryLineageIds":["registry-lineage:depothead-batch-audit"],"gapIds":["gap:runtime-status-policy"],"knowledgeItemIds":["knowledge:activity:depothead-status-change","knowledge:record:jsh-depot-head"],"relationIds":["relation:activity-updates-record"],"metricIds":["metric:depothead-status-change-count"],"ownerSemanticItemIds":["atom:column","atom:eligible-id-set","atom:entry-route","atom:input-field","atom:mapper-method","atom:service-handler","atom:table","atom:value-source","atom:where-key","atom:where-operator","fallback:depothead-route-display","flow:post-depothead-batch-set-status","gap:runtime-status-policy","knowledge:activity:depothead-status-change","knowledge:record:jsh-depot-head","meaning:depothead-batch-audit","registry-proposal:depothead-batch-audit","TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","interpretation-proposal:depothead-batch-audit","metric:depothead-status-change-count","outcome:no-eligible-document","outcome:status-updated","relation:activity-updates-record"],"shardReceiptIds":["knowledge-shard:depothead"],"closed":false,"outcomePathIds":["outcome:no-eligible-document","outcome:status-updated"]},"stageArtifactRoot":"stage-root:0707070707070707070707070707070707070707070707070707070707070707","publishedArtifacts":[{"path":"admitted-flow-meanings.jsonl","sizeBytes":1000,"sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"path":"knowledge-accounting.json","sizeBytes":1200,"sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"path":"knowledge-conflicts.jsonl","sizeBytes":900,"sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"path":"merged-gaps.json","sizeBytes":800,"sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"path":"repository-business-knowledge.json","sizeBytes":2500,"sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"path":"stage-receipt.json","sizeBytes":1200,"sha256":"6666666666666666666666666666666666666666666666666666666666666666"}],"nextStage":"08-build-nine-section-document-and-archive"}}
~~~

### 8.1 Interface 与 records

~~~java
interface KnowledgeAdmissionEngine {
    Stage07Reference admitAndMerge(Stage04Reference facts,
                                   Stage05Reference flows,
                                   Stage06Reference interpretations,
                                   RegistryBundle registries);
}
~~~

~~~text
AdmittedFlowMeaning
  meaningId
  flowSliceId
  anchorId
  registryProposalIds[]
  provisionalKeys[]
  interpretationProposalIds[]
  selectedKeys[]
  basisAtomIds[]
  basisGapIds[]
  decision
  technicalFallbackId?

RegistryMeaningLineage
  registryLineageId
  registryProposalId
  provisionalKey
  interpretationProposalId
  selectedKey
  meaningId
  flowSliceId
  proposalKind
  normalizedLabel
  normalizedPurpose
  basisAtomIds[]
  basisGapIds[]

TechnicalAnchor
  anchorId
  kind
  provenEndpointIds[]
  owningFlowIds[]
  proofBasisAtomIds[]

RepositoryBusinessKnowledge
  repositoryKnowledgeId
  repositoryInterpretationRegistryId
  sourceFlowSliceIds[]
  registryLineage[]
  objects[]
  activities[]
  flows[]
  outcomes[]
  fields[]
  relations[]
  formulas[]
  questions[]
  facts[]
  admittedMeanings[]
  technicalFallbacks[]
  gaps[]
  ownership[]
  conflicts[]

RepositoryKnowledgeCoverage
  flowSliceIds[]
  flowDecisionIds[]
  factAtomIds[]
  registryProposalIds[]
  provisionalKeys[]
  interpretationProposalIds[]
  meaningIds[]
  gapIds[]
  knowledgeItemIds[]
  relationIds[]
  metricIds[]
  ownerSemanticItemIds[]
  shardReceiptIds[]
  closed

KnowledgeOwnership
  semanticItemId
  ownerKnowledgeItemId
  disposition
~~~

### 8.2 Decision lattice

程序 decision：

- KEEP：key、eligibility、basis、owner 全闭合；
- NARROW：可保留更窄 key/basis；
- DROP：合法 response 但提案不成立；
- NEEDS_EVIDENCE：关闭需要新 Fact/Evidence；
- NEEDS_TERM_REGISTRY：事实成立但本run冻结的RepositoryInterpretationRegistry无可准入key；使用technical fallback，不在Stage07在线扩registry。

模型的 KEEP 不能覆盖程序 NARROW/DROP。NEEDS_TERM_REGISTRY 使用 technical fallback 继续，不自动扩 registry。

### 8.3 Anchor 与 merge

Anchor identity 优先级：

1. proven SQL table/column 或 FQN type；
2. Flow/Outcome ID；
3. exact method/field/parameter graph endpoint；
4. Proof-backed equivalence edge。

显示值、simple name、注释和模型 term 不是 merge key。相同 jsh_depot_head table 可以在不同 Flow 合并为同 RECORD；不同 request FQN 即使都叫“单据”也不合并。

### 8.4 Accounting、identity 与预算

~~~text
stage04FactAtoms = knowledgeOwnedFacts + reasonedFactExclusions
stage06RegistryProposals = frozenProvisionalKeys + rejectedRegistryProposals
stage06InterpretationProposals = keep + narrow + drop + needsEvidence + needsTermRegistry
admittedMeanings = exactTerminalMappings(registryProposalId, provisionalKey, interpretationProposalId, selectedKey, meaningId)
allMeanings = knowledgeOwnedMeanings + reasonedMeaningExclusions
allGaps = knowledgeOwnedGaps + mergedAliasGaps
allConflicts = resolvedConflicts + fatalConflicts
stage05FlowSlices = stage07FlowDecisions
stage06EligibleFlows = stage07ReadyOrFallbackFlowDecisions
knowledgeShardFlowIds = exactDisjointUnion(stage05FlowSlices)
repositoryKnowledgeArtifacts = exactlyOne
~~~

repositoryKnowledgeId 绑定upstream roots、RepositoryInterpretationRegistry root、admission/knowledge profiles，以及排序后的registry lineage/meanings/anchors/knowledge/ownership/conflicts/accounting。时间和执行顺序不进入ID。

预算覆盖 anchors、proposals、knowledge items、relations、conflicts、owners 和 merge worklist；超限不得随意丢对象。

### 8.5 安全与 failure codes

不读取源码、Path、raw prompt/response reasoning，不调用 Provider。只解析 exact canonical artifacts；open text 不进入 knowledge value。

稳定 code：

STAGE07_INPUT_INVALID、MODEL_ROUND_REPLAY_MISMATCH、REGISTRY_INVALID、REGISTRY_MEANING_LINEAGE_BROKEN、INTERPRETATION_REFERENCE_INVALID、INTERPRETATION_BASIS_EXPANDED、INTERPRETATION_ADMISSION_INVALID、TECHNICAL_FALLBACK_NOT_TOTAL、ANCHOR_INPUT_NOT_PROVEN、ANCHOR_MERGE_AMBIGUOUS、KNOWLEDGE_OWNER_INVALID、KNOWLEDGE_CONFLICT_UNRESOLVED、KNOWLEDGE_ACCOUNTING_INVARIANT_BROKEN、STAGE07_RESOURCE_LIMIT_EXCEEDED。

### 8.6 测试 seam 与验收

- 同一 canonical round replay 产生相同 decisions/knowledge bytes，无 Provider。
- 模型 KEEP 对抗程序 DROP/NARROW 时程序胜出。
- 删除或断开任一`registryProposalId/provisionalKey/interpretationProposalId/selectedKey`触发lineage failure；合法未准入term才走unique fallback/NEEDS_TERM_REGISTRY，且不改变Facts。
- fallback 0 或 2 matches fatal。
- 同 SQL table 跨 Flow 合并；不同 request FQN 不因同名合并。
- equal-priority different term/value conflict 必须明确处置，不能按输入顺序选。
- atom/meaning/Gap 双 owner、无 owner、静默丢失 fatal。
- 0 Flow 时仍把 deterministic Gaps/范围说明合并进 knowledge。
- multi-flow fixture 至少两Flow：同一table/proven identity可合并、不同FQN保持分离，并形成非空跨Flow relation或metric；每Flow candidate/GAP/FAILED都有decision与owner，最终仍只有一个RepositoryKnowledge。

验收必须覆盖KEEP/NARROW/DROP/NEEDS_EVIDENCE/NEEDS_TERM_REGISTRY全lattice、fallback 0/1/2 matches、至少双Flow的同表合并/同名不同FQN隔离/非空relation或metric、一Flow unavailable fallback、single-flow omission、缺/重叠shard、equal-priority conflict和0Flow knowledge。只有replay bytes稳定、完整Flow→decision→唯一RepositoryKnowledge accounting闭合、所有semantic items有唯一owner且反例不依赖输入/shard顺序，Stage07才算可交付。

### 8.7 已冻结裁决：实现者不得自由推断

- R0 registry proposal和R1/R2 interpretation proposal永不自批；Stage07重新计算最终decision且只能与模型建议相同或更保守。每个admitted meaning必须保存`registryProposalId→provisionalKey→interpretationProposalId→selectedKey→meaningId`完整链。
- 业务 term 缺失时使用 total TechnicalDisplayRegistry 或 NEEDS_TERM_REGISTRY；不能开放写词、在线扩 registry 或丢 Fact。
- merge key 只允许 proven SQL/FQN、Flow/Outcome ID、exact endpoint 或 Proof-backed equivalence；显示名/simple name/term key 不是 identity。
- 每个 Fact atom、meaning、fallback、Gap 恰一个 owner；equal-priority unresolved conflict 是 fatal，不按输入顺序胜出。
- 0 Flow 仍产生 scope/Gaps/technical knowledge；Stage 07 不调用 Provider、不读 raw reasoning/source。
- Stage05全部Flow和Stage06全部candidate/GAP/FAILED dispositions必须被处置；Stage07每run只产一个RepositoryKnowledge，跨Flowidentity/relation/metric/conflict均在其中，禁止per-Flow knowledge旁路。
- merge 数据结构、索引和并行策略可选择；decision lattice、anchor priority、ownership/accounting、Gap/fatal 不得改变。

## 9. 当前实现差距审计

| 状态 | 当前事实 |
| --- | --- |
| **部分具备（bounded v0）** | 现有 Stage03 有 finite term/claim admission、technical fallback、hard-anchor merge、Flow-local Gap 和 typed repository model tests |
| **能力有限** | relation kind、通用 conflict/priority、显式 owner map 和跨 source-shape anchor 仍不完整 |
| **R0 lineage尚未实现** | 当前实现没有Stage06 `RepositoryInterpretationRegistry`，因此也没有`registryProposalId→provisionalKey→interpretationProposalId→selectedKey→meaningId`准入与knowledge lineage |
| **尚未符合目标** | 没有独立 run/stages/07 production artifacts；当前成功结果通常先在内存完成，随后只在 final Candidate archive 中出现 |
| **真实 DepotHead 边界** | 0 Flow/0 Capsule 意味着 0 admitted model meaning；只保留可确定的范围与 Gap，不能写业务成功叙述 |

本阶段目标不是保留现有类 shape，而是保证模型提案不能自批、仓库知识有稳定 owner 和可重放 merge。
