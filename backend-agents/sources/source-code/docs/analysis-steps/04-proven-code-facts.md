# 已证明代码事实

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。运行顺序只由文件名中的 `04-` 与运行目录 `steps/04-proven-code-facts/` 表达。

本文示例严格使用DESIGN §1.3的`NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN`分类；未标为strict的digest/size/ID不可复制为golden。权威字段表、enum、identity和direct-preimage合同始终exact，不能靠示例降级删除。

## 1. 为什么存在

程序图说明“解析到了哪些node/edge”，但一个事实往往由多段关系共同成立。例如“Java在指定guard下以来源已证明的record/example参数调用DepotHeadMapper.updateByExampleSelective”涉及HTTP参数、Service数据流、entity property、call target、ordered arguments、control context和Evidence。它不等于“数据库已写入某列”。

本分析步骤先枚举完整 Fact 的 required atoms，再逐 atom 建 Proof。任何 required atom 不闭合，整条复合 Fact 都不准入；已证明的 sibling atom 也不能拼成一条语义残缺的事实。

## 2. 具体输入与 DepotHead 例子

输入是 persisted snapshot、capability report、五张 graph、Fact/Gap profiles、schema/tool hashes 和预算。

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本文件用连贯目标值展示candidate→Proof/Gap artifact接力，不把历史pre-reset DepotHead rejection改写成success；当前SourceAnalysis尚未运行该样例。技术unknown只用nullable/UNRESOLVED/Gap/fatal表达。

DepotHead 的 **REAL_SOURCE** 在 Controller :43,:178-191、Service :741-822、Mapper Java :23、DepotHead :63,:301-307、Example :149-151 和 Mapper XML :3,:70-93,:385-497。

目标候选Fact：

~~~text
kind: JAVA_BOUNDARY_INVOCATION
required atoms:
  ENTRY_ROUTE = POST /depotHead/batchSetStatus
  INPUT_FIELD = status
  SERVICE_HANDLER = DepotHeadService.batchSetStatus
  ELIGIBLE_ID_SET = dhIds
  INVOCATION_CALL_ID = exact M2 call-site ID
  STATIC_TARGET_TYPE = DepotHeadMapper
  STATIC_TARGET_METHOD = updateByExampleSelective
  STATIC_TARGET_SIGNATURE = updateByExampleSelective(DepotHead, DepotHeadExample)
  ORDERED_ARGUMENTS = [record, example]
  JAVA_LOCAL_ORIGINS = per-ordinal proven origins
  CONTROL_CONTEXT = exact M3 block/guard
  INVOCATION_EVIDENCE = Java call locator + java-boundary-invocation-v1
~~~

这是candidate denominator，不是当前admitted Fact。`jsh_depot_head.status`、`WHERE id IN`等可作为独立M1/M2静态结构Fact；“该boundary invocation执行了更新/筛选”必须始终是外部效果Gap，不能成为这个candidate的atom。

## 3. 程序怎样工作

1. 按版本化 Fact registry 枚举所有 candidate Facts 及 required atoms，先固定分母。
2. 为 atom 选择明确 role：KIND、ATTRIBUTE、CONDITION、LITERAL 或 RELATIONSHIP。
3. 从 Evidence graph 取 source nodes，从结构/call/control/data graphs 取 required edges 和 rule applications。
4. 重开 snapshot，重验 file/span SHA、parsed node identity 和 edge endpoints。
5. 构造无环 Proof：Fact/atom identity 先于 Proof identity。
6. 仅当复合 candidate 的所有 required atoms 都 CLOSED 时生成 CodeFact。
7. 根因 atom 使用具体 rejection code；同一复合 Fact 的 sibling 使用 COMPOSITE_FACT_REJECTED。
8. 生成 capability/fact/expectation Gaps，重算 accounting，原子安装 分析步骤“已证明代码事实”。

## 4. 生成的可观察产物

| 文件 | 唯一职责 |
| --- | --- |
| proven-facts.json | admitted CodeFacts、atoms 和 candidate dispositions |
| proof-pack.json | Proof nodes/edges/rules 与逐 atom CLOSED proofs |
| gap-ledger.json | capability gaps、fact rejections、expectation gaps |
| fact-accounting.json | candidate/admitted/rejected Fact/atom 分母与守恒方程 |
| proven-code-facts-receipt.json | upstream graph roots、control hashes、artifact set |

**示例分类：STRUCTURAL_WIRE_SPECIMEN。** 目标 DepotHead output 可以诚实是；字段完整但不是由本次run重算的strict golden：

