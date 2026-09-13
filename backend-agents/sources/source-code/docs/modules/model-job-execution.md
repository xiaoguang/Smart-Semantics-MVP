# 模型任务并行执行

> 已批准的目标设计，尚未实施。本轮只同步 Markdown；不运行模型、扫描、测试或构建。保留 Java 17、八步、四个业务 Module 和唯一 `RepositoryAnalysisAgent`。本页拥有任务调度、Provider 绑定、YAML、即时保存与验收合同；业务内容仍由 [Step06](../analysis-steps/06-flow-interpretation.md)、[Step07](../analysis-steps/07-repository-knowledge.md)、[Step08](../analysis-steps/08-nine-section-document.md) 拥有。

## 1. 并行单位与阶段顺序

一个 job 是一份材料的 **DRAFT → 校验 → 完整 REVIEW → 校验并保存**。同一 job 的两次请求使用启动前绑定的同一 Provider、账户服务、模型和 reasoning effort。REVIEW 必须携带完整原材料和完整实际 DRAFT，不能只传摘要、标题或 patch，也不能假设新会话记得 DRAFT。Activity 的 `missingEntryKeys` 与 required `unexplainedEntries` 合同原样保留。

```text
01–05 技术步骤 → BusinessMaterialBuilder 保存完整材料（零模型）
  → Activity jobs 并行，各自 DRAFT → 完整 REVIEW → 保存
  → 等全部活动完成并稳定聚合
  → Process-group jobs 并行，各自 DRAFT → 完整 REVIEW → 保存
  → 等全部过程组完成并稳定聚合
  → repository-summary 最多一个 job：DRAFT → 完整 REVIEW → 保存（或按既有规则明确跳过）
  → 一次发布完整 knowledge/process aggregate
  → 唯一 whole-report job：整篇九章 DRAFT → 整篇完整 REVIEW → 保存
  → 确定性 renderer（零模型）
```

两个过程组可以读取同一已审活动。输入是不可变共享视图，成员保持多对多；worker 不修改活动、全局 ID 映射或别组结果。仓库总结与报告各最多一个 job，不拆章并发、不额外增加总结/审阅轮次。保留 ProcessExplainer 既有总结准入：`maxRepositorySummaryItems=0`、没有过程或条目/输入容量不容纳时，零总结请求并保存具体 notConsolidated/范围说明。过程 aggregate 等总结完成或该显式跳过后才发布，不能在组屏障处提前发布。0 活动保留现有 Process 零请求行为；显式空仓九章仍按既有报告 DRAFT/REVIEW 执行，并诚实标明范围不完整。

## 2. 唯一配置入口与精确 YAML

在既有 `RepositoryRunMain --config` 文档的 `sourceAnalysis.modelJobs` 中配置模型执行。目标根版本为 `repository-run-config-v2`；`.yaml` / `.yml` 由既有 Jackson YAML 能力加载，同形 JSON 也归一到同一个配置对象。这是现有启动器的版本升级，不新增 CLI、另一配置文件或公开 Agent 方法。`source`、`paths`、`inputs`、`technical`、`business`、`policyRegistry` 仍由原拥有者处理。

下面是需合入完整运行配置的 **目标 YAML 节选**，不是当前可运行模板。它说明全局 6、Pro 4、API 2；数字限制同时在途任务，不限制总材料数。

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
| provider.model / reasoningEffort | Codex 省略分别为 `gpt-5.6-luna` / `high`；API 均必填；非空且受该 adapter/模型支持，未知或不支持的组合提前失败，不降档或替换 |
| provider.timeoutSeconds | 省略为 600；正整数且可安全转换为现有超时类型；每次 DRAFT/REVIEW 沿用已有进程/请求超时，不新增 job 总时限开关 |
| Codex executable / auth | executable 必填本机绝对可执行路径；auth 必填且 exact keys 为 `mode: chatgpt, codexHomeEnv`；后者是已有登录上下文目录的环境变量名，不能放 token 或内联凭据 |
| API endpoint / auth | endpoint 省略为 `https://api.openai.com/v1`，必须为适配器明确支持的 HTTPS endpoint，无 userinfo；auth exact keys 为 `mode: apiKey, apiKeyEnvs`，非空且无重复环境变量名，值不写 YAML |
| routing | 模型模式必填，exact keys 为 activity/processGroup/repositorySummary/report；每值为非空、不重复且顺序固定的已定义 Provider key 数组；不接受 wildcard、隐式 fallback 或未知引用 |
| journalDirectory / outputDirectory | 模型模式必填，沿用现有本地私有目录/非 symlink 校验；不进入公开请求或模型材料 |

