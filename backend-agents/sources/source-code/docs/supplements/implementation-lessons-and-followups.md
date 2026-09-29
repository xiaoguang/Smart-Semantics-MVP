# 实现经验与待讨论问题

状态：2026-09-29 按当前实现、实际产物及已批准的新技术设计校正。编号是问题身份，不是剩余任务数；历史已完成项保留结论，不再次列为开发工作。本文不授权工具运行、模型生成或自动修复。

本次范围是四个独立技术操作和按入口JSON，见[详细设计](../modules/technical-analysis/README.md)及[依次修改清单](../plans/technical-analysis-cli-and-vue-cleanup-design.md)。第16–19项源码准备原范围已经完成，19.2跨版本增量复用继续延期；20/22有限前端与原三命令已经实施，新拆分尚待实施；21.1/21.3、24/25及20的必要单元修正纳入新设计。21.2与七入口长等待只做受限核验，不许诺已经解决。第1–15项保存业务层经验，不纳入本次技术改造。

详细设计前的事实核对、冲突处理和仍需明确的选择，见[源码准备设计前核对](source-preparation-design-preflight.md)。第19.1项必要消费者排除与旧材料版本检查已按本轮fail-closed范围关闭：prepared reader、正式 Activity/过程旧材料门禁、M10逐次重开完整性核对、Java/Persistence消费者fixture和离线闭环均有直接证据。最终定向168/168和质量检查通过；资源上限停止枚举后的NEW全范围恢复边界已通过3项新增测试，实施计划步骤8按本轮范围关闭。

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

## 16. 源码接入不能只支持 Git，且建模期间不得修改输入

**状态：2026-09-26 普通目录接入已实现，最终定向168/168和质量检查通过；资源上限NEW恢复边界已复验。** 客户提供的源码可能是没有 Git 管理的普通文件夹，框架应能直接接入，不能要求客户先初始化 Git 或提交源码。

设计前旧限制：当时正式捕获入口只有 `capture-local-git`；`LocalGitCaptureRequest`要求完整40位提交号，来源注册、保存和读取也依赖 `commitId`、`gitMode` 等 Git 元数据。现在独立 `prepare-source` 已支持普通目录；此段只保留原问题依据。

预期能力：保留 Git 接入，同时支持普通源码目录。按明确范围保存完整文件及相对路径，以固定快照和内容指纹标识本次源码；Git 提交号和 Git 文件属性只属于 Git 来源，不作为无 Git 来源的必填条件，不伪造提交号。接入后复用同一套源码读取、JDT、持久化材料和业务分析能力，不另建业务流水线。

用户已确认的接入范围与系统约束：

- 符号链接不跟随，明确列出被跳过的链接。
- 不支持直接接入压缩包。用户自行解压后提供普通源码目录。
- 从源码接入开始到本轮建模结束，用户不得修改输入源码。普通目录不保证具有 Git 提交的一致性；违反该约束导致的结果不一致由用户承担。
- 后续任何一步检测到源码变化，仍必须返回可获得的检查结果，指出具体文件与源码准备时保存的内容不一致，由用户决定回到“源码准备”重新准备该文件，或明确排除该文件。不能把检测异常等同于不返回结果，也不能将唯一处理方式写成整仓重来。用户决定前不将变化内容当作已核验源码；重新准备或排除均不自动重跑后续模型，历史结果保留。
- 当前下游读取固定副本，不持续监控原目录。“检测到变化就报告”不表示保证发现所有外部修改；本轮不增加持续监控。

后续修改涉及源码捕获入口、来源描述与快照合同、注册及直接读取器、配置和对应测试，不是仅删除 `gitMode` 字段。下述确认事项已有详细设计，但不能将设计写成已经实现。

本轮已补齐并确认：普通目录默认除 `.git` 外全部纳入，依赖和构建文件不按名称自动过滤；允许明确排除无法列出的具名子目录，并在显式派生版本中继承。Git单文件重新准备只重取同提交的内容，不混合工作树或其它提交；原根路径变化须建立新来源。具体合同见[源码准备Modules](../modules/source-preparation/README.md)，不是待实现者自行选择的默认值。

验收目标：没有 `.git` 的源码目录能够固定保存，Java、XML、Vue 等范围内文件原文完整可读，并可供现有技术分析消费；原 Git 来源及历史快照仍可读取。验收不要求调用业务模型或重新生成已有 Activity。

