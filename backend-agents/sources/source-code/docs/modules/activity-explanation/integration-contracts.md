# Activity来源、范围完成性与端到端接入合同

状态：2026-09-23 Task 1可靠性修复已通过12类79项定向测试；Task 2最终/替换scope、历史计划重开与配置接线通过8类80项定向测试、Spotless及独立复查，均使用隔离Maven输出。这不是完整CI，没有真实模型/JDT/客户构建。新Step05→Activity→正式Step07分支已接通，历史325包真实产生418条Activity。

Task 3进行中：coverage-v4发布、正式CLI零Provider采用、v1/v2阅读计划及私有批次记录已有定向测试。固定325包离线核对已发布独立新批次：311包COMPLETE、14包INCOMPLETE（24个必需切片未解释）；原1个导航缺口另列，新批次不得进入全仓Step07。旧418条Activity、旧结果及Step05材料不改写。第3步仍需完成新增历史边界测试及本地定向回归，第4步的范围核对已得出实际结论；补生成是否进行仍待用户选择。见[历史收口记录](post-review-handoff-20260923.md)及[端到端设计](../../end-to-end-business-delivery-design.md)。

## 1. 现状字段与版本，不能混称目标

| 合同 | 当前真实代码/磁盘 | 本次目标 |
| --- | --- | --- |
| Java导航、持久化、Step05 | java-code-index-v2、persistence-material-index-v1、code-reading-material-set-v1 | 不变 |
| 材料state | repository-run-state-v4 | 不变 |
| YAML | v3接线、实际执行限制和历史重开已通过Task 2直接回归；历史v2按旧字段读取 | 本轮完整本地CI仍待执行 |
| execution config | 历史磁盘为v4；已实现v5写入有效activityReading和v4/v5严格读取，Task 2定向回归通过 | v5还须在Step07编码步骤绑定过程新协议；无materialSource对象别名 |
| run output | analysis-run-output-v6 | 不变 |
| M11 producer | 历史为v3；工作分支v4显式发布及真实存储重开已定向通过 | 贯通真实范围投影及只复用；仍module11及两个文件 |
| Activity正文 | flow-interpretation-activity-explanations-v2 | 不变 |
| Activity覆盖 | 历史v3；工作分支v4读写已定向通过 | 仅增加packetCompletion；完成所有生产者与历史验证 |
| 私有阅读计划 | plan-v2最终/替换scope、DIRECT及分页重开已实现；历史v1实际闭包及scope对应已定向验证 | Task 3据此实现公共完成记录和离线历史接续 |
| 私有阶段/整包 | model-job-reviewed-result-v4 / activity-packet-result-v1 | 未变字段保持，reader核对被引用计划版本 |
| 私有批次 | 历史activity-batch-result-v1；普通及离线批次v2；混合重试批次v3 | 均保存与M11一致的packetCompletion；离线整批采纳保存来源批次及检查点；混合重试保存承接来源、检查点和实际重试的packet集合，供以后逐包核验 |

历史M11 v3的产物策略注册表与新增coverage-v4后的当前注册表身份不同。配置可选`historicalActivityPolicyRegistry`明确指向冻结的旧策略集；读取Activity时先对照该批次已保存的`artifactPolicyRegistryRef`，只选择精确匹配的当前或历史注册表，再按原有receipt校验重开。Step05仍独立使用`inputPolicyRegistry`；未知注册表、错误receipt或缺失历史配置均失败，不重新取材也不修改旧产物。旧v1阅读计划的调用字段可能保留完整导航元数据，读取器仅在按旧格式从同一材料精确重构成功时接受；历史直接整包结果被后续批次包装为切片聚合时，必须追溯原DRAFT/REVIEW并核对未改变的业务内容。
| 私有reading packet/attempt | activity-reading-packet-v1 / model-job-stage-attempt-v1 | 不变 |
| Activity Prompt | DRAFT/REVIEW仍v2；工作区READING_PLAN已路由v2，旧v1资源保留 | 完成新响应合同全部直接fixture迁移与回归 |

