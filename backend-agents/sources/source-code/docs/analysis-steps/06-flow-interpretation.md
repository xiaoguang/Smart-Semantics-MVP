# 流程解释

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。步骤key、目录和对外名称始终是`flow-interpretation`；跨Flow能力是本步骤内部扩展，不是第九步骤。

本文区分两件事：`Flow`是单入口、局部、可审计的代码活动；`BusinessProcess`是可能跨多个Flow的端到端业务过程。二者是多对多关系。所有目标合同均标为目标态；§12单列当前实现，不以规划覆盖事实。

## 1. 为什么存在

BusinessFlows证明每个局部活动“代码中发生了什么”，却不能单凭单个入口回答“多个入口是否共同构成从申请到结算的过程”。FlowInterpretation因此承担两层解释：

1. 既有局部层：R0提出全仓有限业务词表，R1解释一条Flow，R2只复核同一条Flow；R0/R1/R2始终严格单Flow。
2. 新增过程层：程序汇编全仓候选关系和有界组，P1为每个有界任务提出一个或多个`BusinessProcessHypothesis`；P2 `REVIEWS`逐hypothesis只能给`KEEP | NARROW | DROP | PENDING_CONFIRMATION`，整个P2 task另可形成typed `GAP | FAILED`终态。

P1/P2是整个八步工作流中**唯一**允许模型同时看到多个Flow的例外。它们仍只能看程序构造的有界`ProcessEvidenceGroup`，不能读仓库、源码路径、运行日志或别的任务。信号只是候选线索；不能单独证明先后、因果、唯一性或外部系统结果。

## 2. 实际上游交接

本步骤只读取fresh-reopened成功publication，文件归属不得靠概念名称猜测：

- Step 03完整ProgramGraphs publication：`code-structure-graph.json`、`call-graph.json`、`control-flow-graph.json`、`data-flow-graph.json`、`evidence-graph.json`、`graph-index.json`、`graph-gaps.jsonl`与`program-graphs-receipt.json`；其中`evidence-graph.json`提供Evidence node/rule/source closure，其他图与index/gaps用于重验被Step 04/05引用的program identity；
- Step 04完整ProvenCodeFacts publication：`proven-facts.json`、`proof-pack.json`、`gap-ledger.json`、`fact-accounting.json`与`proven-code-facts-receipt.json`；Fact、Proof和Fact/expectation Gap只属于本步骤，不属于Step 03；
- Step 05五项semantic artifacts与`business-flows-receipt.json`：`flow-slices.json`、`flow-coverage.json`、`entry-dispositions.jsonl`、`evidence-capsules.jsonl`、`flow-gaps.jsonl`，尤其是Flow/Capsule双射及其中`processJoinSignals`；
- exact `analysis-run-request-v2`中的profile、budget、runtime、prompt/schema digest、artifact policy和可选organization seed refs。

设：

~~~text
N = 全部Flow数
E = modelEligibility=ELIGIBLE的Flow数
R = R0处置为READY_FOR_FREEZE的Flow数
C = 程序汇编的候选跨Flow边数
G = 覆盖全部Flow的逻辑ProcessEvidenceGroup数
A = M7持久化的全部BusinessProcessTaskShard数（model-safe与no-model的总分母）
S = A中MODEL_SAFE、实际规划P1/P2的过程任务分片数

planned model tasks = E + 2R + 2S
actual calls = E + R + accepted local R1 + S + accepted process P1
~~~

`N-E`条ineligible Flow没有R0/R1/R2任务，但仍进入确定性跨Flow分组和`A`的分片/处置分母。M7为每个group产生至少一个`BusinessProcessTaskShardV1`；`MODEL_SAFE` shard组成`S`并各有P1/P2任务，`NO_MODEL` shard没有模型任务/round/receipt，但仍在`process-interpretation-dispositions.jsonl`保存完整shard、owner edge集合、非空ineligibility Gap和`NO_MODEL_ADMISSION_PENDING`。`G`个组必须不重不漏覆盖全部Flow；一个Flow可在多个最终BusinessProcess中复用，但在候选分组阶段只有一个owner group。每条候选边在全部`A`个shard中恰有一个owner，即使`S=0`也不丢失。

group eligibility只是可重算汇总：当且仅当全部`memberFlowSliceIds`对应Capsule均为`ELIGIBLE`时，`ProcessEvidenceGroupV2.modelEligibility=MODEL_SAFE`且`modelIneligibilityGapIds=[]`；否则为`MODEL_INELIGIBLE`，Gap集合逐字等于ineligible members的`modelIneligibilityGapIds`规范union。它不替代M7逐atomic-unit的shard disposition；混合group中的eligible-only unit仍可成为model-safe shard，任何含ineligible endpoint的unit必须no-model。

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
| M7 `BusinessProcessTaskCompiler` | 程序 | 把每个组切成`A`个确定性ownership shards，持久化`MODEL_SAFE ⊎ NO_MODEL`分区；仅前者组成`S`并编P1/P2 request/task |
| M8 `BusinessProcessInterpretationRunner` | Luna/xhigh | 执行P1/P2；P2不增Flow、边、Fact、Evidence或新过程 |
| M9 `InterpretationPublicationSpecifier` | 程序 | 重验全部shard/edge处置、局部与过程任务/请求/响应/引用/count，receipt-last发布十五文件 |

M2、M5、M8之外不得调用Provider。M6/M7/M9的相同输入必须产生逐字相同输出。

## 4. 程序过程

1. M1重验`N=E+(N-E)`、Flow/Capsule双射、Fact/Proof/Evidence/source locator闭包，为每条eligible Flow编R0；R0材料恰是一条Capsule。
2. M2按稳定任务序调用R0。有效proposal须使用同Capsule basis；完成全部`E`条处置后M3才冻结registry。相同label跨Flow不合并。
3. M4仅为`R`条ready Flow编R1/R2。M5先R1；只有R1 `RESPONSE_ACCEPTED`才调用R2。R1 typed GAP/FAILED时仍保留已规划R2并写`NOT_RUN_UPSTREAM_FAILED`。
4. M6按§5的信号等级比较所有Flow，形成`C`条规范候选边；generic-only依据不得成边。再按正向边的连通分量形成组，并为孤立Flow形成singleton组，得到覆盖`N`的`G`组。
5. M7应用固定partition profile/budget，为每个group产生至少一个`BusinessProcessTaskShardV1`，总数为`A`。每条candidate edge恰由一个shard拥有；同一Flow可作为只读上下文重复，但不能因此复制edge ownership或扩大其Capsule材料。含model-ineligible Flow、或无法在原子Flow/relation不截断的前提下形成安全packet的shard标为`NO_MODEL`，`processModelPacket=null`且写非空ineligibility Gap；其余shard标为`MODEL_SAFE`，按§6.2从path-bearing persisted material精确投影一个path-free packet。只有`MODEL_SAFE`子集计入`S`。
6. M8只为`S`个model-safe shard各编一个P1和一个P2 `ProcessModelTaskV1`，并把唯一`ProcessModelRequestV1`的canonical bytes作为Provider application request。对每个model-safe shard执行一次P1；P1可返回一个或多个hypothesis或typed GAP/FAILED，P1 FAILED由程序生成唯一canonical `PROCESS_P1_HYPOTHESIS_FAILED`。只有P1 accepted才调用同shard P2；否则仍保留P2计划任务并写`NOT_RUN_UPSTREAM_FAILED`。每个`NO_MODEL` shard不创建task/request/round/receipt，而创建一条显式`NO_MODEL_ADMISSION_PENDING` process disposition。
7. P2成功给出reviews时逐个覆盖P1 hypothesis，只能KEEP、NARROW、DROP或PENDING_CONFIRMATION，且不能新增Flow、candidate edge、Fact、Proof、Evidence、registry key或hypothesis。P2也可对整个review task返回typed GAP/FAILED；这两个终态不含逐hypothesis review，保留全部P1 hypothesis并按§7.1进入明确非准入分区。
8. M9重算`N/E/R/C/G/A/S`和完整集合等式，验证每个candidate edge在`A`中恰一owner、每个shard恰一process disposition、每个Step 06-owned Gap恰一canonical carrier、每个model task恰有一个task disposition、每个实际调用恰有round与typed receipt、未调用P2没有round/receipt、P2 GAP/FAILED没有review、no-model shard没有任何模型对象，然后原子发布。

## 5. 候选关系不是业务事实

M6使用下列四个等级。等级不是模型判断；程序按exact pair rule计算。单条Step 05 signal没有自己的等级，只有满足一整行的跨Flow组合才能形成该等级：

| 等级 | exact kind/source mapping | 允许的程序结论 |
| --- | --- | --- |
| `PROVEN_HANDOFF` | 下列任一proof-closed pair：`EXPLICIT_CALL`匹配另一Flow的exact entry target；`IDENTIFIER_OUTPUT`或`RETURN_TRANSFER`匹配另一Flow的`IDENTIFIER_INPUT`；同一non-generic key的`STATE_PRODUCTION`匹配另一Flow的`STATE_CHECK`；同一event key的`EVENT_REFERENCE(direction=PRODUCES)`匹配`EVENT_REFERENCE(direction=CONSUMES)`。pair两端的十三种positive signal都必须各自闭合到本Flow Fact→atom→Proof→Evidence→source | 建有方向candidate edge；状态生产/消费不得降为较弱等级，但仍不自动证明完整业务因果或外部效果 |
| `SHARED_ANCHOR` | 两Flow存在同`anchorKind+anchorKey`且`DOMAIN_SPECIFIC`的`BUSINESS_OBJECT_ANCHOR | JAVA_TYPE_ANCHOR | SQL_TABLE_ANCHOR | FIELD_ANCHOR | BUSINESS_IDENTIFIER_ANCHOR | OBJECT_REFERENCE`；两端各自Proof闭合 | 建默认无方向的相关性edge；共享表/字段/对象不能推顺序 |
| `SEMANTIC_CUE` | 只来自§5.1的`ProcessSemanticCueV1`：finite frozen Registry business term，并由entry verb或state word的同Capsule basis限定；**任何Step 05结构signal、裸状态写/检查、方法名或中文名都不能直接映射到本级** | 只能建`PENDING_ONLY`弱候选；P1/P2和Step 07不得提升为confirmed transition |
| `COUNTER_SIGNAL` | 两类且仅两类程序basis：①Step 05中`signalKind=COUNTER_CONDITION | CONFLICT_STATE | EXTERNAL_EFFECT_GAP`且`direction=BLOCKS`的闭合signal；②关系两端各自完整、非空、Proof闭合的`BUSINESS_OBJECT_ANCHOR | OBJECT_REFERENCE` domain-specific anchorKey集合互斥时的`DIFFERENT_BUSINESS_OBJECT` basis | 只附在一条已由合法positive pair形成的候选关系上；进入关系的counter全部blocking，阻止confirmed/inferred claim，不能被正向信号吞掉；不同对象不能单独成边 |

`tenantId`、创建/修改人等审计字段、日志、generic utility、方法名相似、中文名称相似，**单独都禁止成边**。只有同一个局部信号集合内存在domain-specific正向依据时，它们才可作为附加上下文。缺少专门Proof的外部影响始终是Gap。

counter归属与blocking集合完全由程序决定，而且不得从“某一个”positive pair任意选scope。对候选关系`r(left,right)`，M6先枚举并持久化两端之间**全部**qualifying positive pairs为`positivePairBases[]`；每项保存exact signal/cue IDs、level、`anchorKind+anchorKey`和direction。direct call的left/right signal数组允许仅调用方非空，但exact entry target仍进入`anchorKey`；identifier/state/event/shared-anchor pairs两端signal数组均非空；semantic cue只允许cue数组非空。数组按下节canonical tuple排序、重复项拒绝。

随后M6对每个positive pair `p`收集两类basis：

1. `EXPLICIT_BLOCKING_SIGNAL`：两端所有Step 05 blocking signal中，`anchorKind+anchorKey`与`p`逐字相同，或其`gapIds`明确引用`p`任一Fact/Proof/边界调用Gap的完整集合；
2. `DIFFERENT_BUSINESS_OBJECT`：先分别求两端全部`DOMAIN_SPECIFIC`、Proof闭合的`BUSINESS_OBJECT_ANCHOR | OBJECT_REFERENCE` signal及其anchorKey集合。只有两集合都非空且anchorKey交集为空时，才为**每个**`p`保存同一份完整left/right对象signal集合；有任何共同对象key就不产生本类basis。

`counterBases[]`保存上述每个`p × counter-kind`的完整basis并按canonical bytes排序去重。关系的聚合集合固定为：

