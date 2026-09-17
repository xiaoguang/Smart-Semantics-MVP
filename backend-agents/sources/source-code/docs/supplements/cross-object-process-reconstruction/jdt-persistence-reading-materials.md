# JDT 导航、可选持久化补全与统一阅读材料：详细设计

状态：2026-09-17，目标详细设计已批准实施；**A工具实验通过，B生产迁移与C验收尚未完成**。实际工具结果与限制见[实验报告](../persistence-tool-feasibility-result.md)。本文件属于原补充设计，不是另建一套业务流水线。工具资料见[持久化调研](../persistence-analysis-plugin-research.md)；此前实施问题见[待讨论清单](../implementation-lessons-and-followups.md)。

## 1. 目标、范围与验收边界

本次只交付第1—5步：从固定仓库自动取得入口、完整 Java 调用材料和可选持久化原文，保存为可直接打开阅读、以后可供模型使用的材料。目标不是新的 Fact、Proof、SQL 执行结果或业务解释。

```text
01 固定源码与文件清单
  → 02 识别应用与精确入口
  → 03 JDT 导航与 Java 源码索引
  → 04 可选持久化补全：Mapper XML + 可解析 SQL 结构
  → 05 一次组装、保存并导出入口阅读材料
  → 到此结束；不启动第6步
```

实施验收必须依次通过三道门：

1. **A：先做工具调研实施。** 用真实冻结 XML 和小型通用 fixture 验证官方 MyBatis 部件与 JSqlParser 能交付什么，先展示结果，再决定采用范围。
2. **B：A 通过才全面实施。** 迁移第3—5步生产接线，移除 JavaParser 与严格技术证明生产路线，保留现有 JDT 导航能力、历史结果和必要 reader。
3. **C：全面实施后验证第1—5步。** 真实固定仓库生成新材料，启用/不启用插件均验证；来源、内容、性能与停止位置可检查。**第6步及以后的执行次数、模型请求数均为0。**

本次写设计不等于现在启动 A/B/C。下一份实施计划必须按此顺序安排，不得用“工程已接线”替代 A，不得用“材料已生成”宣称业务质量已通过。

### 1.1 保留与明确退出

| 保留 | 退出新的生产路径 | 本轮不做 |
| --- | --- | --- |
| 冻结源、合法 Spring 入口、JDT LS/Core、完整方法及声明、调用候选、参数、条件/返回、查询缓存、原子存储 | JavaParser Adapter、专属图构造算法、Fact 候选枚举、AtomicProofBuilder、严格 Flow 生成、普通路径重复 compile/project | JPA/Hibernate 实现、客户构建/应用/数据库、SQL 执行、OGNL 求值、Activity 重建、过程重建、九章 |
| 旧源码/JDT/326条已审 Activity、业务过程、原始模型稿件及日志 | 以 Fact accounting/五图/严格 Flow/Capsule 成功作为材料准入条件 | 全局恢复框架、另一个 CLI、热加载插件平台、行业规则引擎 |

“保留现有能力”指保留 JDT 已取得的实际代码内容和查询能力，不再要求维护用户已明确放弃的 JavaParser/Fact/Proof 计算。历史 reader 可以保留格式校验，但不得为了读取旧数据再次调用这些算法。

## 2. 当前代码起点与实际修改面

本次对照正式 checkout，Git HEAD 为 `0784a33`；工作区另有前次三阶段业务解释改动，不能重置、覆盖或纳入本轮未授权的修复。

| 当前实际实现 | 本设计处理 |
| --- | --- |
| JDT LS 定位，独立 JDT Core helper 提供声明、正文和语法记录；当前 JDT 路径不调用 JavaParser | 保留，不重做 Java 类型系统。删除另一路 JavaParser 后须做编译依赖隔离检查 |
| `JavaCodeIndexPublicationSpecifier` 已按 METHOD 共享正文，CALL 保留入口归属 | 保留 index v2；不为插件把所有调用重新导航一次 |
| `MapperCapabilityCataloger` 有安全 DOM 与 namespace/statement 候选，但未交付完整 Java→XML→SQL 材料 | 复用读取和候选逻辑；新增补全归第4步，不把目录发现扩成 SQL 分析 |
| `TechnicalAnalysisWorkflow` 仍调用 `ProvenCodeFactsExecutor`；JDT 下产出 NOT_PRODUCED accounting | 新路径完全绕开，不再生成占位 Fact accounting |
| `EntryContextAssembler` 检查 Fact accounting；`BusinessFlowsExecutor` 仍 compile→project→发布 | 新第5步取代其材料职责；不再保留三层生产包装 |
| `BusinessMaterialBuilder` 在 Step06/M10，依赖旧 Step05 五文件；源码同时存在 sourceRefs 与 modelPacket | 材料职责移入第5步；规范存储不重复保存正文，阅读投影按需生成 |
| `AnalysisRunOutput`、材料 state、artifact 查询和材料 reader 固定认 M10 | 同一交付内增加明确的新材料合同；历史 M10 只读能力保留，不能全局放松归属检查 |
| `plan-materials` 经 technical executor 后仍调用 M10 Builder | 新实现必须直接完成第5步材料登记并结束，不以“没有模型调用”掩盖执行了第6步 |

