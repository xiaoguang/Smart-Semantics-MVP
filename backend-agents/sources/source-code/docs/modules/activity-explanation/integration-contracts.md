# 新Step05材料接入Activity：版本与来源合同

状态：2026-09-22目标设计，尚未实现。当前 `CodeReadingMaterialReader.reopen` 已返回完整Packet；`ActivityExplainer`、`ActivityExplanationCheckpointPublisher`与CLI模型执行仍接受旧BusinessMaterial/BusinessMaterialBuildResult，Step07 FrozenAnalysisCorpus也仍沿旧M10读取。不能把reader已实现等同整条业务链已接通。

## 1. 最小接入范围

1. 唯一CLI读取已保存材料state，按明确checkpointKind分派旧M10或新Step05；Provider前验证完整引用、schema、来源和所选packet。
2. 新路径直接重开CodeReadingMaterialSet，交内部ActivityMaterialProjector/ReadingCoordinator；不构造伪BusinessMaterialBuildResult、不安装M10、不运行Step01–05。
3. ActivityExplainer解释新包/slice，沿既有job pool保存完整stage结果；publisher接收新材料来源与已关闭coverage。
4. M11保存来源discriminator、packet/slice和完整Activity；Step07按其声明材料来源定位历史M10或新Step05，不猜对象字段形状。
5. 所有直接producer/reader、artifact policy、exact-set、registry、output与CLI在同一实施单元升版。旧结果仍按原schema只读，不混写。

## 2. 明确版本表

| 所有者 | 当前 | 本次目标 |
| --- | --- | --- |
| Java导航 | java-code-index-v2 | 不变 |
| 持久化材料 | persistence-material-index-v1 | 不变 |
| Step05材料 | code-reading-material-set-v1 | 不变 |
| 新技术材料state | repository-run-state-v4 | 不变，仍只拥有固定材料 |
| 单一YAML | repository-run-config-v2 | repository-run-config-v3：新增Activity reading/retry；旧v2严格读取原含义 |
| 模型batch配置 | model-job-execution-config-v3（历史v2） | model-job-execution-config-v4：materialSource、Activity packet选择、reading/retry与复用选择 |
| 运行输出 | analysis-run-output-v5只支持READING_MATERIALS_ONLY；历史v3/v4 | analysis-run-output-v6：新材料与Activity/后续输出分别归属，含批次完成性 |
| Activity Prompt/response | activity-draft/review-v2 | activity-draft/review-v3：readingPacket与scope；原业务字段保持 |
| Activity公开文件 | flow-interpretation-activity-explanations-v1 / flow-interpretation-activity-coverage-v2 | flow-interpretation-activity-explanations-v2 / flow-interpretation-activity-coverage-v3 |
| M11 module | activity-explainer v2 | v3，仍module11和两个语义文件 |
| Activity私有完整job | model-job-reviewed-result-v2 pair | model-job-reviewed-result-v4：plan/packet/slice及成功stage引用；Step07过程v3不改 |
| 新私有阅读/attempt | 无 | activity-reading-plan-v1、activity-reading-packet-v1、model-job-stage-attempt-v1、activity-batch-result-v1 |

版本号定义目标wire分派，当前代码不得被描述为已经接受新字段。旧M10内容不迁移到Step05，不将历史Luna结果改标Terra或XML增强。新生产不得安装旧M10，显式旧输入解释/只读能力按既有授权和reader合同保留。

## 3. 材料来源与运行归属

目标execution-config-v4的 `materialSource` 是严格tagged union：

- `kind=CODE_READING_MATERIALS`：sourceRunId、完整Step05 AnalysisStepPublicationReference、materialSchemaVersion、materialProducerVersion；必须匹配state-v4和source basis。
- `kind=LEGACY_BUSINESS_MATERIALS`：sourceRunId、原M10 ModulePublicationReference、历史profile/producer/basis；按历史reader验证。
- 两分支字段互斥，未知kind拒绝，不将Step引用强转Module引用。

sourceRunId拥有材料；新modelBatchId（既有AnalysisRunId）拥有新Activity输出。模型包不含这些运行/存储身份。新batch失败不使固定材料无效，缺失/损坏材料也不触发重扫。

v6顶层明确保存 `schemaVersion,runId,sourceRunId,outputKind,materialSource,activityCheckpoint,knowledgeCheckpoint,reportCheckpoint,modelBatchComplete`；未产生的模型checkpoint为null。沿用现有knowledgeCheckpoint/reportCheckpoint名称，不为业务称谓增加字段别名。v6只用于明确声明新合同的模型输出；v5材料专用格式继续原字段集。modelBatchComplete为程序保存/调度结论，不是业务完整性评分。false时不能自动进入Step07。

