# 九章文档

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。本步骤仍是`nine-section-document`，固定九章，且是八步工作流的唯一人读Markdown出口。

本文描述目标合同；§13单列当前实现。一个仓库始终只有一份RepositoryKnowledge、一份NineSectionPlan和一个`document.md`。禁止per-Flow Markdown、模型写正文或把Flow片段拼成仓库报告。

## 1. 为什么存在

Step 07已经程序化准入局部meaning与跨Flow BusinessProcess，但结构化JSON不是业务读者的交付物。NineSectionDocument把唯一RepositoryKnowledge转换成固定九章：业务stakeholder先看到端到端过程、角色、活动、条件和待确认；审计者仍能把每个句子回放到source。Chapter 4必须**process-first**，不能退回controller/method清单。

本步骤所有模块和renderer的模型调用数严格为0。它只使用Step 07已准入的知识和受限reader slots；不读取raw P1/P2文本、不重新解释源码。

## 2. 实际上游交接

输入只来自fresh-reopened前七步publication及冻结controls，重点为：

- `knowledge-admission-decisions.jsonl`：local与process准入决定；
- `repository-business-knowledge.json`：唯一knowledge及`businessProcesses`、`processActivities`、`processRelations`、`processMemberships`、`roles`、`states`、`processClaims`、`processAlternatives`、`pendingConfirmations`；
- `knowledge-conflicts.jsonl`、`merged-gaps.json`、`knowledge-accounting.json`和Step 07 receipt；
- `RepositoryCoverageLedgerDraftReferenceV1`、frozen nine-section/template profiles、candidate/trace/archive/validation budgets。

Planner会为Trace验证读取typed upstream references，但正文不得包含内部ID、SHA、源码路径、FQN、schema enum或技术状态枚举。所有这些只留在plan/trace。

### 2.1 真实DepotHead边界

如果知识只证明批量状态入口、guard、允许ID和边界调用参数，Chapter 4可说“系统在满足条件时发起批量状态处理调用”；不得写“数据库已更新”“库存已变化”。未证明的外部效果在Chapter 9完整列为待确认，并由Trace回到原Gap。

### 2.2 明确合成、不是jshERP行为

以下只验证展示能力，不代表jshERP：

`提交补货申请 → 门店审批 → 区域审批并创建采购单 → 采购单审批及费用处理 → 执行采购并登记物流 → 收货并登记库存 → 生成、确认、结算月度账单`。

Chapter 4可按过程概览→活动→转换→角色→备选显示该链，标注费用处理可能并行、退回修改是备选，并复用“收货”Flow于采购履约与月度结算。P2/Step 07已拒绝或pending的“唯一采购单”“已经记账”不得在正文复活。Chapter 9集中列出完整pending依据。

## 3. 四个程序模块

| 模块 | 唯一职责 | 模型调用 |
| --- | --- | --- |
| M1 `NineSectionPlanner` | 从唯一knowledge与coverage draft形成final ledger和九章typed plan | 0 |
| M2 `PlanOnlyRenderer` | 只读plan bytes，按冻结模板输出UTF-8/LF Markdown | 0 |
| M3 `TraceCompiler` | 为每个ReaderItem形成typed lineage | 0 |
| M4 `DocumentPublicationSpecifier` | 组装Candidate、analysis-step archive、root manifest并receipt-last发布 | 0 |

独立run validator是run级模块，不是第五个analysis-step module。它只能重验，不修改Candidate、plan或document。

## 4. 程序过程

1. M1按typed draft reference重开`knowledge-accounting.json`，核对carrier schema/hash、nested draft ID、前七步publication refs和唯一RepositoryKnowledge；单Flow PASS不能替代全仓coverage。
2. Planner为全部local/process semantic items计算唯一`readerItemKey`和section owner；未展示项必须有reasoned exclusion，不能silent loss。
3. Chapter 4固定按BusinessProcess排序，过程内按`overview → activities → transitions → role responsibilities → alternatives/fallbacks`规划。独立活动随后显示；技术Flow/method只可作为从属依据或pending，不能成为主列表。
4. `EVIDENCE_SUPPORTED_INFERENCE`集中使用明确的“根据现有证据推断”标记；`PENDING_CONFIRMATION`在相关章节仅作短提示，在Chapter 9完整展开。`SOURCE_CONFIRMED`也不得越过Proof措辞。
5. M1把Step 07 draft补齐reader IDs/section ownership，形成唯一final `RepositoryCoverageLedgerV4`和`NineSectionPlanV4`；ledger不反向引用plan。
6. M2只重开plan，按deterministic template和escaping规则渲染。相同plan bytes必须产生相同Markdown bytes与SHA；renderer无source/model/registry/network能力。
7. M3用plan中的typed refs编译Trace。每个ReaderItem恰一TraceRecord，路径逐跳校验，不让locator代替Proof。
8. M4 fresh-reopen M1–M3，组装不可变`UNPUBLISHED_CANDIDATE`、validation baseline、archive manifest、analysis-step receipt和root run manifest；public store按payload→archive→receipt安装。
9. 完成后`RepositoryAnalysisAgent`仍只通过现有`start/executeStep/inspect/artifact/render/validate/trace`公开能力观察或复用；不新增process专用public方法。

