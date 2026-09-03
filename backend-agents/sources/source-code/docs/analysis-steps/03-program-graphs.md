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
| graph-gaps.jsonl | 有确定源码位置的局部 unsupported/ambiguous/over-limit site；不包含仓库范围缺口 |
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
- **带 Gap 成功**：局部 unsupported、ambiguous 或 over-limit site 有明确 locator、candidate 和 disposition；M2–M4 还必须有非空 affected entries。M1 在入口尚未被发现或无法从该文件安全确定时，可以用空 `affectedEntryIds` 保存“这个已验证源码文件无法解析”的事实，但不得假造一个入口。仓库范围不完整只进入 `scopeGapIds`、index 和 receipt，不进入 `graph-gaps.jsonl`。
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
  inventoryScopeKind, inventoryScopeRoot?, repositoryCompletionEligible, scopeGapIds
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
   精确为 `PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT / program-graphs-code-structure-draft-v3`；
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
`PROGRAM_GRAPHS_CALL_GRAPH_DRAFT / program-graphs-call-graph-draft-v3`。receipt 与 envelope 的
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

M3 module publication也必须经唯一`PersistedControlFlowGraphReader` fresh reopen，M4才可读取CFG；
该reader不是builder、module或新artifact：

```text
PersistedControlFlowGraphReader.reopen(
  ControlFlowGraphDraftReference reference,
  ReopenedProgramGraphInputs sameInputs,
  ReopenedCodeStructureGraph sameStructure,
  ReopenedCallGraph sameCalls,
  ArtifactReference expectedGraphProfileRef)
    -> ReopenedControlFlowGraph

ReopenedControlFlowGraph                         // sealed opaque immutable aggregate
  reference: ControlFlowGraphDraftReference
  payloadRef: ArtifactReference
  draft: ControlFlowGraphDraft
  codeStructurePayloadRef: ArtifactReference
  callGraphPayloadRef: ArtifactReference
  basis: ProgramGraphInputBasis
```

reader先以`CanonicalModuleArtifactStore.reopen(reference.publication)`重验完整module，再要求address精确为
同run的`program-graphs / 3 / control-flow`、completion合法、publication只有
`control-flow-draft.json`，type/schema精确为
`PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT / program-graphs-control-flow-draft-v3`。receipt与envelope的
upstream必须恰为六项：`sameStructure.payloadRef`、`sameCalls.payloadRef`、
`expectedGraphProfileRef`、`sameInputs`的`sourceInventoryRef/verifiedSnapshotRef/entryPointsRef`；
controls必须等于`sameInputs.source.controls`。payload exact解析后必须满足`graphKind=CONTROL_FLOW`、
同一basis/profile、M1/M2 external endpoint、call/return projection、traversal、terminal disposition、
provenance和coverage闭包。任一address、receipt、schema/type、六项lineage、controls、predecessor ref、
basis或payload不一致统一以`GRAPH_REFERENCE_BROKEN`失败，不返回raw draft、partial aggregate或Graph Gap。

#### M1 CodeStructureGraphBuilder

- **解决的问题**：给后续关系一个唯一的声明/包含/SQL 结构坐标系，避免按文件名或 simple name 找对象。
- **精确上游输入及前置**：valid VerifiedSourceInventoryReference、ApplicationDiscovery application/entry/catalog artifacts、CODE_STRUCTURE registry/profile/budget；所有 source handles 与 parser identities 已重验。
- **确定性顺序 / LLM**：解析 verified Java/config/XML/SQL → 建 package/type/field/method/parameter/annotation/configuration/XML namespace/statement/table/column nodes → 为每个 admitted node/edge 写精确 provenance draft → 建 CONTAINS/DECLARES/config/SQL edges → account；0 LLM。
- **目标输出与 DepotHead 示例**：`ProgramGraphDraft{graphKind=CODE_STRUCTURE,nodes,edges,gapDrafts,provenanceDrafts,coverage}`；例子含 Controller/Service/Mapper methods、`DepotHead.status`、XML statement、`jsh_depot_head.status` nodes。
- **必须保持的不变量**：declaration node identity 绑定 snapshot/kind/canonical symbol/source/rule；同名不同 FQN/statement 不合并；每个 candidate 恰一 disposition；`coverage.scopeGapIds`逐字等于8.0.1从fresh-reopened源码范围重算的唯一集合`S`，与local Gap集合相互独立。
- **Gap / fatal / artifact复用**：局部 unsupported/ambiguous/已完整枚举的over-limit syntax必须写共享`GraphGapDraft`。M1先把准确可证明的入口owner写入`affectedEntryIds`；当一个已验证Java/XML/config文件在入口归属建立前就整体解析失败，或该文件没有可证明的入口owner时，M1必须保留非空candidate和精确locator，但允许`affectedEntryIds=[]`。它表示“该源码文件上的局部解析事实未知”，不表示仓库范围不完整，也不得以全仓entry集合代填。duplicate ID、broken containment、无法形成文件级candidate/locator、无法完成候选分母、parser/source/security/accounting 错误 fatal；模块只从相同VerifiedSourceInventory与ApplicationDiscovery roots构建draft。
- **给下游的后置保证**：M2–M5 获得稳定 endpoint IDs 和可重新验证的 source-span provenance drafts；分析步骤“已证明代码事实” 可引用结构节点而无需再解析声明。
- **明确非目标**：不绑定调用、不生成 CFG/data-flow、不证明 Fact。
- **公共测试 seam 与验收**：`buildStructure(source, discovery, profile)` 覆盖 DepotHead FQN/method/property/table/column、同名 decoy、XML include/statement、每个 provenance draft 的 locator/file/excerpt digest以及 root-order determinism；unsupported Java/XML/config fixture还必须直接断言共享Gap的reason/entry/candidate/locator/identity及coverage闭包。至少一个malformed Java与一个forbidden XML entity fixture使用`entryIds=[]`，仍必须各产生一条`affectedEntryIds=[]`、candidate非空、locator非空的M1 local Gap；所有 expected nodes/edges/gap drafts/provenance/coverage 必须闭合。
- **Luna/xhigh 测试指南**：创建 `CodeStructureGraphBuilderTest`，冻结VerifiedSourceInventory与ApplicationDiscovery artifacts、DepotHead bytes和独立node/edge/provenance golden于 `src/test/resources/analysis/graph/code-structure/`。逐RED：完整结构与provenance、同名FQN隔离、XML statement/table/column、配置key→resource、零entry的malformed Java/forbidden XML entity local Gap、broken containment/provenance fatal、root/order determinism。零entry正例必须检查空owner没有被全仓entry代填、candidate与coverage disposition双向相等、locator命中同一verified file/span、Gap identity可重算且COMPLETE_CAPTURE下`coverage.closed=true`；首RED因public builder/schema缺失失败。只fake source handle，parser/canonical/identity不可mock。命令：`mvn -Dtest=CodeStructureGraphBuilderTest test`；禁网络/客户执行。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `analysis/graph/code-structure/`，实现 public `CodeStructureGraphBuilder/CodeStructureGraphDraft` 与 `program-graphs-code-structure-draft-v3`；只读VerifiedSourceInventory与ApplicationDiscovery artifacts，parse→nodes/gap drafts→provenance drafts→edges→coverage。逐slice GREEN，输出IDs/golden稳定；不得合并同名、产生其他图或改registry。缺schema/upstream时STOP交Sol/ultra，完成更新审计。

#### M2 CallGraphBuilder

- **解决的问题**：唯一确定调用 target、call/return pair 和 Mapper Java→XML statement binding。
- **精确上游输入及前置**：`CallGraphInputs{structure: ReopenedCodeStructureGraph, reopened: the same ReopenedProgramGraphInputs}`与`CallGraphProfile`（CALL registry/profile/budget）；M2 execution 必须先用上段 `PersistedCodeStructureGraphReader` 从 M1 reference 得到 sealed structure aggregate，禁止 caller 把 raw `CodeStructureGraphDraft` 塞入 input。M2只可解析`reopened.source`中的verified call-site bytes，只可把`reopened.discovery.entries/mapperCatalog`作为Stage 2候选；builder 开始解析前必须重算 `ProgramGraphInputBasis`，要求它与 `structure.basis` 以及 `CallGraphProfile.graphProfileRef` 逐字段相同。receiver/type/method candidate refs 均存在。`CallGraphInputs`是immutable in-process builder input，不是新wire/module artifact，不含worktree/repository `Path`、自由source string或与该reopen脱离的entries/catalog列表。
- **确定性顺序 / LLM**：枚举 call sites → 求 receiver static type → 解析 method signature/overload → 建 direct target → 配对 call/return → 按 namespace/signature 绑定 Mapper statement；0 LLM。
- **目标输出与 DepotHead 示例**：CALL graph draft及其共享`gapDrafts`；例子有 Controller :185→Service :742、Service :803→Mapper Java :23、Mapper Java→XML :385 三段 exact edges。
- **必须保持的不变量**：每个 admitted call/binding target 唯一；edge 保存 exact endpoints/rule/resolution/evidence draft；simple name/文本相似不是 tie-breaker；每条M2 local Gap有非空entry owner，且`coverage.scopeGapIds`逐字等于同一输入基础的`S`。
- **Gap / fatal / artifact复用**：可定位的 ambiguous/unsupported或已完整枚举的over-limit call必须写共享`GraphGapDraft`，且M2每条local Gap的`affectedEntryIds`必须是owner draft `entryIds`的非空子集；没有入口owner的Mapper catalog未知留在ApplicationDiscovery或范围accounting，不能伪造M2 local Gap。若 Stage 2 已确认 Java interface 与 XML namespace 是同一 Mapper、但缺少被调用的方法 candidate，则写`MAPPER_JAVA_METHOD_UNRESOLVED` Gap，绝不静默省略 binding。没有匹配 Mapper catalog 的普通 Java interface 不被臆断为 Mapper。两个 exact target、broken endpoints、无法完成候选分母、pair/reference/accounting 错误 fatal；只接受相同roots/profile。
- **给下游的后置保证**：M3/M4/BusinessFlows 能沿明确 call/return，不需动态 dispatch 猜测；ProvenCodeFacts 可把 call edge 放入 Proof。
- **明确非目标**：不以调用顺序代替 CFG，不推值流，不把 unresolved candidate 任选一个。
- **公共测试 seam 与验收**：`buildCalls(CallGraphInputs inputs, CallGraphProfile profile)` 对三段 DepotHead chain 做逐段 deletion/decoy/overload mutation；fixture必须先把真实 M1 module publication 安装到 canonical store，再由 `PersistedCodeStructureGraphReader` 与同一次`ReopenedProgramGraphInputs`生成唯一 sealed inputs。raw draft constructor、错 M1 address/key、receipt SHA、schema/type、六项 lineage、controls、profile、snapshot/application/entry denominator 任一 mutation 都必须在 builder 解析源码前以 `GRAPH_REFERENCE_BROKEN` 失败；删除/替换call-site bytes或脱离Stage 2的entry/catalog candidate不得由路径、自由字符串搜索或M1 display value补回；只有唯一 signature/namespace binding 时产生 EXACT edge。ambiguous/unresolved call或Mapper fixture还必须直接断言共享Gap五字段、identity和coverage映射，任一字段/ID substitution均fail closed。
- **Luna/xhigh 测试指南**：创建 `CallGraphBuilderTest`，fixtures/goldens在 `src/test/resources/analysis/graph/call-graph/`。先用真实 canonical module store 建 reader/execution RED：正确 M1 fresh reopen，以及 address/receipt/schema/type/六 refs/controls/profile/snapshot/application/entry mutation fail-closed；不能新增平行 selector 或允许测试直接构造 sealed aggregate。随后一个RED一个三段binding：Controller→Service、Service→Mapper、Mapper→XML；再做overload/decoy Gap、双exact fatal、call-return mutation、determinism。golden不由production生成；禁止mock store receipt验证、resolution/canonical。命令：`mvn -Dtest=CallGraphBuilderTest test`；无网络/runtime。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `analysis/graph/call-graph/`，实现 `PersistedCodeStructureGraphReader`、sealed `ReopenedCodeStructureGraph`、M2 execution，以及 public `CallGraphBuilder/CallGraphInputs/CallGraphProfile/CallGraphDraft` 与 `program-graphs-call-graph-draft-v3`。execution先用同一`ReopenedProgramGraphInputs`和`CallGraphProfile.graphProfileRef` fresh-reopen M1 module，验证 address/receipt/exact payload/六 refs/controls与全部payload identity，严格解析draft并构造opaque aggregate；再只以`inputs.structure.draft`解析endpoint、以`inputs.reopened.source`解析call site、以`inputs.reopened.discovery.entries/mapperCatalog`解析Stage 2候选，按receiver type→signature→target→return pair→Mapper binding→provenance drafts。逐edge GREEN且decoy不命中；不得暴露可伪造aggregate实现、接受raw draft/Path/detached lists/free source string、扫描worktree、字符串fallback或改M1。跨analysis step/data缺失MUST STOP交Sol/ultra，更新审计。

