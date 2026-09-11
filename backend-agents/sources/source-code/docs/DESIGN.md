# Source Code Analysis Agent：从源码到业务过程

## 1. 交付目的

读者需要知道系统做什么、围绕哪些业务对象、一次活动经过什么条件、多个活动怎样组成业务过程。证据的首要用途是可靠地找到代码；图把相关代码的结构和执行关系连起来；模型在连贯代码上下文上解释业务。最终交付是一份可回到冻结源码的九章业务报告。

这条路线保留八个步骤、五张程序图、严格技术 Fact/Proof，以及唯一公开 `RepositoryAnalysisAgent`。Java 负责来源、定位、代码关系、有限上下文、预算、检查和保存；Luna/high 负责业务含义、跨活动过程与业务语言。Java 不维护采购、销售、财务等行业词表来判业务动作。技术证据有用，但不应在每层反复证明同一件事，也不应把尚未被严格 Proof 覆盖的安全源码排除出阅读范围。

本文是目标设计。当前代码中已经存在四个业务 Module 和工作流，Step05 EntryContext 已连续传到材料，普通 Flow/Capsule 发布已停止重复 compile/project，Spring unrestricted method condition 也已落地。旧解释链及其 Capsule registry basis 字段已退出；Activity 的 v2 Prompt/schema、coverage-after-REVIEW 和程序侧未解释入口记录已落地。下游仍只传数量，尚待把具体未解释入口接入仓库知识与第九章。实现按当前实施计划分步开展；本文不把目标合同写成代码或实测结果。

## 2. 一条端到端接力

| 步骤与固定 key | 输入 | 本步工作 | 可观察输出与下一消费者 |
| --- | --- | --- | --- |
| 01 已验证源码清单 / verified-source-inventory | 已注册固定 commit 的离线快照 | 一次核对完整清单、字节、路径和行索引 | source inventory、snapshot；02 与受限源码读取器使用 |
| 02 应用发现 / application-discovery | 已验证文本和框架配置 | 发现 Spring 入口、route、参数、Mapper 候选，保留全部入口分母 | entry inventory、profile、catalog；03、05、06 使用 |
| 03 程序图 / program-graphs | 同一源码和入口 | 建结构、调用、控制、数据、证据五图，记录真实关系与局部缺口 | 五图、index、gaps；04 选事实，05 组织入口执行上下文 |
| 04 已证明代码事实 / proven-code-facts | 已建五图和版本化技术规则 | 对选定模式补充严格、可验证的 Fact/Proof，一次构建 | Facts、Proof、rejection、accounting；05/06 作为可选增强 |
| 05 业务流程 / business-flows | 全入口、五图、可选技术 Facts | 按入口直接读取已持久化调用/控制图，组织调用、实参→形参、条件、返回、边界；一次投影 Capsule | FlowSlice、Capsule、处置、覆盖；06 直接复用关系 |
| 06 流程解释 / flow-interpretation | 入口上下文、图关系、同快照源码 | BusinessMaterialBuilder 封装已有上下文；ActivityExplainer DRAFT + 完整 REVIEW | 材料、已审活动、活动或显式未解释处置闭合的入口覆盖；07 使用 |
| 07 仓库知识 / repository-knowledge | 已审活动、具体未解释入口、连接线索与必要原始材料 | ProcessExplainer 宽松分组、解释跨活动过程、整理仓库知识 | 已审过程、知识、具体 partial 范围与过程覆盖；08 使用 |
| 08 九章文档 / nine-section-document | 完整已审知识、活动/过程、具体未解释入口与来源短 ref | BusinessReportPublisher 写并审阅九章 paragraph JSON，Java 排版 | JSON、SourceRef、Markdown、验证结果；第 9 章保留未解释入口 |

01–05 不调用模型。06–08 内部正好四个业务 Module：BusinessMaterialBuilder、ActivityExplainer、ProcessExplainer、BusinessReportPublisher。它们隐藏在同一个 Agent 后，不新增业务分类器、证明层、POC runtime 或恢复系统。数值前缀只用于文档与 steps 目录排序，语义 package/key 不变。

