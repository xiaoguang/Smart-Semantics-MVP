# 模型任务执行：并发、批次复用与过程发现调度

本文拥有模型 job 的配置、Provider 绑定、两级并发、阶段屏障、逐 job 保存、失败处理和跨批复用合同。业务内容分别由 [Step06](../analysis-steps/06-flow-interpretation.md)、[Step07](../analysis-steps/07-repository-knowledge.md)、[Step08](../analysis-steps/08-nine-section-document.md) 及[中文 Prompt](../references/semantic-interpretation-prompts.md)拥有。

## 1. 当前实现与目标增量

### 1.1 已实现并继续复用

- `repository-run-config-v2` 的单一 YAML/JSON 配置、全局并发和每 Provider 并发。
- Codex Subscription 与显式 OpenAI API Provider Adapter、认证隔离、稳定路由和 runtime identity 校验。
- Java 17 有界 job pool、completion queue、阶段屏障、不可变输入和稳定聚合。
- 每个 Activity 或当前 Candidate-process job 内部严格执行 DRAFT → 完整 REVIEW；不同 job 可以并行。
- 每个完整已审 job 立即保存到批次私有目录；aggregate 只在阶段全部闭合后发布一次。
- `repository-run-state-v3`、当前`model-job-execution-config-v3`及历史v2严格读取、`analysis-run-output-v4`、固定材料直接重开、显式新 model batch 和完整已审 job 复用；历史 output-v3 保留严格读取。
- 固定 jshERP 检查点已经保存 **326/326** 个 ReviewedActivity。新过程发现直接从该检查点开始，Activity Provider 调用数为零。

### 1.2 当前Step07和本次增量

BusinessProcessDiscovery已有目录、完整Activity、详细过程两轮、唯一归并、五文件v2发布。46过程是历史v1，本轮选定的后续已存目录为24候选；旧340 singleton已退役。

旧目录输入、单次全局选材、每候选一次阅读检查和DRAFT前封包已实现。本轮目标为[系统认识、聚焦选材及三阶段成稿](../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)：系统认识并入全局选择，CHECK明确首批保留/移出/补读，只有候选过程改为事实DRAFT→业务WRITE→最终RULE_REVIEW。现有调度、两级并发、Provider直接复用；新过程保存完整三阶段记录，Activity/目录/归并仍保存原pair，326Activity不重跑。

### 1.3 当前验收边界

单次阅读决策、冻结文件取材接线和DRAFT前封包的历史验证见[交付记录](../supplements/cross-object-process-reconstruction/delivery.md)。本次三阶段尚未生产接线，只做三个样本的最小验收与独立预览，随后讨论全仓；不执行全仓归并或关闭全仓覆盖。公共五文件格式继续复用。当前无Step08生产器，历史reader/renderer保留；本轮不生成九章。

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
  → 一次系统认识与问题选材（旧目录直接读取时跳过前面的目录生产）
  → 首批取材（零模型）
  → Reading-check jobs 并行：每候选一次保留/移出/补读决定 → 封包
  → Candidate-process jobs 并行：DRAFT → WRITE → RULE_REVIEW → 保存
  → Repository-consolidation job：DRAFT → 完整 REVIEW → 保存
  → BusinessProcessPublisher 确定性发布 catalog、coverage、business-processes.md（零模型）
  → 未来可接九章；这是完整仓库目标，不是本轮三例执行范围
```

固定 jshERP 本轮三例目标从保存的 326 个 ReviewedActivity 开始：

```text
原始已保存目录 + Activity checkpoint + 冻结文本
  → 系统认识与问题选材（不重跑原目录模型）
  → 样本候选一次阅读检查：明确首批保留集 + 可空补读
  → Candidate-process jobs：DRAFT → WRITE → RULE_REVIEW
  → 三例独立正文/来源预览与验收记录
  → 停止，讨论全仓；不调用归并或九章，不安装全仓publication
```

它不得重新调用 Capture、JDT、JavaParser、Step03–05、BusinessMaterialBuilder 或 ActivityExplainer。模型失败也不是重扫源码或重跑 326 个 Activity 的理由。

## 3. Job 单位与阶段屏障

### 3.1 一个 job 的含义

Activity、首次目录和仓库归并继续每job最多两次Provider请求：

```text
完整固定输入
  → DRAFT
  → 程序校验并准备 REVIEW 材料
  → 完整 REVIEW
  → 程序校验并原子保存完整结果
