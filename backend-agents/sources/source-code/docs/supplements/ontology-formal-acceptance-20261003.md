# 正式本体框架验收：当前已验证范围

状态：2026-10-05，三个局部样例的正式O0–O3、证据审阅、失败隔离与有效部分发布已验收。最终两项框架缺陷已直接RED→GREEN，20项回归和最终质量6695通过；最后代码的inspect及四产物查询均exit0、字节与安装文件完全一致。实现PR正在交付。九声明义务八REVIEWED、一采购关系REJECTED，覆盖INCOMPLETE；模型残余错误不由Java补写。依据[正式实施计划](../plans/ontology-recognition-implementation-plan.md)及[九步收尾计划](../plans/ontology-stability-implementation-plan.md)。本页区分工程、实际材料和模型质量，不以模拟响应或旧PoC替代正式客户样例。

## 输入与保护

- 固定R0：`analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7`。
- 固定源码提交：`8c30ce7861570458920175e200bb2a6442713580`，snapshot为`528e8d2811372396dab1954c02855c9cad0d958f4a16de5e12456de374cdd60f`。
- 准确复用后端R2：`analysis-run:278f1055c8693a2efe53ca8d2192fec6c9d61f479c40ad45c810cdf34603d4d7`。
- 准确复用持久化R3：`analysis-run:bea6ae4a70d24c5dd5ae6ea819bf88e4e07500f9f4eae8f78fc0d6d21d40fb9a`。
- 旧MD分析代码/Prompt/Schema/渲染、历史R0/R4、326及418条Activity和`more-findings.md`在原保护清单中。最新逐项核对790个文件：无变化、无缺失；没有重新生成保护基线。

本轮不重跑客户源码的JDT、后端/SQL取证或旧业务生成；直接测试中的中性技术fixture不算客户重取证。补读/替换单元丢失原入口限制的确定性缺陷已经直接RED→GREEN：75743预期NAVIGATION_CONFLICT=2而实际为空，窄修后12376共11项通过，准确限制继承且矛盾仍拒绝。修复后质量84337及发现消费者专项后的质量15890均exit0；790保护文件无变化/缺失。此前失败尝试未注册O1或派发模型，不为它补运行ID或回执。修复后的O0 `analysis-run:0785b1a0dd08b49b15a3f7f2d41c9264989777cd2dc601ea8e84bc40ba3005df`已经正式保存和查询；本轮O1 `analysis-run:7f43387b34357f8be530bfc065be032e7f9b36316b9917b2ab7af1c7d476ca4f`为FINISHED/COMPLETED，六任务均REVIEWED，实际12/12/12/0请求观察。O2 `analysis-run:c267ff0d9f75146eab5cc25926ab9f74fc52b1576d0a5c7b16cbe17964095a35`已PARTIAL/FAILED结束：财务及统计关系已审、采购关系最终scope.questionRef不属于当前关系题而被局部拒绝，6次请求确认开始并结束。O3 `analysis-run:3886bb564e8d29acd433765feea09bd4aa0755eadcd40f10fe2ed13faa55269e`已PARTIAL/FAILED保存四文件，九义务八已审一拒绝；公开四文件与来源索引已实际重开。最后两项框架边界经直接RED→GREEN、最终质量及零模型重开完成，实现PR正在交付。第7步发现模式O2-v2实际消费者专项已通过，不能以O1发现测试代替。调用次数用于审计/成本；不自动换模型、扩大全仓，模型非法结果不由宿主改编号或范围补成功。

本轮的**首要审阅对象是模型实际收到的证据**，不是模型是否足够聪明。每个需要核验的结论先列必要的建立端、使用端、条件、后处理及限制，再用保存的EXTRACT/REVIEW请求核对完整原文和用途是否真正入模，并确认两次请求使用同一冻结阅读包。证据缺失或来源映射错误归为框架材料问题；证据齐全但模型遗漏、误读归为模型质量；合法材料下反复无法产生可校验结构归为输出合同可用性问题。三者不得互相替代。此标准只针对本次具名问题，不以有限样例证明全仓材料穷尽。

## 稳定性批次的实际请求成本与审阅口径

以下仅对应上述`7f43387b…`批次已保存的真实请求，不是旧PoC预检。Provider stdin按当前适配器的实际拼接规则从保存Prompt和canonical input重建；Schema另通过`--output-schema`文件提供。容量检查额为`input＋Prompt＋Schema＋65,536响应字节预留`，**不是发送给模型的字节数**，也不是token数。保存request.json自身含元数据，大小又不同。

