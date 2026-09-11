# Source Code Analysis Agent 总体设计（ACTIVE）

> 本文是 backend-agents/sources/source-code 的目标设计权威。它记录用户已批准的业务优先简化路线。Step 01–05 的现有技术能力继续保留；Step 06–08 改由四个深 Module 完成。当前 Java、Schema 与运行产物尚未完成这次迁移，本文中的新业务检查点均为待实现目标，不能写成已运行事实。

## 1. 目标：从冻结源码得到可读的业务说明

Source Code Analysis Agent 对一份明确绑定的冻结 Java/Spring MVC/MyBatis 仓库做离线分析，最终交付一份仓库级、恰好九章的业务说明。读者应能回答：

- 这个系统支持哪些业务目标、业务对象和业务活动；
- 活动在什么条件下发生，程序会形成什么代码定义的结果；
- 多个入口可能怎样组成端到端过程；
- 哪些字段、关系、公式和示例问题能由源码说明；
- 哪些岗位、制度、跨系统结果或运行事实仍需业务人员确认；
- 每段重要说明可以点开哪一小段冻结源码复核。

框架不要求 Java 先把源码翻译成行业术语，也不要求每个业务句子拥有一条形式 Proof。程序负责可信输入、定位、覆盖和简单结构验证；Luna/high 负责理解完整活动、串联过程和写自然业务正文。

## 2. 不变的外部形状

以下已批准外部合同保持：

- 仍只有八个公开分析步骤，顺序和 semantic key 不变；
- 仍只有一个 run-centric RepositoryAnalysisAgent public Interface，不创建 POC public Interface、第二套命名空间或旁路运行器；
- 最终 Markdown 仍只有共享 NineSectionProfile 规定的九个 H2：
  1. 文档说明
  2. 业务目标
  3. 业务对象
  4. 业务活动
  5. 字段与维度
  6. 对象关系
  7. 指标口径
  8. 示例问题
  9. 待确认事项
- 一次 Source Agent 运行仍只产生一份仓库候选；选择、冻结、打包和激活仍是另行授权的动作。

固定 52 项 reader-visible output、Step 06 九 payload、每个内部 Module 都必须 fresh-reopen、多层 hash/accounting ledger、R0/finite keys、逐业务原子 Proof/owner/accounting、全记录替换状态机、bridge/reconciliation 独立账本以及 Java/CLI/HTTP 三个 adapter 同时完成，均退出新的业务核心路径。已有 Step 01–05 技术产物、Proof、Trace 能力和 canonical store 不删除、不降级；它们只是不会再把 Step 06–08 绑成同样重的内部协议。

## 3. 两种可同时成立的真相

### 3.1 技术真相

Step 01–05 继续给出冻结源码身份、入口、五图、Fact、Proof、Flow、Capsule、Gap 和技术覆盖。严格 Proof 链适合回答“这个调用、条件、数据流能否被程序重验”，并可通过技术查看入口继续访问。

### 3.2 业务解释

业务解释回答“这段代码整体在支持什么活动”。它的最低来源不是一条逐原子 Proof，而是程序创建的短 SourceRef：

~~~json
{
  "ref": "S3",
  "file": "src/main/java/example/ReplenishmentService.java",
  "startLine": 21,
  "endLine": 22,
  "snippet": "ReplenishmentOrder order = ReplenishmentOrder.from(command.lines());\nreplenishmentOrderMapper.insert(order);"
}
~~~

程序只从已冻结源码创建 ref、路径、行号和 snippet，并验证它们仍指向同一 run input。一个 BusinessMaterialSet 内统一分配 ref：同一个 ref 只能指向一处固定片段，重复使用同一片段就复用 ref，不允许各包分别从 S1 编号后再混合。模型只收到短 ref allowlist 与必要 snippet，只能引用 S3，不能创造路径、行号、hash、Fact、Proof、artifact identity 或 Provider 控制。最终报告的简单 refs sidecar 把 S3 映回可点击片段。

每次模型调用还会收到由当前阅读包/profile 生成的 closed JSON Schema。它预先规定完整输出字段、允许选择的短 ref（以及过程任务可选择的 activity）、certainty 枚举和文本/列表上限；模型不会先返回一个任意 object 再靠事后报错猜结构。Java 收到响应后仍以同一 allowlist 和 profile 重验，Schema 约束结构，不替代业务语义审阅。