可定位实现：[技术编排](../../../src/main/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflow.java)、[导航索引](../../../src/main/java/org/sourceanalysis/app/analysis/code/publish/JavaCodeIndex.java)、[现有 Mapper 目录](../../../src/main/java/org/sourceanalysis/app/analysis/discovery/MapperCapabilityCataloger.java)、[旧材料读取器](../../../src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialCheckpointReader.java)、[运行输出](../../../src/main/java/org/sourceanalysis/app/runtime/AnalysisRunOutput.java)。

## 3. 五步职责：每项内容只由一处取得或组织

| 步骤 | 输入 | 内部动作 | 输出与下一消费者 | 不做 |
| --- | --- | --- | --- | --- |
| 01 固定源码 | 显式指定仓库版本或已有冻结快照 | 核验清单、原文字节、文件身份、文本范围；保持 Java/XML/Vue/配置等文件可读 | `VerifiedSourceInventory`及已有读取能力，供全部后续步骤 | 不构建客户项目；不因模型失败刷新源码 |
| 02 识别应用和入口 | 已验证清单、同会话 JDT declaration catalog | 识别 Spring 应用与路由，保存 entryId、methodKey、完整声明范围；保留轻量 Mapper 候选目录 | 应用画像、完整入口分母、Mapper资源线索 → 03/04/05 | 不把 Vue 页面当 HTTP 入口；不解析 SQL；不因 Mapper 资源重复而删除合法 Controller |
| 03 Java 导航 | 全部精确入口、冻结 JDT 会话 | 查定义/实现/调用，收集完整方法、接口声明和调用结构，按入口记成员 | 共享 `JavaCodeIndex` → 04关联、05组包 | 不运行 Fact/Proof，不分析 SQL，不重新定位已经得到的同一调用 |
| 04 持久化补全 | 索引、冻结 XML、显式插件配置 | 关联 Mapper、取得脚本与依赖、可选解析 SQL 结构 | `PersistenceMaterialIndex` → 05 | 不决定业务流程、不连接数据库、不把动态 SQL 简化成唯一执行结果 |
| 05 阅读材料 | 入口分母、Java索引、可选持久化索引 | 一次选择完整阅读单元、组织调用链、分配短引用、保存和导出 | `CodeReadingMaterialSet`、逐入口覆盖、可读预览 | 不导航、不再解析、不调用 ActivityExplainer/Provider |

第2步需要声明目录，可以与第3步共享已有 JDT session/catalog；这不表示第2步递归收集全部调用。XML 第2步只作目录线索，第4步才解析相关完整脚本；同一运行复用安全读取的 DOM，重开时读取保存索引，不以“验证”重新分析。

### 3.1 模块 Interface

保留既有 `JavaCodeEngine.open`、`JavaCodeSession.catalog/collect/descriptor/close` 的小 Interface，生产只构造 JDT 实现。删除 JavaParser 不要求把 LS/Core 内部类型泄漏到下游。

新增的持久化 seam 与材料 owner 位于现有 source-code 模块内，概念签名如下；名字与字段语义为目标合同，非声称当前已有类：

```java
interface PersistenceAnalyzer {
    PersistenceMaterialIndex analyze(PersistenceAnalysisRequest request);
}

final class CodeReadingMaterialBuilder {
    CodeReadingMaterialSet build(CodeReadingMaterialRequest request);
}
```

- `PersistenceAnalysisRequest`：同源 JavaCodeIndex、已验证冻结文本读取能力、Mapper资源线索、有效插件配置。MyBatis 是第一种 Adapter；未启用时跳过解析和关联，仅保存DISABLED头记录，不建一个伪 parser。
- B的直接输入接线使用既有`JavaCodeIndex`、`ProgramGraphsReference navigationPublication`、已读的`VerifiedSourceTextSet frozenSource`、`List<MapperCatalogEntry>`和`PersistenceConfiguration`。传入已读不可变视图避免再开同一批文本；不引入客户路径或新取证接口。禁用配置返回明确DISABLED头记录且不遍历XML，完整资源/语句等子记录均为空。
- `CodeReadingMaterialRequest`：完整入口清单、索引的不可变已读视图、可空持久化结果、材料大小配置。不接受 Provider、JDT client 或任意当前工作目录。
- 发布与重开复用当前 canonical store；builder 内部保存前组装一次，reader 只还原视图，不重复 `build/analyze/collect`。
- 可读预览和以后请求的自包含材料使用同一个文本投影函数。只导出文本不是执行第6步；本轮不改变 Activity Prompt、响应结构或业务调度。

这是两个深模块，不为关联、XML读取、SQL Visitor、引用投影分别增加公共 Agent、独立存储框架或可部署服务。

## 4. 第3步：完整保留 JDT 能力

### 4.1 内部顺序

1. 使用第2步精确 `EntrySeed`，不能以类名＋方法名重找重载。
2. 从会话 catalog/Core 结果取得完整声明、正文、形参及注解，含无正文接口、构造器、方法引用、嵌套调用等当前已支持记录。
3. 对调用位置使用既有 hierarchy/definition/implementation；保留多个目标与每种查询的原始结果。
4. 继续展开仓库内可导航实现；外部、无源码、未解析与达到限制的调用分别保留原因。方法去重，所有物理调用位置和入口成员保留。
5. 保存既有 index v2。调用所属 if/else、try/catch、return/throw 既有语法信息和完整正文一起保留，不新增 CFG 证明。

