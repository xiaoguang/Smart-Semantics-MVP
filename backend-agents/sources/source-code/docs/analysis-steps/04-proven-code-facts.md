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

目标候选Fact之一是边界调用：

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

另一个独立目标候选是控制条件，而不是把它塞回某一次调用的上下文：

~~~text
kind: JAVA_GUARD_CONDITION
candidate denominator key: entryId + "|" + guardNodeId + "|JAVA_GUARD_CONDITION"
required atom:
  CONTROL_CONDITION = ControlFlow GUARD node 的 normalizedCondition
required proof:
  guard node + guard source excerpt + control-flow-if-guard-v1 rule application
~~~

例如 `if (status == null) { return; }` 的 `status == null` 产生一条 `JAVA_GUARD_CONDITION` Fact 和一条 `CONTROL_CONDITION` atom。TRUE 与 FALSE 两条控制边都引用**同一个** atom，只用 polarity 区分结果。它不说明“空状态在业务上是否应该拒绝”，也不说明后续数据库效果；它只证明 Java 代码在该入口范围内以这个条件分支。

`CONTROL_CONTEXT` 与 `CONTROL_CONDITION` 绝不等价：前者是某个边界调用的复合 Fact 所处控制块；后者是可独立追溯的 guard 条件。没有后者，下一步不能为 `BranchDecision.conditionAtomId` 编造值，必须形成 Gap 或 fatal。

## 3. 程序怎样工作

1. 按版本化 Fact registry 枚举所有 candidate Facts 及 required atoms，先固定分母：既包括 `JAVA_BOUNDARY_INVOCATION`，也包括每个 entry scope 内可达的 `JAVA_GUARD_CONDITION`。
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

分析步骤“业务流程” 只读取 proven-facts.json、proof-pack.json、gap-ledger.json、fact-accounting.json 和五图 roots。它按 factId/atomId/proofId 引用；对每一条 TRUE/FALSE edge，它必须在同 entry ownership scope 找到**恰一条** `JAVA_GUARD_CONDITION` Fact 的 `CONTROL_CONDITION` atom，并以它填入 `BranchDecision.conditionAtomId`。它不能：

- 借用另一个 Fact 的 span；
- 使用 Manifest 中“附近”但未引用的 Evidence；
- 用 `CONTROL_CONTEXT`、guard node ID、字符串摘要或哈希临时合成 condition atom；
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
- **精确上游输入及前置**：`PersistedFactCandidateInputReader`只能从同一run、同一snapshot、同一controls的fresh-reopened `VerifiedSourceInventoryReference`、`ApplicationDiscoveryReference`和完整`ProgramGraphsReference`构造一个`FactCandidateInputs`。它必须重开并验证ProgramGraphs的全部五张public graph、graph-index和graph-gaps；不得接收自由`JsonNode`、Path、单独graph、draft或调用者拼装的catalog。输入携带五张graph payload references、`capability-report.json`与`entry-points.jsonl`的准确ArtifactReference、`source-inventory.jsonl`与`verified-snapshot.json`的准确ArtifactReference、每个public node/edge的endpoint/owner/evidence IDs、ApplicationDiscovery entry IDs，以及版本化Fact registry/profile/budget。尤其，内部`PublicEvidenceGraph.EvidenceNode`必须原样保留二选一的`SourceExcerptV1{locator,rawUtf8,rawUtf8Sha256}`或`RuleApplication{ruleId,ruleVersion,inputProgramElementIds}`；不得把它压缩成“有source/rule”的boolean或只保留ID。M1发布器只接收这个已验证输入和`FactCandidateSet`，由输入导出固定七项graph/discovery上游集合与controls；不得接收调用者给出的任意上游列表或controls。
- **精确适用关系（不得做笛卡尔积）**：v2 registry有两个相互独立的候选模板。对每个DataFlow `JAVA_BOUNDARY_INVOCATION` node，枚举器先取得它的`owningEntryIds`。对其中每个entryId，只在以下同一条冻结Java路径全部闭合时才实例化 boundary candidate：
  1. entryId存在于ApplicationDiscovery与五图的共同entry分母；
  2. boundary node内的`invocationCallId`命中CALL graph的`CALL_SITE` node，且该callsite的owner包含该entry；
  3. boundary node内的`callTargetEdgeId`命中从该callsite出发的EXACT `CALL_TARGET` edge；
  4. 每个ordered argument都有一条EXACT `ARGUMENT_TO_BOUNDARY` data edge，edge的to endpoint是该boundary node，from endpoint与该ordinal的argument node相同；
  5. boundary的control block与可选guard命中CONTROL graph，并且它们的owner包含该entry；
  6. call-site node、boundary node、call-target edge、每条argument edge、每个Java-local-origin node及control context（basic block与可选guard）各有同一Evidence graph中的source-excerpt + rule-application闭包。

  对每个 ControlFlow `GUARD` node，枚举器用该 node 的 owning entry IDs（不能从邻近 boundary 继承 owner）建立 `entryId + guardNodeId + JAVA_GUARD_CONDITION` 分母项。仅当：(a) entry 位于 ApplicationDiscovery 与五图共同分母；(b) guard node 的 kind 精确为 `GUARD`；(c) 至少一条 TRUE/FALSE edge 的 `guardNodeId` 精确命中该 node，二者 owner 都包含同一 entry；(d) guard node 在 Evidence graph 中有同一 subject 的 source-excerpt→rule-application 配对，且 rule 精确为 `control-flow-if-guard-v1/v1`；才产生 `APPLICABLE`。缺任一项只为这个 guard-entry-template 写 `NOT_APPLICABLE`，并记录缺失 role；不允许由 guard 的文字、行号、调用的 `CONTROL_CONTEXT` 或相邻 Evidence 补齐。ProgramGraphs publication、schema、root、controls或引用本身不一致仍是 fatal `PROOF_PACK_REFERENCE_BROKEN`。
