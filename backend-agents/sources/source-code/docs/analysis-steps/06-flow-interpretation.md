# 流程解释

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。步骤key、目录和对外名称始终是`flow-interpretation`；跨Flow能力是本步骤内部扩展，不是第九步骤。

本文区分两件事：`Flow`是单入口、局部、可审计的代码活动；`BusinessProcess`是可能跨多个Flow的端到端业务过程。二者是多对多关系。所有目标合同均标为目标态；§12单列当前实现，不以规划覆盖事实。

## 1. 为什么存在

BusinessFlows证明每个局部活动“代码中发生了什么”，却不能单凭单个入口回答“多个入口是否共同构成从申请到结算的过程”。FlowInterpretation因此承担两层解释：

1. 既有局部层：R0提出全仓有限业务词表，R1解释一条Flow，R2只复核同一条Flow；R0/R1/R2始终严格单Flow。
2. 新增过程层：程序汇编全仓候选关系和有界组，P1为每个有界任务提出一个或多个`BusinessProcessHypothesis`，P2只能`KEEP | NARROW | DROP | PENDING_CONFIRMATION`。

P1/P2是整个八步工作流中**唯一**允许模型同时看到多个Flow的例外。它们仍只能看程序构造的有界`ProcessEvidenceGroup`，不能读仓库、源码路径、运行日志或别的任务。信号只是候选线索；不能单独证明先后、因果、唯一性或外部系统结果。

## 2. 实际上游交接

本步骤只读取fresh-reopened成功publication：

- Step 05五项semantic artifacts与`business-flows-receipt.json`，尤其是`flow-slices.json`、`evidence-capsules.jsonl`及其中`processJoinSignals`；
- Step 03 `facts.jsonl`、`proofs.jsonl`、`evidence-nodes.jsonl`，仅用于重验Capsule与signal引用闭包；
- exact `analysis-run-request-v2`中的profile、budget、runtime、prompt/schema digest、artifact policy和可选organization seed refs。

设：

~~~text
N = 全部Flow数
E = modelEligibility=ELIGIBLE的Flow数
R = R0处置为READY_FOR_FREEZE的Flow数
C = 程序汇编的候选跨Flow边数
G = 覆盖全部Flow的逻辑ProcessEvidenceGroup数
S = model-safe过程任务分片数

planned model tasks = E + 2R + 2S
actual calls = E + R + accepted local R1 + S + accepted process P1
~~~

`N-E`条ineligible Flow没有R0/R1/R2任务，但仍进入确定性跨Flow分组；如果某组不适合模型，则P1/P2任务为零并以Gap交给Step 07。`G`个组必须不重不漏覆盖全部Flow；一个Flow可在多个最终BusinessProcess中复用，但在候选分组阶段只有一个owner group。

### 2.1 真实、受限的DepotHead例子

真实代码材料只支持谨慎描述：某批量状态入口读取目标状态、检查允许的记录ID，并把参数传到边界调用。若静态材料没有Mapper/XML、事务后置读取或外部响应Proof，就只能保留`EXTERNAL_EFFECT_GAP`；不得说“已更新库存”“已写日志”或“状态已经落库”。这个Flow可能只有`STATE_CHECK`、`BUSINESS_IDENTIFIER_ANCHOR`和`EXPLICIT_CALL`信号，不足以自动推出第二个Flow或端到端顺序。

### 2.2 明确合成、不是jshERP行为的验收例子

以下只用于验证合同，**不代表jshERP事实**：

`提交补货申请 → 门店审批 → 区域审批并创建采购单 → 采购单审批及费用处理 → 执行采购并登记物流 → 收货并登记库存 → 生成、确认、结算月度账单`。

每个箭头两端都是独立入口Flow。程序可依据申请单ID/采购单ID/月账单ID的产出与消费、状态生产与检查、对象/表/字段、显式调用/返回/事件引用形成候选关系；P1可描述顺序、并行、备选和回退，P2必须删除无Proof的“唯一采购单”和“已经记账”。同一“收货”Flow可同时属于采购履约过程和月度结算过程，这正是Flow与BusinessProcess多对多，而不是复制Flow事实。

## 3. 九个内部模块与职责

| 模块 | 性质 | 唯一职责 |
| --- | --- | --- |
| M1 `RegistryTaskCompiler` | 程序 | 为`E`条eligible Flow各编一个R0任务 |
| M2 `RegistryProposalRunner` | Luna/xhigh | 每个R0任务一次Provider调用并验证typed响应 |
| M3 `RegistryFreezer` | 程序 | 等全部R0处置后冻结全仓finite registry |
| M4 `FlowTaskCompiler` | 程序 | 为`R`条R0-ready Flow各编R1与R2 |
| M5 `InterpretationRunner` | Luna/xhigh | 严格单Flow执行R1/R2并形成局部候选 |
| M6 `CrossFlowCandidateCompiler` | 程序 | 全仓确定性汇编候选边与有界`ProcessEvidenceGroup`；不排序业务过程、不调用模型 |
| M7 `BusinessProcessTaskCompiler` | 程序 | 把组切成`S`个有界shard；每条候选边恰有一个owner shard |
| M8 `BusinessProcessInterpretationRunner` | Luna/xhigh | 执行P1/P2；P2不增Flow、边、Fact、Evidence或新过程 |
| M9 `InterpretationPublicationSpecifier` | 程序 | 重验局部与过程任务/响应/引用/count/处置，receipt-last发布十五文件 |

M2、M5、M8之外不得调用Provider。M6/M7/M9的相同输入必须产生逐字相同输出。

## 4. 程序过程

1. M1重验`N=E+(N-E)`、Flow/Capsule双射、Fact/Proof/Evidence/source locator闭包，为每条eligible Flow编R0；R0材料恰是一条Capsule。
2. M2按稳定任务序调用R0。有效proposal须使用同Capsule basis；完成全部`E`条处置后M3才冻结registry。相同label跨Flow不合并。
3. M4仅为`R`条ready Flow编R1/R2。M5先R1；只有R1 `RESPONSE_ACCEPTED`才调用R2。R1 typed GAP/FAILED时仍保留已规划R2并写`NOT_RUN_UPSTREAM_FAILED`。
4. M6按§5的信号等级比较所有Flow，形成`C`条规范候选边；generic-only依据不得成边。再按正向边的连通分量形成组，并为孤立Flow形成singleton组，得到覆盖`N`的`G`组。
5. M7应用固定budget分片。每条candidate edge恰由一个shard拥有；同一Flow可作为只读上下文重复，但不能因此复制edge ownership或扩大其Capsule材料。
6. M8对每个model-safe shard执行一次P1。P1可返回一个或多个hypothesis或typed GAP/FAILED。只有P1 accepted才调用同shard P2；否则仍保留P2计划任务并写`NOT_RUN_UPSTREAM_FAILED`。
7. P2逐个覆盖P1 hypothesis，只能KEEP、NARROW、DROP或PENDING_CONFIRMATION。它不能新增Flow、candidate edge、Fact、Proof、Evidence、registry key或hypothesis。
8. M9重算`N/E/R/C/G/S`和完整集合等式，验证每个local/process task恰有一个处置、每个实际调用恰有round与typed receipt、未调用P2没有round/receipt，然后原子发布。

