# 实现经验与待讨论问题

状态：2026-09-17 问题归档，仅依据已有设计、保存产物及交接记录。用户已确认目前文档至少可以看懂，业务也联系起来了；这一认可与具体准确性、容量及结构问题并列保留。本文不是新的实施计划，不批准重跑三例、全仓生成、扫描、模型调用或自动修复。

本次 Step01–05 重构的设计验收和后续重构验收都不调用 Step06 或以后步骤。下面引用已有 Activity、Step07 或历史报告，只为保存经验和说明下游接口风险，不把这些历史结果当成本次重构已验证的输出，也不触发 Activity 再生成。

## 阅读口径与当前事实

- “已修／已实现”指原记录已给出实际接线、直接验证或完整保存证据，不重新列为待开发；本文未重跑这些验证。
- “仍待讨论”指已知问题或尚未获批方案，不等于批准修改合同、响应或提示词。
- 各项“最小后续核对”只是以后讨论该问题时所需的最小证据，不是本次执行清单。涉及新输入、模型或替换候选时，仍须对应授权。
- [三例实际验收](cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)是最新真实状态：采购缺最后核对，销售未保存合法完成任务，调拨程序完成但人工准确性未通过，批次已 FAILED 并排空。其“步骤0–6完成”是历史实施计划的任务编号，不是说业务三例通过，也不是本次分析 Step01–05 的验证结果。
- [more-findings.md](more-findings.md)保留 2026-09-15 的原始讨论字节。其当时的候选内阅读／XML未入包限制不得覆盖后续已实现事实；现状以[实施状态](cross-object-process-reconstruction/implementation-status.md)和[交付记录](cross-object-process-reconstruction/delivery.md)为准。

## 1. 状态、否定词和金额不能只做字面翻译

**状态：具体错误仍待讨论，阅读水平已获认可。** 研究稿的 WRITE 把“已审核”改成“未审核”，把支付订金／本次付款混成应付金额。历史财务稿的正文保留审核条件，transitions 却把审核／反审核名称写反。最新销售返回又把库存许可中的否定词写反。这些不是来源编号越界，结构通过也不能发现其业务含义错误。

影响：读者可能误判允许办理的状态、资金用途或库存例外。相同字段在不同页面用途不同，不能统一翻译后当成通用事实。

最小后续核对：先对已有稿逐处对照同包原文及正文／结构字段，列出“用途或条件—原句—冲突字段”，不扩成全仓审核，也不以只补编号宣称准确性通过。