本次更新设计/backlog，不修改生产代码，不扫描客户源码，不运行JDT或LLM，不自动恢复此前端到端计划。

## 17. 删除源码准备阶段的行索引

**状态：2026-09-26 新源码准备路径已删除，历史读取保留。** “源码准备”是本轮对原 Step01 的功能名称，不能继续把行索引的保留或删除写成待用户决定。

已核实：`VerifiedSourceIndexer`计算各行的字节起点，`VerifiedSourceIndexModulePublisher`与正式文件清单保存的是 `lineIndexDigest`，没有保存完整位置表。Step07 的 `FrozenProcessSourceCorpus`取得全文后自行计算字符行边界，再按行截取或返回搜索命中；它没有复用 Step01 的字节位置表。此前“Step07 利用 Step01 行索引定位原文”的解释不符合当前实现。

实施要求：移除该阶段的行位置表计算和新产物中的对应摘要元数据；改动前核对消费者、身份计算和历史读取合同，明确必要的版本调整。不能为了保留这项计算再建设持久化索引或新的消费链。

保留按文件和行号读取准确原文的能力、已有来源引用及历史产物读取。实际读取模块内部定位行的能力不是本项删除对象；不修改历史文件。本次只记录决定，不修改实现。

## 18. Skill 调用源码准备 CLI：本轮只做这一项

**状态：2026-09-26 正式源码准备CLI及模块内Skill已实现，最终定向168/168和质量检查通过。** 当前只将原 Step01 及其必需的源码接入整理为独立可调用操作。后续步骤尚未讨论，不拆分、不改造其 CLI，也不编写其 Skill 执行流程。

正式功能名称为“源码准备”，子命令名为 `prepare-source`。`Step01`仅用于迁移说明中对应旧设计，不作为新操作的名称。保留唯一 `source-analysis` 入口；可通过模块内薄脚本或 Java 执行 `SourceAnalysisCli.main()`，薄脚本不会自动构建或安装。

本轮操作合同：

- 输入：普通源码目录，或本地 Git 仓库及指定提交；路径和相关选项由配置提供，最终参数格式在详细设计中明确。
- 处理：Java 程序完成源码接入、固定副本保存和文件核验。现有源码捕获在旧 Step01 之前；新命令包含这两个必要操作，不要求用户分别执行。
- 输出：固定源码副本、文件清单、检查结果、问题清单和保存位置；完整性、异常状态和退出码见[数据合同](../modules/source-preparation/contracts-and-storage.md)。
- 用户已确认具名文件/子目录重新准备与明确排除；CLI携带基础版本及目标，参数、新版本保存和旧材料检查见[CLI设计](../modules/source-preparation/cli-and-skill.md)。这些准备及旧材料拒绝边界已实现，不表示已具备跨版本增量分析。
- 不做：不识别 Spring 入口、不启动 JDT、不分析 XML/SQL、不生成 Activity、不调用业务模型、不执行客户代码。
- 结束位置：源码准备结果保存并报告后结束，不自动执行后续步骤。这是本轮功能范围，不是规定未来流程必须每一步人工确认。

Skill 与实际执行者的职责必须无二义性：

- Skill 是供 Codex 阅读的操作说明，不是自己运行的程序。采用一个流程 Skill，目前只包含源码准备的调用、结果检查、报告和已确认约束；不为每个内部操作另建 Skill。
- Codex 按照说明确认输入、调用 CLI、读取程序实际结果并报告用户；不能仅凭命令没有报错或输出目录存在判断成功。
- Java/CLI 负责确定性的源码读取、核验、保存和问题报告；Skill 不重复实现这些算法。程序必须表达使用的输入、实际保存的输出、成功/异常/未检查范围，不能只给含义不明确的“完成”。
- 用户要求只做某项时不扩大；未来若另行批准连续执行多项，不能由 Skill 擅自变成“一步一确认”。当前仅获批源码准备，不能据此设计或启动后续步骤。
- 文档必须使用明确主语与对象，例如“Codex读取检查结果”“Java保存源码副本”“源码准备结束，尚未分析代码结构或业务”，不得写成“Skill自行读取”“整个分析完成”。

验收方向：Codex能按一份说明调用这一项操作，并从真实保存结果准确报告可用文件、具名问题和输出位置；后续步骤调用为0。启动脚本、字段、状态和退出码已实现；Skill只保存在模块内，不自动安装或自动启动后续任务。正式保存、查询、旧材料版本门禁与Java/Persistence排除消费者fixture均已通过；资源上限停止枚举后的NEW全范围恢复合同已通过3项新增测试。