## 5. 候选关系不是业务事实

M6使用下列四个等级。等级不是模型判断；程序按exact pair rule计算。单条Step 05 signal没有自己的等级，只有满足一整行的跨Flow组合才能形成该等级：

| 等级 | exact kind/source mapping | 允许的程序结论 |
| --- | --- | --- |
| `PROVEN_HANDOFF` | 下列任一proof-closed pair：`EXPLICIT_CALL`匹配另一Flow的exact entry target；`IDENTIFIER_OUTPUT`或`RETURN_TRANSFER`匹配另一Flow的`IDENTIFIER_INPUT`；同一non-generic key的`STATE_PRODUCTION`匹配另一Flow的`STATE_CHECK`；同一event key的`EVENT_REFERENCE(direction=PRODUCES)`匹配`EVENT_REFERENCE(direction=CONSUMES)`。pair两端的十三种positive signal都必须各自闭合到本Flow Fact→atom→Proof→Evidence→source | 建有方向candidate edge；状态生产/消费不得降为较弱等级，但仍不自动证明完整业务因果或外部效果 |
| `SHARED_ANCHOR` | 两Flow存在同`anchorKind+anchorKey`且`DOMAIN_SPECIFIC`的`BUSINESS_OBJECT_ANCHOR | JAVA_TYPE_ANCHOR | SQL_TABLE_ANCHOR | FIELD_ANCHOR | BUSINESS_IDENTIFIER_ANCHOR | OBJECT_REFERENCE`；两端各自Proof闭合 | 建默认无方向的相关性edge；共享表/字段/对象不能推顺序 |
| `SEMANTIC_CUE` | 只来自§5.1的`ProcessSemanticCueV1`：finite frozen Registry business term，并由entry verb或state word的同Capsule basis限定；**任何Step 05结构signal、裸状态写/检查、方法名或中文名都不能直接映射到本级** | 只能建`PENDING_ONLY`弱候选；P1/P2和Step 07不得提升为confirmed transition |
| `COUNTER_SIGNAL` | `COUNTER_CONDITION | CONFLICT_STATE | EXTERNAL_EFFECT_GAP`，或两Flow的domain object/state/key不相等；使用反证Proof或Gap+searched source closure | 附在候选关系上；`blocking=true`时阻止confirmed/inferred transition，不能被正向信号吞掉 |

`tenantId`、创建/修改人等审计字段、日志、generic utility、方法名相似、中文名称相似，**单独都禁止成边**。只有同一个局部信号集合内存在domain-specific正向依据时，它们才可作为附加上下文。缺少专门Proof的外部影响始终是Gap。

### 5.1 Registry语义cue的唯一来源

`ProcessSemanticCueV1`由M6在M3 registry冻结后确定性产生，不回写Step 05，也不是第六项公开artifact。字段与nullable规则固定：

~~~text
processSemanticCueId!
cueKind!: REGISTRY_BUSINESS_TERM | ENTRY_VERB | STATE_WORD
leftFlowSliceId!, rightFlowSliceId!       // UTF-8 ordered; never equal
leftRegistryItemId!, rightRegistryItemId! // both proposalKind=BUSINESS_TERM
leftProvisionalKey!, rightProvisionalKey!
normalizedCueKey!                         // frozen ProcessCueProfile exact normalization
leftBasisAtomIds[]!, rightBasisAtomIds[]! // nonempty; each subset of its Capsule/R0 basis
leftEntryId?, rightEntryId?               // nonnull on both sides iff ENTRY_VERB
leftStateSignalIds[]!, rightStateSignalIds[]! // nonempty on both sides iff STATE_WORD
processCueProfileRef!
pendingOnly=true
~~~

`REGISTRY_BUSINESS_TERM`要求两项finite registry labels得到同一`normalizedCueKey`；`ENTRY_VERB`还要求两项basis各包含本Flow entry-bound atom，且cue key命中冻结`entryVerbLexicon`；`STATE_WORD`还要求两端各有Proof-closed `STATE_PRODUCTION | STATE_CHECK` signal且cue key命中冻结`stateWordLexicon`。entry/state lexicon只是对finite registry term分类，不能从method/中文显示名创建term。cue identity覆盖全部字段；任何registry/basis/profile缺失均不产cue而写Gap。它只能支持pending hypothesis。

规范候选边字段如下；`leftFlowSliceId < rightFlowSliceId`按UTF-8 byte order，direction另存：

~~~json
{
  "candidateRelationId": "process-relation:sha256…",
  "leftFlowSliceId": "flow:store-approval",
  "rightFlowSliceId": "flow:regional-approval",
  "strongestSignalLevel": "PROVEN_HANDOFF",
  "direction": "LEFT_TO_RIGHT",
  "supportingProcessJoinSignalIds": ["process-join-signal:request-id-return"],
  "counterProcessJoinSignalIds": ["process-join-signal:status-conflict"],
  "factIds": ["fact:request-id"],
  "proofIds": ["proof:return-to-input"],
  "evidenceNodeIds": ["evidence:request-id"],
  "gapIds": ["gap:purchase-write-unproven"]
}
~~~

M6不能把edge拓扑排序成“真实顺序”。顺序、并行、替代、回退都是P1 hypothesis，最终由Step 07程序准入。

## 6. 模型有界材料

### 6.1 局部R0/R1/R2

每次只含一个`flowSliceId`及其完整Capsule view：Facts/atoms/Proof IDs、Gaps、Outcomes、连续source excerpts、projection obligations、allowed registry items和budget。发送bytes严格是`canonicalJson(inputJson)`；同一Flow的R0/R1/R2不能看其他Flow。R2只复核R1。

### 6.2 过程P1/P2

每个`BusinessProcessTaskShardV1`必须包含：

~~~json
{
  "taskShardId": "process-shard:sha256…",
  "processEvidenceGroupIds": ["process-group:replenishment"],
  "ownerCandidateRelationIds": ["process-relation:approval-to-order"],
  "contextFlowSliceIds": ["flow:submit", "flow:store-approve", "flow:region-approve"],
  "boundedMaterial": {
    "flowViews": [{"flowSliceId":"flow:submit","factIds":["fact:request-created"],"gapIds":[]}],
    "relationViews": [{"candidateRelationId":"process-relation:approval-to-order","level":"PROVEN_HANDOFF"}],
    "registryKeys": ["TERM_P_…"],
    "limits": {"maxFlows":8,"maxRelations":16,"maxInputBytes":65536,"maxHypotheses":8}
  },
  "p1TaskSpecId": "model-task:process-p1:sha256…",
  "p2TaskSpecId": "model-task:process-p2:sha256…"
}
~~~

P1只可引用该shard的Flow、owner/context relation、signal、Fact/Proof/Evidence/Gap和registry keys。P2收到同一bounded material、P1的accepted response和固定review allowlist；它不能用语言合理性补材料。

