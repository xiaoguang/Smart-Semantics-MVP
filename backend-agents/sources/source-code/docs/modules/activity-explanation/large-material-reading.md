# ActivityReadingCoordinator：大材料怎样有效读完

状态：DIRECT/SELECTED/SLICED、分页阅读、逐slice解释、任务池与CLI已实现，325包真实保存418条Activity。Task 1可靠性修复通过12类79项定向测试；Task 2最终/替换scope、计划重开和配置传递通过8类80项定向测试、Spotless及独立复查，均使用隔离Maven输出。这不是完整CI，没有真实模型/JDT/客户构建；Task 3的coverage-v4/reuse-only开始实施。历史收口见[记录](post-review-handoff-20260923.md)，当前进度见[实施计划](../../plans/end-to-end-business-delivery-implementation-plan.md)。每次请求仍自包含，不静默截断，不建立检索服务、向量库或摘要运行框架。

## 1. 三条可结束的路径

| 路径 | 条件 | 请求序列 |
| --- | --- | --- |
| DIRECT | 去重后完整原包，加上配置允许的最大DRAFT输出与REVIEW包装余量，仍可放入REVIEW输入 | DRAFT → 完整REVIEW |
| SELECTED | 原包过大，但经导航与实际补读可取得一个完整业务范围 | READING_PLAN若干受限轮 → 完整阅读包 → DRAFT → 完整REVIEW |
| SLICED | 多个可独立解释的业务范围，每个范围完整闭包可容纳 | READING_PLAN若干受限轮 → 多slice各自DRAFT → 各自完整REVIEW → 程序聚合 |

SLICED直接输出多个ReviewedActivity，不再添加“把所有局部稿塞回同一模型”的合并步骤。多个Activity可以引用同一entry；程序只分配稳定ID与汇总，不按名字/共享表合并，更不将若干不相关的调用片段硬编成顺序。

## 2. 首次看什么：主干与导航

程序从每个entry完整方法起，附入口直接仓库调用的完整声明/方法，按已有调用顺序加入可容纳的主干。尚未进入正文的所有方法、调用候选、Mapper statement、未选/导航限制都进入导航分母。若最小入口完整单元本身也无法容纳，直接报告INPUT_CAPACITY_EXCEEDED，不截入口。

导航采用紧凑短键，当前包含声明展示、完整单元大小、从已保存调用索引提取的`calledFrom/callsTo`短键，以及Mapper语句的`boundByMethods`短键；完整方法和statement中保留候选/延迟/未展开状态、statement变体与依赖原文。它不是全量技术JSON、第二份长调用树，也不是模型生成摘要。模型选中下游方法后，程序沿已保存调用关系把通向入口的完整调用方方法一起放入该slice阅读包；容量不够则留下缺口，不截断调用方正文。

选中slice的模型阅读投影保留每条调用、调用表达式、实参与形参对应、候选短键、展开状态及完整方法正文；JDT临时工作目录路径、重复的导航位置和角色标签不进入该模型投影，形参对应使用紧凑表示。原始导航索引和已存Step05材料不改动。这样减少同一大方法几十至数百条调用元数据的重复字节，但不删调用或截源码；最终仍对实际投影执行容量检查。

当常规完整投影仍超出DRAFT及REVIEW共同上限时，才将**目标正文未选入本slice**的调用移到自描述的`boundaryCallFields`/`boundaryCalls`行目录；已选目标的调用仍保留完整参数绑定。每条边界行保留调用ref、入口、调用方、调用类型、原始表达式、解析/延迟状态、全部目标ref及未展开原因。目录只压缩重复字段名，不删掉调用发生事实或完整方法正文；仍超限则该slice未完成。对于旧批次已验证但因旧投影超限的阅读决定，新批次可以从原冻结材料和原决策**零模型调用地重新计算容量与可执行slice**，另存新决定；旧决定、旧已审slice及模型记录均不可覆盖。重新计算后仍超限的范围继续标记缺口，不能把曾经成功的较窄scope冒充最新完整scope。

最终选材是新的独立模型请求，不能假设它记得前面各页。该请求在容量允许时附上所有已展示页的紧凑`availableUnits`目录（短键、名称、归属和大小），超限时先降到短键与名称，仍超限则明确标记`catalogStatus=CAPACITY_EXCEEDED`。目录只供选取完整单元，不能冒充已读源码；已保存的完整方法仍在`completeUnits`。仅有阅读计划、没有形成Activity的`NOT_ANALYZED`包不作为可复用的完整结果；保存计划的packet ID、导航、已选/未读单元及scope引用先与当前材料严格核对，合法空计划之后显式新批次才可以重新选材。

