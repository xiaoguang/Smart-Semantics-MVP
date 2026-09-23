# 模型任务执行：并发、阶段重试与显式批次复用

本页拥有已有Java17 job pool、单一YAML、Provider绑定、逐阶段保存、包级失败处理和跨批复用。业务阅读分别由[Step06](../analysis-steps/06-flow-interpretation.md)、[Activity详细设计](activity-explanation/README.md)和[Step07](../analysis-steps/07-repository-knowledge.md)拥有。

## 1. 已实现基础与本轮目标

| 内容 | 当前分支实际状态 | 本次端到端增量 |
| --- | --- | --- |
| 材料/模型分离 | 新Step05已直接进入Activity；execution-config-v4/output-v6已实现 | 输入固定，不重新取材；完善范围完成性 |
| 并发 | 全局+Provider双上限，包内阅读/slice/attempt顺序 | 保持现有池，验证单包与绑定失败边界 |
| 保存与复用 | 阶段即时保存、显式stage复用、大包计划及M11/CLI已接通；Task 1可靠性和Task 2最终/替换scope及重开已定向验证 | Task 3补完成记录及只复用入口 |
| 失败与retry | YAML v2已有activityRetry和有界退避；Task 1错误边界与SUCCESS保存次序已定向验证 | 不增加Step07 retry；后续任务不放宽Task 1边界 |
| 范围 | 输出有activityBatchComplete；当前coverage-v3不含packet完成表 | coverage-v4补最小packetCompletion，不能只看entry出现过 |
| 新旧业务内容 | 新418条与旧326条均保存；模型身份各自保持 | 离线核对和只复用，不默认重做Activity |

Task 1可靠性修复在隔离Maven输出中通过12类79项定向测试；Task 2的plan-v2最终/替换scope、重开及配置传递通过8类80项定向测试、Spotless和独立复查。这不是完整CI，也没有真实模型/JDT/客户构建。Task 3进行中：coverage-v4真实发布重开、正式CLI零Provider采用旧直接整包结果及再次离线接续已有直接验证；当前大包局部失败范围、历史v1/v2聚合与切片读取、实际阶段复制复用、同键修订、未读导航、没有整包聚合的部分成功及Step07完成范围准入已通过定向检查。历史接续14项测试通过，完整Activity联合校验的独立复查发现已闭合。私有批次完成记录与CLI缺项展示尚待贯通；325包的真实离线核对尚未开始。历史真实运行不能替代这些后续验收。见[端到端详细设计](../end-to-end-business-delivery-design.md)及[历史Activity收口](activity-explanation/post-review-handoff-20260923.md)。

Codex超时只有确认本地进程结束才能按REQUEST_TIMEOUT处理，否则OUTCOME_UNKNOWN不默认重试。坏JSON实际响应在私有限额内保留；普通日志不暴露凭据/原文。Task 1已验证的错误分类与大包失败保留不代表整个端到端计划完成；后续先继续离线任务，再按计划执行无提示小样，小样展示后的全仓扩大需要用户确认。


## 2. Job与阶段

一个技术packet对应一个ACTIVITY job。小包为DRAFT→REVIEW；大包先有有限READING_PLAN，再按稳定slice顺序各自DRAFT→REVIEW。每个stage有唯一stageKey：

- READING_PLAN：包含导航page或补读round。
- DRAFT / REVIEW：包含sliceKey；DIRECT路径用固定whole-packet sliceKey。
- retry的attempt ordinal不改变stageKey、sliceKey或业务范围。
- 同一job所有请求固定Provider/account/model/effort；每次请求独立发送完整实际输入。

模型REVIEW看到本slice完整实际阅读包、完整实际DRAFT和missingEntryKeys/未读范围；不得只发送摘要、patch或假设前会话记忆。多个slice聚合只由程序执行，不另加全包模型摘要/合并。

其他job保持现行固定序列：

| Job | 固定序列 | 屏障 |
| --- | --- | --- |
| CATALOG / CATALOG_SHARD / CATALOG_MERGE | DRAFT→完整REVIEW | 卡片单包或全部shard后唯一merge |
| PROCESS_MATERIAL_SELECTION | 一次系统认识/全局选材决策 | 原目录可作为输入，不重做目录 |
| PROCESS_READING_CHECK | 每候选一次保留/移出/可空补读决定 | 实际补读结束、完整packet封闭后重建 |
| CANDIDATE_PROCESS | 事实DRAFT→业务WRITE→最终RULE_REVIEW | RULE_REVIEW看完整原包+实际DRAFT+实际WRITE |
| REPOSITORY_CONSOLIDATION | DRAFT→完整REVIEW | 全部候选处置闭合后，不能改写过程正文 |

