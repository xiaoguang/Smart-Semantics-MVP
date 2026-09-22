# ActivityReadingCoordinator：大材料怎样有效读完

状态：目标设计，尚未实现。它复用现有Activity job内顺序调度；最小目标是让模型读完整相关实现，并能诚实报告没有读完的范围，不建立检索服务、向量库或摘要运行框架。

## 1. 三条可结束的路径

| 路径 | 条件 | 请求序列 |
| --- | --- | --- |
| DIRECT | 去重后完整原包及DRAFT/REVIEW预算都可容纳 | DRAFT → 完整REVIEW |
| SELECTED | 原包过大，但经导航与实际补读可取得一个完整业务范围 | READING_PLAN若干受限轮 → 完整阅读包 → DRAFT → 完整REVIEW |
| SLICED | 多个可独立解释的业务范围，每个范围完整闭包可容纳 | READING_PLAN若干受限轮 → 多slice各自DRAFT → 各自完整REVIEW → 程序聚合 |

SLICED直接输出多个ReviewedActivity，不再添加“把所有局部稿塞回同一模型”的合并步骤。多个Activity可以引用同一entry；程序只分配稳定ID与汇总，不按名字/共享表合并，更不将若干不相关的调用片段硬编成顺序。

## 2. 首次看什么：主干与导航

程序从每个entry完整方法起，附入口直接仓库调用的完整声明/方法，按已有调用顺序加入可容纳的主干。尚未进入正文的所有方法、调用候选、Mapper statement、未选/导航限制都进入导航分母。若最小入口完整单元本身也无法容纳，直接报告INPUT_CAPACITY_EXCEEDED，不截入口。

导航采用紧凑短键，包含声明展示、完整单元大小、caller/target短键、候选/延迟/未展开状态、statement变体与可用依赖提示。它不是全量技术JSON、第二份长调用树，也不是模型生成摘要。

导航自身超限时按稳定键顺序分页：

1. 初包含实际入口正文、导航总项/总页数、当前页和未展示页范围。分页只按key/字节计量，不按行业/文件名过滤。
2. 每个READING_PLAN请求可携带一页或多页真实可容纳导航、前次已取得的完整必要正文和紧凑当前选择表。全页目录也过大时用可递进cursor与总页数，不复制完整页表。
3. 默认顺序展示所有页；模型可以先指定需要的页/单元，但未展示页仍待处理，不能被程序判成不重要。
4. 模型每轮返回具体读取问题、完整unitKeys/所需调用上下文、可空slice增量及unknowns；已存在slice只用稳定本包sliceKey引用，完整业务规则此时尚不生成。
5. 程序只执行合法的保存单元读取并扩大已展示/已读集合；看到方法名不等于读过其正文。重复请求去重，不能使轮数不前进或循环不停。

每个决策记录实际展示页、正文allowlist、请求及已取得/缺失单元。导航逐页见过可证明候选范围展示覆盖，不能证明业务含义已理解。

## 3. 补读与范围决策

READING_PLAN只能选择本Packet已有单元及其已保存依赖，不能搜索当前仓库或调用JDT。导航显示Step05未选目标时可报告缺口，但不隐式扩大原技术材料。模型还可请求同单元已保存SQL AST以辅助理解，程序不重新解析SQL。

取得完整实现后再次计量；尚有明确疑问可进入下一轮，读轮上限是可配置的安全终止，不是固定“一次选完”承诺。补读达到上限时，将仍需unit、未展示页与疑问列入未处理；不能把已读摘要作为完整源码替代品。

例如新增单据包正文引用BusinessConstants.BILLS_STATUS_UN_AUDIT，但未包含常量定义；Step06可描述设置该具名常量，值与命名业务释义标为待确认，不能只凭旧Activity写成数字0。Step07可用现有冻结文本reader补读同源常量后确认0/1含义，保留Step06原记录；这不授权Step06新增常量解析器或扩大Packet读取范围，也不阻止其余已读行为的解释。

目标profile在同一YAML中定义：

