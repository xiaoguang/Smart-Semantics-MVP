# 仓库知识

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。本步骤仍是`repository-knowledge`，固定输出五项semantic payload加receipt，不新增分析步骤。

本文把目标合同与§12当前实现分开。`Flow`是入口局部活动，`BusinessProcess`是跨Flow端到端过程，二者多对多；模型hypothesis永远先经程序准入，才可能成为RepositoryKnowledge。

## 1. 为什么存在

Step 06的局部解释和跨Flow过程都是受限模型提案，不自动成为业务事实。RepositoryKnowledge用零模型调用完成唯一权威合并：

1. 准入每条Flow的局部meaning或technical fallback；
2. 验证每个process claim的证据闭包与certainty；
3. 比较冲突、备选和待确认，不强行选唯一故事；
4. 建立Flow ↔ BusinessProcess多对多membership；
5. 生成全仓唯一一份RepositoryKnowledge。

顺序固定为`local admission → process claim validation → conflict/alternative comparison → many-to-many membership → one knowledge`。本步骤没有Provider参数，运行时模型调用数严格为0。

## 2. 实际上游交接

只读取fresh-reopened、receipt-verified正式产物：

- Step 03 Facts、Proofs、Evidence与Gaps；
- Step 05全部Flow/Capsule、Outcomes、coverage和`processJoinSignals`；
- Step 06十四项semantic payload与receipt，包括局部R0/R1/R2、过程groups/P1/P2、hypotheses和两类dispositions；
- frozen `RepositoryInterpretationRegistry`、`TechnicalDisplayRegistry`、profile、budget和前六步publication refs。

程序必须重算Step 06的完整合同：

~~~text
planned model tasks = E + 2R + 2S
actual calls = E + R + accepted local R1 + S + accepted process P1
processEvidenceGroups cover exactly all N Flow IDs
candidateRelationIds = disjoint union of shard ownerCandidateRelationIds
~~~

model-ineligible Flow在局部FlowInterpretation task/round/candidate/disposition中仍为零，但可出现在确定性`ProcessEvidenceGroup`；过程Gap不能制造局部meaning。

### 2.1 真实DepotHead边界

DepotHead静态材料可支持“批量状态入口读取目标状态、校验允许ID、把参数传给边界调用”。如果没有Mapper/XML、事务后置读取或外部响应Proof，RepositoryKnowledge必须把“数据库已经更新”“库存改变”“写入日志/配置”保留为待确认，不能由业务词、方法名或SQL锚点推断外部效果。

### 2.2 明确合成、不是jshERP行为

以下仅是合同fixture：

`提交补货申请 → 门店审批 → 区域审批并创建采购单 → 采购单审批及费用处理 → 执行采购并登记物流 → 收货并登记库存 → 生成、确认、结算月度账单`。

Step 07可准入有证据的活动/对象/角色和某些转换；把并行费用处理、退回修改等保存为备选；把无Proof的“唯一采购单”“已经记账”降为`PENDING_CONFIRMATION`或拒绝。一个收货Flow可同时加入“采购履约”和“月度结算”过程。示例永远不得被标记为jshERP事实。

## 3. 三个程序模块

| 模块 | 唯一职责 | 模型调用 |
| --- | --- | --- |
| M1 `ProposalAdmissionEngine` | 对全部Flow做局部total admission；验证所有process claims并给出process admission decisions | 0 |
| M2 `AnchoredKnowledgeMerger` | 按Proof-backed技术锚点合并、比较冲突/备选、建立多对多membership与唯一knowledge | 0 |
| M3 `KnowledgePublicationSpecifier` | 重算coverage/accounting，发布五项semantic payload；store receipt-last | 0 |

## 4. 程序过程