## 19. 源码准备的异常处理：主路径已实现，资源上限NEW恢复边界已验收

**状态：2026-09-26 源码准备、旧材料门禁及Java/Persistence排除消费者边界已实现；资源上限NEW恢复合同已直接复验。** 对已明确的局部文件问题，用户可选择具名刷新或排除；但资源上限若停止枚举，未知尾部不能靠派生排除已知触发文件消除，必须调整上限或在请求中预声明排除后NEW全范围。最小下游检查已纳入，不等于批准后续算法改造或真实分析。

已核实的当前行为：六个讨论中的错误码均由现有代码实际抛出；`FrozenRequestAdmissionException`和`VerifiedSourceIndexException`仅携带错误码，没有逐文件问题列表。`VerifiedSourceIndexer`在文件循环遇错后退出，未返回完整部分清单；前置输入模块可能已保存，但不表示此前核对过的文件结果已经逐项落盘。正式Agent捕获异常后将本次运行标为 `FAILED`，不会形成完整Step01成功发布。

已明确的行为要求：

- 文件局部问题要返回具名结果；对其余能够安全独立检查的文件继续检查，区分可用、异常、尚未检查及用户明确排除，不能只抛一个字符串后使已知结果不可见。返回检查结果不等于宣布源码全部准备成功。
- 文件无法读取或内容不一致时，用户可以要求重新准备该文件，也可以明确跳过。跳过必须成为保存的文件排除决定，后续分析不再纳入该文件，而不是只在当次循环中临时忽略。排除不是删除客户文件，也不删除历史产物。
- 配置中的源码目录不存在时，不开始源码读取，返回具体问题。磁盘满或结果目录不可写时，报告保存失败和已知保存范围，不声称结果一定落盘。程序被强制关闭时不能保证返回完整报告；未完成输出不作为成功结果，保留已有文件，用户重新执行；不增加自动断点恢复。
- 本轮命令仍只执行源码准备，不自动进入后续分析。用户明确排除文件只是调整分析范围，不允许程序读取不安全路径、接受错误来源或自动重跑业务模型。

详细设计的实施核对项（字段与版本以模块文档为准）：

- 异常分类及信息：保留code，新增范围、路径、操作、说明、已知比较值、处置与allowedActions；数据合同定义固定类别和状态，禁止解析中文说明控制流程。
- 目录枚举与路径问题：根无法列出为阻塞；子目录无法列出继续其它安全范围，用户可明确排除子树；未知数量不计零。越界选择不读取，不能由“确认继续”解除边界。
- 单文件重新准备：明确基础版本、私有根定位及目标；新版本继承其它已保存内容和累计排除，Git保持同提交。不得覆盖旧快照，不把单文件准备说成已更新JDT/Activity/过程。
- 排除的实际执行：公共源码读取处必须落实排除；旧下游产物若仍含该文件，不能冒充已遵守新排除范围的结果。已进一步核实 Step05/Activity 等可直接携带源码内容而绕过原文读取器，因此不止修改一个 reader。用户已确认本轮纳入公共读取及旧材料进入新执行时的必要版本/范围检查；不修改后续分析算法、不拆后续 CLI、不运行后续分析。具体实施和验收持续按第19.1项跟踪。
- 保存与状态合同：按inspection/persistence/readiness分别表达，源码准备失败运行也能查已安装检查结果；reader仍核对消费许可。无法保存/进程终止不承诺存在报告，receipt状态与真实issue/gap引用匹配。

用例（人工构造，非实际运行结果）：100个文件中1个没有读取权限，其余99个可读，返回99个可用文件和具名问题；明确排除后使用新范围。后续发现保存内容不一致时，用户可具名重新准备；保留旧版本，禁止新结果混用旧分析。实现顺序和直接验收见[实施计划](../plans/source-preparation-implementation-plan.md)，不另建恢复系统。

本次只记录异常讨论，不修改六个错误码、现有停止行为、产物或历史结果，不调用模型。

### 19.1 持续验收项：排除规则与旧材料版本检查