## 5. 固定九章

每章恰好一次，顺序、key和标题不可改变：

1. `DOCUMENT_GUIDE / 文档说明`
2. `BUSINESS_GOALS / 业务目标`
3. `BUSINESS_OBJECTS / 业务对象`
4. `BUSINESS_ACTIVITIES / 业务活动`
5. `FIELDS_AND_DIMENSIONS / 字段与维度`
6. `OBJECT_RELATIONS / 对象关系`
7. `METRIC_DEFINITIONS / 指标口径`
8. `EXAMPLE_QUESTIONS / 示例问题`
9. `PENDING_CONFIRMATION / 待确认事项`

不得增加第十章、改名或交换。空章使用typed `EMPTY_SECTION`，不能由renderer临时写filler。

### 5.1 Chapter 4 process-first合同

对每个BusinessProcess，先显示目的和端点，再显示活动、转换条件/分支/并行、角色责任、备选/回退。排序依据是稳定process/activity/relation identity和显式stage order；如果顺序只是推断，必须保留推断标记。相同Flow在多个过程中的展示引用同一knowledge identity，不能复制成不同事实。

Controller、Service、Mapper、方法名、SQL ID和路径不是业务活动标题。若只有technical fallback，则归入“独立活动（技术边界）”并附待确认，不得冒充端到端过程。

## 6. ReaderItemV4

既有八种kind继续保留：

~~~text
FACT_SENTENCE
ADMITTED_TERM
TECHNICAL_FALLBACK
GAP_QUESTION
RELATION_REFERENCE
METRIC_REFERENCE
RECORD_REFERENCE
EMPTY_SECTION
~~~

新增且只新增五种过程kind：

~~~text
BUSINESS_PROCESS_OVERVIEW
PROCESS_ACTIVITY
PROCESS_TRANSITION
ROLE_RESPONSIBILITY
PROCESS_ALTERNATIVE
~~~

所有ReaderItem共享同一封闭wire字段集；`?`表示字段必有但值可为null，`[]!`表示字段必有、可为空数组：

~~~text
readerItemKey!, readerItemKind!, templateKey!, typedSlots!
ownerKnowledgeItemId?, knowledgeItemIds[]!
factIds[]!, proofIds[]!, evidenceNodeIds[]!, gapIds[]!, relationIds[]!, metricIds[]!
meaningIds[]!, registryProposalIds[]!, provisionalKeys[]!
interpretationProposalIds[]!, selectedKeys[]!
businessProcessIds[]!, processActivityIds[]!, processRelationIds[]!
processMembershipIds[]!, roleIds[]!, stateIds[]!, processClaimIds[]!
processAdmissionDecisionIds[]!, businessProcessHypothesisIds[]!
processAlternativeIds[]!, pendingConfirmationIds[]!
processJoinSignalIds[]!, processSemanticCueIds[]!, counterSignalIds[]!
certainty?: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION
~~~

只有`EMPTY_SECTION`允许`ownerKnowledgeItemId=null`且要求`certainty=null`、所有lineage数组为空；其他kind两字段都必须非null。五个process-knowledge kind还必须有非空process knowledge、admission、hypothesis与claim lineage。`GAP_QUESTION`有两个封闭scope：local variant的process数组全空；process-terminal variant要求`gapIds`非空、`ownerKnowledgeItemId`等于对应`MergedGapV3.canonicalGapId`、`knowledgeItemIds`包含该ID、`certainty=PENDING_CONFIRMATION`，且business process/process knowledge/admission/claim数组全空。process-terminal variant若源自P2 GAP/FAILED，则`businessProcessHypothesisIds`逐字等于同一disposition的P2-gap或P2-failed集合；若源自P1 GAP/FAILED或NO_MODEL则该数组为空。任何Proof/Evidence、relation/metric或Gap ref都必须出现在上述对应数组，不能藏进slot prose。

过程kind的template与slot闭集：

