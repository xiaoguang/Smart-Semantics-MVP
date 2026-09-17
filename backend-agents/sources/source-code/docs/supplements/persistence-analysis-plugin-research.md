# 持久化分析插件：框架能力调研

本次推荐已由用户接受，正式目标、模块合同和A/B/C验收顺序已写入[补充详细设计](cross-object-process-reconstruction/jdt-persistence-reading-materials.md)。本文继续保存能力调研及未验证边界，不把后续设计写成工具实验已通过。历史实施问题另见[待讨论记录](implementation-lessons-and-followups.md)。

## 状态与调研问题

本文是 2026-09-17 的技术调研笔记，不是批准设计、实施计划或框架采用决定。问题是：现有成熟工具能否帮助完成「Java 持久化调用 → MyBatis XML 动态 SQL → 查询/写入结构 → 模型理解业务语义」，并为未来 JPA/Hibernate 分析留出扩展可能。

本次只读取正式 source-code 模块和公开官方文档/源码；未修改生产代码、测试、配置或正式设计，未运行客户构建、客户代码、数据库、源捕获或产品模型，也未修改 more-findings.md。官方在线文档和主分支源码会变化；本文查证的是能力、发布信息与边界，不声称已完成依赖接入或本项目真实 XML 的解析覆盖率验证。

用户在本轮讨论中已明确的目标约束是：持久化分析增强可配置；未配置时保留现有分析能力和原路径，不要求重做既有产物。Java 源码引擎与持久化增强分属两个独立维度；同一应用可能混用 MyBatis/JPA，因此持久化增强应能组合，而不是把应用强制归为一种 ORM。SQL parser 是插件内部实现选择，不因此先增加一套公共 parser 配置矩阵。最新方向是保留现有 JDT 能力，准备退出 JavaParser 与严格 Fact/Proof 并重新讨论 Step03–05；这不是已经完成的生产迁移，不能与插件「未配置不增强」的默认约束混为一谈。这些是本轮目标约束，尚不是生产配置字段或接口设计。

## 当前模块的事实边界

当前 [MapperCapabilityCataloger](../../src/main/java/org/sourceanalysis/app/analysis/discovery/MapperCapabilityCataloger.java) 从已验证冻结文本读取 Java Mapper 声明与 XML namespace/statement 候选；产物明确为 `CANDIDATE_NOT_YET_BOUND`，不是已完成的调用绑定或 SQL 分析。其 XML 解析允许标准 Mapper DOCTYPE，但禁用外部 DTD、一般/参数实体、schema、XInclude 和网络解析，不能用运行时框架加载代替这个安全边界。当前 v0 的 [scoped guidance](../../AGENTS.md) 仍把 JPA 归为明确 Gap；本调研不改变该能力声明。

## 初步结论

成熟框架已经分别覆盖 XML 动态 SQL 的运行时语义、SQL 的语法/关系结构、以及 HQL 的语法和实体语义。但是，本次未查证到一个可以直接接入当前冻结源码流程、同时安全完成 Java 调用定位、保留所有动态条件、绑定 SQL 结构并解释业务含义的单一组件。「有成熟部件可复用」与「整个链路已由框架解决」是不同结论。

尤其要区分三个输出：原始条件化脚本；给定参数后的某一份 SQL；SQL 或实体查询的结构树。把它们混为一谈，会把未成立的分支写成必然行为，或把某一组虚构参数的 SQL 当成原代码的完整语义。该判断来自下文官方执行链路的比对，不是对所有市场工具不存在某功能的证明。

复用可分成两层：第一层继续使用现有 JAXP 安全 XML 读取和 namespace/statement id 候选索引，持久化插件补足 Java 调用绑定与完整脚本材料；第二层在插件内部复用成熟 SQL parser 提取可确认的关系/表达式结构，同时保留原条件化脚本与未解析部分。这里不要求把所有动态 SQL 压成一条确定 SQL，也不要求为了启用增强而改变 Java 引擎选择。最重要的默认约束是：没有配置持久化插件时，现有分析能力和路径保持不变，既有产物不重做。本文只记录这一讨论方向，不定义接口或实施顺序。

## MyBatis：成熟的是脚本语义和运行时展开

### 官方已有的执行链路

`XMLMapperBuilder` 构建 namespace、fragment、result mapping 和 mapped statements；`XMLStatementBuilder` 先展开 include、处理 selectKey，再交给 LanguageDriver；默认 `XMLScriptBuilder` 把脚本转换成 SqlNode 组合，根据是否动态返回 DynamicSqlSource 或 RawSqlSource。它的 SqlNode 是构建 SQL 文本的脚本节点，不是 join/投影/聚合等关系结构树。[XMLMapperBuilder 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/builder/xml/XMLMapperBuilder.html)、[XMLStatementBuilder 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/builder/xml/XMLStatementBuilder.html)、[XMLScriptBuilder 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/scripting/xmltags/XMLScriptBuilder.html)。

| XML 构造 | 官方语义/能力 | 冻结源码静态阅读需保留的边界 |
| --- | --- | --- |
| if | 条件表达式决定是否加入子脚本 | 原条件与条件所属片段；没有参数上下文时不能确定实际取值 |
| choose/when/otherwise | 按条件选择一个分支或默认分支 | 分支顺序、互斥选择和默认行为；不能把所有 when 拼成同一条必然 SQL |
| foreach | 遍历集合，用 item/index、open/close/separator 生成片段 | 集合来源、循环体、空/nullable 行为；一次具体展开不代表所有集合大小 |
| bind | 通过 OGNL 求值，产生上下文变量 | 表达式与变量来源；不能为了阅读执行客户表达式 |
| where/set/trim | 对实际生成内容增加前缀、移除特定前后缀 | 不能把尚未成立的条件片段直接当成固定 where 或赋值集合 |
| include/sql | 按 namespace/refid 查 fragment，递归展开并处理静态 property 变量 | 需要已知 fragment 和静态配置变量；展开仍须能追溯原片段来源 |