~~~text
r.supportingProcessJoinSignalIds
  = exactUnion(all positivePairBases.left/rightProcessJoinSignalIds)
r.processSemanticCueIds
  = exactUnion(all positivePairBases.processSemanticCueIds)
r.counterProcessJoinSignalIds
  = exactUnion(all counterBases.left/rightCounterProcessJoinSignalIds)
r.blockingCounterProcessJoinSignalIds = r.counterProcessJoinSignalIds
~~~

`strongestSignalLevel`取全部positive pair的固定最大等级。`direction=LEFT_TO_RIGHT | RIGHT_TO_LEFT`仅当至少一个directed proven pair存在且所有directed proven pairs一致；没有directed pair或两方向均出现时固定为`UNDIRECTED`，不依赖枚举顺序。`relationUse=PENDING_ONLY`当且仅当最强级仅为`SEMANTIC_CUE`、`counterBases`非空或存在本关系的counter-scope Gap；否则为`PROCESS_CANDIDATE`。

`HypothesisRelationBindingV1.counterProcessJoinSignalIds`必须逐字等于所引用relation的完整聚合集合；`ProcessHypothesisClaimV1`的counter与blocking集合分别是其`candidateRelationIds`所绑定relation集合的exact union，因此二者也逐字相等。模型不得省略、降级或新增counter。若一个blocking signal声称关联本关系、却不能同时精确归属`positivePairBases[]`中至少一项，M6不挑first/min/max pair；它将关系固定为`PENDING_ONLY`，在该relation最终owner shard的`ProcessInterpretationDispositionV2.processGaps[]`写一条`PROCESS_COUNTER_SCOPE_UNRESOLVED`，且不把未归属signal混入任何`counterBasis`。因此同一relation含ID handoff、shared table等多个positive pair时，完整counter union、direction和Step 07 certainty在任何输入/遍历顺序下相同。

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

`REGISTRY_BUSINESS_TERM`要求两项finite registry labels得到同一`normalizedCueKey`；`ENTRY_VERB`还要求两项basis各包含本Flow entry-bound atom，且cue key命中冻结`entryVerbLexicon`；`STATE_WORD`还要求两端各有Proof-closed `STATE_PRODUCTION | STATE_CHECK` signal且cue key命中冻结`stateWordLexicon`。entry/state lexicon只是对finite registry term分类，不能从method/中文显示名创建term。cue identity覆盖全部字段；合法材料没有匹配pair是exact absence，profile或已引用registry/Capsule basis闭包损坏则按下述稳定fatal拒绝，不能猜cue或把它降成自由文本Gap。它只能支持pending hypothesis。

**最小可执行`ProcessCueProfileV1`。** `ProcessSemanticCueV1.processCueProfileRef`逐字等于本run `analysis-run-request-v2.profileBundleRef`，不是新的run-request字段或第十六项输出。M6必须fresh-reopen该content-addressed bundle并exact取得以下Step 06 member；缺字段、extra field、乱序/重复lexicon或版本不等均fatal `PROCESS_CUE_PROFILE_INVALID`：

~~~text
flowInterpretation {
  crossFlowCandidateRuleVersion=flow-interpretation-cross-flow-candidate-rules-v1
  processCueProfile {
    schemaVersion=flow-interpretation-process-cue-profile-v1
    normalizationRule=R0_NFC_EXACT_V1
    entryVerbLexicon[]
    stateWordLexicon[]
  }
}
~~~

两个lexicon都是有限、非null、UTF-8 byte严格排序且无重复的string数组（允许空）；每项必须非空、已经NFC，且通过DESIGN §13.6既有control-character/bidi检查。`R0_NFC_EXACT_V1`的写owner是R0 M2 `analysis.interpretation.proposal.RegistryProposalRunner`：它先按既有规则验证Provider response的raw `label/purpose` Unicode、control/bidi与budget，再分别计算`Normalizer.normalize(raw,NFC)`，对normalized bytes重验非空/长度预算，以normalized值构造`BusinessRegistryProposal.normalizedLabel/normalizedPurpose`，且proposal identity也只使用这两个normalized值。它不trim、不折叠空白、不case-fold、不切词、不去标点。R0 M3 freezer在冻结前再次要求两个字段已是NFC。M6不再normalize或读取raw response；它只验证`RepositoryInterpretationRegistryItemV3.normalizedLabel`满足`NFC(value)=value`并令`normalizedCueKey=value`。大小写或空格不同的冻结值不匹配；non-NFC frozen value是`PROCESS_MODEL_REFERENCE_INVALID` fatal，不是“正规形不同但合法不匹配”。lexicon值按同一exact规则比较，不能从method、route、中文显示名、SourceExcerpt或Step 05 signal补term。

v0最小cue枚举只需要`REGISTRY_BUSINESS_TERM`：对每个UTF-8排序的不同Flow pair，枚举双方`proposalKind=BUSINESS_TERM`的registry item pair；两端`basisAtomIds[]`都必须非空、逐字等于各item值并是各自Capsule `registryProposalBasisAtomIds[]`子集，且每个atom仍闭合到本Flow Fact/Proof/Evidence。两个`normalizedCueKey`逐字相等时产生一个cue，left/right registry item按Flow后按item ID排序，relation固定`SEMANTIC_CUE/PENDING_ONLY/UNDIRECTED`。没有equal pair是正常exact absence，不写“缺少同名词”Gap；foreign/missing Capsule basis是upstream reference fatal，不能降级匹配。`entryVerbLexicon`或`stateWordLexicon`命中本身不够：在现有Fact family没有可验证的entry-bound atom、现有Step 05没有相应state signal时，v0不得发`ENTRY_VERB`或`STATE_WORD`；未来只有本节原有两项额外basis gate真实闭合后才可启用，仍不新增source/model Fact。

### 5.2 exact entry target与direct-call candidate

M6不得从entry名称、route、方法simple name或Flow叙述猜callee。`CrossFlowCandidateCompiler`从已fresh-reopen的Step 03/05值为每个COMPILED Flow计算唯一target：

1. 用`FlowSlice.entryId/rootNodeId`命中ControlFlow `kind=ENTRY` root；root的`owningEntryIds`必须**包含**该entry，不要求singleton。
2. 在该entry的`semanticTraversalOrder.edgeIds[]`中筛选从root出发、`kind=NEXT,ruleId=control-flow-entry-root-v1,resolution=EXACT,guardNodeId=null,polarity=null`的edge，必须恰一条。
3. edge的`toNodeId`必须命中CodeStructure `kind=METHOD` node，且其`canonicalValue`满足`<type>#<method>(<parameter-types>)` grammar；`entryTargetKey`就是这个完整canonical value，不做任何normalize。

缺/重entry-root edge、foreign traversal edge、缺METHOD endpoint或canonical grammar错误均fatal `PROCESS_ENTRY_TARGET_INVALID`，不是可由名称或模型补的Gap。两个不同entry Flow合法共享同一METHOD target时，二者都进入该key的callee集合；这不是source ownership冲突，Step 05 §8.4已用per-Flow span identity隔离证据。

随后对每个Flow的每条proof-closed `EXPLICIT_CALL{direction=INVOKES,anchorKind=CALL_TARGET}`，若`anchorKey`逐字命中一个或多个**其他**Flow的`entryTargetKey`，就为每个matching callee枚举一项`EXPLICIT_CALL_TO_ENTRY/PROVEN_HANDOFF` positive pair。caller落在canonical left/right哪一侧，哪一侧的signal ID数组就恰含该signal，callee侧数组为空；`anchorKey`保存exact entry target，direction为caller→callee。若同一Flow pair有多个qualifying call signal全部保留，reciprocal directions按§5聚合成`UNDIRECTED`；anchor不匹配只是零pair，两个Flow仍各进singleton/其他合法group。`GENERIC_TECHNICAL`不阻止这一条exact-call pair，因为call→entry关系本身由ProgramGraphs+Fact Proof闭合；它仍不能让`JAVA_TYPE_ANCHOR`、tenant/audit字段或同名方法单独成边，也不能证明调用已执行、业务先后或外部效果。

domain分类仅是`SHARED_ANCHOR`行和Step 05 §8.6完整domain acceptance的门，不是本节direct-call v0的门。当前没有一个持久化Fact/atom/Proof/rule能区分domain type与repository-local logger/util；因此M6必须保留generic specificity且不得把repository ownership当分类器。这个缺口若进入完整Step 05验收才需要另一个有界Sol合同，不影响本节candidate compiler实现。

**Luna RED / Terra GREEN与增量工时。** Luna先在`RegistryProposalRunnerTest`固定raw decomposed Unicode经NFC后写入normalized label/purpose且identity使用normalized值，并固定control/bidi/normalized-byte-budget拒绝；Terra在`RegistryProposalRunner`实现该deterministic normalization，并让M3 freezer revalidate。Luna再在未来public `CrossFlowCandidateCompilerTest`用fresh-reopenedStep 03–05 artifacts固定四个断言：(1) caller `EXPLICIT_CALL.anchorKey`等于第二Flow exact target时得到一条有方向`PROVEN_HANDOFF`，caller侧signal array非空而callee侧为空；(2) target key只差case/参数、或call指向普通同名METHOD时零relation，删除entry-root edge则`PROCESS_ENTRY_TARGET_INVALID`；(3) 两Flow frozen `BUSINESS_TERM.normalizedLabel`与same-Flow atom basis逐字相等时只产生`REGISTRY_BUSINESS_TERM/PENDING_ONLY`；(4) case/space不同、non-NFC frozen value、空/foreign basis或仅lexicon命中不得产生specialized cue，malformed profile fatal。Terra只实现M6 typed reader/compiler及上述确定性规则；不改Provider、八步、十五文件、57项、Step 07或九章。此澄清相对当前已经计划的counter/M2 publisher/zero-Flow工作约增加18–26连续小时：Step 04 v3 Fact/Proof/public migration约8–12h，Step 05 v3/v6映射与fixture migration约4–6h，per-Flow span identity约2–3h，M6这两条public seam及R0 normalization correction约4–5h；不重复计算完整M6–M9或已GREEN M2 publisher。

规范候选边的可读投影如下；`leftFlowSliceId < rightFlowSliceId`按UTF-8 byte order，direction另存。它不是identity replay specimen；§7.1才是包含locator等完整字段的wire合同：

~~~json
{
  "candidateRelationId": "process-relation:sha256…",
  "leftFlowSliceId": "flow:store-approval",
  "rightFlowSliceId": "flow:regional-approval",
  "strongestSignalLevel": "PROVEN_HANDOFF",
  "direction": "LEFT_TO_RIGHT",
  "relationUse": "PROCESS_CANDIDATE",
  "supportingProcessJoinSignalIds": ["process-join-signal:request-id-return"],
  "processSemanticCueIds": [],
  "counterProcessJoinSignalIds": ["process-join-signal:status-conflict"],
  "blockingCounterProcessJoinSignalIds": ["process-join-signal:status-conflict"],
  "factIds": ["fact:request-id"],
  "proofIds": ["proof:return-to-input"],
  "evidenceNodeIds": ["evidence:request-id"],
  "sourceLocators": [],
  "gapIds": ["gap:purchase-write-unproven"]
}
~~~

M6不能把edge拓扑排序成“真实顺序”。顺序、并行、替代、回退都是P1 hypothesis，最终由Step 07程序准入。

## 6. 模型有界材料

### 6.1 局部R0/R1/R2

每次只含一个`flowSliceId`及其完整Capsule view：Facts/atoms/Proof IDs、Gaps、Outcomes、连续source excerpts、projection obligations、allowed registry items和budget。发送bytes严格是`canonicalJson(inputJson)`；同一Flow的R0/R1/R2不能看其他Flow。R2只复核R1。

### 6.2 过程P1/P2

M6保存的`ProcessPersistedMaterialV1`是程序审计材料：它含完整`ProcessJoinSignalV1.sourceLocators`与`ModelEvidenceSpanV4.sourceExcerpt`，因此是path-bearing、metadata-only，**绝不进入Provider request**。M7先建立下节唯一的`BusinessProcessTaskShardV1`，然后只为`MODEL_SAFE` shard按确定字段映射生成独立`ProcessModelPacketV1`。packet只含path-free positions/excerpts；任何递归字段名`path`、`SourceLocatorV1`或`SourceExcerptV1`值都会使`PROCESS_MODEL_PACKET_PATH_LEAK` fatal。

