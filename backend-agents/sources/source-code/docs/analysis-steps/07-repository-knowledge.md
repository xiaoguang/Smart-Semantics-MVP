# 仓库知识

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。本步骤仍是`repository-knowledge`，固定输出五项semantic payload加receipt，不新增分析步骤。

本文把目标合同与§12当前实现分开。`Flow`是入口局部活动，`BusinessProcess`是跨Flow端到端过程，二者多对多；模型hypothesis永远先经程序准入，才可能成为RepositoryKnowledge。

## 1. 为什么存在

Step 06的局部解释和跨Flow过程都是受限模型提案，不自动成为业务事实。RepositoryKnowledge用零模型调用完成唯一权威合并：

1. 准入每条Flow的局部meaning或technical fallback；
2. 验证每个admission-eligible process claim的允许引用和可定位依据，并按证据强度为实际准入claim分配certainty；
3. 比较冲突、备选和待确认，不强行选唯一故事；
4. 建立Flow ↔ BusinessProcess多对多membership；
5. 生成全仓唯一一份RepositoryKnowledge。

顺序固定为`local admission → admission-eligible process claim validation / terminal-no-model typed exclusion → conflict/alternative comparison → many-to-many membership → one knowledge`。本步骤没有Provider参数，运行时模型调用数严格为0。

## 2. 实际上游交接

只读取fresh-reopened、receipt-verified正式产物：

- Step 03完整ProgramGraphs publication：`code-structure-graph.json`、`call-graph.json`、`control-flow-graph.json`、`data-flow-graph.json`、`evidence-graph.json`、`graph-index.json`、`graph-gaps.jsonl`与`program-graphs-receipt.json`；Evidence node/rule/source closure来自`evidence-graph.json`，Step 03不发布Facts或Proofs；
- Step 04完整ProvenCodeFacts publication：`proven-facts.json`、`proof-pack.json`、`gap-ledger.json`、`fact-accounting.json`与`proven-code-facts-receipt.json`；Fact/Proof及其expectation Gap只从这里读取；
- Step 05完整BusinessFlows publication：`flow-slices.json`、`flow-coverage.json`、`entry-dispositions.jsonl`、`evidence-capsules.jsonl`、`flow-gaps.jsonl`与`business-flows-receipt.json`，包括Flow/Capsule、Outcomes、coverage和`processJoinSignals`；
- Step 06十四项semantic payload与receipt，包括局部R0/R1/R2、过程groups/P1/P2、hypotheses和两类dispositions；
- frozen `RepositoryInterpretationRegistry`、`TechnicalDisplayRegistry`、profile、budget和前六步publication refs。

程序必须重算Step 06的完整合同：

~~~text
planned model tasks = E + 2R + 2S
actual calls = E + R + accepted local R1 + S + accepted process P1
processEvidenceGroups cover exactly all N Flow IDs
allTaskShardIds = modelSafeTaskShardIds ⊎ noModelTaskShardIds
|allTaskShardIds| = A
candidateRelationIds = disjoint union of ownerCandidateRelationIds over all A shards
processInterpretationDisposition.taskShardIds = allTaskShardIds
step06OwnedGapIds = disjoint union of processGaps[].gapId over all A dispositions
allProposedHypothesisIds = admissionEligibleHypothesisIds ⊎ droppedHypothesisIds ⊎
                           p2GapHypothesisIds ⊎ p2FailedHypothesisIds
publishedHypothesisIds = admissionEligibleHypothesisIds ⊎
                         p2GapHypothesisIds ⊎ p2FailedHypothesisIds
~~~

model-ineligible Flow在局部FlowInterpretation task/round/candidate/disposition中仍为零，但可出现在确定性`ProcessEvidenceGroup`与`NO_MODEL` process shard。后者必须有Step 06显式`NO_MODEL_ADMISSION_PENDING` disposition，且没有P1/P2 task/round/receipt；过程Gap不能制造局部meaning。

### 2.1 真实DepotHead边界

DepotHead静态材料可支持“批量状态入口读取目标状态、校验允许ID、把参数传给边界调用”。如果没有Mapper/XML、事务后置读取或外部响应Proof，RepositoryKnowledge必须把“数据库已经更新”“库存改变”“写入日志/配置”保留为待确认，不能由业务词、方法名或SQL锚点推断外部效果。

### 2.2 明确合成、不是jshERP行为

以下仅是合同fixture：

`提交补货申请 → 门店审批 → 区域审批并创建采购单 → 采购单审批及费用处理 → 执行采购并登记物流 → 收货并登记库存 → 生成、确认、结算月度账单`。

Step 07可准入有证据的活动/对象/角色和某些转换；把并行费用处理、退回修改等保存为备选；把无Proof的“唯一采购单”“已经记账”降为`PENDING_CONFIRMATION`或拒绝。一个收货Flow可同时加入“采购履约”和“月度结算”过程。示例永远不得被标记为jshERP事实。

## 3. 三个程序模块

| 模块 | 唯一职责 | 模型调用 |
| --- | --- | --- |
| M1 `ProposalAdmissionEngine` | 对全部Flow做局部total admission；验证六路process partition，只为admission-eligible hypotheses/claims给出process admission decisions，其余走typed exclusion | 0 |
| M2 `AnchoredKnowledgeMerger` | 按Proof-backed技术锚点合并、比较冲突/备选、建立多对多membership与唯一knowledge | 0 |
| M3 `KnowledgePublicationSpecifier` | 重算coverage/accounting，发布五项semantic payload；store receipt-last | 0 |

## 4. 程序过程

