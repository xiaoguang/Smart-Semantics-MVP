# 模型任务执行：并发、批次复用与过程发现调度

本文拥有模型 job 的配置、Provider 绑定、两级并发、阶段屏障、逐 job 保存、失败处理和跨批复用合同。业务内容分别由 [Step06](../analysis-steps/06-flow-interpretation.md)、[Step07](../analysis-steps/07-repository-knowledge.md)、[Step08](../analysis-steps/08-nine-section-document.md) 及[中文 Prompt](../references/semantic-interpretation-prompts.md)拥有。

## 1. 当前实现与目标增量

### 1.1 已实现并继续复用

- `repository-run-config-v2` 的单一 YAML/JSON 配置、全局并发和每 Provider 并发。
- Codex Subscription 与显式 OpenAI API Provider Adapter、认证隔离、稳定路由和 runtime identity 校验。
- Java 17 有界 job pool、completion queue、阶段屏障、不可变输入和稳定聚合。
- 每个 Activity 或当前 Candidate-process job 内部严格执行 DRAFT → 完整 REVIEW；不同 job 可以并行。
- 每个完整已审 job 立即保存到批次私有目录；aggregate 只在阶段全部闭合后发布一次。
- `repository-run-state-v3`、`model-job-execution-config-v2`、`analysis-run-output-v4`、固定材料直接重开、显式新 model batch 和完整已审 job 复用；历史 output-v3 保留严格读取。
- 固定 jshERP 检查点已经保存 **326/326** 个 ReviewedActivity。新过程发现直接从该检查点开始，Activity Provider 调用数为零。

### 1.2 当前Step07和本次增量

BusinessProcessDiscovery已有目录、完整Activity、详细过程两轮、唯一归并、五文件v2发布。46过程是历史v1，本轮选定的后续已存目录为24候选；旧340 singleton已退役。

本轮按[跨对象补充设计](../supplements/cross-object-process-reconstruction/README.md)增加旧目录输入、单次全局选材和每候选一次阅读检查；源码在过程DRAFT前到位。复用调度、两级并发、Provider和已审pair保存。单次阅读决策须单独保存，不伪装成pair；326Activity不重跑。

### 1.3 仍未实施的边界

新增阅读决策、冻结文件取材接线和DRAFT前封包尚未实施。v2正文/五文件已经实现。当前无Step08生产器，历史reader/renderer保留；本轮不生成九章。

## 2. 目标阶段图

一般新仓库的完整路径是：

```text
01–05 技术取材与材料保存（零模型）
  → Activity jobs 并行：每份材料 DRAFT → 完整 REVIEW → 保存
  → 全部 Activity 稳定聚合
  → 全部卡片可容纳时：一个 Catalog job DRAFT → 完整 REVIEW → 保存
    或，全部卡片不可容纳时：
       Catalog-shard jobs 并行 DRAFT → 完整 REVIEW → 保存
       → Catalog-merge job DRAFT → 完整 REVIEW → 保存
  → 一次全局选材（旧目录直接读取时跳过前面的目录生产）
  → 首批取材（零模型）
  → Reading-check jobs 并行：每候选一次决定 → 执行可空补读 → 封包
  → Candidate-process jobs 并行：同一完整包 DRAFT → 完整 REVIEW → 保存
  → Repository-consolidation job：DRAFT → 完整 REVIEW → 保存
  → BusinessProcessPublisher 确定性发布 catalog、coverage、business-processes.md（零模型）
  → 未来可接九章；本轮在五文件发布停止
```

固定 jshERP 当前执行从保存的 326 个 ReviewedActivity 开始：

```text
原始已保存目录 + Activity checkpoint + 冻结文本
  → 全局选材（不重跑原目录模型）
  → 每候选一次阅读检查 + 可空补读
  → Candidate-process jobs
  → Repository consolidation
  → process catalog / business-processes.md
  → 本轮不调用九章
```

它不得重新调用 Capture、JDT、JavaParser、Step03–05、BusinessMaterialBuilder 或 ActivityExplainer。模型失败也不是重扫源码或重跑 326 个 Activity 的理由。

## 3. Job 单位与阶段屏障

### 3.1 一个 job 的含义

内容生成job仍最多两次Provider请求：

```text
完整固定输入
  → DRAFT
  → 程序校验并准备 REVIEW 材料
  → 完整 REVIEW
  → 程序校验并原子保存完整结果
```

同一 job 的两轮使用启动前固定的同一 Provider、账户服务、模型和 reasoning effort。REVIEW 必须携带完整实际 DRAFT 和该任务合同规定的完整原材料，不能只传摘要、patch 或“请修正”指令，也不能假设另一会话记得 DRAFT。