| kind | templateKey | exact typed slots |
| --- | --- | --- |
| `BUSINESS_PROCESS_OVERVIEW` | `business-process-overview-v1` | `processName,purpose,start,finish,certainty` |
| `PROCESS_ACTIVITY` | `process-activity-v1` | `process,activity,role,input,output,certainty` |
| `PROCESS_TRANSITION` | `process-transition-v1` | `process,fromActivity,condition,toActivity,certainty` |
| `ROLE_RESPONSIBILITY` | `role-responsibility-v1` | `role,responsibility,process,certainty` |
| `PROCESS_ALTERNATIVE` | `process-alternative-v1` | `process,alternative,when,certainty` |

结构示例（synthetic）：

~~~json
{
  "readerItemKey": "reader-item:sha256…",
  "readerItemKind": "PROCESS_TRANSITION",
  "templateKey": "process-transition-v1",
  "typedSlots": {
    "process": "补货到采购（合成）",
    "fromActivity": "门店审批",
    "condition": "审批通过；根据现有证据推断",
    "toActivity": "区域审批并创建采购单",
    "certainty": "EVIDENCE_SUPPORTED_INFERENCE"
  },
  "businessProcessIds": ["business-process:sha256…"],
  "processRelationIds": ["process-relation-knowledge:sha256…"],
  "processClaimIds": ["process-claim:sha256…"],
  "processAdmissionDecisionIds": ["process-admission-decision:sha256…"],
  "businessProcessHypothesisIds": ["business-process-hypothesis:sha256…"],
  "factIds": ["fact:sha256…"],
  "proofIds": ["proof:sha256…"],
  "evidenceNodeIds": ["evidence:sha256…"],
  "gapIds": [],
  "certainty": "EVIDENCE_SUPPORTED_INFERENCE"
}
~~~

上例为字段投影示意，公共wire还包含共享字段中的空数组；省略的空数组不可在真实record省略。正文必须翻译certainty，不直接显示技术enum。

### 6.1 正文清洁与pending分层

正文禁止显示：内部IDs、SHA、artifact/path、FQN、schemaVersion、`SOURCE_CONFIRMED`等技术enum、prompt/model/provider信息。可读名称来自Step 07受控display/reader slots，不从ID猜测。

- confirmed：直接、克制陈述；
- evidence-supported inference：相邻项目合并为一个清晰标注的推断组，避免每句重复噪音；
- pending：相关章节仅一句短提示，Chapter 9按业务对象/过程归组完整列出缺失证据、counter和确认问题；
- rejected：正文不出现，但plan/accounting保存reasoned exclusion。

## 7. 可观察产物与schema

NineSectionDocument的七项analysis-step文件与一项run-root文件保持不变：

| 文件 | 唯一职责 |
| --- | --- |
| `nine-section-plan.json` | `nine-section-document-nine-section-plan-v4`，九章typed reader AST |
| `document.md` | `nine-section-document-document-markdown-v1`，唯一reader-facing Markdown |
| `trace.jsonl` | `nine-section-document-trace-record-v4`，每个ReaderItem一条typed lineage |
| `candidate.json` | 不可变`UNPUBLISHED_CANDIDATE`及上游roots |
| `validation-baseline.json` | 安装前deterministic checks baseline |
| `nine-section-archive-manifest.json` | 五semantic payload descriptor/root |
| `nine-section-document-receipt.json` | analysis-step receipt，最后创建 |
| `runs/<runId>/run-manifest.json` | 八分析步骤refs、final ledger、唯一knowledge/plan/document/Candidate/result |

M1的module-only `repository-coverage-ledger.json`和plan draft、M2 rendered JSON、M3 trace set、M4 publication及各module receipt不增加正式run清单。Step 08数量不变；Step 06恰增5项后整个run正式artifact总数恰为**57**。

### 7.1 PlanV4完整wire合同

此合同与总体设计§13.2的同名catalog逐字段相同。standalone plan只有`artifactId`一个self ID；Java/API中的`nineSectionPlanId`只是该值的别名，wire不得同时携带两者。

~~~text
NineSectionPlanV4
  schemaVersion!: nine-section-document-nine-section-plan-v4
  artifactType!: NINE_SECTION_DOCUMENT_NINE_SECTION_PLAN
  artifactId!
  repositoryKnowledgeRef!: ArtifactReference
  repositoryInterpretationRegistryRef!: ArtifactReference
  repositoryCoverageLedgerRef!: ArtifactReference
  nineSectionProfileRef!: ArtifactReference
  profileBundleRef!: ArtifactReference
  rendererProfileRef!: ArtifactReference
  repositoryCardinality!: {knowledgeCount!: 1, planCount!: 1, documentCountExpected!: 1}
  sections[9]!: SectionPlanV4
  dispositions[]!: ReaderItemDispositionV4
  coverage!: NineSectionPlanCoverageV4
  readerSemanticItemIds[]!
  sectionOwnerBySemanticItem[]!: SectionOwnerV4
  readerItemIds[]!
  processReaderItemIds[]!
  reasonedExclusionIds[]!

