# 仓库知识

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。运行顺序只由文件名中的 `07-` 与运行目录 `steps/07-repository-knowledge/` 表达。

本文示例严格使用 DESIGN §1.3 的 `NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN` 分类。`NARRATIVE_ILLUSTRATION` 只帮助理解，允许省略字段，**不是 schema-valid wire record、identity preimage 或 replay golden**；字段表、闭集、nullable 规则、排序和 identity 公式本身是 exact 合同，但不是第四种示例标签。没有标为 `STRICT_REPLAY_GOLDEN` 的 ID、digest 或 size 不得复制为 golden。

## 1. 为什么存在

模型提案不自动成为业务知识。即使两个 Flow 都选择“单据”这个词，它们也可能指不同 Java type、SQL table 或请求对象；反过来，同一个 `jsh_depot_head` 技术锚点也可能被不同流程用不同措辞描述。

分析步骤“仓库知识” 是 分析步骤“业务流程” 全部 Flow 与单一仓库知识之间的程序化 seam。它必须为 **分析步骤“业务流程” 的每一个 `flowSliceId` 写恰一个 `FlowAdmissionDecisionV1`**：model-eligible Flow 重验 分析步骤“流程解释” disposition/candidate；model-ineligible Flow 没有 分析步骤“流程解释” disposition，直接使用带 分析步骤“业务流程” ineligibility Gap 的技术回退。随后程序按 proven technical anchor 合并 Facts、meanings、fallbacks、relations、metrics、conflicts 和 Gaps。无论仓库有多少 Flow，本分析步骤只产生**一份且仅一份 RepositoryKnowledge**；最终决定属于程序，不属于模型。

## 2. 具体输入与 DepotHead 证据边界

输入全部是已安装、可 fresh-reopen 的 canonical artifacts：

- VerifiedSourceInventory、ApplicationDiscovery、ProgramGraphs与ProvenCodeFacts的source scope、program evidence、Facts、Proof、Gaps和coverage；
- 分析步骤“业务流程” 的完整 `flowSliceIds`、Flow/Capsule、`modelEligibleFlowSliceIds`、`modelIneligibleFlowSliceIds`、逐 ineligible Flow 的非空 `modelIneligibilityGapIds` 与 coverage；
- 分析步骤“流程解释” 仅针对 model-eligible Flow 的九项 canonical semantic artifacts 与 `flow-interpretation-receipt.json`：R0/R1/R2 tasks、实际Provider rounds与generation receipts、唯一 `RepositoryInterpretationRegistry`、registry proposal dispositions、interpretation candidates 和 `FlowInterpretationDisposition`。程序直接从两类disposition中的 `ModelTaskDispositionV1` 验证全部planned task，而不读取任何运行时队列或进程状态；
- 冻结的 `TechnicalDisplayRegistry`、knowledge/admission profiles、schema bundle、artifact policy 和预算。

分析步骤“业务流程” 的两个 eligibility 分子必须不交叠且 union 精确等于全部 Flow。分析步骤“流程解释” disposition 的 Flow ID 集必须精确等于 分析步骤“业务流程” `modelEligibleFlowSliceIds`；model-ineligible Flow 出现在任何 分析步骤“流程解释” task、round、candidate 或 disposition 中都是 fatal。

历史pre-reset DepotHead固定样例在分析步骤“业务流程”对应位置得到Gap、0 Flow、0 Capsule，因此成为“仓库知识不得制造业务meaning”的回归baseline。当前SourceAnalysis尚未运行该样例；目标实现即使面对0 Flow，仍须发布一份仓库级technical/Gaps knowledge、空Flow decision集和前七个分析步骤coverage draft。

**示例分类：NARRATIVE_ILLUSTRATION / NOT_SCHEMA_VALID。** 下列只说明当前审计结果，省略全部 wire metadata 与 identity 字段：

~~~json
{
  "flowSliceIds": [],
  "flowAdmissionDecisions": [],
  "admittedMeaningIds": [],
  "repositoryKnowledgeCount": 1,
  "historicalPreResetDepotHeadResult": "GAP",
  "closedThroughRepositoryKnowledge": false,
  "closureReasonCode": "BOUNDED_PATH_SET_NOT_REPOSITORY_COMPLETE"
}
~~~

若未来闭合 Flow 选择“批量审核或反审核”，模型 lineage 仍只证明一个待准入提案。分析步骤“仓库知识” 必须逐跳重验 `registryProposalId → provisionalKey → interpretationProposalId → selectedKey`、同 Flow basis 和 program lattice；不能从这句话反推 Fact 或企业政策。

## 3. 程序怎样工作

1. 重验 前六个分析步骤 roots、controls、完整 分析步骤“业务流程” Flow denominator、eligibility partition、分析步骤“流程解释” eligible-only coverage 和唯一 frozen registry；从FlowInterpretation两类disposition枚举R0/R1/R2 `ModelTaskDispositionV1`，用任务集合重算`E + 2R` task/disposition双射，并验证flow/task/round/state、对应round/receipt和R2 `NOT_RUN_UPSTREAM_FAILED`的same-Flow upstream-task链。
2. 按 `flowSliceId` 对**全部 分析步骤“业务流程” Flow**迭代，而不是按 分析步骤“流程解释” candidate 迭代。
3. 对 model-ineligible Flow，验证 分析步骤“流程解释” 中不存在该 Flow，要求 分析步骤“业务流程” ineligibility Gap 非空，解析唯一 technical fallback，并生成 `MODEL_INELIGIBLE_TECHNICAL_FALLBACK`。
4. 对 model-eligible Flow，要求恰一个 分析步骤“流程解释” `FlowInterpretationDisposition`。`READY_FOR_ADMISSION` 必须绑定恰一个 candidate；`GAP`/`FAILED` 必须没有 candidate 并携带合同规定的 Gap/failure lineage。
5. 对 READY candidate 的每个 interpretation proposal，验证 `registryProposalId → provisionalKey → interpretationProposalId → selectedKey`；`selectedKey`必须逐字等于同 Flow registry item 的 `provisionalKey`，basis 不能扩张。
6. 重算 proposal decision lattice。程序只能与模型建议相同或更保守；KEEP/NARROW 产生 meaning，DROP/NEEDS 不产生 meaning。
7. 根据闭集条件为该 Flow 生成恰一个 flow-level decision。无 admitted meaning、分析步骤“流程解释” GAP/FAILED 或 model-ineligible 都必须走 total technical fallback，不能从 Flow denominator 消失。
8. 构建 `FLOW | REQUEST | RESULT | RECORD | OUTCOME | ACTIVITY | FIELD | FORMULA | RELATION` technical anchors。
9. 只按 FQN、SQL table/column、Flow/Outcome ID、exact graph endpoint 或 Proof-backed equivalence edge 合并；simple name、中文名、display value 和 term key 都不是 identity。
10. 在全 Flow union 上建立对象同一性、关系、指标/formula 和 conflict；逐字段复制 frozen registry 的 `proposalKind/normalizedLabel/normalizedPurpose`，不得改写显示值。
11. 给每个 Fact atom、Flow、Outcome、proposal、provisional key、proposal decision、meaning、fallback、Gap、knowledge item、relation 和 metric 恰一个 knowledge owner或有 reason code 的 exclusion。
12. M3 从已安装 M1/M2 artifacts 重算 exact ID-set equations，形成嵌入 `knowledge-accounting.json` 的 `RepositoryCoverageLedgerDraftV2`，再发布五个 semantic files；analysis step store 最后计算 分析步骤“仓库知识” root/receipt。draft 不引用 分析步骤“仓库知识” root、receipt、M3 receipt、自己的 enclosing artifact 或 分析步骤“九章文档” final ledger。