| 任务 | 阶段 | canonical input字节 | 实际stdin字节 | 独立Schema字节 | 容量检查额字节 |
| --- | --- | ---: | ---: | ---: | ---: |
| 财务OBJECT | EXTRACT / REVIEW | 109,713 / 133,233 | 112,441 / 136,645 | 12,161 / 12,158 | 189,921 / 214,122 |
| 财务ACTION | EXTRACT / REVIEW | 129,808 / 163,118 | 130,799 / 166,530 | 28,047 / 28,044 | 224,165 / 259,893 |
| 采购OBJECT | EXTRACT / REVIEW | 318,372 / 357,062 | 321,100 / 360,474 | 12,161 / 12,158 | 398,580 / 437,951 |
| 采购ACTION | EXTRACT / REVIEW | 351,293 / 363,441 | 352,284 / 366,853 | 28,047 / 28,044 | 445,650 / 460,216 |
| 统计OBJECT | EXTRACT / REVIEW | 64,051 / 82,767 | 66,779 / 86,179 | 12,161 / 12,158 | 144,259 / 163,656 |
| 统计ANALYTIC | EXTRACT / REVIEW | 79,540 / 123,926 | 80,261 / 127,338 | 19,170 / 19,167 | 164,750 / 211,824 |

O1实际十二份请求均通过保存的1,048,576字节检查。O2六份实际请求的核对如下；三题EXTRACT/REVIEW各自的readingPacket及reviewedCatalog规范化JSON相同，目录只来自各题明确选择的已审OBJECT。采购输入仍较大，不能称已适配任意小窗口。配置的4,096输出token记录为`NOT_ENFORCED_BY_ADAPTER`；字节、请求数及超时检查独立执行。

| O2任务 | 阶段 | canonical input字节 | 实际stdin字节 | 独立Schema字节 | 容量检查额字节 |
| --- | --- | ---: | ---: | ---: | ---: |
| 财务RELATE | EXTRACT / REVIEW | 129,817 / 137,701 | 130,413 / 141,113 | 11,392 / 11,389 | 207,124 / 217,821 |
| 采购RELATE | EXTRACT / REVIEW | 351,302 / 357,441 | 351,898 / 360,853 | 11,392 / 11,389 | 428,609 / 437,561 |
| 统计RELATE | EXTRACT / REVIEW | 79,547 / 97,436 | 80,143 / 100,848 | 11,392 / 11,389 | 156,854 / 177,556 |

O2实际命令exit2，PARTIAL/FAILED并保存ONTOLOGY_RELATIONS及全部三条taskOutcomes；请求观察6/6/6/0，没有第三轮。财务和统计为REVIEWED，采购为REJECTED。采购最终L1/L2的scope.questionRef仍为原OBJECT题`procurement-source`，而当前RELATE题是`procurement-source-relation`，两条SCOPE诊断被准确保存。当前题ID在实际请求中明确存在；建立/保存端原文和3个准确对象均存在。程序未替模型改scope，采购拒绝后统计任务正常结束。该结果验证局部失败隔离，不代表采购关系已通过，也不能将被拒关系局部拣入发布。

财务RELATE保留header_id及bill_id的建立/使用和欠款联系，但金融累计额描述未完整写出已提交helper中的求和后abs及动态idList过滤，继续单列为模型遗漏。统计RELATE保留SQL主明细连接、强审核状态及权限相关源码，并列出具体未知；权限helper调用仍有原导航冲突，不能把结构合格的模型措辞当作工具已确认调用。三题同名对象没有跨题SAME_OBJECT审阅决策，不由Java按表名合并。

财务两任务的26个正文单元/27个入口用途完整保存；EXTRACT与REVIEW使用同一冻结包。ACTION实际收到同题已审的3个OBJECT，不收到其他题目录。其首稿的五条引用诊断被原定REVIEW修正，最终validation/success为诊断空、REVIEW_VALID；`reviewed-result.json.diagnostics`保存的是首稿诊断，不能误报成最终拒绝。最终金额表达保留先sum后abs、欠款减法；动态`idList`过滤原文已经入模，但最终A2没有完整写出，单列为模型遗漏，不修改模型结果补成功。

采购最终OBJECT/ACTION区分主表`linkNumber/linkApply`和明细`linkId`；完整页面回调中的`info.linkId = info.id`及后端/Mapper保存端均入模。来源行的后端实体、外键约束仍未知。统计OBJECT的S4实际包含完整`parseHomePriceByLimit`声明及正文，S11三个调用位置仍保存NAVIGATION_CONFLICT、无确定target；补读没有升级调用边。统计最终对象保留角色/用户/配置身份及权限端点未知，不将聚合结果凭空定义为有稳定身份的业务实体。ANALYTIC保存3维度、6组成度量、3净额指标，实际目录只包含该题已审5个OBJECT；其中价格遮蔽、强审核、creator限制及日期边界保留具体未知。结构合法不等于所有表达精确，指标仍非可执行。O2统计关系已审，但具体未知仍保留；采购关系最终拒绝，没有据此发布其关系定义。

实际O1命令和零模型inspect均exit0，FINISHED/COMPLETED、canContinue=true、problems为空，6个taskOutcomes均REVIEWED；reserved/confirmedStarted/confirmedEnded/outcomeUnknown为12/12/12/0。没有自动第三轮或回退。发现模式的O2-v2消费者专项`modelDiscoverySelectionFeedsV2RelationObjectSourceConsumer`通过1/0/0/0（Maven91秒），证明原始空问题清单不妨碍按保存selectedQuestions恢复已审OBJECT。该测试新增后质量15890 exit0（282秒、766文件格式清洁、SpotBugs0/0、PMD通过、UT/IT跳过）；原790保护文件仍无变化/缺失。