### 2.1 四个业务 Module 的深接口

| Module | 输入 | 输出 | 隐藏的职责 | 失败/停止 | 下一消费者 | Luna/xhigh RED → Terra/xhigh GREEN |
| --- | --- | --- | --- | --- | --- | --- |
| BusinessMaterialBuilder | 同源 Step05 EntryContext/Capsule、全入口 coverage、材料 profile | `BusinessMaterialSet`、SourceRefs、global entry→material 处置 | 选择完整上下文、按配置 K 和真实 bytes 在入口边界分包、为每包生成 E1…EN | source/ref/identity 损坏 fatal；单入口无法安全成包时具体 NOT_ANALYZED；Provider 调用为 0 | ActivityExplainer；Step07 必要回查 | RED 覆盖任意 K、noFlow、零入口、映射与预算；GREEN 只改现有 Builder，不重建图/调用链 |
| ActivityExplainer | 一个完整 material、scope-local key/ref allowlist、相容 Activity profile | 完整 ReviewedActivity、global entry coverage、程序侧未解释入口记录 | 一次 DRAFT + 一次完整 REVIEW；完整实际 DRAFT 与 `missingEntryKeys` 进入 REVIEW，最终以活动或 `unexplainedEntries` 闭合 | 调用前表达/输入/输出/REVIEW 容量不相容则零请求；非法 JSON/key/ref 与 started 失败 fatal；REVIEW 仍漏项 fatal，无第三次调用 | ProcessExplainer、Step08 coverage | RED 覆盖 N=4 漏项、N≥12、跨包 E1、真实 REVIEW bytes；GREEN 落地 v2 Prompt/schema 与最小 sidecar |
| ProcessExplainer | 全部完整已审活动、recall cues、必要材料、按 material 聚合的未解释入口 | 完整已审过程、RepositoryBusinessKnowledge、process coverage | 高召回分组后由 Luna 判断有依据的多对多过程，保留独立活动和 partial 范围 | 非法成员/ref/JSON、遗漏范围却报完整、started 失败 fatal；0 活动时过程 Provider 为 0 | BusinessReportPublisher | RED 覆盖完整字段、多对多、保守独立过程和 specific partial；GREEN 只扩现有 knowledge input/save/read seam |
| BusinessReportPublisher | 完整知识、活动/过程、coverage、按 material 聚合的未解释入口、SourceRefs | 固定九章 JSON、source-refs、Markdown、validation | 一次报告 DRAFT + 完整 REVIEW；Java 只校验并确定性排版 | 缺章/非法 ref/虚假全量 fatal；显式空仓报告仍走既有 DRAFT+REVIEW；PARTIAL/INCOMPLETE 不新增 runtime enum | 业务读者与 public render/inspect/artifact | RED 覆盖第4章内容、第9章具体入口、九章/ref/纯 render；GREEN 只补 knowledge→report 与第9章，不建语义 parser |

这张表是后续实现的 Interface 与测试面。模块间传 typed immutable 结果；内部测试 seam 不扩成新的公开 Interface。Activity REVIEW 的 `unexplainedEntries`、程序侧完整记录、v2 Prompt/schema 和 coverage v2 已写入当前实现。Process/Report 的按材料聚合投影仍是下一项工作。ActivityExplainer 已升到 Module v2；repository-summary 与 report DRAFT/REVIEW Prompt、ProcessExplainer 与 BusinessReportPublisher 的 Module v2 升版只在确实接入新输入时实施；process-group DRAFT/REVIEW Prompt 保持 v1。

## 3. 为什么同时保留 03、04、05

03 是代码索引。它回答“这个调用连接谁、这个值传给哪个参数、分支在哪里、返回到哪里、原文在哪里”。它的输出是可跨入口复用的代码关系，不是业务描述。