## 4. 生成的可观察产物

分析步骤“仓库知识” 的 reader-visible set 保持**恰六个文件**：五个 semantic files 加一个 receipt；本次合同不增加文件或改变数量。

| 文件 | 唯一职责 |
| --- | --- |
| `admitted-flow-meanings.jsonl` | 每个 分析步骤“业务流程” Flow 恰一条 `FlowAdmissionRecordV2`，内含完整 flow decision 及其 meanings/fallbacks；文件名保留但内容不再暗示“只有 admitted Flow 才有行” |
| `repository-business-knowledge.json` | 全仓一份对象、活动、流程、Outcome、字段、关系、公式、问题、Facts、meanings、fallbacks、Gaps、ownership、conflicts 与 registry lineage |
| `knowledge-conflicts.jsonl` | 每个 anchor/term/claim/owner conflict 及确定性 resolution；fatal conflict 不发布 |
| `knowledge-accounting.json` | `RepositoryKnowledgeCoverageV2` 和嵌入的 `RepositoryCoverageLedgerDraftV2` |
| `merged-gaps.json` | 去重但可逆的 canonical Gap 与 member Gap provenance |
| `repository-knowledge-receipt.json` | 分析步骤“仓库知识” upstream controls、五项 semantic descriptors、root、status 和 Gap refs；由 analysis step store 最后创建 |

**示例分类：NARRATIVE_ILLUSTRATION / NOT_SCHEMA_VALID。** 下列只对比两个 flow-level variant；省略 required refs、数组和 identity material，不能交给 reader：

~~~jsonl
{"flowSliceId":"flow:eligible","decisionKind":"MODEL_MEANING_ADMITTED","flowInterpretationDisposition":"READY_FOR_ADMISSION","meaningIds":["meaning:one"]}
{"flowSliceId":"flow:ineligible","decisionKind":"MODEL_INELIGIBLE_TECHNICAL_FALLBACK","flowInterpretationDisposition":null,"meaningIds":[],"technicalFallbackIds":["fallback:one"],"gapIds":["gap:model-ineligible"]}
~~~

0 Flow 时 `admitted-flow-meanings.jsonl` 是 policy-allowed canonical empty JSONL；其余四个 semantic files和receipt仍为非空 canonical artifacts，`repositoryKnowledgeCount=1`，draft 的 Flow ID sets为空但 source/site/entry/Fact/Gap accounting不能凭空为空。

## 5. 下游怎样消费而不返工

分析步骤“九章文档” 只读 分析步骤“仓库知识” 的五个 semantic files、分析步骤“仓库知识” receipt、frozen profiles/registry 和前序 typed refs。它不读取 raw model reasoning，不重新选择 term、合并对象、决定 Fact/Gap、打开源码或调用 Provider。

分析步骤“九章文档” M1 fresh-reopen `knowledge-accounting.json`，以 `RepositoryCoverageLedgerDraftReferenceV1{knowledgeAccountingRef,repositoryCoverageLedgerDraftId,schemaVersion}` 精确选中嵌套 draft；然后计算 reader semantic IDs、section ownership 和唯一 **final** `RepositoryCoverageLedgerV3`。分析步骤“仓库知识” draft 不含 reader item/section owner，不可冒充 run completion ledger。

| 分析步骤“九章文档” 开始前必须成立 | 分析步骤“仓库知识” 成功后保证 |
| --- | --- |
| 分析步骤“业务流程” Flow denominator、eligibility partition 与 分析步骤“流程解释” eligible-only disposition set闭合 | 每个 分析步骤“业务流程” Flow 恰一个 decision；model-ineligible Flow 的 分析步骤“流程解释” refs 全部为 null |
| 六个文件、receipt root、knowledge/conflict/accounting refs闭合 | 恰一个 RepositoryKnowledge；所有 Flow、proposal、meaning、fallback、Gap 和 knowledge item 都有 disposition/owner |
| TechnicalDisplayRegistry 对每个 fallback anchor 恰一匹配 | 分析步骤“九章文档” 无需临时补 term 或 display，也不能把 fallback 当 model meaning |
| draft reference 指向 `knowledge-accounting.json` 内 exact nested ID | 分析步骤“九章文档” M1 可从同一 draft 值完成 reader/section accounting，而不形成 plan↔ledger 或 RepositoryKnowledge-artifact↔RepositoryKnowledge-root 环 |

## 6. 成功、Gap、fatal 与显式复用

- **成功**：分析步骤“业务流程” 全 Flow → FlowAdmissionDecision 是 total one-to-one map；分析步骤“流程解释” 只覆盖 eligible subset；所有 semantic ID sets、owners、shards、conflicts 和 draft equations闭合；恰一 RepositoryKnowledge，分析步骤目录原子安装。
- **带 Gap 成功**：model-ineligible、分析步骤“流程解释” GAP、安全闭合的 分析步骤“流程解释” FAILED、proposal NEEDS、无业务 term 或仓库待确认问题都有 typed Gap/failure lineage和 technical fallback；它们不从 denominator 消失。
- **fatal**：eligible Flow 缺/重 分析步骤“流程解释” disposition、ineligible Flow 出现 分析步骤“流程解释” artifact、task disposition缺失/重复/跨Flow/与task、round或receipt不等、R2 `NOT_RUN_UPSTREAM_FAILED`链不闭合、unknown key/ref、registry drift、basis expanded、fallback 0/2 matches、double/no owner、unresolved equal-priority conflict、draft 自引用/未来引用、count 相等但 ID set 不等、accounting 不闭合。
- **显式复用**：若原RepositoryKnowledge执行失败，调用者以`executeStep(targetAnalysisStepKey=repository-knowledge)`创建新的run，显式传入已验证前六个分析步骤publication references。新执行fresh-reopen所列canonical artifacts并只执行RepositoryKnowledge；不重扫源码、不重做六个上游分析步骤、Provider调用为0。上游publication或control refs不闭合时拒绝启动。这是业务artifact复用，不是同run、队列或worker恢复。
- **确定性**：在相同upstream/control refs下，改变shard size或并行完成顺序不得改变canonical bytes。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 验证 eligibility partition、分析步骤“流程解释” coverage、key/basis | 是 | 否 |
| 最终 proposal decision 和 flow decision | 是 | 否 |
| 解析 technical fallback、合并 anchors、解决 conflicts | 是 | 否 |
| 提出有限解释 | 否，本分析步骤只消费 分析步骤“流程解释” | 分析步骤“流程解释” 已完成 |
| 修改 Fact、扩 registry、补源码或写 Markdown | 禁止 | 禁止 |

