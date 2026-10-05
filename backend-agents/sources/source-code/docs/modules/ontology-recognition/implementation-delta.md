# 实验到正式本体框架：详细改动与验收对应

状态：2026-10-05，固定客户O0–O3已通过正式命令有限结束、保存和查询。O1六任务REVIEWED；O2财务和统计REVIEWED、采购关系因最终scope错误REJECTED；O3部分发布四文件，九项声明义务保留为八REVIEWED/一REJECTED，覆盖INCOMPLETE。18份真实请求的完整原文、用途、限制、同题目录及同包审阅已核对；模型遗漏和错误单列，不由Java补业务答案。最后复审两项框架缺陷均已RED→GREEN：8129直接回归20项全绿；最终质量6695通过（767文件格式清洁、SpotBugs零发现/错误、PMD通过，UT/IT跳过）。实现PR正在交付，不将模型拒绝和残余误读改写成业务成功。准确完整ID、实际成本和结果见[验收记录](../../supplements/ontology-formal-acceptance-20261003.md)，剩余动作见[稳定性计划](../../plans/ontology-stability-implementation-plan.md)。不宣称自主全仓、任意小窗口兼容或模型零错误；旧MD路径和历史结果保持原状。

## 1. 当前实现可用到哪里

| 实现/记录 | 已核实能力 | 必须区分的缺口 |
| --- | --- | --- |
| OntologyEvidenceCorpus | 严格打开准确R4 v1/v2，反查、字面搜索、完整单元、稳定短号及页上下文通过直接测试；当前O0正式安装/查询返回339入口、15,726别名，辅助原文经同源R0补读 | reader仍物化全集合，不宣称按需低内存平台；目录不等于全仓业务覆盖 |
| OntologyReadingPacket/ModelProjection | 紧凑v4保留完整源码、用途、逐调用限制及S映射；18份真实EXTRACT/REVIEW成本核对，34个完整Java正文逐字匹配同源R0，各题同包同目录 | 采购最大容量检查额460,216字节，不保证任意小token窗口；实际模型残余遗漏单列 |
| DecisionRunner/ReadingCoordinator及正式runtime | 正式范围、选材状态和冻结直接测试通过；scripted DISCOVERY和QUESTION＋MODEL通过真实Agent/store运行 | 真实自主发现和选材质量未验收，scripted成功不能替代；独立旧QuestionExperiment不属于正式交付路径 |
| TypedTaskRunner/Validator/ScopedAssembler | 正式typed任务、一轮审阅及严格引用检查已工程验证；实际O1六REVIEWED，O2两REVIEWED/一REJECTED，O3发布完整已审任务并保留九项处置 | 结构合法不是业务正确；非已审任务按原owner聚合已直接验证；模型语义仍单独核验 |
| 辅助两联系Java投影/assembler | Java17实际运行、零准备模型、完整选中原文、重复运行相同 | 使用人工清单和实验前端补充；不是正式CLI、不是严格纯R4的自主选材 |
| Provider/任务池/私有保存 | 实际Prompt、请求、原稿、审阅、诊断及观察可查询；实际18请求全部确认开始/结束，无未知，无自动第三轮 | 订阅适配器输出token上限未强制；正式响应大小/期限已修复并回归；最终质量通过，远端取消不在承诺内 |
| R4/四技术命令 | 新前端v3/R4 v2合同、准确历史owner及公共查询通过；339入口、91请求、14页面上下文；采购完整选择回调和保存原文进入真实O1/O2 | 旧材料不改写；前端静态关联不是运行时因果证明，本轮不重跑取证 |
| SourceAnalysisCli/Agent/registry/canonical store/Skill | 四个正式本体命令实际完成O0→O1→O2→O3；失败范围与有效四文件均可inspect/artifact查询；O0/O3/查询零模型 | 两项边界修复后质量通过；最终交付核对与PR正在完成；未安装所有宿主Agent，不声称任意宿主实测 |

实际证据规模是固定R4的339入口，最近辅助PoC的27单元及4调用；这些数字是当前验收输入，不进入通用规则/提示词。

## 2. 依次修改，不新增平台

### A. 先定机器合同和正式准入

修改请求/输出的独立ONTOLOGY分支、ontology-config-v1、scope/selection、公共O0–O3 v1及私有新版本；生产者、读取器、Schema、存储策略与fixture同步。复用当前request v5/output v9，目标v6/v10；不新增公共Agent方法。