~~~json
{
  "candidateFactKey": "DEPOTHEAD_JAVA_BOUNDARY_INVOCATION",
  "disposition": "REJECTED_WITH_REASON",
  "admittedFactId": null,
  "reasonCode": "DATA_FLOW_BINDING_UNPROVEN"
}
~~~

这是exact `FactDisposition`；缺失的boundary target/argument/origin/control/evidence atom分别由同candidate的`AtomDisposition`和Gap记录。在ProgramGraphs boundary合同闭合后，同一candidate才可能成为ADMITTED_WITH_PROOF；外部效果Gap不因invocation Fact准入而关闭。

目标分析步骤“已证明代码事实”出口始终包含五个命名文件和非空candidate/accounting记录。`proven-facts.json`可以有0个admitted Fact，但不能没有candidate dispositions；DepotHead至少留下`DEPOTHEAD_JAVA_BOUNDARY_INVOCATION` disposition，并留下`DEPOTHEAD_EXTERNAL_UPDATE_EFFECT_UNPROVEN` Gap，保证invocation与effect不被混写。

### 4.1 人类 walkthrough：模块用什么文件接力

**示例分类：NARRATIVE_ILLUSTRATION。** 为了展示完整目标故事，下面采用“未来五图已闭合”的分支；当前SourceAnalysis尚无Fact实现或输出，历史pre-reset rejection/Gap只作为验收反例，三行不能加载为wire。

~~~jsonl
{"module":"candidates","artifact":"modules/01-candidates/fact-candidate-set.json","takesFrom":["ApplicationDiscoveryReference","ProgramGraphsReference"],"says":{"candidateKey":"DEPOTHEAD_JAVA_BOUNDARY_INVOCATION","kind":"JAVA_BOUNDARY_INVOCATION","entryId":"entry:post-depothead-batch-set-status","requiredAtoms":["INVOCATION_CALL_ID","STATIC_TARGET_TYPE","STATIC_TARGET_METHOD","STATIC_TARGET_SIGNATURE","ORDERED_ARGUMENTS","JAVA_LOCAL_ORIGINS","CONTROL_CONTEXT","INVOCATION_EVIDENCE"]}}
{"module":"proofs","artifact":"modules/02-proofs/proof-decision-set.json","takesFrom":["fact-candidate-set.json","five graphs","verified source"],"says":{"factId":"fact:depothead-mapper-boundary-invocation","decision":"ADMITTED_WITH_PROOF","statement":"Java invokes the static target with the recorded ordered arguments","externalEffect":"UNPROVEN_GAP"}}
{"module":"publish","artifacts":"modules/03-publish/<four-semantic-files>","receipt":"modules/03-publish/module-receipt.json","takesFrom":["fact-candidate-set.json","proof-decision-set.json"],"says":{"admittedFactIds":["fact:depothead-mapper-boundary-invocation"],"remainingGapIds":["gap:depothead-external-update-effect-unproven"],"semanticFiles":["proven-facts.json","proof-pack.json","gap-ledger.json","fact-accounting.json"],"analysisStepReceipt":null,"nextStep":"CanonicalAnalysisStepArtifactStore"}}
~~~

`fact:depothead-mapper-boundary-invocation`只在全部invocation atoms闭合时出现；它与external-effect Gap可同时存在。M3不能为了让BusinessFlows有Flow而把XML/SQL静态结构加进Proof并改写成持久化成功。

## 5. 下游怎样消费而不返工

分析步骤“业务流程” 只读取 proven-facts.json、proof-pack.json、gap-ledger.json、fact-accounting.json 和五图 roots。它按 factId/atomId/proofId 引用，不能：

- 借用另一个 Fact 的 span；
- 使用 Manifest 中“附近”但未引用的 Evidence；
- 把模型一致、candidateId 或 Trace locator 当 Proof；
- 在 Flow 编译时重新发明 Fact kind。

分析步骤“流程解释” 的 Capsule 只携带可读投影；完整 ProofPack 仍留在程序侧。

### 下游前置条件与后置保证

| 分析步骤“业务流程” 开始前必须成立 | 分析步骤“已证明代码事实” 成功后保证 |
| --- | --- |
| 分析步骤“程序图” 五图 roots、Evidence/source reopen 与 分析步骤“已证明代码事实” controls 完全一致 | 每个 candidate Fact 和 required atom 恰一条 disposition，分母/accounting 不缩水 |
| 五个 分析步骤“已证明代码事实” files 及 receipt root 重验闭合 | 每个 admitted Fact 的每个 atom 都有独立 CLOSED Proof；rejected candidate 有根因与 closure requirement |
| 无 conflicting admitted Facts、断 Proof 或 source drift | 分析步骤“业务流程” 只需按 IDs 消费 Facts/Proof/Gaps，不需要重新判定事实 |

