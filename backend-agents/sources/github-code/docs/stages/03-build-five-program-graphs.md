# 03 构建五张正式程序图

> 总体设计权威：[GitHub Code Agent 总体设计](../DESIGN.md)。

## 1. 为什么存在

业务流程不是“Controller 文件后面跟着 Service 文件”。要证明一次状态更新，程序必须分别知道：

- 代码元素怎样组织；
- 调用实际绑定到谁；
- 条件与终点怎样连接；
- status 和 ids 的值怎样跨层流动；
- 每个 node/edge 为什么能回到固定源码。

这五类问题各有不同不变量，因此形成五张一等、独立持久化的程序图。它们共同服务 Fact 和 Flow，但不能折成一个模糊的 repository model blob。

## 2. 具体输入与 DepotHead 例子

输入是 Stage 01 snapshot/source inventory、Stage 02 application profile/entry/catalog/capability artifacts、graph profile、toolchain/schema hashes 和预算。

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本文件用连贯目标 graph 值解释模块 artifact 接力，不声称当前已有 standalone 五图；技术 unknown 只用 nullable/UNRESOLVED/Gap/fatal 表达。

DepotHead **REAL_SOURCE** 主链：

- Controller :43 与 :178-191；
- Controller :183-185 的 status/ids 读取和 Service 调用；
- Service :741-822，尤其 :749、:752-803；
- Mapper Java :23；
- Entity :63,301-307；
- Example :149-151；
- Mapper XML :3、:70-93、:385-497，尤其 table :386、status :472-473、where include :494-495。

目标图应表达的 **DETERMINISTIC_CONCLUSION**，只有在所有 binding 唯一时成立：

~~~text
Controller.batchSetStatus
  --CALLS--> Service.batchSetStatus
  --CALLS--> DepotHeadMapper.updateByExampleSelective
  --BINDS_STATEMENT--> DepotHeadMapper.xml#updateByExampleSelective

Controller.status
  --ARGUMENT_TO_PARAMETER--> Service.status
  --VALUE_TO_SETTER--> DepotHead.status
  --PROPERTY_TO_PLACEHOLDER--> record.status
  --WRITES_COLUMN--> jsh_depot_head.status
~~~

这段是目标图语义，不是当前 standalone graph 产物。

## 3. 程序怎样工作

1. 重验 Stage 01/02 receipt roots；解析器只读 verified handles。
2. 建代码结构图：package、type、field、method、parameter、annotation、config、XML statement、SQL table/column 和 containment/declaration。
3. 建调用图：receiver 静态类型、method candidate、direct call、call/return pairing、Mapper Java method 与 XML statement binding。
4. 建控制流图：ENTRY、TRUE、FALSE、NEXT、CALL、RETURN、THROW/TERMINAL；每个 branch edge保存 guardId 和 polarity。
5. 建数据流图：definition/use、assignment、return、argument→parameter、setter→property、criteria→XML parameter、placeholder→column。
6. 建证据图：graph node/edge → source span、file SHA、span SHA、parser rule、binding rule；Evidence node不直接等于 Fact。
7. 做逐图 reference/accounting、跨图 endpoint、entry ownership、unique binding 和 resource validation。
8. 写满五图及 index/gaps，在同文件系统原子安装整个 Stage 03 目录。

## 4. 生成的可观察产物

| 文件 | 一等职责 |
| --- | --- |
| code-structure-graph.json | 声明、包含、类型、配置、XML/SQL 结构 |
| call-graph.json | 跨方法、跨层与 Mapper statement 的唯一调用/binding |
| control-flow-graph.json | 入口根可达 CFG、branch polarity、call/return 和 terminals |
| data-flow-graph.json | definitions/uses/arguments/properties/placeholders/columns 的值流 |
| evidence-graph.json | 每个图 node/edge 的 source/rule provenance |
| graph-index.json | 五图 schema/profile/roots、跨图 ID catalog 和 accounting |
| graph-gaps.jsonl | 局部 unsupported/ambiguous/over-limit site |
| stage-receipt.json | 上游 roots、control hashes、artifact set 与 Gap count |

DepotHead status edge 的目标形状：

~~~json
{
  "edgeId": "data-edge:<hex64>",
  "kind": "PROPERTY_TO_SQL_PLACEHOLDER",
  "fromNodeId": "property:DepotHead.status",
  "toNodeId": "placeholder:record.status",
  "ruleId": "mybatis-record-property-binding-v1",
  "resolution": "EXACT",
  "evidenceNodeIds": ["evidence:<hex64>"]
}
~~~

尖括号与短 identity 是示意，不是当前 JSON artifact。

目标出口始终是八个命名文件的完整 stage set。即使某类关系因 Gap 没有 admitted edge，对应 graph 文件也必须包含非空 schema/profile/snapshot/coverage envelope；不能省掉空图。未来 DepotHead 正向验收还要求五图共同包含 8.3 的全部 route、call、control、status 和 ids 闭包；当前实现没有达到这一正向出口。

### 4.1 人类 walkthrough：五图模块用什么文件接力

~~~jsonl
{"module":"CodeStructureGraphBuilder","artifact":"modules/01-code-structure/code-structure-draft.json","takesFrom":["Stage01Reference","Stage02Reference"],"says":{"entryId":"entry:post-depothead-batch-set-status","declares":["DepotHeadController#batchSetStatus","DepotHeadService#batchSetStatus","DepotHeadMapper#updateByExampleSelective","DepotHead.status","jsh_depot_head.status"]}}
{"module":"CallGraphBuilder","artifact":"modules/02-call-graph/call-graph-draft.json","takesFrom":["code-structure-draft.json","Stage02 mapper catalog"],"says":{"entryId":"entry:post-depothead-batch-set-status","calls":["Controller→Service","Service→Mapper Java","Mapper Java→Mapper XML statement"]}}
{"module":"ControlFlowGraphBuilder","artifact":"modules/03-control-flow/control-flow-draft.json","takesFrom":["code-structure-draft.json","call-graph-draft.json"],"says":{"entryId":"entry:post-depothead-batch-set-status","guard":"dhIds is not empty","truePath":"set status and update","terminal":"return result"}}
{"module":"DataFlowGraphBuilder","artifact":"modules/04-data-flow/data-flow-draft.json","takesFrom":["structure/call/control artifacts"],"says":{"entryId":"entry:post-depothead-batch-set-status","valuePath":"request status→Service status→DepotHead.status→record.status→jsh_depot_head.status","wherePath":"request ids→dhIds→andIdIn→WHERE id IN"}}
{"module":"EvidenceGraphBuilder","artifact":"modules/05-evidence-graph/evidence-graph-draft.json","takesFrom":["four program-graph drafts","verified source"],"says":{"statusColumnEdge":"data-edge:record-status-to-column","source":"DepotHeadMapper.xml:472-473","rule":"mybatis-record-property-binding-v1"}}
{"module":"ProgramGraphSetPublisher","artifact":"modules/06-publish/stage03-publication.json","takesFrom":["five graph drafts"],"says":{"graphKinds":["CODE_STRUCTURE","CALL","CONTROL_FLOW","DATA_FLOW","EVIDENCE"],"publicFiles":["code-structure-graph.json","call-graph.json","control-flow-graph.json","data-flow-graph.json","evidence-graph.json","graph-index.json","graph-gaps.jsonl","stage-receipt.json"]}}
~~~

同一个 `entry:post-depothead-batch-set-status` 从 M1 到 M4 原样复用；M5 只给既有 node/edge 加来源，M6 只发布五张图。任何模块都不能把 `status` 和 `ids` 改名或跨缺边拼成长链。

## 5. 下游怎样消费而不返工

Stage 04 只读五张 graph 与 graph-index 来枚举候选 Fact、构建 Proof。Stage 05 复用 call/control/data graphs 编译 Flow。它们都不能：

- 重新解析 AST/XML 来补一个缺边；
- 用源码行顺序推断控制流；
- 用 simple name 或字符串相等绑定调用/数据；
- 从 evidence span 直接跳过 graph edge 生成 Fact。

新增 graph kind/edge rule 必须版本化 schema/profile，并生成新的 Stage 03 identity。

### 下游前置条件与后置保证

| Stage 04/05 开始前必须成立 | Stage 03 成功后保证 |
| --- | --- |
| Stage 01/02 roots 与 Stage 03 controls 一致；八文件 artifact set/root 重验通过 | 五个 graph filenames 恰好存在并共享同一 snapshot/application profile identity |
| graph-index 中的 node/edge catalog、coverage 和 Gap accounting 闭合 | 每个 admitted edge 有 exact endpoints、版本化 rule 和 Evidence refs；歧义只进 Gap |
| 每个 graph envelope 可独立解析；所有跨图 endpoint 均存在 | Stage 04 可只用五图证明 Fact，Stage 05 可只用 call/control/data graph 编译 Flow |

