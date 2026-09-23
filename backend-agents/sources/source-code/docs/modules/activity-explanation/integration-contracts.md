# Activity来源、范围完成性与端到端接入合同

状态：2026-09-23 Task 1可靠性修复已在隔离Maven输出中通过12类79个direct tests及Spotless；这不是完整CI，没有真实模型/JDT/客户构建。新Step05→Activity→正式Step07分支已接通，325包真实产生418条Activity；Task 2的最终/替换scope和Task 3的coverage-v4/reuse-only仍是本页待实施目标，旧coverage仍不能充分区分必需阅读失败和普通未知。见[收口记录](post-review-handoff-20260923.md)及[端到端设计](../../end-to-end-business-delivery-design.md)。

## 1. 现状字段与版本，不能混称目标

| 合同 | 当前真实代码/磁盘 | 本次目标 |
| --- | --- | --- |
| Java导航、持久化、Step05 | java-code-index-v2、persistence-material-index-v1、code-reading-material-set-v1 | 不变 |
| 材料state | repository-run-state-v4 | 不变 |
| YAML | repository-run-config-v2，modelJobs支持activityRetry；不接受activityReading | repository-run-config-v3接通activityReading |
| execution config | model-job-execution-config-v4：readingMaterialCheckpoint、materialBasisSha256、executionScope、modelJobs等 | v5冻结activityReading及过程新协议版本；无materialSource对象别名 |
| run output | analysis-run-output-v6 | 不变 |
| M11 producer | activity-explainer v3 | v4；仍module11及两个文件 |
| Activity正文 | flow-interpretation-activity-explanations-v2 | 不变 |
| Activity覆盖 | flow-interpretation-activity-coverage-v3 | v4，仅增加packetCompletion |
| 私有阅读计划 | Task 1现有activity-reading-plan-v2复用封套已定向验证；尚无最终/替换scope | Task 2完成plan-v2最终/替换合同 |
| 私有阶段/整包 | model-job-reviewed-result-v4 / activity-packet-result-v1 | 未变字段保持，reader核对被引用计划版本 |
| 私有批次 | activity-batch-result-v1 | v2包含packetCompletion及离线只复用核对结果 |
| 私有reading packet/attempt | activity-reading-packet-v1 / model-job-stage-attempt-v1 | 不变 |
| Activity Prompt | activity-draft-v2 / activity-review-v2 / activity-reading-plan-v1 | DRAFT/REVIEW未改业务职责；reading-plan升v2 |

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

packetCompletion是程序执行范围结论，不是业务正确性打分。必需未知或合法未解释影响Step07准入；仅原NOT_COLLECTED的1项或已读范围内诚实业务未知，不把其它325包当模型执行失败。

## 5. 阅读计划v2、历史核对及只复用输出

plan-v2保留完整实际阅读输入、每次decision、完整slice readingPacket和source mapping、Prompt/Schema/profile/binding，以及最终有效slice集合与当前未解决范围。历史诊断与当前义务分开，明确被替换/撤回的scope，不用累积unknown文本驱动当前成功判断。详细响应见[ReadingCoordinator](large-material-reading.md)。

旧plan-v1与coverage-v3严格读，但不能据“无新错误标记”默认为完整。显式离线核对读取旧plan、raw决策、实际包和stage/复用来源：

- 能还原最终必需范围、完整包和成功REVIEW：COMPLETE。
- 明确仍有必需未读/失败：INCOMPLETE。
- 旧记录没表达替换关系或不足以确定义务：UNDETERMINED。
- 声称存在的成功文件/来源损坏：硬错误，不伪装UNDETERMINED或调用模型修补。

只核对已有结构和实际字节，不用Java从中文说明推测业务等价。完整记录可跨版本作为历史已审输入采纳，仍保留原模型和Prompt身份；这不同于把旧DRAFT拿来续接新Prompt的REVIEW，后者必须严格匹配实际stage指纹。

建议在现有Activity execute增加 --reuse-only（待实现）：必须指定--reuse-from-model-batch，与主动retry互斥；新建运行，Provider初始化/调用0，保留所有可验证旧Activity并写新coverage。新M11不改变旧Activity ID/业务字段/来源，不改旧receipt/FAILED/STARTED。不完整或无法判定时保存成功部分和具体范围，退出非零；禁止偷偷退回模型生成。没有第二CLI或公共Agent方法。

inspect只读显示这些范围及原因，不自动创建新内容。无法判定的具名范围先讨论已有记录能否补足；需要模型时先获得新的明确同意，不重跑325包。

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

新reading限制位于sourceAnalysis.activityReading，默认128页、4补读轮、32slice；由唯一配置读取到既有ActivityReadingProfile。当前代码写死这些值，不能称为已可配置。execution-config-v5记录有效值和合同版本。只改并发/退避不改变语义输入；只提高运行上限不重做已完成范围，但改变最终选材/scope必然改变依赖稿件输入。

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

执行文档本轮不运行上述测试或模型。后续完整本地质量检查属于另行评审的实施计划，不启动外层工程或真实JDT扫描。
