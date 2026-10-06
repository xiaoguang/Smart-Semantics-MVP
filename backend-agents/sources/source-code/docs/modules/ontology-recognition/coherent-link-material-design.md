# 连贯紧凑材料与对象—联系联合识别：详细设计

状态：**设计完成，正式接线实施中。** 本文是业务联系优先路径的下一版行为 owner；替换骨架阶段的“先单独 OBJECT，冻结对象目录后再单独 RELATE”要求。不改写已有结果，也不声称薄实验已经通过正式 CLI、完整采购链或小窗口验收。

## 1. 要解决的具体问题与达标含义

用户首先需要看清系统中的业务对象和具体联系，再按需要认识操作、规则、维度、度量和指标。不能先花大量成本解释局部规则，却始终没有业务总览。

现有正式任务把这两个判断拆开：OBJECT先决定对象目录，RELATE后来读新的实现，但只能使用旧目录。正式采购问题中的联系草稿涉及请购单；对象目录却只有泛化单据，REVIEW最终因缺少请购端点删除联系。增加相同材料的重复阅读，不能消除这个合同矛盾。

新的最小工作单元是：**围绕一个真实技术线索，Java准备连贯原文，模型在同一次提取及同原文审阅中定义端点对象和这条联系。** 身份唯一性、全部状态规则和指标未调查，不阻断有依据的联系；不存在的端点仍不能发布。

“确保设计达标”约束的是框架可控制的行为，不是保证任何模型都答对：

| 必须满足的目标 | 通过的实际依据 | 不能替代它的说法 |
| --- | --- | --- |
| 连贯 | 选定技术锚点、完整原文、页面实例、保存映射和已取得使用端能在同一包反查；未取得部分逐项可见 | 文件在R4里存在，因此模型必然看见 |
| 紧凑 | 相同选材的正文/用途/限制不变，机械表达减少；完整EXTRACT/REVIEW成本满足本次声明的配置 | 少读一端后包变小；字节减少就是兼容8k |
| 可组装 | 已审局部对象和联系同任务闭合；跨任务身份必须显式审阅 | 同名对象自动合并；根据参考图补箭头 |
| 可结束 | 非法输出仅拒绝当前任务，完整处置可保存；独立任务继续；共享故障有界退出 | 无限让模型修到正确；失败任务保持RUNNING |
| 可泛化 | 材料算法和Prompt不含客户业务答案；改名/非ERP/假关联反例通过 | 管伊佳一个样例通过就承诺所有项目质量相同 |

框架通过、模型识别质量通过、容量通过分别记录。缺失材料必须修；已提交材料仍被模型遗漏或误读，保存原结果及来源，不无限新增修稿系统。完整采购链必须由实际正式产物另验，不能由单条联系推得。

## 2. 已有事实，不把实验偷换成生产

准确证据见[薄实验记录](../../supplements/source-link-envelope-thin-probe-20261005.md)；已有正式失败/遗漏见[正式验收](../../supplements/ontology-business-link-formal-acceptance-20261005.md)。

| 项目 | 实测事实 | 结论边界 |
| --- | --- | --- |
| 起点 | 从正式被删关系的实际sourceBinding取得标识，核验O0、R4与来源；Java扩展原文 | 针对当前失败的恢复实验，不是从零全仓发现 |
| 选材 | 12个直接用途，展开20个完整单元；没有提供采购链答案或手填源码路径清单 | Java组织完成；其他技术候选没有全部入模 |
| 同材料压缩 | 224,186→119,695字节，减少46.61%；选中正文、用途及投影解码核验通过 | 早期331,036→119,695同时改变选材，不能算全部无损压缩收益 |
| 模型请求 | Luna/high一次EXTRACT和一次REVIEW；完整请求122,215/123,058字节 | 约119.35/120.17KiB；不是任意弱模型或小窗口证明 |
| 结果 | 审阅得到采购订单和请购单，以及订单用linkApply引用请购单编号的联系 | 没有证明全生命周期、单号唯一性、基数或完整修改保存路径 |
| 原文限制 | 页面建立、insertSelective映射、已读使用端可核对；状态helper未读且原导航冲突保留 | 不能因此确认状态更新规则或运行时因果链 |

该薄实验使用联合响应，不是当前正式typed-v4的OBJECT→RELATE协议。实施必须接通下面的联合任务，不能只把实验Prompt塞进旧OBJECT而继续沿用旧端点限制。

## 3. 范围与复用

继续使用固定R4与其真实上游R0、已有Corpus倒排/字面搜索、完整单元读取、页面上下文反查、冻结、Provider、一次REVIEW、失败隔离、canonical store和业务图渲染。保留四个CLI与公共Agent方法。

不修改R0/R4生产或历史文件，不重跑Maven/JDT/Vue/SQL取证。旧MD、Activity、过程、九章的分析、Prompt、Schema、渲染和请求指纹保持原样。可选DDL仍走已有O0能力，本修订不新增DDL任务或R5。

不新增Java/JavaScript/SQL解释器、语义数据流求解器、全仓相似度矩阵、向量/图数据库、自动摘要器、中文事实证明器或无限修稿器。只投影已有结构和执行已有技术反查；字面命中不冒充字段绑定。

## 4. 四命令如何使用新路径

```text
R4＋同源R0
  ↓ prepare-ontology
O0：技术导航、完整单元及准确来源，零模型
  ↓ identify-ontology：SKELETON / LINK
O1：一个锚点 → Java连贯包 → 联合对象/联系EXTRACT → 同包REVIEW
  ├─────────────→ publish-ontology：可以先发布局部业务图
  ↓ relate-ontology：需要跨任务合并或另查联系时才执行
O2：准确已审对象目录 → SAME_OBJECT/变体/新联系及REVIEW
  ↓ publish-ontology
O3：确定性组装、四文件与离线业务图，零模型
  ↓ 如用户需要细化
新O1：明确骨架对象来源 → OBJECT细化、ACTION或ANALYTIC
  ↓ 必要的新O2 → 新O3；旧图保留
```

命令仍是`prepare-ontology / identify-ontology / relate-ontology / publish-ontology`。O不是第五套运行框架；每次操作是独立run，精确引用上游。