标识：`SOURCE-PREPARATION-DOWNSTREAM-EXCLUSION`。**状态：本轮必要消费者边界已实现并具名验证。** prepared reader、来源basis投影、request-v3/output-v7、v4选择解析、零初始化准入，以及 Step05/M10 旧材料的新执行拒绝边界已实现。实际 LEGACY 材料basis来自所引 Step01 publication 的只读元数据重开；`PREPARED_SOURCE` 不能用缺少准备版材料级 provenance/有效排除的旧材料。M10每次重开核对receipt `upstreamArtifacts`与state `businessFlows` publication payload refs。`SourceExclusionConsumerContractTest`单项1/1通过，真实发布/重开READY_WITH_EXCLUSIONS并证明排除的Java/XML不进入prepared reader、`VerifiedJavaProject`、`PersistenceAnalysisRequest`或`MapperXmlResourceView`；最终直接相关套件168/168通过。本项按本轮fail-closed范围关闭；资源上限未知尾部NEW恢复另由计划步骤8完成。跨版本增量复用另列第19.2项，不由本项闭合推出。

实际问题：公共文本 reader 当前只接受成功、无 gap、与 capture manifest 全集一致的旧来源。除此以外，已保存 Step05 含方法正文与持久化原文，Activity 可从它直接投影；历史 M10 也保存来源片段。只在原文 reader 过滤文件，不能防止后续通过这些旧材料继续使用被排除内容。

必须同时实现并验证：

1. 新源码范围保存具名排除决定；枚举和直接读取均执行该决定，不是仅在 Skill 或报告中记一句“跳过”。
2. 新范围具有可区分的来源版本/范围身份；不能保持原身份、仅追加一个消费者看不到的排除表。
3. 旧派生材料进入新执行前校验其来源与本次选择一致。不匹配时返回具体材料、实际/预期来源和可选处理，不偷偷重扫、不重新生成 Activity、不自动调用模型。
4. 用户排除或重新准备文件后，不把旧 Step05、Activity、阅读包或已审结果自动声明为新来源下仍有效。本轮不建设依赖级增量重算或自动判定哪些旧业务结论仍成立。
5. 旧结果保留原归属，可按旧版本只读查看；新版本不覆盖旧文件。历史查看和使用旧材料启动新分析必须是两种明确操作。
6. 具名离线测试穿过真实读取和执行入口，证明被排除文件不会通过旧派生材料重新进入新分析。仅有排除名单测试、fake reader 或 CLI 参数转发测试不足以关闭本项。

范围：必要公共读取、来源版本选择及派生材料准入检查；不改后续步骤分析算法、不拆后续 CLI、不启动后续真实分析。相关运行和模型调用数在验收中必须为0，fixture/scripted 检查与客户真实运行分开统计。

关闭要求：正式详细设计给出具体读写/调用位置；实施 PR 给出上述直接测试与真实存储重开结果；更新本项为已验收时列出对应依据。缺少任一项继续保留 backlog，不以“代码已经提交”替代验收。

### 19.2 后续讨论：跨源码版本复用未受影响的材料

标识：`SOURCE-CROSS-VERSION-INCREMENTAL-REUSE`。**状态：明确延期，本轮不实施。** 用户接受首版整份checkpoint的来源版本/范围必须相同。文件刷新或排除产生新版本后，旧结果可历史查看，不自动升级或触发重算。

未来若要保留确实不受修改影响的JDT/Activity/过程，需要另行确定依赖覆盖、影响判断、不完整依赖的处理及跨版本再验证。本轮不借“最小接线”承诺此能力，也不因为拒绝跨版本复用就自动花费模型调用。

## 20. Vue 页面到 HTTP 入口的关联

标识：`FRONTEND-HTTP-ENTRY-LINK`。**状态：原有限关联已实现并有固定源码结果；独立前端运行、R4匹配及必要源码单元补全尚待实施。** owner是[独立前端与组装关联](../modules/technical-analysis/frontend-http-discovery.md)，工具依据见[官方parser调研](frontend-http-parser-research.md)，真实例子见[Vue到SQL走读](vue-to-sql-walkthrough.md)。

当前真实结果：旧R1的frontend-http-index保存307条文件处置、51条HTTP请求、51条ENTRY_LINK和68条SOURCE_UNIT；旧Step05有49条前端请求纳入、2条因容量未选。已有页面到后端入口的正式静态关系，不能再写成完全未实现。采购查询的getQueryParams原文在固定来源第115–143行，但没有进入必要单元集合，仍需修正选择。

