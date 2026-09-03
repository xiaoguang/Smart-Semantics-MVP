# 程序图

> 总体设计权威：[Source Code Analysis Agent 总体设计](../DESIGN.md)。运行顺序只由文件名中的 `03-` 与运行目录 `steps/03-program-graphs/` 表达。

本文示例严格使用DESIGN §1.3的`NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN`分类；未标为strict的digest/size/ID不可复制为golden。权威字段表、enum、identity和direct-preimage合同始终exact，不能靠示例降级删除。

## 1. 为什么存在

业务流程不是“Controller 文件后面跟着 Service 文件”。要证明一次状态更新，程序必须分别知道：

- 代码元素怎样组织；
- 调用实际绑定到谁；
- 条件与终点怎样连接；
- status 和 ids 的值怎样在 frozen Java 内流动、又在哪里停止；
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

Service.updateByExampleSelective(record, example)
  --ARGUMENT_TO_BOUNDARY--> JavaBoundaryInvocation(
       DepotHeadMapper.updateByExampleSelective(DepotHead, DepotHeadExample))
~~~

这段是目标图语义，不是当前 standalone graph 产物。M1发现的XML/SQL结构与M2的Mapper Java→XML statement绑定继续存在，但M4值流不得穿过`JavaBoundaryInvocation`推断placeholder、column、where或任一外部副作用。

## 3. 程序怎样工作

1. 重验VerifiedSourceInventory与ApplicationDiscovery receipt roots；解析器只读verified handles。
2. 建代码结构图：package、type、field、method、parameter、annotation、config、XML statement、SQL table/column 和 containment/declaration。
3. 建调用图：receiver 静态类型、method candidate、direct call、call/return pairing、Mapper Java method 与 XML statement binding。
4. 建控制流图：ENTRY、TRUE、FALSE、NEXT、CALL、RETURN、THROW/TERMINAL；每个 branch edge保存 guardId 和 polarity。
5. 建数据流图：frozen Java内的definition/use、assignment、return、argument→parameter、setter→property；调用离开frozen Java时停在generic `JavaBoundaryInvocation`，外部返回只作为unknown source连接Java use。
6. 建证据图：graph node/edge（含boundary invocation/unknown return）→ source span、file SHA、span SHA、parser rule、binding rule；Evidence node不直接等于 Fact，也不证明外部效果。
7. 做逐图 reference/accounting、跨图 endpoint、entry ownership、unique binding 和 resource validation。
8. 写满五图及 index/gaps，在同文件系统原子安装整个 分析步骤“程序图” 目录。

## 4. 生成的可观察产物

| 文件 | 一等职责 |
| --- | --- |
| code-structure-graph.json | 声明、包含、类型、配置、XML/SQL 结构 |
| call-graph.json | 跨方法、跨层与 Mapper statement 的唯一调用/binding |
| control-flow-graph.json | 入口根可达 CFG、branch polarity、call/return 和 terminals |
| data-flow-graph.json | frozen Java definitions/uses/arguments/properties、generic boundary invocation与unknown boundary return |
| evidence-graph.json | 每个图 node/edge 的 source/rule provenance |
| graph-index.json | 五图 schema/profile/roots、跨图 ID catalog 和 accounting |
| graph-gaps.jsonl | 有确定源码位置的局部 unsupported/ambiguous/over-limit site；不包含仓库范围缺口 |
| program-graphs-receipt.json | 上游 roots、control hashes、artifact set 与 Gap count |

**示例分类：NARRATIVE_ILLUSTRATION。** DepotHead boundary node 的目标形状：

~~~json
{
  "nodeId": "data-node:<hex64>",
  "kind": "JAVA_BOUNDARY_INVOCATION",
  "canonicalValue": "java-boundary-invocation-v1|com.jsh.erp.datasource.mappers.DepotHeadMapper#updateByExampleSelective(DepotHead,DepotHeadExample)",
  "boundaryInvocation": {
    "invocationCallId": "call-site:<hex64>",
    "callTargetEdgeId": "call-edge:<hex64>",
    "staticTargetType": "com.jsh.erp.datasource.mappers.DepotHeadMapper",
    "staticTargetMethod": "updateByExampleSelective",
    "staticTargetSignature": "updateByExampleSelective(DepotHead,DepotHeadExample)",
    "orderedArguments": ["<BoundaryArgumentV1 ordinal 0>", "<BoundaryArgumentV1 ordinal 1>"],
    "controlContext": "<BoundaryControlContextV1>",
    "sourceLocator": "<SourceLocatorV1>",
    "ruleId": "java-boundary-invocation-v1"
  },
  "unknownBoundaryReturn": null,
  "evidenceNodeIds": ["evidence:<hex64>"]
}
~~~

尖括号与短 identity 是示意，不是当前 JSON artifact。

目标出口始终是八个命名文件的完整 analysis step set。即使某类关系因 Gap 没有 admitted edge，对应 graph 文件也必须包含非空 schema/profile/snapshot/coverage envelope；不能省掉空图。未来 DepotHead 正向验收要求五图共同包含route、call、control、frozen-Java status/ids流、Mapper generic boundary invocation及外部效果Gap；当前实现没有达到这一正向出口。

### 4.1 人类 walkthrough：五图模块用什么文件接力

**示例分类：NARRATIVE_ILLUSTRATION。** 下列行只说明模块职责，不是wire或跨analysis step identity chain。

~~~jsonl
{"module":"code-structure","artifact":"modules/01-code-structure/code-structure-draft.json","takesFrom":["VerifiedSourceInventoryReference","ApplicationDiscoveryReference"],"says":{"entryId":"entry:post-depothead-batch-set-status","declares":["DepotHeadController#batchSetStatus","DepotHeadService#batchSetStatus","DepotHeadMapper#updateByExampleSelective","DepotHead.status","jsh_depot_head.status"]}}
{"module":"call-graph","artifact":"modules/02-call-graph/call-graph-draft.json","takesFrom":["code-structure-draft.json","ApplicationDiscovery mapper catalog"],"says":{"entryId":"entry:post-depothead-batch-set-status","calls":["Controller→Service","Service→Mapper Java","Mapper Java→Mapper XML statement"]}}
{"module":"control-flow","artifact":"modules/03-control-flow/control-flow-draft.json","takesFrom":["code-structure-draft.json","call-graph-draft.json"],"says":{"entryId":"entry:post-depothead-batch-set-status","guard":"dhIds is not empty","truePath":"set status and update","terminal":"return result"}}
{"module":"data-flow","artifact":"modules/04-data-flow/data-flow-draft.json","takesFrom":["structure/call/control artifacts"],"says":{"entryId":"entry:post-depothead-batch-set-status","javaValuePath":"request status→Service status→DepotHead.status","boundaryInvocation":"DepotHeadMapper.updateByExampleSelective(record,example)","externalEffect":"UNKNOWN/GAP"}}
{"module":"evidence-graph","artifact":"modules/05-evidence-graph/evidence-graph-draft.json","takesFrom":["four program-graph drafts","verified source"],"says":{"boundaryInvocationNode":"data-node:boundary-update-by-example","source":"DepotHeadService.java:803","rule":"java-boundary-invocation-v1"}}
{"module":"publish","artifacts":"modules/06-publish/<seven-semantic-files>","receipt":"modules/06-publish/module-receipt.json","takesFrom":["five graph drafts"],"says":{"graphKinds":["CODE_STRUCTURE","CALL","CONTROL_FLOW","DATA_FLOW","EVIDENCE"],"semanticFiles":["code-structure-graph.json","call-graph.json","control-flow-graph.json","data-flow-graph.json","evidence-graph.json","graph-index.json","graph-gaps.jsonl"],"analysisStepReceipt":null,"nextStep":"CanonicalAnalysisStepArtifactStore"}}
~~~

同一个 `entry:post-depothead-batch-set-status` 从 M1 到 M4 原样复用；M5 只给既有 node/edge 加来源，M6只形成七个semantic analysis step payload并先取得自己的module receipt。随后`CanonicalAnalysisStepArtifactStore`从fresh reopen的M6 reference写七项、计算root并最后创建`program-graphs-receipt.json`。任何模块都不能把 `status` 和 `ids` 改名或跨缺边拼成长链。

## 5. 下游怎样消费而不返工

分析步骤“已证明代码事实” 只读五张 graph 与 graph-index 来枚举候选 Fact、构建 Proof。分析步骤“业务流程” 复用 call/control/data graphs 编译 Flow。它们都不能：

