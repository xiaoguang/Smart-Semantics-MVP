# Source Code Analysis Agent：从源码到业务过程

## 1. 交付目的

读者需要知道系统做什么、围绕哪些业务对象、一次活动经过什么条件、多个活动怎样组成业务过程。证据的首要用途是可靠地找到代码；图把相关代码的结构和执行关系连起来；模型在连贯代码上下文上解释业务。最终交付是一份可回到冻结源码的九章业务报告。

这条路线保留八个步骤和唯一公开 `RepositoryAnalysisAgent`。现有五张程序图、严格技术 Fact/Proof继续保留为技术增强；它们不是每种工具把完整源码交给模型的前置门槛。Java负责来源、工具导航、代码材料、检查和保存；Luna/high负责业务含义、跨活动过程与业务语言。Java不维护行业词表，也不自己补全编译器的类型解析规则。

新的取材方式是[JDT/JavaParser可切换引擎](modules/java-code-engines/README.md)：YAML选择一个引擎，统一交付声明、完整方法、调用位置、实参/形参、实现候选和边界。**JDT 第一阶段已独立接入正式分析链；第二阶段才把保留的 JavaParser 恢复到原有能力。** 当前选择 `jdt` 会经过正式配置、发现、索引、Step05、业务材料及既有业务链；选择尚未适配的 `javaparser` 会明确失败，不存在隐式回退。

本文是目标设计。当前代码中已经存在四个业务 Module 和工作流，Step05 EntryContext 已连续传到材料，普通 Flow/Capsule 发布已停止重复 compile/project，Spring unrestricted method condition 也已落地。旧解释链及其 Capsule registry basis 字段已退出；Activity 的 v2 Prompt/schema、coverage-after-REVIEW 和程序侧未解释入口记录已落地。Process/Report 也已把具体未解释入口按材料投影到仓库知识和第九章。实现按当前实施计划分步开展；本文不把目标合同写成代码或实测结果。

## 2. 一条端到端接力

| 步骤与固定 key | 输入 | 本步工作 | 可观察输出与下一消费者 |
| --- | --- | --- | --- |
| 01 已验证源码清单 / verified-source-inventory | 已注册固定 commit 的离线快照 | 一次核对完整清单、字节、路径和行索引 | source inventory、snapshot；02 与受限源码读取器使用 |
| 02 应用发现 / application-discovery | 已验证文本、框架配置、选定引擎catalog | 共享Spring规则识别入口，Java语法只由选定引擎读取 | entry inventory、profile、catalog；03、05使用 |
| 03 程序图 / program-graphs | 同一源码和入口、选定引擎 | 建可复用Java导航索引；保留实际可提供的五图增强 | java-code-index、实际图增强及可用性；05直接取完整方法/调用 |
| 04 已证明代码事实 / proven-code-facts | 实际存在的图增强和规则 | 有图时按原义生成严格Fact；无增强则明确NOT_PRODUCED | 实际Facts/Proof或未生成说明；不阻断源码阅读 |
| 05 业务流程 / business-flows | 全入口、引擎代码上下文、可选技术增强 | 归属并保存完整方法/候选/参数/条件/返回；一次投影Capsule | entryContexts、实际strict Flow、Capsule、处置与覆盖；06复用 |
| 06 流程解释 / flow-interpretation | 入口上下文、图关系、同快照源码 | BusinessMaterialBuilder 封装已有上下文；ActivityExplainer DRAFT + 完整 REVIEW | 材料、已审活动、活动或显式未解释处置闭合的入口覆盖；07 使用 |
| 07 仓库知识 / repository-knowledge | 已审活动、具体未解释入口、连接线索与必要原始材料 | ProcessExplainer 宽松分组、解释跨活动过程、整理仓库知识 | 已审过程、知识、具体 partial 范围与过程覆盖；08 使用 |
| 08 九章文档 / nine-section-document | 完整已审知识、活动/过程、具体未解释入口与来源短 ref | BusinessReportPublisher 写并审阅九章 paragraph JSON，Java 排版 | JSON、SourceRef、Markdown、验证结果；第 9 章保留未解释入口 |

01–05 不调用模型。06–08 内部正好四个业务 Module：BusinessMaterialBuilder、ActivityExplainer、ProcessExplainer、BusinessReportPublisher。它们隐藏在同一个 Agent 后，不新增业务分类器、证明层、POC runtime 或恢复系统。数值前缀只用于文档与 steps 目录排序，语义 package/key 不变。