**一个联合任务里的端点已闭合，O3不强制制造空O2。** 两个任务都返回“采购订单”时，也不能直接拼成采购链：O2须核对各自的来源、变体、身份与用途，明确SAME_OBJECT后才能连接为同一节点。缺少身份对应时先画两份带来源作用域的对象，并显示未解决对应，而不是自动合并。

旧OBJECT→RELATE仍供原版本读取和明确兼容执行，不再是新SKELETON的默认取材路径。ENRICHMENT不改成每题重新识别整张图。

## 5. 输入与锚点：程序知道什么、不知道什么

### 5.1 唯一事实来源

从真实R4 receipt/header恢复R0、有效范围、后端与持久化owner；严格读取保存版本。O0重开依保存规则恢复，不能用当前客户工作区补值。输入、Prompt、算法版本、用途、限制和模型绑定均进入任务身份。

结构化源码路径相对源码根；原始源码字符串不改写。E/U/K是准确Corpus内稳定别名，S是本包已读原文。调用字典C/CT/CO不是原文引文；任何定义只能引用本次实际S，不以裸K或未读路径代替依据。

scope-v3的LINK中，Question.entryRefs是初始导航种子，不是只能读取这些入口的授权白名单。围绕准确K取得的同Corpus直接用途可涉及其它E/未匹配HTTP的页面上下文，必须保存`derivedEntries`及完整派生依据；每个U/E仍须是Corpus中的合法用途，不能把两端别名随意拼接。Provider前的实际E/S allowlist来自冻结包的初始及派生用途，不只来自最初entryRefs。原scope不改写，派生范围单独保存并参与身份/覆盖。

读取的硬边界始终是当前Corpus的同源R4/R0及有效排除；没有合法E用途的页面上下文采用已有准确context绑定，不能伪造一个HTTP入口。如果用户限定了比该Corpus更窄的来源范围，不能借技术扩展越界；应先使用对应合法Corpus或报告范围不支持。本修订不新增任意跨Corpus补读能力。

### 5.2 锚点来自实际技术项

LINK选择一个实际K作为主要锚点：准确方法/Mapper语句身份、已保存表列、控制表达式或前端关联上下文。展示实际共同项、用途、扇出、来源和限制，不仅给一个重合数字。

普通问题或无提示调查可让模型从导航选择锚点；用户也可显式选K。记录锚点选择来源，不能把显式选择称为自主发现。Java负责选中锚点后的机械扩展，而非批准“这里一定是采购”。

DISCOVERY复用现有SURVEY，不把源码搬入选题请求。新版PRIORITIZE的选中问题新增`linkAnchorRefs[]`：LINK任务时为非空、准确原问题clueRefs的子集；不请求LINK时为空。Java为每个实际选择的K生成一个LINK任务及稳定taskId，任务数/请求预算超过配置时保存未执行处置，不静默缩小原选择。模型不能在优先级响应写对象或流程答案。QUESTION直接用scope-v3声明锚点，不强制再SURVEY/PRIORITIZE。

失败稿的sourceBinding可用于**新的具名恢复任务**：先核验它确实定位到当前Corpus中的原文/技术项，再作为未确认线索。失败稿不是事实，不改变历史失败状态。不能将测试中人工指定的恢复锚点宣称为全仓自主选题成功。

### 5.3 不把薄规则变成解释器

优先使用保存的结构身份。已有字面搜索可查原文中的准确token；字符串相同只标`LEXICAL_MATCH`，不自动批准同一字段或对象。getter只有在已有声明/正文确实支持时才作为候选；不根据命名生成方法或路径。

“产生端、保存端、使用端”是模型需要回答的语义问题。Java的材料角色仅为`ANCHOR_SOURCE / STRUCTURED_REFERENCE / LEXICAL_MATCH / PAGE_CONTEXT`；这些角色不等于Java已经证明标识由谁产生、写入哪个对象。

## 6. Java连贯材料算法

入口为现有Corpus的窄操作：**给定一个合法锚点及当前任务范围，返回候选分组、实际读取选择、冻结材料或有结构化原因的未完成结果。** 复用ReadingPacket/ModelProjection，不增加独立公共服务或导出CLI。

### 6.1 有界展开顺序

实现状态：准确K用途、显式必读、一跳技术分组、完整XML依赖与页面上下文、v6冻结、紧凑F/调用/未读目录及实际成本已经接入正式运行；catalog-v4仅传递选定任务的已审对象和完整审阅身份。54项初始材料门禁、41项准备/容量/发布门禁、24项最终投影与正式运行重开门禁及后续限定修正均有通过记录。最后批量检索及页面解析复用没有改变固定两包的四份输出，quality-12通过。真实运行和模型/容量未通过项见验收记录，机械接线不能代替语义全面达标。

1. 核验锚点属于当前Corpus和允许范围，恢复其准确来源/用途。锚点缺失、类型错误或来源损坏先拒绝，零Provider。
2. 收入锚点实际绑定的完整方法、Mapper语句变体、SQL/XML依赖或前端单元。不是按附近行号截取片段。
3. 通过现有倒排恢复直接关联的声明、语句和用途；有唯一可靠完整边界才读方法，否则明确选完整文件并计成本，不能自行切函数。

   Java调用邻居仅检查初始选定方法所在入口的已保存调用。`LOCATED`且目标正是初始方法时，可以收入其完整调用者；初始调用者直接定位到实际存在、名称符合get/is访问器惯例且保存 RETURN 简单标识表达式的声明时，可以收入该完整声明。惯例名称只限制补读候选，不证明字段绑定或方法纯净。新收入的调用者/声明不继续触发这一规则；普通helper、CANDIDATES、NAVIGATION_CONFLICT均不自动展开。没有根据getter名称生成实现，亦没有更改JDT候选或多态算法。精确的其它必要helper仍须显式选择或保留未读。