本分析步骤没有 Provider 参数，运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

执行顺序固定为 `ProposalAdmissionEngine` → `AnchoredKnowledgeMerger` → `KnowledgePublicationSpecifier` → `CanonicalAnalysisStepArtifactStore`。前三个是业务 modules；analysis step store 只负责 fresh-reopen、root 和 receipt-last。任何 module 都没有 Provider、source Path 或开放文本入口。

#### M1 `ProposalAdmissionEngine`

- **解决的问题**：把完整 分析步骤“业务流程” Flow denominator 转成完整、closed、content-addressed flow decisions，同时独立重算 READY candidate 的 proposal decisions。
- **精确输入**：分析步骤“已证明代码事实” 四项 semantic artifacts、分析步骤“业务流程” 五项 semantic artifacts、分析步骤“流程解释” 九项 semantic artifacts及三分析步骤receipt、frozen registry/TechnicalDisplayRegistry、admission profile和预算的 exact refs。分析步骤“业务流程” coverage必须携带 eligibility partition和逐 ineligible Flow Gap mapping；FlowInterpretation两类disposition携带验证task closure所需的`ModelTaskDispositionV1`。
- **顺序**：先验证FlowInterpretation R0/R1/R2 tasks与两类disposition中的task disposition集合精确双射为`E+2R`，再验证每个accepted disposition对应的round/receipt，以及R2 `NOT_RUN_UPSTREAM_FAILED`的same-Flow upstream task；然后按 分析步骤“业务流程” `flowSliceId`排序；model-ineligible 分支不读取 分析步骤“流程解释” per-Flow object；eligible 分支 exact-join一个 disposition；READY 再按 `interpretationProposalId`执行 lattice。0 LLM。
- **输出**：一个 `repository-knowledge-admission-decision-set-v4` `ModuleArtifact<AdmissionDecisionSetV4>`。
- **保证**：每个 分析步骤“业务流程” Flow恰一个 `FlowAdmissionDecisionV1`；每个 分析步骤“流程解释” interpretation proposal恰一个 `InterpretationProposalDecisionV1`；每个 KEEP/NARROW恰一个 meaning lineage；fallback resolution恰一匹配。
- **Gap/fatal**：合法 fallback variant可继续；eligibility/disposition集合不等、ineligible携带FlowInterpretation ref、eligible缺ref、task/disposition集合不精确为`E+2R`、disposition与task/round/receipt的flow/task/round/state或R2 upstream task不等、variant nullable组合错误、lineage/basis/fallback不闭合为fatal。
- **非目标**：不合并anchor、不改 Fact/registry、不调用模型。
- **测试 seam**：`admit(facts, businessFlowsFlows, flowInterpretationInterpretations, registries, profile)`；expected decisions必须独立手写，canonicalizer/lattice/accounting不可mock。
- **Luna RED 指南**：`ProposalAdmissionEngineTest` 逐一覆盖五个 flow variants、五个 proposal variants、eligible/ineligible partition mutation、missing/extra FlowInterpretation disposition、R0/R1/R2 task disposition缺失/重复/foreign task或round/receipt/state mutation、R2 `NOT_RUN_UPSTREAM_FAILED` upstream-task mutation、cross-Flow key、basis expansion、fallback 0/1/2、0Flow、single-flow omission和order determinism；只运行 `mvn -Dtest=ProposalAdmissionEngineTest test`，无网络/Provider。
- **Terra GREEN 指南**：仅实现 `analysis/knowledge/admission/` 与 v4 schema；先闭合 all-Flow total map，再做 READY proposal lattice。不得保留 v3 的 required `flowInterpretationDispositionId` 或开放 `decision` 字符串。

#### M2 `AnchoredKnowledgeMerger`

- **解决的问题**：按 proven technical anchors 合并跨 Flow 知识、处理 conflict，并给每个 semantic item唯一owner。
- **精确输入**：fresh-reopened M1 v4、分析步骤“已证明代码事实” Facts/Gaps、分析步骤“业务流程”全部Flows、frozen registry、anchor/knowledge profiles和预算。
- **顺序**：按 flow decision 建 anchors/items → exact-copy registry values → 全 shard proven-key union → relations/metrics → conflicts → ownership/accounting → 单一 knowledge。0 LLM。
- **输出**：一个 `repository-knowledge-knowledge-merge-v3` `ModuleArtifact<KnowledgeMergeV3>`；它显式保存 `flowAdmissionDecisionIds`，不能只从 分析步骤“流程解释” eligible subset推断 Flow coverage。
- **保证**：Flow shard union等于 分析步骤“业务流程” Flow IDs；meaning lineage完整；ineligible/GAP/FAILED fallbacks仍有 technical anchor/owner；只产一个 `repositoryKnowledgeId`。
- **Gap/fatal**：业务未知保留Gap；anchor不足/ambiguous、double/no owner、unresolved equal-priority conflict、alias provenance丢失为fatal。
- **测试 seam**：`merge(admissions, facts, flows, profiles)`覆盖双Flow同表merge、同名不同FQN隔离、proof equivalence、relation/metric、每种fallback、owner mutation、Gap alias、0Flow和顺序稳定。
- **Luna/Terra 指南**：目标测试 `AnchoredKnowledgeMergerTest`；实现只在 `analysis/knowledge/knowledge-merge/`，使用 v3，禁止显示名merge、丢flow decision或补source。

#### M3 `KnowledgePublicationSpecifier`

- **解决的问题**：验证 M1/M2 和 前七个分析步骤 accounting闭包，生成五个 semantic payload 与嵌套 draft；analysis step store独占 分析步骤“仓库知识” root/receipt。
- **精确输入**：fresh-reopened M1 v4、M2 v3、前六个分析步骤 publication references、M3实际打开的每个前六个分析步骤 semantic coverage artifact reference、所有上游 coverage/shard values和controls。不得接收 分析步骤“仓库知识” publication/root/receipt，因为它们尚不存在。
- **顺序**：验证 all-Flow/proposal/knowledge union → 构造 `RepositoryKnowledgeCoveragePreparationV1` → 重算 前七个分析步骤 ID sets/equations → 构造 `RepositoryCoverageLedgerDraftV2` → canonical 五个 semantic payload → 一次 M3 install/receipt → analysis step store fresh-reopen/root/receipt-last。0 LLM。
- **输出**：M3恰五个 semantic files；analysis step store形成恰六个 reader-visible files。`knowledge-accounting.json`内含 draft，不新增 `repository-coverage-ledger-draft.json`。
- **保证**：draft只含 前七个分析步骤事实；不含 reader/section owner/final `closed`，不引用 enclosing accounting artifact、M3 receipt、分析步骤“仓库知识” root/receipt或任何 分析步骤“九章文档” identity。
- **Gap/fatal**：合法 Gap可使 `closedThroughRepositoryKnowledge=true`；未知/遗漏 denominator、broken equation、自引用/未来引用、orphan/duplicate/canonical/install错误 fatal。`closedThroughRepositoryKnowledge=false`只有在missing/unexpected IDs和非空closure reason完整可诊断时可发布为incomplete draft，不能冒充complete。
- **测试 seam**：`specify(admissions, merge, upstreamAnalysisSteps, controls)`覆盖 exact-five/exact-six、all-flow decisions、embedded draft、cycle injection、count spoof、ID-set mismatch、0Flow nonempty knowledge、missing/overlap shard、receipt-last/partial-install/collision和fresh-reopen。
- **Luna/Terra 指南**：目标测试 `RepositoryKnowledgePublicationSpecifierTest`；实现只在 `analysis/knowledge/publish/`，真实 module/analysis step stores，canonical/root/accounting不可mock。