04 是严格技术增强。当前 `FactRegistry` 只登记 `JAVA_EXACT_CALL`、`JAVA_BOUNDARY_INVOCATION`、`JAVA_GUARD_CONDITION`。每个声称成立的 Fact 必须满足该模式全部 required atoms，缺一个就拒绝该 Fact。它既不是全代码摘要，也不是所有有用信息的白名单。没有 SQL 效果 Proof，仍可以把安全定位的 SQL 原文交给模型；但不能把原文、模型推断或 hash 改名为该 Proof。

05 是入口上下文组织。它复用 03 的关系和 04 的增强，把分散在图中的代码拼成可阅读的技术执行视图，然后生成 Capsule。它不再分析一遍图来创造另一套证明层，不要求 04 先为每一个条件提供 CLOSED atom，也不要求枚举所有组合路径才能描述已经明确的分支。未知边停在边界，已知的条件、调用和结果仍保留。

因此“可选 Facts”指下游可以没有某类或某条已准入 Fact 仍然组织安全上下文。Step04 本身保留，正常八步运行仍保存其处理结果；若该步产物被提交为输入，身份损坏、断引用或伪造 Proof 必须拒绝，不能借“可选”跳过完整性检查。

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

Step05 是关系与相关代码上下文的唯一拥有者。它直接读取已保存的调用图与控制图，取完整实参/形参关系、包围条件、必要声明和返回代码，在现有 flow-slices.json 的 entryContexts 内保存一份连贯对象；Facts/Proofs 只标明少数严格结论，不能过滤可阅读的图关系。Capsule 仅作其有界投影。最小 required 字段与 nullable flowRef 合同已在 Step05 冻结，不新增链路 Module 或文件。BusinessMaterialBuilder 只消费这份对象，做容量选择、短 ref 映射与模型包排版，不重新连接图、猜 callee 或推导业务逻辑。

一个材料包至少能让读者看清入口参数、主要被调方法、条件及分支归属、结果怎样返回或传到边界，以及具体哪里不知道。片段可以是完整短方法；较长方法选连续片段并保留条件及变量定义。不能仅为“最小证据”删掉理解所需的上下文，也不能用更多随机相邻行代替已知关系。

无正式 Flow 时仍由 Step05 生成 flowRef=null 的安全入口上下文，保留 FLOW_NOT_AVAILABLE、CALL_TARGET_AMBIGUOUS 等局部限制。已定位的 Graph Gap 同样保留其源码位置，使模型读到未知调用/条件附近的原文而不会被误导为已解析 target。当前材料路径要求 Step05 已完成；若以后增加更早的无图材料模式，也必须在 Step05 创建明确的安全上下文和限制，不能让 Step06 重新扫描 Java 或猜 callee。不得把未知调用目标写成唯一调用，也不得因没有严格 condition atom 丢掉已经可读的 if/try/catch。若确实无法安全定位或超预算，则该入口明确 NOT_ANALYZED；不是删掉入口。

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

正常可信进程只建立一次源码验证视图、五图、选定 Facts、入口执行上下文和 Capsule 投影。已由拥有者完成的算法不在 publisher、specifier 或每层 reader 中重跑。内部可传不可变 typed view；有意义的步骤/module 产物和 receipts 仍保存，便于观察、定位和显式复用。

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

## 10. 当前实现与已批准但尚未实施的改动