4. 对锚点实际标识执行已有精确字面反查，保存完整命中目录。与选定技术项直接关联的前端命中及其页面实例作为本次候选；仅同词而缺少物理/上下文关联的其他命中仍留候选，不全部硬塞正文。
5. 对入选前端单元做现有单跳source→PAGE_CONTEXT反查，附入该上下文实际保存的完整相关单元和观察。不同页面实例分别记录；不递归扩展所有共享组件和全站页面。
6. 固定排序、完整用途去重、容量预检。保存纳入/未纳入分组、具体理由、每组完整单元和增量成本；然后冻结。

技术扩展固定为一跳。需要更远的helper时，用下一条明确技术引用或下一小任务取得；不以递归闭包默认搬入整个入口调用图。已有显式`requiredUnitUses`必须有处置，不能因自动展开成功就清空。

### 6.1.1 保存什么，下一步如何重开

正式交接约束：新 producer-v4 必须在模块发布及步骤安装两层均明确准入，不能只更新 JSON Schema 或模块白名单。新策略文件仍执行 artifactType/schemaVersion 的严格 UTF-8 排序。scope-v3/selection-v3 对已审 LINK 的结果按准确任务身份读取；旧 outcome 家族不接受 LINK。联合 LINK 可不经过独立 O2 直接发布，公共请求允许空 relationPublications，但具体执行仍由 selection-v3 及严格的已审上游恢复核验放行；旧 selection-v1/v2 的非空关系要求不变。

前端自动字面反查以初始锚点/显式选定单元已保存的返回标识或参数为查询；一跳补入的实际访问器只增加其保存的简单RETURN标识，不增加其参数。完整补读的incoming调用者保留正文，但其新增参数只进入未读候选披露，不继续驱动自动选页，避免rows/request等通用词沿派生链扩大材料。只有命中单元具有路径、摘要、范围、单元种类完全匹配的保存 PAGE_CONTEXT 时才收入正文。允许该页面属于另一入口：建立编号的页面请求与后端使用编号的请求本来可能不同。其用途必须标 LEXICAL_MATCH，而非 ANCHOR_SOURCE；不代表字段绑定或业务联系已成立。缺少准确页面上下文的同词命中保持未读目录。上下文所有实际保存的完整来源单元按现有规则一起读取，不递归展开全站。此规则没有证明其它入口属于锚点的 clueUses，不改写原有绑定。

扩展决定保存在现有私有冻结包v6，不新增公共证据文件。必需的`bundleDecision`字段为：`ruleVersion / anchorRef / selectionOrigin / seedUses / derivedUses / derivedEntries / groups / requiredButUnread / unreadCandidates / cost`。derivedEntries是实际新增E的稳定去重列表；页面context沿原准确绑定保存，不制造E。selectionOrigin记录锚点的EXPLICIT/MODEL来源；各派生组另记技术规则依据，不能把它们假装成模型阅读动作。

groups每项固定为`groupRef / matchKind / technicalRefs / unitUses / pageInstanceRef / outcome / issueCode / unitBytes / projectedIncrementBytes`：groupRef是当前任务稳定G键，matchKind是§5.3的技术角色，unitUses为准确U/E用途，pageInstanceRef可空；正式v6的outcome闭合为`INCLUDED / CAPACITY_BLOCKED / UNAVAILABLE`。仅字面命中的未读候选另在unreadCandidates保存，不制造已读组或引文。未纳入组不进入readHistory或evidenceRefs；来源损坏仍按共享故障停止，不能降级成普通UNAVAILABLE继续。

组按完整来源/用途身份确定排序，G不是原文引文。正式展开不允许容量不足时自动删组；必须返回容量处置，新的明确选择需创建新任务。私有字段带真实身份，模型只看到必要的短键/用途/未知投影。重开核对组决定、真实正文、规则版本及映射；不重新用新算法作一遍选材后冒充旧冻结包。

### 6.2 多实例与高扇出

冻结结果持有不可变OntologyReadingPacket引用，不增加复制整包的服务层。材料包只暴露不可变字节、不可修改的单元列表及重新解析的独立JSON；直接修改返回JSON、字节副本或列表不能改变已冻结正文、决定或模型输入。该变异测试通过后，SpotBugs对Result.packet产生的两项表示暴露告警仅在这个字段/访问器精确排除；不排除整个类型或其它可变引用。

同一共享回调在不同页面出现时，按保存的页面实例、参数、条件和来源分别成组；正文可以共用，用途不能共用。两个实例都满足直接技术匹配且能容纳时均保留，不能按入口编号取第一个。

只含字面重合的候选不自动变成核心材料。歧义明确留在未读目录；本次TECHNICAL_BUNDLE不再追加材料选择模型调用，仍只派发一次EXTRACT和一次REVIEW。确需另一组可明确选择为新锚点任务或显式unitUses（包内登记为seedUses），记录新选择及来源；不能在LINK中伪装成OBJECT调用旧阅读协议，也不静默升级为多轮ReAct。原有OBJECT/细化的MODEL阅读路径按原协议保留。

直接组的完整实现超限时，返回具体`LINK_BUNDLE_TOO_LARGE`及各组成本/未执行范围，不按maxItems静默截掉尾部。只有可独立阅读的不同页面用途/不同锚点才能拆成多个完整任务；同一联系的必要两端不能拆成互不见面的两次提取后靠Java猜结论。

### 6.3 XML/SQL范围

已保存的完整statement及其include/resultMap依赖可以作为单元，不默认同时附整份XML的重复副本。若明确选的是完整文件，冻结后不得偷换为statement来缩小输入；先改变选择并形成新材料身份。

保留动态if/foreach、SQL结构、原文和PARTIAL理由。不执行MyBatis参数展开或SQL，不以一次静态副本冒充所有请求的最终SQL。Mapper映射与参数关系只按已保存结果表达。

### 6.4 何谓“完整”

Java可证明：已选单元原文逐字一致、边界可靠、用途/参数/限制不丢、已声明必需项均有处置。Java不能证明：没有任何尚未发现的业务必要实现。

包明确包含`unreadCandidates`与`requiredButUnread`；未读helper不能被模型解释为“不抛异常”或“总会更新状态”。模型若认为联系需要未读实现，返回具体缺项；本次可保留局部联系与未知，不无限追读规则。人审发现漏交已声明必要原文，归框架缺陷。