- **确定性顺序 / LLM**：按entryId → template registry order → subject node ID排序；先生成每个 template-subject-entry 组合的`APPLICABLE`或`NOT_APPLICABLE` disposition，再只对APPLICABLE组合按模板声明顺序复制required atoms。boundary subject 是 boundaryNodeId，guard subject 是 guardNodeId；同一 guard 被多个 entry 合法拥有时仅为每个共同 owner 各生成一条候选；0 LLM。
- **目标输出与 DepotHead 示例**：`FactCandidateSet{schemaVersion,candidateSetId,sourceGraphRoots,candidates,notApplicableDispositions,denominator}`。`JAVA_BOUNDARY_INVOCATION`保持`candidateFactKey`、entryId、boundaryNodeId、invocationCallId、callTargetEdgeId、ordered argument edge IDs、control block/guard IDs、按subject分组的Evidence node IDs和按模板声明顺序的required atoms。`JAVA_GUARD_CONDITION`保存`candidateFactKey=JAVA_GUARD_CONDITION`、entryId、guardNodeId、guard node 的`normalizedCondition`、两个 branch edge IDs、guard Evidence node IDs，且 requiredAtoms 恰为一项`CONTROL_CONDITION{role=CONDITION,valueType=STRING}`。两类 candidate 都由 M2 从同一 fresh-reopened inputs 取得完整`SourceExcerptV1`及rule application；candidate module artifact不重复源字节。external effect 只属于 boundary candidate 的独立 Gap，绝不进入任一 Fact。
- **必须保持的不变量**：`candidateFactKey + entryId + subjectNodeId`唯一；每种Fact kind的required atoms完整且版本化；每个template-subject-entry组合恰一条applicable/not-applicable disposition；每个可达 guard 不能因没有 boundary invocation 而从分母消失；candidate set的sourceGraphRoots是完整五图payload的排序集合；枚举不受后续Proof成败影响。
- **Gap / fatal / artifact复用**：profile 外但可定位的 candidate 标成 CapabilityGap；registry/schema/reference/accounting 冲突 fatal；模块只读相同graphs/profile，不读取未安装draft。
- **给下游的后置保证**：M2 得到不可变 candidate/atom IDs、required evidence/edge roles 和完整 denominator，无权删减。
- **明确非目标**：不选择具体 proof path、不 admission Fact、不解释业务名称。
- **公共测试 seam 与验收**：`enumerate(FactCandidateInputs, FactRegistry)`。fixture必须先用真实canonical stores安装ApplicationDiscovery与完整ProgramGraphs publication，再由`PersistedFactCandidateInputReader`重开输入。首个two-entry/two-boundary golden证明只产生两条各自闭合的boundary candidate，不允许四条cross-product；另一个含`if (status == null) return`的冻结 fixture 必须产生恰一条同entry guard candidate 和恰一项`CONTROL_CONDITION` required atom，即使该 guard 后没有 boundary invocation。删除 branch edge、guard owner或guard Evidence 只令对应 guard candidate `NOT_APPLICABLE`，不影响其他 entry/guard；删除Proof不得改变M1 candidate bytes/counts。
- **Luna/xhigh 测试指南**：创建或扩展`FactCandidateEnumeratorTest`，冻结完整ProgramGraphs v3/v2 artifacts与手写candidate golden。逐RED：two-entry/two-boundary精确join、单 guard 的独立candidate、同一guard TRUE/FALSE不重复、缺branch/owner/evidence的NOT_APPLICABLE、外部effect不进入Fact、Mapper/HTTP同generic kind、ambiguous Graph Gap、旧ProgramGraphs或v1 Fact registry拒绝、registry atom顺序和input order determinism；registry/canonical store不可mock，不能用缩减graph-index JSON代替真实reopen。
- **Terra/xhigh 实现指南**：RED后只改 `org.sourceanalysis.app.analysis.fact.candidates`（路径为`analysis/fact/candidates/`），实现 v2 `PersistedFactCandidateInputReader`、public `FactCandidateInputs`、`FactRegistry.standardJavaFacts()`、`FactCandidateEnumerator/FactCandidateSet` 与 `proven-code-facts-fact-candidate-set-v2`；只读ApplicationDiscovery与完整ProgramGraphs publication，fresh-reopen→exact boundary/guard join→template match→applicable/not-applicable denominator。逐RED GREEN，Proof结果不得反向影响枚举；禁止fixture硬编码/改required atoms。上游或registry语义不足MUST STOP交Sol/ultra，完成更新审计。

#### M2 AtomicProofBuilder

- **解决的问题**：逐 atom 证明 source bytes 与 semantic graph/rule path都闭合，并执行 composite all-or-nothing admission。
- **精确上游输入及前置**：M1 candidate artifact、由`PersistedFactCandidateInputReader`fresh-reopened的完整ProgramGraphs five graphs/evidence、一个显式`VerifiedSourceInventoryReference`、只读`VerifiedSourceTextReader`及版本化Proof rule registry/budget；candidate IDs、graph endpoints、source roots、snapshot和controls完全一致。`VerifiedSourceTextReader.reopen(source)`返回的文本集必须与candidate input的snapshot/controls一致；每个`SourceExcerptV1.locator.fileId/path`必须命中唯一文本文件，文件的已验证SHA和`[startByte,endByteExclusive)`字节切片必须分别等于excerpt的文件与摘要/字节；这两个byte offset必须是UTF-8 code-point边界，按同一字节流重新计出的one-based start/end line/column也必须与locator相同，不得trim、normalize或附近搜索。任一不符是fatal `PROOF_SOURCE_REOPEN_MISMATCH`，不是可用别处Evidence补齐的Gap。
- **唯一公共seam**：`AtomicProofBuilder(VerifiedSourceTextReader sourceReader)`；其唯一业务方法是`ProofDecisionSet prove(FactCandidateSet candidates, FactCandidateInputs inputs, VerifiedSourceInventoryReference source, ProofRuleRegistry rules)`。builder不接收Path、原始JSON、图draft、任意source bytes或调用者组装的Evidence；source reader仅经构造器注入，测试也必须使用真实frozen text set。M2完成时另由自己的module publisher安装`proof-decision-set.json`，但该publisher不替代本seam的逐atom证明。
- **M2 module落盘seam**：`ProofDecisionSetModulePublisher(CanonicalModuleArtifactStore store)`的唯一业务方法为`publish(AnalysisStepModuleAddress destination, FactCandidateInputs inputs, ModulePublicationReference candidatePublication, ProofDecisionSet decisions)`。它只接受`PROVEN_CODE_FACTS/01-candidates`的已安装M1 publication；fresh reopen后必须恰得到一份`fact-candidate-set.json`，并验证该payload的candidateSetId与`decisions.candidateSetId`相同。M2 envelope的直接upstream是该M1 candidate payload、`FactCandidateInputs`保留的`source-inventory.jsonl` reference与`verified-snapshot.json` reference；M1 receipt再闭合到它的完整ApplicationDiscovery与五图输入。这样M2不靠进程内对象、也不在M2中重复列写同一张图的所有artifact。destination只能是`PROVEN_CODE_FACTS/02-proofs`，并且安装后必须fresh reopen。下游唯一的typed读入口是`PersistedProofDecisionSetReader(CanonicalModuleArtifactStore store).reopen(ModulePublicationReference publication, FactCandidateInputs inputs, ModulePublicationReference candidatePublication, FactCandidateSet candidateSet)`：它重开M2 receipt/envelope/body及M1 candidate publication，要求M2地址、三条direct upstream、candidateSetId、每个Fact/atom/Proof/disposition/Gap分母和canonical排序均闭合；禁止M3接收调用方手写的`ProofDecisionSet`。
- **atom→证据subject规则（固定）**：`ProofRuleRegistry`的`proven-code-facts-proof-rules-v2`对generic `JAVA_BOUNDARY_INVOCATION`精确规定：`INVOCATION_CALL_ID`需要call-site与boundary node；三个`STATIC_TARGET_*` atom各需要call-target edge；`ORDERED_ARGUMENTS`需要每条ordered argument edge；`JAVA_LOCAL_ORIGINS`需要每个ordered argument的每个local-origin node；`CONTROL_CONTEXT`需要basic block及有guard时的guard node；`INVOCATION_EVIDENCE`需要以上subject的并集。对 `JAVA_GUARD_CONDITION`，唯一 required atom 是 `CONTROL_CONDITION`，它只需要 guard node。每个required subject都必须在Evidence graph中存在同一subject的**一条**source-excerpt→rule-application edge，rule application的`inputProgramElementIds`必须包含subject，且其`ruleId/ruleVersion`必须是下表的精确值。一个subject可以保留多条历史来源边；M2选择一个允许的精确配对，保留它的两个Evidence node ID，不能要求该subject的所有历史rule都属于本atom，也不能把不同Evidence edge的source/rule交叉配对。M1没有收集到任一允许pair时，M2不得猜测或临时重解析源码，只为相应atom生成rejection/Gap；复合Fact仍按全有或全无处理。

