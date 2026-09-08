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

`ProcessJoinSignalV1`映射为四个精确信号等级：

| 等级 | 可用依据 | 允许的程序结论 |
| --- | --- | --- |
| `PROVEN_HANDOFF` | 标识符产出/返回与另一Flow消费、显式调用/返回/事件引用，且Proof闭包成立 | 建有方向的candidate edge；仍不是已证业务因果 |
| `SHARED_ANCHOR` | 同一业务对象、Java类型、SQL表/字段或业务ID | 建共享锚点edge；默认无方向 |
| `SEMANTIC_CUE` | 状态写/检查、非generic对象引用等较弱组合 | 建弱候选edge并要求P1/P2保留不确定性 |
| `COUNTER_SIGNAL` | 相斥条件、冲突状态、Gap或反证 | 附在候选关系上；不能被正向信号吞掉 |

`tenantId`、创建/修改人等审计字段、日志、generic utility、方法名相似、中文名称相似，**单独都禁止成边**。只有同一个局部信号集合内存在domain-specific正向依据时，它们才可作为附加上下文。缺少专门Proof的外部影响始终是Gap。

规范候选边字段如下；`leftFlowSliceId < rightFlowSliceId`按UTF-8 byte order，direction另存：

~~~json
{
  "candidateRelationId": "process-relation:sha256…",
  "leftFlowSliceId": "flow:store-approval",
  "rightFlowSliceId": "flow:regional-approval",
  "strongestSignalLevel": "PROVEN_HANDOFF",
  "direction": "LEFT_TO_RIGHT",
  "supportingProcessJoinSignalIds": ["process-join-signal:request-id-return"],
  "counterSignalIds": ["process-join-signal:status-conflict"],
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

`BusinessProcessHypothesisV1`最少包含：

~~~json
{
  "businessProcessHypothesisId": "business-process-hypothesis:sha256…",
  "memberFlows": [{"flowSliceId":"flow:store-approve","role":"INTERMEDIATE"}],
  "businessRoleKeys": [{"keyKind":"REGISTRY","key":"ROLE_P_…"}],
  "stageKeys": [{"keyKind":"TECHNICAL","key":"stage:approval"}],
  "activityKeys": [{"keyKind":"REGISTRY","key":"ACTIVITY_P_…"}],
  "inputObjectKeys": [{"keyKind":"TECHNICAL","key":"object:replenishment-request"}],
  "outputObjectKeys": [{"keyKind":"TECHNICAL","key":"object:purchase-order"}],
  "conditions": ["regional approval is accepted"],
  "branches": [],
  "parallelActivities": ["fee handling", "purchase-order approval"],
  "alternatives": ["return for correction"],
  "fallbacks": ["manual confirmation pending"],
  "candidateRelations": [{"candidateRelationId":"process-relation:approval-to-order","supportSignalIds":["process-join-signal:po-id"],"counterSignalIds":[]}],
  "purposeKey": {"keyKind":"REGISTRY","key":"CLAIM_P_…"},
  "endResultKey": {"keyKind":"REGISTRY","key":"TERM_P_…"},
  "pendingAssumptions": ["external accounting effect lacks Proof"],
  "readerSlots": {"processName":"补货到结算（合成）","purpose":"连接申请、履约和结算","start":"提交补货申请","finish":"结算月度账单"}
}
~~~

`memberFlows.role`闭集为`START | INTERMEDIATE | TERMINAL | PARALLEL | ALTERNATIVE | FALLBACK`。业务词优先用registry key；技术fallback必须显式`TECHNICAL`。所有process claim都必须绑定候选关系与证据/Gap，reader slots仅是受限措辞，不能创建Markdown、Fact或新证据。

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