- 重新解析 AST/XML 来补一个缺边；
- 用源码行顺序推断控制流；
- 用 simple name 或字符串相等绑定调用/数据；
- 从 evidence span 直接跳过 graph edge 生成 Fact。
- 把M2 Mapper→XML结构绑定、XML/SQL静态节点或boundary target名称拼成外部副作用。

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
- **Gap / fatal / artifact复用**：可定位的 ambiguous/unsupported或已完整枚举的over-limit call必须写共享`GraphGapDraft`，且M2每条local Gap的`affectedEntryIds`必须是owner draft `entryIds`的非空子集；没有入口owner的Mapper catalog未知留在ApplicationDiscovery或范围accounting，不能伪造M2 local Gap。若 Stage 2 已确认 Java interface 与 XML namespace 是同一 Mapper、但缺少被调用的方法 candidate，则写`MAPPER_JAVA_METHOD_UNRESOLVED` Gap，绝不静默省略 binding。没有匹配 Mapper catalog 的普通 Java interface 不被臆断为 Mapper。两个以上**兼容候选声明**是`CALL_TARGET_AMBIGUOUS` local Gap；同一canonical signature重复声明、broken M1 endpoint、无法完成候选分母、pair/reference/accounting错误才是fatal。只接受相同roots/profile。
- **给下游的后置保证**：M3/M4/BusinessFlows 能沿明确 call/return，不需动态 dispatch 猜测；ProvenCodeFacts 可把 call edge 放入 Proof。
- **明确非目标**：不以调用顺序代替 CFG，不推值流，不把 unresolved candidate 任选一个。

##### M2.1 调用目标候选集合与唯一决议算法

本节是目标合同，当前代码尚未全部实现。它只深化`buildCalls(...)`内部行为，不增加public Interface、图、字段、文件或schema版本。必须区分两个容易混淆的集合：**目标候选集合**是resolver在内存中比较的method declarations；`GraphGapDraft.candidateElementIds`仍是调用site的coverage candidate ID，不用两个method node ID冒充两个调用site。

对每个从某entry可达的物理`MethodCallExpr`，M2按以下顺序执行，任何排序均使用canonical signature的UTF-8 byte order：

1. 以`fileId + exact call-expression span + caller method signature`建立一个调用site candidate；先将所有可达entry owner求并集，再产生一次处置。递归或两个entry到达同一site不会复制Gap；bounded worklist无法闭合是既有资源fatal。
2. 求receiver static type。支持的receiver形状必须从冻结AST及M1声明唯一得到canonical FQN；不支持的表达式写`CALL_RECEIVER_UNSUPPORTED`，支持形状但零/多类型写`CALL_RECEIVER_UNRESOLVED`。两者都停止该site，不能进入目标候选选择。
3. 枚举该static receiver type在冻结源码中**直接声明**的全部方法；先按method name、固定arity和可证明visibility过滤。visibility闭集为：显式`public`及按Java语言规则隐式public的interface method；同一declaring FQN内的`private`；同package的package-private或`protected`。跨package `protected`、继承查找、varargs展开、generic inference或无法证明visibility的声明不参与猜测；若它们可能决定调用而当前profile不能完成分母，该site写`CALL_TARGET_UNRESOLVED`。确定不可见的方法直接排除。
4. 在过滤前检查声明完整性。相同receiver FQN、method name和canonical formal parameter types出现两次，或任一候选不能唯一命中一个M1 `METHOD` node，说明冻结AST与M1结构endpoint无法形成双射，以`GRAPH_REFERENCE_BROKEN` fatal；禁止`Map.put`覆盖、按源码顺序择一或把非法同签名声明说成overload ambiguity。M1已经处置为`JAVA_PARSE_UNSUPPORTED`的文件不再由M2解析；若M2要遍历的M1 exact method所在字节此时语法解析失败，同样是前驱不一致fatal。
5. 仅当name/arity/visibility候选非空时，对每个ordered actual建立`EXACT_TYPE(canonicalType)`、`NULL_LITERAL`或`UNSUPPORTED`。当前bounded compatibility只支持：非null actual与formal canonical type逐字相等；`NULL_LITERAL`与任意已确定的非primitive reference/array formal兼容，与`boolean/byte/short/int/long/char/float/double`不兼容。`null`本身绝不是`UNSUPPORTED`。方法调用、lambda、method reference、条件表达式或无法唯一得到static type的actual为`UNSUPPORTED`；当前不做boxing/unboxing、primitive widening、reference widening、varargs或generic inference。
6. 若name/arity/visibility候选为空，写`CALL_TARGET_UNRESOLVED`。若候选非空但任一actual为`UNSUPPORTED`，不允许用其他参数或唯一声明猜target，写`CALL_ARGUMENT_TYPE_UNRESOLVED`。否则以步骤5的完整、确定性compatibility过滤出目标候选集合`C`，按下表唯一处置。

| `C`基数 | M2处置 | 允许产生的call内容 |
| ---: | --- | --- |
| `1` | `EXACT` | 一个`CALL_SITE`、一个`CALL_TARGET`、配对`CALL_RETURN`；target必须是该唯一M1 method endpoint |
| `0`，且步骤5出现`UNSUPPORTED` | `CALL_ARGUMENT_TYPE_UNRESOLVED` local Gap | 无该site的call node/edge |
| `0`，没有unsupported actual | `CALL_TARGET_UNRESOLVED` local Gap | 无该site的call node/edge |
| `>1` | `CALL_TARGET_AMBIGUOUS` local Gap | 无该site的call node/edge；不运行“most specific”或源码顺序tie-break |

步骤6第二行是算法的fail-closed分支；实现可在构造`C`前直接结束，但accounting结果等价于“没有可准入候选且参数类型关系未闭合”。一个已知但与所有formal都不逐字相等的非null type落入第三行；这是保守的false negative，不是授权实现reference widening。以后若扩展兼容关系，必须先升级profile/rule和mutation matrix，不能原位改变同一输入的结果。

`CALL_TARGET_AMBIGUOUS`必须加入当前版本化CALL local-Gap reason闭集，它是可定位的业务分析缺口，不是抛出的fatal code。该物理调用site生成恰一个`GraphGapDraft{gapId, reasonCode=CALL_TARGET_AMBIGUOUS, affectedEntryIds, candidateElementIds=[siteCandidateId], sourceLocator}`：owner是到达该site的全部entry ID排序去重集合；locator恰覆盖调用表达式；`siteCandidateId`绑定snapshot、caller signature、receiver static FQN、method name、arity、ordered actual descriptors、排序后的兼容target signatures和locator。它恰进入一次`coverage.gapDispositions`，不进入`exactElementIds`或`exclusions`；在完整分母下local Gap可以与`coverage.closed=true`共存。gap ID继续完全使用8.0.1既有`GraphGapIdentityMaterialV1`公式，不增加自由detail字段。

最小可重放语义例子：

~~~java
interface AuditClient {
  boolean recordStatus(String status);
  boolean recordStatus(Integer status);
}

final class DepotHeadService {
  private AuditClient auditClient;

  boolean record() {
    return auditClient.recordStatus(null);
  }
}
~~~

receiver static type是`AuditClient`，name=`recordStatus`，arity=`1`；两个public declaration都通过visibility，`null`对两个reference formal都兼容，所以`|C|=2`。期望输出不是任一`CALL_TARGET`，而是一条CALL-owner Gap（ID仅示意）：

~~~json
{"affectedEntryIds":["entry:<sha256>"],"candidateElementIds":["call-candidate-v3:<sha256>"],"gapId":"graph-gap:<sha256>","reasonCode":"CALL_TARGET_AMBIGUOUS","sourceLocator":{"endByteExclusive":235,"endColumn":42,"endLine":10,"fileId":"file:<sha256>","path":"src/main/java/example/DepotHeadService.java","startByte":205,"startColumn":12,"startLine":10}}
~~~

若删除`Integer` overload，`|C|=1`并产生EXACT call/return pair；若把它改为`int`，`null`排除primitive后仍只有`String` candidate；若两个formal都是primitive，`C=0`并写`CALL_TARGET_UNRESOLVED`。这个bounded resolver即使遇到`Object`/`String`这类Java可能用most-specific规则继续决议的组合，也不自行实现该规则：多于一个null-compatible reference candidate仍写ambiguity，避免false exact。

M4只消费M2中已有唯一`CALL_TARGET`的M3-activated call。上述site在fresh-reopened M2里只有原始CALL Gap，因此M3/M4不得生成call projection、boundary work item、`JavaBoundaryInvocation`、data-flow edge或复制成M4 Gap；M6最终只把这条M2 Gap按8.0.2一对一投影到`graph-gaps.jsonl`，`graphKind=CALL`。这句话是既有上下游保证，不改变M3/M4/M6合同。