M7 partition算法固定。按`processEvidenceGroupId`处理groups；组内按`candidateRelationId`建立atomic units，每个unit拥有恰一relation并以其两个endpoint Flow作为context，singleton无relation组建立一个owner集合为空、context为唯一member Flow的unit。unit按relation ID（singleton按Flow ID）排序；每个含model-ineligible context Flow的unit独立成为`NO_MODEL` shard。其余units按顺序做deterministic greedy packing：只有加入下一个完整unit后的Flow/relation/signal/registry/input-byte limits全部不超限才合并，否则关闭当前shard再开下一个；单个完整unit仍超限则独立成为带typed `PROCESS_TASK_BUDGET_EXCEEDED` `ProcessInterpretationGapV1`的`NO_MODEL` shard，绝不截断。shard `ownerCandidateRelationIds`是units owner的排序union，`contextFlowSliceIds`是所有owner edge endpoints的排序union（singleton为其member），`shardOrdinal`按本组closed shards从0连续编号。由此每组至少一shard、每edge在`A`中恰一owner、只读context可跨shard重复且同一profile/budget下partition唯一。

Provider的application-layer语义请求恰为下节`ProcessModelRequestV1`的canonical JSON bytes。P1只可引用该shard packet的Flow、owner relation、signal、Fact/Proof/Evidence/Gap和registry keys。P2收到**同一逐字packet**和每个accepted P1 hypothesis的semantic projection；其逐hypothesis allowlist由P1 refs确定，不能在同shard不同hypothesis之间借材料，也不能用语言合理性补材料。Provider adapter可增加传输认证/协议封装，但不得增加模型可见语义字段；所有request hash都对上述application-layercanonical bytes计算。

`BusinessProcessHypothesisV2`使用以下完整字段/闭合引用specimen。它是明确合成的结构fixture，不代表jshERP；所有ID值仅为符合`<prefix>:<64 lowercase hex>`安全语法并维持引用闭包的结构token，未按展示bytes重算semantic identity，不能用于hash replay、identity preimage或跨示例ID映射；每个displayed object的key均按UTF-8 byte order规范排序：