1. fresh-reopen前六步并重验refs、root、controls、Step 05 Flow denominator和Step 06十五文件。
2. **Local admission**：按全部`flowSliceId`排序。eligible Flow exact-join一个local disposition；ineligible Flow要求非空ineligibility Gap且Step 06局部objects为零。每条Flow恰一个`FlowAdmissionDecisionV2`。
3. READY local candidate逐跳验证`registryProposalId → provisionalKey → interpretationProposalId → selectedKey`；程序只能与R2相同或更保守。无法准入时使用冻结`TechnicalDisplayRegistry`的total fallback。
4. **Process shard total validation**：先验证全部`A`个shard与`ProcessInterpretationDispositionV2`一一对应，并验证每个Step 06-owned `ProcessInterpretationGapV1`在全体`processGaps[]`中恰一canonical value、各disposition `gapIds` union闭合。`MODEL_TASKS`且P2 `REVIEWS`的分支逐hypothesis核对P1/P2 task、round、统一generation receipt、review、disposition、group/relation/signal、Flow/Capsule、Fact/Proof/Evidence/source refs及逐hypothesis P2非扩张；仅retained/narrowed/pending集合中的每个hypothesis恰一个`ProcessAdmissionDecisionV1`，DROP没有public hypothesis或admission。P2 `GAP/FAILED`分支保留全部P1 hypothesis与P1/P2 task/round/receipt，但review字段为null且没有process admission；这些hypothesis、claims及owner relations连同typed Step 06 Gap逐项进入`reasonedSemanticExclusionIds`。`MODEL_TASKS`但P1 GAP/FAILED的分支没有hypothesis/admission，必须保留P1 task/round/receipt、planned P2 task与`NOT_RUN_UPSTREAM_FAILED` disposition；P1 FAILED还必须把唯一`PROCESS_P1_HYPOTHESIS_FAILED` carrier与owner relations带入同一exclusion集合。`NO_MODEL`分支必须没有hypothesis/admission/model object，其owner candidate relations也逐条reasoned exclusion。后三类非准入分支的context Flow仍由步骤7生成independent或带显式Gap的unassigned membership，不得丢失。
5. 给每个实际准入的process claim分配恰一certainty：`SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION`。DROP、P1 terminal、P2 GAP/FAILED和no-model分支没有process certainty；不允许numeric confidence或`HIGH/MEDIUM/LOW`等旁路。
6. **Conflict/alternative comparison**：相同anchor上的兼容claims可合并；相斥且无优先Proof的claims保留为`processAlternatives`或pending，不得挑一个更流畅的叙事。
7. **Many-to-many membership**：为准入/保留的process建立activities、relations、roles、states和memberships。每条Flow必须属于至少一个BusinessProcess、一个`INDEPENDENT_ACTIVITY`，或一个带显式Gap的`UNASSIGNED_PENDING`。
8. **One knowledge**：按FQN、SQL table/column、Flow/Outcome ID、exact graph endpoint或Proof-backed equivalence合并；display/simple name/registry term不作identity。生成一份且仅一份RepositoryKnowledge。
9. M3重算ID set equations、ownership和前七步coverage draft，原子安装五项semantic payload；analysis-step store最后写receipt。

## 5. 精确准入记录

`FlowAdmissionDecisionV2`仍是全部Flow的total map：

~~~text
flowAdmissionDecisionId!
flowSliceId!
eligibility!: MODEL_ELIGIBLE | MODEL_INELIGIBLE
decisionKind!: MODEL_MEANING_ADMITTED | MODEL_NO_MEANING_TECHNICAL_FALLBACK |
               MODEL_GAP_TECHNICAL_FALLBACK | MODEL_FAILED_TECHNICAL_FALLBACK |
               MODEL_INELIGIBLE_TECHNICAL_FALLBACK
flowInterpretationDispositionId?
interpretationProposalDecisionIds[]!
meaningIds[]!
technicalFallbackIds[]!
gapIds[]!
failureRef?
reasonCode?
~~~

ineligible variant的Step 06局部ref必须全null；READY/GAP/FAILED variants不得与candidate/meaning nullable组合冲突。每个Gap是该Flow上游Gaps、local disposition Gaps、proposal decision Gaps与deterministic Gaps的规范union。

`ProcessAdmissionDecisionV1`字段严格为：

~~~text
processAdmissionDecisionId!
businessProcessHypothesisId!
processInterpretationDispositionId!
p1TaskId!
p1RoundId!
p2TaskId!
p2RoundId!
processHypothesisReviewId!
decisionKind!: ADMIT | ADMIT_WITH_PENDING | PRESERVE_AS_ALTERNATIVE | REJECT
claimDecisions[]!: ProcessClaimDecisionV1
memberFlowSliceIds[]!
candidateRelationIds[]!
businessProcessId?
processCertainty?: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION
processAlternativeIds[]!
pendingConfirmationIds[]!
gapIds[]!
reasonCode?

ProcessClaimDecisionV1
  processClaimId!
  claimKind!: PURPOSE | END_RESULT | ACTIVITY | TRANSITION | CONDITION |
               BRANCH | PARALLEL | ALTERNATIVE | FALLBACK | ROLE | STATE | OBJECT
  disposition!: ADMIT | NARROW | PRESERVE_AS_ALTERNATIVE | PENDING_CONFIRMATION | REJECT
  certainty!: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION
  factIds[]!
  proofIds[]!
  evidenceNodeIds[]!
  processJoinSignalIds[]!
  processSemanticCueIds[]!
  counterSignalIds[]!
  blockingCounterSignalIds[]!
  gapIds[]!
  reasonCode?
~~~

`processCertainty`只在`ADMIT | ADMIT_WITH_PENDING`时非null，并与创建的`BusinessProcessKnowledgeV1.certainty`相等；其余decision必须为null。此record只为P2 `REVIEWS`后进入retained/narrowed/pending分区的model-safe hypothesis创建，所以P1/P2 task、round与review refs始终非空；P2 DROP、P2 GAP/FAILED、P1 GAP/FAILED和no-model shard均由Step 06 process disposition、typed Gap与reasoned exclusion闭合，不伪造本record。

counter转换不是实现选择。对每个source `ProcessHypothesisClaimV1 c`，Step 07固定复制：

~~~text
ProcessClaimDecisionV1.counterSignalIds = c.counterProcessJoinSignalIds
ProcessClaimDecisionV1.blockingCounterSignalIds = c.blockingCounterProcessJoinSignalIds
ProcessClaimDecisionV1.blockingCounterSignalIds = ProcessClaimDecisionV1.counterSignalIds
~~~

最后一个等式来自Step 06已验证的relation exact-union合同；Step 07不得读取不存在的signal-level blocking flag、重新分类或丢弃counter。所有后续process relation/claim/pending records的两数组，都是其source claim decision集合对应数组的exact union。