## 当前O3产物、完整覆盖与重开

正式O3为`analysis-run:3886bb564e8d29acd433765feea09bd4aa0755eadcd40f10fe2ed13faa55269e`，明确选择上述O0/O1/O2，不绕过失败关系。命令exit2、PARTIAL/FAILED、canContinue=false，安装并公开列出四个产物；请求观察0/0/0/0。原声明九任务保留为八REVIEWED、一REJECTED，coverage为SCOPED/INCOMPLETE、semanticExhaustiveness=UNDETERMINED。FAILED表示原范围未完整通过，不表示没有保存结果，更不是后台仍在等待。

| 正式文件 | UTF-8字节 | 实际内容 |
| --- | ---: | --- |
| ontology.json | 450,285 | 11对象、7操作、6规则、3维度、6度量、3指标、6关系；42顶层定义索引，159条结构化未知 |
| ontology-coverage.json | 36,755 | 全部九任务处置、逐材料阅读范围及339入口/91前端请求/0DDL来源分母；不声称全仓语义完成 |
| ontology-sources.jsonl | 80,195 | 92个唯一来源映射，绑定Corpus/任务/审阅/S/U/E及原始来源 |
| ontology-review.json | 56,328 | 八个完整已审任务、全部处置、准确上游receipt与selection、四项BUSINESS_LINK决定及未知；assemblyIssues为空 |

本体为DRAFT_REVIEWABLE、humanAcceptanceStatus=NOT_REVIEWED；三个指标的executionReadiness均为NOT_EXECUTABLE。159是结构化未知记录数，不是159个独立业务缺陷，也不等于全系统未知数量。四个BUSINESS_LINK决定不等于跨题对象身份合并；没有SAME_OBJECT决定时11个对象保持各自任务身份。

不含模型配置的正式inspect、ONTOLOGY、ONTOLOGY_COVERAGE、ONTOLOGY_REVIEW查询均exit0，O3和查询不登录模型。SOURCE_INDEX第一次宿主核验脚本误以单JSON解析JSONL而exit1，CLI返回的原始行有效；改为逐行解析后同一正式查询exit0，92行、唯一引用92，输出SHA-256与安装文件`8e570ffd4901f22d60f5d78ace0afd6ef72021d0eb14102a93da78f2577ed4c3`一致。这是宿主检查脚本错误，不是产品读取缺陷。ontology.json中1,389个evidenceRefs引用出现都映射到这92个来源，无悬空来源引用。

六任务独立只读材料核查已完成：三个不同包共34个JAVA_METHOD正文均逐字匹配固定捕获树中的唯一完整源码位置，文件摘要与准确R0 inventory吻合；财务/采购/统计完整方法正文分别13,418/45,765/10,366字节。六任务EXTRACT/REVIEW同包同目录，后续O1目录仅含本题3/3/5个已审对象；O2亦分别核对同包同目录和同题对象来源。调用状态逐位置保留，未确认目标仍不展开。仅有结构合法和完整证据不能证明模型事实零错误。

最后只读复审发现的两处框架缺陷均已修复：正式响应文件读取受同一期限及16 MiB完整私有保存上限约束；O3的v2非已审处置按原run/question/task/producer聚合，完整已审任务仍按正式身份去重。Provider稀疏81 MiB文件/64 MiB隔离堆在46391复现OOM；O3同局部任务名的两个上游在13196复现丢一条义务。8129的20项直接回归全部通过，旧三参数Provider与历史分支保持原行为。最终质量6695 exit0（122秒）：767个Java文件格式清洁、应用toolchain17、SpotBugs零发现/错误、PMD通过，UT/IT按范围跳过。最终同代码的inspect及四个正式artifact查询均exit0：ontology450,285、coverage36,755、review56,328、source-index80,195字节，分别与已安装文件逐字一致，92个来源映射闭合。模型scope抄错不由Java猜改，不从拒绝稿拣选局部定义；此工程验收不承诺业务零错误。

## 历史基线：此前真实零模型检查

以下保留此前批次与检查的实际记录；其当时未完项不能覆盖上面的当前O0–O3结果。旧产物本身保持原样。

