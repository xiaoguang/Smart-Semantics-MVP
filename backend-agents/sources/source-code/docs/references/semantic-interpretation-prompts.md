# 业务语义模型任务与中文 Prompt

本文服务 [Step06](../analysis-steps/06-flow-interpretation.md)、[Step07](../analysis-steps/07-repository-knowledge.md) 和 [Step08](../analysis-steps/08-nine-section-document.md)。术语以 [CONTEXT](../../CONTEXT.md) 为准，过程发现的完整变更范围见[业务过程发现与重建设计](../plans/business-process-discovery-and-reconstruction-change-design.md)。

## 1. 当前实现与本次目标

Step06材料和Activity任务已实现，326条已审结果保留；不重新调用。Step07 v2的完整Activity、正文、规则用法和来源页已实现。历史46过程不代表本轮验收，旧340 singleton已退役。

全局选材、每候选一次阅读检查及DRAFT前完整原文已实现，候选生产仍为DRAFT/REVIEW双轮。本轮目标是同一次全局选材中的系统认识、按问题保留/移出/补读，以及候选DRAFT→WRITE→最终RULE_REVIEW。完整中文指令和响应约定由[跨对象Prompt](../supplements/cross-object-process-reconstruction/prompts.zh-CN.md)唯一维护，当前差异见[实施状态](../supplements/cross-object-process-reconstruction/implementation-status.md)。本文件第4节保留首次目录，第5节索引新过程，第6节复用归并，第7节仍是未来九章；生产资源未在这次文档修改中更改。

## 2. 所有模型任务共同遵守的材料边界

### 2.1 程序与模型各自负责什么

程序负责打开已保存内容、分配 ID 和短引用、选择当前任务可见范围、生成闭合 JSON Schema、校验引用与覆盖、保存完整结果。模型负责业务词汇、对象别名、候选成员、活动在过程中的具体用法、阶段、分支、规则、结果和不确定关系。

程序不得硬编码任何行业或领域答案，也不得用字符串相等代替业务同义判断。模型不得创建 Activity、源码位置、SourceRef、代码调用、Fact/Proof、外部运行事实或人工确认。

### 2.2 不同任务看到不同的最小充分材料

| 任务 | 模型实际看到 | 明确不发送 |
| --- | --- | --- |
| Activity DRAFT/REVIEW | 一个 BusinessMaterial 的完整入口、方法正文、调用、参数、条件、返回、限制和短 ref | JDT/LSP 对象、hash、路径、运行/批次身份、完整证据链 |
| 全仓业务目录 | 全部ActivityIndexCard，含原conditions/steps/businessRules等字段；必要时稳定分片 | 完整源码、运行元数据、预置业务领域词 |
| 全局选材 | 冻结项目说明、旧目录、全仓Activity导航、文件相对路径目录、可选问题 | 人工正确答案、宿主路径/hash/运行配置 |
| 每候选阅读检查 | 调查问题、首批实际完整Activity/原文及有明确ID的读取结果、限制、全仓导航 | 未读正文冒充材料、业务硬编码 |
| 候选过程 DRAFT | 最终阅读包：成员/context完整Activity、statement、实际选中源码、限制 | hash/运行身份、未选全文、旧候选来源门禁 |
| 候选过程 WRITE | 完整实际DRAFT及其中名称、用途、具体规则和未知 | 原文包、旧成稿作事实、新材料 |
| 候选过程 RULE_REVIEW | 同一完整包、完整实际DRAFT和完整实际WRITE | 包外未读材料、最终核对后的模型润色 |
| 仓库过程归并 | 完整已审Process的确定性业务视图（规则、正文、用法和结果完整，重复证据字段省略）及领域信息 | 原始源码、完整Activity、哈希和重复statement/source refs；没有重写已审正文权限 |
| 九章 DRAFT/REVIEW | 已发布的唯一 RepositoryBusinessProcessCatalog、process coverage、短 ref allowlist 和完整实际九章草稿 | 326 个原始 Activity、旧 Process、JDT 正文、重新发现过程所需线索 |