准入前还必须在path-bearing `ProcessCandidateRelationV2`上重验：`positivePairBases[]`覆盖全部qualifying pairs，`counterBases[]`覆盖每个pair的全部explicit blocking signals，并在两端完整业务对象key集合均非空且互斥时包含`DIFFERENT_BUSINESS_OBJECT`；四个aggregate signal数组分别是这些bases的exact union。Step 07不从path-free model view重算这一步。一个relation有多个positive pairs时也只消费这份完整union，所以任一counter（包括不同业务对象）都会让相关claim最多为`PENDING_CONFIRMATION`，certainty不随pair/input迭代顺序改变。

certainty规则：

- `SOURCE_CONFIRMED`要求P1响应accepted、P2对该hypothesis/claim为`KEEP | NARROW`、专门Fact与Proof直接支持claim，且`blockingCounterSignalIds=[]`；
- `EVIDENCE_SUPPORTED_INFERENCE`要求P1响应accepted、P2对同一hypothesis/claim为`KEEP | NARROW`、`blockingCounterSignalIds=[]`，并满足以下任一条件：存在一个经M6验证的`PROVEN_HANDOFF | SHARED_ANCHOR`；或至少两类相互独立的可定位依据（对象/标识、状态、入口动作、边界目标、字段/表、冻结业务术语中至少两类）共同支持同一关系。第二种情况不要求有一条直接跨Flow Proof，但每项依据都必须属于允许的Flow/Capsule，并至少能回到file+symbol+line/excerpt或typed locator；模型文字本身不计依据。
- `PENDING_CONFIRMATION`用于仅有一个generic/semantic cue、方向不明、locator较粗、P2明确pending、存在Gap/counter或替代解释的claim；它只能进入pending/alternative，不得用于确定性转换。SHA、byte offset或列号缺失本身不使运行fatal，只限制certainty；完全没有可定位源码上下文的claim必须REJECT或转成searched-scope问题。
- reader wording不能提升certainty；P1/P2文字也不是Fact。`SOURCE_CONFIRMED`仍只来自完整Fact/Proof/Evidence closure；本规则只放宽业务过程推断的准入，不改变源码事实、外部效果或现有精确证据的验证。

对每条实际创建的`ProcessAdmissionDecisionV1 d`，Step 06 Gap传播不是启发式。令`h`为`d.businessProcessHypothesisId`指向的唯一hypothesis，`owner`为`d.processInterpretationDispositionId`指向的唯一disposition；必须满足：

~~~text
affectedStep06GapIds(d) = sorted exact set of owner.processGaps[].gapId where
  h.businessProcessHypothesisId is in g.businessProcessHypothesisIds
  OR intersection(d.claimDecisions.processClaimId, g.processClaimIds) is nonempty
  OR intersection(d.candidateRelationIds, g.candidateRelationIds) is nonempty

d.gapIds = exactUnion(
  all d.claimDecisions[].gapIds,
  review(h.processHypothesisReviewId).reviewGapIds,
  affectedStep06GapIds(d))
~~~

这里`review(...)`必须fresh-reopen `h.processHypothesisReviewId`唯一指向的P2 `REVIEWS` record；本record只为该分支创建，所以该ref必为non-null。不得按group共现、Flow重叠、message或遍历顺序附Gap。该规则使`PROCESS_COUNTER_SCOPE_UNRESOLVED`在受影响relation确被一个准入过程使用时进入该decision并把certainty限制为pending；若没有这样的准入decision，它不虚构process owner。

### 5.1 过程知识数组

`RepositoryBusinessKnowledgeV4`在既有对象/Flow/Outcome/meanings/fallbacks/Gaps/ownership之外，必须新增且只新增以下过程数组：

~~~text
businessProcesses[]!: BusinessProcessKnowledgeV1
processActivities[]!: ProcessActivityKnowledgeV1
processRelations[]!: ProcessRelationKnowledgeV1
processMemberships[]!: ProcessMembershipV1
roles[]!: RoleKnowledgeV1
states[]!: StateKnowledgeV1
processClaims[]!: ProcessClaimKnowledgeV1
processAlternatives[]!: ProcessAlternativeKnowledgeV1
pendingConfirmations[]!: PendingConfirmationV1
~~~

关键记录：

~~~text
BusinessProcessKnowledgeV1
  businessProcessId!, certainty!, purposeClaimId?, endResultClaimId?
  activityIds[]!, relationIds[]!, membershipIds[]!, alternativeIds[]!, pendingConfirmationIds[]!

ProcessMembershipV1
  processMembershipId!, flowSliceId!
  membershipKind!: BUSINESS_PROCESS | INDEPENDENT_ACTIVITY | UNASSIGNED_PENDING
  businessProcessId?, activityIds[]!, certainty!, gapIds[]!

ProcessRelationKnowledgeV1
  processRelationId!, fromActivityId?, toActivityId?, relationKind!
  conditionClaimIds[]!, supportCandidateRelationIds[]!, certainty!, gapIds[]!
~~~

过程级`BusinessProcessKnowledgeV1.certainty`是其所有非alternative、非pending核心claims的最保守值：全部core claim均`SOURCE_CONFIRMED`才可confirmed；否则只要全部core claim满足上述inference predicate就是`EVIDENCE_SUPPORTED_INFERENCE`；任一core claim review pending或`blockingCounterSignalIds`非空即为`PENDING_CONFIRMATION`。P2 GAP/FAILED/NOT_RUN没有BusinessProcessKnowledge，自然没有可伪造的certainty。`ProcessActivityKnowledgeV1`、`ProcessRelationKnowledgeV1`、`ProcessMembershipV1`、`RoleKnowledgeV1`、`StateKnowledgeV1`、`ProcessClaimKnowledgeV1`和所有Step 08 process ReaderItem同样各有且仅有一个三值`certainty`，不得从父process默认继承或用numeric score替代。

nullable约束：`BUSINESS_PROCESS`要求非null `businessProcessId`；`INDEPENDENT_ACTIVITY`要求null process且至少一个activity；`UNASSIGNED_PENDING`要求null process、空activity且非空Gap。一个Flow可有多个`BUSINESS_PROCESS` membership，但不得同时以`UNASSIGNED_PENDING`掩盖已准入membership。

### 5.2 V5/V4完整wire合同

以下是本步骤新增或升级standalone root及九类过程知识记录的完整字段，不允许实现另加自由文本、numeric confidence或第二个self ID。`!`表示字段必有且非null，`?`表示字段必有但值可为null，`[]!`表示字段必有、可为空数组；所有未标`?`的引用都必须闭合。