| subject类别 | 允许的`ruleId` | 精确`ruleVersion` |
| --- | --- | --- |
| call-site、call-target | `java-static-field-receiver-call-v1` | `v1` |
| boundary invocation | `java-boundary-invocation-v1` | `v1` |
| argument-to-boundary edge | `java-boundary-argument-v1` | `v1` |
| Java local origin | `source-element-parser-v1`、`java-argument-binding-v1`、`java-single-reaching-definition-v1`、`java-direct-local-assignment-v1`、`java-direct-field-assignment-v1`、`java-direct-setter-property-v1`、`java-local-read-v1`、`java-parameter-symbol-v1` | `v1` |
| basic block | `control-flow-method-body-v1` | `v1` |
| guard（`JAVA_BOUNDARY_INVOCATION` 的 CONTROL_CONTEXT） | `control-flow-if-guard-v1` | `v1` |
| guard（`JAVA_GUARD_CONDITION` 的 CONTROL_CONDITION） | `control-flow-if-guard-v1` | `v1` |

`source-element-parser-v1`只允许用于Java-local-origin node本身：它证明该已由M4绑定的参数/局部/字段节点来自冻结源码字节；它不单独证明参数到boundary的关系。该关系仍由同一atom所需的`ARGUMENT_TO_BOUNDARY` subject及其`java-boundary-argument-v1` Evidence pair闭合。没有列在表中的rule不是“未来自动兼容”项；它使对应atom形成`REJECTED_WITH_REASON`及具体Gap，直到经过本步骤设计修订。对于 `JAVA_GUARD_CONDITION`，M2 只使用 control-flow v2 public node 的`normalizedCondition`、该 guard node 和它自己的 source-excerpt/rule pair；不得解析`canonicalValue`、从 branch target、boundary invocation 或数据流边补充条件含义。`ProofDecisionSet`按candidate key、registry atom order产生一条atom disposition；只有所有atom都CLOSED才产生一个admitted `CodeFact`及同数`atomProofs`。任一atom未闭合时，Fact和所有sibling atom disposition一律`REJECTED_WITH_REASON`，直接失败atom保存自己的root cause，其余sibling保存`COMPOSITE_FACT_REJECTED`；不得留下可被后续步骤误当成独立事实的partial Proof。
- **确定性顺序 / LLM**：按`candidateDenominatorKey`→registry atom order → 解析required graph/evidence roles → 重开span/hash → 建无环proof closure → 先得atom dispositions → 再按all-atoms rule得Fact disposition；0 LLM。boundary 的 `candidateDenominatorKey = entryId + "|" + boundaryNodeId + "|" + candidateFactKey`；guard 的 `candidateDenominatorKey = entryId + "|" + guardNodeId + "|JAVA_GUARD_CONDITION"`。模板名不能代替这个实例键。
- **目标输出与 DepotHead 示例**：`ProofDecisionSet{proofs,codeFacts,factDispositions,atomDispositions,rootCauseRejections,externalEffectGaps}`；boundary atoms全闭合时可admit invocation Fact，但每个 admitted 或 rejected `JAVA_BOUNDARY_INVOCATION` candidate 仍恰有一条`ExternalEffectGap{gapId,candidateDenominatorKey,entryId,boundaryNodeId,staticTargetType,staticTargetMethod,staticTargetSignature,code=DATA_FLOW_BINDING_UNPROVEN,basisEvidenceNodeIds}`。一个 admitted `JAVA_GUARD_CONDITION` 则只产生 `CONTROL_CONDITION` atom/proof，不产生外部效果 Gap。两者都不声称数据库更新、SQL筛选、消息发送或任何具体外部结果。
- **必须保持的不变量**：一个 admitted atom 恰一个 CLOSED Proof；Fact 任一 required atom失败则无 admitted sibling；Proof refs 只指 M1/ProgramGraphs/VerifiedSourceInventory identities。
- **Gap / fatal / artifact复用**：可解释的不闭合是 rejection/Gap；source/edge/reference drift、proof status伪 CLOSED、conflicting admitted facts fatal；M2从完整candidate set构建全部decisions，不混用其他publication的proof。
- **给下游的后置保证**：M3 获得每个 candidate/atom 的唯一 disposition、closed proofs 或具体根因，能直接守恒计数。
- **明确非目标**：不把 evidence locator、Trace、模型或文本相似当 Proof，不生成 Flow。
- **公共测试 seam 与验收**：`prove(candidateSet, graphs, source, rules)`对每个boundary atom逐段deletion、source/rule mutation、XML/SQL伪支持、conflict和正向closure；另对一个 guard candidate 删除 guard span、rule pair 或对应 branch edge，必须拒绝该 guard Fact，且不得影响其他条件或 boundary Fact。任一缺项不admit invocation Fact，任何完整invocation也不关闭external-effect Gap。
- **Luna/xhigh 测试指南**：创建或扩展`AtomicProofBuilderTest`，fixtures/goldens放`src/test/resources/analysis/fact/proofs/`。一个行为一个RED：全部boundary atoms闭合正例、一个 guard 的独立`CONTROL_CONDITION`正例、每段deletion rejection、external-effect非准入、source/rule drift fatal、sibling all-reject、conflicting facts、root replay；expected Proof paths独立手写。只fake source reopen，禁止mock graph traversal/Proof/canonical；无网络/模型。
- **Terra/xhigh 实现指南**：RED后仅拥有 `org.sourceanalysis.app.analysis.fact.proofs`（路径为`analysis/fact/proofs/`），实现 public `AtomicProofBuilder/ProofDecisionSet` 与 `proven-code-facts-proof-decision-set-v2`；M1+ProgramGraphs+source→role match→reopen→Proof→atom→composite decision。逐atom GREEN再composite GREEN；不得借Evidence/模型/Trace或保留sibling。需改Fact/graph跨analysis step contract则MUST STOP并交用户流程，审计同步。

