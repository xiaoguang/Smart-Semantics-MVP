# 从已保存 Activity 到可读业务过程：端到端收尾详细设计

状态：**2026-09-23 设计评审稿，未授权实施或新业务模型调用。** 本文依据当前代码、主设计、已经整合的补充设计及实际运行记录制定。已有能力、未验证修复和本次提议分开；本文不是新的执行完成记录。

## 1. 要交付什么，不重新做什么

读者最终能回答：系统提供哪些业务；每种业务从哪里开始、经过哪些动作、允许哪些分支和回退、在什么条件下拒绝、怎样关联后续对象、怎样结束，以及哪里仍不确定。

正式出口仍为五文件：`business-processes.md`、`repository-business-process-catalog.json`、`process-coverage.json`、`source-refs.jsonl`、`sources.md`。不恢复九章生成，不增加第九个分析步骤或另一套 Agent。

复用冻结 Java/XML/Vue、JDT/持久化/Step05、已审 Activity、现有目录发现、聚焦阅读、DRAFT→WRITE→RULE_REVIEW、并发与保存、归并及发布。**不默认重新运行 JDT、Step01–05、Builder、325包 Activity。** 业务缺口先使用已有原文；只有明确证明某包必须重读/重解释时，列出最小范围与替代方案，再取得执行同意。

本文只授权文档工作。后续代码、真实样例和全仓执行分别有退出条件，设计写完不代表任何调用已获新授权。保留全部旧模型记录、旧326条 Activity、418条新结果、失败批次和 `more-findings.md`。

## 2. 实际基线：哪些已经有，哪些不能算完成

| 范围 | 核对到的实际状态 | 本设计处理 |
| --- | --- | --- |
| Step01–05 | 已保存325包、326入口处置；1入口 `JDT_QUERY_FAILED` 无包 | 固定复用；该1项不混作模型失败 |
| 新 Activity | `51b6625` 已有直接Step05、小/大包、阶段保存/重试、M11与CLI；真实得到418条 | 不再列为从零开发 |
| 当前工作树 | 13个生产/测试文件中有审查后修复草稿，未完成定向/质量验证 | 必须先检验，不能因代码已写入就标完成 |
| 418条的范围 | 300包各1条；25包多条；销售统计一包18条，调拨详情查询一包16条 | 数量不是独立业务数；确认范围并改善下游阅读组织 |
| 新来源 Step07 | `executeStep05BusinessProcesses → assembleStep05ProcessDiscoveryRequest → PersistedBusinessProcessRunExecutor → Discovery/Publisher` 已接通 | 补正式入口的完整离线验收，不重造接线 |
| 历史三例 | 用户认可可读性/业务联系；采购未完成最终核对，销售/调拨有具体准确性问题 | 保留进展，也保留未关闭问题；不能算全仓质量通过 |
| 新418条的过程结果 | 尚未通过真实新来源全仓过程生成与验收 | 本设计定义如何到达该终点 |
| Git交付 | 实现提交已在专用分支；审查修复和本轮实现PR尚未完成 | 后续通过验证再交付，不在本轮文档操作中提交 |

固定新 Activity 批次：`analysis-run:e00cf448733fc078fe460e84b2ea41f51aec85347a4240a49154087748dfa2f9`；M11 receipt：`module-receipt:5360f1f47cc4c7b4f5016d85d97978f08e4e999b9b4f923c5705e4dd458a8700`。完整输入引用必须从保存记录重开，不能仅凭这两个ID构造新receipt。

该批已存 `activityBatchComplete=true`、325条 `ANALYZED_WITH_GAPS` 和1条原导航失败，顶层语义状态 `PARTIAL`。**不能说它因为PARTIAL就无法进入当前Step07，也不能凭true证明每个必需slice均已读完。** 当前旧coverage缺少足够的独立范围完成记录。