目标过程job不在两轮之间补源码：DRAFT前的ProcessReadingPacket同时用于两轮。新增全局选材与候选阅读检查各只有一次Provider请求，存为单次决策；不是过程pair中的第三次调用。阅读检查必需执行，实际补读清单可空，仍缺材料写UNRESOLVED。不能由Java判断语义足够而跳过模型检查。

### 3.2 新 job 类型

| Job 类型 | 输入 | 输出 | 并发/屏障 |
| --- | --- | --- | --- |
| `ACTIVITY` | 一个 BusinessMaterial | 完整 ReviewedActivity 与 unexplained entries | 已实现；同阶段并行 |
| `CATALOG` | 可以一次容纳的全仓 ActivityIndexCard | 唯一业务领域、别名和重叠 Candidate Process | singleton；成功后不再执行 shard/merge |
| `CATALOG_SHARD` | 一组精简 ActivityIndexCard | 已审局部目录与该分片全卡片处置 | 仅在全仓不可一次容纳时使用；分片间并行 |
| `CATALOG_MERGE` | 全部已审分片目录与全局卡片分母 | 唯一业务领域、别名和重叠 Candidate Process | 仅分片路径使用的 singleton；完成后才能组装候选 |
| `PROCESS_MATERIAL_SELECTION` | 旧/首次目录、全仓Activity导航、冻结文件目录、可选问题 | 候选增量和首批阅读清单（单次决策） | 一个全局任务 |
| `PROCESS_READING_CHECK` | 一个候选的首批实际材料、全仓导航和文件目录 | 可空补读清单、范围修订及未知（单次决策） | 候选间并行；每候选恰一次 |
| `CANDIDATE_PROCESS` | 封闭完整阅读包 | 一个或多个已审过程或不足/支撑处置 | 候选间并行；全部完成后才能归并 |
| `REPOSITORY_CONSOLIDATION` | 全部已审过程的完整业务投影、ActivityUse、状态和 coverage | 合并、父子、相关、替代和拒绝决定 | singleton；不能改写过程详情 |
| `WHOLE_REPORT`（未来，当前无生产器） | 已发布catalog与coverage | 已审九章JSON | 不在本轮路径 |

`BusinessProcessPublisher`、source-ref 解析和 Markdown renderer 都是确定性程序操作，不是模型 job。

### 3.3 屏障和稳定性

- 一个阶段所有必需 job 都有终态后才进入下一阶段。
- Catalog 分片完成顺序不决定 Activity 处置、candidate ID 或 merge 输入顺序。
- Candidate 完成顺序不决定过程 ID、最终排列或 aggregate bytes。
- 同一 Activity 可被多个 Candidate job 只读共享；每个 job 创建自己的 ActivityUse，不修改原 Activity。
- 全局选材是屏障；同候选检查结束、可空补读与封包完成后才能DRAFT。先汇总阅读修订后的成员/处置再稳定派发重建，避免并发完成顺序影响覆盖。所有重建结束后才归并。
- singleton job 不能为提高吞吐拆成逐章、逐字段或逐规则 job，因为它们需要全仓视野。
- `INSUFFICIENT_MATERIAL`、`STANDALONE`、`UNCLASSIFIED` 和 `UNRESOLVED` 是合法、可覆盖的内容处置；未知 ID、漏分母或伪造来源才是结构失败。

## 4. 单一 YAML 与两级并发

现有严格 `repository-run-config-v2` 和两个并发控制保持不变：

- `sourceAnalysis.modelJobs.maxConcurrentJobs`：整个模型阶段最多同时运行多少个 job；
- `sourceAnalysis.modelJobs.providers.<id>.maxConcurrentJobs`：一个 Provider/账户额度范围最多同时运行多少个 job。

并发数是 in-flight job 上限，不是 Activity 数、Candidate 数、分片大小、Builder K 或执行总量。符合范围的 326 个任务在并发为 4 时仍应全部排队处理，不能只处理前四个。