#### M3 FactLedgerPublicationSpecifier

- **解决的问题**：把 Proof decisions、Gaps 与分母汇成四个semantic analysis step payload并验证没有 orphan/silent loss；root与receipt只由analysis step store产生。
- **精确上游输入及前置**：M1 candidate artifact、M2 proof decision artifact、capability/expectation gaps、VerifiedSourceInventory、ApplicationDiscovery与ProgramGraphs roots、ProvenCodeFacts controls；所有 IDs/references 局部有效。
- **确定性顺序 / LLM**：归类 CapabilityGap/FactRejection/ExpectationGap → 计算 Fact/atom equations → conflict/reference validation → canonical四个semantic payload → M3 install/receipt → analysis step store fresh reopen/root/receipt-last；0 LLM。
- **目标输出与 DepotHead 示例**：M3恰四个semantic files；analysis step store形成四项+receipt的五文件reader-visible set。历史回归例要求`proven-facts.json`保存rejected disposition、`gap-ledger.json`保存missing value/where requirements，`fact-accounting.json`记录`admittedFactCount=0`；analysis step receipt只绑定四项descriptor、status、controls与Gap refs/count。
- **必须保持的不变量**：每 candidate/atom 恰一 disposition；Gap 有 affected IDs/missing/impact/closure；semantic files引用同一 M1/M2 roots；M3不含或预报analysis step root/receipt且不改 decision。
- **Gap / fatal / artifact复用**：合法 rejection/Gaps 可 SUCCEEDED_WITH_GAPS；orphan/double disposition、accounting/reference/canonical/install/collision 错误 fatal；下游只认完整receipt。
- **给下游的后置保证**：BusinessFlows 能只读 Facts/Proof/Gaps/accounting，并确定哪些 atom可进入 Flow、哪些必须留 Gap。
- **明确非目标**：不重新证明、不把 rejection 升级、不调用模型、不选择 Flow ownership。
- **唯一公共seam 与验收**：`FactLedgerPublicationSpecifier(CanonicalModuleArtifactStore, CanonicalAnalysisStepArtifactStore).specifyCandidatesAndProofs(FactCandidateInputs inputs, ModulePublicationReference candidatePublication, ModulePublicationReference proofPublication, VerifiedSourceInventoryReference source, ApplicationDiscoveryReference discovery, ProgramGraphsReference graphs)`。它先由M1/M2 typed readers重开并验证candidate/decision，随后重开三个analysis-step predecessor；调用方不能传入手写`ProofDecisionSet`、Gap、JSON或Path。它覆盖orphan/double owner/count-equal-but-ID-different、乱序和partial-install；只有M3 exact-four、analysis-step-store exact-five及ID-set equations全闭合才返回 `ProvenCodeFactsReference`。
- **Luna/xhigh 测试指南**：创建或扩展 `ProvenCodeFactsPublicationSpecifierTest`，module artifacts/goldens在 `src/test/resources/analysis/fact/publish/`。RED顺序：M3 exact-four、analysis step exact-five、current DepotHead rejection、positive boundary+guard admitted、guard/boundary分区计数、每条boundary恰一external-effect Gap而 guard 为零、orphan/double disposition、count spoof/ID mismatch、v1 rejection、Gap closure、receipt-last/partial-install/collision；使用真实module/analysis step stores，不能mockaccounting/canonical/root。命令：`mvn -Dtest=ProvenCodeFactsPublicationSpecifierTest test`；禁网络/customer Maven。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `org.sourceanalysis.app.analysis.fact.publish`（路径为`analysis/fact/publish/`），实现 public `FactLedgerPublicationSpecifier/ProvenCodeFactsReference` 的 v2 schema writer/reader；只读M1/M2 artifacts，classify gaps→guard/boundary ID-set equations→four semantic files→M3 install/receipt→typed analysis-step-store receipt-last。不得生成第五个 public 文件、旧single publication summary、预报root/receipt、重新prove或升级rejection；v1 input/public schema必须fail closed。任何field/accounting跨analysis step改动MUST STOP交Sol/ultra/用户，完成更新审计。

### 8.0.1 模块 artifact wire schemas