下游不得把 graph Gap 当成“可稍后从源码补回”的提示。缺边意味着对应 Fact/Flow 不能闭合，除非以新 schema/profile 生成新的 Stage 03。

## 6. 成功、Gap、fatal 与恢复

- **成功**：五个文件全部存在、分别自验、跨图引用/accounting 闭合、阶段目录原子安装。
- **带 Gap 成功**：局部 unsupported、ambiguous 或 over-limit site 有明确 locator、affected entries 和 disposition；图中不得出现猜测 edge。
- **fatal**：缺任一图、断引用、重复 canonical ID、call/data edge 端点不一致、CFG branch 无 polarity、Evidence 不能回到 snapshot、XML 外部解析、安全/预算无法执行或 artifact collision。
- **恢复**：五图视为一个原子 stage set；不能只复用其中四张。control hashes 完全相等且 artifact root 重验通过才从 Stage 04 继续。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 解析 Java/config/XML/SQL | 是 | 否 |
| 构建五图与唯一 binding | 是 | 否 |
| 补未知调用/数据流 | 否，输出 Gap | 否 |
| 从图解释业务名称 | 否，留到 Stage 06 | 否 |

产品运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块集合固定为五个 graph builder 加一个 graph-set publisher。顺序为 `CodeStructureGraphBuilder` → `CallGraphBuilder` / `ControlFlowGraphBuilder` / `DataFlowGraphBuilder` → `EvidenceGraphBuilder` → `ProgramGraphSetPublisher`；中间只能交换 typed node/edge/provenance drafts。任何 builder 都不能调用 LLM。

#### M1 CodeStructureGraphBuilder

- **解决的问题**：给后续关系一个唯一的声明/包含/SQL 结构坐标系，避免按文件名或 simple name 找对象。
- **精确上游输入及前置**：valid Stage01Reference、Stage02 application/entry/catalog artifacts、CODE_STRUCTURE registry/profile/budget；所有 source handles 与 parser identities 已重验。
- **确定性顺序 / LLM**：解析 verified Java/config/XML/SQL → 建 package/type/field/method/parameter/annotation/statement/table/column nodes → 建 CONTAINS/DECLARES/config/SQL edges → account；0 LLM。
- **目标输出与 DepotHead 示例**：`ProgramGraphDraft{graphKind=CODE_STRUCTURE,nodes,edges,coverage}`；例子含 Controller/Service/Mapper methods、`DepotHead.status`、XML statement、`jsh_depot_head.status` nodes。
- **必须保持的不变量**：declaration node identity 绑定 snapshot/kind/canonical symbol/source/rule；同名不同 FQN/statement 不合并；每个 candidate 恰一 disposition。
- **Gap / fatal / 恢复**：局部 unsupported syntax 是 Gap；duplicate ID、broken containment、parser/source/security/accounting 错误 fatal；恢复从相同 Stage01/02 roots 重建 draft。
- **给下游的后置保证**：M2–M5 获得稳定 endpoint IDs；Stage 04 可引用结构节点而无需再解析声明。
- **明确非目标**：不绑定调用、不生成 CFG/data-flow、不证明 Fact。
- **公共测试 seam 与验收**：`buildStructure(source, discovery, profile)` 覆盖 DepotHead FQN/method/property/table/column、同名 decoy、XML include/statement 和 root-order determinism；所有 expected nodes/edges/coverage 必须闭合。
- **Luna/xhigh 测试指南**：创建 `Stage03CodeStructureGraphBuilderTest`，冻结Stage01/02 artifacts、DepotHead bytes与独立node/edge golden于 `src/test/resources/target/stage03/code-structure/`。逐RED：完整结构、同名FQN隔离、XML statement/table/column、unsupported Gap、broken containment fatal、root/order determinism；首RED因public builder/schema缺失失败。只fake source handle，parser/canonical/identity不可mock。命令：`mvn -Dtest=Stage03CodeStructureGraphBuilderTest test`；禁网络/客户执行。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `target/stage03/codestructure/`，实现 public `CodeStructureGraphBuilder/CodeStructureGraphDraft` 与 `stage03-code-structure-draft-v1`；只读Stage01/02 artifacts，parse→nodes→edges→coverage。逐slice GREEN，输出IDs/golden稳定；不得合并同名、产生其他图或改registry。缺schema/upstream时STOP交Sol/ultra，完成更新审计。

#### M2 CallGraphBuilder

- **解决的问题**：唯一确定调用 target、call/return pair 和 Mapper Java→XML statement binding。
- **精确上游输入及前置**：M1 structure draft、Stage02 entries/mapper catalog、verified call-site bytes、CALL registry/profile/budget；receiver/type/method candidate refs 均存在。
- **确定性顺序 / LLM**：枚举 call sites → 求 receiver static type → 解析 method signature/overload → 建 direct target → 配对 call/return → 按 namespace/signature 绑定 Mapper statement；0 LLM。
- **目标输出与 DepotHead 示例**：CALL graph draft；例子有 Controller :185→Service :742、Service :803→Mapper Java :23、Mapper Java→XML :385 三段 exact edges。
- **必须保持的不变量**：每个 admitted call/binding target 唯一；edge 保存 exact endpoints/rule/resolution/evidence draft；simple name/文本相似不是 tie-breaker。
- **Gap / fatal / 恢复**：可定位的 ambiguous/unsupported call 为 affected-entry Gap；两个 exact target、broken endpoints、pair/reference/accounting 错误 fatal；相同 roots/profile 才可重建。
- **给下游的后置保证**：M3/M4/Stage05 能沿明确 call/return，不需动态 dispatch 猜测；Stage04 可把 call edge 放入 Proof。
- **明确非目标**：不以调用顺序代替 CFG，不推值流，不把 unresolved candidate 任选一个。
- **公共测试 seam 与验收**：`buildCalls(structure, entries, mapperCatalog)` 对三段 DepotHead chain 做逐段 deletion/decoy/overload mutation；只有唯一 signature/namespace binding 时产生 EXACT edge。
- **Luna/xhigh 测试指南**：创建 `Stage03CallGraphBuilderTest`，fixtures/goldens在 `src/test/resources/target/stage03/call-graph/`。一个RED一个三段binding：Controller→Service、Service→Mapper、Mapper→XML；随后overload/decoy Gap、双exact fatal、call-return mutation、determinism。首RED因seam缺失；golden不由production生成。只fake上游artifact reader，禁止mockresolution/canonical。命令：`mvn -Dtest=Stage03CallGraphBuilderTest test`；无网络/runtime。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `target/stage03/callgraph/`，实现 public `CallGraphBuilder/CallGraphDraft` 与 `stage03-call-graph-draft-v1`；只读M1/Stage02，receiver type→signature→target→return pair→Mapper binding。逐edge GREEN且decoy不命中；不能字符串fallback或改M1。跨stage/data缺失MUST STOP交Sol/ultra，更新审计。

#### M3 ControlFlowGraphBuilder

- **解决的问题**：表达每个 entry 可达步骤、guard polarity、call/return 与 terminal，防止用源码行序讲流程。
- **精确上游输入及前置**：M1 structure、M2 call targets、Stage02 entry roots、verified method bodies、CONTROL_FLOW registry/profile/budget；entry/call endpoints 全部有效。
- **确定性顺序 / LLM**：每 entry 建 ENTRY → basic blocks/guards → TRUE/FALSE/NEXT/CALL/RETURN → return/throw/profile stop terminals → reachability/accounting；0 LLM。
- **目标输出与 DepotHead 示例**：CONTROL_FLOW draft；例子保留 Service :752-796 的 status/库存条件 polarity、`:798-803` 非空 dhIds 写入分支与 :821 return terminal。
- **必须保持的不变量**：每个 guard 的 outgoing polarity 显式且合法；每个可达 path 终止、Gap 或 reasoned exclusion；call/return refs 与 M2 一致。
- **Gap / fatal / 恢复**：bounded loop/unsupported construct 可形成 Gap；缺 polarity/terminal、悬空 block、call pair mismatch 或覆盖不闭合 fatal；恢复重建完整 CFG draft。
- **给下游的后置保证**：Stage05 可按 entry root 枚举 Outcomes；Stage04 可证明条件 atom，无需从行号推路径。
- **明确非目标**：不执行代码、不假定异常处理器/事务运行时、不命名业务 Outcome。
- **公共测试 seam 与验收**：`buildControlFlow(structure, calls, entries)` 覆盖 TRUE/FALSE swap、terminal deletion、loop budget、call/return mutation；DepotHead 每个目标 guard 与 terminal 必须可达且 polarity 稳定。
- **Luna/xhigh 测试指南**：创建 `Stage03ControlFlowGraphBuilderTest`，fixtures/goldens置 `src/test/resources/target/stage03/control-flow/`。RED顺序：entry/guards/terminals正向、TRUE/FALSE swap、terminal deletion、call-return mismatch、loop budget Gap、order determinism；首RED应因CFG seam缺失。只fake verified method reader，禁止mock CFG/accounting。命令：`mvn -Dtest=Stage03ControlFlowGraphBuilderTest test`；禁客户运行/网络。偏离按DESIGN 13.11交`gpt-5.6-sol / ultra` Design Authority。
- **Terra/xhigh 实现指南**：RED后只改 `target/stage03/controlflow/`，实现 public `ControlFlowGraphBuilder/ControlFlowGraphDraft` 与 `stage03-control-flow-draft-v1`；M1/M2 artifacts→blocks/guards→typed edges→terminals→coverage。每个RED独立GREEN；禁止行号补flow/运行代码。必要异常语义不在上游即STOP 13.11，审计同步。