#### M3 ControlFlowGraphBuilder

- **解决的问题**：表达每个 entry 可达步骤、guard polarity、call/return 与 terminal，防止用源码行序讲流程。
- **精确上游输入及前置**：`ControlFlowInputs{structure: ReopenedCodeStructureGraph, calls: ReopenedCallGraph, reopened: the same ReopenedProgramGraphInputs}`与`ControlFlowGraphProfile`；M3 execution必须按8.0先fresh-reopen M1/M2，校验同一basis、M1 payload ref与graph profile，再从`reopened.discovery.entries`读取entry roots、从`reopened.source`读取verified method bodies。raw `CodeStructureGraphDraft`、raw `CallGraphDraft`和detached entry/call lists均禁止；entry/call endpoints全部有效。
- **确定性顺序 / LLM**：每 entry 建 ENTRY → basic blocks/guards → TRUE/FALSE/NEXT/CALL → 投影structural RETURN frame link → return/throw/profile stop terminals → 只由normal exit激活continuation → reachability/accounting；0 LLM。
- **目标输出与 DepotHead 示例**：CONTROL_FLOW draft；例子保留 Service :752-796 的 status/库存条件 polarity、`:798-803` 非空 dhIds 写入分支与 :821 return terminal。
- **必须保持的不变量**：每个 guard 的 outgoing polarity 显式且合法；每个可达 path 终止、Gap 或 reasoned exclusion；每个M3-activated M2 call/return pair都投影，但`RETURN`只是frame link，只有callee normal exit/leaf可激活call-site continuation，throw/profile stop均不可；每条M3 local Gap有非空entry owner，且`coverage.scopeGapIds`逐字等于同一输入基础的`S`。
- **Gap / fatal / artifact复用**：可定位的bounded loop/unsupported construct或已完整枚举的over-limit control item必须写共享`GraphGapDraft`，且M3每条local Gap的`affectedEntryIds`必须是owner draft `entryIds`的非空子集；缺 polarity/terminal、悬空 block、call pair mismatch、direct-throw-only call anchor仍有continuation、throw path激活continuation、无法完成候选分母或覆盖不闭合 fatal；只能从已验证前驱重建完整CFG draft。
- **给下游的后置保证**：BusinessFlows 可按 entry root 枚举 Outcomes；ProvenCodeFacts 可证明条件 atom，无需从行号推路径。
- **明确非目标**：不执行代码、不假定异常处理器/事务运行时、不命名业务 Outcome。
- **公共测试 seam 与验收**：`buildControlFlow(ControlFlowInputs inputs, ControlFlowGraphProfile profile)` 覆盖 TRUE/FALSE swap、terminal deletion、loop budget、call/return mutation，以及direct-throw-only call anchor保留M2 RETURN projection但无continuation、也不向lexical successor贡献path；DepotHead 每个目标 guard 与 terminal 必须可达且 polarity 稳定。profile-stop/unsupported fixture必须直接断言共享Gap五字段、identity、terminal disposition和coverage同一candidate/gapId闭包。
- **Luna/xhigh 测试指南**：创建 `ControlFlowGraphBuilderTest`，fixtures/goldens置 `src/test/resources/analysis/graph/control-flow/`。RED顺序：entry/guards/terminals正向、TRUE/FALSE swap、terminal deletion、call-return mismatch、direct-throw与mixed return/throw continuation、loop budget Gap、order determinism；首RED应因CFG seam缺失。direct-throw golden必须同时保留projected RETURN、call anchor无continuation NEXT且不向successor贡献path；只有无独立可达前驱时才省略并exclude该successor。mixed golden只允许normal callee branch激活同一continuation。只fake verified method reader，禁止mock CFG/accounting。命令：`mvn -Dtest=ControlFlowGraphBuilderTest test`；禁客户运行/网络。偏离按DESIGN 13.11交`gpt-5.6-sol / ultra` Design Authority。
- **Terra/xhigh 实现指南**：RED后只改 `analysis/graph/control-flow/`，实现 public `ControlFlowGraphBuilder/ControlFlowGraphDraft` 与 `program-graphs-control-flow-draft-v3`；M1/M2 artifacts→blocks/guards→typed edges/Gap drafts→structural return frames→path-sensitive terminals/continuations→provenance drafts→coverage。每个RED独立GREEN；禁止把M2 RETURN pair当无条件successor、按行号补flow或运行代码。必要异常语义不在上游即STOP 13.11，审计同步。

M3 的唯一 public Java seam 与 exact JSON payload 采用下列最小 records；`ControlFlowInputs`和
`ControlFlowGraphProfile`只存在于进程内，不是新 wire/artifact。Java `ArtifactId`在 JSON 中是单个
string，enum 使用下列大写值；所有字段和数组都 required，标成 nullable 的 JSON key 必须出现但值
可以为null，unknown/missing/defaulted field 一律拒绝。

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
  String schemaVersion,                    // exactly program-graphs-control-flow-draft-v3
  ProgramGraphKind graphKind,              // exactly CONTROL_FLOW
  ArtifactId graphId,
  String snapshotId,
  ArtifactId applicationProfileId,
  ArtifactReference graphProfileRef,
  List<ArtifactId> entryIds,
  List<ControlFlowNode> nodes,
  List<ControlFlowEdge> edges,
  List<ControlFlowTraversal> semanticTraversalOrder,
  List<ControlFlowTerminalDisposition> terminalDispositions,
  List<GraphGapDraft> gapDrafts,
  List<ProvenanceDraftV1> provenanceDrafts,
  GraphCoverage coverage)

record ControlFlowNode(
  ArtifactId nodeId,
  ControlFlowNodeKind kind,
  String canonicalValue,
  List<ArtifactId> owningEntryIds,
  List<ArtifactId> evidenceDraftRefs)