| 当前可确认事实 | 已批准、尚未实施的最小改动或保持项 |
| --- | --- |
| Step03/04 稳定算法、FactRegistry 三类技术模式与 AtomicProofBuilder 全 atoms 规则保留；普通 persisted candidate 读取与 Flow/Capsule 发布已不再重放 owner 算法 | 本次不修改 Step03/04 算法或恢复重复 replay；清理只删除旧解释链的专属消费者/注册 |
| EntryRootedFlowCompiler 已在 flow-slices/Capsule 保存 EntryContext，传递 argument/return/data/control 与可选 Proof；BusinessMaterialBuilder 已直接消费它 | 保持 Step05 owner 与 Builder 单一包装 seam，不新增源码扫描、EntryDescriptor 或 regex context parser |
| EvidenceCapsuleProjector 已按连贯上下文保留必要 guard、变量、调用/返回及 facts/gaps/signals；Capsule 已移除两个旧 registry proposal basis 字段，并以 capsule-projection v9 / evidence-capsule v7 持久化 | 新读取路径拒绝 v8/v6；后续不能以兼容 reader 恢复这两个字段。现有完整 provenance mutation 测试有一项既有 Fact atom replay 缺口，不属于 Capsule wire 的通过结论 |
| ActivityExplainer、ProcessExplainer、BusinessReportPublisher、BusinessAnalysisWorkflow 已存在，完整活动字段已能沿过程/报告传递 | 只在现有四个 Module 上把具体 partial 下传；不建平行业务流水线 |
| 旧 `analysis.interpretation.{model,proposal,registry,process}` 的 78 个生产类、14 个专属测试、旧 Step06 1–9 地址、旧 artifact/schema 分支及测试 fixture policy 已删除；当前测试使用中性的 `BusinessFlowTestSupport` | 当前运行链只保留 Step06 10/11、`ModelRuntimeIdentityV1` 与 `analysis.knowledge.ProcessExplainer`；`AnalysisStepAddressTest` 拒绝 1–9、接受 10/11。下一项工作仅是具体 partial 下传 |
| Activity v2 对任意 N 入口先做容量预检，合法但遗漏 E3/E4 的 DRAFT 会进入唯一 REVIEW；完整实际 DRAFT、`missingEntryKeys` 与 required `unexplainedEntries` 均在程序侧校验 | `activity-coverage.json` v2 和 `ActivityExplanationResult` 已保存全量 `UnexplainedActivityEntry`；下游尚未消费这些记录 |
| 当前 Process/report 模型输入只投影 NOT_ANALYZED 数量，Activity/Process 都在循环结束后聚合 publish | 按 material 只传一次 `{materialContext, unexplainedEntryKeys, reasonCode}` 到 knowledge/第9章；即时逐包 checkpoint 仍是独立未解决缺口 |
| 完整冻结 jshERP 719 文件及图/Fact 运行已有保存证据；零 Provider 全仓材料 run 为 107 包覆盖 339 个入口 | 这些是历史实测，不写成固定 K/包数，不把它们当整仓语义验收 |
| `methodCondition` 已区分 UNRESTRICTED 与 EXPLICIT 集合 | 保持该实现；未来完整仓库重跑只确认真实端点分母，不再写成代码待修正 |

Spring 细则：`@RequestMapping` 省略 method 或 `method={}` 都合法。类和方法均无限制时保持 unrestricted；一方有限制时保留该限制；双方非空按 Spring method-condition combine 取并集。不要猜 GET，也不要把 HEAD/OPTIONS 框架处理拆成多个业务活动。现有 `methodCondition` 已实现该区分，UserController#getOrganizationUserTree 和 MaterialCategoryController#getMaterialCategoryTree 是直接回归样例；未来完整仓库重跑只核对新的真实分母。

后续按 [实施衔接](plans/coherent-code-context-implementation-plan.md) 和[已批准清理/覆盖设计](plans/code-cleanup-and-scalable-activity-coverage-design.md)做 Luna/xhigh RED、Terra/xhigh 最小 GREEN，只运行直接相关测试。重点是旧依赖安全退役、任意 N 与 REVIEW 预算、合法缺项修订、具体 partial 到第 9 章；已稳定的 Step03–05 接力不重新实现，也不开展第二轮架构扩展。

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
- [Prompt 与完整审阅](references/semantic-interpretation-prompts.md)
- [来源与发布边界](references/foundation-and-publication-contracts.md)
- [持久化身份](references/canonical-persistence-identity-contracts.md)
- [公共接口与源码位置](references/inherited-public-and-module-contracts.md)