M1/M2使用 DESIGN 13.3 `ModuleArtifact<T>` envelope；M3直接安装四个analysis step schema注册的semantic JSON bytes而无summary envelope。`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-candidates/fact-candidate-set.json` | `proven-code-facts-fact-candidate-set-v2` / `PROVEN_CODE_FACTS_FACT_CANDIDATE_SET` | exact ApplicationDiscovery `capability-report/entry-points`与完整ProgramGraphs五张public graph、graph-index、graph-gap ArtifactReferences；reader重开后建立不可替代的`FactCandidateInputs` | `candidateSetId!`、`sourceGraphRoots[]!`（五图payload root的UTF-8排序集合）、`candidates[]!`为 closed union：`JAVA_BOUNDARY_INVOCATION{candidateFactKey!,entryId!,boundaryNodeId!,invocationCallId!,callTargetEdgeId!,orderedArgumentEdgeIds[]!,controlBlockId!,guardId?（required nullable）,evidenceNodeIdsBySubject[]!,requiredAtoms[]!}` 或 `JAVA_GUARD_CONDITION{candidateFactKey=JAVA_GUARD_CONDITION!,entryId!,guardNodeId!,normalizedCondition!,branchEdgeIds[]!（恰TRUE/FALSE所属边）,evidenceNodeIdsBySubject[]!,requiredAtoms=[CONTROL_CONDITION]!}`；`notApplicableDispositions[]!{entryId!,subjectNodeId!,templateKey!,missingRoles[]!,reasonCode!}`、`denominator!{applicableKeys[]!,notApplicableKeys[]!}`；dispositions按entry/template/subject，atoms按registry order |
| `modules/02-proofs/proof-decision-set.json` | `proven-code-facts-proof-decision-set-v2` / `PROVEN_CODE_FACTS_PROOF_DECISION_SET` | direct：exact M1 `fact-candidate-set` payload + VerifiedSourceInventory `source-inventory/verified-snapshot`；M1 receipt transitive：ApplicationDiscovery和完整ProgramGraphs inputs | `candidateSetId!`、`codeFacts[]!{factId!,candidateDenominatorKey!,kind!,subjectNodeIds[]!,atoms[]!{atomId!,role!,name!,value!{type!,canonical!},proofId!}}`、`atomProofs[]!{proofId!,candidateDenominatorKey!,factId!,atomId!,rootEvidenceNodeId!,requiredEvidenceNodeIds[]!,requiredProgramEdgeIds[]!,ruleIds[]!,status=CLOSED!}`、`factDispositions[]!{candidateDenominatorKey!,disposition!,admittedFactId?,reasonCode?}`、`atomDispositions[]!{candidateDenominatorKey!,atomKey!,disposition!,proofId?,reasonCode?}`、`rootCauseRejections[]!{candidateDenominatorKey!,atomKey!,reasonCode!,gapId!}`、`externalEffectGaps[]!`仅对应每个boundary candidate，形状为`{gapId!,candidateDenominatorKey!,entryId!,boundaryNodeId!,staticTargetType!,staticTargetMethod!,staticTargetSignature!,code=DATA_FLOW_BINDING_UNPROVEN!,basisEvidenceNodeIds[]!}`；各数组按ID/key，Fact内atoms按registry order |
| `modules/03-publish/<four registered semantic filenames>` | 下表四个public schema/type；无summary envelope | exact M1 `fact-candidates`与M2 `proof-decisions` ArtifactReferences | 一次module install恰`proven-facts.json/proof-pack.json/gap-ledger.json/fact-accounting.json`；module receipt绑定四descriptors；禁止analysis step root/receipt或五项published list；AnalysisStep store provenance绑定M3 reference |

M3的完整module fixture必须用一次install request/receipt绑定表中M1/M2两个ArtifactReferences；四个standalone payload不得重复envelope。只给四份payload而省略该排序upstream集合，不是完整M3 fixture。

**M3 四个 standalone payload（v2，字段锁定）。** 每个文件均含`schemaVersion`、`artifactType`、`artifactId`和`candidateSetId`；`artifactId`由去除`artifactId`后的canonical UTF-8 bytes、schema/type和固定artifact domain计算。四份文件都保存同一个`proofDecisionSetRef{artifactId,sha256}`，并且该reference必须是M2 module的唯一payload；它不把M2的module envelope嵌套进正文。v1 仅含 boundary candidate 的文件不是 v2 reader 的输入，必须以稳定 wire/schema 错误拒绝；这不是兼容升级。

| 文件 | `artifactType` / `schemaVersion` | 除共同字段外的精确正文 | 下游保证 |
| --- | --- | --- | --- |
| `proven-facts.json` | `PROVEN_CODE_FACTS_PROVEN_FACTS` / `proven-code-facts-proven-facts-v2` | `codeFacts[]`（M2 `CodeFact`完整wire）、`factDispositions[]`（M2完整wire） | 一个candidate恰一Fact disposition；只有`ADMITTED`才在`codeFacts[]`出现，且`admittedFactId`命中唯一Fact。 |
| `proof-pack.json` | `PROVEN_CODE_FACTS_PROOF_PACK` / `proven-code-facts-proof-pack-v2` | `atomProofs[]`（M2 `AtomProof`完整wire）、`atomDispositions[]`（M2完整wire）、`rootCauseRejections[]`（M2完整wire） | 每个candidate-required atom恰一atom disposition；`CLOSED`只引用本文件唯一CLOSED Proof；rejection的根因不会静默丢失。 |
| `gap-ledger.json` | `PROVEN_CODE_FACTS_GAP_LEDGER` / `proven-code-facts-gap-ledger-v2` | `gaps[]!{gapId,kind,code,affectedEntryIds[],affectedCandidateDenominatorKeys[],evidenceNodeIds[],missingRequirement,impact,closureRequirement}` | 每个boundary candidate有一项外部效果 Gap；每个 Fact rejection 根因也逐条投影。所有数组UTF-8排序且无重复；不写外部系统效果。 |
| `fact-accounting.json` | `PROVEN_CODE_FACTS_FACT_ACCOUNTING` / `proven-code-facts-fact-accounting-v2` | `candidateDenominatorKeys[]`、`admittedFactIds[]`、`rejectedCandidateDenominatorKeys[]`、`admittedAtomDispositionKeys[]`、`rejectedAtomDispositionKeys[]`、`externalEffectGapIds[]`、`candidateFactCount`、`admittedFactCount`、`rejectedFactCount`、`candidateAtomCount`、`admittedAtomDispositionCount`、`rejectedAtomCount`、`provenFactAtomCount`、`externalEffectGapCount`、`boundaryCandidateDenominatorKeys[]`、`guardCandidateDenominatorKeys[]` | ID集合与count双重守恒：不能以相同计数掩盖不同ID；严格满足8.3方程及两种 candidate 分区。 |