SectionPlanV4
  sectionNumber!: 1..9
  sectionKey!: DOCUMENT_GUIDE | BUSINESS_GOALS | BUSINESS_OBJECTS |
               BUSINESS_ACTIVITIES | FIELDS_AND_DIMENSIONS | OBJECT_RELATIONS |
               METRIC_DEFINITIONS | EXAMPLE_QUESTIONS | PENDING_CONFIRMATION
  title!
  readerItems[]!: ReaderItemV4

ReaderItemV4
  readerItemKey!
  readerItemKind!: TECHNICAL_FALLBACK | EMPTY_SECTION | RECORD_REFERENCE |
                   ADMITTED_TERM | FACT_SENTENCE | RELATION_REFERENCE |
                   METRIC_REFERENCE | GAP_QUESTION | BUSINESS_PROCESS_OVERVIEW |
                   PROCESS_ACTIVITY | PROCESS_TRANSITION | ROLE_RESPONSIBILITY |
                   PROCESS_ALTERNATIVE
  templateKey!
  typedSlots!: ReaderTemplateSlotsV4
  ownerKnowledgeItemId?
  knowledgeItemIds[]!
  factIds[]!
  proofIds[]!
  evidenceNodeIds[]!
  meaningIds[]!
  registryProposalIds[]!
  provisionalKeys[]!
  interpretationProposalIds[]!
  selectedKeys[]!
  gapIds[]!
  relationIds[]!
  metricIds[]!
  businessProcessIds[]!
  processActivityIds[]!
  processRelationIds[]!
  processMembershipIds[]!
  roleIds[]!
  stateIds[]!
  processClaimIds[]!
  processAdmissionDecisionIds[]!
  businessProcessHypothesisIds[]!
  processAlternativeIds[]!
  pendingConfirmationIds[]!
  processJoinSignalIds[]!
  processSemanticCueIds[]!
  counterSignalIds[]!
  certainty?: SOURCE_CONFIRMED | EVIDENCE_SUPPORTED_INFERENCE | PENDING_CONFIRMATION

ReaderTemplateSlotsV4
  TECHNICAL_FALLBACK / technical-scope-v1 -> {display!}
  EMPTY_SECTION / empty-section-v2 -> {sectionKey!, effectiveProfileRef!: ArtifactReference, reasonCode!}
  RECORD_REFERENCE / record-anchor-v1 -> {record!, evidence!}
  ADMITTED_TERM / activity-with-anchor-v1 ->
      {businessTerm!, businessPurpose!, technicalAnchor!, flow!, outcomes[]!}
  FACT_SENTENCE / field-write-v1 -> {inputField!, targetColumn!}
  RELATION_REFERENCE / relation-v1 -> {from!, relation!, to!}
  METRIC_REFERENCE / metric-with-gap-v1 -> {metric!, definitionState!}
  GAP_QUESTION / gap-question-v1 -> {subject!, missingRequirement!}
  BUSINESS_PROCESS_OVERVIEW / business-process-overview-v1 ->
      {processName!, purpose!, start!, finish!, certainty!}
  PROCESS_ACTIVITY / process-activity-v1 ->
      {process!, activity!, role!, input!, output!, certainty!}
  PROCESS_TRANSITION / process-transition-v1 ->
      {process!, fromActivity!, condition!, toActivity!, certainty!}
  ROLE_RESPONSIBILITY / role-responsibility-v1 ->
      {role!, responsibility!, process!, certainty!}
  PROCESS_ALTERNATIVE / process-alternative-v1 ->
      {process!, alternative!, when!, certainty!}

ReaderItemDispositionV4
  semanticItemId!
  disposition!: ADMITTED_TO_READER | REASONED_EXCLUSION
  readerItemKey?
  sectionKey?
  reasonCode?
  gapIds[]!

SectionOwnerV4
  semanticItemId!
  sectionKey!: DOCUMENT_GUIDE | BUSINESS_GOALS | BUSINESS_OBJECTS |
               BUSINESS_ACTIVITIES | FIELDS_AND_DIMENSIONS | OBJECT_RELATIONS |
               METRIC_DEFINITIONS | EXAMPLE_QUESTIONS | PENDING_CONFIRMATION