#### M4 DataFlowGraphBuilder

- **解决的问题**：逐段证明请求值怎样跨参数、变量、property、criteria、placeholder 到 SQL column/where。
- **精确上游输入及前置**：M1 structure、M2 calls、M3 CFG、verified Java/XML bytes、DATA_FLOW registry/profile/worklist budget；所有定义/使用/call endpoints 已存在。
- **确定性顺序 / LLM**：建 intra-method def-use → argument→parameter → assignment/setter/property → Mapper record/example params → XML dynamic condition/include → placeholder/criterion→column/where → fixed-point/account；0 LLM。
- **目标输出与 DepotHead 示例**：DATA_FLOW draft；例子分别闭合 `Controller.status→Service.status→setStatus→record.status→jsh_depot_head.status` 与 `ids→dhIds→andIdIn→WHERE id IN`。
- **必须保持的不变量**：每条长链由相邻 typed edges 组成；每 edge 有唯一 rule/endpoints/guard context；不跨 unresolved call/alias/XML path。
- **Gap / fatal / 恢复**：局部 alias/dynamic SQL/loop 超能力为 Gap；伪 exact binding、broken endpoint、worklist/accounting/identity 错误 fatal；相同 roots/rules 才可重算。
- **给下游的后置保证**：Stage04 能逐 atom 引用 exact value/where edge；Stage05 能保留数据相关条件，不需字符串匹配。
- **明确非目标**：不证明运行时 DB/trigger 值，不用字段同名跨层连边，不硬编码 DepotHead positive edge。
- **公共测试 seam 与验收**：`buildDataFlow(structure, calls, cfg)` 对两条 DepotHead chain 每段做 deletion/decoy/property/placeholder mutation；任一缺段使对应 chain 不闭合。
- **Luna/xhigh 测试指南**：创建 `Stage03DataFlowGraphBuilderTest`，冻结两条DepotHead链及逐段mutation于 `src/test/resources/target/stage03/data-flow/`。依次RED：intra-def/use、argument/parameter、setter/property、record/placeholder/column、ids/criterion/where、alias Gap、worklist fatal、determinism；每个RED应只因该edge/rule缺失。只fakeartifact/source reader，dataflow/canonical不能mock。命令：`mvn -Dtest=Stage03DataFlowGraphBuilderTest test`；禁网络/客户MyBatis。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `target/stage03/dataflow/`，实现 public `DataFlowGraphBuilder/DataFlowGraphDraft` 与 `stage03-data-flow-draft-v1`；只读M1–M3，按局部→跨调用→property→XML fixed-point，逐段保存rule/guard。每段GREEN后跑同selector；禁止字段同名捷径/DepotHead硬编码。上游缺endpoint或需新edge语义MUST STOP交Sol/ultra/必要时用户。

#### M5 EvidenceGraphBuilder

- **解决的问题**：说明每个程序 node/edge 从哪些固定 bytes 和版本化 parser/binding rule 得出。
- **精确上游输入及前置**：M1–M4 drafts 及每个 draft element 的 provenance tokens、Stage01 inventory/source handles、EVIDENCE registry/profile/budget；所有 program IDs 已固定。
- **确定性顺序 / LLM**：canonicalize file/span locators → 重验 file/span SHA → 建 rule-application nodes → 建 LOCATES/PARSED_BY/BOUND_BY/SUPPORTS edges → 覆盖检查；0 LLM。
- **目标输出与 DepotHead 示例**：EVIDENCE draft；例子把 status placeholder edge 回到 Mapper XML :472-473、绑定 rule 和 file/span SHA，把 route 回到 :43 与 :178-191。
- **必须保持的不变量**：每个 admitted program node/edge 至少一个闭合 provenance path；Evidence identity 不含绝对 root；Evidence 不是 Fact/Proof。
- **Gap / fatal / 恢复**：局部 program Gap 可有 gap provenance；admitted element 缺 evidence、span/source drift、rule unknown、coverage/reference broken fatal；恢复重开 source 重建。
- **给下游的后置保证**：Stage04 可从 program edge 稳定走到 source/rule，并独立构造 atom Proof。
- **明确非目标**：不决定业务事实、不把 locator 正确等同于语义正确、不为缺边制造 evidence。
- **公共测试 seam 与验收**：`buildEvidence(programDrafts, source)` 对 span/rule/file SHA/endpoint omission/substitution mutation fail closed；不同 root 产生同 evidence IDs/bytes。
- **Luna/xhigh 测试指南**：创建 `Stage03EvidenceGraphBuilderTest`，fixtures/goldens在 `src/test/resources/target/stage03/evidence-graph/`。逐RED：route与status edge provenance、span/hash/rule omission、endpoint substitution、admitted-edge无evidence fatal、different-root determinism；首RED因seam缺失。只fakeSourceHandle重开，禁止mockhash/coverage/canonical。命令：`mvn -Dtest=Stage03EvidenceGraphBuilderTest test`；无网络。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `target/stage03/evidencegraph/`，实现 public `EvidenceGraphBuilder/EvidenceGraphDraft` 与 `stage03-evidence-graph-draft-v1`；消费M1–M4 provenance artifacts，locator→reopen→rule nodes→support edges→coverage。逐RED GREEN；不得把Evidence当Proof/制造缺边。schema或source identity不足时STOP交Sol/ultra，更新审计。

#### M6 ProgramGraphSetPublisher

- **解决的问题**：把五个 drafts 作为不可拆分的一等图集合校验、落盘并给 Stage 04/05 一个稳定引用。
- **精确上游输入及前置**：M1–M5 完整 drafts、graph gaps、Stage01/02 roots 和 Stage03 controls；五种 graphKind 恰各一份且局部 validation 已通过。
- **确定性顺序 / LLM**：跨图 endpoint/ownership/accounting → graph IDs/root → canonical 五图/index/gaps/receipt → staging force/SHA → atomic install；0 LLM。
- **目标输出与 DepotHead 示例**：八个 exact files；未来正例 index 指向含 8.3 八段闭包的五图，当前 Gap 也保留五个 envelope 和 `graph-gaps.jsonl`。
- **必须保持的不变量**：五图恰一次且 schema/profile/snapshot 一致；外部 endpoint 全存在；graph set 原子安装/恢复，publisher 不改边。
- **Gap / fatal / 恢复**：builder 的局部 Gap 原样入账；缺图、多图、cross-ref、root/canonical/install/collision 错误 fatal；恢复不能选四张复用。
- **给下游的后置保证**：Stage04/05 只凭 Stage03Reference 获得完整五图、index、Gap/accounting，不需源码 parser。
- **明确非目标**：不补 graph edge、不证明 Fact、不编译 Flow、不调用模型。
- **公共测试 seam 与验收**：`publishGraphSet(fiveDrafts, gaps, controls)` 覆盖缺/多/交换 graph、cross-ref mutation、乱序、crash/collision；只有 exact eight-file coherent set 返回 Stage03Reference。
- **Luna/xhigh 测试指南**：创建 `Stage03ProgramGraphSetPublisherTest`，五module artifacts/golden set放 `src/test/resources/target/stage03/graph-set-publisher/`。RED顺序：exact eight files、缺/多/交换graph、cross-ref/ID-set spoof、Gap accounting、乱序、crash/collision/resume；首RED因publisher缺失。只mockartifact-store crash，禁止mockcross-validator/canonical。命令：`mvn -Dtest=Stage03ProgramGraphSetPublisherTest test`；禁网络/build。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `target/stage03/graphsetpublisher/`，实现 public `ProgramGraphSetPublisher/Stage03Reference` 与 `stage03-graph-publication-v1`；只读M1–M5 files，cross-ref→IDs/roots→eight files→atomic install。逐RED GREEN，Stage04/05可只凭reference读取；禁止补边/少图。跨阶段变更MUST STOP交Sol/ultra并按DESIGN升级用户。