`BusinessProcessHypothesisV1`使用以下完整字段/闭合引用specimen。它是明确合成的结构fixture，不代表jshERP；ID值只作schema-valid fixture token，不是从下列展示bytes重算的replay golden：

~~~json
{
  "schemaVersion": "flow-interpretation-business-process-hypothesis-v1",
  "artifactType": "FLOW_INTERPRETATION_BUSINESS_PROCESS_HYPOTHESIS",
  "businessProcessHypothesisId": "business-process-hypothesis:fixture-approval-to-procurement",
  "taskShardId": "process-shard:fixture-approval",
  "p1TaskId": "process-model-task:fixture-p1-approval",
  "p1RoundId": "process-model-round:fixture-p1-approval",
  "processEvidenceGroupIds": ["process-evidence-group:fixture-approval"],
  "memberFlows": [
    {
      "flowSliceId": "flow:store-approve",
      "role": "START",
      "stageKey": {"keyKind":"TECHNICAL","key":"stage:10-store-approval","registryItemId":null,"technicalAnchorIds":["entry:store-approve"]},
      "activityKey": {"keyKind":"REGISTRY","key":"ACTIVITY_P_STORE_APPROVAL","registryItemId":"registry-item:store-approval","technicalAnchorIds":[]},
      "supportingProcessClaimIds": ["process-claim:01-purpose","process-claim:02-store-activity","process-claim:04-transition"]
    },
    {
      "flowSliceId": "flow:region-approve",
      "role": "TERMINAL",
      "stageKey": {"keyKind":"TECHNICAL","key":"stage:20-region-approval","registryItemId":null,"technicalAnchorIds":["entry:region-approve"]},
      "activityKey": {"keyKind":"REGISTRY","key":"ACTIVITY_P_REGION_APPROVAL","registryItemId":"registry-item:region-approval","technicalAnchorIds":[]},
      "supportingProcessClaimIds": ["process-claim:03-region-activity","process-claim:04-transition","process-claim:05-result"]
    }
  ],
  "businessRoleKeys": [
    {"keyKind":"REGISTRY","key":"ROLE_P_STORE_APPROVER","registryItemId":"registry-item:store-approver","technicalAnchorIds":[]},
    {"keyKind":"REGISTRY","key":"ROLE_P_REGION_APPROVER","registryItemId":"registry-item:region-approver","technicalAnchorIds":[]}
  ],
  "stageKeys": [
    {"keyKind":"TECHNICAL","key":"stage:10-store-approval","registryItemId":null,"technicalAnchorIds":["entry:store-approve"]},
    {"keyKind":"TECHNICAL","key":"stage:20-region-approval","registryItemId":null,"technicalAnchorIds":["entry:region-approve"]}
  ],
  "activityKeys": [
    {"keyKind":"REGISTRY","key":"ACTIVITY_P_STORE_APPROVAL","registryItemId":"registry-item:store-approval","technicalAnchorIds":[]},
    {"keyKind":"REGISTRY","key":"ACTIVITY_P_REGION_APPROVAL","registryItemId":"registry-item:region-approval","technicalAnchorIds":[]}
  ],
  "inputObjectKeys": [
    {"keyKind":"TECHNICAL","key":"object:replenishment-request","registryItemId":null,"technicalAnchorIds":["java-type:ReplenishmentRequest"]}
  ],
  "outputObjectKeys": [
    {"keyKind":"TECHNICAL","key":"object:approved-procurement-request","registryItemId":null,"technicalAnchorIds":["java-type:ProcurementRequest"]}
  ],
  "objectKeys": [
    {"keyKind":"TECHNICAL","key":"object:replenishment-request","registryItemId":null,"technicalAnchorIds":["java-type:ReplenishmentRequest"]},
    {"keyKind":"TECHNICAL","key":"object:approved-procurement-request","registryItemId":null,"technicalAnchorIds":["java-type:ProcurementRequest"]}
  ],
  "stateKeys": [],
  "processClaims": [
    {
      "processClaimId": "process-claim:01-purpose",
      "claimKind": "PURPOSE",
      "subjectKeys": [{"keyKind":"TECHNICAL","key":"process:replenishment-approval","registryItemId":null,"technicalAnchorIds":["process-evidence-group:fixture-approval"]}],
      "predicateKey": {"keyKind":"TECHNICAL","key":"purpose:authorize-replenishment","registryItemId":null,"technicalAnchorIds":["fact:replenishment-request-id"]},
      "objectKeys": [{"keyKind":"TECHNICAL","key":"object:replenishment-request","registryItemId":null,"technicalAnchorIds":["java-type:ReplenishmentRequest"]}],
      "memberFlowSliceIds": ["flow:store-approve","flow:region-approve"],
      "candidateRelationIds": [],
      "supportProcessJoinSignalIds": ["process-join-signal:request-id"],
      "processSemanticCueIds": [],
      "counterProcessJoinSignalIds": [],
      "blockingCounterProcessJoinSignalIds": [],
      "factIds": ["fact:replenishment-request-id"],
      "proofIds": ["proof:replenishment-request-id"],
      "evidenceNodeIds": ["evidence:replenishment-request-id"],
      "gapIds": []
    },
    {
      "processClaimId": "process-claim:02-store-activity",
      "claimKind": "ACTIVITY",
      "subjectKeys": [{"keyKind":"REGISTRY","key":"ACTIVITY_P_STORE_APPROVAL","registryItemId":"registry-item:store-approval","technicalAnchorIds":[]}],
      "predicateKey": {"keyKind":"TECHNICAL","key":"activity:checks-request","registryItemId":null,"technicalAnchorIds":["entry:store-approve"]},
      "objectKeys": [{"keyKind":"TECHNICAL","key":"object:replenishment-request","registryItemId":null,"technicalAnchorIds":["java-type:ReplenishmentRequest"]}],
      "memberFlowSliceIds": ["flow:store-approve"],
      "candidateRelationIds": [],
      "supportProcessJoinSignalIds": ["process-join-signal:request-id"],
      "processSemanticCueIds": [],
      "counterProcessJoinSignalIds": [],
      "blockingCounterProcessJoinSignalIds": [],
      "factIds": ["fact:store-approval-guard"],
      "proofIds": ["proof:store-approval-guard"],
      "evidenceNodeIds": ["evidence:store-approval-guard"],
      "gapIds": []
    },
    {
      "processClaimId": "process-claim:03-region-activity",
      "claimKind": "ACTIVITY",
      "subjectKeys": [{"keyKind":"REGISTRY","key":"ACTIVITY_P_REGION_APPROVAL","registryItemId":"registry-item:region-approval","technicalAnchorIds":[]}],
      "predicateKey": {"keyKind":"TECHNICAL","key":"activity:reviews-approved-request","registryItemId":null,"technicalAnchorIds":["entry:region-approve"]},
      "objectKeys": [{"keyKind":"TECHNICAL","key":"object:approved-procurement-request","registryItemId":null,"technicalAnchorIds":["java-type:ProcurementRequest"]}],
      "memberFlowSliceIds": ["flow:region-approve"],
      "candidateRelationIds": [],
      "supportProcessJoinSignalIds": ["process-join-signal:request-id"],
      "processSemanticCueIds": [],
      "counterProcessJoinSignalIds": [],
      "blockingCounterProcessJoinSignalIds": [],
      "factIds": ["fact:region-approval-guard"],
      "proofIds": ["proof:region-approval-guard"],
      "evidenceNodeIds": ["evidence:region-approval-guard"],
      "gapIds": []
    },
    {
      "processClaimId": "process-claim:04-transition",
      "claimKind": "TRANSITION",
      "subjectKeys": [{"keyKind":"REGISTRY","key":"ACTIVITY_P_STORE_APPROVAL","registryItemId":"registry-item:store-approval","technicalAnchorIds":[]}],
      "predicateKey": {"keyKind":"TECHNICAL","key":"transition:request-id-handoff","registryItemId":null,"technicalAnchorIds":["process-relation:approval-to-region"]},
      "objectKeys": [{"keyKind":"REGISTRY","key":"ACTIVITY_P_REGION_APPROVAL","registryItemId":"registry-item:region-approval","technicalAnchorIds":[]}],
      "memberFlowSliceIds": ["flow:store-approve","flow:region-approve"],
      "candidateRelationIds": ["process-relation:approval-to-region"],
      "supportProcessJoinSignalIds": ["process-join-signal:request-id"],
      "processSemanticCueIds": [],
      "counterProcessJoinSignalIds": [],
      "blockingCounterProcessJoinSignalIds": [],
      "factIds": ["fact:approval-id-transfer"],
      "proofIds": ["proof:approval-id-transfer"],
      "evidenceNodeIds": ["evidence:approval-id-transfer"],
      "gapIds": []
    },
    {
      "processClaimId": "process-claim:05-result",
      "claimKind": "END_RESULT",
      "subjectKeys": [{"keyKind":"TECHNICAL","key":"process:replenishment-approval","registryItemId":null,"technicalAnchorIds":["process-evidence-group:fixture-approval"]}],
      "predicateKey": {"keyKind":"TECHNICAL","key":"result:approved-request-ready","registryItemId":null,"technicalAnchorIds":["fact:approved-request-output"]},
      "objectKeys": [{"keyKind":"TECHNICAL","key":"object:approved-procurement-request","registryItemId":null,"technicalAnchorIds":["java-type:ProcurementRequest"]}],
      "memberFlowSliceIds": ["flow:region-approve"],
      "candidateRelationIds": [],
      "supportProcessJoinSignalIds": ["process-join-signal:approved-request-output"],
      "processSemanticCueIds": [],
      "counterProcessJoinSignalIds": [],
      "blockingCounterProcessJoinSignalIds": [],
      "factIds": ["fact:approved-request-output"],
      "proofIds": ["proof:approved-request-output"],
      "evidenceNodeIds": ["evidence:approved-request-output"],
      "gapIds": []
    }
  ],
  "conditionClaimIds": [],
  "branchClaimIds": [],
  "parallelClaimIds": [],
  "alternativeClaimIds": [],
  "fallbackClaimIds": [],
  "candidateRelations": [
    {
      "candidateRelationId": "process-relation:approval-to-region",
      "processClaimIds": ["process-claim:04-transition"],
      "supportProcessJoinSignalIds": ["process-join-signal:request-id"],
      "processSemanticCueIds": [],
      "counterProcessJoinSignalIds": []
    }
  ],
  "purposeClaimId": "process-claim:01-purpose",
  "endResultClaimId": "process-claim:05-result",
  "pendingAssumptionClaimIds": [],
  "readerSlots": [
    {
      "slotKind": "PROCESS_NAME",
      "text": "补货审批到采购准备（合成）",
      "processClaimIds": ["process-claim:01-purpose","process-claim:05-result"],
      "registryOrTechnicalKeys": [{"keyKind":"TECHNICAL","key":"process:replenishment-approval","registryItemId":null,"technicalAnchorIds":["process-evidence-group:fixture-approval"]}]
    },
    {
      "slotKind": "PROCESS_SUMMARY",
      "text": "门店检查补货申请后，将同一申请标识交给区域审批；结果仅表示已批准的采购准备材料，不表示外部系统已经建单（合成）。",
      "processClaimIds": ["process-claim:01-purpose","process-claim:02-store-activity","process-claim:03-region-activity","process-claim:04-transition","process-claim:05-result"],
      "registryOrTechnicalKeys": [{"keyKind":"TECHNICAL","key":"process:replenishment-approval","registryItemId":null,"technicalAnchorIds":["process-evidence-group:fixture-approval"]}]
    },
    {
      "slotKind": "TRANSITION",
      "text": "同一补货申请标识从门店审批交给区域审批（合成）。",
      "processClaimIds": ["process-claim:04-transition"],
      "registryOrTechnicalKeys": [{"keyKind":"TECHNICAL","key":"transition:request-id-handoff","registryItemId":null,"technicalAnchorIds":["process-relation:approval-to-region"]}]
    }
  ],
  "p2TaskId": "process-model-task:fixture-p2-approval",
  "p2RoundId": "process-model-round:fixture-p2-approval",
  "processHypothesisReviewId": "process-hypothesis-review:fixture-approval",
  "finalReviewDecision": "NARROW"
}
~~~