~~~text
KnowledgeAdmissionDecisionRecordV5
  schemaVersion!: repository-knowledge-admission-decision-v5
  artifactType!: REPOSITORY_KNOWLEDGE_ADMISSION_DECISION
  artifactId!
  decisionScope!: FLOW | BUSINESS_PROCESS
  flowDecision?: FlowAdmissionDecisionV2
  processDecision?: ProcessAdmissionDecisionV1
  gapIds[]!

RepositoryBusinessKnowledgeV4
  schemaVersion!: repository-knowledge-business-knowledge-v4
  artifactType!: REPOSITORY_KNOWLEDGE_BUSINESS_KNOWLEDGE
  artifactId!
  repositoryInterpretationRegistryId!
  sourceScopeId!
  flowSliceIds[]!
  flowAdmissionDecisionIds[]!
  processAdmissionDecisionIds[]!
  objects[]!
  activities[]!
  flows[]!
  outcomes[]!
  fields[]!
  relations[]!
  formulas[]!
  questions[]!
  facts[]!
  admittedMeanings[]!
  technicalFallbacks[]!
  registryLineage[]!
  gaps[]!
  ownership[]!
  conflicts[]!
  businessProcesses[]!: BusinessProcessKnowledgeV1
  processActivities[]!: ProcessActivityKnowledgeV1
  processRelations[]!: ProcessRelationKnowledgeV1
  processMemberships[]!: ProcessMembershipV1
  roles[]!: RoleKnowledgeV1
  states[]!: StateKnowledgeV1
  processClaims[]!: ProcessClaimKnowledgeV1
  processAlternatives[]!: ProcessAlternativeKnowledgeV1
  pendingConfirmations[]!: PendingConfirmationV1

BusinessProcessKnowledgeV1
  businessProcessId!
  sourceBusinessProcessHypothesisId!
  processAdmissionDecisionId!
  nameKey!: RegistryOrTechnicalKeyV1
  certainty!: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION
  purposeClaimId?
  endResultClaimId?
  activityIds[]!
  relationIds[]!
  membershipIds[]!
  roleIds[]!
  stateIds[]!
  processClaimIds[]!
  alternativeIds[]!
  pendingConfirmationIds[]!
  gapIds[]!

ProcessActivityKnowledgeV1
  processActivityId!
  sourceProcessClaimId!
  processAdmissionDecisionId!
  activityKey!: RegistryOrTechnicalKeyV1
  memberFlowSliceIds[]!
  businessProcessIds[]!
  roleIds[]!
  inputObjectKeys[]!: RegistryOrTechnicalKeyV1
  outputObjectKeys[]!: RegistryOrTechnicalKeyV1
  stateIds[]!
  certainty!: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION
  gapIds[]!

ProcessRelationKnowledgeV1
  processRelationId!
  sourceProcessClaimId!
  processAdmissionDecisionId!
  fromActivityId?
  toActivityId?
  relationKind!: PRECEDES | CONDITIONALLY_PRECEDES | PARALLEL_WITH |
                 ALTERNATIVE_TO | FALLS_BACK_TO | PRODUCES_FOR | CONSUMES_FROM
  conditionClaimIds[]!
  supportCandidateRelationIds[]!
  processJoinSignalIds[]!
  processSemanticCueIds[]!
  counterSignalIds[]!
  blockingCounterSignalIds[]!
  certainty!: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION
  gapIds[]!

ProcessMembershipV1
  processMembershipId!
  flowSliceId!
  membershipKind!: BUSINESS_PROCESS | INDEPENDENT_ACTIVITY | UNASSIGNED_PENDING
  businessProcessId?
  activityIds[]!
  certainty!: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION
  gapIds[]!

RoleKnowledgeV1
  roleId!
  sourceProcessClaimId!
  processAdmissionDecisionId!
  roleKey!: RegistryOrTechnicalKeyV1
  businessProcessIds[]!
  activityIds[]!
  responsibilityClaimIds[]!
  certainty!: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION
  gapIds[]!

StateKnowledgeV1
  stateId!
  sourceProcessClaimId!
  processAdmissionDecisionId!
  stateKey!: RegistryOrTechnicalKeyV1
  objectKey!: RegistryOrTechnicalKeyV1
  producerActivityIds[]!
  checkerActivityIds[]!
  processJoinSignalIds[]!
  certainty!: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION
  gapIds[]!

ProcessClaimKnowledgeV1
  processClaimKnowledgeId!
  sourceProcessClaimId!
  processAdmissionDecisionId!
  processHypothesisReviewId!
  claimKind!: PURPOSE | END_RESULT | ACTIVITY | TRANSITION | CONDITION |
               BRANCH | PARALLEL | ALTERNATIVE | FALLBACK | ROLE | STATE | OBJECT
  subjectKeys[]!: RegistryOrTechnicalKeyV1
  predicateKey!: RegistryOrTechnicalKeyV1
  objectKeys[]!: RegistryOrTechnicalKeyV1
  memberFlowSliceIds[]!
  candidateRelationIds[]!
  processJoinSignalIds[]!
  processSemanticCueIds[]!
  counterSignalIds[]!
  blockingCounterSignalIds[]!
  factIds[]!
  proofIds[]!
  evidenceNodeIds[]!
  certainty!: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION
  gapIds[]!

ProcessAlternativeKnowledgeV1
  processAlternativeId!
  sourceProcessClaimIds[]!
  processAdmissionDecisionId!
  alternativeKind!: COMPETING_PROCESS | COMPETING_RELATION | COMPETING_CLAIM
  memberFlowSliceIds[]!
  candidateRelationIds[]!
  mutuallyExclusiveWithAlternativeIds[]!
  certainty!: PENDING_CONFIRMATION
  gapIds[]!

PendingConfirmationV1
  pendingConfirmationId!
  sourceProcessClaimIds[]!
  processAdmissionDecisionId!
  subjectKey!: RegistryOrTechnicalKeyV1
  questionKey!: RegistryOrTechnicalKeyV1
  memberFlowSliceIds[]!
  processJoinSignalIds[]!
  processSemanticCueIds[]!
  counterSignalIds[]!
  blockingCounterSignalIds[]!
  gapIds[]!
  certainty!: PENDING_CONFIRMATION