### 8.0.1 Wire-exact module 与 public schemas

`!` 表示 required non-null，`?` 表示 required nullable；required-nullable 字段必须存在，不能省略。所有数组按本节规定的 stable ID UTF-8 byte order排序、去重。

| artifact | schemaVersion / artifactType | wire-exact payload |
| --- | --- | --- |
| `modules/01-admission/admission-decision-set.json` | `repository-knowledge-admission-decision-set-v4` / `REPOSITORY_KNOWLEDGE_ADMISSION_DECISION_SET` | `admissionDecisionSetId!`, `repositoryInterpretationRegistryId!`, `businessFlowsFlowSliceIds[]!`, `modelEligibleFlowSliceIds[]!`, `modelIneligibleFlowSliceIds[]!`, `flowAdmissionDecisions[]!:FlowAdmissionDecisionV1`, `interpretationProposalDecisions[]!:InterpretationProposalDecisionV1`, `admittedMeanings[]!:AdmittedFlowMeaningV2`, `technicalFallbacks[]!:TechnicalFallbackV2`, `decisionAccounting!:AdmissionDecisionAccountingV2` |
| `modules/02-knowledge-merge/knowledge-merge.json` | `repository-knowledge-knowledge-merge-v3` / `REPOSITORY_KNOWLEDGE_KNOWLEDGE_MERGE` | `knowledgeMergeId!`, `repositoryKnowledgeId!`, `repositoryInterpretationRegistryId!`, `sourceFlowSliceIds[]!`, `flowAdmissionDecisionIds[]!`, `registryLineage[]!`, `flows[]!`, `outcomes[]!`, `anchors[]!`, `knowledgeItems[]!`, `relations[]!`, `metrics[]!`, `ownership[]!`, `conflicts[]!`, `mergedGaps[]!`, `knowledgeShardReceipts[]!`, `accounting!:RepositoryKnowledgeCoverageV2` |
| `admitted-flow-meanings.jsonl` | `repository-knowledge-flow-admission-record-v2` / `REPOSITORY_KNOWLEDGE_FLOW_ADMISSION_RECORD` | 每行 `flowAdmissionDecision!:FlowAdmissionDecisionV1`, `admittedMeanings[]!`, `technicalFallbacks[]!`；按 `flowSliceId`，行数精确等于 分析步骤“业务流程” Flow count |
| `repository-business-knowledge.json` | `repository-knowledge-repository-business-knowledge-v3` / `REPOSITORY_KNOWLEDGE_REPOSITORY_BUSINESS_KNOWLEDGE` | 恰一个 `RepositoryBusinessKnowledgeV3`，含 `repositoryKnowledgeId`, `repositoryInterpretationRegistryId`, `sourceFlowSliceIds`, `flowAdmissionDecisionIds`, typed knowledge arrays、registry lineage、ownership和conflicts |
| `knowledge-conflicts.jsonl` | `repository-knowledge-knowledge-conflict-v2` / `REPOSITORY_KNOWLEDGE_KNOWLEDGE_CONFLICT` | 每个已resolved conflict恰一行；按 `conflictId`；fatal conflict不得出现于成功publication |
| `knowledge-accounting.json` | `repository-knowledge-knowledge-accounting-v2` / `REPOSITORY_KNOWLEDGE_KNOWLEDGE_ACCOUNTING` | `artifactId!`, `repositoryKnowledgeId!`, `repositoryKnowledgeCoverage!:RepositoryKnowledgeCoverageV2`, `repositoryCoverageLedgerDraft!:RepositoryCoverageLedgerDraftV2` |
| `merged-gaps.json` | `repository-knowledge-merged-gaps-v2` / `REPOSITORY_KNOWLEDGE_MERGED_GAPS` | `canonicalGaps[]!{canonicalGapId!,memberGapIds[]!,missingRequirement!,impact!,closureRequirement!}`；members非空、按ID排序、所有alias可逆 |

M1 `upstreamArtifacts[]`必须逐项列出 分析步骤“已证明代码事实”四项、分析步骤“业务流程”五项、分析步骤“流程解释”九项 semantic `ArtifactReference`，以及实际打开的TechnicalDisplayRegistry/admission profile/budget refs；不得从运行目录猜测未声明输入。M2列M1、ProvenCodeFacts四项、BusinessFlows五项、frozen registry和实际打开的anchor/knowledge profile/budget refs；M3一次 install request 的 upstream 必须恰为M1/M2 refs及形成draft时实际打开的每个前六个分析步骤 semantic `ArtifactReference`。六个`AnalysisStepPublicationReference`另作为root/receipt chain验证值进入draft，不代替实际读取bytes的direct preimage。任何额外读取先进入 upstream 并改变 module identity。

### 8.1 Interface 与核心 records

~~~java
interface KnowledgeAdmissionEngine {
    RepositoryKnowledgeReference admitAndMerge(ProvenCodeFactsReference facts,
                                   BusinessFlowsReference flows,
                                   FlowInterpretationReference interpretations,
                                   RegistryBundle registries);
}
~~~

以下为 **exact record contracts**，不是伪代码示例。

~~~text
FlowAdmissionDecisionV1
  flowAdmissionDecisionId!
  flowSliceId!
  modelEligibility!: MODEL_ELIGIBLE | MODEL_INELIGIBLE
  modelIneligibilityGapIds[]!
  decisionKind!: MODEL_MEANING_ADMITTED
               | MODEL_READY_TECHNICAL_FALLBACK
               | MODEL_GAP_TECHNICAL_FALLBACK
               | MODEL_FAILED_TECHNICAL_FALLBACK
               | MODEL_INELIGIBLE_TECHNICAL_FALLBACK
  flowInterpretationDispositionId?
  flowInterpretationDisposition?: READY_FOR_ADMISSION | GAP | FAILED
  candidateId?
  interpretationProposalDecisionIds[]!
  meaningIds[]!
  technicalFallbackIds[]!
  gapIds[]!
  failureRef?
  reasonCode?