`gap-ledger.json` 的固定投影规则如下：`DATA_FLOW_BINDING_UNPROVEN`生成`kind=EXTERNAL_EFFECT`，affected entry/candidate/evidence直接取M2 `ExternalEffectGap`，`missingRequirement=APPROVED_EXTERNAL_SEMANTICS_ADAPTER_OR_PROVEN_RETURN_CHAIN`，`impact=DO_NOT_DESCRIBE_JAVA_BOUNDARY_AS_EXTERNAL_EFFECT`，`closureRequirement=APPROVED_EXTERNAL_SYSTEM_ANALYSIS_STEP`；每个`RootCauseRejection`生成`kind=FACT_REJECTION`，affected candidate从同M1 candidate取得entry和Evidence，`missingRequirement`与`closureRequirement`均为该rejection的`reasonCode`，`impact=FACT_NOT_ADMITTED`。任何未知Gap code或找不到candidate/Evidence是fatal `FACT_ACCOUNTING_INVARIANT_BROKEN`，不得以自由文本补齐。

guard condition 改变了候选闭集、Proof 选择和已发布的 Fact/atom 分母，因此 M1/M2 module schema 与 M3 四个 public schema 同步升为 v2；文件数量、文件名、artifactType、Step04 位置和 downstream output count 不变。Fact/Gap profile必须发布包含`JAVA_BOUNDARY_INVOCATION`、`JAVA_GUARD_CONDITION`与external-effect Gap的新版 content-addressed ref。M1/M2 readers必须exact接受完整ProgramGraphs public set所发布的control-flow v2、data-flow v2、evidence v3、index v2以及其余两张public graph/Gap集合，拒绝旧集合、draft与任何新旧混搭；不能通过重开XML/SQL补成 effect 或 condition。

atom value使用8.1 typed union；静态unknown不允许story value，必须使atom disposition=`REJECTED_WITH_REASON`并引用Gap。一个candidate admitted时其requiredAtoms与atomProofs一一对应；rejected candidate的`admittedFactId` required nullable为null。success envelope failureRef=null；integrity fatal写ModuleFailure。field/atom registry/source rule/sort/identity变化先设计并升version。

**示例分类：STRUCTURAL_WIRE_SPECIMEN（isolated candidate semantics）。** 会把静态XML/SQL结构误写成boundary side effect的旧正例已删除。M1/M2 wire字段形状仍以上表为准；v2 Fact registry只能从ProgramGraphs v3/v2枚举`JAVA_BOUNDARY_INVOCATION`和`JAVA_GUARD_CONDITION`，并只把前者的外部effect作为独立Gap。

### 8.0.2 已批准的最小后继合同：`JAVA_EXACT_CALL`（v3）

本小节冻结 eventual cross-Flow compiler 所需的最小上游增量；它不把当前v2的boundary/guard纵切改写成“已实现”。v3在同一四个公开文件内增加一个且仅一个Fact family：`JAVA_EXACT_CALL`。它只证明“某entry拥有的Java call-site经已持久化EXACT call edge指向同一ProgramGraphs publication中的`METHOD`”，不证明target有具体body、调用成功、外部效果、业务顺序或domain specificity。固定jshERP没有用户提供的业务表映射；即使Java→Mapper→XML→SQL静态引用分别闭合，也只形成generic/pending结构材料，不能证明业务对象或形成`SHARED_ANCHOR`。Step 05完整出口不要求`DOMAIN_SPECIFIC`；业务含义只能由Step 06冻结的R0/R1/R2与P1/P2解释。interface/abstract method只要是冻结CodeStructure中的`METHOD`且call edge为EXACT，仍属于本family；unresolved/ambiguous call没有EXACT `CALL_TARGET`，继续只由Step 03 Graph Gap表达。

**M1 exact join及wire。** `FactCandidateEnumerator`对每个Call graph `CALL_SITE` node，按其`owningEntryIds`与ApplicationDiscovery/五图共同entry分母的交集逐entry枚举；每个实例必须命中恰一条`kind=CALL_TARGET,resolution=EXACT,ruleId=java-static-field-receiver-call-v1`且`fromNodeId=callSiteNodeId`的edge，并且`toNodeId`命中CodeStructure `kind=METHOD` node。METHOD `canonicalValue`必须恰为`<staticTargetType>#<staticTargetMethod>(<parameter-types>)`：取第一个`#`前的完整串为type、`#`后第一个`(`前的完整串为method、`#`后至末尾（含括号）为signature；不trim、不case-fold、不按simple name匹配。candidate denominator key固定为`entryId + "|" + callTargetEdgeId + "|JAVA_EXACT_CALL"`。同一call-site合法被多个entry拥有时逐entry产生独立candidate，绝不能要求`owningEntryIds=[entryId]`。

v3 M1 closed-union新增以下variant，其他两个v2 variant逐字段保持：

~~~text
JAVA_EXACT_CALL {
  candidateFactKey=JAVA_EXACT_CALL
  entryId
  callSiteNodeId
  callTargetEdgeId
  targetMethodNodeId
  targetCanonicalMethod
  evidenceNodeIdsBySubject[]
  requiredAtoms=[
    INVOCATION_CALL_ID       {role=RELATIONSHIP,valueType=SYMBOL_REF},
    STATIC_TARGET_TYPE       {role=ATTRIBUTE,valueType=STRING},
    STATIC_TARGET_METHOD     {role=ATTRIBUTE,valueType=STRING},
    STATIC_TARGET_SIGNATURE  {role=ATTRIBUTE,valueType=STRING}
  ]
}
~~~

call edge引用不存在的endpoint仍是fresh-reopen reference fatal `PROOF_PACK_REFERENCE_BROKEN`；endpoint存在但不是`METHOD`时，这个entry-edge-template只写`NOT_APPLICABLE/JAVA_EXACT_CALL_TARGET_NOT_METHOD`。不存在EXACT edge时不制造Fact candidate；对应Step 03 unresolved/ambiguous Gap不得被M1猜回。candidate的存在也不依赖DataFlow是否另外投影了`JAVA_BOUNDARY_INVOCATION`，所以冻结interface boundary可同时有两个不同Fact candidate；下游去重优先级由Step 05 §8.1.2固定，而不是M1静默删分母。