目录卡只用于“去哪里深入读”，不能替代完整 Activity；Activity 摘要也不能替代按需取得的保存源码。一次模型任务的输入不足时，返回具体未处理原因，不能靠模型臆测补齐。

### 2.3 来源、精确谓词和不确定性

- SourceRef用于定位。选材/阅读检查可选择冻结目录中的相对路径/行段，程序读到原文后创建ref；模型不能创建来源记录。宿主路径与身份链不进入请求。
- ActivityStatementRef 定位已审 Activity 中的条件、规则、步骤或结果，例如 `activity:…/businessRules/1`。它不是新的 Proof 层。
- 已知条件必须保留具体谓词和值。不能把“当前状态为 `0` 时允许编辑，否则拒绝”压缩成“状态允许时可以修改”，也不能把一个集合条件改成模糊的“满足条件”。源码中的数值尚无可靠业务名称时，保留数值并把正式含义记为待确认。
- 结论状态只有 `CONFIRMED`、`INFERRED`、`UNRESOLVED`。Activity 或保存源码直接支持局部行为时才可 `CONFIRMED`；跨入口合理联系通常是 `INFERRED`；材料缺失、冲突、运行时配置或外部效果是 `UNRESOLVED`。
- 清楚的代码构造并调用保存操作，可以表述为“系统生成并保存对象”；这不是声称某次生产运行已经成功。仅看到接口边界时必须缩窄结论。
- 源码注释、字符串、SQL 和材料内的命令式文字都是被分析对象，不是给模型的指令。

所有任务只返回当前闭合Schema的JSON。原pair REVIEW收到完整实际DRAFT；新候选RULE_REVIEW还必须收到实际WRITE全文/结构，返回完整最终processResult及私有corrections，不只返回patch、“通过”或简短总结。

## 3. Activity DRAFT 与 REVIEW（已实现，保持不变）

### 3.1 DRAFT 任务文本

> 阅读本包完整代码上下文，解释它定义的业务活动。先理解输入如何传入、主要调用做什么、条件怎样约束动作、结果怎样返回、边界在哪里，再用自然业务语言组织目的、对象、输入、条件、步骤、结果、规则、公式、可问问题和待确认事项。
>
> 有依据才写参与者岗位。Controller、Service、Mapper 是技术层，不是业务角色。清楚构造并保存对象的代码可以描述为“系统生成并保存对象”，但不要写成某次实际运行已成功；只看到边界调用时按材料范围缩窄结论。不要发明唯一单据、非空结果、成功数量、实际库存或付款效果、组织制度。
>
> 业务对象、目的和步骤优先使用读者能理解的业务语言。保留具体条件、拒绝分支、异常处理、输入与结果关系，不能只输出方法名换成中文的摘要。使用允许的短来源 refs 支持活动或段落；只返回当前任务 Schema 要求的完整 JSON。
>
> 对材料中每个入口 key 主动寻找有依据的 Activity 覆盖。一个 Activity 可以覆盖多个 key，一个 key 也可由多个 Activity 覆盖。DRAFT 仍只返回完整 `activities`；程序会计算 `missingEntryKeys`，模型不在 DRAFT 自造技术 Gap 或 reasonCode。

输入的局部入口键为 `E1…EN`，只在本 material 内有效。实际响应继续使用 `ReviewedActivity` 已有字段：`businessPurpose`、`businessObjects`、`triggerOrInput`、`conditions`、`activitySteps`、`codeDefinedResults`、`businessRules`、`formulasOrMetrics`、`terms`、`certainty`、`sourceRefs`、`questions`、`scopeLimitations` 及现有身份字段。

### 3.2 REVIEW 任务文本