index v2现有 `technicalEnhancements` 字段保持原义的未生成描述，不能伪造增强；保留这个旧字段不意味着继续生成第4步Fact/accounting文件。若未来确需删除该字段，应单独升index版本，不能原地修改v2。

### 4.2 一次查找与合理停止

缓存仍按冻结会话、操作和位置区分；同一 Service 被多个入口经过时，不重复向 JDT 查询相同位置。入口自己的实参、展开状态和限制不能由另一个入口替代。

源码、classpath、工具版本或有效导航配置变化才建立新会话。XML 插件开关、SQL parser 版本变化不使已保存 Java 索引失效；仅重做第4—5步时应可直接重开兼容的第3步检查点。

无法解析 Java 目标不表示没有调用；照常输出该调用原文。第4步不能按简单方法名猜 Mapper 来“修复”JDT。允许从 JDT 已给出的接口声明或候选关联 XML，并保留候选性质。

## 5. 第4步：MyBatis 官方解析部件与 SQL 增强

### 5.1 优先工具组合及自研界线

| 工作 | 优先复用 | 本框架只补什么 |
| --- | --- | --- |
| XML 安全解析与节点读取 | 现有安全 JAXP DOM；MyBatis `XPathParser(Document)` / `XNode` | 从已解析节点提取材料，不自己写 XML tokenizer/parser |
| namespace、语句、resultMap、sql片段 | MyBatis读取部件及现有目录逻辑 | 与JDT方法关联、建立材料索引，不启动整个应用 |
| `<include>`展开 | 官方 `XMLIncludeTransformer`，先在调研中验证窄范围复用 | 注册冻结片段、保留原引用及依赖；不自己实现第二个include解释器 |
| 动态标签结构 | 官方 `XMLScriptBuilder` 能构造 `SqlNode`；原 DOM 保留所有条件和文本次序 | 优先使用可读节点投影；不以反射私有字段或复制全部 handlers 作为生产方案 |
| SQL语法树 | JSqlParser | 使用其Visitor导出需要的结构与位置关联，不手写SQL文法 |

完整 `XMLMapperBuilder` 确实是现成解析器，但还会解析客户类型/映射配置；`getBoundSql` 又会执行条件和表达式。**调研先确认可直接复用多少，不把“不能输出本项目JSON”作为自研理由。** 现有安全 DOM 接 `XNode` 保留节点与原文，是读取投影而不是重写动态SQL执行语义。

MyBatis `SqlNode` 并非完整 SQL AST，部分节点未提供公开遍历字段；如果直接导出要复制其运行时实现或大规模反射，则采用原 DOM 条件结构，不开展自研等价运行器。是否使用完整 Builder、限定子部件，必须在 A 的报告中列出实测 API 与限制。

工具依赖固定到A实际通过的MyBatis 3.5.19与JSqlParser 5.3；版本、校验值、许可证及Java17实测见实验报告。生产代码独立接入，不依赖研究工程。只下载/构建分析工具自己的依赖，不运行客户Maven/Gradle。

### 5.2 Java Mapper → XML 关联

1. 遍历索引中已有的 Mapper 声明/调用候选，保留声明 FQN、methodKey、方法名、形参、`@Param` 与完整声明。
2. 按 XML 的 `namespace + statement id` 关联对应接口和方法。不同文件的同名资源、`databaseId`变体全部保留；不可用 Map 后写覆盖前写。
3. 一个Java调用有多个候选时逐候选关联，不伪造运行时唯一实现。继承方法使用 JDT 已取得的声明/类型信息；该信息不足就记录待解析，不增加自研继承解析。
4. 保存有序 Java 实参→目标形参的已有位置关系；`@Param` 别名与XML占位符路径并列呈现。未显式命名、依赖编译参数或复杂对象属性时，不猜唯一参数名；保留表达式、序号与声明即可。
5. 未被入口命中的 XML 语句仍可留在全仓资源目录，但不硬塞到每个入口，不当作已经执行的代码。

第2步现有 `MapperCapabilityCataloger` 对重复 namespace 会失败。迁移时在持久化资源路径改为候选列表/局部诊断；重复 Mapper 不应终止全部 Spring 入口发现。该调整是支持实际资源选择的直接接线，不重写应用发现算法。

现有Mapper目录若不能表达多资源变体，新增该目录的明确schema版本与直接reader，旧目录按原合同读取；不改变HTTP入口格式，也不在旧schema中暗改唯一性。目录只提供候选，不承担第4步完整解析。

参数对照至少覆盖显式 `@Param`、单标量、单Bean/Map、集合与多个参数。`param1/arg0/list/collection`等名字取决于框架规则及配置，不能只凭ordinal生成一个确定映射；不运行客户反射或读取动态对象求值。继承、重载、特殊参数和属性路径有歧义时保留Java声明与XML原名供阅读，不能删掉语句。

### 5.3 取得完整脚本，不执行脚本

每个命中语句保存：原 statement 块、其 `namespace/id/databaseId`、使用的 `<sql>` 片段、相关 resultMap 与 selectKey、原引用关系、对应文件范围。依赖递归按已访问键去重；循环/缺失引用保留诊断，不无限展开。

原始内容包含 `if/choose/when/otherwise/foreach/where/set/trim/bind` 的标签、属性、文本/CDATA及顺序。条件作为表达式字符串保留，不执行 OGNL，不构造虚假参数，不实例化客户类型或自定义 LanguageDriver。