当前AnalysisRunOutput构造器禁止readingMaterialCheckpoint与Activity共存，hasCompletedActivities()也仅判断checkpoint非null；两者都必须按v6修改并保留历史分支。目标hasCompletedActivities()同时验证检查点存在、来源/scope闭合和完整性门禁，不能让部分M11放行。后续独立Step07 run可以消费另一已完成Activity batch：Activity owner以其checkpoint地址及已绑定输入核对，不能强制等于当前process run；knowledge/report按各自输出owner核对，sourceRun始终是材料owner。

## 4. M11与来源映射

继续只发布 `activity-explanations.jsonl` 与 `activity-coverage.json`；不新建独立公共来源manifest。coverage-v3中的materialSource与packet映射共同承担来源manifest职责：

- 包：packetId、entry局部/全局映射、sliceKeys、原技术限制、导航/正文处置。
- slice：稳定sliceKey、范围、必需unit与来源ref allowlist、成功Activity IDs、未解释/未读/失败stage。
- entry：汇总全部slice处置，只有必需范围完整闭合才能形成相应已分析结论。
- 来源：每个S ref指向 `publication + packetId + 原sourceRef`，statement投影另带statement选择键，但原件仍指完整Resource。
- 业务字段沿用ReviewedActivity既有name/purpose/participants/objects/inputs/conditions/steps/results/rules/formulas/terms/certainty/questions/limitations；新增program-owned packet/slice/provenance不由模型生成。

跨packet source:1可能重复；所有跨包读取必须带完整来源身份。XML没有精确statement行号，不能制造更细来源以满足review。

publisher只接受验证后的不可变结果，检查来源、entry/slice、ref、覆盖与exact-set，然后稳定安装一次。没有模型重写、重导航或业务规则推断。

在所有已启动/排队任务有终态后，若存在失败/必需未读，可以发布包含成功Activities与完整不足coverage的M11 `SUCCEEDED_WITH_GAPS` 检查点，同时将run/batch标非成功，v6.modelBatchComplete=false。该检查点是可读成功子集，不是可自动下游的完整输入；它不把失败slice记为MODEL_NOT_EXPLAINED。整批结构/来源损坏时不安装无法验证的aggregate，只保留已验证私有stage。未开始就失败的batch可没有M11。

保存次序必须符合现有FileSystemAnalysisRunRegistry.recordOutput只接受RUNNING的约束：先完成可验证的M11安装与activity-batch-result，再在RUNNING下recordOutput保存v6，最后转FAILED或成功终态。不能先结束run再写output，也不为补结果修改旧结束run；若保存环节失败，保持已安装不可变产物与诊断并按存储错误停止，不宣称有完整可查询输出。

## 5. Step07的直接使用

FrozenAnalysisCorpus重开M11 coverage-v3的materialSource，使用正式Step05 reader取得相关Packet与source mapping；历史coverage按M10原合同读取。它不重新调用ActivityExplainer或JDT。ProcessMaterialAssembler可取完整已审Activity与其实际来源，不用导航卡替代原文。

默认Step07只接受modelBatchComplete=true且所需范围满足现行接受条件的检查点。部分M11不会因有Activity就自动触发；未来若显式支持部分输入，需要独立、明确范围，不能隐式缩小全仓分母。本次不扩大Step07 retry，原三阶段/单决策/复用行为按其既有合同。

## 6. CLI：显式选择与手动retry

以下为待实现的现有 `source-analysis` 参数扩展，不能当成当前已可运行命令。保留现有 `execute-step --target flow-interpretation`，不新增公共Agent方法或第二CLI。

| 参数 | 语义 |
| --- | --- |
| `--material-id ID` | 已有单材料样本选择；明确新kind时ID为该Step05 packetId，旧kind仍按旧材料ID |
| `--retry-failed-from-model-batch RUN_ID` | 新增；读取已结束批次的activity-batch-result-v1，选择其所有失败/未完成packet；不重新执行旧run |
| `--packet-id ID`（可重复） | 新增；仅与retry选项组合，将范围限制为上述批次失败清单中的这些packet |
| `--reuse-from-model-batch RUN_ID` | 既有参数扩展到新Activity成功stage；仅选择复用来源，不表示“只重试失败包” |
| `--run RUN_ID` | 只接受新QUEUED run并核对已绑定配置；不能把旧FAILED/FINISHED run重新激活 |

默认的手动retry操作由CLI失败摘要展示为：

```text
source-analysis --config /absolute/path/activity.yaml execute-step --target flow-interpretation --retry-failed-from-model-batch <failed-batch> --reuse-from-model-batch <failed-batch>
```

