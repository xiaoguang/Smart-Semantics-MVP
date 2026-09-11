# 分析步骤 07：Repository Knowledge（ACTIVE 目标设计）

> 本步骤由一个深 Module——ProcessExplainer——完成“程序宽松召回 + Luna/high 跨入口业务串联 + 一份仓库知识”。旧 admission/merge/publisher 三模块和 bridge/reconciliation 多账本退出目标路线。当前已完成 scripted Provider 的最小垂直链：共享活动材料的宽松分组、每组一次 DRAFT + 一次完整 REVIEW、可选且有界的一次仓库总整理 DRAFT + REVIEW、短 ref/成员/结构校验，以及三份可重开检查点。新进程可从三份 checkpoint 重建同一份 typed `RepositoryBusinessKnowledge`，不会重放模型；另有一次明确 opt-in 的合成三活动 Luna/high P1/P2 质量验证。正式整仓运行编排仍未实现。

## 1. 为什么存在

Step 06 给出完整局部活动，却不能只靠一个入口说明端到端业务。补货、收货和账单可能由不同 HTTP 入口触发；同一个活动也可能参与多个过程。程序图和相同字段能帮助找到“值得一起读”的活动，但不能证明业务顺序、对象同一性或组织制度。

ProcessExplainer 将两类工作收进同一 Module：

- 程序做高召回、可复现、带容量上限的候选分组；
- Luna/high 阅读完整已审活动，提出并审阅过程与仓库级解释；
- 程序校验 refs、成员、覆盖和简单 JSON，保存一份完整仓库知识；
- 未分析入口、未整理 group、未知岗位/制度和有损压缩显式留下。

它不要求 Java 从名字批准 process，不要求每个关系有逐原子 Proof，也不建立第二套过程 POC。

## 2. Interface、具体输入与完成点

~~~text
explain(ExplainRepositoryProcessesRequest) -> RepositoryBusinessKnowledge
~~~

Java 的最小 typed seam 固定如下。生产调用方提交已审活动和已经保存的业务材料，获得完整仓库知识；
group recall、DRAFT/REVIEW、跨组整理和检查点均属于 Module 内部实现：

~~~java
public final class ProcessExplainer {
    RepositoryBusinessKnowledge explain(ExplainRepositoryProcessesRequest request);
}

public record ExplainRepositoryProcessesRequest(
    ActivityExplanationResult activities,
    BusinessMaterialSet materials,
    ProcessExplanationProfile profile) {}

public record ProcessExplanationProfile(
    int maxActivitiesPerGroup,
    int maxProcessGroups,
    int maxModelInputBytes,
    int maxModelOutputBytes,
    int maxProcessesPerGroup,
    int maxValuesPerField,
    int maxTextCharsPerValue) {}

public record RepositoryBusinessKnowledge(
    List<ReviewedActivity> activities,
    List<BusinessProcess> processes,
    List<ActivityEntryCoverage> activityCoverage,
    List<String> unmatchedActivityIds,
    List<String> confirmationTopics,
    ModulePublicationReference checkpoint) {}
~~~

`BusinessProcess` 保留 `processId`、`name`、`businessPurpose`、`activityIds`、有序
`stages`、`branches`、`sharedObjects`、`codeDefinedResults`、`certainty`、`sourceRefs` 与
`confirmationNotes`。`stage` 仅是模型审阅后过程中的业务说明，不是 Java 对调用顺序的证明。
调用者不得传源码路径、Proof、Flow、artifact/run identity 或预先批准的 process order。

输入：

~~~json
{
  "runInputBindingId": "run-input:synthetic-v1",
  "businessMaterialCheckpoint": "steps/06-flow-interpretation/business-materials.jsonl",
  "activityCheckpoint": "steps/06-flow-interpretation/activity-explanations.jsonl",
  "activityCoverageCheckpoint": "steps/06-flow-interpretation/activity-coverage.json",
  "processProfile": {
    "maxActivitiesPerGroup": 12,
    "maxProcessGroups": 80,
    "maxRepositorySummaryItems": 120
  },
  "generationProfile": {
    "promptVersion": "business-process-v1",
    "moduleVersion": "process-explainer-v1",
    "maxGroupResponseChars": 20000,
    "maxRepositoryResponseChars": 24000
  }
}
~~~

