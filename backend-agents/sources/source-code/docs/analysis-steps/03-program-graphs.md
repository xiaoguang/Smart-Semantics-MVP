# 程序图

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。运行顺序只由文件名中的 `03-` 与运行目录 `steps/03-program-graphs/` 表达。

本文示例严格使用DESIGN §1.3的`NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN`分类；未标为strict的digest/size/ID不可复制为golden。权威字段表、enum、identity和direct-preimage合同始终exact，不能靠示例降级删除。

## 1. 为什么存在

业务流程不是“Controller 文件后面跟着 Service 文件”。要证明一次状态更新，程序必须分别知道：

- 代码元素怎样组织；
- 调用实际绑定到谁；
- 条件与终点怎样连接；
- status 和 ids 的值怎样跨层流动；
- 每个 node/edge 为什么能回到固定源码。

这五类问题各有不同不变量，因此形成五张一等、独立持久化的程序图。它们共同服务 Fact 和 Flow，但不能折成一个模糊的 repository model blob。

## 2. 具体输入与 DepotHead 例子

输入是 分析步骤“已验证源码清单” snapshot/source inventory、分析步骤“应用发现” application profile/entry/catalog/capability artifacts、graph profile、toolchain/schema hashes 和预算。

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

1. 重验VerifiedSourceInventory与ApplicationDiscovery receipt roots；解析器只读verified handles。
2. 建代码结构图：package、type、field、method、parameter、annotation、config、XML statement、SQL table/column 和 containment/declaration。
3. 建调用图：receiver 静态类型、method candidate、direct call、call/return pairing、Mapper Java method 与 XML statement binding。
4. 建控制流图：ENTRY、TRUE、FALSE、NEXT、CALL、RETURN、THROW/TERMINAL；每个 branch edge保存 guardId 和 polarity。
5. 建数据流图：definition/use、assignment、return、argument→parameter、setter→property、criteria→XML parameter、placeholder→column。
6. 建证据图：graph node/edge → source span、file SHA、span SHA、parser rule、binding rule；Evidence node不直接等于 Fact。
7. 做逐图 reference/accounting、跨图 endpoint、entry ownership、unique binding 和 resource validation。
8. 写满五图及 index/gaps，在同文件系统原子安装整个 分析步骤“程序图” 目录。

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
| program-graphs-receipt.json | 上游 roots、control hashes、artifact set 与 Gap count |

**示例分类：NARRATIVE_ILLUSTRATION。** DepotHead status edge 的目标形状：

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

目标出口始终是八个命名文件的完整 analysis step set。即使某类关系因 Gap 没有 admitted edge，对应 graph 文件也必须包含非空 schema/profile/snapshot/coverage envelope；不能省掉空图。未来 DepotHead 正向验收还要求五图共同包含 8.3 的全部 route、call、control、status 和 ids 闭包；当前实现没有达到这一正向出口。

### 4.1 人类 walkthrough：五图模块用什么文件接力

**示例分类：NARRATIVE_ILLUSTRATION。** 下列行只说明模块职责，不是wire或跨analysis step identity chain。

~~~jsonl
{"module":"code-structure","artifact":"modules/01-code-structure/code-structure-draft.json","takesFrom":["VerifiedSourceInventoryReference","ApplicationDiscoveryReference"],"says":{"entryId":"entry:post-depothead-batch-set-status","declares":["DepotHeadController#batchSetStatus","DepotHeadService#batchSetStatus","DepotHeadMapper#updateByExampleSelective","DepotHead.status","jsh_depot_head.status"]}}
{"module":"call-graph","artifact":"modules/02-call-graph/call-graph-draft.json","takesFrom":["code-structure-draft.json","ApplicationDiscovery mapper catalog"],"says":{"entryId":"entry:post-depothead-batch-set-status","calls":["Controller→Service","Service→Mapper Java","Mapper Java→Mapper XML statement"]}}
{"module":"control-flow","artifact":"modules/03-control-flow/control-flow-draft.json","takesFrom":["code-structure-draft.json","call-graph-draft.json"],"says":{"entryId":"entry:post-depothead-batch-set-status","guard":"dhIds is not empty","truePath":"set status and update","terminal":"return result"}}
{"module":"data-flow","artifact":"modules/04-data-flow/data-flow-draft.json","takesFrom":["structure/call/control artifacts"],"says":{"entryId":"entry:post-depothead-batch-set-status","valuePath":"request status→Service status→DepotHead.status→record.status→jsh_depot_head.status","wherePath":"request ids→dhIds→andIdIn→WHERE id IN"}}
{"module":"evidence-graph","artifact":"modules/05-evidence-graph/evidence-graph-draft.json","takesFrom":["four program-graph drafts","verified source"],"says":{"statusColumnEdge":"data-edge:record-status-to-column","source":"DepotHeadMapper.xml:472-473","rule":"mybatis-record-property-binding-v1"}}
{"module":"publish","artifacts":"modules/06-publish/<seven-semantic-files>","receipt":"modules/06-publish/module-receipt.json","takesFrom":["five graph drafts"],"says":{"graphKinds":["CODE_STRUCTURE","CALL","CONTROL_FLOW","DATA_FLOW","EVIDENCE"],"semanticFiles":["code-structure-graph.json","call-graph.json","control-flow-graph.json","data-flow-graph.json","evidence-graph.json","graph-index.json","graph-gaps.jsonl"],"analysisStepReceipt":null,"nextStep":"CanonicalAnalysisStepArtifactStore"}}
~~~

同一个 `entry:post-depothead-batch-set-status` 从 M1 到 M4 原样复用；M5 只给既有 node/edge 加来源，M6只形成七个semantic analysis step payload并先取得自己的module receipt。随后`CanonicalAnalysisStepArtifactStore`从fresh reopen的M6 reference写七项、计算root并最后创建`program-graphs-receipt.json`。任何模块都不能把 `status` 和 `ids` 改名或跨缺边拼成长链。

## 5. 下游怎样消费而不返工

分析步骤“已证明代码事实” 只读五张 graph 与 graph-index 来枚举候选 Fact、构建 Proof。分析步骤“业务流程” 复用 call/control/data graphs 编译 Flow。它们都不能：

- 重新解析 AST/XML 来补一个缺边；
- 用源码行顺序推断控制流；
- 用 simple name 或字符串相等绑定调用/数据；
- 从 evidence span 直接跳过 graph edge 生成 Fact。

新增 graph kind/edge rule 必须版本化 schema/profile，并生成新的 分析步骤“程序图” identity。

### 下游前置条件与后置保证

| ProvenCodeFacts或BusinessFlows开始前必须成立 | 分析步骤“程序图”成功后保证 |
| --- | --- |
| VerifiedSourceInventory与ApplicationDiscovery roots和ProgramGraphs controls一致；八文件artifact set/root重验通过 | 五个graph filenames恰好存在并共享同一snapshot/application profile identity |
| graph-index 中的 node/edge catalog、coverage 和 Gap accounting 闭合 | 每个 admitted edge 有 exact endpoints、版本化 rule 和 Evidence refs；歧义只进 Gap |
| 每个 graph envelope 可独立解析；所有跨图 endpoint 均存在 | 分析步骤“已证明代码事实” 可只用五图证明 Fact，分析步骤“业务流程” 可只用 call/control/data graph 编译 Flow |

下游不得把 graph Gap 当成“可稍后从源码补回”的提示。缺边意味着对应 Fact/Flow 不能闭合，除非以新 schema/profile 生成新的 分析步骤“程序图”。

## 6. 成功、Gap、fatal 与显式复用

- **成功**：五张 graph、graph-index、graph-gaps 与analysis step receipt这八个命名文件全部存在；五图分别自验、跨图引用/accounting 闭合，整个分析步骤目录原子安装。
- **带 Gap 成功**：局部 unsupported、ambiguous 或 over-limit site 有明确 locator、affected entries 和 disposition；图中不得出现猜测 edge。
- **fatal**：缺任一图、断引用、重复 canonical ID、call/data edge 端点不一致、CFG branch 无 polarity、Evidence 不能回到 snapshot、XML 外部解析、安全/预算无法执行或 artifact collision。
- **显式复用**：五图视为一个原子analysis step set；不能只引用其中四张。control hashes完全相等且artifact root重验通过，新的ProvenCodeFacts execution才可读取。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 解析 Java/config/XML/SQL | 是 | 否 |
| 构建五图与唯一 binding | 是 | 否 |
| 补未知调用/数据流 | 否，输出 Gap | 否 |
| 从图解释业务名称 | 否，留到 分析步骤“流程解释” | 否 |

产品运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块集合固定为五个 graph builder 加一个 graph-set publication specifier。顺序为 `CodeStructureGraphBuilder` → `CallGraphBuilder` / `ControlFlowGraphBuilder` / `DataFlowGraphBuilder` → `EvidenceGraphBuilder` → `ProgramGraphSetPublicationSpecifier` → `CanonicalAnalysisStepArtifactStore`；analysis step store不是第七个业务模块。中间只能交换 typed node/edge/provenance drafts。任何 builder 都不能调用 LLM。

在 M1 前、并在每一个 graph builder 需要上游输入时，内部的
`PersistedProgramGraphInputReader`执行一次**输入重新打开**。它不是第六种图、不是
module、不会产生新文件，也不会改变五图的模块顺序。它只接收
`VerifiedSourceInventoryReference + ApplicationDiscoveryReference`，重新打开并校验以下已经
发布的 JSON/JSONL 与 receipt：源码清单的`source-input.json`、`verified-snapshot.json`、
`source-inventory.jsonl`；应用发现的`application-profile.json`、`capability-report.json`、
`entry-points.jsonl`、`mapper-catalog.jsonl`。然后它重新读取已登记的冻结源码字节，验证文件
ID、路径、模式、大小、UTF-8、文件 SHA-256 与两个 analysis-step publication 的 controls/
source predecessor 完全一致。

重新打开成功后，reader 只向本 analysis step 提供两个不可变输入视图：

```text
ReopenedProgramGraphInputs
  source: CodeStructureSource
    snapshotId, complete/bounded scope, controls,
    source-inventory/snapshot ArtifactReference, verified text documents
  discovery: ProgramGraphDiscoveryInputs
    applicationProfileId + profile/capability/entry/catalog ArtifactReference,
    entries[] { entryId, handlerFqn, parameterNames, route evidence },
    mapperCatalog[] { catalogEntryId, Java interface/method candidates,
                      XML resource/namespace/statement candidates, binding state }
```

M1只消费`source`和`discovery`中的 identity/entry ownership；M2 才消费 entry 的 handler
和 Mapper candidates，M3消费 entry root，M4消费已验证的源码及前驱图。reader 不从路径扫描
工作区、不执行 Maven、不重新发现 HTTP/Mapper site、不用字符串搜索替代上游 JSON，也不替
builder 猜测缺失值。任何 descriptor/schema/receipt/source predecessor/controls/file identity/
coverage/accounting 不闭合时，整个本次 graph module 以稳定`GRAPH_REFERENCE_BROKEN`失败；
语法或 binding 局部不支持仍由相应 builder 形成 typed Gap。这样 M2 可以从真实的已发布
ApplicationDiscovery 内容获取候选，而不是从 M1 的简化 ID 列表或测试对象反推业务关系。

M1 自己的 module publication 还必须经过一个独立的、同样 fail-closed 的重开模块，才能成为
M2 的输入。`PersistedCodeStructureGraphReader` 是该唯一 seam；它不是 graph builder、不是第七个
module，也不产生新文件：

