# 模型任务并行执行

> 并行池、严格 v2 单配置、Provider 隔离、逐 job 保存，以及 §7 的“固定材料＋独立模型批次”均已实现。本页统一拥有调度、配置、批次身份、复用和失败合同；业务内容仍由 [Step06](../analysis-steps/06-flow-interpretation.md)、[Step07](../analysis-steps/07-repository-knowledge.md)、[Step08](../analysis-steps/08-nine-section-document.md) 拥有。

## 1. 并行单位与阶段顺序

一个 job 是一份材料的 **DRAFT → 校验 → 完整 REVIEW → 校验并保存**。同一 job 的两次请求使用启动前绑定的同一 Provider、账户服务、模型和 reasoning effort。Activity 和 Process job 的 REVIEW 必须携带完整原材料和完整实际 DRAFT，不能只传摘要、标题或 patch，也不能假设新会话记得 DRAFT；唯一整仓报告 job 使用本节下述去重后的仓库级 REVIEW 输入。Activity 的 `missingEntryKeys` 与 required `unexplainedEntries` 合同原样保留。

```text
01–05 技术步骤 → BusinessMaterialBuilder 保存完整材料（零模型）
  → Activity jobs 并行，各自 DRAFT → 完整 REVIEW → 保存
  → 等全部活动完成并稳定聚合
  → Process-group jobs 并行，各自 DRAFT → 完整 REVIEW → 保存
  → 等全部过程组完成并稳定聚合
  → repository-summary 最多一个 job：DRAFT → 完整 REVIEW → 保存（或按既有规则明确跳过）
  → 一次发布完整 knowledge/process aggregate
  → 唯一 whole-report job：完整仓库知识生成整篇九章 DRAFT → 实际草稿与汇总知识做整篇 REVIEW → 保存
  → 确定性 renderer（零模型）
```

两个过程组可以读取同一已审活动。输入是不可变共享视图，成员保持多对多；worker 不修改活动、全局 ID 映射或别组结果。仓库总结与报告各最多一个 job，不拆章并发、不额外增加总结/审阅轮次。报告 DRAFT 读取完整 Activity、Process 与仓库知识；报告 REVIEW 读取整篇实际 DRAFT、Process、仓库总结、coverage、具体未解释范围、确认主题和来源 allowlist，不再次传输全部 Activity。该去重只解决同一整仓知识在 REVIEW 中的重复运输，不减少 DRAFT 的业务材料，也不把 REVIEW 降为局部审阅。保留 ProcessExplainer 既有总结准入：`maxRepositorySummaryItems=0`、没有过程或条目/输入容量不容纳时，零总结请求并保存具体 notConsolidated/范围说明。过程 aggregate 等总结完成或该显式跳过后才发布，不能在组屏障处提前发布。0 活动保留现有 Process 零请求行为；显式空仓九章仍按既有报告 DRAFT/REVIEW 执行，并诚实标明范围不完整。

## 2. 唯一配置入口与精确 YAML

在既有 `RepositoryRunMain --config` 文档的 `sourceAnalysis.modelJobs` 中配置模型执行。目标根版本为 `repository-run-config-v2`；`.yaml` / `.yml` 由既有 Jackson YAML 能力加载，同形 JSON 也归一到同一个配置对象。这是现有启动器的版本升级，不新增 CLI、另一配置文件或公开 Agent 方法。`source`、`paths`、`inputs`、`technical`、`business`、`policyRegistry` 仍由原拥有者处理。

下面是归一化后的 v2 YAML 形状。启动器严格加载 JSON/YAML、补齐默认值、校验路由并保存非秘密执行配置；任务池、API adapter 和混合路由均使用这一配置。它说明全局 6、Pro 4、API 2；数字限制同时在途任务，不限制总材料数。

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
business:
  maxMaterialsToStart: 100000
  # 其余既有 material/activity/process/report profile 必须按完整配置提供。