准入只接版本明确的按入口R4 v1/v2与其准确PREPARED_V1 R0；历史v1和新增v2分别按原生产者、Schema、策略及controls读取，不能统一当作v2。新前端修复的三个真实样例使用具名新v2结果，历史v1准入仍须独立验证。旧PoC输入可作为明确测试/历史比较，不能改名作为正式O1结果。旧Activity、M10和Process没有转换器；旧结果保持可查看。

**完成证据**：错误owner、版本、排除范围、伪receipt在Provider初始化前拒绝；没有本体路由时不回退旧业务runtime。

### B. 正式材料投影与选读状态

在现有Corpus/ReadingPacket/ModelProjection和协调器内收拢职责：技术倒排/共同项、早期短号、完整单元、紧凑调用上下文、逐调用限制、实际成本、范围与activeUnits分开、可逆映射和输入冻结。实验208行prepare器只说明方法可行，不整份直接复制其P2/实验源适配进生产。

**完成证据**：选择清单相同则零模型准备相同；所有选中完整源码可反查；高调用方法不附全部私有记录；歧义/未知仍定位；选中未读查询端不丢。完整阅读/提取/审阅封套均测量，而非只比较包JSON。

**已接通的同源补读与剩余验收**：新O0规则通过准确R4上游恢复R2声明目录，再从同R0读取完整边界。已登记来源与唯一的保存候选进入现有Corpus；只有选定单元进入模型。直接测试已查看EXTRACT/REVIEW中的完整辅助正文、入口用途及未确认观察，旧规则仍按原格式读取。`SOURCE_REFERENCE`不再只有元数据，但这不等于任意路径读取能力。固定客户新O1曾因同入口原单元与补读单元的限制不一致在准备阶段拒绝并阻止派发；现在替换单元保留原限制、新增单元继承经核对的同入口限制，11项直接回归通过。当前O1和O2已经正式有限结束，18份实际封套均核对；统计helper在EXTRACT/REVIEW中逐字匹配同R0，并保留原NAVIGATION_CONFLICT。不得由宿主Agent粘贴正文代替。补读不重跑JDT，也不把未确认调用候选升级为确定关系。v4紧凑投影已有无损直接GREEN；模型遗漏和拒绝与材料缺项分别记录；最后工程边界和PR仍在收尾。

### C. 必要前端材料补齐（backlog27）

仅将已用Vue parser及实验已支持的事件、选择回调、数据整理、保存封装和页面实例上下文接入正式R1→R4发布/重开。保留原查询链，不扩大为通用JS求值器。原R1/R4不覆盖；形成具名新前端/组装运行，后端/持久化不因此自动重算。

这项是**技术材料生产修复**，不能与B的本体consumer混称。本轮已授权同源新建前端R1与组装R4，准确复用原R0、后端R2和持久化R3；不重跑Java/SQL取证。新R1写frontend-http-index-v3/module-v3，新R4写entry-evidence-v2家族/module-v4，旧格式严格保留。没有新正式结果前，采购PoC只能注明用了同源实验补充。

**完成证据**：真实选择/保存完整单元经正式发布、读取、组装出现；两个页面不串参数，非ERP改名fixture通过；原全部限制/未知地址保留。依赖新R4的本体运行使用新输入身份，不能自动复用旧材料任务。

### D. 可选DDL证据，有限成熟工具适配

O0按明确同R0 schemaSources读取原文，用现有JSqlParser只投影实际支持结构，按机器合同可选保存schema-evidence.json。未配置时不解析、不要求文件、不增加串行R5/DDL CLI。whole dump失败仍保存实际诊断，不能生成假全库表目录。

**完成证据**：源版本/排除核验、parser版本和状态可查；普通索引不当外键；无列定位时引用原文件；配置关闭零parser；不执行SQL、无自研分号切分器/DDL恢复器。DDL不支持不阻断不依赖DDL的对象/联系，依赖库级约束的结论写未知。

### E. typed语义任务及实际REVIEW

现有TypedTaskRunner/Validator改用正式结构，引用已有对象目录；Operation使用多目标数组，表达式/条件与解释分开。模型任务仅返回当前类别，不强制凑六类。明确定义ID作用域，空维度/指标不会导致程序“未完成”，但要记录实际任务范围和未识别处置。

