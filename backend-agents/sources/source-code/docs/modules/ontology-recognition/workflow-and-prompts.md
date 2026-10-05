# 本体识别：Skill、CLI、模型提示词与可观察工作流

状态：2026-10-05，固定客户O0–O3已通过正式命令有限结束、保存和查询。O1六任务REVIEWED；O2财务和统计REVIEWED、采购关系因最终scope错误REJECTED；O3部分发布四文件，九项声明义务保留为八REVIEWED/一REJECTED，覆盖INCOMPLETE。18份真实请求的完整原文、用途、限制、同题目录及同包审阅已核对；模型遗漏和错误单列，不由Java补业务答案。最后复审两项框架缺陷均已RED→GREEN：8129直接回归20项全绿；最终质量6695通过（767文件格式清洁、SpotBugs零发现/错误、PMD通过，UT/IT跳过）。实现已提交[PR #35](https://github.com/xiaoguang/Smart-Semantics-MVP/pull/35)，不将模型拒绝和残余误读改写成业务成功。准确完整ID、实际成本和结果见[验收记录](../../supplements/ontology-formal-acceptance-20261003.md)，剩余动作见[稳定性计划](../../plans/ontology-stability-implementation-plan.md)。不宣称自主全仓、任意小窗口兼容或模型零错误；旧MD路径和历史结果保持原状。

## 1. 推荐方案与可修改面

采用 **Skill驱动Java CLI的固定workflow；操作内部由Java执行有界模型任务**。模型不直接操作磁盘；宿主Agent不临时拼输入。本体和业务解释采用同一责任原则，本轮不改旧Activity/过程算法。

| 方案 | 取舍 |
| --- | --- |
| 宿主Agent读文件、写摘要、调用后导入 | 材料依赖Agent能力/隐藏上下文；不提供这条第二通道 |
| Java准备材料、调度提取/核对；Skill调用和解释 | **推荐**：同选择清单生成同材料，过程可查；语义仍取决于模型 |
| Java理解中文、批准业务对象/关系/规则 | 不采用，需要重建语义解释器，不属于本框架的机械职责 |

普通用户无需编码。允许配置来源、范围、Provider/模型、容量/并发/调用上限，以及替换业务Prompt、宿主沟通模板。“只能改提示词”指**不写代码即可改变语义要求与报告表达**，不禁止选择运行能力；选择输入/模型是配置，不是改证据算法。

Prompt不能修改Schema、短号映射、来源/排除检查、投影算法或保存合同。改变这些机制须明确进入开发扩展，不由宿主Agent临时补脚本/字段/结果。Java结构检查不能保证模型一定遵守语义指令。

## 2. 四个操作，六个处理环节

以下四个命令已注册并通过限定正式运行测试，包括scripted QUESTION＋MODEL阅读和有限DDL。当前紧凑材料、隔离和查询已有直接回归，格式、SpotBugs及PMD检查通过；客户三例O0–O3与18份实际请求已核验，采购关系局部拒绝仍完整返回；不能将这项限定验收说成所有模式或模型语义正确：

```bash
source-analysis --config /absolute/ontology.yaml prepare-ontology --evidence-run <R4>
source-analysis --config /absolute/ontology.yaml identify-ontology --corpus-run <O0> --scope /absolute/scope.json
source-analysis --config /absolute/ontology.yaml relate-ontology --selection /absolute/identification-selection.json
source-analysis --config /absolute/ontology.yaml publish-ontology --selection /absolute/publication-selection.json
```

沿唯一RepositoryAnalysisAgent的start/executeStep/inspect/artifact接线，不另建公共方法、运行平台或启动程序。repository-knowledge只是保存分类，不调用旧Step07/knowledgeCheckpoint。

当前公共接口没有trace方法。本设计通过已有inspect/artifact新增本体查询键返回阶段观察和来源映射，不写成“已有trace可用”，也不为可观察性新增一个公共Agent方法。

```text
R4 + 可选同R0 DDL
 → O0：①读取/索引/短号/可选结构声明，零模型
 → O1：②认识系统与选题 → ③选读/封包 → ④提取/原文审阅
 → O2：⑤跨入口/跨题身份与关系提取/原文审阅
 → O3：⑥确定性校验、保存、查询，零模型
```

角色名不是只允许运行四次；可有多个同Corpus的O1/O2，每次新run。范围由scope明确，已结束run不可复活；一个操作成功不授权全仓扩大、模型调用或Action执行。无关系任务时O2零模型保存明确处置；没有指标/维度时允许为空，不为了齐全补数。

## 3. 五个内部Module与牵扯面

它们不是五个公共命令，不要求为薄职责建立空interface/adapter。

| Module | 输入和内部处理 | 输出/下游 | 实现改动面 |
| --- | --- | --- | --- |
| OntologyEvidenceCorpus | R4/R0准入→技术倒排/可选DDL→早期短号→执行选择清单/紧凑封包 | Corpus/导航给Recognition与Linker；冻结包给runner | 现有Corpus、ReadingPacket、ModelProjection、来源reader；薄投影与成本，不新解析语言 |
| OntologyRecognition | 范围→调查/选题→有界选读→OBJECT及所需操作/分析提取→原文REVIEW | 已审定义和未知给Linker/Publisher | 复用DecisionRunner/ReadingCoordinator/TypedTaskRunner；显式范围、scripted DISCOVERY及QUESTION＋MODEL分别限定通过；真实选材质量未验证 |
| OntologyLinker | 已审目录→邻居取材→身份/关系比较→原文REVIEW | 显式对应/不合并/关系给Publisher | 复用Corpus/runner；薄对应合同，不全入口笛卡尔积 |
| OntologyValidator | Schema/来源allowlist→引用、端点、组件及范围/版本检查 | 合法结果或诊断给runner/Publisher | 现有validator基础；不检查中文真假 |
| OntologyPublisher | 已审结果/显式对应→稳定映射、coverage→原子保存/重开 | 本体四文件、可选确定性视图 | 现有canonical store/receipt/Agent查询，新增本体分派 |

O0/O3及查询不要求模型登录。O1/O2先检查来源、范围、预算再初始化Provider；只接新R4/R0，不读取旧Activity/M10/过程/HTML答案。

## 4. O0准备：Java组织材料

**输入**：准确R4 receipt/index；由实际header恢复的R0；可选同源说明/DDL路径。

**处理**：一次严格重开，保留输入分母，建立已有技术身份的倒排及短号；可选薄用JSqlParser读DDL。不执行Maven/JDT/客户SQL或模型，不写业务摘要。

**输出**：ontology-corpus.json；配置DDL时同O0另存schema-evidence.json。完整源码仍保存在R4/R0。

**下游**：调查、取材、关联和来源追溯。来源损坏/不符阻断；动态SQL、地址未知、缺helper等保留。按清单准确准备不等于自动知道业务必需材料，详见[材料合同](material-preparation.md)。

## 5. O1识别：问题、阅读、定义与审阅

### 5.1 合法起点

QUESTION由用户给普通业务问题，不要求正确Controller/字段；DISCOVERY由模型看项目说明、技术线索组和入口目录提出假设/问题。允许系统类型未知/混合，ERP/CRM不能变成必需流程清单。

选择来源另记EXPLICIT或MODEL：前者是用户具名范围，后者是模型根据导航选择。二者用同一Java投影，不能把人工选择写成自主发现。调查仅产生候选问题/入口，不批准本体。

先按共同项组织线索，再按容量分页；相邻entryId不是相关性。选题汇总只比较实际问题，不再附整仓源码/导航。未比较/未读范围保存，不暗删问题。

#### 5.1.1 正式调查、选题的薄合同（已有限接通）

本节固定正式profile的字段，旧实验survey-v2/prioritize-v3不变。正式DISCOVERY要求`selectionMode=MODEL`且输入questions为空；QUESTION沿用户已经声明的问题和任务进入EXPLICIT或MODEL阅读，不为了齐全再调用一次调查。调查、选题和阅读均使用配置中实际保存的Prompt，不用宿主聊天补充答案。

- SURVEY输入版本为`ontology-survey-input-v3`，响应为`ontology-survey-response-v3`。输入只含当前有界导航、实际E/K短键、具体共同项、展示/未展示范围及容量上限；完整来源映射留程序侧。响应必需`schemaVersion / systemHypotheses / questions / unresolved`。hypothesis字段为`type / observedEntryRefs / uncertainty`；question字段为`questionId / question / candidateEntryRefs / clueRefs / searchTerms`。questionId是本次响应内唯一的Q短键；E/K只能引用本次实际展示的正确类别，字符串不是已确认业务结论。数组允许为空，未知明确保存。
- Java按已保存调查的确定顺序为每条实际问题分配汇总范围内唯一的`questionRef`（Q短键），保存其原始响应局部questionId、实际调查jobKey/页归属、问题全文及E/K映射。不同页的Q1不合并；页/任务长身份不要求模型抄写。重排模型响应不能把一个短键解释成另一问题；映射内容参与输入身份。
- PRIORITIZE输入版本为`ontology-prioritize-input-v4`，响应为`ontology-prioritize-response-v4`。输入仅含上述实际问题的`questionRef / question / candidateEntryRefs / clueRefs`、实际不确定性及有限选择/调用边界，不再重复完整导航或源码。响应必需`schemaVersion / selectedQuestions / deferredQuestions / unresolved`。选中项字段为`questionRef / specificQuestion / selectionReason / currentUnknowns / taskKinds`；延期项为`questionRef / reason`。选题可以收窄问法，但不得重写、增加或丢失原问题的候选E/K集合；该集合由Java从准确questionRef恢复，而不是让模型再次抄写。
- taskKinds只允许OBJECT、ACTION、ANALYTIC；每个实际选中问题必须先有OBJECT，另外两类按需要选，不强制齐全。RELATE仅由O2执行。Java生成唯一任务标签，转换为现有严格Scope/Question/Task，不引入第二套范围语言。MODEL任务从空正文开始，候选入口持续可见；已审对象按已有依赖规则传给后续任务。
- 重复或不属于本次输入的Q/E/K、错误类别及非法任务顺序直接拒绝；不靠同名、上一页、模糊匹配或宿主Agent补值。未在选中/延期数组出现的实际问题由程序记录`NOT_SELECTED_WITHIN_DECLARED_LIMIT`，不当作处理成功。调查或选题返回空结果只表示本次未提出或未选中任务，不能声明全仓本体已识别。
- 上限来自既有maxNavigationEntries、maxRequestBytes、maxOutputBytes和剩余maxRequests；每个实际请求计入同一有限额度。它们限制派发和封套，不保证全部题完成。需要的后续请求没有额度时保留UNPROCESSED，不自动追加额度或减去失败请求。Java保存页覆盖、问题选择及真实阅读分别的处置；只准备导航或正文不计为模型已读。

DISCOVERY的原始`scope`仍按第11.3节完整保存，并与输入摘要一致，其中questions为空。O1另保存`selectedQuestions`，逐项采用现有Scope.Question字段和实际生成任务，记录本次调查后真正执行的范围；不能把原始空questions说成“没有必需任务”。同一O1的`discovery`仅保存实际调查/选题jobKey、各页实际展示范围、程序的Q对应及选中/延期处置，详细请求和响应仍在既有私有journal。已读、未读与任务处置继续使用既有阅读和coverage合同，不增加第五个公开文件或第二套范围语言。明确问题/显式选择不生成虚构调查记录。

`discovery`首版字段固定为：`surveyPages`（每项`pageIndex / jobKey / entryRefs / clueRefs`，pageIndex从1开始、仅列实际派发页）、`priorityJobKey`（未派发为null）、`questions`（每项`questionRef / surveyJobKey / questionId / question / candidateEntryRefs / clueRefs`，局部Q与汇总Q显式区分）、`selectedQuestionRefs`、`deferredQuestions`（每项`questionRef / reason`）。所有数组按实际确定的调查/选择顺序保存，不从最后的正文范围反推。未派发页仍在既有coverage里处置，不制造jobKey；缺额度而未执行选题时selectedQuestionRefs为空，实际调查问题均保留未选择原因。`selectedQuestions`保存实际Scope.Question字段；正式typed成员仍以自己的taskRecords标识，不用裸Q跨运行查询。QUESTION/EXPLICIT不添加虚构的discovery。

上述两套正式响应已使用独立Schema资源，与Prompt、实际builder、校验器及scripted正式运行测试同步接通。它们复用DecisionRunner、同一运行额度和私有journal；没有新增调查Agent、语义聚类、全入口两两比较或重试框架。限定测试覆盖实际调查→选题→MODEL阅读→OBJECT审阅，以及非法问题引用和派发前容量拒绝。QUESTION＋MODEL另有正式入口直接回归；两者均不证明真实自主发现质量，不互相代替验收。

### 5.2 选读和冻结

模型看到当前问题、持续可见的已选范围、合法短键、真实成本、实际已读原文和限制。允许入口单元页、方法/语句用法反查、实际AST表列、字面搜索及完整读回，没有shell/网络/任意表达式。

Java执行请求并保存实际观察；模型明确保留/移出/补读/停止。selectedEntries与activeUnits独立，读了保存端不能让已选查询端消失。轮次/容量用完只能明确未完成，不能强制READY。

Java检查已申报必要单元是否真正入模，不能自己保证语义充分。未读helper不得被写成必然正常返回、抛异常或某个运算顺序。冻结后EXTRACT与REVIEW使用同一包；新增实现产生新材料/任务版本，不暗换旧稿依据。

### 5.3 小任务及依赖，不强制每题全套

| Task | 输入重点 | 输出 | 依赖 |
| --- | --- | --- | --- |
| OBJECT | 身份产生/查询、映射、属性/变体 | 对象、身份、属性、未知 | 无对象前置 |
| ACTION | 已审对象、操作/拒绝/保存/返回 | 多目标操作、规则、条件/效果 | 目标只能用输入已审对象；缺目标进顶层unresolved |
| ANALYTIC | 对象、SQL/XML、Java后处理、UI口径 | 维度/度量/指标，可空 | 对象须已审；组件可来自输入已审目录或本次同包，最终整体闭合 |
| RELATE | 对象、建立/保存/读取/更新两端 | links和顶层identityDecisions | O2执行，两端只能用输入已审对象；缺端点进unresolved |

按scope只运行需要的任务；先OBJECT，其他任务独立阅读，不硬并全部材料。下游获得已审定义及来源索引，相关关系/规则的REVIEW仍须实际原文。

**已接线并通过限定测试的依赖语义：** O1的ACTION/ANALYTIC只依赖同题排在它之前的全部OBJECT，不使用其它题的累计对象目录冒充前置成功；缺依赖者UNPROCESSED/DEPENDENCY_NOT_REVIEWED、零派发。ACTION失败不阻断只依赖OBJECT的ANALYTIC，也不阻断其它问题。O2用新版selection的明确objectSources恢复对象任务；QUESTION范围已验证，DISCOVERY保存问题分派仍需专项回归。准确字段与版本见[contracts§11.6](contracts.md#116-本次新增目标稳定证据与单任务隔离待实施)。不增加通用DAG或另一执行框架。

ANALYTIC同包候选的V/D/M可以互相依赖，但在完整REVIEW及组件校验通过前都不供其它任务使用。最终删除组件须同步修正指标或移到unresolved，不必额外增加模型阶段。ACTION规则的owner也可以是同包Operation。正式typed-v3只向模型投影B/B.P短键和机械重映射后的结构引用；完整Corpus/任务/review身份仅保留在私有catalog映射和journal。字段形状与unresolved/identityDecisions的准确位置由[contracts§7](contracts.md#71-正式任务字段可组装不让程序再读中文补结构)唯一维护。

辅助PoC在小题中一起返回对象/关系/操作，不意味着正式每题必须输出全部种类；正式Schema只开放当前任务字段。合并任务profile以后按容量/复杂度评估，不默认要求弱模型一次认识整套系统。

### 5.4 原定的一次REVIEW可处理结构错误草稿

- 普通问题是各任务共享的调查背景，不要求OBJECT同时返回操作、请求链或指标。OBJECT只识别有身份/属性依据的业务实体；身份不足可保留PARTIAL及具体未知，不能为了给动作或计算分配O编号而创造对象。ACTION/ANALYTIC/RELATE分别负责其自身定义。
- REVIEW以最终返回的定义集合检查所有引用，不只检查顶层unresolved。对象变体条件中的`targetObjectRefs`、Property bindings、操作目标、规则owner、关系端点、指标组件和粒度键都必须闭合。删除草稿中确有的O3后，仍不能在嵌套条件引用O3；从未定义的O2也不能因为出现在说明文字里便成为合法端点。
- 有依据且仍被需要的对象由模型保留/补出完整定义；端点不足时保留原条件、表达式、来源及具体未知，在Schema允许时返回空target引用。Java不把O3猜成O1，不删除业务条件以制造成功。这里完善的是通用任务/审阅指令，未改变定义Schema、局部编号分配或严格校验；模型仍可能不遵守，最终坏引用仍拒绝。
- 原有`actualDraft`与`reviewedCatalog`已经保存真实定义目录；不再增加一份重复编号表或新的引用修复器。Prompt实际文本参与现有任务匹配，修改后是新任务，不改写或升级历史已审请求。
- 提取返回后立即保存原始响应、输入和校验。正式typed-v3 profile以静态、Provider-free的`prepareFormal`先冻结并检查EXTRACT封套（无需先构造runner），再由`runFormal`派发；它保存实际请求、原始草稿、候选校验、REVIEW请求/响应、运行时身份和完成结果。
- JSON可读、根任务可识别且有界，但局部端点/引用/字段结构错误：标为无效候选；**原定REVIEW可收到原始错误草稿、同包完整原文及通用结构诊断**。不能先把它当已审结果供下游。
- 非JSON、超容量、来源/映射损坏、请求状态不确定或保存失败：不让REVIEW替代一个缺失草稿，不自动追加请求。
- REVIEW可修正/删除/拆分；返回完整结果和对应corrections。程序仅规范化无歧义的结构记号：对象内Property声明的`O1.P1`写法转为`P1`、本次定义误列为已审known引用时移回本次related引用、corrections改由实际草稿与最终定义的顶层结构差异计算。原始响应、规范化响应和事件全部保存；重开时重算核对。业务名称、定义、条件、来源和未知不由程序补写，规范化后仍非法则本次任务失败，不自动重试。
- 最终结构/引用仍不合法则失败，保存原两份结果，没有第三次自动修稿。

这个窄合同已由正式typed-v3 profile实现；旧实验typed-v2 runner仍保持候选校验失败即停止的历史行为。它不是阶段恢复平台，也不保证审阅必纠正。

最终坏稿导致**该任务拒绝**，不导致所有独立任务跳过。O1/O2正常返回的非法审阅稿，以及直接坏JSON和Provider报告的INVALID_JSON，已有局部隔离测试通过；原始响应保存，非JSON不额外派发REVIEW。其它失败须按[详细设计§7–9](evidence-stability-and-failure-isolation.md#7-错误分类及准确处理)的实际生产边界分类并保存，不能仅因保存了failure便判断为局部模型错误。Java不猜业务端点、不删除条件。坏稿和证据分别稳定保存，已审目录仅含最终合法完整任务。

## 6. O2跨Controller、跨题关联

**输入**：准确O0；同Corpus所选O1中的完整已审对象任务；selection中的具名比较范围。新目标允许来源运行PARTIAL，但实际对象依赖须全部REVIEWED，未处理义务仍继承；不能从拒绝稿抽半个对象。当前v1和本次目标v2按[contracts](contracts.md)分别读取。

1. Java按准确方法/语句/表列/已有字段绑定给技术邻居、共同项和完整句柄，同表不直接批准关系。
2. 模型核对标识如何产生、传递、保存、查询/更新及反例；两个Controller无需互调。
3. RELATE区分SAME_OBJECT、ROLE_OR_VARIANT、BUSINESS_LINK、SUPPORTS、UNRELATED、UNRESOLVED，再一次原文REVIEW。
4. 输出显式对象对应或不合并；同名、同表、共用Controller均不能自动合并。
5. Publisher只执行已审映射；未比较同名对象分别保留，标未统一。关系端点和指标组件不得悬空。

对象拆分/身份改变时列受影响定义；未重审项不继承旧审阅状态。首版不全量自动回溯重算，用户可启动新的受影响范围。只有材料不足则UNRESOLVED，不补完整业务生命周期。

## 7. O3确定性发布，不再模型写一遍

输入为准确selection、完整已审结果及所有最终处置。程序检查来源/作用域/引用/显式对应/coverage后稳定排序、生成本体ID及原子安装：

- ontology.json：对象/身份/属性、关系、操作/规则、维度/度量/指标、建议和未知。
- ontology-coverage.json：来源、实际阅读和语义任务处置分别统计。
- ontology-sources.jsonl：定义→阅读包→R4/R0/可选DDL的可逆来源。
- ontology-review.json：模型身份、实际审阅修正、结构结果和独立人工接受状态。

可选ontology.md只渲染已审字段，不增加WRITE。publication=DRAFT_REVIEWABLE、指标NOT_EXECUTABLE；COMPLETE只表示声明义务闭合，不表示穷尽业务或语义全对。已知语义错误须拒绝/待确认，不因Schema通过仍展示为已确认事实。

## 8. 提示词：产品任务与宿主沟通分开

| 层 | 可改 | 固定合同 |
| --- | --- | --- |
| 业务Prompt：调查/选择/对象/操作/分析/关系/审阅 | 语言、领域说明、语义要求、纠正风格、例子格式 | 真实材料、Schema、合法引用、任务/工具/容量上限 |
| Skill沟通模板 | 怎样解释结果、询问和展示未知 | 真正命令、结果事实、不能擅改材料/范围的操作规则 |
| 框架封套 | 不属于普通Prompt定制 | 来源/任务身份、原文是数据、准确保存和结构检查 |

沟通模板由宿主Agent按真实结果表达，不调用产品模型另写一份解释，也不建Java审批/错误翻译系统。改沟通模板不重算本体；改业务Prompt只使依赖该Prompt的任务匹配变化，不使R4失效。

目标配置只指定Prompt文件和明确运行选择，用户不用编辑巨大Schema。排队时读取并保存实际UTF-8内容；执行前变化须新run。实际组合Prompt、Provider实际收到的Schema和映射参与指纹并可查询，相同文件路径不等于相同Prompt。模型输入不包含沟通模板或宿主隐藏聊天。

通用语义要求：一次具体问题、小型typed输出；ID字段只填标识/标识数组，解释放description；对象不是表答案，查询可记录为QUERY操作但不等于改变业务状态的Action，静态实现不是执行成功；具体条件/运算顺序/过滤/粒度保留；缺helper写未知；关系核对两端；维度区分筛选/展示/分组，数值或符号转换不凑经营指标。程序/存储类型只填dataType，不能当业务unit或币种；原文未给单位时保留UNKNOWN。REVIEW看实际候选和原文，不看人工标准答案。

Prompt无法保证遵守；Java不新增中文语义黑名单或蕴含器。用户可改Prompt另行运行，不能无限自动纠错。

## 9. Agent沟通：可观察、可移植，不是黑盒

Java通过现有StructuredModelProvider执行产品模型，宿主只编排。当前存在Codex subscription/OpenAI Responses适配，不能承诺任意厂商已支持；新厂商实现既有Provider接口，不让宿主代写结果绕过它。

目标CLI结果最少含：runId、operation、执行/保存状态、真实产物、准确来源/上游/scope、框架检查、模型结果、coverage、具名issues、实际支持的nextActions及新增/复用/未读计数。控制流不依赖中文message。nextActions不能提供尚未实现的功能或任意shell。

Skill必须：
1. 按目标调用操作；资料准备零模型。
2. 缺输入/阻断时读实际诊断，说明缺项、已保存内容和可行选择，不自己补字段。
3. 在既有Provider/范围/额度内执行；已获授权的同范围不每轮重问。
4. 读JSON及已安装结果；退出非零也查询报告，不能仅凭文件存在/退出码猜成功。
5. 用户仅要小样则不扩大；已要求完整工作流且无阻断则连续执行已支持命令。
6. 改业务Prompt/范围/模型或重试须新run，不复活旧状态；普通运行不要求用户写代码。
7. 提供最终本体、未知及选材清单、实际阅读包、Prompt/Schema、原始候选、REVIEW、来源的查询方式。
8. 用户改变组织算法时明确转开发扩展，保存新协议/测试，不能用临时补丁冒充框架能力。

Trae、Claude Code、OpenClaw等只需CLI环境、Skill说明、可用Provider和来源配置；不依赖Codex屏幕、跨聊天消息、桌面插件或此聊天隐含答案。这是设计合同，**不是已经在所有宿主产品实测**。

## 10. 容量、失败、保存与复用

每阶段实际封套包含Prompt/Schema/导航或原文/已有对象/实际草稿/输出余量。maxOutputBytes是响应字节边界，不是max-output-tokens；本体请求独立传递maxOutputTokens，SDK实际HTTP请求的直接测试已验证两者不混用。旧MD请求行为不改，本轮不顺带修旧分支的历史单位问题。无可靠计数器时披露估算，不建分词证明系统。

**当前限定验证**已证明typed阶段坏JSON/非法最终引用只结束本任务，后续独立任务继续；准确身份不会因中间失败串位。MODEL阅读坏响应的局部处置、原始响应保存和后续任务继续也通过直接测试。实测完整单元超限在排队后的对应任务记录UNPROCESSED/MATERIAL，未准备无job；已知响应超限局部拒绝；私有journal写失败以STORAGE共享停止，canonical存储仍可写时保留完整处置。O2派发额度失败保留准确taskId/phase/category的typed公开观察；O1/O2后续公共安装失败以STORAGE/INSTALL覆盖操作终态，但不覆盖先前私有坏稿或任务处置。完整任务查询、来源失败报告及无回执组装诊断已有定向通过；MATERIAL命令问题摘要及跨运行准确身份/同语义任务选择已经通过直接回归；最终O3非已审处置按原owner聚合，不能将裸taskId当全局身份。限定验证不代表所有外部故障或模型语义已证明。无第三次自动修稿；本地CLI退出不证明远端取消，远端未知停止对应绑定并结束CLI返回报告。来源/配置/认证/存储共享故障或OUTCOME_UNKNOWN停止实际范围；未知STARTED不重发。具体分类、依赖和有限进程规则以[失败隔离设计](evidence-stability-and-failure-isolation.md)为准，不按消息猜控制流，不扩到旧Step07。

CLI必须结束并返回完整taskOutcomes、已保存部分和未执行原因，不在进程内等待用户修改Prompt。canContinue表示原声明范围完整，nextActions另表示实际查询、部分发布或新运行；false不自动禁止独立任务或零模型查询。任务与阅读覆盖按producingTaskId/jobKey关联，不按prepared/completed下标；O1/O2有直接测试及真实局部拒绝后继续的结果。task-index-v2、准确nextActions、任务坏稿和安装失败查询已接通并有直接回归；无法实际保存时仍不得虚构报告或receipt。Provider远端未知/本地退出分别记录。

默认无自动重试、无Provider/账户/模型回退。已获授权的同范围修复不重复索取相同许可；超出授权或支持能力须另行确认。任何新增执行建立新run，不复活历史失败或不确定请求。

完整已审任务复用匹配问题/范围、来源、全文输入/映射、投影、实际Prompt/Schema、任务顺序和模型身份，不复用孤立稿或旧业务结果。已完成查询不依赖原Maven/JDK目录存活。producer/reader/store/request/output及fixture同步，不改旧PoC版本，不改名导入正式publication。

## 11. 分层验收和当前能力

| 层 | 验收 | 不保证 |
| --- | --- | --- |
| 框架/材料 | Java零模型准备、完整选定原文/用途/限制可反查、状态不丢、紧凑输入、真实保存 | 自动知道全部业务必需材料 |
| 模型/业务 | 明确问题的实际定义/规则质量、原文核对及残余错误 | 任意弱模型必答对或自动纠错 |
| 覆盖/可移植性 | 已发现对象和任务有处置、CLI/Skill共享说明可执行 | 穷尽全部业务、所有模型/宿主已适配 |

两题辅助PoC已支持材料/局部联系方向；选材人工，维度为空，两金额定义不是完整指标体系。正式CLI、DDL全dump、全局对象统一、自主全仓和任意小窗口未证明。

正式实现先以真实保存片段及非ERP fixture离线跑稳材料、状态、Prompt、错误稿REVIEW、ID和保存查询，再按有限真实范围验收；不继续用产品模型修实验驱动。框架不追求模型零错，也不把漏材料归给模型。

## 12. 本次设计纳入与延期

纳入backlog26紧凑投影/真实成本/范围保持，27前端选择回调/保存单元正式保留，28弱模型及可纠正性硬约束；同时设计早期短号、可配置业务/沟通Prompt、可选DDL证据、typed端点/指标合同、正式CLI/查询。

延期全仓发现验收、旧Activity适配、完整采购生命周期、动态SQL执行/实例采集、Foundry部署、跨版本增量复用、通用解释器/图平台。27仅补已有parser支持模式，不借本体设计扩大JS能力。

改动清单与旧计划门禁调整见[implementation-delta](implementation-delta.md)。本页维护已批准方案及当前实施边界；它不授予模型调用许可。三个正式样例已有本次收尾范围的授权；先完成离线贯通，再以每运行有限配置执行，不继承旧PoC累计预算反复阻断同范围程序修复。扩大来源、模型或业务范围仍须另行确认。