`memberFlows.role`闭集为`START | INTERMEDIATE | TERMINAL | PARALLEL | ALTERNATIVE | FALLBACK`。业务词优先用registry key；技术fallback必须显式`TECHNICAL`。条件、分支、并行、备选、回退、目的、结束结果和pending均保存为typed claim ID，不保存裸模型字符串。每个`ProcessHypothesisClaimV1`必须至少绑定一个member Flow，并绑定非空candidate relation/support signal/Fact/Proof/Evidence闭包或非空Gap/counter；每个reader slot必须绑定非空`processClaimIds[]`，且只能摘要这些claims。slot文本不能创建Markdown、Fact、新证据或未绑定process assertion。

## 7. 十五个正式文件

Step 06固定发布十四项semantic payload加receipt，共十五文件：

| 文件 | 数量合同 |
| --- | --- |
| `registry-proposal-tasks.jsonl` | `E` |
| `registry-proposal-rounds.jsonl` | `E`（成功publication） |
| `registry-proposal-dispositions.jsonl` | `E` |
| `repository-interpretation-registry.json` | 1 |
| `flow-model-tasks.jsonl` | `2R` |
| `model-rounds.jsonl` | `R + accepted local R1` |
| `generation-receipts.jsonl` | 所有R0/R1/R2/P1/P2实际调用各一行 |
| `interpretation-candidates.jsonl` | 局部accepted候选子集 |
| `flow-interpretation-dispositions.jsonl` | `E` |
| `process-evidence-groups.jsonl` | `G`；新增 |
| `process-model-tasks.jsonl` | `2S`；每shard一个P1和一个P2 |
| `process-model-rounds.jsonl` | `S + accepted process P1` |
| `business-process-hypotheses.jsonl` | P1提出且P2未DROP的hypothesis |
| `process-interpretation-dispositions.jsonl` | `S`；每shard一条P1/P2闭合处置 |
| `flow-interpretation-receipt.json` | 14 descriptors、上游refs、controls、M9 provenance |