ProcessExplainer 可直接接收 Step 06 的 materialSet、reviewedActivitySet 与 activityCoverage typed result，无须为每个 group 重开文件；跨进程时读取上述三个 simple checkpoints。M1 可以使用 material 的同一局部 Flow、技术证据或冻结源码文件作为**不透明**召回 cue；它在模型包中最多表述为“技术材料存在共同来源”，不会传递路径、行号或哈希，也不把 cue 当作业务关系。每个 group REVIEW 完成就保存对应 business-processes.jsonl 记录并更新 process-coverage.json，后续 group/总整理失败不删除它。M1 计算一个 inputFingerprint，覆盖实际 materials/activities/coverage、实际 grouping、实际 Prompt 内容及版本、有效模型与输出配置和 Module 版本，但不包含新 runId。

成功检查点：

~~~text
steps/07-repository-knowledge/
  business-processes.jsonl
  repository-business-knowledge.json
  process-coverage.json
~~~

repository-business-knowledge.json 是 Step 08 唯一业务知识输入。它必须保留完整活动、条件、步骤、结果、规则、公式、问题与 refs；repositorySummary 只是导航。

## 3. M1 ProcessExplainer

### 3.1 为什么是一个深 Module

候选召回和过程解释彼此需要，但不应成为两个要求调用者理解 bridge、relation owner、reconciliation 状态与多层 publication 的浅 Module。调用者只提交完整 Step 06 结果并得到仓库知识；召回 cue、重叠分组、模型包、DRAFT/REVIEW、跨组汇总和校验都留在实现内。

Provider seam 仍可替换：产品使用经授权的 Codex Subscription Luna/high adapter，自动化测试使用 scripted adapter。没有必要为了一个实现再增加第三个 Provider adapter。

### 3.2 输入示例：不能只传 name/summary

每个 ReviewedActivity 必须保留报告需要的内容：

~~~json
{
  "activities": [
    {
      "activityId": "activity:create-replenishment",
      "entryIds": ["entry:create-replenishment"],
      "name": "创建补货单",
      "businessPurpose": "把补货明细形成并保存为补货单。",
      "participants": [],
      "businessObjects": ["补货单", "补货明细"],
      "triggerOrInput": ["补货明细集合"],
      "conditions": ["补货明细集合不能为空"],
      "activitySteps": ["校验明细", "生成补货单", "保存补货单"],
      "codeDefinedResults": ["系统生成并保存补货单"],
      "businessRules": ["没有补货明细时不进入补货单生成"],
      "formulasOrMetrics": [],
      "sourceRefs": ["S1", "S2", "S3"],
      "questions": ["哪类岗位或系统有权发起补货？"],
      "scopeLimitations": ["静态源码不证明某次保存成功"]
    },
    {
      "activityId": "activity:record-receipt",
      "entryIds": ["entry:record-receipt"],
      "name": "记录收货",
      "businessPurpose": "保存与补货单关联的收货记录。",
      "participants": [],
      "businessObjects": ["补货单", "收货记录"],
      "triggerOrInput": ["补货单标识", "收货数量", "单价"],
      "conditions": [],
      "activitySteps": ["读取收货数据", "生成收货记录", "保存收货记录"],
      "codeDefinedResults": ["系统生成并保存收货记录"],
      "businessRules": [],
      "formulasOrMetrics": [],
      "sourceRefs": ["S4", "S5"],
      "questions": ["收货前是否要求补货单处于特定状态？"],
      "scopeLimitations": ["记录写入不证明物理货物在某次运行中实际到达"]
    },
    {
      "activityId": "activity:create-payable-bill",
      "entryIds": ["entry:create-payable-bill"],
      "name": "创建应付账单",
      "businessPurpose": "按收货记录形成并保存应付账单。",
      "participants": [],
      "businessObjects": ["收货记录", "应付账单"],
      "triggerOrInput": ["收货记录标识"],
      "conditions": [],
      "activitySteps": ["读取收货记录", "计算应付金额", "生成并保存应付账单"],
      "codeDefinedResults": ["系统计算金额并生成及保存应付账单"],
      "businessRules": [],
      "formulasOrMetrics": ["应付金额 = 收货数量 × 单价"],
      "sourceRefs": ["S6", "S7", "S8"],
      "questions": ["账单保存后是否另有审核、过账和付款？"],
      "scopeLimitations": ["账单写入不证明某次运行已完成过账或付款"]
    }
  ],
  "activityCoverage": {
    "discoveredEntryCount": 3,
    "analyzedEntryIds": [
      "entry:create-replenishment",
      "entry:record-receipt",
      "entry:create-payable-bill"
    ],
    "analyzedWithGaps": [],
    "notAnalyzed": []
  }
}
~~~