允许可读/有界但结构不合法的实际草稿进入原定一次REVIEW并附通用诊断；最终严检，原稿不改。未提供helper/技术矛盾不得被Java改成业务事实。任务真实Prompt、实际稿件、来源与容量均保存。

OBJECT中的普通问题只作为共享背景，不要求它定义保存动作或统计过程；REVIEW必须按最终定义集合核对嵌套条件target、property、operation、rule、link与metric引用。删除对象后有依据就由模型保留/补回定义，没有依据就保留原条件/来源并明确未知；Java不重定向悬空端点。当前修正使用原有actualDraft/reviewedCatalog和严格validator，不增加重复的编号表。Prompt变更能降低机械出错机会，不能承诺任何模型都会遵守。

**完成证据**：坏端点/O1说明文本可被scripted REVIEW修正而调用数不增加；最终坏ref仍拒绝；非JSON/来源损坏不绕过；真实对象/关系可按字段组装。不能因为模型把某金额写进metrics便自动批准经营KPI。

### F. 跨题对象对应、关系与确定性发布

O1局部定义按taskId/localId保存；O2读取已审目录和实际原文做显式身份对应/业务关系REVIEW。Publisher只按合法字段/对应组装，不依中文名称合并。未知同名对象允许独立，组件/关系引用不允许悬空。

**完成证据**：两个题的O1/S1不串源，输入顺序无关；SAME_OBJECT映射合法且可反查；角色/变体/不合并保留；冲突没有处理则PARTIAL/UNRESOLVED。最终四文件、sources和coverage一致，NOT_EXECUTABLE/DRAFT_REVIEWABLE不被视图改掉。

### G. 正式CLI、Prompt和Skill接线

新增四个本体操作配置/结果分派及模块2–5的准确文件合同，沿现有Agent、registry、canonical store保存/重开。不让O1/O2借旧knowledgeCheckpoint。O0可选schema文件的两种精确文件集各测试，不放宽通用存储规则。

业务Prompt覆盖按实际文件内容绑定；宿主沟通模板只是共享Skill文本。输出token设置与响应字节限制正确分离；厂商不支持某Schema特性时只薄做协议投影，最终本地校验不放松。

inspect/artifact开放真实材料/选择/Prompt/原稿/REVIEW/映射/失败查询，受保护访问策略仍生效。Skill先看真实JSON/产物，再解释；不知道则报未核实，不能自行拼材料。只修改Prompt的客户不需要写代码；改组织机制则转明确的开发扩展。

**完成证据**：同一正式入口scripted闭环，0准备模型、0上游工具调用；0任务运行不登录Provider；失败报告可查询，没有假receipt。第二个Agent仅看说明/配置/产物即可完成已有范围接力，不依赖本聊天补值；无需为了这项安装全部Agent产品。

### H. 稳定证据、局部错误隔离与有限结束（限定工程及实际证据验收完成）

详细行为由[evidence-stability-and-failure-isolation](evidence-stability-and-failure-isolation.md)拥有，准确版本/字段由[contracts§11.6](contracts.md)拥有。已保存R4/R0和坏响应不改写；已有OBJECT/REVIEW指令继续严格约束最终引用，不新增猜端点或删条件的程序修复。

以下是本轮修改与已取得的定向工程依据；真实三例O0–O3已有限结束并保留有效部分，最后两项边界20项直接回归和质量6695均通过。此表不意味着模型语义零错误：