这五项是且仅是新增Step 06 semantic artifacts：`process-evidence-groups.jsonl`、`process-model-tasks.jsonl`、`process-model-rounds.jsonl`、`business-process-hypotheses.jsonl`、`process-interpretation-dispositions.jsonl`。全run正式artifact总数固定为**57**。

所有调用（包括P1/P2）的typed discriminator仍写在统一`generation-receipts.jsonl`：

~~~text
R0_REGISTRY_PROPOSAL
R1_FLOW_INTERPRETATION
R2_FLOW_PRECISION_REVIEW
PROCESS_P1_HYPOTHESIS
PROCESS_P2_PRECISION_REVIEW
~~~

M3内部module envelope与公开registry必须使用不同pair，禁止store key冲突：

~~~text
M3: flow-interpretation-repository-interpretation-registry-module-v1
    FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY_MODULE
public: flow-interpretation-repository-interpretation-registry-v3
        FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY
~~~

新增正式schema/type pair：

~~~text
flow-interpretation-process-evidence-group-v1 / FLOW_INTERPRETATION_PROCESS_EVIDENCE_GROUP
flow-interpretation-process-model-task-v1 / FLOW_INTERPRETATION_PROCESS_MODEL_TASK
flow-interpretation-process-model-round-v1 / FLOW_INTERPRETATION_PROCESS_MODEL_ROUND
flow-interpretation-business-process-hypothesis-v1 / FLOW_INTERPRETATION_BUSINESS_PROCESS_HYPOTHESIS
flow-interpretation-process-interpretation-disposition-v1 / FLOW_INTERPRETATION_PROCESS_INTERPRETATION_DISPOSITION
flow-interpretation-generation-receipt-v3 / FLOW_INTERPRETATION_GENERATION_RECEIPT
~~~

### 7.1 新增standalone wire的完整字段

本节是五项新增standalone records及升级后generation receipt的exact合同。`!`表示required non-null，`?`表示**字段仍required但值可为null**，`[]!`表示required array（可空但不可null）；未列字段和extra field一律拒绝。object key按canonical JSON UTF-8 byte order；普通ID arrays排序去重，stage/member/claim/slot数组保留声明的业务顺序并另由其ID保证唯一。JSONL分别按`processEvidenceGroupId`、`processModelTaskId`、`processModelRoundId`、`businessProcessHypothesisId`、`processInterpretationDispositionId`和`generationReceiptId`排序。

~~~text
ProcessEvidenceGroupV1
  schemaVersion!=flow-interpretation-process-evidence-group-v1
  artifactType!=FLOW_INTERPRETATION_PROCESS_EVIDENCE_GROUP
  processEvidenceGroupId!
  groupKind!: CONNECTED_COMPONENT | SINGLETON
  memberFlowSliceIds[]!
  candidateRelations[]!: ProcessCandidateRelationV1
  processSemanticCues[]!: ProcessSemanticCueV1
  supportingProcessJoinSignalIds[]!
  counterProcessJoinSignalIds[]!
  repositoryInterpretationRegistryItemIds[]!
  modelEligibility!: MODEL_SAFE | MODEL_INELIGIBLE
  modelIneligibilityGapIds[]!
  boundedMaterial!: ProcessBoundedMaterialV1

ProcessCandidateRelationV1
  candidateRelationId!
  leftFlowSliceId!, rightFlowSliceId!       // left < right; never equal
  strongestSignalLevel!: PROVEN_HANDOFF | SHARED_ANCHOR | SEMANTIC_CUE
  direction!: LEFT_TO_RIGHT | RIGHT_TO_LEFT | UNDIRECTED
  relationUse!: PROCESS_CANDIDATE | PENDING_ONLY
  supportingProcessJoinSignalIds[]!
  processSemanticCueIds[]!
  counterProcessJoinSignalIds[]!
  blockingCounterProcessJoinSignalIds[]!
  factIds[]!, proofIds[]!, evidenceNodeIds[]!, sourceLocators[]!, gapIds[]!

ProcessBoundedMaterialV1
  flowViews[]!: ProcessFlowEvidenceViewV1
  relationViews[]!: ProcessCandidateRelationV1
  registryItems[]!: RepositoryInterpretationRegistryItemV3
  limits!: ProcessMaterialLimitsV1

ProcessFlowEvidenceViewV1
  flowSliceId!, evidenceCapsuleId!, evidenceCapsuleRef!
  entryId!
  factViews[]!: ModelFactViewV1
  gapViews[]!: ModelGapViewV1
  outcomePathViews[]!: FlowOutcomePathViewV1
  processJoinSignals[]!: ProcessJoinSignalV1
  modelEvidenceSpans[]!: ModelEvidenceSpanV4
  projectionObligations[]!: ProjectionObligationV1

ProcessMaterialLimitsV1
  maxFlows!, maxRelations!, maxSignals!, maxRegistryItems!
  maxInputBytes!, maxHypotheses!, maxClaimsPerHypothesis!, maxReaderSlots!

ProcessModelTaskV1
  schemaVersion!=flow-interpretation-process-model-task-v1
  artifactType!=FLOW_INTERPRETATION_PROCESS_MODEL_TASK
  processModelTaskId!
  taskKind!: PROCESS_P1_HYPOTHESIS | PROCESS_P2_PRECISION_REVIEW
  taskShardId!, taskOrdinal!
  processEvidenceGroupIds[]!
  ownerCandidateRelationIds[]!
  contextFlowSliceIds[]!
  boundedMaterial!: ProcessBoundedMaterialV1
  reviewedP1TaskId?, reviewedP1RoundId?      // both null for P1; both nonnull for P2
  reviewedBusinessProcessHypothesisIds[]!   // empty for P1; exact P1 IDs for P2
  promptBundleRef!, responseSchemaRef!, expectedRuntime!: ModelRuntimeIdentityV1
  inputJsonSha256!, resourceBudget!: ProcessMaterialLimitsV1

ProcessModelRoundV1
  schemaVersion!=flow-interpretation-process-model-round-v1
  artifactType!=FLOW_INTERPRETATION_PROCESS_MODEL_ROUND
  processModelRoundId!
  processModelTaskId!, taskKind!, taskShardId!
  roundOrdinal!: 1 | 2
  requestSha256!, responseSha256!
  responseKind!: P1_HYPOTHESES | P1_GAP | P1_FAILED |
                 P2_REVIEWS | P2_GAP | P2_FAILED
  businessProcessHypothesisIds[]!
  processHypothesisReviews[]!: ProcessHypothesisReviewV1
  gapIds[]!
  failureCode?                            // nonnull iff typed *_FAILED
  generationReceiptId!