InterpretationProposalDecisionV1
  interpretationProposalDecisionId!
  flowSliceId!
  interpretationProposalId!
  registryProposalId!
  provisionalKey!
  selectedKey!
  decisionKind!: KEEP | NARROW | DROP | NEEDS_EVIDENCE | NEEDS_TERM_REGISTRY
  admittedBasisAtomIds[]!
  admittedBasisGapIds[]!
  meaningId?
  technicalFallbackId?
  decisionGapIds[]!
  reasonCode?

AdmittedFlowMeaningV2
  meaningId!
  flowSliceId!
  anchorId!
  registryProposalIds[]!
  provisionalKeys[]!
  interpretationProposalIds[]!
  interpretationProposalDecisionIds[]!
  selectedKeys[]!
  basisAtomIds[]!
  basisGapIds[]!
  decisionKind!: KEEP | NARROW
  technicalFallbackId?

TechnicalFallbackV2
  technicalFallbackId!
  flowSliceId!
  anchorId!
  displayKey!
  displayValue!
  basisFactIds[]!
  basisAtomIds[]!
  gapIds[]!
  sourceDecisionKind!: MODEL_MEANING_ADMITTED
                     | MODEL_READY_TECHNICAL_FALLBACK
                     | MODEL_GAP_TECHNICAL_FALLBACK
                     | MODEL_FAILED_TECHNICAL_FALLBACK
                     | MODEL_INELIGIBLE_TECHNICAL_FALLBACK

RegistryMeaningLineageV2
  registryLineageId!
  registryProposalId!
  provisionalKey!
  interpretationProposalId!
  interpretationProposalDecisionId!
  selectedKey!
  meaningId!
  flowSliceId!
  proposalKind!
  normalizedLabel!
  normalizedPurpose!
  basisAtomIds[]!
  basisGapIds[]!

AdmissionDecisionAccountingV2
  flowSliceIds[]!
  modelEligibleFlowSliceIds[]!
  modelIneligibleFlowSliceIds[]!
  modelIneligibilityGapIds[]!
  flowInterpretationDispositionIds[]!
  flowInterpretationCandidateIds[]!
  flowAdmissionDecisionIds[]!
  registryProposalIds[]!
  acceptedRegistryProposalIds[]!
  rejectedRegistryProposalIds[]!
  provisionalKeys[]!
  interpretationProposalIds[]!
  interpretationProposalDecisionIds[]!
  keepDecisionIds[]!
  narrowDecisionIds[]!
  dropDecisionIds[]!
  needsEvidenceDecisionIds[]!
  needsTermRegistryDecisionIds[]!
  meaningIds[]!
  registryLineageIds[]!
  technicalFallbackIds[]!
  gapIds[]!
  closed!

RepositoryKnowledgeCoverageV2
  flowSliceIds[]!
  flowAdmissionDecisionIds[]!
  outcomePathIds[]!
  factAtomIds[]!
  registryProposalIds[]!
  provisionalKeys[]!
  interpretationProposalIds[]!
  interpretationProposalDecisionIds[]!
  meaningIds[]!
  registryLineageIds[]!
  technicalFallbackIds[]!
  gapIds[]!
  knowledgeItemIds[]!
  relationIds[]!
  metricIds[]!
  conflictIds[]!
  semanticItemIds[]!
  ownerSemanticItemIds[]!
  reasonedSemanticExclusionIds[]!
  shardReceiptIds[]!
  closed!

TechnicalAnchorV2
  anchorId!
  kind!: FLOW | REQUEST | RESULT | RECORD | OUTCOME | ACTIVITY | FIELD | FORMULA | RELATION
  provenEndpointIds[]!
  owningFlowIds[]!
  proofBasisAtomIds[]!

RepositoryBusinessKnowledgeV3
  repositoryKnowledgeId!
  repositoryInterpretationRegistryId!
  sourceFlowSliceIds[]!
  flowAdmissionDecisionIds[]!
  registryLineage[]!: RegistryMeaningLineageV2
  objects[]!
  activities[]!
  flows[]!
  outcomes[]!
  fields[]!
  relations[]!
  formulas[]!
  questions[]!
  facts[]!
  admittedMeanings[]!: AdmittedFlowMeaningV2
  technicalFallbacks[]!: TechnicalFallbackV2
  gaps[]!
  ownership[]!: KnowledgeOwnership
  conflicts[]!

KnowledgeOwnership
  semanticItemId!
  ownerKnowledgeItemId?
  disposition!: OWNED | REASONED_EXCLUSION
  reasonCode?
~~~

### 8.2 FlowAdmissionDecisionV1 闭集与 nullable 矩阵

不存在第六种 variant，也不存在 `ADMITTED_WITH_GAPS`、`UNAVAILABLE` 或自由字符串 decision。Gap 是独立数组，不应编码进 decision 名称。

| decisionKind | model/FlowInterpretation 条件 | meaning/fallback | Gap/failure/reason |
| --- | --- | --- | --- |
| `MODEL_MEANING_ADMITTED` | `MODEL_ELIGIBLE`; disposition=`READY_FOR_ADMISSION`; disposition ID和candidate非null；ineligibility Gaps为空 | meaning非空；fallback可为空或覆盖没有业务meaning的其他proven anchors；proposal decision IDs精确等于candidate proposals | gap可空；failure/reason为null |
| `MODEL_READY_TECHNICAL_FALLBACK` | `MODEL_ELIGIBLE`; disposition=`READY_FOR_ADMISSION`; disposition ID和candidate非null；ineligibility Gaps为空 | meaning为空；fallback非空；proposal decision IDs精确等于candidate proposals且没有KEEP/NARROW | gap是所有NEEDS decision Gap的union，可在全DROP时为空；failure=null；reason=`NO_PROPOSAL_ADMITTED` |
| `MODEL_GAP_TECHNICAL_FALLBACK` | `MODEL_ELIGIBLE`; disposition=`GAP`; disposition ID非null；candidate=null；ineligibility Gaps为空 | proposal decision/meaning为空；fallback非空 | FlowInterpretation gapIds非空；failure=null；reason逐字复制FlowInterpretation版本化reason |
| `MODEL_FAILED_TECHNICAL_FALLBACK` | `MODEL_ELIGIBLE`; disposition=`FAILED`; disposition ID非null；candidate=null；ineligibility Gaps为空 | proposal decision/meaning为空；fallback非空 | failure非null；reason逐字复制FlowInterpretation reason；gap非空：优先复制FlowInterpretation gaps，若为空则确定性创建一个引用failureRef的`FLOW_INTERPRETATION_FAILED` Gap |
| `MODEL_INELIGIBLE_TECHNICAL_FALLBACK` | `MODEL_INELIGIBLE`; FlowInterpretation disposition ID/value和candidate全部null | proposal decision/meaning为空；fallback非空 | `modelIneligibilityGapIds`非空且逐字等于BusinessFlows mapping；failure=null；reason=`FLOW_MODEL_INELIGIBLE` |

所有 required-nullable 字段在每个 variant 中都必须出现。`MODEL_MEANING_ADMITTED` 可有 Gap，但仍表示至少一个 meaning通过；analysis step status `SUCCEEDED_WITH_GAPS`由整体 Gap accounting决定，不能改写flow decision kind。

