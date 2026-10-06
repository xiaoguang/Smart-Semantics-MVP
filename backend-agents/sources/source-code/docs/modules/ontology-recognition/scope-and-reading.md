# 范围清单与正式阅读状态

状态：现有四命令、失败隔离、LINK-v1／v6材料、跨运行对象来源与四文件发布已有工程及有限真实结果，当前事实见[连贯路径验收](../../supplements/coherent-link-20261005-acceptance.md)。[前序129项／业务总览](../../supplements/ontology-business-link-formal-acceptance-20261005.md)和[稳定性18请求](../../supplements/ontology-formal-acceptance-20261003.md)保持各自原版本记录。新的精简材料／专用类型对应设计待实施；不宣称小窗口、完整生命周期、全仓自主发现或跨项目同等质量，旧MD及历史产物不变。

当前薄实现将scope/selection解析公开为窄输入边界，并在既有coordinator/decision/private-store上保存`ontology-decision-result-v4`与`ontology-formal-reading-state-v1`。后者保留实际派发的visibleScope、查询观察、五个状态集合、结构化未解/收窄处置和同一冻结包；它不是公共Reader/Publisher，也不替代已接线的Task 7 receipt、配置和CLI准入。公开`FormalState`构造器的八个列表防御性复制合同已经实际RED/GREEN；未用SpotBugs排除代替该复制。

## 1. 清单不是业务答案

