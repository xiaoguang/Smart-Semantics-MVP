# 更多发现：跨对象业务串联与 Mapper SQL

记录日期：2026-09-15。

**状态：讨论备忘，留待后续讨论。** 用户本次只要求记录，不表示批准实施。本文不替代总体或模块设计，不新增必做模块、提示词合同或验收门槛；不启动源码扫描、模型调用或代码修改。

## 1. 要解决的业务问题

目前已经能解释不少局部活动及过程，但对“请购 → 采购订单 → 采购入库”这类跨对象、跨入口的长链，仍未充分回答：

- 前一个活动产生什么业务对象，后一个活动如何引用它？
- 数量、金额、状态或完成进度如何在对象之间传递或回写？
- 哪些是必经步骤，哪些是可选、并行、回退或支撑查询？
- 哪些联系明确，哪些只能合理推断，哪些仍无法判断？

核心仍是生成可阅读的业务语义文档，不是扩大证据系统。过程数量、多个 Activity 或多个阶段，本身不能证明已经识别完整生命周期。

## 2. 已核实：现有材料并非完全缺少跨对象联系

以下来自已经保存的 Java 源码，不是新模型输出，也不是预先指定给框架的行业规则。

| 已保存片段 | 实际内容 | 能帮助理解什么，以及不能据此声称什么 |
| --- | --- | --- |
| S688，DepotItemService.saveDetials，380–738 行 | 当前单据是采购订单且 linkApply 非空时，计算并更新关联请购单状态 | 采购订单可以承接请购单并影响其进度；不能据此说所有采购订单必须来自请购单 |
| S1583，DepotItemService.getFinishNumber，1246–1280 行 | 查询完成数量时，将采购订单映射到采购入库，也包含销售订单到销售出库、出入库到退货的类型映射 | 支持识别对象转换关系；这个查询本身不等于自动创建下游单据 |
| S688，DepotItemService.saveDetials，380–738 行 | 采购入库等单据有 linkNumber 时，重新计算并回写关联订单状态 | 下游单据和原订单存在关联及进度联动，不只是名称相近 |
| S689，DepotItemService.getBillStatusByParam，739–777 行 | Java 比较原单商品数量与分批操作数量，计算完成情况 | 支持解释分批办理与完成进度；底层查询究竟纳入哪些记录，还需查看相关 SQL |