分析步骤“业务流程” 遇到未准入 candidate、REJECTED atom 或 expectation Gap，只能据此形成 Flow Gap/coverage；不能在流程编译时升级为 Fact。

## 6. 成功、Gap、fatal 与显式复用

- **成功**：允许同时有 admitted Facts、rejections 和 Gaps；全部 dispositions/accounting 闭合，分析步骤目录原子安装。
- **带 Gap 成功**：Fact 无法唯一证明、profile 外语义或受控静态 absence；Gap 必须写缺什么、影响什么、如何关闭。
- **fatal**：Proof source/edge/reference 漂移、duplicate canonical ID、CLOSED Proof 缺 required node/edge、conflicting Facts 同时 admitted、accounting 不守恒或 artifacts 不一致。
- **显式复用**：新BusinessFlows execution重验全部graph roots和ProvenCodeFacts artifact root；controls相同才读取。fatal不删除VerifiedSourceInventory、ApplicationDiscovery与ProgramGraphs。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 枚举 candidate Fact/atoms | 是，版本化 registry | 否 |
| 构造/验证 Proof | 是 | 否 |
| 决定 admitted/rejected | 是 | 否 |
| 把未知补成事实 | 否，写 Gap | 否 |
| 业务命名 | 否，留到 分析步骤“流程解释” | 否 |

产品运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块执行顺序固定为 `FactCandidateEnumerator` → `AtomicProofBuilder` → `FactLedgerPublicationSpecifier` → `CanonicalAnalysisStepArtifactStore`。前三步先安装自己的 canonical module artifact，下一步只读该 artifact；analysis step store不是第四个业务模块，不得靠共享 mutable collections 传 candidate、Proof 或 Gap。

为避免实现者把“事实分析步骤根包”与“某个模块的Java包”混为一谈，三个模块的公开类型位置固定如下：

| 模块 | 唯一Java包 | 公开类型 |
| --- | --- | --- |
| M1 候选枚举 | `org.sourceanalysis.app.analysis.fact.candidates` | `FactCandidateEnumerator`、`FactCandidateSet`及其候选/原子/registry records |
| M2 原子证明 | `org.sourceanalysis.app.analysis.fact.proofs` | `AtomicProofBuilder`、`ProofDecisionSet`及其 Proof/decision records |
| M3 账本发布 | `org.sourceanalysis.app.analysis.fact.publish` | `FactLedgerPublicationSpecifier`、`ProvenCodeFactsReference`及其发布 records |

`org.sourceanalysis.app.analysis.fact`只表示整个语义步骤；它不放置这三个模块的别名、桥接类型或第二个公开入口。测试目录必须镜像所属模块包。这个局部命名裁决不改变步骤输入、输出、数量、schema或公共运行时Interface。

#### M1 FactCandidateEnumerator

- **解决的问题**：在看证明结果前先冻结应该尝试证明的 Fact/required atom 分母，防止失败项消失。
- **精确上游输入及前置**：`PersistedFactCandidateInputReader`只能从同一run、同一snapshot、同一controls的fresh-reopened `ApplicationDiscoveryReference`和完整`ProgramGraphsReference`构造一个`FactCandidateInputs`。它必须重开并验证ProgramGraphs的全部五张public graph、graph-index和graph-gaps；不得接收自由`JsonNode`、Path、单独graph、draft或调用者拼装的catalog。输入携带五张graph payload references、每个public node/edge的endpoint/owner/evidence IDs、ApplicationDiscovery entry IDs，以及版本化Fact registry/profile/budget。
- **精确适用关系（不得做笛卡尔积）**：对每个DataFlow `JAVA_BOUNDARY_INVOCATION` node，枚举器先取得它的`owningEntryIds`。对其中每个entryId，只在以下同一条冻结Java路径全部闭合时才实例化候选：
  1. entryId存在于ApplicationDiscovery与五图的共同entry分母；
  2. boundary node内的`invocationCallId`命中CALL graph的`CALL_SITE` node，且该callsite的owner包含该entry；
  3. boundary node内的`callTargetEdgeId`命中从该callsite出发的EXACT `CALL_TARGET` edge；
  4. 每个ordered argument都有一条EXACT `ARGUMENT_TO_BOUNDARY` data edge，edge的to endpoint是该boundary node，from endpoint与该ordinal的argument node相同；
  5. boundary的control block与可选guard命中CONTROL graph，并且它们的owner包含该entry；
  6. boundary node、call-target edge、每条argument edge和control context各有同一Evidence graph中的source-excerpt + rule-application闭包。

  以上任一关系缺失、owner不相交、endpoint不匹配、resolution不是EXACT或Evidence不闭合时，枚举器为这个`entryId + boundaryNodeId + templateKey`写一个`NOT_APPLICABLE` disposition，带具体missing role/reason；它不能把该boundary分配给别的entry、借用别的boundary/edge的证据，或把这种不闭合静默变成零分母。ProgramGraphs publication、schema、root、controls或引用本身不一致是fatal `PROOF_PACK_REFERENCE_BROKEN`，不是普通not-applicable。