合法UNRESOLVED/INSUFFICIENT_MATERIAL是内容处置；未知ID/ref、来源损坏与结构失败不能伪装为这些状态。Step07不因Activity retry增加第三选择或额外过程修复轮。

Step08当前只有历史reader/确定性renderer，其操作为零Provider；本轮不新增九章job或恢复已退役的序列。

## 3. 唯一配置与模型绑定

`repository-run-config-v3`在既有YAML增加Activity阅读配置；历史v2严格按原字段读取。加载、传递、实际执行上限及历史计划重开已通过Task 2的八类80项定向测试和Spotless；这不是Task 9的完整本地CI。以下为配置字段节选，不修改历史运行配置：

```yaml
schemaVersion: repository-run-config-v3
sourceAnalysis:
  javaEngine: jdt
  modelJobs:
    maxConcurrentJobs: 4
    providers:
      pro:
        kind: codexSubscription
        quotaScope: personal-pro-account
        maxConcurrentJobs: 4
        model: gpt-5.6-luna
        reasoningEffort: high
        executable: /absolute/path/to/codex
        timeoutSeconds: 3600
        auth:
          mode: chatgpt
          codexHomeEnv: SOURCE_ANALYSIS_PRO_HOME
    routing:
      activity: [pro]
      processGroup: [pro]
      repositorySummary: [pro]
      report: [pro]
    activityRetry:
      maxAttempts: 3
      initialBackoffMillis: 1000
      maxBackoffMillis: 30000
      multiplier: 2
      jitterRatio: 0.2
      retryableReasons: [TRANSIENT_TRANSPORT, RATE_LIMITED, PROVIDER_UNAVAILABLE, REQUEST_TIMEOUT]
      stageOverrides:
        REVIEW:
          maxAttempts: 3
  activityReading:
    maxNavigationPages: 128
    maxReadingRounds: 4
    maxSlicesPerPacket: 32
```