每个variant的`gapIds`都不是自由列表，固定为该Flow的canonical union：`BusinessFlows Flow.gapIds ∪ modelIneligibilityGapIds ∪ FlowInterpretation disposition.gapIds ∪ referenced proposal decision.decisionGapIds ∪ RepositoryKnowledge deterministicGapIds`。不适用的分子为空；`MODEL_FAILED_TECHNICAL_FALLBACK`在FlowInterpretation gap为空时增加恰一个引用`failureRef`的`FLOW_INTERPRETATION_FAILED` Gap。任何漏项、额外跨Flow Gap或输入顺序造成的差异都使decision invalid。

### 8.3 InterpretationProposalDecisionV1 闭集

| decisionKind | meaningId | fallbackId | decisionGapIds | reasonCode | basis规则 |
| --- | --- | --- | --- | --- | --- |
| KEEP | 非null | null | empty | null | 逐字等于validated proposal basis |
| NARROW | 非null | null | empty | 非null版本化code | validated basis的闭合子集；basis若被收窄仍必须非空，也可保持basis而只收窄meaning eligibility；绝不扩张 |
| DROP | null | null | empty | 非null版本化code | 保存已验证basis，不用删除basis伪装DROP |
| NEEDS_EVIDENCE | null | 非null | 非空 | 非null版本化code | 保存当前闭合basis；Gap说明缺什么新Fact/Evidence |
| NEEDS_TERM_REGISTRY | null | 非null | 非空 | 非null版本化code | selectedKey仍逐字保留；Gap说明本run frozen registry eligibility不足，禁止在线扩registry |

每个 分析步骤“流程解释” interpretation proposal恰一个 decision；`interpretationProposalDecisionId`不能用 proposal ID代替。KEEP/NARROW meaning 与 `RegistryMeaningLineageV2`一一对应。

### 8.4 Fallback、Gap 与 meaning 行为

`TechnicalDisplayRegistry` 是 total deterministic registry。每个需要fallback的 proven anchor必须恰一匹配；0或2 matches都是 `TECHNICAL_FALLBACK_NOT_TOTAL` fatal。fallback display只从冻结registry复制，不能使用模型开放文本、source comment或临时中文名。

Fallback不等于模型失败，也不替代Fact：

- model-ineligible Flow仍保留全部 Facts、Outcomes和ineligibility Gaps；
- FlowInterpretation GAP/FAILED保留 disposition/failure lineage；
- proposal DROP是reasoned exclusion，不自动创造Gap；
- NEEDS_EVIDENCE/NEEDS_TERM_REGISTRY必须创建或引用typed Gap；
- meaning admitted时，未被meaning覆盖的其他proven anchor仍可有technical fallback；
- merged Gap必须保留 `canonicalGapId + memberGapIds`，alias不等于删除。

### 8.5 Anchor、merge 与 ownership

Anchor identity优先级固定为：

1. proven SQL table/column 或 FQN type；
2. Flow/Outcome ID；
3. exact method/field/parameter graph endpoint；
4. Proof-backed equivalence edge。

相同 `jsh_depot_head` table可跨Flow合并为同一RECORD；不同request FQN即使都显示“单据”也不能合并。Relation/metric每个endpoint必须有proven refs。每个semantic item恰一个 `KnowledgeOwnership{semanticItemId,ownerKnowledgeItemId,disposition}`；reasoned exclusion也必须有closed reason code，不能成为遗漏垃圾桶。

`KnowledgeOwnership`的nullable组合固定：`OWNED`要求`ownerKnowledgeItemId`非null且`reasonCode=null`；`REASONED_EXCLUSION`要求`ownerKnowledgeItemId=null`且`reasonCode`为版本化非空code。一个`semanticItemId`不能同时出现两种disposition。

### 8.6 RepositoryCoverageLedgerDraftV2

Draft 是 `knowledge-accounting.json` 的嵌套值，不是独立文件。分析步骤“九章文档”引用它时使用：

~~~text
RepositoryCoverageLedgerDraftReferenceV1
  knowledgeAccountingRef!: ArtifactReference
  repositoryCoverageLedgerDraftId!
  schemaVersion=repository-coverage-ledger-draft-v2
~~~

分析步骤“九章文档”先fresh-reopen `knowledgeAccountingRef`、验证artifact/schema/hash，再要求 nested draft ID逐字相等。普通 `ArtifactReference`不能单独假装指向JSON内部值。

下列为 **exact** records：

~~~text
RepositoryKnowledgeCoveragePreparationV1
  schemaVersion=repository-knowledge-coverage-preparation-v1
  upstreamAnalysisStepCoverageRoots[6]!: AnalysisStepPublicationReference
  repositoryKnowledgeAdmissionDecisionSetRef!: ArtifactReference // installed M1 payload
  repositoryKnowledgeKnowledgeMergeRef!: ArtifactReference       // installed M2 payload
  repositoryInterpretationRegistryRef!: ArtifactReference
  repositoryKnowledgeId!
  flowAdmissionDecisionIds[]!
  interpretationProposalDecisionIds[]!
  admittedMeaningIds[]!
  registryLineageIds[]!
  technicalFallbackIds[]!
  repositoryKnowledgeItemIds[]!
  relationIds[]!
  metricIds[]!
  knowledgeConflictIds[]!
  mergedCanonicalGapIds[]!
  semanticItemIds[]!
  ownerSemanticItemIds[]!
  reasonedSemanticExclusionIds[]!
  repositoryKnowledgeCoveragePreparationRoot!

RepositoryCoverageLedgerDraftV2
  schemaVersion=repository-coverage-ledger-draft-v2
  repositoryCoverageLedgerDraftId!
  sourceScopeKind!: COMPLETE_CAPTURE | BOUNDED_PATH_SET
  repositoryCompletionEligible!: BOOLEAN
  upstreamAnalysisStepCoverageRoots[6]!: AnalysisStepPublicationReference
  repositoryKnowledgeCoveragePreparation!: RepositoryKnowledgeCoveragePreparationV1
  sourceFileIds[]!
  analyzableTextFileIds[]!
  nonAnalyzableMediaFileIds[]!
  discoverySiteIds[]!
  entryIds[]!
  graphCandidateIdsByKind{}!
  factCandidateKeys[]!
  admittedFactIds[]!
  atomIds[]!
  outcomeCandidateIds[]!
  outcomePathIds[]!
  flowSliceIds[]!
  evidenceCapsuleIds[]!
  modelEligibleFlowSliceIds[]!
  modelIneligibleFlowSliceIds[]!
  modelIneligibilityGapIds[]!
  modelIneligibilityByFlow[]!{flowSliceId!,gapIds[]!}
  gapIds[]!
  registryProposalTaskIds[]!
  registryProposalRoundIds[]!
  registryProposalDispositionIds[]!
  registryProposalIds[]!
  acceptedRegistryProposalIds[]!
  rejectedRegistryProposalIds[]!
  repositoryInterpretationRegistryItemIds[]!
  provisionalKeys[]!
  interpretationTaskIds[]!
  interpretationRoundIds[]!
  flowInterpretationDispositionIds[]!
  flowInterpretationCandidateIds[]!
  interpretationProposalIds[]!
  interpretationProposalDecisionIds[]!
  flowAdmissionDecisionIds[]!
  admittedMeaningIds[]!
  registryLineageIds[]!
  technicalFallbackIds[]!
  repositoryKnowledgeItemIds[]!
  relationIds[]!
  metricIds[]!
  knowledgeConflictIds[]!
  semanticItemIds[]!
  ownerSemanticItemIds[]!
  reasonedSemanticExclusionIds[]!
  shardReceipts[]!: CoverageShardReceiptV1
  equations[]!: CoverageEquationV1
  closedThroughRepositoryKnowledge!: BOOLEAN
  closureReasonCode?