- **确定性顺序 / LLM**：按entryId → boundaryNodeId → registry template key排序；先生成每个template-boundary-entry组合的`APPLICABLE`或`NOT_APPLICABLE` disposition，再只对APPLICABLE组合按registry声明顺序复制required atoms。一个boundary被多个entry共同拥有时只为这些共同owners各生成一个候选；0 LLM。
- **目标输出与 DepotHead 示例**：`FactCandidateSet{schemaVersion,candidateSetId,sourceGraphRoots,candidates,notApplicableDispositions,denominator}`。每个`JAVA_BOUNDARY_INVOCATION` candidate保存`candidateFactKey`、entryId、boundaryNodeId、invocationCallId、callTargetEdgeId、ordered argument edge IDs、control block/guard IDs、按subject分组的Evidence node IDs和按模板声明顺序的required atoms；例子含`DEPOTHEAD_JAVA_BOUNDARY_INVOCATION`及call ID、static target triple、ordered arguments、Java-local origins、control、evidence atoms。external effect另列Gap，绝不进入candidate。
- **必须保持的不变量**：`candidateFactKey + entryId + boundaryNodeId`唯一；每种Fact kind的required atoms完整且版本化；每个template-boundary-entry组合恰一条applicable/not-applicable disposition；candidate set的sourceGraphRoots是完整五图payload的排序集合；枚举不受后续Proof成败影响。
- **Gap / fatal / artifact复用**：profile 外但可定位的 candidate 标成 CapabilityGap；registry/schema/reference/accounting 冲突 fatal；模块只读相同graphs/profile，不读取未安装draft。
- **给下游的后置保证**：M2 得到不可变 candidate/atom IDs、required evidence/edge roles 和完整 denominator，无权删减。
- **明确非目标**：不选择具体 proof path、不 admission Fact、不解释业务名称。
- **公共测试 seam 与验收**：`enumerate(FactCandidateInputs, FactRegistry)`。fixture必须先用真实canonical stores安装ApplicationDiscovery与完整ProgramGraphs publication，再由`PersistedFactCandidateInputReader`重开输入。首个two-entry/two-boundary golden证明只产生两条各自闭合的candidate，不允许四条cross-product；删除一条argument/control/evidence edge或改变owner/endpoint只令其对应组合NOT_APPLICABLE，不影响另一个entry-boundary组合。后续覆盖DepotHead template、registry deletion/reorder、同名decoy、old ProgramGraphs set拒绝与input order determinism；Proof删除不能改变M1的candidate bytes/counts。
- **Luna/xhigh 测试指南**：创建`FactCandidateEnumeratorTest`，冻结完整ProgramGraphs v3/v2 boundary artifacts与手写candidate golden。逐RED：two-entry/two-boundary精确join、缺argument/control/evidence的NOT_APPLICABLE、外部effect不进入Fact、Mapper/HTTP同generic kind、ambiguous Graph Gap、old ProgramGraphs set拒绝、registry atom顺序和input order determinism；registry/canonical store不可mock，不能用缩减graph-index JSON代替真实reopen。
- **Terra/xhigh 实现指南**：RED后只改 `org.sourceanalysis.app.analysis.fact.candidates`（路径为`analysis/fact/candidates/`），实现 `PersistedFactCandidateInputReader`、public `FactCandidateInputs`、`FactRegistry`、`FactCandidateEnumerator/FactCandidateSet` 与 `proven-code-facts-fact-candidate-set-v1`；只读ApplicationDiscovery与完整ProgramGraphs publication，fresh-reopen→exact boundary path join→template match→applicable/not-applicable denominator。逐RED GREEN，Proof结果不得反向影响枚举；禁止fixture硬编码/改required atoms。上游或registry语义不足MUST STOP交Sol/ultra，完成更新审计。