DOM序列化会改变空白/实体表示，不能冒充原始片段。优先复用现有位置能力；工具不给精确块范围时，允许引用冻结XML整文件，并将从DOM导出的statement明确标为结构化投影。不得为行号另造XML tokenizer，也不因只能定位到文件而阻断工具验证；A报告必须展示采用哪一种定位方式及其材料大小影响。

官方 include transformer 只在 namespace、片段与静态 property 可确定时展开**工作副本**；原文、原引用仍保存。未知 `${...}`、循环引用或未提供的片段保持未展开，不为凑齐结果调用客户框架。来源依赖映射只定位原块，不创建逐 token Proof。

标准 MyBatis DOCTYPE 可读，但外部实体/DTD/网络解析维持现有禁用设置。安全控制本身无法设置/保证时，整次报 `XML_SECURITY_POLICY_UNENFORCEABLE`；单个非法或不安全XML记录资源原因并拒绝解析，不走不安全parser。冻结快照字节损坏/越界也是运行错误，不能作为普通“不支持”忽略。跨文件依赖只能读取同一inventory中已冻结成员，缺少配置/片段时记录缺口，不读当前checkout、依赖jar或网络补齐。

### 5.4 SQL 分析怎样做，怎样避免写成另一个编译器

第一版采用两种输出：**完整条件化 XML 原文是基础；可确认 SQL AST 是增强**。

1. 没有动态控制的完整 SQL：把 `#{path,...}` 在分析副本中映射为值占位符，保留原 token 与来源映射，调用 JSqlParser。
2. 有动态标签：先保存完整脚本与条件树。对本身构成完整语句或表达式的静态片段，分别使用 Statement/Expression parser；条件、片段位置仍绑定原 XML 节点。
3. `AND/OR` 引出的条件片段可在明确记录去掉连接词的分析副本上解析；不能把分析副本当原文，也不能把多个互斥分支无条件拼接。
4. 只有去掉可选块后恰好得到完整语句时，可以生成带说明的“无可选块分析骨架”；它不是一条已确认可执行 SQL。不能为了使其语法通过补业务值、补表、补列或枚举 `2^n` 种分支。
5. `<foreach>`、动态表/列 `${...}`、不完整列表、方言不支持等无法无损形成 AST 的部分，保留原文和具体限制。不把 `${table}`替成普通值参数，不把独立的列/值条件合成任意组合。
6. visitor 保留语句类型、表/别名、投影、聚合、JOIN/ON、WHERE、分组、子查询、赋值/写入列和值表达式的层级。不能把 ON 条件移到 WHERE，也不只输出一份扁平“表名列表”。

SQL内容是SELECT还是UPDATE从可解析语句确定，不只靠 XML 标签；例如 `<select>`也可能承载返回数据的写语句。解析结构只帮助阅读查询/写入口径，不生成“这是采购”“必须先审核”等中文业务结论。

### 5.5 插件配置与扩展

目标 YAML 在原 `--config` 内增加一个可选字段，既有 JDT配置保持：

```yaml
sourceAnalysis:
  javaEngine: jdt
  jdt:
    installation: /opt/source-analysis/tools/jdtls
    javaHome: /opt/source-analysis/tools/jdk
  persistence:
    plugins:
      - type: mybatis
        sqlParser: jsqlparser
```

- 不写 `persistence` 或 `plugins: []`：不做持久化增强，JDT材料照常保存。不是默认猜测启用。
- 配置 `mybatis`：加载本框架自己的 MyBatis/JSqlParser 依赖；客户POM仅作为被分析文本，不能控制宿主插件加载。
- 第一版只接受 `mybatis/jsqlparser` 组合；未知插件/参数启动前报配置错误。允许未来同仓多种持久化 Adapter，不预建 JPA 分支或解析器矩阵。
- `javaEngine: javaparser` 在新执行中明确拒绝，不自动切为 JDT；历史配置由历史 reader 读取，不参与新执行。
- 依赖/有效配置随第4步结果保存。改此配置只影响持久化与材料结果；不影响已保存 Java索引与326条旧Activity。
- 未配置时可以不要求模型服务/登录存在；第1—5步启动根本不创建 Provider。

未来 JPA 可以输出实体/关系、JPQL/HQL、derived method、native SQL等原生材料，不强制还原最终SQL。因此插件共用的是“关联来源＋查询表示＋限制”，不是必须有SQL字符串。

### 5.6 持久化索引的数据合同

`PersistenceMaterialIndex` 使用现有 canonical JSONL保存，不持久化第三方 Java 对象类型名：

| 记录 | 必需内容 |
| --- | --- |
| HEADER | snapshot与Java索引引用、producer、实际插件/工具版本、状态 ENABLED/DISABLED |
| RESOURCE | 冻结文件位置、namespace、资源变体、相关原文单元；同一原文单元保存一次 |
| STATEMENT | statementKey、id、XML kind、databaseId、原文单元引用、include/resultMap/selectKey依赖、条件结构 |
| JAVA_BINDING | methodKey、候选性质、statementKey列表、形参与占位符对照及限制；不冒称执行绑定 |
| SQL_ANALYSIS | 原statement/片段引用、分析副本的转换说明、AST投影、状态 PARSED/PARTIAL/UNSUPPORTED及原因 |

