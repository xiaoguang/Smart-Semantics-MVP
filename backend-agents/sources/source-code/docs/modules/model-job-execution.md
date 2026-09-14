# 模型任务执行：并发、批次复用与过程发现调度

本文拥有模型 job 的配置、Provider 绑定、两级并发、阶段屏障、逐 job 保存、失败处理和跨批复用合同。业务内容分别由 [Step06](../analysis-steps/06-flow-interpretation.md)、[Step07](../analysis-steps/07-repository-knowledge.md)、[Step08](../analysis-steps/08-nine-section-document.md) 及[中文 Prompt](../references/semantic-interpretation-prompts.md)拥有。

## 1. 当前实现与目标增量

### 1.1 已实现并继续复用

- `repository-run-config-v2` 的单一 YAML/JSON 配置、全局并发和每 Provider 并发。
- Codex Subscription 与显式 OpenAI API Provider Adapter、认证隔离、稳定路由和 runtime identity 校验。
- Java 17 有界 job pool、completion queue、阶段屏障、不可变输入和稳定聚合。
- 每个 Activity 或旧 Process-group job 内部严格执行 DRAFT → 完整 REVIEW；不同 job 可以并行。
- 每个完整已审 job 立即保存到批次私有目录；aggregate 只在阶段全部闭合后发布一次。
- `repository-run-state-v3`、`model-job-execution-config-v2`、`analysis-run-output-v3`、固定材料直接重开、显式新 model batch 和完整已审 job 复用。
- 固定 jshERP 检查点已经保存 **326/326** 个 ReviewedActivity。新过程发现直接从该检查点开始，Activity Provider 调用数为零。

### 1.2 已实现但不再作为目标的路径

当前 `ProcessExplainer` 的 Process-group 并发和可选 repository summary 可以运行，但其分组由精确字段相等、同文件和容量切片驱动。实际结果是 340 个单 Activity、单 Stage Process，且一个 Activity 未进入 Process 也未出现在 unmatched 集合中。

这套旧 Process 输入、Prompt、浅结果和 summary 不是新业务过程设计的继续扩展点。保留其历史结果用于对照；实施时复用它的 job pool、Provider、保存和批次能力，替换其业务分组与结果模型。

### 1.3 本次目标尚未实现

- 全仓 ActivityIndexCard 目录任务；
- Candidate Process 的完整 Activity 材料和按需保存源码核对；
- ActivityUse、详细阶段、精确规则和三档 certainty；
- 仓库过程归并；
- 确定性 `business-processes.md`；
- 只消费已归并 process catalog 的九章任务。

文档描述目标调度并不表示这些新 job 或 Schema 已经存在。

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
  → Candidate-process jobs 并行：
       完整 Activity DRAFT
       → 程序读取 requestedSourceRefs（零模型、零导航）
       → 完整 REVIEW
       → 保存
  → Repository-consolidation job：DRAFT → 完整 REVIEW → 保存
  → BusinessProcessPublisher 确定性发布 catalog、coverage、business-processes.md（零模型）
  → Whole-report job：catalog 生成九章 DRAFT → 完整 REVIEW → 保存
  → 确定性 renderer（零模型）
```

固定 jshERP 当前执行从保存的 326 个 ReviewedActivity 开始：

```text
已验证 Activity checkpoint
  → Catalog；只有实际需要时才分片并 merge
  → Candidate-process jobs
  → Repository consolidation
  → process catalog / business-processes.md
  → 九章
```

它不得重新调用 Capture、JDT、JavaParser、Step03–05、BusinessMaterialBuilder 或 ActivityExplainer。模型失败也不是重扫源码或重跑 326 个 Activity 的理由。

## 3. Job 单位与阶段屏障

### 3.1 一个 job 的含义

一个模型 job 最多有两次 Provider 请求：

```text
完整固定输入
  → DRAFT
  → 程序校验并准备 REVIEW 材料
  → 完整 REVIEW
  → 程序校验并原子保存完整结果
