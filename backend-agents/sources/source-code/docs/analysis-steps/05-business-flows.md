# 业务流程

> [总体设计](../DESIGN.md)；固定 key：business-flows，目录：steps/05-business-flows/。本步骤保留，名称不变，产物是入口执行上下文而非最终跨活动业务过程。

## 1. 为什么存在

五图把关系分开放在正确位置，Fact 为部分技术模式提供严格证明；模型仍需要知道一次入口如何把这些代码连起来。Step05 把已有结构、调用、参数传递、条件与返回组织成一份入口技术视图，再投影为 Capsule，让 Step06 直接取用。

这里的 FlowSlice 是 entry-rooted code context。它可以包含分支、已知结果和明确缺口，不等于一个业务活动，更不等于仓库业务过程。Flow、活动和 BusinessProcess 是多对多。跨入口的业务先后和目的由 Step07 的模型阅读判断。

保留 05 的理由是让所有消费者复用一次组织好的代码关系。它不成为第二次图分析、第二层 Proof 审判或自然语言资格筛选器。

## 2. 输入：全入口与五图，附带可选 Facts

输入包括同一冻结 source view、ApplicationDiscovery 全入口 inventory、ProgramGraphs 五图、可用 ProvenCodeFacts、预算和版本化技术规则。现有 exact Facts 保留原来的证明范围；无对应 Fact 的已知 graph/source 信息可以继续作为上下文。

“可选”不能掩盖产物损坏。若提交了 Fact/graph reference，其身份、schema、basis 与引用必须正确。没有某条 Fact 是局部信息状态；指向不存在的 Fact 是完整性错误。

真实财务小例由多张图共同提供：

- 结构图：Controller、Service、Mapper 的方法和参数。
- 调用图：Controller→Service→Mapper 的静态调用绑定。
- 数据图：Controller 参数→actual 的 DEF_USE、actual→Service formal 的 ARGUMENT_TO_PARAMETER、Service 参数→actual 的 DEF_USE、actual→Mapper boundary 的 ARGUMENT_TO_BOUNDARY。最后一段不是进入 Mapper body 的 formal 绑定。
- 控制图：当前该入口 8 个节点（3 个 Controller blocks、1 个 Service block、ENTRY、3 个 terminals）；没有独立 TRY/CATCH/GUARD。完整源码中的 try/catch 可作为 SOURCE_CONTEXT 保留，不能写成当前 CFG 已证明的细边。
- 证据图：每个关系定位到同一快照的源码。

这套结构已有跨层参数证据，但当前核对的图/Fact run 没有 Step05 正式 publication。下列展示目标组织形状，不能当当前 JSON 产物。

## 3. 处理：一次组织，一次投影

### 3.1 EntryRootedFlowCompiler

以全部 discovery entry IDs 为分母，从入口 handler 沿**已经持久化的调用图和控制图**读取已知关系。每条 `CALL_TARGET` 关系直接成为 `CallContext`，每个 guard 节点直接成为 `ControlContext`；参数按 ordinal 连接 actual 与 formal，保留 call/return stack、guard expression、polarity、控制范围、返回值的接收位置和外部边界。Fact 只补充“这个结论已严格证明”的标签，不能决定哪条已定位的代码关系可被模型阅读。

同一个 caller 对相同 target、以相同实参表达式调用多次，仍是多次独立的代码发生点；不能因显示文字相同就折叠。`CallContext` 的稳定排序键必须附带其精确 source/evidence occurrence，因此后续材料能够看到两个分支或两个语句位置各自发生的调用；只在 call-site 与 evidence 都相同时才允许去重。

同一调用的实参列表是有序序列而非集合：`transfer(id, id)`必须保留两个位置，后续材料才能说明两个形参都接收了同一值。只有引用类 ID、限制代码等集合字段才要求排序且唯一。

