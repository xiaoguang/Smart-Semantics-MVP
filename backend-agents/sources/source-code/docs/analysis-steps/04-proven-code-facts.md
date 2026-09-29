# 持久化材料补全

> 2026-09-29实施中：旧独立持久化命令已在固定源码保存573条statement；新R3消费准确后端R2，不需要前端R1。SQL排序投影、新Java索引来源及R3独立发布／重开已通过直接测试，固定客户源码和全量运行仍未验收。[运行合同](../modules/technical-analysis/cli-and-runtime.md)。

> [总体设计](../DESIGN.md)；固定 key：`proven-code-facts`，目录：`steps/04-proven-code-facts/`。当前 owner 是 `analysis.persistence`。旧 Fact/Proof/accounting 为严格历史读取；新生产只发布可选持久化材料。

## 1. 为什么存在

Mapper 方法通常只有 Java 声明，实际查询、条件列和值、关联字段在 XML 中。仅解释 Mapper 名称会遗漏这些业务规则。Step04 关联已导航的声明与同源 XML，保留完整脚本和依赖，并用可选 SQL AST 帮助阅读。

例如 `getFinishNumber` 的 XML 汇总 `di.basic_number`，带商品/关联标识、删除过滤与三个动态条件。这里的目标是保存完整条件化脚本；它不是数据库执行结果，也不能独立证明采购或销售的先后。

## 2. 输入与 Interface

`PersistenceAnalyzer.analyze(PersistenceAnalysisRequest)` 是内部深模块 Interface，返回 `PersistenceMaterialIndex`。请求接收已经读好的 JavaCodeIndex、navigation publication、VerifiedSourceTextSet、Mapper目录线索和有效 PersistenceConfiguration。它不接受当前客户工作目录、Provider 或另一个 Java parser。

| 输入 | 作用 |
| --- | --- |
| Step03 Mapper 声明/候选 | FQN、methodKey、签名、形参和注解；不猜继承/重载 |
| 已验证同源 XML | 唯一字节来源；跨文件依赖只能在该 inventory 内读取 |
| Step02 Mapper 目录 | namespace/id/kind 候选；重复资源和 databaseId 不覆盖 |
| 可选插件配置 | 缺省或 plugins=[] 返回明确 DISABLED；不加载 MyBatis/JSqlParser |

独立命令在本操作首次从同源快照打开只读`MapperXmlResourceView`，不跨进程保存DOM；include变换只操作副本。不同来源不可共享DOM，不建全局缓存服务。

## 3. MyBatis Adapter 的具体步骤

1. 检查是否启用。DISABLED 只保存 HEADER，tools/resources/statements/bindings/sqlAnalyses/diagnostics均为空，Java材料仍可继续。
2. 安全 JAXP DOM读取已冻结 XML，使用 MyBatis 3.5.19 的 XPathParser/XNode读取 namespace、statement、sql片段、resultMap等。
3. 将 JDT 已提供声明按 namespace + statement id 关联。一个调用的多个Java候选、同namespace多个资源与databaseId变体逐项保留；不存在的唯一运行时实现不补出来。
4. 保留有序 Java 实参/形参与 `@Param`、XML placeholder paths；别名不足或动态属性含义不明时记录限制，不执行客户反射或对象求值。
5. 保存完整 statement XmlNode，按顺序保留 ELEMENT/TEXT/CDATA/COMMENT、属性和动态标签；建立 include/resultMap/selectKey 与跨文件依赖。
6. 使用官方 XMLIncludeTransformer 对可确定的静态 include 工作副本作展开；动态引用、循环或缺片段保留未解析原因，不复制框架运行语义。
7. 在分析副本上调用 JSqlParser 5.3，导出可解析结构与限制。一次发布保存结果；下游重开不再次解析。

不调用客户 XMLMapperBuilder 全配置启动、getBoundSql、OGNL、自定义 LanguageDriver、数据库或客户应用。安全策略接受标准 Mapper DOCTYPE，同时禁用外部DTD、实体、schema、XInclude和网络读取。

## 4. XML 与 SQL 保存什么

| 记录 | 精确职责 |
| --- | --- |
| Resource | resourcePath、namespace、rawSource、dependencyResourcePaths；每文件原文一份 |
| Statement | statementRef、resourceRef、namespace/id/kind/databaseId、完整 xmlSubtree、dependencyRefs |
| JavaBinding | 已有 methodKey、签名、candidateNature、有序参数、statement变体与limitations |
| SqlAnalysis | statementRef、analysisCopy、transformations、分层ast、PARSED/PARTIAL/UNSUPPORTED及reason |
| Diagnostic | code、subjectRef、detail；不写业务判断 |

XmlNode 是从安全 DOM 保存的结构化投影，不是逐字 XML 引用。没有可靠块行号时，来源继续指向冻结 XML 整文件；不得通过新 tokenizer 虚构精确位置。Step06 可以从保存的 XmlNode 生成完整 statement 阅读投影并标为结构化投影，技术 Resource 原件不变。

SQL增强按如下规则保真：

- 静态完整语句把 `#{...}` 仅在分析副本映射为参数；原 token 与变换说明保留。
- 动态 if/choose/foreach/trim 等完整XML始终保留；可独立解析的语句/表达式分别调用官方 parser。
- 分析骨架明确标注“无可选块”，不能当唯一实际SQL；不穷举组合或将互斥分支无条件拼接。
- 保留 SELECT/写入、聚合、JOIN/ON、WHERE、子查询、列/值及赋值层级；不把 ON 移到 WHERE，不把结构扁平为表名清单。
- 动态表/列、方言或不完整片段失败时保存 PARTIAL/UNSUPPORTED，仍可读完整脚本。
- `insertSelective` 的条件列和值两处都在完整statement内；投影与分包不得只留下其中一侧。