v6 的未读目录在私有 bundleDecision 保持完整逐项对象。LINK不再执行导航选材，EXTRACT/REVIEW只能引用已读S原文，因此不将成千上万未读U/E导航项当作事实材料发送。模型侧使用 `PRIVATE_UNREAD_DIRECTORY_SUMMARY_V1`：按准确kind、matchKind、reason及有序查询值汇总数量，同时保存完整目录的摘要及总数。没有语义排序或“业务无关”判断，只区分已读正文与未读导航。所有逐项U/E用途仍可在保存任务中查询；必需未读项`requiredButUnread`仍完整入模，不作汇总。解码必须配合该任务的私有完整目录，核验摘要、各组和总数后恢复完整原序列；不允许替换另一份数量相同的目录。

前端上下文观察在v6采用`EXACT_FRONTEND_OBSERVATIONS_V1`：完整观察的全部字段相等时可共用F行；每个PAGE_CONTEXT仍保留自己的instanceKey、页面、入口用途、条件、限制及有序F引用。实参、形参、绑定处置、from/to源码引用或位置不同，均为不同行。不以“同组件”或“同事件名”合并。F仅是元数据字典编号，不是引文；事实仍引用对应S正文。解码恢复每个上下文的完整有序观察，未知引用、重复定义或无用途行拒绝。固定K2072包的153次观察只有31个精确不同值，这是表达共享的依据，不是业务联系证据。

固定R4的同选材审计测得：K1342完整源码38,951字节，原模型投影799,724字节，最终投影188,260字节；3,104项完整未读目录仍保存私有。K2072完整源码107,387字节，最终投影491,501字节，比中间逐项字典表达651,002字节减少；5,648项未读目录仍保存私有。两包解码后的正文、调用行、调用用途、调用限制、入口上下文和必需未读项摘要均与原投影一致，完整未读目录按自己的摘要恢复。这些数值仅为材料投影，不是含Prompt、Schema及实际草稿的完整EXTRACT/REVIEW封套；也不是“小窗口通过”。第二包仍偏大，容量限制须单列，不能以少选正文或丢弃私有范围冒充收益。

## 7. 紧凑表达及可逆边界

### 7.1 模型收到什么

字面检索使用请求内批量遍历：每个阶段一次解析每份已存入口、一次取得每个单元的可检索文本，再为该阶段每个精确字面词独立累计命中。保持原navigation→单元种类→单元顺序、首次命中、±80字符摘录、总数及分页；查询消费顺序保持不变。自动选页和最终未读披露仍是两个先后阶段，后者词集依赖前者结果，不能提前合并。空词集不扫描。此优化不新增索引、跨请求缓存、解释器或业务裁决，不改变源码选择和版本；需直接等价测试及同源冻结包字节核验。

页面完整单元匹配仍按已保存物理身份执行。在一次`frontendContextSources`调用内，可以惰性复用已经解析的同一单元，不能跨调用存放可变JsonNode。首次解析必须留在原内层遍历位置：空sourceUnits、非法首项、早期缺失/冲突的失败顺序不变。单元、匹配和输出顺序、完整正文/摘要/范围检查不变；不能用文件名或单元编号代替物理身份核验。用直接解析次数及失败优先级测试核验，不用不稳定的墙钟时间代替语义门禁。

只发送当前问题、真实锚点及具体用途、完整入选原文、必要调用/参数、页面实例、已知限制、未读范围和固定小型响应Schema。LINK不携带此前全题累计对象目录。

程序侧保留R0/R4 receipt、长ID、源摘要、AST便利字段、完整技术记录和可逆映射；模型不靠它们判断业务。不送重复的调用树和调用明细，不送完整entry JSON。

### 7.2 正文与用途分开

精确相同正文按内容字节及完整边界核验后存一次；`sourceUses`保存每个E/页面实例、条件、实参及完整来源。不同分支、同词不同字段、不同SQL变体或页面用途不可因正文相同而合并。解码必须恢复每份选中单元及有序用途。

完整单元内的嵌套回调和其独立已选方法可有正文重叠；只有可靠边界/来源关系且解码逐字恢复时允许引用共用正文。否则保留原重叠，不自写语法切分器追求更小数字。

### 7.3 调用分两种投影，原记录不删除

- 与当前锚点直接关联或显式选择的调用，保留实际参数、目标、参数对应、角色、位置、状态、展开观察及同入口已选目标。
- 其它入选方法中的调用保留紧凑位置、状态、完整候选/观察字典引用及用途；不默认附入全部非必要便利字段和callee正文。查询失败、冲突、未知不能因“不属于锚点”而消失。

两种投影的选择理由保存。后者没有提供的参数/正文不得被模型当作已读；完整私有调用可反查。解码核验针对**明确选中的模型投影**，不能谎称所有原始技术字段都在模型请求中。

沿用callRows/callUses、CT/CO字典，固定列携带列名与编码版本。实参、候选、状态或展开结果不同不共享公共行。页面观察/请求元数据也只能按canonical字节完全相等共享，实例和入口关联仍放用途表。模型更容易阅读的短字段名可用，但必须有单一版本化说明，不形成隐式代码表。

### 7.4 每次比较都使用同一选材

成本对照保存源清单、展开后的清单、正文/用途摘要、旧新投影大小、完整请求大小。少选材料与表达压缩分开报告。离线反例改变一个参数、候选、限制或页面实例必须使核验失败；不是仅检查总行数一致。

## 8. 容量：不靠高端模型掩盖问题

### 8.1 完整请求的三次检查

冻结前计算材料及候选选择成本；EXTRACT前计算实际Prompt、Schema、材料和配置的输出预留；REVIEW前计算同一材料、真实草稿、诊断、审阅Prompt/Schema及输出预留。提取前还检查最大允许草稿的REVIEW预留，防止已知必然容纳不下仍派发。

记录`sourceBytes / projectionBytes / extractEnvelopeBytes / reviewEnvelopeBytes / reservedReviewEnvelopeBytes`及具体计量口径。token计数只能使用适配器支持的准确计数或明确标注的估算；未知写未知。Prompt和Provider协议额外开销参与实际容量记录，字节不是token。

