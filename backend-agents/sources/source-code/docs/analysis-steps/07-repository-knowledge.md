# 仓库知识

> [总体设计](../DESIGN.md)；固定 key：repository-knowledge，目录：steps/07-repository-knowledge/。唯一业务 Module：ProcessExplainer。

## 1. 为什么存在

一个入口常常只能说明一个局部活动。业务读者关心的是这些活动怎样围绕同一对象协作：例如申请形成采购单、采购单对应收货、收货进入应付账单。这样的跨入口过程不能从一个函数切片得出，也不能仅靠共享表名确定。

Step07 用完整已审活动及必要代码上下文解释跨活动过程，形成仓库级业务知识。Java 做有界召回与组织，Luna/high 判断业务关系。它还必须保留 Step06 已显式处置但没有形成活动解释的具体入口，使下游知道“哪些入口未解释”，而不只得到一个数量。它不重新建五图、证明代码或按行业字典给活动归类。

## 2. 输入与宽松分组

输入是完整 activity-explanations、activity-coverage（含目标 v2 的程序侧 `unexplainedActivityEntries`）和必要 business-materials。材料中已经携带 Step05 组织的 actual/formal、控制与返回关系；不丢掉这些信息后另行扫描仓库。

coverage/checkpoint 内每个 `UnexplainedActivityEntry` 保存 global `entryId`、`materialId`、包内 `entryKey`、原 `materialContext` 与程序固定 `MODEL_NOT_EXPLAINED`。Process 模型输入不逐入口重复整包 context，而按 materialId 聚合为 `{materialContext, unexplainedEntryKeys, reasonCode}`，同一 context 只出现一次，并移除 material/global entry IDs。聚合使用程序持有的 local-key 映射，不从中文 context 正则提取路径，也不假设 BusinessMaterial 已有 EntryDescriptor。

Java 可以根据直接调用、显式标识传递、数据联系、已审对象/术语、processJoinSignals 做宽松召回。cue 表示“值得放在一起读”，不表示已批准顺序、对象同一性、因果、相同业务过程或唯一归属。共享 tenantId、日志、工具类、同名都不能单独得出这些结论。

下例为**SYNTHETIC_ACCEPTANCE_SCENARIO，非 jshERP 行为，也非当前 wire**：

~~~json
{
  "groupId": "G1",
  "activityIds": ["A1", "A2", "A3"],
  "recallCues": [
    {"from": "A1", "to": "A2", "cue": "reviewed order identifier reference"},
    {"from": "A2", "to": "A3", "cue": "reviewed receipt identifier reference"}
  ],
  "activities": [
    {"id": "A1", "name": "形成采购单", "completeReviewedContentAvailable": true},
    {"id": "A2", "name": "登记收货", "completeReviewedContentAvailable": true},
    {"id": "A3", "name": "形成应付账单", "completeReviewedContentAvailable": true}
  ]
}
~~~

正式模型包应包含这些活动的实际完整内容及解释关系所需的来源短片段；不能发送上例的 boolean 来假装内容已经提供。只有名称/摘要不足以判断条件、顺序或结果。

## 3. ProcessExplainer 怎样工作

| Interface 项 | 合同 |
| --- | --- |
| 输入 | 全部完整 ReviewedActivity、activity coverage、recall cues、必要 materials、按 material 聚合的未解释入口 |
| 输出 | 完整 BusinessProcess、RepositoryBusinessKnowledge、process coverage；程序侧继续保留完整 unexplained records |
| 职责 | 有界高召回分组后，以 DRAFT+完整 REVIEW 解释有依据的多对多过程和仓库知识；不强造连接 |
| 失败 | 非法成员/ID/ref/JSON、遗漏范围却报完整、started 失败 fatal；超容量组零请求；0 活动时 Process Provider 为 0 |
| 下游 | BusinessReportPublisher 读取完整活动/过程/coverage 与具体未解释入口 |
| Luna RED / Terra GREEN | RED 覆盖完整活动字段、同名/异名、多对多、保守独立过程、按 material partial；GREEN 只扩现有 input/save/read seam，不放宽其他 validator |

先为全部已审活动建立有界且可重叠的候选组，并记录单独活动与未分组范围。Luna 对每组做一次过程 DRAFT，再用同组完整输入和完整 DRAFT 做一次 REVIEW，返回完整修订结果。

模型可以提出源码未由 Java 预计算、但材料两端有根据的业务联系，使用合理限定。例如“收货信息可能用于形成应付账单；具体汇总时点待确认”。没有标识/控制/业务上下文支撑时，不能仅凭常识把多个查询和更新拼成必然流程。明确条件、分支、回退、并行与结果应保留；不存在的角色、制度、唯一性及运行事实不能发明。

一个活动允许属于多个过程，同名活动不自动合并，不同名活动也可以在同一过程中承担不同职责。成员关系显式保存多对多，不强制每个活动唯一 owner。孤立查询活动本身也可形成合理独立过程，不为了满足大流程叙事强行接到写入过程。

`MODEL_NOT_EXPLAINED` 入口不成为虚构活动或过程成员。模型可以在仓库范围说明中承认它们未形成活动解释，但不能把这个程序归因升级为 SOURCE 缺失、Flow Gap 或源码业务结论。四个用户入口同包也不能仅因同一 Controller 或“用户”词汇强串为删除→会话→注册→退出；两个独立过程、四个局部过程或有实际材料依据的多对多归属都可能正确。

## 4. 大仓库与完整内容

按组解释后，对全部已审过程摘要、跨组 cue 和覆盖表进行一次有界仓库总整理，同样最多 DRAFT + 完整 REVIEW。摘要只用于汇总导航；完整已审活动、过程段落、条件、规则、公式和来源继续保存在输出中，报告按明确 ID 获取相应章节需要的完整内容。