| 本轮修复点 | 修改位置/职责 | 定向验证的行为 |
| --- | --- | --- |
| 一个任务异常跳出O1/O2整段循环 | 现有runtime任务边界catch，可靠区分局部模型/容量与共享来源、配置、认证、存储和状态不确定 | 中间任务最终拒绝，后续独立任务继续；共享故障仍停止实际范围 |
| formalCoverage按列表位置对prepared/completed | 现有runtime覆盖投影按producingTaskId/jobKey关联 | 第二项失败、第三项成功后第三项阅读及来源不串到第二项 |
| 已审目录累计跨题，缺少明确依赖 | O1同题前置OBJECT规则；O2selection-v2 objectSources；formal catalog只投影真实依赖 | 另一题对象不能满足本题依赖；缺依赖任务零Provider初始化/零派发 |
| 单值problem不能表达多个失败/未执行 | 现有阶段payload与私有观察保存完整taskOutcomes，真实job成员另存 | 每项声明义务都有终态及具体原因；未准备者不伪造jobKey |
| canContinue=false容易被误作整条流程禁行 | CLI/inspect新版观察加入准确nextActions；O3保留PARTIAL源范围 | 有效部分可查询/按明确选单零模型发布，原覆盖仍INCOMPLETE |
| selectedRelationStage要求每个O2的O1列表等于O3全表 | v2逐个核对O2自身完整来源，再验证其O1集包含于O3显式集合，允许额外独立O1 | 两个关系题分别依赖不同O1时可共同发布；缺语义上游仍拒绝，旧v1规则不改 |
| 预检前累计计数被说成实际请求已派发 | 现有Provider及runtime观察区分预留和实际开始/结束 | 登录预检失败不算确认STARTED；未知状态明确记录 |
| 超时计时在同步stdin写入后开始 | 现有ProcessCodexSubscriptionCommand整次期限及有界写入 | 可控假进程不读stdin也能有限结束；确认终止/不确定分别保存，无重叠重发 |
| 真实采购包仍有较大机械上下文，统计helper未入模 | B的任务相关投影和正式完整单元补读，不重取证、不建新解析器 | 查看真实EXTRACT/REVIEW和反查，不能仅凭文件存在/包字典压缩算通过 |

这项不重写执行器或增加恢复平台，不改变旧MD语义/Prompt/渲染。Producer、Reader、Schema、policy和fixture一次接通新版O1/O2、覆盖/审阅与查询观察；旧文件按原合同查阅，不自动转换。先离线脚本Provider跑稳错误路径，再继续已授权范围的实际样例。任何新真实调用不由本次写设计自动触发。

**完成标准**：坏稿不改证据且不发布；独立任务继续，依赖失败者有准确未执行原因；正常错误路径CLI结束并可查成功/失败/未处理；来源/存储或请求状态不确定不绕过；原范围完整性与模型语义质量分开。模型永不再错不是关闭本项的条件。

## 3. 实际例子：材料怎样成为业务联系和本体

以下机制来自辅助PoC实际原文/审阅，**不是正式CLI已经产生的ontology-v1**。正式接线会用同样机械操作；业务字段不写入通用程序规则。

### 财务关联

1. 选择清单包含保存端和查询端，Java取10个完整单元。原文共11,506字节，实际EXTRACT输入13,718字节。
2. 模型读实际原文，识别三类局部对象：财务主表、财务明细、业务单据；这是模型结果，不是Java根据类名建对象。
3. 保存端原文以row.billNumber调用getDepotHead(String)，取返回id赋给AccountItem.billId，insert XML条件写bill_id。
4. 查询端SQL以ai.bill_id=#{billId}筛选，ah.id=ai.header_id关联财务主表，选择ah.bill_no。它没有直接JOIN业务单据表；Java将完整XML原文和查询代码一起送入REVIEW。
5. 实际REVIEW保留2条关系/2个操作、具体删除过滤和未读helper未知。Java校验端点/ref，然后局部组装；不解释为付款实际完成。

**正式合同的目标效果**：来源字段对应变成mechanism、来源/目标对象成为合法端点，查询和保存成为不同Operation。若DDL未提供，“数据库已强制唯一/外键”继续未知；不妨碍有依据的字段交接。

### 采购来源明细

1. 选择清单包括页面选择回调、提交和后端保存/XML，共17个完整单元；EXTRACT实际54,524字节，REVIEW59,292字节。原文不是截取的业务摘要。
2. 页面完整回调中info.linkId=info.id；该明细进入rows，saveDetials读取linkId，XML batchInsert写jsh_depot_item.link_id。
3. 源订单头number经linkNumber→link_number保存是另一条联系。实际REVIEW拆开了主表与明细对象，不把两者当一类身份。
4. 原提取把operations.target写成组合说明，第四次原文REVIEW改成合法对象键；Java按结构字段组装，不读中文猜目标。

**当前边界**：旧PoC完整回调来自同R0实验补充，不能改称旧正式R4已完整保存。实际客户R1的完整范围、standalone保存调用及跨mixin源码owner修复已经直接验证；新R1/R4及新O0正式保存、公共查询通过。当前采购真实O1对象/操作已审，O2关系因最终scope错误被拒；O3未发布拒绝稿的关系。实际完整回调和保存端原文已入模，材料验证与模型判断分开。没有删除回调、放宽唯一性/范围校验或增加解释器。旧PoC金额定义仅为局部计算、维度为空；全生命周期分批量、状态回写和经营指标未由旧PoC核验。