#### M2 AtomicProofBuilder

- **解决的问题**：逐 atom 证明 source bytes 与 semantic graph/rule path都闭合，并执行 composite all-or-nothing admission。
- **精确上游输入及前置**：M1 candidate artifact、ProgramGraphs five graphs/evidence、VerifiedSourceInventory source handles、Proof rule registry/budget；candidate IDs、graph endpoints、source roots 完全一致。
- **确定性顺序 / LLM**：按 candidate/atom key → 解析 required graph/evidence roles → 重开 span/hash → 建无环 proof closure → 先得 atom dispositions → 再按 all-atoms rule 得 Fact disposition；0 LLM。
- **目标输出与 DepotHead 示例**：`ProofDecisionSet{proofs,codeFacts,factDispositions,atomDispositions,rootCauseRejections}`；boundary atoms全闭合时可admit invocation Fact，但external update/where效果仍有`DATA_FLOW_BINDING_UNPROVEN` Gap且不进入该Fact。
- **必须保持的不变量**：一个 admitted atom 恰一个 CLOSED Proof；Fact 任一 required atom失败则无 admitted sibling；Proof refs 只指 M1/ProgramGraphs/VerifiedSourceInventory identities。
- **Gap / fatal / artifact复用**：可解释的不闭合是 rejection/Gap；source/edge/reference drift、proof status伪 CLOSED、conflicting admitted facts fatal；M2从完整candidate set构建全部decisions，不混用其他publication的proof。
- **给下游的后置保证**：M3 获得每个 candidate/atom 的唯一 disposition、closed proofs 或具体根因，能直接守恒计数。
- **明确非目标**：不把 evidence locator、Trace、模型或文本相似当 Proof，不生成 Flow。
- **公共测试 seam 与验收**：`prove(candidateSet, graphs, source, rules)`对每个boundary atom逐段deletion、source/rule mutation、XML/SQL伪支持、conflict和正向closure；任一缺项不admit invocation Fact，任何完整invocation也不关闭external-effect Gap。
- **Luna/xhigh 测试指南**：创建`AtomicProofBuilderTest`，fixtures/goldens放`src/test/resources/analysis/fact/proofs/`。一个行为一个RED：全部boundary atoms闭合正例、每段deletion rejection、external-effect非准入、source/rule drift fatal、sibling all-reject、conflicting facts、root replay；expected Proof paths独立手写。只fake source reopen，禁止mock graph traversal/Proof/canonical；无网络/模型。
- **Terra/xhigh 实现指南**：RED后仅拥有 `org.sourceanalysis.app.analysis.fact.proofs`（路径为`analysis/fact/proofs/`），实现 public `AtomicProofBuilder/ProofDecisionSet` 与 `proven-code-facts-proof-decision-set-v1`；M1+ProgramGraphs+source→role match→reopen→Proof→atom→composite decision。逐atom GREEN再composite GREEN；不得借Evidence/模型/Trace或保留sibling。需改Fact/graph跨analysis step contract则MUST STOP并交用户流程，审计同步。

#### M3 FactLedgerPublicationSpecifier