禁用插件时仅 HEADER，明确DISABLED；没有命中时 HEADER＋实际资源诊断。不是伪造“零Fact全部证明”，也不为每个未命中点创建外部效果证明。多调用共享相同 statement记录；调用位置仍留在Java索引。

## 6. 第5步：唯一阅读材料 owner

### 6.1 组装顺序

1. 以第2步全入口清单为分母，按第3步入口成员取既有方法与调用。
2. 读取命中方法的持久化 binding及其完整原文依赖。不因Mapper没有body而过滤其声明/参数注解。
3. 复用现有按完整单元选材的大小控制，默认不增加摘要/关键词筛选。一个单元可以是完整方法、完整Mapper声明、statement及必要片段依赖。
4. 每个包保存调用树/环引用、实参形参、条件/返回、候选与停止原因。相同方法正文不重复存储；同一包中的多业务用途不在此推断。
5. 分配程序侧短引用，保存原文位置与索引单元引用；按稳定顺序保存材料与入口处置。未选内容给出具体标识/原因，不能只写“预算限制”却宣称完整。
6. 需要打开检查时，reader从上游已保存单元生成自包含文本：完整Java＋Mapper声明＋XML＋可用SQL结构；不调用JDT、parser或Builder重算。

选择顺序固定为入口声明/正文、按已有调用关系的首次遍历顺序加入完整方法，Mapper命中同时加入声明与对应持久化单元；并列项以现有稳定key排序。材料profile使用 `maxPacketUtf8Bytes`与`maxEntriesPerPacket`，前者按实际完整自包含文本的UTF-8字节计算，含调用、参数、完整源码/依赖和SQL结构；不继承旧 `maxLinesPerRef` 截断行为。目标单元放不下时保留该调用和明确未选目标；一个最小入口单元也放不下时记NOT_COLLECTED。全仓预览可以按包流式导出，不以单包limit承诺整个文件的大小或模型可容纳。模型缺口不能靠扩大“已完成”定义掩盖。

### 6.2 持久化与读取视图

规范材料 `code-reading-materials.jsonl` 保存 HEADER、PACKET、ENTRY_COVERAGE三类记录：

- HEADER：producer、完整上游引用、实际取材配置、来源快照。
- PACKET：materialId、entryIds、入口说明、方法/调用引用、持久化引用、SourceRef→单元及位置映射、已选/未选范围与限制。
- ENTRY_COVERAGE：entryId、材料ID集合、`COLLECTED / COLLECTED_WITH_LIMITATIONS / NOT_COLLECTED`及具体原因。

方法正文以第3步METHOD记录为唯一派生存储；XML单元以第4步RESOURCE为唯一派生存储。第5步不再把相同snippet分别保存到sourceRefs和modelPacket。原始快照作为不可变原件保留，不视为应删除的“重复正文”。

读取后的 `CodeReadingMaterialSet` 是完整只读视图，包含实际正文而非未解析文件键；来源JSON/文本预览只是该视图的确定性导出，不能成为下一层再加工的生产输入。同一次读取复用已验证索引，不逐入口重新打开全仓。

可读 `reading-materials.md` 按入口给出短编号调用树、参数对照、完整代码块、XML及未展开清单。正文可包含源码，因为它是技术阅读材料，**不是业务过程文档**。文件/行号可直接查看，不要求人手工拼接几十个ID。同数据重开导出逐字节一致。

材料容量只表示内存/输出大小与完整单元选择，不是费用预算或本轮模型上下文承诺。本轮不绑定模型；第6步以后是否要按模型上下文重新分包，留给后续设计。不能通过截掉半个方法/动态块使材料“通过”。

### 6.3 当前材料与未来第6步的关系

本次把**材料生产职责**从M10移到第5步，不顺带重写 Activity解释。第5步出口完成后，本次新运行的Activity/过程/报告checkpoint全为空。

已有旧M10材料、326条Activity和其原来源关系继续可读；旧Activity生成的显式功能不因本轮清理悄悄调用新材料，也不能把它们改标为含XML增强的新版Activity。新材料如何进入第6步、是否新增字段、模型请求怎样使用SQL，后续单独讨论。本轮只证明材料已经可完整读取和导出，不宣称新业务链已端到端通过。

## 7. 运行、版本与历史迁移

### 7.1 名称与兼容范围

人类可见步骤名称改为“Java代码导航”“持久化材料补全”“入口阅读材料”。内部新生产类放在 `analysis.code`、新增 `analysis.persistence`、新增 `analysis.material`；不新增 numbered package。

本轮不做全工程WireReset：既有 `AnalysisStepKey.PROGRAM_GRAPHS / PROVEN_CODE_FACTS / BUSINESS_FLOWS` 暂作为03/04/05**存储槽位**保留，旧目录/receipt名字仅用于定位。它们不再表示新路径执行旧算法；新moduleKey/producer/artifact明确区分。若未来需要改存储key，再独立迁移，不在本次附带改所有历史身份。