SourceRef 允许模型形成有来源的业务解释，但不会把解释升级为形式证明。反过来，严格 Proof 证明了 Java exact call，也不会自动证明岗位、制度或某次生产运行的结果。

## 4. 四个深 Module

四个 Module 各有一个小 Interface，把复杂度收在实现内。它们是 Step 06–08 内部 seam，不扩展 public RepositoryAnalysisAgent。

~~~text
Step 01–05 frozen technical publications
                 |
                 v
       BusinessMaterialBuilder
        | coherent materials
        v
          ActivityExplainer  -- Luna/high draft + one review
        | reviewed activities
        v
           ProcessExplainer  -- recall + grouped Luna/high + repository summary
        | repository knowledge
        v
      BusinessReportPublisher -- Luna/high chapter draft + one review
        | validated nine-section JSON
        v
 deterministic Markdown + simple clickable refs
~~~

| 深 Module | 主要复杂度藏在哪里 | 小 Interface 给调用者什么 |
| --- | --- | --- |
| BusinessMaterialBuilder | 从入口、五图、Fact、Flow 和冻结源码选择少量连贯片段；noFlow fallback；ref allowlist；入口覆盖 | build(request) -> BusinessMaterialSet |
| ActivityExplainer | 有界包、完整局部活动理解、一次内容审阅、来源与覆盖校验 | explain(materialSet) -> ReviewedActivitySet |
| ProcessExplainer | 用已审活动与已保存材料做宽松召回、重叠分组、多对多活动成员关系、分组解释、仓库级有界总整理 | explain(activitySet, materialSet) -> RepositoryBusinessKnowledge |
| BusinessReportPublisher | 九章内容创作、一次整体审阅、简单 JSON/来源/章节验证、确定性排版 | publish(knowledge) -> BusinessReportPublication |

Provider 是真实 seam：产品 adapter 使用经授权的 Codex Subscription Luna/high，自动化测试 adapter 使用 scripted fake。HTTP adapter 是 RepositoryAnalysisAgent 的后续接入能力，不是这四个业务 Module 的完成前置。

## 5. 八步怎样接力

| Step | 稳定职责 | 给业务路线的直接投影 |
| --- | --- | --- |
| 01 verified-source-inventory | 验证快照、盘点文件、提供安全 source handles | run input binding 与可读取文件集合 |
| 02 application-discovery | 识别应用能力、所有受支持入口与 Mapper 候选 | 业务分析入口分母、handler 与 route |
| 03 program-graphs | 保留结构、调用、控制、数据、证据五图及 Gap | 邻近调用、条件、数据承接和片段选择线索 |
| 04 proven-code-facts | 保留可重验 Fact/Proof/Gap | 材料中的可靠技术观察与可选技术详情 |
| 05 business-flows | 保留 entry-rooted Flow/Capsule 和 entry disposition | 优先的连贯路径；不是最小业务流程 |
| 06 flow-interpretation | BusinessMaterialBuilder + ActivityExplainer | 业务材料与已审局部活动 |
| 07 repository-knowledge | ProcessExplainer | 跨入口过程与一份仓库知识 |
| 08 nine-section-document | BusinessReportPublisher | 九章结构 JSON、Markdown、简单 refs 与验证结果 |

Step 01–05 不因业务简化而重写 parser、图、Fact、Proof、Flow、Capsule 或既有技术 publication。Step 06 可以把它们作为同进程 typed objects 使用，也可以从已完成 step publication 重开；它不要求为了内部每个小动作再建立一层 module publication。

## 6. 四个 Interface 的稳定输入输出

### 6.1 BusinessMaterialBuilder

输入 BuildBusinessMaterialsRequest 只需：

- 一个最小 RunInputBinding：runId、sourceSnapshotId、sourceManifestSha256、Steps 01–05 publication root；
- 已验证的入口全集及 disposition；
- 五图、Fact、Flow/Capsule 与 Gap 的 typed view；
- 能按已验证 locator 读取冻结源码的 reader；
- maxSourceRefsPerMaterial、maxLinesPerRef、maxMaterialChars 等明确上限。