- **解决的问题**：把 Proof decisions、Gaps 与分母汇成四个semantic analysis step payload并验证没有 orphan/silent loss；root与receipt只由analysis step store产生。
- **精确上游输入及前置**：M1 candidate artifact、M2 proof decision artifact、capability/expectation gaps、VerifiedSourceInventory、ApplicationDiscovery与ProgramGraphs roots、ProvenCodeFacts controls；所有 IDs/references 局部有效。
- **确定性顺序 / LLM**：归类 CapabilityGap/FactRejection/ExpectationGap → 计算 Fact/atom equations → conflict/reference validation → canonical四个semantic payload → M3 install/receipt → analysis step store fresh reopen/root/receipt-last；0 LLM。
- **目标输出与 DepotHead 示例**：M3恰四个semantic files；analysis step store形成四项+receipt的五文件reader-visible set。历史回归例要求`proven-facts.json`保存rejected disposition、`gap-ledger.json`保存missing value/where requirements，`fact-accounting.json`记录`admittedFactCount=0`；analysis step receipt只绑定四项descriptor、status、controls与Gap refs/count。
- **必须保持的不变量**：每 candidate/atom 恰一 disposition；Gap 有 affected IDs/missing/impact/closure；semantic files引用同一 M1/M2 roots；M3不含或预报analysis step root/receipt且不改 decision。
- **Gap / fatal / artifact复用**：合法 rejection/Gaps 可 SUCCEEDED_WITH_GAPS；orphan/double disposition、accounting/reference/canonical/install/collision 错误 fatal；下游只认完整receipt。
- **给下游的后置保证**：BusinessFlows 能只读 Facts/Proof/Gaps/accounting，并确定哪些 atom可进入 Flow、哪些必须留 Gap。
- **明确非目标**：不重新证明、不把 rejection 升级、不调用模型、不选择 Flow ownership。
- **公共测试 seam 与验收**：`specifyCandidatesAndProofs(candidateSet, decisions, gaps)` 覆盖 orphan/double owner/count-equal-but-ID-different、乱序和partial-install；只有M3 exact-four、analysis-step-store exact-five及ID-set equations全闭合才返回 ProvenCodeFactsReference。
- **Luna/xhigh 测试指南**：创建 `ProvenCodeFactsPublicationSpecifierTest`，module artifacts/goldens在 `src/test/resources/analysis/fact/publish/`。RED顺序：M3 exact-four、analysis step exact-five、current DepotHead rejection、positive admitted、orphan/double disposition、count spoof/ID mismatch、Gap closure、receipt-last/partial-install/collision；使用真实module/analysis step stores，不能mockaccounting/canonical/root。命令：`mvn -Dtest=ProvenCodeFactsPublicationSpecifierTest test`；禁网络/customer Maven。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `org.sourceanalysis.app.analysis.fact.publish`（路径为`analysis/fact/publish/`），实现 public `FactLedgerPublicationSpecifier/ProvenCodeFactsReference`；只读M1/M2 artifacts，classify gaps→ID-set equations→four semantic files→M3 install/receipt→typed analysis-step-store receipt-last。不得生成旧single publication summary、预报root/receipt、重新prove或升级rejection。任何field/accounting跨analysis step改动MUST STOP交Sol/ultra/用户，完成更新审计。

### 8.0.1 模块 artifact wire schemas

M1/M2使用 DESIGN 13.3 `ModuleArtifact<T>` envelope；M3直接安装四个analysis step schema注册的semantic JSON bytes而无summary envelope。`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-candidates/fact-candidate-set.json` | `proven-code-facts-fact-candidate-set-v1` / `PROVEN_CODE_FACTS_FACT_CANDIDATE_SET` | exact ApplicationDiscovery `capability-report/entry-points`与完整ProgramGraphs五张public graph、graph-index、graph-gap ArtifactReferences；reader重开后建立不可替代的`FactCandidateInputs` | `candidateSetId!`、`sourceGraphRoots[]!`（五图payload root的UTF-8排序集合）、`candidates[]!{candidateFactKey!,entryId!,kind=JAVA_BOUNDARY_INVOCATION!,boundaryNodeId!,invocationCallId!,callTargetEdgeId!,orderedArgumentEdgeIds[]!,controlBlockId!,guardId?（required nullable）,evidenceNodeIdsBySubject[]!,requiredAtoms[]!{atomKey!,role!,valueType!,expectedEvidenceKinds[]!}}`、`notApplicableDispositions[]!{entryId!,boundaryNodeId!,templateKey!,missingRoles[]!,reasonCode!}`、`denominator!{applicableKeys[]!,notApplicableKeys[]!}`；dispositions按entry/boundary/template，atoms按registry order |
| `modules/02-proofs/proof-decision-set.json` | `proven-code-facts-proof-decision-set-v1` / `PROVEN_CODE_FACTS_PROOF_DECISION_SET` | exact M1、VerifiedSourceInventory `source-inventory/verified-snapshot`和ProgramGraphs七个semantic ArtifactReferences | `candidateSetId!`、`codeFacts[]!{factId!,kind!,subjectNodeIds[]!,atoms[]!{atomId!,role!,name!,value!{type!,canonical!},proofId!}}`、`atomProofs[]!{proofId!,factId!,atomId!,rootEvidenceNodeId!,requiredEvidenceNodeIds[]!,requiredProgramEdgeIds[]!,ruleIds[]!,status=CLOSED!}`、`factDispositions[]!{candidateFactKey!,disposition!,admittedFactId?,reasonCode?}`、`atomDispositions[]!{candidateFactKey!,atomKey!,disposition!,proofId?,reasonCode?}`、`rootCauseRejections[]!{candidateFactKey!,atomKey!,reasonCode!,gapId!}`；各数组按ID/key，Fact内atoms按registry order |
| `modules/03-publish/<four registered semantic filenames>` | 各public schema/type；无summary envelope | exact M1 `fact-candidates`与M2 `proof-decisions` ArtifactReferences | 一次module install恰`proven-facts.json/proof-pack.json/gap-ledger.json/fact-accounting.json`；module receipt绑定四descriptors；禁止analysis step root/receipt或五项published list；AnalysisStep store provenance绑定M3 reference |