图上不确定的调用目标不升级为 exact；读到方法源码也不等于调用绑定已证实。不能解析的局部语法、未知分支或外部返回用明确 limitation 表达，同时保留其前后的已知结构。特别是 `graph-gaps.jsonl` 中属于该入口且有安全 locator 的 Gap，必须进入 EntryContext 的限制和定位集合：模型能看到“这里有调用/条件，但静态目标或效果未知”的原文，不能把它变成假 target。递归、深度、节点及字节预算限制遍历范围，超限给受影响 entry 明确原因。

不要求为了阅读每个 guard 都找到 CLOSED CONTROL_CONDITION。有严格条件 Fact 时附 proof ref；没有时保留 graph/source context 并明确 exact condition proof unavailable。不能借 CONTROL_CONTEXT 冒充 CONTROL_CONDITION，也不能把未证明 graph edge 补成已知连线。

不要求枚举所有分支组合或循环路径。结构化分支、共享前缀、可知终点和未展开区间足以表达可阅读的控制上下文；只有声明了某条完整 exact path 时才满足该 path 的严格条件。已知 guard 与已知 outcome 必须保存，不能因无法穷举其他路径一起丢弃。

### 3.2 EvidenceCapsuleProjector

从已组织的执行视图选择连贯阅读需要的源码位置、技术观察及限制。选择入口/参数定义、主调用链、完整条件及其作用域、必要赋值、返回和边界。保留当前可用 Fact/Proof refs 作技术增强，不只从 admitted atoms 的最小 spans 取材。

短方法可以完整保留，长方法可选连续段落并带必要上下文。删除任一 span 都必须损失某条 Proof obligation 的“数学最小性”不再是业务可读 Capsule 的门槛；真正要求是有界、可追溯、没有无关大段、关键结构完整。不要把 if 谓词和 if body 分离，也不要只保留调用名却删除实参来源和返回处理。

### 3.3 FlowPublicationSpecifier

复用 compiler 与 projector 的不可变结果，检查 entry/Flow/Capsule/refs、预算和保存格式，安装五个 semantic payload 与 receipt。普通路径不再次 compile/project，不重新跑 Fact 枚举，也不靠将发布结果再次递归发布验证自己。

当前普通路径已经让 FlowCompilationModulePublisher、CapsuleProjectionModulePublisher 与 FlowPublicationSpecifier 复用 owner 产出的 immutable compilation/projection，不再 compile/project。canonical bytes/hash、原子安装和外部重开验证仍保留；这是已实现的算法去重，不等于删除完整性保护。本次批准的旧解释链清理不回头修改这条 Step05 算法。

## 4. 目标上下文示例

**TARGET_CONCEPTUAL_PROJECTION_NOT_WIRE_SCHEMA**。短 ID 仅用于说明。字段及约束冻结如下，后续实现直接按此更新拥有者的 record/schema 版本；本轮未改现有 Schema，不把新增字段塞入旧版本。

~~~json
{
  "entryId": "E1",
  "flowId": "T1",
  "executionContext": {
    "calls": [
      {"id": "C1", "from": "Controller", "to": "Service", "resolution": "EXACT",
       "arguments": [{"ordinal": 0, "actual": "billId", "formal": "billId", "basis": "data-edge:D1"}]},
      {"id": "C2", "from": "Service", "to": "Mapper", "resolution": "EXACT",
       "arguments": [{"ordinal": 0, "actual": "billId", "formal": null, "basis": "ARGUMENT_TO_BOUNDARY:D2"}]}
    ],
    "controlContexts": [
      {"kind": "TRY", "steps": ["list=C1", "res.code=200", "res.data=list"], "sourceRef": "S1", "basisKind": "SOURCE_CONTEXT", "conditionProofRef": null},
      {"kind": "CATCH", "steps": ["log exception", "res.code=500", "res.data=获取数据失败"], "sourceRef": "S1", "basisKind": "SOURCE_CONTEXT", "conditionProofRef": null}
    ],
    "returns": ["Mapper result → Service return", "Service result → Controller local list → res.data", "Controller returns res"],
    "boundary": {"call": "C2", "externalExecution": "UNKNOWN"}
  },
  "factRefs": ["F1", "F2", "F3"],
  "limitations": ["No proof of actual database execution"]
}
~~~