### 3.3 程序动作：宽松召回，不批准业务

程序按确定性 activityId 顺序，从 Step 06 传入的 activity 和 material observations 建立 recall cues：

1. 已审活动中的共同 business object、term 或输入；
2. 同一已编译局部 Flow；
3. 同一技术证据；
4. 同一冻结源码文件。

cue 只决定“哪些活动一起交给模型看”。它不写 stage order，不自动 merge，不从相邻入口宣布因果。相同名称、不同名称、相同字段、相同 table 都只能增加 recall 分值。若调用者不提供 materials，M1 只用已审活动中的对象、输入和术语召回；它不会暗中重新扫描源码、重开 Step 03–05 或发起 lookup。

程序把活动分成有界、可重叠 group：

- 每组不超过 maxActivitiesPerGroup；
- 一个 activity 可以进入多个 group；
- 每个 activity 至少属于一个 group，或写 unmatched reason；
- group 超过 maxProcessGroups 时，未入组活动进入 process-coverage，不能静默丢失；
- 每包携完整 activity 业务字段和允许的短 refs，不携 path/line/hash/Proof/provider/control。

### 3.4 Luna/high 动作：完整过程 DRAFT + REVIEW

对每个通过 preflight 的 group：

1. DRAFT 提出零个或多个 BusinessProcess，包括 purpose、activities、stages/branches、shared objects、code-defined results、sourceRefs 与 confirmationNotes。
2. REVIEW 阅读同一 group 的完整 activities 和完整实际 draft，返回完整修订过程 JSON。
3. 一个 activity 可属于多个 process；相同/不同名字都不自动合并。
4. 跨入口顺序可写 REASONABLE_INFERENCE，但在过程段落集中说明制度是否强制仍待确认。
5. 不编造岗位、唯一采购单、库存运行结果、记账/付款完成、次数或无来源指标。

每次调用同时带随当前 group 生成的严格 JSON Schema：它固定完整字段、活动 ID 与短引用的可选范围、certainty 枚举和当前 profile 的文本/列表容量。可选的全仓 summary 使用自己的五字段 Schema，而不复用 group Schema。Schema 先阻止可预测的结构错误；Java 收到响应后仍按同一范围重新验证，不能把模型输出当作可信输入。

这是 whole process review，不是为每个 transition 返回 KEEP/NARROW/DROP/PENDING。

### 3.5 Group 输出示例

~~~json
{
  "processId": "process:replenishment-to-payable-bill",
  "groupId": "group:replenishment-receipt-bill",
  "name": "补货到应付账单形成",
  "businessPurpose": "从补货明细形成补货单，在记录关联收货后，按收货数据形成应付账单。",
  "activityIds": [
    "activity:create-replenishment",
    "activity:record-receipt",
    "activity:create-payable-bill"
  ],
  "stages": [
    {"order":1,"activityId":"activity:create-replenishment","description":"生成并保存补货单"},
    {"order":2,"activityId":"activity:record-receipt","description":"按补货单标识生成并保存收货记录"},
    {"order":3,"activityId":"activity:create-payable-bill","description":"按收货记录计算金额并生成及保存应付账单"}
  ],
  "branches": [
    "补货明细为空时，创建补货单活动在生成对象前停止"
  ],
  "sharedObjects": ["补货单", "收货记录", "应付账单"],
  "codeDefinedResults": [
    "系统可形成补货单、关联收货记录和关联应付账单",
    "应付金额按收货数量乘以单价计算"
  ],
  "certainty": "REASONABLE_INFERENCE",
  "sourceRefs": ["S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"],
  "confirmationNotes": [
    "标识承接支持该串联，但源码不能说明组织制度是否强制三个入口依次执行",
    "源码没有说明岗位、币种、舍入、审核、过账或付款"
  ]
}
~~~