| 检查 | 实际结果 | 不能据此声称 |
| --- | --- | --- |
| 历史R4→正式O0 | O0 `analysis-run:e577ec905ce2128860907f0fdcc54b84a90abbb3108892c7388d47e9a6f09335` 的prepare/inspect/artifact均exit0；FINISHED/COMPLETED；339入口、15,252内容别名；Corpus查询13,227,457字节 | 该历史R4已补齐新前端上下文；实际模型已理解；输入适合任意上下文窗口 |
| 新前端采集 | R1 `analysis-run:a07a4ee88f8dc5a37fdaa60d1eb84e135fb331b3e99c9acec4887a22edaf8e2b` 正式collect-frontend与inspect exit0；FINISHED/READY | 全仓前端全部识别、静态地址是实际部署地址、页面事件之间已证明因果 |
| 新R1完整协议 | 同源helper处理307文件、91请求、14页面上下文；493,365输出字节；4,909毫秒；严格Java解码接受412条helper记录。已安装索引包含177源码单元、402组件使用记录 | 412条helper记录与已安装索引行数相同；正文或本体已审阅 |
| 新R4生产保存及查询 | 正式assemble-materials exit0/COMPLETED/READY；inspect exit0/FINISHED/READY并列出严格v3/v2查询键；ENTRY_EVIDENCE_INDEX_V2查询exit0，返回339入口、14页面上下文；采购建单的ENTRY_EVIDENCE_V2单入口查询exit0，包含55前端单元、8页面上下文、129 Java方法和10 XML语句。coverage原始文件为1 HEADER＋91 REQUEST_COVERAGE＋14 PAGE_CONTEXT_COVERAGE。入口合计153,867,454字节、目录217,742字节；最大入口3,781,511字节 | 把全部入口文件作为模型输入；每一条候选关联都确定；三例本体已经通过 |
| 新R4→正式O0 | O0 `analysis-run:38e5e04b2be6fb15a1df911001129a9258aa702013aac3f1be38e9e7deb8caf9` 的prepare/inspect/ONTOLOGY_CORPUS均exit0；FINISHED/COMPLETED，canContinue=true，0模型派发；339入口、15,404内容别名；查询13,373,791字节；schemaEvidence=DISABLED | 13 MB目录会全部进入一次模型请求；DDL参与了三例；本体已识别或已审 |
| 同源R2/R0补读的新O0 | `analysis-run:f68d9b1aaf39a73626fea6aeace49ee005297baf2ed9197658bb69a8d4022455` 正式prepare exit0，FINISHED/COMPLETED，canContinue=true、problems为空、0模型派发；规则`ontology-model-projection-v2`，339入口、15,726内容别名，保存13,754,630字节。`parseHomePriceByLimit`的完整声明候选为U11052，含E316用途；旧O0不改写 | 目录全部送模型；补读将原调用冲突变成确定边；模型已理解权限或三个样例已验收 |
| 限制继承修复后的新O0 | `analysis-run:0785b1a0dd08b49b15a3f7f2d41c9264989777cd2dc601ea8e84bc40ba3005df` 正式prepare exit0，FINISHED/COMPLETED，canContinue=true、problems为空、0模型派发；正式ONTOLOGY_CORPUS查询exit0，339入口、15,726别名。三题清单的每个实际U/E用途均存在，missingScopeUses为空；U11052仍是保存的唯一辅助声明候选，保留两入口用途；f68不改写 | 目录或别名核对等于实际提取/审阅已经入模；选材已经自主化；三个本体已识别 |

前端修复限定于已有模式：Promise观察使用完整AST范围；已有三参数保存封装支持唯一顶层Promise表达式；跨mixin完整单元使用其实际所属文件。14项直接Node测试通过。没有创建JS解释器，没有放宽源码范围或唯一身份校验。

## 接线修复及剩余验收边界

以新R1和旧R3执行正式assemble-materials时，运行`analysis-run:3d27b15b2f9cba0d87d55ceb88cc599816c0475f513f3b69d2320e7a9f8ce05e`返回exit2/`ARTIFACT_POLICY_MISMATCH`。保存的运行元数据是FAILED，没有run-output或成功R4 receipt。随后正式inspect本次返回`TECHNICAL_ARGUMENTS_INVALID`；这不是可查询报告或成功产物，不对它补写报告/回执。

源码已定位：初始准入选中了保存上游的准确策略，但执行中的Java/持久化reader及发布前上游检查使用了目的R4的新策略store。旧R2/R3保存策略v2与新R1/R4目的策略v3不同，严格校验因此拒绝。有限修复将各实际读取接到准确上游owner的保存策略，不改写旧receipt，不合并策略，不删除校验，不重跑R2/R3。混合策略直接回归已核对RED 1/1/0/0（4.234秒）、GREEN 1/0/0/0（4.763秒）和整类10/0/0/0（11.431秒）。

修复后的实际R4 `analysis-run:1bcd11687fd7ab082d6b7a51c5218758bb0d60af0677e8d34f717d702e4b6744` 正式assemble-materials exit0，COMPLETED/READY。旧查询键ENTRY_EVIDENCE_INDEX不适用于新v2，曾被拒绝；准确owner及严格查询分派修复后，直接回归1/0/0/0（6.226秒），实际inspect及ENTRY_EVIDENCE_INDEX_V2均exit0。单入口必须使用ENTRY_EVIDENCE_V2和目录返回的完整entryId；控制器一次抄错ID的调用在参数准入被拒绝，不作为产品缺陷或证据缺失。