两个题的O1不是同一个对象，两个S1不是同一原文。正式F在有依据时做跨题对应，不根据名称相似自动融合。

## 4. 哪些backlog一起设计，哪些不借机扩大

| 台账 | 本设计处理 | 关闭依据 |
| --- | --- | --- |
| 26 紧凑投影/真实成本/阅读状态 | B及G | 正式请求源码/用途/限制完整，已选未读范围不丢，成本准确 |
| 27 前端选择/保存单元 | C | 正式前端发布→R4→consumer可反查；实验补读不算关闭 |
| 28 弱模型材料硬约束 | 全部环节 | 长期约束，不因某模型成功关闭；材料质量与模型质量分开 |
| 29 Prompt/Skill透明工作流 | E/G | 改Prompt不改证据，真实全过程可查，其他宿主依共享说明可用 |
| 30 可选DDL结构声明证据 | D | 有限定来源/工具/覆盖与原文，不伪造全dump成功 |
| 31 证据接口早期短号/作用域 | B/F/G | 可逆唯一映射、跨页稳定、跨包/版本不串用，不重写R4 |

旧9项是旧Activity/过程长编号，不能把本体短号设计写成旧分支也完成。16–19源码准备等已完成范围不重做；JDT/Lombok/外部边界的技术剩余限制只继承，不把本体实施扩成技术取证全量重验。跨版本增量复用19.2继续延期。

## 5. 验收、门禁与可行性推导

可行性来自已验证的短链：确定选择清单→Java零模型取完整原文/短号→小输入EXTRACT→同源实际REVIEW→程序端点/ref检查及局部组装。源、Provider、canonical store已有接口，正式接线是已核实差距，不需要发明Maven/JS/SQL解释器或新的执行平台。

正式验收按三层记录：框架/材料，模型/业务，覆盖/可移植性。先scripted和真实证据fixture保证框架正确，再有限真实任务观察模型表现。若必要完整单元太大、正式前端来源缺失、来源不符或无法保存，属于框架/材料未达标；若完整材料入模而解释错，属于模型结果，需要记录而非无限造系统修幻觉。

旧计划G1曾要求自主选材通过才正式化，G2要求独立自动发现再次通过才交付。**这些不再作为“材料准备框架可行”的门禁**。自动选材/无提示覆盖仍单列未通过，不改历史记录；今后若用户要求自主全仓质量，则另定具名覆盖验收。允许先交付来源完整且局部可用的框架：问题模式QUESTION/DISCOVERY与选择来源EXPLICIT/MODEL分别配置，不是三种互斥模式；不能称已自主发现全系统。

期间统计已通过正式ANALYTIC/REVIEW和O3保存3维度、6组成度量、3净额指标；对象粒度和权限具体未知仍单列，指标为NOT_EXECUTABLE。不能将已有定义当作已执行或经营KPI已验证，也不保证同一模型一定答对。

### 不增加的工作

没有新的技术全量扫描、数据库实例/SQL执行、跨版本依赖复用、通用JS求值、DDL恢复器、向量/图平台、中文规则证明、动态本体运行时或Foundry部署。新Evidence/Prompt指纹只用现有保存底座，不设计审批服务器或全局恢复框架。

### 已批准的设计选择与尚需授权的执行

已批准：可选schema-evidence存O0，不加必经R5；短号放证据读取接口并保留包作用域；结构错误草稿允许原定一次REVIEW；语义Prompt/沟通模板可改而程序合同不改。新前端/R4生产修复属于前序计划。本轮使用已保存结果，不重跑取证。用户已授权三个具名样例及同范围明确程序缺陷的修复/再验证，采用有限运行配置、不无限重试；不能恢复已被用户撤回的旧PoC累计额度门禁，也不能扩大全仓、来源或改模型/账户。

## 6. 当前设计—实现—直接测试对应

这是限定工程验收的对应表，不是客户模型结果。下表“通过”仅指已记录的直接测试与限定审查；实际命令、产物和验收边界由[正式验收记录](../../supplements/ontology-formal-acceptance-20261003.md)维护。