程序按入口组织完整局部活动所需的少量材料：入口声明、关键条件、对象构造/转换、持久化或边界调用，以及必要的直接 callee/Mapper SQL。它优先使用 Flow/Capsule，但没有 Flow 时，只要入口和冻结源码仍可安全定位，就从入口与图构造 ENTRY_SOURCE_FALLBACK。长方法不会只截取开头：在既有引用额度内保留开头，并加上一至两个后续的直接调用行；对同一个已选完整方法还会以有上限的多样化语法观察优先保留输入、状态/持久化形态调用、关键条件、条件后的调用与终止。因此中段 `set...`、`update...` 等动作不会只因不在少量原文窗口内而消失。这不扩展到新的文件或调用图。Java 只写“构造某类型”“调用 insert”“条件为 items 非空”这类技术观察，不内置采购、医疗或审批词典。

输出 BusinessMaterialSet 包含材料、SourceRef reverse map 和 entryCoverage。每个发现入口必须是 ANALYZED_MATERIAL、MATERIAL_WITH_GAPS 或 NOT_MATERIALIZED；后两者必须有具体原因。模型安全视图只含上下文、观察、短 ref/snippet 和限制；materialId、Flow 与 Gap 身份只留在程序侧的外层映射。

### 6.2 ActivityExplainer

输入是一个或多个在预算内的 BusinessMaterial。模型先对完整局部活动写 ActivityDraft，再对实际完整草稿做一次 review 并返回修订后的完整活动 JSON。审阅对象是 whole activity，不是逐原子 key。

活动至少可表达开放词汇的：

- businessPurpose、participants、businessObjects；
- triggerOrInput、conditions、activitySteps、codeDefinedResults；
- businessRules、formulasOrMetrics、terms；
- sourceRefs、questions、scopeLimitations。

participants、rules、metrics 可以为空。没有岗位依据时不填“采购员”“仓库员”等常识角色；没有公式时不造指标。每个活动整体或自然段引用一个或多个 allowlisted SourceRef 即可，不要求每个词挂 Proof。

### 6.3 ProcessExplainer

输入是全部 ReviewedActivity、entry coverage 以及已经保存的 BusinessMaterialSet。程序可根据共同对象候选，也可根据材料中同一局部 Flow、技术证据或冻结源码文件做宽松召回；后一类只会生成“值得一起阅读”的候选包，路径不会发送给模型。这些都不是业务结论，更不会批准业务顺序或对象同一性。

每个有界 process group 由 Luna/high 写 draft，并对完整过程做一次 review。一个活动可以属于多个过程；同名活动不自动合并，不同名活动也不因共享字段自动合并。只有模型在同包来源基础上提出并经 review 保留的关系，才进入过程知识。

大仓不会单次把全仓源码交给模型。先基于已审活动建立有界重叠分组，再生成每组短摘要，最后只对组摘要、跨组候选与覆盖表做一次有界仓库总整理。若总整理容量不足，超出的 group 明确列为 NOT_CONSOLIDATED_BUDGET，仓库总览标为部分覆盖；不得静默压缩掉条件或把已处理切片写成全仓完成。

输出 RepositoryBusinessKnowledge：activities、processes、manyToManyMemberships、objectsAndTerms、repositorySummary、confirmationTopics 和 coverage。

### 6.4 BusinessReportPublisher

输入是一份 RepositoryBusinessKnowledge 与同 run 的 SourceRef allowlist。Luna/high 可直接创作自然、面向业务读者的九章段落内容；它输出 JSON paragraph/list item，不输出 Markdown 标题、编号或样式。随后同一完整草稿最多做一次 review，返回完整替换 JSON。

程序只做容易确定的检查：

- sections 恰好 1..9，标题与共享 NineSectionProfile 完全一致；
- paragraph/list item 文本非空，refs 只能来自 allowlist；
- 文档说明写明源码行为与运行结果的区别；
- entry/process coverage 的未分析原因进入第 1 或第 9 章；
- 第 7 章只呈现知识中实际存在的公式/口径；没有就明确“本次未从源码识别到可定义指标”；
- 结构合法后由程序确定性加入 H1/H2、段落、列表和 ref 标记。

纯 render 只重排已验证 JSON，不调用模型；inspect/observe 只读；重新编辑正文是新的显式 model action。三者不可暗中互换。

## 7. 怎样描述源码行为，而不冒充运行事实

冻结源码在上下文中清楚构造对象并调用具有明确持久化含义的 insert/save 时，可以写：

> 系统根据补货明细生成补货单并保存。

这是对代码定义行为的说明，不是对某次运行的断言。以下表述需要运行数据或外部证据，不能仅从静态源码得出：