enum ControlFlowNodeKind {
  ENTRY, BASIC_BLOCK, GUARD, ENTRY_RETURN_TERMINAL, CALLEE_RETURN_TERMINAL,
  THROW_TERMINAL, PROFILE_STOP_TERMINAL
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

record ControlFlowTraversal(
  ArtifactId entryId,
  List<ArtifactId> nodeIds,
  List<ArtifactId> edgeIds)

record ControlFlowTerminalDisposition(
  ArtifactId terminalNodeId,
  ArtifactId candidateElementId,
  ControlFlowTerminalDispositionKind dispositionKind,
  ArtifactId gapId,                         // nullable Java value; JSON key required
  String exclusionReasonCode)               // nullable Java value; JSON key required
enum ControlFlowTerminalDispositionKind { GAP, EXCLUSION }
```

`ControlFlowInputs`的 public canonical constructor先要求三个值non-null，再以
`structure.basis.graphProfileRef`从`reopened`重算`ProgramGraphInputBasis`；只有重算值、
`structure.basis`、`calls.basis`逐字段相等且
`calls.codeStructurePayloadRef == structure.payloadRef`时才构造成功，否则立即抛
`GraphReferenceException(GRAPH_REFERENCE_BROKEN)`。两个 predecessor 本身是sealed aggregates，
因此这里保证的是同一已验证basis而不是不可验证的“同一对象/进程”身份。builder读源码前再要求
`profile.graphProfileRef == structure.basis.graphProfileRef`。profile constructor只接受non-null完整
`graph-profile:{sha256}` reference；rule registry和budget由该已验证 artifact 决定，不接受 caller
复制的自由 rule/budget 字段。任一不一致不返回 partial draft。

所有 Java `List`在 constructor defensive-copy；`entryIds`、nodes、edges、terminal dispositions、
provenance分别按其主ID排序且distinct。`semanticTraversalOrder`按`entryId`排序；每个`nodeIds`只保存
canonical interprocedural walker实际激活的node preorder，`edgeIds`保存实际激活的flow edges，加上在
reachable call anchor登记的structural `RETURN` frame links；`RETURN`出现在`edgeIds`不表示该path已返回。
两者各自distinct。walker从该entry唯一`ENTRY`开始，guard先记录/访问`TRUE`再`FALSE`，其余同类edge
按`edgeId`。

对每个entry，`owningEntryIds`包含该entry的全部M2 `CALL_SITE`先形成discovered call-pair candidate集合；
walker从`ENTRY`沿可执行M3 flow到达其external endpoint时，该call anchor才是activated。只有activated
anchors及其沿M2 exact edges得到的唯一pair构成projection denominator；每个pair在M3必须恰投影一个
`CALL`和一个`RETURN`，反向地每个M3 `CALL/RETURN`也必须来自该denominator。未activated pair对应的两个
M3 candidate edge ID仍按8.4预计算：已证明anchor不可达时各进reasoned exclusion，profile-stop/unsupported
使可达性未知时各进Gap，禁止exact projection或静默省略。`CALL`逐字段复用
`CALL_TARGET | JAVA_METHOD_TO_XML_STATEMENT`的from/to/rule/resolution，`RETURN`逐字段复用其唯一反向
`CALL_RETURN`；二者的`guardNodeId/polarity=null`、`evidenceDraftRefs`与M2 edge逐字相同，M3 registry
重列相同ID/内容的provenance draft，各自edgeId还绑定M2 edgeId。该RETURN projection在call anchor
reachable时无条件登记为structural frame link，与callee是否存在normal exit无关；它从来不是可单独
遍历的successor。

`CALL_TARGET`的local predecessor以`NEXT`进入其external M2 call-site(from endpoint)。只有callee至少有
一条exact `CALLEE_RETURN_TERMINAL` path或normal leaf时，该call-site才有恰一条`NEXT` continuation；
call若为method最后动作，该continuation就是caller上下文的normal-return terminal。direct-throw-only
call anchor不得生成continuation NEXT，也不从该anchor向lexical successor贡献可执行path；successor只有
不存在其他可达前驱时才从nodes/traversal省略并进reasoned exclusion，若有bypass branch则照常保留但无
来自该call anchor的`NEXT`。只有profile-stop/unsupported导致可达性未知时才进Gap。
`JAVA_METHOD_TO_XML_STATEMENT`不制造call-site：其
external Java method from endpoint就是anchor，XML statement是normal leaf，因此continuation固定为该
Mapper callee的`CALLEE_RETURN_TERMINAL`。

walker在anchor先登记`CALL`与paired structural `RETURN`，仅在上述normal-exit条件成立时保存
continuation，然后进入target；有body的Java method target以`NEXT`进入callee首个control node，空body
直接进入`CALLEE_RETURN_TERMINAL`。`CALLEE_RETURN_TERMINAL`或normal leaf消费匹配栈顶的`RETURN`并
激活continuation NEXT；`ENTRY_RETURN_TERMINAL`结束entry，`THROW_TERMINAL`或
`PROFILE_STOP_TERMINAL`既不消费RETURN也不激活continuation。缺失、额外或错配pair/continuation均fatal。

每个 traversal 可包含上述已验证的 M1/M2 external endpoint，但它们不在 M3 `nodes`中重复声明；每个
entry恰一条traversal，`nodeIds`必须等于activated reachable nodes，`edgeIds`必须等于activated flow
edges加reachable-call-anchor的structural RETURN links，不能只给一个排序提示。每个M3-owned node必须
出现在且只出现在其全部`owningEntryIds`对应的`nodeIds`；每个M3 edge的derived owning entries恰为包含
它的`edgeIds`之entry集合且必须非空。structural RETURN以call anchor可达计；continuation NEXT必须被
至少一条normal-exit path激活，不允许direct-throw-only call anchor留下dormant continuation，也不允许
仅凭该throw path把successor标为reachable；由独立path可达的同一successor不受影响。因此漏项、多项、
wrong-owner或unreachable owned subgraph一律 fatal。node的
`owningEntryIds`必须非空、排序、distinct且为draft `entryIds`子集；每个edge endpoint必须解析到
本draft node或已验证M1/M2 external node。

M3-owned node/edge 的ID遵守8.4；`CALL/RETURN edgeId`的direct preimage还绑定对应M2 edgeId，M3
`graphId`除8.4字段外还绑定完整`semanticTraversalOrder/terminalDispositions`。`coverage.exactElementIds`必须恰等于全部
M3-owned nodeId和edgeId；candidate denominator再与exact/gap/exclusion互斥闭合。每个node/edge的
非空`evidenceDraftRefs`只引用本draft唯一、按ID排序的`provenanceDrafts`，registry无悬空、无孤儿；
traversal中的external node不重复计入M3 coverage。每个`PROFILE_STOP_TERMINAL`恰有一个且只有它们能有
`ControlFlowTerminalDisposition`：mapping的`terminalNodeId`命中该node，`candidateElementId`必须等于
`identity("control-flow-profile-stop-successor-v1", terminalNodeId)`。`GAP` variant要求`gapId!=null`、
`exclusionReasonCode=null`且coverage恰含同candidate/gapId；`EXCLUSION` variant要求`gapId=null`、
nonblank reason且coverage恰含同candidate/reason。profile-stop node自身仍是exact element，mapping不是
新program element、module或artifact。

basic Java 行为严格固定：每个 entry 恰有一个无入边`ENTRY`并以唯一`NEXT`进入external handler method
endpoint；handler随后以唯一`NEXT`进入首个control node或entry normal-return terminal。
`BASIC_BLOCK`是被call/branch/terminal边界切开的最大连续语句段；每个`if`条件产生一个`GUARD`，
且恰有一条`TRUE`和一条`FALSE`出边，二者`guardNodeId`均为自身、polarity与kind相同，无`else`时
FALSE指向lexical successor；若`if`位于method末尾，FALSE必须指向该method的正常fall-through terminal，
不能悬空。entry handler中的显式`return`或normal fall-through形成`ENTRY_RETURN_TERMINAL`；被调用Java
method中的对应出口形成`CALLEE_RETURN_TERMINAL`。二者分别由return语句或method closing token提供
source provenance，只有callee variant按上述walker stack消费structural RETURN并恢复caller。显式
`throw`形成`THROW_TERMINAL`；所有terminal出度必须为0，throw不消费RETURN、不激活continuation，且不猜
catch、事务或runtime handler。
unsupported/over-limit路径只能以`PROFILE_STOP_TERMINAL`加恰一个typed disposition闭合，
不得按行号补边；任何reachable nonterminal缺合法后继、terminal有出边或guard polarity不完整均fatal。

为让M4不重建CFG，fresh-reopened M3还必须对每个activated Java `BASIC_BLOCK`保证：其
source provenance恰覆盖一个已验证method body内的唯一连续AST statement range；每个M4候选
statement/expression按最小包含span恰归属一个activated block；该block的所有可执行入边与出边都出现在
该entry traversal。block内Java求值顺序由M4从同一verified AST按语言规则重算，不新增M3 wire字段。
任一多重/零block归属、跨method span、不完整predecessor closure或source locator/digest矛盾都是
`GRAPH_REFERENCE_BROKEN`，M4不得以source order补图。

#### M4 DataFlowGraphBuilder

- **解决的问题**：逐段证明请求值怎样跨参数、变量、property、criteria、placeholder 到 SQL column/where。
- **精确上游输入及前置**：`DataFlowInputs{structure: ReopenedCodeStructureGraph, calls: ReopenedCallGraph, controlFlow: ReopenedControlFlowGraph, reopened: the same ReopenedProgramGraphInputs}`与`DataFlowGraphProfile`；M4 execution必须以同一次reopened inputs按8.0依序fresh-reopen M1/M2/M3，校验同一basis、三段payload refs与graph profile，只从`reopened.source`读取verified Java/XML bytes。raw drafts、detached endpoints/lists、`Path`和自由source均禁止。
- **确定性顺序 / LLM**：建 intra-method def-use → argument→parameter → assignment/setter/property → Mapper record/example params → XML dynamic condition/include → placeholder/criterion→column/where → fixed-point/account；0 LLM。
- **目标输出与 DepotHead 示例**：DATA_FLOW draft；例子分别闭合 `Controller.status→Service.status→setStatus→record.status→jsh_depot_head.status` 与 `ids→dhIds→andIdIn→WHERE id IN`。
- **必须保持的不变量**：每条长链由相邻 typed edges 组成；每 edge 有唯一 rule/endpoints/guard context；parameter/local read只连唯一reaching definition，赋值RHS先于write处理；不跨 unresolved call/alias/XML path；每条M4 local Gap有非空entry owner，且`coverage.scopeGapIds`逐字等于同一输入基础的`S`。
- **Gap / fatal / artifact复用**：局部 alias、multi-definition join、非direct write/setter、dynamic SQL/loop 超能力为 Gap；M4每条local Gap的`affectedEntryIds`必须是owner draft `entryIds`的非空子集。伪 exact binding、空entry的M4 local carrier、broken predecessor endpoint、worklist/accounting/identity 错误 fatal；只接受相同roots/rules。
- **给下游的后置保证**：ProvenCodeFacts 能逐 atom 引用 exact parameter/local reaching-definition、direct assignment、setter/field和后续where edge；BusinessFlows 能保留M3 guard相关数据条件，不需字符串匹配或重开AST。
- **明确非目标**：不证明运行时 DB/trigger 值，不用字段同名跨层连边，不硬编码 DepotHead positive edge。
- **公共测试 seam 与验收**：`buildDataFlow(DataFlowInputs inputs, DataFlowGraphProfile profile)`；首个slice保留每个M3-activated exact Java call的ordinal `ARGUMENT_TO_PARAMETER`，下一组RED在同一seam上加parameter/local singleton-reaching `DEF_USE`、direct-local `ASSIGNMENT`与direct-setter `SETTER_TO_PROPERTY`，覆盖shadow decoy、RHS-before-write、branch join Gap、compound write Gap、伪setter/field decoy、guard、ID-set accounting与逐边mutation。
- **Luna/xhigh 测试指南**：创建/扩展 `DataFlowGraphBuilderTest`，fixtures/goldens置`src/test/resources/analysis/graph/data-flow/`。先保持真实canonical store fresh-reopen M1/M2/M3的Controller status/ids ordinal binding及其reference/accounting mutations。下一RED依次冻结parameter→simple-name ARGUMENT/USE、local initializer/reassignment的USE→DEFINITION、direct setter body的formal→USE→M1 FIELD `ASSIGNMENT`与caller `ARGUMENT`→FIELD `SETTER_TO_PROPERTY`；每步加same-name/shadow decoy、ambiguous join、complex RHS/compound operator、fake `setX`、wrong FIELD `DECLARES`、guard mutation、work-item omission/duplication/max+1和order determinism。只fake verified source handle，禁止mock predecessor readers、dataflow/accounting/canonical/reaching-definition。命令：`mvn -Dtest=DataFlowGraphBuilderTest test`；禁网络/客户MyBatis。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `analysis/graph/data-flow/`，保持已批准的`PersistedControlFlowGraphReader`、sealed `ReopenedControlFlowGraph`、M4 execution与唯一public seam/wire。在argument binding GREEN后按read/write denominator→singleton reaching definition→direct local assignment→direct setter FIELD扩展；不新增public method、record field、artifact或M1–M3 schema。禁止raw draft/Path、字段或setter名称捷径、DepotHead硬编码或output-derived denominator。上游缺endpoint或需新edge语义MUST STOP交Sol/ultra/必要时用户。

M4唯一public Java seam与exact JSON payload如下。`DataFlowInputs`和`DataFlowGraphProfile`只存在于
进程内，不是新wire/artifact；所有字段/数组required，nullable JSON key必须出现，unknown、missing、
defaulted field一律拒绝：

```text
DataFlowGraphBuilder.buildDataFlow(DataFlowInputs inputs,
                                   DataFlowGraphProfile profile)
  -> DataFlowGraphDraft

record DataFlowInputs(
  ReopenedCodeStructureGraph structure,
  ReopenedCallGraph calls,
  ReopenedControlFlowGraph controlFlow,
  ReopenedProgramGraphInputs reopened)

record DataFlowGraphProfile(ArtifactReference graphProfileRef)

record DataFlowGraphDraft(
  String schemaVersion,                    // exactly program-graphs-data-flow-draft-v2
  ProgramGraphKind graphKind,              // exactly DATA_FLOW
  ArtifactId graphId,
  String snapshotId,
  ArtifactId applicationProfileId,
  ArtifactReference graphProfileRef,
  List<ArtifactId> entryIds,
  List<DataFlowNode> nodes,
  List<DataFlowEdge> edges,
  DataFlowWorklistAccounting worklistAccounting,
  List<GraphGapDraft> gapDrafts,
  List<ProvenanceDraftV1> provenanceDrafts,
  GraphCoverage coverage)

record DataFlowNode(
  ArtifactId nodeId,
  DataFlowNodeKind kind,
  String canonicalValue,
  List<ArtifactId> owningEntryIds,
  List<ArtifactId> evidenceDraftRefs)
enum DataFlowNodeKind {
  DEFINITION, USE, ARGUMENT, CRITERION, PLACEHOLDER, WHERE_PREDICATE
}

record DataFlowEdge(
  ArtifactId edgeId,
  DataFlowEdgeKind kind,
  ArtifactId fromNodeId,
  ArtifactId toNodeId,
  String ruleId,
  ProgramResolution resolution,            // exactly EXACT
  ArtifactId guardNodeId,                   // nullable Java value; JSON key required
  ControlFlowPolarity polarity,             // nullable Java value; JSON key required
  List<ArtifactId> evidenceDraftRefs)
enum DataFlowEdgeKind {
  DEF_USE, ARGUMENT_TO_PARAMETER, ASSIGNMENT, SETTER_TO_PROPERTY,
  PROPERTY_TO_PLACEHOLDER, CRITERION_TO_WHERE, PLACEHOLDER_TO_COLUMN
}

record DataFlowWorklistAccounting(
  List<ArtifactId> enqueuedWorkItemIds,
  List<ArtifactId> processedWorkItemIds,
  boolean overLimit)

record GraphGapDraft(
  ArtifactId gapId,
  String reasonCode,
  List<ArtifactId> affectedEntryIds,
  List<ArtifactId> candidateElementIds,
  SourceLocatorV1 sourceLocator)             // local Gap必须non-null；JSON key required
```

`DataFlowInputs`的public canonical constructor要求四项non-null，以
`structure.basis.graphProfileRef`从`reopened`重算`ProgramGraphInputBasis`，并要求重算值、三个sealed
aggregate的basis逐字段相等，`calls.codeStructurePayloadRef == structure.payloadRef`，且
`controlFlow.codeStructurePayloadRef/callGraphPayloadRef`分别等于M1/M2 payload ref；否则在读取源码前抛
`GraphReferenceException(GRAPH_REFERENCE_BROKEN)`。builder再要求
`profile.graphProfileRef == structure.basis.graphProfileRef`；profile constructor只接受non-null完整
`graph-profile:{sha256}` reference。caller不能构造/实现sealed aggregates，也不能传raw predecessor
draft、module bytes、`Path`、detached entry/call/CFG lists或自由source string。

首个`ARGUMENT_TO_PARAMETER` slice的worklist denominator在生成任何M4 node/edge、查找formal
endpoint或判定shape是否支持前独立计算：取每个entry的M3 traversal中已激活、且能反向唯一
对应M2 `CALL_TARGET`的Java call anchor，对verified invocation AST每个显式syntactic actual取zero-based
ordinal。每个actual的work-item ID恰为`identity("data-flow-argument-work-item-v1", M2 CALL_TARGET
edgeId, actualOrdinal)`；它不包含尚未证明的formal endpoint、support result或处置。固定arity call要求
actual/formal count相等，且每个formal ordinal恰有一个M1 endpoint；零参数fixed-arity call产生零work item。

varargs或本slice不支持的explicit-actual/expression shape按work item生成受影响entry的
`DATA_FLOW_BINDING_UNPROVEN` Gap，不能按名称或邻近位置补边。synthetic/implicit call shape若没有显式
actual，则另产生`identity("data-flow-call-shape-candidate-v1", M2 CALL_TARGET edgeId)`的local Gap
candidate；它不是work item，不进入两个worklist ID集合。已被M2/M3声明为exact却缺target、call
activation、固定arity formal endpoint或arity自相矛盾是`GRAPH_REFERENCE_BROKEN` fatal，不得降级为Gap。

每个受支持的explicit-actual work item都以同ordinal查找已验证M1 `PARAMETER`，并恰产生一个
M4-owned `ARGUMENT` node和一个`ARGUMENT_TO_PARAMETER` edge。argument的`canonicalValue`恰为
actual expression的`java-expression-canonical-v1` AST语义序列化：comments、whitespace与source
location不进入该值，literal/operator、解析后的symbol/type和expression shape必须保留；无法产生该
canonical form就是上述typed Gap。M2 site/edge与ordinal只进入ID preimage，不写入
`canonicalValue`。edge从该argument node指向既有M1 parameter external endpoint，M4不得重声明
parameter；固定`ruleId=java-argument-binding-v1`、`resolution=EXACT`、`guardNodeId=null`、
`polarity=null`。`ARGUMENT.owningEntryIds`恰为激活该call的全部entries之排序distinct集合；
edge wire没有ownership字段，其derived owning entries恰等于from `ARGUMENT.owningEntryIds`，且必须与M3
activation集合相等。argument transfer在call被M3激活后无条件发生，其到达条件仍由M3表达。

argument node引用其连续expression span provenance；edge引用该span、M2 call binding和M1 formal
parameter的全部exact provenance drafts，M4 registry逐ID重列相同上游draft及新draft，禁止孤儿或悬空。
argument nodeId的direct preimage绑定M2 edgeId和ordinal；binding edgeId的direct preimage再绑定M1
parameter nodeId。reader可从fresh-reopened predecessors重算，不允许仅凭canonicalValue相信binding。

下一组intra-method/direct-property slice不新增public record或JSON field，仍只写上述
`DataFlowNode/DataFlowEdge/DataFlowWorklistAccounting/GraphGapDraft`。它在生成任何新node/edge前，
对每个M3 traversal中已activated method且按上段唯一归属于`BASIC_BLOCK`的verified Java AST枚举三类
work item：

1. 每个value-read位置的`NameExpr`（不包declaration/type/method/member/label name）为
   `identity("data-flow-java-read-work-item-v1", declaring M1 METHOD nodeId, fileId,
   startByte, endByteExclusive, astRole)`；`astRole`只能是`SIMPLE_NAME_READ`或
   `ARGUMENT_SIMPLE_NAME_READ`，后者的span必须恰等于已枚举argument work item的整个actual。
2. 每个含initializer的`VariableDeclarator`、每个`AssignExpr`以及每个其他Java value-write
   form（compound assign、`++/--`、enhanced-for/catch/lambda binding）为
   `identity("data-flow-java-write-work-item-v1", declaring M1 METHOD nodeId, fileId,
   startByte, endByteExclusive, astWriteKind)`；kind来自闭合Java AST enum，不是caller字符串。
3. 每个activated exact M2 Java `CALL_TARGET`的actual ordinal，若target body在语法上含有从该
   formal到instance field的write candidate，再枚举
   `identity("data-flow-direct-setter-work-item-v1", M2 CALL_TARGET edgeId, actualOrdinal)`。
   这个语法筛选发生在setter语义证明前；方法名`set*`不参与denominator。

Java语义处理严格固定。lexical resolver按Java 8 scope把每个`NameExpr`绑定到唯一M1
`PARAMETER`或同method的local declaration；shadowing、declaration-before-use、内外scope均按AST结构，
不按名称邻近。method entry状态以M1 `PARAMETER`作initial definition；每个有值local initializer或
simple `=` reassignment产生新M4 `DEFINITION`并kill该local的旧definition。initializer/assignment都先按
Java left-to-right顺序求值RHS reads，再使新definition进scope/state。M4在无cycle的M3 executable
predecessors上前向合并reaching-definition sets；某read只在所有owning entries得到同一singleton
definition时为exact。零/多definition、loop back-edge、capture/alias或不同entry得到不同binding均不造边。

新元素的exact JSON字段/端点矩阵为：

| kind | `fromNodeId` | `toNodeId` | `ruleId` |
| --- | --- | --- | --- |
| `DEF_USE` | external M1 `PARAMETER`或M4 `DEFINITION` | M4 `USE`，或span相同的已有M4 `ARGUMENT` | `java-single-reaching-definition-v1` |
| `ASSIGNMENT` | M4 `USE` | M4 `DEFINITION` | `java-direct-local-assignment-v1` |
| `ASSIGNMENT` | M4 `USE` | external M1 `FIELD` | `java-direct-field-assignment-v1` |
| `SETTER_TO_PROPERTY` | 已有caller M4 `ARGUMENT` | external M1 `FIELD` | `java-direct-setter-property-v1` |

四种edge的`resolution=EXACT`；`guardNodeId/polarity`必须both-null，或恰为该occurrence在M3中所有
owning entries共享的唯一activation guard/polarity。需要多个guard pair、不同owner context或无法从
M3重算时为Gap。edge wire仍无ownership字段：`DEF_USE`取to-node owners，local
`ASSIGNMENT`取to-definition owners，field `ASSIGNMENT`和`SETTER_TO_PROPERTY`取from-node owners；每个
derived set必须非空且恰等于M3重算的relation owners。

M4 local `DEFINITION`及引用local的`USE`使用
`java-local-symbol-v1|<declaring METHOD canonical signature>|<declared canonical type>|<identifier>`语义值；
引用parameter的`USE.canonicalValue`则必须逐字等于该external M1 `PARAMETER.canonicalValue`。
shadowed local declarations可共享该值，但其nodeId direct preimage还绑定declaring METHOD nodeId、原始
local declaration fileId/byte range；parameter use改为绑定M1 PARAMETER nodeId。两者都再绑定当前read/write
fileId/byte range、AST role与rule version，因而不会合并。`USE`引用
read span provenance，`DEFINITION`引用write span provenance。simple-name actual必须复用已有`ARGUMENT`
node而不再声明`USE`。每个edgeId除8.4端点外还绑定它的read/write work-item ID和M3 block/guard
context；setter edgeId再绑定M2 `CALL_TARGET` edgeId、ordinal与target-body assignment identity。

local exact assignment只接受单一declarator的`T x = rhsName`或operator为plain `=`的
`x = rhsName`；允许括号包住RHS simple name，不允许cast、call、constructor、binary/conditional expression、
array/member target或compound/update operator。direct field assignment只接受当前declaring type中的
`this.f = rhsName`；LHS必须命中fresh-reopened M1唯一`FIELD`，RHS必须是上述exact `USE`。
它产生`ASSIGNMENT`但不在M4重声明field/definition。

direct setter summary只在已activated exact M2 target为instance `void` method、目标ordinal恰有一个M1
formal，且其唯一可执行transfer为`this.f = formalName`时生成。field必须由同一declaring M1
`TYPE`以唯一`DECLARES`边声明，caller actual必须命中已有同ordinal `ARGUMENT`。transform、guard、alias、
inherited/static field、multi-write、fluent return、generated/source-unavailable body或只有`set*`名称均不是证明。
该target body的direct field `ASSIGNMENT`与caller侧`SETTER_TO_PROPERTY`是两个不同typed facts；若各自denominator
成立则都发布，但不共用edge ID。

`enqueuedWorkItemIds`恰为graph profile已启用slices的argument/read/write/direct-setter work-item IDs
之排序、distinct集合；每类都先按M3 traversal、block AST order、再按上述identity生成，payload最后只按ID
canonical排序。`processedWorkItemIds`记录实际处理的同一IDs且同样
排序、distinct。每个work item只处理一次，无论结果是exact还是typed Gap；任何返回的draft都要求两个集合
逐ID相等且`overLimit=false`。尝试发现profile上限后的第一项立即以
`DATA_FLOW_WORKLIST_LIMIT_EXCEEDED` fatal，且不返回partial draft、Gap或module publication；本schema不
安装`overLimit=true`的draft，因为停止时尚未证明剩余prospective IDs的完整处置。重复/遗漏work item或
两个集合/denominator不等均为`GRAPH_ACCOUNTING_INVARIANT_BROKEN`。

所有lists在constructor defensive-copy；`entryIds/nodes/edges/gapDrafts/provenanceDrafts`分别按主ID排序且
distinct。
各slice coverage candidate的集合union恰为M4全部candidate denominator。argument work item仍预计算
argument node/binding edge IDs；受支持read产生`USE`（或复用`ARGUMENT`）与`DEF_USE`，受支持write产生
`DEFINITION/ASSIGNMENT`或field `ASSIGNMENT`，受支持setter产生`SETTER_TO_PROPERTY`。元素在set union中
只计一次；external M1 `PARAMETER/FIELD`不计入M4 coverage。不支持或无法唯一绑定的work item不造
program edge，而以`identity("data-flow-transfer-gap-candidate-v1", workItemId, transferKind)`的确定性ID进
`DATA_FLOW_BINDING_UNPROVEN` Gap；该ID必须逐字出现在coverage disposition与`GraphGapDraft.candidateElementIds`。
`exactElementIds`恰为全部M4-owned emitted node/edge。candidate/exact/gap/exclusion按8.0.1互斥闭合；
不得从已生成edge反推denominator。
每个emitted element的非空evidence refs只命中本draft唯一registry entry，且registry无孤儿；external
endpoint必须命中fresh-reopened M1，guard/polarity只能both-null或合法M3 pair。`graphId`除8.4字段外还绑定
完整`worklistAccounting/gapDrafts`。

M4使用8.0.1定义的共享`GraphGapDraft`、identity和coverage闭包；它只为local data-flow Gap写draft，
`scopeGapIds`仍是独立范围处置。Gap不引用provenance draft ID；typed locator就是可重开的source anchor。
M6只从已安装、fresh-reopened M4 draft投影，不接受detached/in-memory Gap注入或替换。任一unknown enum、
错排序/owner/endpoint、伪exact binding、provenance、Gap、identity或coverage不闭合均fatal且不返回partial draft。

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
- **必须保持的不变量**：五图恰一次且 schema/profile/snapshot 一致；外部 endpoint 全存在；local Gap集合`G`与repository scope集合`S`分别验证、只在index和receipt中取去重并集`G ∪ S`；M6不得包含或预报analysis step root/receipt；analysis step set 原子安装并可按identity显式复用，specifier不改边。
- **Gap / fatal / artifact复用**：builder 的local Gap一对一写入`graph-gaps.jsonl`；无locator的`scopeGapIds`只进入四张图coverage、index和module/analysis-step receipt，绝不伪造成GraphGap行。缺图、多图、cross-ref、root/canonical/install/collision或local/scope accounting错误 fatal；下游不能选四张引用。
- **给下游的后置保证**：ProvenCodeFacts和BusinessFlows只凭ProgramGraphsReference获得完整五图、index、Gap/accounting，不需源码parser。
- **明确非目标**：不补 graph edge、不证明 Fact、不编译 Flow、不调用模型。
- **公共测试 seam 与验收**：`specifyGraphSet(fiveReopenedDrafts, controls)`重开并校验五个已安装draft；M1–M4 local Gap只能来自各自已重开draft的共享`gapDrafts`，不得作为detached参数注入或替换。必须分别覆盖：M1零entry源码文件local Gap；M2–M4非空entry local Gap；`BOUNDED_PATH_SET`且没有local Gap时的零字节`graph-gaps.jsonl`与唯一`S`；local和scope并存时index/receipt的`G ∪ S`。缺/多/交换 graph、每一种graph kind的Gap缺失/额外/替换、cross-ref mutation、乱序、partial-install/collision均fail closed；只有M6 exact-seven和analysis-step-store eight-file coherent set才返回 ProgramGraphsReference。
- **Luna/xhigh 测试指南**：创建 `ProgramGraphsPublicationSpecifierTest`，五module artifacts/golden set放 `src/test/resources/analysis/graph/publish/`。RED顺序：M6 exact-seven、analysis step exact-eight；M1零entrylocal Gap与M2/M3/M4各一个entry-local Gap；complete capture的`S=[]`；bounded scope的四份coverage同一`S`、零local时JSONL零字节、index/module/analysis-step receipt只把`S`计一次；local+scope混合时`G ∪ S`；再做缺/多/交换graph、cross-ref/ID-set spoof、Gap字段/identity/coverage/module-gapRefs accounting、乱序、receipt-last/partial-install/collision/fresh-reopen。使用真实module/analysis step stores，禁止mock cross-validator/canonical/root。命令：`mvn -Dtest=ProgramGraphsPublicationSpecifierTest test`；禁网络/build。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `analysis/graph/publish/`，实现 public `ProgramGraphSetPublicationSpecifier/ProgramGraphsReference`；只读M1–M5 files，cross-ref→semantic files→M6 install/receipt→typed analysis-step-store receipt-last。不得生成旧single publication summary、预报root/receipt、补边或少图。跨分析步骤变更MUST STOP交Sol/ultra并按DESIGN升级用户。

### 8.0.1 模块 artifact wire schemas

M1–M5使用 DESIGN 13.3 `ModuleArtifact<T>` envelope并采用8.1 ProgramGraph records；M6直接安装七个analysis step schema注册的JSON/JSONL semantic bytes而无summary envelope。`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-code-structure/code-structure-draft.json` | `program-graphs-code-structure-draft-v3` / `PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT` | exact `graph-profile`；VerifiedSourceInventory `source-inventory/verified-snapshot`；ApplicationDiscovery `application-profile/capability-report/entry-points/mapper-catalog` ArtifactReferences | `graphKind=CODE_STRUCTURE!`、`graphId!`、`snapshotId!`、`applicationProfileId!`、`graphProfileRef!`、`entryIds[]!`、`nodes[]!`、`edges[]!`、`gapDrafts[]!`、`provenanceDrafts[]!`、`coverage!`；nodes/edges/gaps/provenance按ID |
| `modules/02-call-graph/call-graph-draft.json` | `program-graphs-call-graph-draft-v3` / `PROGRAM_GRAPHS_CALL_GRAPH_DRAFT` | exact M1、`graph-profile`、VerifiedSourceInventory两项、ApplicationDiscovery `entry-points/mapper-catalog` ArtifactReferences | M1同形payload且`graphKind=CALL!`；每个CALL_TARGET/JAVA_METHOD_TO_XML_STATEMENT有唯一`CALL_RETURN` pair，edge保留resolution/rule/evidenceDraftRefs并按edgeId |
| `modules/03-control-flow/control-flow-draft.json` | `program-graphs-control-flow-draft-v3` / `PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT` | exact M1/M2、`graph-profile`、VerifiedSourceInventory两项、ApplicationDiscovery `entry-points` ArtifactReferences | M1同形payload且`graphKind=CONTROL_FLOW!`；nodes/edges/gaps/provenance按ID，每entry的`semanticTraversalOrder[]!`按canonical DFS顺序；每guard的TRUE/FALSE或typed terminal/Gap disposition必备 |
| `modules/04-data-flow/data-flow-draft.json` | `program-graphs-data-flow-draft-v2` / `PROGRAM_GRAPHS_DATA_FLOW_DRAFT` | exact M1/M2/M3、`graph-profile`、VerifiedSourceInventory `source-inventory/verified-snapshot` ArtifactReferences | M1同形identity且`graphKind=DATA_FLOW!`；`nodes[]!/edges[]!/gapDrafts[]!/provenanceDrafts[]!/coverage!`按各自ID；external endpoints直接对fresh-reopened M1–M3校验，global graph index只由M6稍后生成；`worklistAccounting!{enqueuedWorkItemIds[]!,processedWorkItemIds[]!,overLimit!}` |
| `modules/05-evidence-graph/evidence-graph-draft.json` | `program-graphs-evidence-graph-draft-v2` / `PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT` | exact M1–M4、`graph-profile`、VerifiedSourceInventory `source-inventory/verified-snapshot` ArtifactReferences | `graphKind=EVIDENCE!`及M1共通identity字段；`nodes[]!`为下述`EvidenceNodeV2`且按evidenceNodeId，`edges[]!`使用下述EvidenceEdge并按ID；`coverage!`逐program element闭合 |
| `modules/06-publish/<seven registered semantic filenames>` | 各public schema/type；无summary envelope | M1–M5 IDs/SHAs | 一次module install恰五图+`graph-index.json`+`graph-gaps.jsonl`；module receipt绑定七descriptors；禁止analysis step root/receipt或八项published list；AnalysisStep store provenance绑定M6 reference |

M2 不能把上表 M1 行理解为“持有一个 Java draft 就等于依赖 M1”。M1→M2 handoff 必须使用
`CodeStructureGraphDraftReference` 经 canonical store 与 `PersistedCodeStructureGraphReader` fresh
reopen；store 验证 module publication，domain reader 再验证该行的 exact address、单 payload
descriptor/envelope、receipt、upstream、controls 和 payload identity，最后才产生
`ReopenedCodeStructureGraph`。M2 module 的 own upstream 仍按上表 M2 行保存；这个 in-process
aggregate 不新增 schema、artifact 或第 53 项 reader-visible 文件。

`graphProfileRef`是完整content-addressed `ArtifactReference{artifactId,sha256}`，固定prefix=`graph-profile`；它不是自由字符串或仅语法合法的profile key。M1–M5必须逐字使用同一个ref，将其列入`upstreamArtifacts`，并在每个graph payload中重复该typed ref；graphId identity绑定两个字段且fresh reopen验证profile bytes。M6完整module fixture用一次install request绑定M1–M5五个draft ArtifactReferences，七个standalone payload不重复envelope。

M1–M4 draft 的 `DraftProgramNode` 字段为`nodeId/kind/canonicalValue/owningEntryIds/evidenceDraftRefs`，`DraftProgramEdge`为`edgeId/kind/fromNodeId/toNodeId/ruleId/resolution/guardNodeId?/polarity?/evidenceDraftRefs`，全部required，仅guard/polarity可在不适用时null。每一个draft另有一份同一module内的`provenanceDrafts[]!` registry；`evidenceDraftRefs`只能引用其中的ID，所有被引用ID必须恰有一条entry，且每条entry至少被一个node或edge引用。`ProvenanceDraftV1`固定字段为`provenanceDraftId! / ruleId! / sourceLocator! / sourceFileSha256! / excerptSha256!`：locator逐字段采用`SourceLocatorV1`，full-file SHA-256绑定冻结文件，excerpt SHA-256绑定该连续byte range。其identity由`ruleId + fileId + sourceFileSha256 + locator byte range + excerptSha256`分帧计算；不得使用绝对路径、显示名、顺序号或source search结果。M5只可按此locator重新打开VerifiedSource bytes、重算两个digest后生成`SourceExcerptV1`和最终Evidence node；它不得重新解析语义结构或以字符串邻近回填span。`canonicalValue`只保存该program element的版本化语义值（FQN、route、symbol、operator等），不得保存`path:line`或冒充source evidence。nodeId在整个ProgramGraphs graph set内全局唯一；from/to可指本图node或M1–M4已发布draft中的node，后图不得重新声明前图node。M2–M5在自己构建时直接对fresh-reopened predecessors验证external endpoints；`graph-index.json`尚不存在，只有M6稍后汇总全局`nodeId→owningGraphKind`并再次验证。

M1–M4 `coverage`固定为`candidateElementIds[]!/exactElementIds[]!/gapDispositions[]!{candidateElementId!,gapId!}/exclusionDispositions[]!{candidateElementId!,reasonCode!,evidenceRefs[]!}/scopeGapIds[]!/closed!`；四个ID/disposition数组按program element ID，candidate集合必须恰等于其余三个互斥分子的element ID union。M5固定为`candidateProgramElementIds[]!/evidencedProgramElementIds[]!/closed!`，两集合必须相等；admitted program element缺evidence是fatal而不是M5 Gap。`scopeGapIds`解释仓库范围，不代替任何已发现element的处置；bounded示例因此`closed=false`。

M1–M4必须区分三种不能证明的情况，不能因为它们都叫“Gap”就使用同一种假数据补齐：

| 类别 | 所在位置 | 必须有源码 locator | `candidateElementIds` | `affectedEntryIds` | 是否写 `graph-gaps.jsonl` |
| --- | --- | --- | --- | --- | --- |
| M1 已验证源码文件上的局部 Gap | `CODE_STRUCTURE.gapDrafts` | 是 | 非空 | 只列可证明受影响的entry；入口归属尚未建立时允许空 | 是 |
| M2–M4 入口内局部 Gap | owner graph 的 `gapDrafts` | 是 | 非空 | owner `entryIds` 的非空子集 | 是 |
| 仓库范围不完整 | M1–M4 `coverage.scopeGapIds` | 否 | 不适用 | 不适用 | 否 |

第一类的“空 entry”不表示这个问题影响所有入口，也不表示没有影响；它只诚实表示：程序已经在一个
`VerifiedSourceInventory`文件的确定span发现解析/结构问题，但在不猜测的前提下还不能把该文件问题归给
某个已发现入口。例如一个仓库只有损坏的`MalformedController.java`，ApplicationDiscovery合理地给出
`entryIds=[]`；M1仍须保存该文件的解析Gap，不能因没有entry而丢失，也不能制造一个Controller entry。
v0中允许这种空owner的reason只来自CODE_STRUCTURE profile的源码文件级闭集：
`JAVA_PARSE_UNSUPPORTED`、`JAVA_PACKAGE_DECLARATION_UNSUPPORTED`、
`MYBATIS_XML_ENTITY_FORBIDDEN`、`MYBATIS_MAPPER_UNSUPPORTED`、
`MYBATIS_XML_LOCATOR_UNSUPPORTED`、`MYBATIS_XML_PARSE_UNSUPPORTED`、
`CONFIGURATION_MAPPING_UNSUPPORTED`、`CONFIGURATION_INDENTATION_UNSUPPORTED`、
`CONFIGURATION_RESOURCE_DYNAMIC`、`MYBATIS_DYNAMIC_SQL_UNSUPPORTED`。新增reason必须先升级profile；
M6不通过文字猜测reason类别。

前两类local unsupported、ambiguous或over-limit graph item使用同一个typed carrier；它是每个draft内
required、按`gapId`排序且ID distinct的`gapDrafts[]!`，不另建sidecar或detached M6参数：

~~~text
GraphGapDraft(
  gapId!, reasonCode!, affectedEntryIds[]!, candidateElementIds[]!, sourceLocator!)

GraphGapIdentityMaterialV1(
  graphKind!, reasonCode!, affectedEntryIds[]!, candidateElementIds[]!, sourceLocator!)
~~~

`GraphGapDraft`不重复保存`graphKind`；它由封闭自己的M1–M4 draft唯一提供。`reasonCode`必须是该
graph profile注册的版本化local Gap code；两个ID数组均按UTF-8严格递增且distinct，
`candidateElementIds`始终非空。locator始终非空，并逐字段命中同一snapshot的一项
VerifiedSourceInventory文件及一个非空连续span。`affectedEntryIds`必须是owner draft `entryIds`的子集：
CALL/CONTROL_FLOW/DATA_FLOW时必须非空；CODE_STRUCTURE时只有上表已注册的源码文件级local reason、
且M1不能从现有结构/发现输入证明entry owner时才可为空。M1能证明owner时必须写准确子集，禁止用全仓
entry denominator兜底。这个条件不新增wire字段或自由scope枚举，而是owner graph kind、reason registry、
locator和entry集合共同构成的validation不变量。

只有同一graph kind、reason、affected-entry集合和source span的一个local occurrence才能把多个candidate
IDs合并为一条draft，否则必须拆分。不得保存自由detail、provenance draft ID、绝对路径，或为让数组非空
而制造entry/candidate。M1零entry的文件级candidate仍由版本化CODE_STRUCTURE candidate rule从
snapshot、file、reason和span确定，并必须恰好进入一条coverage gap disposition。

identity不由builder任选。M1–M4和fresh reader都必须重算：

~~~text
gapId = "graph-gap:" + lowercaseHex(SHA-256(
  frame(UTF8("program-graph-local-gap-id-v1")) ||
  frame(canonicalJson(GraphGapIdentityMaterialV1))))
~~~

identity material中的`graphKind`取owner draft；其余四项逐字取`GraphGapDraft`的非identity字段，canonical JSON字段和数组
顺序就是上列顺序及已排序ID。空`affectedEntryIds`仍作为canonical空数组进入identity，不能省字段或
用null代替。每个M1–M4 `graphId`都绑定完整排序`gapDrafts`，所以任一reason、entry、candidate、locator或
gapId mutation同时破坏Gap identity和graph identity。

coverage关系固定为：对每条draft `g`，
`g.candidateElementIds == sorted({d.candidateElementId | d in coverage.gapDispositions && d.gapId == g.gapId})`；
反向每个gap disposition恰命中一条draft，`gapDrafts`不得命中`scopeGapIds`，也不得有零candidate或额外draft。
local Gap已经完整处置candidate，因此可与`coverage.closed=true`共存；成功安装的draft若候选分母未知则
fatal而不是`closed=false` partial draft。

仓库范围不完整是另一条无locator的accounting链。`PersistedProgramGraphInputReader`从fresh-reopened
`source-input.json + verified-snapshot.json + source-inventory.jsonl`一次性导出canonical集合`S`：

~~~text
COMPLETE_CAPTURE && repositoryCompletionEligible=true  => S=[]
BOUNDED_PATH_SET && repositoryCompletionEligible=false  => S=[scopeGapId]
其他组合                                                => GRAPH_REFERENCE_BROKEN

SourceScopeGapIdentityMaterialV1(
  snapshotId, inventoryScopeKind, inventoryScopeRoot?, repositoryCompletionEligible,
  sourceInventoryRef, verifiedSnapshotRef)

scopeGapId = "graph-scope-gap:" + lowercaseHex(SHA-256(
  frame(UTF8("program-graph-scope-gap-id-v1")) ||
  frame(canonicalJson(SourceScopeGapIdentityMaterialV1))))
~~~

nullable `inventoryScopeRoot`在identity JSON中必须存在：COMPLETE_CAPTURE为null，BOUNDED_PATH_SET为已验证的
repository-relative root。M1–M4必须逐字复制同一`S`到各自`coverage.scopeGapIds`；不得各自重新解释、
增删或改ID。M5 basis仍须匹配该source scope，但Evidence coverage没有`scopeGapIds`。因此每个M1–M4 module
completion/receipt的`gapRefs`恰为`localGapIds_i ∪ S`的排序去重union，且成功draft的
`coverage.closed == S.isEmpty()`。`closed`表示候选accounting与仓库范围是否闭合，不表示“没有local Gap”；
module status则由`localGapIds_i ∪ S`是否为空决定`SUCCEEDED/SUCCEEDED_WITH_GAPS`。

本轮校正后的模块责任和数量关系固定如下；这是对既有五图主线的边界澄清，不新增模块或文件：

| 模块 | local Gap责任 | scope责任 | fresh reopen与数量/identity | 必须拒绝 |
| --- | --- | --- | --- | --- |
| M1 CODE_STRUCTURE | 对每个已定位的源码文件candidate生成恰一个disposition；入口owner可证明则写准确非空子集，不可证明且reason属于上述文件级闭集时允许空owner | 复制唯一`S` | reader重验每个local `gapId`、locator与coverage双向映射；`module.gapRefs = L1 ∪ S` | 文件级candidate丢失、假entry、空candidate/null locator、未知reason、scope被当local |
| M2 CALL | 每条local Gap必须有非空entry owner | 复制同一`S` | fresh-reopen M1和输入基础后重验；`module.gapRefs = L2 ∪ S` | 空entry、detached Gap、scope漂移、broken call candidate accounting |
| M3 CONTROL_FLOW | 每条local Gap必须有非空entry owner | 复制同一`S` | fresh-reopen M1/M2和输入基础后重验；`module.gapRefs = L3 ∪ S` | 空entry、detached Gap、scope漂移、broken path/terminal accounting |
| M4 DATA_FLOW | 每条local Gap必须有非空entry owner | 复制同一`S` | fresh-reopen M1/M2/M3和输入基础后重验；`module.gapRefs = L4 ∪ S` | 空entry、detached Gap、scope漂移、broken work-item accounting |
| M6 PUBLISH | 不创建local Gap；把`L1…L4`一对一投影为GraphGap行 | 不创建scope ID；重算并验证`S`，只在coverage/index/receipts引用 | fresh-reopen M1–M5；JSONL行数=`Σ|Li|`；index与两层receipt `gapRefs=G ∪ S`；所有集合排序去重且identity可重算 | scope投影成行、local行缺失/额外/改写、M2–M4空entry、`G`/`S`重叠、任一module refs不等式 |

这里的“恰一个disposition”是每个candidate的处置基数，不是每个文件最多一条Gap；同一文件中不同
occurrence可以有多个candidate和多条local Gap，但每个candidate只能进入一个exact、Gap或exclusion分子。

生产责任不转移给M6：M1在结构/配置/XML candidate site（包括零entry的已验证文件级site），M2在call/return/Mapper binding site，M3在
reachable control/profile-stop site，M4在value-transfer/work-item site决定unsupported/ambiguous/
over-limit时，各自在返回draft前计算candidate IDs、affected entries、reason和exact source locator并写
上述carrier。已知local预算停止只有在完整candidate集合已经枚举且其余coverage仍可闭合时才是over-limit
Gap；若预算/解析停止导致剩余candidate denominator未知，则按既有resource/accounting code fatal，禁止
partial draft。

M5不复用ProgramEdge字段冒充“evidence指向program edge”。`EvidenceNodeV2(evidenceNodeId,kind,sourceExcerpt,ruleApplication)`是closed union：`SOURCE_EXCERPT{sourceExcerpt!,ruleApplication=null}`或`RULE_APPLICATION{sourceExcerpt=null,ruleApplication!}`，两个required-nullable槽必须都出现且恰一非null。`sourceExcerpt`逐字段使用DESIGN §13.2 `SourceExcerptV1`；`RuleApplicationV2(ruleId,ruleVersion,inputProgramElementIds)`三项required，input IDs按UTF-8排序。一个SOURCE_EXCERPT只能表示同一文件的一个连续span；不连续证据必须拆为多个nodes，禁止`path:line`、` + `、`...`或合成raw。

`EvidenceEdge`固定为`edgeId/kind/evidenceNodeId/subjectGraphKind/subjectProgramElementId/ruleApplicationNodeId`，六项全required；`subjectProgramElementId`必须是M1–M4已登记nodeId或edgeId，`subjectGraphKind`必须匹配graph index owner，rule node必须存在于M5 nodes。M6发布public五图时，把M1–M4的`evidenceDraftRefs`确定性替换为排序非空`evidenceNodeIds`，并把M5 v2 nodes原样写入`program-graphs-evidence-graph-v2`；public `ProgramNode/ProgramEdge`不再含draft ref。unknown binding必须不生成edge并写Gap，不能把endpoint设为故事值。M1–M5 draft的payload和module envelope一起入artifactId；M6 public standalone identity按下节的self-exclusion公式。schema/registry/sort/source rule任何变化先设计+升version；下游禁止打开AST/XML补字段。

### 8.0.2 M6 public wire 与 publication seam

本节冻结M6七个semantic payload的完整wire；M1–M4内部draft schema升级不自动升级public graph schema。全部字段required且不得有未列字段，只有标成`?`的值required-nullable。注册项恰为：

| fileName | artifactType | schemaVersion / policy |
| --- | --- | --- |
| `code-structure-graph.json` | `PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH` | `program-graphs-code-structure-graph-v1` / `STANDALONE_JSON` |
| `call-graph.json` | `PROGRAM_GRAPHS_CALL_GRAPH` | `program-graphs-call-graph-v1` / `STANDALONE_JSON` |
| `control-flow-graph.json` | `PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH` | `program-graphs-control-flow-graph-v1` / `STANDALONE_JSON` |
| `data-flow-graph.json` | `PROGRAM_GRAPHS_DATA_FLOW_GRAPH` | `program-graphs-data-flow-graph-v1` / `STANDALONE_JSON` |
| `evidence-graph.json` | `PROGRAM_GRAPHS_EVIDENCE_GRAPH` | `program-graphs-evidence-graph-v2` / `STANDALONE_JSON` |
| `graph-gaps.jsonl` | `PROGRAM_GRAPHS_GRAPH_GAP` | `program-graphs-graph-gap-v1` / `CANONICAL_JSONL` |
| `graph-index.json` | `PROGRAM_GRAPHS_GRAPH_INDEX` | `program-graphs-graph-index-v1` / `STANDALONE_JSON` |

四张program graph的公共document逐字段为：

~~~text
PublicProgramGraphV1(
  schemaVersion, artifactType, artifactId, graphKind, graphId,
  snapshotId, applicationProfileId, graphProfileRef, entryIds[],
  nodes[], edges[], coverage)
ControlFlowPublicFields(semanticTraversalOrder[], terminalDispositions[])
DataFlowPublicFields(worklistAccounting)

ProgramNode(nodeId, kind, canonicalValue, owningEntryIds[], evidenceNodeIds[])
ProgramEdge(edgeId, kind, fromNodeId, toNodeId, ruleId, resolution,
            guardNodeId?, polarity?, evidenceNodeIds[])
~~~

`schemaVersion/artifactType/graphKind`逐行采用上表；其余common identity、entries、coverage和graph-specific accounting逐字投影相应fresh-reopened M1–M4 draft。CODE_STRUCTURE/CALL禁止三个variant字段；CONTROL_FLOW必须有`semanticTraversalOrder/terminalDispositions`而禁止`worklistAccounting`；DATA_FLOW必须有`worklistAccounting`而禁止前两项。四张public program graph都不携带内部`gapDrafts/provenanceDrafts/evidenceDraftRefs`；Gap只进入下述JSONL。`nodes/edges`除证据字段外逐字段复制draft。

每个public node/edge的`evidenceNodeIds`恰等于M5 `EvidenceEdge`中`subjectGraphKind`和`subjectProgramElementId`匹配该元素、且kind分别为`SUPPORTS_PROGRAM_NODE`或`SUPPORTS_PROGRAM_EDGE`的`evidenceNodeId`排序去重集合；集合必须非空且每项命中一个`SOURCE_EXCERPT` node。每条support edge的`ruleApplicationNodeId`还必须命中一个`RULE_APPLICATION` node，其`inputProgramElementIds`包含该subject。缺、错kind、错owner、额外support或孤立evidence一律fatal；M6不得从draft ref、source或rule重新合成证据。

`evidence-graph.json`使用同一common header至`entryIds`，随后严格为`nodes[]/edges[]/coverage`：nodes是8.0.1的`EvidenceNodeV2`，edges是`EvidenceEdge`，coverage严格为`candidateProgramElementIds[]/evidencedProgramElementIds[]/closed`。它不使用`ProgramNode/ProgramEdge`，也不自带`evidenceNodeIds`；两个coverage集合都恰等于四张public program graph全部nodeId和edgeId的union且`closed=true`。

`graph-gaps.jsonl`每一行严格为：

~~~text
GraphGapV1(schemaVersion, gapId, graphKind, reasonCode,
           affectedEntryIds[], candidateElementIds[], sourceLocator)
~~~

`schemaVersion=program-graphs-graph-gap-v1`，`graphKind`只允许CODE_STRUCTURE/CALL/CONTROL_FLOW/DATA_FLOW；Evidence缺口是fatal。M6按owner graph kind遍历fresh-reopened M1–M4 `gapDrafts`，每条一对一输出：逐字复制`gapId/reasonCode/affectedEntryIds/candidateElementIds/sourceLocator`，只补owner `graphKind`与public `schemaVersion`；它不重算候选语义、不解析source、不合并不同draft，也不补写reason、entry或locator。每一行的candidate和locator必须非空；只有CODE_STRUCTURE源码文件级local Gap允许`affectedEntryIds=[]`，CALL/CONTROL_FLOW/DATA_FLOW行必须有至少一个entry。Evidence缺口是fatal。

`scopeGapIds`绝不产生GraphGap行。它们没有源码occurrence、candidate或entry owner；若M6把它们投影为
`sourceLocator=null`、`candidateElementIds=[]`，或用某张图的全部`entryIds`代填，就会把“这次只分析了
部分仓库”伪装成“某段源码里的局部问题”。M6只能在fresh-reopened VerifiedSourceInventory证明
`BOUNDED_PATH_SET/repositoryCompletionEligible=false`并重算8.0.1的`S`后接受这些ID。exclusion同样不是
Gap行。`graph-gaps.jsonl`的精确行数为`|L1|+|L2|+|L3|+|L4|`，其中`Li`是owner graph的local
`gapDrafts`；零local Gap为exact zero bytes，即使`S`非空。否则每行canonical JSON、LF结尾且无空行。

M6在写任何payload前重验8.0.1的local Gap identity、按graph kind决定的entry条件、locator source basis、
coverage双向等式、canonical source scope集合`S`及每个M1–M4 module receipt `gapRefs`。缺失/额外/重复
draft、错owner、错identity、M2–M4空entry、一个candidate映射多个Gap、draft与coverage/module refs不等、
scope/local ID重叠或detached Gap注入统一`GRAPH_ACCOUNTING_INVARIANT_BROKEN`；无法fresh-reopen相同
snapshot/profile/controls、重算`S`或验证locator所属source则`GRAPH_REFERENCE_BROKEN`。这些都是carrier
损坏，不是业务Graph Gap；不得安装partial M6 module或analysis-step publication。合法M1零entry源码文件
local Gap及M2–M4 entry-local Gap本身不再是fatal。

`graph-index.json`严格为下列records：

~~~text
GraphIndexV1(
  schemaVersion, artifactType, artifactId, snapshotId, applicationProfileId,
  graphProfileRef, entryIds[], graphs[], nodeCatalog[], edgeCatalog[],
  coverage[], graphGapsRef, gapIds[], status, closed)

GraphArtifactIndexEntryV1(
  graphKind, fileName, artifactType, schemaVersion, graphId, artifactRef)
GraphNodeCatalogEntryV1(nodeId, owningGraphKind, nodeKind)
GraphEdgeCatalogEntryV1(edgeId, owningGraphKind, edgeKind)
GraphCoverageIndexEntryV1(
  graphKind, candidateElementIds[], exactElementIds[], gapCandidateElementIds[],
  excludedCandidateElementIds[], scopeGapIds[], closed)
~~~

header的schema/type固定为index v1注册项；snapshot/application/profile/entries必须与五图及两个upstream step publications一致。`graphs`恰五项，每项引用对应public standalone artifact的`ArtifactReference{artifactId,sha256}`。`nodeCatalog`恰为五图node union（Evidence的`evidenceNodeId`映射为`nodeId`），`edgeCatalog`恰为五图edge union；ID在各catalog内及node/edge两catalog之间全局唯一。每个program edge endpoint/guard、EvidenceEdge的source/rule/subject均必须由catalog唯一解析，owner/kind必须与payload一致；catalog不得漏项或加项。

前四个`coverage` entry从同图coverage投影：`gapCandidateElementIds`取gap dispositions的candidate IDs，`excludedCandidateElementIds`取exclusions的candidate IDs，其余同名字段复制；四项`scopeGapIds`必须逐字等于同一canonical `S`，其`closed`逐字复制，因此bounded scope下四项都为false。Evidence entry把candidate/evidenced分别投影为candidate/exact，其余三个数组为空，entry `closed`逐字复制原图。

`graphGapsRef`是只含local Gap行的完整JSONL artifact reference。令`G`为这些行的`gapId`集合，则
`index.gapIds = sortedDistinct(G ∪ S)`；`graphGapsRef`中的行ID必须恰为`G`，不能错误要求其等于
`index.gapIds`。M6 module completion/receipt与analysis-step receipt的`gapRefs`也都恰为`G ∪ S`。
同一个scope ID即使在四张program graph coverage中各出现一次，在union中只出现一次；四个coverage
occurrence仍保留，以证明任何单图都不能被误读为完整仓库。index `closed`只表示上述引用、catalog和
accounting等式已全部验证，成功安装时固定`true`；它不把已有Gap或bounded scope伪装成fatal。
`status=SUCCEEDED`当且仅当`G ∪ S=[]`，否则恰为`SUCCEEDED_WITH_GAPS`；M6 module completion、
analysis-step receipt status和gap refs必须与它逐字一致。

所有JSON object key按DESIGN §13.3 canonical UTF-8规则；普通ID集合按ID UTF-8 bytes严格递增。例外的语义数组只保留既有定义：M3 traversal/terminal顺序和M4 worklist顺序。`graphs/coverage`按CODE_STRUCTURE、CALL、CONTROL_FLOW、DATA_FLOW、EVIDENCE；catalog按ID；GraphGap行按上述四种graphKind顺序再gapId；receipt descriptors仍按fileName。五图和gap bytes先算，index引用它们后算；五图/index的`artifactId`按`STANDALONE_JSON`删除且只删除自身顶层artifactId，Gap artifact ID按完整exact JSONL bytes计算。绝对root、运行时间、迭代顺序或caller选择不得参与identity。

public Java seam固定为：

~~~java
record ProgramGraphsPublicationInputs(
    AnalysisStepPublicationReference verifiedSourceInventoryPublication,
    AnalysisStepPublicationReference applicationDiscoveryPublication,
    ReopenedCodeStructureGraph codeStructure,
    ReopenedCallGraph callGraph,
    ReopenedControlFlowGraph controlFlow,
    ReopenedDataFlowGraph dataFlow,
    ReopenedEvidenceGraph evidence) {}

ProgramGraphsReference specifyGraphSet(
    ProgramGraphsPublicationInputs inputs, ArtifactControls controls);

record ProgramGraphsReference(AnalysisStepPublicationReference publication) {}
~~~

两个step reference必须分别为`verified-source-inventory`、`application-discovery`并按closed dependency order进入ProgramGraphs receipt；specifier须从analysis-step store fresh reopen二者。五个sealed aggregates必须恰为M1–M5且相同basis/profile/controls/entry denominator，保存的predecessor payload refs逐级精确闭合；M6只取其typed references再从module store fresh reopen五次并与输入逐字段相等。不得接受raw draft/bytes/Path/list、detached Gap或第六张图。M6 module upstream恰为五个draft payload refs；它先原子安装七payload+module receipt，再让analysis-step store绑定该M6 module reference和两个upstream step references安装七payload+`program-graphs-receipt.json`，fresh reopen八文件成功后才返回reference。`ProgramGraphsReference` constructor必须拒绝null或非`program-graphs` key，且不暴露按图选择器。

reference/basis/schema/type/五图数量错误统一`GRAPH_REFERENCE_BROKEN`；catalog/coverage/Gap/status/order守恒错误统一`GRAPH_ACCOUNTING_INVARIANT_BROKEN`；Evidence support/source/rule闭包错误统一`EVIDENCE_GRAPH_INVARIANT_BROKEN`；canonical、resource、install/collision错误沿用DESIGN §13.3稳定code。任何一种都不得改写成GraphGap、返回partial reference或留下reader-visible半套publication。

M6 selector测试必须直接断言七个注册版本、每种record精确字段、resolved evidence且无draft refs、catalog双向闭合、M1/M2/M3/M4各一个local Gap与四图混合集的逐字段一对一投影、scope Gap、空JSONL、status/gapRefs、canonical乱序/identity、两个upstream refs、五次fresh reopen、M6 exact-seven与analysis-step exact-eight/receipt-last；对Gap reason/entry/candidate/locator/gapId、coverage mapping、module `gapRefs`分别做deletion/substitution/extra mutation。M1–M3 reader/builder selector还必须直接断言各自v3 exact field set、required `gapDrafts`、旧v2/unknown version拒绝及共享identity重算；M4 selector对同一identity/closure做等价mutation。测试只用真实canonical stores，禁止mock validator、detached gap、production生成golden或补读source/AST。

**示例分类：STRUCTURAL_WIRE_SPECIMEN（两个隔离的 EvidenceNodeV2 variants，不可replay）。** 字段、closed variant与continuous excerpt完整；offset/digest未从本页未展示的完整source file重算，不能作为golden或与相邻大块的story IDs映射。

~~~jsonl
{"evidenceNodeId":"evidence:1111111111111111111111111111111111111111111111111111111111111111","kind":"SOURCE_EXCERPT","sourceExcerpt":{"locator":{"fileId":"file:2222222222222222222222222222222222222222222222222222222222222222","path":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java","startByte":30100,"endByteExclusive":30128,"startLine":800,"startColumn":9,"endLine":800,"endColumn":37},"rawUtf8":"depotHead.setStatus(status);","rawUtf8Sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},"ruleApplication":null}
{"evidenceNodeId":"evidence:3333333333333333333333333333333333333333333333333333333333333333","kind":"RULE_APPLICATION","sourceExcerpt":null,"ruleApplication":{"ruleId":"java-setter-property-binding","ruleVersion":"v1","inputProgramElementIds":["parameter:service-status","property:depothead-status"]}}
~~~

**示例分类：NARRATIVE_ILLUSTRATION（五个ProgramGraphs模块的隔离故事投影）。** 下列大块只说明图之间的业务关系；它不是ModuleArtifact wire、schema-valid fixture或跨分析步骤replay链。尤其其中`canonicalValue`里的`File.java:line`、`source:*`和story IDs不是v2 source evidence，data-flow故事中的重复`PARAMETER`/`PROPERTY` nodes、数值型worklist与缺失`gapDrafts`也不是M4 v2 wire；exact M4必须引用M1 `PARAMETER/FIELD`并使用上方ID-set accounting/Gap registry。Luna fixtures必须改用上面的exact records并在自己的closure内重算identity；不存在隐式ID remap。

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
| data flow | M4-owned `DEFINITION`、`USE`、`ARGUMENT`、`CRITERION`、`PLACEHOLDER`、`WHERE_PREDICATE`；external endpoint复用M1 `PARAMETER`、`FIELD`、`SQL_COLUMN`而不重声明；edge恰为`DEF_USE`、`ARGUMENT_TO_PARAMETER`、`ASSIGNMENT`、`SETTER_TO_PROPERTY`、`PROPERTY_TO_PLACEHOLDER`、`CRITERION_TO_WHERE`、`PLACEHOLDER_TO_COLUMN`。这里property语义端点就是已有M1 `FIELD`；不存在M1 `PROPERTY` kind。 |
| evidence | source file/span/rule application；LOCATES、PARSED_BY、BOUND_BY、SUPPORTS_PROGRAM_NODE、SUPPORTS_PROGRAM_EDGE |

M1 `PARAMETER`不新增wire字段，但其语义身份必须完整：`canonicalValue`是
`java-parameter-symbol-v1|<declaring METHOD canonical signature>|<zero-based ordinal>|<declared canonical type>`，
nodeId direct preimage同时绑定declaring M1 `METHOD` nodeId、ordinal和declared canonical type/signature，且该
method必须以唯一M1 `DECLARES` edge指向该parameter。M4若不能从fresh-reopened M1逐字重验
这三项就以`GRAPH_REFERENCE_BROKEN` fatal；不得从parameter name、source order或邻近declaration恢复。

M1 `FIELD`同样不新增wire字段；其`canonicalValue`恰为
`java-field-symbol-v1|<declaring TYPE canonical name>|<field name>|<declared canonical type>`，nodeId direct
preimage同时绑定declaring M1 `TYPE` nodeId、field name和declared canonical type/signature，且该type必须
以唯一M1 `DECLARES` edge指向该field。M4不得从setter name、bean convention、receiver simple name或
story `PROPERTY` node恢复该endpoint；缺失/重复/矛盾均为`GRAPH_REFERENCE_BROKEN`。

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

本次M4 slice对第4段的exact walkthrough是：M3先证明Service :800
`depotHead.setStatus(status)`的call/argument在`dhIds`非空guard TRUE下activated；`status`的
`ARGUMENT_SIMPLE_NAME_READ` work item复用该call ordinal 0已有M4 `ARGUMENT`，并从Service M1
`status PARAMETER`生成一条`DEF_USE`。若M2 exact target与fresh-reopened target body又恰证明唯一
`this.status = formal[0]`，M4则把该caller `ARGUMENT`以`SETTER_TO_PROPERTY`连到DepotHead已有M1
`status FIELD`；target body本身的formal read与direct field write同时分别产生`DEF_USE`和
`ASSIGNMENT`。这些edge的单一guard pair均为上述TRUE context，external PARAMETER/FIELD不在M4重声明。
若setter只是同名、body缺失/生成、有transform/multi-write或field `DECLARES`不唯一，该段就以
line :800 locator的M4 local Gap处置，绝不从`setStatus`拼出FIELD。

缺任一段只能形成 Gap，不能跨缺口连一条“看起来正确”的长 edge。

### 8.4 Identity、accounting 与预算

nodeId 绑定 snapshot、graph kind、semantic kind、canonical value、source identity 和 rule version；edgeId 再绑定 exact endpoints、rule、resolution、guard/polarity。需要直接predecessor证明的投影edge还绑定前驱edge ID/ordinal。M1–M4 graphId 最后绑定全部排序 nodes/edges、排序`gapDrafts`、图特有traversal或worklist accounting、完整`graphProfileRef.artifactId+sha256`和 coverage；M5按自己的evidence records计算graphId。禁止用display key或仅ID替代profile bytes reference。

每张图必须满足 discovered candidates = admitted exact + gap + reasoned exclusion。跨图index建立全局唯一node/edge catalog，验证ProgramEdge所有外部endpoint以及EvidenceEdge的subject/rule refs；unknown node/edge不得被默默丢弃。Evidence graph从来不把program edge ID塞进`toNodeId`。

预算分图执行 maxNodes/maxEdges，并覆盖 AST/XML/SQL chars、call depth、CFG states、dataflow worklist、evidence spans 和 traversal depth。超限不截断为 COMPLETE。M4 worklist必须保存完整enqueued/processed ID集合；本v2在max+1时fatal，不安装`overLimit=true`或只剩计数的partial draft。

### 8.5 安全与 failure codes

所有 parser 输入来自 verified handles。XML 禁外部 DTD/entity/schema/network。不开客户 classloader，不做 reflection runtime，不运行 annotation processor、MyBatis 或 SQL。

稳定 code：

GRAPH_PROFILE_INVALID、GRAPH_REFERENCE_BROKEN、GRAPH_ACCOUNTING_INVARIANT_BROKEN、CODE_STRUCTURE_INVARIANT_BROKEN、CALL_TARGET_AMBIGUOUS、CALL_RETURN_PAIR_INVALID、CFG_POLARITY_MISSING、CFG_TERMINAL_UNRESOLVED、DATA_FLOW_BINDING_UNPROVEN、DATA_FLOW_WORKLIST_LIMIT_EXCEEDED、EVIDENCE_GRAPH_INVARIANT_BROKEN、EVIDENCE_SOURCE_REOPEN_MISMATCH、XML_SECURITY_POLICY_UNENFORCEABLE、XML_EXTERNAL_RESOLUTION_ATTEMPT、PROGRAM_GRAPHS_RESOURCE_LIMIT_EXCEEDED。

M4分类固定：可定位且前驱一致，但本profile不支持的call/read/write/setter shape、零/多
reaching definitions、loop/capture/alias、multi-guard context以`DATA_FLOW_BINDING_UNPROVEN`写M4的共享
`GraphGapDraft` carrier；M1/M2/M3 reference、source hash/basis、PARAMETER/FIELD identity、唯一AST→block mapping或
predecessor closure漂移用`GRAPH_REFERENCE_BROKEN` fatal；伪exact、重复binding、work-item/gap/provenance/coverage守恒错误用
`GRAPH_ACCOUNTING_INVARIANT_BROKEN` fatal；worklist max+1用`DATA_FLOW_WORKLIST_LIMIT_EXCEEDED` fatal。
fatal不返回draft、不安装M4 module；integrity错误不得改写成Gap。

### 8.6 测试 seam 与验收

- 五个 exact filenames 是 first-class artifacts；缺一、多一或交叉替换必须失败。
- M1–M4各自的public builder/reader必须覆盖一个local Gap正例；五字段、owner graph kind identity、coverage映射、module `gapRefs`任一mutation fail closed，M1–M3旧v2必须拒绝。
- M1必须覆盖`entryIds=[]`的malformed Java与forbidden XML entity：每个已验证文件site产生一条candidate非空、locator非空、`affectedEntryIds=[]`的CODE_STRUCTURE local Gap；COMPLETE_CAPTURE下该local Gap不使`coverage.closed`变为false。M2–M4必须拒绝任何`affectedEntryIds=[]`的local Gap。
- source scope集合必须由fresh reopen的输入重算：COMPLETE_CAPTURE恰为`S=[]`；BOUNDED_PATH_SET恰为一个稳定`scopeGapId`。只有`S`而没有local Gap时，`graph-gaps.jsonl`必须为零字节，四张program graph coverage都携带同一个`S`，index与两层receipt各自的集合只计该ID一次；local与scope并存时再验证`G ∪ S`。删除、替换、额外、重复或把scope伪装成GraphGap行都必须fail closed。
- Controller→Service、Service→Mapper、Mapper→XML 分段 deletion mutation 分别失败。
- TRUE/FALSE、terminal、call/return edge mutation 不得用源码顺序补回。
- M4首slice必须覆盖M3-activated call×ordinal分母、external M1 parameter、work-item ID集合相等、typed Gap registry；ordinal/parameter、processed ID、gap reason/locator或predecessor ref任一mutation fail closed。
- M4下一slices必须覆盖AST-role read/write/setter分母、shadow-safe singleton reaching definition、RHS-before-write、
  `USE/ARGUMENT`复用、external M1 `FIELD`、三种新edge端点矩阵与work-item union；block/guard/
  declaration/field/call-target/assignment-body任一mutation fail closed，join/compound/fake-setter必须为typed Gap。
- status 和 ids 每一段 dataflow deletion 都使对应 Fact 不可证明。
- 同名 decoy type/method/XML statement 不能被字符串匹配选中。
- Evidence span hash、rule ID 或 graph endpoint mutation fail closed。
- 不同 root/input order 产生相同五图 canonical bytes。
- standard DOCTYPE 零网络 lookup；external entity mutation fatal。

验收分两层：结构验收要求八个 exact files、五图 envelope/roots/cross-references/accounting 全闭合；DepotHead 业务验收要求 8.3 的八段闭包逐段 mutation 可使对应 edge/Fact 不成立。两层都通过且无字符串猜测旁路，分析步骤“程序图” 才算目标实现完成。

### 8.7 已冻结裁决：实现者不得自由推断

- 图种类和五个 filenames 恰为本文件定义的五项；不能合并为 repository blob，也不能把第六种临时图加入 production set。
- node/edge kind 只来自版本化 registry；不支持的语法必须产生8.0.1定义的local Gap，不能发明自由字符串kind。M1只有在源码文件级candidate尚无可证明入口owner时允许空`affectedEntryIds`；M2–M4必须关联非空entry。
- 仓库范围未完整只用canonical `scopeGapIds`表达；它没有source locator或program candidate，不能写入`graph-gaps.jsonl`，也不能伪造入口。
- call、control、data binding 必须逐段 EXACT；simple name、源码邻近、声明顺序和注释不能补边。
- M4不得等待M6 graph index才校验external endpoint，也不得重声明M1 `PARAMETER/FIELD`或发明M1 `PROPERTY`；每个builder的Gap必须随已安装draft持久化，M6只汇总不补写reason/locator。
- Evidence graph 证明 graph provenance，Proof 在 分析步骤“已证明代码事实” 证明 Fact；两者不可合并或互相替代。
- 五图作为一个原子analysis step set安装并按完整identity复用；不能引用四张再现场生成第五张。
- parser core、graph storage、worklist 和并行实现可自行选择；graph 边界、registry、identity、Gap/fatal 与 DepotHead closure 不得改变。

## 9. 当前实现成熟度审计

Wire Reset后的`org.sourceanalysis.app.analysis.graph`已有受限的M1–M4切片，并正在接入M5 evidence builder及其receipt-last持久化/可信重开；M6 public publication仍在实现中。它们不等于完整的“程序图”分析步骤，也不等于已完成的全仓库业务分析。当前实现状态必须与上方目标设计分开阅读。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 目标package、Maven身份和JDK 17 Toolchain已经就位；通用wire头门禁只负责拒绝非`SOURCE_ANALYSIS/v1`输入。 |
| **已实现（M1 有界切片）** | `CodeStructureGraphBuilder`已能对传入的已验证 UTF-8 Java、标准 MyBatis XML/静态 SQL 和静态 YAML 产生 code-structure draft：声明、配置、Mapper、表/列节点与关系均带 v2 `ProvenanceDraftV1`。Java 用 AST 范围，XML 表/列用经标签和属性校验的范围，嵌套 YAML 键按 `.` 展平；遇到解析、实体、动态资源或不安全映射则记 Gap。`CodeStructureGraphModulePublisher`已将该 draft 按 receipt-last 安装并从 module store fresh reopen。当前直接验证为 8 个 M1 builder/publisher 测试通过。 |
| **部分实现（M1 的产品组装）** | 代码结构 builder 的公开测试 seam 仍接收结构化的 `CodeStructureSource` 与 `CodeStructureDiscovery` 输入。由正式运行核心重新打开已验证源码清单和应用发现 artifacts、构造这两个输入并驱动 M1 的路径尚未落地；因此当前模块产物不是完整分析步骤的 reader-visible 输出。 |
| **部分实现（M1→M2 可信重开）** | `PersistedCodeStructureGraphReader`、sealed `ReopenedCodeStructureGraph`、exact payload parser和`ProgramGraphInputBasis`已经实现并由真实 canonical module store 验证：M2只能先重开 M1 receipt/payload，核对 address、schema/type、七个上游引用、controls、profile和分母，再读取结构 draft。改变 fresh-reopened source controls 会在解析前以`GRAPH_REFERENCE_BROKEN`拒绝。M2 execution 与自己的 receipt-last 持久化已经使用这条 seam；完整产品运行核心的 M1/M2 组装仍受上行“产品组装”缺口限制。 |
| **部分实现（M2 有界调用图）** | `CallGraphBuilder`已对冻结 fixture 产生唯一 Controller→Service、Service→Mapper、Mapper Java→XML statement 以及 call/return edges；重载 handler 或受显式 import 影响的 receiver 都记录 Gap，绝不按源码顺序或简单名称猜 target。`CallGraphExecution`会先以同一 reopened inputs 重开 M1，再构建并由`CallGraphModulePublisher`将调用图写为独立 M2 receipt-last artifact；后者把已重开 M1 payload 加入八项上游 lineage。上一轮发现的直接 Java 调用 provenance 断链已在当前有界切片修复：builder 会把调用 AST span 对应的`ProvenanceDraftV1`收入`CallGraphDraft.provenanceDrafts`，`CallGraphDraft`会拒绝节点或边引用 registry 中不存在的 evidence draft。完整 receipt mutation matrix、Mapper binding accounting 与完整仓库验收仍未实现。 |
| **部分实现（M2→M3 可信重开）** | `PersistedCallGraphReader`和sealed `ReopenedCallGraph`已由真实 canonical M1/M2 modules 验证 address、type/schema、八项 upstream、controls、producer/completion、payload、profile与同一 M1/source/discovery basis。当前 M3 公共测试已通过这条 fresh-reopen seam 取得 M1/M2，并在解析前核对相同 basis、graph profile 与 M2 保存的 M1 payload lineage；这只证明当前有界输入链可用，不代表完整 mutation matrix 已完成。 |
| **部分实现（M3 线性与单 guard 控制流切片）** | `ControlFlowGraphBuilderTest`当前验证一个单入口 fixture：builder 只投影已经由 M2 证明的 call/return edges，并生成`ENTRY`、`BASIC_BLOCK`、`ENTRY_RETURN_TERMINAL`和`CALLEE_RETURN_TERMINAL`。对上游 Service 中的`if (status == null) { return; }`，当前实现生成一个`GUARD`：TRUE edge 的`guardNodeId`指向该 guard、`polarity=TRUE`并到达 callee return terminal；FALSE edge 使用同一`guardNodeId`与`polarity=FALSE`到达 guard 后的正常续接节点。这个受支持形状不再产生`PROFILE_STOP_TERMINAL`。当前fixture没有direct-throw callee，因而尚未验证“M2 RETURN始终保留为structural frame link、throw不生成或激活continuation”的目标语义。当前仅支持“单个、无 else、then 子树含 return”的 guard；一般多重/嵌套 if、else、throw、loop budget、多入口 ownership、完整 DFS/reachability、跨调用栈语义、终止节点删除/返回配对变异测试与 M3 module publication仍未实现，因此该 GREEN 不能代表控制流图或分析步骤“程序图”完成。 |
| **部分实现（M4 有界数据流）** | 唯一`buildDataFlow(DataFlowInputs, DataFlowGraphProfile)` seam、M3 fresh-reopen、v2 records、argument→parameter、parameter/local singleton `DEF_USE`、direct local/field `ASSIGNMENT`、direct-setter `SETTER_TO_PROPERTY`、worklist accounting、typed `gapDrafts`及receipt-last重开已在当前工作树形成受限GREEN。property/XML/criteria/placeholder/column fixed-point、更多Java shape与完整mutation matrix仍未完成；任何局部GREEN都不能代表M4或ProgramGraphs完成。 |
| **部分实现（M5 evidence 与可信重开）** | 当前工作树中的M5 bounded builder已按每条provenance生成SOURCE_EXCERPT/RULE_APPLICATION nodes与support edges，module publisher/reader提供receipt-last安装和fresh reopen；这只是正在集成的有界实现，不是已发布的public `evidence-graph.json`，也不关闭完整evidence registry/预算/跨仓库验收。 |
| **进行中（M6 public publication）** | M1–M5 typed draft/reference、共享local Gap carrier和M6七个public payload正在集成。最近一次阶段级selector运行57个测试，53个通过；剩余4个失败暴露的是本轮设计纠正，而不是应被跳过的fixture：M1对零entry的malformed Java/forbidden XML entity仍错误拒绝源码文件local Gap，M6仍把合法`scopeGapIds`当作必须投影为GraphGap的引用错误。本节现已冻结local carrier与repository scope accounting的边界；在对应实现和mutation selectors通过前，不能声称exact-seven module、exact-eight analysis-step publication、Gap JSONL或ProgramGraphs receipt已完成。 |
| **尚未实现** | M3 的direct-throw/mixed return-throw frame与continuation验证、通用多重/嵌套/else 分支、异常、循环、跨调用栈语义与完整发布；M4后续property/XML fixed-point；M5完整证据覆盖；M6原子公开集合，以及跨图/完整仓库验收。 |
| **历史证据，不是当前能力** | 已删除的`RepositoryModel`/旧FlowView曾投影部分结构、调用、SQL和CFG，并暴露DepotHead跨层status/ids dataflow不足。它们只提供测试反例，不是当前图或永久seam。 |
| **下一实现门** | 先按本轮冻结合同修正M1零entry源码文件local Gap与M6 `G`/`S`分账，并让`CodeStructureGraphBuilderTest`、`ProgramGraphsPublicationSpecifierTest`和`ProgramGraphPublicWireTest`的对应selector通过；不得通过伪造entry或GraphGap行修fixture。随后继续完成M4其余transfer、M5及M6原子发布。任一局部GREEN都不能关闭ProgramGraphs。 |

历史pre-reset jshERP slice的Gap、0 Flow、0 Capsule不能被目标edge示例改写成成功，也不能被误报为当前SourceAnalysis输出。