~~~json
{
  "activityKeys": [
    {
      "key": "ACTIVITY_P_STORE_APPROVAL",
      "keyKind": "REGISTRY",
      "registryItemId": "registry-item:cff88e5432754f7744756abd14b9dbcca58c61ea769551113652844dd3e32b8a",
      "technicalAnchorIds": []
    },
    {
      "key": "ACTIVITY_P_REGION_APPROVAL",
      "keyKind": "REGISTRY",
      "registryItemId": "registry-item:d1878d798f51516049f2a5b81b8fed5d9af4bfafe95d2f8ff4f62ffd955f7ac3",
      "technicalAnchorIds": []
    }
  ],
  "alternativeClaimIds": [],
  "artifactType": "FLOW_INTERPRETATION_BUSINESS_PROCESS_HYPOTHESIS",
  "branchClaimIds": [],
  "businessProcessHypothesisId": "business-process-hypothesis:f0abc6e6c537d54c4c7362a9b73503397659480ab86642f610d72acc19728fc8",
  "businessRoleKeys": [
    {
      "key": "ROLE_P_STORE_APPROVER",
      "keyKind": "REGISTRY",
      "registryItemId": "registry-item:016fb55d9af2731760e21064d2905f717d90f110c6cb851df5caaa985a6802f2",
      "technicalAnchorIds": []
    },
    {
      "key": "ROLE_P_REGION_APPROVER",
      "keyKind": "REGISTRY",
      "registryItemId": "registry-item:ef3fba46f85b6c001cde49358e4e64fe8a95b4126984ab9d0ef6846a8e36c2af",
      "technicalAnchorIds": []
    }
  ],
  "candidateRelations": [
    {
      "candidateRelationId": "process-relation:2d7ca03a7e01ed3b5bf5080a6cdbc042844149604c341fc2c28fd0bc9110f144",
      "counterProcessJoinSignalIds": [],
      "processClaimIds": [
        "process-claim:9ee5ffed0169a6702e61d085fdc82509749550fb0f7e7d7b046b096677d9a277"
      ],
      "processSemanticCueIds": [],
      "supportProcessJoinSignalIds": [
        "process-join-signal:3f04cfb2adb3d86fc64f2c3606122bc6ed72bcdb9f0e879dd149b71f324172a5"
      ]
    }
  ],
  "conditionClaimIds": [],
  "endResultClaimId": "process-claim:aa9f85d770d04544261522449d3f13d08c8d565142edc1f30bf3a92042ff49e7",
  "fallbackClaimIds": [],
  "finalReviewDecision": "NARROW",
  "inputObjectKeys": [
    {
      "key": "object:replenishment-request",
      "keyKind": "TECHNICAL",
      "registryItemId": null,
      "technicalAnchorIds": [
        "java-type:f6e91911a9a75170d403752817d9d4d8cf48a9011972dfd6d8a4ee5b2d5f5269"
      ]
    }
  ],
  "memberFlows": [
    {
      "activityKey": {
        "key": "ACTIVITY_P_STORE_APPROVAL",
        "keyKind": "REGISTRY",
        "registryItemId": "registry-item:cff88e5432754f7744756abd14b9dbcca58c61ea769551113652844dd3e32b8a",
        "technicalAnchorIds": []
      },
      "flowSliceId": "flow:845923e67c18ae248ef98ecdb1276637fd51b8307b4f4c62bad353eae30219a8",
      "role": "START",
      "stageKey": {
        "key": "stage:10-store-approval",
        "keyKind": "TECHNICAL",
        "registryItemId": null,
        "technicalAnchorIds": [
          "entry:9d936463beb69145c707968441c731aabab23c6fb5ca2ce17633c92572ca375a"
        ]
      },
      "supportingProcessClaimIds": [
        "process-claim:0e00c4760f23ede28ac71ebfe65c1cbec305dd5516f35a148487be8da6b9a9bc",
        "process-claim:9ee5ffed0169a6702e61d085fdc82509749550fb0f7e7d7b046b096677d9a277",
        "process-claim:e33055efd6795247d749111a7c14d002be6f5779c9c705d1385361d2f82c768c"
      ]
    },
    {
      "activityKey": {
        "key": "ACTIVITY_P_REGION_APPROVAL",
        "keyKind": "REGISTRY",
        "registryItemId": "registry-item:d1878d798f51516049f2a5b81b8fed5d9af4bfafe95d2f8ff4f62ffd955f7ac3",
        "technicalAnchorIds": []
      },
      "flowSliceId": "flow:5a17c1e9844e07d878309fac255adc8fc0204b2a9cc64bed9e5e6e10b8477fdf",
      "role": "TERMINAL",
      "stageKey": {
        "key": "stage:20-region-approval",
        "keyKind": "TECHNICAL",
        "registryItemId": null,
        "technicalAnchorIds": [
          "entry:2ff3b75fe291d8ce393cc8bed049a2f7114a8a867dae7574a3022f5b4026e2db"
        ]
      },
      "supportingProcessClaimIds": [
        "process-claim:01f5a1365c478440e7faa99e8ef40cc5381c313d1e8d9c72db1b0601c725e3a1",
        "process-claim:9ee5ffed0169a6702e61d085fdc82509749550fb0f7e7d7b046b096677d9a277",
        "process-claim:aa9f85d770d04544261522449d3f13d08c8d565142edc1f30bf3a92042ff49e7"
      ]
    }
  ],
  "objectKeys": [
    {
      "key": "object:replenishment-request",
      "keyKind": "TECHNICAL",
      "registryItemId": null,
      "technicalAnchorIds": [
        "java-type:f6e91911a9a75170d403752817d9d4d8cf48a9011972dfd6d8a4ee5b2d5f5269"
      ]
    },
    {
      "key": "object:approved-procurement-request",
      "keyKind": "TECHNICAL",
      "registryItemId": null,
      "technicalAnchorIds": [
        "java-type:94cb3ffd879bf81eac194fd852a807dcaa6679efb33b74520015ee8ccb29045f"
      ]
    }
  ],
  "outputObjectKeys": [
    {
      "key": "object:approved-procurement-request",
      "keyKind": "TECHNICAL",
      "registryItemId": null,
      "technicalAnchorIds": [
        "java-type:94cb3ffd879bf81eac194fd852a807dcaa6679efb33b74520015ee8ccb29045f"
      ]
    }
  ],
  "p1RoundId": "process-model-round:eb23171f9fec04973b1cf84f94650cb9d893caac11e858c9e98a5ee4cbf9251f",
  "p1TaskId": "process-model-task:7a3b482cfdd0c2c7665d4971daec8fd933776ddfb5102f0832330057c2158f3e",
  "p2RoundId": "process-model-round:256888825f3d098a7aeb5d1bbd2f50c0a8af259fbb1d046dbba1fe51617a5b28",
  "p2TaskId": "process-model-task:593b26976db3e5aad81ab910be783c34c270fe9dd10ed6993d67d86707bc3755",
  "parallelClaimIds": [],
  "pendingAssumptionClaimIds": [],
  "processClaims": [
    {
      "blockingCounterProcessJoinSignalIds": [],
      "candidateRelationIds": [],
      "claimKind": "PURPOSE",
      "counterProcessJoinSignalIds": [],
      "evidenceNodeIds": [
        "evidence:db9c6307d62362cde606b64c409c313e078e406d539d35a6056f2252e716a689"
      ],
      "factIds": [
        "fact:cea94e6141ae25efb17a1c8a1466b329c1356046fd000d979104dc0b368ab129"
      ],
      "gapIds": [],
      "memberFlowSliceIds": [
        "flow:5a17c1e9844e07d878309fac255adc8fc0204b2a9cc64bed9e5e6e10b8477fdf",
        "flow:845923e67c18ae248ef98ecdb1276637fd51b8307b4f4c62bad353eae30219a8"
      ],
      "objectKeys": [
        {
          "key": "object:replenishment-request",
          "keyKind": "TECHNICAL",
          "registryItemId": null,
          "technicalAnchorIds": [
            "java-type:f6e91911a9a75170d403752817d9d4d8cf48a9011972dfd6d8a4ee5b2d5f5269"
          ]
        }
      ],
      "predicateKey": {
        "key": "purpose:authorize-replenishment",
        "keyKind": "TECHNICAL",
        "registryItemId": null,
        "technicalAnchorIds": [
          "fact:cea94e6141ae25efb17a1c8a1466b329c1356046fd000d979104dc0b368ab129"
        ]
      },
      "processClaimId": "process-claim:0e00c4760f23ede28ac71ebfe65c1cbec305dd5516f35a148487be8da6b9a9bc",
      "processSemanticCueIds": [],
      "proofIds": [
        "proof:204384ac8cded417faa92b7cf21968193ec9c5ad65e25f65c4d8830599dc041f"
      ],
      "subjectKeys": [
        {
          "key": "process:replenishment-approval",
          "keyKind": "TECHNICAL",
          "registryItemId": null,
          "technicalAnchorIds": [
            "process-evidence-group:e26641cdad55a54d08077243cb917ccfa8be8f6e1261202d95387bdcedac981c"
          ]
        }
      ],
      "supportProcessJoinSignalIds": [
        "process-join-signal:3f04cfb2adb3d86fc64f2c3606122bc6ed72bcdb9f0e879dd149b71f324172a5"
      ]
    },
    {
      "blockingCounterProcessJoinSignalIds": [],
      "candidateRelationIds": [],
      "claimKind": "ACTIVITY",
      "counterProcessJoinSignalIds": [],
      "evidenceNodeIds": [
        "evidence:65ec773113e3ebc5c3f33e5d75170f818717ae686759039c19e1539973aaa3d5"
      ],
      "factIds": [
        "fact:cec9dcc7d4fb6ac7048fe4209a87a9dcc642538766d96d23f726cdeb9929e98d"
      ],
      "gapIds": [],
      "memberFlowSliceIds": [
        "flow:845923e67c18ae248ef98ecdb1276637fd51b8307b4f4c62bad353eae30219a8"
      ],
      "objectKeys": [
        {
          "key": "object:replenishment-request",
          "keyKind": "TECHNICAL",
          "registryItemId": null,
          "technicalAnchorIds": [
            "java-type:f6e91911a9a75170d403752817d9d4d8cf48a9011972dfd6d8a4ee5b2d5f5269"
          ]
        }
      ],
      "predicateKey": {
        "key": "activity:checks-request",
        "keyKind": "TECHNICAL",
        "registryItemId": null,
        "technicalAnchorIds": [
          "entry:9d936463beb69145c707968441c731aabab23c6fb5ca2ce17633c92572ca375a"
        ]
      },
      "processClaimId": "process-claim:e33055efd6795247d749111a7c14d002be6f5779c9c705d1385361d2f82c768c",
      "processSemanticCueIds": [],
      "proofIds": [
        "proof:16621e1dbb2e4b043e582516388ae2582c44ec84ae2d3a9facd7005095a8900f"
      ],
      "subjectKeys": [
        {
          "key": "ACTIVITY_P_STORE_APPROVAL",
          "keyKind": "REGISTRY",
          "registryItemId": "registry-item:cff88e5432754f7744756abd14b9dbcca58c61ea769551113652844dd3e32b8a",
          "technicalAnchorIds": []
        }
      ],
      "supportProcessJoinSignalIds": [
        "process-join-signal:3f04cfb2adb3d86fc64f2c3606122bc6ed72bcdb9f0e879dd149b71f324172a5"
      ]
    },
    {
      "blockingCounterProcessJoinSignalIds": [],
      "candidateRelationIds": [],
      "claimKind": "ACTIVITY",
      "counterProcessJoinSignalIds": [],
      "evidenceNodeIds": [
        "evidence:12a9fcffa471cb680fc950197a28947b132f7cb2237da521fbeb31a733d85112"
      ],
      "factIds": [
        "fact:9e867f7d056e3572d3046ccc5ec98a61c5dacc68abb6d3692be03434e0a08cd4"
      ],
      "gapIds": [],
      "memberFlowSliceIds": [
        "flow:5a17c1e9844e07d878309fac255adc8fc0204b2a9cc64bed9e5e6e10b8477fdf"
      ],
      "objectKeys": [
        {
          "key": "object:approved-procurement-request",
          "keyKind": "TECHNICAL",
          "registryItemId": null,
          "technicalAnchorIds": [
            "java-type:94cb3ffd879bf81eac194fd852a807dcaa6679efb33b74520015ee8ccb29045f"
          ]
        }
      ],
      "predicateKey": {
        "key": "activity:reviews-approved-request",
        "keyKind": "TECHNICAL",
        "registryItemId": null,
        "technicalAnchorIds": [
          "entry:2ff3b75fe291d8ce393cc8bed049a2f7114a8a867dae7574a3022f5b4026e2db"
        ]
      },
      "processClaimId": "process-claim:01f5a1365c478440e7faa99e8ef40cc5381c313d1e8d9c72db1b0601c725e3a1",
      "processSemanticCueIds": [],
      "proofIds": [
        "proof:205798dccbe2c5afc6fac8406720580a51a4de9562b21c513f1691f21d7c784d"
      ],
      "subjectKeys": [
        {
          "key": "ACTIVITY_P_REGION_APPROVAL",
          "keyKind": "REGISTRY",
          "registryItemId": "registry-item:d1878d798f51516049f2a5b81b8fed5d9af4bfafe95d2f8ff4f62ffd955f7ac3",
          "technicalAnchorIds": []
        }
      ],
      "supportProcessJoinSignalIds": [
        "process-join-signal:3f04cfb2adb3d86fc64f2c3606122bc6ed72bcdb9f0e879dd149b71f324172a5"
      ]
    },
    {
      "blockingCounterProcessJoinSignalIds": [],
      "candidateRelationIds": [
        "process-relation:2d7ca03a7e01ed3b5bf5080a6cdbc042844149604c341fc2c28fd0bc9110f144"
      ],
      "claimKind": "TRANSITION",
      "counterProcessJoinSignalIds": [],
      "evidenceNodeIds": [
        "evidence:b961de54aea44b53d55f51ab12e85a651c373ac58dea096c88f6cef288e15799"
      ],
      "factIds": [
        "fact:4e8527054a6a043200fd7680609b29ccf94724136a81da6425ff11d2281ddef2"
      ],
      "gapIds": [],
      "memberFlowSliceIds": [
        "flow:5a17c1e9844e07d878309fac255adc8fc0204b2a9cc64bed9e5e6e10b8477fdf",
        "flow:845923e67c18ae248ef98ecdb1276637fd51b8307b4f4c62bad353eae30219a8"
      ],
      "objectKeys": [
        {
          "key": "ACTIVITY_P_REGION_APPROVAL",
          "keyKind": "REGISTRY",
          "registryItemId": "registry-item:d1878d798f51516049f2a5b81b8fed5d9af4bfafe95d2f8ff4f62ffd955f7ac3",
          "technicalAnchorIds": []
        }
      ],
      "predicateKey": {
        "key": "transition:request-id-handoff",
        "keyKind": "TECHNICAL",
        "registryItemId": null,
        "technicalAnchorIds": [
          "process-relation:2d7ca03a7e01ed3b5bf5080a6cdbc042844149604c341fc2c28fd0bc9110f144"
        ]
      },
      "processClaimId": "process-claim:9ee5ffed0169a6702e61d085fdc82509749550fb0f7e7d7b046b096677d9a277",
      "processSemanticCueIds": [],
      "proofIds": [
        "proof:427cf5d2cbafdb11e4ef14aa8ae0e5d4ea080c05d250e9e85da169c55d49447d"
      ],
      "subjectKeys": [
        {
          "key": "ACTIVITY_P_STORE_APPROVAL",
          "keyKind": "REGISTRY",
          "registryItemId": "registry-item:cff88e5432754f7744756abd14b9dbcca58c61ea769551113652844dd3e32b8a",
          "technicalAnchorIds": []
        }
      ],
      "supportProcessJoinSignalIds": [
        "process-join-signal:3f04cfb2adb3d86fc64f2c3606122bc6ed72bcdb9f0e879dd149b71f324172a5"
      ]
    },
    {
      "blockingCounterProcessJoinSignalIds": [],
      "candidateRelationIds": [],
      "claimKind": "END_RESULT",
      "counterProcessJoinSignalIds": [],
      "evidenceNodeIds": [
        "evidence:1b103a8b1272eb39231becd50a633b72b6acf64ff7eeae31d1b233c0d4408c12"
      ],
      "factIds": [
        "fact:a065d2dbe7e7599247442feccebf4ebe7d9f03457c330230bc2b31ca099a05c2"
      ],
      "gapIds": [],
      "memberFlowSliceIds": [
        "flow:5a17c1e9844e07d878309fac255adc8fc0204b2a9cc64bed9e5e6e10b8477fdf"
      ],
      "objectKeys": [
        {
          "key": "object:approved-procurement-request",
          "keyKind": "TECHNICAL",
          "registryItemId": null,
          "technicalAnchorIds": [
            "java-type:94cb3ffd879bf81eac194fd852a807dcaa6679efb33b74520015ee8ccb29045f"
          ]
        }
      ],
      "predicateKey": {
        "key": "result:approved-request-ready",
        "keyKind": "TECHNICAL",
        "registryItemId": null,
        "technicalAnchorIds": [
          "fact:a065d2dbe7e7599247442feccebf4ebe7d9f03457c330230bc2b31ca099a05c2"
        ]
      },
      "processClaimId": "process-claim:aa9f85d770d04544261522449d3f13d08c8d565142edc1f30bf3a92042ff49e7",
      "processSemanticCueIds": [],
      "proofIds": [
        "proof:bd9f8b011eff1274a287156ef91020bd80310d08f36242269ed5613b555cdcef"
      ],
      "subjectKeys": [
        {
          "key": "process:replenishment-approval",
          "keyKind": "TECHNICAL",
          "registryItemId": null,
          "technicalAnchorIds": [
            "process-evidence-group:e26641cdad55a54d08077243cb917ccfa8be8f6e1261202d95387bdcedac981c"
          ]
        }
      ],
      "supportProcessJoinSignalIds": [
        "process-join-signal:55be38edb45e56b8120bf7ef5f7273b055895e58d8da3e80fa0b9ad7515acba1"
      ]
    }
  ],
  "processEvidenceGroupIds": [
    "process-evidence-group:e26641cdad55a54d08077243cb917ccfa8be8f6e1261202d95387bdcedac981c"
  ],
  "processHypothesisReviewId": "process-hypothesis-review:0665764e7cbe47f8a63b3138df3a25a53bfb6122959de939e4b0d82f12176a6e",
  "purposeClaimId": "process-claim:0e00c4760f23ede28ac71ebfe65c1cbec305dd5516f35a148487be8da6b9a9bc",
  "readerSlots": [
    {
      "processClaimIds": [
        "process-claim:0e00c4760f23ede28ac71ebfe65c1cbec305dd5516f35a148487be8da6b9a9bc",
        "process-claim:aa9f85d770d04544261522449d3f13d08c8d565142edc1f30bf3a92042ff49e7"
      ],
      "registryOrTechnicalKeys": [
        {
          "key": "process:replenishment-approval",
          "keyKind": "TECHNICAL",
          "registryItemId": null,
          "technicalAnchorIds": [
            "process-evidence-group:e26641cdad55a54d08077243cb917ccfa8be8f6e1261202d95387bdcedac981c"
          ]
        }
      ],
      "slotKind": "PROCESS_NAME",
      "text": "补货审批到采购准备（合成）"
    },
    {
      "processClaimIds": [
        "process-claim:01f5a1365c478440e7faa99e8ef40cc5381c313d1e8d9c72db1b0601c725e3a1",
        "process-claim:0e00c4760f23ede28ac71ebfe65c1cbec305dd5516f35a148487be8da6b9a9bc",
        "process-claim:9ee5ffed0169a6702e61d085fdc82509749550fb0f7e7d7b046b096677d9a277",
        "process-claim:aa9f85d770d04544261522449d3f13d08c8d565142edc1f30bf3a92042ff49e7",
        "process-claim:e33055efd6795247d749111a7c14d002be6f5779c9c705d1385361d2f82c768c"
      ],
      "registryOrTechnicalKeys": [
        {
          "key": "process:replenishment-approval",
          "keyKind": "TECHNICAL",
          "registryItemId": null,
          "technicalAnchorIds": [
            "process-evidence-group:e26641cdad55a54d08077243cb917ccfa8be8f6e1261202d95387bdcedac981c"
          ]
        }
      ],
      "slotKind": "PROCESS_SUMMARY",
      "text": "门店检查补货申请后，将同一申请标识交给区域审批；结果仅表示已批准的采购准备材料，不表示外部系统已经建单（合成）。"
    },
    {
      "processClaimIds": [
        "process-claim:9ee5ffed0169a6702e61d085fdc82509749550fb0f7e7d7b046b096677d9a277"
      ],
      "registryOrTechnicalKeys": [
        {
          "key": "transition:request-id-handoff",
          "keyKind": "TECHNICAL",
          "registryItemId": null,
          "technicalAnchorIds": [
            "process-relation:2d7ca03a7e01ed3b5bf5080a6cdbc042844149604c341fc2c28fd0bc9110f144"
          ]
        }
      ],
      "slotKind": "TRANSITION",
      "text": "同一补货申请标识从门店审批交给区域审批（合成）。"
    }
  ],
  "schemaVersion": "flow-interpretation-business-process-hypothesis-v2",
  "stageKeys": [
    {
      "key": "stage:10-store-approval",
      "keyKind": "TECHNICAL",
      "registryItemId": null,
      "technicalAnchorIds": [
        "entry:9d936463beb69145c707968441c731aabab23c6fb5ca2ce17633c92572ca375a"
      ]
    },
    {
      "key": "stage:20-region-approval",
      "keyKind": "TECHNICAL",
      "registryItemId": null,
      "technicalAnchorIds": [
        "entry:2ff3b75fe291d8ce393cc8bed049a2f7114a8a867dae7574a3022f5b4026e2db"
      ]
    }
  ],
  "stateKeys": [],
  "taskShardId": "process-shard:b32947c90a3f29b9dd43116f21db8c15d568475e149c965907a6bdf6254ac0e5"
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
| `business-process-hypotheses.jsonl` | retained/narrowed/pending及P2 GAP/FAILED下未review的P1 hypothesis；P2 DROP不发布 |
| `process-interpretation-dispositions.jsonl` | `A`；每个model-safe/no-model shard各一条闭合处置，并作为全部Step 06-owned typed Gap value的canonical carrier |
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
flow-interpretation-process-evidence-group-v2 / FLOW_INTERPRETATION_PROCESS_EVIDENCE_GROUP
flow-interpretation-process-model-task-v1 / FLOW_INTERPRETATION_PROCESS_MODEL_TASK
flow-interpretation-process-model-round-v1 / FLOW_INTERPRETATION_PROCESS_MODEL_ROUND
flow-interpretation-business-process-hypothesis-v2 / FLOW_INTERPRETATION_BUSINESS_PROCESS_HYPOTHESIS
flow-interpretation-process-interpretation-disposition-v2 / FLOW_INTERPRETATION_PROCESS_INTERPRETATION_DISPOSITION
flow-interpretation-generation-receipt-v3 / FLOW_INTERPRETATION_GENERATION_RECEIPT
~~~

### 7.1 新增standalone wire的完整字段

本节是五项新增standalone records及升级后generation receipt的exact合同。`!`表示required non-null，`?`表示**字段仍required但值可为null**，`[]!`表示required array（可空但不可null）；未列字段和extra field一律拒绝。object key按canonical JSON UTF-8 byte order；普通ID arrays排序去重，stage/member/claim/slot数组保留声明的业务顺序并另由其ID保证唯一。JSONL分别按`processEvidenceGroupId`、`processModelTaskId`、`processModelRoundId`、`businessProcessHypothesisId`、`processInterpretationDispositionId`和`generationReceiptId`排序。

~~~text
ProcessEvidenceGroupV2
  schemaVersion!=flow-interpretation-process-evidence-group-v2
  artifactType!=FLOW_INTERPRETATION_PROCESS_EVIDENCE_GROUP
  processEvidenceGroupId!
  groupKind!: CONNECTED_COMPONENT | SINGLETON
  memberFlowSliceIds[]!
  candidateRelations[]!: ProcessCandidateRelationV2
  processSemanticCues[]!: ProcessSemanticCueV1
  supportingProcessJoinSignalIds[]!
  counterProcessJoinSignalIds[]!
  repositoryInterpretationRegistryItemIds[]!
  modelEligibility!: MODEL_SAFE | MODEL_INELIGIBLE
  modelIneligibilityGapIds[]!
  persistedMaterial!: ProcessPersistedMaterialV1

ProcessCandidateRelationV2
  candidateRelationId!
  leftFlowSliceId!, rightFlowSliceId!       // left < right; never equal
  strongestSignalLevel!: PROVEN_HANDOFF | SHARED_ANCHOR | SEMANTIC_CUE
  direction!: LEFT_TO_RIGHT | RIGHT_TO_LEFT | UNDIRECTED
  relationUse!: PROCESS_CANDIDATE | PENDING_ONLY
  positivePairBases[]!: ProcessRelationPositivePairBasisV1 // nonempty; every qualifying pair
  counterBases[]!: ProcessRelationCounterBasisV1           // complete scoped union
  supportingProcessJoinSignalIds[]!
  processSemanticCueIds[]!
  counterProcessJoinSignalIds[]!
  blockingCounterProcessJoinSignalIds[]!
  factIds[]!, proofIds[]!, evidenceNodeIds[]!, sourceLocators[]!, gapIds[]!

ProcessRelationPositivePairBasisV1       // program-only exact association basis
  pairKind!: EXPLICIT_CALL_TO_ENTRY | IDENTIFIER_HANDOFF | STATE_HANDOFF |
             EVENT_HANDOFF | SHARED_ANCHOR | SEMANTIC_CUE
  signalLevel!: PROVEN_HANDOFF | SHARED_ANCHOR | SEMANTIC_CUE
  leftProcessJoinSignalIds[]!, rightProcessJoinSignalIds[]!
  processSemanticCueIds[]!
  anchorKind!: CALL_TARGET | BUSINESS_IDENTIFIER | STATE | EVENT |
               BUSINESS_OBJECT | JAVA_TYPE | SQL_TABLE | FIELD | REGISTRY_TERM
  anchorKey!
  direction!: LEFT_TO_RIGHT | RIGHT_TO_LEFT | UNDIRECTED

ProcessRelationCounterBasisV1            // program-only; scoped to one exact positive pair
  counterKind!: EXPLICIT_BLOCKING_SIGNAL | DIFFERENT_BUSINESS_OBJECT
  scopedPositivePair!: ProcessRelationPositivePairBasisV1
  leftCounterProcessJoinSignalIds[]!
  rightCounterProcessJoinSignalIds[]!

ProcessSemanticCueV1
  processSemanticCueId!
  cueKind!: REGISTRY_BUSINESS_TERM | ENTRY_VERB | STATE_WORD
  leftFlowSliceId!, rightFlowSliceId!
  leftRegistryItemId!, rightRegistryItemId!
  leftProvisionalKey!, rightProvisionalKey!
  normalizedCueKey!
  leftBasisAtomIds[]!, rightBasisAtomIds[]!
  leftEntryId?, rightEntryId?
  leftStateSignalIds[]!, rightStateSignalIds[]!
  processCueProfileRef!
  pendingOnly=true

ProcessPersistedMaterialV1                // program-only, path-bearing, never sent to Provider
  flowViews[]!: ProcessPersistedFlowViewV1
  relationViews[]!: ProcessCandidateRelationV2
  registryItems[]!: RepositoryInterpretationRegistryItemV3
  limits!: ProcessMaterialLimitsV1

ProcessPersistedFlowViewV1
  flowSliceId!, evidenceCapsuleId!, evidenceCapsuleRef!
  entryId!
  factViews[]!: FlowFactViewV1
  gapViews[]!: FlowGapViewV1
  outcomePathViews[]!: FlowOutcomePathViewV1
  processJoinSignals[]!: ProcessJoinSignalV1
  modelEvidenceSpans[]!: ModelEvidenceSpanV4
  projectionObligations[]!: ProjectionObligationV1

ProcessMaterialLimitsV1
  maxFlows!, maxRelations!, maxSignals!, maxRegistryItems!
  maxInputBytes!, maxHypotheses!, maxClaimsPerHypothesis!, maxReaderSlots!

BusinessProcessTaskShardV1
  taskShardId!
  shardOrdinal!                            // zero-based, contiguous within the group
  processEvidenceGroupId!
  ownerCandidateRelationIds[]!
  contextFlowSliceIds[]!                   // nonempty; may repeat read-only across shards
  shardModelDisposition!: MODEL_SAFE | NO_MODEL
  modelIneligibilityGapIds[]!              // empty iff MODEL_SAFE; nonempty iff NO_MODEL
  processModelPacket?: ProcessModelPacketV1 // nonnull iff MODEL_SAFE

ProcessModelPacketV1                       // the only cross-Flow evidence packet visible to the model
  schemaVersion!=flow-interpretation-process-model-packet-v1
  packetKind!=PATH_FREE_PROCESS_EVIDENCE
  processEvidenceGroupId!
  ownerCandidateRelationIds[]!
  contextFlowSliceIds[]!
  flowViews[]!: ProcessModelFlowViewV1
  relationViews[]!: ProcessModelRelationViewV1
  registryItems[]!: ProcessModelRegistryItemViewV1
  limits!: ProcessMaterialLimitsV1

ProcessModelFlowViewV1
  flowSliceId!, evidenceCapsuleId!, entryId!
  factViews[]!: ProcessModelFactViewV1
  gapViews[]!: ProcessModelGapViewV1
  outcomePathViews[]!: ProcessModelOutcomePathViewV1
  processJoinSignals[]!: ProcessModelJoinSignalViewV1
  modelEvidenceSpans[]!: ProcessModelEvidenceSpanV1
  projectionObligations[]!: ProcessModelProjectionObligationV1

ProcessModelFactViewV1
  factId!, kind!, subjectNodeIds[]!
  atoms[]!: ProcessModelAtomViewV1

ProcessModelAtomViewV1
  atomId!, role!, name!, value!: {type!, canonical!}, proofId!

ProcessModelGapViewV1
  gapId!, scope!: FLOW | OUTCOME | FACT | ATOM
  reasonCode!, affectedSemanticIds[]!, evidenceRefs[]!: ArtifactReference

ProcessModelOutcomePathViewV1
  outcomePathId!, decisions[]!: ProcessModelBranchDecisionV1
  terminalNodeId!, terminalKind!
  terminalFactIds[]!, requiredAtomIds[]!, requiredProofIds[]!

ProcessModelBranchDecisionV1
  guardNodeId!, conditionAtomId!, polarity!, normalizedCondition!

ProcessModelJoinSignalViewV1
  processJoinSignalId!, flowSliceId!, signalKind!, anchorKind!, anchorKey!
  direction!, specificity!, claimScope!
  factIds[]!, atomIds[]!, proofIds[]!, evidenceNodeIds[]!
  sourcePositions[]!: PathFreeSourcePositionV1
  gapIds[]!

ProcessModelRelationViewV1
  candidateRelationId!, leftFlowSliceId!, rightFlowSliceId!
  strongestSignalLevel!, direction!, relationUse!
  supportingProcessJoinSignalIds[]!, processSemanticCueIds[]!
  counterProcessJoinSignalIds[]!, blockingCounterProcessJoinSignalIds[]!
  factIds[]!, proofIds[]!, evidenceNodeIds[]!
  sourcePositions[]!: PathFreeSourcePositionV1
  gapIds[]!

ProcessModelRegistryItemViewV1
  registryItemId!, provisionalKey!, flowSliceId!, evidenceCapsuleId!, proposalKind!
  normalizedLabel!, normalizedPurpose!, basisAtomIds[]!, basisGapIds[]!

PathFreeSourcePositionV1
  fileId!, startByte!, endByteExclusive!
  startLine!, startColumn!, endLine!, endColumn!

PathFreeSourceExcerptV1
  position!: PathFreeSourcePositionV1
  rawUtf8!, rawUtf8Sha256!

ProcessModelEvidenceSpanV1
  spanId!, sourceExcerpt!: PathFreeSourceExcerptV1
  supportedAtomIds[]!, supportedOutcomePathIds[]!, supportedProcessJoinSignalIds[]!

ProcessModelProjectionObligationV1
  obligationId!, kind!: ATOM_DIRECT_SEMANTICS | OUTCOME_TERMINAL | PROCESS_JOIN_SIGNAL_BASIS
  semanticItemId!, satisfyingSpanIds[]!

ProcessPromptMessageV1
  role!: SYSTEM | USER
  contentUtf8!

ProcessP2AllowedReferencesV1               // computed separately for each P1 hypothesis
  businessProcessHypothesisId!
  memberFlowSliceIds[]!, candidateRelationIds[]!, processClaimIds[]!
  factIds[]!, proofIds[]!, evidenceNodeIds[]!
  supportProcessJoinSignalIds[]!, processSemanticCueIds[]!
  counterProcessJoinSignalIds[]!, blockingCounterProcessJoinSignalIds[]!
  gapIds[]!, registryOrTechnicalKeys[]!: RegistryOrTechnicalKeyV1

ProcessP1HypothesisReviewInputV1
  businessProcessHypothesisId!
  p1SemanticProjection!                   // exact §7.2 P1 semantic fields plus this already-computed ID
  allowedReferences!: ProcessP2AllowedReferencesV1

ProcessModelRequestV1
  schemaVersion!=flow-interpretation-process-model-request-v1
  requestKind!: PROCESS_P1_HYPOTHESIS_REQUEST | PROCESS_P2_PRECISION_REVIEW_REQUEST
  taskShardId!, taskOrdinal!
  processModelPacket!: ProcessModelPacketV1
  promptBundleRef!, promptMessages[]!: ProcessPromptMessageV1
  responseSchemaRef!, expectedRuntime!: ModelRuntimeIdentityV1
  reviewedP1TaskId?, reviewedP1RoundId?
  reviewedHypotheses[]!: ProcessP1HypothesisReviewInputV1

ProcessModelTaskV1
  schemaVersion!=flow-interpretation-process-model-task-v1
  artifactType!=FLOW_INTERPRETATION_PROCESS_MODEL_TASK
  processModelTaskId!
  taskKind!: PROCESS_P1_HYPOTHESIS | PROCESS_P2_PRECISION_REVIEW
  taskShardId!, taskOrdinal!
  request!: ProcessModelRequestV1
  inputJsonSha256!                       // SHA-256(canonicalJson(request))

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

BusinessProcessHypothesisV2
  schemaVersion!=flow-interpretation-business-process-hypothesis-v2
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
  processHypothesisReviewId?
  finalReviewDecision?: KEEP | NARROW | PENDING_CONFIRMATION

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
  reasonCode?, reviewGapIds[]!              // only deterministic review gaps; never claim support

ProcessInterpretationDispositionV2
  schemaVersion!=flow-interpretation-process-interpretation-disposition-v2
  artifactType!=FLOW_INTERPRETATION_PROCESS_INTERPRETATION_DISPOSITION
  processInterpretationDispositionId!
  taskShard!: BusinessProcessTaskShardV1
  executionKind!: MODEL_TASKS | NO_MODEL
  p1TaskId?, p1TaskDisposition?: ModelTaskDispositionV2
  p2TaskId?, p2TaskDisposition?: ModelTaskDispositionV2
  proposedBusinessProcessHypothesisIds[]!
  retainedBusinessProcessHypothesisIds[]!
  narrowedBusinessProcessHypothesisIds[]!
  droppedBusinessProcessHypothesisIds[]!
  pendingBusinessProcessHypothesisIds[]!
  p2GapBusinessProcessHypothesisIds[]!
  p2FailedBusinessProcessHypothesisIds[]!
  disposition!: READY_FOR_ADMISSION | NO_MODEL_ADMISSION_PENDING | GAP | FAILED
  processGaps[]!: ProcessInterpretationGapV1
  gapIds[]!, failureRef?, reasonCode?

ProcessInterpretationGapV1               // Step 06-internal; canonical value is embedded above
  gapId!
  gapCode!: PROCESS_COUNTER_SCOPE_UNRESOLVED | PROCESS_TASK_BUDGET_EXCEEDED |
            PROCESS_P1_HYPOTHESIS_FAILED | PROCESS_P2_RESPONSE_GAP |
            PROCESS_P2_REVIEW_FAILED |
            PROCESS_P2_REVIEW_PENDING_CONFIRMATION |
            PROCESS_P2_REVIEW_PRECISION_AMBIGUITY
  gapScope!: PROCESS_RELATION | PROCESS_TASK_SHARD | PROCESS_P1_RESPONSE |
             PROCESS_P2_RESPONSE | PROCESS_HYPOTHESIS_REVIEW
  taskShardId!                            // later-lineage field; excluded from gap ID
  processEvidenceGroupId!
  candidateRelationIds[]!
  businessProcessHypothesisIds[]!
  processClaimIds[]!
  processModelTaskIds[]!
  affectedFlowSliceIds[]!
  processJoinSignalIds[]!
  processSemanticCueIds[]!
  repositoryInterpretationRegistryItemIds[]!
  factIds[]!, proofIds[]!, evidenceNodeIds[]!, sourceLocators[]!
  searchedScopeRefs[]!: ArtifactReference // nonempty; frozen inputs/controls actually searched
  failureCode?                            // nonnull iff PROCESS_P1_HYPOTHESIS_FAILED or PROCESS_P2_REVIEW_FAILED
  limitKind?: FLOW_COUNT | RELATION_COUNT | SIGNAL_COUNT |
              REGISTRY_ITEM_COUNT | INPUT_BYTES
  configuredLimit?, observedValue?        // nonnull iff PROCESS_TASK_BUDGET_EXCEEDED
  messageKey!

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

`positivePairBases[]`按`(signalLevel,pairKind,anchorKind,anchorKey,direction,canonicalJson(fullBasis))`的UTF-8 byte order排序并拒绝重复，`counterBases[]`按`canonicalJson(fullBasis)` bytes排序并拒绝重复。二者只存在于path-bearing program record `ProcessCandidateRelationV2`及其group/persisted-material副本；`ProcessModelRelationViewV1`有意省略它们，只复制由完整bases确定的现有aggregate IDs、level、direction和relationUse。这样模型看不到新的程序关联结构，也不能重分配counter；evidence/model trust boundary保持不变。

`ProcessInterpretationGapV1`是Step 06自己创建的Gap的唯一typed value。其`gapId`排除且只排除`gapId`与稍后才知道的`taskShardId`：

~~~text
gapId = "gap:" + lowercaseHex(SHA-256(
    frame(UTF8("flow-interpretation-process-gap-id-v1")) ||
    frame(canonicalJson(processGapWithoutGapIdAndTaskShardId))))
~~~

`taskShardId`虽不进Gap semantic ID，仍是required non-null，并随完整embedded Gap进入disposition ID、JSONL bytes、artifact SHA/root。其余字段全部进preimage；数组按ID排序，`sourceLocators[]`按`(path,startByte,endByteExclusive)`，`searchedScopeRefs[]`按`(artifactId,sha256)`排序。`searchedScopeRefs[]`是实际检查过的Step 03–05 publication、registry、profile/budget等已冻结`ArtifactReference`的非空exact union；它不得引用尚未发布的本Step文件。`sourceLocators[]`只取上述证据闭包中真实存在的位置，可以为空；为空时Trace终止于`SEARCHED_SCOPE`而不得合成source excerpt。

code/discriminator矩阵是闭集：

| gapCode | gapScope与必需affected字段 | required-nullable规则 | 固定messageKey |
| --- | --- | --- | --- |
| `PROCESS_COUNTER_SCOPE_UNRESOLVED` | `PROCESS_RELATION`；`candidateRelationIds`与`processJoinSignalIds`非空 | failure/limit三字段全null | `process-counter-scope-unresolved` |
| `PROCESS_TASK_BUDGET_EXCEEDED` | `PROCESS_TASK_SHARD`；group、受影响Flow及owner relation集合精确 | `limitKind/configuredLimit/observedValue`全non-null，failure null | `process-task-budget-exceeded` |
| `PROCESS_P1_HYPOTHESIS_FAILED` | `PROCESS_P1_RESPONSE`；`processModelTaskIds=[p1TaskId]`，hypothesis/claim数组为空，group、owner relation、context Flow及其signal/cue/registry/Fact/Proof/Evidence/source闭包逐字等于同shard P1 packet | `failureCode` non-null且逐字等于P1 round failure，limit三字段null | `process-p1-hypothesis-failed` |
| `PROCESS_P2_RESPONSE_GAP` | `PROCESS_P2_RESPONSE`；同shard全部P1 hypothesis IDs及P2 task ID精确 | failure/limit三字段全null | `process-p2-response-gap` |
| `PROCESS_P2_REVIEW_FAILED` | `PROCESS_P2_RESPONSE`；同shard全部P1 hypothesis IDs及P2 task ID精确 | `failureCode` non-null，limit三字段null | `process-p2-review-failed` |
| `PROCESS_P2_REVIEW_PENDING_CONFIRMATION` | `PROCESS_HYPOTHESIS_REVIEW`；恰一hypothesis及该review pending claim IDs | failure/limit三字段全null | `process-p2-review-pending-confirmation` |
| `PROCESS_P2_REVIEW_PRECISION_AMBIGUITY` | `PROCESS_HYPOTHESIS_REVIEW`；恰一hypothesis及确定性发现歧义的claim IDs | failure/limit三字段全null | `process-p2-review-precision-ambiguity` |

未适用的affected ID arrays必须为空，适用的Fact/Proof/Evidence/signal/cue/registry/source集合必须是受影响relation、hypothesis或task packet闭包的exact union，不能由模型文字补充。counter-scope Gap在relation ID与group ID固定后产生，再交给该relation唯一owner shard；预算Gap在group和待装atomic units固定后、shard ID之前产生；P2 response/review Gap属于该P2 task的同一shard。这样`taskShardId`是唯一需要后填的Gap字段，不形成relation/group/shard/review/round identity环。

每个Step 06-owned Gap value恰在其owner `ProcessInterpretationDispositionV2.processGaps[]`出现一次，跨全部`A`条disposition不重不漏；中间M6/M7/M8 module envelope只可转交同一完整value。该disposition的`gapIds[]`逐字等于`processGaps[].gapId`与它引用的Step 03–05 upstream Gap IDs的规范union。`ProcessCandidateRelationV2.gapIds[]`只含pre-existing upstream Gap IDs，Step 06 counter-scope Gap由其typed `candidateRelationIds[]`反向定位，避免关系↔Gap ID环。任何Step 06-owned ID无value、value重复、foreign owner、字段矩阵不符或union不等都以`PROCESS_DISPOSITION_INCOMPLETE` fatal。canonical carrier仍是既有`process-interpretation-dispositions.jsonl`，因此Step 06仍只有十五文件、全run仍是57项。

`ModelTaskDispositionV2`是仅用于P1/P2 scope的版本化扩展，不改局部V1。`ProcessModelRoundV1.generationReceiptId`单向指向先固定的receipt；receipt不含round ID，避免identity环。`BusinessProcessHypothesisV2`公开文件保存P2 KEEP/NARROW/PENDING以及P2 GAP/FAILED下未获review的P1 hypothesis；DROP仍由round/review/disposition计数，不在hypothesis文件伪装保留。只要P1 accepted并实际调用P2，公开hypothesis的`p2TaskId/p2RoundId`均non-null；仅当P2产生该hypothesis的review时，`processHypothesisReviewId/finalReviewDecision`才同时non-null，否则在P2 GAP/FAILED分支同时为null。P1 GAP/FAILED时不存在hypothesis record。

`ProcessModelRequestV1`是task、round和receipt共同指向的唯一request preimage。每个model-safe shard恰有`taskOrdinal=1`的P1与`taskOrdinal=2`的P2；task和request ordinal逐字相同。P1要求`reviewedP1TaskId=null`、`reviewedP1RoundId=null`、`reviewedHypotheses=[]`；P2要求两个reviewed P1 ID均non-null并指向同shard terminal P1，`reviewedHypotheses`恰按P1 response中的hypothesis ID排序。P1 GAP/FAILED时已规划P2仍有合法request，但其`reviewedHypotheses=[]`并处置为`NOT_RUN_UPSTREAM_FAILED`；P1 accepted时该数组非空。P1/P2的`requestKind`、task `taskKind`、`taskShardId`和`taskOrdinal`必须逐字段对应。

`ProcessModelRoundV1`的六个response variant按下表完全闭合；不存在可通过schema却无法publication的组合：

| responseKind | `businessProcessHypothesisIds[]` | `processHypothesisReviews[]` | `gapIds[]` | `failureCode` |
| --- | --- | --- | --- | --- |
| `P1_HYPOTHESES` | nonempty、等于P1完整hypothesis ID集 | empty | empty | null |
| `P1_GAP` | empty | empty | nonempty且为packet中已有upstream Gap ID子集 | null |
| `P1_FAILED` | empty | empty | 恰一`PROCESS_P1_HYPOTHESIS_FAILED` ID | non-null且逐字等于该Gap的`failureCode` |
| `P2_REVIEWS` | 逐字等于reviewed P1 ID集 | 每个P1 ID恰一review，不多不少 | 全部`reviewGapIds[]`的exact union | null |
| `P2_GAP` | 逐字等于reviewed P1 ID集 | empty | 恰一`PROCESS_P2_RESPONSE_GAP` ID | null |
| `P2_FAILED` | 逐字等于reviewed P1 ID集 | empty | 恰一`PROCESS_P2_REVIEW_FAILED` ID | non-null且逐字等于该Gap的`failureCode` |

`P2_REVIEWS`中`PENDING_CONFIRMATION` review必须恰有一条`PROCESS_P2_REVIEW_PENDING_CONFIRMATION`；程序发现不违反subset但无法唯一收窄的precision ambiguity时必须恰有一条`PROCESS_P2_REVIEW_PRECISION_AMBIGUITY`。两条件可同时成立，除此之外`reviewGapIds=[]`。所有这些ID都必须在owner disposition的typed `processGaps[]`找到唯一值。raw Provider response由adapter先hash、strict-decode，再由程序生成上述canonical Step 06 Gap value与round；模型不能提供或预测`gapId`。

P1 `FAILED`同样先由adapter验证raw response hash/schema，再由程序从该P1 task、同shard packet闭包和stable failure code生成唯一`PROCESS_P1_HYPOTHESIS_FAILED` value。令其ID为`p1FailureGapId`，则P1 `ProcessModelRoundV1.gapIds = ModelTaskDispositionV2.gapIds = [p1FailureGapId]`；owner `ProcessInterpretationDispositionV2.processGaps[]`中恰有这一个P1-failure value，而owner `gapIds[]`按通用union规则包含该ID及所有适用upstream Gap IDs。它不创建hypothesis或claim，却为Step 07 singleton merge及Step 08 process-terminal ReaderItem/Trace提供canonical owner；模型仍不能提供Gap ID或扩大证据闭包。

对每个实际Provider调用，exact equality固定为：

~~~text
requestBytes = canonicalJson(processModelTask.request)
processModelTask.inputJsonSha256
  = processModelRound.requestSha256
  = generationReceipt.requestSha256
  = lowercaseHex(SHA-256(requestBytes))

processModelRound.responseSha256
  = generationReceipt.responseSha256
  = lowercaseHex(SHA-256(exactProviderResponseBytes))

processModelRound.processModelTaskId = generationReceipt.taskSpecId
processModelRound.taskShardId = generationReceipt.taskShardId = processModelTask.taskShardId
~~~

`exactProviderResponseBytes`是adapter完成transport framing后交给schema decoder的原始application response bytes；validator必须先验hash再解析。未调用的P2没有round/receipt，因此只验证task `inputJsonSha256 = SHA-256(canonicalJson(request))`和`NOT_RUN_UPSTREAM_FAILED`处置，禁止伪造response hash。

process disposition discriminator也固定。所有六个hypothesis outcome arrays互斥，且：

| branch | P1/P2 task disposition | hypothesis partitions | process disposition |
| --- | --- | --- | --- |
| P2 `REVIEWS` | 两者`RESPONSE_ACCEPTED`，均有round/receipt | proposed恰分为retained、narrowed、dropped、pending；两个P2 terminal数组空 | `READY_FOR_ADMISSION`；review Gaps按上表进入`processGaps/gapIds` |
| P2 `GAP` | P1 accepted；P2 `RESPONSE_GAP`且有round/receipt | proposed=`p2GapBusinessProcessHypothesisIds`；其余五个outcome数组空 | `GAP`，`processGaps`中恰一response Gap、`gapIds`按owner union含该ID；`failureRef=null`、`reasonCode=PROCESS_P2_RESPONSE_GAP` |
| P2 `FAILED` | P1 accepted；P2 `RESPONSE_FAILED`且有round/receipt | proposed=`p2FailedBusinessProcessHypothesisIds`；其余五个outcome数组空 | `FAILED`，`processGaps`中恰一failed Gap、`gapIds`按owner union含该ID；`failureRef=p2RoundId`、`reasonCode=PROCESS_P2_REVIEW_FAILED` |
| P1 `GAP` | P1 `RESPONSE_GAP`；P2 `NOT_RUN_UPSTREAM_FAILED`且无round/receipt | proposed及六个outcome数组全空 | `GAP`，P1 upstream Gap IDs保留，`failureRef=null` |
| P1 `FAILED` | P1 `RESPONSE_FAILED`且round/task disposition各有恰一typed failure Gap；P2 `NOT_RUN_UPSTREAM_FAILED`且无round/receipt | proposed及六个outcome数组全空 | `FAILED`，`processGaps`中恰一`PROCESS_P1_HYPOTHESIS_FAILED`、`gapIds`按owner union含该ID；`failureRef=p1RoundId`、`reasonCode=PROCESS_P1_HYPOTHESIS_FAILED` |
| `NO_MODEL` | P1/P2 task ID和disposition全null | proposed及六个outcome数组全空 | `NO_MODEL_ADMISSION_PENDING`，`gapIds=taskShard.modelIneligibilityGapIds`、`failureRef=null`、`reasonCode=NO_MODEL_SHARD` |

这里“六个outcome数组”是retained/narrowed/dropped/pending/P2-gap/P2-failed；`proposedBusinessProcessHypothesisIds`是它们的disjoint union。P2 GAP/FAILED时每个P1 hypothesis仍按`BusinessProcessHypothesisV2`发布，只有review lineage为null；它们不伪造DROP或PENDING decision。typed `P2_FAILED`表示Provider成功返回、hash和schema均有效的业务级失败回答，所以可随完整Gap账本发布并进入`COMPLETED_WITH_GAPS`；transport/runtime failure、started call不完整或raw response schema无效仍是当前run fatal，不发布该分支。任何混搭以`PROCESS_DISPOSITION_INCOMPLETE` fatal。

从path-bearing persisted material到model packet只有下列字段映射，禁止自由重组或补证据：按shard的`contextFlowSliceIds`从group `persistedMaterial.flowViews`取exact flow records，按`ownerCandidateRelationIds`取exact relation records，再取这些records闭包引用的registry items；普通标量、ID、array和limits逐字复制并规范排序。`FlowFactViewV1 → ProcessModelFactViewV1`删除program-only origin artifact reference；`FlowGapViewV1 → ProcessModelGapViewV1`删除origin Gap-ledger reference但保留其已声明`evidenceRefs`；`FlowOutcomePathViewV1`只投影列出的path-free字段；`ProcessJoinSignalV1.sourceLocators`和`ProcessCandidateRelationV2.sourceLocators`逐项映射为仅含`fileId+coordinates`的`PathFreeSourcePositionV1`；relation的program-only `positivePairBases/counterBases`完全省略；`ModelEvidenceSpanV4.sourceExcerpt`映射为`PathFreeSourceExcerptV1(position,rawUtf8,rawUtf8Sha256)`；`ProjectionObligationV1`和Registry item只投影§7.1列出的字段。投影不得引入persisted material中不存在的ID、text或evidence；递归path-leak scan在request hash之前执行。

### 7.2 Step 06显式无环identity DAG

经用户直接确认，语义ID与完整wire/artifact identity分层：语义ID只哈希下表的semantic projection；required later-lineage字段仍保存在wire中、进入JSONL/artifact descriptor SHA与analysis-step root，并由M9逐引用验证。排除后向引用不会隐藏篡改。除表内明确字段外，不得再排除字段；没有alias、dual-write或旧公式兼容路径。

| record / self ID | semantic projection | 从semantic ID精确排除 | projection中必须先存在的reference字段 |
| --- | --- | --- | --- |
| `ProcessSemanticCueV1.processSemanticCueId` | §7.1全部字段减排除列 | `processSemanticCueId` | `leftFlowSliceId,rightFlowSliceId,leftRegistryItemId,rightRegistryItemId,leftBasisAtomIds,rightBasisAtomIds,leftEntryId,rightEntryId,leftStateSignalIds,rightStateSignalIds,processCueProfileRef` |
| `ProcessCandidateRelationV2.candidateRelationId` | §7.1全部字段减排除列 | `candidateRelationId` | `leftFlowSliceId,rightFlowSliceId,positivePairBases,counterBases,supportingProcessJoinSignalIds,processSemanticCueIds,counterProcessJoinSignalIds,blockingCounterProcessJoinSignalIds,factIds,proofIds,evidenceNodeIds,sourceLocators,gapIds` |
| `ProcessEvidenceGroupV2.processEvidenceGroupId` | §7.1全部字段减排除列 | `processEvidenceGroupId` | `memberFlowSliceIds,candidateRelations,processSemanticCues,supportingProcessJoinSignalIds,counterProcessJoinSignalIds,repositoryInterpretationRegistryItemIds,modelIneligibilityGapIds,persistedMaterial` |
| `ProcessInterpretationGapV1.gapId` | §7.1全部字段减`gapId,taskShardId` | `gapId,taskShardId` | `processEvidenceGroupId,candidateRelationIds,businessProcessHypothesisIds,processClaimIds,processModelTaskIds,affectedFlowSliceIds,processJoinSignalIds,processSemanticCueIds,repositoryInterpretationRegistryItemIds,factIds,proofIds,evidenceNodeIds,sourceLocators,searchedScopeRefs,failureCode,limitKind,configuredLimit,observedValue,messageKey` |
| `BusinessProcessTaskShardV1.taskShardId` | §7.1全部字段减排除列 | `taskShardId` | `processEvidenceGroupId,ownerCandidateRelationIds,contextFlowSliceIds,modelIneligibilityGapIds,processModelPacket` |
| `ProcessModelTaskV1.processModelTaskId` | §7.1全部字段减排除列 | `processModelTaskId` | `taskShardId,request`；`inputJsonSha256`必须是该request canonical bytes的hash，作为显式冗余完整性字段参与identity |
| `ProcessHypothesisClaimV1.processClaimId` | §7.1全部字段减排除列 | `processClaimId` | `subjectKeys,predicateKey,objectKeys,memberFlowSliceIds,candidateRelationIds,supportProcessJoinSignalIds,processSemanticCueIds,counterProcessJoinSignalIds,blockingCounterProcessJoinSignalIds,factIds,proofIds,evidenceNodeIds,gapIds` |
| `BusinessProcessHypothesisV2.businessProcessHypothesisId` | §7.1的P1 semantic content减排除列 | `businessProcessHypothesisId,p1RoundId,p2TaskId,p2RoundId,processHypothesisReviewId,finalReviewDecision` | `taskShardId,p1TaskId,processEvidenceGroupIds,memberFlows,businessRoleKeys,stageKeys,activityKeys,inputObjectKeys,outputObjectKeys,objectKeys,stateKeys,processClaims,conditionClaimIds,branchClaimIds,parallelClaimIds,alternativeClaimIds,fallbackClaimIds,candidateRelations,purposeClaimId,endResultClaimId,pendingAssumptionClaimIds,readerSlots` |
| `GenerationReceiptV3.generationReceiptId` | §7.1全部字段减排除列 | `generationReceiptId` | `taskSpecId,expectedRuntime,observedRuntime`；request/response SHA是调用bytes preimage，不是round/hypothesis back-reference |
| `ProcessHypothesisReviewV1.processHypothesisReviewId` | §7.1全部字段减排除列 | `processHypothesisReviewId` | `businessProcessHypothesisId,retainedProcessClaimIds,narrowedProcessClaimIds,droppedProcessClaimIds,pendingProcessClaimIds,retainedMemberFlowSliceIds,retainedCandidateRelationIds,reviewGapIds` |
| `ProcessModelRoundV1.processModelRoundId` | §7.1全部字段减排除列 | `processModelRoundId` | `processModelTaskId,businessProcessHypothesisIds,processHypothesisReviews,gapIds,generationReceiptId` |
| `ProcessInterpretationDispositionV2.processInterpretationDispositionId` | §7.1全部字段减排除列 | `processInterpretationDispositionId` | `taskShard,p1TaskId,p1TaskDisposition,p2TaskId,p2TaskDisposition,proposedBusinessProcessHypothesisIds,retainedBusinessProcessHypothesisIds,narrowedBusinessProcessHypothesisIds,droppedBusinessProcessHypothesisIds,pendingBusinessProcessHypothesisIds,p2GapBusinessProcessHypothesisIds,p2FailedBusinessProcessHypothesisIds,processGaps,gapIds,failureRef` |

无self ID的`ProcessRelationPositivePairBasisV1`、`ProcessRelationCounterBasisV1`、`ProcessPersistedMaterialV1`、`ProcessPersistedFlowViewV1`、全部`ProcessModel*ViewV1`、`ProcessModelPacketV1`、`ProcessModelRequestV1`、`ProcessP1HypothesisReviewInputV1`、`ProcessP2AllowedReferencesV1`、path-free source records、`ProcessMaterialLimitsV1`、`BusinessProcessFlowMemberV1`、`RegistryOrTechnicalKeyV1`、`HypothesisRelationBindingV1`、`ProcessClaimBoundSlotV1`和`ModelTaskDispositionV2`不单独计算identity；其完整规范值只参加上表明确拥有它的parent projection，且不得含parent/later ID。

唯一合法计算/物化顺序为：upstream Flow/Capsule/Fact/Proof/Evidence/Registry IDs → semantic cue → 全部positive/counter bases与candidate relation → evidence group → M6 counter-scope/M7 budget Gap semantic IDs → 全部`A`个task shard → 回填这些Gap的`taskShardId` → 对每个model-safe shard物化P1 request/task → P1 Provider response bytes → P1 generation receipt及`claim IDs + hypothesis semantic ID`或同rank的P1 failure Gap semantic ID → P1 round → P2 request/task（即使随后`NOT_RUN_UPSTREAM_FAILED`也在P1 terminal round后物化）→ P2 response bytes → P2 generation receipt及P2 response/review Gap IDs（同rank；NOT_RUN时均不存在）→ P2 review（仅REVIEWS）→ P2 round → 回填published hypothesis的五个later-lineage excluded字段 → 全部`A`个process disposition及其canonical Gap carriers。no-model分支固定为`evidence group → counter/budget Gap ID（如有）→ task shard → Gap taskShardId回填 → process disposition`，不越过任何模型节点。P1 round引用hypothesis或P1 failure Gap semantic ID，二者都不引用round；review引用hypothesis/Gap，Gap ID不引用review或round；P2 round引用review/Gap，review/Gap都不引用round。

`BusinessProcessHypothesisV2`的五个later-lineage excluded字段必须满足：`p1RoundId`指向唯一含本hypothesis ID的同task/shard P1 round；`p2TaskId`指向reviewed P1 task/round及本ID的同shard P2 task；`p2RoundId`指向该task且其round hypothesis IDs含本ID。若P2为`P2_REVIEWS`，review ID与decision必须同时non-null，review反向指向本ID，decision逐字等于review且不是`DROP`；若P2为`P2_GAP | P2_FAILED`，二者必须同时null，且本ID必须分别出现在同一disposition的P2-gap或P2-failed集合。任一不符fatal；完整record bytes仍随这些字段（包括null）变化而改变artifact SHA/root。

各semantic ID固定为`<prefix> + lowercaseHex(SHA-256(frame(UTF8(<domain>)) || frame(canonicalJson(semanticProjection))))`，其中prefix/domain依次为：

~~~text
process-evidence-group: / flow-interpretation-process-evidence-group-id-v2
process-relation: / flow-interpretation-process-candidate-relation-id-v2
gap: / flow-interpretation-process-gap-id-v1
process-semantic-cue: / flow-interpretation-process-semantic-cue-id-v1
process-shard: / flow-interpretation-business-process-task-shard-id-v1
process-model-task: / flow-interpretation-process-model-task-id-v1
process-model-round: / flow-interpretation-process-model-round-id-v1
business-process-hypothesis: / flow-interpretation-business-process-hypothesis-id-v2
process-claim: / flow-interpretation-process-hypothesis-claim-id-v1
process-hypothesis-review: / flow-interpretation-process-hypothesis-review-id-v1
process-interpretation-disposition: / flow-interpretation-process-interpretation-disposition-id-v2
generation-receipt: / flow-interpretation-generation-receipt-id-v3
~~~

非excluded required-nullable字段以null参加projection。embedded record有self ID时先按DAG计算embedded ID，owner projection覆盖该embedded record中属于semantic projection的完整值。`sourceLocators[]`只存在于program-only persisted records，按`(path,startByte,endByteExclusive)`；model packet的`sourcePositions[]`按`(fileId,startByte,endByteExclusive)`且递归不得出现`path`。member/claim/slot业务序列由M8按`(stage ordinal,flowSliceId,claimKind,processClaimId)`规范化，不能信任模型顺序。

## 8. 集合、identity与处置合同

~~~text
businessFlowsFlowSliceIds = eligibleFlowSliceIds ⊎ ineligibleFlowSliceIds
|eligibleFlowSliceIds| = E
r0Task.flowSliceIds = r0Disposition.flowSliceIds = eligibleFlowSliceIds
localModelTaskIds = exactlyTwoTasksPer(r0ReadyFlowSliceIds)
|localModelTaskIds| = 2R
|candidateRelationIds| = C
processEvidenceGroups cover exactly all N Flow IDs
|processEvidenceGroupIds| = G
allTaskShardIds = modelSafeTaskShardIds ⊎ noModelTaskShardIds
|allTaskShardIds| = A
each processEvidenceGroupId owns one-or-more taskShardIds
candidateRelationIds = disjointUnion(ownerCandidateRelationIds over all A shards)
processModelTaskIds = exactlyTwoTasksPer(modelSafeTaskShardIds)
|processModelTaskIds| = 2S
processInterpretationDisposition.taskShardIds = allTaskShardIds
noModelTaskShardIds have zero process tasks/rounds/receipts
allProposedHypothesisIds = retainedHypothesisIds ⊎ narrowedHypothesisIds ⊎
                           droppedHypothesisIds ⊎ pendingHypothesisIds ⊎
                           p2GapHypothesisIds ⊎ p2FailedHypothesisIds
publishedBusinessProcessHypothesisIds = retainedHypothesisIds ⊎ narrowedHypothesisIds ⊎
                                        pendingHypothesisIds ⊎ p2GapHypothesisIds ⊎
                                        p2FailedHypothesisIds
step07AdmissionEligibleHypothesisIds = retainedHypothesisIds ⊎
                                       narrowedHypothesisIds ⊎ pendingHypothesisIds
step06OwnedGapIds = disjointUnion(processGaps[].gapId over all A dispositions)
each disposition.gapIds = exactUnion(its processGaps IDs, its referenced upstream Gap IDs)
allPlannedTaskIds ↔ allModelTaskDispositionIds
planned model tasks = E + 2R + 2S
actual calls = E + R + accepted local R1 + S + accepted process P1
~~~

仅`P2_REVIEWS`的逐hypothesis review结果满足：

~~~text
P2.memberFlowIds ⊆ P1.memberFlowIds
P2.candidateRelationIds ⊆ P1.candidateRelationIds
P2.processClaimIds ⊆ P1.processClaimIds
P2.factIds ⊆ P1.factIds
P2.proofIds ⊆ P1.proofIds
P2.evidenceNodeIds ⊆ P1.evidenceNodeIds
P2.supportProcessJoinSignalIds ⊆ P1.supportProcessJoinSignalIds
P2.processSemanticCueIds ⊆ P1.processSemanticCueIds
P2.counterProcessJoinSignalIds ⊆ P1.counterProcessJoinSignalIds
P2.blockingCounterProcessJoinSignalIds ⊆ P1.blockingCounterProcessJoinSignalIds
P2.gapIds ⊆ P1.gapIds
P2.registryOrTechnicalKeys ⊆ P1.registryOrTechnicalKeys
P2.hypothesisIds = P1.hypothesisIds
P2 decision ∈ {KEEP,NARROW,DROP,PENDING_CONFIRMATION}
~~~

这里的`P2.<refs>`是P2 decision保留、收窄或pending的P1 claim/member/relation IDs所传递闭包出的exact refs，不是假设response另有开放引用字段。这些subset逐个hypothesis验证，右侧只来自该hypothesis的P1 semantic projection；禁止与同shard packet、别的hypothesis或P1 response全局集合做union。P2唯一可新增的引用是`ProcessHypothesisReviewV1.reviewGapIds`：M9只可为schema-valid `PENDING_CONFIRMATION`或不违反subset的review precision ambiguity确定性创建对应typed `ProcessInterpretationGapV1`，必须位于review与owner disposition而非hypothesis/claim，且永远不能作为claim support。schema或subset失败仍是fatal `PROCESS_REVIEW_EXPANDED`，不得降级成review Gap；正常accepted review若无上述合法不确定性则`reviewGapIds=[]`。`P2_GAP | P2_FAILED`没有review或decision refs，所以不套用上述subset式；它们按§7.1 response/disposition矩阵保存完整P1 hypothesis内容并进入非准入分区。

R2/P2因上游typed GAP/FAILED未运行时，必须持久化`NOT_RUN_UPSTREAM_FAILED`，round/receipt ID为null且`upstreamTaskSpecId`指向同Flow或同shard上一轮。任何task最多一次started call；无自动重试、Provider switching或API fallback。

所有公开记录ID是全长SHA-256 direct-preimage identity：domain字符串+全部identity-significant字段的canonical JSON frames。线程调度、遍历顺序与写盘路径不得进入业务identity；partition profile/budget以及由其确定的shard边界是显式control/input，进入group persisted limits、shard/task/request和analysis-step identity。只有相同冻结输入、partition profile与budget在不同线程/遍历/写盘顺序下要求Step 06逐字相同。只改变M7 partition controls时，M6产生的`ProcessCandidateRelationV2` records/IDs、关系拓扑、逻辑group的成员Flow集合与candidate-relation成员集合保持稳定；但是`ProcessEvidenceGroupV2`内嵌`ProcessPersistedMaterialV1.limits`，所以group records的ID和bytes**明确不稳定**，`A/S`、shard/task/request/round/receipt/disposition及下游identity也允许变化。无论controls如何，全部`A`上的owner relation集合仍必须互斥且规范union等于完整candidate relation集合。source locator仍是repo-relative、forward-slash、行列1-based，并且不进入model packet。

## 9. 成功、Gap与fatal

### 成功

- M9证明15文件receipt-last安装、全部上游ref与task/round/receipt/disposition闭包、`N/E/R/C/G/A/S`集合合同和逐hypothesis P2非扩张。
- `N=0`仍发布空registry、空JSONL和完整十五文件，`A=S=0`；`N>0,E=0`有groups、至少每组一个no-model shard及显式process disposition，但无任何过程模型任务/round/receipt。
- 同一Flow可被多个最终hypothesis引用；每个引用仍回到同一Flow/Capsule事实。

### 业务Gap

- 没有正向domain signal的孤立Flow保留singleton group；不是fatal。
- signal冲突、顺序不确定、外部效果未证明或P2 `PENDING_CONFIRMATION`进入显式Gap/待确认，不得被自然语言抹平。
- typed P2 `GAP/FAILED`是已完成调用的业务级终态：保留P1 hypothesis、null review lineage、typed process Gap和非准入处置；不得伪装成DROP、NOT_RUN或transport failure。
- typed `R0/R1/P1` GAP/FAILED可使后继R2/P2 `NOT_RUN_UPSTREAM_FAILED`；计划任务仍计数。P1 FAILED必须携带唯一`PROCESS_P1_HYPOTHESIS_FAILED`，不得留下空Gap owner。

### 当前run fatal

- provider transport/runtime失败、started call无完整响应、observed runtime不符；
- schema/extra-field/Unicode/budget/hash/identity/ref closure错误，或model request出现path-bearing字段；
- generic-only成边、edge在全部`A`上多owner/无owner、groups漏Flow、P2扩张或跨hypothesis借refs；
- task/round/receipt/disposition缺失或重复、部分publication或count伪造。

稳定Gap/failure codes至少包括：`PROCESS_SIGNAL_LEVEL_INVALID`、`PROCESS_GENERIC_SIGNAL_ONLY`、`PROCESS_COUNTER_SCOPE_UNRESOLVED`、`PROCESS_ENTRY_TARGET_INVALID`、`PROCESS_CUE_PROFILE_INVALID`、`PROCESS_GROUP_COVERAGE_BROKEN`、`PROCESS_EDGE_OWNERSHIP_BROKEN`、`PROCESS_TASK_BUDGET_EXCEEDED`、`PROCESS_P1_HYPOTHESIS_FAILED`、`PROCESS_P2_RESPONSE_GAP`、`PROCESS_P2_REVIEW_FAILED`、`PROCESS_P2_REVIEW_PENDING_CONFIRMATION`、`PROCESS_P2_REVIEW_PRECISION_AMBIGUITY`、`PROCESS_MODEL_PACKET_PATH_LEAK`、`PROCESS_MODEL_REQUEST_HASH_MISMATCH`、`PROCESS_MODEL_RESPONSE_INVALID`、`PROCESS_MODEL_REFERENCE_INVALID`、`PROCESS_REVIEW_EXPANDED`、`PROCESS_DISPOSITION_INCOMPLETE`，并沿用局部`PROVIDER_FAILURE_AFTER_START`、`MODEL_TASK_NOT_RUN_UPSTREAM_INVALID`与`FLOW_INTERPRETATION_RESOURCE_LIMIT_EXCEEDED`。

## 10. 给Step 07的下游保证

RepositoryKnowledge无需回读源码或调用模型即可：先准入local meanings，再验证admission-eligible process claims，并把terminal/no-model分支编译为typed reasoned exclusions，再比较conflict/alternative，再建立多对多membership，最后生成一个全仓知识。每个实际准入的process claim都能沿以下链路回放：

`BusinessProcessHypothesis → ProcessInterpretationDisposition → P1/P2 task、round、generation receipt → ProcessEvidenceGroup / ProcessJoinSignal → Flow / EvidenceCapsule → Fact / Proof / Evidence / Source`。

Step 07必须先从`ProcessInterpretationDispositionV2.processGaps[]`验证每个Step 06-owned Gap value，再按`canonicalGapId=g.gapId=memberGapIds[0]`可逆映射进singleton `MergedGapV3`；P1 FAILED的typed Gap进入reasoned exclusion/terminal ReaderItem，P2 GAP/FAILED hypothesis只进入reasoned exclusion，二者都不产生`ProcessAdmissionDecision`或过程知识。Step 06只发布hypothesis，不把它们宣称为最终事实；确定性准入和certainty由Step 07负责。Step 08只消费Step 07的知识与Gap provenance，不直接读模型文本。

## 11. Luna RED 与 Terra GREEN 指南

### Luna/xhigh RED

- 先写`N=0`、`N>0,E=0,A>0,S=0`、`E=1,R=1,A=1,S=1`、R1/P1 typed GAP、R2/P2 NOT_RUN的失败fixture；逐式断言planned/actual call公式。
- 反例覆盖四级signal、generic-only禁边、一个relation含多个positive pairs时的完整pair/counter union、业务对象集合交叠/互斥、counter exact blocking、全部`A`上的edge唯一owner、group全覆盖、Flow只读重复、packet path leak、request/round/receipt hash不等、P2新增Flow/edge/fact或跨hypothesis借ref、外部效果无Proof、M3/public registry pair冲突。
- 为counter-scope、单unit预算、P1 FAILED、P2 review pending/ambiguity、P2 GAP与P2 FAILED逐一断言typed Gap preimage、唯一disposition carrier、gapIds union、nullable hypothesis lineage与六路hypothesis accounting；P1 FAILED的round/task/process disposition必须共用同一canonical Gap ID。删value、重复carrier、伪review或把typed FAILED冒充transport failure都必须fail closed。
- 显式改变partition limits的fixture须保持candidate relation records/IDs、关系拓扑与每个逻辑group的成员集合，却必须允许group ID/bytes及shard/downstream identity变化；任何断言changed controls下group ID稳定的测试都是错误测试。
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
