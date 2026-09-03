# 程序图：已稳定边界与实现待办

> 权威功能设计仍是[程序图详细设计](../analysis-steps/03-program-graphs.md)。本文只回答两个实施问题：哪些边界已经稳定、哪些能力还没有实现。它不是另一套架构，也不改变八步主线。

## 1. 为什么需要这份清单

程序图已经经历了一轮较大的合同校正：五个 builder 的中间结果都落盘并重新打开，局部源码 Gap 与仓库范围 Gap 分开，M6 能把五张图发布为正式七项语义产物。既定直接 selector 当前为 **57 tests / 57 passed / 0 failures / 0 errors / 0 skipped**。这证明当前被测试覆盖的 M1--M6 有界链路闭合，不证明所有 Java/Spring MVC/MyBatis 仓库已经能得到完整五图。

从本轮结束起，后续实现遵循一个收口原则：

> 不再因为一个尚未支持的 Java、调用、控制流或数据流形状而重新推导八步流程。先把它列入“仍未实现的能力”，然后在已稳定的模块 Interface、正式 wire 和 Gap 规则内，用一个新的 Luna RED 与一个最小 Terra GREEN 补齐。

只有确实无法用已稳定合同表达时，才按第 4 节升级；不能把普通实现缺口包装成架构问题。

## 2. 已稳定的边界

下面各项在后续 ProgramGraphs 开发中视为关闭的设计决定。实现者可以修 bug 或扩充内部算法，但不得重新选择另一条数据流。

1. **主线位置稳定。** ProgramGraphs 只接收已发布并重新验证的 VerifiedSourceInventory 与 ApplicationDiscovery，输出交给 ProvenCodeFacts 和 BusinessFlows。它不调用模型、不生成 Fact、不编译 Flow、不生成 Markdown。
2. **五图职责稳定。** 正式图恰为代码结构、调用、控制流、数据流、证据五张。Evidence 证明图元素来自哪里；下一步的 Proof 才证明业务事实，二者不能合并。
3. **六模块接力稳定。** `CodeStructureGraphBuilder → CallGraphBuilder → ControlFlowGraphBuilder → DataFlowGraphBuilder → EvidenceGraphBuilder → ProgramGraphSetPublicationSpecifier`。每个模块先安装自己的 canonical payload 和 receipt；下游只消费 fresh-reopen 后的 sealed aggregate，不接受 raw draft、自由 JSON、工作区路径或内存旁路。
4. **正式输出稳定。** ProgramGraphs 恰有七项 semantic payload：五张图、`graph-index.json`、`graph-gaps.jsonl`，再由 analysis-step store 创建 `program-graphs-receipt.json`。M6 不预报 root/receipt，也不增加第六张图或 summary blob。
5. **公共 wire 稳定。** 已冻结的 graph kind、node/edge kind、schema 名、artifact type、文件名、identity、自排除、canonical ordering 和 direct-preimage 规则继续生效。内部实现不能用别名、旧 schema、兼容 reader 或第二套 identity 绕开它们。
6. **Gap 分账稳定。** M1 已验证源码文件上的 local Gap 必须有 candidate 和精确 locator；在无法证明入口 owner 时可有空 `affectedEntryIds`。M2--M4 local Gap 必须属于至少一个入口。仓库范围不足只进入 `scopeGapIds`、graph index 和 receipts，绝不伪造成 `graph-gaps.jsonl` 的源码问题。
7. **M6 权限稳定。** M6 只重开、校验和投影 M1--M5；它不能重新解析源码、修补边、改写 Gap、创造 locator 或决定业务语义。`graph-gaps.jsonl` 恰投影 local Gap；index/receipts 使用 local 与 scope ID 的去重并集。
8. **证据和安全边界稳定。** 每个 admitted 程序 node/edge 至少有一条 source-excerpt + rule-application 路径。parser 只读已验证 UTF-8 bytes；禁止客户 Maven、客户代码、外部 XML 资源、活动工作树和网络。
9. **完成口径稳定。** 一个 DepotHead 切片或一个 GREEN 测试不能代表整个仓库完成。最终必须处理完整冻结仓库的全部已发现入口，每个 graph candidate 都有唯一 exact / Gap / reasoned exclusion 处置，且五图、index、Gap 和 receipt 可从磁盘重新验证。

