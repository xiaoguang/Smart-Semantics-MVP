# 本体识别：证据稳定、任务失败隔离与三个样例收尾实施计划

状态：2026-10-05，九步已按本轮限定框架/材料范围验收完成，相关实现已提交[PR #35](https://github.com/xiaoguang/Smart-Semantics-MVP/pull/35)，尚未合入main。最后两项框架缺陷专门RED→GREEN、8129共20项全绿，最终质量6695通过；最后代码的inspect和四文件查询exit0且字节完全一致。九任务八REVIEWED、一采购RELATE拒绝，四文件有效部分为INCOMPLETE；模型残余误读和未知单列。本计划接续已有四命令，不重新执行原十步计划；准确产物、保护和成本见[验收记录](../supplements/ontology-formal-acceptance-20261003.md)。

## Global Constraints

- 固定 R0/R4/O0 由正式验收记录恢复；不改写证据，不重跑客户 JDT、前端/SQL取证、Activity、业务过程或九章。
- 只交付财务关联、采购来源明细、期间统计三个局部样例；不扩大全仓。
- Java 准备简洁、完整、可追溯材料；模型语义错误单列，不用行业规则或语义修复器猜端点。
- 旧 MD 分析代码、Prompt、Schema、指纹、渲染及历史产物保持原状。共享底座只做必要增量接线。
- GPT-6 Sol/xhigh调度、设计、文档和debug；Terra/xhigh编码；Luna/xhigh测试。最多两个工作Agent，一个重型构建。
- 每项行为先 RED 再 GREEN；只运行新增/直接覆盖测试。离线贯穿通过前零真实模型调用。
- 产品模型沿用授权 Luna/high，无API-key、账户或模型回退。调用数是成本/审计；同范围程序缺陷修复不重复索取旧预算授权。每个运行仍有有限配置，一次提取加一次审阅，无无限重试。
- 原始响应、冻结包及历史终态不覆盖；输入/Prompt改变产生新任务/运行。只按完整准确匹配复用已审任务。
- 保留已有脏工作和暂存区；仅根Agent在验收后选择相关改动提交PR，不强推，不提交运行材料、本机配置或凭据。

## Task 0: 基线与保护（0.5–1小时）

- [x] 核对 linked worktree/codex/ontology-recognition、HEAD、已有改动归属。
- [x] 保存本计划与执行进度；核对原790文件保护清单，不重建原清单掩盖变化。
- [x] 保存本轮开始时的相关代码/测试/文档字节快照，作为任务增量复审基线。

## Task 1: 合同与准确任务身份（3–4小时）

- 本步骤直接验收通过：O1/O2准确身份、完整声明处置、v2保存/查询及明确历史owner接续均已验证。O1/O2具体派发上限报告与终态安装失败优先级亦经直接RED→GREEN；真实客户样例仍由第8步验收。
- [x] 将准备、成功、失败、阅读覆盖按 producingTaskId/jobKey 关联，先删除数组位置配对（中间失败、第三任务成功的直接测试通过）。
- [x] taskRecords只列实际job；taskOutcomes列所有声明任务，未准备无假jobKey（O1/O2缺依赖和局部拒绝测试通过）。
- [x] 新阶段identification/relations v2、查询/观察v2、coverage/review v2及producer/reader/schema/policy/fixture一并接通。
- [x] scope字段保留v1，依赖规则版本参与任务匹配；selection新执行v2，历史v1严格查看。
- [x] RED/GREEN：中间失败、后续成功仍归属于正确任务；缺项和未准备记录可重开。

## Task 2: 正式补读、紧凑投影与Prompt（4–6小时）

- [x] 选中的源码引用通过同源R0正式读取正文；可靠完整边界优先，否则准确路径完整文件；不切函数（直接SOURCE、完整边界及同文件多引用测试通过）。
- [x] 统计parseHomePriceByLimit辅助原文进入scripted EXTRACT/REVIEW；客户新O0保存该完整单元，原导航冲突仍保留，不升级确定调用。真实请求核对与最后一项一并完成。
- [x] 冻结包/model投影v4保存补读、边界种类、摘要、用途及来源映射；旧v3原规则读取。
- [x] 完整原文保留；机械去重、紧凑调用及最小准确对象目录减少重复；具体不确定性不丢失（无损及高调用量直接测试通过）。
- [x] OBJECT/ACTION/ANALYTIC/RELATE职责清楚；REVIEW看同包、实际稿、目录、诊断并检查全嵌套引用（严格引用及原定两次调用直接测试通过）。
- [x] 实测六个O1及三个O2任务的全部18份EXTRACT/REVIEW封套；每任务两阶段同包/同目录，按准确同题来源隔离。三个包34个完整Java正文逐字匹配同R0内容及摘要；统计helper实际入模且冲突保留。字节不冒称token，不恢复70KB门槛；采购最大容量检查额460,216字节，仍不宣称任意小窗口兼容。

## Task 3: O1/O2局部失败与依赖（4–6小时）

- [x] O1 OBJECT独立；ACTION/ANALYTIC只依赖同题此前全部OBJECT。ACTION失败不挡ANALYTIC（新增正式CLI测试通过）。
- [x] O1/O2缺依赖UNPROCESSED/DEPENDENCY_NOT_REVIEWED，零Provider初始化/调用（同题/明确对象来源的直接测试通过）。
- [x] O2 objectSources明确O1 run/question；正式QUESTION测试证明依赖未审只阻断本题，另一有效来源的关系题继续。DISCOVERY范围读取已接线，专项消费者验证在第7步单独收尾，不由已有O1发现测试替代。
- [x] 明确结束的坏模型稿局部REJECTED；可读坏稿至多进入原定一次REVIEW，不增第三轮。直接坏JSON不进入REVIEW，原始响应仍保存，独立任务继续（定向测试通过）。
- [x] 来源/配置/认证/保存/未知请求故障停受影响范围，保存完整处置，不凭中文消息分类。SOURCE准入、STORAGE共享停止、Provider未知状态、MODEL阅读坏决策局部性及完整查询均有直接GREEN。
- [x] scripted正式CLI/Agent/store验证独立继续、依赖阻断和全部失败有限结束。

## Task 4: Provider有界执行与观察（2–4小时）

- 本步骤定向验收完成：Provider叶层13项、正式超时/预检观察以及实际订阅适配器响应超限私有保存均有GREEN，最终直接覆盖组和受影响边界回归已执行。真实远端取消不在承诺内。
- [x] 整次deadline覆盖进程启动、stdin写入和等待；终止后确认本地状态。
- [x] 本地退出不等于远端取消/结束；未知远端结果停同绑定新派发，不重叠重发（正式CLI假超时观察测试通过）。
- [x] 真实观察区分reservedAttempts/confirmedStarted/confirmedEnded/outcomeUnknown；初始化/登录预检失败仅预留，不报已启动（正式CLI直接测试通过）。
- [x] 旧MD请求、指纹、失败字段/重试语义不改；新增观察供本体消费（旧请求字段回归及保护清单核对通过）。
- [x] 假进程直接验证大输入/不读stdin的有界结束，旧超时/认证/私有失败输出回归13项通过；正式本体remote观察、预检计数及100001字节响应私有保留测试通过。
- [x] 最后复审的正式响应读取边界已RED→GREEN：81 MiB稀疏输出/64 MiB隔离堆在46391复现OOM；受限重载按原deadline及16 MiB私有完整保存上限读取，超限返回明确失败、不读入/截断正文。旧三参数路径和测试注入默认委托不变；8129直接组合20项全绿。

## Task 5: O3部分发布、查询与终态（4–6小时）

- [x] O3只取REVIEWED完整任务；原义务分母及失败/未执行全部继承。
- [x] 各O2按自身保存请求核对上游后，与O3显式O1集合检查子集闭合；显式并集可发布、漏选实际上游拒绝（新增正式CLI/存储测试通过）。
- [x] ontology/source保持v1，coverage/review v2；DRAFT_REVIEWABLE/NOT_EXECUTABLE，缺范围INCOMPLETE。
- [x] 断端点或身份冲突保存组装诊断，拒绝正式安装，不删定义补成功。
- [x] CLI/inspect/artifact返回taskOutcomes/nextActions；canContinue仅表示原范围完整。
- [x] COMPLETED→FINISHED，PARTIAL/BLOCKED→FAILED但保留output；无receipt不报假产物；不等待用户保持RUNNING。晚于任务拒绝的真实ArtifactStore安装故障准确报告STORAGE/INSTALL，原任务坏稿仍可查询（最新直接六项GREEN）。
- [x] 最后复审的UNPROCESSED裸编号聚合已RED→GREEN：13196证明两个上游只留下一个处置；v2按原run/question/task/producer聚合，完整两义务保留。8129直接组合覆盖准确owner、合法已审结果去重、独立O2并集、漏上游拒绝及冲突无安装，共20项全绿；历史分支不变。

## Task 6: Skill、示例与文档（1–2小时）

- [x] 维持唯一程序四操作；Skill读实际结果，区分材料/模型/依赖/共享故障，非零也查询报告（按共享说明的限定应用检查通过）。
- [x] 同范围合法操作继续；改变Prompt/材料新运行，不能手填答案/映射，不能擅自扩大全仓。
- [x] 配置/selection示例由实际reader验证；共享说明明确命令、准确上游、任务查询和失败处理，不依赖本聊天补值。未安装或调用其它Agent产品，不将说明检查冒称所有宿主的真实验收。

## Task 7: 离线贯穿与质量（2–3小时）

- [x] 新增及直接覆盖测试：身份、依赖、补读、引用、压缩、O3闭合、保存失败、状态未知、历史及旧MD隔离。组合181项中的两处剩余边界已分别GREEN，后续六项终态/依赖及重构后五项直接回归通过；唯一可选跳过为历史P1/P2i成本对照。
- [x] 发现模式的O2-v2实际消费者：`modelDiscoverySelectionFeedsV2RelationObjectSourceConsumer`以原始空questions的DISCOVERY O1保存selectedQuestions，再由准确objectSources进入RELATE；session28778，1项、0失败/错误/跳过（Maven91秒），实际OBJECT目录、同包审阅及准确依赖通过。不是用O1发现成功推测O2可消费。
- [x] 原始证据和坏响应字节保持；O0/O3/查询/scripted零真实模型。受保护790文件重算无变化、无缺失。
- [x] 最后复审两项窄修均有专门RED→GREEN，8129共20项、零失败/错误/跳过；最终同状态静态质量6695 exit0（122秒、767文件格式清洁、应用toolchain17、SpotBugs零发现/错误、PMD通过、UT/IT跳过）。没有借用修复前质量报最终通过。
- [x] 修复后运行质量宿主JDK21+，应用toolchain17；session84337 exit0（209秒）；新增发现消费者专项后只机械格式化该测试，再执行session15890 exit0（282秒），格式766文件清洁、SpotBugs0发现/0错误、PMD通过，UT/IT跳过。先前检查不代替当前结果：

```bash
JAVA_HOME="$SOURCE_ANALYSIS_QUALITY_JAVA_HOME" \
mvn -t .mvn/toolchains.local.xml -Pquality \
  -DskipUTs -DskipITs spotless:check verify
```

## Task 8: 三例正式运行与交付（1–2小时核验＋模型时间）

- [x] 离线通过后按正式Java清单准备财务、采购、统计：新O0为0785b1a0…，339入口/15,726别名，三题全部实际用途存在；没有Agent手拼正文或本体答案。实际O1为7f43387b…，FINISHED/COMPLETED，6任务REVIEWED，12/12/12/0实际观察；关系、发布及模型质量按以下各项分别验收，不由O1通过推定。
- [x] 每题执行适用O1/O2及O3并有限结束；O1六REVIEWED，O2两REVIEWED/采购关系一REJECTED，O3 3886bb56…保存四文件及九义务8/1处置；不从拒绝关系稿拣定义。实际包、映射、草稿、审阅、18次请求及完整成本已核对。
- [x] 框架材料与模型质量分别核验：34个完整Java正文/R0摘要、六组同包同目录及各题依赖准确；辅助源码入模不改变调用冲突。财务动态过滤/累计额表达遗漏、采购最终scope错误、统计粒度/权限具体未知单列，不归咎证据缺失，不冒称业务零错误。
- [x] 最后两项框架缺陷已具名修复、直接回归和质量通过；没有追加真实模型调用或改业务答案来掩盖材料/控制流问题。
- [x] 原790保护文件逐项无变化/无缺失，12个排除PoC文件及原暂存索引未变；设计—实现—测试/backlog已同步。相关212文件正常提交并创建[PR #35](https://github.com/xiaoguang/Smart-Semantics-MVP/pull/35)，不含运行数据、凭据、本机配置或临时进度；未强推或直接修改main。

完成要求：非法输出不发布、证据不变、独立任务继续、依赖缺口明确、进程有界结束，三个局部样例经正式链路保存、重开、追溯。模型零错误、自主全仓发现不属于本轮承诺。