真实保存原文：[类型转换映射](../../.workspace/jsherp-full-parallel-20260913/stores/runs/analysis-run--c589a5e62f1327d1991899949a5678b267f3da04ec0eea821bb466c37c6ac035/steps/07-repository-knowledge/modules/01-business-process-publisher/sources.md#s1583)、[关联及状态回写](../../.workspace/jsherp-full-parallel-20260913/stores/runs/analysis-run--c589a5e62f1327d1991899949a5678b267f3da04ec0eea821bb466c37c6ac035/steps/07-repository-knowledge/modules/01-business-process-publisher/sources.md#s688)、[完成状态计算](../../.workspace/jsherp-full-parallel-20260913/stores/runs/analysis-run--c589a5e62f1327d1991899949a5678b267f3da04ec0eea821bb466c37c6ac035/steps/07-repository-knowledge/modules/01-business-process-publisher/sources.md#s689)。这些链接指向本地保留产物，其他机器需要对应归档。

因此，不能简单把长链未识别归因于“代码没有线索”，也不能声称必须重做全部 JDT 或 Activity 才能继续。

## 3. 已核实：当前归并不是新的业务串联

当前过程重建读取候选组内的完整 Activity，并可请求该候选允许范围内的已保存源码。最初未进入候选的其他 Activity，不能在当前重建请求中直接补进来。

最后的仓库归并主要执行 KEEP、MERGE_INTO、REJECT 以及相关、父子、替代关系。`requireLosslessMerge` 要求归一化后的完整阶段序列和过程身份一致，才允许去重合并；它不编写新的跨过程阶段。

这能避免机械拼接出虚假的顺序，但也意味着：如果前面把请购、订单、入库分别组织成局部过程，最后的去重归并不会自动补出贯穿它们的生命周期。

核查位置：[DefaultBusinessProcessDiscovery.java](../../src/main/java/org/sourceanalysis/app/analysis/knowledge/DefaultBusinessProcessDiscovery.java) 中的 `processInput`、`reconstruct`、`applyConsolidation`、`requireLosslessMerge`，以及[当前归并提示词](../../src/main/resources/org/sourceanalysis/app/analysis/knowledge/business-process-consolidation-draft-v2.txt)。现有提示词已经提到生命周期，所以再加一句抽象的“请识别完整流程”并不足以保证改善。

## 4. 已核实：发现过 Mapper XML，但 SQL 正文没有进入这轮模型材料

核查对象是固定来源运行 `analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b` 的保存产物。

| 层次 | 实际结果 |
| --- | --- |
| 冻结源码盘点 | 65 个 XML 文件，其中 mapper_xml 下有 61 个 Mapper XML |
| Step02 Mapper catalog | 61 个目录项、573 个 XML statement candidates；bindingState 均为 CANDIDATE_NOT_YET_BOUND |
| JDT 导航索引 | 326 个入口上下文；supportingSources 非空的数量为 0 |
| M10 模型材料 | 326 个材料及 326 条覆盖记录；sourceRefs 跨材料累计 19,992 项，其中 XML 引用为 0；模型允许片段中没有附加 Mapper XML 正文 |

上述 19,992 是跨包累计次数，不是唯一来源数量。此结论针对本次保存的 XML 材料接线，不等于 Java 中完全没有数据库相关信息，也不证明其他历史路径从未处理过 SQL。

当前接线：

- [MapperCapabilityCataloger](../../src/main/java/org/sourceanalysis/app/analysis/discovery/MapperCapabilityCataloger.java) 发现接口、XML namespace、statement ID 和类型候选；目录发现不等于业务模型读过 SQL。
- [EntryCodeCollector](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/EntryCodeCollector.java) 构造上下文时，supportingSources 当前为空列表。
- [BusinessMaterialBuilder](../../src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java) 从方法源码和 supportingSources 组织来源，因此本轮输入有 Java 方法及 Mapper 调用，但没有对应 XML SQL 正文。

## 5. 留待讨论的最小改进方向

以下是建议，不是已实现能力或新实施计划。

### 5.1 优先让模型识别对象交接，并取齐相关活动

在现有目录发现和过程重建职责内，研究是否可以明确要求模型回答：

> 哪个活动产生对象？哪些活动通过业务编号、关联字段或状态引用它？后续动作怎样改变原对象的数量、金额或进度？还缺哪个活动，才能判断这段业务如何继续？

模型选择业务关系和需要一起阅读的 Activity，程序从现有 326 条已审结果中读取完整内容和保存源码。候选之外的相关 Activity 不应仅因最初分组而永远不可访问；具体如何提供有界补充阅读，尚未决定。

优先考虑复用现有过程重建能力，生成引用局部过程的上层业务链，保留局部详细规则。不要仅取消归并限制，直接拼接不同阶段数组。无需先增加新公共接口、第二套运行框架或多层汇总系统。

### 5.2 按实际疑问补相关 SQL，而不是先建设 SQL 分析工程

SQL 对以下问题可能有直接帮助：关联字段选中哪些单据、数量如何汇总、哪些状态被排除、删除或退货怎样参与计算、更新具体落到哪些原单。

可讨论利用已有 Mapper/XML 目录，按接口和 statement 对应关系读取相关 SQL 正文及必要引用片段，再交给模型解释。Java 负责查找和取材，不执行 SQL，不手写业务推断或完整 SQL 编译器；无法匹配则保留具体未知项。

现有 Step07 只读取 Activity/M10 已有来源。让模型读取尚未进入该来源集合的冻结 XML，需要后续明确一个小范围的取材接线变更，不能声称现在改提示词就能访问。原则上保留现有 Activity/JDT 成果，不默认重跑 326 条；受影响的过程任务需要使用新的实际输入，不能把旧结果冒充新结果。

### 5.3 用户问题是可选的关注方向，不是业务事实

例如用户可以问：“一个申请如何变成订单？能否分批办理？后续办理怎样改变原单进度？”

问题帮助模型查找相关材料，但不能证明该流程确实存在，也不能把问句隐含的顺序强加到代码上。没有用户示例问题时，框架仍应能自动寻找对象交接。行业名称来自仓库材料或用户输入，不写死在通用 Java 或提示词中。

## 6. 后续如何检验这个方向是否有价值

若以后批准验证，优先用已保存材料检查一条跨对象链，而不是立刻重跑整仓：

1. 模型能否把上面已存在的对象转换、关联字段和进度回写联系起来？
2. 是否真的将相关完整 Activity 和必要原文放在同一个业务上下文，而不只是增加提示词口号？
3. 输出能否说明“关联时怎样处理、不关联时是否仍允许、分批办理怎样影响原单”，并区分必经、可选与回退？
4. 如果某个准确条件仍缺失，相关 SQL 能否针对性解决，而非扩大所有上游工作？
5. 同样的方法换一个业务领域是否仍有效，且没有增加行业规则？

可支持的讨论表述是：“采购订单可以关联请购单，关联后回写请购进度；采购入库可以关联订单并影响其完成情况。”这不是已经由新版模型生成的最终流程，也不等于“所有采购必须从请购开始，并自动生成入库单”。

下一次仍需共同决定：如何在现有模块内安排跨候选阅读及上层串联；是否需要 SQL；先验证哪条链。**本次仅保存发现，不执行这些建议。**