### 8.0.1 模块 artifact wire schemas

全部使用 DESIGN 13.3 envelope。M1–M5 payload 都使用 8.1 的 ProgramGraph records；`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-code-structure/code-structure-draft.json` | `stage03-code-structure-draft-v1` / `STAGE03_CODE_STRUCTURE_DRAFT` | Stage01+Stage02 publication IDs/SHAs | `graphKind=CODE_STRUCTURE!`、`graphId!`、`snapshotId!`、`applicationProfileId!`、`graphProfileRef!`、`entryIds[]!`、`nodes[]!`、`edges[]!`、`coverage!`；nodes/edges按ID |
| `modules/02-call-graph/call-graph-draft.json` | `stage03-call-graph-draft-v1` / `STAGE03_CALL_GRAPH_DRAFT` | M1+Stage02 IDs/SHAs | M1同形payload且`graphKind=CALL!`；每个CALL_TARGET/JAVA_METHOD_TO_XML_STATEMENT有唯一`CALL_RETURN` pair，edge保留resolution/rule/evidenceDraftRefs并按edgeId |
| `modules/03-control-flow/control-flow-draft.json` | `stage03-control-flow-draft-v1` / `STAGE03_CONTROL_FLOW_DRAFT` | M1+M2+Stage02 IDs/SHAs | M1同形payload且`graphKind=CONTROL_FLOW!`；nodes/edges按ID，每entry的`semanticTraversalOrder[]!`按canonical DFS顺序；每guard的TRUE/FALSE或typed terminal/Gap disposition必备 |
| `modules/04-data-flow/data-flow-draft.json` | `stage03-data-flow-draft-v1` / `STAGE03_DATA_FLOW_DRAFT` | M1+M2+M3 IDs/SHAs | M1同形payload且`graphKind=DATA_FLOW!`；typed adjacent edges按edgeId，跨图endpoint只引用global graph index；`worklistAccounting!{enqueued!,processed!,overLimit!}` |
| `modules/05-evidence-graph/evidence-graph-draft.json` | `stage03-evidence-graph-draft-v1` / `STAGE03_EVIDENCE_GRAPH_DRAFT` | M1–M4+Stage01 IDs/SHAs | `graphKind=EVIDENCE!`及M1共通identity字段；`nodes[]!`为source span/rule application，`edges[]!`使用下述EvidenceEdge并按ID；`coverage!`逐program element闭合 |
| `modules/06-publish/stage03-publication.json` | `stage03-graph-publication-v1` / `STAGE03_GRAPH_PUBLICATION` | M1–M5 IDs/SHAs | `stageStatus!`、`graphIds!{codeStructure!,call!,controlFlow!,dataFlow!,evidence!}`、`stageArtifactRoot!`、`publishedArtifacts[8]!`、`nextStage!`；paths排序 |

M1–M4 draft 的 `DraftProgramNode` 字段为`nodeId/kind/canonicalValue/owningEntryIds/evidenceDraftRefs`，`DraftProgramEdge`为`edgeId/kind/fromNodeId/toNodeId/ruleId/resolution/guardNodeId?/polarity?/evidenceDraftRefs`，全部required，仅guard/polarity可在不适用时null。nodeId在整个Stage03 graph set内全局唯一；from/to可指本图node或M1–M4已发布draft中的node，后图不得重新声明前图node。`graph-index.json`保存`nodeId→owningGraphKind`并验证，不能用未登记external endpoint。

M1–M4 `coverage`固定为`candidateElementIds[]!/exactElementIds[]!/gapDispositions[]!{candidateElementId!,gapId!}/exclusionDispositions[]!{candidateElementId!,reasonCode!,evidenceRefs[]!}/scopeGapIds[]!/closed!`；四个ID/disposition数组按program element ID，candidate集合必须恰等于其余三个互斥分子的element ID union。M5改用`candidateProgramElementIds[]!/evidencedProgramElementIds[]!/gapDispositions[]!/exclusionDispositions[]!/scopeGapIds[]!/closed!`并要求candidate等于evidenced+gap+excluded。`scopeGapIds`解释仓库范围，不代替任何已发现element的处置；bounded示例因此`closed=false`。

M5不复用ProgramEdge字段冒充“evidence指向program edge”。`EvidenceEdge`固定为`edgeId/kind/evidenceNodeId/subjectGraphKind/subjectProgramElementId/ruleApplicationNodeId`，六项全required；`subjectProgramElementId`必须是M1–M4已登记nodeId或edgeId，`subjectGraphKind`必须匹配graph index owner，rule node必须存在于M5 nodes。M6发布public五图时，把M1–M4的`evidenceDraftRefs`确定性替换为排序非空`evidenceNodeIds`；public `ProgramNode/ProgramEdge`不再含draft ref。unknown binding必须不生成edge并写Gap，不能把endpoint设为故事值。payload和envelope一起入artifactId。schema/registry/sort/source rule任何变化先设计+升version；下游禁止打开AST/XML补字段。