NineSectionPlanCoverageV4
  semanticItemIds[]!
  ownerSemanticItemIds[]!
  readerSemanticItemIds[]!
  ownedReaderSemanticItemIds[]!
  reasonedExclusionIds[]!
  processKnowledgeItemIds[]!
  processReaderItemIds[]!
  sectionOwnerBySemanticItem[]!: SectionOwnerV4
  traceExpectedReaderItemIds[]!
~~~

`EMPTY_SECTION`要求owner/certainty为null且全部lineage数组为空；其他kind要求owner/certainty非null。`ADMITTED_TO_READER`要求reader/section非null且reason为null；`REASONED_EXCLUSION`要求reader为null、section为null、reason非null并可有Gap。`title`必须与§5固定key逐字配对。所有引用数组去重后UTF-8 bytewise升序；`sections`按number升序，section内ReaderItems按冻结profile的显式business order，不能借上游输入顺序推断。`readerItemKey = "reader-item-v4:" + lowercaseHex(SHA-256(frame(UTF8("reader-item-id-v4")) || frame(canonicalJson(recordWithoutReaderItemKey))))`，preimage覆盖kind/template/slots、全部typed refs和required-nullable certainty。`artifactId`按`STANDALONE_JSON`公式排除且只排除自身；plan identity因此覆盖九章与ReaderItem顺序，Markdown bytes不参与plan identity。

## 8. TraceV4

既有八种traceKind继续与旧ReaderItem对应；新增过程kind统一使用typed `PROCESS_KNOWLEDGE_CLAIM`，其必经链严格为：

~~~text
ReaderItem
→ ProcessKnowledge
→ ProcessAdmissionDecision
→ BusinessProcessHypothesis
→ ProcessInterpretationDisposition
→ P1/P2 task, round, generation receipt
→ ProcessEvidenceGroup / ProcessJoinSignal
→ Flow / EvidenceCapsule
→ Fact / Proof / Evidence / Source
~~~

每个`PROCESS_KNOWLEDGE_CLAIM`正常process Trace至少包含这些hop kinds：

~~~text
READER_ITEM, PROCESS_KNOWLEDGE, PROCESS_ADMISSION_DECISION,
BUSINESS_PROCESS_HYPOTHESIS, PROCESS_INTERPRETATION_DISPOSITION,
PROCESS_MODEL_TASK, PROCESS_MODEL_ROUND,
GENERATION_RECEIPT, PROCESS_EVIDENCE_GROUP, PROCESS_JOIN_SIGNAL,
FLOW_SLICE, EVIDENCE_CAPSULE, FACT, PROOF, EVIDENCE_NODE, SOURCE_EXCERPT
~~~

### 8.1 TraceV4完整wire合同

~~~text
TraceRecordV4
  schemaVersion!: nine-section-document-trace-record-v4
  artifactType!: NINE_SECTION_DOCUMENT_TRACE_RECORD
  traceId!
  readerItemKey!
  traceKind!: FACT_SENTENCE | ADMITTED_TERM | TECHNICAL_FALLBACK | GAP_QUESTION |
              RELATION_REFERENCE | METRIC_REFERENCE | RECORD_REFERENCE | EMPTY_SECTION |
              PROCESS_KNOWLEDGE_CLAIM
  hops[]!: TraceHopV4

TraceHopV4
  IDENTITY {
    identityKind!: READER_ITEM | KNOWLEDGE_ITEM | FLOW_ADMISSION_DECISION |
                   INTERPRETATION_PROPOSAL | REPOSITORY_REGISTRY_ITEM |
                   REGISTRY_PROPOSAL | FLOW_INTERPRETATION_DISPOSITION |
                   PROCESS_KNOWLEDGE | PROCESS_ADMISSION_DECISION |
                   BUSINESS_PROCESS_HYPOTHESIS | PROCESS_INTERPRETATION_DISPOSITION |
                   PROCESS_HYPOTHESIS_REVIEW |
                   PROCESS_MODEL_TASK | PROCESS_MODEL_ROUND | GENERATION_RECEIPT |
                   PROCESS_EVIDENCE_GROUP | PROCESS_CANDIDATE_RELATION |
                   PROCESS_JOIN_SIGNAL | PROCESS_SEMANTIC_CUE | COUNTER_SIGNAL |
                   FLOW_SLICE | EVIDENCE_CAPSULE | FACT | PROOF | EVIDENCE_NODE | GAP,
    id!
  }
  ARTIFACT_REFERENCE {
    referenceRole!: REPOSITORY_BUSINESS_KNOWLEDGE | KNOWLEDGE_ADMISSION_DECISIONS |
                    KNOWLEDGE_CONFLICTS | KNOWLEDGE_ACCOUNTING | MERGED_GAPS |
                    REPOSITORY_INTERPRETATION_REGISTRY | BUSINESS_PROCESS_HYPOTHESES |
                    PROCESS_INTERPRETATION_DISPOSITIONS | PROCESS_MODEL_TASKS |
                    PROCESS_MODEL_ROUNDS | GENERATION_RECEIPTS | PROCESS_EVIDENCE_GROUPS |
                    BUSINESS_FLOW_ARTIFACT | PROVEN_CODE_FACT_ARTIFACT |
                    PROGRAM_GRAPH_ARTIFACT | VERIFIED_SOURCE_INVENTORY | SEARCHED_SCOPE |
                    NINE_SECTION_PROFILE | PROFILE_BUNDLE | RENDERER_PROFILE,
    artifactRef!: ArtifactReference
  }
  SOURCE_EXCERPT {sourceExcerpt!: SourceExcerptV1}
  SECTION {
    sectionKey!: DOCUMENT_GUIDE | BUSINESS_GOALS | BUSINESS_OBJECTS |
                 BUSINESS_ACTIVITIES | FIELDS_AND_DIMENSIONS | OBJECT_RELATIONS |
                 METRIC_DEFINITIONS | EXAMPLE_QUESTIONS | PENDING_CONFIRMATION
  }
  TEMPLATE {templateKey!}
