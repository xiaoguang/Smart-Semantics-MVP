# 业务流程：保存连贯入口代码上下文

> [总体设计](../DESIGN.md)；固定key：business-flows，目录steps/05-business-flows/不变。本步材料是入口的技术上下文，不是最终跨入口业务过程。[引擎及子模块合同](../modules/java-code-engines/README.md)定义新的取材来源。

## 1. 为什么存在

JDT或JavaParser已经找出方法、调用和源码。Step05负责让这些结果变成下游可以直接读的一份入口材料，保存后不必再扫描源码寻找缺失Service。它不重新找实现、不重复证明关系、不决定业务含义。

例如财务查询：上游已经有Controller、Service、Mapper声明、billId实参/形参及三段原文；本步按入口把它们放在一起。下一步只分配短引用和组包，不能再倒回五图或源码重建这条链。

原来的strict FlowSlice继续保存已有精确切片，但不等于全部可读代码，不决定业务上下文数量。一个入口可以没有strict Flow，却有完整Service材料；多个入口也可以共用同一方法。

## 2. 上游实际交来什么

| 输入 | 必需性 | 本步怎么用 |
| --- | --- | --- |
| 同一冻结来源/entry inventory | 必需 | 每个入口都有上下文或具体不能收集的原因 |
| 选定引擎java-code-index与ENTRY_MEMBERSHIP | 必需 | 按method/call keys取已有完整正文、候选和位置 |
| 实际五图/Fact/Proof | 可选技术增强 | 附真实同源refs，不筛掉未证明的源码 |
| 已有strict FlowSlice | 可选 | 保持精确技术意义；没有时flowRef=null |
| 既有安全配置/XML片段 | 有定位时附 | 帮助理解调用；不宣称外部实际执行 |

提交了增强引用却找不到文件/身份不符仍是错误。“可选”只允许未生成，不允许忽略已提供产物损坏。JDT第一阶段只交付导航/源码时，本步不能强迫它制造五图或Proof。

## 3. 各子模块怎么做

### 3.1 EntryContextAssembler：组装已有结果

这是现有flow compiler内部接线职责，不新增公共Agent或独立运行服务。

输入为上表不可变结果。按入口membership取出完整MethodCode及每个CallSite：

- caller、调用位置、原始表达式和有序实参一起保留。
- 目标声明与实现候选分别保存；同一调用可能有多个候选，不能选第一个。
- 每候选的形参来自该候选正文/声明；与实参按位置对照，不证明运行时值。
- conditions、return、throw和try/catch以语法范围及完整body保存，不要求先有CFG节点。
- 外部/无法定位/未展开调用保留原文、原因和受影响位置。
- 方法正文去重，调用发生点不去重。`f(id,id)`保留两个实参位置。
- 附可用Fact/Proof/Flow引用，不改变其已证明含义。

本步不调用LS、Core或JavaParser，不按包名/类型名重新找callee。无法组装已有key是输入/接线错误，不启动另一种引擎补齐。

输出是一个EntryCodeContext。它可以有局部限制；是否足以解释业务留给材料检查与模型，不用Java行业字典评分。

### 3.2 EvidenceCapsuleProjector：引用完整上下文，不再复制正文

输入EntryCodeContext的完整不可变视图，输出与该context唯一绑定的Capsule引用。保留完整选中方法、调用目录、候选、来源和限制的可读取性，不独立创造calls/controls。磁盘只写entryContextRef，正文由既有reader沿compilation的codeContextRef到索引读取；业务消费者不自己调用JDT。

有完整body时不能只保留方法名和几行入口。Mapper只有声明时保留声明；没有SQL正文不删Java调用。没有strict Fact、OutcomePath或Proof obligation不阻断已定位源码。

数学最小证据、每片段必须证明一个atom、每body必须属于已证明outcome等旧阅读门禁不再适用。已有strict Flow增强的真实性检查仍保留，但不强加给普通代码材料。

### 3.3 FlowPublicationSpecifier：保存已计算结果

输入compiler/assembler与projector不可变结果。检查同源、entry/method/call/source引用、context/Capsule一致和版本，复用现有canonical/原子安装。

不再次compile/project、枚举Fact或导航。新process/磁盘重开校验保存内容，不为验一次文件再索引仓库。修改新context合同必须同步publisher、reader、artifact policy和直接fixtures，不能只在内存里新增字段。

## 4. 精确合同与可读例子