~~~jsonl
{"schemaVersion":"stage03-code-structure-draft-v1","artifactType":"STAGE03_CODE_STRUCTURE_DRAFT","artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","producer":{"stage":3,"module":"CodeStructureGraphBuilder","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"stage01-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"artifactId":"stage02-publication:4444444444444444444444444444444444444444444444444444444444444444","sha256":"2222222222222222222222222222222222222222222222222222222222222222"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"graphKind":"CODE_STRUCTURE","graphId":"graph:code-structure-depothead","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryIds":["entry:post-depothead-batch-set-status"],"nodes":[{"nodeId":"column:jsh-depot-head-status","kind":"SQL_COLUMN","canonicalValue":"jsh_depot_head.status","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-column"]},{"nodeId":"entry:post-depothead-batch-set-status","kind":"HTTP_ENTRY","canonicalValue":"POST /depotHead/batchSetStatus","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-route"]},{"nodeId":"method:controller-batch-set-status","kind":"METHOD","canonicalValue":"com.jsh.erp.controller.DepotHeadController#batchSetStatus","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-method"]},{"nodeId":"method:mapper-update-by-example","kind":"METHOD","canonicalValue":"com.jsh.erp.datasource.mappers.DepotHeadMapper#updateByExampleSelective","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:mapper-method"]},{"nodeId":"method:service-batch-set-status","kind":"METHOD","canonicalValue":"com.jsh.erp.service.DepotHeadService#batchSetStatus","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-method"]},{"nodeId":"property:depothead-status","kind":"PROPERTY","canonicalValue":"com.jsh.erp.datasource.entities.DepotHead.status","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:set-status"]},{"nodeId":"route:post-depothead-batch-set-status","kind":"HTTP_ROUTE","canonicalValue":"POST /depotHead/batchSetStatus","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-route"]},{"nodeId":"statement:update-by-example-selective","kind":"XML_STATEMENT","canonicalValue":"com.jsh.erp.datasource.mappers.DepotHeadMapper#updateByExampleSelective","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:xml-statement"]},{"nodeId":"table:jsh-depot-head","kind":"SQL_TABLE","canonicalValue":"jsh_depot_head","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:table-update"]},{"nodeId":"xml-guard:record-status-not-null","kind":"XML_GUARD","canonicalValue":"record.status != null","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-guard"]}],"edges":[{"edgeId":"structure-edge:controller-route","kind":"ROUTE_HANDLED_BY","fromNodeId":"route:post-depothead-batch-set-status","toNodeId":"method:controller-batch-set-status","ruleId":"spring-route-merge-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:controller-route"]},{"edgeId":"structure-edge:statement-table","kind":"STATEMENT_CONTAINS_SQL","fromNodeId":"statement:update-by-example-selective","toNodeId":"table:jsh-depot-head","ruleId":"sql-update-table-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:table-update"]},{"edgeId":"structure-edge:table-declares-status","kind":"DECLARES","fromNodeId":"table:jsh-depot-head","toNodeId":"column:jsh-depot-head-status","ruleId":"sql-column-declaration-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:status-column"]}],"coverage":{"candidateElementIds":["column:jsh-depot-head-status","entry:post-depothead-batch-set-status","method:controller-batch-set-status","method:mapper-update-by-example","method:service-batch-set-status","property:depothead-status","route:post-depothead-batch-set-status","statement:update-by-example-selective","structure-edge:controller-route","structure-edge:statement-table","structure-edge:table-declares-status","table:jsh-depot-head","xml-guard:record-status-not-null"],"exactElementIds":["column:jsh-depot-head-status","entry:post-depothead-batch-set-status","method:controller-batch-set-status","method:mapper-update-by-example","method:service-batch-set-status","property:depothead-status","route:post-depothead-batch-set-status","statement:update-by-example-selective","structure-edge:controller-route","structure-edge:statement-table","structure-edge:table-declares-status","table:jsh-depot-head","xml-guard:record-status-not-null"],"gapDispositions":[],"exclusionDispositions":[],"scopeGapIds":["gap:bounded-path-set"],"closed":false},"graphProfileRef":"graph-profile:java8-spring-mybatis-v1"}}
{"schemaVersion":"stage03-call-graph-draft-v1","artifactType":"STAGE03_CALL_GRAPH_DRAFT","artifactId":"graph-draft:2222222222222222222222222222222222222222222222222222222222222222","producer":{"stage":3,"module":"CallGraphBuilder","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"stage02-publication:4444444444444444444444444444444444444444444444444444444444444444","sha256":"2222222222222222222222222222222222222222222222222222222222222222"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"graphKind":"CALL","graphId":"graph:call-depothead","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryIds":["entry:post-depothead-batch-set-status"],"nodes":[{"nodeId":"call:controller-to-service","kind":"CALL_SITE","canonicalValue":"DepotHeadController.java:185","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-call"]},{"nodeId":"call:service-to-mapper","kind":"CALL_SITE","canonicalValue":"DepotHeadService.java:803","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:mapper-call"]}],"edges":[{"edgeId":"call-edge:controller-service","kind":"CALL_TARGET","fromNodeId":"call:controller-to-service","toNodeId":"method:service-batch-set-status","ruleId":"java-static-receiver-call-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:controller-call"]},{"edgeId":"call-edge:mapper-xml","kind":"JAVA_METHOD_TO_XML_STATEMENT","fromNodeId":"method:mapper-update-by-example","toNodeId":"statement:update-by-example-selective","ruleId":"mybatis-namespace-signature-binding-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:xml-statement"]},{"edgeId":"call-edge:service-mapper","kind":"CALL_TARGET","fromNodeId":"call:service-to-mapper","toNodeId":"method:mapper-update-by-example","ruleId":"java-static-receiver-call-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:mapper-call"]},{"edgeId":"call-return-edge:mapper-service","kind":"CALL_RETURN","fromNodeId":"method:mapper-update-by-example","toNodeId":"call:service-to-mapper","ruleId":"java-call-return-pair-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:mapper-call"]},{"edgeId":"call-return-edge:service-controller","kind":"CALL_RETURN","fromNodeId":"method:service-batch-set-status","toNodeId":"call:controller-to-service","ruleId":"java-call-return-pair-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:controller-call"]},{"edgeId":"call-return-edge:xml-mapper","kind":"CALL_RETURN","fromNodeId":"statement:update-by-example-selective","toNodeId":"method:mapper-update-by-example","ruleId":"mybatis-statement-return-pair-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:xml-statement"]}],"coverage":{"candidateElementIds":["call-edge:controller-service","call-edge:mapper-xml","call-edge:service-mapper","call-return-edge:mapper-service","call-return-edge:service-controller","call-return-edge:xml-mapper","call:controller-to-service","call:service-to-mapper"],"exactElementIds":["call-edge:controller-service","call-edge:mapper-xml","call-edge:service-mapper","call-return-edge:mapper-service","call-return-edge:service-controller","call-return-edge:xml-mapper","call:controller-to-service","call:service-to-mapper"],"gapDispositions":[],"exclusionDispositions":[],"scopeGapIds":["gap:bounded-path-set"],"closed":false},"graphProfileRef":"graph-profile:java8-spring-mybatis-v1"}}
{"schemaVersion":"stage03-control-flow-draft-v1","artifactType":"STAGE03_CONTROL_FLOW_DRAFT","artifactId":"graph-draft:3333333333333333333333333333333333333333333333333333333333333333","producer":{"stage":3,"module":"ControlFlowGraphBuilder","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"graph-draft:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"stage02-publication:4444444444444444444444444444444444444444444444444444444444444444","sha256":"2222222222222222222222222222222222222222222222222222222222222222"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"graphKind":"CONTROL_FLOW","graphId":"graph:control-flow-depothead","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryIds":["entry:post-depothead-batch-set-status"],"nodes":[{"nodeId":"guard:dhids-not-empty","kind":"GUARD","canonicalValue":"dhIds is not empty","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:dhids-guard"]},{"nodeId":"step:set-status-and-update","kind":"STEP","canonicalValue":"set status and update by example","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:set-status"]},{"nodeId":"terminal:return-result","kind":"TERMINAL","canonicalValue":"return result","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:return"]}],"edges":[{"edgeId":"cfg-edge:guard-false-return","kind":"FALSE","fromNodeId":"guard:dhids-not-empty","toNodeId":"terminal:return-result","ruleId":"java-if-cfg-v1","resolution":"EXACT","guardNodeId":"guard:dhids-not-empty","polarity":"FALSE","evidenceDraftRefs":["provenance:dhids-guard"]},{"edgeId":"cfg-edge:guard-true-update","kind":"TRUE","fromNodeId":"guard:dhids-not-empty","toNodeId":"step:set-status-and-update","ruleId":"java-if-cfg-v1","resolution":"EXACT","guardNodeId":"guard:dhids-not-empty","polarity":"TRUE","evidenceDraftRefs":["provenance:dhids-guard"]},{"edgeId":"cfg-edge:update-return","kind":"NEXT","fromNodeId":"step:set-status-and-update","toNodeId":"terminal:return-result","ruleId":"java-sequence-cfg-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:return"]}],"semanticTraversalOrder":["guard:dhids-not-empty","step:set-status-and-update","terminal:return-result"],"coverage":{"candidateElementIds":["cfg-edge:guard-false-return","cfg-edge:guard-true-update","cfg-edge:update-return","guard:dhids-not-empty","step:set-status-and-update","terminal:return-result"],"exactElementIds":["cfg-edge:guard-false-return","cfg-edge:guard-true-update","cfg-edge:update-return","guard:dhids-not-empty","step:set-status-and-update","terminal:return-result"],"gapDispositions":[],"exclusionDispositions":[],"scopeGapIds":["gap:bounded-path-set"],"closed":false},"graphProfileRef":"graph-profile:java8-spring-mybatis-v1"}}
{"schemaVersion":"stage03-data-flow-draft-v1","artifactType":"STAGE03_DATA_FLOW_DRAFT","artifactId":"graph-draft:4444444444444444444444444444444444444444444444444444444444444444","producer":{"stage":3,"module":"DataFlowGraphBuilder","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"graph-draft:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"graph-draft:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"graphKind":"DATA_FLOW","graphId":"graph:data-flow-depothead","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryIds":["entry:post-depothead-batch-set-status"],"nodes":[{"nodeId":"criterion:id-in-dhids","kind":"CRITERION","canonicalValue":"id IN dhIds","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:id-where"]},{"nodeId":"parameter:service-depot-head-ids","kind":"PARAMETER","canonicalValue":"DepotHeadService.batchSetStatus.depotHeadIDs","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-ids"]},{"nodeId":"parameter:service-status","kind":"PARAMETER","canonicalValue":"DepotHeadService.batchSetStatus.status","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-status"]},{"nodeId":"placeholder:record-status","kind":"PLACEHOLDER","canonicalValue":"record.status","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-placeholder"]},{"nodeId":"value:eligible-dhids","kind":"DEFINITION","canonicalValue":"eligible dhIds","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:eligible-dhids"]},{"nodeId":"value:request-ids","kind":"DEFINITION","canonicalValue":"request.ids","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:request-ids"]},{"nodeId":"value:request-status","kind":"DEFINITION","canonicalValue":"request.status","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:request-status"]},{"nodeId":"where:id-in","kind":"WHERE_PREDICATE","canonicalValue":"id IN","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:id-where"]}],"edges":[{"edgeId":"data-edge:eligible-dhids-to-criterion","kind":"ARGUMENT_TO_PARAMETER","fromNodeId":"value:eligible-dhids","toNodeId":"criterion:id-in-dhids","ruleId":"java-criteria-argument-binding-v1","resolution":"EXACT","guardNodeId":"guard:dhids-not-empty","polarity":"TRUE","evidenceDraftRefs":["provenance:id-criterion"]},{"edgeId":"data-edge:ids-to-where","kind":"CRITERION_TO_WHERE","fromNodeId":"criterion:id-in-dhids","toNodeId":"where:id-in","ruleId":"mybatis-example-criterion-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:id-where"]},{"edgeId":"data-edge:property-to-placeholder","kind":"PROPERTY_TO_PLACEHOLDER","fromNodeId":"property:depothead-status","toNodeId":"placeholder:record-status","ruleId":"mybatis-record-property-binding-v1","resolution":"EXACT","guardNodeId":"xml-guard:record-status-not-null","polarity":"TRUE","evidenceDraftRefs":["provenance:status-placeholder"]},{"edgeId":"data-edge:record-status-to-column","kind":"PLACEHOLDER_TO_COLUMN","fromNodeId":"placeholder:record-status","toNodeId":"column:jsh-depot-head-status","ruleId":"sql-assignment-binding-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:status-column"]},{"edgeId":"data-edge:request-ids-to-service-ids","kind":"ARGUMENT_TO_PARAMETER","fromNodeId":"value:request-ids","toNodeId":"parameter:service-depot-head-ids","ruleId":"java-argument-binding-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:controller-call"]},{"edgeId":"data-edge:request-to-service-status","kind":"ARGUMENT_TO_PARAMETER","fromNodeId":"value:request-status","toNodeId":"parameter:service-status","ruleId":"java-argument-binding-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:controller-call"]},{"edgeId":"data-edge:service-ids-to-eligible-dhids","kind":"DEF_USE","fromNodeId":"parameter:service-depot-head-ids","toNodeId":"value:eligible-dhids","ruleId":"java-def-use-worklist-v1","resolution":"EXACT","guardNodeId":"guard:dhids-not-empty","polarity":"TRUE","evidenceDraftRefs":["provenance:eligible-dhids"]},{"edgeId":"data-edge:service-status-to-property","kind":"SETTER_TO_PROPERTY","fromNodeId":"parameter:service-status","toNodeId":"property:depothead-status","ruleId":"java-setter-property-v1","resolution":"EXACT","guardNodeId":"guard:dhids-not-empty","polarity":"TRUE","evidenceDraftRefs":["provenance:set-status"]}],"coverage":{"candidateElementIds":["criterion:id-in-dhids","data-edge:eligible-dhids-to-criterion","data-edge:ids-to-where","data-edge:property-to-placeholder","data-edge:record-status-to-column","data-edge:request-ids-to-service-ids","data-edge:request-to-service-status","data-edge:service-ids-to-eligible-dhids","data-edge:service-status-to-property","parameter:service-depot-head-ids","parameter:service-status","placeholder:record-status","value:eligible-dhids","value:request-ids","value:request-status","where:id-in"],"exactElementIds":["criterion:id-in-dhids","data-edge:eligible-dhids-to-criterion","data-edge:ids-to-where","data-edge:property-to-placeholder","data-edge:record-status-to-column","data-edge:request-ids-to-service-ids","data-edge:request-to-service-status","data-edge:service-ids-to-eligible-dhids","data-edge:service-status-to-property","parameter:service-depot-head-ids","parameter:service-status","placeholder:record-status","value:eligible-dhids","value:request-ids","value:request-status","where:id-in"],"gapDispositions":[],"exclusionDispositions":[],"scopeGapIds":["gap:bounded-path-set"],"closed":false},"worklistAccounting":{"enqueued":12,"processed":12,"overLimit":0},"graphProfileRef":"graph-profile:java8-spring-mybatis-v1"}}
{"schemaVersion":"stage03-evidence-graph-draft-v1","artifactType":"STAGE03_EVIDENCE_GRAPH_DRAFT","artifactId":"graph-draft:5555555555555555555555555555555555555555555555555555555555555555","producer":{"stage":3,"module":"EvidenceGraphBuilder","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"graph-draft:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"graph-draft:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"graph-draft:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"artifactId":"stage01-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"graphKind":"EVIDENCE","graphId":"graph:evidence-depothead","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryIds":["entry:post-depothead-batch-set-status"],"nodes":[{"nodeId":"evidence:controller-call","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:183-185","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-call"]},{"nodeId":"evidence:controller-method","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:178-191","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-method"]},{"nodeId":"evidence:controller-route","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:43,178-191","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-route"]},{"nodeId":"evidence:dhids-guard","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:798-821","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:dhids-guard"]},{"nodeId":"evidence:eligible-dhids","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:749-803","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:eligible-dhids"]},{"nodeId":"evidence:id-criterion","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHeadExample.java:149-151","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:id-criterion"]},{"nodeId":"evidence:id-where","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:494-495","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:id-where"]},{"nodeId":"evidence:mapper-call","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:798-803","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:mapper-call"]},{"nodeId":"evidence:mapper-method","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/DepotHeadMapper.java:23","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:mapper-method"]},{"nodeId":"evidence:request-ids","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:184","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:request-ids"]},{"nodeId":"evidence:request-status","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:183","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:request-status"]},{"nodeId":"evidence:return","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:821","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:return"]},{"nodeId":"evidence:service-ids","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:742-749","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-ids"]},{"nodeId":"evidence:service-method","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:742-821","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-method"]},{"nodeId":"evidence:service-status","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:742","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-status"]},{"nodeId":"evidence:set-status","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:798-803","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:set-status"]},{"nodeId":"evidence:status-column-span","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:472-473","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-column"]},{"nodeId":"evidence:status-guard","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:472","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-guard"]},{"nodeId":"evidence:status-placeholder","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:472-473","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-placeholder"]},{"nodeId":"evidence:table-update","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:386","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:table-update"]},{"nodeId":"evidence:xml-statement","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:385-495","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:xml-statement"]},{"nodeId":"rule:java-argument-binding-v1","kind":"RULE_APPLICATION","canonicalValue":"java-argument-binding-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-call-return-pair-v1","kind":"RULE_APPLICATION","canonicalValue":"java-call-return-pair-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-criteria-argument-binding-v1","kind":"RULE_APPLICATION","canonicalValue":"java-criteria-argument-binding-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-def-use-worklist-v1","kind":"RULE_APPLICATION","canonicalValue":"java-def-use-worklist-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-if-cfg-v1","kind":"RULE_APPLICATION","canonicalValue":"java-if-cfg-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-sequence-cfg-v1","kind":"RULE_APPLICATION","canonicalValue":"java-sequence-cfg-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-setter-property-v1","kind":"RULE_APPLICATION","canonicalValue":"java-setter-property-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-static-receiver-call-v1","kind":"RULE_APPLICATION","canonicalValue":"java-static-receiver-call-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:mybatis-example-criterion-v1","kind":"RULE_APPLICATION","canonicalValue":"mybatis-example-criterion-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:mybatis-namespace-signature-binding-v1","kind":"RULE_APPLICATION","canonicalValue":"mybatis-namespace-signature-binding-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:mybatis-record-property-binding-v1","kind":"RULE_APPLICATION","canonicalValue":"mybatis-record-property-binding-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:mybatis-statement-return-pair-v1","kind":"RULE_APPLICATION","canonicalValue":"mybatis-statement-return-pair-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:source-element-parser-v1","kind":"RULE_APPLICATION","canonicalValue":"source-element-parser-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:spring-route-merge-v1","kind":"RULE_APPLICATION","canonicalValue":"spring-route-merge-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:sql-assignment-binding-v1","kind":"RULE_APPLICATION","canonicalValue":"sql-assignment-binding-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:sql-column-declaration-v1","kind":"RULE_APPLICATION","canonicalValue":"sql-column-declaration-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:sql-update-table-v1","kind":"RULE_APPLICATION","canonicalValue":"sql-update-table-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]}],"edges":[{"edgeId":"evidence-edge:support:call-edge:controller-service","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:controller-call","subjectGraphKind":"CALL","subjectProgramElementId":"call-edge:controller-service","ruleApplicationNodeId":"rule:java-static-receiver-call-v1"},{"edgeId":"evidence-edge:support:call-edge:mapper-xml","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:xml-statement","subjectGraphKind":"CALL","subjectProgramElementId":"call-edge:mapper-xml","ruleApplicationNodeId":"rule:mybatis-namespace-signature-binding-v1"},{"edgeId":"evidence-edge:support:call-edge:service-mapper","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:mapper-call","subjectGraphKind":"CALL","subjectProgramElementId":"call-edge:service-mapper","ruleApplicationNodeId":"rule:java-static-receiver-call-v1"},{"edgeId":"evidence-edge:support:call-return-edge:mapper-service","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:mapper-call","subjectGraphKind":"CALL","subjectProgramElementId":"call-return-edge:mapper-service","ruleApplicationNodeId":"rule:java-call-return-pair-v1"},{"edgeId":"evidence-edge:support:call-return-edge:service-controller","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:controller-call","subjectGraphKind":"CALL","subjectProgramElementId":"call-return-edge:service-controller","ruleApplicationNodeId":"rule:java-call-return-pair-v1"},{"edgeId":"evidence-edge:support:call-return-edge:xml-mapper","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:xml-statement","subjectGraphKind":"CALL","subjectProgramElementId":"call-return-edge:xml-mapper","ruleApplicationNodeId":"rule:mybatis-statement-return-pair-v1"},{"edgeId":"evidence-edge:support:call:controller-to-service","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:controller-call","subjectGraphKind":"CALL","subjectProgramElementId":"call:controller-to-service","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:call:service-to-mapper","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:mapper-call","subjectGraphKind":"CALL","subjectProgramElementId":"call:service-to-mapper","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:cfg-edge:guard-false-return","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:dhids-guard","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"cfg-edge:guard-false-return","ruleApplicationNodeId":"rule:java-if-cfg-v1"},{"edgeId":"evidence-edge:support:cfg-edge:guard-true-update","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:dhids-guard","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"cfg-edge:guard-true-update","ruleApplicationNodeId":"rule:java-if-cfg-v1"},{"edgeId":"evidence-edge:support:cfg-edge:update-return","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:return","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"cfg-edge:update-return","ruleApplicationNodeId":"rule:java-sequence-cfg-v1"},{"edgeId":"evidence-edge:support:column:jsh-depot-head-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:status-column-span","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"column:jsh-depot-head-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:criterion:id-in-dhids","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:id-where","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"criterion:id-in-dhids","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:data-edge:eligible-dhids-to-criterion","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:id-criterion","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:eligible-dhids-to-criterion","ruleApplicationNodeId":"rule:java-criteria-argument-binding-v1"},{"edgeId":"evidence-edge:support:data-edge:ids-to-where","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:id-where","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:ids-to-where","ruleApplicationNodeId":"rule:mybatis-example-criterion-v1"},{"edgeId":"evidence-edge:support:data-edge:property-to-placeholder","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:status-placeholder","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:property-to-placeholder","ruleApplicationNodeId":"rule:mybatis-record-property-binding-v1"},{"edgeId":"evidence-edge:support:data-edge:record-status-to-column","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:status-column-span","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:record-status-to-column","ruleApplicationNodeId":"rule:sql-assignment-binding-v1"},{"edgeId":"evidence-edge:support:data-edge:request-ids-to-service-ids","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:controller-call","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:request-ids-to-service-ids","ruleApplicationNodeId":"rule:java-argument-binding-v1"},{"edgeId":"evidence-edge:support:data-edge:request-to-service-status","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:controller-call","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:request-to-service-status","ruleApplicationNodeId":"rule:java-argument-binding-v1"},{"edgeId":"evidence-edge:support:data-edge:service-ids-to-eligible-dhids","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:eligible-dhids","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:service-ids-to-eligible-dhids","ruleApplicationNodeId":"rule:java-def-use-worklist-v1"},{"edgeId":"evidence-edge:support:data-edge:service-status-to-property","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:set-status","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:service-status-to-property","ruleApplicationNodeId":"rule:java-setter-property-v1"},{"edgeId":"evidence-edge:support:entry:post-depothead-batch-set-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:controller-route","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"entry:post-depothead-batch-set-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:guard:dhids-not-empty","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:dhids-guard","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"guard:dhids-not-empty","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:method:controller-batch-set-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:controller-method","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"method:controller-batch-set-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:method:mapper-update-by-example","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:mapper-method","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"method:mapper-update-by-example","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:method:service-batch-set-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:service-method","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"method:service-batch-set-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:parameter:service-depot-head-ids","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:service-ids","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"parameter:service-depot-head-ids","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:parameter:service-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:service-status","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"parameter:service-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:placeholder:record-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:status-placeholder","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"placeholder:record-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:property:depothead-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:set-status","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"property:depothead-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:route:post-depothead-batch-set-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:controller-route","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"route:post-depothead-batch-set-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:statement:update-by-example-selective","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:xml-statement","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"statement:update-by-example-selective","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:step:set-status-and-update","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:set-status","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"step:set-status-and-update","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:structure-edge:controller-route","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:controller-route","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"structure-edge:controller-route","ruleApplicationNodeId":"rule:spring-route-merge-v1"},{"edgeId":"evidence-edge:support:structure-edge:statement-table","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:table-update","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"structure-edge:statement-table","ruleApplicationNodeId":"rule:sql-update-table-v1"},{"edgeId":"evidence-edge:support:structure-edge:table-declares-status","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:status-column-span","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"structure-edge:table-declares-status","ruleApplicationNodeId":"rule:sql-column-declaration-v1"},{"edgeId":"evidence-edge:support:table:jsh-depot-head","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:table-update","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"table:jsh-depot-head","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:terminal:return-result","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:return","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"terminal:return-result","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:value:eligible-dhids","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:eligible-dhids","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"value:eligible-dhids","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:value:request-ids","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:request-ids","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"value:request-ids","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:value:request-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:request-status","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"value:request-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:where:id-in","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:id-where","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"where:id-in","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:xml-guard:record-status-not-null","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:status-guard","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"xml-guard:record-status-not-null","ruleApplicationNodeId":"rule:source-element-parser-v1"}],"coverage":{"candidateProgramElementIds":["call-edge:controller-service","call-edge:mapper-xml","call-edge:service-mapper","call-return-edge:mapper-service","call-return-edge:service-controller","call-return-edge:xml-mapper","call:controller-to-service","call:service-to-mapper","cfg-edge:guard-false-return","cfg-edge:guard-true-update","cfg-edge:update-return","column:jsh-depot-head-status","criterion:id-in-dhids","data-edge:eligible-dhids-to-criterion","data-edge:ids-to-where","data-edge:property-to-placeholder","data-edge:record-status-to-column","data-edge:request-ids-to-service-ids","data-edge:request-to-service-status","data-edge:service-ids-to-eligible-dhids","data-edge:service-status-to-property","entry:post-depothead-batch-set-status","guard:dhids-not-empty","method:controller-batch-set-status","method:mapper-update-by-example","method:service-batch-set-status","parameter:service-depot-head-ids","parameter:service-status","placeholder:record-status","property:depothead-status","route:post-depothead-batch-set-status","statement:update-by-example-selective","step:set-status-and-update","structure-edge:controller-route","structure-edge:statement-table","structure-edge:table-declares-status","table:jsh-depot-head","terminal:return-result","value:eligible-dhids","value:request-ids","value:request-status","where:id-in","xml-guard:record-status-not-null"],"evidencedProgramElementIds":["call-edge:controller-service","call-edge:mapper-xml","call-edge:service-mapper","call-return-edge:mapper-service","call-return-edge:service-controller","call-return-edge:xml-mapper","call:controller-to-service","call:service-to-mapper","cfg-edge:guard-false-return","cfg-edge:guard-true-update","cfg-edge:update-return","column:jsh-depot-head-status","criterion:id-in-dhids","data-edge:eligible-dhids-to-criterion","data-edge:ids-to-where","data-edge:property-to-placeholder","data-edge:record-status-to-column","data-edge:request-ids-to-service-ids","data-edge:request-to-service-status","data-edge:service-ids-to-eligible-dhids","data-edge:service-status-to-property","entry:post-depothead-batch-set-status","guard:dhids-not-empty","method:controller-batch-set-status","method:mapper-update-by-example","method:service-batch-set-status","parameter:service-depot-head-ids","parameter:service-status","placeholder:record-status","property:depothead-status","route:post-depothead-batch-set-status","statement:update-by-example-selective","step:set-status-and-update","structure-edge:controller-route","structure-edge:statement-table","structure-edge:table-declares-status","table:jsh-depot-head","terminal:return-result","value:eligible-dhids","value:request-ids","value:request-status","where:id-in","xml-guard:record-status-not-null"],"gapDispositions":[],"exclusionDispositions":[],"scopeGapIds":["gap:bounded-path-set"],"closed":false},"graphProfileRef":"graph-profile:java8-spring-mybatis-v1"}}
{"schemaVersion":"stage03-graph-publication-v1","artifactType":"STAGE03_GRAPH_PUBLICATION","artifactId":"stage03-publication:6666666666666666666666666666666666666666666666666666666666666666","producer":{"stage":3,"module":"ProgramGraphSetPublisher","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"graph-draft:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"graph-draft:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"graph-draft:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"artifactId":"graph-draft:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"stageStatus":"SUCCEEDED_WITH_GAPS","graphIds":{"codeStructure":"graph:code-structure-depothead","call":"graph:call-depothead","controlFlow":"graph:control-flow-depothead","dataFlow":"graph:data-flow-depothead","evidence":"graph:evidence-depothead"},"stageArtifactRoot":"stage-root:0303030303030303030303030303030303030303030303030303030303030303","publishedArtifacts":[{"path":"call-graph.json","sizeBytes":2100,"sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"path":"code-structure-graph.json","sizeBytes":2200,"sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"path":"control-flow-graph.json","sizeBytes":2300,"sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"path":"data-flow-graph.json","sizeBytes":2400,"sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"path":"evidence-graph.json","sizeBytes":2500,"sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"path":"graph-gaps.jsonl","sizeBytes":700,"sha256":"6666666666666666666666666666666666666666666666666666666666666666"},{"path":"graph-index.json","sizeBytes":900,"sha256":"7777777777777777777777777777777777777777777777777777777777777777"},{"path":"stage-receipt.json","sizeBytes":1100,"sha256":"8888888888888888888888888888888888888888888888888888888888888888"}],"nextStage":"04-prove-code-facts"}}
~~~

### 8.1 Interface 与通用 graph records

~~~java
interface ProgramGraphBuilder {
    Stage03Reference build(Stage01Reference source, Stage02Reference discovery);
}
~~~

~~~text
ProgramGraph
  schemaVersion
  graphKind
  graphId
  snapshotId
  applicationProfileId
  graphProfileRef
  nodes[]
  edges[]
  coverage

ProgramNode
  nodeId
  kind
  canonicalValue
  owningEntryIds[]
  evidenceNodeIds[]

ProgramEdge
  edgeId
  kind
  fromNodeId
  toNodeId
  ruleId
  resolution
  guardNodeId?
  polarity?
  evidenceNodeIds[]

EvidenceEdge
  edgeId
  kind
  evidenceNodeId
  subjectGraphKind
  subjectProgramElementId
  ruleApplicationNodeId
~~~

graphKind 恰为 CODE_STRUCTURE、CALL、CONTROL_FLOW、DATA_FLOW、EVIDENCE。各 graph 使用自己的 node/edge kind registry；不能临时发明字符串 kind。

### 8.2 五图最小 registry

| 图 | 必需 node/edge 类别 |
| --- | --- |
| code structure | package/type/field/method/parameter/annotation/config/XML namespace/statement/SQL table/column；CONTAINS、DECLARES、CONFIG_RESOLVES_RESOURCE、STATEMENT_CONTAINS_SQL |
| call | call site/receiver/method target/mapper statement；RECEIVER_TYPE、CALL_TARGET、CALL_RETURN、JAVA_METHOD_TO_XML_STATEMENT |
| control flow | entry/guard/basic step/call/return/throw/terminal；ENTRY、TRUE、FALSE、NEXT、CALL、RETURN、TERMINAL |
| data flow | definition/use/argument/parameter/property/criterion/placeholder/column；DEF_USE、ARGUMENT_TO_PARAMETER、ASSIGNMENT、SETTER_TO_PROPERTY、PROPERTY_TO_PLACEHOLDER、CRITERION_TO_WHERE、PLACEHOLDER_TO_COLUMN |
| evidence | source file/span/rule application；LOCATES、PARSED_BY、BOUND_BY、SUPPORTS_PROGRAM_NODE、SUPPORTS_PROGRAM_EDGE |

标准 MyBatis动态 set/if/include 不是天然 Gap；只有实现能安全展开并证明条件/parameter path 时才生成 exact edges，否则局部 Gap。

### 8.3 DepotHead 必需闭包

要让后续证明 jsh_depot_head.status，至少闭合：

1. class/method route structure；
2. Controller call site :185 → Service method :742；
3. status expression :183 → argument :185 → Service param :742；
4. Service param → setStatus :800 → DepotHead.status property；
5. Mapper call :803 → Mapper Java :23 → XML namespace/statement :3,:385；
6. record parameter/property → XML if/placeholder :472-473；
7. XML statement → table jsh_depot_head :386；
8. ids :184 → parsed ids :749 → eligible dhIds :752-795 → andIdIn :802 → Example criterion :149-151 → XML where include :70-93,:494-495。

缺任一段只能形成 Gap，不能跨缺口连一条“看起来正确”的长 edge。

### 8.4 Identity、accounting 与预算

nodeId 绑定 snapshot、graph kind、semantic kind、canonical value、source identity 和 rule version；edgeId 再绑定 exact endpoints、rule、resolution、guard/polarity。graphId 最后绑定全部排序 nodes/edges、profile 和 coverage。

每张图必须满足 discovered candidates = admitted exact + gap + reasoned exclusion。跨图index建立全局唯一node/edge catalog，验证ProgramEdge所有外部endpoint以及EvidenceEdge的subject/rule refs；unknown node/edge不得被默默丢弃。Evidence graph从来不把program edge ID塞进`toNodeId`。

预算分图执行 maxNodes/maxEdges，并覆盖 AST/XML/SQL chars、call depth、CFG states、dataflow worklist、evidence spans 和 traversal depth。超限不截断为 COMPLETE。

### 8.5 安全与 failure codes

所有 parser 输入来自 verified handles。XML 禁外部 DTD/entity/schema/network。不开客户 classloader，不做 reflection runtime，不运行 annotation processor、MyBatis 或 SQL。

稳定 code：

GRAPH_PROFILE_INVALID、GRAPH_REFERENCE_BROKEN、GRAPH_ACCOUNTING_INVARIANT_BROKEN、CODE_STRUCTURE_INVARIANT_BROKEN、CALL_TARGET_AMBIGUOUS、CALL_RETURN_PAIR_INVALID、CFG_POLARITY_MISSING、CFG_TERMINAL_UNRESOLVED、DATA_FLOW_BINDING_UNPROVEN、DATA_FLOW_WORKLIST_LIMIT_EXCEEDED、EVIDENCE_GRAPH_INVARIANT_BROKEN、EVIDENCE_SOURCE_REOPEN_MISMATCH、XML_SECURITY_POLICY_UNENFORCEABLE、XML_EXTERNAL_RESOLUTION_ATTEMPT、STAGE03_RESOURCE_LIMIT_EXCEEDED。

### 8.6 测试 seam 与验收

- 五个 exact filenames 是 first-class artifacts；缺一、多一或交叉替换必须失败。
- Controller→Service、Service→Mapper、Mapper→XML 分段 deletion mutation 分别失败。
- TRUE/FALSE、terminal、call/return edge mutation 不得用源码顺序补回。
- status 和 ids 每一段 dataflow deletion 都使对应 Fact 不可证明。
- 同名 decoy type/method/XML statement 不能被字符串匹配选中。
- Evidence span hash、rule ID 或 graph endpoint mutation fail closed。
- 不同 root/input order 产生相同五图 canonical bytes。
- standard DOCTYPE 零网络 lookup；external entity mutation fatal。

验收分两层：结构验收要求八个 exact files、五图 envelope/roots/cross-references/accounting 全闭合；DepotHead 业务验收要求 8.3 的八段闭包逐段 mutation 可使对应 edge/Fact 不成立。两层都通过且无字符串猜测旁路，Stage 03 才算目标实现完成。

### 8.7 已冻结裁决：实现者不得自由推断

- 图种类和五个 filenames 恰为本文件定义的五项；不能合并为 repository blob，也不能把第六种临时图加入 production set。
- node/edge kind 只来自版本化 registry；不支持的语法产生带 affected entry 的 Gap，不能发明自由字符串 kind。
- call、control、data binding 必须逐段 EXACT；simple name、源码邻近、声明顺序和注释不能补边。
- Evidence graph 证明 graph provenance，Proof 在 Stage 04 证明 Fact；两者不可合并或互相替代。
- 五图作为一个原子 stage set 安装/恢复；不能复用四张再现场生成第五张。
- parser core、graph storage、worklist 和并行实现可自行选择；graph 边界、registry、identity、Gap/fatal 与 DepotHead closure 不得改变。

## 9. 当前实现差距审计

| 状态 | 当前事实 |
| --- | --- |
| **部分具备（内存投影）** | RepositoryModel 含部分结构/call/SQL，Stage01FlowView 含受限 CFG 和 Proof binding projection |
| **缺失目标产物** | 没有 code-structure/call/control-flow/data-flow/evidence 五个 standalone canonical graph files |
| **能力缺口** | 通用跨 Controller→Service→entity/example→Mapper XML 的 DepotHead status/ids dataflow 不充分；Evidence graph 也未作为独立 proof input |
| **设计裁决** | 不把现有 RepositoryModel shape 当永久 seam；后续应从共享 parser core 投影五图并逐阶段落盘 |

当前 jshERP slice 的 Gap、0 Flow、0 Capsule 是诚实结果；不能用本文的目标 edge JSON 改写这一成熟度。