> 使用原始完整材料审阅下面的**完整实际 DRAFT**。逐项检查主要代码行为、参数来源、具体条件与异常分支、结果和边界是否准确；检查是否凭方法名或行业常识发明角色、流程顺序、唯一性、运行成功或外部效果。
>
> 保留原稿中准确、有用的详细内容，只修正有问题的部分。不要把完整 Activity 缩成标题、摘要、通过意见或 patch；返回 Schema 要求的完整修订 JSON。
>
> 程序同时给出 `missingEntryKeys`。逐项回到完整原材料：有依据时补入现有或新增 Activity；确实不能形成可靠解释时，把该 key 放入最终 `unexplainedEntries`。它必须存在，可为 `[]`，不得为 null，也不得包含材料之外的 key、重复 key、reasonCode 或自由原因。Activity 覆盖 keys 与 unexplained keys 必须无交集且并集恰好为本包 `E1…EN`。

每个结构、scope、JSON/key/ref/ID/bytes 都合法的 DRAFT 都进入 REVIEW；coverage 完整时 `missingEntryKeys=[]`，只有 coverage 不足这一种情况也允许携带程序计算的缺项进入同一个 REVIEW。started 请求失败、非法 JSON、未知 key/ref 或 REVIEW 再次漏项均为 fatal；不自动发第三次请求。程序把局部 `unexplainedEntries` 映射为带全局入口和材料身份的 `MODEL_NOT_EXPLAINED` 记录。

### 3.3 复用规则

已保存326个ReviewedActivity是正式输入，不重跑。若完整源码纠正其局部解释，在本次过程说明差异并保留原Activity；确实需要重做Activity时先讨论并另获明确同意，不自动追加delta模型任务。

## 4. 新仓库首次目录与全局合并（v2已实现）

已有目录本轮直接重开，不重复以下任务；改用[全局选材和阅读检查](../supplements/cross-object-process-reconstruction/prompts.zh-CN.md)。旧目录作为输入与完整任务精确复用不同。

### 4.1 CATALOG_DRAFT / CATALOG_SHARD DRAFT

> 从输入卡片发现系统支持的业务对象、目的和候选过程。先寻找同一业务对象怎样产生、改变、关联其他对象、完成或撤销，不要只按照接口的新增、修改、查询、删除分类。
>
> 同一个局部活动可能根据参数、对象类型或条件服务于多个业务。为每种有材料支持的用法分别给出variant和role，允许它参加同一或不同候选。不要把共享实现等同于相同业务，不要把整个通用活动里的所有条件都适用于每个用法。
>
> 在候选purpose中说明为什么这些用法值得一起阅读，以及联系依赖什么线索。可以根据对象、标识、状态变化和结果提出候选；不要把同名、同表或文件位置直接变成业务顺序。查询能揭示关联，但查询本身不等于创建或执行。
>
> 明确主活动、可选/回退、查询、统计和其他支撑用途。材料只支持片段时保留片段，不靠常见行业流程补全。所有输入Activity有现有Schema规定的成员、支撑、独立、未分类或容量处置；不要以强制归组制造完整性。只返回完整目录JSON，不写最终阶段。

卡片是已有name、businessPurpose、participants、businessObjects、triggerOrInput、conditions、activitySteps、codeDefinedResults、businessRules、terms、scopeLimitations的字面投影。Java不生成隐含actions/stateObservations，也不预置行业词。

### 4.2 CATALOG_REVIEW

> 对照本次全部卡片和完整实际草稿，检查是否漏了业务用法，是否把含多个对象分支的活动只保留为通用维护，是否把查询伪装成业务流转。检查每个候选purpose是否说明了材料中的联系，而不只是重复候选名称。
>
> 同一Activity可以有多个不同variant，不应因为ID相同删除用途。也不能为了凑完整流程增加未提供活动、未见对象或确定顺序。每个Activity处置必须与候选成员一致；无法分类时明确说明。返回完整修订目录。

### 4.3 CATALOG_MERGE DRAFT / REVIEW

> 阅读所有已审分片目录，寻找跨分片的相同对象、互补业务用法和候选联系。保留各成员的原ActivityId与variant；不同业务用法不能因为使用同一个实现重新压成通用维护。
>
> 合并重复候选，保留相关、重叠和不确定候选，检查全仓所有Activity都有处置。不得仅按分片顺序拼接目录，也不得在缺少联系时编造全生命周期。候选仍只供深入阅读，不是已确认的过程。
>
> REVIEW对照全部分片结果与实际完整merge草稿检查上述要求，返回完整替代记录，不返回摘要或patch。