```text
PersistedCodeStructureGraphReader.reopen(
  CodeStructureGraphDraftReference reference,
  ReopenedProgramGraphInputs sameInputs,
  ArtifactReference expectedGraphProfileRef)
    -> ReopenedCodeStructureGraph

ReopenedCodeStructureGraph                         // sealed opaque immutable aggregate
  reference: CodeStructureGraphDraftReference
  payloadRef: ArtifactReference
  draft: CodeStructureGraphDraft
  basis: ProgramGraphInputBasis

ProgramGraphInputBasis
  snapshotId, applicationProfileId, entryIds
  sourceInventoryRef, verifiedSnapshotRef
  applicationProfileRef, capabilityReportRef
  entryPointsRef, mapperCatalogRef
  controls, graphProfileRef
```

reader 先调用 `CanonicalModuleArtifactStore.reopen(reference.publication)`；只有 store 已重验 module
root、receipt ID/SHA、payload descriptor/bytes、envelope 和 receipt 后才继续。reader 随后要求：

1. module address 精确为当前 ProgramGraphs execution 的
   `<runId> / program-graphs / 1 / code-structure`，envelope producer、receipt address 与 reference
   相同，module version 为 `v1`，completion 只能是 `SUCCEEDED | SUCCEEDED_WITH_GAPS`；
2. publication 只有一个 `code-structure-draft.json`，descriptor/envelope 的 artifact type 与 schema
   精确为 `PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT / program-graphs-code-structure-draft-v2`；
3. receipt 与 envelope 的 upstream 集合恰为同一个 graph profile，加上 `sameInputs` 中六个直接
   reference：`sourceInventoryRef`、`verifiedSnapshotRef`、`applicationProfileRef`、
   `capabilityReportRef`、`entryPointsRef`、`mapperCatalogRef`；不能缺、多或以 receipt/root 代替；
4. receipt、envelope 与 `sameInputs.source.controls` 的五项 controls 逐字段相同，payload 的
   `graphProfileRef` 等于 `expectedGraphProfileRef` 且它就是 upstream 中的 graph profile；
5. canonical envelope 的 `payload` 按 exact schema 解析成 `CodeStructureGraphDraft`，拒绝未知、
   缺失或默认补出的字段；draft 的 snapshotId、applicationProfileId、entryIds 分别精确等于
   `sameInputs` 的 snapshot、application profile 与完整排序 entry denominator。

成功后 reader 才创建不可由 caller 实现或构造的 sealed aggregate；`basis` 是上述已验证值的
不可变投影，不是第二份 wire。任何 address/key、receipt、schema/type、lineage、controls、profile
或 payload identity 不一致都统一以 `GRAPH_REFERENCE_BROKEN` 失败，不返回 raw draft、partial
aggregate 或 Graph Gap。reader 和 M2 execution 均不接受 caller `Path`、raw source、自由 JSON、
detached entry/Mapper lists，也不扫描 worktree。M2 的 `CallGraphProfile.graphProfileRef` 同时就是
这里的 `expectedGraphProfileRef`，因为 8.0.1 已要求 M1–M5 逐字复用同一 graph profile。

M2 自己的 module publication 也必须先经唯一的 `PersistedCallGraphReader` fresh reopen，M3 才能读取
调用关系；该 reader 不是 builder、module 或新 artifact：

```text
PersistedCallGraphReader.reopen(
  CallGraphDraftReference reference,
  ReopenedProgramGraphInputs sameInputs,
  ReopenedCodeStructureGraph sameStructure,
  ArtifactReference expectedGraphProfileRef)
    -> ReopenedCallGraph

ReopenedCallGraph                              // sealed opaque immutable aggregate
  reference: CallGraphDraftReference
  payloadRef: ArtifactReference
  draft: CallGraphDraft
  codeStructurePayloadRef: ArtifactReference
  basis: ProgramGraphInputBasis
```

reader 先调用 `CanonicalModuleArtifactStore.reopen(reference.publication)`，再要求 address 精确为同一
run 的`program-graphs / 2 / call-graph`、completion 合法、publication 只有
`call-graph-draft.json`，type/schema 精确为
`PROGRAM_GRAPHS_CALL_GRAPH_DRAFT / program-graphs-call-graph-draft-v2`。receipt 与 envelope 的
upstream 必须恰为八项：`sameStructure.payloadRef`、`expectedGraphProfileRef`及`sameInputs`的六个
direct references；controls 必须等于`sameInputs.source.controls`。payload 以exact schema解析，
`graphKind=CALL`，其 snapshot/application profile/排序entry denominator/profile逐字段等于
`sameStructure.basis`，外部endpoint只能命中`sameStructure.draft`，call/return、provenance与coverage
引用必须闭合。任一address、receipt、payload、八项lineage、controls、profile、basis或endpoint不一致
统一以`GRAPH_REFERENCE_BROKEN`失败，不返回raw draft、partial aggregate或Graph Gap。

M3 execution 必须使用同一次`sameInputs`先重开 M1，再以上述 reader 重开 M2，最后构造只含
`ReopenedCodeStructureGraph + ReopenedCallGraph + ReopenedProgramGraphInputs`的immutable
`ControlFlowInputs`；M3开始解析前重算`ProgramGraphInputBasis`，要求两个sealed aggregate的basis、
M2保存的`codeStructurePayloadRef`、M1的`payloadRef`和`ControlFlowGraphProfile.graphProfileRef`
全部一致。caller不得传入raw `CallGraphDraft`、detached entry/call lists、`Path`、自由source string或
自行实现的aggregate。

#### M1 CodeStructureGraphBuilder

- **解决的问题**：给后续关系一个唯一的声明/包含/SQL 结构坐标系，避免按文件名或 simple name 找对象。
- **精确上游输入及前置**：valid VerifiedSourceInventoryReference、ApplicationDiscovery application/entry/catalog artifacts、CODE_STRUCTURE registry/profile/budget；所有 source handles 与 parser identities 已重验。
- **确定性顺序 / LLM**：解析 verified Java/config/XML/SQL → 建 package/type/field/method/parameter/annotation/configuration/XML namespace/statement/table/column nodes → 为每个 admitted node/edge 写精确 provenance draft → 建 CONTAINS/DECLARES/config/SQL edges → account；0 LLM。
- **目标输出与 DepotHead 示例**：`ProgramGraphDraft{graphKind=CODE_STRUCTURE,nodes,edges,provenanceDrafts,coverage}`；例子含 Controller/Service/Mapper methods、`DepotHead.status`、XML statement、`jsh_depot_head.status` nodes。
- **必须保持的不变量**：declaration node identity 绑定 snapshot/kind/canonical symbol/source/rule；同名不同 FQN/statement 不合并；每个 candidate 恰一 disposition。
- **Gap / fatal / artifact复用**：局部 unsupported syntax 是 Gap；duplicate ID、broken containment、parser/source/security/accounting 错误 fatal；模块只从相同VerifiedSourceInventory与ApplicationDiscovery roots构建draft。
- **给下游的后置保证**：M2–M5 获得稳定 endpoint IDs 和可重新验证的 source-span provenance drafts；分析步骤“已证明代码事实” 可引用结构节点而无需再解析声明。
- **明确非目标**：不绑定调用、不生成 CFG/data-flow、不证明 Fact。
- **公共测试 seam 与验收**：`buildStructure(source, discovery, profile)` 覆盖 DepotHead FQN/method/property/table/column、同名 decoy、XML include/statement、每个 provenance draft 的 locator/file/excerpt digest以及 root-order determinism；所有 expected nodes/edges/provenance/coverage 必须闭合。
- **Luna/xhigh 测试指南**：创建 `CodeStructureGraphBuilderTest`，冻结VerifiedSourceInventory与ApplicationDiscovery artifacts、DepotHead bytes和独立node/edge/provenance golden于 `src/test/resources/analysis/graph/code-structure/`。逐RED：完整结构与provenance、同名FQN隔离、XML statement/table/column、配置key→resource、unsupported Gap、broken containment/provenance fatal、root/order determinism；首RED因public builder/schema缺失失败。只fake source handle，parser/canonical/identity不可mock。命令：`mvn -Dtest=CodeStructureGraphBuilderTest test`；禁网络/客户执行。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `analysis/graph/code-structure/`，实现 public `CodeStructureGraphBuilder/CodeStructureGraphDraft` 与 `program-graphs-code-structure-draft-v2`；只读VerifiedSourceInventory与ApplicationDiscovery artifacts，parse→nodes→provenance drafts→edges→coverage。逐slice GREEN，输出IDs/golden稳定；不得合并同名、产生其他图或改registry。缺schema/upstream时STOP交Sol/ultra，完成更新审计。

#### M2 CallGraphBuilder

- **解决的问题**：唯一确定调用 target、call/return pair 和 Mapper Java→XML statement binding。
- **精确上游输入及前置**：`CallGraphInputs{structure: ReopenedCodeStructureGraph, reopened: the same ReopenedProgramGraphInputs}`与`CallGraphProfile`（CALL registry/profile/budget）；M2 execution 必须先用上段 `PersistedCodeStructureGraphReader` 从 M1 reference 得到 sealed structure aggregate，禁止 caller 把 raw `CodeStructureGraphDraft` 塞入 input。M2只可解析`reopened.source`中的verified call-site bytes，只可把`reopened.discovery.entries/mapperCatalog`作为Stage 2候选；builder 开始解析前必须重算 `ProgramGraphInputBasis`，要求它与 `structure.basis` 以及 `CallGraphProfile.graphProfileRef` 逐字段相同。receiver/type/method candidate refs 均存在。`CallGraphInputs`是immutable in-process builder input，不是新wire/module artifact，不含worktree/repository `Path`、自由source string或与该reopen脱离的entries/catalog列表。
- **确定性顺序 / LLM**：枚举 call sites → 求 receiver static type → 解析 method signature/overload → 建 direct target → 配对 call/return → 按 namespace/signature 绑定 Mapper statement；0 LLM。
- **目标输出与 DepotHead 示例**：CALL graph draft；例子有 Controller :185→Service :742、Service :803→Mapper Java :23、Mapper Java→XML :385 三段 exact edges。
- **必须保持的不变量**：每个 admitted call/binding target 唯一；edge 保存 exact endpoints/rule/resolution/evidence draft；simple name/文本相似不是 tie-breaker。
- **Gap / fatal / artifact复用**：可定位的 ambiguous/unsupported call 为 affected-entry Gap；若 Stage 2 已确认 Java interface 与 XML namespace 是同一 Mapper、但缺少被调用的方法 candidate，则写`MAPPER_JAVA_METHOD_UNRESOLVED` Gap，绝不静默省略 binding。没有匹配 Mapper catalog 的普通 Java interface 不被臆断为 Mapper。两个 exact target、broken endpoints、pair/reference/accounting 错误 fatal；只接受相同roots/profile。
- **给下游的后置保证**：M3/M4/BusinessFlows 能沿明确 call/return，不需动态 dispatch 猜测；ProvenCodeFacts 可把 call edge 放入 Proof。
- **明确非目标**：不以调用顺序代替 CFG，不推值流，不把 unresolved candidate 任选一个。
- **公共测试 seam 与验收**：`buildCalls(CallGraphInputs inputs, CallGraphProfile profile)` 对三段 DepotHead chain 做逐段 deletion/decoy/overload mutation；fixture必须先把真实 M1 module publication 安装到 canonical store，再由 `PersistedCodeStructureGraphReader` 与同一次`ReopenedProgramGraphInputs`生成唯一 sealed inputs。raw draft constructor、错 M1 address/key、receipt SHA、schema/type、六项 lineage、controls、profile、snapshot/application/entry denominator 任一 mutation 都必须在 builder 解析源码前以 `GRAPH_REFERENCE_BROKEN` 失败；删除/替换call-site bytes或脱离Stage 2的entry/catalog candidate不得由路径、自由字符串搜索或M1 display value补回；只有唯一 signature/namespace binding 时产生 EXACT edge。
- **Luna/xhigh 测试指南**：创建 `CallGraphBuilderTest`，fixtures/goldens在 `src/test/resources/analysis/graph/call-graph/`。先用真实 canonical module store 建 reader/execution RED：正确 M1 fresh reopen，以及 address/receipt/schema/type/六 refs/controls/profile/snapshot/application/entry mutation fail-closed；不能新增平行 selector 或允许测试直接构造 sealed aggregate。随后一个RED一个三段binding：Controller→Service、Service→Mapper、Mapper→XML；再做overload/decoy Gap、双exact fatal、call-return mutation、determinism。golden不由production生成；禁止mock store receipt验证、resolution/canonical。命令：`mvn -Dtest=CallGraphBuilderTest test`；无网络/runtime。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `analysis/graph/call-graph/`，实现 `PersistedCodeStructureGraphReader`、sealed `ReopenedCodeStructureGraph`、M2 execution，以及 public `CallGraphBuilder/CallGraphInputs/CallGraphProfile/CallGraphDraft` 与 `program-graphs-call-graph-draft-v2`。execution先用同一`ReopenedProgramGraphInputs`和`CallGraphProfile.graphProfileRef` fresh-reopen M1 module，验证 address/receipt/exact payload/六 refs/controls与全部payload identity，严格解析draft并构造opaque aggregate；再只以`inputs.structure.draft`解析endpoint、以`inputs.reopened.source`解析call site、以`inputs.reopened.discovery.entries/mapperCatalog`解析Stage 2候选，按receiver type→signature→target→return pair→Mapper binding→provenance drafts。逐edge GREEN且decoy不命中；不得暴露可伪造aggregate实现、接受raw draft/Path/detached lists/free source string、扫描worktree、字符串fallback或改M1。跨analysis step/data缺失MUST STOP交Sol/ultra，更新审计。