- “今天成功保存了 37 张补货单”；
- “本次运行已经成功增加库存”；
- “本次运行已经成功过账并完成付款”；
- “每个收货记录只会产生唯一采购单”；
- “采购专员负责审批”。

如果源码清楚定义库存登记或记账动作，正文可以说“该流程登记库存”或“该流程生成记账记录”，但不能声称某次运行已经成功发生。如果只看到语义模糊的边界调用，正文应写“系统调用 X 处理接口”或把具体结果集中列为待确认；不再机械地把所有清楚的 save/insert 也降格为“提交供后续处理”。这种业务表述不要求 Java 先为它建立新的行业 Proof；只有声称 exact call/guard 等技术证明时才继续遵守 Step 04 的 Proof 门槛。跨入口过程可作合理推测，但在过程段落结尾集中标注“过程顺序/制度约束待确认”，不在每句话后重复技术警告。

## 8. 检查点、复用与最小身份绑定

新路线保存少量可复用 JSON/JSONL：

| Step | 检查点 | 用途 |
| --- | --- | --- |
| 06 | business-materials.jsonl | 连贯材料、SourceRef 与入口处置 |
| 06 | activity-explanations.jsonl | 已审活动与局部问题 |
| 06 | activity-coverage.json | 全入口业务分析覆盖 |
| 07 | business-processes.jsonl | 已审过程与多对多成员关系 |
| 07 | repository-business-knowledge.json | 报告的唯一业务知识输入 |
| 07 | process-coverage.json | 分组、总整理和遗漏说明 |
| 08 | business-report.json | 已审九章 paragraph JSON |
| 08 | source-refs.jsonl | 报告 ref 到冻结源码片段映射 |
| 08 | document.md | 程序确定性排版结果 |
| 08 | report-validation.json | 九章、来源与覆盖检查结果 |

同一进程允许直接传 typed object，无须在每次调用前重开；但有意义的边界立即落盘：business-materials 在材料编译完成、首次模型调用前保存；每个完成 REVIEW 的 activity/process package 随即写入对应 JSONL；完整知识与报告也在各自完成点保存。后续失败不删除这些已完成检查点。每个检查点保存 producing run 供审计，并携一个简单 inputFingerprint。该 fingerprint 由实际内容输入（不含新 runId）、实际 Prompt 内容及版本、有效模型与输出配置和本 Module 版本计算；所以相同冻结输入可跨新 run 显式复用，而 Prompt 改字或有效配置变化不会误用旧结果。复用只比较这个完整 fingerprint。它是防止读错源码/缓存的一层绑定，不展开成逐内部 Module receipt、fresh reopen 链、多层 hash、全记录状态机或同 run 复杂恢复。

started 的 Provider 调用仍保存请求/响应/模型身份和结果诊断，且绝不自动重试；这是一条调用审计，不是新的恢复账本。

## 9. 有界模型调用

每种内容任务使用同一简单模式：

~~~text
一个有界输入包 -> DRAFT -> 对完整实际 DRAFT 的一次 REVIEW -> 程序校验
~~~

- ActivityExplainer：每个 material package 最多 2 个 started requests。
- ProcessExplainer：每个 process group 最多 2 个；有容量时另有 2 个仓库总整理 requests。
- BusinessReportPublisher：整份九章最多 2 个。
- preflight 不满足容量时 0 个请求，并写未分析原因。
- `maxMaterialsToStart`是一次 ActivityExplainer 执行的显式启动上限。达到上限的 material
  不发请求，并以`NOT_ANALYZED_EXECUTION_CAPACITY`留在 coverage 中。因此整仓在真正扩大前
  可以先运行 0 或 1 个包，检查实际输入和完整 DRAFT/REVIEW，而不会把全部入口隐式排队。
- started 后失败即本次执行 fatal；无自动 retry、Provider switch、API-key fallback 或无限补修循环。

内部这些调用共同形成一份 Reader Candidate，不增加产品候选轮数。一个冻结来源最多仍有 ReaderCandidateRound 1 和经用户针对明确问题授权的 Round 2 两份产品候选；不能借内部任务生成第三份候选或无限回合。

真实产品调用必须另获当次授权。验证顺序固定为：先一个小型真实材料包观察返回，再一个不同领域的小包验证开放词汇，最后才考虑整仓。时间和成本在核心样本实际测量后估算，本设计不承诺 24 小时或其他未经测量的工期。