严格旧读不意味着把新字段默认补空。所有改变涉及producer、reader、exact-set/schema策略、artifact注册和直接fixture同一交付；版本拒绝测试保留。不覆盖历史产物、不恢复M10生产或JavaParser。

## 2. 当前来源是如何保存的

当前coverage-v3根字段只有：

~~~text
artifactId, artifactType, schemaVersion,
entryCoverage, unexplainedActivityEntries, semanticDeliveryStatus
~~~

entryCoverage只有entryId、disposition、activityIds、reasonCode。它**没有**materialSource对象、packet完成表或逐unit阅读台账。

新Activity explanations-v2每条在原业务字段外保存：

- materialId：真实packetId。
- materialSource：CODE_READING_MATERIALS等来源字符串，不是嵌套publication。
- sliceKey：本包稳定范围键，DIRECT依实际保存约定。
- originalSourceRefs：本条局部S到原packet sourceRef的准确映射。

完整Step05引用来自state、execution config及run output的readingMaterialCheckpoint。旧M10则从其历史businessMaterialCheckpoint及原协议重开。公开Activity、完整来源引用、basis与packet映射合起来决定来源；不能按对象形状猜类型，也不能只凭S1跨包找代码。

新Activity的主信息、完整条件、规则、公式、正文、问题与原来源字段保持。XML结构投影的Sref可以指整个Resource，但实际已读范围仍以私有readingPacket为准；不得声称已读整XML。

## 3. run output v6与三个owner

现有v6由FileSystemAnalysisRunRegistry写出的新模型分支使用：

~~~text
schemaVersion, runId, sourceRunId, outputKind,
readingMaterialCheckpoint, activityCheckpoint, knowledgeCheckpoint,
activityBatchComplete
~~~

上表为两个新模型分支的字段并集：ACTIVITIES_ONLY没有knowledgeCheckpoint，PROCESS_CATALOG才包含它；不能给前者补空字段。reading-only分支和旧报告版本按原精确字段集读；不额外塞入modelBatchComplete或materialSource。Java内部AnalysisRunOutput为兼容历史还保留businessMaterialCheckpoint/reportCheckpoint等字段，不代表它们都是每个v6文件的字段。

材料属于sourceRunId；Activity属于Activity检查点的run；过程属于新process run。AnalysisRunOutput.step05Processes已支持这三个owner。hasCompletedActivities()当前只检查checkpoint和activityBatchComplete，不会自行重读scope；范围完整性必须在M11生成和Step07准入核验后计算，不能把这个getter描述成深度验证。

先在RUNNING下安装M11、保存批次结果并recordOutput，之后才进入FINISHED/FAILED。存储失败不能通过改旧run终态“补保存”。已验证部分M11可查询，run非成功并不销毁它。

## 4. 本次目标：最小packetCompletion

coverage-v4沿用原根字段，新增packetCompletion数组；不另建公共manifest文件：

~~~text
packetId
entryIds[]
completion: COMPLETE | INCOMPLETE | UNDETERMINED
requiredSliceKeys[]
completedSliceKeys[]
incompleteScopes[{sliceKey|null, entryIds[], reasonCode}]
~~~

约束：

