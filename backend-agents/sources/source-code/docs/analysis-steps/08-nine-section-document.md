# 分析步骤 08：Nine-section Document（ACTIVE 目标设计）

> 本步骤由一个深 Module——BusinessReportPublisher——完成。Luna/high 负责九章业务正文 DRAFT 与一次整体 REVIEW；程序验证简单 JSON、来源 allowlist、覆盖披露和九章顺序，再确定性组装 Markdown。旧 planner/renderer/trace/archive 四模块与固定五 payload/52-output 门槛退出业务核心路径。当前已实现 scripted Provider 的完整最小垂直链与四项检查点，并完成一次明确 opt-in 的合成九章 Luna/high DRAFT + REVIEW 质量验证；正式运行入口与整仓容量规划仍未实现。

## 1. 为什么存在

RepositoryBusinessKnowledge 已经包含业务目标、完整活动、过程、对象关系、条件、规则、公式、问题和覆盖，但直接交 JSON 会让业务读者读不出主线。把正文全部硬编码进 Java 模板又会重复早期问题：程序只能按字段拼套话，无法写出连贯、重点明确的业务章节。

BusinessReportPublisher 把“业务写作”集中交给 Luna/high，同时保留程序擅长的确定性职责：

- 九章名称、顺序和基数；
- 段落/list item JSON 结构；
- ref allowlist；
- entry/process coverage 是否如实披露；
- SourceRef 到冻结 file + lines + snippet 的默认可点击呈现；
- Markdown 标题、列表、间距和最终 bytes。

模型可以写自然正文，但不能写 Markdown styling、源码路径、行号、hash 或技术身份。

## 2. Interface、具体输入与完成点

~~~text
publish(PublishBusinessReportRequest) -> BusinessReportPublication
~~~

PublishBusinessReportRequest：

~~~json
{
  "runInputBindingId": "run-input:synthetic-v1",
  "knowledgeCheckpoint": "steps/07-repository-knowledge/repository-business-knowledge.json",
  "processCoverageCheckpoint": "steps/07-repository-knowledge/process-coverage.json",
  "sourceRefCheckpoint": "steps/06-flow-interpretation/business-materials.jsonl",
  "generationProfile": {
    "promptVersion": "business-report-v1",
    "moduleVersion": "business-report-publisher-v1",
    "maxInputChars": 48000,
    "maxResponseChars": 32000
  }
}
~~~

同一进程可直接传 typed RepositoryBusinessKnowledge 与 BusinessMaterialBuilder 生成的 SourceRef map。跨进程时 sourceRefCheckpoint 指向 Step 06 business-materials.jsonl；Publisher 只提取其中程序生成的 ref/file/lines/snippet 映射，并与 Step 07 知识中的 ref allowlist 交叉验证，不重新扫描源码。复用时，inputFingerprint 覆盖实际知识/coverage/ref 内容、实际 Prompt 内容及版本、有效模型与输出配置和 Module 版本，不包含新 runId。

完成检查点：

~~~text
steps/08-nine-section-document/
  business-report.json
  source-refs.jsonl
  document.md
  report-validation.json
~~~

这些文件可由现有 canonical publication 基础保存，但不要求为 Publisher 的每个内部动作再建 receipt/fresh-reopen 链，也不参与固定 52 项计数。

## 3. M1 BusinessReportPublisher

### 3.1 为什么是一个深 Module

写作、审阅、结构校验、refs 呈现和 Markdown 排版都是“把一份已审知识发布为业务报告”的内部复杂度。把它们拆成 planner/renderer/trace/archive 四个 public-ish Module，会让调用者学习中间状态、逐层 publication 和恢复规则，却没有增加业务能力。

Publisher 的小 Interface 一次返回可审阅业务 JSON、可读 Markdown、简单 refs 与验证结果。内部可分别测试 authoring、validation、rendering，但这些是 internal seams。

### 3.2 输入示例：必须包含报告实际需要的信息

输入不能只给 repositorySummary 或 process 名称。以下精简样例仍包含条件、步骤、结果、关系、公式、问题和来源：

