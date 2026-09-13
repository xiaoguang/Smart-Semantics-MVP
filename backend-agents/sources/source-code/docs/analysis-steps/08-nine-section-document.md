# 九章文档

> JDT/JavaParser接入不新增报告路线。按[插件设计](../modules/java-code-engines/README.md)，两种引擎统一提供代码材料，经相同活动/过程模块进入本步。报告不能再只收到方法名摘要；[真实注册/财务推演](../examples/java-code-engine-walkthrough.md)说明完整实现怎样贡献九章。本次仅改设计，未重新生成报告。

> [总体设计](../DESIGN.md)；固定 key：nine-section-document，目录：steps/08-nine-section-document/。唯一业务 Module：BusinessReportPublisher。

## 1. 为什么存在

前面已经得到已审活动和业务过程，最后需要让业务读者连续读懂。Step08 让 Luna/high 用这些完整内容写自然段，并审阅整篇文档；Java 负责章节、短来源引用与确定性 Markdown 排版。

报告不能退化为方法名清单，也不能只保留先前 DRAFT 的标题和短摘要。条件、业务步骤、结果、规则、公式与限制应在九章中得到适当位置。程序不重新从源码推断业务，不做行业语言判定，不再运行技术 compiler/projector。

## 2. 输入：完整已审知识与简单 refs

输入是同源 repository-business-knowledge、已审 activity/process 内容、coverage、程序侧具体 unexplained records 和 SourceRef allowlist。必要时按已知 IDs 装入相应章节需要的完整活动/过程材料；摘要可以用于导航，不能代替关键条件和原有解释。

送给报告模型的未解释入口也按 materialId 聚合为 `{materialContext, unexplainedEntryKeys, reasonCode}`，同一 material context 只发送一次，global IDs 留在程序侧。这是模型输入，不是读者语言：第 9 章只在存在未解释入口时，用其中已有的完整 HTTP 方法/路径说明“尚未形成业务解释”及自然语言原因；不能只写数量，也不能泄露 `unexplainedActivityEntries`、`materialContext`、`reasonCode`、`MODEL_NOT_EXPLAINED` 或 E3/E4。集合为空时，第 9 章只保留已有的业务待确认项。程序不反解析中文 context，不凭空创建 EntryDescriptor。

模型包只给短 ref 和内容，文件路径、行号、hash、run identity 留在程序侧。程序在调用前计算完整输入/输出预算；容量不足应明确未覆盖材料并给出 PARTIAL 状态，不能截断后把剩余文字标为全仓报告。

## 3. DRAFT、完整 REVIEW、确定性排版

| Interface 项 | 合同 |
| --- | --- |
| 输入 | 完整 RepositoryBusinessKnowledge、ReviewedActivity/BusinessProcess、coverage、具体未解释入口、SourceRef allowlist |
| 输出 | 完整九章 BusinessReport JSON、source-refs.jsonl、document.md、report-validation.json |
| 职责 | 一次 DRAFT + 完整 REVIEW；模型写正文，Java 只校验固定章节/ref/budget 并排版 |
| 失败 | 缺章/错标题/非法 ID/ref/bytes、遗漏范围却报完整、started 失败 fatal；显式空仓报告保持既有模型调用 |
| 下游 | public render/inspect/artifact 与业务读者 |
| Luna RED / Terra GREEN | RED 覆盖完整第4章、第9章具体 partial、九章/ref、纯 render；GREEN 只补 knowledge→report 输入和正文保留，不新增语义 parser |

1. Luna/high 对完整九章输入做一次 DRAFT，输出 paragraph/list item JSON，不输出 Markdown 样式或来源身份。
2. REVIEW 输入包含原有业务知识与**整篇实际 DRAFT**，最多一次；输出完整修订 JSON。不能只回“通过”、章节标题或局部 patch。
3. Java 检查 exact section IDs/titles、文本类型、scope-local ID/ref、coverage 和预算，保存完整已审 JSON。
4. renderer 只按结构排 H1/H2、段落、列表与 ref，不截断、不重新摘要、不改业务正文。原子安装报告文件。