## 10. 全仓覆盖而不是切片冒充

业务覆盖以发现入口为主要分母，并保留 Step 01–05 的完整技术覆盖：

~~~json
{
  "discoveredEntryCount": 3,
  "analyzedEntryIds": ["entry:create", "entry:receive", "entry:bill"],
  "analyzedWithGaps": [],
  "notAnalyzed": [],
  "processGroupCount": 1,
  "notConsolidatedGroups": [],
  "repositorySummaryCoverage": "COMPLETE_FOR_DISCOVERED_ENTRIES"
}
~~~

覆盖账闭合的条件是每个发现入口都有活动解释或清楚的未分析原因，每个 process group 已处理或列出未整理原因，报告如实呈现覆盖；这不自动等于业务目标完成。只有足够的入口活动与过程已被解释、九章能够给出仓库级业务理解时，才能声称交付“整仓业务九章”。若所有入口都 NOT_ANALYZED，或仓库总整理/报告因预算未完成，可以保存部分检查点和范围说明，但语义交付状态必须是 INCOMPLETE，不能用覆盖账闭合冒充完成。报告可以按业务聚合而无需逐方法罗列，不过所有未纳入业务聚合的范围必须显式说明。它不要求给每个业务原子分配 owner/Proof，不保留固定 52 项总数，也不以一个小包 PASS 代表全仓 PASS。

边界处理：

- 0 入口：0 次 activity/process 调用，仍可生成九章范围说明，但不得声称理解了仓库业务。
- 有材料无 Flow：可以解释活动，保留“技术 Flow 未编译”的局部限制，不伪造 Flow。
- 单个 material 超预算：0 次请求，入口记 NOT_ANALYZED_BUDGET。
- 本次运行的可启动 material 数已达上限：其余材料 0 次请求，入口记
  NOT_ANALYZED_EXECUTION_CAPACITY。先用小上限检查输入和业务质量，再主动启动更大范围的新
  执行；它不是不透明的自动续跑机制。
- 某活动可能属于多个过程：显式多对多 membership，不强迫唯一 owner。
- 岗位或制度无法从代码知道：集中进入活动/过程的 confirmationTopics。
- 总整理超预算：保留各组知识，仓库 summary 标为部分覆盖，列出未纳入 group。

## 11. Gap、fatal 与停止条件

可交付 Gap 包括 unsupported syntax/framework、无安全定位片段、无 Flow、超预算、未知岗位/制度、过程顺序或对象同一性待确认、没有指标口径。它们必须进入 coverage 或第 9 章，但不必触发全记录替换状态机。

以下必须停止当前步骤且不发布伪成功：

- 冻结 snapshot、manifest 或 Steps 01–05 root 与 RunInputBinding 不一致；
- SourceRef 不能重读到相同 file/line/snippet，或模型引用 allowlist 外 ref；
- JSON 不完整、九章数量/顺序错误，review 后仍无法通过简单结构校验；
- Provider 已 started 后 transport/schema/runtime 失败；
- 覆盖表遗漏发现入口或将未处理 group 写成全仓完成；
- 需要通过发明路径、行号、岗位、制度、运行次数或外部效果才能填满输出。

## 12. 旧内部 Module 的去向

| 旧目标/实现名 | 新路线 | 说明 |
| --- | --- | --- |
| SemanticMaterialCompiler + SemanticPacketCompiler | 合并为 BusinessMaterialBuilder | 材料选择、ref 映射与 clean packet 是一个深 Module 的内部实现 |
| LocalSemanticInterpreter | 合并为 ActivityExplainer | 保留局部 DRAFT + 一次 REVIEW，删除逐 record 处置机 |
| ProcessContextRetriever + ProcessSemanticInterpreter | 合并并移动到 ProcessExplainer | 召回和业务判断在同一深 Module 内，程序 cue 仍不等于结论 |
| FlowInterpretationPublisher | 退役为独立 Module | Step 06 只写三个简单检查点，不再维护九 payload publisher |
| KnowledgeAdmissionCompiler + RepositoryKnowledgeMerger + RepositoryKnowledgePublisher | 合并为 ProcessExplainer | shape/ref/coverage 校验与仓库知识输出藏在一个 Interface 后 |
| NineSectionPlanner + NineSectionRenderer + ReaderTraceCompiler + NineSectionDocumentPublisher | 合并为 BusinessReportPublisher | 模型写 paragraph JSON，程序验证并排 Markdown/ref |
| R0 registry proposal、finite keys、R1/R2/P1/P2、bridge/reconciliation ledger | 退出目标路线 | 现有代码/fixture 仅作迁移输入，不扩展、不包装为兼容层 |
| Step 01–05 全部技术 Module | 保留 | 能力、I/O、技术 publication 和测试 seam 不变 |