null 不代表该段源码不可信，而代表没有这类 exact Proof。TRY/CATCH 也不是 JAVA_GUARD_CONDITION 的 if guard；不能为了填字段强行套用。SourceRef 是说明用短引用，正式程序侧 provenance 仍使用既有 locator/graph refs，模型侧短 refs 由 Step06 建立。

### 4.1 可实现的最小 EntryContext 合同

唯一关系模型由 EntryRootedFlowCompiler 持有。目标在现有 flow-slices.json 中增加 `entryContexts[]`；不新增文件、Module、图或证明系统。strict FlowSlice 可以继续保存已建立的精确切片，EntryContext 是全部入口的可读视图，`flowRef` 可为空。复用现有 SourceExcerptV1/SourceLocatorV1、图元素 ID、Fact/Proof ID 与 artifact 身份算法。

下列字段全部 required；只有标 nullable 者允许 null，数组允许空但不得省略：

| Record / 字段 | 类型与约束 |
| --- | --- |
| EntryContext.contextId / entryId | String；程序生成身份 / 现有 discovery entry ID；entryId 在集合中唯一 |
| EntryContext.flowRef | String nullable；已有 strict Flow ID，无则 null，不能制造成功 Flow |
| EntryContext.entrySignature | String；准确 handler 声明与入口参数 |
| EntryContext.calls | CallContext[]；按已有执行/源码顺序，未知顺序不得强排为已证实顺序 |
| EntryContext.controls | ControlContext[]；条件、分支或 try/catch 源码作用域 |
| EntryContext.returns | ReturnContext[]；表达式及接收处，未知目标可 null |
| EntryContext.fragments | CodeFragment[]；至少一段准确 ENTRY_METHOD，必要 callees/条件/返回代码一并保留 |
| EntryContext.factRefs | String[]；可空，仅真实 admitted Facts |
| EntryContext.limitations | ContextLimitation[]；具体缺口及影响，不填泛化低置信度标签 |
| CallContext.callerSignature | String；准确 caller 声明，模型投影保留可读调用方，不要求读者解码图 ID |
| CallContext.callSiteRef / targetSignature | String nullable / String nullable；有真实图 ref 才填，未知 target 保持 null |
| CallContext.resolution | EXACT 或 UNRESOLVED；EXACT 只复用既有图绑定 |
| CallContext.arguments | ArgumentContext[]；按 ordinal 升序 |
| CallContext.boundary | Boolean；是否停在 frozen Java 外或无可分析 body 的边界 |
| CallContext.fragmentIds / basisGraphRefs | String[]；至少一个片段 / 可空图 refs |
| ArgumentContext.ordinal / expression | 非负 integer / String；源码实参表达式 |
| ArgumentContext.formalParameterRef | String nullable；有 ARGUMENT_TO_PARAMETER 才填真实 formal ID |
| ArgumentContext.bindingKind | ARGUMENT_TO_PARAMETER、ARGUMENT_TO_BOUNDARY 或 SOURCE_CONTEXT |
| ArgumentContext.basisGraphRefs / fragmentIds | String[] / String[]；后者非空 |
| ControlContext.kind / expression | IF、TRY、CATCH、LOOP、OTHER / String；准确条件或结构文本 |
| ControlContext.polarity | TRUE、FALSE 或 null；无已知极性不猜 |
| ControlContext.parentIndex | 非负 integer nullable；指 controls 内包围结构，禁止环 |
| ControlContext.fragmentIds / basisGraphRefs | String[] / String[]；片段非空 |
| ControlContext.basisKind / conditionProofRef | GRAPH_AND_SOURCE 或 SOURCE_CONTEXT / String nullable；null 不升级为 Proof |
| ReturnContext.expression / receiver | String / String nullable；例如 list 或 res.data，有何种来源就保留何种标签 |
| ReturnContext.fragmentIds / basisGraphRefs | String[] / String[]；来源片段非空 |
| CodeFragment.fragmentId / role | String / ENTRY_METHOD、CALLEE_METHOD、DECLARATION、CONTROL、RETURN、BOUNDARY、STATIC_SQL |
| CodeFragment.excerpt | 完整现有 SourceExcerptV1，包含真实 locator、原文与摘要 |
| ContextLimitation.code / detail / fragmentIds | String / String / String[]；明确限制及受影响位置 |