候选slice在进入DRAFT之前按完整实际阅读包计量，而不是把方法正文长度之和当成最终请求大小。若模型选出的slice超限，程序记录实测字节数和本次上限；在尚有配置的阅读轮次时，将该反馈交给下一次阅读决策，允许模型缩小同一业务范围或拆成多个可独立解释的范围。程序不会自行删掉某个条件或调用来凑容量，也不会把超限slice当成已经审阅；读完仍超限则保留明确缺口。`sliceCapacity.maxPacketBytes`作为阅读请求的一部分说明单slice上限，实际完整包仍由程序验证。

阅读计划不能把“有一处下游实现尚不可读”误判为“入口中没有任何可解释活动”：已读完整源码能独立说明的局部行为仍应作为slice进入DRAFT，缺失实现单独列为unknown；审阅结果不得把该局部范围扩大成完整入口。这个判断交给模型，程序仍只验证所选完整单元、来源及范围，不从同名方法猜测重载目标。

导航自身超限时按稳定键顺序分页：

1. 初包含实际入口正文、导航总项/总页数、当前页和未展示页范围。分页只按key/字节计量，不按行业/文件名过滤。
2. 每个READING_PLAN请求携带当前导航页、截至本轮已选单元的全部完整正文和当前选择表。模型请求之间不共享记忆，不能只给短键或上轮增量。请求超容量时报告具体容量问题；导航页过大时按稳定键分页，不复制完整页表。
3. 默认顺序展示所有页；模型可指定关注的页/单元，但未展示页仍待处理，不能被程序判成不重要。输入的 `navigation.currentPage` 是从 1 开始的数字；`requestedNavigationPages` 接受对应的数字字符串（如 `"2"`）或 `page-2`。越界页拒绝；重复请求已展示页只作为冗余提示忽略，不重读、不重置轮数或阻断其他有效选择。
4. 模型每轮返回具体读取问题、完整unitKeys/所需调用上下文、可空slice增量及unknowns；已存在slice可用同一个本包sliceKey提交收窄或补充后的定义，程序按键替换，若修订版超容量则保留前一个可用版本并记录缺口。同一次响应内重复sliceKey仍拒绝。完整业务规则此时尚不生成。
   `slice.entryKeys`只引用本次输入顶层的入口短键（E类）；方法和SQL单元短键（M/X类）只进入required/sharedUnitKeys。每次独立请求重新核对当前输入；程序继续拒绝未知入口键，不把方法键猜作入口。
5. 程序只执行合法的保存单元读取并扩大已展示/已读集合；看到方法名不等于读过其正文。重复请求去重，不能使轮数不前进或循环不停。

每个决策记录实际展示页、正文allowlist、请求及已取得/缺失单元。导航逐页见过可证明候选范围展示覆盖，不能证明业务含义已理解。

## 3. 补读与范围决策

READING_PLAN只能选择本Packet已有单元及其已保存依赖，不能搜索当前仓库或调用JDT。导航显示Step05未选目标时可报告缺口，但不隐式扩大原技术材料。模型还可请求同单元已保存SQL AST以辅助理解，程序不重新解析SQL。

取得完整实现后再次计量；尚有明确疑问可进入下一轮，读轮上限是可配置的安全终止，不是固定“一次选完”承诺。补读达到上限时，将仍需unit、未展示页与疑问列入未处理；不能把已读摘要作为完整源码替代品。

例如新增单据包正文引用BusinessConstants.BILLS_STATUS_UN_AUDIT，但未包含常量定义；Step06可描述设置该具名常量，值与命名业务释义标为待确认，不能只凭旧Activity写成数字0。Step07可用现有冻结文本reader补读同源常量后确认0/1含义，保留Step06原记录；这不授权Step06新增常量解析器或扩大Packet读取范围，也不阻止其余已读行为的解释。

profile在同一YAML的`sourceAnalysis.activityReading`中定义：