不能把每个活动压缩成一句标题就丢掉原文，再让报告模型凭摘要补写。若某组或报告章节无法在预算内包含必要完整内容，明确列未覆盖 IDs/原因，文档语义与验收结论为 PARTIAL；不得静默截断后声称已整理全仓。PARTIAL 不是新增 Process/runtime enum。也不通过重复 REVIEW、无限分组或不透明自动续跑增加候选轮次。

## 5. 输出与下一消费者

| 文件 | 内容 |
| --- | --- |
| business-processes.jsonl | 完整已审过程、活动成员、关系、条件/结果、依据与待确认项 |
| repository-business-knowledge.json | 全部活动/过程引用、必要完整内容、对象术语、仓库说明、待确认主题、覆盖；目标新版本保留程序侧具体 unexplained records |
| process-coverage.json | 已整理组、未整理组、活动归属/未归组原因、仓库总整理覆盖及真正需要该数组的具体 partial 范围 |

**目标业务知识投影，非保存 wire：**

~~~json
{
  "process": {
    "name": "采购收货与应付衔接",
    "activityIds": ["A1", "A2", "A3"],
    "narrative": "系统先形成采购单，再依据采购单登记收货；已登记的收货信息供应付账单处理使用。",
    "conditions": ["只有已审活动材料实际记载的条件才在此保留"],
    "confirmationTopics": ["跨活动顺序若仅由对象引用推断，应在此说明待确认"],
    "sourceRefs": ["S1", "S2", "S3"]
  },
  "coverage": {
    "reviewedGroupIds": ["G1"],
    "notConsolidatedGroups": [],
    "scope": "SYNTHETIC_SCENARIO_ONLY"
  }
}
~~~

示例中的叙述只在合成输入确实定义这些关系时成立。正式输出不保留说明性占位字符串。最终 Step08 读取知识中的完整已审业务内容与短 refs，Java renderer 不再重新总结这些段落。

## 6. 失败与复用

每个候选组和总整理都是预先有界内容任务。超容量在启动前记 NOT_CONSOLIDATED_BUDGET，Provider 为 0；已有完成组不删除。started 后请求失败终止执行，无自动重试、Provider switch 或 API-key fallback。

模型越界 ref、缺活动成员、重复冲突 ID、source/basis 不一致、损坏 JSON、覆盖表遗漏后假称 COMPLETE 都是 fatal。业务顺序、岗位、对象同一性待确认是知识内容限制；不需要把整个组排除。

目标是完成 REVIEW 随即保存，不等全仓完成；当前 ProcessExplainer 实际在循环结束后才用固定 module 地址聚合 publish。即时逐包保存与地址/聚合语义是独立已知缺口，本次只保持现有 publication，不循环安装不同 bytes，也不新建恢复/桥接账本。跨磁盘/新进程复用核验实际输入 fingerprint、hash/schema/ref/basis；同进程直接复用 immutable view，不重构图和 Proof。0 活动只保存明确范围和已有 unexplained records，不调用过程模型，不能宣称理解仓库业务。

## 7. 当前实现与后续测试

ProcessExplainer 以及 BusinessAnalysisWorkflow 的 material→activity→process→report 调用顺序已经存在。过程模型输入现在保留完整已审活动字段：参与者、对象、输入、条件、步骤、代码定义结果、规则、公式、术语、可信度、问题、范围限制和短 ref；它不能只看到活动标题、对象或摘要。`ProcessMaterialRecallTest` 已以 scripted Provider 直接验证这些字段和完整 DRAFT→REVIEW 输入。

尚未实施的是 Activity v2 的具体未解释入口接力：当前 `repositoryInput` 只投影 NOT_ANALYZED 数量，没有 `unexplainedActivityEntries` 或按 material 聚合的 `{materialContext, unexplainedEntryKeys, reasonCode}`。后续 owning schema/readers 只为实际增加的字段升版；不修改 Process DRAFT/REVIEW 的其他成员、ref、JSON 或 fatal 校验，也不把 PARTIAL 增加为 runtime 状态。

固定 jshERP 的一次受控真实小包把已经完成的“用户注册”和“用户登录”活动放进同一过程候选组。Luna/high 保守地保留为两个独立的局部过程，没有因为它们来自同一 Controller、都涉及验证码而写成“注册后登录”的必然顺序。这是期望的边界行为：召回线索只决定哪些活动一起阅读，模型仍可拒绝不存在充分业务交接依据的跨入口关系。该小包不代表固定 jshERP 的自动跨活动过程或九章已经验收。

随后对一个自动形成的账户查询分组做了一次受控过程 DRAFT。传输成功但返回未通过
`PROCESS_GROUP_DRAFT_INVALID` 的结构校验；因此该候选在 30.33 秒后终止，REVIEW 没有启动，也没有
重试。这个结果不说明账户活动之间没有业务关系，只说明该次模型返回不能作为正式过程记录。下一份
**不同**小包的 opt-in 测试会在传输前保存干净请求、在校验前保存返回，以便区分“材料不足、提示词
不清、返回结构错误”三类问题；它不会重放已经启动的账户候选。

后续 Luna/xhigh RED 直接覆盖：共享标识只触发召回、同名/异名不强制合并、多对多成员、完整条件/规则/公式从活动保留到知识、同 material context 只传一次及具体 keys/reason、仓库总整理遗漏显式 PARTIAL、超预算零请求及非法 ref 拒绝。Terra/xhigh 在现有 ProcessExplainer 的 input/result/checkpoint seam 做最小 GREEN，不写 Java 业务分类器、不放宽既有 validator。本轮未运行测试或真实 Provider。