BusinessProcessHypothesisV1
  schemaVersion!=flow-interpretation-business-process-hypothesis-v1
  artifactType!=FLOW_INTERPRETATION_BUSINESS_PROCESS_HYPOTHESIS
  businessProcessHypothesisId!
  taskShardId!, p1TaskId!, p1RoundId!
  processEvidenceGroupIds[]!
  memberFlows[]!: BusinessProcessFlowMemberV1
  businessRoleKeys[]!, stageKeys[]!, activityKeys[]!
  inputObjectKeys[]!, outputObjectKeys[]!, objectKeys[]!, stateKeys[]!
  processClaims[]!: ProcessHypothesisClaimV1
  conditionClaimIds[]!, branchClaimIds[]!, parallelClaimIds[]!
  alternativeClaimIds[]!, fallbackClaimIds[]!
  candidateRelations[]!: HypothesisRelationBindingV1
  purposeClaimId!, endResultClaimId!
  pendingAssumptionClaimIds[]!
  readerSlots[]!: ProcessClaimBoundSlotV1
  p2TaskId!, p2RoundId!
  processHypothesisReviewId!
  finalReviewDecision!: KEEP | NARROW | PENDING_CONFIRMATION

BusinessProcessFlowMemberV1
  flowSliceId!
  role!: START | INTERMEDIATE | TERMINAL | PARALLEL | ALTERNATIVE | FALLBACK
  stageKey!: RegistryOrTechnicalKeyV1
  activityKey!: RegistryOrTechnicalKeyV1
  supportingProcessClaimIds[]!

RegistryOrTechnicalKeyV1
  keyKind!: REGISTRY | TECHNICAL
  key!
  registryItemId?                         // nonnull iff REGISTRY
  technicalAnchorIds[]!                   // nonempty iff TECHNICAL

ProcessHypothesisClaimV1
  processClaimId!
  claimKind!: PURPOSE | END_RESULT | ACTIVITY | TRANSITION | CONDITION |
              BRANCH | PARALLEL | ALTERNATIVE | FALLBACK | ROLE | STATE | OBJECT
  subjectKeys[]!: RegistryOrTechnicalKeyV1
  predicateKey!: RegistryOrTechnicalKeyV1
  objectKeys[]!: RegistryOrTechnicalKeyV1
  memberFlowSliceIds[]!
  candidateRelationIds[]!
  supportProcessJoinSignalIds[]!
  processSemanticCueIds[]!
  counterProcessJoinSignalIds[]!
  blockingCounterProcessJoinSignalIds[]!
  factIds[]!, proofIds[]!, evidenceNodeIds[]!, gapIds[]!

HypothesisRelationBindingV1
  candidateRelationId!
  processClaimIds[]!
  supportProcessJoinSignalIds[]!
  processSemanticCueIds[]!
  counterProcessJoinSignalIds[]!

ProcessClaimBoundSlotV1
  slotKind!: PROCESS_NAME | PROCESS_SUMMARY | PURPOSE | START | FINISH |
             ACTIVITY | TRANSITION | ROLE | ALTERNATIVE | PENDING
  text!
  processClaimIds[]!                       // nonempty
  registryOrTechnicalKeys[]!: RegistryOrTechnicalKeyV1

ProcessHypothesisReviewV1
  processHypothesisReviewId!
  businessProcessHypothesisId!
  decision!: KEEP | NARROW | DROP | PENDING_CONFIRMATION
  retainedProcessClaimIds[]!
  narrowedProcessClaimIds[]!
  droppedProcessClaimIds[]!
  pendingProcessClaimIds[]!
  retainedMemberFlowSliceIds[]!
  retainedCandidateRelationIds[]!
  reasonCode?, gapIds[]!

ProcessInterpretationDispositionV1
  schemaVersion!=flow-interpretation-process-interpretation-disposition-v1
  artifactType!=FLOW_INTERPRETATION_PROCESS_INTERPRETATION_DISPOSITION
  processInterpretationDispositionId!
  taskShardId!
  p1TaskId!, p1TaskDisposition!: ModelTaskDispositionV2
  p2TaskId!, p2TaskDisposition!: ModelTaskDispositionV2
  proposedBusinessProcessHypothesisIds[]!
  retainedBusinessProcessHypothesisIds[]!
  narrowedBusinessProcessHypothesisIds[]!
  droppedBusinessProcessHypothesisIds[]!
  pendingBusinessProcessHypothesisIds[]!
  disposition!: READY_FOR_ADMISSION | GAP | FAILED
  gapIds[]!, failureRef?, reasonCode?

ModelTaskDispositionV2                  // shared shape; local V1 stays unchanged
  taskSpecId!, taskScopeKind!: PROCESS_SHARD
  taskShardId!, round!: P1 | P2
  state!: RESPONSE_ACCEPTED | RESPONSE_GAP | RESPONSE_FAILED | NOT_RUN_UPSTREAM_FAILED
  modelRoundId?, generationReceiptId?, upstreamTaskSpecId?
  gapIds[]!, failureRef?, reasonCode?

GenerationReceiptV3
  schemaVersion!=flow-interpretation-generation-receipt-v3
  artifactType!=FLOW_INTERPRETATION_GENERATION_RECEIPT
  generationReceiptId!
  generationKind!: R0_REGISTRY_PROPOSAL | R1_FLOW_INTERPRETATION |
                   R2_FLOW_PRECISION_REVIEW | PROCESS_P1_HYPOTHESIS |
                   PROCESS_P2_PRECISION_REVIEW
  taskSpecId!
  flowSliceId?, taskShardId?               // exactly one scope field nonnull
  requestSha256!, responseSha256!
  configuredAdapterId!, configuredAuthMode!
  expectedRuntime!: ModelRuntimeIdentityV1
  observedRuntime!: ModelRuntimeIdentityV1
  started=true, completed=true
~~~

`ModelTaskDispositionV2`是仅用于P1/P2 scope的版本化扩展，不改局部V1。`ProcessModelRoundV1.generationReceiptId`单向指向先固定的receipt；receipt不含round ID，避免identity环。`BusinessProcessHypothesisV1`公开文件只保存P2 KEEP/NARROW/PENDING的记录；DROP仍由round/review/disposition计数，不在hypothesis文件伪装保留。P2字段在成功public record中均non-null；P1 GAP/FAILED时不存在hypothesis record。

### 7.2 Step 06显式无环identity DAG

经用户直接确认，语义ID与完整wire/artifact identity分层：语义ID只哈希下表的semantic projection；required later-lineage字段仍保存在wire中、进入JSONL/artifact descriptor SHA与analysis-step root，并由M9逐引用验证。排除后向引用不会隐藏篡改。除表内明确字段外，不得再排除字段；没有alias、dual-write或旧公式兼容路径。