- **公共测试 seam 与验收**：`buildCalls(CallGraphInputs inputs, CallGraphProfile profile)` 对三段 DepotHead chain 做逐段 deletion/decoy/overload mutation；fixture必须先把真实 M1 module publication 安装到 canonical store，再由 `PersistedCodeStructureGraphReader` 与同一次`ReopenedProgramGraphInputs`生成唯一 sealed inputs。raw draft constructor、错 M1 address/key、receipt SHA、schema/type、六项 lineage、controls、profile、snapshot/application/entry denominator 任一 mutation 都必须在 builder 解析源码前以 `GRAPH_REFERENCE_BROKEN` 失败；删除/替换call-site bytes或脱离Stage 2的entry/catalog candidate不得由路径、自由字符串搜索或M1 display value补回；只有唯一 compatible target/namespace binding 时产生 EXACT edge。ambiguous/unresolved call或Mapper fixture还必须直接断言共享Gap五字段、identity和coverage映射，任一字段/ID substitution均fail closed。
- **Luna/xhigh 测试指南**：只扩展既有`CallGraphBuilderTest`及其`src/test/resources/analysis/graph/call-graph/`fixtures，不创建平行resolver seam。第一条RED必须用真实M1 publication/readback建立上面的`String`/`Integer`+`null` fixture，并直接断言：恰一`CALL_TARGET_AMBIGUOUS`、非空准确owner、call-expression locator、单site candidate、Gap identity、coverage双向映射，以及该site零`CALL_SITE/CALL_TARGET/CALL_RETURN`；反转两个overload声明顺序应得到相同draft。随后每个行为一个RED：删一overload→EXACT；reference+primitive+`null`→唯一reference EXACT；primitive-only+`null`→`CALL_TARGET_UNRESOLVED`；unsupported actual→`CALL_ARGUMENT_TYPE_UNRESOLVED`；unknown receiver→既有receiver Gap；同签名重复与M1 endpoint缺失→`GRAPH_REFERENCE_BROKEN`。至少一个双entry fixture断言同一site只有一条Gap且owner为两个entry的排序并集。再在既有M4 selector加入一条窄测试，只验证fresh-reopened M2 ambiguity不产生boundary/data-flow元素且不复制Gap；M6既有Gap projection selector验证它最终仍是CALL Gap。golden不得由production生成，不能把`ENTRY_HANDLER_AMBIGUOUS`、`MAPPER_JAVA_METHOD_AMBIGUOUS`或`CALL_TARGET_UNRESOLVED`改名充数。命令：`mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test`，之后才运行直接覆盖M4/M6的既有selector；无网络/runtime。
- **Terra/xhigh 实现指南**：RED后只改既有`analysis/graph`内M2实现与必要的M2 fixture reader，不改M1/M3/M4/M6合同。把signature-keyed单值`Map`替换为不会覆盖重复声明的有序candidate index；实现`ReceiverResolution`、`ActualTypeDescriptor`和bounded compatibility为M2内部类型，不暴露新public record。按“物理site枚举→entry owner fixed point→receiver→name/arity/visibility denominator→actual descriptors→compatibility→cardinality disposition”执行；只在`|C|=1`时建call/return及后续Mapper binding。所有Gap沿现有`GraphGapDraft`与coverage carrier落盘；不得加入source-order/most-specific/simple-name fallback或改变draft schema。完成每条RED后跑Spotless apply、重跑同一selector及`git diff --check`；若当前M1不能提供唯一endpoint或reason registry拒绝新reason，停止并交Sol/ultra，不得在M4伪造。

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

- **解决的问题**：逐段证明 frozen Java 内的值流，并在调用离开 frozen Java 时用一个技术无关的 `JavaBoundaryInvocation` 明确截断；不描述边界外部实现或副作用。
- **精确上游输入及前置**：`DataFlowInputs{structure: ReopenedCodeStructureGraph, calls: ReopenedCallGraph, controlFlow: ReopenedControlFlowGraph, reopened: the same ReopenedProgramGraphInputs}`与`DataFlowGraphProfile`；M4 execution必须以同一次reopened inputs按8.0依序fresh-reopen M1/M2/M3，校验同一basis、三段payload refs与graph profile，只从`reopened.source`读取verified Java bytes。M1仍可发现XML/SQL结构，M2仍可发布Mapper Java→XML statement绑定；两者都不是M4跨边界传播值的许可。
- **确定性顺序 / LLM**：建intra-method def-use → 对exact call判定target是否有frozen Java concrete body → 内部call做argument→parameter/return transfer → 外部call建generic boundary invocation及argument edges → 被Java消费的外部返回建unknown source/use edges → fixed-point/account；0 LLM。
- **目标输出与 DepotHead 示例**：DATA_FLOW draft只证明`status`和`record/example`各自怎样到达`DepotHeadMapper.updateByExampleSelective(record, example)`的有序arguments，并记录该call的静态target与guard。它不生成`record.status→placeholder→column`或`example criterion→where`；数据库更新与筛选结果为Gap/待确认。
- **必须保持的不变量**：每条Java长链由相邻typed edges组成；每edge有唯一rule/endpoints/guard context；parameter/local read只连唯一reaching definition，赋值RHS先于write处理；一旦call离开frozen Java，值流只进入一个generic boundary node。任何Mapper、Kafka、ES、HTTP、Redis、event、client或library名称都不得改变node/edge kind、record shape或rule family。
- **Gap / fatal / artifact复用**：局部alias、multi-definition join、非direct write/setter、unsupported return-use shape、无法唯一确定external static target或control context为Gap。M2已经判为ambiguous/unresolved的target继续由M2 Gap承载；若M2给出exact call但M4不能唯一重验boundary target triple，则M4以`DATA_FLOW_BINDING_UNPROVEN`写local Gap且不造boundary node。broken predecessor endpoint、worklist/accounting/identity错误fatal。
- **给下游的后置保证**：ProvenCodeFacts只能陈述“Java在某控制上下文以这些有序arguments及已证明Java-local origins调用了这个静态target”，以及“某unknown external return被某Java use消费”。外部写入、发布、缓存、索引、网络响应或其他效果必须保留Gap/待确认。
- **明确非目标**：不解释或模拟外部实现，不执行Mapper/SQL/client/library，不把M2 Mapper→XML结构边当data-flow edge，不从target名称推断技术类别，不证明任一外部副作用或返回值。
- **公共测试 seam 与验收**：`buildDataFlow(DataFlowInputs inputs, DataFlowGraphProfile profile)`；现有frozen-Java argument/read/write/setter cases保持。新合同测试以同一generic records覆盖至少Mapper和一个非Mapper外部call，并断言输出除静态target值外同形；再覆盖void/non-void、return unused/used、ambiguous target、ordered argument/origin、guard、identity、old-version rejection和M5/M6 closure。
- **Luna/xhigh RED指南**：只扩展`DataFlowGraphBuilderTest`、`EvidenceGraphBuilderTest`和`ProgramGraphsPublicationSpecifierTest`。先写失败测试并手工冻结golden：① exact外部call产生一个`JAVA_BOUNDARY_INVOCATION`且arguments按ordinal；②每个`javaLocalOriginNodeIds`恰等于不越过boundary可达的Java来源；③Mapper/HTTP等两个fixture的kind/rule完全相同；④M2 Mapper→XML边存在但M4无placeholder/column/where edge；⑤外部return未使用时无return node，使用时恰有一个`UNKNOWN_BOUNDARY_RETURN`和use edge；⑥ambiguous target只有Gap；⑦缺/乱argument、origin、control、locator/rule、support evidence或任一identity preimage均失败；⑧M4 draft v2、public data-flow v1、M5 draft v2、public evidence v2与index v1全部拒绝。只fake verified source handle，禁止mock predecessor readers/canonical/evidence closure；禁网络、客户代码和客户MyBatis。
- **Terra/xhigh GREEN指南**：RED后仅实现M4 boundary records/edges/identity与M5/M6的版本化验证/投影；先按activated exact call枚举完整denominator，再以M1 METHOD provenance判断是否存在同snapshot verified concrete Java body。有则沿既有Java transfer；无则构建generic boundary node。不得添加Mapper/Kafka/ES/HTTP/Redis/event/client/library enum、adapter或专用rule，不得读取XML/SQL来补值流。每个RED独立最小GREEN；任何需要改变M1/M2/M3、五图、七项semantic payload、八文件analysis-step set或八步主线的方案必须STOP。

M4唯一public Java seam保持不变；wire升级为`program-graphs-data-flow-draft-v3`。所有字段/数组required，两个variant payload key required-nullable，unknown/missing/defaulted field拒绝：