既有journalDirectory、outputDirectory、JDT安装、固定材料state、technical与业务输入输出profile仍由同一配置拥有，示例为相关字段节选。Provider的capacity声明本次使用的contextWindowTokens与providerOverheadTokens/reasoningReserveTokens保守预留，tokenAccounting使用UTF8_BYTE_ESTIMATE或记录已有离线工具的实际计量方式，详见[容量合同](activity-explanation/integration-contracts.md#8-容量失败与验收)。缺必需数值/数值非法才是CAPACITY_PROFILE_REQUIRED；未知隐藏tokenizer或推理开销不否决入口，不从模型名猜窗口。READING_PLAN完整实际序列化与累计选择表同样预检，但估算不保证服务端精确fit。

- maxAttempts包含首次请求；1关闭自动retry。全局默认3，允许READING_PLAN/DRAFT/REVIEW三个stage family单独覆盖；其它未知stage拒绝。
- 初始/最大退避为非负，max>=initial；multiplier>=1，jitterRatio在[0,1]；maxAttempts和reading上限为正整数。未知/重复key、reason、非法Provider/并发在启动前拒绝。
- retryableReasons可显式增加INVALID_JSON、RESPONSE_SCHEMA_INVALID、UNKNOWN_REFERENCE。它们默认不retry；每次仍是同Prompt/Schema/输入的新attempt，不能追加纠错业务答案或语言黑名单。
- 来源损坏、输入/配置不合法、认证失败、runtime identity漂移、容量不足、取消及存储错误不可通过reason名单放行。
- timeoutSeconds仍是Provider单attempt超时；maxAttempts不是超时秒数，退避也不占单次调用timeout。有效Retry-After至少被尊重；若服务要求等待大于maxBackoffMillis，则该stage结束为RETRY_DEFERRED并保存earliestRetryAt供显式新batch参考，绝不缩短服务端要求提前发送。
- reading省略整段时默认128/4/32；显式提供时必须给出全部三个正整数字段，不接受未知字段。32指当前有效scope上限，不累计已被明确替代的历史定义。它们不表达费用预算或行业分类，不能据运行结束推断阅读义务完整。

订阅Provider使用指定Codex登录上下文、ChatGPT auth和read-only sandbox，屏蔽API-key覆盖。显式openaiApi Adapter仍可配置，但只有获准实际运行才启用，不作为订阅失败fallback；不能换模型/账户/effort补成功。同账户/项目的多个key或会话必须放在同一Provider配置下共享cap；两个Provider重复quotaScope仍按现有加载器在启动前拒绝，不新增跨Provider自动合并配额。

## 4. 并发如何约束所有attempt

复用已有Java17有界executor和completion queue，不增加内层pool。全局和绑定Provider都有名额时才派发packet；一个packet从READING_PLAN到最后slice结果保存保持同一job名额。内部stage/attempt顺序执行，退避期间仍持有名额，避免实现第二个定时队列或让实际并发超过上限。

退避使用可中断等待，不busy loop；尚有空位的其他packet照常执行。保守占位可能降低吞吐，真实授权运行再测，不能凭线程数承诺速度。成功stage即时写入而不是等整个包REVIEW后才落盘。

同包同stage同一时刻最多一个attempt；新attempt必须等待前一个请求和本地Provider进程/句柄终止并保存终态。已超时但不能确认结束的请求记OUTCOME_UNKNOWN，保留原记录，不在同一batch并行重发；用户显式新batch才可以重新请求，且可能重复服务端工作。没有同run崩溃接管或旧STARTED重放。

固定Provider路由先分配后排队；可以调度其他已有合法绑定、恰有空位的job，不能动态换绑。Worker通过既有私有store同步保存自己唯一目录下的stage，保存成功后才继续；它返回不可变完成/失败结果，不改共享Activity集合。Coordinator拥有并发计数、包级结果保存和稳定aggregate安装，不必等整包完成才让DRAFT落盘。

## 5. 阶段attempt算法

对于一个已确定stage：

1. 计算实际完整input、Prompt/Schema、scope/ref映射和binding指纹，核验容量；失败则零请求，记录stage原因。
2. 找到本batch已保存成功stage，或按显式reuse来源验证成功stage；匹配则零请求取回，不能把STARTED/FAILED响应当成功。
3. 分配 `requestIdentity=(modelBatchId,jobKey,stageKey,attemptOrdinal)`，先保存不可变request/STARTED元数据，再调用固定Provider。
4. 核对runtime identity、实际响应、JSON/schema/ref/预算/coverage；成功后原子保存响应、验证和SUCCESS记录，下一stage才可读取。
5. 失败记录错误类别、是否请求已启动/已结束、实际响应和attempt序号。retry名单允许、attempt<maxAttempts且前请求已结束时退避，再以新requestIdentity重发**同一stage**。
6. 不可retry或穷尽则返回该stage终态；同slice后续stage不执行。其他可独立slice可以继续，依赖失败阅读计划的slice不能猜测启动。
7. 所有可独立slice处理完，packet带成功结果与未完成scope返回Coordinator。不能因一slice成功就丢掉另一个失败scope。

退避为 `min(maxBackoffMillis, initialBackoffMillis * multiplier^(attemptOrdinal-1))`，应用配置jitter后再次clamp到[0,maxBackoffMillis]；有效Retry-After不超过该上限时作为等待下界，超过则按RETRY_DEFERRED退出当前stage。退避只影响时间，不能改输入、消耗读取轮或使attempt计数重置。

第一次DRAFT成功、REVIEW失败两次后成功的实际调用为1次DRAFT+3次REVIEW。REVIEW始终使用同一成功DRAFT；成功DRAFT不得由新attempt覆盖。若Prompt、来源或scope改变，它是新stage输入，只能在明确的新batch重建相应依赖。

## 6. 哪些失败影响一个包，哪些影响运行

| 失败类别 | retry/调度 | 最终处置 |
| --- | --- | --- |
| 瞬时网络、限流、Provider暂不可用、已终止的request超时 | 默认按当前stage有界retry | 穷尽保存包/slice/stage失败，继续其他包 |
| 坏JSON、schema、未知ref | 默认不retry；可在Activity配置显式允许 | 失败响应不可成为DRAFT或REVIEW输入 |
| 不完整业务内容但结构合法 | 按DRAFT/REVIEW既有语义协议表达或unexplained | 不通过执行retry制造额外内容改稿轮 |
| 单包容量、必需单元未读、原技术局部缺失 | 不retry同输入 | 该范围未处理，其他包继续 |
| Provider明确拒绝输入/context容量 | PROVIDER_INPUT_CAPACITY_EXCEEDED，不按transient retry同输入 | 保存实际attempt/原因和成功前置stage；显式新batch可调整声明容量/阅读profile |
| 全局配置非法、固定材料整体损坏、认证预检失败 | Provider启动前fail-fast | 零请求；保存诊断 |
| 运行中确认某Provider认证/配置不可用或identity漂移 | 停该binding新派发；同绑定未启动包记BLOCKED_BY_PROVIDER | 其他合法Provider任务可结束；不fallback |
| 共享来源损坏、安全策略失效、无法可靠保存、用户取消 | 停本batch新派发，安全收尾/取消在途任务 | 运行错误；不安装无法验证的aggregate |

不建设新的熔断器服务。Provider不可用由现有Coordinator的绑定状态/终止原因控制；普通包失败不能全局stop，真正共享运行安全故障也不能让326包重复发无效请求。

默认transient分类只来自可靠的结构化Provider状态/本地已确认超时终止，不靠中文或英文消息子串识别。当前Codex订阅进程边界没有已验证的typed error code：非零退出的stdout/stderr自由文本统一记UNKNOWN，不自动按限流、容量或配置重试；其他Provider已经提供的可靠typed reason仍按原策略处理。实际response优先、否则stdout/stderr各自在4096字节上限内按原字节保留到私有失败记录，公共异常与普通日志不携带这些原文。

## 7. 私有保存与复用

每attempt使用新私有目录：

```text
model-jobs/<batch>/activity/<packet>/<stage-key>/attempt-<n>/
  request.json
  started.json
  response.json
  validation.json
  outcome.json
```

文件按各自发生点不可变安装；不存在的response不造空成功。同一attempt不覆盖前状态文件，失败与成功均保留。stage成功索引引用唯一已验证attempt；新Activity的完整解释阶段结果使用 `model-job-reviewed-result-v4`；大包聚合另用现有 `activity-packet-result-v1` 引用plan、slice packets和结果，两者不能混为同一wire。一个已存在的完成聚合是对其原plan和每个slice成功stage的声明；即使包因后续slice失败而没有聚合，已存在的单slice v4完成声明也必须能找到自己的DRAFT/REVIEW成功索引。重开时先核对这些引用、source mapping与结果一致，缺失/损坏不能当cache miss或触发新Provider请求。沿用既有私有store实现，不创建事件重建/恢复数据库。

阶段fingerprint覆盖实际输入字节、上游成功stage内容、projection/reading plan版本、Prompt内容、Schema、scope与SourceRef映射、有效业务profile、模块版本、Provider/account/quotaScope/model/effort/sandbox。排除batchId、attempt序号、时间、并发、日志目录、退避和秘密值；仅改maxAttempts不使已成功内容失效。

| 新Activity可复用内容 | 条件 |
| --- | --- |
| 已成功READING_PLAN | 原实际展示页/正文、问题、决策结构和binding完全匹配 |
| 已成功DRAFT | 完整阅读包、业务scope、Prompt/Schema/输出profile与binding完全匹配 |
| 已成功REVIEW | 还必须匹配实际DRAFT和REVIEW原材料；完整结构/coverage验证通过 |
| STARTED/失败/无响应/损坏stage | 不复用；损坏需报告，不能默默调用模型掩盖 |
| 旧v2完整Activity pair | 按历史整job合同复用，不拆出DRAFT伪装新stage |
| Step07 | 继续完整pair/triple/decision复用；不获得Activity的新续审规则 |

手动retry只通过显式新batch，按[CLI合同](activity-explanation/integration-contracts.md#6-正式cli与重试)读取失败范围与指定reuse来源。成功slice、成功DRAFT的键保持稳定；失败stage重新attempt序号从新batch的1开始，旧记录原样保留。未改变scope的成功其他包可显式零调用继承。

材料始终属于sourceRunId；modelBatchId拥有新输出。不能删除旧journal、清空FAILED状态或重扫源码来获得新的request身份。batch不是新的ReaderCandidateRound，不能绕过最终内容修订/候选授权。

## 8. 聚合、失败清单与下游

所有packet终态后，按固定packet/slice/localActivity键聚合，完成顺序不影响业务身份。完整规则、条件、公式、正文和ref不截断，多个slice直接保留多Activity。

每个成功REVIEW已经私有保存。若还存在失败/必需未读范围，可安装一次含成功子集和完整不足coverage的M11，run仍FAILED、CLI退出非零、activityBatchComplete=false。共享结构/来源损坏时仅保留可验证私有结果，不安装不可信aggregate。成功M11 payload不等于整个batch完成。

私有activity-batch-result-v1与CLI/inspect给出packet、entry展示、slice、stage、reason、attemptsUsed/maxAttempts、已成功可复用stage和下一步。普通错误清单不带敏感原文。用户可定向一个packet或全部失败包创建新batch，不能一处失败就默认重做所有材料。

Step07不会自动消费未完成batch，不自动模型重启、不调用JDT、Capture或Step05；即使有成功Activity，也不能悄悄缩小入口或slice分母。历史326Activities和原M10仍保留为独立可读输入。

## 9. 直接测试与验收

以下为已批准端到端计划的直接验证范围。Task 1已通过上述79项直接测试；后续新增合同仍须各自验证，不能沿用旧提交的通过记录：

- scripted Provider制造DRAFT成功/REVIEW两次瞬时失败后成功，精确断言1+3调用、DRAFT逐字相同、attempt记录不覆盖。
- maxAttempts=1不retry；退避与timeout独立；stage override、未知reason/配置与非retry错误拒绝。
- 同包同stage单飞、全局/Provider峰值不超限；retry期间其他合法包可继续；不同完成顺序结果/ID一致。
- 一个包retry穷尽不阻断其他包成功保存；最终失败清单完整、非零且Step07调用0。
- 共享输入预检失败请求0；Provider运行中认证失效停止其队列，另一合法binding可完成，无换绑。
- 成功DRAFT/完整slice跨新batch复用；改scope、Prompt、Schema、模型使相应阶段失效；只改并发/退避不失效。
- 未结束attempt不重叠重发；旧STARTED不删除；保存故障不能返回成功或继续消费未保存DRAFT。
- 新Step05 readingMaterialCheckpoint可重开并进入真实模型输入fixture；JDT/PersistenceAnalyzer/JSqlParser调用0；XML依赖投影安全读取允许。
- 单entry多slice成功/失败混合不误全覆盖；大局部稿程序聚合不丢条件、不新增摘要调用。
- 旧Activity pair、Step07完整三阶段/单决策和历史renderer继续按原规则读取；无顺带自动retry。

只运行新增或直接覆盖测试，重型验证串行。真实产品调用须另有明确授权；先小包，再新增单据2MB样例，记录实际请求数、各阶段耗时、重试/未读/失败范围和业务规则准确性，展示结果并由用户决定扩大。线程数、材料字节减少或entry集合闭合都不能代替业务接受。

## 10. 不扩建的内容

继续使用一个RepositoryAnalysisAgent、一个CLI、现有Provider Adapter、Java17线程池与canonical/private store。不增加队列服务、向量库、自研编译器/业务词表/证据链、自动换模型/账户、同run恢复或无限模型修复。新版本精确表由[接入合同](activity-explanation/integration-contracts.md)唯一维护。

## 11. 端到端收尾：范围、历史只复用和新过程任务

Activity未完成必须按最终有效计划计算，而不是遍历历史unknown寻找错误词；失败范围/普通业务未知/原无packet导航缺口分开。公共packetCompletion与私有当前义务按[集成合同](activity-explanation/integration-contracts.md)一致保存。先验证完整阅读响应，再原子接受选择并保存SUCCESS，非法unit不能成为成功阅读阶段。

显式新batch接续大包时先重开原计划和已冻结scope。合法成功slice和成功DRAFT不因另一slice失败丢失；后续slice容量失败也要公开保留先前成功。更改scope不是“再试同一阶段”，受影响稿件失效前先告知新生成范围。

新 `--reuse-only` 正在Task 3实施：新run内纯读旧结果/范围核对并发布M11，不初始化Provider、不自动补模型请求；与主动retry互斥。不完整/无法判定时非成功，成功子集仍可查看，Step07不偷偷缩小分母。历史完整已审输入采纳与新版Prompt精确stage复用分开，不能放宽后者的指纹。新离线批次沿用execution-config-v5的明确REUSE_ONLY模式及私有batch-result-v2采纳记录，保留原实际模型身份，并可作为后续显式复用来源；不伪造新模型请求。具体范围和版本检查统一见[接入合同](activity-explanation/integration-contracts.md#5-阅读计划v2历史核对及只复用输出)。

execution-config-v5冻结有效activityReading值及Step07实际输入编码/任务合同。工作区已接通Activity配置保存和历史v4严格读取；Step07编码字段在后续对应步骤落地。YAML config-v3开放activityReading；已有activityRetry不迁位置。run-output-v6仍用activityBatchComplete，不新增modelBatchComplete别名。

Step07沿同一池保持单候选DRAFT→WRITE→RULE_REVIEW。新局部编号与WRITE约束只改变实际输入/Prompt/Schema/私有三阶段合同，见[Reconstructor](business-process-discovery/candidate-process-reconstructor.md)。目录/单次决策/归并仍各自pair或单次；没有新增业务去重模型、第四次写作或自动retry。完整候选失败仍按现有fatal停止新派发，不套用Activity单包失败继续策略。

本轮真实模型调用0。后续执行先完成本次Activity范围的离线核对、正式CLI/scripted全链，再具名真实样例；用户看过样例并同意后才扩大全仓。旧批次/失败输出不改写。