| record / self ID | semantic projection | 从semantic ID精确排除 | projection中必须先存在的reference字段 |
| --- | --- | --- | --- |
| `ProcessSemanticCueV1.processSemanticCueId` | §7.1全部字段减排除列 | `processSemanticCueId` | `leftFlowSliceId,rightFlowSliceId,leftRegistryItemId,rightRegistryItemId,leftBasisAtomIds,rightBasisAtomIds,leftEntryId,rightEntryId,leftStateSignalIds,rightStateSignalIds,processCueProfileRef` |
| `ProcessCandidateRelationV1.candidateRelationId` | §7.1全部字段减排除列 | `candidateRelationId` | `leftFlowSliceId,rightFlowSliceId,supportingProcessJoinSignalIds,processSemanticCueIds,counterProcessJoinSignalIds,blockingCounterProcessJoinSignalIds,factIds,proofIds,evidenceNodeIds,sourceLocators,gapIds` |
| `ProcessEvidenceGroupV1.processEvidenceGroupId` | §7.1全部字段减排除列 | `processEvidenceGroupId` | `memberFlowSliceIds,candidateRelations,processSemanticCues,supportingProcessJoinSignalIds,counterProcessJoinSignalIds,repositoryInterpretationRegistryItemIds,modelIneligibilityGapIds,boundedMaterial` |
| `ProcessModelTaskV1.processModelTaskId` | §7.1全部字段减排除列 | `processModelTaskId` | `processEvidenceGroupIds,ownerCandidateRelationIds,contextFlowSliceIds,boundedMaterial,reviewedP1TaskId,reviewedP1RoundId,reviewedBusinessProcessHypothesisIds,promptBundleRef,responseSchemaRef,expectedRuntime` |
| `ProcessHypothesisClaimV1.processClaimId` | §7.1全部字段减排除列 | `processClaimId` | `subjectKeys,predicateKey,objectKeys,memberFlowSliceIds,candidateRelationIds,supportProcessJoinSignalIds,processSemanticCueIds,counterProcessJoinSignalIds,blockingCounterProcessJoinSignalIds,factIds,proofIds,evidenceNodeIds,gapIds` |
| `BusinessProcessHypothesisV1.businessProcessHypothesisId` | §7.1的P1 semantic content减排除列 | `businessProcessHypothesisId,p1RoundId,p2TaskId,p2RoundId,processHypothesisReviewId,finalReviewDecision` | `taskShardId,p1TaskId,processEvidenceGroupIds,memberFlows,businessRoleKeys,stageKeys,activityKeys,inputObjectKeys,outputObjectKeys,objectKeys,stateKeys,processClaims,conditionClaimIds,branchClaimIds,parallelClaimIds,alternativeClaimIds,fallbackClaimIds,candidateRelations,purposeClaimId,endResultClaimId,pendingAssumptionClaimIds,readerSlots` |
| `GenerationReceiptV3.generationReceiptId` | §7.1全部字段减排除列 | `generationReceiptId` | `taskSpecId,expectedRuntime,observedRuntime`；request/response SHA是调用bytes preimage，不是round/hypothesis back-reference |
| `ProcessHypothesisReviewV1.processHypothesisReviewId` | §7.1全部字段减排除列 | `processHypothesisReviewId` | `businessProcessHypothesisId,retainedProcessClaimIds,narrowedProcessClaimIds,droppedProcessClaimIds,pendingProcessClaimIds,retainedMemberFlowSliceIds,retainedCandidateRelationIds,gapIds` |
| `ProcessModelRoundV1.processModelRoundId` | §7.1全部字段减排除列 | `processModelRoundId` | `processModelTaskId,businessProcessHypothesisIds,processHypothesisReviews,gapIds,generationReceiptId` |
| `ProcessInterpretationDispositionV1.processInterpretationDispositionId` | §7.1全部字段减排除列 | `processInterpretationDispositionId` | `taskShardId,p1TaskId,p1TaskDisposition,p2TaskId,p2TaskDisposition,proposedBusinessProcessHypothesisIds,retainedBusinessProcessHypothesisIds,narrowedBusinessProcessHypothesisIds,droppedBusinessProcessHypothesisIds,pendingBusinessProcessHypothesisIds,gapIds,failureRef` |

无self ID的`ProcessBoundedMaterialV1`、`ProcessFlowEvidenceViewV1`、`ProcessMaterialLimitsV1`、`BusinessProcessFlowMemberV1`、`RegistryOrTechnicalKeyV1`、`HypothesisRelationBindingV1`、`ProcessClaimBoundSlotV1`和`ModelTaskDispositionV2`不单独计算identity；其完整规范值只参加上表明确拥有它的parent projection，且不得含parent/later ID。

唯一合法计算/物化顺序为：upstream Flow/Capsule/Fact/Proof/Evidence/Registry IDs → semantic cue → candidate relation → evidence group → P1 task → P1 generation receipt与claim IDs（同rank）→ hypothesis semantic ID → P1 round → P2 task（即使随后`NOT_RUN_UPSTREAM_FAILED`也在P1 terminal round后物化）→ P2 review与generation receipt（同rank）→ P2 round → 回填hypothesis的五个later-lineage excluded字段 → process disposition。P1 round引用hypothesis semantic ID，hypothesis ID不引用round；review引用hypothesis，hypothesis ID不引用review；P2 round引用review，review不引用round。

`BusinessProcessHypothesisV1`的五个later-lineage excluded字段必须满足：`p1RoundId`指向唯一含本hypothesis ID的同task/shard P1 round；`p2TaskId`指向reviewed P1 task/round及本ID的同shard P2 task；`p2RoundId`指向该task且含`processHypothesisReviewId`的P2 round；review反向指向本ID；`finalReviewDecision`逐字等于review decision且不是`DROP`。任一不符fatal；完整record bytes仍随这些字段变化而改变artifact SHA/root。

各semantic ID固定为`<prefix> + lowercaseHex(SHA-256(frame(UTF8(<domain>)) || frame(canonicalJson(semanticProjection))))`，其中prefix/domain依次为：

~~~text
process-evidence-group: / flow-interpretation-process-evidence-group-id-v1
process-relation: / flow-interpretation-process-candidate-relation-id-v1
process-semantic-cue: / flow-interpretation-process-semantic-cue-id-v1
process-model-task: / flow-interpretation-process-model-task-id-v1
process-model-round: / flow-interpretation-process-model-round-id-v1
business-process-hypothesis: / flow-interpretation-business-process-hypothesis-id-v1
process-claim: / flow-interpretation-process-hypothesis-claim-id-v1
process-hypothesis-review: / flow-interpretation-process-hypothesis-review-id-v1
process-interpretation-disposition: / flow-interpretation-process-interpretation-disposition-id-v1
generation-receipt: / flow-interpretation-generation-receipt-id-v3
~~~

非excluded required-nullable字段以null参加projection。embedded record有self ID时先按DAG计算embedded ID，owner projection覆盖该embedded record中属于semantic projection的完整值。`sourceLocators[]`按`(path,startByte,endByteExclusive)`；member/claim/slot业务序列由M8按`(stage ordinal,flowSliceId,claimKind,processClaimId)`规范化，不能信任模型顺序。

## 8. 集合、identity与处置合同