1. fresh-reopen前六步并重验refs、root、controls、Step 05 Flow denominator和Step 06十五文件。
2. **Local admission**：按全部`flowSliceId`排序。eligible Flow exact-join一个local disposition；ineligible Flow要求非空ineligibility Gap且Step 06局部objects为零。每条Flow恰一个`FlowAdmissionDecisionV2`。
3. READY local candidate逐跳验证`registryProposalId → provisionalKey → interpretationProposalId → selectedKey`；程序只能与R2相同或更保守。无法准入时使用冻结`TechnicalDisplayRegistry`的total fallback。
4. **Process claim validation**：逐hypothesis核对P1/P2 task、round、统一generation receipt、disposition、group/relation/signal、Flow/Capsule、Fact/Proof/Evidence/source refs及P2非扩张。每个hypothesis恰一个`ProcessAdmissionDecisionV1`。
5. 给每个claim分配恰一certainty：`SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION`。不允许numeric confidence或`HIGH/MEDIUM/LOW`等旁路。
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

`processCertainty`只在`ADMIT | ADMIT_WITH_PENDING`时非null，并与创建的`BusinessProcessKnowledgeV1.certainty`相等；其余decision必须为null。P1/P2 task、round与review refs始终非空，即使decision为alternative/reject，也不能丢失审查谱系。`blockingCounterSignalIds`必须是`counterSignalIds`中`blocking=true`项的精确子集。

certainty规则：

- `SOURCE_CONFIRMED`要求P1响应accepted、P2对该hypothesis/claim为`KEEP | NARROW`、专门Fact与Proof直接支持claim，且没有`blocking=true` counter；
- `EVIDENCE_SUPPORTED_INFERENCE`要求P1响应accepted、P2对同一hypothesis/claim为`KEEP | NARROW`、至少一个经M6验证的`PROVEN_HANDOFF | SHARED_ANCHOR` supporting relation/signal、完整Fact/Proof/Evidence/source闭包，并且没有`blocking=true` counter；任何仅有`SEMANTIC_CUE`、P2 pending/not-run、或仍有blocking counter的claim只能`PENDING_CONFIRMATION`；
- `PENDING_CONFIRMATION`要求非空Gap或counter refs，只能进入pending/alternative，不得用于确定性转换；
- reader wording不能提升certainty；P1/P2文字也不是Fact。

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

过程级`BusinessProcessKnowledgeV1.certainty`是其所有非alternative、非pending核心claims的最保守值：全部core claim均`SOURCE_CONFIRMED`才可confirmed；否则只要全部core claim满足上述inference predicate就是`EVIDENCE_SUPPORTED_INFERENCE`；任一core claim pending、P2 pending/not-run或blocking counter存在即为`PENDING_CONFIRMATION`。`ProcessActivityKnowledgeV1`、`ProcessRelationKnowledgeV1`、`ProcessMembershipV1`、`RoleKnowledgeV1`、`StateKnowledgeV1`、`ProcessClaimKnowledgeV1`和所有Step 08 process ReaderItem同样各有且仅有一个三值`certainty`，不得从父process默认继承或用numeric score替代。

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
  subjectKey!: RegistryOrTechnicalKeyV1
  predicateKey!: RegistryOrTechnicalKeyV1
  objectKey?: RegistryOrTechnicalKeyV1
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

MergedGapV2
  schemaVersion!: repository-knowledge-merged-gap-v2
  artifactType!: REPOSITORY_KNOWLEDGE_MERGED_GAP
  artifactId!
  canonicalGapId!
  memberGapIds[]!
  gapCode!
  gapScope!: LOCAL | BUSINESS_PROCESS | REPOSITORY
  affectedFlowSliceIds[]!
  affectedBusinessProcessIds[]!
  factIds[]!
  proofIds[]!
  sourceLocators[]!
  messageKey!
~~~

discriminator与nullable规则是闭集：`FLOW`只允许`flowDecision`非null，`BUSINESS_PROCESS`只允许`processDecision`非null；`KnowledgeConflictV3.winningSemanticItemId`只在`MERGE | REJECT`时非null；`fromActivityId/toActivityId`仅在关系端点确实未知且该记录为`PENDING_CONFIRMATION`、同时有非空Gap时可null。所有过程记录必须保留其own certainty；不得从process继承。