本次目标：独立collect-frontend不读取后端入口、不启动JDT；新索引不含ENTRY_LINK。R4消费前端和准确后端，按HTTP method/path及已知条件匹配，并与Mapper/XML/SQL组成每入口JSON。补齐已支持路径的完整单元；原四实参和第五purchaseStatus NOT_PASSED不得改变。动态/多候选/未知地址、未支持语法仍保留，不造通用JS解释器。

关闭条件：独立前端成功/关闭均可保存重开；同一共享组件不同页面实例不串参数；getQueryParams等必要原文实际进入新文件；未匹配请求和候选不丢失；真实采购查询验收不冒充订单创建/入库/付款。旧记录不迁写；本次文档更新不运行工具。

## 21. Step02编译环境就绪，复用Step03验收

标识：`JAVA-COMPILATION-READINESS`。**状态：2026-09-28 外部 Maven 输出交接和 JDT 环境已实现并运行；调用准确性尚未通过。** 原内嵌 Maven/Resolver、下载和诊断完成证明路线已撤回。固定源码 R1 使用官方 classpath（106个可读JAR）、有效POM及目标Java8；332/339入口完成收集，7个入口受两个定义查询失败影响。旧具名 logger/Mapper 错边未重现，但内层调用仍混入外层候选。详见[固定源码验收](technical-analysis-fixed-source-acceptance.md)。这些计数不证明所有依赖完整或所有调用准确。

现行合同由 Agent/用户在获准环境运行 Maven 官方 `dependency:build-classpath` 和 `help:effective-pom`，交接真实文件位置与模块、JDK选择。Java从已求值输出提取源码根和编译设置，不要求Agent手填语义JSON，不做依赖求解或下载。Maven原始退出结果由Agent解释。已知缺项或绑定错误仍阻断；**诊断覆盖未确认允许继续但必须披露**，不能把未收到错误说成全仓零错误。[依赖交接](../modules/technical-analysis/dependency-preparation.md) · [Java 就绪](../modules/technical-analysis/java-readiness.md)

逐模块及Java8/17接线已有定向验证；本轮固定源码的实际环境就绪，但`diagnosticCoverage=UNCONFIRMED`。后续只核对真实遗留问题，不重新开发全文件诊断证明系统。

改动有效性通过已有Step03检查Mapper、logger.error、重载、嵌套调用和合法多态，再核对错误目标未进04/05。**不开发新的Step03候选裁决器。** 当前已获准的窄改动为21.1的JDT绑定/物理位置归属及21.3分类，详见JDT拥有者设计；超出该范围仍须讨论。

### 21.1 小项：直接使用 JDT 的逐调用方法绑定

标识：`JDT-CALL-SITE-BINDING`。**状态：已纳入本次详细设计，未实施、未完成真实验证。** 现有Core helper已开启`setResolveBindings(true)`，但调用记录未读取或保存`resolveMethodBinding()`；LS结果按范围归属后合并，已出现`Convert.toInt`混入外层`setPageSize`候选。

具名问题：`pageDomain.setPageSize(Convert.toInt(...))` 的内层调用记录混入外层 `setPageSize` 目标。这是已保存调用关系的错误归属，不是缺少方法原文，也不能靠降低未解析警告的优先级处理。验收应确认错误外层候选不再挂入内层调用，同时保留内层真实目标及合法重载/实现候选。现有生产导航尚未修正，旧索引不改写；目标修正进入新版本。

本次修正使用JDT本身的逐调用方法binding、声明binding和准确AST位置；冲突或归属未确认的hierarchy观察不成为确定展开边。精确逐点definition及合法implementation候选保留；空/recovered binding不能伪造身份。Core协议、Java索引、reader和下游同步更新；规则由[JDT详细设计](../modules/java-code-engines/jdt-engine.md)唯一维护。直接测试必须涵盖嵌套同名/不同名、重载、合法多态及下游SQL不串入，真实验收另报告。不得扩成新的Java解析器或自研分派规则。

### 21.2 JDT 对 Lombok 生成成员的支持

标识：`JDT-LOMBOK-GENERATED-MEMBERS`。**状态：本次仅纳入成熟集成方式的受限核验；未接入、未真实验收，不是客户漏写代码的结论。** 固定源码使用`@Getter`、`@Data`、`@Slf4j`，本轮保存的11个文件首条错误涉及对应的`getCode()`、`getId()`、`setInitStock(...)`和`log`。官方classpath已含Lombok 1.18.12，其JAR摘要与R1保存环境一致；现有JDT Core helper以普通`java -jar`启动，未显式接入Lombok生成成员支持。因此，提供Lombok依赖文件不等于分析工具已经识别其生成成员。