**M1/M2 exact分母闭包裁决。** 为保持§8.0在查看Proof前冻结且不得缩小的分母，通过上游endpoint/reference校验后，对每个owner-entry × qualifying exact `CALL_TARGET` edge，M1恰写一条candidate或`NOT_APPLICABLE` disposition，key仍为`entryId + "|" + callTargetEdgeId + "|JAVA_EXACT_CALL"`。M1先校验present `METHOD`的canonical grammar：失败时只写`NOT_APPLICABLE/REQUIRED_ATOM_MISSING`且`missingRoles`恰为`[TARGET_METHOD_CANONICAL]`；canonical合法后，若call-site、call-target edge或target METHOD的generic source-excerpt→rule-application闭包缺失，则写`NOT_APPLICABLE/PROOF_NOT_CLOSED`，`missingRoles`恰为`[CALL_SITE_EVIDENCE, CALL_TARGET_EDGE_EVIDENCE, TARGET_METHOD_EVIDENCE]`中对应缺失闭包的非空有序子序列，并保持该固定顺序。只有canonical及三项generic闭包都存在时M1才产生candidate；其后M2缺任一允许的exact rule pair时必须保留该candidate并按all-or-nothing拒绝整个Fact，直接失败atom用`PROOF_NOT_CLOSED`、siblings用`COMPOSITE_FACT_REJECTED`；M1/M2 publisher、reader、M3 accounting及Luna/Terra测试均消费这一裁决，它不改变status、schema、API、model boundary、四文件/五项reader-visible output count或既定v3替换/拒绝旧版行为，也不引入compatibility reader、dual registration或迁移路径。

**M2 atom、Proof和Fact。** `AtomicProofBuilder`令admitted Fact的`subjectNodeIds`恰为UTF-8排序的`[callSiteNodeId,targetMethodNodeId]`，四个atom value依次是call-site ID及上段从METHOD canonical value切出的三个串。`INVOCATION_CALL_ID` Proof必须同时闭合call-site的source-excerpt→`java-static-field-receiver-call-v1/v1` pair和该call-target edge的同rule pair，并把`callTargetEdgeId`列入`requiredProgramEdgeIds`；三个`STATIC_TARGET_*` Proof各自必须同时闭合该edge的`java-static-field-receiver-call-v1/v1` pair、target METHOD node的source-excerpt→`source-element-parser-v1/v1` pair，并把同一edge列入`requiredProgramEdgeIds`。任一pair/endpoint/value不等按既有all-or-nothing规则拒绝整个Fact；不得重解析源码或从boundary typed fields补target METHOD Proof。这个Fact不产生`ExternalEffectGap`，也不能关闭boundary已有的`DATA_FLOW_BINDING_UNPROVEN`。

**版本与守恒级联（没有占位版本）。** Fact registry与Proof rule registry升为`proven-code-facts-fact-registry-v3`、`proven-code-facts-proof-rules-v3`；M1/M2 payload升为`proven-code-facts-fact-candidate-set-v3`、`proven-code-facts-proof-decision-set-v3`；四个M3 public schema分别升为`proven-code-facts-proven-facts-v3`、`proven-code-facts-proof-pack-v3`、`proven-code-facts-gap-ledger-v3`、`proven-code-facts-fact-accounting-v3`。`fact-accounting.json`新增`exactCallCandidateDenominatorKeys[]`，并把candidate分区方程固定为`candidateDenominatorKeys = disjointUnion(boundaryCandidateDenominatorKeys,guardCandidateDenominatorKeys,exactCallCandidateDenominatorKeys)`；其他count/ID方程和四文件/五项reader-visible数量不变。ProgramGraphs schema不变，因为上述字段、edge、METHOD及Evidence已经持久化。协调cutover之前，当前v2 registration/reader继续完成当前工作；cutover时必须在同一work unit以v3 registration/reader**替换**v2并迁移全部依赖fixture，v3只接受完整v3 Step 04 set。禁止dual registration、compatibility reader、v2/v3混搭或保留可执行旧schema测试路径；历史v2 bytes由v3明确拒绝，不能原地重解释。

**Luna RED / Terra GREEN交接。** Luna在`FactCandidateEnumeratorTest`与`AtomicProofBuilderTest`各增加一个public stored-artifact seam：(1) 两entry fixture中caller的共享service call-site同时可达callee entry target，断言按call-site owner产生准确`JAVA_EXACT_CALL`分母、四atom及edge+METHOD Proof；(2) 删除target METHOD Evidence pair时只拒绝该Fact，改target kind时得到typed NOT_APPLICABLE，unresolved/ambiguous call只保留Step 03 Gap。Terra只修改`analysis.fact.candidates`、`analysis.fact.proofs`和`analysis.fact.publish`及其v3 schema/registry安装；不得改Step 03、重跑parser、添加effect atom或借名称分类domain。cutover前的boundary/guard行为断言必须在迁移后的v3 fixture中继续成立；旧v2 schema fixture不得为了保留第二条执行路径而长期留在suite中。

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
  candidateDenominatorKey
  disposition
  admittedFactId?                       // required nullable
  reasonCode?                            // required nullable

AtomDisposition
  candidateDenominatorKey
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

`ExternalEffectGap`是上面`ProofDecisionSet.externalEffectGaps`的窄类型，不用通用prose补全：它的basis只能来自该candidate已绑定的Evidence node IDs，`missingRequirement`固定为“已批准的外部系统语义adapter或同一仓库内可证明返回链”，`impact`固定为“不把Java边界调用写成外部效果”，`closureRequirement`固定为“另一个经过设计批准的外部系统分析步骤”。

Atom value type 只允许 STRING、INTEGER、DECIMAL、BOOLEAN、SYMBOL_REF 或 ENUM_REF。任意 prose 不进入 atom。

### 8.2 Proof 与 identity

无环顺序：

1. graph/evidence node/edge IDs 已由 分析步骤“程序图” 固定；
2. atomId 依赖 snapshot、candidateFactKey、entry owner、subject node、role/name/value；`CONTROL_CONDITION`因此不可能被另一个 entry 或另一 guard 重用；
3. factId 依赖 snapshot、kind、subject nodes 和排序 atoms，不含 proofId；
4. proofId 依赖 factId、atomId、Evidence/program-edge closure 和 rule IDs；
5. proofPackId、provenFactSetId、gapLedgerId、accountingId 最后计算。

Proof 必须同时证明 source bytes 和 semantic rule path。file/span hash 正确只证明完整性，不自动证明语义。

### 8.3 Accounting

~~~text
candidateFactCount = admittedFactCount + rejectedFactCount
candidateAtomCount = admittedAtomDispositionCount + rejectedAtomCount
provenFactAtomCount = admittedAtomDispositionCount
boundaryCandidateCount = count(boundaryCandidateDenominatorKeys)
guardCandidateCount = count(guardCandidateDenominatorKeys)
candidateFactCount = boundaryCandidateCount + guardCandidateCount
externalEffectGapCount = boundaryCandidateCount
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