```yaml
schemaVersion: repository-run-config-v2
sourceAnalysis:
  javaEngine: jdt
  jdt:
    installation: /opt/source-analysis/tools/jdtls-1.61.0
    javaHome: /opt/source-analysis/tools/jdk
  modelJobs:
    maxConcurrentJobs: 6
    journalDirectory: /absolute/path/to/ignored/run-journal
    outputDirectory: /absolute/path/to/ignored/inspection
    providers:
      pro:
        kind: codexSubscription
        quotaScope: personal-pro-account
        maxConcurrentJobs: 4
        model: gpt-5.6-luna
        reasoningEffort: high
        executable: /absolute/path/to/codex
        timeoutSeconds: 600
        auth:
          mode: chatgpt
          codexHomeEnv: SOURCE_ANALYSIS_PRO_HOME
      api:
        kind: openaiApi
        quotaScope: approved-api-project
        maxConcurrentJobs: 2
        model: gpt-5.6-luna
        reasoningEffort: high
        endpoint: https://api.openai.com/v1
        timeoutSeconds: 600
        auth:
          mode: apiKey
          apiKeyEnvs: [SOURCE_ANALYSIS_API_KEY]
    routing:
      activity: [pro, api]
      processGroup: [pro, api]
      repositorySummary: [pro]
      report: [pro]
```

本次过程设计不顺带重置配置 wire。目标协调器暂时按现有严格路由键映射：

| 现有 YAML 路由键 | 目标 job |
| --- | --- |
| `activity` | `ACTIVITY` |
| `processGroup` | `CATALOG_SHARD`、`PROCESS_READING_CHECK`、`CANDIDATE_PROCESS` |
| `repositorySummary` | `CATALOG`、`CATALOG_MERGE`、`PROCESS_MATERIAL_SELECTION`、`REPOSITORY_CONSOLIDATION` |
| `report` | `WHOLE_REPORT` |

这只是复用现有 Provider 选择入口，不表示旧 Process-group 算法仍参与执行。若未来需要让 catalog 与 candidate 使用不同 Provider，再单独设计并升版 routing；本次不得增加别名、双读或隐式 fallback。

严格加载继续拒绝重复 key、未知字段/kind/auth mode、非法并发、未知 Provider 引用；模型执行前检查必需的认证环境变量。`plan-materials` 可以省略 modelJobs；有配置时只校验结构，不构造 Provider、不解析秘密值、不调用模型。

## 5. Provider、认证与任务绑定

一个 Provider 定义代表一个账户或服务额度范围，不等于一把 key 或一个新会话。多个 key 共享组织、项目或模型限额时必须在同一个 Provider cap 下；不同 Codex 会话若属于同一账户，也不产生新额度。

`codexSubscription` 强制 ChatGPT 认证，使用指定登录上下文、read-only sandbox 和显式 model/effort；屏蔽 API-key 认证覆盖。`openaiApi` 只在 YAML 明确定义并获得实际运行授权时使用。Pro 失败或额度耗尽不自动切到 API，API 失败也不切账户、模型或 effort。

每个 job 在派发前按稳定顺序固定 Provider；两轮和保存都持有同一 binding。执行时可以越过暂时无空位的 Provider，派发已绑定到其他有空位 Provider 的后续 job，但不能为填满槽位改写绑定。journal 和私有输出按 batch、job kind、job key、Provider namespace 隔离。

Provider request 只包含当前业务任务的 clean input 和闭合 JSON Schema，不包含 sourceRunId、modelBatchId、复用来源、队列统计、并发、密钥、host path 或材料 checkpoint。

## 6. Java 17 池与保存

协调器使用一个有界 Java 17 executor、一个 completion queue、全局计数和每 Provider 计数。只有全局和绑定 Provider 都有名额时才提交 job；pair名额持有到 REVIEW 结果原子保存，单次decision名额持有到该决策原子保存。不能把整个 Provider 调用包在 `synchronized` 中，也不能把所有大输入预复制到无界队列。

worker 返回不可变结果，不修改共享 Activity、candidate 或 aggregate。协调器收到完成结果后立即写入：

```text
model-jobs/<model-batch-id>/<job-kind>/<job-key>/reviewed-result.json
```

以上为现有内容pair的保存地址。新增单次选材/阅读检查使用同级`decision-result.json`，不能写入reviewed-result冒充完整pair；实际ReadingRecord和ProcessReadingPacket按候选存入同一私有任务目录。

同一 job key 在同一批次只能提交一次。不同 job 即使 clean input bytes 相同也独立保存；同一业务输入在不同批次通过显式 reuse record 关联，不能抢写旧结果。阶段 aggregate 按程序稳定顺序安装一次，不把 publisher 放进 worker 循环。

过程pair保存完整packet依据、DRAFT、REVIEW与最终处置。单次decision保存原输入/响应、指纹、身份与终态，并保存读取记录/最终包；不能造空DRAFT/REVIEW伪装pair。沿用简单私有JSON保存，不新建运行状态机。

## 7. 固定材料与独立模型批次（已实现基础，扩展到新 job）

### 7.1 现有运行身份