1. 每个实际输入packet恰好一项；entryIds与原Step05成员一致。无包的原NOT_COLLECTED入口只在entryCoverage，不造packet。
2. requiredSliceKeys来自最终有效阅读计划，稳定且唯一；completedSliceKeys必须属于它，并有完整有效REVIEW与可见成功Activity/合法未解释处置。
3. 合法REVIEW明确未解释仍保留MODEL_NOT_EXPLAINED，不能满足完整业务准入。最终批次完成性同时检查该项，不只比较slice集合。
4. INCOMPLETE有明确失败/必需未读/未处理范围；UNDETERMINED有历史记录不足的具体原因；COMPLETE无未完成必需范围。全部导航未完成时不能隐式宣布全包读取决策结束。
5. entry级范围由slice.entryKeys准确汇总；一个entry的失败不抹掉同包其它entry的成功，整个packet仍非完整。
6. 普通scopeLimitations、外部调用未知、未选辅助正文不自动等于必需失败。不复制每个unit的状态到公共coverage。
7. 保存的partial结果包含所有已成功独立slice。后续slice失败、甚至其容量预检失败，都不能使先前成果从公开M11消失。
8. 实际packet的COMPLETE至少有一个必需范围（直接整包为`whole-packet`）。零包输入使用空packetCompletion集合，不能用必需范围为空的COMPLETE记录替代；历史UNDETERMINED仍允许范围键未知并保留原因及有效Activity。

packetCompletion是程序执行范围结论，不是业务正确性打分。必需未知或合法未解释影响Step07准入；仅原NOT_COLLECTED的1项或已读范围内诚实业务未知，不把其它325包当模型执行失败。

校验沿已有边界分工：M11普通读取器核对版本、字节、身份及Activity与完成记录的内部一致性；发布器、离线接续和Step07请求在已经持有Step05材料时核对真实packet与完整entry集合。不为普通重开增加一套上游加载或扫描流程。历史没有完成表与新版明确的空包集合必须可区分，不能由旧构造器默认生成COMPLETE。

内存使用`ActivityPacketCompletion`值对象及`ActivityExplanationResult.packetCompletion`的显式可缺省集合：历史v2/v3为缺省，新v4必须存在（零包时可以为空集合）。新生产发布必须显式提供完成记录并写v4；保留旧读取，不为历史测试再保留一条生产v3写出路径。历史fixture使用原格式构造，与当前发布测试分开。

## 5. 阅读计划v2、历史核对及只复用输出

plan-v2保存每次decision、完整slice readingPacket及其来源映射、最终有效slice集合与当前未解决范围。正式保存器另写入jobInputFingerprint、quotaScope和readingContractFingerprint；实际请求及模型身份在阶段记录中，有效配置在execution config中，不要求计划文件重复内嵌完整Prompt/Schema/profile/binding。历史诊断与当前义务分开，明确被替换/撤回的scope，不用累积unknown文本驱动当前成功判断。详细响应见[ReadingCoordinator](large-material-reading.md)。

旧plan-v1与coverage-v3严格读，但不能据“无新错误标记”默认为完整。显式离线核对读取旧plan、raw决策、实际包和stage/复用来源：

历史计划按实际保存形状读取：含完整 `slices/readingPacket` 的 v1 分页计划可以恢复原范围及原始记录，但不补造 v2 的最终范围、结束决定或完成结论；旧 DIRECT 只有头部和 `sliceKeys` 时，先保留原记录，离线核对再结合其已审结果与固定材料。历史可读不等于能续接新版模型阶段；线上阶段复用仍须匹配原实际输入、Prompt、Schema和模型身份。

现有小包直接路径还可能根本没有阅读计划：它先传入完整包，再保存DRAFT/REVIEW，Activity的`sliceKey=null`。此时按实际完整包输入及成功两稿核对，可用既有`whole-packet`键表达新完成记录中的整包义务，但不改原Activity的空sliceKey、ID或正文。这个对应只适用于确认的直接整包任务；大包明确引用的计划缺失不能借此降为DIRECT，也不补造一份历史计划。

- 能还原最终必需范围、完整包和成功REVIEW：COMPLETE。
- 明确仍有必需未读/失败：INCOMPLETE。
- 旧记录没表达替换关系或不足以确定义务：UNDETERMINED。
- 声称存在的成功文件/来源损坏：硬错误，不伪装UNDETERMINED或调用模型修补。