前五项由 [MyBatis 动态 SQL 官方文档](https://mybatis.org/mybatis-3/dynamic-sql.html) 与 XMLScriptBuilder 的 handler 表确认；include 是脚本构建之前的独立处理，不在该 handler 表中。[XMLIncludeTransformer 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/builder/xml/XMLIncludeTransformer.html)。include 的配置/property 替换与运行时 SQL 文本中的 `${...}` 不能仅因同用 `${}` 就被视为同一阶段的绑定。

`#{...}` 通常变成 PreparedStatement 的值参数；运行时 `${...}` 则原样替换 SQL 文本，可能决定表名、列名或排序结构，不能一律归一化为值占位符。另外，带 RETURNING/OUTPUT 的写语句可以在 `<select affectData="true">` 中声明，因此 XML 标签 kind 并不普遍等于 SQL 读写类型。这两点都需要从实际脚本/SQL 内容确认，不能由方法名或标签直接猜测。[MyBatis Mapper XML 官方文档，String Substitution 与 insert/update/delete](https://mybatis.org/mybatis-3/sqlmap-xml.html)。

### BoundSql 能解决什么，不能解决什么

`DynamicSqlSource#getBoundSql(parameterObject)` 创建 DynamicContext，执行根 SqlNode，然后创建 SQL/参数映射；`BoundSql` 保存动态处理后的 SQL 字符串、有序参数映射、参数对象和动态生成的附加参数。这个调用本身并不要求发出 JDBC 查询，但会执行脚本和表达式。[DynamicSqlSource 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/scripting/xmltags/DynamicSqlSource.html)、[BoundSql 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/mapping/BoundSql.html)。

因此，BoundSql 可以给后续 SQL parser 提供一份具体 SQL；它不是「所有可能 SQL 的静态分析结果」，也不保留原始条件树、include 的源位置、投影/连接/聚合的 SQL AST 或业务解释。传入 null、空 Map 或构造的样例参数不能普遍补足上述缺口：某些条件会被消除，foreach 依赖真实集合形状，`${...}` 可以影响 SQL 文本；样例未触达的分支仍然未知。这是由执行语义推出的限制，不是已测量的项目覆盖率。

### 直接使用运行时 Builder 的安全与依赖问题

完整 XMLMapperBuilder 不是纯文本解析器。resultMap/parameterMap 等会解析 Java 类型；XMLStatementBuilder 解析 parameterType/resultType/lang；namespace 绑定会尝试 `Resources.classForName` 和 `configuration.addMapper`。仅有 XML、没有客户编译产物时，某些映射会失败或需要隔离其运行时配置；加载自定义 language/cache/type handler 等还带来不在当前静态阅读授权内的可执行行为。不能把「不连接数据库」等同于「不加载/不执行客户代码」。[XMLMapperBuilder 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/builder/xml/XMLMapperBuilder.html)、[XMLStatementBuilder 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/builder/xml/XMLStatementBuilder.html)。

OGNL 求值同样不是读取条件文字：OgnlCache 调用 `Ognl.getValue`，bind 的官方例子甚至直接调用 `_parameter.getTitle()`；[VarDeclSqlNode 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/scripting/xmltags/VarDeclSqlNode.html)、[OgnlCache 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/scripting/xmltags/OgnlCache.html)、[MyBatis 动态 SQL 文档](https://mybatis.org/mybatis-3/dynamic-sql.html)。OGNL 的官方语言还定义了实例方法、静态方法、构造器和赋值表达式。不同 OGNL/MyBatis 版本或限制配置能否阻断某种表达式，需要另行验证；本次不把语言能力描述为对任意版本的漏洞证明。[OGNL 官方语言说明](https://commons.apache.org/dormant/commons-ognl/language-guide.html)。

可确认的复用价值是：官方已有完整标签语义和 fragment 展开规则，可以作为静态表示或受控解析的权威依据。不能据此承诺直接调用完整 Builder/getBoundSql 就获得安全、保留条件且可定位来源的离线分析。具体 API 复用、源码借鉴或插件依赖仍是未决定事项。

### 两个 Builder 的窄范围复用边界

`XMLIncludeTransformer(Configuration, MapperBuilderAssistant)` 的公开入口是 `applyIncludes(Node)`：通过已登记的 sqlFragments、当前 namespace 和静态 Properties 查找/克隆 fragment，再替换 DOM。该类源码没有 OGNL 求值或客户类型解析调用，因而比完整 XMLMapperBuilder 更适合单独核验；但它依赖预先建立的 fragment/configuration 上下文，也不是安全 XML 入口。只能在现有安全 XML 读取之后讨论复用；未知 refid/property、递归引用及展开来源仍须显式保留边界，不能为凑齐配置而启动客户框架。[XMLIncludeTransformer 官方源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/builder/xml/XMLIncludeTransformer.html)。

`XMLScriptBuilder` 的 protected `parseDynamicTags(XNode)` 构建 MixedSqlNode，if/bind handler 此时保存表达式文字而不求值；公开 `parseScriptNode()` 则返回运行时 SqlSource，而非带源位置、公开遍历接口的静态脚本模型。动态节点的实际选择在 apply/getBoundSql 阶段；静态路径还会构造 RawSqlSource，因此不能将公开入口笼统视为永不处理参数类型的纯 DOM 转换器。可借鉴节点语义，也可研究限定 API 复用，但本次未验证安全嵌入或承诺直接使用它输出完整材料。[XMLScriptBuilder 官方源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/scripting/xmltags/XMLScriptBuilder.html)。

### 现成 Mapper XML 读取工具：不必因材料格式不同重写解析器

「不能直接得到本项目最终材料格式」不等于「需要自研 XML/MyBatis parser」。官方现成入口可直接复用：`XPathParser(Document)` 接收已有安全解析的 DOM，`evalNode/evalNodes` 定位 mapper/statement/fragment；`XNode.getStringAttribute/getChildren/getNode` 读取 namespace/id/属性和子节点。`getChildren()` 只列元素，混合 SQL 文本/CDATA 与标签顺序应读原 DOM 的 childNodes，不能只用根节点 getStringBody 代替整个条件脚本。读取这些节点不必执行 XMLMapperBuilder 或客户 OGNL。[官方 XPathParser 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/parsing/XPathParser.html)、[官方 XNode 源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/parsing/XNode.html)。

结合上文公开的 `XMLIncludeTransformer.applyIncludes(Node)` 和 XMLScriptBuilder 已有的官方 SqlNode 构建规则，可以先核验窄范围复用/轻量导出，而非先重写 if/choose/foreach 等标签解释器。源位置、原文字节与 Java 方法/@Param 关联仍由现有冻结来源和材料 adapter 保持；这属于关联/映射/序列化工作，不是另造 XML 或 SQL 语法。公开读取接口不自动批准完整运行时加载；实际嵌入尚未验证。[IncludeTransformer 官方源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/builder/xml/XMLIncludeTransformer.html)、[XMLScriptBuilder 官方源码](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/scripting/xmltags/XMLScriptBuilder.html)。

短补查也找到较贴近的现成独立工具，不只是给样例参数求 BoundSql：

- `actiontech/mybatis-mapper-2-sql` 的作者仓库源码提供 `ParseXMLs([]XmlFile, configFns...) → []ast.StmtInfo`，输入为文件内容；内部用 encoding/xml 建 Mapper/SQL/include/if/choose/trim/foreach 节点，聚合多文件后输出语句。它不是 MyBatis SQL 生成 DSL，也不要求客户参数对象/getBoundSql。公共出口以 SQL/StmtInfo 为主，内部 parse 未导出；本次未确认完整条件树、resultMap/selectKey 和来源信息是否无损导出，因此不能当成已符合材料要求。它是 Go 库，不直接等于当前 Java 可用依赖。[作者仓库 parser.go](https://github.com/actiontech/mybatis-mapper-2-sql/blob/main/parser.go)。
- `batis-xml` 的发布者 API 文档（所读页面为 0.1.1）更接近静态代码材料：`parse_bytes` 直接读 XML 字节，公开 statements/fragments/resultMaps/includes、带激活条件的 SQL variants 与 span map，不依赖运行时参数。它是 Rust/WASM 的第三方项目，不是 MyBatis 官方工具；include 仍保留 marker 交消费者展开，variants 有 32 上限，错误采用部分结果/diagnostics，不可替代当前安全 XML 的 fail-closed 要求。作者 GitHub/详细源码入口读取失败，故本次只确认发布者文档中的接口/限制，没有核验实现或其自报覆盖率，更不据此决定引入 WASM。[发布者 API 文档](https://docs.rs/batis-xml/latest/batis_xml/)。

查证到的是「官方 XML 读取/脚本构建部件可拿来适配，且已有独立 Mapper-aware 静态工具」；未查证到的是「上述工具已在本仓真实 fixtures 上安全、无损接通最终模型正文」。当前应先讨论复用与薄材料 adapter，不把未验证的导出缺口扩写成自研解析器的必要性。本次未安装或运行这些库，也不扩展既有待讨论方案。

## SQL 结构：现成 parser 的复用空间

SQL parser 适合提供语句类型、表/别名、投影、join/ON、WHERE、聚合/分组、子查询和表达式结构；它不直接理解 MyBatis 条件标签，也不会替代 Java→Mapper 的调用绑定或模型对业务含义的解释。以下比较只基于官方公开能力，没有安装依赖或实际运行本项目 SQL。

| 候选 | 官方可确认的能力 | 对当前 Java 后台取材的判断 |
| --- | --- | --- |
| JSqlParser | SQL 文本转 Java 对象 AST，提供 Statement/Select/Expression/FromItem 等 Visitor | 可作为优先核验的纯 Java 结构解析候选；不预先锁定尚未实测的版本/接口 |
| Alibaba Druid SQL Parser | 方言 AST 与 Visitor；SchemaStatVisitor 示例提取表、列、条件等 | 可作为 MySQL 方言的比较候补；统计列表不能替代完整 ON/WHERE/表达式树 |
| Apache Calcite | SQL parser、验证、关系代数及优化基础设施 | 如果只需给模型提供源码结构，完整关系规划/优化路径偏重；不先引入 schema/adapter 闭环 |
| SQLGlot | Python SQL AST、方言转换及 lineage 等能力 | 有复用价值，但本模块是 Java；目前没有必要先增加 Python 接缝 |

依据为 [JSqlParser 官方用法](https://jsqlparser.github.io/JSqlParser/usage.html) 与 [官方仓库](https://github.com/JSQLParser/JSqlParser)、[Druid SQL Parser 官方文档](https://github.com/alibaba/druid/wiki/SQL-Parser) 与 [Visitor 官方示例](https://github.com/alibaba/druid/wiki/SQL_Parser_Demo_visitor)、[Calcite 官方背景文档](https://calcite.apache.org/docs/) 和 [SQLGlot 官方 API 文档](https://sqlglot.com/sqlglot.html)。表中优先级/偏重判断是对当前任务的工程推论，不是框架官方承诺或批准选型。

只需先核验一个候选能否保留真实材料中的结构差异；本调研不要求串接多个 parser、做自动 fallback 或扩展公共配置维度。候选库支持「SQL」不等于原始 XML 脚本可以直接整体 parse，尤其要单独面对条件片段、动态标识符和占位符的来源。解析结果是增强，不是冻结材料的准入门槛：能够安全读取的原 XML 即使不能生成 AST，也仍可连同限制交给模型阅读；不能把技术增强失败伪装为原文缺失，或为获得 parser 成功而删除条件。

### JSqlParser：发布/JDK 与实际 API 核对

截至本次查证，官方 GitHub release 页将 `jsqlparser-5.4` 标为 Latest，指向 `e847e94`。这确认发布 tag，不等于本次已验证 Maven Central 上的 5.4 artifact 或该 tag 的全部 API；5.4 tag POM/源码多个入口读取失败。可读取的 `jsqlparser-5.3` tag POM 明确为 `com.github.jsqlparser:jsqlparser:5.3`，compiler source/target 均为 11。故 5.4 是最新 upstream release 候选，5.3 是本次已核对 tag POM/API 的非 SNAPSHOT 参照，不在本文锁版。[5.4 官方 release](https://github.com/JSQLParser/JSqlParser/releases/tag/jsqlparser-5.4)、[5.3 tag POM](https://github.com/JSQLParser/JSqlParser/blob/jsqlparser-5.3/pom.xml)。

官方 README 区分运行与源码构建要求：4.9 是最后支持 JDK 8 的 release；5.0+ 运行要求 JDK 11，Visitor 有破坏性变化；5.1+ 的源码构建需要 JDK 17 toolchain。这不是客户源码必须改用 Java 11，也不要求本模块构建 parser 源码。当前官网/README 的安装示例还包含 Manticore 持续构建的不同 groupId/版本范围，不能把网页的最新文档号或范围直接当成 upstream 固定依赖；不按其示例引入额外坐标。[官方 README，Install 与 Java version](https://github.com/JSQLParser/JSqlParser)。

实际 API 以可读取的 5.3 tag 为准：`CCJSqlParserUtil.parse(String)` 返回 Statement，`parseStatements(String)` 返回 Statements；表达式与条件片段有 `parseExpression(String, boolean)`、`parseCondExpression(String, boolean)`。后两者的单参数入口默认允许 partial parse，完整片段核验应显式使用 `false`，并记录失败/剩余材料，不把解析前缀当成整段成功。[5.3 CCJSqlParserUtil 源码](https://github.com/JSQLParser/JSqlParser/blob/jsqlparser-5.3/src/main/java/net/sf/jsqlparser/parser/CCJSqlParserUtil.java)。

5.3 的 `ExpressionVisitorAdapter<T>` 使用带 context 的泛型 visit/accept；PlainSelect 提供投影、FromItem、joins、where 等结构，Join 的 `getOnExpressions()` 与 WHERE 分开，旧单数 `getOnExpression()` 已 deprecated。可以直接保留结构位置而不是先拍平为表/条件列表；这是 API 可用性查证，不是已编译通过的本模块调用示例。[ExpressionVisitorAdapter](https://github.com/JSQLParser/JSqlParser/blob/jsqlparser-5.3/src/main/java/net/sf/jsqlparser/expression/ExpressionVisitorAdapter.java)、[PlainSelect](https://github.com/JSQLParser/JSqlParser/blob/jsqlparser-5.3/src/main/java/net/sf/jsqlparser/statement/select/PlainSelect.java)、[Join](https://github.com/JSQLParser/JSqlParser/blob/jsqlparser-5.3/src/main/java/net/sf/jsqlparser/statement/select/Join.java)。

### MyBatis 材料交给 SQL parser 前的最小处理建议

5.3 grammar 的 JDBC 参数规则处理 `?`/`$数字`，named 参数规则处理 `:`/`&`；本次没有查到 MyBatis 参数绑定/动态 XML 节点语义，不能宣称原脚本可直接整体解析。[5.3 官方 grammar](https://raw.githubusercontent.com/JSQLParser/JSqlParser/jsqlparser-5.3/src/main/jjtree/net/sf/jsqlparser/parser/JSqlParserCC.jjt)。以下仅是材料处理的最小讨论建议，未实施或实测：

| 输入 | 可讨论的分析处理 | 必须保留的限制 |
| --- | --- | --- |
| 值位置的 `#{property,...}` | 为 SQL 语法分析生成 `?` 投影，并另存 property/选项与源片段对应 | 不是运行时参数绑定或取值验证；不能用正则替换后丢掉原文/映射 |
| SQL 文本中的 `${expression}` | 保留原表达式与动态位置；只有来源已确定的静态 property 才讨论静态替换 | 可能是一整个标识符、排序或任意片段；不能统一改成 `?`，也不能把虚构表/列当成真实关系 |
| if/choose/foreach/bind | 保留原条件、互斥/循环/变量关系及完整脚本；解析可确认的 SQL 部分 | 不求值 OGNL，不用 dummy 参数猜全分支，不把不同分支拼成一条必然 SQL |
| SQL fragment | 按已知片段类型解析表达式/条件，或在明确的语句上下文中分析 | 裸 `AND ...`、join/赋值列表未必是完整语句；去掉前缀或加上下文必须标为分析投影，保持来源 |

优先 JSqlParser 不意味着强迫每个 statement 都有完整 AST；未知动态结构可以只保留 XML/片段材料与限制。SQL AST 帮助模型看清 sum、join 和条件位置，不等于数据库 schema 校验、参数传递已绑定或业务语义已闭合。

## 一个冻结示例：结构差别确实会影响阅读

主任务已只读核对固定 jshERP commit `8c30ce7861570458920175e200bb2a6442713580` 的 DepotItemMapperEx.xml 中 `getFinishNumber`（约 986 行起）片段。本例只说明需要保留哪些代码事实，未由本次候选 parser 执行验证，也不是实际采购/收货成功的运行证据。

| 源码中可见结构 | 结构化阅读必须保留的含义 |
| --- | --- |
| `IFNULL(SUM(di.basic_number),0)` | 对匹配明细的 basic_number 求和，并将空聚合结果转为 0；不能只输出「查询数量」 |
| jsh_depot_item LEFT JOIN jsh_depot_head，`di.header_id=dh.id` | 保留明细与单头的左连接、别名和连接键，不改成 inner join |
| head.delete_flag 非删除条件位于 ON | 约束右侧单头匹配；不能移动到 WHERE 或概括为所有左侧明细因此被过滤 |
| WHERE 含 `material_extend_id=#{meId}`、`link_id=#{linkId}` 与明细未删除条件 | 保留各字段的范围限制及占位符来源，不把它们与 ON 条件混成一个无位置的条件列表 |
| noType 为 normal 时增加 `dh.link_number=#{linkStr}`；apply 时增加 link_apply 相关条件 | 保留两个条件分支及各自引用字段，不把二者都叙述为恒成立 |
| goToType 非空时增加 sub_type 条件 | 保留可选条件，不宣称查询始终只针对某个固定业务单据类型 |

这些 SQL 事实可帮助模型理解数量口径、单头/明细关系和参数化范围。仅凭方法名 getFinishNumber、sum、关联字段或该 Mapper 片段，不能确认「采购已经收货」、完整流程先后或某次执行结果；还需读取调用方的输入、条件、返回使用和相关业务源材料。程序解析应提供实际结构与明确未解析部分，模型解释应受这些原文边界约束。

## Claude Code、Eclipse、VS Code：宿主与跨语言能力分开看

这些产品没有一个公开统一的「MyBatis/SQL 解析器」可作相同依赖名称引用；宿主的 Java/XML 导航、MyBatis 关联规则和 SQL 方言解析是不同层。

| 产品/组件 | 官方确认的相关能力 | 不由该能力直接推出的结论 |
| --- | --- | --- |
| Claude Code | 内置 LSP 工具，通过代码智能插件配置外部语言服务器；官方 Java 项为 jdtls，需另装 server binary | 不公开证明内部使用某款 SQL/MyBatis parser；模型能阅读 SQL 不等于确定性 SQL AST |
| Eclipse + MyBatipse | MyBatis Java/XML 补全、验证、跳转与重构；建立 namespace/id/type 等关联 | 不是独立 SQL AST/业务语义 SDK，也不等于所有 Eclipse MyBatis 功能都由 DTP 提供 |
| VS Code 的 Red Hat Java/XML 扩展 | Java 使用 Eclipse JDT LS；XML 使用 LemMinX | 通用 Java 与 XML 服务并不自动完成 Mapper 调用 → XML statement → SQL 的跨语言绑定 |
| VS Code SQLTools | 数据库连接、查询、格式化与补全等扩展，带语言服务器及可插拔驱动 | 不是 VS Code 内建的统一 SQL parser，也不直接证明静态血缘/持久化语义能力 |

各项依据分别为 [Claude Code 官方代码智能文档](https://code.claude.com/docs/en/discover-plugins#code-intelligence)、[MyBatipse 官方仓库](https://github.com/mybatis/mybatipse)、[VS Code Java 扩展官方仓库](https://github.com/redhat-developer/vscode-java)、[VS Code XML 扩展官方仓库](https://github.com/redhat-developer/vscode-xml) 和 [SQLTools 官方仓库](https://github.com/mtxr/vscode-sqltools)。以上是公开技术栈证据，不是对闭源内部组件的猜测，也不是本项目已安装这些插件。

MyBatipse 的关联逻辑值得独立看待：MapperNamespaceCache 通过 JDT source roots 与 WTP XML DOM/XPath 建 namespace → Mapper XML 索引；XmlHyperlinkDetector 用 Java project.findType 与 statement id 找方法，并解析限定 namespace 的 refid/resultMap/select 引用。包含 `${...}` 的动态引用会跳过 hyperlink，表明动态引用仍有静态边界。[MapperNamespaceCache 官方源码](https://github.com/mybatis/mybatipse/blob/master/mybatipse/src/net/harawata/mybatipse/mybatis/MapperNamespaceCache.java)、[XmlHyperlinkDetector 官方源码](https://github.com/mybatis/mybatipse/blob/master/mybatipse/src/net/harawata/mybatipse/hyperlink/XmlHyperlinkDetector.java)。

Eclipse DTP 确实公开一个可扩展 SQL Query Parser 框架，但没有证据把它当成上述 MyBatipse 的底层 SQL parser。[Eclipse DTP 官方文档](https://help.eclipse.org/latest/topic/org.eclipse.datatools.common.doc.user/doc/html/asc1229700383338.html)。MyBatipse 的 Eclipse 宿主关联规则与独立 SQL AST 库的价值应分别评估；本次不展开整套 IDE 搬入后台的方案，也不据此作许可证法律结论。

## JPA/Hibernate：可复用 HQL 前端，但不是另一种 XML Mapper

Hibernate 官方已提供 HQL grammar 和 ANTLR 语法树；StandardHqlTranslator 随后通过 SemanticQueryBuilder 把语法树变成 SQM。SQM 表示解析后的实体查询语义，而非仅 SQL 字符串。可查证的核心上下文 SqmCreationContext 包含 JpaMetamodel、类型配置、查询引擎等；接口在 6.6 文档标为 Incubating，相关实现位于 internal 包。它不是版本无关、只接受字符串的纯文本语义插件 API。[官方 HQL grammar](https://github.com/hibernate/hibernate-orm/blob/main/hibernate-core/src/main/antlr/org/hibernate/grammars/hql/HqlParser.g4)、[StandardHqlTranslator 官方源码](https://github.com/hibernate/hibernate-orm/blob/main/hibernate-core/src/main/java/org/hibernate/query/hql/internal/StandardHqlTranslator.java)、[SqmCreationContext 官方 API](https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/query/sqm/spi/SqmCreationContext.html)。

HQL/JPQL 指向实体和实体属性，不直接指向关系表和列；关联路径可能隐式产生 join。因此只有查询文字、不掌握实体/属性/关联映射时，可以解析语法，但不应宣称物理表列与关系已闭合。是否必须得到最终方言 SQL，是独立需求，不能预先把实体级语义分析强制成 SQL 恢复问题。[Hibernate HQL 官方指南，1.1 与 3.2.7](https://docs.hibernate.org/orm/7.1/querylanguage/html_single/)。

另一方面，不能笼统声称 Hibernate 的语义检查一定要运行客户应用或加载客户运行时实体类：官方 HibernateProcessor 已能在编译期校验 annotated HQL/JPQL 的语法和类型；ProcessorSessionFactory 使用 ProcessingEnvironment、TypeMirror/TypeElement 和实体名映射。它说明 source-model 路径有官方先例，不说明已经可安全直接嵌入本项目，也不授权运行客户 Maven/annotation processor。[Hibernate Data Repositories 官方文档，1.7](https://docs.hibernate.org/orm/7.1/repositories/html_single/#_annotated_query_methods)、[ProcessorSessionFactory 官方 API](https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/processor/validation/ProcessorSessionFactory.html)。

未来 JPA 范围还必须明确区分：字面量 JPQL/HQL、native SQL、Criteria 的 Java 查询对象图，以及实体状态变化/持久化操作。Criteria 规范将查询定义为基于实体 metamodel 的对象图；managed entity 的字段/关系变更可在 commit/flush 同步，且 cascade/关系拥有方影响写入。只抓查询字符串会遗漏后一类持久化语义。[Jakarta Persistence 3.2 规范，3.3.4 与 6.1](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2)。

所以「MyBatis 与未来 JPA 可插拔」在技术上不矛盾，而且符合本轮已明确的可组合增强目标；但共用的输出语义、实体映射范围和支持边界仍须单独讨论。当前没有具体插件接口、配置 schema 或扩展实现由本文批准。

## Step03–05：待讨论的收敛方向

以下将本轮方向展开为具体输入、程序动作、输出与下一消费者，便于用户确认；它不是现行流水线事实、批准设计或实施承诺。输出名称复用已有 JavaCodeIndex 和材料概念，不在此规定新接口、schema、阶段 key 或公共配置字段。当前 scoped guidance 中的旧生产约束不因这份笔记而被默示改写。

| 阶段 | 输入 | 程序拥有的工作 | 输出与直接消费者 |
| --- | --- | --- | --- |
| Step03 | 已验证冻结 Java 源码、声明目录和精确入口 | JDT 定位/导航、完整方法与调用材料索引，保留原有候选、条件和限制 | 共享 JavaCodeIndex，直接供 Step04 关联与 Step05 阅读材料组装 |
| Step04 | 同一冻结来源的 XML、JavaCodeIndex、明确启用的持久化增强配置 | 关联 Mapper statement/参数，收集条件脚本与依赖；内部 JSqlParser 补可支持的 SQL 结构 | 与 Java 方法/调用相连的持久化材料，供同一个 Step05 owner 消费；未配置不执行增强 |
| Step05 | JavaCodeIndex、可选 Step04 材料、已有冻结来源读取与材料预算 | 一次组装/选择完整阅读单元、引用映射、持久化和最终请求正文 | 自包含业务阅读材料及覆盖/限制，直接供模型解释；不是再生产一层技术 Flow/Proof |

### Step03：保留 Java 导航材料，而非换一种简化调用图

输入中的精确入口包含 entry 归属、methodKey 与完整声明 SourceRange，不能只给类名/方法名再猜重载。程序继续保留当前 JDT 方法、调用和关系能力：完整声明/方法体，形参及其注解，调用表达式与实参，实参→形参关联，调用所属条件、返回/throw，所有实现/构造器/延迟回调候选，以及已定位/候选/未解析与展开限制。不把接口边缘截断成仅剩方法名，也不恢复 JavaParser 作为隐藏 fallback。[现有 EntryCodeContext](../../src/main/java/org/sourceanalysis/app/analysis/code/EntryCodeContext.java)、[声明目录与 ParameterView](../../src/main/java/org/sourceanalysis/app/analysis/code/JavaDeclarationCatalog.java)。

输出仍是共享 JavaCodeIndex：同一方法内容可共享，但 entry 的成员关系与每个物理 call site 的归属保留，某个入口的展开状态不能污染另一个入口。冻结且 ready 的会话内保持现有导航 operation/location cache 与方法内容共享，不借新插件重做同一导航。[现有 JavaCodeIndex](../../src/main/java/org/sourceanalysis/app/analysis/code/publish/JavaCodeIndex.java)、[JDT collector](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/EntryCodeCollector.java)、[导航缓存实现](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtLanguageServerClient.java)。这些数据是阅读和定位材料，不宣称调用在某次运行中必然发生。

### Step04：可选 XML + SQL 材料补全

启用 MyBatis 增强时，输入是同一冻结来源的 XML 与 Step03 已有 Mapper 方法/调用材料。关联依据是 XML namespace、statement id 与 Java Mapper 声明，结合方法参数注解（如 `@Param`）及已有实参/形参对应，保留参数名、顺序、属性路径和注解原文。不能只按同名方法匹配：重载、多候选、动态引用或参数命名尚不确定时，保留候选/限制，而非虚构唯一绑定。Mapper 接口声明本身必须进入补全材料，即使没有 body；这样模型能够实际看到 `@Param`，而不只看到一张转换后的参数表。

程序沿用安全 XML 读取及 namespace/statement 候选索引，再补齐原 statement 正文、include 的原引用与 fragment 依赖、相关 resultMap/selectKey 材料，以及 if/choose/foreach/bind/trim 的条件/变量/循环树。保留原脚本是基础输出，SQL 分析投影及 AST 是附加材料；resultMap/selectKey 仅在实际存在和相关时加入，不伪造它们。已知静态 fragment/property 可按上文规则处理，未知依赖仍展示原文和未闭合原因。不加载客户 Mapper/result 类型，不启动客户框架，不求值客户 OGNL，不调用 getBoundSql(dummy)。

JSqlParser 是该插件内部优先候选，处理支持的静态语句/上下文片段，补充表/别名、投影、聚合、join/ON、WHERE、写入列/值及子查询结构。条件所属位置和源片段不能在提取时消失。输出必须区分原文、已解析结构、分析投影与未知动态位置；AST 不意味着业务结论或数据库运行结果。未配置时该增强不执行，原 JavaCodeIndex 和 Java 阅读材料保持不变；原 XML 可安全阅读但 SQL AST 失败时，仍把原脚本及具体限制交给 Step05，不阻断可读材料。

未来 JPA/Hibernate 增强在相同材料 seam 后提供实体持久化操作、实体/关联映射、JPQL/HQL/Criteria 或 native SQL 查询结构及限制，而不是强制产出一条 SQLString。实体变更/flush/cascade 与查询文字是不同材料，不能为迁就 MyBatis 实现而丢失前者。

### Step05：一个业务材料 builder owner

同一个 owner 接 Step03 和可选 Step04，合并现有 Step05 的入口上下文/相关来源组装、Capsule 的阅读投影以及 BusinessMaterialBuilder 的完整材料选择/来源映射职责。讨论目标是删除重复职责，不是把原三层分别改名后继续编译、投影、重开再包装；持久化 publisher 只保存/校验已组装结果，不重算关系或业务语义。

这个 owner 负责一次形成模型实际阅读的完整单元：入口及有关完整方法体、调用/实参形参/控制与返回材料、Mapper 接口声明和参数注解、XML statement/实际依赖原文，以及 SQL 结构/动态限制。可在材料内部去重共享内容，但最终模型请求必须自包含，正文含已选择单元的完整源码，不能只含 JavaCodeIndex key、Capsule ref、statement handle 或“请看附件”的引用。程序侧位置/来源映射仍沿用现有安全存储与短 SourceRef；模型不需要因此收到主机路径、hash、Proof 链或运行配置。

保存内容只保留一份规范源码正文及所需定位映射，提交模型时由同一 owner 的材料读取/组装职责补成自包含正文；不再次持久化一套逐字重复的 snippet，也不增加独立 hydration 包装层。材料预算针对完整单元检查，放不下就给具体覆盖/限制，不悄悄截掉 if/返回/XML 依赖后声称完整。该工作不再要求旧五文件、compilation ref、Fact accounting 或严格 Flow 成功作为 Java 阅读前置；这一点必须经下节所列生产依赖的统一迁移才能实现。

### 当前硬依赖：尚不能只删除旧 producer

当前代码查证如下，均是本轮只读观察，不是目标已经实现：

- [TechnicalAnalysisWorkflow](../../src/main/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflow.java) 的 selected-engine 路径仍执行 ProvenCodeFactsExecutor，再将 facts 传入 BusinessFlowsExecutor；JDT 没有严格 Facts 不代表它已绕过 accounting。
- [EntryContextAssembler](../../src/main/java/org/sourceanalysis/app/analysis/flow/compiler/EntryContextAssembler.java) 仍以 Fact publication/NOT_PRODUCED accounting 及其导航 lineage 为前置校验。
- [BusinessFlowsExecutor](../../src/main/java/org/sourceanalysis/app/analysis/flow/BusinessFlowsExecutor.java) 仍生产 FlowCompilation M1、CapsuleProjection M2，再由 M3 汇总发布。
- [BusinessMaterialBuilder](../../src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java) 的 reopenFlows 要求五个 payload；materialInput/capsules 要求 compilation 引用与 EntryContext 引用闭合。navigatedSourceSpans 过滤 bodyPresent，因而接口声明/参数注解虽可存在于 JDT index，未必作为源码片段进入模型包。materialJson 又同时保存 sourceRefs[].snippet 和 modelPacket.allowlistedRefs[].snippet，重复存了源码正文。新 owner 需要实质消除这两处材料问题，而不只是换名字。

因此 JavaParser、严格 Fact/Proof、generic graph producer 及专属测试只能在当前所有相关 producer/consumer、阶段编排和产物校验统一迁移后退出；保留必要历史 reader 数据类型用于读取旧成果，不继续保留旧 producer 的生产路径。通用 VerifiedSourceTextReader/来源验证、artifact/receipt 存储、短引用定位、共用断言与历史源快照/JDT 材料/326 Activities/模型结果不是这些 producer 的专属设施，不能一并删除。这里记录迁移保护条件，不安排实施批次；历史可读也不等于新增一条旧格式 writer 或自动转换通道。

### getFinishNumber：贯穿三个阶段的预期材料

主任务已只读核对既有 JDT 材料和上文固定 commit 的真实 XML：入口为 DepotItemController.getDetailList，只有 BILLS_STATUS_SKIPING 分支调用 getFinishNumber；Service 的 basic/purchase 与采购订单条件中会设置 goToType 为采购入库，Mapper 聚合结果用于 finishNumber，并可换算单位展示。此处沿用该核对，不重跑 JDT，不再生成 Activity，也没有实际运行 JSqlParser；下表是预期材料形状，不是新产生的解析输出或完整调用绑定验收。

| 阶段 | 本例预期留下的具体内容 | 不能由它推出的结论 |
| --- | --- | --- |
| Step03 | getDetailList 完整方法、BILLS_STATUS_SKIPING 调用所属条件、Service 分支/参数构造、getFinishNumber Mapper 声明及参数注解、结果给 finishNumber/单位换算的代码；保留精确 entry 成员与实参形参关系 | 不把条件调用当每个列表请求必然执行；不由方法名先填业务标签 |
| Step04 | 原 getFinishNumber statement；`IFNULL(SUM(di.basic_number),0)`、明细 LEFT JOIN 单头、连接键及 head 删除条件在 ON；meId/linkId 与明细未删条件在 WHERE；normal/apply 分支和 goToType/sub_type 条件；参数名关联与未解析位置 | 不把 ON 移到 WHERE，不把 normal/apply 同时视为恒成立，不把动态参数值猜成固定值 |
| Step05 | 一份模型可直接阅读的 Java 入口/Service/Mapper 声明完整源码、真实 XML 原文与条件说明、可解析 SQL 结构和来源映射；模型把调用条件、数量口径和展示使用放在同一上下文解释 | SQL 自己不会识别“采购入库”业务名；这是范围内已有记录的数量统计/展示材料，不能声称此查询创建采购入库单或证明某次已收货 |

业务理解来自 Java 条件/参数与 XML 数量口径的共同阅读；新增 SQL 材料是既有 Java 导航的补全，不是重新运行导航、重建旧 326 Activities 或再套业务证明层。

### 三项最小验收建议：均未实施、未运行

只在用户确认方向与验证范围后，对冻结 fixtures/确定性材料组装进行以下最小验收，不在本次启动 parser/JDT/客户构建或业务模型：

1. 静态聚合与 join：以本例可确认静态部分检查 SUM/IFNULL、LEFT JOIN、别名、连接键、ON/WHERE 原位置及值参数来源；最终序列化模型正文实际含完整 Java/Mapper 声明/XML 原文，不能只验证 index/引用存在。
2. insertSelective 条件写入：原文保留条件列列表与对应 values 的 if/trim 结构；解析增强不得把某个可选字段写成恒定写入，也不得独立展开出不对应的列/值；AST 不支持时仍保存条件脚本与明确限制。
3. include + foreach：保留 namespace/refid/property、原 fragment 和展开来源、collection/item/index/open/close/separator，以及外层条件/循环树；不求值集合或 OGNL，不把某一次集合展开当完整语义。未配置时无增强执行且原 Java 材料内容不变；原文可读而 AST 失败不阻断，同一源码正文不被重复保存。

以上验收只是未实施建议，不是兼容性、性能或业务语义通过结论。重划阶段/退出严格 Fact/Proof 是独立迁移讨论；可选插件的默认不变要求仍成立，新增材料不隐式触发旧分析/模型重跑。

## 后续核验边界

本次尚未验证候选 parser 对真实方言/动态片段的覆盖、Java→Mapper 参数和 statement 的绑定，以及 Hibernate source-only 语义上下文的受控接入。后续若获得明确设计与验证范围，只需按选定版本和冻结 fixtures 核验这些边界；不因调研笔记先增加配置矩阵、重做旧产物或启动客户代码。框架解析成功仍不构成库存、结算或流程先后等业务结论已被证明。

## 本次验证

已只读核对 scoped guidance、progress 模板和 MapperCapabilityCataloger；官方文档/源码链接支撑上述框架结论。部分 Hibernate 7.1 Javadoc/raw 分支链接读取失败，相关 API 事实改由官方 6.6 Javadoc 与官方 main 源码交叉核对，并明确未作版本等价承诺。JSqlParser 5.4 tag POM/源码读取失败，5.3 tag POM/API 与当前 README 用于限定范围的核验；直接只读获取上游 raw 的 curl 也因 DNS 解析失败，未下载任何依赖。本次没有运行测试/构建、依赖安装或代码实验，未运行 JDT 或产品模型；因此不提供解析性能、实际样例通过率、5.4 artifact 可用性或可直接接入的验证结论。

Step03–05 补充只读核对了文中所链当前 Java 实现；笔记空白格式、相对源码链接与关键保护条件检查通过，more-findings.md 对 HEAD 无差异。该文档检查不是三个建议验收已通过，也不是重新捕获来源、运行 JDT/parser 或重新解释既有 Activities。