#### M3 ControlFlowGraphBuilder

- **解决的问题**：表达每个 entry 可达步骤、guard polarity、call/return 与 terminal，防止用源码行序讲流程。
- **精确上游输入及前置**：`ControlFlowInputs{structure: ReopenedCodeStructureGraph, calls: ReopenedCallGraph, reopened: the same ReopenedProgramGraphInputs}`与`ControlFlowGraphProfile`；M3 execution必须按8.0先fresh-reopen M1/M2，校验同一basis、M1 payload ref与graph profile，再从`reopened.discovery.entries`读取entry roots、从`reopened.source`读取verified method bodies。raw `CodeStructureGraphDraft`、raw `CallGraphDraft`和detached entry/call lists均禁止；entry/call endpoints全部有效。
- **确定性顺序 / LLM**：每 entry 建 ENTRY → basic blocks/guards → TRUE/FALSE/NEXT/CALL/RETURN → return/throw/profile stop terminals → reachability/accounting；0 LLM。
- **目标输出与 DepotHead 示例**：CONTROL_FLOW draft；例子保留 Service :752-796 的 status/库存条件 polarity、`:798-803` 非空 dhIds 写入分支与 :821 return terminal。
- **必须保持的不变量**：每个 guard 的 outgoing polarity 显式且合法；每个可达 path 终止、Gap 或 reasoned exclusion；call/return refs 与 M2 一致。
- **Gap / fatal / artifact复用**：bounded loop/unsupported construct 可形成 Gap；缺 polarity/terminal、悬空 block、call pair mismatch 或覆盖不闭合 fatal；只能从已验证前驱重建完整CFG draft。
- **给下游的后置保证**：BusinessFlows 可按 entry root 枚举 Outcomes；ProvenCodeFacts 可证明条件 atom，无需从行号推路径。
- **明确非目标**：不执行代码、不假定异常处理器/事务运行时、不命名业务 Outcome。
- **公共测试 seam 与验收**：`buildControlFlow(ControlFlowInputs inputs, ControlFlowGraphProfile profile)` 覆盖 TRUE/FALSE swap、terminal deletion、loop budget、call/return mutation；DepotHead 每个目标 guard 与 terminal 必须可达且 polarity 稳定。
- **Luna/xhigh 测试指南**：创建 `ControlFlowGraphBuilderTest`，fixtures/goldens置 `src/test/resources/analysis/graph/control-flow/`。RED顺序：entry/guards/terminals正向、TRUE/FALSE swap、terminal deletion、call-return mismatch、loop budget Gap、order determinism；首RED应因CFG seam缺失。只fake verified method reader，禁止mock CFG/accounting。命令：`mvn -Dtest=ControlFlowGraphBuilderTest test`；禁客户运行/网络。偏离按DESIGN 13.11交`gpt-5.6-sol / ultra` Design Authority。
- **Terra/xhigh 实现指南**：RED后只改 `analysis/graph/control-flow/`，实现 public `ControlFlowGraphBuilder/ControlFlowGraphDraft` 与 `program-graphs-control-flow-draft-v2`；M1/M2 artifacts→blocks/guards→typed edges→terminals→provenance drafts→coverage。每个RED独立GREEN；禁止行号补flow/运行代码。必要异常语义不在上游即STOP 13.11，审计同步。

M3 的唯一 public Java seam 与 exact JSON payload 采用下列最小 records；`ControlFlowInputs`和
`ControlFlowGraphProfile`只存在于进程内，不是新 wire/artifact。Java `ArtifactId`在 JSON 中是单个
string，enum 使用下列大写值；所有字段和数组都 required，只有标成`?`的两个 JSON key required-
nullable，unknown/missing/defaulted field 一律拒绝。

```text
ControlFlowGraphBuilder.buildControlFlow(ControlFlowInputs inputs,
                                         ControlFlowGraphProfile profile)
  -> ControlFlowGraphDraft

record ControlFlowInputs(
  ReopenedCodeStructureGraph structure,
  ReopenedCallGraph calls,
  ReopenedProgramGraphInputs reopened)

record ControlFlowGraphProfile(ArtifactReference graphProfileRef)

record ControlFlowGraphDraft(
  String schemaVersion,                    // exactly program-graphs-control-flow-draft-v2
  ProgramGraphKind graphKind,              // exactly CONTROL_FLOW
  ArtifactId graphId,
  String snapshotId,
  ArtifactId applicationProfileId,
  ArtifactReference graphProfileRef,
  List<ArtifactId> entryIds,
  List<ControlFlowNode> nodes,
  List<ControlFlowEdge> edges,
  List<ControlFlowTraversal> semanticTraversalOrder,
  List<ProvenanceDraftV1> provenanceDrafts,
  GraphCoverage coverage)

record ControlFlowNode(
  ArtifactId nodeId,
  ControlFlowNodeKind kind,
  String canonicalValue,
  List<ArtifactId> owningEntryIds,
  List<ArtifactId> evidenceDraftRefs)
enum ControlFlowNodeKind {
  ENTRY, BASIC_BLOCK, GUARD, RETURN_TERMINAL, THROW_TERMINAL, PROFILE_STOP_TERMINAL
}

record ControlFlowEdge(
  ArtifactId edgeId,
  ControlFlowEdgeKind kind,
  ArtifactId fromNodeId,
  ArtifactId toNodeId,
  String ruleId,
  ProgramResolution resolution,            // exactly EXACT
  ArtifactId guardNodeId,                   // nullable Java value; JSON key required
  ControlFlowPolarity polarity,             // nullable Java value; JSON key required
  List<ArtifactId> evidenceDraftRefs)
enum ControlFlowEdgeKind { NEXT, TRUE, FALSE, CALL, RETURN }
enum ControlFlowPolarity { TRUE, FALSE }

record ControlFlowTraversal(ArtifactId entryId, List<ArtifactId> nodeIds)
```

三个 input 都 non-null immutable，其中两个 predecessor 是不可伪造的 sealed aggregates；builder 读源码前重算
`ProgramGraphInputBasis(reopened, profile.graphProfileRef)`，要求它逐字段等于`structure.basis`和
`calls.basis`，并要求`calls.codeStructurePayloadRef == structure.payloadRef`。profile 只含完整
`graph-profile:{sha256}` reference；rule registry和budget由该已验证 artifact 决定，不接受 caller
复制的自由 rule/budget 字段。任一不一致以`GRAPH_REFERENCE_BROKEN`失败，不返回 partial draft。

所有 Java `List`在 constructor defensive-copy；`entryIds`、nodes、edges、provenance分别按 ID
排序且按ID distinct。`semanticTraversalOrder`按`entryId`排序，但每个
`nodeIds`保持 canonical interprocedural DFS preorder：从该 entry 唯一的`ENTRY`开始，首次访问一个
node时写一次，guard先`TRUE`后`FALSE`，其余同类edge按`edgeId`。每个reachable M2 call pair在M3中
恰投影一次：`CALL`逐字段复用`CALL_TARGET | JAVA_METHOD_TO_XML_STATEMENT`的from/to/rule/resolution，
`RETURN`逐字段复用其唯一反向`CALL_RETURN`，两者`guardNodeId/polarity=null`且各自edgeId绑定M2 edgeId。
两者的`evidenceDraftRefs`与M2 edge逐字相同，M3 registry重列相同ID和内容的provenance draft。
local predecessor以`NEXT`进入external M2 call-site，call-site以唯一`NEXT`指向continuation；walker先沿
`CALL`进入target，Java method target以`NEXT`进入callee首个control node，XML statement是leaf。
normal callee terminal或leaf验证栈顶`RETURN`后恢复call-site continuation；`RETURN` link不作为普通
successor单独遍历，throw/profile-stop不恢复caller。缺失、额外或错配pair均 fatal。

每个 traversal 可包含上述已验证的 M1/M2 external endpoint，但它们不在 M3 `nodes`中重复声明；每个
entry恰一条traversal且不得重复或漏掉reachable node。每个M3-owned node必须出现在且只出现在其全部
`owningEntryIds`对应的traversal；每个M3 edge必须被至少一个entry的walker命中（投影RETURN以其call-site
可达计），因此unreachable owned subgraph一律 fatal。node的`owningEntryIds`必须非空、排序、distinct
且为draft `entryIds`子集；每个edge endpoint必须解析到本draft node或已验证M1/M2 external node。

M3-owned node/edge 的ID遵守8.4；`CALL/RETURN edgeId`的direct preimage还绑定对应M2 edgeId，M3
`graphId`除8.4字段外还绑定完整`semanticTraversalOrder`。`coverage.exactElementIds`必须恰等于全部
M3-owned nodeId和edgeId；candidate denominator再与exact/gap/exclusion互斥闭合。每个node/edge的
非空`evidenceDraftRefs`只引用本draft唯一、按ID排序的`provenanceDrafts`，registry无悬空、无孤儿；
traversal中的external node不重复计入M3 coverage。每个`PROFILE_STOP_TERMINAL`另以
`identity("control-flow-profile-stop-successor-v1", nodeId)`确定唯一stopped-successor candidate ID，
该candidate必须恰落入一个gap或exclusion disposition；profile-stop node自身仍是exact element。

basic Java 行为严格固定：每个 entry 恰有一个无入边`ENTRY`并以唯一`NEXT`进入handler；
`BASIC_BLOCK`是被call/branch/terminal边界切开的最大连续语句段；每个`if`条件产生一个`GUARD`，
且恰有一条`TRUE`和一条`FALSE`出边，二者`guardNodeId`均为自身、polarity与kind相同，无`else`时
FALSE指向lexical successor，若method已结束则指向正常fall-through terminal。显式`return`和正常fall-through分别形成由语句或method closing token
提供source provenance的
`RETURN_TERMINAL`，显式`throw`形成`THROW_TERMINAL`；terminal出度必须为0，throw不猜catch、事务或
runtime handler；callee正常terminal通过上述walker stack恢复而不增加出边。unsupported/over-limit路径只能以`PROFILE_STOP_TERMINAL`加恰一个对应Gap/exclusion闭合，
不得按行号补边；任何reachable nonterminal缺合法后继、terminal有出边或guard polarity不完整均fatal。

#### M4 DataFlowGraphBuilder