M3的完整module fixture必须用一次install request/receipt绑定表中M1/M2两个ArtifactReferences；四个standalone payload不得重复envelope。只给四份payload而省略该排序upstream集合，不是完整M3 fixture。

本步骤自身record字段未因boundary合同改变，因此module/public文件名和schema保持；但Fact/Gap profile必须发布包含`JAVA_BOUNDARY_INVOCATION`与external-effect Gap的新版content-addressed ref。M1/M2 readers必须exact接受完整ProgramGraphs public set所发布的data-flow v2、evidence v3、index v2以及其余三张public graph/Gap集合，拒绝旧集合、draft与任何新旧混搭；不能通过重开XML/SQL补成effect。

atom value使用8.1 typed union；静态unknown不允许story value，必须使atom disposition=`REJECTED_WITH_REASON`并引用Gap。一个candidate admitted时其requiredAtoms与atomProofs一一对应；rejected candidate的`admittedFactId` required nullable为null。success envelope failureRef=null；integrity fatal写ModuleFailure。field/atom registry/source rule/sort/identity变化先设计并升version。

**示例分类：STRUCTURAL_WIRE_SPECIMEN（isolated candidate semantics）。** 会把静态XML/SQL结构误写成boundary side effect的旧正例已删除。M1/M2 wire字段形状仍以上表为准；新Fact registry只能从ProgramGraphs v3/v2枚举`JAVA_BOUNDARY_INVOCATION`，并把外部effect作为独立Gap。

### 8.1 Interface 与 records

~~~java
interface CodeFactProver {
    ProvenCodeFactsReference prove(ProgramGraphsReference graphs);
}
~~~

~~~text
CodeFact
  factId
  kind
  subjectNodeIds[]
  atoms[]

FactAtom
  atomId
  role
  name
  value {type, canonical}
  proofId

Proof
  proofId
  factId
  atomId
  rootEvidenceNodeId
  requiredEvidenceNodeIds[]
  requiredProgramEdgeIds[]
  ruleIds[]
  status=CLOSED

FactDisposition
  candidateFactKey
  disposition
  admittedFactId?                       // required nullable
  reasonCode?                            // required nullable

AtomDisposition
  candidateFactKey
  atomKey
  disposition
  proofId?                               // required nullable
  reasonCode?                            // required nullable

Gap
  gapId
  kind
  code
  affectedEntryIds[]
  affectedCandidateFactKeys[]
  evidenceRefs[]
  missingRequirement
  impact
  closureRequirement
~~~

Atom value type 只允许 STRING、INTEGER、DECIMAL、BOOLEAN、SYMBOL_REF 或 ENUM_REF。任意 prose 不进入 atom。

### 8.2 Proof 与 identity

无环顺序：

1. graph/evidence node/edge IDs 已由 分析步骤“程序图” 固定；
2. atomId 依赖 snapshot、candidateFactKey、role/name/value；
3. factId 依赖 snapshot、kind、subject nodes 和排序 atoms，不含 proofId；
4. proofId 依赖 factId、atomId、Evidence/program-edge closure 和 rule IDs；
5. proofPackId、provenFactSetId、gapLedgerId、accountingId 最后计算。

Proof 必须同时证明 source bytes 和 semantic rule path。file/span hash 正确只证明完整性，不自动证明语义。

### 8.3 Accounting

~~~text
candidateFactCount = admittedFactCount + rejectedFactCount
candidateAtomCount = admittedAtomDispositionCount + rejectedAtomCount
provenFactAtomCount = admittedAtomDispositionCount
~~~

一个复合 Fact 任一 atom 失败时，其所有 atoms 都是 REJECTED_WITH_REASON；不得把可独立证明的 sibling 留成孤立 admitted atom。

### 8.4 Gap 分类