```

`api` 是显式计量付费服务示例，不表示该模型在任意账户或兼容服务可用；必须验证已配置适配器、模型、effort 与 structured-output 能力。只使用订阅时删除整个 `api` 定义并将四条 route 均设为 `[pro]`。普通 Pro 默认是 `gpt-5.6-luna / high`、全局与该 Provider 各 4。

| 字段 | 默认与校验 |
| --- | --- |
| modelJobs.maxConcurrentJobs | 省略为 4；正整数，1…`Integer.MAX_VALUE`，拒绝 null、0、负值、小数、溢出；启动前检查实际宿主资源能承载有效 worker 数 |
| providers | 模型模式必填非空对象；key 为唯一 `[a-z][a-z0-9-]{0,47}`，不接受别名、URL 或路径作为 key |
| provider.kind | 必填闭集 `codexSubscription` / `openaiApi`；仅支持已实现并验证的 adapter，缺 adapter 启动前拒绝 |
| provider.quotaScope | 必填非秘密字符串，声明本 Provider 实际共享额度范围；已知相同账户/组织项目/共享模型额度必须合并到一个 Provider 定义，重复 scope 拒绝 |
| provider.maxConcurrentJobs | Codex 省略为 4；API 必须显式提供；同全局正整数校验，可大于全局值，有效同时任务数仍取二者约束 |
| provider.model / reasoningEffort | Codex 省略分别为 `gpt-5.6-luna` / `high`；API 均必填；本地先校验安全名称和 adapter 支持的 effort 闭集。账户侧实际不可用的 model/effort 由首次请求单次失败，不重试、不降档或替换 |
| provider.timeoutSeconds | 省略为 600；正整数且可安全转换为现有超时类型；每次 DRAFT/REVIEW 沿用已有进程/请求超时，不新增 job 总时限开关 |
| Codex executable / auth | executable 必填本机绝对可执行路径；auth 必填且 exact keys 为 `mode: chatgpt, codexHomeEnv`；后者是已有登录上下文目录的环境变量名，不能放 token 或内联凭据 |
| API endpoint / auth | endpoint 省略为 `https://api.openai.com/v1`，必须为适配器明确支持的 HTTPS endpoint，无 userinfo；auth exact keys 为 `mode: apiKey, apiKeyEnvs`，非空且无重复环境变量名，值不写 YAML |
| routing | 模型模式必填，exact keys 为 activity/processGroup/repositorySummary/report；每值为非空、不重复且顺序固定的已定义 Provider key 数组；不接受 wildcard、隐式 fallback 或未知引用 |
| journalDirectory / outputDirectory | 模型模式必填，沿用现有本地私有目录/非 symlink 校验；不进入公开请求或模型材料 |

环境变量引用必须匹配 `[A-Z_][A-Z0-9_]*`，引用值缺失、空、类型/路径不合法或已知认证模式不匹配时，在任何模型请求前拒绝。YAML 的重复 key、未知字段、未知 kind/auth mode、互斥字段混用均拒绝；不是忽略后继续。API 默认值并不隐式创建 API Provider。`materials-only` 可以省略整个 modelJobs；存在时检查结构，但不构造 Provider、不解析秘密值、不登录或调用模型。

组合根解析整个配置一次，输出不可变有效 engine 与 model-job 配置。当前 `EngineConfigurationLoader.SourceAnalysisDocument` 只接受 javaEngine/jdt；实现时由组合根向它投影 **仅含这两个字段的 sourceAnalysis 子树**，不能把 modelJobs 原样传入并假称旧 loader 已支持。JDT `collect` 的单 worker 范围仍限技术取材；模型池在材料保存后才工作。

当前 CLI 使用 `repository-run-config-v2` 的单一 JSON/YAML 配置，并在模型模式拒绝旧根版本、独立 `repository-run-provider-v1` 和 `--provider-config`。executable、timeout、journal/output 已归入 `sourceAnalysis.modelJobs`；材料模式只验证其结构，模型模式在读取 continuation state 前预检认证环境并构造全部已路由 Provider。有效非秘密路由、Provider/model/effort 配置归入已有运行诊断，不往 public request 塞路径/密钥，也不把技术阶段 `provider: NONE` 误当业务身份。

旧 continuation state 使用 `baseConfigurationSha256 = SHA256(canonicalJson(normalizedV2Document 删除且只删除 sourceAnalysis.modelJobs))`，且没有直接保存材料 checkpoint。该格式现在只能通过显式离线导出转换为 §7.2 的私有 state v3；正常模型模式不再读取它。根 YAML 继续 v2。

当前只改并发不改变 base SHA，但模型失败会把同一个 run 标为 FAILED，后续被 RUNNING 检查挡住；且业务 profile 仍被旧 base SHA 连带绑定。这不是材料或 JDT 失效。目标只核验所选固定材料及其真实输入依据，模型配置独立绑定到新批次；不通过删日志、改失败状态或重扫 JDT 获得新模型请求。材料格式与公开 run-request-v2 不因此升版。

## 3. Provider、认证与额度