| 字段 | 默认 | 含义 |
| --- | --- | --- |
| maxNavigationPages | 128 | 每技术包最多展示的不同导航页；正整数，不按付费额度推算 |
| maxReadingRounds | 4 | 全导航展示之后最多进行的正文补读/范围修订轮；每轮必须增加正文或明确终止 |
| maxSlicesPerPacket | 32 | 一个包最大局部业务范围数；限制失控分解，不限定业务本身有几类 |
| request/input/output容量 | 必需配置缺失则拒绝 | 现有字节限制精确检查；Provider声明的context上限与保守预留采用离线计数/字节估算，不要求隐藏tokenizer证明；详见[容量预检合同](integration-contracts.md#8-直接实施检查) |

默认值提供有界运行，真实样例可在用户批准的后续batch显式调整；本轮未实测它们足够解释2MB样例。导航展示决策不消耗maxReadingRounds，仍计实际请求与attempt；达到页限就停止新增阅读请求并列出未展示范围。所有READING_PLAN请求总数上界为实际页展示次数 + maxReadingRounds；同页或已取单元的重复请求不能重置上界。零进展响应结束为READING_PLAN_NO_PROGRESS，而不是再开隐含修复轮。

## 4. 如何形成多个完整slice

slice是模型提出的局部业务阅读范围，不是固定行数块：

- 每slice声明entryKeys、具体触发/参数/variant范围、核心完整unitKeys、共享上下文unitKeys、预计结果及未解决连接。
- 程序取所有完整方法/statement及依赖，保留到所选调用发生点的完整调用方方法链、参数和返回/异常使用。共享方法可在多个模型包中完整重复，canonical原件仍一份。
- 程序验证单元存在、call连接和来源一致、所有声明必需单元已提供、容量与分母；不证明Chinese rule适用性。
- 模型在完整REVIEW中核对本slice的条件/公式/规则适用范围，看到共同方法里的其他variant时保留区别，禁止把所有分支都写成本slice必然发生。
- 调用环或多个强相关方法只有一起可解释时作为一个闭包；不能仅为容量把相互依赖条件分开。完整闭包超限时该scope未处理。
- 未建立跨slice业务联系时保留各自独立Activity与scopeLimitations，不能在聚合时制造前后顺序。

每slice的DRAFT/REVIEW均收到自身完整实际阅读包；REVIEW另得完整实际DRAFT与程序计算的missingEntryKeys/未解决范围。局部只看索引或任意源码摘要的输出不满足此门。

## 5. 覆盖不以一个成功Activity掩盖剩余范围

程序沿用Activity coverage，在v3中增加包/slice范围，而非另建业务证据账本：

- 每包记录navigationPagesShown/remaining、unit dispositions、sliceKeys、未选/必需未读范围及原技术限制。
- 每单元明确FULL_TEXT_PROVIDED、NAVIGATION_ONLY、UPSTREAM_UNAVAILABLE，及其所属slice/读取结果；NAVIGATION_ONLY不能被称为源码已读或无需分析。
- 每slice记录scope、必需unitKeys、REVIEWED、MODEL_NOT_EXPLAINED、READING_INCOMPLETE、FAILED/容量原因；重试耗尽还含失败stage与attempt引用。
- 每entry汇集它所有slice的处置及已审Activity。某slice成功仅贡献该scope；仍有必需未读、未完成slice或未展示导航时，entry保持带原因的未完全分析。
- 不需要的导航正文只能由模型明确记录“本次解释未选”的范围，保留为未读；不能因此得出这些代码不存在业务规则。完整业务验收须核对当前声明范围覆盖与剩余未读是否影响结论。

slice内部继续 `activity entryKeys ∪ unexplainedEntries = slice entryKeys` 且不相交。跨slice可重复entry，不同包的局部key不能直接合并。包级闭合还需全部必需slice有终态，不能只比较entry ID集合。

对于未读但不影响已解释范围的内容，允许保存带scopeLimitations的Activity；其存在不证明整个入口或仓库完成。失败/未读清单和成功Activity分别保存，不将失败scope伪造为空活动。

## 6. 容量终止与失败隔离

任何下列情形都有限终止：

| 原因 | 输出/后续 |
| --- | --- |
| 入口/单方法/statement依赖/共同条件闭包过大 | INPUT_CAPACITY_EXCEEDED，受影响entry/slice/unit与实际计量 |
| 导航页或补读轮耗尽 | READING_INCOMPLETE，未展示页、未读必需单元和疑问 |
| 需要范围外源码/未保存实现 | UPSTREAM_MATERIAL_UNAVAILABLE，保留具体目标；不重扫 |
| 所有slice局部稿合起来很大 | 直接程序保存多个完整Activity；无全包模型合并容量问题 |
| 单slice实际DRAFT使REVIEW超限 | REVIEW_INPUT_CAPACITY_EXCEEDED，保存DRAFT；不截稿、不改输入后假装同stage |
| Provider实际拒绝输入/context容量 | PROVIDER_INPUT_CAPACITY_EXCEEDED，保留attempt与已成功稿件；不对相同输入自动retry，不影响其他独立包 |
| 某stage瞬时失败 | 按[执行retry](../model-job-execution.md)在原阶段有界重试；穷尽只影响该包/阶段 |

所有slice按稳定计划顺序聚合。任何必需scope未完成，批次保存成功结果和失败清单后非零终态，不自动启动Step07；用户可显式新batch定向retry。没有自动修改profile、重新分包、Provider切换、同run崩溃接管或无限提示词修复。

## 7. 直接验证与真实接受

直接测试覆盖：小包只两阶段；443方法/1468calls形状的中性fixture能分页且不丢导航分母；正文补读重复去重；跨页候选可被选择；多slice同E1一成功一失败不误全覆盖；共同方法多variant不由程序复制业务规则；SQL列值/依赖完整；不可拆闭包超限零对应DRAFT；局部稿总和过大仍程序保存完整多Activity；阶段retry不增加阅读轮；页/轮限制不循环；旧M10无新阅读任务。

真实验收须从新Step05检查点独立开始，先一个小包、再新增单据大包，展示模型实际看到哪些完整实现/SQL条件以及遗漏范围，再讨论扩大规模。所有模型请求需要明确授权。当前仅设计推演，没有新的Activity或实测时间/压缩率。