~~~text
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
  String schemaVersion,                    // exactly program-graphs-data-flow-draft-v3
  ProgramGraphKind graphKind,              // exactly DATA_FLOW
  ArtifactId graphId,
  String snapshotId,
  ArtifactId applicationProfileId,
  ArtifactReference graphProfileRef,
  List<ArtifactId> entryIds,
  List<DataFlowNodeV3> nodes,
  List<DataFlowEdge> edges,
  DataFlowWorklistAccounting worklistAccounting,
  List<GraphGapDraft> gapDrafts,
  List<ProvenanceDraftV1> provenanceDrafts,
  GraphCoverage coverage)

record DataFlowNodeV3(
  ArtifactId nodeId,
  DataFlowNodeKind kind,
  String canonicalValue,
  List<ArtifactId> owningEntryIds,
  List<ArtifactId> evidenceDraftRefs,
  JavaBoundaryInvocationV1 boundaryInvocation,       // required nullable
  UnknownBoundaryReturnV1 unknownBoundaryReturn)     // required nullable

enum DataFlowNodeKind {
  DEFINITION, USE, ARGUMENT, JAVA_BOUNDARY_INVOCATION, UNKNOWN_BOUNDARY_RETURN
}

record JavaBoundaryInvocationV1(
  ArtifactId invocationCallId,             // exact M2 CALL_SITE node ID
  ArtifactId callTargetEdgeId,              // exact M2 CALL_TARGET edge ID
  String staticTargetType,                  // canonical FQN/type identity
  String staticTargetMethod,
  String staticTargetSignature,             // canonical declared signature
  List<BoundaryArgumentV1> orderedArguments,
  BoundaryControlContextV1 controlContext,
  SourceLocatorV1 sourceLocator,            // exact Java invocation expression
  String ruleId)                            // exactly java-boundary-invocation-v1

record BoundaryArgumentV1(
  int ordinal,
  ArtifactId argumentNodeId,
  List<ArtifactId> javaLocalOriginNodeIds)

record BoundaryControlContextV1(
  ArtifactId basicBlockNodeId,
  ArtifactId guardNodeId,                   // required nullable
  ControlFlowPolarity polarity)             // required nullable

record UnknownBoundaryReturnV1(
  ArtifactId boundaryInvocationNodeId,
  String declaredReturnType,
  BoundaryReturnState sourceState,          // exactly UNKNOWN_EXTERNAL_RETURN
  SourceLocatorV1 sourceLocator,
  String ruleId)                            // exactly java-boundary-return-source-v1

enum DataFlowEdgeKind {
  DEF_USE, ARGUMENT_TO_PARAMETER, ASSIGNMENT, SETTER_TO_PROPERTY,
  ARGUMENT_TO_BOUNDARY, BOUNDARY_INVOCATION_TO_RETURN, BOUNDARY_RETURN_TO_USE
}
~~~

Variant closure固定：普通`DEFINITION/USE/ARGUMENT`的两个payload均为null；`JAVA_BOUNDARY_INVOCATION`只允许`boundaryInvocation`非null；`UNKNOWN_BOUNDARY_RETURN`只允许`unknownBoundaryReturn`非null。public `data-flow-graph.json` v2把`evidenceDraftRefs`替换为`evidenceNodeIds`，其余variant字段逐字投影。不得保留或重新引入M4 `CRITERION/PLACEHOLDER/WHERE_PREDICATE`节点及`PROPERTY_TO_PLACEHOLDER/CRITERION_TO_WHERE/PLACEHOLDER_TO_COLUMN`边；对应XML/SQL结构仍由M1和M2独立表达。

**边界判定。** exact M2 target仅在其M1 METHOD具有同snapshot verified source locator且AST证明为concrete Java body时可进入；interface/abstract/native/generated/source-unavailable/profile-excluded或snapshot外target均离开frozen Java。这个判定与技术名称无关。M2的`JAVA_METHOD_TO_XML_STATEMENT`可继续存在，但不能让Mapper interface获得Java body，也不能成为M4 predecessor edge。

**argument与origin。** 每个boundary call的显式actual沿用既有M4 `ARGUMENT` node；`orderedArguments`按zero-based ordinal严格连续且argument ID不重复。每项`javaLocalOriginNodeIds`按ID排序去重，只列通过同一entry内`DEF_USE/ASSIGNMENT/SETTER_TO_PROPERTY`且不经过任何boundary node可达的M1 `PARAMETER/FIELD`或M4 `DEFINITION`；可以为空，绝不能把外部返回值、XML placeholder、SQL column或故事常量伪装成known origin。每个argument以`ARGUMENT_TO_BOUNDARY`指向同一boundary node；edge direct preimage额外绑定callTargetEdgeId和ordinal。

**control与evidence。** `basicBlockNodeId`必须是包含call的M3 activated block；guard/polarity both-null或恰为所有owning entries共享的唯一M3 pair。locator必须精确覆盖Java invocation expression；node的`evidenceDraftRefs`中必须恰有一项`ProvenanceDraftV1`与record的locator和`java-boundary-invocation-v1` rule一致，其余refs只允许证明M2 target和各argument origin。多guard或locator/rule不一致是Gap或fatal，不能默认。

**return。** void、未消费或只被丢弃的外部return不创建return node。非void return一旦被frozen Java expression消费，恰创建一个`UNKNOWN_BOUNDARY_RETURN` node、一条`BOUNDARY_INVOCATION_TO_RETURN`和每个Java use一条`BOUNDARY_RETURN_TO_USE`；use仍可参加后续Java assignment/def-use，但unknown node没有value、literal、external origin或effect含义。不得依据declared type、target名、annotation、XML/SQL或调用后的分支推断返回值。

**identity。** boundary node ID为
`identity("data-flow-java-boundary-invocation-node-v1", snapshotId, invocationCallId, callTargetEdgeId, staticTargetType, staticTargetMethod, staticTargetSignature, ordered BoundaryArgumentV1 records, BoundaryControlContextV1, sourceLocator source identity, ruleId)`。unknown-return node ID为
`identity("data-flow-unknown-boundary-return-node-v1", snapshotId, boundaryInvocationNodeId, declaredReturnType, sourceState, sourceLocator source identity, ruleId)`。三种新edge继续使用8.4通用edge identity，并分别额外绑定ordinal、boundary invocation node ID或Java use work-item ID。node/edge IDs、variant fields、provenance、worklist、coverage和graph profile共同进入M4 graphId；任一变化必须改变identity或被拒绝。

**denominator与Gap。** 每个M3-activated call site先按call ID计算一个transfer candidate，不能只枚举成功boundary。exact target但target triple/body classification/arguments/control无法唯一重验时，以call locator产生`DATA_FLOW_BINDING_UNPROVEN` local Gap；candidate ID为`identity("data-flow-boundary-transfer-candidate-v1", invocationCallId, callTargetEdgeId)`。M2 target ambiguous仍保留M2 `CALL_TARGET_AMBIGUOUS` Gap，M4不得从候选名称选一个target。每个M4 local Gap仍须有非空entry owner；coverage、worklist、scope Gap及fatal/accounting规则沿用8.0.1。

**版本迁移与拒绝。** M1 draft v3、M2 draft v3、M3 draft v3和其public graphs不变。新graph profile下只接受M4 draft `program-graphs-data-flow-draft-v3`、M5 draft `program-graphs-evidence-graph-draft-v3`、public data-flow `program-graphs-data-flow-graph-v2`、public evidence `program-graphs-evidence-graph-v3`与index `program-graphs-graph-index-v2`。必须逐项拒绝旧`program-graphs-data-flow-draft-v2`、`program-graphs-evidence-graph-draft-v2`、`program-graphs-data-flow-graph-v1`、`program-graphs-evidence-graph-v2`和`program-graphs-graph-index-v1`，也不得把任何旧M4/M5/public/index artifact混入新五图集合、兼容读取、默认补字段或原位迁移。迁移只允许从相同已验证M1–M3 references重新运行M4→M5→M6，产生新的content identities；下游ProvenCodeFacts到NineSectionDocument必须fresh-reopen并拒绝旧ProgramGraphs set。文件名、artifact type、五图、M6七项semantic文件和analysis-step八文件数量完全不变。

#### M5 EvidenceGraphBuilder