原记录：[研究对照](cross-object-process-reconstruction/experiment-comparison.md)、[详细设计§1、§6](cross-object-process-reconstruction/business-reasoning-and-writing.md)、[三例销售问题](cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。

## 2. 通用方法规则必须收窄到实际业务用法与配置分支

**状态：已有规则信息，仍有适用范围和字段一致性问题。** 最新销售稿把原单进度回写扩大到销售退货和零售，实际分支不包含这些单据。调拨阶段拒绝条件遗漏“强审核开启且未审核时跳过保存阶段库存检查”例外，结束结果又把强审核开启后的库存刷新写成无条件；同稿其它规则已保留例外。正常调拨删除条件还混入通用入库序列号分支，属待讨论的用法表达问题。

影响：引用完整通用方法并不表示其中每个分支适用于每个子类型；单个正确规则也不能抵消阶段或结果中的相反说法。

最小后续核对：用已保存销售／调拨包，逐处核对触发事件、单据用法、状态与配置例外，并同时对照正文、条件、规则和结束结果。明确错误与表达待办分开，不新增行业规则引擎。

原记录：[三例实际验收§2、§5](cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)、[详细设计§6](cross-object-process-reconstruction/business-reasoning-and-writing.md)。

## 3. 最终核对必须检查实际写作全文，但三阶段不是准确性保证

**状态：DRAFT → WRITE → 最终 RULE_REVIEW 已实现，真实三例未通过。** 研究中先核对事实再写作，WRITE 仍引入错误，因此顺序已调整。生产最终核对已收到同一完整外层输入和实际两份稿件；私有完整三阶段保存、精确复用及确定性正文渲染也已接线。调拨最终核对虽给出纠正记录，人工仍发现配置条件遗漏。

影响：不能把 WRITE 当 reviewed，不能把模型纠正清单或最终 parser 成功当业务准确性已审。最终核对之后再润色还会重新引入未经核对的内容。

最小后续核对：沿已有原始请求确认最终核对实际看见包、DRAFT 和 WRITE；人工核查已命名的错误及对应字段。后续若批准替换候选，不续接半轮、不添加第四次润色。

原记录：[详细设计§6–§8](cross-object-process-reconstruction/business-reasoning-and-writing.md)、[实施状态§1–§4](cross-object-process-reconstruction/implementation-status.md)、[三例终态](cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。

## 4. 查询 context、办理成员和局部编号必须保持不同含义

**状态：成员／context区分和最终关系校验已有；悬空查询用法仍待讨论。** 最新销售最终返回定义6个办理用法，却在查询阶段和支撑列表引用7个未定义用法；真实候选为6个 member、8个 context。历史消息稿也有2个未定义支撑用法。它们不是源码找不到，不能把 context 全部强行补成办理步骤或默默清空列表。

历史租户、商品属性和操作记录的有限离线修正已经在明确授权下完成，派生身份及不可覆盖差异清单保留；该权限不能套用到新的销售／消息问题，也不构成生产自动修复能力。

影响：来源／statement allowlist合法仍可能存在内部业务用法关系错误；错编号阻止合法完成任务，修好编号也不等于语义通过。

最小后续核对：对已有响应列出全部定义和引用，分清“仅供理解的查询”与“实际办理用法”；无唯一合法映射时保留失败。如何表达支撑查询须先讨论，不补造定义。

原记录：[三例销售问题](cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)、[历史修正及消息问题](cross-object-process-reconstruction/experiment-comparison.md)、[有限编号诊断](../../progress/reading-attribute-review-diagnosis.md)。

## 5. Prompt、Schema与parser的接口约束必须一致

**状态：已定位的 CHECK 合同及中间结构门禁已修，不作为新待办。** 历史 CHECK Schema 允许空 activityUses，parser却要求非空最终成员；现已要求完整最终集合、minItems=1、完整 context 集合及 nullable 文本继承。三阶段 fresh 和 matching reuse 的实际 DRAFT／WRITE 也已按候选 Schema校验，不能由合法最终响应掩盖缺字段、错类型或错误包装。

影响：接口包含必填、允许值、完整集合、继承和错误模式，不只是一份类型签名。结构门禁应拒绝确定性损坏，但不能冒称自动判断了中文条件或规则适用性。

最小后续核对：只有相关接口再次变化时，核对实际发送 Schema、Prompt完整集合要求、parser及保存／重开使用同一合同；沿已有损坏断言维持 fresh／reuse 的明确错误。不把业务覆盖或中文蕴含门禁提前塞入中间阶段。

原记录：[交付记录的 CHECK 合同修正](cross-object-process-reconstruction/delivery.md)、[详细设计§7](cross-object-process-reconstruction/business-reasoning-and-writing.md)、[中间Schema修正](../../progress/intermediate-schema-green.md)。

## 6. 自主召回相关材料不等于按一个问题聚焦

**状态：系统认识、调查问题、保留／移出／一次补读及首读预览已实现；自主成稿质量未闭合。** 研究中模型从 README、326导航及冻结目录自主提出方向，但最终自动包有32 Activity、80文件，没有送入后续 DRAFT。生产现已在同一次全局选材中返回自由系统假设，并由 CHECK 明确选择首批保留片段和补读；大文件首读预览与已有 M10 定位导航也已落地。

影响：不能把自主提出候选当自主端到端通过，也不能只用文件数量判失败。核心是实际问题是否得到关键材料、是否有无关展开、最终包能否容纳。

最小后续核对：先对已有三份 SELECT／CHECK记录逐个关联“调查问题—请求用途—实际保留／补读—未解决问题”，确认预览没有被偷偷恢复成全文。不得以固定行业分类、文件上限或 Java语义评分代替模型选择。

原记录：[研究事实](cross-object-process-reconstruction/experiment-comparison.md)、[详细设计§3、§5](cross-object-process-reconstruction/business-reasoning-and-writing.md)、[实施状态§2](cross-object-process-reconstruction/implementation-status.md)。

## 7. 文件被发现不等于原文已进入实际模型输入

**状态：旧 XML缺正文问题已记录，冻结 XML／Vue读取及跨候选补材已实现。** more-findings 核查的历史来源运行发现61个 Mapper XML、573个 statement candidate，但 M10 的 XML引用为0；这只能说明那一轮未给模型 SQL正文。后来阅读包已经实际取回 Java／XML／Vue原文，不能继续描述为“当前模型无法读冻结XML”或要求重做全部上游。

影响：目录命中、M10 locator、搜索片段或预览均不等于读过完整关键规则；关系／汇总口径只能依据实际进入包的材料解释。

最小后续核对：遇具体 SQL口径疑问，先检查已有包中的实际行段、全文／预览标记与限制，再决定缺什么。按疑问读取已冻结材料是已有 seam，不执行 SQL、不启动源码扫描或新建 SQL业务解析工程。

原记录：[more-findings§4–§5（历史）](more-findings.md)、[后续真实包变化](cross-object-process-reconstruction/experiment-comparison.md)、[已有读取能力](cross-object-process-reconstruction/implementation-status.md)。

## 8. 发现生命周期不是增加阶段，也不是最后归并拼接

**状态：跨对象阅读已扩展，无损归并已实现；完整长链效果仍须按实际内容判断。** 旧采购稿包含关联对象与分批状态回写等具体信息，但尚未建立确定“请购—订单—入库”顺序。保存原文已有转换映射、关联字段和进度回写线索，不能归因于代码完全无线索。最终归并只作处置、关系及合法无损去重，不编写新阶段或把不同阶段数组拼成生命周期。

影响：名称、共享表、阶段数、关联查询不能单独证明必经顺序、自动建单、因果或某次运行成功。反过来，也不应以缺运行时证据否认代码已明确的生成／保存行为。

最小后续核对：在已有正文中核对对象怎样产生、怎样被后续引用、数量／金额／进度怎样传递，哪些必经、可选、分批或回退；材料仅支持片段则如实保留。仍缺口径时指出具体材料，不取消无损约束。

原记录：[more-findings§1–§3（历史）](more-findings.md)、[旧小样最终核对](cross-object-process-reconstruction/experiment-comparison.md)、[详细设计§6、§8](cross-object-process-reconstruction/business-reasoning-and-writing.md)。

## 9. 机械编号与重复投影占容量：已有精简不能被说成待做

**状态：导航／Activity重复投影和Schema允许值共享已修；任务局部短编号仍只是方案。** 旧全局导航移除长 statementHandles、保留 statementCount；完整 Activity去掉两项合成重复字段，canonical statementDirectory保持；CHECK省略已完整提供的导航卡和不用的目录，长允许值通过本地 $defs／$ref共享。这些已有改进保留完整业务字段、数组顺序和选中原文。

最新采购最终请求仍有1048个长 statement句柄及14个长 Activity ID重复出现，其中 Activity ID共2595次。可逆局部短编号尚未实现，不能写成已修，也不为容量静默截断规则或替换公共身份。

影响：重复技术标识可能使最后核对无法启动；降低机械开销与删除业务材料是不同动作。

最小后续核对：先沿已有实际序列化请求区分业务正文、编号目录、Schema重复量。若以后批准短编号合同，最小证据必须含唯一／可逆映射、关系还原、未知编号拒绝及原始数据不变；本文不启动该改造或重跑。

原记录：[交付记录的导航／容量修正](cross-object-process-reconstruction/delivery.md)、[容量GREEN说明](../../progress/reading-capacity-green.md)、[采购只读定位](cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。

## 10. 容量、输出空间、费用和耗时是不同问题

**状态：逐阶段容量检查已有；采购最后核对容量问题未修。** CHECK和DRAFT可容纳不代表“原文包＋DRAFT＋WRITE＋Prompt／Schema＋输出空间”能容纳。采购最终请求两种编码估算为252524／264448 tokens，加该次实验输出余量后超过绑定环境观察窗口，所以最后一次未发出；两稿和未发送请求均保留。

这些是本地编码估算及当次窗口观察，不是服务端精确计数、框架固定容量、费用预算或收费证据。实际12次请求全部返回，采购未发送的第13次不能计成模型调用。

影响：用费用预算限制材料范围、降低窗口检查、删掉一稿或把输出字节上限当 token上限都会混淆接口条件。请求更少也不能据单次不同实验推出更快。

最小后续核对：讨论容量时使用各阶段真实序列化 input／Prompt／Schema和实际输出余量，标明估算方法与绑定环境；讨论成本须另有计费证据。本次没有测量新增费用、性能倍数或修正后可通过的概率。

原记录：[三例实际调用与采购容量](cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)、[详细设计§7](cross-object-process-reconstruction/business-reasoning-and-writing.md)、[实验容量与时间边界](cross-object-process-reconstruction/experiment-comparison.md)。

## 11. 模型任务失败不应触发上游重复扫描或材料重建

**状态：材料检查点、独立模型批次、双重产物所有权及显式已审任务复用已实现。** 有效 M10、326 Activity、冻结文件和旧目录可以重开；旧来源运行整体 FAILED不自动使已完成材料失效。材料属于 sourceRun，新模型输出属于新 batch；Step07 Prompt变化不要求 JDT／Builder／Activity再生成。旧目录作为输入资料与任务结果精确复用是两种语义。

复用只接受完整合法任务及匹配实际内容、来源映射、Prompt／Schema／任务序列／模型绑定。三阶段过程要求全部三稿；历史 pair、孤立 DRAFT／WRITE或新问题不匹配结果不能冒充已审三阶段。

影响：为换请求身份反复 JDT或删除 journal既浪费计算，也破坏来源／失败记录；相反，忽略指纹强制复用会把旧输出伪装成新合同结果。

最小后续核对：沿已有 receipt与 reusedFromModelBatchId链核对输入、所有权和完整性；真实三例已有上游重建调用0、调拨导出新模型调用0的记录。新输入只使依赖结果失配，不默认全部重建。

原记录：[详细设计§4、§7](cross-object-process-reconstruction/business-reasoning-and-writing.md)、[实施状态的已有保存／复用](cross-object-process-reconstruction/implementation-status.md)、[active v0复用与恢复边界](runtime-recovery-todo.md#5-与-active-v0-的边界)。

## 12. 显式新执行复用不是同一 run自动恢复

**状态：fatal停止派发／排空保留已有；同一运行恢复明确 DEFERRED。** 已启动 Provider失败不能自动重试、切换服务或回放不确定请求；候选 fatal后停止新派发，已启动且自身前序合法的任务可完成授权阶段并保存。当前三例已排空，不能继续报告仍在运行。新批次复用完整已审 job是已有能力，不是 resume。

影响：把失败后继续新批次说成自动恢复，会引入未批准的状态机、worker接管、started裁决或终态修补；不能把延期恢复机制当这次重构缺项。

最小后续核对：只核对已有终态、实际请求是否仍活跃、完整结果与诊断是否保留。未来如需同一 run跨进程恢复，必须另行批准设计；本文不细化旧TODO或新增恢复接口。

原记录：[恢复TODO与明确禁区](runtime-recovery-todo.md)、[详细设计§7](cross-object-process-reconstruction/business-reasoning-and-writing.md)、[三例终态](cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。

## 13. 等待时长、超时与可诊断异常不能混为一谈

**状态：已有墙钟与启动诊断记录；部分旧 UNKNOWN根因仍不可还原。** 销售最终核对观察为825.946秒并确已返回；较久未返回本身不能认定异常，更不能当成模型质量错误。若真正达到已配置超时，则按实际进程／传输失败处理，而非自动续跑。本记录不声称本次三例发生过超时失败。

历史默认受限环境曾出现 PATH别名目录写入拒绝；正常权限预检及后续请求成功。先前三次 UNKNOWN的原始 stderr被适配器删除，不能完全反推原因；后来由私有外部观察器保留最多64KiB stderr，未改变生产 Provider。

影响：没有原始诊断时不能把 UNKNOWN定性为配额、模型或来源问题。墙钟包含子进程启动、等待与返回；并行阶段不能直接相加成批次墙钟。

最小后续核对：优先查已有实际开始／返回时间、终态、绑定超时和可用 stderr receipt；证据缺失就保持未知。区分预启动权限失败、发送前容量拒绝、已启动失败和合法返回，不为补诊断重放请求。

原记录：[三例耗时定义](cross-object-process-reconstruction/three-case-acceptance-result-20260916.md#3-实际调用和耗时)、[启动环境与stderr记录](cross-object-process-reconstruction/delivery.md)、[原批次终态交接](../../progress/cross-object-reading-coordination.md)。

## 14. 样本先行；可读、程序通过、coverage闭合与真实质量各自成立

**状态：用户认可当前可读与衔接；三例准确性、合法完成及全仓交付未通过。** 历史有提示三样本的完整 pair和可读导出已完成；无提示 B保留20个合法 pair及27个 CHECK，6候选未启动，消息结果被拒绝，尚无归并／正式发布。最新三例也不关闭326覆盖或安装伪全仓 publication；调拨离线导出 FINISHED只说明导出完成。

影响：导向样本成功不证明自主发现成功，coverage CLOSED、过程数、阶段数、引用数、CI通过或一次有限核查都不能代替业务理解。旧结果有表达差距，不得推翻用户后来认可的当前阅读水平。

最小后续核对：先交付现有样本及已命名问题，分别说明输入人工／自主选择、各阶段合法完成、实际业务错误和未执行范围。只有用户讨论决定后才考虑替换样本或全仓；样本许可不自动授权后续批次。

原记录：[用户反馈与当前三例](cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)、[历史A/B对照](cross-object-process-reconstruction/experiment-comparison.md)、[样本先行交接](../../progress/cross-object-reading-coordination.md)。

## 15. 构建／工具故障与业务失败分开，验证不得争用输出

**状态：已有隔离验证通过和工具链处置；历史竞争的写入归因不夸大。** 测试编译曾报未修改基础类不可见，诊断确认 IDE Java builder与 Maven共用 target/classes及target/test-classes，但原始竞争写入归因未独立闭合。后续交付记录保存了暂停具名 builder后同代码干净CI成功及恢复 builder的证据，不能把最初编译失败当业务流程失败。

格式化宿主兼容性也已处置：应用编译／测试保持 Java17，google-java-format工具宿主显式用 JDK21+。CLI复杂度问题后来PMD已通过，不再把只读诊断中的预估复杂度当最新测量或待修项。

影响：重型验证并发／编辑同时进行可能产生假失败；测试范围约束只限制执行，不代表 Maven不编译其它测试源。已有全CI结论属于历史运行，不授权本次全套重跑。

最小后续核对：以后相关改动仅做直接覆盖验证，串行占用唯一重型验证席位，保持应用与工具JVM分离；环境诊断保留“确认共享输出”与“未直接归因”的差别。本次文档任务不运行测试、构建或质量工具。

原记录：[构建只读诊断](../../progress/reading-build-diagnosis.md)、[CLI质量诊断](../../progress/reading-cli-quality-diagnosis.md)、[后续已通过与恢复证据](cross-object-process-reconstruction/delivery.md)。

## 本次归档边界

本文仅新增问题索引与状态区分，没有改代码、测试、构建配置、Prompt、已保存输入／模型响应或 publication；没有运行上游扫描、模型、Step06及以后、客户代码或验证套件。more-findings.md必须保持原字节。新优化仍停在讨论，历史运行、来源、稿件、纠正清单和比较产物继续保留。