CoverageShardReceiptV1
  analysisStepKey!: verified-source-inventory | application-discovery | program-graphs |
                    proven-code-facts | business-flows | flow-interpretation |
                    repository-knowledge
  shardKind!
  shardId!
  denominatorIds[]!
  dispositionIds[]!
  outputIds[]!
  status!
  gapIds[]!
  owningArtifactRef!: ArtifactReference

CoverageEquationV1 = closed tagged union
  EXACT_SET_EQUAL {equationKey!,leftIds[]!,rightIds[]!}
  DISJOINT_UNION {equationKey!,denominatorIds[]!,partitions[]!{partitionKey!,ids[]!}}
  BIJECTION {equationKey!,leftIds[]!,rightIds[]!,mappings[]!{leftId!,rightId!}}
  TOTAL_FUNCTION {equationKey!,domainIds[]!,codomainIds[]!,mappings[]!{domainId!,codomainId!}}
  NONEMPTY_SET_BY_DOMAIN {equationKey!,domainIds[]!,mappings[]!{domainId!,memberIds[]!}}
~~~

`RepositoryKnowledgeCoveragePreparationV1`只能引用已安装的M1/M2和upstream registry，不引用M3、任何RepositoryKnowledge public artifact、RepositoryKnowledge analysis step root/receipt或未来NineSectionDocument值。其 ID sets逐字等于M1/M2；因此它给draft一个无环的 RepositoryKnowledge coverage root。

Draft至少包含并验证以下 equations（实际artifact保存完整左右ID sets，不只保存count）：

~~~text
sourceFileIds = analyzableTextFileIds ⊎ nonAnalyzableMediaFileIds
flowSliceIds ↔ evidenceCapsuleIds
flowSliceIds = modelEligibleFlowSliceIds ⊎ modelIneligibleFlowSliceIds
modelIneligibleFlowSliceIds -> nonempty modelIneligibilityByFlow.gapIds
modelEligibleFlowSliceIds = set(d.flowSliceId for each canonical FlowInterpretationDisposition d in flow-interpretation-dispositions.jsonl)
flowSliceIds ↔ FlowAdmissionDecision.flowSliceIds
modelIneligibleFlowSliceIds = MODEL_INELIGIBLE_TECHNICAL_FALLBACK.flowSliceIds
modelEligibleFlowSliceIds = union(the other four FlowAdmissionDecision variants)
registryProposalIds = acceptedRegistryProposalIds ⊎ rejectedRegistryProposalIds
interpretationProposalIds = InterpretationProposalDecision.interpretationProposalIds
interpretationProposalDecisionIds = KEEP ⊎ NARROW ⊎ DROP ⊎ NEEDS_EVIDENCE ⊎ NEEDS_TERM_REGISTRY
admittedMeaningIds = meanings produced by KEEP ⊎ NARROW
registryLineageIds ↔ KEEP/NARROW interpretationProposalDecisionIds
semanticItemIds = ownerSemanticItemIds ⊎ reasonedSemanticExclusionIds
knowledgeShardFlowIds = exactDisjointUnion(flowSliceIds)
repositoryKnowledgeArtifacts = exactlyOne
~~~

合法 typed Gap本身不使`closedThroughRepositoryKnowledge=false`；闭包表示每个denominator item已有唯一处置，不表示“没有Gap”。`closedThroughRepositoryKnowledge=true`要求`sourceScopeKind=COMPLETE_CAPTURE`、`repositoryCompletionEligible=true`、所有 equations成立、六个upstream publication refs/所有shard refs有效、M1/M2 preparation sets一致、无orphan/duplicate，且`closureReasonCode=null`。`BOUNDED_PATH_SET`必须为false并使用`BOUNDED_PATH_SET_NOT_REPOSITORY_COMPLETE`；其他false也要求版本化非空reason并可从equations精确算出missing/unexpected/overlap IDs。scope kind与eligibility不相容不是Gap，而是draft invalid。

Draft没有 `readerSemanticItemIds`、`sectionOwnerBySemanticItem`、NineSectionDocument preparation、final `closed` 或 final ledger ID。唯一 final `RepositoryCoverageLedgerV3`只由NineSectionDocument M1安装。

### 8.7 Identity、排序和预算

Identity公式固定为：

~~~text
flowAdmissionDecisionId = "flow-admission-decision:" + lowercaseHex(SHA-256(
    frame(UTF8("flow-admission-decision-id-v1")) ||
    frame(canonicalJson(decisionWithoutFlowAdmissionDecisionId))))

interpretationProposalDecisionId = "interpretation-proposal-decision:" + lowercaseHex(SHA-256(
    frame(UTF8("interpretation-proposal-decision-id-v1")) ||
    frame(canonicalJson(decisionWithoutInterpretationProposalDecisionId))))

repositoryKnowledgeCoveragePreparationRoot = "repository-knowledge-coverage-preparation:" + lowercaseHex(SHA-256(
    frame(UTF8("repository-knowledge-coverage-preparation-root-v1")) ||
    frame(canonicalJson(preparationWithoutRepositoryKnowledgeCoveragePreparationRoot))))

repositoryCoverageLedgerDraftId = "repository-coverage-ledger-draft:" + lowercaseHex(SHA-256(
    frame(UTF8("repository-coverage-ledger-draft-id-v2")) ||
    frame(canonicalJson(draftWithoutRepositoryCoverageLedgerDraftId))))
~~~

每个preimage删除且只删除自己的顶层self字段；required-nullable字段继续存在。Identity覆盖完整variant字段、refs、ID sets、equations、scope和closure。不得按flow临时映射、用count、display或enclosing artifact ID代替。

普通 ID arrays按UTF-8 bytes排序去重；draft与preparation中的`upstreamAnalysisStepCoverageRoots`必须逐字相同并按closed registry依赖顺序覆盖VerifiedSourceInventory到FlowInterpretation；`modelIneligibilityByFlow`按flowSliceId；`graphCandidateIdsByKind`按closed graph kind；`shardReceipts`按(analysisStepKey,shardKind,shardId)；equations按equationKey且partition按partitionKey；mapping按(domainId,codomainId/memberIds)。输入、shard或线程完成顺序不进入identity。