KnowledgeConflictV3
  schemaVersion!: repository-knowledge-conflict-v3
  artifactType!: REPOSITORY_KNOWLEDGE_CONFLICT
  artifactId!
  conflictScope!: LOCAL | BUSINESS_PROCESS
  competingSemanticItemIds[]!
  conflictKind!: EQUIVALENT | COMPATIBLE | MUTUALLY_EXCLUSIVE | INSUFFICIENT_EVIDENCE
  resolution!: MERGE | KEEP_BOTH | PRESERVE_ALTERNATIVES | PENDING_CONFIRMATION | REJECT
  winningSemanticItemId?
  processAlternativeIds[]!
  pendingConfirmationIds[]!
  factIds[]!
  proofIds[]!
  gapIds[]!
  reasonCode!

KnowledgeAccountingV3
  schemaVersion!: repository-knowledge-accounting-v3
  artifactType!: REPOSITORY_KNOWLEDGE_ACCOUNTING
  artifactId!
  repositoryKnowledgeId!
  repositoryKnowledgeCoverage!: RepositoryKnowledgeCoverageV3
  repositoryCoverageLedgerDraft!: RepositoryCoverageLedgerDraftV3
  semanticArtifactDescriptors[5]!
  gapIds[]!
  status!: COMPLETE | COMPLETE_WITH_GAPS

MergedGapV3
  schemaVersion!: repository-knowledge-merged-gap-v3
  artifactType!: REPOSITORY_KNOWLEDGE_MERGED_GAP
  artifactId!
  canonicalGapId!
  memberGapIds[]!
  gapCode!
  gapScope!: LOCAL | BUSINESS_PROCESS | REPOSITORY
  affectedSemanticIds[]!
  affectedFlowSliceIds[]!
  affectedBusinessProcessIds[]!
  factIds[]!
  proofIds[]!
  evidenceNodeIds[]!
  sourceLocators[]!
  searchedScopeRefs[]!: ArtifactReference
  failureCode?
  limitKind?: FLOW_COUNT | RELATION_COUNT | SIGNAL_COUNT |
              REGISTRY_ITEM_COUNT | INPUT_BYTES
  configuredLimit?, observedValue?
  messageKey!
~~~

Step 06-owned Gap不得经lossy归并。每个`ProcessInterpretationGapV1 g`确定性产生一条member-singleton `MergedGapV3`；这条记录逐字复制`gapCode/messageKey/failureCode/limitKind/configuredLimit/observedValue`、`affectedFlowSliceIds/factIds/proofIds/evidenceNodeIds/sourceLocators/searchedScopeRefs`，并固定：

~~~text
merged.gapScope = BUSINESS_PROCESS
merged.canonicalGapId = g.gapId
merged.memberGapIds = [g.gapId]
merged.affectedSemanticIds = exactUnion(
  g.processEvidenceGroupId, g.taskShardId, g.candidateRelationIds,
  g.businessProcessHypothesisIds, g.processClaimIds, g.processModelTaskIds,
  g.affectedFlowSliceIds, g.processJoinSignalIds, g.processSemanticCueIds,
  g.repositoryInterpretationRegistryItemIds)
merged.affectedBusinessProcessIds = sorted exact set of d.businessProcessId where
  d is a ProcessAdmissionDecisionV1 AND d.businessProcessId is nonnull
  AND g.gapId is in d.gapIds
~~~

因此P1/P2 terminal和budget/no-model等没有non-null process decision的分支自然得到`affectedBusinessProcessIds=[]`；counter-scope或P2 review Gap则可在且仅在某个实际准入decision按上式携带它时得到非空process IDs。禁止因为Gap code、group/Flow共现或“非准入分支”标签硬编码空/非空。上述singleton规则加上fresh-reopened Step 06 carrier，使mapping从`MergedGapV3.canonicalGapId = MergedGapV3.memberGapIds[0]`回到原typed value完全可逆；不同Step 06 Gap不得因message相同而合并。其他上游Gap仍可按既有canonical equivalence合并，但新增字段必须取各member exact union，required-nullable值不一致时不得合并。

discriminator与nullable规则是闭集：`FLOW`只允许`flowDecision`非null，`BUSINESS_PROCESS`只允许`processDecision`非null；`KnowledgeConflictV3.winningSemanticItemId`只在`MERGE | REJECT`时非null；`fromActivityId/toActivityId`仅在关系端点确实未知且该记录为`PENDING_CONFIRMATION`、同时有非空Gap时可null。所有过程记录必须保留其own certainty；不得从process继承。

`ProcessClaimKnowledgeV1.subjectKeys/objectKeys`与source `BusinessProcessHypothesisV2.processClaims[].subjectKeys/objectKeys`保持plural且逐字相等（包括空数组、canonical顺序和集合基数）；`predicateKey`逐字复制。P2 `NARROW`只能影响整条claim的admission disposition/certainty或保留的claim集合，因为当前review wire没有replacement key payload；它不得把plural keys任选一个、生成笛卡尔积、拆成多个knowledge claims、合并重复语义或改写顺序。于是每个admitted source claim恰映射一个knowledge claim且信息无损。

### 5.3 Step 07显式无环identity DAG

经用户直接确认，过程semantic ID只覆盖不含later child/back-reference的semantic projection；完整final wire仍进入standalone artifact SHA、semantic file root与receipt，M3逐项验证excluded refs。除下表明确字段外不得排除；没有alias、dual-write或旧循环公式兼容路径。