环境变量引用必须匹配 `[A-Z_][A-Z0-9_]*`，引用值缺失、空、类型/路径不合法或已知认证模式不匹配时，在任何模型请求前拒绝。YAML 的重复 key、未知字段、未知 kind/auth mode、互斥字段混用均拒绝；不是忽略后继续。API 默认值并不隐式创建 API Provider。`materials-only` 可以省略整个 modelJobs；存在时检查结构，但不构造 Provider、不解析秘密值、不登录或调用模型。

组合根解析整个配置一次，输出不可变有效 engine 与 model-job 配置。当前 `EngineConfigurationLoader.SourceAnalysisDocument` 只接受 javaEngine/jdt；实现时由组合根向它投影 **仅含这两个字段的 sourceAnalysis 子树**，不能把 modelJobs 原样传入并假称旧 loader 已支持。JDT `collect` 的单 worker 范围仍限技术取材；模型池在材料保存后才工作。

当前 CLI 实际是 `repository-run-config-v1` JSON 加独立 `repository-run-provider-v1` / `--provider-config`，且硬编码单个 Luna/high Provider。目标 v2 将后者 executable/timeout/journal/output 吸收到上述同一配置，并移除模型模式的第二配置入口；同时提供旧 provider-config 或旧根版本显式失败，不静默合并或双读。此轮不改 JSON/YAML 模板、loader 或命令。有效非秘密路由、Provider/model/effort 配置归入已有 profile/toolchain/prompt 配置引用及运行诊断，不往 public request 塞路径/密钥，也不把技术阶段 `provider: NONE` 误当业务身份。

当前 continuation state 用整个 canonical `--config` 文档 SHA 校验，因此不能只往它加 modelJobs 就宣布可改变并发而复用材料。v2 精确采用 `baseConfigurationSha256 = SHA256(canonicalJson(normalizedV2Document 删除且只删除 sourceAnalysis.modelJobs))`，其余 source/technical/business/inputs 等检查全部保留。私有材料状态 exact keys 为 `schemaVersion: repository-run-state-v2, baseConfigurationSha256, runId, businessFlowsPublication`，后两项及 publication 子字段原样保留；它不保存调度 SHA。每次模型执行前另在现有私有运行区原子保存 `{schemaVersion: model-job-execution-config-v1, runId, modelJobsSha256, modelJobs}`，后两者为补齐默认值后的非秘密 modelJobs 对象及其 canonical SHA，保留引用名称但绝不写解析后的 key/token 值。无 modelJobs 的 materials-only 不产生该记录。

只改并发 4→8 不改变 base SHA，不重跑 JDT；模型/Prompt/输出等真实内容配置变化仍使相应业务 fingerprint 失效。每次核验 base SHA、保存的执行配置 SHA 及实际技术 publication，不能将未知 bytes 当成可信。此变更只升级上述本地 config/state，不改变 public run-request-v2。旧 v1 状态不就地转换或覆盖；旧完成产物通过既有显式新执行/checkpoint 复用边界使用，不引入同 run 自动恢复。

## 3. Provider、认证与额度