FACT_PROFILE_INVALID、FACT_KIND_UNSUPPORTED、REQUIRED_ATOM_MISSING、GUARD_CONDITION_UNPROVEN、DATA_FLOW_BINDING_UNPROVEN、COMPOSITE_FACT_REJECTED、PROOF_PACK_REFERENCE_BROKEN、PROOF_PROGRAM_EDGE_BROKEN、PROOF_SOURCE_REOPEN_MISMATCH、PROOF_NOT_CLOSED、CONFLICTING_FACTS、FACT_ACCOUNTING_INVARIANT_BROKEN、PROVEN_CODE_FACTS_RESOURCE_LIMIT_EXCEEDED。

### 8.6 测试 seam 与验收

- 先枚举denominator，再mutation route、Service call、setter/property、boundary call ID/target triple/argument/origin/control/evidence或独立guard node/branch edge/evidence任一段；XML table/column/where不得成为external-effect Proof，也不得成为 guard condition Proof。
- 删除 Fact 自己引用的 span/edge 必须 rejection；另一个 Fact 的 Evidence 不能补。
- 同名 decoy、注释字符串和目标 JSON 不得形成 Proof。
- Proof source span、graph endpoint、rule ID、fact/atom reference mutation fatal。
- conflicting canonical Fact 双方都不 admitted。
- 等价 root/input order 产生相同 records/IDs/canonical bytes。
- fixed DepotHead slice在generic boundary能力补齐前继续输出Gap/rejection；补齐后只可positive-admit invocation，不得硬编码external update positive golden。

验收要求同时有：完整composite Fact正例、每个required atom的独立deletion/mutation反例、conflicting Fact、不同root fresh-reopen，以及从历史审计提炼的DepotHead rejection baseline。只有positive case全atoms CLOSED才admitted，任一反例都不会留下sibling admitted atom，五个artifacts可独立重验，分析步骤“已证明代码事实”才算可交付。

### 8.7 已冻结裁决：实现者不得自由推断

- candidate Fact/required atoms 来自版本化 registry，并在证明前固定；实现者不能为某 fixture 临时删 atom 或新增硬编码 Fact。
- 每个可达 guard 是独立 candidate；`CONTROL_CONTEXT` 不能作为 `CONTROL_CONDITION` 的同义词或替代品。`BranchDecision.conditionAtomId` 只能指向后者的 CLOSED Proof。
- 复合 Fact 是全有或全无：任一 required atom 不闭合，整条 Fact 及 sibling dispositions 都 rejected。
- span hash、graph edge 和 rule path 三者共同构成 Proof；locator、Trace、模型一致或字符串相似都不能替代。
- Gap 必须有 missing requirement、impact 和 closure requirement；不能把 Gap 写成负面业务事实或空 placeholder。
- conflicting Facts、accounting、reference 或 source-integrity 问题是 fatal，不能降为“低置信度”Fact。
- Proof 内部数据结构和 rule engine 实现可自行选择；Fact registry/version、atom roles、identity 顺序、admission 与失效规则不得改变。

## 9. 当前实现成熟度审计

以下是正式实现分支已由定向测试验证的当前事实；它不把候选清单外推成已证明Fact，也不把synthetic fixture外推成jshERP运行结果。M1/M2/M3的 v2 guard-condition 合同已交付；这仍不是完整jshERP业务事实验收。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 目标package与JDK 17 Toolchain已就位；通用wire头门禁不理解Fact、Proof或Evidence语义。 |
| **已实现（v2 M1 候选清单）** | `PersistedFactCandidateInputReader`从同一冻结依据重新打开应用发现与完整五图，校验五图、graph index、应用画像和源码清单之间的身份闭合；`FactCandidateEnumerator`为证据、调用、参数、控制路径均精确闭合的Java边界调用建立候选，并为每个可达且证据闭合的`if` guard建立独立`JAVA_GUARD_CONDITION`候选。guard仅保存控制图v2的`normalizedCondition`、自身TRUE/FALSE branch和自身Evidence；不闭合组合保留`NOT_APPLICABLE`。`FactCandidateSetModulePublisher`安装唯一v2 `fact-candidate-set.json`与七项上游引用；独立reader重开、重枚举并拒绝内容漂移。 |
| **已实现（v2 M2 原子证明）** | `AtomicProofBuilder`只从已保存M1输入、Evidence和冻结源码构造逐atom CLOSED Proof；它逐字节重验span/hash、选择同一Evidence边上的允许source/rule pair，并在任一atom失败时拒绝整个复合Fact。boundary继续留下不声称外部效果的`DATA_FLOW_BINDING_UNPROVEN` Gap；独立guard只证明一个`CONTROL_CONDITION` atom，绝不产生外部效果Gap。`ProofDecisionSetModulePublisher`与`PersistedProofDecisionSetReader`将M1 candidate、source inventory、snapshot和M2 decision严格重开闭合；源码字节漂移会fatal。 |
| **已实现（v2 M3 事实账本）** | `FactLedgerPublicationSpecifier`只消费重开的M1/M2与三个已保存analysis-step predecessor，生成并原子安装v2 `proven-facts.json`、`proof-pack.json`、`gap-ledger.json`、`fact-accounting.json`及receipt。账本分别保存boundary与guard候选分母，并强制`externalEffectGapCount = boundaryCandidateCount`：guard绝不会被记成外部效果Gap。正向fixture验证两条内部Java invocation Fact、一个guard Fact和两条外部效果Gap可共存；反向样例证明每条rejected boundary Fact同时保留根因Gap与外部效果Gap。 |
| **历史证据，不是当前能力** | 已删除的pre-reset代码曾验证有限profile的Fact/Proof/Gap/accounting；旧POC五个人工LockedFact独立审计仅两条成立。它们说明“hash闭合不等于语义证据闭合”，不能复制为当前Fact。 |
| **下一实现门** | 进入分析步骤“业务流程”。后者只能重新打开 v2 四个semantic files与五图，以每个入口为根编译Flow与Evidence Capsule；不能重新扫描源码、重建Fact或把本步骤external-effect Gap升级成SQL/消息/API效果。 |

分析步骤“已证明代码事实”的成功允许有Gap；但只有当前新实现 admitted-with-Proof 的Fact才能进入分析步骤“业务流程”。