定向只处理一个失败包时追加 `--packet-id <packetId>`。配置仍绑定同一个已保存材料state；未给--run时沿既有机制创建新modelBatchId。retry参数与--material-id、--activity-model-batch、--catalog-from-model-batch/focus-question互斥，只能用于flow-interpretation。来源批次必须已结束，失败报告与材料身份须匹配；未知packet或成功packet不作为失败范围接收。

scope与reuse是两回事：只指定retry范围、不指定reuse，意味着在新batch对所选失败包重新执行所需序列；同时指定reuse则先精确验证并导入该包成功plan/slice/stage，从首个未成功阶段继续。成功DRAFT+失败REVIEW仅发新REVIEW。旧完整成功包可通过同一显式reuse来源以零调用继承进新批次coverage，未选且仍失败的包保持失败/未完成，不悄悄删除。

继承的是已保存稳定packet/slice/localActivity键与plan，不能随并发完成顺序重编号。若重新规划导致输入或slice范围变化，其依赖DRAFT/REVIEW全部失效，不能把旧Activity拼入新scope；未受影响其他包仍复用。改变Prompt/schema/model绑定使相应成功stage不可复用，CLI在调用前报告哪些stage将重新生成。

## 7. 用户如何看见失败

执行完成时输出batchId、M11是否存在、成功/失败/未开始包数、非零退出状态，以及每个失败项：

`packetId, entry display, sliceKey或null, stageKey, reasonCode, attemptsUsed/maxAttempts, reusableSuccessfulStages, nextAction`。

实际输入/输出/token和错误摘要留在私有attempt记录，普通终端不打印整段源码或秘密。完整 `activity-batch-result-v1` 存批次私有目录；CLI可显示其本地路径，公共Agent不暴露Path。现有inspect --run给出同一精简失败/完成性视图；读失败清单不启动Provider。

run非成功和M11含部分成功不矛盾。无论剩余原因是容量、导航未读还是retry耗尽，CLI必须指出受影响范围，并给出合适下一步：可retry瞬时故障、需改profile的新batch、或需讨论缺失技术材料；不能建议所有错误无脑重试。

## 8. 直接实施检查

模型容量由现有ActivityExplanationProfile.maxModelInputBytes/maxModelOutputBytes与新增Provider `capacity` 配置共同拥有。capacity字段为 `contextWindowTokens,providerOverheadTokens,reasoningReserveTokens,tokenAccounting`；contextWindowTokens为配置声明本次采用的正整数上限，两个reserve为显式配置的非负保守预留，均不是对服务端隐藏实现的证明。tokenAccounting首版使用UTF8_BYTE_ESTIMATE；已有适用的离线计数工具可直接复用并记录实际计量方式，不新增token子系统。缺少必需数值或数值非法时CAPACITY_PROFILE_REQUIRED，Provider调用0；不知道实际tokenizer、协议隐藏开销或内部推理量，不自动否决整个业务入口，也不要求Adapter证明它们。

预检覆盖Adapter最终实际发送的system/输入JSON/Schema及其可见封套。默认按每个UTF8字节估一个token，并加配置的providerOverheadTokens、reasoningReserveTokens和按maxModelOutputBytes估算的输出预留，与声明的contextWindowTokens比较；实际输入字节另按maxModelInputBytes精确检查。READING_PLAN分页、累计选择表、补读正文、DRAFT和REVIEW都参与估算。估算超出配置预算时先按既有完整单元阅读策略安排，无法容纳的范围明确未处理；实际可见输入超过硬字节上限则拒绝对应请求。该预检可能偏保守，也不保证服务端精确fit，不将字节估算称为已证明token上界，不臆测Terra窗口。

Provider若实际返回明确的输入/context容量拒绝，保存该attempt和服务端原因，分类为PROVIDER_INPUT_CAPACITY_EXCEEDED；它不是瞬时传输故障，不对相同输入自动retry，也不偷偷提高上限、截稿或切换服务。成功DRAFT仍保留，受影响stage/范围未完成，其余独立包按既有失败隔离继续；用户可在显式新batch调整声明容量或阅读profile。stage输出profile在调用前固定，未知隐藏开销本身不新增模型调用前置门禁。

测试先覆盖新state→execution→reader→projector→Activity→M11→重开→Step07 source读取的冻结fixture链；所有关键边界观察零JDT/PersistenceAnalyzer/JSqlParser和正确Provider调用次数。补XML依赖允许安全解析已保存rawSource，不宣称所有XML parser调用为0。

版本mutation检查包括：旧M10ref冒充Step05、错误sourceRun/outputRun、旧Activity改标新来源、未知kind、partial batch误放行下游、跨包同短ref串源、缺include原件、v6字段混入v5。CLI测试覆盖全部失败/具名失败选择、scope/reuse分离、同包部分成功stage复用与稳定ID。

新设计本身没有执行上述测试，也没有新的产品结果；已有326Activities、M10与其来源SHA应维持不变。