一个 Provider 定义对应一个被配置的账户/服务额度范围，不等于一把 key 或一个新会话。多个 API key 若共享组织/项目或模型限制，就放在同一 provider.auth.apiKeyEnvs 中；不同 key 不能获得独立并发额度。不同 Codex Pro 会话仍共享所属账户额度。`quotaScope` 是操作者的明确声明，程序拒绝已知重复 scope，并核对可获得的账户/项目元数据；即使 scope 名不同，解析后 API key 相同或 Codex 上下文实际目录相同也应拒绝重复 Provider（仅内存比较，不记录秘密）。没有证据时不能声称能自动从任意 key 或登录状态发现所有共享额度。操作者必须正确合并这些配置，不新增额度发现或第三层限流系统。[API 官方限制](https://developers.openai.com/api/docs/guides/rate-limits)说明组织、项目和共享模型限制。

Codex Provider 可以只引用一个已登录的上下文。每个 job 使用新的对话/调用会话；每次 `codex exec --ephemeral` 保持独立请求临时目录、schema、输出与诊断。DRAFT 和 REVIEW 显式传完整输入，不依赖 resume。Codex 自己管理登录上下文的状态；应用不在并发运行中 login/logout、改共享配置、切全局账号、复制凭据或新建 cap 数量的登录目录。不同账户可在不同 Provider 引用不同上下文。API adapter 每个 job 固定一份 key 引用与独立请求对象，不修改进程全局环境。

`codexSubscription` 必须强制使用 ChatGPT 认证（官方 `forced_login_method="chatgpt"`），并针对实际选定的上下文确认登录方式及本地状态可初始化。仅 `codex login status` exit 0 不足。子进程环境清理/屏蔽 API-key 认证覆盖；显式固定 auth、model、effort、read-only sandbox，不能从继承环境或其他配置启用计量 API。所选 CLI 版本无法可靠执行这些限制就提前失败。[官方认证说明](https://learn.chatgpt.com/docs/auth)区分 ChatGPT 订阅认证与 API-key 计量认证。

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

当前两个 Explainer 在全部外循环之后向固定 module 地址聚合 publish；直接把 publisher 移进 worker 会 collision。目标是在既有运行私有区域保存每个完成 REVIEW 的独立结果：coordinator 收到结果就以稳定 job key 原子保存，然后回收名额；一个较慢的前序 job 不能阻塞后序结果保存。全部结束后，coordinator 按原材料/组顺序一次合并全局 coverage/ID 映射；activity aggregate 在活动屏障后安装，process/knowledge aggregate 必须再等总结完成或既有规则明确跳过后才一次安装。worker 不共享可变 `ArrayList/Map`，不发布同一固定 aggregate 地址。

复用已有私有 journalDirectory 和安全原子写入能力；每个运行下增加有限的 `model-jobs/<phase>/<job-key>/reviewed-result.json` 私有结果，不新增公开 artifact key、step/module 地址或枚举。phase 为 activity/process-group/repository-summary/report；job key 用当前 canonical framing 对 phase、原材料/组稳定身份（singleton 用固定 scope）、内容输入 fingerprint 求 SHA-256。它是调度元数据，不进入模型输入。私有记录保存完整已审结果、Provider key/非秘密 quotaScope、ModelRuntimeIdentityV1、输入 fingerprint 与诊断，不保存 token/key 值。重复相同 bytes 可认已保存，冲突拒绝；不覆盖历史产物。

`RunJournalStructuredProvider` 目前按一个 `ModelRuntimeIdentityV1(provider,model,reasoningEffort,sandbox)` 校验，身份里没有账户；最小修改是每个已绑定 Provider/job 使用独立私有 journal namespace，保持 request/response 身份验证。不同账户上相同模型/输入不能串读；同一 job 只提交一次，同一进程维护单一 job-key 登记，重复排队不能发第二次 DRAFT。不同材料即使模型输入 bytes 相同也不会互相抢写。DRAFT/REVIEW 仍分别记录实际请求及输出；不改业务 packet 增加调度 ID。

inputFingerprint 继续覆盖实际完整内容、实际 Prompt 文本/版本、有效模型/effort/output 与 Module 版本。Provider 绑定/非秘密 quotaScope 在私有执行命名空间和运行配置中可核对；路径、密钥值、新 runId、队列时间和并发数不是业务内容，不混入模型 packet。变更路由须重新固定各 job 绑定并检查配置身份；不能在同一执行中热改。改变并发且输入/绑定相同时不能制造不同业务排序。跨 run 显式复用仍核验现有 fingerprint、schema/hash/ref/basis，不自动续跑 uncertain started 请求。

任一 fatal 被观察到后立刻关闭新 job 派发。未开始 DRAFT 的队列项不再发模型请求，逐项在既有失败诊断中说明“本执行已停止、未启动”，不冒称预算不足或分析完成。已经开始的 job 若自身 DRAFT 合法，就在原绑定上继续它唯一的 REVIEW，并在既有各次超时内保存结果；兄弟 job 失败不让它丢掉已完成 DRAFT，也不构成重试。自身 DRAFT 非法/失败的 job 不进入 REVIEW；自身 REVIEW 失败则保留诊断，不造已审结果。尚未真正开始 DRAFT 的已派发 worker 在调用门检查停止标记。

协调器等待/收集所有已开始 job 的终态或既有 timeout，保留已经成功的私有结果与上游产物，最终以现有 FAILED 路径结束；没有所有必需结果就不安装成功 aggregate，不跨过阶段屏障开始总结或报告。保存失败同为 fatal。没有 retry、自动 reroute、failed-call replay、同 run takeover 或终态修复。后续人可观察/显式复用可验证的完成产物；本文不增加自动恢复协议。

## 6. 最小实施顺序与定向验证

这是后续实现工作单的依据，不是本轮已完成代码：

1. Luna/xhigh 在既有配置/Provider/explainer/workflow seams 写 frozen/scripted 行为 RED；Terra/xhigh 增加 v2 单配置解析及严格认证绑定。
2. 提取每材料/过程组不可变结果，接入一个池和两级名额；复用 BusinessAnalysisWorkflow 现有阶段边界。
3. 接通逐 job 私有保存、Provider/job journal namespace、单次提交与最终稳定 aggregate。接线既有 CLI / PersistedBusinessRunExecutor；移除硬编码单 Provider 和旧第二配置入口。
4. 完成下面的直接测试与文档同步，再由用户当前授权范围决定是否做真实小样本；不重做旧清理、JDT 优化或失败产品调用。

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