依据：[Activity实测](modules/activity-explanation/full-step05-acceptance-20260923.md)、[收口记录](modules/activity-explanation/post-review-handoff-20260923.md)、[三例实测](supplements/cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。这些历史记录不改写成新结论。

## 3. 选择的最小路线

三个可选方向：

1. 全部重做Activity：代价大，不能保证消除同样的分解和写作问题，不选。
2. Java按名称、表名或文本相似度删并Activity：会丢失不同条件/用途，也可能写死行业规则，不选。
3. **保留原Activity；修正范围完成性；给下游显式的同入口关联；缩小WRITE职责并核对最终正文。** 推荐此方案。

不同切片的业务含义仍由模型判断。Java只展示“这几条来自同一包/入口”，不宣布它们相同，不替模型发现业务顺序。旧结果不因更合理的下游解释而被覆盖。

## 4. 模块、数据流与职责

```text
Step05完整引用 + 旧/新Activity检查点 + 已保存阅读计划/阶段结果
  → 离线范围核对（无模型）
  → 核对本次全部325包：可复用Activity + 明确未完成/无法判定范围
  → 必需范围完成后进入本次全部Activity的目录（不偷偷过滤失败包）
  → 同入口关联的全仓导航卡（不删Activity）
  → 首次目录 + 系统认识/全局选材
  → 完整Activity + 同源Java/XML/Vue原文 + 一次阅读检查/补读
  → 局部短编号、自包含过程阅读包
  → 事实DRAFT → 限定职责WRITE → 对照原文的最终RULE_REVIEW
  → 完整已审过程 → 仓库关系/无损归并 → 确定性五文件
```

| Module及现有代码位置 | 拥有的工作 | 不承担的工作 | 详细owner |
| --- | --- | --- | --- |
| ActivityReadingCoordinator / ActivityReadingPlan | 当前有效阅读计划、必需scope、选材结束；分开历史告警与未解决问题 | 业务词典、按名称合并 | [大包阅读](modules/activity-explanation/large-material-reading.md) |
| ActivityExplainer / ActivityJobCoordinator | 成功阶段复用、包内部分结果、阶段错误与包/绑定失败隔离 | 重做上游、修改已结束批次 | [执行](modules/model-job-execution.md) |
| PrivateModelJobResultStore / M11 Publisher/Reader / SourceAnalysisExecution | 保存并重开范围完成性、输出归属、显式只复用与重试 | 给未知范围补一个成功标记 | [来源与版本](modules/activity-explanation/integration-contracts.md) |
| FrozenAnalysisCorpus / ActivityIndexCard | 完整Activity与来源读取、原包/入口关联导航 | 业务去重、把导航卡当正文 | [Corpus](modules/business-process-discovery/frozen-analysis-corpus.md)、[Cataloger](modules/business-process-discovery/repository-business-cataloger.md) |
| ProcessMaterialAssembler | 取完整成员/context/原文；自包含封包、局部编号映射 | 再导航、再解析SQL、摘要替代正文 | [Assembler](modules/business-process-discovery/process-material-assembler.md) |
| CandidateProcessReconstructor | 推理、限定写作、最终稿规则核对 | Java语义判断、无限修稿 | [Reconstructor](modules/business-process-discovery/candidate-process-reconstructor.md) |
| RepositoryProcessConsolidator / Publisher | 保留完整过程，裁决关系，确定性发布 | 再次写作、拼阶段制造生命周期 | [Consolidator](modules/business-process-discovery/repository-process-consolidator.md)、[Publisher](modules/business-process-discovery/business-process-publisher.md) |

以上是既有Module内部增量，不新增公共业务接口。两个Step07深Interface和唯一 `source-analysis` 启动入口保持。

## 5. 第一项：Activity可靠性与范围完成性

### 5.1 五处修复的确定合同

| 场景 | 目标行为 | 验收观测 |
| --- | --- | --- |
| 大包S1成功、S2审阅失败 | 显式新批次先重开原完整计划，复用S1及S2成功DRAFT，仅续审S2 | 零新增阅读/DRAFT；1个新REVIEW |
| 另一必需slice未能读完 | 保存成功部分，但包不完整；不能用同entry已有Activity掩盖 | CLI失败范围、M11及Step07准入一致 |
| 后一slice失败 | 前面完整已审Activity进入可查询的部分M11，私有记录也保留 | 公开结果中能看到S1；整包仍未完成 |
| 限流/认证/未知错误 | 按结构化原因区分瞬时retry与绑定停止；不能按错误句子猜 | 瞬时只重试当前阶段；认证停止对应服务新派发；未知不自动重试 |
| 阅读响应结构表面合法但有未知unit/重复slice | 在临时选择状态完整验证后才保存SUCCESS并提交选择 | 错误attempt没有成功标记，按已配置响应策略处理 |

这些是未验证草稿的收尾，不是新恢复系统。已启动但结果不确定的请求不得重叠重发。新Activity阶段重试不扩展到Step07。

### 5.2 当前义务与历史事件分开

阅读计划保存“最终需要解释哪些范围、哪些仍未取得”，历史决策保存“曾经怎样尝试”。一次超大slice已被明确替换后，其旧容量错误不再驱动补读或导致当前范围失败；不同key的新增slice本身不能隐式撤销原必需范围。

在READING_PLAN的现有增量响应中明确表示同key修订及被替换/撤回key；程序不按名称识别替换。撤回某范围必须有模型给出的用途/不足说明，不能自动丢掉原义务。解释开始前冻结计划，之后只重试阶段；若要改变范围，属于显式重新规划且使受影响稿件失效。

不增加“每个源码单元必须证明无业务”的台账。未读辅助实现、未知常量、外部行为与必需slice失败不同。模型判断读取用途，程序检查模型声明的必需范围是否确实供给、是否完成审阅。

### 5.3 最小公开完成记录

在已有 `activity-coverage.json` 新版本增加 `packetCompletion`，只汇总包及最终必需slice，不复制私有全部导航/原文/attempt：

```json
{
  "packetId": "<真实packetId>",
  "entryIds": ["<真实entryId>"],
  "completion": "INCOMPLETE",
  "requiredSliceKeys": ["range-1", "range-2"],
  "completedSliceKeys": ["range-1"],
  "incompleteScopes": [{"sliceKey": "range-2", "entryIds": ["<真实entryId>"], "reasonCode": "REVIEW_FAILED"}]
}
```

这是目标格式示意，不是实际历史JSON。`completion=COMPLETE|INCOMPLETE|UNDETERMINED`；范围原因使用实际已有失败分类，不把示意字符串直接当生产枚举。`sliceKey=null`只用于无法形成slice的包级阅读不足。

- COMPLETE：所有声明必需范围均有完整、有效REVIEW；非必需未读或解释未知仍可存在。
- INCOMPLETE：有明确失败、必需未读、未完成解释或未处置导航范围。
- UNDETERMINED：历史保存信息无法可靠还原完成性。不能当COMPLETE，也不等于全部内容无用。

`activityBatchComplete`保留现有名字/意义，由实际选择范围及packetCompletion计算；不是模型自评或语义质量评分。Step07再次检查该范围，不只看布尔值或 `ANALYZED_WITH_GAPS`。

原1个NOT_COLLECTED入口仍计入326入口分母，但没有packet，不制造虚假slice或列入模型失败重试。可在325包完成时保留它而交付明确范围的部分业务文档。

## 6. 第二项：已有418条怎样继续使用

### 6.1 先离线核对，不重新理解全部业务

利用现有M11、批次结果、阅读计划、原决策、已保存slice包和阶段记录，逐包核对：来源相同、最终有效scope、实际完整正文、成功DRAFT/REVIEW对应关系、未解决范围。只读取已有字节，不重新请求模型、重跑选材或编译源码。

程序可确认的结果必须有具体保存记录支持。旧字符串unknown无法区分历史告警和当前必需范围时，记UNDETERMINED；不通过中文关键词推断“应该没事”。终态FINISHED、成功Activity数量及无reasonCode都不能补足缺失信息。

首次结果由已有 `inspect` 的只读检查及私有批次结果呈现：完整包、不完整包、无法判定包、原因和可复用稿件。没有新公共审计模块、源码证明链或数据库。

### 6.2 不覆盖旧结果的接续

建议为既有Activity执行增加窄选项 `--reuse-only`，必须与 `--reuse-from-model-batch`同时使用且不能与主动重试组合。它创建新运行、只重开并验证已有结果、按新coverage保存；Provider初始化及请求数严格为0。无法确定/不完整的包仍有处置和成功子集，运行非成功，不隐式降级到模型生成。

业务正文、Activity ID、来源映射和原始模型身份保持；新M11只拥有新聚合/范围判断，复用来源写入已有私有记录。旧检查点不改。新覆盖引用新运行，源码仍属于原sourceRun。

遇到不完整/无法判定时，先给出包名、入口、具体缺什么、已有哪份可复用稿件。选择为：继续离线核对已有记录；明确授权后只修该包的阅读/失败阶段；或保留不足但不称作全量业务验收。**不自动让全部325包重做。** Step07不新增隐式跳过这些必需不足的参数。

### 6.3 重复内容交给下游怎样读

将同一来源publication、packet和entry集合的Activity组织成一个导航组，各Activity仍有独立ID、scope、规则和全部原文。分组是来源关系，不是业务去重。

目录卡新增局部包/入口键及原sliceKey，让模型知道18条可能是一次查询的多个说明；全局选材和CHECK继续保留这种关系。所有418条仍有最终去向。不同包仅因为名称相同不分到同组；同包不同业务变体也不强制合并。

过程模型可把多个相近Activity放在同一个阶段，或归入支撑查询；每条原Activity的引用/规则仍可取回。多个不同规则必须保留，不能用集合闭合掩盖语义遗漏。只有完整相同的正文/来源可以由程序无损去重；中文相似性不触发删除。

任意N入口、一个Activity覆盖多个入口、同Activity多个variant、候选SPLIT得到多个过程均保留。每条Activity携带完整入口键集合，例如[E1,E2]与[E2,E3]的交叠不能只用组名表达；保留原映射即可，不增加重叠分析算法。每个U键限定在其所属process中，跨过程相同U1不是同一用法。

不新增一次“Activity语义去重”模型调用、不改写418条、不把阅读切片数当业务过程数。若单个来源组卡片仍超限，稳定分页并保留同组键/页号，沿既有目录分片和merge合读，不能截取前几条。

## 7. 第三项：新版来源的正式接力

现有正式入口已经支持：

```text
source-analysis --config /absolute/path/process.yaml execute-step
  --target repository-knowledge
  --activity-model-batch <经核对的新Activity批次>
  [--reuse-from-model-batch <匹配的过程批次>]
```

新418条与旧326条的身份和内容不同，旧目录不能直接作为匹配目录。当前Step05入口拒绝非空 `catalogFromModelBatchId`，这是现状，不在本设计顺带扩建历史目录导入。首次目录使用本次所有Activity；新的相同输入过程任务可按现有指纹规则显式复用。

正式离线贯穿必须覆盖真实CLI→真实Agent→新Step05 reader/M11 reader→Discovery→canonical store→artifact查询→重开/确定性重渲染。仅fake Agent参数转发、反射签名或模拟store不够。使用scripted Provider，观察JDT/Builder/ActivityExplainer调用数为0。

材料owner、Activity owner、过程owner保持三者分离；process output仍为现有 `AnalysisRunOutput.step05Processes`。新模型配置不会让Step05失效。历史M10/旧过程/旧报告保持严格读取，不将新SQL补写旧Activity。

## 8. 第四项：让模型装得下，并写对具体条件

### 8.1 局部编号，不删正文

采购旧最终请求大量重复长Activity/statement ID；现有Schema已经用了根 `$defs/$ref`，不重复开发该能力。补缺的是贯穿输入、Schema、三稿、保存和解析的可逆局部编号。

Assembler在候选阅读包封闭后一次分配：A=实际Activity，T=原字段statement，S=实际来源；长全局ID只在程序侧映射中。稳定排序、候选内唯一，DRAFT/WRITE/RULE_REVIEW使用同一映射。F/R阅读定位及过程自有process/use局部ID保留各自作用域；阶段使用现有(processLocalId,order)标识，不新增stageLocalId，不能与A/T/S混用。

只替换结构化身份和引用字段，不替换业务正文中的数字、字段名称或自然语言。映射同步Schema allowlist与 `$defs`；模型原始响应保留，验证后在副本还原全局ID，再生成canonical过程身份与跨候选来源编号。跨包两个T1不可直接连接。

完整Activity正文、原文、事实DRAFT和实际WRITE仍进入最后核对。只减少重复元数据、不做摘要。估算使用最终请求、Prompt、Schema及输出余量，区分真实容量与费用，不臆定模型窗口。短编号仍不足时保存明确容量原因，不删稿强行通过。

### 8.2 WRITE只负责表达

当前WRITE可返回完整结构，本次继续复用此响应，不另建散文/补丁协议。只允许改写 `processes[*].name`、`processes[*].purpose`、`processes[*].stages[*].name`、`processes[*].stages[*].narrative` 四类展示字段。去掉这四类字段后，DRAFT与WRITE必须深相等：包括数组长度/顺序、process/use标识、阶段order、disposition/reason、范围、成员、条件、规则、公式、状态、结果、certainty及引用。Java不判断展示文字的中文语义。

这样不能保证正文永不写错，但能避免WRITE把结构化事实同时改掉，使最终核对有稳定事实底稿。涉及业务范围的事实变化由最后RULE_REVIEW对照原文决定，不由WRITE自行重新推理。新限制属于本次待评审改动，不能说现有代码已经如此。

### 8.3 最后核对针对实际读者文字

保持三次过程请求，不加第四次润色或独立规则模型。RULE_REVIEW仍收到同一阅读包、实际DRAFT与实际WRITE，返回完整 `processResult + corrections`。

逐个核对最终正文和相应结构字段：

- 谁/哪类对象适用；哪种业务变体不适用。
- 允许与拒绝是否写反；AND/OR、空值/缺省条件是否遗漏。
- 状态值和业务名称是否有原文对应，不凭常量名猜数值。
- 订金、抵扣订金、本次付款、欠款等用途是否被泛化成一个金额。
- 配置开启/关闭和回退路径是否在正文、规则、拒绝条件及结果中一致。
- 查询得到关联信息与实际创建/回写动作是否混淆。
- 页面“可以关联”形成可选前后关系，不误写为所有情况必经，也不误写为无法描述流程。

有明确错误则在最终结果中同步改正所有受影响字段，已有corrections记录原因；材料冲突/不足则收窄结论或列具体待确认。Java不建立自然语言事实核验器，也不把合法来源编号当作语义正确证明。

### 8.4 context不能凭空变成办理编号

CHECK确定最终成员/context；为理解联系而读的查询仍可提供statement/source依据，不强迫成为步骤。若确有要解释的办理/支撑动作，应在CHECK时成为显式成员并获得合法用法。

DRAFT在进入WRITE前只检查局部定义/引用闭合，不提前做最终CONFIRMED依据或中文事实判断。WRITE保持DRAFT结构和ID。任一局部引用错误或WRITE越界改写都保存原响应并停止当前Step07执行；不新增自动修复轮。

RULE_REVIEW不受上述字段相等约束：固定的是A/T/S实际材料映射与CHECK最终成员边界，而不是错误草稿里的全部U键。它允许在成员边界内纠正、删除、拆分和重新组织process/use局部定义，最后逐过程校验定义—引用闭合、候选覆盖及SPLIT等处置。删除阶段若使某成员不再出现，必须同时形成合法最终处置，不能仅验证剩余引用。context可以提供T/S依据，但不自动产生办理U；不清空错误列表或自动造定义让保存通过。

## 9. 第五项：样例、全仓、归并和发布

### 9.1 小样是扩大前的硬停止点

后续获准执行时，先用本次新输入生成自己的目录，并检查少量模型实际发现的候选。采购、销售、调拨是已有回归关注范围，不写进通用Prompt为必有业务，不手工注入正确ID/阶段。

2026-09-23执行选择已确认：全局选材focusQuestion=null，三类关注范围仅用于人工挑选和验收，不传入模型。小样与全仓沿用同一无提示输入，不能在扩大时改变关注问题导致复用失配。

样例preview必须来自完整合法最终RULE_REVIEW；缺审阅、非法引用或重要规则错误均不能按“正文可读”放行。正文与来源直接提供用户，记录哪些条件核对过、哪些仍未知。用户看过并同意后再全仓，不在展示样例前启动其余候选。

### 9.2 全仓处理与复用

全仓批次只复用本轮相同Activity集合、目录/阅读输入、Prompt/Schema、映射和模型身份匹配的完整任务。旧326条的目录、旧双轮或孤立DRAFT/WRITE不能冒充新结果。新版Activity修正使相关目录/候选输入变更时，按实际指纹失效，不重算技术材料。

候选并行、每候选阅读和三阶段顺序、绑定固定。Step07仍不自动retry；fatal停止新派发，已启动且自身前置有效者完成并保存，不能带坏候选进入正式归并。用户决定后续具名新批次，不隐式反复运行全仓。

全部候选有合法最终处置后沿用一次仓库归并DRAFT/REVIEW；它只决定关系和无损合并，不能改写最终正文。不同条件或阶段序列不能拼接；保留不同过程和明确关系。归并本身也计量完整业务视图；装不下时报告，不能将其悄悄替成标题列表或顺带建设分层归并系统。

### 9.3 什么算交付

1. 五文件安装、查询和重开成功；重渲染零模型、字节一致。
2. 本次全部Activity、候选、过程有最终去向，原无材料入口仍在范围说明中。
3. 读者能从正文讲出主要办理步骤、条件、可选路径和结果，不靠接口名或裸S编号拼图。
4. 样例的重要事实错误已关闭，全仓抽查有具体记录。不能声称全部自然语言结论经程序证明正确。
5. 未分类、材料不足、未确认联系分别展示；coverage CLOSED不能代替语义验收。

源码继续在独立 `sources.md`；链接可缺，不增加补证据调用。发布后不调用模型润色。现有Publisher行为无需重写。

## 10. 模型次数、失败与调用许可

本轮离线核对：产品调用0。后续默认使用用户最新指定的ChatGPT登录 `gpt-5.6-terra/high`、全局/服务并发4，不回退API；旧Activity的Terra/xhigh身份保持不变，具体新运行须记录实际模型身份和样例/全仓范围。

若需要新目录，设目录任务数为C（含分片和merge），进入重建的候选数为N，无失败/复用时：

```text
2C 目录请求 + 1 全局选材 + N 阅读检查 + 3N 过程请求 + 2 仓库归并
= 2C + 4N + 3
```

每候选仅WRITE为专门写作，DRAFT推理、RULE_REVIEW核对并纠正；归并/渲染没有新的正文写作。实际请求数减去精确复用，加已授权的替换执行，不能预先假定N等于418或325。若Activity确需局部补做，单列其阅读/DRAFT/REVIEW/attempt数量。

真实调用前分别列理想质量、不可接受错误、程序/人工检查、首轮及具名替换轮，沿现有ReaderCandidate与任务授权，不因新batch绕过轮次限制。出现同类错误后先定位输入/结构/Prompt，不自动多一轮“再试试”。模型等待与工程时间分别测量，不在设计中许诺全仓总小时数。

## 11. 版本、接口与历史

精确新旧字段、版本与迁移由[Activity集成合同](modules/activity-explanation/integration-contracts.md)维护；过程局部编号和三阶段的修改由[Reconstructor](modules/business-process-discovery/candidate-process-reconstructor.md)维护。原则是只升实际改变的合同，不重置工程。

- 新范围汇总：coverage-v4、M11 producer v4；Activity解释业务JSONL仍v2。
- 新阅读计划：activity-reading-plan-v2；历史v1严格读，未知完成性不得默认为完整。
- YAML补齐已设计但未接线的activityReading：config-v3；执行配置写v5冻结实际reading配置，历史v4严格读。run-output-v6及其 `activityBatchComplete` 保持。
- Step07新增同入口导航、局部编号与WRITE限制：修改相关Prompt/Schema及Discovery producer；新私有三阶段记录保存编码版本与映射，历史三阶段按旧合同读。
- 五项公共过程文件和确定性排版不改格式，Publisher v4不因前端输入优化机械升版；新的内容本身已有新身份。Step05/导航/持久化版本不变。

指纹区分语义输入变化与运行参数：改同入口组织、局部映射、Prompt、Schema或scope影响相应任务；只改并发、退避、路径不改变已审内容。保存映射不意味着改写raw响应。任何历史迁移都显式新运行输出，不补写旧文件或静默兼容新必填字段。

## 12. 用实际材料推演是否闭合

### A. 销售统计18条：不再变成18步销售流程

实际包 `packet:334a456eccc5b0cb5fe158da77bc6a9aefae3373f4a721adfb980552588ff377` 已有18条近似Activity，保存计划曾出现超大范围，随后多轮添加查询过滤、分页、商品聚合、销售净额等slice。这个事实不证明存在18种业务。

1. 离线重开计划及原始决策，区分已被替代的超限方案与仍必需的未完成范围；未知不猜。
2. 原18条保持，导航呈现“同一入口的18份解释”，保留各自范围和规则。
3. 目录/过程模型判断它们是一个统计活动的组成说明、多个独立统计目的，或其它有据用途；不得预置答案。
4. 若归为支撑分析，仍保存净额公式和过滤条件，但不插进“创建订单→审核→出库”的主流程时序。
5. 验收看是否减少重复和假步骤、实际公式/条件是否保留，而不是最终一定只剩1条。

### B. 新增单据：XML/SQL怎样带来具体规则

实际 `/depotHead/addDepotHeadAndDetail` 的Step05包有443个方法、58条statement；[已有原文推演](examples/activity-material-end-to-end-walkthrough.md)核对了：缺省状态才赋常量；两类关联不能同时填写；订金与关联订单同时满足条件才查累计值；累计查询排除当前单号和已删除单据。

1. 已有Activity给出已读范围，不因SourceRef指整XML就声称它读完整文件。
2. Step07模型按问题补读冻结常量、页面与相关XML原文，说明字段在当前业务用法中的意思。
3. DRAFT保留上述具体条件；WRITE把它写成读者可懂的抵扣订金说明，但不能改结构化条件或把金额统称应付金额。
4. RULE_REVIEW同时核对正文与规则，若解释订金时丢了“关联订单记录金额”的条件，同步修正；不得把所有通用方法规则复制给每种单据。

这条接力可用现有材料进行，不要求重新解析SQL；其中业务解释仍须真实模型/人工核对，设计推演不是生成结果。

### C. 调拨：同稿不同字段必须一致

历史调拨最终稿的业务规则包含强审核配置例外，但阶段拒绝条件与结束结果遗漏。新WRITE不改这些结构字段；最终RULE_REVIEW必须对照原文同时核对正文、拒绝条件、规则和结果，不能只在corrections说“已核对”。Java能检查结构保持和实际最终稿是否保存，不能证明中文已经无误。真实小样仍必须检查例外是否贯穿，而非看到三个请求完成就扩大。

## 13. 实施前后验证清单及停止点

| 检查层 | 必须直接观察 | 不代表什么 |
| --- | --- | --- |
| 文档审查 | 当前事实与目标分开、无“CLI未接”旧结论、版本字段与真实代码一致 | 实现已完成 |
| Activity修复 | 原plan重开、1DRAFT+失败REVIEW重试、不完整scope、公开部分成功、Provider分类、先验后SUCCESS | 325包业务均准确 |
| 旧输入离线核对 | 325包/326处置不缩分母；旧稿字节不变；COMPLETE/INCOMPLETE/UNDETERMINED可追到已有记录 | 自动证明每个未读方法无业务 |
| Step07完整fixture | 正式CLI真实Agent/存储→新M11→五文件→查询重开；旧M10也可读；上游和真实Provider0调用 | 真实业务质量通过 |
| 编码与写作 | A/T映射可逆、不串包；$defs有效；WRITE固定字段不变；最终核对实际看见两稿/原文；最终纠正才进正文 | Java可判自然语言真伪 |
| 样例 | 具体条件、否定、金额、配置例外、对象关联核对，并把原文交给用户看 | 可以未确认就自动全仓 |
| 全仓 | 实际候选数、逐任务复用/失败、归并、五文件及质量检查记录 | 全系统所有隐藏业务必然已识别 |

开发只跑新增/直接覆盖测试；正式实施交付须在当次计划明确获得完整套件授权后安排本模块一次完整本地质量检查，JDK21+质量宿主、Java17应用toolchain，不启用真实JDT或外层测试，不并发重型构建。文档本轮只检查差异、链接及受保护文件；不运行Maven。

停止点：本轮文档交付后评审；实施阶段先离线；真实小样后必须展示并等待扩大决定；全仓中fatal保留已成功结果、不自动重扫/换模型/增加轮次。无法在现有材料/容量下完成时明确给出受影响范围，不承诺一定能完整还原所有业务。

## 14. 本次变更与尚未实现状态

本设计新增的实质改动是：可靠的范围完成记录及历史离线接续、来源分组导航、局部短ID、限定WRITE职责、针对实际最终稿的条件核对、正式新来源贯穿验收和样例先行屏障。生产代码和模型Prompt本轮均未修改。

详细Module合同同步到相应owner；[补充设计入口](supplements/cross-object-process-reconstruction/README.md)只指向它们，不恢复第二份活跃合同。既有Activity修复草稿保持未验证标签，旧过程质量问题保留。[端到端实施计划](plans/end-to-end-business-delivery-implementation-plan.md)已获准执行，列明剩余修改、离线范围检查、小样展示与全仓确认点；实际完成状态独立记录，不以授权代表实现或验收完成。