工具复用的已测范围与局限见[实验报告](../supplements/persistence-tool-feasibility-result.md)。

## 5. 发布和下一消费者

历史v1地址为 Step04 module4 `persistence-analysis`；语义文件 `persistence-material-index.jsonl`，schema `persistence-material-index-v1`，artifact `PERSISTENCE_MATERIAL_INDEX`。沿用既有 AnalysisStepPublicationReference、canonical store、receipt与原子发布。

Step05按 Java 调用关联引用选取资源、语句、绑定和SQL结构，不自行推断表用途。Step06模型才能结合 Java 条件与XML解释数量/写入口径；不能把 AST PARSED 等同业务已经确认。

旧 `proven-facts.json`、`proof-pack.json`、`gap-ledger.json`、`fact-accounting.json` 及原receipt仅按历史合同重开，既有 CLOSED Proof 含义不变。不新安装空accounting，也不把它们作为新材料准入门槛。

## 6. 失败、复用与测试

| 情况 | 处置 |
| --- | --- |
| 未启用/项目无MyBatis/无Mapper | DISABLED或合法空材料；不影响Java读取 |
| 多资源、缺include、未知参数别名、SQL不支持 | 保存候选/原文/具体限制，不假定完整 |
| 单XML非法或不安全 | 拒绝该资源解析并保存原因，不换不安全parser |
| 不能落实安全配置 | XML_SECURITY_POLICY_UNENFORCEABLE，停止运行 |
| 冻结字节损坏、越界、来源冲突 | 失败关闭；不按普通解析限制忽略 |
| 保存重开 | 验schema/身份/引用/原文，返回完整不可变索引；零解析/模型/数据库 |

直接测试覆盖：插件关闭零工具、@Param与多参数、重复namespace/databaseId、跨文件include/resultMap继承、动态条件/列值保持、静态与部分SQL、标准DOCTYPE安全拒绝、保存重开和缺依赖保留。不同领域fixture不得要求修改Java词表。只跑直接覆盖测试。

## 7. 本轮SQL排序投影修正（代码及定向测试已接通，固定源码待验收）

已核实JSqlParser 5.3能返回SELECT的排序项；旧版 `plainSelectNode` 未调用其排序getter，导致analysisCopy里的ORDER BY未进入保存AST。本轮已接入已有AST的排序项投影，并通过直接测试；固定源码R3的真实发布、重开和R4最终入口JSON仍待核验。这是我们的投影遗漏，不是客户SQL缺失，也不要求更换SQL parser。[固定5.3官方Select源码](https://github.com/JSQLParser/JSqlParser/blob/jsqlparser-5.3/src/main/java/net/sf/jsqlparser/statement/select/Select.java)提供getOrderByElements；其它核查见[工具依据](../supplements/mybatis-dynamic-sql-parser-tools-research.md)。

最小修改：

1. 在既有SELECT投影的相应层读取工具返回的有序排序项，保存表达式、显式ASC/DESC或未指定、工具支持的NULLS顺序信息。内层/外层SELECT及集合查询分别挂在其实际owner，不能拉平成一个全局排序表。
2. 不手写字符串SQL解析。字段缺失/工具不支持时保留原SQL及具体限制，不能以省略字段假装结构完整。
3. 保存分析副本、变换和完整AST。原XML的if/foreach/choose等完整结构和原文不变；未展开动态条件仍为PARTIAL，不声称得到运行时最终SQL。
4. 持久化schema从v1升v2、技术module producer从v2升v3，同时接受准确Java index v3及其分析基础。R3保存自己的owner与准确R2，不要求前端publication非空。
5. 新R4只从本入口所纳入Mapper目标取得相关绑定/资源。被Java索引明确排除的错误候选不作为本入口SQL入口；R3全仓目录中存在其它Mapper本身不算污染。
6. reader/policy/fixture同步，不改历史schema v1及producer v1/v2文件。旧SQL结构不补造新字段，不把未知排序写成“无排序”。

验收：带子查询内外排序、多个排序项、ASC/DESC未显式、工具支持的NULLS、动态SQL原文、无排序及不支持表达式；先经真正JSqlParser，再发布重开并检查最终入口JSON。只测本修改及现有直接消费者，不运行数据库、MyBatis动态求值或模型。

## 8. 当前成熟度

算法的实现与同运行保存/重开已完成。技术命令另有一条 R2 v2 跨运行发布/严格重开路径：R0 Step01 总是通过 source-preparation policy store 重开，R1 Step02/03 和 R2 Step04 总是通过 technical policy store 重开；v1 的同运行发布/读取守卫未放宽。`TechnicalAnalysisSourceAdmissionTest#analyzePersistencePublishesR2Step04FromExactR1WithoutJdtOrNode` 用真实 Agent 生命周期验证 R2 owner、准确 R0/R1 ancestry、重新打开的 ENABLED 空索引以及零 JDT/Node。它是 fixture 级接线证据，不是固定客户源码验收。

固定仓库启用结果为61份XML、573条语句、572条Java绑定、573份SQL分析，其中187 PARSED、162 PARTIAL、224 UNSUPPORTED；同一索引关闭插件时所有明细为0且没有再次JDT。来源为[2026-09-18交付核验](../supplements/jdt-persistence-reading-materials-delivery.md)。

Step05→Activity的后端/XML投影曾另行实现并保存418条新Activity，具体范围与限制由[Step06](06-flow-interpretation.md)维护；该历史事实不等于新增前端材料已被消费。本轮代码和直接测试已覆盖上节排序增量及新R3接线，仍须用固定源码真实验收；不重做持久化算法、不运行Step06。