程序验证 activityIds 都属于输入、sourceRefs 都在 allowlist、stage activity 都是成员、order 不重复，并分配稳定 processId。它不评判自由正文是否逐字被 Proof 蕴含。

### 3.6 仓库级有界总整理

Group REVIEW 后，程序创建短 process summary，但仍把报告需要的完整活动/过程集合保留在 RepositoryBusinessKnowledge 中。仓库总整理输入只含：

- 每个 process 的短 summary、purpose、activityIds、certainty 和 refs；
- 有界范围内完整条件、规则、公式与 questions；
- unmatched activities；
- name/object conflict candidates；
- process coverage。

若输入不超过 maxRepositorySummaryItems，Luna/high 做一次 repository DRAFT + 一次完整 REVIEW，形成 repositorySummary、crossGroupRelations、objectsAndTerms 和 confirmationTopics。它不重新读整仓源码。

若超过上限，程序不静默选取前 N 项再声称完成。它：

- 不发 repository summary 请求；
- 保留所有已审 group process；
- 列出 notConsolidatedGroups 与受影响 activityIds；
- 把 repositorySummaryCoverage 设为 PARTIAL；
- 阻止 semanticDeliveryStatus=READY_FOR_REPORT_COMPLETE。

这是一种明确有损边界，不是多层 reconciliation 系统。

### 3.7 RepositoryBusinessKnowledge 输出示例

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
      "businessPurpose":"把补货明细形成并保存为补货单。",
      "triggerOrInput":["补货明细集合"],
      "conditions":["补货明细集合不能为空"],
      "activitySteps":["校验明细","生成补货单","保存补货单"],
      "codeDefinedResults":["系统生成并保存补货单"],
      "businessRules":["没有补货明细时不进入补货单生成"],
      "formulasOrMetrics":[],
      "sourceRefs":["S1","S2","S3"],
      "questions":["哪类岗位或系统有权发起补货？"]
    },
    {
      "activityId":"activity:record-receipt",
      "name":"记录收货",
      "businessPurpose":"保存与补货单关联的收货记录。",
      "triggerOrInput":["补货单标识","收货数量","单价"],
      "conditions":[],
      "activitySteps":["读取收货数据","生成收货记录","保存收货记录"],
      "codeDefinedResults":["系统生成并保存收货记录"],
      "businessRules":[],
      "formulasOrMetrics":[],
      "sourceRefs":["S4","S5"],
      "questions":["收货前是否要求补货单处于特定状态？"]
    },
    {
      "activityId":"activity:create-payable-bill",
      "name":"创建应付账单",
      "businessPurpose":"按收货记录形成并保存应付账单。",
      "triggerOrInput":["收货记录标识"],
      "conditions":[],
      "activitySteps":["读取收货记录","计算金额","生成并保存应付账单"],
      "codeDefinedResults":["系统计算金额并生成及保存应付账单"],
      "businessRules":[],
      "formulasOrMetrics":["应付金额 = 收货数量 × 单价"],
      "sourceRefs":["S6","S7","S8"],
      "questions":["账单保存后是否另有审核、过账和付款？"]
    }
  ],
  "processes": [
    {
      "processId":"process:replenishment-to-payable-bill",
      "name":"补货到应付账单形成",
      "businessPurpose":"从补货明细形成补货单，在记录关联收货后形成应付账单。",
      "activityIds":["activity:create-replenishment","activity:record-receipt","activity:create-payable-bill"],
      "stages":[
        {"order":1,"activityId":"activity:create-replenishment","description":"生成并保存补货单"},
        {"order":2,"activityId":"activity:record-receipt","description":"生成并保存关联收货记录"},
        {"order":3,"activityId":"activity:create-payable-bill","description":"计算金额并生成及保存应付账单"}
      ],
      "certainty":"REASONABLE_INFERENCE",
      "sourceRefs":["S1","S2","S3","S4","S5","S6","S7","S8"],
      "confirmationNotes":["组织制度是否强制三个入口依次执行仍待确认"]
    }
  ],
  "manyToManyMemberships": [
    {"activityId":"activity:create-replenishment","processIds":["process:replenishment-to-payable-bill"]},
    {"activityId":"activity:record-receipt","processIds":["process:replenishment-to-payable-bill"]},
    {"activityId":"activity:create-payable-bill","processIds":["process:replenishment-to-payable-bill"]}
  ],
  "objectsAndRelations": [
    {"text":"收货记录用补货单标识关联补货单","sourceRefs":["S4","S5"]},
    {"text":"应付账单用收货记录标识关联收货记录","sourceRefs":["S6","S8"]}
  ],
  "formulasOrMetrics": [
    {"text":"应付金额 = 收货数量 × 单价","sourceRefs":["S7"]}
  ],
  "confirmationTopics": [
    "哪些岗位或系统执行这些活动？",
    "组织制度是否强制三个活动依次发生？",
    "币种、金额舍入、审核、过账和付款如何处理？"
  ],
  "coverage": {
    "discoveredEntryCount":3,
    "analyzedEntryIds":["entry:create-replenishment","entry:record-receipt","entry:create-payable-bill"],
    "notAnalyzed":[],
    "processGroupCount":1,
    "unmatchedActivityIds":[],
    "notConsolidatedGroups":[],
    "repositorySummaryCoverage":"COMPLETE_FOR_DISCOVERED_ENTRIES",
    "semanticDeliveryStatus":"READY_FOR_REPORT"
  }
}
~~~