| 位置 | 新生产合同 | 历史处理 |
| --- | --- | --- |
| 03 module7 `java-code-index` | 保持 `java-code-index-v2`与既有Java信息；只写JDT产物 | JavaParser旧索引及图仍按旧格式只读 |
| 04 新module4 `persistence-analysis`，producer v1 | `persistence-material-index.jsonl`，`persistence-material-index-v1`；类型 `PERSISTENCE_MATERIAL_INDEX` | 旧Fact模块1–3/accounting不再生产；历史reopen保持 |
| 05 新module4 `code-reading-materials`，producer v1 | 单文件 `code-reading-materials.jsonl`，schema `code-reading-material-set-v1` | 旧compilation/Capsule/五文件仅历史读取，不双写 |
| 新材料状态 | `repository-run-state-v4`：CODE_READING_MATERIALS checkpoint kind及完整ref；精确字段见下文 | 旧state-v3与显式旧版导出读取保留，不覆盖旧状态 |
| 新运行输出 | `analysis-run-output-v5`：新增`readingMaterialCheckpoint`；本轮新kind `READING_MATERIALS_ONLY` | v3/v4按原义读取；不将新ref塞进旧M10字段 |

结构材料artifact类型为 `CODE_READING_MATERIAL_SET`，媒体类型为JSONL。`reading-materials.md`由同一reader按需导出到用户指定或本次私有检查目录，不是第二个canonical payload，不成为下游输入；导出有独立的格式版本标记，不改变材料身份。现有store的精确文件集合、Step最终publisher地址、前驱要求、artifact策略与查询分支必须一并更新。第4步新模块不需要旧图，第5步新模块不需要Fact publication。

版本化state/output reader仅按明确schema分派，未知版本报错。对于v5本轮新kind：sourceRunId必须拥有readingMaterialCheckpoint；旧businessMaterialCheckpoint和三个模型输出均为空。历史kind通过原严格reader保留原归属，不能因为增加一种材料就允许任意跨运行引用。

新state-v4字段明确为 `schemaVersion/sourceRunId/checkpointKind/readingMaterialCheckpoint/inventoryPublication/navigationPublication/persistencePublication/materialProducerVersion/materialSchemaVersion/materialProfile/materialBasisSha256`。basis覆盖真实上游内容引用、有效插件/材料配置和producer，不包含模型/并发/输出目录。不再用旧 `businessFlowsPublication` 表示新材料，也不以旧flows生成basis。`COMPLETE_CAPTURE / BOUNDED_PATH_SET`与`repositoryCompletionEligible`随来源保留，局部fixture不能写为整仓完成。

直接修改点包括 `AnalysisStepModuleAddress`、`AtomicAnalysisStepPublicationEngine`、`AtomicCanonicalPublicationEngine`、`CanonicalArtifactPolicyRegistry`、当前工具policy、`BusinessOutputArtifactKey`、`BusinessCheckpointArtifactReader`和`FileSystemAnalysisRunRegistry`。保持旧policy文件/receipt可重开，禁止用宽泛任意文件白名单解决新产物接线。

### 7.2 正式命令与结束状态

继续唯一 `source-analysis --config /absolute/path/config.yaml plan-materials`。它以既有 `PREPARE_MATERIALS` 意图启动一次运行，执行1—5后登记 `READING_MATERIALS_ONLY`，完成并返回新材料ref。不再把 `FLOW_INTERPRETATION`当材料准备的隐含target。

修改现有 `SourceAnalysisExecution.executeMaterialsOnly`、`PersistedTechnicalRunExecutor`与`TechnicalAnalysisWorkflow`；`TechnicalAnalysisWorkflowResult`改为inventory/discovery/navigation/persistence/materials引用，不另建同功能协调器。指定已有固定来源时直接使用该来源；只有显式配置新capture才捕获，不能为材料重做悄悄刷新仓库。

`start / executeStep / inspect / artifact / render`公共方法不增加第二套。内部PREPARE_MATERIALS target归到第5步槽位；`inspect`显示材料已完成、业务解释未执行；`artifact`读取JSONL，CLI的材料artifact导出支持 `--format markdown --output <path>`，使用同一reader生成完整预览而不安装新publication。路径只属于CLI配置层。`render()`没有历史报告时仍未就绪，不启动模型补报告。

第4步的Interface直接接受已重开的兼容Java索引，重做SQL增强不必重扫；第5步reader也不调用parser。为了保持本轮范围，不新增导航复用CLI参数、旧运行自动搜索或恢复命令。C中的插件开关对照由验收驱动经这两个正式Interface复用同一索引；另以正式plan-materials验证一次完整1—5链。不能把此Interface能力误报为CLI已经支持跨运行自动续跑。

### 7.3 删除清单的判定方法

全面实施先接通新的1—5路径，再按实际引用删除：

- JavaParser引擎实现、discovery旧解析重载、专属AST/五图builder、`javaparser-symbol-solver-core`及无其他消费者的依赖/测试。
- Fact候选枚举、Proof构建、严格Flow编译与Capsule生产、旧M10材料生成路径及仅为这些生产行为存在的测试。
- 旧程序模块注册从**新安装**路径退出，历史地址reader仍有效；禁止历史reader借机调用旧producer校验。
- 历史DTO、SourceRange/SourceReference、canonical存储、必要旧schema与纯读取/渲染保留。涉及已有Step06/07引用的通用类型不可整包删除。
- 原来测试里来源正确、非法引用、候选保留、内容完整、重开和零重扫等有效断言迁入新深模块；不机械保留每个旧包装的独占测试。

新模块不引用 `com.github.javaparser`，工程生产依赖树不再含该parser；保留研究程序如仍需它只能留在独立研究构建，不能把它带回正式应用。实际清单以消费者核对为准，不能执行目录通配删除。