规范顺序为：standalone JSON对象按以上字段顺序；JSONL先按`decisionScope`（`FLOW`在前）再按对应decision ID；九个数组分别按自身ID bytewise升序；内部ID数组去重后bytewise升序；业务活动展示顺序另由Step 08的显式section/reader order表达，不能借数组输入顺序推断。身份域固定为`repository-knowledge`：root `artifactId = sha256(schemaVersion || artifactType || canonical payload excluding artifactId)`；内部ID分别使用前缀`bp-knowledge-v1`、`process-activity-v1`、`process-relation-v1`、`process-membership-v1`、`role-knowledge-v1`、`state-knowledge-v1`、`process-claim-knowledge-v1`、`process-alternative-v1`、`pending-confirmation-v1`加其全部规范字段。引用字段不能参与另一记录的self-ID之外的隐式身份推导。

## 6. 五项semantic文件加receipt

| 文件 | 唯一职责 |
| --- | --- |
| `knowledge-admission-decisions.jsonl` | 每条Flow一个`FlowAdmissionDecisionV2`，每个process hypothesis一个`ProcessAdmissionDecisionV1`；这是唯一admission文件 |
| `repository-business-knowledge.json` | 恰一份`RepositoryBusinessKnowledgeV4`，含§5.1九个过程数组 |
| `knowledge-conflicts.jsonl` | local/process claim冲突、备选、deterministic resolution与保留理由 |
| `knowledge-accounting.json` | `RepositoryKnowledgeCoverageV3`与嵌套`RepositoryCoverageLedgerDraftV3` |
| `merged-gaps.json` | canonical Gap及member Gap provenance，去重但可逆 |
| `repository-knowledge-receipt.json` | 五semantic descriptors、上游refs、root、status、Gap refs；最后创建 |

不得为历史admission basename双写、建立兼容别名或把第二份admission文件加入正式清单。Step 07仍是六文件，因此全run总数仍是**57**。

公开schema/type pair至少更新为：

~~~text
repository-knowledge-admission-decision-v5 / REPOSITORY_KNOWLEDGE_ADMISSION_DECISION
repository-knowledge-business-knowledge-v4 / REPOSITORY_KNOWLEDGE_BUSINESS_KNOWLEDGE
repository-knowledge-conflict-v3 / REPOSITORY_KNOWLEDGE_CONFLICT
repository-knowledge-accounting-v3 / REPOSITORY_KNOWLEDGE_ACCOUNTING
repository-coverage-ledger-draft-v3 / REPOSITORY_COVERAGE_LEDGER_DRAFT
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
businessProcessHypothesisIds ↔ processAdmissionDecision.hypothesisIds
candidateRelationIds = disjoint union(process shard ownerCandidateRelationIds)
processEvidenceGroups cover exactly flowSliceIds
every admitted process claim has exactly one certainty
every flowSliceId -> nonempty processMembershipIds
businessProcessIds ↔ admitted/admit-with-pending ProcessAdmissionDecision.businessProcessIds
processSemanticItemIds = ownedIds ⊎ reasonedExclusionIds
repositoryKnowledgeArtifacts = exactlyOne
~~~

Step 08只读本步骤五semantic files、receipt和前序typed refs；不读raw model text、不回源码、不调用Provider。它能沿以下完整链回放process reader item：

`ReaderItem → ProcessKnowledge → ProcessAdmissionDecision → BusinessProcessHypothesis → P1/P2 task、round、receipt → ProcessEvidenceGroup / Signal → Flow / EvidenceCapsule → Fact / Proof / Evidence / Source`。

下游得到的保证是：每条Flow有局部准入结果；每个hypothesis有过程准入结果；每个claim有certainty；冲突/备选/pending未被隐藏；每条Flow有至少一个process/independent/unassigned membership；全仓只有一个knowledge。

## 9. 成功、Gap与fatal

### 成功

- 全Flow local decisions total；全hypothesis process decisions total；九个过程数组引用闭合；唯一knowledge与六文件receipt-last安装。
- 0 Flow仍发布一份technical/Gaps knowledge、空decisions/过程数组和完整六文件。
- `COMPLETE_CAPTURE`且所有集合式闭合时draft可`closedThroughRepositoryKnowledge=true`；合法Gap不等于coverage不闭合。

### 带Gap成功