完整 walkthrough 中的对象、限制和字段更多；这里的例子仍展示了 Step 08 必需的条件、步骤、规则、公式、过程与 refs，没有只保留名称摘要。

### 3.8 下游直接使用

| Step 07 字段 | Step 08 用途 |
| --- | --- |
| repositorySummary + coverage | 第 1 章范围与整仓完整性 |
| businessGoals | 第 2 章 |
| activities 的对象/目的/条件/步骤/结果 | 第 3、4 章 |
| triggerOrInput 与已审字段说明 | 第 5 章 |
| processes + objectsAndRelations | 第 4、6 章 |
| formulasOrMetrics | 第 7 章，禁止另造指标 |
| conditions/rules/relations/formulas | 第 8 章示例问题 |
| questions/confirmationNotes/notAnalyzed | 第 9 章 |
| sourceRefs | 每个事实段落的可点击技术依据 |

Step 08 不重新读取源码或靠名称补字段。

### 3.9 Gap、fatal 与停止

可继续交付但要显式呈现：

- 一个 activity 未找到过程：保留为 standalone activity，不是失败；
- 同名/异名 relation 未能确认：保留 conflict/confirmation topic；
- 同一 activity 属于多个 process：写多对多 membership；
- 岗位、制度、顺序强制性、外部效果未知：集中 confirmationNotes；
- repository summary 超预算：保留 group knowledge，标 PARTIAL。

必须停止或阻止完整交付：

- Step 06 inputFingerprint/run binding 不匹配；
- activityCoverage 遗漏发现入口；
- group DRAFT/REVIEW 引用未知 activity/ref 或非法 JSON；
- started Provider 调用失败；
- group/summary 被截断却标 COMPLETE；
- 只有名称相似就自动 merge；
- 需要编造岗位、唯一性、记账、付款、成功次数或公式才能形成过程。

一个 group 失败不允许发布伪过程。是否保留其他已完成 group 供新 execution 复用由相同 inputFingerprint 决定；active v0 不自动 resume 或重放不确定 started call。

### 3.10 调用上界

~~~text
each group: one DRAFT + one REVIEW
repository summary when capacity passes: one DRAFT + one REVIEW
capacity failure: zero calls for that scope
~~~

没有 bridge call、reconciliation call 或无限补修层。所有调用属于同一 Reader Candidate，产品候选仍最多 Round 1 与明确授权的 Round 2。

### 3.11 开发与测试

测试只跨 ProcessExplainer Interface：

1. 三活动因显式标识/data relation 被召回，scripted model 可形成一个过程。
2. recall cue 不会由 Java 直接变成 stage order 或 merge。
3. 同名活动默认分开；不同名共享字段也默认分开。
4. 同一活动可属于两个过程，多对多 membership 保留。
5. group REVIEW 接收完整实际 draft，并只调用一次。
6. group 输入保留 conditions、rules、formulas、questions 和 refs。
7. repository summary 不替代完整 knowledge。
8. 总整理超预算时 0 summary call、notConsolidatedGroups 非空、complete 被拒绝。
9. unknown activity/ref、漏入口、started failure 与错误 fingerprint fail closed。