新v6的maxUnitBytes检查完整入选正文UTF-8字节，maxRequestBytes检查最终完整模型封套；私有映射/技术记录另按现有存储资源限制检查。不能把未入模的AST便利字段算成模型单元大小，也不能因投影小就绕过私有存储限制。旧v3/v4/v5的原单元门禁保持原规则，配置原值和新规则版本均保存，不能改变旧任务身份。

正文计量按真实保存字段：Java使用`source.text`，前端使用`text`，已补读的SOURCE_REFERENCE使用顶层`sourceText`；`preparedBody`只保存边界种类及范围，不含正文。完整源码补读不得被计为零字节。

本修订不新增任意70KB门槛，不提高当前上限或换强模型。已有约120KiB实验仍是待压缩/测量的容量事实，不能按“九成置信度”宣布小窗口达标。

### 8.2 支持范围必须具体

正式验收开始前固定一个目标模型/上下文配置、输出预留和计量方法；未选具体配置时，可以完成机械材料验收，但容量结论只能是`NOT_VERIFIED`。

框架不承诺任意4k/8k窗口都能读任意完整方法。若完整必要单元本身无法容纳，明确报告能力边界，允许用户显式收窄问题或更改配置；不能截断原文后仍标完整。是否支持一个更小窗口是独立验收，不是本次算法的默认成功条件。

## 9. LINK：对象和联系一起返回

### 9.1 新任务与最小响应

在现有O1任务枚举增加`LINK`，仅用于SKELETON；不是第五个CLI。使用独立`ontology-link-candidate-v1 / ontology-link-review-v1`响应家族，避免让旧OBJECT Schema偷偷接受Link。

响应闭合根字段固定为：

```text
schemaVersion, taskKind="LINK",
objects[], links[], clueDispositions[], unresolved[], corrections[]
```

对象使用本任务内精确唯一的`objectKey`，可以是简短业务名称/模型自选短键，不要求抄完整内部ID。对象项必需：`objectKey / name / definition / displayRole / certainty / scope / backing / variants / evidenceRefs / unknowns`。backing、variants、scope和unknowns复用已有结构化子Schema，不用一段“identity字符串”替代身份定义。scope.questionRef必须为本题；entryUseRefs只能引用本次实际入模用途，variants描述该项适用条件，不能因整个包含另一页面就自动扩大该对象范围。

联系项必需：`fromKey / toKey / name / definition / certainty / scope / mechanism / conditions / cardinality / evidenceRefs / unknowns`，沿用现有机制、条件、基数和未知子结构。两端必须精确匹配本次最终objects里的key；不得模糊匹配名称或引用旧B目录。没有可定义端点时放unresolved，不画悬空箭头。

复用子结构不等于沿用旧编号作用域：LINK中的semanticItem.targetObjectRefs及expression.bindings.definitionRef只允许本次objectKey或Schema允许的空值；propertyRef必须为空，因为本任务没有声明属性。Java只在这些Schema明确列出的引用字段做key→O映射，不替换描述、表达式或源码字符串。evidenceRefs只能是实际S；unknowns.missingUnitRefs只准本任务已经展示的未读U，缺少句柄时用具体reason和空数组，不编造编号。全部嵌套引用在最终集合上再核验。

objects/links均可为空，明确处置即可；不得为凑一条联系编造第二对象。clueDispositions每项闭合字段为`clueRef / outcome / linkIndexes / reason`：对实际展示K返回`LINK_SUPPORTED / NOT_A_BUSINESS_LINK / NEEDS_MORE_MATERIAL`，支持项用非空linkIndexes引用本次最终links的零起始数组序号，程序检查范围；其它两类数组为空且reason非空。未回答K记MODEL_NOT_ADDRESSED，不替模型批准。

unresolved复用`field / reason / missingUnitRefs`未知结构。EXTRACT的corrections为空；REVIEW的corrections仅为说明字符串数组，不参与业务组装或引用。Java另按真实原稿/最终JSON计算结构差异，保留原数组位置和key的变化；不把模型自述“已纠正”当修复事实，也不从中文corrections推定义。

这里只识别骨架，不要求模型填写identities/properties/operations/metrics。不在LINK任务顺带解释所有规则；variants的业务类型条件不等于唯一身份。例如按subType区分两种业务单据，可支持两个对象角色，但不能证明单号唯一或所有租户共享一个身份。

### 9.2 Java编号与确定性投影

当前实现状态：scope/selection-v3、联合两稿、确定性编号、材料冻结、私有保存、准确taskIds、显式同一对象对应及O3四文件已经接通。真实新结果包含采购三对象的来源编号骨架，另有财务/统计细化；9项已审、2项拒绝保留，最终PARTIAL。实际来源、完整请求成本、原误引和统计瑕疵在验收记录分开维护，不宣称小窗口、全仓或完整采购生命周期通过。

原始EXTRACT/REVIEW响应逐字保存。REVIEW最终合法后，Java按objectKey的确定字节排序分配局部O号；联系按完整结构的确定排序分配L号，保存原数组序号→L的映射。重复objectKey拒绝；业务名称不同、不完整键或错拼不能由Java语义校正。

投影是固定字段映射，不是“把中文转成正式本体”：

| LINK结果 | 当前本体定义中的落点 |
| --- | --- |
| objectKey | 本任务局部映射；全局身份仍绑定Corpus/任务/审阅版本 |
| name/definition/displayRole/certainty/backing/variants/refs/unknowns | 原字段原值保留并映射来源 |
| 未请求identity/properties | identities/properties为空；PARTIAL，并明确“本骨架任务未调查”，不宣称不存在 |
| fromKey/toKey | 经本次最终映射生成fromObjectRef/toObjectRef |
| mechanism/conditions/cardinality/refs/unknowns | 原结构保留，不推断基数或规则 |
| scope/origin | scope按模型返回的合法范围原样保留；origin固定为本任务IMPLEMENTATION，不由名称推导 |
| K处置的数组序号 | 仅按保存映射转换L引用；坏序号拒绝 |