不得通过 SOURCE_CONTEXT 添加假的 graph edge；它只是显示原文已经明确写出的结构。例如财务例的 try/catch 与 return 表达式可读，但当前 CFG 未细分，故 basisGraphRefs 不声称相关分支边存在。Mapper 声明的 @Param("billId") 可作 DECLARATION 片段，ARGUMENT_TO_BOUNDARY 的 formalParameterRef 仍是 null。

Capsule 只序列化这个 EntryContext 中选定的完整内容，目标增加 required `contextId` 并令 `flowRef` required nullable。不另建独立关系集；它的 calls/controls/returns/fragments 必须逐项引用或原样投影 context，不再推理。每个有安全片段的入口恰一 EntryContext、恰一 Capsule；没有安全材料的入口只有明确 disposition。投影超模型预算时保留 context 和限制，Step06 零请求；不能悄悄裁掉条件使其“合格”。这些目标字段需要升级当前 flow-compilation/flow-slices/Capsule schema；旧格式不原位解释为已含上下文。

Capsule 可以没有 admitted Fact：这表示严格证明规则尚未覆盖该入口，并不表示入口源码不可读。只要入口上下文、至少一个可观察 outcome、所选源码片段和相应 obligation 都闭合，Capsule 仍可进入后续业务解释；模型会同时看到这个限制，不能把它升级为已证明事实。

## 5. 输出与下一消费者

| 文件 | 内容 |
| --- | --- |
| flow-slices.json | entry-rooted 调用/数据/控制/返回/边界关系、可选 Facts、已知 outcomes 和 gaps |
| flow-coverage.json | 全入口分母、已组织/未组织范围、技术覆盖及预算限制 |
| entry-dispositions.jsonl | 每入口恰一条 COMPILED、GAP 或 EXCLUDED，带具体原因 |
| evidence-capsules.jsonl | 每个安全 EntryContext 的有界原样投影与代码，flowRef 可为空 |
| flow-gaps.jsonl | 未知/unsupported/预算造成的局部限制及来源 |
| business-flows-receipt.json | 同源上游身份、controls、artifacts 与状态 |

目标 cardinality 为每个安全入口 1 个 EntryContext 与 1 个 Capsule；strict Flow 数可小于 context 数。0 strict Flow 仍可有安全上下文；0 安全上下文仍保存完整命名产物和全部入口 dispositions。当前 N strict Flows→N Capsules 是旧实现约束，后续按上述明确版本改变，不能继续阻断 noFlow 阅读。旧 modelEligibleFlowSliceIds 等 strict-Flow 覆盖信息可以保留技术含义，但不能是 Step06 安全源码阅读的唯一 gate；业务材料资格由定位安全、必要上下文和预算决定。

BusinessMaterialBuilder **直接读 flow-slices 的 EntryContext 及 Capsule**，只在已经提供的位置范围提取原文、按预算选择完整单元并映射短 refs；不再自行连接 graph/data refs，不重跑调用分析。特别需要传递 actual→formal、guard scope、正常/异常返回及边界限制。完整 OutcomePathViews 中有用的内容不能在 parser 中被静默忽略。

processJoinSignals 只是召回线索，可来自已知调用/参数/标识/类型/数据联系。保留已有严格字段的证明标签；若新增普通 graph/source cue 应显式区分 basis，不把它塞进要求 Proof 的旧字段。共享表、tenantId、日志或名称相似都不能单独证明顺序、对象同一性和唯一过程归属。Step07 才做业务判断。

## 6. 无 Flow、覆盖与失败