- `sourceRunId` 属于材料原生产 run；失败的模型执行不修改它。
- `materialsCheckpoint` 是带地址、root 和 receipt SHA 的完整 M10 reference，不是仅凭可读 ID 猜路径。
- `modelBatchId` 使用新 `AnalysisRunId`，拥有该批 Activity/Process/Report 输出。
- Provider请求日志按`providerKey/modelBatchId`隔离。同一批次内的STARTED仍禁止重放；用户显式启动新批次时，旧STARTED日志原样保留，但不能阻断新批次完整执行未完成job。
- `repository-run-state-v3` 保存实际材料 profile、producer version 和 basis；当前`model-job-execution-config-v2`保存本批范围、服务与可选 reuse 来源；`analysis-run-output-v4`区分材料、Activity和过程输出归属，历史v3严格只读。

本次目标仅将私有执行配置升至`model-job-execution-config-v3`，增加旧目录输入、完整verified inventory引用与可空focusQuestion；保留旧v2严格只读。YAML、材料state和output版本不变。CLI创建/选择QUEUED run后、Provider初始化前原子保存这些真实选择；已有绑定不匹配须使用新run。内部`ProcessDiscoveryRequest`接收验证后的依赖，不改变公共Agent请求协议。具体接线见[模块设计](../supplements/cross-object-process-reconstruction/module-design.md#7-运行接线私有保存和版本)。

源码、入口、取材规则和材料未变时，改变模型、并发或日志目录不使材料失效。改变源码、引擎、入口选择、分包或代码内容时必须显式生成新材料。读取缺失、损坏或来源不一致的材料时失败，不能偷偷调用 Builder 或 JDT 补齐。

### 7.2 复用单位

内容生成的复用单位仍是完整已审pair，不是单次网络响应。新增单次阅读decision是独立类型，仅完整成功、已验证且精确匹配时显式复用；它不是半轮内容生成。旧目录作输入读取也与直接任务复用不同。

| 旧 job 状态 | 新批次行为 |
| --- | --- |
| 未启动、DRAFT 失败或结果未知 | 新 job 做完整 DRAFT＋REVIEW |
| 只有 DRAFT，或 REVIEW 失败/未知 | 保留旧草稿作诊断；新 job 重做完整 pair |
| 完整已审结果、输入与 binding 全部匹配 | 零 Provider 调用复用，保存来源记录 |
| 声称完成但结果损坏 | 明确失败，不以隐式重调掩盖损坏 |
| 输入、Prompt、Schema、profile、producer 或 Provider binding 改变 | 不复用，按新批次明确配置执行 |

稳定 `jobKey` 包含 job kind 与真实业务输入成员，不含 batchId。`inputFingerprint` 包含完整 clean input、allowlist 映射、实际 Prompt/Schema、内容 profile、Module/producer version 和实际 Provider/account/model/effort/sandbox；不包含新 batch ID、时间、并发、日志目录或秘密值。

单次decision同样验证`providerBindingKey`、`quotaScope`与`ModelRuntimeIdentity`中的provider/model/effort/sandbox；实际关注问题和读取结果是输入的一部分。旧目录作输入则要求来源批次已停止、完整目录pair有效、Activity检查点/集合及冻结basis一致，不要求旧批次下游也成功或旧Prompt与新Prompt相同。两者不能混成一个复用判定。

新过程路线按依赖逐层复用：

- Activity checkpoint 已经完整匹配时，不启动 Activity jobs。
- ActivityIndexCard 投影或 Activity 内容变化时，只失效相应 catalog 输入。
- Candidate成员/context、完整Activity、实际阅读包、statement/ref映射、关注问题、Prompt或源码变化时，失效相应任务及依赖；只改并发/路径不失效。
- 仓库归并输入变化时不能沿用旧 catalog。
- catalog 或 coverage 变化时不能沿用旧九章。

复用不会把 `PARTIAL` 或 `UNRESOLVED` 升格为完整业务验收。原生产批次、结果 SHA 和引用保留在新批次的 reuse record 中。

### 7.3 固定 jshERP 检查点的起点

当前输入是326条已审Activity和显式选择的已保存目录，不是340 singleton。新model batch直接重开；原目录模型、前五步、JDT、Builder、Activity调用均为0。两个新读决策和过程/归并才是本轮新增模型工作。

旧Process/九章留作对照；旧目录可作选材输入。Activity与更完整源码有局部差异，在本次过程说明并保留原记录，不自动启动delta review。确需重做Activity必须先与用户讨论并另获同意。

## 8. Fatal、未决结果与显式新批次

任一 fatal 被观察到后，协调器立即停止派发新 job。尚未真正开始 DRAFT 的任务记为本执行未启动；已经开始且自身 DRAFT 合法的 job 在原 binding 和既有超时内完成唯一 REVIEW 并保存。自身 DRAFT 失败不进 REVIEW；自身 REVIEW 失败保留诊断。收齐已开始 job 的终态后，当前批次以 FAILED 结束，不启动依赖它的下游。

未知/未提供Activity/ref、坏Schema、来源漂移、损坏、遗漏必须处置、started失败仍fatal。选材/阅读检查可以按合同增减成员；最终REVIEW不能引入包外材料。合法未命中/不足和UNRESOLVED不伪造失败或完整性。单次decision失败保存诊断，无隐式重发；pair沿用既有失败纪律。

同一批次没有自动 retry、reroute、模型降档、第三轮修复、failed-call replay 或终态修复。用户显式启动的新 model batch 可以按 §7 复用完整结果并重新执行未完成 job；其Provider请求日志使用新的batch目录，保留旧 STARTED/FAILED 记录，不能称为同一请求恢复成功。无法确认旧 started 请求的服务端状态时，必须承认新批次可能再次产生该任务的模型工作。

model batch 是运行身份，不是调用许可，也不推进 Reader Candidate Round。已有完整最终候选后的内容修正仍遵守具名 finding 和允许的 Round 2；不能靠换 batchId 无限生成替代报告。

## 9. 验收与可观测性

### 9.1 已实现基础的回归

- 全局 6、Pro 4、API 2 从不越限，同 Provider 的独立 job 确实可同时执行。
- 并发 1、4、6 处理相同任务集合时，聚合业务内容和顺序一致。
- DRAFT、完整 REVIEW、missingEntryKeys 与 unexplained entries 不串包。
- 中途 fatal 后不派发新 job；已开始的合法 pair 可保存；下游不启动。
- 完整已审 job 跨 batch 零调用复用；孤立 DRAFT 不冒充完整结果。
- 直接重开固定材料时 Capture、JDT、Step03–05 和 Builder 调用数为零。

### 9.2 新过程 job 必须新增的直接验收

- 所有 ActivityIndexCard 恰进入一个目录分片，并最终进入 candidate、standalone 或 unclassified 处置。
- 分片完成顺序变化不改变合并目录；Prompt 没有预置领域答案。
- 两个Candidate并行读取同Activity时各自用法/ref/结果不串扰；同候选不同variant保留，完整正文只传一次。
- DRAFT前实际包已含所选完整原文；REVIEW得到同一包和完整草稿；不启动JDT。
- 具体谓词、拒绝条件和状态变化从 Activity/source 保留到已审过程，不能退化为空泛表述。
- repository consolidation 不丢 stage、rule、certainty 或 ref，不制造父子循环。
- 新 catalog 确定性生成 `business-processes.md`；九章只消费 catalog，删除原始 Activity 输入仍能生成同一九章内容。
- 真实样例必须说明对象怎样流转及具体规则适用哪个variant；当前46过程中的技术阶段模板不能作为语义成功。多Activity/多Stage只是结构计数。
- 归并使用现有完整Process JSON，保留narrative/规则用法；无需新增摘要模型或请求轮次。
- 每候选一次模型检查，补读可空；实际请求数含空清单检查。来源链接可空，不为补链接加任务。
- 有/无提示验收从同一原始目录独立执行，不跨实验复用定向候选/阅读决策。完整调用数和范围见[验收](../supplements/cross-object-process-reconstruction/acceptance.md#5-调用数与时间)。

测试使用 frozen fixture 与 scripted/替身 Provider，不运行客户构建或真实模型。观测记录 queue wait、DRAFT、source-resolution、REVIEW、save、phase elapsed、实际请求数、复用数、未启动数、失败数，以及全局/每 Provider 峰值；不记录凭据或完整源码到普通日志。真实模型加速比只能在授权运行后测量，不能用 scripted 并发数字冒充。

## 10. 明确不建设

- 不建设第二个线程池、消息队列、分布式锁、事件重建或同 run 恢复系统。
- 不新增自动重试、Provider fallback、孤立 DRAFT 跨批续审或第三轮模型修复。
- 不重新运行 JDT、重新解释 326 个 Activity、重建五图或逐自然语言句子加 Proof。
- 不在 Java 中加入领域词典、过程顺序规则或中文蕴含判断。
- 不让九章模型重新发现、合并或改变业务过程。

目标完成时，同一现有 job 基础设施承载 Activity、目录、候选、归并和报告任务；变化集中在 Step07 的业务输入、Prompt、结果模型与确定性发布，而不是扩建另一套运行框架。