- **解决的问题**：逐段证明请求值怎样跨参数、变量、property、criteria、placeholder 到 SQL column/where。
- **精确上游输入及前置**：M1 structure、M2 calls、M3 CFG、verified Java/XML bytes、DATA_FLOW registry/profile/worklist budget；所有定义/使用/call endpoints 已存在。
- **确定性顺序 / LLM**：建 intra-method def-use → argument→parameter → assignment/setter/property → Mapper record/example params → XML dynamic condition/include → placeholder/criterion→column/where → fixed-point/account；0 LLM。
- **目标输出与 DepotHead 示例**：DATA_FLOW draft；例子分别闭合 `Controller.status→Service.status→setStatus→record.status→jsh_depot_head.status` 与 `ids→dhIds→andIdIn→WHERE id IN`。
- **必须保持的不变量**：每条长链由相邻 typed edges 组成；每 edge 有唯一 rule/endpoints/guard context；不跨 unresolved call/alias/XML path。
- **Gap / fatal / artifact复用**：局部 alias/dynamic SQL/loop 超能力为 Gap；伪 exact binding、broken endpoint、worklist/accounting/identity 错误 fatal；只接受相同roots/rules。
- **给下游的后置保证**：ProvenCodeFacts 能逐 atom 引用 exact value/where edge；BusinessFlows 能保留数据相关条件，不需字符串匹配。
- **明确非目标**：不证明运行时 DB/trigger 值，不用字段同名跨层连边，不硬编码 DepotHead positive edge。
- **公共测试 seam 与验收**：`buildDataFlow(structure, calls, cfg)` 对两条 DepotHead chain 每段做 deletion/decoy/property/placeholder mutation；任一缺段使对应 chain 不闭合。
- **Luna/xhigh 测试指南**：创建 `DataFlowGraphBuilderTest`，冻结两条DepotHead链及逐段mutation于 `src/test/resources/analysis/graph/data-flow/`。依次RED：intra-def/use、argument/parameter、setter/property、record/placeholder/column、ids/criterion/where、alias Gap、worklist fatal、determinism；每个RED应只因该edge/rule缺失。只fakeartifact/source reader，dataflow/canonical不能mock。命令：`mvn -Dtest=DataFlowGraphBuilderTest test`；禁网络/客户MyBatis。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `analysis/graph/data-flow/`，实现 public `DataFlowGraphBuilder/DataFlowGraphDraft` 与 `program-graphs-data-flow-draft-v2`；只读M1–M3，按局部→跨调用→property→XML fixed-point，逐段保存rule/guard/provenance drafts。每段GREEN后跑同selector；禁止字段同名捷径/DepotHead硬编码。上游缺endpoint或需新edge语义MUST STOP交Sol/ultra/必要时用户。

#### M5 EvidenceGraphBuilder

- **解决的问题**：说明每个程序 node/edge 从哪些固定 bytes 和版本化 parser/binding rule 得出。
- **精确上游输入及前置**：M1–M4 drafts 及每个 draft element 的 provenance tokens、VerifiedSourceInventory inventory/source handles、EVIDENCE registry/profile/budget；所有 program IDs 已固定。
- **确定性顺序 / LLM**：canonicalize file/span locators → 重验 file/span SHA → 建 rule-application nodes → 建 LOCATES/PARSED_BY/BOUND_BY/SUPPORTS edges → 覆盖检查；0 LLM。
- **目标输出与 DepotHead 示例**：EVIDENCE draft；例子把 status placeholder edge 回到 Mapper XML :472-473、绑定 rule 和 file/span SHA，把 route 回到 :43 与 :178-191。
- **必须保持的不变量**：每个 admitted program node/edge 至少一个闭合 provenance path；Evidence identity 不含绝对 root；Evidence 不是 Fact/Proof。
- **Gap / fatal / artifact复用**：局部 program Gap 可有 gap provenance；admitted element 缺 evidence、span/source drift、rule unknown、coverage/reference broken fatal；模块fresh-reopen source artifacts构建。
- **给下游的后置保证**：ProvenCodeFacts 可从 program edge 稳定走到 source/rule，并独立构造 atom Proof。
- **明确非目标**：不决定业务事实、不把 locator 正确等同于语义正确、不为缺边制造 evidence。
- **公共测试 seam 与验收**：`buildEvidence(programDrafts, source)` 对 span/rule/file SHA/endpoint omission/substitution mutation fail closed；不同 root 产生同 evidence IDs/bytes。
- **Luna/xhigh 测试指南**：创建 `EvidenceGraphBuilderTest`，fixtures/goldens在 `src/test/resources/analysis/graph/evidence-graph/`。逐RED：route与status edge provenance、span/hash/rule omission、endpoint substitution、admitted-edge无evidence fatal、different-root determinism；首RED因seam缺失。只fakeSourceHandle重开，禁止mockhash/coverage/canonical。命令：`mvn -Dtest=EvidenceGraphBuilderTest test`；无网络。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `analysis/graph/evidence-graph/`，实现 public `EvidenceGraphBuilder/EvidenceGraphDraft` 与 `program-graphs-evidence-graph-draft-v2`；消费M1–M4 provenance artifacts，typed `SourceExcerptV1`→reopen→rule nodes→support edges→coverage。逐RED GREEN；不得把Evidence当Proof、制造缺边或用`canonicalValue`/string locator代替source bytes。schema或source identity不足时STOP交Sol/ultra，更新审计。

#### M6 ProgramGraphSetPublicationSpecifier

- **解决的问题**：把五个drafts作为不可拆分的一等图集合校验并形成七个semantic payload；由analysis step store独占root与receipt计算并给ProvenCodeFacts和BusinessFlows一个稳定引用。
- **精确上游输入及前置**：M1–M5完整drafts、graph gaps、VerifiedSourceInventory与ApplicationDiscovery roots和ProgramGraphs controls；五种graphKind恰各一份且局部validation已通过。
- **确定性顺序 / LLM**：跨图 endpoint/ownership/accounting → canonical五图/index/gaps → M6 exact-seven module install/receipt → analysis step store fresh reopen/root/receipt-last；0 LLM。
- **目标输出与 DepotHead 示例**：M6恰七个semantic files；analysis step store形成七项+receipt的八文件reader-visible set。未来正例 index 指向含 8.3 八段闭包的五图，当前 Gap 也保留五个 envelope 和 `graph-gaps.jsonl`。
- **必须保持的不变量**：五图恰一次且 schema/profile/snapshot 一致；外部 endpoint 全存在；M6不得包含或预报analysis step root/receipt；analysis step set 原子安装并可按identity显式复用，specifier不改边。
- **Gap / fatal / artifact复用**：builder 的局部 Gap 原样入账；缺图、多图、cross-ref、root/canonical/install/collision 错误 fatal；下游不能选四张引用。
- **给下游的后置保证**：ProvenCodeFacts和BusinessFlows只凭ProgramGraphsReference获得完整五图、index、Gap/accounting，不需源码parser。
- **明确非目标**：不补 graph edge、不证明 Fact、不编译 Flow、不调用模型。
- **公共测试 seam 与验收**：`specifyGraphSet(fiveDrafts, gaps, controls)` 覆盖缺/多/交换 graph、cross-ref mutation、乱序、partial-install/collision；只有M6 exact-seven和analysis-step-store eight-file coherent set才返回 ProgramGraphsReference。
- **Luna/xhigh 测试指南**：创建 `ProgramGraphsPublicationSpecifierTest`，五module artifacts/golden set放 `src/test/resources/analysis/graph/publish/`。RED顺序：M6 exact-seven、analysis step exact-eight、缺/多/交换graph、cross-ref/ID-set spoof、Gap accounting、乱序、receipt-last/partial-install/collision/fresh-reopen；使用真实module/analysis step stores，禁止mockcross-validator/canonical/root。命令：`mvn -Dtest=ProgramGraphsPublicationSpecifierTest test`；禁网络/build。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `analysis/graph/publish/`，实现 public `ProgramGraphSetPublicationSpecifier/ProgramGraphsReference`；只读M1–M5 files，cross-ref→semantic files→M6 install/receipt→typed analysis-step-store receipt-last。不得生成旧single publication summary、预报root/receipt、补边或少图。跨分析步骤变更MUST STOP交Sol/ultra并按DESIGN升级用户。

### 8.0.1 模块 artifact wire schemas

M1–M5使用 DESIGN 13.3 `ModuleArtifact<T>` envelope并采用8.1 ProgramGraph records；M6直接安装七个analysis step schema注册的JSON/JSONL semantic bytes而无summary envelope。`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-code-structure/code-structure-draft.json` | `program-graphs-code-structure-draft-v2` / `PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT` | exact `graph-profile`；VerifiedSourceInventory `source-inventory/verified-snapshot`；ApplicationDiscovery `application-profile/capability-report/entry-points/mapper-catalog` ArtifactReferences | `graphKind=CODE_STRUCTURE!`、`graphId!`、`snapshotId!`、`applicationProfileId!`、`graphProfileRef!`、`entryIds[]!`、`nodes[]!`、`edges[]!`、`provenanceDrafts[]!`、`coverage!`；nodes/edges/provenance按ID |
| `modules/02-call-graph/call-graph-draft.json` | `program-graphs-call-graph-draft-v2` / `PROGRAM_GRAPHS_CALL_GRAPH_DRAFT` | exact M1、`graph-profile`、VerifiedSourceInventory两项、ApplicationDiscovery `entry-points/mapper-catalog` ArtifactReferences | M1同形payload且`graphKind=CALL!`；每个CALL_TARGET/JAVA_METHOD_TO_XML_STATEMENT有唯一`CALL_RETURN` pair，edge保留resolution/rule/evidenceDraftRefs并按edgeId |
| `modules/03-control-flow/control-flow-draft.json` | `program-graphs-control-flow-draft-v2` / `PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT` | exact M1/M2、`graph-profile`、VerifiedSourceInventory两项、ApplicationDiscovery `entry-points` ArtifactReferences | M1同形payload且`graphKind=CONTROL_FLOW!`；nodes/edges/provenance按ID，每entry的`semanticTraversalOrder[]!`按canonical DFS顺序；每guard的TRUE/FALSE或typed terminal/Gap disposition必备 |
| `modules/04-data-flow/data-flow-draft.json` | `program-graphs-data-flow-draft-v2` / `PROGRAM_GRAPHS_DATA_FLOW_DRAFT` | exact M1/M2/M3、`graph-profile`、VerifiedSourceInventory `source-inventory/verified-snapshot` ArtifactReferences | M1同形payload且`graphKind=DATA_FLOW!`；nodes/edges/provenance按ID，typed adjacent edges按edgeId，跨图endpoint只引用global graph index；`worklistAccounting!{enqueued!,processed!,overLimit!}` |
| `modules/05-evidence-graph/evidence-graph-draft.json` | `program-graphs-evidence-graph-draft-v2` / `PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT` | exact M1–M4、`graph-profile`、VerifiedSourceInventory `source-inventory/verified-snapshot` ArtifactReferences | `graphKind=EVIDENCE!`及M1共通identity字段；`nodes[]!`为下述`EvidenceNodeV2`且按evidenceNodeId，`edges[]!`使用下述EvidenceEdge并按ID；`coverage!`逐program element闭合 |
| `modules/06-publish/<seven registered semantic filenames>` | 各public schema/type；无summary envelope | M1–M5 IDs/SHAs | 一次module install恰五图+`graph-index.json`+`graph-gaps.jsonl`；module receipt绑定七descriptors；禁止analysis step root/receipt或八项published list；AnalysisStep store provenance绑定M6 reference |

M2 不能把上表 M1 行理解为“持有一个 Java draft 就等于依赖 M1”。M1→M2 handoff 必须使用
`CodeStructureGraphDraftReference` 经 canonical store 与 `PersistedCodeStructureGraphReader` fresh
reopen；store 验证 module publication，domain reader 再验证该行的 exact address、单 payload
descriptor/envelope、receipt、upstream、controls 和 payload identity，最后才产生
`ReopenedCodeStructureGraph`。M2 module 的 own upstream 仍按上表 M2 行保存；这个 in-process
aggregate 不新增 schema、artifact 或第 53 项 reader-visible 文件。