| 字段 | 默认 | 含义 |
| --- | --- | --- |
| maxNavigationPages | 128 | 每技术包最多展示的不同导航页；正整数，不按付费额度推算 |
| maxReadingRounds | 4 | 全导航展示之后最多进行的正文补读/范围修订轮；每轮必须增加正文或明确终止 |
| maxSlicesPerPacket | 32 | 一个包当前有效局部业务范围的最大数量；已明确替代的历史定义不占当前名额，原始响应仍保留 |
| request/input/output容量 | 必需配置缺失则拒绝 | 现有字节限制精确检查；Provider声明的context上限与保守预留采用离线计数/字节估算，不要求隐藏tokenizer证明；详见[容量预检合同](integration-contracts.md#8-容量失败与验收) |

配置v3的加载、默认值、自定义值及向ActivityExplainer传递已接线，首条配置定向测试通过；实际执行上限与完整回归仍待验证。省略整个activityReading使用上表默认值；显式提供时三个字段均须为正整数，不接受未知字段。没有Activity执行配置的材料规划、检查或导出不因此要求新增模型配置。真实大包的历史运行不能证明本次改动后的范围无缺口。

导航展示决策不消耗maxReadingRounds，仍计实际请求与attempt；达到页限就停止新增阅读请求并列出未展示范围。所有READING_PLAN请求总数上界为实际页展示次数 + maxReadingRounds；同页或已取单元的重复请求不能重置上界。零进展响应结束为READING_PLAN_NO_PROGRESS，而不是再开隐含修复轮。

## 4. 如何形成多个完整slice

slice是模型提出的局部业务阅读范围，不是固定行数块：

- 每slice声明entryKeys、具体触发/参数/variant范围、核心完整unitKeys、共享上下文unitKeys、预计结果及未解决连接。
- 程序取所有完整方法/statement及依赖，保留到所选调用发生点的完整调用方方法链、参数和返回/异常使用。共享方法可在多个模型包中完整重复，canonical原件仍一份。阅读包内的已选目标保留完整实参与形参绑定；未选目标只保留调用表达式、目标候选和解析状态作为可见边界，不在每个局部包重复其未读正文的绑定细节。原始导航索引与Step05材料不变。
- 程序验证单元存在、call连接和来源一致、所有声明必需单元已提供、容量与分母；不证明Chinese rule适用性。
- 阅读模型应在已读到独立业务动作时划出有界局部范围；身份获取、审计或响应封装等未读辅助调用记为该范围的未知，不能自动抹掉已读业务动作。确实连局部动作也无法解释时，保留具体原因而不制造Activity。
- 模型在完整REVIEW中核对本slice的条件/公式/规则适用范围，看到共同方法里的其他variant时保留区别，禁止把所有分支都写成本slice必然发生。
- slice的稳定键和业务范围说明与完整源码一起进入DRAFT/REVIEW输入及任务指纹。两个slice即使选中完全相同的方法正文，也仍是不同的解释任务，不能共用一个私有结果地址；源码相同不等于业务用法相同。
- 调用环或多个强相关方法只有一起可解释时作为一个闭包；不能仅为容量把相互依赖条件分开。完整闭包超限时该scope未处理。
- 未建立跨slice业务联系时保留各自独立Activity与scopeLimitations，不能在聚合时制造前后顺序。

每slice的DRAFT/REVIEW均收到自身完整实际阅读包；REVIEW另得完整实际DRAFT与程序计算的missingEntryKeys/未解决范围。局部只看索引或任意源码摘要的输出不满足此门。

## 5. 覆盖不以一个成功Activity掩盖剩余范围

当前coverage-v3只有入口处置，不能冒称已保存下述完整范围。本次目标分工是：私有plan-v2保存实际阅读细节，公共coverage-v4只增加packetCompletion摘要，见[集成合同](integration-contracts.md#4-本次目标最小packetcompletion)。私有阅读信息包括：

- 复用现有页游标、已供给完整单元、slice定义及实际缺失信息，保存最终范围；不要求为每个未读单元建立业务处置记录。
- 实际阅读结果区分已供给正文、仅有导航、上游不可取得；可由现有请求/返回和导航计算的，不再复制一份独立台账。仅有导航不能声称源码已读。
- 每slice记录scope、必需unitKeys、REVIEWED、MODEL_NOT_EXPLAINED、READING_INCOMPLETE、FAILED/容量原因；重试耗尽还含失败stage与attempt引用。
- 每entry汇集它所有slice的处置及已审Activity。某slice成功仅贡献该scope；仍有必需未读、未完成slice或未展示导航时，entry保持带原因的未完全分析。
- 模型在范围级说明未选和待确认；不必逐方法证明“无需分析”。程序只跟踪已声明必需范围是否供给并完成，不推论未读代码不存在业务规则。

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
| 单slice实际DRAFT使REVIEW超限 | 先以配置的最大DRAFT输出和包装余量作保守预检；若Provider实际输出仍超出，则记录REVIEW_INPUT_CAPACITY_EXCEEDED并保存DRAFT，不截稿、不改输入后假装同stage |
| Provider实际拒绝输入/context容量 | PROVIDER_INPUT_CAPACITY_EXCEEDED，保留attempt与已成功稿件；不对相同输入自动retry，不影响其他独立包 |
| 某stage瞬时失败 | 按[执行retry](../model-job-execution.md)在原阶段有界重试；穷尽只影响该包/阶段 |

所有slice按稳定计划顺序聚合。一个slice的程序容量预检或packet-local Provider/schema失败不终止后续独立slice；结束时以失败终态携带此前及后续成功，公开coverage保持缺口，私有成功stage不回滚。认证/配置失败停止该binding但仍携带已验证成功，来源/存储损坏保持硬错误且不安装不可信聚合。任何必需scope未完成，批次保存成功结果和失败清单后非零终态，不自动启动Step07；用户可显式新batch定向retry。没有自动修改profile、重新分包、Provider切换、同run崩溃接管或无限提示词修复。

## 7. 直接验证与真实接受

直接测试覆盖：小包只两阶段；443方法/1468calls形状的中性fixture能分页且不丢导航分母；正文补读重复去重；跨页候选可被选择；多slice同E1一成功一失败不误全覆盖；共同方法多variant不由程序复制业务规则；SQL列值/依赖完整；不可拆闭包超限零对应DRAFT；局部稿总和过大仍程序保存完整多Activity；阶段retry不增加阅读轮；页/轮限制不循环；旧M10无新阅读任务。

既有小包/大包与325包运行已保存，实际调用/耗时见[实测](full-step05-acceptance-20260923.md)；本次不重复执行。新的验收先离线核对旧plan及阶段，再测试以下v2合同，不因文档设计增加产品调用。

## 8. 本次v2修正：最终范围必须明确，历史告警不反复驱动

销售统计真实包产生18条近重复，旧流程只按相同sliceKey修订、不同key累计，并用历史容量错误推动后续决策。目标在既有READING_PLAN响应增加以下字段，不增加一个独立“最终选材”模型阶段：

~~~text
finalSliceKeys[]                  本轮认可的有效scope集合
supersededSlices[]                {sliceKey,replacementSliceKeys[],reason}
finishReading                    是否请求结束阅读并冻结范围
~~~

finalSliceKeys包含本轮及此前已定义的实际scope。移出旧key必须在supersededSlices说明是由哪些key替代，或无替代且保留具体未解释范围；程序不能从相近名称推断替代。相同key改定义仍允许，同轮重复定义仍拒绝；全部引用先在临时状态验证。声明结束不代表源码已足够，实际缺必需unit/容量/未展示范围仍登记不足。

1. 每轮保存原始响应和历史诊断；从本轮有效定义计算currentOpenScopeIssues，不扫描累积unknown字符串决定是否继续。下一次私有阅读请求使用`historicalDiagnostics`和`currentOpenScopeIssues`两个数组，明确区分历史诊断与当前问题：旧范围已被有效替代时，其历史超限记录仍保存，但不能继续作为当前必需缺口交给模型。原始决策和保存记录的unknowns保留原字段及内容，不改写历史。
2. 一个旧超限scope被明确替换后只留历史记录；替代scope仍缺材料或超容量才继续有界读取。不能靠新key增加就自动解除旧义务。
3. 结束前核验finalSliceKeys、完整unit/依赖和当前不足，保存不可变plan-v2。没有新进展或轮数结束则保存有限失败，不请求额外修复轮。
4. 每scope必须说明独立的业务问题/触发/结果。相同查询的过滤、分页、汇总可能是一个动作的组成部分；模型应优先修订既有scope，不因轮次不同重复提议。程序不审判这种语义，只展示旧定义和要求明确替换。
5. 开始任一DRAFT后冻结该计划。显式重试先重开原plan和完整slice包，验证Prompt/Schema/profile/binding及source mapping；不重新调用阅读模型来“恢复”同一计划。损坏不能自动replan；必须改变范围时先说明失效稿件和调用范围。
6. 一个slice失败仍保存之前的成功，继续没有依赖该失败的其余slice；容量预检失败也进入同一包结果。最后返回成功集合与全部不足，不在第一次slice异常处丢掉之前或跳过之后的独立结果。

plan-v2私有记录增加最终有效范围、明确替换/撤回关系及当前未完成原因，保持历史decisions和完整readingPacket。SOURCE未选仅是本范围没有读，不是无业务证明。旧v1可以严格读取，但若义务无法还原则UNDETERMINED，不能自动推成成功/失败。

直接回归：历史超限随后明确替换→不多一轮、不误标不完整；不同key未声明替换→不静默删除旧范围；5次阅读产生18相近提议的录制响应不被程序变成18个必经步骤；S1成功/S2失败/S3独立成功→公开结果含S1/S3；同scope审阅重试零阅读调用；坏引用/重复key的失败attempt不能改变选择状态或留下SUCCESS。