这些是框架读写接线问题，不属于模型幻觉或JDT误识别。当前目录、单入口和新O0的正式查询已通过；不将查询通过说成三例材料已经足以支持全部判断，或三例本体已经完成。

## 正式运行与模型边界的工程检查点

目前已核对的正式runtime整类为21项，0失败/错误/跳过，74.339秒；技术四操作10项，0失败/错误/跳过，12.984秒。此前明确选定32个直接相关Java类，最终XML报告合计216测试、0失败/错误/跳过；四个直接Node文件17项全部通过。首次SDK六项因沙箱禁止绑定本地HTTP端口报错，同类在允许环境下重跑6/0/0/0（2.120秒）。这是分次验证后的最终报告合计，不伪称首次216项命令全绿。最新压缩直接回归已核对：材料13项＋typed合同8项，共21/0/0/0（Maven 44.321秒）；这是直接受影响类的增量验证，不冒称32类重新全部运行。便携配置示例由实际读取器测试，整类7/0/0/0，其中新增示例加载测试没有认证或创建Provider。

发布前复审发现两处真实遗漏，并已按原合同补齐：已审`ANALYTIC_EXTENSION`及结构化未知原先未进入`ontology.json`根索引；O1私有/O3公开覆盖原先把前端请求和选定DDL文件分母写成0。针对前者，新增直接测试先在空索引处RED，再验证定义全局编号、任务/审阅身份、嵌套未知、顶层未解引用及逆序输入稳定性。针对后者，正式R4→O0→O1→O2→O3夹具先证实一条前端请求和三份DDL被写成0，再验证修复后均记为1/3；不能解析或未匹配的DDL仍留在分母。窄复审另发现历史R4 v1的覆盖行没有v2页面上下文字段，第二个直接测试先证实两条旧请求被误报为0，再验证修复后为2。旧行只参与计数，不凭空成为可反查页面关联。最终直接相关四类合计28项、0失败/错误/跳过。两处遗漏是确定性发布缺陷，不是模型解释错误；没有改写历史产物或旧MD路径。

密集调用投影经过限定独立只读审查：没有Critical/Important问题。历史v2没有新增逐字golden测试，该项作为Minor披露；当前v2原分支未改，仍使用已有直接历史回归，不声称新增了字节级golden。

最终静态质量首次执行在Spotless阶段exit1。该次报告57个Java格式违规；对随后当前代码的只读formatter核查列出63个需格式化路径，全部属于本计划修改且与原790文件保护清单交集为空。机械格式化只作用于这些路径，初次逗号路径过滤没有选中任何文件，不计作修复。格式修复后的十类直接回归90/0/0/0、正式runtime21/0/0/0及旧CLI/Markdown/只复用回归8/0/0/0已经核对实际XML报告。

第二次质量命令通过Spotless（759文件、0项需修改）和编译，随后在SpotBugs阶段exit1：41项Medium发现、0分析器错误。逐项源码核对区分出6项未用局部值/私有委托、一个公共FormalState构造入口未防御复制八个列表，以及19项候选不可变值误报。公开FormalState的输入可变列表反例实际RED 1项失败；构造器对八个列表复制后同例GREEN，其余直接覆盖的15类回归118/0/0/0。未使用私有代码清理保留原本必需的V2来源/回执重开；精确例外只涉及已核对不可变的具体记录类型和实际EI模式，没有屏蔽整个包、FormalState或未用代码检查。发布修复及历史v1计数修复后的最新同状态质量命令再次exit0，Spotless 759项无修改、SpotBugs 0项/0错误、PMD通过；原保护清单790文件再次核对无变化无缺失。最终质量通过仅说明工程检查通过，不能代替下面的客户模型质量验收。

## 真实材料预检（零模型）

正式Java Corpus、ScopeReader、ReadingCoordinator和typed请求builder读取新O0及明确保存的E/U选择，不由宿主Agent复制源码或填写本体答案。

| 问题 | 已选用途/正文单元 | model packet字节 | callContext字节/调用位置 | 初始OBJECT完整请求余量预检字节 |
| --- | --- | --- | --- | --- |
| 财务关联 | 27/26 | 117,034 | 42,052/71 | 195,627 |
| 采购来源明细 | 22/28（包括实际页上下文依赖） | 389,895 | 218,665/348 | 468,562 |
| 期间统计 | 12/12 | 70,476 | 41,511/67 | 149,178 |

初始OBJECT封套包含实际默认Prompt、Schema、模型声明及65,536字节响应余量，通过已配置1MiB字节检查。后续依赖任务需要实际已审对象，REVIEW需要实际草稿；这些输入尚不存在，不能预写为全部通过。上表不是token计数或任意小窗口能力证明。

实际采购包揭示密集重复candidate/observation说明。有限修复仅对完全相同的投影目标/观察建立CT/CO字典，每位置保留有序引用，源码、参数、状态和不确定性不删除；实际发送投影也纳入任务身份，防止投影变化复用旧结果。新增两项直接回归的首轮结果为21测试、2断言失败、0错误（30.621秒）：缺少callEvidence字典，及仅实际投影改变时formalJobKey未变。两项都是预期产品RED。修复后同两类21项全绿；中间两处test-only断言形状错误分别更正，没有放宽保存/分类断言。完整中性源码、两入口各自用途、每位置状态、字典解引用的有序重复及v2不变均由直接测试覆盖。