- model-ineligible、local/process typed GAP/FAILED、孤立Flow、冲突信号、外部效果未证明、P2 pending均有显式Gap/fallback/alternative/pending/membership处置。
- `BOUNDED_PATH_SET`必须`closedThroughRepositoryKnowledge=false`并给`BOUNDED_PATH_SET_NOT_REPOSITORY_COMPLETE`，但仍可发布可审计知识。

### Fatal

- eligible/ineligible/local disposition集合不等，或Step 06 task/round/receipt/disposition计数与引用不闭合；
- process hypothesis缺/重处置、P2扩张未被上游拒绝、claim无certainty或numeric confidence出现；
- Flow没有任何membership、invalid nullable组合、business process/activities/relations dangling；
- display/name作为identity、外部效果无Proof却标confirmed、equal-priority conflict被静默覆盖；
- double/no owner、draft自引用/未来引用、count相等但ID sets不同、partial publication。

稳定codes至少包括：`PROCESS_ADMISSION_COVERAGE_BROKEN`、`PROCESS_CLAIM_REFERENCE_INVALID`、`PROCESS_CLAIM_CERTAINTY_INVALID`、`PROCESS_CONFLICT_UNRESOLVED`、`PROCESS_MEMBERSHIP_NOT_TOTAL`、`PROCESS_KNOWLEDGE_REFERENCE_INVALID`，并沿用`FLOW_ADMISSION_COVERAGE_BROKEN`、`TECHNICAL_FALLBACK_NOT_TOTAL`、`ANCHOR_MERGE_AMBIGUOUS`、`KNOWLEDGE_OWNER_INVALID`、`REPOSITORY_COVERAGE_DRAFT_INVALID`。

## 10. Luna RED指南

- local fixtures覆盖eligible/ineligible五个decision variants、R0/R1/R2 disposition闭包、fallback 0/1/2、basis expansion和0 Flow。
- process fixtures覆盖P1/P2 keep/narrow/drop/pending、missing/duplicate decision、cross-shard ref、counter preservation、三种certainty、numeric confidence拒绝和external-effect unproven。
- synthetic七Flow fixture必须得到至少两个BusinessProcess并复用一个Flow；另测每条Flow仅`INDEPENDENT_ACTIVITY`和显式Gap的`UNASSIGNED_PENDING`。
- DepotHead fixture只能产生静态边界knowledge和pending external-effect Gap，不能出现“已更新”。
- coverage测试逐集合验证57总数相关Step 06 IDs、九个新数组、唯一owner/knowledge、旧文件名不存在、输入/并发顺序不改变bytes。
- 仅运行直接覆盖M1–M3的targeted tests；不用网络、live Provider或全套件。

## 11. Terra GREEN指南

- 仅在Sol/ultra合同与Luna RED冻结后实现，保持零Provider和三个module；不要改公开`RepositoryAnalysisAgent`。
- 先升级M1为local+process total admissions，再升级M2为many-to-many merge，最后升级M3/accounting/filename；不要双写旧文件。
- 所有certainty、decision、membership、claim kind使用closed enum；程序重算证据闭包，reader slots不能成为identity或Proof。
- technical fallback来自冻结registry，外部效果默认Gap；不在线扩词表、不回读源码、不引入numeric score。

## 12. 当前实现差距

| 层 | 当前事实 | 目标合同 |
| --- | --- | --- |
| package | 仅有Wire Reset后的语义package骨架 | M1–M3生产实现与六文件publication |
| local admission | 尚无当前正式all-Flow total map | `FlowAdmissionDecisionV2`覆盖全部Flow |
| process admission | 不存在 | 每个hypothesis一个`ProcessAdmissionDecisionV1`与三值certainty |
| knowledge | 不存在完整过程数组/membership | §5.1九数组、Flow多对多total membership、唯一knowledge |
| file/accounting | 旧设计名尚未实现为正式产物 | 只发布`knowledge-admission-decisions.jsonl`，draft v3含过程集合，全run57 |
| sample | 没有当前DepotHead或synthetic运行结果 | fixtures按边界验证；不得把synthetic当真实仓库事实 |

任何改变三值certainty、准入顺序、Flow membership totality、八步/九章/57总数、跨步骤identity或模型边界的实现必须STOP并交Sol/ultra Design Authority；业务目标变化再由用户裁决。