| 设计职责 | 当前生产接线 | 直接测试 | 当前结论 |
| --- | --- | --- | --- |
| 独立配置与请求/输出 | `OntologyConfiguration`、`AnalysisRunRequest.OntologyInputs`、`OntologyRunOutput`及保存codec | `OntologyConfigurationTest`、`OntologyRunRequestOutputV10Test`，直接历史technical wire测试 | 配置与wire限定通过；实际scope/selection/receipt准入仍随正式runtime验收 |
| 紧凑原文、E/U/K/S及成本 | `OntologyEvidenceCorpus`、`OntologyReadingPacket`、`OntologyModelProjection` | 材料13项＋typed8项直接回归、有限独立审查及实际三例重测 | 第2步有限完成：CT/CO字典无损保留逐位置信息，实际投影参与复用身份；不冒称任意小窗口或真实模型质量 |
| 有限前端上下文及完整源码 | 现有Vue helper、`FrontendHttpDiscoverer`、前端v3发布、R4-v2组装及Corpus消费 | frontend/store/Corpus及technical runtime直接测试和17项Node测试 | 第3步限定通过；客户新R1/R4/O0公共查询通过，实际采购完整回调/保存端入模，模型关系scope错误单列 |
| 阅读状态、查询和冻结 | `OntologyScopeReader`、`OntologySelectionReader`、`OntologyReadingCoordinator`及私有保存 | 三个`OntologyFormalReading*`测试类及正式runtime | 第4步限定通过；DISCOVERY/QUESTION＋MODEL scripted运行通过，不代表真实自主选材 |
| 类型化候选及一次实际审阅 | `OntologyTypedTaskRunner`、validator和formal私有记录 | `OntologyFormalTypedTaskContractsTest`、`OntologyFormalTypedReviewRegressionsTest`及直接历史runner/store测试 | 第5步31项限定通过；不是客户业务质量证明 |
| 身份对应、来源和局部组装 | `OntologyScopedAssembler.assembleFormal`及source-index投影 | `OntologyFormalScopedAssemblyContractsTest`、`OntologyFormalPureAssemblyFix1ContractsTest`及直接历史assembler测试，另有正式runtime11项 | 第6步限定工程通过：纯组装16项及显式runtime多选发布、实际审阅/来源闭合均已核验；实际O3部分发布四文件与九项处置；不保证语义零错误 |
| 四操作、真实安装、查询及额度 | `OntologyAnalysisConfiguredRuntime`、现有Agent/registry/canonical store、本体query keys | 正式runtime21项、technical runtime10项及SDK6项 | 第7步工程边界通过；Agent/store真实，外部模型使用scripted，不冒称客户模型质量 |
| 可选DDL与宿主Skill | 明确同R0/schemaSources，现有JSqlParser及同一个Skill的本体reference | 正式runtime21项全部通过，含真实R0/SQL fixture的DDL保存、消费及来源闭合；Skill有限前向检查 | 有限DDL/Skill及第8步最终质量、保护核对通过；不宣称固定客户dump全部解析或任意宿主实测 |
| 三个局部样例 | 固定新R4/R0→正式O0→显式O1/O2→O3 | 客户源码及已授权Luna/high真实18请求 | 六个O1已审；财务/统计关系已审，采购关系因scope错误拒绝；四文件部分发布，覆盖九义务八已审一拒绝。证据核验通过，模型残余错误/未知不补写 |
| 稳定证据、局部隔离、依赖与有限结束 | H的现有runtime/Provider/store接线 | `OntologyFormalRuntimeContractsTest`限定新增selectors；Provider叶层13项直接测试 | 正式提取/审阅、依赖、Provider边界、阅读坏响应局部隔离、完整处置查询、O3多上游及冲突不安装已定向通过；辅助原文/未确认限制实际入模和有界超限响应私有保存也已通过。v4、安全下一动作、具体组装诊断及独立owner的相同结果值比较均有直接GREEN；O2上限报告及三例18份实际请求/四文件已核对；最后两项边界20项GREEN、最终质量6695通过；实现PR正在交付 |

旧MD分析/Prompt/Schema/渲染/历史请求与结果不属于这些新生产职责。共享接口只加本体分支，最终需直接隔离回归及原保护清单核对；不能凭代码文件存在关闭backlog或把有限fixture称为全仓本体识别。