```

同一 job 的两轮使用启动前固定的同一 Provider、账户服务、模型和 reasoning effort。REVIEW 必须携带完整实际 DRAFT 和该任务合同规定的完整原材料，不能只传摘要、patch 或“请修正”指令，也不能假设另一会话记得 DRAFT。

候选过程 job 在两轮之间解析 `requestedSourceRefs`。它只读取已保存 JDT/source corpus 中 allowlist 内的片段；这不是第三次模型调用，也不重新导航。DRAFT 没请求到但 REVIEW 又需要的源码只能记为 `UNRESOLVED`，不能自动追加一轮。

### 3.2 新 job 类型

| Job 类型 | 输入 | 输出 | 并发/屏障 |
| --- | --- | --- | --- |
| `ACTIVITY` | 一个 BusinessMaterial | 完整 ReviewedActivity 与 unexplained entries | 已实现；同阶段并行 |
| `CATALOG` | 可以一次容纳的全仓 ActivityIndexCard | 唯一业务领域、别名和重叠 Candidate Process | singleton；成功后不再执行 shard/merge |
| `CATALOG_SHARD` | 一组精简 ActivityIndexCard | 已审局部目录与该分片全卡片处置 | 仅在全仓不可一次容纳时使用；分片间并行 |
| `CATALOG_MERGE` | 全部已审分片目录与全局卡片分母 | 唯一业务领域、别名和重叠 Candidate Process | 仅分片路径使用的 singleton；完成后才能组装候选 |
| `CANDIDATE_PROCESS` | 一个候选的完整 Activity、statement handles、ref 目录 | 一个或多个详细已审过程，或合法不足/支撑处置 | 候选间并行；全部完成后才能归并 |
| `REPOSITORY_CONSOLIDATION` | 全部已审过程摘要、ActivityUse、首尾状态和 coverage | 合并、父子、相关、替代和拒绝决定 | singleton；不能改写过程详情 |
| `WHOLE_REPORT` | 唯一已发布 process catalog 与 coverage | 完整已审九章 JSON | singleton；过程发布后才开始 |

`BusinessProcessPublisher`、source-ref 解析和 Markdown renderer 都是确定性程序操作，不是模型 job。

### 3.3 屏障和稳定性

- 一个阶段所有必需 job 都有终态后才进入下一阶段。
- Catalog 分片完成顺序不决定 Activity 处置、candidate ID 或 merge 输入顺序。
- Candidate 完成顺序不决定过程 ID、最终排列或 aggregate bytes。
- 同一 Activity 可被多个 Candidate job 只读共享；每个 job 创建自己的 ActivityUse，不修改原 Activity。
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
| `processGroup` | `CATALOG_SHARD`、`CANDIDATE_PROCESS` |
| `repositorySummary` | `CATALOG`、`CATALOG_MERGE`、`REPOSITORY_CONSOLIDATION` |
| `report` | `WHOLE_REPORT` |

这只是复用现有 Provider 选择入口，不表示旧 Process-group 算法仍参与执行。若未来需要让 catalog 与 candidate 使用不同 Provider，再单独设计并升版 routing；本次不得增加别名、双读或隐式 fallback。

严格加载继续拒绝重复 key、未知字段/kind/auth mode、非法并发、未知 Provider 引用和缺秘密环境变量。`materials-only` 可以省略 modelJobs；有配置时只校验结构，不构造 Provider、不解析秘密值、不调用模型。

## 5. Provider、认证与任务绑定

一个 Provider 定义代表一个账户或服务额度范围，不等于一把 key 或一个新会话。多个 key 共享组织、项目或模型限额时必须在同一个 Provider cap 下；不同 Codex 会话若属于同一账户，也不产生新额度。

`codexSubscription` 强制 ChatGPT 认证，使用指定登录上下文、read-only sandbox 和显式 model/effort；屏蔽 API-key 认证覆盖。`openaiApi` 只在 YAML 明确定义并获得实际运行授权时使用。Pro 失败或额度耗尽不自动切到 API，API 失败也不切账户、模型或 effort。

每个 job 在派发前按稳定顺序固定 Provider；两轮和保存都持有同一 binding。执行时可以越过暂时无空位的 Provider，派发已绑定到其他有空位 Provider 的后续 job，但不能为填满槽位改写绑定。journal 和私有输出按 batch、job kind、job key、Provider namespace 隔离。

Provider request 只包含当前业务任务的 clean input 和闭合 JSON Schema，不包含 sourceRunId、modelBatchId、复用来源、队列统计、并发、密钥、host path 或材料 checkpoint。

## 6. Java 17 池与保存

协调器使用一个有界 Java 17 executor、一个 completion queue、全局计数和每 Provider 计数。只有全局和绑定 Provider 都有名额时才提交 job；名额持有到 REVIEW 结果原子保存后释放。不能把整个 Provider 调用包在 `synchronized` 中，也不能把所有大输入预复制到无界队列。

worker 返回不可变结果，不修改共享 Activity、candidate 或 aggregate。协调器收到完成结果后立即写入：

```text
model-jobs/<model-batch-id>/<job-kind>/<job-key>/reviewed-result.json
```

同一 job key 在同一批次只能提交一次。不同 job 即使 clean input bytes 相同也独立保存；同一业务输入在不同批次通过显式 reuse record 关联，不能抢写旧结果。阶段 aggregate 按程序稳定顺序安装一次，不把 publisher 放进 worker 循环。

候选过程的保存记录必须同时闭合：完整 DRAFT、`requestedSourceRefs`、程序实际解析的 ref 与片段摘要、完整 REVIEW、最终处置。不能只保存 REVIEW 文本而丢掉它核对过的来源选择。

## 7. 固定材料与独立模型批次（已实现基础，扩展到新 job）

### 7.1 现有运行身份

- `sourceRunId` 属于材料原生产 run；失败的模型执行不修改它。
- `materialsCheckpoint` 是带地址、root 和 receipt SHA 的完整 M10 reference，不是仅凭可读 ID 猜路径。
- `modelBatchId` 使用新 `AnalysisRunId`，拥有该批 Activity/Process/Report 输出。
- `repository-run-state-v3` 保存实际材料 profile、producer version 和 basis；`model-job-execution-config-v2` 保存本批范围、服务与可选 reuse 来源；`analysis-run-output-v3` 保持材料 source owner 与模型 output owner 的双归属。

源码、入口、取材规则和材料未变时，改变模型、并发或日志目录不使材料失效。改变源码、引擎、入口选择、分包或代码内容时必须显式生成新材料。读取缺失、损坏或来源不一致的材料时失败，不能偷偷调用 Builder 或 JDT 补齐。

### 7.2 复用单位

复用单位是完整、已校验并原子保存的 DRAFT＋REVIEW job，不是单次网络响应：

| 旧 job 状态 | 新批次行为 |
| --- | --- |
| 未启动、DRAFT 失败或结果未知 | 新 job 做完整 DRAFT＋REVIEW |
| 只有 DRAFT，或 REVIEW 失败/未知 | 保留旧草稿作诊断；新 job 重做完整 pair |
| 完整已审结果、输入与 binding 全部匹配 | 零 Provider 调用复用，保存来源记录 |
| 声称完成但结果损坏 | 明确失败，不以隐式重调掩盖损坏 |
| 输入、Prompt、Schema、profile、producer 或 Provider binding 改变 | 不复用，按新批次明确配置执行 |

稳定 `jobKey` 包含 job kind 与真实业务输入成员，不含 batchId。`inputFingerprint` 包含完整 clean input、allowlist 映射、实际 Prompt/Schema、内容 profile、Module/producer version 和实际 Provider/account/model/effort/sandbox；不包含新 batch ID、时间、并发、日志目录或秘密值。

新过程路线按依赖逐层复用：

- Activity checkpoint 已经完整匹配时，不启动 Activity jobs。
- ActivityIndexCard 投影或 Activity 内容变化时，只失效相应 catalog 输入。
- Candidate 成员、完整 Activity、statement handles、SourceRef 目录、Prompt 或实际保存源码变化时，只失效该 candidate job 及依赖它的归并/报告。
- 仓库归并输入变化时不能沿用旧 catalog。
- catalog 或 coverage 变化时不能沿用旧九章。

复用不会把 `PARTIAL` 或 `UNRESOLVED` 升格为完整业务验收。原生产批次、结果 SHA 和引用保留在新批次的 reuse record 中。

### 7.3 固定 jshERP 检查点的起点

当前已验证的输入是 326 份完整 ReviewedActivity，而不是旧 340 个 singleton Process。新过程发现应在新 model batch 中选择该 Activity checkpoint，直接生成卡片和目录；前五步、JDT、Builder 与 Activity Provider 调用数都为零。

当前旧 Process 和九章可以保留作差异基线，但不能作为新目录或候选的事实输入，也不能覆盖新输出。只有候选审阅发现具名 Activity 与保存源码矛盾，才另行定向 delta review；不自动重跑全部 Activity。

## 8. Fatal、未决结果与显式新批次

任一 fatal 被观察到后，协调器立即停止派发新 job。尚未真正开始 DRAFT 的任务记为本执行未启动；已经开始且自身 DRAFT 合法的 job 在原 binding 和既有超时内完成唯一 REVIEW 并保存。自身 DRAFT 失败不进 REVIEW；自身 REVIEW 失败保留诊断。收齐已开始 job 的终态后，当前批次以 FAILED 结束，不启动依赖它的下游。

以下属于 fatal：非法 JSON/Schema、未知或越界 Activity/ref、目录或候选分母无声遗漏、来源漂移、保存碰撞/损坏、REVIEW 引入新成员、started 请求失败。以下可以是合法内容结果：`STANDALONE`、`UNCLASSIFIED`、`INSUFFICIENT_MATERIAL`、`INFERRED`、`UNRESOLVED`，前提是处置和 coverage 闭合。

同一批次没有自动 retry、reroute、模型降档、第三轮修复、failed-call replay 或终态修复。用户显式启动的新 model batch 可以按 §7 复用完整结果并重新执行未完成 job；它保留旧 STARTED/FAILED 记录，不能称为同一请求恢复成功。无法确认旧 started 请求的服务端状态时，必须承认新批次可能再次产生该任务的模型工作。

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
- 两个 Candidate 并行读取同一 Activity 时，各自 ActivityUse、requested refs 和结果不串扰。
- Candidate REVIEW 得到完整 Activity、实际 DRAFT 和程序解析的已保存片段；不启动 JDT。
- 具体谓词、拒绝条件和状态变化从 Activity/source 保留到已审过程，不能退化为空泛表述。
- repository consolidation 不丢 stage、rule、certainty 或 ref，不制造父子循环。
- 新 catalog 确定性生成 `business-processes.md`；九章只消费 catalog，删除原始 Activity 输入仍能生成同一九章内容。
- 至少一个真实 fixture 形成多 Activity、多 Stage 过程；当前 340 个 singleton 结果必须作为 RED，而不是成功基线。

测试使用 frozen fixture 与 scripted/替身 Provider，不运行客户构建或真实模型。观测记录 queue wait、DRAFT、source-resolution、REVIEW、save、phase elapsed、实际请求数、复用数、未启动数、失败数，以及全局/每 Provider 峰值；不记录凭据或完整源码到普通日志。真实模型加速比只能在授权运行后测量，不能用 scripted 并发数字冒充。

## 10. 明确不建设

- 不建设第二个线程池、消息队列、分布式锁、事件重建或同 run 恢复系统。
- 不新增自动重试、Provider fallback、孤立 DRAFT 跨批续审或第三轮模型修复。
- 不重新运行 JDT、重新解释 326 个 Activity、重建五图或逐自然语言句子加 Proof。
- 不在 Java 中加入领域词典、过程顺序规则或中文蕴含判断。
- 不让九章模型重新发现、合并或改变业务过程。

目标完成时，同一现有 job 基础设施承载 Activity、目录、候选、归并和报告任务；变化集中在 Step07 的业务输入、Prompt、结果模型与确定性发布，而不是扩建另一套运行框架。