Root重新运行同一零Provider正式Java预检，exit0。以下调用上下文体积**包含字典开销**，不是只计算缩短后的行：

| 问题 | 新model packet字节 | 与原包相比 | callContext＋字典字节 | 调用位置 | 初始OBJECT完整封套字节 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 财务关联 | 108,837 | 减8,197（7.0%） | 33,520 | 71 | 187,430 |
| 采购来源明细 | 318,608 | 减71,287（18.3%） | 147,043 | 348 | 397,275 |
| 期间统计 | 62,110 | 减8,366（11.9%） | 32,810 | 67 | 140,812 |

对照前后六任务的实际输出，已选用途数量、全部正文单元的投影字节及调用位置数量保持相同；同一R4/O0及选择清单未变。完整正文逐字保留另由源码实现及直接测试核验，单凭相同字节数量不当作相同内容的证明。三个初始OBJECT封套仍通过1MiB字节检查。采购包仍约311KiB，不能称它已适配任意小token窗口；本次只是有限无损压缩，不新增摘要器或解释器。依赖任务及实际REVIEW仍必须按其实际对象目录和草稿预检。

### 实际模型请求的证据审阅

下表检查的是已保存的**实际EXTRACT请求**，不是仅查看R4里有没有源码。审阅请求中的`readingPacket`另与同任务提取请求作规范化JSON逐项比较：财务ACTION、采购ACTION、统计ANALYTIC三组均相同；三组REVIEW也都包含实际`actualDraft`。这证明上述原文在这两次调用间没有被换掉，不证明模型一定会解释正确。

| 具名判断 | 请求中实际提交的关键两端与限制 | 本次材料结论 |
| --- | --- | --- |
| 财务单据与业务单据、金额及欠款 | ACTION包有16个完整Java方法、5份SQL分析及5段Mapper XML；`saveDetials`保存业务单据编号，`getFinancialBillNoByBillId`/`getFinancialBillPriceByBillIdList`查询，`getFinancialBillPriceByBillId`在分组求和后取绝对值，`updateLastDebtByBillId`执行欠款减法；XML保留`bill_id`分组、删除过滤和动态`idList`。 | 对这些具名连接与运算，建立端、查询端、后处理及动态限制均已提交。若输出漏掉欠款效果或改错绝对值顺序，应列为模型误读/遗漏，不归咎于没有给这些源码。不能据此证明所有财务场景均已覆盖。 |
| 采购来源明细编号的建立与保存 | ACTION包有28个单元，前端完整函数`S27`含`info.linkId = info.id`，后端`saveDetials`含`depotItem.setLinkId(rowObj.getLong("linkId"))`，Mapper XML保留`link_id`；页面实例与入口用途分开。 | 对“来源明细ID怎样进入后续明细linkId”两端原文已提交。模型若把主表编号当成明细编号是模型问题；但静态材料不能证明用户真实执行了页面动作，未读分支仍不得凭名称补全。 |
| 期间采购/销售统计的粒度、筛选和金额 | ANALYTIC包有10个完整Java方法、1份SQL分析、1段Mapper XML；包含`getBuyAndSaleStatistics`、`updateStatistics`、`getCreatorArray`、`getForceApprovalFlag`，XML含`distinct dh.id`、金额列、日期/类型/状态/创建人及删除过滤。 | 这些具名取数、过滤和后处理的原文已提交。`parseHomePriceByLimit`仅在调用上下文出现，**没有其方法正文**；权限遮蔽的具体规则不能据此宣布完整。该项是明确的材料未提交范围，需保留未知或补入原文后再审，不能算模型幻觉。 |

审阅的合格条件是每项所声称的业务事实均能在本次入模原文中找到对应建立端/使用端、条件及限制。现有可追溯引用和相同阅读包只证明材料的可核查性；上述统计权限辅助方法的缺口说明不能把“有R4证据”直接等同于“已交给模型”。

该辅助函数在固定R0的`RoleService.java`第241行确有正文；本次R2索引里对应调用位置保存的是`NAVIGATION_CONFLICT`、`targets=[]`及一个未确认声明观察，R4统计入口没有该方法正文。不能把未确认观察升级成确定调用边；若要核验权限遮蔽，只能从同源R0按明确位置读取并标明“待核对的辅助源码”，或保留这项未知。此处是实际材料接线边界，不要求重跑JDT，也不能只在报告里补人读到的答案。

这些测试使用中性fixture及scripted Provider，不是客户模型质量或自主选材验收。实际ChatGPT订阅适配器不能强制逐请求输出token上限，记录`NOT_ENFORCED_BY_ADAPTER`；响应字节、请求数和超时限制独立执行。不能用字节数声称已证明token窗口。