转换结果保存并参与任务身份/重开核验；不能只保存修好的定义而丢原稿。旧v1实验文本不自动转换为正式新结果。

### 9.3 一次REVIEW与失败隔离

REVIEW接收同一冻结包、实际草稿、结构诊断和联合Schema。明确要求：定义最终对象集合，全部联系端点必须存在；如果删对象，要同步处置其联系；保留原文条件和未知，不为消除错误而猜一个端点。

可读有界坏稿允许进入原定一次REVIEW。最终仍坏，只拒绝当前LINK，保存两稿及具体问题，无第三次自动修稿。非JSON、来源损坏、容量不足、保存失败和远端状态未知仍按已有失败隔离边界处理，不用REVIEW掩盖。

整个LINK任务原子接受/拒绝；不能从被拒审阅稿捞出“看起来正确的对象”。独立任务继续；依赖此对象来源的任务标DEPENDENCY_NOT_REVIEWED、零派发。原R4、原文、候选及冻结包不被模型结果修改。

## 10. 跨任务身份、细化及发布

O1成功LINK可以提供对象目录。新objectSources明确`identificationRun / questionId / taskIds[]`；只恢复这些准确任务中已审的OBJECT或LINK，不取该run所有题的累计对象。空、重复、未声明、未审任务拒绝或按已有依赖规则未执行；自引用、循环和错Corpus在Provider前拒绝。

该精确选择在 O2 准备和 O3 重开 O2 时采用同一规则：v3 必须命中声明的 taskIds，不能因同题还有 OBJECT 而纳入它，也不能拒绝已选 LINK。历史 v2 的无 taskIds 来源保持仅 OBJECT 的原规则。selection-v3 的 RELATE 必须使用 identification/relations producer-v4；PUBLISH 必须使用 coverage/review producer-v4，拒绝旧策略或混合发布家族。

O2读取选中对象的backing、variants、适用范围、来源及未知。名称相同、同表或同Controller不能自动SAME_OBJECT。一个表承载请购和订单的不同条件，不强制合并成一个泛化单据；两个任务涉及同一订单角色，也须显式核对对应。

执行已审 SAME_OBJECT 时，canonical 选择与全局对象查找均使用完整 `Corpus / producingTaskId / reviewVersion / localId`。同一任务的两份合法审阅不能仅按 `producingTaskId / localId` 合并或覆盖。图只呈现该完整身份决定，不以最新审阅覆盖明确选择。

身份判断必要原文超过容量时保留UNRESOLVED，不用Java名词匹配补成功。未经统一的对象在图上仍各自保留，附所属任务/角色及未解决对应；不得产生肉眼看似连续、实际身份未核实的链。

ENRICHMENT继续使用现有OBJECT/ACTION/ANALYTIC及一次审阅；身份、操作、条件、度量粒度/公式/过滤逐项填充，不要求LINK阶段先填满。外部已审对象目录必须小而准确，材料组织规则与骨架相同。

O3显式选择O1/O2全集及它们的真实对象上游闭包。允许relationRuns为空，不制造假O2；缺实际依赖拒绝，不自动补入。发布仅包含完整已审任务，继承全部失败/未处理义务，不缩小分母。

最终仍为ontology.json、ontology-coverage.json、ontology-sources.jsonl、ontology-review.json。ontology-v2定义形状保持；producer/coverage/review升级以准确表达LINK同时承担OBJECT和RELATION。一个任务不算两次模型请求，但在两个层的声明范围中都有去向。未请求ACTION/ANALYTIC保持NOT_REQUESTED。

业务图复用Mermaid离线渲染：业务对象方框、实际连接机制标签、未知可展开；箭头采用已审定义的引用/影响方向，不自动改成办理时间顺序。支撑/孤立对象不丢；未请求层显示“未调查”。所有文本继续按不可信数据转义。

## 11. Prompt与宿主Agent

LINK EXTRACT的通用要求是：“阅读一个技术锚点及其实际上下文，识别被区别使用的业务对象；说明是否存在标识、记录、状态或数量的交接，怎样建立和使用。共同技术项不足时拒绝关系；只解释已读实现。” 不列行业链模板、客户字段、预期对象数或参考图答案。

LINK REVIEW要求核对最终对象集合/全部端点、类型条件与唯一身份的区别、来源及未知；用原文核查机制，不以流程常识补齐。ACTION/ANALYTIC仍分别核对条件、效果、公式、粒度和过滤，不在联合任务里强制生成。

两个通用Prompt都要求：不要因为实现共用同一Java实体或数据库表，就只留下一个无法区分用途的泛化对象；原文确实区别对待时，保留有来源的角色/变体条件，并说明是否需要不同业务对象。反之，不为画链条强拆同一个对象。这个判断仍由模型完成；泛化标签导致无法看清联系时属于实际语义验收未通过，Java不能据分支字符串自动命名对象。

联合LINK没有属性目录，表达式绑定的`propertyRef`必须为null；原文字段名保存在表达式文本，不能假借字段名作为未定义的属性编号。LINK候选及审阅Schema与最终校验采用同一规则；其他类型化任务已有属性目录时的引用不受此限制。`missingUnitRefs`只接受模型实际看到的未读U候选，不能填写已提交S正文或私有目录编号；无可引用U时保留空数组并用具体文本说明缺项。此处是机械合同对齐，不是程序补全业务含义。

用户可以替换业务Prompt和宿主沟通文本；不能通过Prompt改变Schema、映射、来源、投影和容量。实际文本保存并参与任务匹配。Java准备材料不调用模型；框架Provider负责产品模型调用，宿主Agent不手工添源码或业务答案。

Skill在既定授权范围调用四命令、读取真实JSON和已安装产物，交付实际图及缺口。非零退出也查询报告；局部模型拒绝不反复询问，不无限重试。选择需要扩大业务/来源范围或改变能力边界时才讨论。整个工作不依赖Codex隐含记忆、界面或OpenAI专用工具；现有Provider支持范围如实声明。

## 12. 版本与正式接线清单