```

上述pair的两轮使用启动前固定的同一Provider、账户服务、模型和reasoning effort。REVIEW必须携带完整实际DRAFT和该任务合同规定的完整原材料，不能只传摘要、patch或“请修正”指令，也不能假设另一会话记得DRAFT。

候选过程目标单独采用固定三阶段：DRAFT从完整ProcessReadingPacket做事实推理；WRITE只读完整实际DRAFT及其中业务名称/用途/规则；最终RULE_REVIEW同时收到同一完整包、实际DRAFT和实际WRITE，返回局部修正后的完整正文与结构及私有corrections。三阶段共享一个Callable、并发名额和同一binding；不在阶段间补源码，不在最终核对后再润色。每次调用前按真实输入、Prompt、Schema和输出空间检查容量；最终核对超限时保留已完成中间结果及未完成原因，不删稿强行调用、不标为reviewed或自动重试。

DRAFT外层输入除readingPacket v1外，还实际发送investigationContext和readingSelections；最终RULE_REVIEW复用同一外层输入再加两份实际结果。调查背景、读取用途和选择说明随私有决策保存、参与指纹与重开，不能只在日志中留存却不发送给模型；字段细节仍以补充合同为准。

全局选材与候选阅读检查各只有一次Provider请求，存为单次决策，不是过程三阶段的一部分。CHECK明确首批保留/移出与一次可空补读；剩余未知留存。Java不能判断语义足够而跳过检查。详细字段、局部读取记录和阶段响应由[补充合同](../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)维护。

### 3.2 新 job 类型

| Job 类型 | 输入 | 输出 | 并发/屏障 |
| --- | --- | --- | --- |
| `ACTIVITY` | 一个 BusinessMaterial | 完整 ReviewedActivity 与 unexplained entries | 已实现；同阶段并行 |
| `CATALOG` | 可以一次容纳的全仓 ActivityIndexCard | 唯一业务领域、别名和重叠 Candidate Process | singleton；成功后不再执行 shard/merge |
| `CATALOG_SHARD` | 一组精简 ActivityIndexCard | 已审局部目录与该分片全卡片处置 | 仅在全仓不可一次容纳时使用；分片间并行 |
| `CATALOG_MERGE` | 全部已审分片目录与全局卡片分母 | 唯一业务领域、别名和重叠 Candidate Process | 仅分片路径使用的 singleton；完成后才能组装候选 |
| `PROCESS_MATERIAL_SELECTION` | 冻结项目说明、旧/首次目录、全仓导航、文件目录和可选问题 | 系统认识、可证伪假设、调查问题、候选增量与首批清单 | 一个全局单次决策 |
| `PROCESS_READING_CHECK` | 一个候选的问题、首批实际材料、全仓导航和文件目录 | 首批保留/移出、可空补读、范围修订及未知 | 候选间并行；每候选恰一次 |
| `CANDIDATE_PROCESS` | 封闭完整阅读包与调查背景 | 事实DRAFT→业务WRITE→最终RULE_REVIEW；完整已审结果或不足处置 | 一个候选Callable内顺序三阶段；候选间并行 |
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

每个 job 在派发前按稳定顺序固定 Provider；该job全部约定阶段和保存都持有同一binding。执行时可以越过暂时无空位的 Provider，派发已绑定到其他有空位 Provider 的后续 job，但不能为填满槽位改写绑定。journal 和私有输出按 batch、job kind、job key、Provider namespace 隔离。

Provider request 只包含当前业务任务的 clean input 和闭合 JSON Schema，不包含 sourceRunId、modelBatchId、复用来源、队列统计、并发、密钥、host path 或材料 checkpoint。

## 6. Java 17 池与保存

协调器使用一个有界 Java 17 executor、一个 completion queue、全局计数和每 Provider 计数。只有全局和绑定 Provider 都有名额时才提交 job；pair名额持有到REVIEW结果保存，新候选三阶段名额持有到RULE_REVIEW完整结果保存，单次decision名额持有到该决策保存。不能把整个 Provider 调用包在 `synchronized` 中，也不能把所有大输入预复制到无界队列。

worker 返回不可变结果，不修改共享 Activity、candidate 或 aggregate。协调器收到完成结果后立即写入：

```text
model-jobs/<model-batch-id>/<job-kind>/<job-key>/reviewed-result.json
```

以上地址继续用于内容job。原Activity/目录/归并保存v2 pair；新候选过程目标保存`model-job-reviewed-result-v3`，携带pipeline、draft、writing、最终review、完整包、来源映射、指纹与绑定。旧过程v2严格历史读取，不能冒充三阶段完成。单次选材/检查使用同级`decision-result.json`，目标阅读决策v2与历史v1严格区分；ReadingRecord和ProcessReadingPacket仍按候选私有保存。完整版本由补充合同维护。

同一 job key 在同一批次只能提交一次。不同 job 即使 clean input bytes 相同也独立保存；同一业务输入在不同批次通过显式 reuse record 关联，不能抢写旧结果。阶段 aggregate 按程序稳定顺序安装一次，不把 publisher 放进 worker 循环。

新过程完整记录保存三次实际结果、最终处置及corrections；只有三阶段完整且验证通过才可复用。单次decision保存原输入/响应、指纹、身份与终态，并保存读取记录/最终包；不能造空DRAFT/REVIEW伪装pair。沿用简单私有JSON保存，不新建运行状态机。

## 7. 固定材料与独立模型批次（已实现基础，扩展到新 job）

### 7.1 现有运行身份

- `sourceRunId` 属于材料原生产 run；失败的模型执行不修改它。
- `materialsCheckpoint` 是带地址、root 和 receipt SHA 的完整 M10 reference，不是仅凭可读 ID 猜路径。
- `modelBatchId` 使用新 `AnalysisRunId`，拥有该批 Activity/Process/Report 输出。
- Provider请求日志按`providerKey/modelBatchId`隔离。同一批次内的STARTED仍禁止重放；用户显式启动新批次时，旧STARTED日志原样保留，但不能阻断新批次完整执行未完成job。
- `repository-run-state-v3` 保存实际材料 profile、producer version 和 basis；当前`model-job-execution-config-v3`保存本批范围、服务、可选reuse来源和过程读取输入；历史执行配置v2严格读取。`analysis-run-output-v4`区分材料、Activity和过程输出归属，历史v3严格只读。

执行配置v3中的旧目录输入、完整verified inventory引用及可空focusQuestion已接线；本次不再升级执行配置、YAML、材料state或output。CLI创建/选择QUEUED run后、Provider初始化前原子保存真实选择；已有绑定不匹配须使用新run。内部`ProcessDiscoveryRequest`接收验证后的依赖，不改变公共Agent请求协议。既有接线见[模块设计](../supplements/cross-object-process-reconstruction/module-design.md)。

源码、入口、取材规则和材料未变时，改变模型、并发或日志目录不使材料失效。改变源码、引擎、入口选择、分包或代码内容时必须显式生成新材料。读取缺失、损坏或来源不一致的材料时失败，不能偷偷调用 Builder 或 JDT 补齐。

### 7.2 复用单位

内容生成的复用单位是完整约定job：Activity/目录/归并为pair，新候选过程为完整三阶段，均不能复用单次网络响应或续接半轮。单次阅读decision为独立类型，仅完整成功、已验证且精确匹配时显式复用。旧目录作输入读取也与直接任务复用不同。

| 旧 job 状态 | 新批次行为 |
| --- | --- |
| 未启动、某阶段失败或结果未知 | 经显式授权的新job执行完整约定序列；候选过程为三阶段 |
| 只有DRAFT/WRITE或不完整REVIEW | 保留实际中间结果诊断；不跨批续接半轮 |
| 完整已审结果、输入与 binding 全部匹配 | 零 Provider 调用复用，保存来源记录 |
| 声称完成但结果损坏 | 明确失败，不以隐式重调掩盖损坏 |
| 输入、Prompt、Schema、profile、producer 或 Provider binding 改变 | 不复用，按新批次明确配置执行 |

稳定 `jobKey` 包含 job kind 与真实业务输入成员，不含 batchId。`inputFingerprint` 包含完整clean input、allowlist映射、实际Prompt/Schema、完整任务序列、内容profile、Module/producer version和实际Provider/account/model/effort/sandbox；新过程必须覆盖三阶段而非沿用仅draft/review指纹。不包含新batch ID、时间、并发、日志目录或秘密值。

单次decision同样验证`providerBindingKey`、`quotaScope`与`ModelRuntimeIdentity`中的provider/model/effort/sandbox；实际关注问题和读取结果是输入的一部分。旧目录作输入则要求来源批次已停止、完整目录pair有效、Activity检查点/集合及冻结basis一致，不要求旧批次下游也成功或旧Prompt与新Prompt相同。两者不能混成一个复用判定。

新过程路线按依赖逐层复用：

- Activity checkpoint 已经完整匹配时，不启动 Activity jobs。
- ActivityIndexCard 投影或 Activity 内容变化时，只失效相应 catalog 输入。
- Candidate成员/context、完整Activity、实际阅读包、statement/ref映射、关注问题、Prompt或源码变化时，失效相应任务及依赖；只改并发/路径不失效。
- 仓库归并输入变化时不能沿用旧 catalog。
- catalog 或 coverage 变化时不能沿用旧九章。

复用不会把 `PARTIAL` 或 `UNRESOLVED` 升格为完整业务验收。原生产批次、结果 SHA 和引用保留在新批次的 reuse record 中。

### 7.3 固定 jshERP 检查点的起点

当前输入是326条已审Activity和显式选择的已保存目录，不是340 singleton。新model batch直接重开；原目录模型、前五步、JDT、Builder、Activity调用均为0。本轮只针对三个样本执行获准的选材、检查及过程三阶段；不自动运行原全仓候选和归并。

旧Process/九章留作对照；旧目录可作选材输入。Activity与更完整源码有局部差异，在本次过程说明并保留原记录，不自动启动delta review。确需重做Activity必须先与用户讨论并另获同意。

## 8. Fatal、未决结果与显式新批次

任一fatal被观察到后，协调器立即停止派发新job。尚未真正开始的任务记为本执行未启动；已经开始且自身阶段合法的job在原binding和既有超时内完成剩余约定阶段并保存。pair最多完成其唯一REVIEW，新过程最多完成其WRITE和RULE_REVIEW；自身某阶段失败不进入下一阶段，保留实际结果和诊断。收齐已开始job的终态后，当前批次以FAILED结束，不启动依赖它的下游。

未知/未提供Activity/ref、坏Schema、来源漂移、损坏、遗漏必须处置、started失败仍fatal。选材/阅读检查可以按合同增减成员；最终REVIEW不能引入包外材料。合法未命中/不足和UNRESOLVED不伪造失败或完整性。单次decision失败保存诊断，无隐式重发；pair沿用既有失败纪律。

同一批次没有自动retry、reroute、模型降档、超出约定序列的修复调用、failed-call replay或终态修复。候选固定三阶段不是失败后的补救轮。用户显式启动的新model batch可以按§7复用完整结果并重新执行未完成job；其Provider请求日志使用新的batch目录，保留旧STARTED/FAILED记录，不能称为同一请求恢复成功。无法确认旧started请求的服务端状态时，必须承认新批次可能再次产生该任务的模型工作。

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
- DRAFT前实际包已含所选完整原文；WRITE仅得完整实际事实草稿，RULE_REVIEW得同一外层输入和完整DRAFT/WRITE；不启动JDT。
- 具体谓词、拒绝条件和状态变化从 Activity/source 保留到已审过程，不能退化为空泛表述。
- repository consolidation 不丢 stage、rule、certainty 或 ref，不制造父子循环。
- 新 catalog 确定性生成 `business-processes.md`；九章只消费 catalog，删除原始 Activity 输入仍能生成同一九章内容。
- 真实样例必须说明对象怎样流转及具体规则适用哪个variant；当前46过程中的技术阶段模板不能作为语义成功。多Activity/多Stage只是结构计数。
- 归并使用现有完整Process JSON，保留narrative/规则用法；无需新增摘要模型或请求轮次。
- 每候选一次模型检查，补读可空；实际请求数含空清单检查。来源链接可空，不为补链接加任务。
- 新直接测试覆盖CHECK保留集、三阶段实际顺序/完整输入、最终修正文案及规则进入保存渲染、v3完整重开及旧pair不误复用。真实验收只做[三个样本](../supplements/cross-object-process-reconstruction/acceptance.md)，不延续历史全仓实验。

测试使用frozen fixture与scripted/替身Provider，不运行客户构建或真实模型。观测按实际任务记录queue wait、DRAFT、WRITE、RULE_REVIEW或原pair REVIEW、source-resolution、save、phase elapsed、实际请求数、复用/未启动/失败数及全局/Provider峰值；不把凭据或完整源码写到普通日志。真实模型加速比只能在授权运行后测量，不能用scripted并发数字冒充。

## 10. 明确不建设

- 不建设第二个线程池、消息队列、分布式锁、事件重建或同 run 恢复系统。
- 不新增自动重试、Provider fallback、孤立DRAFT/WRITE跨批续审或超出固定序列的模型修复。
- 不重新运行 JDT、重新解释 326 个 Activity、重建五图或逐自然语言句子加 Proof。
- 不在 Java 中加入领域词典、过程顺序规则或中文蕴含判断。
- 不让九章模型重新发现、合并或改变业务过程。

目标完成时，同一现有 job 基础设施承载 Activity、目录、候选、归并和报告任务；变化集中在 Step07 的业务输入、Prompt、结果模型与确定性发布，而不是扩建另一套运行框架。