报告传输 Schema 对段落的 `refs` 使用有长度限制的字符串数组；**完整合法 ref 集合在模型输入中提供，并由 Java 在接收时逐项校验**，不在九章的 18 个 paragraph/item 位置重复展开同一份大枚举。九章编号、标题和结构仍使用固定槽位及单值枚举，未知来源仍以 `BUSINESS_REPORT_SOURCE_SCOPE_INVALID` 拒绝。这样来源数量增大不会因为重复枚举而触发 Provider 的 Schema 容量限制，也不减少可引用来源或改变保存格式。直接测试必须同时证明大来源集合能形成传输 Schema，以及集合外 ref 仍被拒绝。这是传输表示的缩减，不把引用合法性交给模型自行保证。

业务 review 检查的是源码定义行为和实际运行事实是否混淆、是否捏造岗位/制度/唯一性、推断是否有依据且有适当限定、原有条件和公式是否保留。Java 不用术语匹配或字符串 blacklist 假装已经判断业务正确。

## 4. 九章固定职责

| 序号 | H2 标题 | 应保留的内容 |
| --- | --- | --- |
| 1 | 文档说明 | 固定来源、分析范围、覆盖与局限、源码行为/运行事实区别；折叠来源区 |
| 2 | 业务目标 | 已审活动和过程体现的目的，不发明组织战略 |
| 3 | 业务对象 | 对象、用途及有依据的业务含义 |
| 4 | 业务活动 | 连贯过程与局部活动段落，保留条件、分支、结果 |
| 5 | 字段与维度 | 源码/已审知识支持的输入、状态、日期、标识等 |
| 6 | 对象关系 | 有依据的引用与过程联系；合理推断标注待确认 |
| 7 | 指标口径 | 仅已审材料存在的公式和定义；没有则明确未识别 |
| 8 | 示例问题 | 与已识别活动、对象、字段有关的读者问题 |
| 9 | 待确认事项 | 未知角色/制度/顺序/效果，以及按完整 HTTP 方法/路径列出的具体未解释/未分析入口与原因类别 |

不能生成第十个 H2，不用固定业务分类、强行每章等长或固定输出条数代替内容质量。0 入口/全部未分析可以产生九章范围说明，但文档语义与验收结论是 INCOMPLETE；PARTIAL/INCOMPLETE 不是新 report/runtime enum。0 入口时 Activity/Process Provider 调用为 0；调用方若显式生成空仓九章，BusinessReportPublisher 仍执行现有 DRAFT+REVIEW，本设计不增加零模型空报告分支。

## 5. 目标输出示例

下列为**目标 paragraph JSON 投影，不是完整 wire、已生成产品或可运行 fixture**；正式输出必须同时包含九个 section。

~~~json
{
  "sectionId": 4,
  "title": "业务活动",
  "paragraphs": [
    {
      "text": "系统根据业务单据标识查询关联财务单号。查询服务把标识传入 Mapper；正常返回时将结果放入响应，捕获异常时返回失败信息。",
      "sourceRefs": ["S1", "S2"]
    },
    {
      "text": "上述说明反映源码定义的处理方式。实际是否存在关联记录，以及查询是否成功，需要运行时数据才能确认。",
      "sourceRefs": ["S1", "S2"]
    }
  ]
}
~~~

正文例子来自固定财务查询源码的目标解释。真实 Controller 正常路径先接收 Service 结果，再设置 code=200/data=list；catch 设置 code=500/data=“获取数据失败”，最后返回 res。它没有计费、过账、角色或唯一单号的结论。

来源显示采用 [S1] 等短标记，在第一章折叠区域对应冻结文件、精确行段与原始代码；不要求读者看 Proof ID 才理解业务，也不依赖未来 HTTP 源码查看器。

## 6. 保存文件与纯渲染

| 文件 | 作用 |
| --- | --- |
| business-report.json | 完整 REVIEW 后的九章业务 JSON |
| source-refs.jsonl | 被引用短 ref 到冻结文件/行段/原文的程序侧映射 |
| document.md | 确定性排版结果 |
| report-validation.json | 类型/章节/ref/budget/coverage 检查结果与内容审阅状态 |

publisher 保存时只做必要结构检查、序列化与原子安装，不能调用 Flow compiler、Fact 枚举器或 Capsule projector。磁盘重新打开或显式导入验证 hash/schema/ref/basis；按已验证 JSON 纯 render 是 0 Provider 操作。inspect/artifact 只读；编辑业务正文是另行授权的模型内容动作。