~~~

五个hop variant恰一成立，不存在null payload或开放extra字段。`traceKind=PROCESS_KNOWLEDGE_CLAIM`只配五个process ReaderItem；`GAP_QUESTION`还允许§6定义的process-terminal variant，其他七值只配local ReaderItem。正常reviewed process hop顺序必须是`READER_ITEM → SECTION → TEMPLATE → PROCESS_KNOWLEDGE → PROCESS_ADMISSION_DECISION → BUSINESS_PROCESS_HYPOTHESIS → PROCESS_INTERPRETATION_DISPOSITION → P1 task/round/receipt → P2 task/round/receipt → PROCESS_HYPOTHESIS_REVIEW → PROCESS_EVIDENCE_GROUP → supporting/counter SIGNAL → FLOW_SLICE → EVIDENCE_CAPSULE → FACT → PROOF → EVIDENCE_NODE → SOURCE_EXCERPT-or-SEARCHED_SCOPE`；artifact refs紧邻其拥有identity，Gap可紧邻受影响identity。若review含Step 06 review Gap，还必须在review附近加入原`ProcessInterpretationGapV1.gapId`、其singleton `MergedGapV3.canonicalGapId`和`MERGED_GAPS` artifact ref。每个ReaderItem恰一record，records按`readerItemKey` bytewise升序；hops保持本节分支的语义次序，不排序。

`traceId = "trace-record-v4:" + lowercaseHex(SHA-256(frame(UTF8("trace-record-id-v4")) || frame(canonicalJson(recordWithoutTraceId))))`，排除且只排除`traceId`，所以reader key、kind和完整有序hops都参与identity。`SOURCE_EXCERPT`必须使用统一`SourceExcerptV1`并由validator重验；`SEARCHED_SCOPE`和profile lineage必须是完整ArtifactReference，禁止裸ID、`path:line`或合成excerpt。

若P1 GAP/FAILED使P2为`NOT_RUN_UPSTREAM_FAILED`，该shard没有BusinessProcessHypothesis或ProcessAdmissionDecision；Step 07将其Gap/owner exclusion形成的`GAP_QUESTION` ReaderItem走独立exact branch：`READER_ITEM → SECTION → TEMPLATE → canonical GAP / MERGED_GAPS → PROCESS_INTERPRETATION_DISPOSITION → P1 task/round/receipt → P2 task → PROCESS_EVIDENCE_GROUP → relevant SIGNAL/GAP → FLOW_SLICE → EVIDENCE_CAPSULE → FACT/PROOF/EVIDENCE_NODE/SOURCE_EXCERPT-or-SEARCHED_SCOPE（按Gap实际闭包）`。disposition identity hop必须指向同shard、含`p2TaskDisposition.state=NOT_RUN_UPSTREAM_FAILED`且`upstreamTaskSpecId`等于该P1 task ID的record，并紧邻`PROCESS_INTERPRETATION_DISPOSITIONS` artifact ref；不得伪造BusinessProcessHypothesis、ProcessAdmissionDecision、ProcessKnowledge、P2 round、P2 receipt或`PROCESS_HYPOTHESIS_REVIEW` hop。