Step02 入口以 `methodKey + SourceRange` 定位完整声明，不能只凭 handler 名区分重载。`java-code-index` 是 Step03 既有 `PROGRAM_GRAPHS` 下的新 module 7，不是第九步。JDT 第一阶段的实际集合固定为 Step03 `java-code-index.jsonl + program-graphs-receipt.json`，Step04 `fact-accounting.json + proven-code-facts-receipt.json`；后者 v4 为 `NOT_PRODUCED`、reason 非空且 counts 为 null。Step05 context-first，继续发布既有五个语义文件与 receipt，具体 schema 版本见[代码引擎合同](modules/java-code-engines/contracts-and-configuration.md#5-保存格式位置与复用)。

### 2.1 四个业务 Module 的深接口

| Module | 输入 | 输出 | 隐藏的职责 | 失败/停止 | 下一消费者 | Luna/xhigh RED → Terra/xhigh GREEN |
| --- | --- | --- | --- | --- | --- | --- |
| BusinessMaterialBuilder | 同源 Step05 EntryContext/Capsule、全入口 coverage、材料 profile | `BusinessMaterialSet`、SourceRefs、global entry→material 处置 | 选择完整上下文、按配置 K 和真实 bytes 在入口边界分包、为每包生成 E1…EN | source/ref/identity 损坏 fatal；单入口无法安全成包时具体 NOT_ANALYZED；Provider 调用为 0 | ActivityExplainer；Step07 必要回查 | RED 覆盖任意 K、noFlow、零入口、映射与预算；GREEN 只改现有 Builder，不重建图/调用链 |
| ActivityExplainer | 一个完整 material、scope-local key/ref allowlist、相容 Activity profile | 完整 ReviewedActivity、global entry coverage、程序侧未解释入口记录 | 一次 DRAFT + 一次完整 REVIEW；完整实际 DRAFT 与 `missingEntryKeys` 进入 REVIEW，最终以活动或 `unexplainedEntries` 闭合 | 调用前表达/输入/输出/REVIEW 容量不相容则零请求；非法 JSON/key/ref 与 started 失败 fatal；REVIEW 仍漏项 fatal，无第三次调用 | ProcessExplainer、Step08 coverage | RED 覆盖 N=4 漏项、N≥12、跨包 E1、真实 REVIEW bytes；GREEN 落地 v2 Prompt/schema 与最小 sidecar |
| ProcessExplainer | 全部完整已审活动、recall cues、必要材料、按 material 聚合的未解释入口 | 完整已审过程、RepositoryBusinessKnowledge、process coverage | 高召回分组后由 Luna 判断有依据的多对多过程，保留独立活动和 partial 范围 | 非法成员/ref/JSON、遗漏范围却报完整、started 失败 fatal；0 活动时过程 Provider 为 0 | BusinessReportPublisher | RED 覆盖完整字段、多对多、保守独立过程和 specific partial；GREEN 只扩现有 knowledge input/save/read seam |
| BusinessReportPublisher | 完整知识、活动/过程、coverage、按 material 聚合的未解释入口、SourceRefs | 固定九章 JSON、source-refs、Markdown、validation | 一次报告 DRAFT + 完整 REVIEW；Java 只校验并确定性排版 | 缺章/非法 ref/虚假全量 fatal；显式空仓报告仍走既有 DRAFT+REVIEW；PARTIAL/INCOMPLETE 不新增 runtime enum | 业务读者与 public render/inspect/artifact | RED 覆盖第4章内容、第9章具体入口、九章/ref/纯 render；GREEN 只补 knowledge→report 与第9章，不建语义 parser |

这张表是后续实现的 Interface 与测试面。模块间传 typed immutable 结果；内部测试 seam 不扩成新的公开 Interface。Activity REVIEW 的 `unexplainedEntries`、程序侧完整记录、v2 Prompt/schema 和 coverage v2 已写入当前实现。Process/Report 的按材料聚合投影也已接通：repository-summary 与 report DRAFT/REVIEW Prompt、ProcessExplainer 与 BusinessReportPublisher 已升到 Module v2；process-group DRAFT/REVIEW Prompt 保持 v1。四入口 complete/partial、跨包 N=9、单包 N≥12、零入口和预算/非法响应边界的 scripted 全链已通过；独立宿主会话候选也已对固定四入口材料完成一次真实 Activity DRAFT+REVIEW，结果只作为局部语义质量样本。

## 3. 为什么同时保留 03、04、05

03 是代码索引。它回答“这个调用可导航到谁、有哪些候选、实参与形参是什么、条件和返回写在哪里”。JDT LS/Core负责导航与语法读取；JavaParser作为第二阶段备选。调用位置与参数对照不冒充完整数据流证明，其输出是源码材料，不是业务判断。

04 是严格技术增强。当前 `FactRegistry` 只登记 `JAVA_EXACT_CALL`、`JAVA_BOUNDARY_INVOCATION`、`JAVA_GUARD_CONDITION`。每个声称成立的 Fact 必须满足该模式全部 required atoms，缺一个就拒绝该 Fact。它既不是全代码摘要，也不是所有有用信息的白名单。没有 SQL 效果 Proof，仍可以把安全定位的 SQL 原文交给模型；但不能把原文、模型推断或 hash 改名为该 Proof。

05 是入口上下文保存与组织。它复用03选定引擎已经取得的方法和关系，附可选04增强，再投影Capsule；不重新找Service或解析一遍Java。未知候选、外部边界及原文保留，完整body不因缺CLOSED atom而消失。

Step04本身保留。JDT第一阶段没有提供原严格五图增强时，步骤明确保存“未生成技术增强”，不制造空图/假Fact满足旧接口；JavaParser现有五图/Fact能力不删除，第二阶段接回。已有产物若被提交，损坏或伪造仍拒绝。具体产物可用性、读写器变化见[接入设计](modules/java-code-engines/integration-and-javaparser.md)。

## 4. 真实小例：按业务单据查财务单号

固定 jshERP commit 为 `8c30ce7861570458920175e200bb2a6442713580`。真实入口 `GET /accountHead/getFinancialBillNoByBillId` 的 Controller 接收 `Long billId`，传给 Service；Service 原样传给 Mapper，随后把查询返回值向上返回。Controller 在 try 中先将 Service 结果赋给局部 list，再设置 res.code=200、res.data=list；catch 记录异常并设置 res.code=500、res.data=“获取数据失败”，最后返回 res。

源码位置是 AccountHeadController.java:181–196、AccountHeadService.java:442–444、AccountHeadMapperEx.java:44–45 与 AccountHeadMapperEx.xml:150–155。XML 静态 select 关联 account_head/account_item 并按 bill_id 过滤；当前严格证明到 Java Mapper 边界，不据此宣称数据库查询已经执行成功。

以下是**目标阅读投影，短 ID 仅为说明，不是当前保存文件，也不是 Schema**：

~~~json
{
  "entry": "E1",
  "calls": [
    {"from": "Controller", "to": "Service", "arguments": [{"actual": "billId", "formal": "billId", "ordinal": 0}]},
    {"from": "Service", "to": "Mapper", "arguments": [{"actual": "billId", "formal": null, "ordinal": 0, "binding": "ARGUMENT_TO_BOUNDARY"}]}
  ],
  "control": [
    {"context": "try", "basis": "SOURCE_CONTEXT", "steps": ["list=Service(billId)", "res.code=200", "res.data=list"]},
    {"context": "catch", "basis": "SOURCE_CONTEXT", "steps": ["log exception", "res.code=500", "res.data=获取数据失败"]},
    {"after": "try/catch", "step": "return res"}
  ],
  "returns": ["Mapper result → Service return", "Service result → Controller local list → res.data"],
  "boundary": {"target": "Mapper", "staticSqlContext": "join account_head/account_item; filter bill_id", "externalExecution": "UNKNOWN"}
}
~~~

这份投影说明 05/06 应从已存在图关系中组织什么，不能假称已有一个完整的人类调用链文件。已核对的图/Fact run 为 `analysis-run--c10be6c086fec92ca85081bf9856e7ed1cd7f3ad617e1e90d7fc9ffaec89286d`，相关图保存了 Controller→Service 的实参/形参绑定及 Service→Mapper 的边界实参；当前 CFG 没有独立 TRY/CATCH 节点，结构从源码读取，该入口有 2 条 exact call 与 1 条 boundary Fact；这个 run 没有 Step05 正式 publication。另一材料规划 run `analysis-run--54d866fa27f7b4fef1e272a404b09e9b03ba104945caafe717c69ff9cdd79530` 的第 314 条材料为 ENTRY_SOURCE_FALLBACK，flowRefs 为空，只有 S567 的完整 Controller 和 S568 的完整 Service 片段。两次运行不能拼成一条已实测的完整流水线。

目标业务段落可以写为：

> 系统根据业务单据标识查询关联的财务单号，并把查询结果返回给调用方。代码分别处理正常返回和异常情况；异常时返回错误信息。这里描述的是源码定义的查询处理，实际是否查到记录取决于运行时数据。

这段不需要把代码 ID、Proof 链或岗位猜测放到正文。完整示例见 [walkthrough](examples/semantic-framework-walkthrough.md)。

## 5. 连贯业务材料怎样形成

Step05拥有下游读取的入口上下文：消费选定引擎一次取得的完整方法、调用、参数和候选，在flow-slices.json的entryContexts保存；Capsule只投影，不重新判定。统一字段以[代码引擎合同](modules/java-code-engines/contracts-and-configuration.md)为准，替换旧单target的窄CallContext；必要producer/schema/reader同步升版。BusinessMaterialBuilder只选择完整方法、分配短ref并组包，禁止隐藏JavaParser/JDT调用。源码缺失必须在context中说明，不能让模型只读Controller却称连贯取材完成。

一个材料包至少能看清入口、直接实现、条件归属、结果及未知位置。引擎先保存完整方法，不以模型费用裁源码；Builder按完整方法组包，不能仅为“最小证据”删除Service主体。物理上下文装不下时，具体列出未交给模型的方法/入口，而不是静默截短后宣称完整。

无strict Flow时仍由Step05保存flowRef=null的引擎上下文。JDT不需要先生成五图/Proof才可提供Service；未知目标/候选以原文和限制保留。Step06仍只读Step05结果，不扫描Java或猜callee。无法安全定位的入口单独处置，不删入口分母。

程序保存 SourceRef→冻结文件/精确行段/原文映射。模型包只携带 S1 等短 ref、必要代码、连贯技术观察与限制；不含本机路径、行号、hash、完整 Proof、控制预算或 Provider 配置。模型不能创造 refs、来源身份或技术 Fact。

## 6. 从活动到跨活动过程

BusinessMaterialBuilder 按每份材料稳定有序的 global entry IDs 建立本包映射 `E1…EN → global entry ID`；`N` 是该材料的实际入口数，不固定为 4，也不等于仓库入口总数。配置容量 `K` 只约束 Builder 怎样在入口边界分包。每包都可从 E1 重新编号，后续只能通过 `(materialId, local key)` 回到 global ID，不能用裸 E1、前缀、substring 或词典序跨包连接。活动与入口是多对多：一项活动可覆盖多个 key，一个 key 也可有多个有依据的活动；容量必须允许每入口单独表达，但不强制一入口一活动。

ActivityExplainer 用 Luna/high 对完整 material 做一次 DRAFT，再对原材料及**完整实际草稿**做一次 REVIEW，返回完整修订后的活动。purpose、participants、objects、inputs、conditions、steps、results、rules、formulas、questions 和必要长段落都保留。review 不是只查 schema、标题或摘要；后续保存和传递也不能截断成一个短 label。

Activity v2 把验证分为两层：DRAFT 的 JSON、字段、local ID、key/ref allowlist 与 bytes 必须合法；只有 coverage 不足时不再提前 fatal，程序计算 `missingEntryKeys` 并把它和完整实际 DRAFT 送入唯一 REVIEW。REVIEW 必须满足 `reviewed activity keys ∪ unexplainedEntries = expected keys` 且二者不相交；仍漏项或任何结构/范围错误 fatal，不发第三次请求。程序把未解释 key 映射为 global entry coverage 的 `MODEL_NOT_EXPLAINED`，它不是源码/Proof Gap。当前 Java、v2 resources 与 coverage v2 已实现此合同；尚未完成的是将这些完整记录传入 Process/Report。

`UnexplainedActivityEntry(entryId, materialId, entryKey, materialContext, reasonCode)` 是程序侧完整值类型，reasonCode 固定 `MODEL_NOT_EXPLAINED`。`ActivityExplanationResult` 和 `RepositoryBusinessKnowledge` 均持有完整 `unexplainedActivityEntries` 列表；便利构造器可默认 `List.of()`，canonical v2 磁盘 reader 必须读取 required 数组，拒绝字段缺失/null 和旧版 fallback。局部 key 数组的顺序服从材料入口映射，E10 保持第十入口含义，不能词典序重排为第二入口。

ProcessExplainer 接收全部已审活动、必要来源包和程序侧完整未解释入口记录；仓库总整理模型与报告模型只接收按 `materialId` 聚合的 `{materialContext, unexplainedEntryKeys, reasonCode}`。同一 context 只传一次，global IDs 留在程序 coverage/knowledge；不新增 EntryDescriptor，也不从中文 context 正则反解入口。Java 用调用、参数/标识传递、数据关系、对象/术语候选及 processJoinSignals 做宽松召回；Luna 根据多入口材料判断它们可能组成怎样的过程。线索不能自动证明先后、因果、对象同一性或唯一归属。一个活动可以属于多个过程，同名不自动合并，异名也不自动排除。未解释入口随知识进入报告范围，但不能被模型升级为来源缺失或技术 Gap。

大仓库按有界、可重叠的活动组工作，每组有一次 DRAFT 和完整 REVIEW，再对全部组摘要与跨组线索做有界仓库总整理。摘要可以帮助检索，但完整已审活动与过程保留到报告输入或按 ID 可取的有界章节材料；条件、规则、公式不能在分组后消失。无法在预算内送入某组或某章的内容明确列入未覆盖范围，不能静默压缩后宣称完成。

合成“补货申请 → 审批形成采购单 → 登记收货 → 形成应付账单”只演示跨活动业务叙事，并非 jshERP 事实。若输入只支持对象引用关系，模型可提出有依据的可能过程并集中标“顺序待确认”；没有岗位资料则不用“采购员/仓库员”等常识角色。

## 7. 九章报告与可读来源

BusinessReportPublisher 用完整已审仓库知识和必要活动/过程材料做一次 DRAFT、一次完整 REVIEW，输出段落 JSON。Java 校验类型、章节、ID/ref 范围和预算，然后确定性排 Markdown。最终 H2 严格为：

1. 文档说明
2. 业务目标
3. 业务对象
4. 业务活动
5. 字段与维度
6. 对象关系
7. 指标口径
8. 示例问题
9. 待确认事项

正文使用自然业务段落；已审草稿中的有用解释、条件、规则和公式应进入对应章节，不能在 renderer 中重新摘要或只留下标题。第 7 章没有源码支持的公式/定义时明确“本次未从源码识别到可定义指标”。第 8 章的问题应可由已识别活动/对象理解，不能用问题形式夹带未证实制度。

来源标记采用简单短 ref。默认在第一章内放折叠来源区，含同文档锚点和经验证的真实文件、行段、片段；不增加第十章。业务推断可按活动或段落标注依据和不确定性，不要求每个自然语言原子配一个 Proof。

清楚的源码构造对象并调用明确 save/insert 时，可以说“系统生成并保存该对象”；这是代码定义的行为。没有运行证据不能写本次已成功保存、库存实际增加、付款完成、唯一单据或成功数量。只看到模糊调用边界时，应缩窄结论或列待确认。业务真伪由模型完整 REVIEW 与授权后的人工样本审查判断，不建立 Java 行业语言解析器。

## 8. 一次计算，可靠保存，边界核验

正常可信进程建立一次源码验证视图及选定引擎会话；LS语义索引、Core每文件语法读取分别缓存，不按步骤重复。实际启用的图/Fact增强、上下文和Capsule由各自拥有者计算一次，publisher不重跑。内部可传不可变view；持久化仍用于观察和显式复用。

publisher 负责序列化、最小必要的 type/ID/ref/budget 检查、canonical bytes/hash 和原子安装；写入完整性不等于再次运行图编译、Fact 枚举或 Capsule projector。磁盘重新打开、新进程、导入或跨 run 复用才校验保存身份/hash/schema/ref/basis 以及需要读取的源码 bytes。已验证的同进程 immutable bytes 可重复利用，不重复扫描全仓。显式独立审计或 mutation tests 可以重放算法，不能变成普通消费的必经层。

Step01–05 保留既有命名技术产物；调整的是重复计算和过强准入门。业务检查点仍为：

| Step | 保存文件 |
| --- | --- |
| 06 | business-materials.jsonl、activity-explanations.jsonl、activity-coverage.json |
| 07 | business-processes.jsonl、repository-business-knowledge.json、process-coverage.json |
| 08 | business-report.json、source-refs.jsonl、document.md、report-validation.json |

本次 owning wire 升版固定为：`flow-interpretation-activity-coverage-v2` required `unexplainedActivityEntries`；`repository-knowledge-business-knowledge-v2` required 同名完整记录数组；`repository-knowledge-process-coverage-v2` 修正现有 `semanticDeliveryStatus` 判定。任一 activity coverage 为 `NOT_ANALYZED`，或 unmatchedActivityIds/notConsolidatedProcessIds 任一非空时，process coverage 必须为 `PARTIAL`，不能只看过程归组。`activity-explanations.jsonl`、`business-processes.jsonl` 和报告九章 JSON 的输出 shape/版本保持 v1。publisher、engine、policy fixture、canonical readers 和直接版本测试须同步，不保留旧 reader fallback。

材料在首次 Provider 前保存。目标仍是每个完成 REVIEW 的活动和过程随即保存，但当前 ActivityExplainer/ProcessExplainer 都在各自循环结束后才聚合 publish；固定 module 地址不能在循环中反复安装不同内容。即时逐包保存是独立已知缺口，本次批准的清理/覆盖修复不假称解决，也不扩建分片协议、恢复系统或逐记录状态机。inputFingerprint 包含实际内容输入（不含新的 runId）、实际 Prompt 文本/版本、有效模型与输出配置、Module 版本；显式复用要求相等，并通过磁盘边界完整性检查。

## 9. 覆盖、预算与失败

全部发现入口必须分到 ANALYZED、ANALYZED_WITH_GAPS 或有具体原因的 NOT_ANALYZED；全部过程组必须已整理或有未整理原因。覆盖表闭合只代表没有漏记，不代表业务内容完成。一个好样本、零入口范围报告、全部入口未分析或预算未完成的九章都不能称整仓业务交付。

模型内容任务统一最多一 DRAFT 加一完整 REVIEW。`maxMaterialsToStart` 明确限制本次启动材料数；超限材料零请求并记录具体原因。对每个其余 Activity material，调用前以实际 `N` 检查：活动输出槽位至少允许 N 个独立活动、每项 key 数容量至少为 N、N 项最小合法 REVIEW 输出能装入 output budget、实际 `cleanPacket` 能装入 input budget，并为“完整材料 + 最大 DRAFT + `missingEntryKeys` REVIEW envelope”预留 input budget。DRAFT 返回后仍须用真实实际 DRAFT 重新序列化 REVIEW packet 核对 bytes。任一前置容量不相容时该材料零 Provider 请求；ActivityExplainer 不临时拆 Builder 已成形的材料、不截断源码。

只有结构与 scope 合法、但 coverage 不足的 Activity DRAFT 可以继续唯一 REVIEW。非法 JSON、未知/越界 key 或 ref、重复冲突 local ID、输出超预算，及任何 started transport/schema/runtime 失败都立即终止，不进入修补通道。REVIEW 用活动和 required `unexplainedEntries` 闭合集合；仍漏 key fatal，无第三次调用。materials-only 目标经同一 Step05 context owner 后封装材料，仍为零 Provider；最终报告运行要求正启动上限。内部 DRAFT/REVIEW 属于同一 Reader Candidate；仍只允许产品 Round 1 和针对明确问题另行授权的 Round 2，不重放已失败的产品调用。

当前已完成一次明确授权的小包质量验收：jshERP 的用户登录、用户注册两个已审活动及两个保守独立过程经报告 DRAFT+REVIEW 生成九章。它不把共享用户控制器或业务名称当成注册后必然登录的顺序证据；该小包证明当前业务语言链路可产出可读报告，不代表完整仓库已经验收。

错误分两类：

- 来源错误、损坏 bytes、路径不安全、断引用、伪造 exact Proof、违反安全预算、非法 JSON/key/ref，以及 REVIEW 既未解释也未显式处置入口：fatal，停止相应交付。DRAFT 只有 coverage 不足且其余结构/范围合法时是唯一例外，必须进入已批准 REVIEW。
- 未证明静态边、unsupported 语法、未知业务含义/岗位/制度、局部 Flow 缺口，以及 REVIEW 明确列出的 `MODEL_NOT_EXPLAINED`：如实限制受影响结论；安全可读部分仍可分析。不可读取或调用前包超预算的入口单独说明原因。

0 入口时 Activity 与 Process Provider 调用均为 0；若调用方仍显式要求空仓九章范围报告，BusinessReportPublisher 保持现有 DRAFT+REVIEW，不承诺全链零调用。本文的 PARTIAL/INCOMPLETE 是文档语义完整度与业务验收结论，不新增 runtime/report 状态 enum；覆盖账闭合但含 `MODEL_NOT_EXPLAINED` 仍不能通过完整业务验收。

业务质量观察顺序是一个真实小包、第二领域小包、再整仓。本次已批准的 live 范围仅为[实施计划 Task 7](plans/coherent-code-context-implementation-plan.md#task-7脚本验收后执行已批准的单材料-live-验证)固定的四入口材料：直接 scripted 测试通过后，新候选最多一次 Activity DRAFT + 一次 REVIEW，Luna/high，总调用数不超过 2；精确 material/entry/ref 身份和 `ActivityExplanationProfile(20000, 12000, 4, 24, 1000)` 不得替换。PARTIAL 或失败保留产物并停止；不重试，不运行真实 Process/Report，也不重扫整仓。更大范围不在本次批准内，本文不虚构耗时改善。

## 10. 当前实现与剩余边界

JDT-only 第一阶段已经完成；本表只区分已验证能力与下一阶段边界，不把调研程序、目标合同或 JavaParser 的未来适配混写成当前 JDT 实现。

| 当前可确认事实 | 剩余边界或保持项 |
| --- | --- |
| Step03/04 稳定算法、FactRegistry 三类技术模式与 AtomicProofBuilder 全 atoms 规则保留；普通 persisted candidate 读取与 Flow/Capsule 发布已不再重放 owner 算法 | 本次不修改 Step03/04 算法或恢复重复 replay；清理只删除旧解释链的专属消费者/注册 |
| EntryRootedFlowCompiler 已在 flow-slices/Capsule 保存 EntryContext，传递 argument/return/data/control 与可选 Proof；BusinessMaterialBuilder 已直接消费它 | 保持 Step05 owner 与 Builder 单一包装 seam，不新增源码扫描、EntryDescriptor 或 regex context parser |
| EvidenceCapsuleProjector 已按连贯上下文保留完整方法、调用、候选、实参/形参、控制、退出和限制；当前版本为 flow compilation v5、flow slices v5、capsule projection v10、evidence capsule v8 | 新读取路径拒绝旧版；Flow/Capsule 正常发布重开检查来源和引用，但不会重启 JDT、重跑 compiler 或重新判断导航结果 |
| ActivityExplainer、ProcessExplainer、BusinessReportPublisher、BusinessAnalysisWorkflow 已存在，完整活动字段与具体 `UnexplainedActivityEntry` 已能沿过程/报告传递；四入口和任意 N 的 scripted 全链已验收 | 不建平行业务流水线；普通沙箱候选失败后，独立宿主会话候选以同一材料完成四项局部 Activity。它不是过程、报告或整仓验收 |
| 旧 `analysis.interpretation.{model,proposal,registry,process}` 的 78 个生产类、14 个专属测试、旧 Step06 1–9 地址、旧 artifact/schema 分支及测试 fixture policy 已删除；当前测试使用中性的 `BusinessFlowTestSupport` | 当前运行链只保留 Step06 10/11、`ModelRuntimeIdentityV1` 与 `analysis.knowledge.ProcessExplainer`；`AnalysisStepAddressTest` 拒绝 1–9、接受 10/11。四入口与任意 N 的全链验收已通过 |
| Activity v2 对任意 N 入口先做容量预检，合法但遗漏 E3/E4 的 DRAFT 会进入唯一 REVIEW；完整实际 DRAFT、`missingEntryKeys` 与 required `unexplainedEntries` 均在程序侧校验 | `activity-coverage.json` v2、`ActivityExplanationResult` 和仓库知识 v2 已保存全量 `UnexplainedActivityEntry` |
| Process/report 模型输入按 material 只投影一次 `{materialContext, unexplainedEntryKeys, reasonCode}`；全局/material ID 保留在程序侧，`MODEL_NOT_EXPLAINED` 不进入技术 receipt Gap | Prompt 要求第9章显示 context 的 HTTP 方法/路径与原因，禁止第4章为未解释入口编造活动；即时逐包 checkpoint 仍是独立未解决缺口 |
| 完整冻结 jshERP 719 文件及图/Fact 运行已有保存证据；零 Provider 全仓材料 run 为 107 包覆盖 339 个入口 | 这些是历史实测，不写成固定 K/包数，不把它们当整仓语义验收 |
| `methodCondition` 已区分 UNRESTRICTED 与 EXPLICIT 集合 | 保持该实现；未来完整仓库重跑只确认真实端点分母，不再写成代码待修正 |
| Step02 v3 入口 wire 已保存中立 `methodKey` 与完整声明 `SourceRange`，可稳定定位重载；Spring unrestricted method condition 继续合法 | JavaParser 第二阶段必须写同一最终格式，不能退回 handler 名匹配 |
| Step03 module 7 已发布 `java-code-index`；JDT 路径实际集合为 index+receipt。Step04 已发布 v4 `NOT_PRODUCED` accounting+receipt，不运行旧 Fact 枚举器，也不写空图/Fact | JavaParser 第二阶段恢复已有七图增强和严格 Fact 实际集合；不能削弱 JDT 合同或混合两个引擎 |
| JDT LS 1.61.0 与独立 JDT Core helper 已在固定 jshERP 注册/财务入口验证；正式选择 JDT 的自包含 Spring/MyBatis 运行已从 capture 一直进入 scripted 九章 | 尚未进行产品 Luna 或完整 jshERP 仓库的业务语义验收；缺依赖、多模块 classpath、反射和运行时代理仍须如实报告限制 |
| BusinessMaterialBuilder 只消费已保存的 EntryCodeContext，并把声明类型、完整方法、调用、参数、控制和限制送入模型材料；生产类不再引用 JavaParser AST | JavaParser Adapter 尚未开始；在完成第二阶段前 `javaparser` 仍为 `ENGINE_NOT_INTEGRATED` |

Spring 细则：`@RequestMapping` 省略 method 或 `method={}` 都合法。类和方法均无限制时保持 unrestricted；一方有限制时保留该限制；双方非空按 Spring method-condition combine 取并集。不要猜 GET，也不要把 HEAD/OPTIONS 框架处理拆成多个业务活动。现有 `methodCondition` 已实现该区分，UserController#getOrganizationUserTree 和 MaterialCategoryController#getMaterialCategoryTree 是直接回归样例；未来完整仓库重跑只核对新的真实分母。

旧[实施衔接](plans/coherent-code-context-implementation-plan.md)与[清理/覆盖设计](plans/code-cleanup-and-scalable-activity-coverage-design.md)用于已完成能力的历史核对，不限制当前引擎接入。JDT 合同与必要接线已经完成；后续只按第二阶段适配 JavaParser，不重做其五图/Fact算法，不复活旧语义路线。所有产品实测范围与结果仍按其原记录说明。

## 11. 阅读导航

每步文档按“为何需要 → 输入 → 处理 → 输出 → 下一消费者 → 失败与验证”展开，精确持久化规则放在附录。

- [01 已验证源码清单](analysis-steps/01-verified-source-inventory.md)
- [02 应用发现](analysis-steps/02-application-discovery.md)
- [03 程序图](analysis-steps/03-program-graphs.md)
- [04 已证明代码事实](analysis-steps/04-proven-code-facts.md)
- [05 业务流程](analysis-steps/05-business-flows.md)
- [06 流程解释](analysis-steps/06-flow-interpretation.md)
- [07 仓库知识](analysis-steps/07-repository-knowledge.md)
- [08 九章文档](analysis-steps/08-nine-section-document.md)
- [真实与合成 walkthrough](examples/semantic-framework-walkthrough.md)
- [Java代码引擎完整设计与各子模块](modules/java-code-engines/README.md)
- [JDT真实注册/财务材料到业务解释](examples/java-code-engine-walkthrough.md)
- [Prompt 与完整审阅](references/semantic-interpretation-prompts.md)
- [来源与发布边界](references/foundation-and-publication-contracts.md)
- [持久化身份](references/canonical-persistence-identity-contracts.md)
- [公共接口与源码位置](references/inherited-public-and-module-contracts.md)