- **解决的问题**：说明每个程序 node/edge 从哪些固定 bytes 和版本化 parser/binding rule 得出。
- **精确上游输入及前置**：M1–M4 drafts 及每个 draft element 的 provenance tokens、VerifiedSourceInventory inventory/source handles、EVIDENCE registry/profile/budget；所有 program IDs 已固定。
- **确定性顺序 / LLM**：canonicalize file/span locators → 重验 file/span SHA → 建 rule-application nodes → 建 LOCATES/PARSED_BY/BOUND_BY/SUPPORTS edges → 覆盖检查；0 LLM。
- **目标输出与 DepotHead 示例**：EVIDENCE draft；例子把generic boundary invocation及两条argument edge回到Service :803的Java call span、`java-boundary-invocation-v1`与各Java-local origin evidence；XML :472-473仍只证明M1静态结构节点。
- **必须保持的不变量**：每个admitted program node/edge至少一个闭合provenance path；boundary node必须有call locator/rule且与M4 record逐字段一致；Evidence identity不含绝对root；Evidence不是Fact/Proof，也不证明边界外效果。
- **Gap / fatal / artifact复用**：局部 program Gap 可有 gap provenance；admitted element 缺 evidence、span/source drift、rule unknown、coverage/reference broken fatal；模块fresh-reopen source artifacts构建。
- **给下游的后置保证**：ProvenCodeFacts可从boundary invocation、ordered arguments、unknown return/use稳定走到source/rule，并独立构造invocation atom Proof；没有任何Evidence path可把它升级成外部效果。
- **明确非目标**：不决定业务事实、不把locator正确等同于语义正确、不为缺边或外部效果制造evidence，不使用技术专用boundary rule。
- **公共测试 seam 与验收**：`buildEvidence(programDrafts, source)` 对 span/rule/file SHA/endpoint omission/substitution mutation fail closed；不同 root 产生同 evidence IDs/bytes。
- **Luna/xhigh 测试指南**：扩展`EvidenceGraphBuilderTest`，fixtures/goldens在`src/test/resources/analysis/graph/evidence-graph/`。逐RED：boundary call node、每个argument/return edge的source/rule closure；locator/hash/rule omission、subject substitution、技术专用rule、XML evidence伪支持boundary effect、admitted element无evidence、旧v2拒绝与different-root determinism。只fakeSourceHandle重开，禁止mock hash/coverage/canonical；无网络。
- **Terra/xhigh 实现指南**：RED后仅改`analysis/graph/evidence-graph/`，实现`program-graphs-evidence-graph-draft-v3`；消费M1–M4 provenance artifacts，typed `SourceExcerptV1`→reopen→generic boundary rule node→support edges→coverage。逐RED GREEN；不得把Evidence当Proof、制造缺边/外部效果或用`canonicalValue`/string locator代替source bytes。

#### M6 ProgramGraphSetPublicationSpecifier

- **解决的问题**：把五个drafts作为不可拆分的一等图集合校验并形成七个semantic payload；由analysis step store独占root与receipt计算并给ProvenCodeFacts和BusinessFlows一个稳定引用。
- **精确上游输入及前置**：M1–M5完整drafts、graph gaps、VerifiedSourceInventory与ApplicationDiscovery roots和ProgramGraphs controls；五种graphKind恰各一份且局部validation已通过。
- **确定性顺序 / LLM**：跨图 endpoint/ownership/accounting → canonical五图/index/gaps → M6 exact-seven module install/receipt → analysis step store fresh reopen/root/receipt-last；0 LLM。
- **目标输出与 DepotHead 示例**：M6恰七个semantic files；analysis step store形成七项+receipt的八文件reader-visible set。新正例index指向generic boundary invocation、ordered arguments、可选unknown return及外部效果Gap；M1 XML/SQL与M2 Mapper binding仍在各自图中。
- **必须保持的不变量**：五图恰一次且schema/profile/snapshot一致；M4 v3 variant和external endpoints全闭合，M5 v3 evidence不支持任何不存在的external-effect edge；local Gap集合`G`与repository scope集合`S`分别验证、只在index和receipt中取去重并集`G ∪ S`；M6不改边且不混读旧版本。
- **Gap / fatal / artifact复用**：builder 的local Gap一对一写入`graph-gaps.jsonl`；无locator的`scopeGapIds`只进入四张图coverage、index和module/analysis-step receipt，绝不伪造成GraphGap行。缺图、多图、cross-ref、root/canonical/install/collision或local/scope accounting错误 fatal；下游不能选四张引用。
- **给下游的后置保证**：ProvenCodeFacts和BusinessFlows只凭ProgramGraphsReference获得完整五图、index、Gap/accounting，不需源码parser。
- **明确非目标**：不补 graph edge、不证明 Fact、不编译 Flow、不调用模型。
- **公共测试 seam 与验收**：`specifyGraphSet(fiveReopenedDrafts, controls)`重开并校验五个已安装draft；M1–M4 local Gap只能来自各自已重开draft的共享`gapDrafts`，不得作为detached参数注入或替换。必须分别覆盖：M1零entry源码文件local Gap；M2–M4非空entry local Gap；`BOUNDED_PATH_SET`且没有local Gap时的零字节`graph-gaps.jsonl`与唯一`S`；local和scope并存时index/receipt的`G ∪ S`。缺/多/交换 graph、每一种graph kind的Gap缺失/额外/替换、cross-ref mutation、乱序、partial-install/collision均fail closed；只有M6 exact-seven和analysis-step-store eight-file coherent set才返回 ProgramGraphsReference。
- **Luna/xhigh 测试指南**：扩展`ProgramGraphsPublicationSpecifierTest`：保留M6 exact-seven、analysis step exact-eight和既有Gap/accounting矩阵；新增M4 v3→public data-flow v2 variant逐字段投影、M5 v3→public evidence v3、index v2 kind catalog、boundary evidence closure及全部旧版本/混搭拒绝。使用真实module/analysis step stores，禁止mock cross-validator/canonical/root；禁网络/build。
- **Terra/xhigh 实现指南**：RED后仅改`analysis/graph/publish/`所需版本注册、cross-validation和投影；只读M1–M5 files，cross-ref→semantic files→M6 install/receipt→typed analysis-step-store receipt-last。不得生成旧single publication summary、兼容reader、预报root/receipt、补外部效果边或改变文件数量。

### 8.0.1 模块 artifact wire schemas

M1–M5使用 DESIGN 13.3 `ModuleArtifact<T>` envelope并采用8.1 ProgramGraph records；M6直接安装七个analysis step schema注册的JSON/JSONL semantic bytes而无summary envelope。`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-code-structure/code-structure-draft.json` | `program-graphs-code-structure-draft-v3` / `PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT` | exact `graph-profile`；VerifiedSourceInventory `source-inventory/verified-snapshot`；ApplicationDiscovery `application-profile/capability-report/entry-points/mapper-catalog` ArtifactReferences | `graphKind=CODE_STRUCTURE!`、`graphId!`、`snapshotId!`、`applicationProfileId!`、`graphProfileRef!`、`entryIds[]!`、`nodes[]!`、`edges[]!`、`gapDrafts[]!`、`provenanceDrafts[]!`、`coverage!`；nodes/edges/gaps/provenance按ID |
| `modules/02-call-graph/call-graph-draft.json` | `program-graphs-call-graph-draft-v3` / `PROGRAM_GRAPHS_CALL_GRAPH_DRAFT` | exact M1、`graph-profile`、VerifiedSourceInventory两项、ApplicationDiscovery `entry-points/mapper-catalog` ArtifactReferences | M1同形payload且`graphKind=CALL!`；每个CALL_TARGET/JAVA_METHOD_TO_XML_STATEMENT有唯一`CALL_RETURN` pair，edge保留resolution/rule/evidenceDraftRefs并按edgeId |
| `modules/03-control-flow/control-flow-draft.json` | `program-graphs-control-flow-draft-v3` / `PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT` | exact M1/M2、`graph-profile`、VerifiedSourceInventory两项、ApplicationDiscovery `entry-points` ArtifactReferences | M1同形payload且`graphKind=CONTROL_FLOW!`；nodes/edges/gaps/provenance按ID，每entry的`semanticTraversalOrder[]!`按canonical DFS顺序；每guard的TRUE/FALSE或typed terminal/Gap disposition必备 |
| `modules/04-data-flow/data-flow-draft.json` | `program-graphs-data-flow-draft-v3` / `PROGRAM_GRAPHS_DATA_FLOW_DRAFT` | exact M1/M2/M3、`graph-profile`、VerifiedSourceInventory `source-inventory/verified-snapshot` ArtifactReferences | `graphKind=DATA_FLOW!`；`DataFlowNodeV3`带required-nullable boundary/return variants；`nodes[]!/edges[]!/gapDrafts[]!/provenanceDrafts[]!/coverage!`按ID；external endpoints直接对fresh-reopened M1–M3校验；`worklistAccounting!{enqueuedWorkItemIds[]!,processedWorkItemIds[]!,overLimit!}` |
| `modules/05-evidence-graph/evidence-graph-draft.json` | `program-graphs-evidence-graph-draft-v3` / `PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT` | exact M1–M4、`graph-profile`、VerifiedSourceInventory `source-inventory/verified-snapshot` ArtifactReferences | `graphKind=EVIDENCE!`及M1共通identity字段；`nodes[]!`为下述`EvidenceNodeV3`且按evidenceNodeId，`edges[]!`使用下述EvidenceEdge并按ID；`coverage!`逐program element闭合，boundary record locator/rule必须匹配support path |
| `modules/06-publish/<seven registered semantic filenames>` | 各public schema/type；无summary envelope | M1–M5 IDs/SHAs | 一次module install恰五图+`graph-index.json`+`graph-gaps.jsonl`；module receipt绑定七descriptors；禁止analysis step root/receipt或八项published list；AnalysisStep store provenance绑定M6 reference |