一个 Provider 定义对应一个被配置的账户/服务额度范围，不等于一把 key 或一个新会话。多个 API key 若共享组织/项目或模型限制，就放在同一 provider.auth.apiKeyEnvs 中；不同 key 不能获得独立并发额度。不同 Codex Pro 会话仍共享所属账户额度。`quotaScope` 是操作者的明确声明，程序拒绝已知重复 scope，并核对可获得的账户/项目元数据；即使 scope 名不同，解析后 API key 相同或 Codex 上下文实际目录相同也应拒绝重复 Provider（仅内存比较，不记录秘密）。没有证据时不能声称能自动从任意 key 或登录状态发现所有共享额度。操作者必须正确合并这些配置，不新增额度发现或第三层限流系统。[API 官方限制](https://developers.openai.com/api/docs/guides/rate-limits)说明组织、项目和共享模型限制。

Codex Provider 可以只引用一个已登录的上下文。每个 job 使用新的对话/调用会话；每次 `codex exec --ephemeral` 保持独立请求临时目录、schema、输出与诊断。DRAFT 和 REVIEW 显式传完整输入，不依赖 resume。Codex 自己管理登录上下文的状态；应用不在并发运行中 login/logout、改共享配置、切全局账号、复制凭据或新建 cap 数量的登录目录。不同账户可在不同 Provider 引用不同上下文。API adapter 每个 job 固定一份 key 引用与独立请求对象，不修改进程全局环境。

`codexSubscription` 必须强制使用 ChatGPT 认证（官方 `forced_login_method="chatgpt"`），并针对实际选定的上下文确认登录方式及本地状态可初始化。Provider 构造时只做一次 `codex login status`，要求退出成功且受限诊断明确为 ChatGPT、不是 API key；job 请求不重复预检。子进程环境清理/屏蔽 API-key 认证覆盖；显式固定 auth、model、effort、read-only sandbox，不能从继承环境或其他配置启用计量 API。所选 CLI 版本无法可靠执行这些限制就提前失败。[官方认证说明](https://learn.chatgpt.com/docs/auth)区分 ChatGPT 订阅认证与 API-key 计量认证。

订阅模式不购买额度，不接入 API fallback，额度耗尽即失败并保留结果。**当前没有核实到 CLI 可以禁止消耗账户已有付费 credits 的开关，不能保证零额外 credits 消耗。** [官方定价](https://learn.chatgpt.com/docs/pricing)说明已有 credits 可在套餐额度用尽后继续支持使用。启动前须由账户侧核实额度、已有 credits/计费状态及是否满足本次仅套餐使用的要求；无法确认时不启动。不得发明 `no-paid-credits` 等保证开关。增加新会话不会增加订阅额度。

显式配置 `openaiApi` 是已批准设计中的计量路线；仍需该次真实运行的授权与账户配置。它仅服务预先分配给它的 jobs。Pro 限额、超时或失败都不能把原 job 转给 API、另一 key/模型或另一 Pro 会话重试。本文的设计批准不授权当前真实调用或购买。

## 4. Java 17 池与不可变结果

复用 `StructuredModelProvider.generate` seam。ActivityExplainer 与 ProcessExplainer 各自把串行外循环中的“一份输入到完整已审结果”提取成内部可调用函数；同一函数中的 DRAFT/REVIEW 保持串行。worker 返回一个不可变结果，含已有完整内容、局部 coverage/diagnostics 与必要执行元数据。现有三类业务语义 validator 保持不变。

当前 Activity/Process 的 runtime identity 检查依赖 Module profile，不能仅替换 Provider 后继续拿固定 Luna 配置验证。启动前先确定绑定，再由既有 business profile 与该服务 model/effort/adapter 输出能力形成每 job 的有效 profile；DRAFT/REVIEW schema、实际 input/output/完整 REVIEW 容量和 runtime identity 检查统一使用它。服务不支持所需完整材料或 JSON Schema 时在调用前失败，不改语义、删字段、放宽校验或偷偷降档。业务 JSON shape 未变化就不升语义 schema；只升级实际变化的配置/私有元数据拥有者。

协调器持有一个有界 Java 17 executor、全局在途计数及每 Provider 在途计数。只在全局及已绑定 Provider 都有空位时提交 job，持有两个名额直到该 job 完成 REVIEW 并安全保存，结束后释放。executor 的工作线程/已提交任务容量有界；剩余材料只是已有不可变输入及轻量待发 job，不把全仓大 packet 复制到无限 executor 队列。默认最多四个活跃 job。锁只保护短暂派发/计数/收集操作，绝不能 `synchronized generate` 或锁住整个 Provider 网络调用。

每阶段按现有 material/group 稳定顺序生成 job，预先按 route 数组作 `ordinal % route.size()` 固定 Provider 分配；API 的多 key 引用也按该 Provider 内稳定 job 顺序轮转，并在请求前固定。singleton summary/report 使用其 route 第一项。执行时可越过暂时无 Provider 名额的待发 job，派发后续已绑定且有空位者；不因空闲 Provider 改写绑定。真实完成顺序不决定 Provider、业务 ID、coverage 顺序或聚合 bytes。

调度层不生成业务知识，也不独立构造输出 schema。各业务 Module 拥有其完整输入、local-ID 映射、DRAFT/REVIEW 与验证；协调器拥有队列、绑定、阶段屏障、保存和最终稳定聚合。不存在第二 public Interface、工作流 runtime、消息队列或恢复框架。

只有两个并发配置：`modelJobs.maxConcurrentJobs` 和 `providers.<id>.maxConcurrentJobs`。它们限制在途 job，实际同时模型请求数不超过在途 job 数。`business.maxMaterialsToStart` 是既有本执行 Activity 启动总数授权/范围上限；`business.material.maxEntriesPerMaterial` 的 K 与每包实际 N 是组包容量，均不是并发值。若有 107 个合格材料、总启动上限足够、并发为 4，107 个都排队处理；不能处理前 4/6/12 个就把其余写成容量排除。只有真实总启动上限排除才用 `NOT_ANALYZED_EXECUTION_CAPACITY`。

## 5. 保存、身份与失败

ActivityExplainer 已把一份材料提取为一个不可变完整 DRAFT→REVIEW job；ProcessExplainer 对每个有资格的过程组执行同样的不可拆 job。两者的有界 completion queue 都执行全局及 Provider 两级上限，在完成 REVIEW 后先私有保存再释放名额，最终按稳定材料/组顺序聚合。过程 aggregate 仍须等全部组及至多一次仓库总结完成，或按既有规则明确跳过总结后才安装。worker 不共享可变业务集合，也不发布同一固定 aggregate 地址。

Activity 使用 `model-jobs/<run-hex>/activity/<job-key>/reviewed-result.json`，Process-group、repository-summary 和 report 使用并列目录，均为原子 no-replace 写入。最小已审结果 reader 只接受完整、匹配的 DRAFT＋REVIEW；新模型批次会在自己的目录中写复用记录并保留原生产批次。模型输入不包含调度身份或凭据。

`RunJournalStructuredProvider` 目前按一个 `ModelRuntimeIdentityV1(provider,model,reasoningEffort,sandbox)` 校验，身份里没有账户；最小修改是每个已绑定 Provider/job 使用独立私有 journal namespace，保持 request/response 身份验证。不同账户上相同模型/输入不能串读；同一 job 只提交一次，同一进程维护单一 job-key 登记，重复排队不能发第二次 DRAFT。不同材料即使模型输入 bytes 相同也不会互相抢写。DRAFT/REVIEW 仍分别记录实际请求及输出；不改业务 packet 增加调度 ID。

inputFingerprint 的目标比较规则见 §7.3：内容与实际有效服务绑定必须一致，新的批次身份、队列时间和并发不影响业务内容。变更路由须重新固定各 job 绑定，不能同批热改。现有 run-bound 请求/结果身份不能直接拿来作跨批内容比较；复用 reader 必须区分来源批次与新输出归属，不跳过 runtime 校验。

任一 fatal 被观察到后立刻关闭新 job 派发。未开始 DRAFT 的队列项不再发模型请求，逐项在既有失败诊断中说明“本执行已停止、未启动”，不冒称预算不足或分析完成。已经开始的 job 若自身 DRAFT 合法，就在原绑定上继续它唯一的 REVIEW，并在既有各次超时内保存结果；兄弟 job 失败不让它丢掉已完成 DRAFT，也不构成重试。自身 DRAFT 非法/失败的 job 不进入 REVIEW；自身 REVIEW 失败则保留诊断，不造已审结果。尚未真正开始 DRAFT 的已派发 worker 在调用门检查停止标记。

协调器收齐已开始 job 的终态或既有 timeout，保留结果并以 FAILED 结束；没有所有必需结果不发布成功 aggregate，也不进入下游。保存失败同为 fatal。同一批次没有 retry/reroute、failed-call replay、takeover 或终态修复。用户显式发起的新批次可以按 §7 重新执行未完成 job；它保留旧失败并产生新请求，不能称为旧请求恢复成功。

## 6. 已完成实现与定向验证

本节列出当前实现的验收面。scripted 并发、Provider 隔离和屏障的实测数字见[并行执行验证记录](../supplements/model-job-parallel-execution-verification.md)。

1. v2 单配置解析、严格认证绑定及旧第二配置入口拒绝已经接入组合根。
2. Activity 与 Process-group 都已提取不可变的完整 DRAFT→REVIEW job，并执行全局和 Provider 两级限制。
3. 逐 job 私有保存、Provider/job journal namespace、稳定 aggregate 与 `PersistedBusinessRunExecutor` 阶段屏障已经接通。
4. 下列直接测试均使用 frozen/scripted 或替身 Provider；真实模型性能仍须在另行授权的运行中测量。

这里的“已完成”不包括 §7 的材料直接读取、独立批次、完整结果复用与跨 run 输出接线。

| 直接测试 | 需要观察的行为 |
| --- | --- |
| 两级上限 | scripted 阻塞点统计全局/每 Provider 活跃 job 和实际活跃 generate；6/4/2 不越限，同 Provider 网络调用确可重叠 |
| 排队与边界 | 多于 12 个独立材料、并发 1/4/6、启动上限足够时全部各一次 DRAFT/REVIEW；总启动上限与 K/N 各自单独验收 |
| REVIEW 完整性 | 所有 job 的完整原材料、实际 DRAFT、missingEntryKeys 不串包；required unexplainedEntries 与第9章传递原样保留 |
| Provider/认证隔离 | job 两轮 provider/model/effort/key 绑定不变；同模型不同账户 journal 不混，重复 quotaScope 拒绝；继承 API env 不能改变订阅认证，无 API adapter 的配置提前失败 |
| 多对多和稳定性 | 两个组共享同一已审活动且并行；不修改共享内容/成员；反转完成顺序仍生成相同 aggregate bytes 与 local/global 映射 |
| 阶段屏障 | 活动未完成不开始过程；组未完成不开始总结；总结准入时只有一个 DRAFT/REVIEW，既有关闭/无过程/容量规则显式跳过；其后才发布 knowledge 并做唯一完整九章 DRAFT/REVIEW；renderer 不触发 generate |
| fatal 与保存 | 中途 DRAFT/REVIEW/保存失败后无新 job；已开始且自身合法的 pair 完成审阅并保留；失败 pair 不重试，summary/report 不开始，无假成功 publication |
| 去重与冲突 | 同 job 重复提交只有一次；不同 job 同输入独立保存；相同/冲突原子保存保持 existing collision 语义 |
| YAML | 默认 4/Pro Luna high、显式 API、非法值/缺项/未知项、错误环境引用、旧第二配置与 root 版本拒绝；materials-only 零 Provider |

只运行新增或直接覆盖改动的测试，重型检查串行；不运行客户构建或真实模型验证上述调度规则。观测使用单调时钟记录 queue wait、DRAFT、REVIEW、save、各 phase elapsed，以及全局/每 Provider 实际在途 job 和实际活跃模型请求峰值。统计请求数、完成/未启动/失败数和真实保存 bytes，不记录凭据或完整源码到日志。全局 6 不代表快 6 倍；在测量模型耗时、额度和保存成本之前不承诺加速比。

## 7. 固定材料与独立模型批次（已实现）

### 7.1 给人看的规则

JDT 和 Builder 的工作是准备一本可重复阅读的“代码资料”。模型某次没有读完，不意味着资料失效。先保存完整材料，再用它执行模型批次；失败留在那个批次里，下一次只重做必要模型工作。

- `materialsCheckpointId` 是既有 BusinessMaterialBuilder module receipt ID 的便于阅读名称，实际读写必须使用带地址、root、receipt SHA 的**完整既有 checkpoint reference**，不能仅凭 ID 猜路径。
- `sourceRunId` 是材料原生产 run，不因模型失败被改写或复制成另一套来源。
- `modelBatchId` 直接采用现有 `start` 为本次新执行分配的 `AnalysisRunId`；它也是业务输出的 `runId`。不再生成一套并行的随机批次 ID、不新增 public Agent 方法或生命周期枚举。
- 一批次的执行范围和服务绑定启动前冻结；sample 与整仓是不同批次。失败批次永不重新打开来派发请求。新批次不能因为旧 sourceRun 为 FAILED 就拒绝有效材料，也不能把旧 run 改回 RUNNING。

源码、导航、材料和来源引用保持不变时，新批次的 Capture/JDT/graph/Fact/Flow/Builder **调用数均为 0**。只有材料缺失、损坏或用户明确要改变取材内容时，才回到相应上游；读取失败不得自动扫描。模型返回质量问题也不能成为重建五张图的理由。

### 7.2 最小接线与私有记录

沿现有 RepositoryRunMain / PersistedBusinessRunExecutor / BusinessAnalysisWorkflow 接线，不增加第二工作流。保留现有从 Step05 构建材料的入口；另外让同一业务执行逻辑接受**已验证的完整 BusinessMaterialBuildResult 与显式输出 run**。重新读取材料只解码文件和核验引用，不调用 Builder.build。

材料创建模式在 M10 安装成功后写私有 `repository-run-state-v3`，exact keys 为：

```text
schemaVersion = repository-run-state-v3
sourceRunId
businessFlowsPublication  // 完整既有 Step05 reference，保留原地址
materialsCheckpoint      // 完整既有 M10 reference，保留原地址
materialProfile          // 保存时实际完整 BusinessMaterialProfile
materialModuleVersion    // 实际材料 producer 版本
materialBasisSha256      // 下述三项的 canonical 摘要
```

`materialBasisSha256 = SHA256(canonicalJson({businessFlowsPublication, materialProfile, materialModuleVersion}))`。这里 publication 是完整引用；源码、工具和入口选择由该引用及上游 basis 闭合，不另建证据链。当前 M10 receipt 未单独保存 material profile，不能假称 receipt 已包含它；新 state 必须保存实际值，离线导出从该次原配置取出，先通过旧 state 的 baseConfigurationSha256 核验原配置，再核验现存材料身份与 producer。不能从今天的默认值补造，也不能为了核对 profile 重新运行 Builder。

不再用“整份配置减 modelJobs”的 SHA 代表材料有效性。核验 `sourceRunId == materialsCheckpoint.address.runId == businessFlowsPublication.address.runId`，重开 M10/key/payload/schema、上游引用和实际材料 basis。模型模式验证配置声明的来源 commit/仓库与保存依据一致，材料及短 ref 分母完整；取材参数以 state 中实际采用的值为准，CLI 显示它们，模型模式不应用新取材参数。要改变入口选择、引擎/classpath、源码、分包或代码内容，明确生成新的材料 checkpoint；不能在 generate 中悄悄生效。Activity/Process/Report profile、模型、Prompt、并发、日志路径变化都不让既有材料失效。

`generate` / `activities-sample` 每次显式调用都创建新输出 run，模型执行前原子写私有 `model-job-execution-config-v2`：

```text
schemaVersion = model-job-execution-config-v2
modelBatchId              // 值等于本次输出 runId
sourceRunId
materialsCheckpoint      // 完整引用；不内嵌源码
reuseFromModelBatchId     // required nullable；只选一个已停止的旧批次
executionScope           // {mode, materialIds, maxMaterialsToStart}
modelJobsSha256
modelJobs                // 有效非秘密服务/路由/两层并发配置
```

复用来源由同一维护 CLI 的可选 `--reuse-from-model-batch <analysis-run-id>` 明确给出；缺省不跨批自动搜索结果。根 YAML 保持 v2、两个并发字段不变。该参数不是热重试、不是新的公开 Agent API。技术创建模式拒绝它，批次不得引用自身或仍在运行的批次；新旧批次必须绑定同一完整 materialsCheckpoint reference，不能跨材料集合仅凭同名 S/E 编号复用。完整复用记录可经旧批次保存引用继续定位真实原生产者，不另建通用图搜索器。

executionScope.mode沿现有 `activities-sample | generate`；sample的materialIds为该次精确选中ID，generate为空数组表示整个所选checkpoint，maxMaterialsToStart记录本次实际启动范围上限。它们不是新的并发限制，不进入单job的内容fingerprint；扩大批次范围不否定同一材料job的完整已审结果。sample保存完整已审job及私有样本说明后，将**本批**由RUNNING置FINISHED，不安装虚构的四项全链run output、不宣称整仓报告完成。这样后续generate可明确选这个已结束批次作为复用来源；失败sample则仍为FAILED。生命周期完成只指已声明执行范围，必须与业务交付完整度分别显示。

私有路径继续使用 `model-jobs/<batch-run-hex>/<stage>/<job-key>/`；服务/request journals 也必须包含该 batch namespace。execution config、request ID、request/output 路径和重复提交登记都按新批次隔离；sample inspection也写在outputDirectory的batch子目录，不再只按materialId覆盖。原请求原样保留。旧 v1 私有执行配置、v2 state 不被正常新 reader 静默双读；实施时提供一次显式、离线的旧 state 导出：核验原 Step05 与实际已完成 M10，写到**新 v3 文件**，保留旧文件。导出只读现存 artifacts，绝不扫描或发模型请求；找不到有效 M10 就报告无法导出，不编造 reference。本次已保存材料因此不需要为了升级元数据重新生成。

**输出归属已贯通而非只换目录。** Activity/Process/Report publisher 接受显式新输出 run；材料保持原 sourceRun 地址，新 Activity/Knowledge/Report publication 属于 batch run。私有run-output已升至 `analysis-run-output-v3`，保留既有四个 checkpoint reference并显式增加 `sourceRunId`：材料按 sourceRun 校验，后三项按输出 owner/batch 校验，写入和重开完全同义。只能验证后引用旧产物，不能篡改上游 receipt/runId 或关闭所有跨 run 检查。公开 reference 字段形状不变；新结果的普通 inspect/artifact/render 沿真实引用读。最终私有执行清单把 document/report checkpoint 链接到 materialsCheckpoint 和 modelBatchId，业务 JSON/九章正文不增加这些控制字段。

新批次的根 request 继承 source run 的冻结来源与材料相关 controls、candidateSeriesRef、readerCandidateRound、parentCandidateRef 和 approvedFindingRefs；模型业务 profile/Prompt 可按实际配置形成新请求引用并进入结果 fingerprint，不以技术来源改变处理。包含技术与业务子项的复合配置引用，应核对真正影响所选材料的子项相同；新业务 controls 要绑定新实际Prompt/profile，不能沿用旧引用冒充已采用新配置，也不能要求无关模型项相等才让材料通过。写入前逐项核验这份明确继承关系，拒绝不同来源/不同候选 lineage。该改动只接入现有维护 CLI 的 model-only composition；现有 public executeStep 仍可能进入技术 coordinator，不能仅凭新增内部入口就宣称任意 executeStep 都已支持材料批次复用。

### 7.3 哪些结果可以复用

最小复用单位是**完整、校验通过并已保存的 DRAFT＋REVIEW job 结果**，不是任意成功的一次网络响应。只给现有私有 job store 增加验证读取和“复用或执行”的选择，不建设半轮恢复或请求修补系统。

| 旧 job 情况 | 新批次行为 |
| --- | --- |
| 未启动，或 DRAFT 失败/STARTED 后结果未知 | 新 job 做一次 DRAFT＋一次完整 REVIEW；旧记录不动 |
| DRAFT 完成但 REVIEW 失败/未知 | 保留旧草稿作诊断；新 job 重新做完整 pair，不拿孤立草稿跳过 DRAFT |
| 完整已审结果已原子保存，内容和绑定全部匹配 | 两次模型调用都跳过；保存指向原结果的复用记录 |
| REVIEW 似乎返回但结果保存不完整/损坏 | 不承认为可复用结果；保留故障，先报告损坏，不自动重新调用 |
| 已审结果合法但输入、Prompt、schema/profile 或绑定不匹配 | 标记不匹配，不复用；新批次按当前明确配置执行 |

稳定 jobKey 包含阶段及材料/过程组/总结/报告的真实输入成员身份，不含 batchId；不同材料上的 E1 不能相撞。`inputFingerprint` 比较完整 clean input、来源/入口短 ref 映射、实际 Prompt 文本与版本、response schema、有效内容 profile、Module/producer 版本、实际 Provider/账户 scope/model/effort/sandbox。使用现有 canonical codec；不得用中文摘要相等替代内容比较。新的 run/batch、排队时间、并发数、日志/输出目录及秘密值不属于内容 fingerprint；不同账户或模型不能只因输出字节恰巧相同就串用。Provider 绑定仍需独立验证非秘密账户 scope 与实际 runtime identity。

现有材料、活动或结果 record 可能含来源 runId，不能直接改字段后冒充原结果。读取复用记录时验证原结果、完整 REVIEW、对应输入映射与服务绑定；把已审语义内容经现有确定性 local/global 映射装入新批次的聚合，保留 `reusedFrom {modelBatchId, jobKey, resultReference, resultSha256}`。不重新总结中文，不丢条件/规则/字段。新旧 execution bytes 可以因归属不同而不同，验收比较语义投影、coverage 和 refs，不虚称所有 archive 字节相同。

Activity 先逐材料选择复用/执行，全部完成后稳定聚合；Process 在新活动集合上重新计算**既有分组**，按实际完整组输入匹配结果。只复用输入未变的组；受变更影响的组、仓库总结和报告各按自己的 fingerprint 判定，不因为“活动大部分相同”直接复用旧整仓报告。孤立单活动组仍按既有规则零过程调用。完整结果包含 required unexplainedEntries 但为语义 PARTIAL 时可如实复用；不能把 PARTIAL 升格为完整业务验收。

### 7.4 失败、授权和不做什么

自动重试始终关闭；显式新批次是用户决定的新执行，不是 worker 观察失败后自行另起批次。一次授权可覆盖已明确的多个批次/范围，不重复询问同一授权；若用户要求先讨论、暂停或收口，不启动新批次。批次 ID 不是调用许可。运行故障后的新批次继承原 candidateSeries/readerCandidateRound/parent/findings，不递增轮次、不因分配了新 runId 就创建新业务候选；仅有 DRAFT 或失败 REVIEW 不是可选最终 Candidate。复用完整最终报告只是复用同一候选内容，不另算一份新候选；已有完整且验证通过的最终候选若需内容修正，仍走原有具名 finding 的 ROUND_2，不能换 batchId 无限生成同轮替代报告。

本次不做同 run 恢复、自动重试/切服务、孤立 DRAFT 跨批续审、分布式锁、消息队列、事件重建或复杂状态修复。必要条件只有：旧材料可验证、批次隔离、已审 job 可验证复用、输出来源正确。无法确认某个 STARTED 请求是否在服务端完成时，报告“旧结果未知，新批次可能再次产生该任务的模型工作”，不承诺 exactly-once 或零额外耗时。

### 7.5 用已保存整仓样例推演

2026-09-13 固定 jshERP 的第一份保存材料有 **326 份材料、326 个入口处置**；其后 4 个 Activity DRAFT 留有 STARTED、没有完整已审结果。材料 checkpoint 为 `module-receipt:7228b9f5048bc3dc9dfba94712e3e839fd211d8a1c2c9baebccea6825a89e67b`。完整位置和第二轮记录见 [实际运行记录](../../progress/jsherp-full-parallel-business-report.md)。这不是模型已经理解了 326 个入口。

采用本设计时，显式导出并选定第一份材料，创建新 batch：已审可复用数为 0；4 个失败 job 与其余未启动 job 都按当前范围排队，不重跑 JDT，也不把旧 STARTED 改成 COMPLETED。若另一批次有 100 个完全匹配的已审 job，则先验证复用这 100 个，只为其余必要 job调用模型；这个 100 是规则示例，不是本次实测。

后一次 host 扫描实际保存 **325 份材料、326 个入口处置**，缺失点为 `MaterialExtendController#getList` 的导航超时。它是不同 checkpoint，不能按“最新目录”自动替换第一份，不能手工把另一份的 S 编号混进来。用户提出的单入口复核仍单列待执行；本次文档修改不执行它。首次模型故障的具体进程根因尚未由完整日志证明，不写成已确认的 JDT 缺陷。

### 7.6 最小实施与验收指南

Luna/xhigh 先针对既有公开/内部组合入口写 RED，Terra/xhigh 实现相同 seam；Sol/xhigh 定位真实故障，设计偏差交 Sol/ultra 或 Astra/ultra。按材料 reader＋v3导出、batch绑定＋输出归属、已审结果读取＋复用、下游传播与 CLI 四个局部交付推进，不重建技术阶段。实施者不能只改 journal 目录后声称完成。

直接验收：同一材料先失败四 job，再显式新 batch；前五步、JDT及Builder计数均为0，旧失败/run状态/bytes不变，新请求与旧请求隔离；完整已审 job零调用复用，孤立DRAFT新pair；缺失/损坏/跨来源/错账户拒绝；并发1/4/6不改变内容；变更Prompt只使相应模型结果失效；过程/总结/报告按依赖内容失效；sample不占整仓固定输出；新报告来源可回查原材料；旧v2state导出零扫描；已暂停时不自动另开batch。保留当前多入口、覆盖、来源、完整REVIEW和单次保存测试。

全部自动化使用 scripted/替身 Provider，不运行客户构建或真实模型。文档同步不表示上述测试或代码已经完成。