下表明确旧合同与本轮联合LINK合同；生产、精确读取、安装和查询已有直接测试，真实业务图、材料对照及有限验收已有保存结果。容量与模型质量仍有未通过项，见验收记录。历史精确读取不变，字段缺失不能猜版本；新执行不自动复用旧失败或不匹配的旧审阅任务。

| 合同 | 旧合同 | 联合LINK合同及原因 |
| --- | --- | --- |
| Corpus/O0 producer/投影规则 | corpus-v2 / producer-v2 / projection-v3 | 保持；锚点使用已有K/倒排/原文，不改变保存Corpus形状或别名。新增link-bundle-rule-v1在v6包/任务中绑定 |
| scope | scope-v2 | scope-v3，SKELETON允许LINK及OBJECT；LINK为TECHNICAL_BUNDLE阅读，task新增anchorRefs，恰一个真实K；其他task该数组为空 |
| 外部对象来源与selection | scope-v2对象来源、selection-v2 | scope-v3及selection-v3的objectSources必需taskIds，准确选择OBJECT/LINK；PUBLISH仍显式完整run集合 |
| 冻结包/模型投影 | reading-packet/model-reading-v5 | 各v6，保存展开决定、全文用途及新紧凑投影，编码名EXACT_LINK_BUNDLE_V1 |
| 调查/优先级/决策 | survey-input/response-v3、prioritize-input/response-v4、decision-result-v5 | SURVEY保持；PRIORITIZE input/response-v5增加LINK/准确linkAnchorRefs；decision-result-v6保存新实际scope，不自动改写旧记录 |
| 原有有界阅读 | input/response-v4、state-v2 | 保持；TECHNICAL_BUNDLE不派发阅读模型，不伪造QUERY/READ；原OBJECT/细化MODEL选择仍按准确原协议保存 |
| 联合模型响应 | 不存在 | ontology-link-candidate/review-v1；旧typed-v4原样保留 |
| 已审目录/私有typed任务结果 | catalog-v3 / typed-job-result-v3 | catalog-v4 / typed-job-result-v4，绑定LINK原稿、映射、确定性定义及对象来源 |
| O1/O2阶段及producer | identification/relations-v3，各producer-v3 | 各v4/producer-v4，读取LINK并保存完整taskOutcomes、材料处置、准确依赖 |
| 本体定义/source行 | ontology-v2 / source-v1 | 保持；LINK固定投影到原结构，不改写历史定义 |
| O3 producer/覆盖/审阅 | producer-v3 / coverage/review-v3 | 各v4，双层义务、未调查细节和LINK映射有准确来源 |
| 操作观察及artifact查询 | observation-v2 | observation-v3识别LINK阶段/成本/下一动作；旧v2语义不变 |
| 策略集 | ontology-artifact-policy-set-v3 | v4，精确列新旧家族、文件集、容量与owner规则 |
| 公共请求/输出、配置、Agent、技术证据 | v6/v10、ontology-config-v1、原公共方法 | 保持，用现有范围/selection文件和明确版本化引用绑定 |

scope-v3保留v2根字段及Question字段。task字段为原`taskId/taskKind/readingMode/unitUses/requiredUnitUses`加必需`anchorRefs`；LINK必须TECHNICAL_BUNDLE且有一个K，其他任务仍EXPLICIT/MODEL且anchorRefs为空。SKELETON可请求联合LINK或单独OBJECT，不允许ACTION/ANALYTIC；ENRICHMENT沿用细化任务，不在此模式混入LINK。DISCOVERY的实际scope和优先级Schema同步升级并约束上述任务，不把额外任务静默丢弃。

新策略默认Prompt按明确producer/响应家族分派，不凭配置路径猜版本。新LINK复用准确Corpus-v2/projection-v3，展开规则绑定在包/任务，不为这次消费新增O0格式。旧任务仍按原规则恢复；不能换算法重造旧别名或包。新四文件重开不能依赖临时材料脚本或客户目录。

### 接线到现有代码，不另起平台

| 当前模块 | 需要修改 | 不该加入 |
| --- | --- | --- |
| OntologyEvidenceCorpus | 复用准确锚点和反查；必要的窄候选接口，不改变现有O0保存形状 | 客户业务名、语义相似度模型 |
| OntologyReadingPacket / ModelProjection | 连贯展开、用途/观察去重、精确解码、冻结v6 | 新语言解析器、自动摘要 |
| ScopeReader / SelectionReader / ReadingCoordinator | scope-v3、TECHNICAL_BUNDLE与准确对象task来源；保留原五类状态 | 通用任务DSL或恢复服务器 |
| TaskRunner / TypedTaskRunner / TypedDefinitionValidator | LINK家族、联合一次REVIEW、原稿/映射、合法定义与局部失败 | 猜端点、三轮自动修稿 |
| OntologyAnalysisConfiguredRuntime / SavedTaskContract | 正式四命令、准入/依赖/身份、失败与终态 | 第五个CLI或新的Agent方法 |
| ScopedAssembler / publisher/readers/policy | LINK直接骨架、O2对象对应、双层义务和原子四文件 | 同表同名自动合并 |
| OperationObservation / BusinessOverviewRenderer / Skill | 真实查询、成本、图、限制及支持下一动作 | 模型生成图事实、用户Agent临时补材料 |

代码、Schema、producer、Reader、policy、fixture与文档同一交付修改，不能先加无消费者的LINK枚举便宣布完成。

## 13. 实际例子：怎样产生一条有来源的图边

以下取自已完成薄实验，不是新正式结果，也不是通用Prompt的标准答案。

1. 起点是现有失败稿所指的真实标识；Java核验后定位到linkApply相关保存/使用原文。
2. 页面完整`onSearchLinkApply`调用`purchaseShow('其它','请购单','客户',"1,3")`。完整回调根据返回单据的subType选择分支，将`linkNumber`写入`linkApply`。采购订单类型在完整表单整理实现中可核查。
3. 完整`DepotHeadMapper.insertSelective`映射非空linkApply到`link_apply`。已读明细保存实现会取得该值并传给状态辅助调用；其冲突/未读helper明确保留。
4. 联合提取/审阅得到“采购订单”和“请购单”，以及订单通过linkApply引用来源请购单的联系。subType用于区分角色；不把它当唯一身份键。
5. Java只校验本次最终两个对象确实存在、S来源合法并编号；不替模型写业务结论。实际方向为采购订单→请购单，标签说明保存/引用来源编号。