## 8. 真实例子：查询订单关联数量的材料怎样贯通

来源为已冻结 jshERP `8c30ce7861570458920175e200bb2a6442713580`。本节是从已保存源码核对后的**设计推演，不是新插件运行输出**。

入口 `DepotItemController.getDetailList` 在其特定状态分支进入 `DepotItemService.getFinishNumber`，后者调用 `DepotItemMapperEx.getFinishNumber`。第3步保留完整调用方条件、Service参数构造和Mapper声明；第4步按方法定位真实XML。

已核对XML的核心原文：

```xml
<select id="getFinishNumber" resultType="java.math.BigDecimal">
    select ifnull(sum(di.basic_number),0) from jsh_depot_item di
    left join jsh_depot_head dh on di.header_id=dh.id and ifnull(dh.delete_flag,'0') !='1'
    where di.material_extend_id=#{meId}
    and di.link_id=#{linkId}
    and ifnull(di.delete_flag,'0') !='1'
    <if test="noType == 'normal'">
        and dh.link_number=#{linkStr}
    </if>
    <if test="noType == 'apply'">
        and dh.link_apply=#{linkStr}
    </if>
    <if test="goToType != null and goToType !=''">
        and dh.sub_type=#{goToType}
    </if>
</select>
```

固定原文位于[保存的Mapper XML第986行](../../../.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/1a93c021f80273216ea3eabf08ac6be3973343b2f233f9e64833a648257b2c72)。实施实验必须从manifest解析原仓库路径，不能把这个blob路径或期望SQL当导航输入。

| 接力 | 本例具体内容 | 如何检查 |
| --- | --- | --- |
| 01→02 | 固定源码、HTTP入口及精确方法定位 | 与已保存入口逐项对照；不把XML当新Controller |
| 02→03 | entry methodKey，JDT展开Controller→Service→Mapper | 同包保留触发分支、实参与目标形参、接口声明、返回使用 |
| 03→04 | Mapper FQN/方法名、参数与同源XML目录 | 自动匹配getFinishNumber，取得上述完整块；不传预期XML答案 |
| 04内部 | SUM/IFNULL、LEFT JOIN/ON、WHERE、三个条件块 | 条件结构未丢；`noType`两个分支不被同时写成必然过滤；删除条件未移动 |
| 04→05 | 完整XML、可解析片段结构、绑定/限制 | material视图实际包含它们，不只有statement ID |
| 05结束 | Controller/Service/Mapper/XML的连贯资料 | 打开预览即能沿调用阅读；源码位置能查；模型调用0 |

未来模型可把Java中的单据类型选择与XML中的关联字段/数量汇总一起理解，从而解释“按关联单据及商品汇总已办理数量”。但本轮**不生成该业务结论**，也不声称这条查询会创建入库单、已发生实物收货或确定整个采购时序。

另外两个实验选择：`insertSelective`验证条件列与值没有错配；`include + foreach`验证公共片段和集合条件保留。真实源码没有某种构造时用中性fixture补测，标注fixture，不能伪装客户代码。

## 9. 验收A：工具最小调研实施

### 9.1 输入与动作

使用上述冻结例子、实际insertSelective/XML引用例子和少量不同领域fixture。只实现独立薄驱动：安全读XML→调用官方部件→JSqlParser→导出原文和结构。先不接store/CLI/Step05，不写完整生产插件。

第一项直接展示官方组件的真实返回：类/API、节点/对象形状、原文与动态条件、include结果、SQL AST。未知返回形式按官方API处理；“我们的旧JSON不接受”不是工具不可行的证据。

### 9.2 必须通过的判定

| 检查 | 通过标准 |
| --- | --- |
| 官方复用 | 不自研XML/SQL文法，不运行客户类、框架或OGNL；明确哪些MyBatis部件直接使用 |
| XML安全 | 标准DOCTYPE可读；外部DTD/一般与参数实体/XInclude使用本地拒绝/计数替身验证零外部访问，不以实际联网为测试方法 |
| XML完整性 | statement、动态条件、文本顺序、实际include/相关映射可读；未支持构造保留原文 |
| SQL实用性 | 聚合/连接例子的主体与条件有正确结构；insertSelective列值原条件对应保留；复杂片段不误报完整 |
| 自动关联 | 用JDT Mapper声明/目录输入自动关联，无例子专用路径/方法答案 |
| 通用性 | 至少一个不同命名/领域fixture同样得到结果，不硬编码表名/业务词 |
| 来源与成本 | 定位实际文件块即可；记录工具调用、大小和耗时，不新增证据证明链 |

通过不要求所有动态分支都有完整SQL AST，但不接受“只输出XML文件名”或“只有简单SELECT通过、没有新增可读关系”。若官方组件只能解析一部分，报告可采用的准确范围；只有主要目标成立才能进入B。若需要大量自研MyBatis运行语义或关键原文无法保持，则A未通过，讨论替代工具/降低解析增强范围，不硬上全面实施。

### 9.3 调研交付

独立报告包含：实际工具版本、实际调用/API返回、三个例子的原文及解析结果、自动Mapper关联、未解析清单、读者得到什么新增信息、下一步采用建议。产物留在忽略的研究工作区，正式文档保存链接和结论；不覆盖旧材料。**A实测已完成，详见[实验报告](../persistence-tool-feasibility-result.md)；不可将研究产物当作生产检查点。**