| record / self ID | semantic projection | 从semantic ID精确排除 | projection中必须先存在的reference字段 |
| --- | --- | --- | --- |
| `ProcessAdmissionDecisionV1.processAdmissionDecisionId` | §5.2全部字段减排除列 | `processAdmissionDecisionId,businessProcessId,processAlternativeIds,pendingConfirmationIds` | `businessProcessHypothesisId,processInterpretationDispositionId,p1TaskId,p1RoundId,p2TaskId,p2RoundId,processHypothesisReviewId,claimDecisions,memberFlowSliceIds,candidateRelationIds,gapIds` |
| `BusinessProcessKnowledgeV1.businessProcessId` | §5.2字段中`sourceBusinessProcessHypothesisId,processAdmissionDecisionId,nameKey,certainty,gapIds` | `businessProcessId,purposeClaimId,endResultClaimId,activityIds,relationIds,membershipIds,roleIds,stateIds,processClaimIds,alternativeIds,pendingConfirmationIds` | `sourceBusinessProcessHypothesisId,processAdmissionDecisionId,nameKey,gapIds` |
| `ProcessActivityKnowledgeV1.processActivityId` | §5.2全部字段减排除列 | `processActivityId,businessProcessIds,roleIds,stateIds` | `sourceProcessClaimId,processAdmissionDecisionId,activityKey,memberFlowSliceIds,inputObjectKeys,outputObjectKeys,gapIds` |
| `ProcessClaimKnowledgeV1.processClaimKnowledgeId` | §5.2全部字段减排除列 | `processClaimKnowledgeId` | `sourceProcessClaimId,processAdmissionDecisionId,processHypothesisReviewId,subjectKeys,predicateKey,objectKeys,memberFlowSliceIds,candidateRelationIds,processJoinSignalIds,processSemanticCueIds,counterSignalIds,blockingCounterSignalIds,factIds,proofIds,evidenceNodeIds,gapIds` |
| `ProcessAlternativeKnowledgeV1.processAlternativeId` | §5.2全部字段减排除列 | `processAlternativeId,mutuallyExclusiveWithAlternativeIds` | `sourceProcessClaimIds,processAdmissionDecisionId,memberFlowSliceIds,candidateRelationIds,gapIds` |
| `PendingConfirmationV1.pendingConfirmationId` | §5.2全部字段减排除列 | `pendingConfirmationId` | `sourceProcessClaimIds,processAdmissionDecisionId,subjectKey,questionKey,memberFlowSliceIds,processJoinSignalIds,processSemanticCueIds,counterSignalIds,blockingCounterSignalIds,gapIds` |
| `ProcessRelationKnowledgeV1.processRelationId` | §5.2全部字段减排除列 | `processRelationId` | `sourceProcessClaimId,processAdmissionDecisionId,fromActivityId,toActivityId,conditionClaimIds,supportCandidateRelationIds,processJoinSignalIds,processSemanticCueIds,counterSignalIds,blockingCounterSignalIds,gapIds` |
| `ProcessMembershipV1.processMembershipId` | §5.2全部字段减排除列 | `processMembershipId` | `flowSliceId,businessProcessId,activityIds,gapIds` |
| `RoleKnowledgeV1.roleId` | §5.2全部字段减排除列 | `roleId` | `sourceProcessClaimId,processAdmissionDecisionId,roleKey,businessProcessIds,activityIds,responsibilityClaimIds,gapIds` |
| `StateKnowledgeV1.stateId` | §5.2全部字段减排除列 | `stateId` | `sourceProcessClaimId,processAdmissionDecisionId,stateKey,objectKey,producerActivityIds,checkerActivityIds,processJoinSignalIds,gapIds` |

`ProcessClaimDecisionV1`和`RegistryOrTechnicalKeyV1`没有self ID；其完整值参加拥有record的projection。`ProcessRelationKnowledgeV1.conditionClaimIds`与`RoleKnowledgeV1.responsibilityClaimIds`都逐字引用已计算的`processClaimKnowledgeId`，不能引用上游裸claim ID冒充知识ID。

唯一合法计算/物化顺序为：Step 06全部task shards/dispositions、typed Gap carriers及model-safe分支的hypothesis/task/round/review与证据IDs → no-model/P1 terminal/P2 GAP-or-FAILED reasoned exclusions（均无process admission）和P2-reviewed admission-eligible hypothesis的process admission semantic ID → business-process、activity、claim、alternative、pending semantic IDs（同rank）→ relation、membership、role、state IDs → 回填admission的三个excluded output字段、business-process的十个excluded child/claim字段、activity的三个excluded association字段及alternative mutual refs → admission wrapper/conflict/`MergedGapV3` standalone IDs → `RepositoryBusinessKnowledgeV4.artifactId` → `KnowledgeAccountingV3.artifactId`。任何child不得在自己的semantic projection中引用一个尚未计算的parent/peer ID。

excluded字段必须闭合：admission的`businessProcessId`非null时，目标process必须反向携带同一admission ID；alternative/pending集合必须等于以该admission为source且被decision保留的精确IDs。BusinessProcess purpose/end refs必须指向其`processClaimIds`中的对应knowledge claim；其八个child arrays必须等于反向引用该process/admission的规范集合。Activity的process/role/state集合必须等于反向引用集合。Alternative mutual refs必须无self、双向对称。任何遗漏、额外或不对称均fatal，且改变完整artifact SHA/root。

各semantic ID公式固定为`<prefix> + lowercaseHex(SHA-256(frame(UTF8(<domain>)) || frame(canonicalJson(semanticProjection))))`，prefix/domain为：

~~~text
process-admission-decision: / repository-knowledge-process-admission-decision-id-v1
business-process: / bp-knowledge-v1
process-activity: / process-activity-v1
process-relation-knowledge: / process-relation-v1
process-membership: / process-membership-v1
role-knowledge: / role-knowledge-v1
state-knowledge: / state-knowledge-v1
process-claim-knowledge: / process-claim-knowledge-v1
process-alternative: / process-alternative-v1
pending-confirmation: / pending-confirmation-v1
~~~

五个standalone root `KnowledgeAdmissionDecisionRecordV5`、`RepositoryBusinessKnowledgeV4`、`KnowledgeConflictV3`、`KnowledgeAccountingV3`、`MergedGapV3`仍按各自`STANDALONE_JSON` policy排除且只排除`artifactId`，并覆盖已经完成back-reference校验的完整final record；因此semantic projection exclusions不会传播到artifact identity。standalone JSON对象按§5.2字段顺序；JSONL先按`decisionScope`（`FLOW`在前）再按对应decision ID；九数组按自身ID、内部ID数组按UTF-8 bytewise排序去重。业务展示顺序只由Step 08显式表达。

## 6. 五项semantic文件加receipt

| 文件 | 唯一职责 |
| --- | --- |
| `knowledge-admission-decisions.jsonl` | 每条Flow一个`FlowAdmissionDecisionV2`，每个P2-reviewed且retained/narrowed/pending的hypothesis一个`ProcessAdmissionDecisionV1`；这是唯一admission文件 |
| `repository-business-knowledge.json` | 恰一份`RepositoryBusinessKnowledgeV4`，含§5.1九个过程数组 |
| `knowledge-conflicts.jsonl` | local/process claim冲突、备选、deterministic resolution与保留理由 |
| `knowledge-accounting.json` | `RepositoryKnowledgeCoverageV3`与嵌套`RepositoryCoverageLedgerDraftV3` |
| `merged-gaps.json` | `MergedGapV3` canonical Gap及member provenance；Step 06-owned Gap为singleton、逐字段可逆 |
| `repository-knowledge-receipt.json` | 五semantic descriptors、上游refs、root、status、Gap refs；最后创建 |