`graphProfileRef`是完整content-addressed `ArtifactReference{artifactId,sha256}`，固定prefix=`graph-profile`；它不是自由字符串或仅语法合法的profile key。M1–M5必须逐字使用同一个ref，将其列入`upstreamArtifacts`，并在每个graph payload中重复该typed ref；graphId identity绑定两个字段且fresh reopen验证profile bytes。M6完整module fixture用一次install request绑定M1–M5五个draft ArtifactReferences，七个standalone payload不重复envelope。

M1–M4 draft 的 `DraftProgramNode` 字段为`nodeId/kind/canonicalValue/owningEntryIds/evidenceDraftRefs`，`DraftProgramEdge`为`edgeId/kind/fromNodeId/toNodeId/ruleId/resolution/guardNodeId?/polarity?/evidenceDraftRefs`，全部required，仅guard/polarity可在不适用时null。每一个draft另有一份同一module内的`provenanceDrafts[]!` registry；`evidenceDraftRefs`只能引用其中的ID，所有被引用ID必须恰有一条entry，且每条entry至少被一个node或edge引用。`ProvenanceDraftV1`固定字段为`provenanceDraftId! / ruleId! / sourceLocator! / sourceFileSha256! / excerptSha256!`：locator逐字段采用`SourceLocatorV1`，full-file SHA-256绑定冻结文件，excerpt SHA-256绑定该连续byte range。其identity由`ruleId + fileId + sourceFileSha256 + locator byte range + excerptSha256`分帧计算；不得使用绝对路径、显示名、顺序号或source search结果。M5只可按此locator重新打开VerifiedSource bytes、重算两个digest后生成`SourceExcerptV1`和最终Evidence node；它不得重新解析语义结构或以字符串邻近回填span。`canonicalValue`只保存该program element的版本化语义值（FQN、route、symbol、operator等），不得保存`path:line`或冒充source evidence。nodeId在整个ProgramGraphs graph set内全局唯一；from/to可指本图node或M1–M4已发布draft中的node，后图不得重新声明前图node。`graph-index.json`保存`nodeId→owningGraphKind`并验证，不能用未登记external endpoint。

M1–M4 `coverage`固定为`candidateElementIds[]!/exactElementIds[]!/gapDispositions[]!{candidateElementId!,gapId!}/exclusionDispositions[]!{candidateElementId!,reasonCode!,evidenceRefs[]!}/scopeGapIds[]!/closed!`；四个ID/disposition数组按program element ID，candidate集合必须恰等于其余三个互斥分子的element ID union。M5改用`candidateProgramElementIds[]!/evidencedProgramElementIds[]!/gapDispositions[]!/exclusionDispositions[]!/scopeGapIds[]!/closed!`并要求candidate等于evidenced+gap+excluded。`scopeGapIds`解释仓库范围，不代替任何已发现element的处置；bounded示例因此`closed=false`。

M5不复用ProgramEdge字段冒充“evidence指向program edge”。`EvidenceNodeV2(evidenceNodeId,kind,sourceExcerpt,ruleApplication)`是closed union：`SOURCE_EXCERPT{sourceExcerpt!,ruleApplication=null}`或`RULE_APPLICATION{sourceExcerpt=null,ruleApplication!}`，两个required-nullable槽必须都出现且恰一非null。`sourceExcerpt`逐字段使用DESIGN §13.2 `SourceExcerptV1`；`RuleApplicationV2(ruleId,ruleVersion,inputProgramElementIds)`三项required，input IDs按UTF-8排序。一个SOURCE_EXCERPT只能表示同一文件的一个连续span；不连续证据必须拆为多个nodes，禁止`path:line`、` + `、`...`或合成raw。

`EvidenceEdge`固定为`edgeId/kind/evidenceNodeId/subjectGraphKind/subjectProgramElementId/ruleApplicationNodeId`，六项全required；`subjectProgramElementId`必须是M1–M4已登记nodeId或edgeId，`subjectGraphKind`必须匹配graph index owner，rule node必须存在于M5 nodes。M6发布public五图时，把M1–M4的`evidenceDraftRefs`确定性替换为排序非空`evidenceNodeIds`，并把M5 v2 nodes原样写入`program-graphs-evidence-graph-v2`；public `ProgramNode/ProgramEdge`不再含draft ref。unknown binding必须不生成edge并写Gap，不能把endpoint设为故事值。payload和envelope一起入artifactId。schema/registry/sort/source rule任何变化先设计+升version；下游禁止打开AST/XML补字段。

**示例分类：STRUCTURAL_WIRE_SPECIMEN（两个隔离的 EvidenceNodeV2 variants，不可replay）。** 字段、closed variant与continuous excerpt完整；offset/digest未从本页未展示的完整source file重算，不能作为golden或与相邻大块的story IDs映射。