若P1 accepted后P2返回typed `P2_GAP | P2_FAILED`，Step 07同样形成process-terminal `GAP_QUESTION`，但exact branch不同：`READER_ITEM → SECTION → TEMPLATE → MergedGapV3 canonical GAP / MERGED_GAPS → BusinessProcessHypothesisV2 / BUSINESS_PROCESS_HYPOTHESES → ProcessInterpretationDispositionV2 / PROCESS_INTERPRETATION_DISPOSITIONS → P1 task/round/receipt → P2 task/round/receipt → PROCESS_EVIDENCE_GROUP → PROCESS_CANDIDATE_RELATION → relevant supporting/counter SIGNAL-or-CUE → FLOW_SLICE → EVIDENCE_CAPSULE → FACT/PROOF/EVIDENCE_NODE → SOURCE_EXCERPT-or-SEARCHED_SCOPE`。hypothesis的`processHypothesisReviewId/finalReviewDecision`必须同时null；disposition分别含该ID于P2-gap/P2-failed集合；P2 round必须是对应terminal response并引用carrier中的原`ProcessInterpretationGapV1.gapId`。本分支明确禁止`PROCESS_KNOWLEDGE`、`PROCESS_ADMISSION_DECISION`和`PROCESS_HYPOTHESIS_REVIEW` hop，却必须保留两个实际调用的round/receipt；把typed P2 FAILED写成NOT_RUN或transport failure同样fatal。

正常`PROCESS_KNOWLEDGE_CLAIM` P2 branch必须包含P2 review/round/receipt。`SOURCE_CONFIRMED`可要求直接Fact/Proof链；推断/pending还必须带support/counter/Gap。任何Gap若`sourceLocators[]`非空则逐项闭合到真实`SOURCE_EXCERPT`；若为空则至少一个`SEARCHED_SCOPE` ArtifactReference必须逐字来自`MergedGapV3.searchedScopeRefs[]`，Trace在此终止且不得合成excerpt。source location只在完整Candidate/run validation之后对外返回，且不是Proof。

真实DepotHead的外部效果问题走`GAP_QUESTION → canonical Gap → searched source scope`，不得从Mapper/XML locator补成write。synthetic过程Trace必须显式标识fixture source，不得指向jshERP source。

## 9. Renderer与公开接口边界

renderer内部唯一接口：

~~~java
byte[] render(NineSectionPlanArtifact exactPlan);
~~~

它只能读一个已验证plan：UTF-8、LF、final LF、固定标题、deterministic template、Markdown escaping。不得读取source、Facts、model rounds、registry、Path或network。

对外公开接口保持且仅保持`RepositoryAnalysisAgent`：

~~~text
start(AnalysisRunRequest)
executeStep(AnalysisStepExecutionRequest)
inspect(...)
artifact(...)
render(...)
validate(...)
trace(...)
~~~

CLI与loopback HTTP只是同义adapter。`executeStep`使用显式、连续、已验证的上游publication refs创建新run；不是旧run恢复。观察方法模型调用数为0。active v0仍无resume、retry、Provider switching或API fallback。

## 10. Coverage、成功、Gap与fatal

final `RepositoryCoverageLedgerV4`保留Step 07 draft全部集合，并新增/验证：

~~~text
readerSemanticItemIds = ownedReaderIds ⊎ reasonedReaderExclusionIds
processKnowledgeItemIds -> nonempty processReaderItemIds or reasoned exclusion
readerItemIds ↔ traceRecord.readerItemIds
sectionOwnerBySemanticItem is a total function over readerSemanticItemIds
sectionKeys = exact ordered fixed nine keys
repositoryKnowledge : nineSectionPlan : document = 1 : 1 : 1
formal run artifact count = 57
~~~

### 成功

- `COMPLETE_CAPTURE`、repository completion eligible、ledger `closed=true`、九章恰一次、所有semantic items有owner/disposition、plan-only rerender逐字一致、Trace闭合、八项出口原子安装。
- 无Gap映射`COMPLETE`；有合法Gap但完整分母仍闭合映射`COMPLETED_WITH_GAPS`。

### 诊断成功但不可Selection

- `BOUNDED_PATH_SET`映射`INCOMPLETE_SCOPE`；完整capture但coverage未闭合映射`INCOMPLETE_COVERAGE`。
- 两者可生成诚实九章与immutable Candidate，但不是repository completion，不能因单Flow PASS升级。

### 带Gap内容

- 无过程的Flow显示为独立活动或Chapter 9 unassigned pending；
- counter、alternative、inference和外部效果未证明按§6.1分层；
- 0 Flow仍有九章plan/document/trace/manifest，业务章用typed EMPTY_SECTION，Chapter 9说明范围/Gaps。

### Fatal