这九项是未来 Luna/Terra 的上游约束，不是待重新讨论的选项。

## 3. 仍未实现的能力

以下按阻塞完整仓库分析的优先级排序。它们是实现 backlog，不是架构待定项。完成一项不自动表示 ProgramGraphs 完成；只有第 5 节全部满足才可关闭本步骤。

### P1：形成一个完整的 ProgramGraphs 执行入口

当前各 builder、publisher 和 reader 已能在有界测试中接力，但尚缺一条产品执行路径：在固定的 `RepositoryAnalysisAgent.executeStep` Interface 背后，从两个正式 upstream references 开始，依序运行 M1--M6，并只返回一个 `ProgramGraphsReference`。该路径必须负责 module address、同一 controls/profile/basis、失败停止和已安装上游保留，调用者不得手工组装 draft；这里不增加新的 public Interface。

**完成证据：** 一个analysis-step内部执行模块用真实 canonical stores 从 VerifiedSourceInventory/ApplicationDiscovery references 走到八项 reader-visible 产物，并由既定run核心调用；任一中间 module 篡改、缺失或碰撞都会在下一模块读取前失败。

### P2：补齐 M1 的仓库级结构分母

当前 M1 已覆盖有界 Java 声明、方法/字段/参数、标准 MyBatis XML/静态 SQL、静态 YAML 与文件级 Gap。尚需证明它对完整 inventory 中每个受支持结构 candidate 都有唯一处置，并补齐目标设计要求的完整 parameter/field canonical identity、Mapper include/statement/table/column 关系、资源预算与 mutation matrix。

**完成证据：** 多 Maven module、同名 FQN decoy、完整 Mapper/XML 关系和全文件 denominator 测试闭合；删除任一声明只删除对应节点/边或产生精确 Gap，不影响不相关文件。

### P3：把 M2 从有界直接调用扩展到完整支持范围

当前 M2 支持已验证 fixture 中的 Controller→Service、Service→Mapper、Mapper Java→XML statement、call/return pair，以及一部分 field receiver、local declaration、overload/import/未解析 Gap。它尚未证明完整仓库中全部 activated direct-call candidate 的 receiver/static type/signature/overload 分母，也缺完整的多入口 ownership、递归/循环调用处置、classpath 缺失与 Mapper binding mutation matrix。