M2 不能把上表 M1 行理解为“持有一个 Java draft 就等于依赖 M1”。M1→M2 handoff 必须使用
`CodeStructureGraphDraftReference` 经 canonical store 与 `PersistedCodeStructureGraphReader` fresh
reopen；store 验证 module publication，domain reader 再验证该行的 exact address、单 payload
descriptor/envelope、receipt、upstream、controls 和 payload identity，最后才产生
`ReopenedCodeStructureGraph`。M2 module 的 own upstream 仍按上表 M2 行保存；这个 in-process
aggregate 不新增 schema、artifact 或第 53 项 reader-visible 文件。

`graphProfileRef`是完整content-addressed `ArtifactReference{artifactId,sha256}`，固定prefix=`graph-profile`；它不是自由字符串或仅语法合法的profile key。M1–M5必须逐字使用同一个ref，将其列入`upstreamArtifacts`，并在每个graph payload中重复该typed ref；graphId identity绑定两个字段且fresh reopen验证profile bytes。M6完整module fixture用一次install request绑定M1–M5五个draft ArtifactReferences，七个standalone payload不重复envelope。

M1–M3 draft继续使用`DraftProgramNode{nodeId,kind,canonicalValue,owningEntryIds,evidenceDraftRefs}`；M4 v3使用字段前五项相同、再带`boundaryInvocation?`与`unknownBoundaryReturn?`的`DataFlowNodeV3`。M1–M4的`DraftProgramEdge`形状不变。每个draft另有同module的`provenanceDrafts[]!` registry；boundary record内的locator/rule必须命中node evidence refs中的同一`ProvenanceDraftV1`。M5只按locator重开VerifiedSource bytes并生成Evidence；它不得重新解析外部系统、XML执行语义或从字符串邻近回填span。nodeId在整个graph set内全局唯一；M2–M5在构建时直接对fresh-reopened predecessors验证external endpoints，M6稍后汇总catalog。

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

M5不复用ProgramEdge字段冒充“evidence指向program edge”。`EvidenceNodeV3(evidenceNodeId,kind,sourceExcerpt,ruleApplication)`是closed union：`SOURCE_EXCERPT{sourceExcerpt!,ruleApplication=null}`或`RULE_APPLICATION{sourceExcerpt=null,ruleApplication!}`，两个required-nullable槽必须都出现且恰一非null。`sourceExcerpt`逐字段使用DESIGN §13.2 `SourceExcerptV1`；`RuleApplicationV3(ruleId,ruleVersion,inputProgramElementIds)`三项required，input IDs按UTF-8排序。v3字段形状与v2相同，但注册rule语义增加generic boundary invocation/return且明确禁止external-effect support，因此仍必须exact-version拒绝v2。

`EvidenceEdge`固定为`edgeId/kind/evidenceNodeId/subjectGraphKind/subjectProgramElementId/ruleApplicationNodeId`，六项全required；subject必须是M1–M4已登记nodeId或edgeId。M6发布时把M1–M4的`evidenceDraftRefs`替换为排序非空`evidenceNodeIds`，把M5 v3 nodes写入`program-graphs-evidence-graph-v3`。boundary invocation/unknown return只由generic Java boundary rules支持；XML/SQL evidence不能支持不存在的external-effect edge。unknown binding不生成node/edge而写Gap。

### 8.0.2 M6 public wire 与 publication seam

本节冻结M6七个semantic payload的完整wire；M1–M4内部draft schema升级不自动升级public graph schema。全部字段required且不得有未列字段，只有标成`?`的值required-nullable。注册项恰为：

| fileName | artifactType | schemaVersion / policy |
| --- | --- | --- |
| `code-structure-graph.json` | `PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH` | `program-graphs-code-structure-graph-v1` / `STANDALONE_JSON` |
| `call-graph.json` | `PROGRAM_GRAPHS_CALL_GRAPH` | `program-graphs-call-graph-v1` / `STANDALONE_JSON` |
| `control-flow-graph.json` | `PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH` | `program-graphs-control-flow-graph-v1` / `STANDALONE_JSON` |
| `data-flow-graph.json` | `PROGRAM_GRAPHS_DATA_FLOW_GRAPH` | `program-graphs-data-flow-graph-v2` / `STANDALONE_JSON` |
| `evidence-graph.json` | `PROGRAM_GRAPHS_EVIDENCE_GRAPH` | `program-graphs-evidence-graph-v3` / `STANDALONE_JSON` |
| `graph-gaps.jsonl` | `PROGRAM_GRAPHS_GRAPH_GAP` | `program-graphs-graph-gap-v1` / `CANONICAL_JSONL` |
| `graph-index.json` | `PROGRAM_GRAPHS_GRAPH_INDEX` | `program-graphs-graph-index-v2` / `STANDALONE_JSON` |

四张program graph的公共document逐字段为：

~~~text
PublicProgramGraphV1(
  schemaVersion, artifactType, artifactId, graphKind, graphId,
  snapshotId, applicationProfileId, graphProfileRef, entryIds[],
  nodes[], edges[], coverage)
ControlFlowPublicFields(semanticTraversalOrder[], terminalDispositions[])
DataFlowPublicFields(worklistAccounting)        // nodes use DataFlowProgramNodeV2 variants

ProgramNode(nodeId, kind, canonicalValue, owningEntryIds[], evidenceNodeIds[])
DataFlowProgramNodeV2(nodeId, kind, canonicalValue, owningEntryIds[], evidenceNodeIds[],
                      boundaryInvocation?, unknownBoundaryReturn?)
ProgramEdge(edgeId, kind, fromNodeId, toNodeId, ruleId, resolution,
            guardNodeId?, polarity?, evidenceNodeIds[])
~~~

`schemaVersion/artifactType/graphKind`逐行采用上表；其余common identity、entries、coverage和graph-specific accounting逐字投影相应fresh-reopened M1–M4 draft。CODE_STRUCTURE/CALL使用`ProgramNode`并禁止variant字段；CONTROL_FLOW必须有`semanticTraversalOrder/terminalDispositions`；DATA_FLOW必须有`worklistAccounting`且每个node使用`DataFlowProgramNodeV2`。四张public program graph都不携带`gapDrafts/provenanceDrafts/evidenceDraftRefs`；M4的两个variant payload逐字保留，只把evidence refs替换为IDs。

每个public node/edge的`evidenceNodeIds`恰等于M5 `EvidenceEdge`中`subjectGraphKind`和`subjectProgramElementId`匹配该元素、且kind分别为`SUPPORTS_PROGRAM_NODE`或`SUPPORTS_PROGRAM_EDGE`的`evidenceNodeId`排序去重集合；集合必须非空且每项命中一个`SOURCE_EXCERPT` node。每条support edge的`ruleApplicationNodeId`还必须命中一个`RULE_APPLICATION` node，其`inputProgramElementIds`包含该subject。缺、错kind、错owner、额外support或孤立evidence一律fatal；M6不得从draft ref、source或rule重新合成证据。