- CapabilityGap：parser/profile 无法确定某个 site。
- FactRejection：candidate atom/Fact 的 Proof 不闭合。
- ExpectationGap：版本化 profile 声明应检查的问题，经 bounded search 没有静态答案。

Gap 不是负面业务事实，也不是 placeholder。它必须保存 searched scope 或缺失 requirement。

### 8.5 预算、安全与 failure codes

预算覆盖 candidate Facts、atoms、Proof nodes/edges、reopen bytes、rule applications、Gaps 和 conflict set。超限不能缩小 candidate denominator。

只读 分析步骤“程序图” artifacts 和 verified source handles；不运行客户代码或模型。所有 locator/path/hash 由程序产生。

稳定 code：

FACT_PROFILE_INVALID、FACT_KIND_UNSUPPORTED、REQUIRED_ATOM_MISSING、DATA_FLOW_BINDING_UNPROVEN、COMPOSITE_FACT_REJECTED、PROOF_PACK_REFERENCE_BROKEN、PROOF_PROGRAM_EDGE_BROKEN、PROOF_SOURCE_REOPEN_MISMATCH、PROOF_NOT_CLOSED、CONFLICTING_FACTS、FACT_ACCOUNTING_INVARIANT_BROKEN、PROVEN_CODE_FACTS_RESOURCE_LIMIT_EXCEEDED。

### 8.6 测试 seam 与验收

- 先枚举denominator，再mutation route、Service call、setter/property、boundary call ID/target triple/argument/origin/control/evidence任一段；XML table/column/where不得成为external-effect Proof。
- 删除 Fact 自己引用的 span/edge 必须 rejection；另一个 Fact 的 Evidence 不能补。
- 同名 decoy、注释字符串和目标 JSON 不得形成 Proof。
- Proof source span、graph endpoint、rule ID、fact/atom reference mutation fatal。
- conflicting canonical Fact 双方都不 admitted。
- 等价 root/input order 产生相同 records/IDs/canonical bytes。
- fixed DepotHead slice在generic boundary能力补齐前继续输出Gap/rejection；补齐后只可positive-admit invocation，不得硬编码external update positive golden。

验收要求同时有：完整composite Fact正例、每个required atom的独立deletion/mutation反例、conflicting Fact、不同root fresh-reopen，以及从历史审计提炼的DepotHead rejection baseline。只有positive case全atoms CLOSED才admitted，任一反例都不会留下sibling admitted atom，五个artifacts可独立重验，分析步骤“已证明代码事实”才算可交付。

### 8.7 已冻结裁决：实现者不得自由推断

- candidate Fact/required atoms 来自版本化 registry，并在证明前固定；实现者不能为某 fixture 临时删 atom 或新增硬编码 Fact。
- 复合 Fact 是全有或全无：任一 required atom 不闭合，整条 Fact 及 sibling dispositions 都 rejected。
- span hash、graph edge 和 rule path 三者共同构成 Proof；locator、Trace、模型一致或字符串相似都不能替代。
- Gap 必须有 missing requirement、impact 和 closure requirement；不能把 Gap 写成负面业务事实或空 placeholder。
- conflicting Facts、accounting、reference 或 source-integrity 问题是 fatal，不能降为“低置信度”Fact。
- Proof 内部数据结构和 rule engine 实现可自行选择；Fact registry/version、atom roles、identity 顺序、admission 与失效规则不得改变。

## 9. 当前实现成熟度审计

Wire Reset后的`org.sourceanalysis.app.analysis.fact`目前只有语义package骨架；当前没有Fact candidate、Proof builder、Gap ledger或本步骤artifact。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 目标package与JDK 17 Toolchain已就位；通用wire头门禁不理解Fact、Proof或Evidence语义。 |
| **本步骤生产能力尚未实现** | M1–M3、逐atom proof、五项正式输出和从五图fresh-reopen的Fact compiler均不存在。 |
| **历史证据，不是当前能力** | 已删除的pre-reset代码曾验证有限profile的Fact/Proof/Gap/accounting；旧POC五个人工LockedFact独立审计仅两条成立。它们说明“hash闭合不等于语义证据闭合”，不能复制为当前Fact。 |
| **下一实现门** | 按本章只消费五张图与其Evidence，逐atom建立generic boundary invocation Proof或Gap，并用target/argument/origin/guard/evidence与XML/SQL伪支持mutation证明不会把外部效果猜成事实。 |

分析步骤“已证明代码事实”的成功允许有Gap；但只有当前新实现 admitted-with-Proof 的Fact才能进入分析步骤“业务流程”。