M2.1在[程序图详细设计](../analysis-steps/03-program-graphs.md#m21-调用目标候选集合与唯一决议算法)冻结局部算法，不再由实现者自由选择：receiver static type先闭合；直接声明method按name、固定arity、可证明visibility和bounded argument compatibility形成有序目标候选集合；`|C|=1`才是EXACT，`|C|>1`是`CALL_TARGET_AMBIGUOUS`，零候选按是否存在unsupported actual分别是`CALL_ARGUMENT_TYPE_UNRESOLVED`或`CALL_TARGET_UNRESOLVED`。`null`是受支持的特殊actual：匹配全部reference/array formal、排除primitive formal，不能提前写argument-type Gap。重复canonical signature或M1 endpoint不成双射是fatal，不是ambiguity。

这只是目标设计，不能写成实现事实。当前`CallGraphBuilder`仍以一个推导signature做单值lookup，尚未实现候选集合、`null`兼容、多entry owner并集或`CALL_TARGET_AMBIGUOUS`路径；状态仍为OPEN。

**完成证据：** 每个被扫描的物理call site恰有exact call pair、一个typed local Gap或reasoned exclusion；同一site被多个entry到达时只处置一次且owner为完整排序并集；`String`/`Integer` overload+`null`稳定产生一条`CALL_TARGET_AMBIGUOUS`，删一overload变为EXACT，reference/primitive和primitive-only mutation按M2.1处置；同名、重载、多实现和缺依赖不能被简单名称或源码顺序选中。

### P4：把 M3 从已验证控制流形状扩展到通用入口路径

当前 M3 已验证线性方法、一个受支持 guard、显式 return/throw、mixed return/throw、call/return structural frame、direct-throw 不激活 continuation、basic-block AST range 与 loop profile-stop Gap。尚未完整支持或处置多重/嵌套 `if`、完整 `else` 树、多个调用点、复杂路径汇合、异常结构、循环预算、多入口 ownership 和一般跨调用栈 reachability。

**完成证据：** 每个入口的所有可达路径终止于 exact terminal、Gap 或 reasoned exclusion；TRUE/FALSE、call/return、throw 和 continuation mutation 任一变化都会使对应路径变化或 fail closed，不能按源码行序补边。

### P5：闭合 frozen-Java 数据流与通用 boundary contract

当前M4已验证activated internal-Java call的argument→parameter、parameter/local singleton reaching definition、direct local/field assignment、direct setter→field和相关typed Gap。目标不再要求把Java值传播到MyBatis placeholder/column/where：任一exact call一旦离开frozen Java，不论目标是Mapper、Kafka、ES、HTTP、Redis、event、client还是library，都必须停在同一个generic `JavaBoundaryInvocation`。

本项的完成证据改为同一DepotHead源码上的以下闭包：

```text
request status / ids
  → frozen-Java definitions, uses, fields and arguments
  → JavaBoundaryInvocation(
      DepotHeadMapper.updateByExampleSelective(DepotHead, DepotHeadExample),
      ordered record/example argument IDs,
      Java-local proven origins,
      M3 control context,
      Java call locator + generic rule)
  → external database effect = Gap / 待确认
```

若外部调用返回值被frozen Java消费，还必须有`UNKNOWN_BOUNDARY_RETURN` source、invocation→return和return→Java-use typed edges；不得推断具体值。static target type/method/signature不能唯一确定时只产生Gap。M1的XML/SQL结构发现与M2的Mapper Java→XML binding保持不变，但它们不能给M4跨boundary补edge，也不能把静态statement/table/column/where结构升级为已执行副作用。

**版本门：** M4 draft=`program-graphs-data-flow-draft-v3`，public data flow=`program-graphs-data-flow-graph-v2`，M5 draft=`program-graphs-evidence-graph-draft-v3`，public evidence=`program-graphs-evidence-graph-v3`，index=`program-graphs-graph-index-v2`。旧`program-graphs-data-flow-draft-v2`、`program-graphs-data-flow-graph-v1`、`program-graphs-evidence-graph-draft-v2`、`program-graphs-evidence-graph-v2`、`program-graphs-graph-index-v1`及任何新旧混搭必须拒绝；不提供兼容reader、默认字段或原位迁移。只可复用相同M1–M3正式references重新运行M4→M6。文件名、五图、M6七项semantic payload及analysis-step八文件不变。

**Luna RED：** 在既有M4/M5/M6 public seams先冻结generic node/record/三种edge、ordered argument/origin、control/locator/rule、unknown return、技术无关性、external-effect absence与旧版本拒绝；golden独立手写，使用真实canonical stores。ambiguous Gap用例必须等M2 public seam先提供正式`CALL_TARGET_AMBIGUOUS`输出后再写，且只验证M4承接Gap、不猜target。**Terra GREEN：** 只实现这些RED所需的v3/v2 records、identity、evidence和M6投影；不得引入技术专用boundary enum/rule、解析外部实现或通过XML/SQL补值流。

### P6：补齐 M5 的全量 Evidence 闭包与预算

当前 M5 已能重开 provenance locator，重算文件/摘录哈希，并为当前 admitted M1--M4 元素生成 source/rule 节点和 support edges。尚缺完整 rule registry、所有 graph element/多 provenance 组合的 mutation matrix、跨根确定性、资源预算，以及完整仓库规模下“无孤儿、无缺证据、无错误 owner”的验证。

**完成证据：** 五图每个 admitted node/edge 都有可重开的最小证据路径；更改 locator、文件 SHA、摘录 SHA、rule 或 subject owner 必须失败；M5 仍不得为缺失的程序边制造 Evidence。

### P7：强化 M6 的完整发布矩阵

当前 M6 已通过有界的 exact-seven module、exact-eight analysis-step、public evidence/index、local Gap 投影和 scope Gap 分账测试。尚缺设计要求的五图缺失/交换/额外、所有 schema/type/address/controls/predecessor 变化、全部 local-Gap 字段替换、partial install/collision、乱序和 full fresh-reopen mutation matrix。

**完成证据：** 七项 payload 和 receipt 从任意干净根目录重开得到相同 identity；任何一张图或任一引用变化都不会留下可观察的半套 ProgramGraphs publication。

### P8：完成多入口和完整 jshERP 离线验收

现有57/57是有界fixtures的直接测试，不是完整仓库运行。ProgramGraphs最终还需至少两个独立入口的graph ownership/Gap隔离，以及固定jshERP commit的完整离线输入。从完整入口分母出发，每个入口相关候选必须被五图唯一处置；DepotHead的frozen-Java链与generic boundary必须逐边证明，boundary外数据库效果必须留下准确Gap。

**完成证据：** 完整 jshERP ProgramGraphs publication 可重新打开；仓库中不存在未计数入口、graph candidate 或 orphan Evidence。验收不运行客户 Maven/代码，不访问网络，不调用模型。

## 4. 以后可以改什么，什么必须升级

### 实现者可以直接做的局部变化

- 在现有 builder 内增加对已注册 Java/XML/SQL 形状的确定性解析算法；
- 增加 private/internal record、索引、worklist 或缓存，只要不越过 module Interface；
- 添加 Luna public-seam RED、独立 fixture、mutation 和性能测试；
- 修复 identity、排序、引用、owner、Gap 投影、source reopen 或原子安装的局部 bug；
- 改善性能或分片，只要完整 denominator、canonical bytes 和输出 identity 不变；
- 在对应详细设计的“当前实现审计”和本文 backlog 中更新事实，不重写主流程。

这些变化仍须遵循 design-publication gate、Luna RED → Terra GREEN、定向 selector 和每 Agent 独立 progress。

### 必须停止并交回 Sol/ultra；跨步骤时再交用户确认

- 需要增加、删除或合并图种类、M1--M6 模块、七项 semantic payload 或 analysis-step receipt；
- 需要改变 public wire 字段/schema、artifact type/name、identity preimage、Gap 三分法或 public Interface；
- 想让 M6、ProvenCodeFacts、BusinessFlows 或模型重新解析源码补边；
- 想把 local Gap、scope Gap、fatal 或 exclusion 相互替代；
- 想改变 ProgramGraphs 的上游、下游、模型职责、安全范围或完整仓库完成口径；
- 一个业务必须能力无法用已注册 node/edge/rule/Gap 表达。

前五类影响已稳定边界，不能由实现 Agent 权衡。最后一类先由 Sol/ultra 判断能否作为本步骤内的版本化扩展；若影响八步主线、跨步骤 postcondition、正式数量、公共 Interface 或业务目标，必须交回用户讨论确认。

## 5. 后续每个实现切片的验收清单

每个 future slice 必须全部回答“是”，否则保持 IN_PROGRESS：

1. 这个切片是否只关闭第 3 节的一项明确能力，而没有改八步流程？
2. Luna 是否先在现有 public seam 写出一个因目标行为缺失而失败的最小 RED？
3. Terra 是否只实现该 RED，并且没有源码字符串 fallback、simple-name 猜测或内存旁路？
4. exact、local Gap、scope Gap、reasoned exclusion 与 fatal 是否仍按既有规则互斥？
5. 新/改 graph element 是否有精确 provenance，且对应 module 安装后可以 fresh-reopen？
6. 删除或变异关键源码/前驱引用时，目标 node/edge 是否消失、变 Gap 或 fail closed，而不是继续存在？
7. 当前模块 selector、它的 publisher/reader selector和受影响的后继 selector是否通过？
8. 既定 57-test ProgramGraphs selector是否仍为 57/57；新增测试另计，不通过改数字掩盖回归？
9. 文档是否只更新当前实现事实和 backlog，不重新推导已稳定边界？

只有 P1--P8 全部关闭、上述清单通过，并完成多入口与完整 jshERP 离线验收，ProgramGraphs 才能从“部分实现”改为“目标实现完成”。在此之前，57/57 只能表述为“当前有界合同回归通过”。