```text
┌──────────┐    通过linkApply引用来源编号    ┌────────┐
│ 采购订单 │ ───────────────────────────→ │ 请购单 │
└──────────┘                              └────────┘
```

如果另一个正式LINK得到入库单→订单来源引用，O2确认两题订单身份后才能组成分支图。未调查财务或库存时不画相应节点/边；参考图只是展示要求，不是自动补全答案。用户期望的全链必须实际读取、审阅和组装所得，不能直接将此例拓展。

## 14. 验收门禁与失败结论

### 14.1 先零模型检查机械路径

仅运行新增/直接覆盖测试，scripted Provider验证正式Agent/store全链：

- 新任务从真实K扩展，无人工正文/路径补丁；错owner/来源/排除/版本在Provider前拒绝。
- 多页面实例、同一方法多入口、同名字段不同对象、共有工具假关联均不串用；实际派生E进入allowlist/覆盖，原初始scope不改，不能因查询端不在初始E里把它丢掉。
- 正文逐字/边界一致；用途、参数、候选、动态SQL和未读helper限制可逆；负向篡改必须拒绝。
- 同材料成本对照、完整EXTRACT/REVIEW及最大草稿余量检查；超限有具体结果且零派发。
- 对象/关系在同一次REVIEW闭合；删端点却保留Link被拒；合法空结果有处置，不伪造关系。
- 中间LINK拒绝、后续独立LINK成功，准确归属；依赖坏任务零调用；有限退出与报告可查。
- LINK可直接O3；两个同名对象不自动合并；O2显式对应后再合并；缺上游拒绝。
- 覆盖同时记录OBJECT/RELATION全部义务；ACTION/ANALYTIC未请求不冒充完成；漏答K有去向。
- 四文件/原稿/映射/查询/图可重开，目录搬移不依赖临时文件；图不倒转边或隐藏孤立节点。
- 历史版本、旧MD/Prompt/请求指纹及受保护文件不变；O0/O3/查询零业务模型。

非ERP fixture与业务词、字段词改名fixture必须走相同技术规则；加入共用Service但无业务交接、状态同名不同对象及合法多态反例。测试预期只留在测试中，不进入Prompt或材料算法。

### 14.2 再运行同路径的有限真实样例

使用新O0、普通问题/调查、新导航锚点、正式Java连贯材料及LINK提取/审阅。不得搬入PoC冻结包，不预填对象或参考链答案。记录锚点选择来源；恢复既有失败锚点可单独验证合同修复，但不能称自主全仓发现。

最低验证两项：既有来源编号联系和另一处来源引用/不同业务联系；再用财务及期间统计范围验证已审骨架如何细化。每项先交实际图，再交定义、来源、完整请求成本和未调查范围，不仅报调用成功。

要声明“请购、订单、入库连上”，正式产物必须同时存在：准确区分的三个对象、两处来源引用、跨任务时由O2明确的共享对象对应、来源/未知，以及可查看图。一个LINK已经闭合全部端点时无需空O2。若仅得到其中一边，只报告那一边。完整数量、库存及结算规则不是这项骨架验收的前置条件，也不能凭骨架通过宣称已经识别。

### 14.3 结论与下一动作

| 实际情况 | 准确结论及处理 |
| --- | --- |
| 已声明原文漏交、实例串用、条件/限制丢失 | 框架不通过，修机械缺陷后新运行；旧结果不改 |
| 原文完整入模，但模型遗漏/误读关系 | 材料通过，模型结果部分通过/未通过；保存并可调整通用Prompt，不无限重试 |
| 对象合法但跨题身份未确认 | 可发布局部图，连续链未通过；明确身份对应任务，不按名称拼接 |
| 完整必要材料仍不适合目标配置 | 容量未通过，给具体成本/缺项；不得截断或扩大窗口后掩盖原目标 |
| 局部结构、材料与实际关系均通过 | 仅证明已声明样例的正式路径，不能关闭全仓或跨真实项目质量验收 |

## 15. 尚未获得的证明与实施收口

1. 薄实验约120KiB完整请求并未证明小窗口兼容。下一实施必须在正式同材料路径测量和压缩；无法满足时如实保留容量未通过，不能再借模型升级收口。
2. 单条联合提取通过，不等于跨任务身份审阅已通过；O2与新LINK目录的正式接线必须先scripted后真实验收。
3. 由失败标识恢复不等于全仓自动选锚点。有限自主选择另验；不在本设计中承诺全仓发现。
4. 完整采购生命周期、运行时SQL、实例库存与财务执行仍不在本修订承诺内。需要扩范围另议，不靠行业常识画确定边。
5. 其他同架构项目可复用材料机制、协议和工具；同等语义质量需独立真实项目验收，不能预先保证。

当前没有必须由用户再决定才能写完的设计点。采用联合LINK和四命令复用路线；目标模型窗口必须在容量验收前明确，未明确不阻碍机械接线但不能宣布容量通过。剩余验收按[实现差距§8](implementation-delta.md#8-连贯材料与联合link修订工程接线与剩余验收)执行。backlog32保持OPEN，第28项长期硬约束不撤销。

## 16. 设计自检与文档交付边界

本修订已在ScopeReader、TypedTaskRunner、TypedDefinitionValidator和ScopedAssembler接通scope-v3的LINK准入、版本边界、联合任务执行、保存和发布，并通过定向贯穿测试。四命令、Corpus及公开wire保留；新业务定义只作固定结构映射，没有新增下载、语言分析或中文裁决系统。工程贯穿通过与真实业务识别验收分别记录。

README、总体职责、材料、范围、工作流、合同、实现差距及backlog32维护一致的当前边界。本轮按实施计划完成直接RED→GREEN、quality-12、有限正式运行及四文件重开/图检查；原12项暂存PoC及旧MD保留。采购来源编号骨架有实际新核对，模型剩余质量、容量、图简洁度与全仓覆盖明确未通过或未证明；不以工程测试代替真实结论。