离线核对不重新计算旧运行的分页大小或容量判断，也不把新配置上限套到旧记录。v1没有新版结束字段本身不等于无法判断：旧结构中的导航已经全部展示、原始请求的非空scope集合没有冲突、完整保存的scope及两稿均对应时，可以确认这些既定范围已完成。相同声明的重复不算冲突；不同修订或移除提案的最终意图无法还原时才记UNDETERMINED。v2使用其已验证的最终集合、结束及当前缺口字段。两者都只判断已记录的执行义务，不宣称业务语义完整。

v1的同键修订按其原实现的最后声明核对：最后声明与实际冻结包和已审阶段一致时，先前超大提案不使该范围永久未完成。若仍保留较旧的可执行版本，不能用它代替较新的义务；不同键之间未明确的替代也不能自行猜测。这个判断来自保存的结构，不解释自由文本中的错误词或业务未知。

历史v1的剩余导航页按已验证的`remainingNavigationPages`读取；非零时保留现有成功切片，同时报告导航未完成，不能标为COMPLETE。不会重新计算历史分页或把普通业务未知当作导航失败。

旧失败包没有整包聚合时，单个成功slice不能按直接整包任务核对。使用该批次已有终态记录中的真实jobKey定位原阅读计划，复用同一个纯读取器核对必需范围和已审slice；已完成正文保留，缺失范围记INCOMPLETE。失败记录不表示计划一定已保存：未声称完成且计划确实不存在时记录UNDETERMINED，不补造计划；已存在但损坏的计划、声称成功却损坏的stage仍明确失败。

各成功slice保存的完整Activity联合必须与该包公开保留的Activity一致；存在聚合时，聚合也必须一致。按Activity ID比较完整字段，不只检查某条Activity覆盖过入口，避免一条漏存或正文、来源不一致被误判为完成。这里只比较已有结构化记录，不重新解释模型原文。

只核对已有结构和实际字节，不用Java从中文说明推测业务等价。完整记录可跨版本作为历史已审输入采纳，仍保留原模型和Prompt身份；这不同于把旧DRAFT拿来续接新Prompt的REVIEW，后者必须严格匹配实际stage指纹。

现有Activity execute已接通`--reuse-only`参数、离线材料读取前置分流及已验证的历史接续。该选项必须指定--reuse-from-model-batch，与主动retry互斥；本轮核对整个材料检查点，不接受`--material-id`或`--packet-id`过滤，不能静默忽略选择或缩小分母。`--run`仍只接受匹配的QUEUED运行。目标是新建运行，Provider初始化/调用0，保留所有可验证旧Activity并写新coverage。新M11不改变旧Activity ID/业务字段/来源，不改旧receipt/FAILED/STARTED。不完整或无法判定时保存成功部分和具体范围，退出非零；禁止偷偷退回模型生成。没有第二CLI或公共Agent方法。

配置仍复用已解析的`ModelJobsConfiguration`。离线入口通过现有配置类的`requireModelJobsForStorage()`只检查journal/output位置，不解析登录环境、密钥、可执行程序或服务容量。普通线上执行继续使用`requireModelJobsForExecution()`执行完整认证与运行环境检查；两者共享存储位置检查，不增加一份离线YAML或配置类型。历史记录只在显式指定批次及其记录的复用来源中读取，不按新Prompt推导旧任务身份，也不搜索无关journal。

离线新批次也必须能作为下次显式复用来源，不能只发布一份无法接续的M11。沿用execution-config-v5，明确保存`executionScope.mode=REUSE_ONLY`及完整材料检查点；`packetIds=[]`表示未做包过滤。为满足现有正整数结构，`maxMaterialsToStart=Integer.MAX_VALUE`仅作该模式的非执行占位，不限制离线核对范围，也不授权任何模型调用。现行非敏感模型/阅读配置是新批次的配置声明，不冒充旧Activity实际使用的模型身份或核对历史时的容量。新私有activity-batch-result-v2的逐包完成记录必须与公开M11一致；离线批次另外登记实际采纳的原批次及检查点，下一次显式接续沿该已登记来源验证。明确标记为REUSE_ONLY的批次若缺失、损坏或没有采纳来源记录，必须失败，不得回退为普通历史批次；旧v1普通批次继续按其原格式核对和读取。不创建虚假的新DRAFT/REVIEW记录。线上模式继续执行原范围和模型指纹校验。