分片输入合起来恰好覆盖全部Activity；同候选的唯一用法键为(ActivityId, variant)。声明PROCESS_MEMBER却没有成员关系必须报错，不静默改处置。

完整merge DRAFT是Activity覆盖分母的权威基线。REVIEW仍返回完整修订目录，但其重复输出的Activity处置清单若仅发生重复ID或遗漏ID，不得覆盖已经闭合的DRAFT分母；程序保留DRAFT的非成员处置，并按REVIEW后的候选成员关系重算PROCESS_MEMBER。若REVIEW处置清单本身完整且唯一，却仍把无候选归属的Activity声明为PROCESS_MEMBER，则继续明确失败。该规则只避免长数组抄写错误，不替模型发明候选或业务含义。

## 5. 详细业务过程（三阶段目标，尚未写入生产资源）

当前生产过程Prompt为v3双轮。最新目标正文只维护在[系统认识、推理、写作与核对指令](../supplements/cross-object-process-reconstruction/prompts.zh-CN.md)，避免本文件保留第二份可漂移的长Prompt。

| 任务 | 目标职责 | 实际输入/输出 |
| --- | --- | --- |
| PROCESS_MATERIAL_SELECTION v2 | 在同一次全局选材中形成可修正系统认识和可证伪业务假设、调查问题 | 项目说明/全体导航/目录/可选问题→系统认识、候选、首批请求；常识不当成已确认能力 |
| PROCESS_READING_CHECK v3 | 按问题明确首批保留/移出与一次补读 | 返回完整最终成员/context、retainedReadingRecordIds、supplementaryRequests及未知；空保留集不表示沿用 |
| PROCESS_DRAFT v4 | 事实与过程推理 | 完整包及调查问题→完整详细Process草稿，保留具体条件、规则、字段用途、公式和未知 |
| PROCESS_WRITE v1 | 把事实草稿写成可连续阅读的业务说明 | 只接收完整实际DRAFT；返回同一详细结构，保持局部ID/引用和全部结构化规则 |
| PROCESS_RULE_REVIEW v1 | 对实际成稿进行最后规则核对 | 完整包＋实际DRAFT＋实际WRITE→局部修正后的完整processResult及私有corrections |

WRITE中间稿不是已审结果。最后核对直接改正文和对应结构，正确且可读段落保留；之后只有确定性编号、保存和排版，不再模型润色。缺链接可空，缺关键材料则收窄或留具体未知，不追加取材循环。

完整Activity按ID去重，用法与context分别保留。stage.narrative、rule.activityUseLocalIds及其余完整结构继续使用现有Process合同；引用只定位实际包内材料，不是规则的旧Activity所有权。Java校验结构、实际引用及用法，不检查中文蕴含；三例最后正文仍需人工审阅。

## 6. 仓库归并DRAFT与REVIEW（已有能力，继续复用）

### 6.1 DRAFT

> 依据全部已审Process的业务完整投影视图、用法、阶段、分支及领域情况，给每个过程一个主处置KEEP、MERGE_INTO或REJECT，可另外记录PARENT_CHILD、RELATED或ALTERNATIVE关系。投影省略的是重复证据字段，不是业务正文、条件、规则或结果。
>
> MERGE_INTO只用于映射用法后完整阶段序列业务字段逐项相同的记录；不同阶段序列保持KEEP及适当关系，不要求程序推断怎样拼接。共用一个Activity不等于相同过程，尤其是对象variant不同。只有能无损保留业务顺序、不同条件和分支时才合并；无法做到则KEEP并说明相关/替代关系，不将阶段数组拼接成不存在的生命周期。
>
> 不重写narrative，不缩写规则、状态、公式、知识项和来源，不扩大规则适用范围。你只决定关系，程序保留原详细内容。

### 6.2 REVIEW