## 10. 验收B/C：全面接线后，只验证1—5步

### 10.1 全面实施的退出条件

- 新3/4/5 producer、reader、存储策略、配置与运行归属一起接通；不是仅内存对象可用。
- 删除旧重复生产与JavaParser依赖；已有JDT方法、调用、参数、候选、异常/返回不减少。
- 新材料保存后直接重开与导出，不重新导航或解析。
- 第5步结束就是材料完成；不调用第6步Builder/ActivityExplainer，也不调用Step07/08或Provider。
- 历史326条Activity、原M10、已有业务文档仍可读取；没有隐式新版本转换或覆盖。

### 10.2 直接测试矩阵

| 场景 | 观察结果 |
| --- | --- |
| 插件缺省/空列表 | Java材料完整，持久化明确DISABLED，MyBatis/JSqlParser调用0 |
| 同Service多个入口、循环、候选 | 查询缓存继续有效；每入口调用/实参不串，所有候选保留 |
| Mapper无body、@Param、多namespace/databaseId | 声明与参数可读，变体保留，不随文件顺序覆盖 |
| include/if/choose/foreach/trim/selectKey/resultMap | 完整原文、相关依赖与条件保留；不执行表达式 |
| SQL不支持/动态表名/空集合 | 记录部分解析或未解析，不删除原脚本或伪造固定SQL |
| XML安全设置/冻结源损坏 | 安全拒绝明确；真实来源损坏失败，不不安全回退 |
| 零入口/多入口/超大小限制 | 入口分母不变，零入口合法，未收集具体说明 |
| 保存、重开、导出 | 内容和调用关系不丢，预览确定性，重开parser/JDT调用0 |
| 旧M10/Activity/报告 | 历史reader仍通过，字节不变，无模型调用 |
| 正式plan-materials | 新运行止于05，输出kind正确，06–08未执行，Provider未初始化 |

只运行新增/直接覆盖测试；交付时检查本模块编译、格式与直接相关质量项，任何完整测试套件须在实施计划中列明并取得当次批准，重型命令串行。真实JDT检查走现有独立IT入口，不让普通单测重复调用工具。不运行客户工程或外层无关测试。

### 10.3 固定仓库1—5完整验收

1. 选定原完整固定快照，不重新下载不同提交；显式新运行执行既有Step01核验与Step02发现，记录实际入口数量并与旧基线逐项比较，不硬编码必须是326。
2. JDT生成/复用合法Java索引，插件关闭先核对Java基线；开启插件后只补持久化与材料，不因开关再重复JDT。
3. 检查所有入口都有材料或具体原因；采购/销售/调拨相关入口作为查看样本，不预置其业务阶段。
4. 对照方法/调用/声明数、Mapper关联数、未关联/多候选数、解析状态、原文内容、RPC/耗时/保存体积。没有旧计数就写“无可比基线”，不能编加速倍数。
5. 断开工具和模型会话后重开新材料，导出可读预览。运行记录验证第6/7/8步未执行，产品模型0调用，客户构建/数据库0调用。
6. 提供最终材料清单、真实完整入口例子、差异和未处理范围，**收口讨论第6步，不自动生成Activity或业务过程**。

本轮完成只表示“取得和组织材料的能力验收通过”。不证明SQL AST能独立推导业务，不证明业务文档会达标，也不把可定位率/coverage当语义完整性。

## 11. 历史问题、本轮消除什么、留什么

本轮直接消除：重复包装/证明、JavaParser生产依赖、Mapper正文与SQL未进入材料、Mapper声明被body过滤、同一正文重复保存、材料命令实际越过Step05。

保留已实现改进：JDT查询缓存、方法共享、材料与模型批次分离、已审任务显式复用、并发池、单测/IT/质量分类、正文与源码分离。不得把它们再列为待开发。

待后续讨论：业务选材聚焦、规则翻译/适用范围、最终核对容量、模型返回局部编号、语义质量与长业务链。详见[实施问题与待讨论项](../implementation-lessons-and-followups.md)；保留[更多发现](../more-findings.md)原文，不在这里启动修复。

## 12. 文档交付检查与当前成熟度

| 项目 | 当前状态 |
| --- | --- |
| JDT现有路径与历史材料 | 已实现，当前代码/保存结果可查 |
| MyBatis/JSqlParser能力资料 | A通过：MyBatis3.5.19/JSqlParser5.3，14项直接测试，真实冻结XML自动关联及三个样本已核验 |
| 新Step04插件与Step05统一生产 | 本文详细设计，未实施 |
| A工具验收/B全面实施/C全1—5验证 | A完成；B开始接线，C未开始；不把A结果视为生产接线完成 |
| 第6步模型消费新材料 | 尚未讨论，不在本轮范围 |

本次文档核对包含：当前类/合同引用、模块输入输出、原文例子、三道验收门、历史读取边界、零Step06约束、相对链接与空白检查。没有运行构建/JDT/SQL parser/产品模型。`more-findings.md`基线SHA256为`59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e`，不得更改。

本次已完成只读设计复核，并据此明确XML安全失败分层、预览不重复进入canonical checkpoint、index-v2字段保留及新state精确字段。两份新文档58处本地链接检查通过；导航/设计差异通过`git diff --check`，受保护文档哈希未变。这些是文档检查结果，不是A/B/C已经通过。
