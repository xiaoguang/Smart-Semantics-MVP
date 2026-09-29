# Java 代码导航

> 2026-09-29实施中：本步骤与Step02在只负责后端的`collect-code`（R2）中共用JDT会话，前端另由R1采集。逐调用绑定归属、外部边界和查询失败的窄修正已有直接测试，新Java索引v3的fixture发布／重开也已验证；固定客户源码的具名错边定向测试通过，但全量R2在339入口导航循环后发布失败，没有可验收的Step03索引，不能宣称全量准确性通过。不重开发通用导航/多态裁决、Fact/Proof。[详细接线](../modules/technical-analysis/cli-and-runtime.md)、[JDT内部合同](../modules/java-code-engines/jdt-engine.md#3-jdtnavigationresolver让jdt决定调用连接谁)。

> [总体设计](../DESIGN.md)；固定 key：`program-graphs`，目录：`steps/03-program-graphs/`。本页拥有当前 JDT 导航的职责和验收。历史图算法已退出新生产；保留其历史读取，不要求新运行制造图、Fact 或 Proof。

## 1. 为什么存在

业务解释需要看到 Controller 如何进入 Service、条件在哪个调用方成立、参数怎样传递、返回如何使用，以及 Mapper 或外部调用停在哪里。Step03 把冻结 Java 源码变成可直接读取的声明、完整方法和逐入口调用关系。它不决定业务名称、运行时分派或流程顺序。

固定源码中 `DepotItemController.getDetailList` 的特定分支调用 `DepotItemService.getFinishNumber`，再到 `DepotItemMapperEx.getFinishNumber`。本步保留调用方完整条件、Service 参数和 Mapper 声明；SQL 内容交给 Step04。仅有“Controller → Service → Mapper”三个名字不构成本步交付。

## 2. 输入和 Interface

| 输入 | 使用方式 |
| --- | --- |
| Step01 验证清单与冻结文本 | 唯一源码、来源范围及模块/source level基础 |
| Step02 完整入口清单 | 每条含 entryId、methodKey、完整声明 SourceRange、trigger；不按名称重找重载 |
| 同一已就绪 JDT session/catalog | 复用 Step02 已读声明、Core 语法和查询缓存 |
| 有效导航配置 | 工具版本、受控源码根/本地 classpath、语言级别、超时/取消 |

入口以精确位置选定。主应用 Java17，JDT LS/Core 使用独立工具 JVM；本步骤不构建客户工程，不运行插件、注解处理器、应用或数据库，也不自行下载依赖。目标Step02先在配置允许范围完成依赖准备，本步骤只消费已核验的每模块环境和目标平台。

内部 Interface 保持 `JavaCodeEngine.open(VerifiedJavaProject)` 与 `JavaCodeSession.catalog/collect/descriptor/close`。生产 Factory 只创建 JDT。具体字段见[共同合同](../modules/java-code-engines/contracts-and-configuration.md)，工具内部算法见[JDT 模块](../modules/java-code-engines/jdt-engine.md)。

## 3. 程序具体做什么

1. 核对 entry methodKey 与完整声明范围，取得入口的完整声明和正文。
2. JDT Core helper 从原文件位置提取方法、形参、注解、语法调用点、controls/exits。源码来自原文切片，不用 AST 格式化文本替代。
3. 当前对精确 navigationSite 使用 LS hierarchy、definition 与必要 implementation；目标R2加Core逐调用方法绑定，以AST物理调用范围核对hierarchy `fromRange`归属，拒绝内外嵌套调用互串。interface、abstract、可覆盖实例方法即使 hierarchy 命中也保留实现查询；多个合法实现仍是可能候选，不选择运行时唯一实现。
4. 继续收集**归属已确认**的仓库候选完整方法；冲突/不足的工具位置和原因保留为未确认观察，不成为确定展开边。循环使用引用防环；每个方法正文全仓共享，每个物理调用位置保留。
5. 为每个候选保存有序实参/形参对照。构造器、lambda、方法引用和延迟调用保留各自归属；语法嵌套不变成执行顺序。
6. 当前外部与未解析仍可能混为`UNRESOLVED_CALL`，单次查询失败可能使入口`NOT_COLLECTED`。目标R2每条调用分开记录工具确认的外部边界、未确认目标及具体查询失败；正常外部不单独成为Gap，空导航结果不能冒称外部，超时不转成“没有调用”。
7. 按入口记录成员与展开状态，交给一次确定性索引发布。后续 publisher/reader 检查保存合同，不重演导航。

同一冻结、就绪会话内，同操作/位置最多一次真实 RPC；合法空和最终失败诊断也缓存。调用者等待超时后保留原在途请求，后续相同查询等待它或使用其最终结果，不重新发送RPC；不能把一次等待超时始终当作最终失败。该修正已通过43项直接测试，固定源码七入口已定向重验但仍有首次等待超时，实验未正式发布且调用准确性未过，具体合同见[JDT引擎](../modules/java-code-engines/jdt-engine.md#3-jdtnavigationresolver让jdt决定调用连接谁)。不同操作、不同位置或不同会话不能混用。共享 Service 的方法正文可复用，入口 A 的 BODY_INCLUDED 不能覆盖入口 B 的 NOT_EXPANDED。

## 4. 输出：共享方法，入口拥有调用

当前模块地址仍是 `PROGRAM_GRAPHS / module 7 / java-code-index`；唯一当前语义文件是 `java-code-index.jsonl`，schema `java-code-index-v2`，沿用既有 step receipt。四命令目标新生产采用index v3/技术Step03 module producer v4（Core协议v4）；现存index v2按原版本严格重开，不重写旧边。新CALL的外部身份、逐调用失败、未确认观察与可展开候选必须能被publisher/reader及Step04/05区分，不能把旧v2记录缺失的新字段默认读成已确认。

| 记录 | 内容 | 下一消费者 |
| --- | --- | --- |
| ENGINE | snapshot、descriptor、有效工具信息；既有 technicalEnhancements 原义保留 | reader 核对来源和能力 |
| TYPE / METHOD | 类型声明、完整方法正文、参数、controls/exits | Step04 关联 Mapper；Step05 取完整单元 |
| CALL | `{entryId, call}`；当前保存每入口目标/参数/展开结果；目标v3另保存工具确认程度、外部身份或查询失败，并把未确认候选与可展开目标隔开 | Step05 连贯组织阅读材料；Step04仅接确认的Mapper边 |
| ENTRY_MEMBERSHIP | seed、收集状态/原因、methodKeys/callKeys、限制 | Step05 完整入口分母 |
| DIAGNOSTIC | 实际工具/文件问题 | 下游如实保留受影响范围 |

CALL 记录以 entryId 与物理 callKey 组合定位。源码固有字段在不同入口必须一致，展开状态可以不同。METHOD 重复但正文冲突、CALL owner 错误、缺失 membership 引用均拒绝。详细 framed identity 和字段由共同合同维护。

保存的 `technicalEnhancements=NOT_PRODUCED` 只是 index v2 的既有字段，不意味着 Step04 还要安装空 Fact accounting。新索引不引用退役 graph/Fact producer，也不伪造可达性/SQL执行证明。

## 5. 模型与下游职责

本步模型调用为零。Java 只报告“导航到哪些声明/候选”“原文里有哪些控制和返回”；是否为审核、收款或库存登记由后续模型依据完整原文解释。

Step04 消费已经得到的 Mapper 声明，不重新导航。Step05 消费不可变索引，按已有关系取方法/调用，不能临时扫描 Service。Step06 新目标只重开已保存的 Step05 材料；Activity retry 不使本步重跑。Step07 读取固定 corpus 的保存结果，不打开 JDT。

## 6. 失败、停止与复用

| 情况 | 处置 |
| --- | --- |
| 外部库/Mapper 只有声明、多个候选、动态行为未知 | 保留原调用和限制，可继续取得其他安全材料 |
| 单次查询失败且 session 可用 | 保存 QUERY_FAILED 和受影响位置；本轮 Activity retry 不改变 JDT 查询策略 |
| 入口收集不能成立 | 保留该入口 NOT_COLLECTED 与原因，不缩小分母 |
| 工具缺失、协议损坏、会话不可安全继续 | 明确工具错误；不换 JavaParser、不返回空成功 |
| 来源字节漂移、URI 越界、索引引用冲突 | 失败关闭，保留已完成上游 |
| 重开索引 | 校验 bytes/schema/身份/引用，一次恢复不可变视图；零 JDT/Core/parser 调用 |

工具、源码、语言级别或有效导航基础改变才使索引失效。模型、Prompt、并发、XML 插件开关与模型输出路径不是 Java 索引失效条件。显式重做 Step04–05可复用兼容索引；这不是跨运行自动恢复命令。

目标区分源码版本和`javaAnalysisBasis`：只补classpath也会改变后者，旧索引保留供查看，不自动成为修正版。四命令目标index v3同时保存ENGINE中的basis与准备来源，以及已批准的CALL逐调用身份与边界状态。跨运行发布必须把R0 Step01与`source-preparation-result.json` / `SOURCE_PREPARATION_RESULT` / `source-preparation-result-v1`的准确 snapshot 引用绑定；`source-inventory.jsonl`的 inventory 引用即使属于同一个R0，也不能代替 snapshot。目标R2先由Step02核对环境，再具名验证Mapper、logger.error、重载、嵌套调用、合法多态、已确认外部及查询失败；发现残余错误时保留工具观察，不添加自研裁决器。就绪采集与验收限制见[Step02 Java环境](../modules/technical-analysis/java-readiness.md)。

## 7. 直接测试与实际成熟度

直接测试只覆盖本次改动：精确重载位置、中文/emoji/CRLF切片、interface 多候选、必要 implementation、构造/延迟调用、循环、共享方法与入口独立 CALL、真实 query cache 次数、空/失败区分、保存重开、损坏拒绝。普通测试使用冻结 fixture/录制工具响应；真实 JDT 单独授权且重型命令串行。

跨运行发布测试使用 prepared-source v3 R0 和独立技术 R1 Step02；即使同一 R0 的 `source-inventory.jsonl` ref 与索引自洽，也必须拒绝把它当作 `source-preparation-result.json` 的 verified-snapshot ref。

当前 JDT LS/Core、缓存、index v2、01–05接线已实现。2026-09-18 固定 jshERP 新验收取得 9,189 方法、51,675 入口归属调用；326 入口中325取得材料，`/plugin/files` definition 查询超时形成1个明确未收集。28,033次真实RPC、115,250次缓存命中。数字来自[交付核验](../supplements/jdt-persistence-reading-materials-delivery.md)，不是本轮重跑结果。

旧五图、CodeStructure、Fact依赖和图生成算法的完整历史正文可在 Git 提交 `5ceb111` 的同一文件查阅；历史 wire/receipt reader 仍按原版本校验。它们不再是当前 Step03 的实现待办或验收条件。