迁移直接改现有 semantic packages 下的目标实现，不建立第二套 POC package、parallel runtime 或 public Interface。旧 artifact 可作为历史/迁移事实读取，但新路线不写双格式、不做 alias fallback。

## 13. 实施和测试次序

这是设计，不是已完成实现。后续实现仍按测试先行，但测试面收敛在四个 Interface：

1. BusinessMaterialBuilder：三入口、noFlow、ref 重读、预算和全入口 coverage。
2. ActivityExplainer：scripted DRAFT/REVIEW、开放词汇、清楚 save 行为、未知岗位和非法 ref。
3. ProcessExplainer：跨入口串联、同名/异名不自动合并、多过程 membership、总整理超预算。
4. BusinessReportPublisher：完整九章 JSON、自然正文、section/ref 校验、纯 rerender 零模型。
5. 经单独授权后才按“小包 → 第二领域 → 整仓”观察 Luna/high 产品质量。

自动化测试只用 frozen fixtures 和 scripted Provider；不运行客户 Maven、不联网、不调用真实模型。HTTP/第二产品 Provider adapter 列入后续接入，不阻塞前述业务质量验证。

## 14. 当前成熟度与导航

- Step 01–05 已有受控技术纵切。固定 jshERP 已完成一次零模型的 Step01/02 → direct-entry
  材料规划：337 个发现入口全部得到一份 `ENTRY_SOURCE_FALLBACK` 材料，其中 281 份还包含唯一、
  直接的同仓库 Java 调用目标；模型包不含路径、行号、哈希或 Proof。这个结果验证的是整仓材料分母、
  相邻源码选择和保存链，不是业务解释、跨流程过程或九章验收。该规划产生于自动语法摘要加入前，
  不应作为模型阅读材料质量的证据；当前 Builder 会从已选 Java 片段投影输入、条件、直接调用与终止路径，
  但仍须先以真实小包验证自动产物能否达到人工审阅材料的清晰度；
  五图/Fact/Flow 的完整固定仓运行仍未重新执行。
- Runtime 目前已有最小的 public run 起点：`LocalRepositoryAnalysisAgent.start`把一个
  path-free、内容寻址的分析请求保存为新的 `QUEUED` run，`inspect`可在关闭后重开同一 run。
  同一 Agent 现可对一个明确请求执行到最终九章文档：它从应用 bootstrap 注入的
  internal coordinator 取得能力，持久化 `QUEUED → RUNNING → FINISHED/FAILED`，不接受路径、
  模型包或配置参数，不自动重试或“恢复”未知的模型调用。`SourceAnalysisApplication`把一个共享 Agent、
  immutable round-one request template 和最小 CLI 合在同一 composition root：CLI 只能用已登记的
  `sourceRegistrationId`排队，或按已知 run 执行最终目标/inspect，不能传入路径、Provider 或配置。唯一例外是
  `capture-local-git`：它在分析开始前把一个绝对本地 repository path 和完整 commit 交给 bootstrap 已配置的
  `LocalSourceCapture`，只返回 registration ID；它不能在命令行替换 repository identity、capture policy、预算、
  workspace 或 Provider。
  `render/artifact`的 CLI command mapping 已存在，但仍需要后续 bootstrap 供应只读依赖；它也仍不负责从
  用户配置文件构造技术/业务 executor。HTTP、validate 和 trace 需要在其底层结果可查询后加入，不能用
  placeholder 冒充可用功能。成功运行
  在变为 `FINISHED` 前保存 typed checkpoint reference：materials-only output 只有材料 checkpoint，完整
  报告 output 才有材料、活动、过程和报告四项；`inspect`在关闭后重开这个小型 output manifest，而不是复制
  Markdown、模型包或另建恢复子系统。完整报告的 `render(runId)`可从 output manifest 定位唯一的 report
  checkpoint，fresh-reopen 后重新排版并核对 Markdown，返回 SHA 与字节长度；它仍不会把正文、来源或模型内容
  复制进 run manifest。`artifact(query)`按闭集业务输出名读取已存在 checkpoint 中的材料、活动、过程、报告、
  来源与验证文件；它拒绝路径、任意 artifact 浏览、模型原始输入/输出、未生成下游文件及超出正向字节预算的内容。