~~~json
{
  "repositorySummary": {
    "text": "仓库围绕补货单、收货记录和应付账单定义了三个可串联活动。",
    "coverage": "COMPLETE_FOR_DISCOVERED_ENTRIES",
    "sourceRefs": ["S2", "S4", "S6"]
  },
  "businessGoals": [
    {"text":"形成并保存补货单","sourceRefs":["S1","S2","S3"]},
    {"text":"保存关联收货记录","sourceRefs":["S4","S5"]},
    {"text":"按收货数据形成并保存应付账单","sourceRefs":["S6","S7","S8"]}
  ],
  "activities": [
    {
      "activityId":"activity:create-replenishment",
      "name":"创建补货单",
      "triggerOrInput":["补货明细集合"],
      "conditions":["补货明细集合不能为空"],
      "activitySteps":["校验明细","生成补货单","保存补货单"],
      "codeDefinedResults":["系统生成并保存补货单"],
      "businessRules":["没有补货明细时不进入补货单生成"],
      "sourceRefs":["S1","S2","S3"]
    },
    {
      "activityId":"activity:record-receipt",
      "name":"记录收货",
      "triggerOrInput":["补货单标识","收货数量","单价"],
      "conditions":[],
      "activitySteps":["读取收货数据","生成收货记录","保存收货记录"],
      "codeDefinedResults":["系统生成并保存关联收货记录"],
      "businessRules":[],
      "sourceRefs":["S4","S5"]
    },
    {
      "activityId":"activity:create-payable-bill",
      "name":"创建应付账单",
      "triggerOrInput":["收货记录标识"],
      "conditions":[],
      "activitySteps":["读取收货记录","计算金额","生成并保存应付账单"],
      "codeDefinedResults":["系统计算金额并生成及保存应付账单"],
      "businessRules":[],
      "sourceRefs":["S6","S7","S8"]
    }
  ],
  "processes": [
    {
      "name":"补货到应付账单形成",
      "activityIds":["activity:create-replenishment","activity:record-receipt","activity:create-payable-bill"],
      "certainty":"REASONABLE_INFERENCE",
      "confirmationNotes":["源码承接支持该串联，但组织制度是否强制仍待确认"],
      "sourceRefs":["S2","S4","S5","S6","S8"]
    }
  ],
  "objectsAndRelations": [
    {"text":"收货记录用补货单标识关联补货单","sourceRefs":["S4","S5"]},
    {"text":"应付账单用收货记录标识关联收货记录","sourceRefs":["S6","S8"]}
  ],
  "formulasOrMetrics": [
    {"text":"应付金额 = 收货数量 × 单价","sourceRefs":["S7"]}
  ],
  "confirmationTopics": [
    "哪些岗位或系统执行三个活动？",
    "币种、舍入、审核、过账和付款如何处理？"
  ],
  "coverage": {
    "discoveredEntryCount":3,
    "analyzedEntryIds":["entry:create-replenishment","entry:record-receipt","entry:create-payable-bill"],
    "notAnalyzed":[],
    "notConsolidatedGroups":[],
    "repositorySummaryCoverage":"COMPLETE_FOR_DISCOVERED_ENTRIES",
    "semanticDeliveryStatus":"READY_FOR_REPORT"
  }
}
~~~

模型安全包把 SourceRef map 投影为 allowlistedRefs=["S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"]，不传 file、line、hash、Proof 或 Provider controls。活动会保留已审阅的`terms`与`certainty`；过程阶段把内部`activityId`投影为相应的已审阅活动名称，例如`{"order":1,"activity":"创建补货单","description":"生成并保存补货单"}`。因此模型能读到“哪项业务活动构成哪个阶段”，却看不到技术内部 ID。

### 3.3 程序动作