`evidence-graph.json`使用同一common header至`entryIds`，随后严格为`nodes[]/edges[]/coverage`：nodes是字段形状不变但rule语义升版的`EvidenceNodeV3`，edges是`EvidenceEdge`，coverage严格为`candidateProgramElementIds[]/evidencedProgramElementIds[]/closed`。两个coverage集合恰等于四张public program graph全部nodeId和edgeId的union且`closed=true`。

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
GraphIndexV2(
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

header的schema/type固定为index v2注册项；snapshot/application/profile/entries必须与五图及两个upstream step publications一致。`graphs`恰五项，每项引用对应public standalone artifact。`nodeCatalog`恰为五图node union，`edgeCatalog`恰为五图edge union；v2额外拒绝旧M4/M5/public graph schema及任何旧/新混搭，并校验generic boundary/unknown-return kind与其variant closure。每个program edge endpoint/guard、EvidenceEdge的source/rule/subject均须由catalog唯一解析。

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

M6 selector测试必须直接断言七个注册版本、M4 v3/DataFlow public v2 exact variants、M5/public Evidence v3、index v2、resolved evidence且无draft refs、catalog双向闭合、既有Gap/accounting、两个upstream refs、五次fresh reopen、M6 exact-seven与analysis-step exact-eight/receipt-last；M4 v2/public v1、M5/public v2、index v1及任意混搭必须fail closed。测试只用真实canonical stores，禁止mock validator、detached gap、production生成golden或补读source/AST。

**示例分类：STRUCTURAL_WIRE_SPECIMEN（两个隔离的 EvidenceNodeV3 variants，不可replay）。** 字段、closed variant与continuous excerpt完整；offset/digest未从本页未展示的完整source file重算，不能作为golden。

~~~jsonl
{"evidenceNodeId":"evidence:1111111111111111111111111111111111111111111111111111111111111111","kind":"SOURCE_EXCERPT","sourceExcerpt":{"locator":{"fileId":"file:2222222222222222222222222222222222222222222222222222222222222222","path":"jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java","startByte":30100,"endByteExclusive":30128,"startLine":800,"startColumn":9,"endLine":800,"endColumn":37},"rawUtf8":"depotHead.setStatus(status);","rawUtf8Sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},"ruleApplication":null}
{"evidenceNodeId":"evidence:3333333333333333333333333333333333333333333333333333333333333333","kind":"RULE_APPLICATION","sourceExcerpt":null,"ruleApplication":{"ruleId":"java-setter-property-binding","ruleVersion":"v1","inputProgramElementIds":["parameter:service-status","property:depothead-status"]}}
~~~

**示例分类：NARRATIVE_ILLUSTRATION（M4/M5 boundary投影，不可replay）。** 旧的五图长故事曾把M1/M2 XML结构拼成M4 placeholder/column/where值流，现已从目标设计删除。下面只展示新语义；字段、identity和完整wire仍以上文exact records为准。

~~~jsonl
{"storyProjection":"data-flow-v3","boundaryInvocation":{"staticTargetType":"com.jsh.erp.datasource.mappers.DepotHeadMapper","staticTargetMethod":"updateByExampleSelective","staticTargetSignature":"updateByExampleSelective(DepotHead,DepotHeadExample)","orderedArgumentIds":["argument:record","argument:example"],"javaLocalOriginsByOrdinal":[["field:depot-head-status"],["definition:depot-head-example"]],"externalEffect":"UNKNOWN_GAP"}}
{"storyProjection":"evidence-v3","subject":"data-node:boundary-update-by-example","source":"DepotHeadService.java:803 invocation expression","rule":"java-boundary-invocation-v1","proves":"the Java invocation and its arguments only","doesNotProve":["SQL execution","column write","WHERE effect","external return value"]}
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

EvidenceNodeV3
  evidenceNodeId
  kind: SOURCE_EXCERPT | RULE_APPLICATION
  sourceExcerpt?: SourceExcerptV1
  ruleApplication?: RuleApplicationV3

RuleApplicationV3
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
| data flow | M4-owned `DEFINITION`、`USE`、`ARGUMENT`、`JAVA_BOUNDARY_INVOCATION`、`UNKNOWN_BOUNDARY_RETURN`；Java endpoint复用M1 `PARAMETER/FIELD`而不重声明；edge恰为`DEF_USE`、`ARGUMENT_TO_PARAMETER`、`ASSIGNMENT`、`SETTER_TO_PROPERTY`、`ARGUMENT_TO_BOUNDARY`、`BOUNDARY_INVOCATION_TO_RETURN`、`BOUNDARY_RETURN_TO_USE`。边界kind/rule对Mapper/Kafka/ES/HTTP/Redis/event/client/library完全相同。 |
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

M1可继续发现标准MyBatis XML/SQL静态结构，M2可继续绑定Mapper Java method与XML statement；M4不得展开它们形成值流。任何“这次调用更新了某列/where”的外部效果均为Gap/待确认，而不是缺少待实现的MyBatis data-flow edge。

### 8.3 DepotHead 必需闭包

要让后续诚实描述该Java路径，至少闭合：

1. class/method route structure；
2. Controller call site :185 → Service method :742；
3. status expression :183 → argument :185 → Service param :742；
4. Service param → setStatus :800 → DepotHead.status property；
5. Mapper call :803的exact M2 call site/target；
6. 同一call的generic `JavaBoundaryInvocationV1`、ordered `record/example` argument IDs及Java-local origins；
7. call所在M3 block/guard与Service :803 locator/rule evidence；
8. XML statement/table/column/where由M1/M2作为独立静态结构保留，同时创建或传播“外部更新/筛选效果未证明”的Gap，不把两侧拼成data-flow。

本次M4 slice对第4段的exact walkthrough是：M3先证明Service :800
`depotHead.setStatus(status)`的call/argument在`dhIds`非空guard TRUE下activated；`status`的
`ARGUMENT_SIMPLE_NAME_READ` work item复用该call ordinal 0已有M4 `ARGUMENT`，并从Service M1
`status PARAMETER`生成一条`DEF_USE`。若M2 exact target与fresh-reopened target body又恰证明唯一
`this.status = formal[0]`，M4则把该caller `ARGUMENT`以`SETTER_TO_PROPERTY`连到DepotHead已有M1
`status FIELD`；target body本身的formal read与direct field write同时分别产生`DEF_USE`和
`ASSIGNMENT`。这些edge的单一guard pair均为上述TRUE context，external PARAMETER/FIELD不在M4重声明。
若setter只是同名、body缺失/生成、有transform/multi-write或field `DECLARES`不唯一，该段就以
line :800 locator的M4 local Gap处置，绝不从`setStatus`拼出FIELD。

缺任一Java或boundary段只能形成Gap，不能跨缺口连一条“看起来正确”的长edge。即使1–8全部存在，也只能证明Java boundary invocation及独立XML/SQL结构，不能证明数据库实际写入或where效果。

### 8.4 Identity、accounting 与预算

nodeId 绑定 snapshot、graph kind、semantic kind、canonical value、source identity 和 rule version；edgeId 再绑定 exact endpoints、rule、resolution、guard/polarity。需要直接predecessor证明的投影edge还绑定前驱edge ID/ordinal。M1–M4 graphId 最后绑定全部排序 nodes/edges、排序`gapDrafts`、图特有traversal或worklist accounting、完整`graphProfileRef.artifactId+sha256`和 coverage；M5按自己的evidence records计算graphId。禁止用display key或仅ID替代profile bytes reference。

每张图必须满足 discovered candidates = admitted exact + gap + reasoned exclusion。跨图index建立全局唯一node/edge catalog，验证ProgramEdge所有外部endpoint以及EvidenceEdge的subject/rule refs；unknown node/edge不得被默默丢弃。Evidence graph从来不把program edge ID塞进`toNodeId`。

预算分图执行maxNodes/maxEdges，并覆盖AST/XML/SQL chars、call depth、CFG states、dataflow worklist、evidence spans和traversal depth。超限不截断为COMPLETE。M4 worklist必须保存完整enqueued/processed ID集合；v3在max+1时fatal，不安装partial draft。

### 8.5 安全与 failure codes

所有 parser 输入来自 verified handles。XML 禁外部 DTD/entity/schema/network。不开客户 classloader，不做 reflection runtime，不运行 annotation processor、MyBatis 或 SQL。

稳定 code：

GRAPH_PROFILE_INVALID、GRAPH_REFERENCE_BROKEN、GRAPH_ACCOUNTING_INVARIANT_BROKEN、CODE_STRUCTURE_INVARIANT_BROKEN、CALL_TARGET_AMBIGUOUS、CALL_RETURN_PAIR_INVALID、CFG_POLARITY_MISSING、CFG_TERMINAL_UNRESOLVED、DATA_FLOW_BINDING_UNPROVEN、DATA_FLOW_WORKLIST_LIMIT_EXCEEDED、EVIDENCE_GRAPH_INVARIANT_BROKEN、EVIDENCE_SOURCE_REOPEN_MISMATCH、XML_SECURITY_POLICY_UNENFORCEABLE、XML_EXTERNAL_RESOLUTION_ATTEMPT、PROGRAM_GRAPHS_RESOURCE_LIMIT_EXCEEDED。

其中`CALL_TARGET_AMBIGUOUS`按M2.1是CALL图的版本化local-Gap `reasonCode`，不是“发现两个合法overload就抛fatal”的异常。重复canonical declaration、M1 endpoint不闭合或同一site出现互斥处置仍分别按`GRAPH_REFERENCE_BROKEN`或`GRAPH_ACCOUNTING_INVARIANT_BROKEN` fatal。

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
- M4新slice必须覆盖generic boundary record、ordered argument/origin、control/locator/rule、optional unknown return、三种boundary edge、技术无关性、ambiguous Gap和旧版本拒绝；任一字段/edge/evidence mutation fail closed。
- status 和 ids 每一段frozen-Java dataflow或boundary argument deletion都使对应invocation Fact不可证明；XML/SQL结构无论是否存在都不能补成外部效果。
- 同名 decoy type/method/XML statement 不能被字符串匹配选中。
- Evidence span hash、rule ID 或 graph endpoint mutation fail closed。
- 不同 root/input order 产生相同五图 canonical bytes。
- standard DOCTYPE 零网络 lookup；external entity mutation fatal。

验收分两层：结构验收要求八个exact files、五图envelope/roots/cross-references/accounting全闭合；DepotHead验收要求8.3逐段mutation使对应Java/boundary invocation Fact不成立，并始终保留external-effect Gap。两层都通过且无字符串猜测旁路，分析步骤“程序图”才算目标实现完成。

### 8.7 已冻结裁决：实现者不得自由推断

- 图种类和五个 filenames 恰为本文件定义的五项；不能合并为 repository blob，也不能把第六种临时图加入 production set。
- node/edge kind 只来自版本化 registry；不支持的语法必须产生8.0.1定义的local Gap，不能发明自由字符串kind。M1只有在源码文件级candidate尚无可证明入口owner时允许空`affectedEntryIds`；M2–M4必须关联非空entry。
- 仓库范围未完整只用canonical `scopeGapIds`表达；它没有source locator或program candidate，不能写入`graph-gaps.jsonl`，也不能伪造入口。
- call、control、data binding 必须逐段 EXACT；simple name、源码邻近、声明顺序和注释不能补边。
- 所有离开frozen Java的exact call只使用generic `JavaBoundaryInvocation`；技术专用boundary kind/rule、跨boundary值流或外部效果edge一律禁止。
- M4不得等待M6 graph index才校验external endpoint，也不得重声明M1 `PARAMETER/FIELD`或发明M1 `PROPERTY`；每个builder的Gap必须随已安装draft持久化，M6只汇总不补写reason/locator。
- Evidence graph 证明 graph provenance，Proof 在 分析步骤“已证明代码事实” 证明 Fact；两者不可合并或互相替代。
- 五图作为一个原子analysis step set安装并按完整identity复用；不能引用四张再现场生成第五张。
- parser core、graph storage、worklist 和并行实现可自行选择；graph 边界、registry、identity、Gap/fatal 与 DepotHead boundary closure 不得改变。

## 9. 当前实现成熟度审计

Wire Reset后的`org.sourceanalysis.app.analysis.graph`已经形成一条可持久化、可fresh-reopen的M1--M6有界链路。既定阶段级直接selector最新结果为 **57 tests / 57 passed / 0 failures / 0 errors / 0 skipped**；它覆盖当前有界builders、module publications/readers、M6 public wire、local Gap投影和scope Gap分账。这个结果证明“当前已经实现并被测试覆盖的行为没有回归”，不证明完整Java/Spring MVC/MyBatis仓库已经具备完整五图。

本轮大合同校正到此收口。后续不再因普通实现缺口重新推导八步流程；[《程序图：已稳定边界与实现待办》](../supplements/program-graphs-implementation-backlog.md)固定哪些决定不再重选、哪些能力仍需用Luna RED和Terra GREEN逐项补齐。

| 状态 | 当前事实 |
| --- | --- |
| **已实现（结构/构建门）** | 目标package、Maven身份和JDK 17 Toolchain已经就位；通用wire头门禁只负责拒绝非`SOURCE_ANALYSIS/v1`输入。 |
| **已实现（M1 有界代码结构）** | `CodeStructureGraphBuilder`能对已验证 UTF-8 Java、标准 MyBatis XML/静态 SQL和静态YAML产生结构draft；Java声明、方法、字段、参数，XML namespace/statement，SQL表/列与静态配置关系都有`ProvenanceDraftV1`。解析失败、XML实体、动态资源或当前不安全的映射形成source-located local Gap；无可证明入口owner时保留空`affectedEntryIds`而不伪造入口。M1 draft可receipt-last安装并fresh-reopen。完整仓库结构分母、field完整identity、全部Mapper/include关系和完整mutation matrix仍未关闭。 |
| **已实现（M2 有界调用图）** | `CallGraphBuilder`已在冻结fixtures中生成Controller→Service、Service→Mapper、Mapper Java→XML statement和call/return pairs；field receiver、部分local declaration、overload/import/未解析情况均按当前规则确定性处置。M2必须fresh-reopen M1，同basis/profile/controls不一致会在读取调用语义前失败；draft及local Gap carrier均可receipt-last安装并重开。完整仓库全部call-site denominator、多入口ownership、递归/循环调用与classpath缺失矩阵尚未关闭。 |
| **已实现（M3 有界控制流）** | 当前builder与测试覆盖线性方法、受支持guard的TRUE/FALSE、显式return/throw、mixed return/throw、call/return structural frame、direct-throw不激活continuation、basic-block连续AST span、前驱闭包及loop profile-stop Gap。M3 v3 draft、typed local Gap、module publisher/reader与fresh-reopen均已落地。通用多重/嵌套`if`、完整`else`树、复杂汇合、异常结构、循环预算、多入口和一般跨调用栈reachability仍未关闭。 |
| **已实现（M4 有界数据流）** | 唯一`buildDataFlow(DataFlowInputs, DataFlowGraphProfile)` seam已覆盖activated internal-Java call的argument→parameter、parameter/local singleton `DEF_USE`、direct local/field `ASSIGNMENT`、direct-setter `SETTER_TO_PROPERTY`、worklist accounting、typed Gap及receipt-last重开。generic boundary invocation、unknown return、branch join/alias及v3发布尚未实现；MyBatis/XML/SQL跨边界传播已从目标移除。 |
| **已实现（M5 有界证据图）** | `EvidenceGraphBuilder`会重新打开已验证source bytes，重算file/excerpt digest，并为当前admitted M1--M4元素建立`SOURCE_EXCERPT`、`RULE_APPLICATION`和support edges。M5 draft可receipt-last安装并fresh-reopen，且M6能将其投影为public evidence refs。完整rule registry、预算、全元素mutation和完整仓库证据闭包仍未关闭。 |
| **已实现（M6 有界正式发布）** | M6只消费fresh-reopened M1--M5与两个上游analysis-step publications，已能原子安装五张public graph、`graph-index.json`、`graph-gaps.jsonl`并由analysis-step store最后创建`program-graphs-receipt.json`。public graph已去除draft-only字段并闭合Evidence/catalog；M1--M4 local Gap一对一进入JSONL，scope Gap只进入coverages/index/receipts。exact-seven/exact-eight、public identity和当前Gap分账均包含在57/57证据中；完整缺/多/交换/乱序/partial-install/collision mutation矩阵仍未关闭。 |
| **部分实现（完整产品执行）** | `PersistedProgramGraphInputReader`、M1/M2 execution和M1--M5 publishers/readers已存在，但尚没有一个产品执行入口从正式VerifiedSourceInventory/ApplicationDiscovery references依序驱动M1--M6并只返回`ProgramGraphsReference`。当前测试仍需显式组装部分sealed inputs；这不是允许的长期调用方式。 |
| **部分实现（本步骤整体）** | 既定有界回归是57/57，但完整多入口fixture、完整graph候选分母、DepotHead generic boundary/argument/unknown-return合同、external-effect Gap及完整jshERP离线ProgramGraphs publication尚未通过。因此ProgramGraphs状态保持 **PARTIAL**。 |
| **历史证据，不是当前能力** | 已删除的`RepositoryModel`/旧FlowView曾投影部分结构、调用、SQL和CFG，并暴露DepotHead跨层status/ids dataflow不足。它们只提供测试反例，不是当前图或永久seam。 |
| **下一实现门** | 按补充backlog的P1开始：先建立完整ProgramGraphs执行入口，再依次补齐M1--M6能力与多入口/完整jshERP离线验收。每个slice只关闭一个已列缺口；不得因为局部不支持重新推导八步主线。 |

历史pre-reset jshERP slice的Gap、0 Flow、0 Capsule不能被目标edge示例改写成成功，也不能被误报为当前SourceAnalysis输出。