在开始任何产品模型调用前，公开 runtime 已支持一次显式的 **materials-only** 执行：它从已登记
源码运行既有 Step01/02 和 `BusinessMaterialBuilder`，只保存 `business-materials.jsonl`及其 checkpoint。
该 run 没有 activity、process 或 report checkpoint；因此 `render`、报告查询和其他下游 artifact 查询会
拒绝，而不是把“尚未分析”伪装成一份九章文档。materials-only 是可观察、零 Provider 的预检，用来核对
材料数量、短 ref、片段和 coverage 原因。需要模型时，调用方必须另行排队一份 final-document run，并明确
给出正的 `maxMaterialsToStart`；最终文档配置拒绝 0，避免“没有局部活动”仍继续启动过程或报告模型。
不复用或重放任何不确定的 started call。
- 旧 Step 06 R0/finite-key/process carrier 是当前实现事实，不是本文目标。
- 四个深 Module 已有一条受控实现纵切：`BusinessMaterialBuilder`优先从持久化的 Step 05
  `BusinessFlowsReference`生成材料；在 Step05 尚未发布而同一冻结运行已有 Step01 已验证源码和
  Step02 已发现入口时，它也能生成带 `FLOW_NOT_AVAILABLE` 限制的入口源码材料；`ActivityExplainer`以scripted Provider完成局部
  DRAFT+REVIEW，并以调用方提供的 `maxMaterialsToStart` 限制一次实际启动的材料数；超过该数的
  入口保留为可观察 coverage；`ProcessExplainer`完成分组、过程 DRAFT+REVIEW 和可选仓库总整理；
  `BusinessReportPublisher`完成整篇 DRAFT+REVIEW、九章 Markdown 与四个报告文件。
  `BusinessAnalysisWorkflow`把这四项按唯一顺序接通；`PersistedBusinessRunExecutor`可以以已经
  落盘的 Step 05 `BusinessFlowsReference`为优先输入，也可以以匹配的 Step01/Step02 publication
  为直接输入，并在两种情况下安装同样四层 checkpoint，且已把 bootstrap 选择的启动上限传入
  `ActivityExplainer`。定向 scripted 回归证明 direct-entry continuation 不重新扫描源码或重建技术图、Fact、Proof、Flow。
  `RepositoryAnalysisRunCoordinator`已将一个内部 `runId`执行按该唯一 handoff 连到报告：materials-only 预检只运行到
  Step01/Step02，并可使用 direct-entry 材料路线；正常最终报告运行 Step01–05，再把已持久化的
  `BusinessFlowsReference`交给业务模块。因此最终报告的材料优先来自 Flow/Capsule，而不是静默退化为入口后备材料。
  它不引入第二个 source/model 路径。它们只证明合成冻结 fixture 中的实现合同；固定 jshERP 的完整 Source publication、
  用户配置加载、整仓语义容量规划和整仓语义验收仍为 NOT RUN / NOT IMPLEMENTED。
- 两个固定 jshERP Luna/high 小包曾使用人工审阅的技术观察完成 DRAFT+REVIEW，说明模型和活动 Prompt
  能把清楚的代码材料写成业务语言；它们不是自动 Builder 的验收，也不是跨入口过程或九章文档的验收。
- DepotHead 八文件只复用既有说明和 fixture 身份；不能把它写成新的实测或全仓结果。

详细设计：

- [01 已验证源码清单](analysis-steps/01-verified-source-inventory.md)
- [02 应用发现](analysis-steps/02-application-discovery.md)
- [03 程序图](analysis-steps/03-program-graphs.md)
- [04 已证明代码事实](analysis-steps/04-proven-code-facts.md)
- [05 业务 Flow 技术切片](analysis-steps/05-business-flows.md)
- [06 业务材料与局部活动](analysis-steps/06-flow-interpretation.md)
- [07 跨活动过程与仓库知识](analysis-steps/07-repository-knowledge.md)
- [08 九章业务报告](analysis-steps/08-nine-section-document.md)
- [完整合成 walkthrough](examples/semantic-framework-walkthrough.md)
- [中文 Prompt 与审阅准则](references/semantic-interpretation-prompts.md)