1. 验证 knowledge、coverage 和 SourceRef map 的 inputFingerprint/run input 一致。
2. 做报告输入/输出容量 preflight；不能容纳完整关键知识时不截断调用。
3. 生成 Business Report DRAFT Prompt，将 knowledge 当不可信数据隔离。
4. 发起一次 DRAFT，保存 started request/response 诊断。
5. 解析完整九章 JSON，拒绝 Markdown styling、未知 ref、路径、行号、hash、Proof 和技术身份字段。
6. 把完整 knowledge 与完整实际 draft 交给一次 REVIEW；接收完整替换 JSON，不接收 patch/decision table。
7. 重新验证恰好九章、固定标题/顺序、文本非空、refs allowlisted，并验证结构化 coverage 字段与输入机器覆盖一致；程序不判断中文是否蕴含某条来源。
8. 从程序侧 SourceRef map 生成 source-refs.jsonl。
9. 确定性渲染 document.md：H1、九个 H2、段落、列表、ref 链接以及第 1 章内的可折叠技术依据。
10. 重读最终 bytes，生成 report-validation.json；不做第三次模型补修。

程序不评判每个自然句是否被形式 Proof 逐字蕴含，也不写规则识别“某次成功”或判断指标中文是否真的来自输入。Publisher 由 renderer 在第 1 章固定插入“本文描述静态源码定义行为，不代表某次运行成功”的范围声明；正文其余语义质量由整篇 Luna REVIEW 与后续真实小样本人工审阅负责。程序只检查 schema、allowlisted refs、九章顺序和机器 coverage。

两次报告调用各自携带相同的完整 JSON Schema：它要求 title、恰好九个 section、固定标题集合，以及 paragraph/item 的完整 text 与 refs 结构；refs 只能选择本次知识输入已有的短引用，文本和列表服从当前 profile 的容量。这样模型在生成前就知道最终可保存的形状；Java 仍重新检查九章、引用和 coverage，不把 Schema 遵守误当成业务语义已经正确。

### 3.4 Luna/high 动作

DRAFT 是 business chapter author：

- 将完整知识写成连贯、面向业务读者的九章正文；
- 使用中文业务词为主，技术字段只作为括号内依据；
- 业务活动以完整活动和过程组织，不按类/方法清单堆砌；
- 清楚代码中的 construct/save/insert 可写“系统生成并保存”；
- 某次运行成功、岗位、制度、唯一性、库存/记账/付款结果没有来源时集中放第 9 章；
- 第 7 章只使用实际公式/口径，没有则明确未识别到可定义指标；
- 每个事实段落或条目引用 allowlisted ref。

REVIEW 以整份九章为单位检查遗漏、误写、业务语言、来源、覆盖和章节归属，返回完整修订 JSON。模型不输出 Markdown headings/bullets，也不生成 ref reverse binding。

### 3.5 输出示例：完整九章结构 JSON

~~~json
{
  "title": "合成补货仓库业务说明",
  "sections": [
    {"number":1,"title":"文档说明","paragraphs":[{"text":"本文描述冻结源码定义的系统行为，不代表某次生产运行已经成功；三个发现入口均已分析，跨入口顺序仍需制度确认。","refs":[]}],"items":[]},
    {"number":2,"title":"业务目标","paragraphs":[{"text":"系统支持形成补货单、记录关联收货并依据收货数据形成应付账单。","refs":["S1","S2","S3","S4","S5","S6","S7","S8"]}],"items":[]},
    {"number":3,"title":"业务对象","paragraphs":[{"text":"主要对象是补货单、补货明细、收货记录和应付账单。","refs":["S2","S4","S5","S6","S8"]}],"items":[]},
    {"number":4,"title":"业务活动","paragraphs":[{"text":"系统校验补货明细后生成并保存补货单，再可按补货单记录收货，并按收货数据计算金额及保存应付账单。该串联有标识承接支持，但制度顺序待确认。","refs":["S1","S2","S3","S4","S5","S6","S7","S8"]}],"items":[]},
    {"number":5,"title":"字段与维度","paragraphs":[{"text":"业务数据包括补货明细、补货单编号、收货数量、单价、收货记录编号和账单金额。","refs":["S1","S4","S5","S6","S8"]}],"items":[]},
    {"number":6,"title":"对象关系","paragraphs":[{"text":"收货记录关联补货单，应付账单关联收货记录；源码未定义这些关系的业务唯一性。","refs":["S4","S5","S6","S8"]}],"items":[]},
    {"number":7,"title":"指标口径","paragraphs":[{"text":"本例按单条收货记录计算账单金额：应付金额 = 收货数量 × 单价；未定义按期间汇总、税费、币种或舍入。","refs":["S7"]}],"items":[]},
    {"number":8,"title":"示例问题","paragraphs":[],"items":[{"text":"没有补货明细时系统是否生成补货单？","refs":["S1"]},{"text":"应付金额怎样计算？","refs":["S7"]}]},
    {"number":9,"title":"待确认事项","paragraphs":[],"items":[{"text":"需要确认执行岗位、制度顺序、唯一性、币种、舍入、审核、过账和付款。","refs":[]},{"text":"静态源码不证明某次保存成功或成功次数。","refs":[]}]}
  ]
}
~~~