inspect只读显示这些范围及原因，不自动创建新内容。无法判定的具名范围先讨论已有记录能否补足；需要模型时先获得新的明确同意，不重跑325包。

跨批次离线核对失败包时，若阅读计划尚未形成，使用该包已保存、归属验证通过的终态失败记录中的`reasonCode`恢复`INCOMPLETE`及其具体原因；不能把明确的阅读错误或容量拒绝改写为`UNDETERMINED`。只有既没有可验证计划也没有可验证终态失败原因的历史包，才保留`HISTORICAL_SCOPE_UNDETERMINED`。已完成业务内容仍须通过原有结果及来源核对，失败原因不能替代成功稿件。

## 6. 正式CLI与重试

现有正式flow-interpretation分支已支持：

| 参数 | 当前含义 |
| --- | --- |
| --material-id | 单材料选择；来源明确为Step05时按packet处理 |
| --packet-id | 新包精确选择；样本可逗号多个，retry只接受一个失败包 |
| --retry-failed-from-model-batch | 选择已结束批次的失败/未完成范围，创建新批次 |
| --reuse-from-model-batch | 选择完整结果或匹配stage的复用来源，不等于失败范围 |
| --run | 仅匹配的QUEUED运行，不重新激活旧run |

新增--reuse-only只是明确“本次不允许模型生成”的执行选项，不是重试。普通retry仍按现有入口与阶段策略：重开原有效计划、scope和source mapping；成功DRAFT对应的REVIEW单独重试；失败响应永不复用。损坏计划不自动replan。已有`activity-packet-result-v1`完成声明还必须能重开它引用的非空plan及每个slice的DRAFT/REVIEW成功记录，并与聚合的Activity、coverage、source refs一致；没有包聚合时，匹配的单slice v4完成声明同样必须能重开它引用的两个成功stage。引用缺失或损坏为硬错误、零补生成。合法空计划且没有业务结果不形成可复用scope，但也必须先通过packet ID、导航、未读单元及保存形状校验，之后显式新执行才可重新选材。scope/Prompt/Schema/model改变使受影响stage失效，先告知哪些内容需重新生成，保留其它可复用包。

当前Step05重试范围以已验证的`packetCompletion`为准，不能仅凭entry coverage中已有Activity就认为整包完成；一个entry跨多个packet时，重试包含该entry的全部packet。显式指定`--reuse-from-model-batch`时，新批次只执行选中的packet，并从同一材料检查点承接其他packet的原Activity ID、正文、来源、覆盖和原完成状态。定向重试一个失败包不应被另一未选失败包阻断；后者仍为`INCOMPLETE/UNDETERMINED`，不能冒充完成。混合批次的私有v3记录逐包区分本次生成与承接来源；下一批次沿来源链重新核验，而非仅凭复制后的M11完整标记。执行配置声明混合批次而v3记录缺失或不一致必须失败。普通线上复用当前只能读取所指定批次自己的私有任务。下一批次选择某个仅从更早批次承接的未完成packet时，必须先完整核验该混合来源链；只有该packet没有任何已审slice或其他有效业务结果，才允许新执行。否则仍在Provider启动前拒绝，避免因所指定批次没有私有阶段而静默丢失旧成功内容。完整packet仍不可作为失败范围重跑。零调用重开仍可沿来源链完整核验。所选packet使用本批次新结果，旧批次、旧模型记录不改写；没有显式复用来源时，未选packet保持未完成状态。旧v3没有packetCompletion，必须先显式离线核对，不能按entry coverage猜测可承接。