无正式 Flow 的入口仍由本步骤同一个 compiler 生成 flowRef=null 的 EntryContext，包含安全源码和已知关系并注明 FLOW_NOT_AVAILABLE。当前实现要求 Step05 已有已验证源码、入口和程序图；若未来支持更早的无图输入，也必须由 Step05 产生明确的安全 EntryContext 和限制，不能让 Step06 或 Builder 再扫描 Java、按 field type/name/arity 猜测 callee。Step06 只封装该结果，不能另行构造后备调用链，也不能假造成功 strict Flow。Step05 的已有技术 gaps 不被业务解释抹掉。

全部 entries 恰一处分，shard 的 ID 集合两两不交且 union 等于 discovery 分母。部分入口超预算、unsupported 或数据不全，可以留下局部 Gap；一个入口成功不能让整个仓库业务完成。预算限制包括遍历节点/深度、已展开路径、源码与投影 bytes；不能以丢弃条件、遗漏入口或假完整来满足限制。

fatal 包括来源漂移、损坏 artifact、非法 path、断 refs、Flow/Capsule 互相矛盾、错误 owner、伪造 exact Fact、超安全边界及原子安装失败。未知静态 edge 或业务含义属于诚实限制，不能当成全仓源码禁止阅读的理由。

同进程复用 immutable typed view。磁盘、新进程、导入及显式复用核验 identity/hash/schema/ref/basis，重新读源码时核验所读 bytes；独立审计允许重放，普通发布不重跑算法。

## 7. DepotHead 与合成跨活动例子的边界

DepotHead#batchSetStatus 的源码包含状态检查、配置条件、更新调用及返回，适合检验较复杂的条件保留。八文件 BOUNDED_PATH_SET 只用于局部 walkthrough；历史 pre-reset 的 Gap/0 Flow 结果不能当作新运行。只有未来实际产物包含已知 guard、Java 参数流、Mapper 边界及限制时，才可描述该次技术编译完成；不能用文档示例把当前计数改为 1。

合成“补货申请 → 创建采购单 → 登记收货 → 形成应付账单”应在本步形成各入口自己的技术上下文，不在这里直接宣布为一条长业务 Flow。具体角色、顺序和引用关系来自合成源码及 Step07 模型审阅，而非 Java 的行业字典。

## 8. 当前实现与后续测试

EntryRootedFlowCompiler 现已直接从持久化调用图/控制图保存每个入口的 EntryContext：调用关系、形参、边界、控制、返回、限制和固定源码 locator；无 strict Flow 的入口也保存同一结构。Fact/Proof 仍随上下文作为严格结论增强，但不再筛掉图中已定位的调用或控制关系。属于入口的图 Gap 也将其安全 locator 保留为限制，以便后续材料显示未知目标附近的真实源码。该对象已随 flow-compilation、Capsule 和 flow-slices 公开产物持久化。对应 schema 已升级为 flow-compilation v4、capsule-projection v9、flow-slices v4 与 evidence-capsule v7；旧版本不能被新读取路径静默当作包含连贯上下文的结果。

普通发布路径已停止重复 compile/project，只检查已保存对象的引用闭合和 Flow-rooted span 身份。Capsule span 同时保存 evidenceNodeId，因此发布器可拒绝跨 Flow 替换或裸 evidence-node 替换，而不重新执行完整投影算法。BusinessMaterialBuilder 已直接读取 flow-slices 的 EntryContext；其旧的源码重扫/直接 callee 猜测路径已删除。

已完成的直接回归覆盖：Controller→Service→边界调用、参数、guard、返回和源码片段同包出现；长方法中的 Mapper 写入不会因为没有对应 Fact 而从材料消失；无 strict Flow 时仍从保存的 EntryContext 形成材料；以及跨 Flow/裸 evidence-node span 变异在发布前被拒绝。后续测试转向 Step02 Spring method condition、完整活动/过程/报告内容保留和真实模型小包质量，不回头重建这条已稳定的关系接力。

固定财务查询的图/Fact run 没有本步 publication，Step06 另一历史 run 的后备材料也不补足这个**样本运行**缺口。当前通用 Step05→Builder 接力已经实现，但不能跨 run 拼接这两个历史产物来声称该财务样本已有正式 Flow、完整单次执行或自动九章验收。