最小后续工作：先核对Lombok/JDT已有的官方或成熟集成方式，以及现用工具版本适配，分别确认LS与Core helper的支持情况；选择最小接入方式，不自行实现注解解释器或生成方法。用上述具名源码验证诊断、目标定位及材料保留，同时区分原文成员与工具生成成员，不把生成代码冒充冻结源码原文。验收必须检查实际结果，不能只验证JAR存在。当前尚未证明该支持缺口是所有诊断的唯一原因，也未证明接入后能解决第21.1项错边或两次定义查询失败。受限核验通过后才按明确工具配置采用；失败则保留具名限制，不自动扩大为自研注解处理或客户构建。本次写设计不执行工具或业务模型。

依据：[固定源码验收](technical-analysis-fixed-source-acceptance.md)、[helper启动](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtSyntaxHelperClient.java)、[Core解析配置](../../tools/jdt-syntax-helper/src/main/java/org/sourceanalysis/tools/jdtsyntax/JdtSyntaxReader.java)、Lombok官方[Getter/Setter](https://projectlombok.org/features/GetterSetter)、[日志字段](https://projectlombok.org/features/log)及[Eclipse集成](https://projectlombok.org/setup/eclipse)。

### 21.3 区分正常外部调用、目标未确定与查询失败

标识：`JDT-EXTERNAL-CALL-BOUNDARY`。**状态：已纳入本次修正，分类未实施；等待超时缓存修正已完成，稳定性另行核验。** 现有收集器在没有客户仓库目标时统一添加`UNRESOLVED_CALL`；导航适配器也会排除无法从固定源码读取的位置。这使正常外部库边界和实际识别缺口混在一起。具名`Long.parseLong(userIdObj.toString())`的保存记录显示定义/实现查询成功但返回空位置，不能把该记录说成查询报错，也不能说程序已通过该响应确认了JDK身份。

目标处理规则：

- JDT方法/类型绑定或其他明确工具结果确认目标属于JDK或外部依赖时，保留调用表达式、参数及已确认目标身份，标明外部调用并正常停止展开；不因未收集外部实现正文而增加分析缺口。
- 目标身份尚未确定时，继续保留未解析状态和实际原因；不能仅凭方法名、类名、空位置列表或无法打开文件就推定为正常外部调用。
- 查询执行失败时，单独记录工具错误及可取得的底层原因，不能混成正常外部调用或“客户源码不存在”。

本项相关的查询失败处理已有[真实复现及最小修正](jdt-definition-query-failure-experiment.md)：同一请求在30秒等待超时后被缓存为失败，51.404秒时实际正常返回，另外三个入口仍重复读取旧异常。用户确认后，适配器已保留原在途请求，后续观察其最终结果；不重发请求、不提高超时、不自动重跑失败入口。等待超时、线程中断和最终结果的私有日志已分开，保留可取得的原因及耗时；43项直接测试通过。2026-09-29真实七入口重验为首轮6成功/1超时，剩余入口随后新会话成功、最后一次诊断又超时，稳定性未通过；迟到正常结果已能供后续入口使用。两份JFR将50.629秒、52.308秒慢请求定位到定义解析前的文档生命周期任务等待，具体任务和调度根因仍未确认；不能仅据worker被唤醒即退出的重复模式宣称工具bug已证实。外部调用分类本身仍未实施；原R1历史根因、间歇长等待、入口部分结果保存和第21.1项错边均不因此算作解决，旧正式覆盖保持不变。

最小后续工作：结合第21.1项验证已有JDT绑定能提供的身份，调整状态、限制及覆盖的投影，保留原始查询信息。验收覆盖已确认JDK/第三方调用、真正未定位的项目方法、查询失败和正常项目内调用；确认正常外部调用不再单独使入口变为“有缺口”，真实缺口仍然可见。不展开JDK/第三方实现，不按名称硬编码库清单，不自行建立外部语言分析系统。本项分类进入新索引与覆盖；历史索引不回填。七入口及长等待只做受限稳定性验证，不新增恢复框架；本次文档工作不执行JDT。

依据：[现行调用状态判定](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/EntryCodeCollector.java)、[现行目标范围处理](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtNavigationResolver.java)、[本轮保存结果](technical-analysis-fixed-source-acceptance.md)。

## 22. 独立前端、后端、持久化和组装操作

标识：`TECHNICAL-CLI-PREPARED-SOURCE-LINEAGE`。**状态：原三个技术命令已实现并保存固定源码结果；新增独立前端及双分支组装尚待实施。**

新目标为R1 collect-frontend、R2 collect-code、R3 analyze-persistence、R4 assemble-materials；R0保留。前端/后端独立，匹配放在组装。运行、配置、发布和查询必须一起调整，不能只改命令名。[唯一合同](../modules/technical-analysis/cli-and-runtime.md)

关闭要求：正式CLI、真实Agent/registry/canonical store贯通四命令，错source/basis/owner拒绝；R2零Node、R3零Node/JDT、R4零parser，全部零业务模型。前端关闭有明确R1结果，不能缺文件视为关闭。外部Maven文件仍由用户或获授权Agent交给Java，不恢复Maven下载/审批体系。独立运行和来源继承是目标，不改历史R1/R2/R3的真实身份。

## 23. 退役路线清理与活跃文档纠偏

标识：`RETIRED-TECHNICAL-PATH-CLEANUP`。**状态：JavaParser/五图/Fact等生产、内嵌Maven下载与旧plan-materials已退役；本次检查残余消费者、旧合包新生产及活跃文档。**

不再把JavaDependencyPreparer或plan-materials删除列成未做。现行ProgramGraphsExecution仍服务JDT，不因旧名字删除。旧DTO/schema/reader/renderer只在真实历史消费者需要时保留；无消费者的专属测试才一起清理。新按entry发布后，随机多入口合包和默认巨型Markdown退出新生产，历史Packet/renderer仍可查。

关闭依据是精确文件—消费者—保留/迁移/删除清单及直接回归，不是“名字里有Graph就删”，也不能未经核对承诺所有遗留已清空。[清理设计](../plans/technical-analysis-cli-and-vue-cleanup-design.md)

## 24. 按后端入口交付完整机器证据

标识：`ENTRY-OWNED-SELF-CONTAINED-EVIDENCE`。**状态：已确认并形成详细设计，未实施。**

当前47包按entryId顺序和12入口/5MiB配置合并，没有业务分组依据；339条覆盖不是339份正文。目标一entryId一文件，完整包含已取得页面、Java、XML和SQL，源路径相对R0；一个Controller可有多个入口，多页面也可访问同一入口。

目录包含entry-evidence-index.json、平铺entry-<完整entryId十六进制>.json及frontend-coverage.jsonl。正常外部调用、未匹配页面、工具失败和动态SQL限制分开说明。未匹配/歧义前端请求不丢弃，失败后端入口仍有准确缺口记录。存储超限/损坏则R4失败，不截断后称完整。

本次必须适配现store固定文件白名单及64 payload上限，使用索引限定的变长集合，不新建证据系统。关闭须超过64入口真实安装重开、可移植路径、实际正文与覆盖相符、历史可读及新业务入口提前拒绝。[字段合同](../modules/technical-analysis/entry-evidence.md)

长ID持久身份不改，未来模型投影可短编号，不属于本次取证LLM调用。跨入口共享方法会重复内嵌，这是自包含文件的明确取舍，不重复JDT取材。

## 25. 保留JSqlParser已经解析出的排序

标识：`SQL-ORDER-BY-PROJECTION`。**状态：已确认投影遗漏，纳入本次修正，未实施。**

具名查询analysisCopy中有ORDER BY，而当前plainSelectNode没有输出对应节点；这是我们没有保存工具已有结果，不是需要另写SQL解析器。目标通过现有JSqlParser AST保存表达式、方向、空值排序及所属查询层，沿持久化发布、重开和entry JSON验证。

完整Mapper XML和条件/循环继续保留。未提供真实参数时不执行MyBatis动态展开，不新增多参数取证，也不把静态副本当真实执行SQL。修排序不表示所有动态SQL已完整解析。[Step04设计](../analysis-steps/04-proven-code-facts.md)

## 本次文档更新边界

本次校正状态及详细设计，没有改生产代码、测试、构建配置、Prompt、实际Skill、输入/模型响应或publication，没有运行Maven/JDT/Node/业务模型。新的四命令和entry证据属于待实施目标；原三命令全量技术结果存在但调用准确性未过，这两个事实并列保留。历史材料和more-findings.md原字节不变。