~~~jsonl
{"evidenceNodeId":"evidence:1111111111111111111111111111111111111111111111111111111111111111","kind":"SOURCE_EXCERPT","sourceExcerpt":{"locator":{"fileId":"file:2222222222222222222222222222222222222222222222222222222222222222","path":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java","startByte":30100,"endByteExclusive":30128,"startLine":800,"startColumn":9,"endLine":800,"endColumn":37},"rawUtf8":"depotHead.setStatus(status);","rawUtf8Sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},"ruleApplication":null}
{"evidenceNodeId":"evidence:3333333333333333333333333333333333333333333333333333333333333333","kind":"RULE_APPLICATION","sourceExcerpt":null,"ruleApplication":{"ruleId":"java-setter-property-binding","ruleVersion":"v1","inputProgramElementIds":["parameter:service-status","property:depothead-status"]}}
~~~

**示例分类：NARRATIVE_ILLUSTRATION（五个ProgramGraphs模块的隔离故事投影）。** 下列大块只说明图之间的业务关系；它不是ModuleArtifact wire、schema-valid fixture或跨分析步骤replay链。尤其其中`canonicalValue`里的`File.java:line`、`source:*`和story IDs不是v2 source evidence，Luna fixtures必须改用上面的exact records并在自己的closure内重算identity；不存在隐式ID remap。

~~~jsonl
{"storyProjection":"code-structure","artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","producer":{"address":{"kind":"ANALYSIS_STEP","runId":"analysis-run:9999999999999999999999999999999999999999999999999999999999999999","analysisStepKey":"program-graphs","moduleNumber":1,"moduleKey":"code-structure"},"storyModuleVersion":"illustrative"},"upstreamArtifacts":[{"artifactId":"graph-profile:8888888888888888888888888888888888888888888888888888888888888888","sha256":"8888888888888888888888888888888888888888888888888888888888888888"},{"artifactId":"verified-source-inventory-source-inventory:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"application-discovery-application-profile:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"application-discovery-capability-report:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"artifactId":"application-discovery-entry-points:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"application-discovery-mapper-catalog:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"verified-snapshot:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null,"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"graphKind":"CODE_STRUCTURE","graphId":"graph:code-structure-depothead","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryIds":["entry:post-depothead-batch-set-status"],"nodes":[{"nodeId":"column:jsh-depot-head-status","kind":"SQL_COLUMN","canonicalValue":"jsh_depot_head.status","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-column"]},{"nodeId":"entry:post-depothead-batch-set-status","kind":"HTTP_ENTRY","canonicalValue":"POST /depotHead/batchSetStatus","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-route"]},{"nodeId":"method:controller-batch-set-status","kind":"METHOD","canonicalValue":"com.jsh.erp.controller.DepotHeadController#batchSetStatus","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-method"]},{"nodeId":"method:mapper-update-by-example","kind":"METHOD","canonicalValue":"com.jsh.erp.datasource.mappers.DepotHeadMapper#updateByExampleSelective","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:mapper-method"]},{"nodeId":"method:service-batch-set-status","kind":"METHOD","canonicalValue":"com.jsh.erp.service.DepotHeadService#batchSetStatus","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-method"]},{"nodeId":"property:depothead-status","kind":"PROPERTY","canonicalValue":"com.jsh.erp.datasource.entities.DepotHead.status","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:set-status"]},{"nodeId":"route:post-depothead-batch-set-status","kind":"HTTP_ROUTE","canonicalValue":"POST /depotHead/batchSetStatus","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-route"]},{"nodeId":"statement:update-by-example-selective","kind":"XML_STATEMENT","canonicalValue":"com.jsh.erp.datasource.mappers.DepotHeadMapper#updateByExampleSelective","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:xml-statement"]},{"nodeId":"table:jsh-depot-head","kind":"SQL_TABLE","canonicalValue":"jsh_depot_head","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:table-update"]},{"nodeId":"xml-guard:record-status-not-null","kind":"XML_GUARD","canonicalValue":"record.status != null","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-guard"]}],"edges":[{"edgeId":"structure-edge:controller-route","kind":"ROUTE_HANDLED_BY","fromNodeId":"route:post-depothead-batch-set-status","toNodeId":"method:controller-batch-set-status","ruleId":"spring-route-merge-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:controller-route"]},{"edgeId":"structure-edge:statement-table","kind":"STATEMENT_CONTAINS_SQL","fromNodeId":"statement:update-by-example-selective","toNodeId":"table:jsh-depot-head","ruleId":"sql-update-table-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:table-update"]},{"edgeId":"structure-edge:table-declares-status","kind":"DECLARES","fromNodeId":"table:jsh-depot-head","toNodeId":"column:jsh-depot-head-status","ruleId":"sql-column-declaration-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:status-column"]}],"coverage":{"candidateElementIds":["column:jsh-depot-head-status","entry:post-depothead-batch-set-status","method:controller-batch-set-status","method:mapper-update-by-example","method:service-batch-set-status","property:depothead-status","route:post-depothead-batch-set-status","statement:update-by-example-selective","structure-edge:controller-route","structure-edge:statement-table","structure-edge:table-declares-status","table:jsh-depot-head","xml-guard:record-status-not-null"],"exactElementIds":["column:jsh-depot-head-status","entry:post-depothead-batch-set-status","method:controller-batch-set-status","method:mapper-update-by-example","method:service-batch-set-status","property:depothead-status","route:post-depothead-batch-set-status","statement:update-by-example-selective","structure-edge:controller-route","structure-edge:statement-table","structure-edge:table-declares-status","table:jsh-depot-head","xml-guard:record-status-not-null"],"gapDispositions":[],"exclusionDispositions":[],"scopeGapIds":["gap:bounded-path-set"],"closed":false},"graphProfileRef":{"artifactId":"graph-profile:8888888888888888888888888888888888888888888888888888888888888888","sha256":"8888888888888888888888888888888888888888888888888888888888888888"}}}
{"storyProjection":"call-graph","artifactId":"graph-draft:2222222222222222222222222222222222222222222222222222222222222222","producer":{"address":{"kind":"ANALYSIS_STEP","runId":"analysis-run:9999999999999999999999999999999999999999999999999999999999999999","analysisStepKey":"program-graphs","moduleNumber":2,"moduleKey":"call-graph"},"storyModuleVersion":"illustrative"},"upstreamArtifacts":[{"artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"graph-profile:8888888888888888888888888888888888888888888888888888888888888888","sha256":"8888888888888888888888888888888888888888888888888888888888888888"},{"artifactId":"verified-source-inventory-source-inventory:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"application-discovery-entry-points:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"application-discovery-mapper-catalog:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"verified-snapshot:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null,"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"graphKind":"CALL","graphId":"graph:call-depothead","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryIds":["entry:post-depothead-batch-set-status"],"nodes":[{"nodeId":"call:controller-to-service","kind":"CALL_SITE","canonicalValue":"DepotHeadController.java:185","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-call"]},{"nodeId":"call:service-to-mapper","kind":"CALL_SITE","canonicalValue":"DepotHeadService.java:803","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:mapper-call"]}],"edges":[{"edgeId":"call-edge:controller-service","kind":"CALL_TARGET","fromNodeId":"call:controller-to-service","toNodeId":"method:service-batch-set-status","ruleId":"java-static-receiver-call-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:controller-call"]},{"edgeId":"call-edge:mapper-xml","kind":"JAVA_METHOD_TO_XML_STATEMENT","fromNodeId":"method:mapper-update-by-example","toNodeId":"statement:update-by-example-selective","ruleId":"mybatis-namespace-signature-binding-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:xml-statement"]},{"edgeId":"call-edge:service-mapper","kind":"CALL_TARGET","fromNodeId":"call:service-to-mapper","toNodeId":"method:mapper-update-by-example","ruleId":"java-static-receiver-call-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:mapper-call"]},{"edgeId":"call-return-edge:mapper-service","kind":"CALL_RETURN","fromNodeId":"method:mapper-update-by-example","toNodeId":"call:service-to-mapper","ruleId":"java-call-return-pair-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:mapper-call"]},{"edgeId":"call-return-edge:service-controller","kind":"CALL_RETURN","fromNodeId":"method:service-batch-set-status","toNodeId":"call:controller-to-service","ruleId":"java-call-return-pair-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:controller-call"]},{"edgeId":"call-return-edge:xml-mapper","kind":"CALL_RETURN","fromNodeId":"statement:update-by-example-selective","toNodeId":"method:mapper-update-by-example","ruleId":"mybatis-statement-return-pair-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:xml-statement"]}],"coverage":{"candidateElementIds":["call-edge:controller-service","call-edge:mapper-xml","call-edge:service-mapper","call-return-edge:mapper-service","call-return-edge:service-controller","call-return-edge:xml-mapper","call:controller-to-service","call:service-to-mapper"],"exactElementIds":["call-edge:controller-service","call-edge:mapper-xml","call-edge:service-mapper","call-return-edge:mapper-service","call-return-edge:service-controller","call-return-edge:xml-mapper","call:controller-to-service","call:service-to-mapper"],"gapDispositions":[],"exclusionDispositions":[],"scopeGapIds":["gap:bounded-path-set"],"closed":false},"graphProfileRef":{"artifactId":"graph-profile:8888888888888888888888888888888888888888888888888888888888888888","sha256":"8888888888888888888888888888888888888888888888888888888888888888"}}}
{"storyProjection":"control-flow","artifactId":"graph-draft:3333333333333333333333333333333333333333333333333333333333333333","producer":{"address":{"kind":"ANALYSIS_STEP","runId":"analysis-run:9999999999999999999999999999999999999999999999999999999999999999","analysisStepKey":"program-graphs","moduleNumber":3,"moduleKey":"control-flow"},"storyModuleVersion":"illustrative"},"upstreamArtifacts":[{"artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"graph-draft:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"graph-profile:8888888888888888888888888888888888888888888888888888888888888888","sha256":"8888888888888888888888888888888888888888888888888888888888888888"},{"artifactId":"verified-source-inventory-source-inventory:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"application-discovery-entry-points:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"verified-snapshot:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null,"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"graphKind":"CONTROL_FLOW","graphId":"graph:control-flow-depothead","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryIds":["entry:post-depothead-batch-set-status"],"nodes":[{"nodeId":"guard:dhids-not-empty","kind":"GUARD","canonicalValue":"dhIds is not empty","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:dhids-guard"]},{"nodeId":"step:set-status-and-update","kind":"STEP","canonicalValue":"set status and update by example","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:set-status"]},{"nodeId":"terminal:return-result","kind":"TERMINAL","canonicalValue":"return result","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:return"]}],"edges":[{"edgeId":"cfg-edge:guard-false-return","kind":"FALSE","fromNodeId":"guard:dhids-not-empty","toNodeId":"terminal:return-result","ruleId":"java-if-cfg-v1","resolution":"EXACT","guardNodeId":"guard:dhids-not-empty","polarity":"FALSE","evidenceDraftRefs":["provenance:dhids-guard"]},{"edgeId":"cfg-edge:guard-true-update","kind":"TRUE","fromNodeId":"guard:dhids-not-empty","toNodeId":"step:set-status-and-update","ruleId":"java-if-cfg-v1","resolution":"EXACT","guardNodeId":"guard:dhids-not-empty","polarity":"TRUE","evidenceDraftRefs":["provenance:dhids-guard"]},{"edgeId":"cfg-edge:update-return","kind":"NEXT","fromNodeId":"step:set-status-and-update","toNodeId":"terminal:return-result","ruleId":"java-sequence-cfg-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:return"]}],"semanticTraversalOrder":["guard:dhids-not-empty","step:set-status-and-update","terminal:return-result"],"coverage":{"candidateElementIds":["cfg-edge:guard-false-return","cfg-edge:guard-true-update","cfg-edge:update-return","guard:dhids-not-empty","step:set-status-and-update","terminal:return-result"],"exactElementIds":["cfg-edge:guard-false-return","cfg-edge:guard-true-update","cfg-edge:update-return","guard:dhids-not-empty","step:set-status-and-update","terminal:return-result"],"gapDispositions":[],"exclusionDispositions":[],"scopeGapIds":["gap:bounded-path-set"],"closed":false},"graphProfileRef":{"artifactId":"graph-profile:8888888888888888888888888888888888888888888888888888888888888888","sha256":"8888888888888888888888888888888888888888888888888888888888888888"}}}
{"storyProjection":"data-flow","artifactId":"graph-draft:4444444444444444444444444444444444444444444444444444444444444444","producer":{"address":{"kind":"ANALYSIS_STEP","runId":"analysis-run:9999999999999999999999999999999999999999999999999999999999999999","analysisStepKey":"program-graphs","moduleNumber":4,"moduleKey":"data-flow"},"storyModuleVersion":"illustrative"},"upstreamArtifacts":[{"artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"graph-draft:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"graph-draft:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"graph-profile:8888888888888888888888888888888888888888888888888888888888888888","sha256":"8888888888888888888888888888888888888888888888888888888888888888"},{"artifactId":"verified-source-inventory-source-inventory:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"verified-snapshot:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null,"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"graphKind":"DATA_FLOW","graphId":"graph:data-flow-depothead","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryIds":["entry:post-depothead-batch-set-status"],"nodes":[{"nodeId":"criterion:id-in-dhids","kind":"CRITERION","canonicalValue":"id IN dhIds","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:id-where"]},{"nodeId":"parameter:service-depot-head-ids","kind":"PARAMETER","canonicalValue":"DepotHeadService.batchSetStatus.depotHeadIDs","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-ids"]},{"nodeId":"parameter:service-status","kind":"PARAMETER","canonicalValue":"DepotHeadService.batchSetStatus.status","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-status"]},{"nodeId":"placeholder:record-status","kind":"PLACEHOLDER","canonicalValue":"record.status","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-placeholder"]},{"nodeId":"value:eligible-dhids","kind":"DEFINITION","canonicalValue":"eligible dhIds","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:eligible-dhids"]},{"nodeId":"value:request-ids","kind":"DEFINITION","canonicalValue":"request.ids","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:request-ids"]},{"nodeId":"value:request-status","kind":"DEFINITION","canonicalValue":"request.status","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:request-status"]},{"nodeId":"where:id-in","kind":"WHERE_PREDICATE","canonicalValue":"id IN","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:id-where"]}],"edges":[{"edgeId":"data-edge:eligible-dhids-to-criterion","kind":"ARGUMENT_TO_PARAMETER","fromNodeId":"value:eligible-dhids","toNodeId":"criterion:id-in-dhids","ruleId":"java-criteria-argument-binding-v1","resolution":"EXACT","guardNodeId":"guard:dhids-not-empty","polarity":"TRUE","evidenceDraftRefs":["provenance:id-criterion"]},{"edgeId":"data-edge:ids-to-where","kind":"CRITERION_TO_WHERE","fromNodeId":"criterion:id-in-dhids","toNodeId":"where:id-in","ruleId":"mybatis-example-criterion-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:id-where"]},{"edgeId":"data-edge:property-to-placeholder","kind":"PROPERTY_TO_PLACEHOLDER","fromNodeId":"property:depothead-status","toNodeId":"placeholder:record-status","ruleId":"mybatis-record-property-binding-v1","resolution":"EXACT","guardNodeId":"xml-guard:record-status-not-null","polarity":"TRUE","evidenceDraftRefs":["provenance:status-placeholder"]},{"edgeId":"data-edge:record-status-to-column","kind":"PLACEHOLDER_TO_COLUMN","fromNodeId":"placeholder:record-status","toNodeId":"column:jsh-depot-head-status","ruleId":"sql-assignment-binding-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:status-column"]},{"edgeId":"data-edge:request-ids-to-service-ids","kind":"ARGUMENT_TO_PARAMETER","fromNodeId":"value:request-ids","toNodeId":"parameter:service-depot-head-ids","ruleId":"java-argument-binding-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:controller-call"]},{"edgeId":"data-edge:request-to-service-status","kind":"ARGUMENT_TO_PARAMETER","fromNodeId":"value:request-status","toNodeId":"parameter:service-status","ruleId":"java-argument-binding-v1","resolution":"EXACT","guardNodeId":null,"polarity":null,"evidenceDraftRefs":["provenance:controller-call"]},{"edgeId":"data-edge:service-ids-to-eligible-dhids","kind":"DEF_USE","fromNodeId":"parameter:service-depot-head-ids","toNodeId":"value:eligible-dhids","ruleId":"java-def-use-worklist-v1","resolution":"EXACT","guardNodeId":"guard:dhids-not-empty","polarity":"TRUE","evidenceDraftRefs":["provenance:eligible-dhids"]},{"edgeId":"data-edge:service-status-to-property","kind":"SETTER_TO_PROPERTY","fromNodeId":"parameter:service-status","toNodeId":"property:depothead-status","ruleId":"java-setter-property-v1","resolution":"EXACT","guardNodeId":"guard:dhids-not-empty","polarity":"TRUE","evidenceDraftRefs":["provenance:set-status"]}],"coverage":{"candidateElementIds":["criterion:id-in-dhids","data-edge:eligible-dhids-to-criterion","data-edge:ids-to-where","data-edge:property-to-placeholder","data-edge:record-status-to-column","data-edge:request-ids-to-service-ids","data-edge:request-to-service-status","data-edge:service-ids-to-eligible-dhids","data-edge:service-status-to-property","parameter:service-depot-head-ids","parameter:service-status","placeholder:record-status","value:eligible-dhids","value:request-ids","value:request-status","where:id-in"],"exactElementIds":["criterion:id-in-dhids","data-edge:eligible-dhids-to-criterion","data-edge:ids-to-where","data-edge:property-to-placeholder","data-edge:record-status-to-column","data-edge:request-ids-to-service-ids","data-edge:request-to-service-status","data-edge:service-ids-to-eligible-dhids","data-edge:service-status-to-property","parameter:service-depot-head-ids","parameter:service-status","placeholder:record-status","value:eligible-dhids","value:request-ids","value:request-status","where:id-in"],"gapDispositions":[],"exclusionDispositions":[],"scopeGapIds":["gap:bounded-path-set"],"closed":false},"worklistAccounting":{"enqueued":12,"processed":12,"overLimit":0},"graphProfileRef":{"artifactId":"graph-profile:8888888888888888888888888888888888888888888888888888888888888888","sha256":"8888888888888888888888888888888888888888888888888888888888888888"}}}
{"storyProjection":"evidence-graph","artifactId":"graph-draft:5555555555555555555555555555555555555555555555555555555555555555","producer":{"address":{"kind":"ANALYSIS_STEP","runId":"analysis-run:9999999999999999999999999999999999999999999999999999999999999999","analysisStepKey":"program-graphs","moduleNumber":5,"moduleKey":"evidence-graph"},"storyModuleVersion":"illustrative"},"upstreamArtifacts":[{"artifactId":"graph-draft:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"graph-draft:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"graph-draft:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"graph-draft:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"artifactId":"graph-profile:8888888888888888888888888888888888888888888888888888888888888888","sha256":"8888888888888888888888888888888888888888888888888888888888888888"},{"artifactId":"verified-source-inventory-source-inventory:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"verified-snapshot:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null,"artifactPolicyRegistryRef":{"artifactId":"artifact-policy-registry:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"}},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:bounded-path-set"],"failureRef":null},"payload":{"graphKind":"EVIDENCE","graphId":"graph:evidence-depothead","snapshotId":"snapshot:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","applicationProfileId":"application:jsh-erp-java8-spring-mybatis","entryIds":["entry:post-depothead-batch-set-status"],"nodes":[{"nodeId":"evidence:controller-call","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:183-185","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-call"]},{"nodeId":"evidence:controller-method","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:178-191","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-method"]},{"nodeId":"evidence:controller-route","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:43,178-191","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:controller-route"]},{"nodeId":"evidence:dhids-guard","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:798-821","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:dhids-guard"]},{"nodeId":"evidence:eligible-dhids","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:749-803","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:eligible-dhids"]},{"nodeId":"evidence:id-criterion","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/DepotHeadExample.java:149-151","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:id-criterion"]},{"nodeId":"evidence:id-where","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:494-495","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:id-where"]},{"nodeId":"evidence:mapper-call","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:798-803","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:mapper-call"]},{"nodeId":"evidence:mapper-method","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/DepotHeadMapper.java:23","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:mapper-method"]},{"nodeId":"evidence:request-ids","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:184","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:request-ids"]},{"nodeId":"evidence:request-status","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:183","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:request-status"]},{"nodeId":"evidence:return","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:821","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:return"]},{"nodeId":"evidence:service-ids","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:742-749","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-ids"]},{"nodeId":"evidence:service-method","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:742-821","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-method"]},{"nodeId":"evidence:service-status","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:742","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:service-status"]},{"nodeId":"evidence:set-status","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:798-803","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:set-status"]},{"nodeId":"evidence:status-column-span","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:472-473","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-column"]},{"nodeId":"evidence:status-guard","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:472","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-guard"]},{"nodeId":"evidence:status-placeholder","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:472-473","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:status-placeholder"]},{"nodeId":"evidence:table-update","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:386","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:table-update"]},{"nodeId":"evidence:xml-statement","kind":"SOURCE_SPAN","canonicalValue":"jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapper.xml:385-495","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":["provenance:xml-statement"]},{"nodeId":"rule:java-argument-binding-v1","kind":"RULE_APPLICATION","canonicalValue":"java-argument-binding-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-call-return-pair-v1","kind":"RULE_APPLICATION","canonicalValue":"java-call-return-pair-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-criteria-argument-binding-v1","kind":"RULE_APPLICATION","canonicalValue":"java-criteria-argument-binding-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-def-use-worklist-v1","kind":"RULE_APPLICATION","canonicalValue":"java-def-use-worklist-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-if-cfg-v1","kind":"RULE_APPLICATION","canonicalValue":"java-if-cfg-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-sequence-cfg-v1","kind":"RULE_APPLICATION","canonicalValue":"java-sequence-cfg-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-setter-property-v1","kind":"RULE_APPLICATION","canonicalValue":"java-setter-property-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:java-static-receiver-call-v1","kind":"RULE_APPLICATION","canonicalValue":"java-static-receiver-call-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:mybatis-example-criterion-v1","kind":"RULE_APPLICATION","canonicalValue":"mybatis-example-criterion-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:mybatis-namespace-signature-binding-v1","kind":"RULE_APPLICATION","canonicalValue":"mybatis-namespace-signature-binding-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:mybatis-record-property-binding-v1","kind":"RULE_APPLICATION","canonicalValue":"mybatis-record-property-binding-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:mybatis-statement-return-pair-v1","kind":"RULE_APPLICATION","canonicalValue":"mybatis-statement-return-pair-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:source-element-parser-v1","kind":"RULE_APPLICATION","canonicalValue":"source-element-parser-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:spring-route-merge-v1","kind":"RULE_APPLICATION","canonicalValue":"spring-route-merge-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:sql-assignment-binding-v1","kind":"RULE_APPLICATION","canonicalValue":"sql-assignment-binding-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:sql-column-declaration-v1","kind":"RULE_APPLICATION","canonicalValue":"sql-column-declaration-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]},{"nodeId":"rule:sql-update-table-v1","kind":"RULE_APPLICATION","canonicalValue":"sql-update-table-v1","owningEntryIds":["entry:post-depothead-batch-set-status"],"evidenceDraftRefs":[]}],"edges":[{"edgeId":"evidence-edge:support:call-edge:controller-service","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:controller-call","subjectGraphKind":"CALL","subjectProgramElementId":"call-edge:controller-service","ruleApplicationNodeId":"rule:java-static-receiver-call-v1"},{"edgeId":"evidence-edge:support:call-edge:mapper-xml","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:xml-statement","subjectGraphKind":"CALL","subjectProgramElementId":"call-edge:mapper-xml","ruleApplicationNodeId":"rule:mybatis-namespace-signature-binding-v1"},{"edgeId":"evidence-edge:support:call-edge:service-mapper","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:mapper-call","subjectGraphKind":"CALL","subjectProgramElementId":"call-edge:service-mapper","ruleApplicationNodeId":"rule:java-static-receiver-call-v1"},{"edgeId":"evidence-edge:support:call-return-edge:mapper-service","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:mapper-call","subjectGraphKind":"CALL","subjectProgramElementId":"call-return-edge:mapper-service","ruleApplicationNodeId":"rule:java-call-return-pair-v1"},{"edgeId":"evidence-edge:support:call-return-edge:service-controller","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:controller-call","subjectGraphKind":"CALL","subjectProgramElementId":"call-return-edge:service-controller","ruleApplicationNodeId":"rule:java-call-return-pair-v1"},{"edgeId":"evidence-edge:support:call-return-edge:xml-mapper","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:xml-statement","subjectGraphKind":"CALL","subjectProgramElementId":"call-return-edge:xml-mapper","ruleApplicationNodeId":"rule:mybatis-statement-return-pair-v1"},{"edgeId":"evidence-edge:support:call:controller-to-service","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:controller-call","subjectGraphKind":"CALL","subjectProgramElementId":"call:controller-to-service","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:call:service-to-mapper","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:mapper-call","subjectGraphKind":"CALL","subjectProgramElementId":"call:service-to-mapper","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:cfg-edge:guard-false-return","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:dhids-guard","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"cfg-edge:guard-false-return","ruleApplicationNodeId":"rule:java-if-cfg-v1"},{"edgeId":"evidence-edge:support:cfg-edge:guard-true-update","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:dhids-guard","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"cfg-edge:guard-true-update","ruleApplicationNodeId":"rule:java-if-cfg-v1"},{"edgeId":"evidence-edge:support:cfg-edge:update-return","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:return","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"cfg-edge:update-return","ruleApplicationNodeId":"rule:java-sequence-cfg-v1"},{"edgeId":"evidence-edge:support:column:jsh-depot-head-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:status-column-span","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"column:jsh-depot-head-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:criterion:id-in-dhids","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:id-where","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"criterion:id-in-dhids","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:data-edge:eligible-dhids-to-criterion","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:id-criterion","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:eligible-dhids-to-criterion","ruleApplicationNodeId":"rule:java-criteria-argument-binding-v1"},{"edgeId":"evidence-edge:support:data-edge:ids-to-where","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:id-where","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:ids-to-where","ruleApplicationNodeId":"rule:mybatis-example-criterion-v1"},{"edgeId":"evidence-edge:support:data-edge:property-to-placeholder","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:status-placeholder","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:property-to-placeholder","ruleApplicationNodeId":"rule:mybatis-record-property-binding-v1"},{"edgeId":"evidence-edge:support:data-edge:record-status-to-column","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:status-column-span","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:record-status-to-column","ruleApplicationNodeId":"rule:sql-assignment-binding-v1"},{"edgeId":"evidence-edge:support:data-edge:request-ids-to-service-ids","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:controller-call","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:request-ids-to-service-ids","ruleApplicationNodeId":"rule:java-argument-binding-v1"},{"edgeId":"evidence-edge:support:data-edge:request-to-service-status","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:controller-call","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:request-to-service-status","ruleApplicationNodeId":"rule:java-argument-binding-v1"},{"edgeId":"evidence-edge:support:data-edge:service-ids-to-eligible-dhids","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:eligible-dhids","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:service-ids-to-eligible-dhids","ruleApplicationNodeId":"rule:java-def-use-worklist-v1"},{"edgeId":"evidence-edge:support:data-edge:service-status-to-property","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:set-status","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"data-edge:service-status-to-property","ruleApplicationNodeId":"rule:java-setter-property-v1"},{"edgeId":"evidence-edge:support:entry:post-depothead-batch-set-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:controller-route","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"entry:post-depothead-batch-set-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:guard:dhids-not-empty","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:dhids-guard","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"guard:dhids-not-empty","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:method:controller-batch-set-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:controller-method","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"method:controller-batch-set-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:method:mapper-update-by-example","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:mapper-method","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"method:mapper-update-by-example","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:method:service-batch-set-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:service-method","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"method:service-batch-set-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:parameter:service-depot-head-ids","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:service-ids","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"parameter:service-depot-head-ids","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:parameter:service-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:service-status","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"parameter:service-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:placeholder:record-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:status-placeholder","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"placeholder:record-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:property:depothead-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:set-status","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"property:depothead-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:route:post-depothead-batch-set-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:controller-route","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"route:post-depothead-batch-set-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:statement:update-by-example-selective","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:xml-statement","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"statement:update-by-example-selective","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:step:set-status-and-update","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:set-status","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"step:set-status-and-update","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:structure-edge:controller-route","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:controller-route","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"structure-edge:controller-route","ruleApplicationNodeId":"rule:spring-route-merge-v1"},{"edgeId":"evidence-edge:support:structure-edge:statement-table","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:table-update","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"structure-edge:statement-table","ruleApplicationNodeId":"rule:sql-update-table-v1"},{"edgeId":"evidence-edge:support:structure-edge:table-declares-status","kind":"SUPPORTS_PROGRAM_EDGE","evidenceNodeId":"evidence:status-column-span","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"structure-edge:table-declares-status","ruleApplicationNodeId":"rule:sql-column-declaration-v1"},{"edgeId":"evidence-edge:support:table:jsh-depot-head","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:table-update","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"table:jsh-depot-head","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:terminal:return-result","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:return","subjectGraphKind":"CONTROL_FLOW","subjectProgramElementId":"terminal:return-result","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:value:eligible-dhids","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:eligible-dhids","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"value:eligible-dhids","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:value:request-ids","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:request-ids","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"value:request-ids","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:value:request-status","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:request-status","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"value:request-status","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:where:id-in","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:id-where","subjectGraphKind":"DATA_FLOW","subjectProgramElementId":"where:id-in","ruleApplicationNodeId":"rule:source-element-parser-v1"},{"edgeId":"evidence-edge:support:xml-guard:record-status-not-null","kind":"SUPPORTS_PROGRAM_NODE","evidenceNodeId":"evidence:status-guard","subjectGraphKind":"CODE_STRUCTURE","subjectProgramElementId":"xml-guard:record-status-not-null","ruleApplicationNodeId":"rule:source-element-parser-v1"}],"coverage":{"candidateProgramElementIds":["call-edge:controller-service","call-edge:mapper-xml","call-edge:service-mapper","call-return-edge:mapper-service","call-return-edge:service-controller","call-return-edge:xml-mapper","call:controller-to-service","call:service-to-mapper","cfg-edge:guard-false-return","cfg-edge:guard-true-update","cfg-edge:update-return","column:jsh-depot-head-status","criterion:id-in-dhids","data-edge:eligible-dhids-to-criterion","data-edge:ids-to-where","data-edge:property-to-placeholder","data-edge:record-status-to-column","data-edge:request-ids-to-service-ids","data-edge:request-to-service-status","data-edge:service-ids-to-eligible-dhids","data-edge:service-status-to-property","entry:post-depothead-batch-set-status","guard:dhids-not-empty","method:controller-batch-set-status","method:mapper-update-by-example","method:service-batch-set-status","parameter:service-depot-head-ids","parameter:service-status","placeholder:record-status","property:depothead-status","route:post-depothead-batch-set-status","statement:update-by-example-selective","step:set-status-and-update","structure-edge:controller-route","structure-edge:statement-table","structure-edge:table-declares-status","table:jsh-depot-head","terminal:return-result","value:eligible-dhids","value:request-ids","value:request-status","where:id-in","xml-guard:record-status-not-null"],"evidencedProgramElementIds":["call-edge:controller-service","call-edge:mapper-xml","call-edge:service-mapper","call-return-edge:mapper-service","call-return-edge:service-controller","call-return-edge:xml-mapper","call:controller-to-service","call:service-to-mapper","cfg-edge:guard-false-return","cfg-edge:guard-true-update","cfg-edge:update-return","column:jsh-depot-head-status","criterion:id-in-dhids","data-edge:eligible-dhids-to-criterion","data-edge:ids-to-where","data-edge:property-to-placeholder","data-edge:record-status-to-column","data-edge:request-ids-to-service-ids","data-edge:request-to-service-status","data-edge:service-ids-to-eligible-dhids","data-edge:service-status-to-property","entry:post-depothead-batch-set-status","guard:dhids-not-empty","method:controller-batch-set-status","method:mapper-update-by-example","method:service-batch-set-status","parameter:service-depot-head-ids","parameter:service-status","placeholder:record-status","property:depothead-status","route:post-depothead-batch-set-status","statement:update-by-example-selective","step:set-status-and-update","structure-edge:controller-route","structure-edge:statement-table","structure-edge:table-declares-status","table:jsh-depot-head","terminal:return-result","value:eligible-dhids","value:request-ids","value:request-status","where:id-in","xml-guard:record-status-not-null"],"gapDispositions":[],"exclusionDispositions":[],"scopeGapIds":["gap:bounded-path-set"],"closed":false},"graphProfileRef":{"artifactId":"graph-profile:8888888888888888888888888888888888888888888888888888888888888888","sha256":"8888888888888888888888888888888888888888888888888888888888888888"}}}
~~~

### 8.1 Interface 与通用 graph records

~~~java
interface ProgramGraphBuilder {
    ProgramGraphsReference build(VerifiedSourceInventoryReference source, ApplicationDiscoveryReference discovery);
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

EvidenceNodeV2
  evidenceNodeId
  kind: SOURCE_EXCERPT | RULE_APPLICATION
  sourceExcerpt?: SourceExcerptV1
  ruleApplication?: RuleApplicationV2

RuleApplicationV2
  ruleId
  ruleVersion
  inputProgramElementIds[]
~~~

graphKind 恰为 CODE_STRUCTURE、CALL、CONTROL_FLOW、DATA_FLOW、EVIDENCE。各 graph 使用自己的 node/edge kind registry；不能临时发明字符串 kind。

### 8.2 五图最小 registry

| 图 | 必需 node/edge 类别 |
| --- | --- |
| code structure | `PACKAGE`、`TYPE`、`FIELD`、`METHOD`、`PARAMETER`、`ANNOTATION`、`CONFIGURATION_KEY`、`CONFIGURATION_RESOURCE`、`XML_NAMESPACE`、`XML_STATEMENT`、`SQL_TABLE`、`SQL_COLUMN`；`CONTAINS`、`DECLARES`、`CONFIG_RESOLVES_RESOURCE`、`STATEMENT_CONTAINS_SQL`。配置仅接受已实现的静态key→resource literal；嵌套YAML键以`.`展平，无法安全展平或非literal值进入Gap。 |
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

nodeId 绑定 snapshot、graph kind、semantic kind、canonical value、source identity 和 rule version；edgeId 再绑定 exact endpoints、rule、resolution、guard/polarity。graphId 最后绑定全部排序 nodes/edges、完整`graphProfileRef.artifactId+sha256`和 coverage；禁止用display key或仅ID替代profile bytes reference。

每张图必须满足 discovered candidates = admitted exact + gap + reasoned exclusion。跨图index建立全局唯一node/edge catalog，验证ProgramEdge所有外部endpoint以及EvidenceEdge的subject/rule refs；unknown node/edge不得被默默丢弃。Evidence graph从来不把program edge ID塞进`toNodeId`。

预算分图执行 maxNodes/maxEdges，并覆盖 AST/XML/SQL chars、call depth、CFG states、dataflow worklist、evidence spans 和 traversal depth。超限不截断为 COMPLETE。

### 8.5 安全与 failure codes

所有 parser 输入来自 verified handles。XML 禁外部 DTD/entity/schema/network。不开客户 classloader，不做 reflection runtime，不运行 annotation processor、MyBatis 或 SQL。

稳定 code：

GRAPH_PROFILE_INVALID、GRAPH_REFERENCE_BROKEN、GRAPH_ACCOUNTING_INVARIANT_BROKEN、CODE_STRUCTURE_INVARIANT_BROKEN、CALL_TARGET_AMBIGUOUS、CALL_RETURN_PAIR_INVALID、CFG_POLARITY_MISSING、CFG_TERMINAL_UNRESOLVED、DATA_FLOW_BINDING_UNPROVEN、DATA_FLOW_WORKLIST_LIMIT_EXCEEDED、EVIDENCE_GRAPH_INVARIANT_BROKEN、EVIDENCE_SOURCE_REOPEN_MISMATCH、XML_SECURITY_POLICY_UNENFORCEABLE、XML_EXTERNAL_RESOLUTION_ATTEMPT、PROGRAM_GRAPHS_RESOURCE_LIMIT_EXCEEDED。

### 8.6 测试 seam 与验收

- 五个 exact filenames 是 first-class artifacts；缺一、多一或交叉替换必须失败。
- Controller→Service、Service→Mapper、Mapper→XML 分段 deletion mutation 分别失败。
- TRUE/FALSE、terminal、call/return edge mutation 不得用源码顺序补回。
- status 和 ids 每一段 dataflow deletion 都使对应 Fact 不可证明。
- 同名 decoy type/method/XML statement 不能被字符串匹配选中。
- Evidence span hash、rule ID 或 graph endpoint mutation fail closed。
- 不同 root/input order 产生相同五图 canonical bytes。
- standard DOCTYPE 零网络 lookup；external entity mutation fatal。

验收分两层：结构验收要求八个 exact files、五图 envelope/roots/cross-references/accounting 全闭合；DepotHead 业务验收要求 8.3 的八段闭包逐段 mutation 可使对应 edge/Fact 不成立。两层都通过且无字符串猜测旁路，分析步骤“程序图” 才算目标实现完成。

### 8.7 已冻结裁决：实现者不得自由推断

- 图种类和五个 filenames 恰为本文件定义的五项；不能合并为 repository blob，也不能把第六种临时图加入 production set。
- node/edge kind 只来自版本化 registry；不支持的语法产生带 affected entry 的 Gap，不能发明自由字符串 kind。
- call、control、data binding 必须逐段 EXACT；simple name、源码邻近、声明顺序和注释不能补边。
- Evidence graph 证明 graph provenance，Proof 在 分析步骤“已证明代码事实” 证明 Fact；两者不可合并或互相替代。
- 五图作为一个原子analysis step set安装并按完整identity复用；不能引用四张再现场生成第五张。
- parser core、graph storage、worklist 和并行实现可自行选择；graph 边界、registry、identity、Gap/fatal 与 DepotHead closure 不得改变。

## 9. 当前实现成熟度审计

Wire Reset后的`org.sourceanalysis.app.analysis.graph`已经有受限的 M1 代码结构图垂直切片；它不等于完整的“程序图”分析步骤，也不等于已完成的全仓库业务分析。当前实现状态必须与下方目标设计分开阅读。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 目标package、Maven身份和JDK 17 Toolchain已经就位；通用wire头门禁只负责拒绝非`SOURCE_ANALYSIS/v1`输入。 |
| **已实现（M1 有界切片）** | `CodeStructureGraphBuilder`已能对传入的已验证 UTF-8 Java、标准 MyBatis XML/静态 SQL 和静态 YAML 产生 code-structure draft：声明、配置、Mapper、表/列节点与关系均带 v2 `ProvenanceDraftV1`。Java 用 AST 范围，XML 表/列用经标签和属性校验的范围，嵌套 YAML 键按 `.` 展平；遇到解析、实体、动态资源或不安全映射则记 Gap。`CodeStructureGraphModulePublisher`已将该 draft 按 receipt-last 安装并从 module store fresh reopen。当前直接验证为 8 个 M1 builder/publisher 测试通过。 |
| **部分实现（M1 的产品组装）** | 代码结构 builder 的公开测试 seam 仍接收结构化的 `CodeStructureSource` 与 `CodeStructureDiscovery` 输入。由正式运行核心重新打开已验证源码清单和应用发现 artifacts、构造这两个输入并驱动 M1 的路径尚未落地；因此当前模块产物不是完整分析步骤的 reader-visible 输出。 |
| **部分实现（M1→M2 可信重开）** | `PersistedCodeStructureGraphReader`、sealed `ReopenedCodeStructureGraph`、exact payload parser和`ProgramGraphInputBasis`已经实现并由真实 canonical module store 验证：M2只能先重开 M1 receipt/payload，核对 address、schema/type、七个上游引用、controls、profile和分母，再读取结构 draft。改变 fresh-reopened source controls 会在解析前以`GRAPH_REFERENCE_BROKEN`拒绝。M2 execution 与自己的持久化发布仍未实现。 |
| **部分实现（M2 有界调用图）** | `CallGraphBuilder`已对冻结 fixture 产生唯一 Controller→Service、Service→Mapper、Mapper Java→XML statement 以及 call/return edges；重载 handler 或受显式 import 影响的 receiver 都记录 Gap，绝不按源码顺序或简单名称猜 target。`CallGraphExecution`会先以同一 reopened inputs 重开 M1，再构建并由`CallGraphModulePublisher`将调用图写为独立 M2 receipt-last artifact；后者把已重开 M1 payload 加入八项上游 lineage。完整 receipt mutation matrix与完整仓库验收仍未实现。 |
| **部分实现（M2→M3 可信重开）** | `PersistedCallGraphReader`和sealed `ReopenedCallGraph`已由真实 canonical M1/M2 modules 验证。M3只能在核对 M2 address、type/schema、八项 upstream、controls、producer/completion、payload、profile与同一 M1/source/discovery basis 后读取调用图；不符一律为`GRAPH_REFERENCE_BROKEN`。控制流 builder 及 M3 module publication仍未实现。 |
| **尚未实现** | M3–M6、control-flow/data-flow/evidence 五图集合、graph index、正式 graph Gap JSONL、ProgramGraphs receipt，以及跨图/完整仓库验收均未实现。 |
| **历史证据，不是当前能力** | 已删除的`RepositoryModel`/旧FlowView曾投影部分结构、调用、SQL和CFG，并暴露DepotHead跨层status/ids dataflow不足。它们只提供测试反例，不是当前图或永久seam。 |
| **下一实现门** | 完成 M2 的完整 fail-closed mutation matrix和 Mapper binding accounting；之后按本章完成 M3–M5，并以 M6 原子发布五图、index、Gap 和 receipt。删除任一源码关系时对应 edge 必须消失或形成 Gap。 |

历史pre-reset jshERP slice的Gap、0 Flow、0 Capsule不能被目标edge示例改写成成功，也不能被误报为当前SourceAnalysis输出。