> 对照全部过程的业务完整投影视图和完整实际归并草稿，检查遗漏、错误去重、父子循环、不同用法混合、伪造顺序或certainty升级。所有过程必须有一个主处置；程序会把裁决应用到未缩写的原始已审过程。
>
> 无法确认相同过程时保留独立/相关，不任意合并。返回完整修订决定，不生成替代业务正文。

程序以全部已审Process作为分母；REVIEW遗漏的处置安全地视为KEEP。这个容错只防止长数组转录遗漏删除业务过程，不接受未知/重复ID，也不把DRAFT中的未审合并升级为正式裁决。

当前实现对无法无损执行的合并建议保留原过程及未合并原因；不让此类建议抹去已审正文，也不拼阶段或追加一轮修稿。

## 7. 九章 DRAFT 与完整 REVIEW（目标）

Step07 先从已归并 catalog 确定性发布 `business-processes.md`；它是业务过程质量的首要人工验收面。九章只是仓库级概览，不承担第二次过程发现。

### 7.1 DRAFT 任务文本

> 根据唯一、已归并的仓库业务过程目录和 coverage，写一份面向业务读者的九章概览：文档说明、业务目标、业务对象、业务活动、字段与维度、对象关系、指标口径、示例问题、待确认事项。第 3、5、6、7、8 章只使用 catalog 已保存的对应 knowledgeItems 正文；引用只定位来源，不能代替或暗示未提供的内容。
>
> 第四章按 catalog 中的业务过程组织，保留每个过程的名称、目的、主要阶段、关键分支和结束结果。不能重新给 Activity 分组、合并或拆分过程、改变阶段顺序、补造过程关系，或从过程摘要之外重新解释源码。详细规则和完整阶段由 `business-processes.md` 承载；九章可以精炼表达，但不得改变具体谓词和 certainty。
>
> 第七章只使用 catalog 已有公式或定义；没有时明确本次没有识别到可定义指标。第九章集中列出 `UNRESOLVED`、未分类 Activity、未处理候选和 coverage 限制。使用 allowlist 内短 refs，不输出 Markdown 样式、源码块、路径、hash、内部身份或第十章。返回完整九章段落 JSON。

### 7.2 REVIEW 任务文本

> 对照已发布的仓库业务过程目录、coverage 和完整实际九章 DRAFT 审阅整篇概览。检查是否遗漏过程、规则、分支或未处理范围，是否改变了 catalog 中的条件、状态值、顺序和 certainty，是否捏造角色、制度、运行成功、指标或外部效果。
>
> 只修正九章表达和编排；不能重新发现过程或回读 326 个 Activity 来覆盖 catalog。保留准确、有用的业务内容，返回完整修订九章 JSON，不能只回复通过、标题或修订摘要。

Java 验证九章顺序、类型、ref 和 coverage 后确定性渲染 `document.md`。纯重渲染零 Provider；Step08正文不附源码块；本次Step07新增sources.md只是一份可读来源视图，不触发九章修改或模型调用。

## 8. 调用、版本与验收

Activity、首次目录、merge及归并仍最多一次DRAFT+完整REVIEW。只有候选过程目标固定为DRAFT→WRITE→RULE_REVIEW；全局选材和每候选阅读检查继续是分别保存的单次决策。实际补读可空；Java不能决定语义充分。读取零Provider，无自动重试、切服务或重扫。

当前全局选择v1、阅读检查v2、过程v3双轮已实现；目标各版本及私有三阶段v3保存由[详细合同](../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)维护。上游Activity、首次目录及归并不整体升版，公共五文件不变。实际输入、全部阶段Prompt/Schema、任务序列和producer进入fingerprint；旧目录能作资料，旧pair不得误复用为三阶段完成。

本轮只对照[三个样本](../supplements/cross-object-process-reconstruction/acceptance.md)审阅最后正文的具体条件、联系、金额用途和业务语言，之后停止讨论全仓。多Activity/多Stage只是结构门，不能代替语义验收；产物不得把未知条件写成空泛确定句。

Step07正文和sources.md零模型渲染；三例独立预览不安装全仓publication。复用人工/历史阅读包只证明成稿效果，不能证明自主选材通过。此次文档同步无产品模型调用，也不授权后续全仓执行。