唯一字段定义是[EntryCodeContext](../modules/java-code-engines/contracts-and-configuration.md#3-entrycodecontext下游真正需要的内容)，替换旧单target/EXACT的CallContext草稿。本页不再复制另一份不同字段表。

```text
入口 E1
 methods：
   M1 Controller完整代码（参数、try/catch、响应、return）
   M2 Service完整代码（return Mapper(billId)）
   M3 Mapper完整声明（@Param("billId") Long billId）
 calls：
   M1调用位置 → M2；actual billId，对照formal Long billId
   M2调用位置 → M3声明；actual billId，对照声明formal
   另有构造器、日志调用：逐项保留目标或未知原因
 technicalEnhancements：
   有真实图/Fact就附引用；没有则NOT_PRODUCED，不伪造
```

形参对照不要求ARGUMENT_TO_PARAMETER Proof：它描述源码中“第几个实参对应目标声明第几个形参”，不是外部数据库效果或完整数据流结论。旧严格证明如果存在，保持独立标记。

注册例必须同时保留Controller与验证码、登录名检查、注册Service完整正文；否则模型只能说“调用注册方法”。[完整真实代码及新材料投影](../examples/java-code-engine-walkthrough.md)展示两例，不把目标投影冒充当前生产格式。

## 5. 可观察产物与下一消费者

保留原步骤中的这些文件位置，按新合同升级受影响格式，不承诺某个固定总文件数：

| 文件 | 内容 |
| --- | --- |
| flow-slices.json | 入口归属/状态、codeContextRef及实际已有strict Flows；解引用可读完整上下文，没有strict Flow时flows可空 |
| flow-coverage.json | 全入口分母、上下文形成情况、技术增强是否可用与具体限制 |
| entry-dispositions.jsonl | 每入口已有技术处置及context收集处置；二者不混同 |
| evidence-capsules.jsonl | 每个安全context的唯一投影；flowRef可空 |
| flow-gaps.jsonl | 入口取材/导航/技术增强的已知限制及来源，不发明业务制度Gap |
| business-flows-receipt.json | 实际输入、产物、引擎basis与状态 |

已交付版本为entry-code-context-v1、flow compilation v6、capsule projection v11、flow slices v6、evidence capsule v9、flow coverage v2、entry disposition v2。`codeContextRef`精确为`{indexArtifact: ArtifactReference, entryId: string}`，Capsule的`entryContextRef`为`{compilationArtifact: ArtifactReference, entryContextId: string}`；COLLECTED须引用正确入口，NOT_COLLECTED的codeContextRef为空且有reason。读取端从既有索引/compilation恢复完整不可变上下文，不重启引擎；五个语义文件加receipt的实际集合不变。旧历史产物不覆盖，不静默双读。详见[字段与版本表](../modules/java-code-engines/contracts-and-configuration.md#5-保存格式位置与复用)。

保留技术处置`COMPILED/GAP/EXCLUDED`的原义。context处置单独使用`COLLECTED/NOT_COLLECTED`和reason，不把“没有strict Flow”误判成没有材料，也不把SOURCE_CONFIRMED之类业务状态塞进technical ledger。

```text
发现的每个入口 = 一个COLLECTED context 或一个NOT_COLLECTED原因
每个COLLECTED context ↔ 一个Capsule
strict Flow数量可以少于context数量
```

Builder只读这些context和Capsule，选完整方法、分包、分配S短ref；没有引擎分支、不调用解析器。模型输入能看见调用/参数/候选/正文，而程序侧path/行号/hash用于来源展示，不强塞给模型。

引用去重只改变保存方式：reader一次打开所需索引并还原各入口完整视图，跨入口共享方法对象；不同入口的CALL/NOT_EXPANDED不互相覆盖。只复制Capsule而缺失上游索引不是完整导出，必须明确报缺引用，不能重跑JDT补救。一个请求里同一方法只展开一次，但发送给模型前不能只留下程序内部key。

没有安全context时仍保留入口处置；0入口保存空集合和真实范围说明。多入口共享方法不能让入口覆盖分母缩水。

## 6. 失败与未完成范围

来源漂移、范围越界、断引用、context/Capsule相互矛盾、版本错或原子安装失败是fatal。保留之前完成产物，不通过重扫/换工具伪装成功。

未知静态目标、多个候选、缺依赖、Mapper无body、没有严格条件Fact是局部限制。保留可读部分；未知条件或业务含义不能变成整仓禁止阅读的理由。

引擎取材默认不以费用限制只展开几层。宿主资源/取消触发时记录未展开点。Builder面对真实模型上下文上限，可以在入口/完整方法单元组包，但不得静默裁去关键Service实现后标完整。是否达到“模型实际看见足够实现”必须直接检查请求内容。

## 7. 当前代码与剩余边界

JDT 第一阶段已经完成 context 生产、投影、保存、读取和 Builder 接力。当前格式为 flow compilation v5、flow slices v5、capsule projection v10、evidence capsule v8。`EntryCodeContext` 保存完整方法、调用点、所有候选、实参/形参、control/exits、supporting sources 与 limitations；没有 strict Flow 的安全入口也可用 `flowRef=null` 发布上下文和 Capsule。

`EvidenceCapsuleProjector` 原样投影 context；`BusinessMaterialBuilder` 只选择已保存方法、分配短引用并格式化模型输入，生产代码不再调用 JavaParser 或 JDT。相同导航限制在进入严格 EntryContext 前去重，避免工具重复报告同一外部调用时破坏集合约束，但不吞掉不同调用点或不同原因。

剩余工作是第二阶段 JavaParser Adapter：把迁移前已有能力映射到同一最终合同，不新增通配 import、继承或重载解析，也不要求追平 JDT。它没有完成不影响 JDT 独立发布。

## 8. Luna与Terra的直接指南

Luna先测试保存后完整Controller/Service/Mapper可读、多个候选不丢、重复调用/参数顺序、无strict Flow、零入口、来源错误和跨入口混引用，再测试Builder实际input包含Service body。不能只断言ID数或JSON非空。

Terra只组装已有索引、升级拥有者DTO/schema和直接读写器；不得手写类型解析、替JDT选择运行时实现、按业务名称过滤方法、造Proof或增加恢复系统。

真实注册、财务与跨领域fixture共用同一算法。DepotHead复杂条件可作回归，但不成为行业规则。业务过程由Step07的Luna理解，Step05不把几个HTTP入口硬编为必经顺序。