新reading限制位于sourceAnalysis.activityReading，默认128页、4补读轮、32个当前有效slice；由唯一配置读取到既有ActivityReadingProfile。配置加载、真实执行接线及历史重开已通过Task 2定向回归。execution-config-v5保存有效输入/输出容量和三项运行上限；三项上限不进入材料基础标识，且不单独使已冻结有效计划失效。Prompt、Schema、实际输入/输出容量、scope和模型绑定仍按原合同匹配，降低上限后也不能接纳最终有效范围已不符合当前约束的计划；不追溯拒绝已被替换的旧提案。改变最终选材/scope必然改变依赖稿件输入。

## 7. Step07已接线与目标准入

SourceAnalysisExecution已实现state-v4分派、executeStep05BusinessProcesses、assembleStep05ProcessDiscoveryRequest及持久executor调用。它验证Activity输出与同一Step05 checkpoint，FrozenCorpus/Publisher使用真实StepPublication。不是“仅改了读取器”或“CLI尚未接”。

当前新分支拒绝catalogFromModelBatchId，418必须做自己的首次目录；不顺带建设跨Activity版本的目录迁移。新同源批次的完整任务复用按现有指纹执行。

目标Step07对新coverage-v4核验全部packetCompletion、entryCoverage、unexplained记录和对应Activity，而非只有output布尔值。必需INCOMPLETE/UNDETERMINED不自动进入全仓过程；原NOT_COLLECTED与普通已读未知可带入范围说明。历史v3保留严格读/查询，但新正式全仓应先离线核对并得到新范围检查点，不能静默把旧无标记当通过。

Step07不获得Activity retry能力。ProcessDiscoveryRequest、Result、workflow、executor、Publisher和artifact query需要同一个实际来源视图；这条接线已有，新增验收要证明正式CLI贯穿真实store，不仅是反射签名或fake Agent。

## 8. 容量、失败与验收

保持现有Activity输入/输出字节检查与Provider声明capacity：contextWindowTokens、providerOverheadTokens、reasoningReserveTokens、tokenAccounting。UTF8_BYTE_ESTIMATE是保守估算，不是服务端精确token证明；可记录已有离线计数工具实际结果。计量完整Prompt、最终input、Schema、封套与输出余量，REVIEW含完整真实DRAFT。未知隐藏开销不引入额外取证或自动调用。

Provider通过可靠typed reason给出的实际容量拒绝为PROVIDER_INPUT_CAPACITY_EXCEEDED；同输入不按transient重试，不提高上限/截稿/换服务。Codex进程的非零退出自由文本不是可靠类别，统一为UNKNOWN；只有确认进程终止的本地timeout保留REQUEST_TIMEOUT类型。程序自己算出的单slice输入/输出容量reason是packet-local：保留此前成功并继续后续独立slice；认证/配置停止该binding，来源/存储损坏保持硬错误。其它阶段重试、共享故障域和私有错误保留由[model-job-execution](../model-job-execution.md)统一维护。

必须直接验证：

- 两个来源分别正式读取、packet-local同名S隔离、所有条件/SQL依赖进入实际请求。
- 一个多entry、多slice包的成功与失败均可保存；缺必需范围不误放行；普通语义未知不误重试。
- plan重开及--reuse-only全程0 Provider/JDT/Builder/Activity生成调用；旧418/326及原材料字节不变。
- CLI真实Agent与store的新M11→Step07→五文件→artifact重开；Activity输入变化正确使目录失效。
- v3与v4 coverage精确区分；缺packetCompletion不能默认空；run-output-v6原字段保持。
- 新Activity审查草稿和新增测试未通过前不宣称修复完成；544测试的旧提交记录不代表当前dirty代码。

当前已获批准的端到端计划正在实施，开发期间运行直接测试；完整本地质量检查在该计划第9步执行。不启动外层工程或真实JDT扫描，真实Step07小样和确认后的全仓扩大分别遵守第10、11步边界。