## 首个真实任务及离线修复

- 固定新R4/O0上，首个真实O1运行`analysis-run:d27c47adf1aa2abe56e684b627b013f0988d70c9f484f648b70b2a44b334fd51`对财务OBJECT实际派发EXTRACT、REVIEW各1次，随后以`ONTOLOGY_FORMAL_RESPONSE_INVALID`结束；其余五个O1任务没有派发。原始输入、输出和校验记录均留在私有journal，没有将失败结果当作已审定义。
- 初次REVIEW原始响应的66项校验诊断包含17项Schema（Property声明ID误写为`O1.P1`）、25项随之产生的Property引用、3项本次对象误列为已审known引用、21项corrections账本与实际定义差异不符。这是结构记号与账本问题；不能由此断言财务业务解释正确。候选中对象变体引用同一包本次对象的合法形式也曾被校验器拒绝，该项已作直接测试和窄修复。
- 使用**已保存的相同两份真实响应**进行零模型离线回放。最初手动机械变换的回放保存了`REVIEW_VALID`。将这些变换移入正式代码后，再以**未变换的原始两份响应**回放，运行`3d62ed89e7d093048151a440a3d9bf138eb4fc1613c270a804cc6805e77cfc14`保存`formal-typed-review/success.json`与`reviewed-result.json`；后续任务因回放Provider只有两份响应而按预期停止。这证明首个已读任务的原始记录可被正式代码规范化、保存和重开；不是正式三例成功结果，也未新增模型调用。
- 正式实现保存原始REVIEW、规范化版本及事件；完成结果重开时从原始响应重算，并要求与私有记录逐项一致。直接测试先发现“即时校验通过但重开沿原始响应重新校验”的遗漏，修复后相关两类26项均通过。规范化不改业务字段、不修正模型漏读的金额/条件、不追加REVIEW。
- 对离线重开的首个财务对象作有限人工核查：模型把`BigDecimal`填入金额Property的`unit`，而同一字段的`dataType`也为`BigDecimal`。程序类型不能证明业务币种/量纲，不能将这份对象视为业务质量已通过。通用OBJECT/REVIEW Prompt已明确类型与业务单位区分；不为客户源码预置币种或业务答案，下一次真实结果仍需核验。
- 当前代码状态的正式runtime直接回归22项通过；静态质量命令在只修复本轮五个Java文件的格式后exit0，Spotless 760文件无需再修改、SpotBugs 0项/0错误、PMD通过。原790个保护文件逐项重算摘要，0缺失、0变化。这些是工程结果，不代表三例本体语义已经通过。

## 历史O1批次与问题分类

后续真实批次`analysis-run:63a1ee5d7578b53ef27289afcfbbbcace0b5f24234841c785c53340391f83d07`中，财务OBJECT/ACTION和采购OBJECT/ACTION共4个任务已完成EXTRACT＋REVIEW、保存并重开。统计OBJECT在该批次被拒，ANALYTIC未派发；整个批次为`PARTIAL`，不能作为三例完成。已审结果仍须按原文核验：财务ACTION正确写出`abs(sum(each_amount))`，但一项欠款更新操作漏写`effects`；采购OBJECT把一项新增保存操作误列为对象。这些是**已提交相关证据后的模型结果问题**，不是工程结构通过即可放行的正确本体。

统计单题批次`analysis-run:f7746d16dae5e083b72598f2efd58efef5800e3c70bbb7f314dac5659436adee`已审OBJECT通过，ANALYTIC审阅仍把维度D键填入指标的Property粒度键。零模型定向测试后，程序仅在该D键确为本次维度且已列于同一指标`dimensionRefs`时移除重复位置；业务表达和未知不改。下一批次`analysis-run:751945aaedc63fcea29ffbefcd84573ab79f1a7f01682a771b536c85e32fb7bb`的统计OBJECT候选只定义`O1`，但未解决项已引用从未定义的`O2`；审阅稿仍只定义`O1`，并**新增**一个对象变体条件指向`O2`。严格校验正确拒绝，不能删除该条件中的O2来伪造成功。五版无歧义结构记号规范化及历史回放均有直接测试；最后一次直接测试和质量检查exit0，但这不等于统计业务识别完成。

这里显露两项不同未完事项：一是统计权限辅助实现尚未入模，属于材料缺口；二是最终输出引用未闭合，不能机械修正为某个业务端点。仅由Java重新分配编号不能修复“模型删掉定义却保留引用”，因此本轮先保持现有编号合同，收窄OBJECT职责、明确REVIEW按最终定义检查所有嵌套引用。实际草稿和已审目录已经提供真实定义清单，不再增加重复表或端点推断器。严格校验不放宽；这项通用Prompt修正不保证模型一定遵守，不能当成引用问题已彻底消除。原已保存批次保持原样。