不得为历史admission basename双写、建立兼容别名或把第二份admission文件加入正式清单。Step 07仍是六文件，因此全run总数仍是**57**。

公开schema/type pair至少更新为：

~~~text
repository-knowledge-admission-decision-v5 / REPOSITORY_KNOWLEDGE_ADMISSION_DECISION
repository-knowledge-business-knowledge-v4 / REPOSITORY_KNOWLEDGE_BUSINESS_KNOWLEDGE
repository-knowledge-conflict-v3 / REPOSITORY_KNOWLEDGE_CONFLICT
repository-knowledge-accounting-v3 / REPOSITORY_KNOWLEDGE_ACCOUNTING
repository-coverage-ledger-draft-v3 / REPOSITORY_COVERAGE_LEDGER_DRAFT
repository-knowledge-merged-gap-v3 / REPOSITORY_KNOWLEDGE_MERGED_GAP
~~~

## 7. 合并、冲突与外部效果

技术anchor优先级固定：

1. proven SQL table/column或FQN type；
2. Flow/Outcome ID；
3. exact method/field/parameter graph endpoint；
4. Proof-backed equivalence edge。

`tenantId`、审计字段、日志、generic utility、方法名/中文名相似不能证明对象同一性或过程关系。静态Mapper/XML可证明结构存在；generic Java boundary call可证明target、参数来源与control context。两者均不能在缺少专门Proof时证明数据库、消息、库存、账务、日志或配置已经发生外部影响。相应claim必须`PENDING_CONFIRMATION`并保留Gap。

equal-priority冲突不得按语言流畅度决胜：兼容项合并，相斥项进入`processAlternatives`，证据不足项进入`pendingConfirmations`，违反Fact/Proof或引用越界项REJECT。所有semantic item恰一owner或`REASONED_EXCLUSION`，不可成为遗漏垃圾桶。

## 8. Accounting与下游保证

`RepositoryCoverageLedgerDraftV3`仍嵌在`knowledge-accounting.json`，不是独立文件。除前版全部source/site/entry/graph/fact/flow/local interpretation/meaning/owner集合外，必须加入：

~~~text
processEvidenceGroupIds[]!
processCandidateRelationIds[]!
processTaskShardIds[]!
processModelTaskIds[]!
processModelRoundIds[]!
businessProcessHypothesisIds[]!
processInterpretationDispositionIds[]!
processAdmissionDecisionIds[]!
businessProcessIds[]!
processActivityIds[]!
processRelationIds[]!
processMembershipIds[]!
roleIds[]!
stateIds[]!
processClaimIds[]!
processAlternativeIds[]!
pendingConfirmationIds[]!
~~~

至少验证：

~~~text
flowSliceIds ↔ evidenceCapsuleIds
flowSliceIds ↔ flowAdmissionDecision.flowSliceIds
publishedBusinessProcessHypothesisIds = admissionEligibleHypothesisIds ⊎
                                        p2GapHypothesisIds ⊎ p2FailedHypothesisIds
admissionEligibleHypothesisIds = sorted exact set of d.businessProcessHypothesisId
                                   over all ProcessAdmissionDecisionV1 d
p2GapHypothesisIds ⊆ reasonedSemanticExclusionIds
p2FailedHypothesisIds ⊆ reasonedSemanticExclusionIds
claims and owner relations of p2Gap/p2Failed hypotheses ⊆ reasonedSemanticExclusionIds
step06OwnedGapIds ↔ singleton MergedGapV3.canonicalGapIds
step06OwnedGapIds ↔ singleton MergedGapV3.memberGapIds[0]
each singleton MergedGapV3.affectedBusinessProcessIds = exact nonnull admitted process IDs whose decision gapIds contains its canonicalGapId
candidateRelationIds = disjoint union(ownerCandidateRelationIds over processTaskShardIds)
processTaskShardIds = modelSafeTaskShardIds ⊎ noModelTaskShardIds
processInterpretationDisposition.taskShardIds = processTaskShardIds
noModelOwnerCandidateRelationIds ⊆ reasonedSemanticExclusionIds
noModelOwnerCandidateRelationIds carry the owning shard gapIds
processEvidenceGroups cover exactly flowSliceIds
every admitted process claim has exactly one certainty
every flowSliceId -> nonempty processMembershipIds
businessProcessIds ↔ admitted/admit-with-pending ProcessAdmissionDecision.businessProcessIds
processSemanticItemIds = ownerSemanticItemIds ⊎ reasonedSemanticExclusionIds
repositoryKnowledgeArtifacts = exactlyOne
~~~

Step 08只读本步骤五semantic files、receipt和前序typed refs；不读raw model text、不回源码、不调用Provider。它能沿以下完整链回放已准入process reader item：

`ReaderItem → ProcessKnowledge → ProcessAdmissionDecision → BusinessProcessHypothesis → ProcessInterpretationDisposition → P1/P2 task、round、receipt → ProcessEvidenceGroup / Signal → Flow / EvidenceCapsule → Fact / Proof / Evidence / Source`。

P2 GAP/FAILED的`GAP_QUESTION`则可沿独立链回放：`ReaderItem → MergedGapV3 → BusinessProcessHypothesisV2 → ProcessInterpretationDispositionV2 → P1/P2 task、round、receipt → ProcessEvidenceGroup / Signal → Flow / EvidenceCapsule → Fact / Proof / Evidence / Source-or-searched-scope`，其中不得出现ProcessAdmissionDecision、ProcessKnowledge或review。

P1 GAP/FAILED导致P2 `NOT_RUN_UPSTREAM_FAILED`时使用另一条链：`ReaderItem → MergedGapV3 → ProcessInterpretationDispositionV2 → P1 task/round/receipt → planned P2 task → ProcessEvidenceGroup / Signal → Flow / EvidenceCapsule → 实际Fact / Proof / Evidence / Source-or-searched-scope`；P1 FAILED的owner必须是Step 06唯一`PROCESS_P1_HYPOTHESIS_FAILED` singleton，不得缺Gap，也不得伪造hypothesis、process admission/knowledge或P2 round/receipt/review。