本节v1/v2是历史兼容规则；当前联合路径已经采用[scope-v3详细合同](coherent-link-material-design.md#12-版本与正式接线清单)：SKELETON增加LINK；readingMode为TECHNICAL_BUNDLE时由Java从一个真实anchorRef展开连贯材料，不制造模型阅读历史。外部对象来源的taskIds精确选择已审OBJECT/LINK。原scope-v1/v2不升级，旧五类MODEL阅读状态和协议继续按原版本保存。

待实施[精简及类型对应设计§17](coherent-link-material-design.md#17-精简材料与跨段类型对应的正式设计)保持scope-v3和原MODEL状态；新selection-v4明确GENERAL_RELATE／OBJECT_TYPE_CORRESPONDENCE。只有新的类型对应profile允许O2使用TECHNICAL_BUNDLE，旧selection-v3的禁止规则不放宽。候选由准确已审对象来源产生，一对一题、两端有限完整材料，未读和未执行分母保存；不增加ReAct循环或第五个CLI。

identify-ontology的范围清单明确一次局部执行的普通问题、任务和材料选择性质。它不包含对象、关系、规则或指标定义。调用者只能选择真实O0的E/U/K；不能给程序写一份“正确本体”作为输入。历史`ontology-scope-v1`按原字段严格读取；新增`ontology-scope-v2`也按自己的完整字段严格读取，不能借缺字段推断或升级旧清单。

v1固定顶层为`schemaVersion / mode / selectionMode / questions`；v2另必需`purpose`，只准`SKELETON / ENRICHMENT`：

- mode为QUESTION或DISCOVERY；本次三样例使用QUESTION。
- selectionMode为EXPLICIT或MODEL，记录材料由谁选，不能把显式选择宣称自主成功。
- QUESTION中questions非空；v1每项为`questionId / question / entryRefs / clueRefs / tasks`，v2另必需`objectSources[]`。questionId在本清单内唯一，question是非空普通问题；entryRefs和clueRefs来自准确Corpus。DISCOVERY中questions为空，由有界调查产生新的实际问题及处置，不用硬编码业务种子。
- tasks每项为`taskId / taskKind / readingMode / unitUses / requiredUnitUses`。taskId在本次执行内唯一；taskKind只准OBJECT/ACTION/ANALYTIC，RELATE在O2。readingMode为EXPLICIT或MODEL。
- 单元用途统一为`{unitRef, entryRef}`，必须是同Corpus中实际允许的U/E组合。一个U可在不同E中使用；不从同一正文猜同一用途。
- EXPLICIT直接由Java取得unitUses、检查requiredUnitUses并冻结；不为已明确选择再调用阅读模型。MODEL以unitUses作为初始正文，持续保留entryRefs/clueRefs并执行有界阅读。初始正文可以为空。

v1的ACTION/ANALYTIC仍须由同题排在前面的OBJECT任务提供依赖。v2的SKELETON仅允许OBJECT任务且`objectSources`为空；ENRICHMENT允许OBJECT/ACTION/ANALYTIC，后两类必须有同题此前OBJECT或非空外部对象来源。每个来源只有准确的`identificationRun / questionId`，运行ID须为完整`analysis-run:`键，同一来源对不得重复。Reader仅检查清单的形状和本Corpus内E/U/K；来源是否同Corpus、已结束且全部OBJECT已审，由运行器在Provider前恢复和核验。已声明objectSources是本题全部任务的依赖，包括OBJECT细化；源问题真实存在但未声明OBJECT时，保存具名依赖失败且零派发。不存在的源问题、损坏来源或错Corpus仍在准入阶段拒绝，不伪造任务或源对象。有效的源对象目录及依赖指纹也进入OBJECT细化，最终身份统一仍必须另经已审SAME_OBJECT。任务可以没有对象/指标结论，但必须有实际处置。材料清单按完整用途身份去重及稳定排序；不因文件位置变化改变内容身份。

scope-v2的DISCOVERY也保留原purpose：优先级请求明确携带purpose；SKELETON的实际响应Schema只允许一项OBJECT，运行器再核对选择范围。它不静默丢弃模型额外建议的ACTION/ANALYTIC来冒充成功；非法建议保留原响应与阻断报告，不派发类型化任务。生成的实际工作scope继续使用v2及原purpose。旧策略、旧Corpus下scope-v1的优先级输入、Schema和Prompt保持原样。scope-v1若明确使用新Corpus和producer-v3，则使用新投影及新默认Prompt，形成新任务身份；不能将其解释为旧任务的无条件复用。

## 2. 关联和发布清单

`ontology-selection-v1`按operation严格分派，不寻找最新结果：

| operation | 必需字段 |
| --- | --- |
| RELATE | corpusRun、identificationRuns、questions |
| PUBLISH | corpusRun、identificationRuns、relationRuns |

RELATE questions每项为`questionId / question / taskId / readingMode / entryRefs / clueRefs / unitUses / requiredUnitUses`，与上述阅读字段同义，任务固定RELATE。对象目录来自被选择的真实已审O1，不从清单提供。questions可以为空，此时O2零模型保存明确的零任务处置。PUBLISH只选择完整已结束阶段，不接受关系/指标正文或局部替代答案。

三个run集合准确重开，同Corpus、来源和语义上游检查先于Provider初始化。运行ID是存储归属；定义身份还包含任务和审阅版本，不能只凭run列表或同R4合并。重复run拒绝，且同一run不得跨`corpusRun`、`identificationRuns`、`relationRuns`任意两个槽位复用；输入集合排序只稳定身份，不改变模型语义。

## 3. 一份状态，五个集合

正式状态保存在已有私有job底座，不另建公共阅读Reader/Publisher。状态内容包括准确Corpus、问题/任务、轮次、实际可见集合，以及：

| 集合 | 何时改变 |
| --- | --- |
| selectedEntries / selectedClues | 初始明确选择或模型显式范围增减；移出正文不能改变它 |
| discoveredHandles | 实际合法查询/导航返回后并入；重复查询不丢前项 |
| readHistory | 完整单元确实进入已派发模型输入时登记；仅从磁盘取回不算模型已读 |
| activeUnits | 明确保留、移出或补读正文后的集合；每项含U/E用途 |
| requiredButUnread | 模型/范围提出的必要单元；实际送入原文或显式未解/收窄处置才改变 |

### FormalState 的公开构造不可变性

`FormalState`的公开八列表构造器在保存前分别执行`List.copyOf`：selectedEntries、selectedClues、discoveredHandles、readHistory、activeUnits、requiredButUnread、unresolvedDispositions和queryObservations。调用者随后改变任一输入列表不能改变状态，且每个getter不可修改。该合同的单方法回归先实际RED（修改selectedEntries后泄漏E2），再GREEN；它不是SpotBugs排除项。

readHistory记录轮次、请求身份、单元及投影摘要；不会因移出正文变成从未读。EXPLICIT冻结后由实际提取发送登记，不能以“准备过包”计模型已读。EXPLICIT冻结、单元或全封套预检拒绝、以及结构化Provider失败也会保存私有`ontology-formal-reading-state-v1`：记录稳定的题目/任务/范围/材料/来源身份和`PREPARATION`、`PROVIDER_FAILURE_PRE_DISPATCH`或`PROVIDER_FAILURE_DISPATCHED`观察类别；未实际派发时`visibleScope`为null，不能补造decision或readHistory。正式MODEL job的私有匹配基础另含Corpus来源身份、完整canonical短号映射的摘要，以及调用者提供的正式配置上下文身份；这些私有身份不进入模型导航输入，避免两个短导航相同但来源/映射不同的包错误复用。

## 4. 统一阅读响应

reading-response-v3固定为`decision / entrySelection / clueSelection / retainedUnitUses / requiredUnitUses / actions / unresolved`。decision为NEEDS_MORE_MATERIAL、READY_TO_EXTRACT或UNRESOLVED。

- entrySelection、clueSelection各含addRefs与remove列表，移出项含ref/reason；只接受本次实际展示的合法类别。同一E/K不得同时add和remove，remove必须指向当前已选项；因此只有实际从selected范围移出的entry才会处分其准确未读必要用途。未出现显式移出时原选择继续保留。
- retainedUnitUses只能引用已在当前正文中的U/E，不能把导航句柄当“已读”。其用途集合决定下一轮保留正文，但不决定selectedEntries。
- requiredUnitUses是追加申报，不因为未出现在下一轮响应中就清空。unresolved具名问题及缺项，保留实际范围。
- 正式unresolved每项固定为`{reason, disposition, unitUses}`：reason非空，disposition为UNRESOLVED或EXCLUDED_FROM_TASK，unitUses为准确U/E用途数组。一般问题可用UNRESOLVED且unitUses为空；EXCLUDED_FROM_TASK必须具名至少一个用途。只有实际展示的用途及已申报必要用途可被处分，不能靠中文提到一个编号就清除义务。处分记录始终保存；该用途不再阻止其余已读材料冻结，但不写入readHistory，不改称已读或原题全范围完成。显式移出入口时，受影响的必要用途以同一个移出理由记为EXCLUDED_FROM_TASK，不能无声消失。
- actions是同一数组，每项QUERY或READ；总数不能超过maxActionsPerRound。QUERY含queryKind、合法keyRef、offset/limit，或LITERAL_SEARCH的query文本；READ含合法unitRef/entryRef。不得让Schema允许8项而执行只收2项。
  新业务阅读输入v4同时提供实际maxUnitBytes/maxRequestBytes；每次从模板复制的校验Schema写入配置动作maxItems及查询limit.maximum，Provider投影与本地验证使用这同一份实例，实际Schema参与请求身份。模板和历史v3输入不改写。提示词要求复制完整U/E配对，不能将两个分别可见的编号拼成未展示用途；导航成本不是程序已经判定超限。
- 查询只开放ENTRY_UNITS、METHOD_USES、STATEMENT_USES、TABLE_STATEMENTS、COLUMN_STATEMENTS、LITERAL_SEARCH。U/E/K类别按查询矩阵检查，变体不能任取第一个；METHOD_USES也可使用当前active的JAVA_METHOD U，STATEMENT_USES也可使用当前active的XML_STATEMENT U，并以该准确U/E用途的原身份查询，不能从模型文本猜键。结果显示实际总数/页/用途和成本，不是业务摘要。
- 本轮可见allowlist由Java生成，范围和轮次由请求绑定；模型不回填viewId、哈希或来源身份。未展示的U/K不可直接引用。

READY必须有非空当前完整正文、没有本轮未执行动作，且已申报必要范围有明确处置。轮次/容量达到上限只能INCOMPLETE或UNRESOLVED，不能自动READY。显式范围收窄保留原范围和原因，不把它改写为原题完整成功。

模型输入分别提供实际展示的导航和当前已选择集合。导航含准确方法/语句/表列或前端单元的原有技术标签、共同项、用途及以实际一用途formal packet测得的成本；不能只给E/U/K编号让模型猜，也不把私有canonical记录大小标成模型成本。每个入口卡保留按kind的`total/shown/unread`线索disclosure及无AST SQL分析数；卡片仍只展示有界线索。查询观察保留query、总数、offset/limit及该页完整返回项，字面搜索实际命中同样可再读取；ENTRY_UNITS结果只携带其准确U/E用途对应的既有K，并只将这些返回K和实际entryRef加入下一轮displayed allowlist，因此页外命中可被显式选择或继续查询而任意未展示编号仍被拒绝。新的查询观察本身是有状态进展；完全相同的重复查询不会借此绕过no-progress。当前已选入口不会因正文移出而丢失。maxActionsPerRound只限制响应动作，maxNavigationEntries独立限制导航页，二者不得借用同一参数。正式questionId只要求清单内唯一非空，不强制继承实验Q数字命名。

执行结果没有进展时保存具体状态并结束，不重复调用直到凑齐次数。QUERY/READ在当前Corpus执行；没有shell、网络或客户代码执行。调查或优先级的正常模型响应若不符合实际Schema，保留原响应和准确`MODEL_OUTPUT`原因及`SURVEY/PRIORITIZE`阶段；优先级拒绝仍保留已提出问题的延后处置，不能写成未知工具故障或继续派发类型化任务。

## 5. 冻结和检查

新LINK使用材料v6；展开目录、选择来源、未纳入/容量处置、同包原文和实际成本参与身份，准确字段由[连贯材料设计](coherent-link-material-design.md#6-java连贯材料算法)拥有。TECHNICAL_BUNDLE准备结果按任务保存，不派发阅读模型或编造QUERY/READ；歧义候选留未读，必要的新选择形成明确新任务。未经实际读取的来源不能进入evidenceRefs。

冻结包使用正式v3投影，保存S→U/E→完整原身份、完整选定原文、必要调用观察、未读范围和实际配置/Prompt/Schema身份。EXTRACT与原定REVIEW使用同一冻结正文；之后要补材料须新任务，不能让旧稿凭空获得新依据。

阅读输入、提取输入以及含实际草稿的REVIEW都检查实际封套；源码单元不得截断，不能凭导航成本声称REVIEW必然容纳。Task 4将`maxUnitBytes`继续交给formal packet的完整canonical单元门禁，并单独以实际`modelInput`投影检查`maxRequestBytes`；Provider决策器也以同一请求上限检查实际输入、Prompt、Schema和输出保留额的完整封套。私有canonical记录大小不误作模型请求容量。五参数正式便利构造保留1,000,000请求字节/100导航项默认值；七参数构造使用调用者的请求上限且保持100,000字节输出保留额，运行时可注入显式决策器来提供真实输出上限。`maxNavigationEntries`也独立于`maxActionsPerRound`。配置不固定70KB阈值，不将字节当token。

直接测试用scripted Provider串行模拟：选保存与查询两入口→读取保存端→移出正文→查询端仍在目录→重复读取不重新编号→补读查询端→冻结。另测类别填混、动作超限、超容量、必要未读、无进展和历史记录保持。真实模型不用于调试这条状态机。