完整、逐段来源可见的版本见 [端到端 walkthrough](../examples/semantic-framework-walkthrough.md)。

### 3.6 程序确定性 Markdown 与默认 refs

Renderer 只接受已验证 BusinessReport JSON。它固定：

~~~text
# {title}
## 1. 文档说明
{paragraphs/items with [S1](#source-ref-s1)}
{program-generated collapsible technical basis}
## 2. 业务目标
...
## 9. 待确认事项
~~~

上面的三点只表示此处省略排版模板重复行，不是输出样例；实际 renderer 必须产生全部九章，walkthrough 已给出完整 document.md。

HTTP/前端源码查看器尚未实现，因此默认不能只生成指向未来 UI 的死链接。程序把 source-refs.jsonl 的内容插入第 1 章末尾，不增加第 10 个 H2：

~~~markdown
<details>
<summary>技术依据（可选）</summary>

<a id="source-ref-s1"></a>
- S1 — src/main/java/example/ReplenishmentService.java:21–23
  <pre><code>if (command.lines().isEmpty()) {
      throw new IllegalArgumentException("lines required");
  }</code></pre>

</details>
~~~

正文 [S1](#source-ref-s1) 因而在同一 document.md 内可点击。每个条目含 repository-relative file、行范围和短 snippet；host absolute path 不进入正文。未来本地/HTTP source viewer 可把相同 ref 增强成外部打开动作，但不是业务质量或发布前置。

这个技术依据区域由程序从 ref map 排版，不由模型生成。它属于第 1 章内容，不改变“恰好九个 H2”。

### 3.7 report-validation.json

~~~json
{
  "status": "VALID",
  "sectionCount": 9,
  "sectionOrderValid": true,
  "sourceRefsValid": true,
  "sourceAnchorsEmbedded": true,
  "coverageDisclosureValid": true,
  "inputKnowledgeHasFormula": true,
  "sectionSevenHasAllowlistedRefs": true,
  "runtimeBoundaryNoticeInsertedByRenderer": true,
  "semanticDeliveryStatus": "READY_FOR_REPORT"
}
~~~

inputKnowledgeHasFormula 只表示结构化输入存在公式，sectionSevenHasAllowlistedRefs 只表示该章引用合法；二者都不是对中文蕴含关系的证明。这里没有自然语言判定器、逐句 Proof verdict、reader item ledger 或 52-output inventory。

### 3.8 下游直接使用

BusinessReportPublication 提供：

- business-report.json：人工审阅或经明确授权创建 ReaderCandidateRound 2 时的基线；
- document.md：业务读者直接阅读；
- source-refs.jsonl：程序重渲染、技术查看和 ref 验证；
- report-validation.json：候选选择/冻结前确认结构与来源。

共享候选 selection/freeze/package 仍是 Source Agent 外的显式动作。Publisher 不自动选择、冻结、部署或覆盖旧 Candidate。

### 3.9 Gap、fatal 与停止

可以形成带限制报告：

- 少量 entry 为 ANALYZED_WITH_GAPS，且原因和影响在第 1/9 章出现；
- 岗位、制度、跨系统效果、币种/舍入未知；
- 没有公式：第 7 章明确“本次未从源码识别到可定义指标”；
- 0 entry：可生成范围说明九章，但 semanticDeliveryStatus=INCOMPLETE，不能称完成业务分析。

不能发布伪成功：

- knowledge/coverage/ref 的 fingerprint 不匹配；
- 报告输入无法完整容纳关键知识，却静默截断；
- DRAFT/REVIEW 非法 JSON、未知 ref 或生成路径/行号/hash；
- sections 不是恰好 1..9 或标题/顺序不符；
- notAnalyzed/notConsolidatedGroups 未披露却称 COMPLETE；
- started Provider request 失败。

模型新增无来源公式、币种、聚合/KPI，或把静态行为写成某次运行成功，是 Luna REVIEW 与真实样本人工审阅必须拦截的内容质量 fatal；Java 不靠关键词识别它们。review 后结构仍非法，或人工评审发现这些 fatal，都停止候选且不自动发第三个请求。上游知识和安全诊断保留，可在新的显式 execution 中按相同 fingerprint 复用；不 replay 不确定 started call。

### 3.10 调用、编辑、观察与重渲染

~~~text
capacity PASS -> one report DRAFT + one full-report REVIEW
capacity FAIL -> zero Provider requests
~~~

- author/edit：显式产品生成动作，需当次授权并受 ReaderCandidateRound 上限约束；
- review：authoring execution 内唯一一次内容审阅；
- render/rerender：只读已验证 JSON + refs，0 model；
- inspect/artifact/validate/trace：只读，0 model。

内部 DRAFT/REVIEW 不创建额外产品 candidate。一份冻结来源最多仍有 Round 1 与针对明确问题另行授权的 Round 2；无自动 retry、fallback、Provider switch 或无限修订。

### 3.11 开发与测试

测试只跨 BusinessReportPublisher Interface：

1. scripted DRAFT/REVIEW 收到完整知识并各调用一次。
2. 输出恰好九章、固定标题和顺序。
3. 模型输出没有 Markdown styling，renderer bytes 确定。
4. unknown ref、路径/行号/hash 注入失败。
5. source-refs 在第 1 章生成有效同文档锚点，不增加第 10 个 H2。
6. scripted REVIEW 收到“清楚保存动作可写业务行为、不得冒充某次运行成功”的完整要求并返回修订正文；程序只保留审阅结果，不实现业务关键词识别。
7. scripted REVIEW 在有公式/无公式两个 fixture 中保留正确第 7 章；程序只验证章节与 allowlisted refs，不判断中文公式蕴含。
8. 业务章节使用中文业务名，技术字段只是辅助。
9. notAnalyzed/notConsolidatedGroups 必须披露。
10. rerender/inspect/validate 为 0 Provider。
11. preflight 超预算为 0 started request；started failure 无伪 document.md。

真实 Luna/high 质量验证按小型核心样本、不同领域样本、整仓推进。当前合成“补货→收货→应付账单”样本已完整生成九章：第 4 章用业务活动列表表达过程，第 7 章只保留已有金额公式，第 9 章集中列出顺序与运行效果的不确定性。该结果只证明报告 Prompt、结构校验、ref 渲染和整篇 REVIEW 能共同产生业务语言，不代表客户整仓结果。HTTP 和第二产品 Provider adapter 不阻塞。

## 4. 旧 Step 08 Module 的去向

| 旧名 | 新路线 |
| --- | --- |
| NineSectionPlanner | 九章映射成为 BusinessReportPublisher 内部 Prompt/input projection |
| NineSectionRenderer | 确定性排版成为 Publisher internal seam |
| ReaderTraceCompiler | 简化为 source-refs.jsonl + 同文档 anchors |
| NineSectionDocumentPublisher/archive module | 简单四检查点写入成为 Publisher 内部实现 |

既有 candidate/archive 技术能力可在 Publisher 验证成功后复用，不删除、不降级；旧五 payload 和 52-output 数量不再决定业务核心完成。迁移不建立兼容 alias、dual writer、第二 public Interface 或旁路 POC。

## 5. 当前实现成熟度

- 已实现 M1 in-memory 垂直链：`BusinessReportPublisher` 接收完整 reviewed activities、
  processes、coverage/confirmation topics 与程序侧 short `SourceReference` map；scripted
  Provider 执行一次 `BUSINESS_REPORT_DRAFT` 加一次完整 `BUSINESS_REPORT_REVIEW`。REVIEW
  阅读实际 DRAFT，最终只保留完整审阅结果。
- 已实现：程序严格检查九章固定标题/顺序、段落与条目结构、每个短 ref 的 allowlist；未知 ref
  fail closed。Renderer 确定性生成九个 H2，并在第一章追加同文档的可点击 source snippet
  anchors。模型输入没有 file、line、hash、Proof、Flow 或 run identity。
- 已实现：当输入来自已持久化 Step 07 knowledge checkpoint 时，Publisher 安装并 fresh reopen
  `business-report.json`、`source-refs.jsonl`、`document.md` 和
  `report-validation.json`。Markdown 是受严格 UTF-8/no-BOM/content-ID 校验的 RAW_UTF8
  artifact，不把正文塞回 JSON 伪装成报告。
- 已实现：`BusinessReportCheckpointReader`从同一四文件 checkpoint 重建完整 typed
  `BusinessReportPublication`，重新检查模块地址、文件集合、schema/type、九章、短来源、validation
  和 strict UTF-8 Markdown；这一路径不读源码、不重写 Markdown、也不调用 Provider，供后续
  inspect/render Adapter 复用已有报告。
- 已实现：保存后的 typed `BusinessReport`与短来源可经独立的确定性 renderer 再生成 Markdown；
  此行为不读取已保存 Markdown 字段、不读源码、也不调用 Provider。checkpoint 测试会故意替换
  内存中保存的正文，再断言重渲染仍逐字节回到已审阅文档，防止未来 render Adapter 把“返回旧文本”
  误当成“重新渲染”。
- 已实现：`BusinessAnalysisWorkflow` 从一个已持久化 Step 05 `BusinessFlowsReference` 依次接通
  materials、reviewed activities、repository knowledge 与本 Publisher，并返回四层 typed result；
  该 internal composition 是未来 Runtime/CLI 的唯一业务接线，避免 CLI 或测试重新拼装四模块。
- 已实现：`PersistedBusinessRunExecutor`以该 Step 05 publication 作为唯一业务输入，安装四层
  checkpoint 并生成一份 nine-section report；scripted runtime test 证明它没有回头扫描源码或重建
  技术结论。
- 已实现：`RepositoryAnalysisRunCoordinator`将一个内部 runId 的技术执行结果唯一地交给上述
  continuation；它只转交已经持久化的 Flow publication，不能额外读取源码或创建另一套模型调用路径。
- 已实现：public `RepositoryAnalysisAgent.executeStep`在唯一 final-document target 成功后保存四个
  typed business checkpoint reference，再从 `RUNNING` 转为 `FINISHED`；`inspect`只在 finished run
  上重开这个 output manifest。它不会复制 report 内容，也不会重放任一 Provider 调用。
- 已实现：public `render(runId)`只将该 finished run 的 report checkpoint 交给 read-only adapter；
  adapter fresh-reopen 四个报告文件、确定性 rerender 并核对保存的 Markdown，再返回 document SHA 与
  byte length。此操作不读源码、不调用 Provider、也不修改 run。
- 已实现：public `artifact(query)`以 closed business-output key 在已完成 run 的既有四个 checkpoint
  中 fresh-reopen 材料、活动、过程、报告、source-ref 或 validation 文件。它要求正向完整字节预算，
  不截断、不收路径、不开放 prompt/raw model response。
- 已实现：最小 CLI 可通过同一 `RepositoryAnalysisAgent`对已登记 sourceRegistrationId 排队、对已有 run
  执行最终目标和 inspect；它不读源码路径、不传 Provider 配置。render/artifact 命令已映射，但当前 composition
  root 尚未装配它们的只读依赖。尚未实现：用户配置加载、capture command、完整整仓输入下的容量规划与真实整仓
  Luna/high author/review。已完成的合成小包 author/review 不能替代这些出口。
  旧 planner/renderer/trace/archive 目标与当前骨架不能冒充本设计。
- walkthrough 是完整合成推演，不是 jshERP 或 DepotHead 当前产物。

Prompt 细则见 [业务解释与九章写作中文 Prompt](../references/semantic-interpretation-prompts.md)。