随后按原三题清单机械拆出的**财务单题**独立运行`analysis-run:8bca5c816edf3c91e3d67c24046632641fe6809452241d34371c530ddc544d36`，4次真实请求，O1 `COMPLETED`、`canContinue=true`。ACTION审阅稿明确保存来源业务单据ID写入财务明细`billId`、按`bill_id`分组汇总后取绝对值，以及`lastDebt = debt - financialBillPrice`，其实际提取/审阅阅读包一致。OBJECT审阅稿同时把“本次欠款”“最终欠款更新”列作对象，分类仍需按定义合同核验；O1结构通过不等于本体语义全对。

**采购单题**独立运行`analysis-run:069f25e4648027605961de7c5a43a8351dc6fd45a4a76b9801baabc9e160cf8f`，2次真实请求，O1 `PARTIAL`、`canContinue=false`。实际请求保留`S27: info.linkId = info.id`。OBJECT首稿定义了`O1`至`O6`，其中`O3`确实存在；审阅稿只保留`O1`，但两项来源关联条件仍指向已删除的`O3`，留下两项REFERENCE诊断。审阅提示已经要求删除候选定义时同步清理`unresolved.relatedLocalDefinitionRefs`，却未明确点名嵌套的`variants.conditions.targetObjectRefs`；审阅结果两处均未清理。此失败不是来源端没送入，也不能由Java猜测`O3`应改指哪个对象。它与统计单题的未定义O2同属最终引用未闭合，但形成方式不同；不继续以相同合同盲目重跑。

当前OBJECT Prompt明确“普通问题是共享背景，只返回业务对象，不把动作、请求链或指标当对象”；REVIEW Prompt新增最终集合的全引用检查及“有依据保留完整定义、缺端点保留条件/来源与未知”的处理要求。定义Schema、原文包、局部编号算法和最终validator未改；实际Prompt内容照既有规则进入新任务身份。直接历史/审阅三类回归本次33项通过；新增正式OBJECT/REVIEW安全类3项通过，验证保留对象的合法引用、删除对象后的非法嵌套引用不被程序猜改、原稿及失败记录保存。新类首次有一项失败和一项错误，原因是测试Provider的模型身份与fixture不一致；修正测试身份后3项通过，不把首次失败称为产品RED。保护清单790文件无变化。这些scripted测试不是模型遵守提示词的证明。

第四个新增直接测试进一步验证：OBJECT变体条件可以保留完整条件、表达式和S1原文引用，用空的可选`targetObjectRefs`及明确unknown表示端点未建立；严格最终validator接受此结果，没有猜成O1/O2。这不改变ACTION操作必须有目标的要求。最新四类一起执行的实际报告合计37项、0失败/错误/跳过；本次尚未重新执行完整静态质量命令，不借用此前的质量结果声称新增测试已通过最终交付检查。

随后采购单题新运行`analysis-run:e79070803b025e7fd4bedfd5cf36d4fc80ff619c83cae79ad857548be90562da`使用更新后的通用Prompt，正式CLI exit0，O1 `COMPLETED`、`canContinue=true`，实际派发4次。OBJECT和ACTION的实际EXTRACT/REVIEW均已保存，最终两份`REVIEW_VALID`、诊断为空；已审对象为采购单据主表、采购单据明细、来源单据，后两项保留部分定义和具体未知。本次没有删掉O3后仍引用O3，程序没有为模型改指向。ACTION把`info.linkId = info.id`表达为选择来源明细形成关联标识的效果，并引用真实已审对象；同时保留来源实体、更新接口及约束未确认。该单次结果说明更新Prompt后采购引用结构本次闭合，不证明业务定义全部准确、任意模型均遵守或三例本体计划通过。此真实运行准入阶段的读取栈位于已保存R0私有事实的规范化校验，不能把这段准备时间说成模型等待。

新增安全测试文件的限定Spotless检查exit0。尚未对本次新增测试重跑完整静态质量命令；最终PR仍待整个计划的样例核验和交付检查。

同源R0补读现在已经接入新的正式v2 O0路径：从R4准确上游重开原owner的R2完整声明，用同源R0恢复正文，保存新Corpus规则及别名。2026-10-04定向测试已证明选中的辅助方法完整正文实际进入EXTRACT和REVIEW，未选哨兵正文不进入，原未确认调用及定位限制仍可见；旧v1 O0字节和元数据表达不变。这是scripted正式请求边界的工程结果，**固定客户三个样例尚未使用这份新材料完成真实模型验收**。v4紧凑表达及最终查询收尾仍在实施，不以宿主Agent直接读文件替代材料交接。

上述旧批次曾未完成统计合法定义及跨入口发布；当前六个O1和三个O2任务已执行，O3保留采购关系拒绝并发布有效部分，准确结论以前节为准。最后边界修复与最终质量已完成，实现PR正在交付。真实请求数继续记录，但不是单独停止理由。三例仅验证具名局部范围；自动选材与全仓本体仍未验收。

可选DDL有限保存/重开/消费、共享Skill有限前向检查和便携配置加载已通过直接工程检查，不列作尚未实施；但不宣称固定客户DDL、任意宿主或客户模型质量已实测。