~~~text
businessFlowsFlowSliceIds = eligibleFlowSliceIds ⊎ ineligibleFlowSliceIds
|eligibleFlowSliceIds| = E
r0Task.flowSliceIds = r0Disposition.flowSliceIds = eligibleFlowSliceIds
localModelTaskIds = exactlyTwoTasksPer(r0ReadyFlowSliceIds)
|localModelTaskIds| = 2R
candidateRelationIds = disjointUnion(ownerCandidateRelationIds by process shard)
|candidateRelationIds| = C
processEvidenceGroups cover exactly all N Flow IDs
|processEvidenceGroupIds| = G
processModelTaskIds = exactlyTwoTasksPer(S shards)
|processModelTaskIds| = 2S
allPlannedTaskIds ↔ allModelTaskDispositionIds
planned model tasks = E + 2R + 2S
actual calls = E + R + accepted local R1 + S + accepted process P1
~~~

P2的结果满足：

~~~text
P2.memberFlowIds ⊆ P1.memberFlowIds
P2.candidateRelationIds ⊆ P1.candidateRelationIds
P2.fact/proof/evidence/signal/gap refs ⊆ P1 refs ∪ same-shard bounded refs
P2.hypothesisIds = P1.hypothesisIds
P2 decision ∈ {KEEP,NARROW,DROP,PENDING_CONFIRMATION}
~~~

R2/P2因上游typed GAP/FAILED未运行时，必须持久化`NOT_RUN_UPSTREAM_FAILED`，round/receipt ID为null且`upstreamTaskSpecId`指向同Flow或同shard上一轮。任何task最多一次started call；无自动重试、Provider switching或API fallback。

所有公开记录ID是全长SHA-256 direct-preimage identity：domain字符串+全部identity-significant字段的canonical JSON frames。排序、分片、线程数与写盘路径不得进入业务identity；source locator仍是repo-relative、forward-slash、行列1-based。

## 9. 成功、Gap与fatal

### 成功

- M9证明15文件receipt-last安装、全部上游ref与task/round/receipt/disposition双射、`N/E/R/C/G/S`集合合同和P2非扩张。
- `N=0`仍发布空registry、空JSONL和完整十五文件；`N>0,E=0`有groups但无任何模型任务。
- 同一Flow可被多个最终hypothesis引用；每个引用仍回到同一Flow/Capsule事实。

### 业务Gap

- 没有正向domain signal的孤立Flow保留singleton group；不是fatal。
- signal冲突、顺序不确定、外部效果未证明或P2 `PENDING_CONFIRMATION`进入显式Gap/待确认，不得被自然语言抹平。
- typed `R0/R1/P1` GAP/FAILED可使后继R2/P2 `NOT_RUN_UPSTREAM_FAILED`；计划任务仍计数。

### 当前run fatal

- provider transport/runtime失败、started call无完整响应、observed runtime不符；
- schema/extra-field/Unicode/budget/hash/identity/ref closure错误；
- generic-only成边、edge多owner/无owner、groups漏Flow、P2扩张；
- task/round/receipt/disposition缺失或重复、部分publication或count伪造。

稳定failure codes至少包括：`PROCESS_SIGNAL_LEVEL_INVALID`、`PROCESS_GENERIC_SIGNAL_ONLY`、`PROCESS_GROUP_COVERAGE_BROKEN`、`PROCESS_EDGE_OWNERSHIP_BROKEN`、`PROCESS_TASK_BUDGET_EXCEEDED`、`PROCESS_MODEL_RESPONSE_INVALID`、`PROCESS_MODEL_REFERENCE_INVALID`、`PROCESS_REVIEW_EXPANDED`、`PROCESS_DISPOSITION_INCOMPLETE`，并沿用局部`PROVIDER_FAILURE_AFTER_START`、`MODEL_TASK_NOT_RUN_UPSTREAM_INVALID`与`FLOW_INTERPRETATION_RESOURCE_LIMIT_EXCEEDED`。

## 10. 给Step 07的下游保证

RepositoryKnowledge无需回读源码或调用模型即可：先准入local meanings，再验证process claims，再比较conflict/alternative，再建立多对多membership，最后生成一个全仓知识。它接收的每个process claim都能沿以下链路回放：

`BusinessProcessHypothesis → P1/P2 task、round、generation receipt → ProcessEvidenceGroup / ProcessJoinSignal → Flow / EvidenceCapsule → Fact / Proof / Evidence / Source`。

Step 06只发布hypothesis，不把它们宣称为最终事实；确定性准入和certainty由Step 07负责。Step 08只消费Step 07的知识，不直接读模型文本。

## 11. Luna RED 与 Terra GREEN 指南

### Luna/xhigh RED

- 先写`N=0`、`N>0,E=0`、`E=1,R=1,S=1`、R1/P1 typed GAP、R2/P2 NOT_RUN的失败fixture；逐式断言planned/actual call公式。
- 反例覆盖四级signal、generic-only禁边、counter保留、edge唯一owner、group全覆盖、Flow只读重复、P2新增Flow/edge/fact、外部效果无Proof、M3/public registry pair冲突。
- 用真实DepotHead bounded fixture证明“调用参数可见但外部更新未证”；用显式synthetic七Flow fixture证明顺序/并行/alternative、Flow复用及P2删除“唯一采购单”“已经记账”。
- Reader slots只测试JSON字段与引用，不让模型生成Markdown。真实Provider测试必须另行授权并先做preflight；默认只用scripted provider，禁止API fallback。

### Terra/xhigh GREEN

- 只在Sol/ultra合同和对应RED已冻结后实现；保持M1–M5局部边界，新增M6–M9，不改八步runner或公开`RepositoryAnalysisAgent`。
- 程序排序、分组、budget、identity、验证与publication；Luna只执行五类规定round。
- 先实现零模型的M6/M7与store pair隔离，再实现P1/P2 typed runner，最后实现M9十五文件receipt-last publication。
- 不为fixture硬编码仓库词，不把semantic similarity当edge，不增加retry/resume/provider switching。

## 12. 当前实现差距

| 层 | 当前事实 | 目标合同 |
| --- | --- | --- |
| 局部M1–M5 | 已有scripted-provider有界纵切；局部R0/R1/R2仍是单Flow | 保持单Flow，补齐公开闭包与失败fixture |
| 跨Flow M6–M9 | 未实现 | 确定性candidate/group/shard、Luna P1/P2、十五文件M9 publication |
| 公共文件 | 当前尚未形成完整Step 06 success publication | 从10文件变为15文件；全run总数57 |
| registry envelope | 当前M3 module pair与拟公开pair存在冲突风险 | 使用§7明确分离的module/public schema/type pair |
| 真实DepotHead | 没有模型或端到端process输出 | 仅把已有静态边界事实作为有界材料，外部效果继续Gap |

任何试图让R0/R1/R2读取多Flow、让P1/P2读源码、让Step 06输出Markdown、改变八步/九章/公开接口、改变57总数或跨步骤identity的实现都必须STOP并交Sol/ultra Design Authority；业务目标变化再由用户裁决。