已开始但结果未知的请求不自动恢复，已完成报告不被覆写。使用现有 checkpoint/inputFingerprint 规则即可，不新建十几层 receipts、reader replay 或恢复状态机。

## 7. 失败与完成标准

缺章节、错标题、越界 ref、来源 bytes/basis 错误、非法 ID、损坏 JSON、预算安全失败、漏记范围却报全仓完成必须停止交付。未识别指标、未知角色或未证明外部效果是诚实说明，不需要发明内容补齐章节。

交付完整还要求入口与过程覆盖真实：每个发现入口有活动解释或具体未分析原因，每个过程组有整理状态，已审内容进入相应章节且没有无声缩水。`unexplainedEntries` 可以让集合账闭合，却不能把未形成活动解释的真实入口算作业务验收成功。只跑一个好活动不代表整仓完成；所有入口 NOT_ANALYZED 时覆盖账虽闭合，语义交付仍 INCOMPLETE。

静态代码清楚构造并 save 对象时，可以描述“生成并保存对象”的程序行为；没有运行证据不得断言本次保存成功、库存已经增加或支付已经完成。合理业务推断在相应段落集中限定，避免每句话重复警告。

## 8. 当前实现与后续测试

BusinessReportPublisher 已有 DRAFT+完整 REVIEW、九章 Markdown 和四个报告文件，BusinessAnalysisWorkflow 已接通它。`PersistedBusinessRunExecutorTest` 以一个真实的已保存 Step05 fixture 和 scripted Provider 直接验证：活动 REVIEW 的目的、条件、规则、问题先进入过程/报告模型输入，报告 DRAFT 再完整进入报告 REVIEW，最终 Markdown 保留该业务段落。另有一次明确授权的 Luna/high 小包验收：它只重用已完成的 jshERP 用户登录、用户注册活动及其保守的两个独立过程，生成一份九章报告；没有把注册和登录伪造成有源码顺序的单一过程。该结果证明小包的业务语言与报告链路可用，不证明自动 Builder→整仓业务九章已经通过真实质量验收。

报告任务的结构化输出 Schema 还必须固定九个命名槽位：`section1` 只能是“文档说明”、`section6` 只能是“对象关系”，依此直到 `section9`。这只是模型临时返回形状；Java 立即把它转换为既有的顺序九章 `BusinessReport`，持久化 JSON 和 Markdown 不变。仅把标题限制为九个可选值不足以保证章节对应关系；一次真实报告 DRAFT 曾把第 6 章错误写成第二个“指标口径”，Java 在 REVIEW 前拒绝了它。数组 tuple 的 `prefixItems` 编码随后被本机 Codex 在生成前以 `MODEL_CONFIGURATION` 拒绝，因此改用这个 Provider 路径已经使用过的闭合对象属性、`required` 和单值 `enum`。Provider Schema 与 Java 返回校验都按固定槽位约束，避免把本可在输出边界阻止的错误留到一次已启动候选之后。

当前报告输入已获得 Activity v2 的具体 `unexplainedActivityEntries` 的按 material 聚合投影；E1–E4 这样的 PARTIAL 输入会把完整 HTTP context 和 `MODEL_NOT_EXPLAINED` 原因交给报告模型。报告 prompt 要求第9章说明这些具体范围，而第4章不得为它们编造活动。`cleanKnowledge`/Prompt/input 与真正承载字段的 owning schema/readers 已升级；报告九章 output shape 不因内部新增输入而强制升版。

Luna/xhigh RED 已直接验证按 material 一次投影具体未解释入口到第9章，Terra/xhigh 已在 publisher/input/render 接力处做最小 GREEN；不新增业务语义 parser或空报告捷径。四入口与任意 N scripted 全链现已复核：完整 REVIEW 可补齐 E3/E4，partial 时第2–8章只消费已审 E1/E2，第9章保留 E3/E4 的实际 HTTP entry 和 `MODEL_NOT_EXPLAINED`，并输出恰好九章。条件/规则字段仍通过 Report 模型 input 保留；最终自然语言质量不由 scripted Provider 假称已验收。本轮没有调用真实模型。