- 九章少/多/乱序/改名，Chapter 4以controller/method为顶层清单；
- process knowledge漏ReaderItem/Trace，pending被confirmed、正文泄漏ID/SHA/path/enum；
- renderer读取plan外材料或相同plan输出不同bytes；
- reviewed-process Trace跳过process admission/P1/P2/review/evidence链，P1 terminal分支伪造未运行P2 round，P2 GAP/FAILED分支漏实际P2 round/receipt或伪造review/admission/knowledge，locator冒充Proof；
- Step 06-owned Gap缺`MergedGapV3` singleton mapping，或无source locator时Trace既无`SEARCHED_SCOPE`又合成excerpt；
- coverage count/集合/owner/cardinality不闭合、per-Flow Markdown、identity cycle、partial install、57总数漂移。

稳定codes至少包括：`PROCESS_READER_ITEM_INVALID`、`PROCESS_READER_COVERAGE_BROKEN`、`PROCESS_TRACE_CLOSURE_BROKEN`、`PROCESS_CERTAINTY_RENDERING_INVALID`、`PENDING_CONFIRMATION_DISCLOSURE_INVALID`，并沿用`NINE_SECTION_INVALID`、`SECTION_OWNER_INVALID`、`READER_ITEM_INVALID`、`BODY_CLEANLINESS_FAILED`、`DOCUMENT_HASH_MISMATCH`、`REPOSITORY_COVERAGE_LEDGER_INVALID`、`TRACE_CLOSURE_BROKEN`、`RUN_MANIFEST_INVALID`。

## 11. Luna RED指南

- 九章少/多/乱序/改名、EMPTY_SECTION、0 Flow、双Flow只生成一个plan/document。
- synthetic七Flow fixture：Chapter 4顺序为overview/activity/transition/role/alternative；同一Flow在两process复用；“唯一采购单”“已经记账”不出现；每个过程item有完整Trace。
- DepotHead fixture：只陈述guard/ID/边界调用，external effect短提示+Chapter 9完整pending；任何“已更新”使测试失败。
- 五种新ReaderItem逐个检查exact template/slots/shared refs；三certainty渲染、inference grouping、pending两层披露、正文ID/SHA/path/enum清洁。
- Trace mutation覆盖ProcessKnowledge/Admission/Hypothesis/P1/P2/group/signal/Flow/Capsule/Fact/Proof/Evidence/source每一hop；P2 NOT_RUN不能有round/receipt，P2 GAP/FAILED必须有round/receipt且不能有review/admission/knowledge。
- 为counter-scope、budget、P2 review Gap和P2 GAP/FAILED逐一验证`ProcessInterpretationGapV1 → MergedGapV3 → GAP_QUESTION → Trace`；source locator为空时只能走至少一个exact `SEARCHED_SCOPE`。
- plan-only capability测试让renderer无法取得source/model/registry；相同plan bytes/SHA稳定。coverage测试断言57、1:1:1、owner total和partial-install fail-closed。
- 只运行直接覆盖M1–M4/validator/public contract的targeted tests；不跑全suite、live provider或network。

## 12. Terra GREEN指南

- 仅在Sol/ultra合同和Luna RED冻结后实现；保持八步骤、九标题和公开`RepositoryAnalysisAgent`不变。
- 先升级M1的process ReaderItems/final ledger，再做M2 templates/body cleanliness，再做M3 TraceV4，最后M4/archive；不要让renderer补知识。
- Chapter 4只从Step 07过程数组编译；没有BusinessProcess时走独立活动/pending，不按方法名猜过程。
- process IDs和技术enum只留plan/Trace；正文使用受控slots与certainty翻译。模板/escaping可内部选择，字段/kind/lineage不可改变。

## 13. 当前实现差距

| 层 | 当前事实 | 目标合同 |
| --- | --- | --- |
| document/runtime | Wire Reset后只有package骨架，没有正式九章、Trace、Candidate或run manifest | M1–M4、validator与八项出口 |
| planner | 尚无process-first计划 | PlanV4、13种ReaderItem、Chapter 4过程优先 |
| renderer | 尚无plan-only生产实现 | 零模型、确定性模板、正文清洁与certainty分层 |
| trace | 尚无当前Trace closure | TraceV4完整跨Flow链，NOT_RUN不伪造round |
| public interface | 当前目标仍是run-centric interface，未完整实现 | 保持七方法，不增加process专用API |
| examples | 没有当前DepotHead或synthetic输出 | 只作fixture；synthetic永不冒充jshERP |

任何改变固定九章、Chapter 4 process-first含义、P1/P2唯一多Flow边界、57总数、Trace主链、八步workflow或公开接口的实现必须STOP并交Sol/ultra Design Authority；业务目标变化再由用户裁决。