真实产品质量按小包、第二领域、整仓顺序推进。当前的合成补货→收货→应付账单 P1/P2 验证说明：模型可以形成三阶段过程，也可以只保留独立候选或未归入活动；对象承接不足以证明强制顺序时，结果应是 `REASONABLE_INFERENCE` 或 `NEEDS_CONFIRMATION`，而不是由 Java 或测试强制合并。该合成结果不代表客户源码。HTTP 或第二产品 Provider adapter 不阻塞 scripted Interface 验证。

## 4. 旧 Step 07 Module 的去向

| 旧名 | 新路线 |
| --- | --- |
| KnowledgeAdmissionCompiler | shape/ref/coverage 检查成为 ProcessExplainer 内部实现 |
| RepositoryKnowledgeMerger | 完整活动与过程总整合成为 ProcessExplainer 内部实现 |
| RepositoryKnowledgePublisher | 简单三个 checkpoint 写入成为 ProcessExplainer 内部实现 |
| bridge/reconciliation ledger | 退役；跨组关系和遗漏直接进入 knowledge/coverage |

旧 Java/schema/artifact 是迁移输入，不作为新路线别名、不双写、不建立兼容 reader。

## 5. 当前实现成熟度

- 已实现：`ProcessExplainer` 接收完整 `ActivityExplanationResult`，生产 workflow 同时传入已保存
  `BusinessMaterialSet`；Java 只按共享 `businessObjects`、`terms`、输入，或同一局部 Flow、技术证据、
  冻结源码文件召回活动组。后面三类仅是共同阅读线索，模型输入不暴露路径，Java 不批准过程顺序。超出单包上限的连通组以一个
  共享边界活动重叠切分，避免程序把可能的业务承接直接切断；达到 group 上限后，未进入任何
  group 的活动明确进入 unmatched。scripted Provider 按每组运行一次 `PROCESS_GROUP_DRAFT`
  和一次 `PROCESS_GROUP_REVIEW`；REVIEW 必须阅读完整 DRAFT。三活动补货/收货/应付账单
  测试验证过程顺序来自模型输出，并保留制度顺序待确认。
- 已实现：活动成员、短 ref、阶段成员/序号、输出字段和容量限制均由程序校验；模型包不含
  path、line、hash、Proof、Flow 或 run 身份。DRAFT/REVIEW 使用独立的中文 Prompt 资源。
- 已实现：使用已持久化 Step 06 输入时，模块可安装并 fresh reopen
  `business-processes.jsonl`、`process-coverage.json` 和
  `repository-business-knowledge.json`。知识文件保留完整活动，而不是只留过程名称。
- 已实现：调用方配置正 `maxRepositorySummaryItems` 且所有过程在上限内时，模块只将已审
  activities、process summaries、coverage 和 allowlisted short refs 交给一次
  `REPOSITORY_SUMMARY_DRAFT` 与一次完整 REVIEW；结果仅增加仓库导航、业务目标、对象关系、
  确认主题和短 refs，绝不替代完整活动或过程。超过过程数或输入预算时，模块不发 summary
  请求，并把未整理 process IDs 写入 coverage/knowledge，供第八步如实披露。
- 已实现：`PersistedBusinessRunExecutor`将同一条已持久化 Step 05 Flow 链继续传入
  `ProcessExplainer`，并保存本步骤三份 checkpoint；这条接线不重新读取技术图，也不重放已经
  完成的局部活动调用。
- 尚未实现：从 Step 03–05 投影更细的 call/data processJoinSignals 召回线索、多对多过程汇总、
  每 group 的即时 checkpoint、真实 Luna/high 与公开 runId/CLI 运行编排。当前三份文件是可复开的
  核心检查点，不代表完整 Step 07 已经交付。
- 合成补货过程只是设计/脚本化验证样例；真实 DepotHead 没有在本轮重新扫描或运行。

完整 Prompt 见 [中文 Prompt](../references/semantic-interpretation-prompts.md)，端到端字段连续性见 [walkthrough](../examples/semantic-framework-walkthrough.md)。