预算覆盖 anchors、proposal decisions、flow decisions、knowledge items、relations、metrics、conflicts、owners、draft ID sets/equations和merge worklist。超限不得截断或抽样；可完整指出受影响IDs时成为Gap/incomplete coverage，否则fatal。

### 8.8 安全与稳定 failure codes

不读取源码、Path、raw prompt/response reasoning，不调用Provider，不接收开放knowledge text。稳定codes：

`REPOSITORY_KNOWLEDGE_INPUT_INVALID`, `BUSINESS_FLOWS_FLOW_ELIGIBILITY_PARTITION_INVALID`, `FLOW_INTERPRETATION_ELIGIBLE_FLOW_COVERAGE_INVALID`, `FLOW_INTERPRETATION_TASK_DISPOSITION_CLOSURE_INVALID`, `MODEL_INELIGIBLE_FLOW_HAS_FLOW_INTERPRETATION_ARTIFACT`, `FLOW_ADMISSION_DECISION_INVALID`, `FLOW_ADMISSION_COVERAGE_BROKEN`, `MODEL_ROUND_REFERENCE_MISMATCH`, `REGISTRY_INVALID`, `REGISTRY_MEANING_LINEAGE_BROKEN`, `INTERPRETATION_REFERENCE_INVALID`, `INTERPRETATION_BASIS_EXPANDED`, `INTERPRETATION_ADMISSION_INVALID`, `TECHNICAL_FALLBACK_NOT_TOTAL`, `ANCHOR_INPUT_NOT_PROVEN`, `ANCHOR_MERGE_AMBIGUOUS`, `KNOWLEDGE_OWNER_INVALID`, `KNOWLEDGE_CONFLICT_UNRESOLVED`, `KNOWLEDGE_ACCOUNTING_INVARIANT_BROKEN`, `REPOSITORY_COVERAGE_DRAFT_INVALID`, `REPOSITORY_COVERAGE_DRAFT_CYCLE`, `REPOSITORY_KNOWLEDGE_RESOURCE_LIMIT_EXCEEDED`。

### 8.9 测试 seam 与验收

只运行当前slice的三个targeted selectors；禁止 full suite、live Provider、network、customer Maven或mock canonical/accounting core。

- M1：五个flow decision variants各一个正例；五个proposal variants各一个正例；model-ineligible无FlowInterpretation且有Gap；eligible缺/重 disposition；ineligible多出task/disposition；R0/R1/R2 task/disposition的`E+2R`双射、accepted round/receipt refs、R2同Flow `NOT_RUN_UPSTREAM_FAILED`的正反例；candidate/proposal遗漏；cross-Flow key；basis expansion；fallback 0/2；identity mutation；0Flow；input order稳定。
- M2：至少双Flow，其中一个model-ready admitted、一个model-ineligible或FlowInterpretation failed fallback；同table merge、同名不同FQN隔离、非空relation/metric、registry lineage任一hop删除、owner/alias/conflict/shard mutation、0Flow单一knowledge。
- M3：BusinessFlows Flow IDs与public JSONL行/decision IDs exact equality；FlowInterpretation只等于eligible subset；draft含全部前七个分析步骤 ID sets；RepositoryKnowledge publication/root/self/M3/future-NineSectionDocument cycle injection全部拒绝；nested draft reference reopen；count spoof、missing/overlap shard、second repository knowledge、exact-five/exact-six、partial-install/collision/fresh-reopen。
- 端到端 fixture至少两个入口/Flow/Capsule；正常eligible Flow保持R0→registry→R1/R2→meaning lineage，并从FlowInterpretation public dispositions重验对应task、round与receipt；另有model-ineligible（0 FlowInterpretation disposition）、R0 GAP、R1/R2 FAILED和all-DROP fixtures。每种都产生恰一个FlowAdmissionDecision且最终仍只有一个RepositoryKnowledge。
- 改变shard size或并发完成顺序后，M1/M2、五个public semantic bytes、draft ID和RepositoryKnowledge逐字节相同。

验收只在 `businessFlowsFlowSliceIds == flowAdmissionDecision.flowSliceIds`、eligible subset与FlowInterpretation dispositions精确相等、public tasks与`ModelTaskDispositionV1`集合精确双射为`E+2R`、accepted round/receipt与R2 upstream-task refs闭合、proposal/meaning/fallback/owner accounting闭合、draft无环且五semantic+receipt原子安装时通过。单Flow PASS永远不能完成RepositoryKnowledge或run。

### 8.10 已冻结裁决：实现者不得自由推断

- RepositoryKnowledge的主分母永远是BusinessFlows全部Flow，不是FlowInterpretation candidates或dispositions。
- 每个model-ineligible Flow恰一个`MODEL_INELIGIBLE_TECHNICAL_FALLBACK`，三个FlowInterpretation字段为null，不能伪造FlowInterpretation disposition。
- Flow decision是五variant closed union；proposal decision是五variant separate closed union；Gap不编码进decision名称。
- 业务term缺失时使用total TechnicalDisplayRegistry；不能开放写词、在线扩registry或丢Fact。
- merge key只允许proven SQL/FQN、Flow/Outcome、exact endpoint或Proof-backed equivalence；显示名/simple name/term key不是identity。
- 每个semantic item恰一owner或有reasoned exclusion；equal-priority unresolved conflict fatal。
- `RepositoryCoverageLedgerDraftV2`嵌入现有`knowledge-accounting.json`，只到RepositoryKnowledge；NineSectionDocument M1拥有唯一final ledger。不得增加reader-visible文件。
- RepositoryKnowledge每run只有一个RepositoryKnowledge；禁止per-Flow knowledge或Markdown旁路。

## 9. 当前实现成熟度审计

Wire Reset后的`org.sourceanalysis.app.analysis.knowledge`目前只有语义package骨架；当前没有admission、anchor merge、coverage draft或RepositoryKnowledge artifact。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 目标package与JDK 17 Toolchain已就位；通用wire头门禁不做proposal admission、anchor merge或ownership。 |
| **本步骤生产能力尚未实现** | M1–M3、all-Flow decision、model-ineligible fallback、R0→meaning lineage、conflict/owner、RepositoryCoverageLedgerDraftV2和六项正式输出均不存在。 |
| **历史证据，不是当前能力** | 已删除的pre-reset路径曾验证有限term/claim admission、technical fallback、hard-anchor merge和Flow-local Gap。它只说明可复用的测试意图，不是当前bounded v0或当前RepositoryKnowledge。 |
| **样例边界** | 历史DepotHead 0 Flow/0 Capsule baseline意味着未来重放时应有0 flow decision和0 admitted model meaning，同时仍发布唯一technical/Gaps knowledge；当前没有该运行结果。 |
| **下一实现门** | 按本章对BusinessFlows全部Flow建立total admission map，以proven anchor而非显示文字合并，并保存唯一RepositoryKnowledge、完整ownership和无环coverage draft。 |

本分析步骤目标不是恢复旧类shape，而是让每个Flow都有可重验的程序决定、每个semantic item都有稳定owner，并始终维持一份完整仓库知识输出。