下游得到的保证是：每条Flow有局部准入结果；每个admission-eligible hypothesis有过程准入结果；P2 GAP/FAILED hypothesis有typed Gap及reasoned exclusion；每个no-model shard有显式Step 06 disposition且其owner edge被reasoned exclusion覆盖；每个admitted claim有certainty；冲突/备选/pending未被隐藏；每条Flow有至少一个process/independent/unassigned membership；全仓只有一个knowledge。

## 9. 成功、Gap与fatal

### 成功

- 全Flow local decisions total；全部admission-eligible hypothesis有process decision，P2 GAP/FAILED hypothesis有唯一typed exclusion，P1 FAILED有唯一canonical failure Gap/exclusion；九个过程数组引用闭合；唯一knowledge与六文件receipt-last安装。
- 0 Flow仍发布一份technical/Gaps knowledge、空decisions/过程数组和完整六文件。
- `COMPLETE_CAPTURE`且所有集合式闭合时draft可`closedThroughRepositoryKnowledge=true`；合法Gap不等于coverage不闭合。

### 带Gap成功

- model-ineligible、local/process typed GAP/FAILED、孤立Flow、冲突信号、不同业务对象、外部效果未证明、P2 pending均有显式Gap/fallback/alternative/pending/membership或reasoned-exclusion处置。
- `BOUNDED_PATH_SET`必须`closedThroughRepositoryKnowledge=false`并给`BOUNDED_PATH_SET_NOT_REPOSITORY_COMPLETE`，但仍可发布可审计知识。

### Fatal

- eligible/ineligible/local disposition集合不等，或Step 06 task/round/receipt/disposition计数与引用不闭合；
- process hypothesis六路分区缺/重、P2 GAP/FAILED仍建admission/review、P2扩张未被上游拒绝、admitted claim无certainty或numeric confidence出现；
- Step 06-owned Gap缺typed carrier、被多条`MergedGapV3`消费、mapping字段不等或searched scope丢失；
- Flow没有任何membership、invalid nullable组合、business process/activities/relations dangling；
- display/name作为identity、外部效果无Proof却标confirmed、equal-priority conflict被静默覆盖；
- double/no owner、draft自引用/未来引用、count相等但ID sets不同、partial publication。

稳定codes至少包括：`PROCESS_ADMISSION_COVERAGE_BROKEN`、`PROCESS_CLAIM_REFERENCE_INVALID`、`PROCESS_CLAIM_CERTAINTY_INVALID`、`PROCESS_CONFLICT_UNRESOLVED`、`PROCESS_MEMBERSHIP_NOT_TOTAL`、`PROCESS_KNOWLEDGE_REFERENCE_INVALID`，并沿用`FLOW_ADMISSION_COVERAGE_BROKEN`、`TECHNICAL_FALLBACK_NOT_TOTAL`、`ANCHOR_MERGE_AMBIGUOUS`、`KNOWLEDGE_OWNER_INVALID`、`REPOSITORY_COVERAGE_DRAFT_INVALID`。

## 10. Luna RED指南

- local fixtures覆盖eligible/ineligible五个decision variants、R0/R1/R2 disposition闭包、fallback 0/1/2、basis expansion和0 Flow。
- process fixtures覆盖P1/P2 keep/narrow/drop/pending/GAP/FAILED、missing/duplicate decision、cross-shard ref、counter完整union（含不同业务对象）、三种certainty、numeric confidence拒绝和external-effect unproven。
- 对每类Step 06 process Gap（含P1 FAILED）做singleton `MergedGapV3` mutation：断言`canonicalGapId=g.gapId=memberGapIds[0]`，code/affected IDs/evidence/searched scope/message/nullable failure-limit字段逐项不等、丢member或重复member均fail closed；另以同一counter-scope Gap分别覆盖“被pending admitted decision携带→非空affected process IDs”和“无admitted decision→空集合”，P2 GAP/FAILED不得产生process admission或knowledge。
- synthetic七Flow fixture必须得到至少两个BusinessProcess并复用一个Flow；另测每条Flow仅`INDEPENDENT_ACTIVITY`和显式Gap的`UNASSIGNED_PENDING`。
- DepotHead fixture只能产生静态边界knowledge和pending external-effect Gap，不能出现“已更新”。
- coverage测试逐集合验证57总数相关Step 06 IDs、九个新数组、唯一owner/knowledge、旧文件名不存在、输入/并发顺序不改变bytes。
- 仅运行直接覆盖M1–M3的targeted tests；不用网络、live Provider或全套件。

## 11. Terra GREEN指南

- 仅在Sol/ultra合同与Luna RED冻结后实现，保持零Provider和三个module；不要改公开`RepositoryAnalysisAgent`。
- 先升级M1为all-Flow local total admission、admission-eligible process decisions与terminal/no-model typed exclusions，再升级M2为many-to-many merge，最后升级M3/accounting/filename；不要双写旧文件。
- 所有certainty、decision、membership、claim kind使用closed enum；程序重算证据闭包，reader slots不能成为identity或Proof。
- technical fallback来自冻结registry，外部效果默认Gap；不在线扩词表、不回读源码、不引入numeric score。

## 12. 当前实现差距

| 层 | 当前事实 | 目标合同 |
| --- | --- | --- |
| package | 仅有Wire Reset后的语义package骨架 | M1–M3生产实现与六文件publication |
| local admission | 尚无当前正式all-Flow total map | `FlowAdmissionDecisionV2`覆盖全部Flow |
| process admission | 不存在 | 每个P2-reviewed且retained/narrowed/pending的hypothesis一个`ProcessAdmissionDecisionV1`与三值certainty；P2 GAP/FAILED走typed exclusion |
| knowledge | 不存在完整过程数组/membership | §5.1九数组、Flow多对多total membership、唯一knowledge |
| file/accounting | 旧设计名尚未实现为正式产物 | 只发布`knowledge-admission-decisions.jsonl`，draft v3含过程集合，全run57 |
| sample | 没有当前DepotHead或synthetic运行结果 | fixtures按边界验证；不得把synthetic当真实仓库事实 |

任何改变三值certainty、准入顺序、Flow membership totality、八步/九章/57总数、跨步骤identity或模型边界的实现必须STOP并交Sol/ultra Design Authority；业务目标变化再由用户裁决。
