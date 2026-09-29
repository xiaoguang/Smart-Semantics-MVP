# MyBatis 动态 SQL：现成解析工具与当前缺口

状态：**工具研究及当前源码审计；未批准或执行实现。** 2026-09-29。没有运行客户 Mapper、OGNL、数据库、JDT、Maven、测试或产品模型。这里区分三种产物：保留所有动态分支的 **XML 脚本结构**、给定一次参数后形成的 **SQL 形状**、对该 SQL 形状解析出的 **SQL AST**。没有一个不提供参数且不执行动态表达式的 SQL parser 能把任意 MyBatis 脚本变成唯一完整的执行 SQL。

## 当前代码究竟丢了什么

当前依赖已是 MyBatis 3.5.19 与 JSqlParser 5.3（[POM](../../pom.xml)）。`DefaultPersistenceAnalyzer` 保存 statement 原 XML 结构、参数 token 和 include 依赖；`staticSql` 遇到 `if/choose/foreach/where/set/trim/bind` 时跳过整个元素子树，得到分析用静态 SQL，`#{...}` 被换成 `?` 后交给 JSqlParser（[源码：静态副本](../../src/main/java/org/sourceanalysis/app/analysis/persistence/DefaultPersistenceAnalyzer.java#L1112)、[解析入口](../../src/main/java/org/sourceanalysis/app/analysis/persistence/DefaultPersistenceAnalyzer.java#L553)）。因此 `statusArray` 的 `foreach` 仍可在原 XML 中阅读，却没有被展开为本次参数对应的 `IN (?, ?)` 等语句；只对没有嵌套动态元素的简单 `if` 片段作有界 WHERE 投影，包含 `foreach` 的 `if` 会被跳过（[源码](../../src/main/java/org/sourceanalysis/app/analysis/persistence/DefaultPersistenceAnalyzer.java#L661)）。

`ORDER BY` 是另一种缺口：JSqlParser 5.3 的 `Select` 已公开 `getOrderByElements()`（[该版本官方源码](https://github.com/JSQLParser/JSqlParser/blob/jsqlparser-5.3/src/main/java/net/sf/jsqlparser/statement/select/Select.java#L173)），但本项目 `plainSelectNode` 只投影选择项、FROM、JOIN、WHERE、GROUP BY、HAVING、DISTINCT；没有读取排序项（[本地源码](../../src/main/java/org/sourceanalysis/app/analysis/persistence/DefaultPersistenceAnalyzer.java#L918)）。因此静态 `ORDER BY` 可进入 `analysisCopy` 和 JSqlParser 原生 AST，却不进入**存储的项目 AST**。`SqlStatus.PARTIAL` 只由 `dynamicXml` 决定；排序项投影遗漏本身不会改变状态或产生限制提示（[本地源码](../../src/main/java/org/sourceanalysis/app/analysis/persistence/DefaultPersistenceAnalyzer.java#L633)）。这不是 JSqlParser 5.3 不支持排序的证据。协调者另作受控 JShell 观察，解析器分别返回内外层 `ORDER BY`；该进程退出时首选项写入失败，因此只将解析输出视为观察，不把整个命令记为通过。

## 现成工具可直接承担的部分

| 工具 | 确认的能力 | 不能由此推断的能力 |
| --- | --- | --- |
| **MyBatis 3 XML 脚本处理器** | `XMLScriptBuilder.parseScriptNode()` 将 `if/choose/foreach/trim/where/set/bind` 等 XML 节点编为 `SqlNode`，动态脚本得到 `DynamicSqlSource`；`getBoundSql(parameterObject)` 对**给定参数对象**应用这些节点并形成 SQL、参数映射和额外参数。[官方 3.5.19 源码：XMLScriptBuilder](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/scripting/xmltags/XMLScriptBuilder.java)、[DynamicSqlSource](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/scripting/xmltags/DynamicSqlSource.java)、[BoundSql](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/mapping/BoundSql.java)。 | 不是通用 SQL AST；`BoundSql.getSql()` 通常仍有 JDBC `?`，值在参数对象/映射中，并非把字面值安全插入 SQL。一次参数只覆盖一次分支和集合长度；没有穷举所有可能条件的承诺。MyBatis 允许自定义 `LanguageDriver`，不能假定所有 Mapper 都只走默认 XML 脚本。[官方动态 SQL 文档](https://mybatis.org/mybatis-3/dynamic-sql.html)、[XMLStatementBuilder](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/builder/xml/XMLStatementBuilder.html)。 |
| **JSqlParser 5.3** | 对已形成的、该语法版本可接受的 SQL 用 `CCJSqlParserUtil.parse` 得原生 SQL AST；`Select.getOrderByElements()` 能读取排序项。项目现已使用这个依赖。[该版本官方源码](https://github.com/JSQLParser/JSqlParser/blob/jsqlparser-5.3/src/main/java/net/sf/jsqlparser/statement/select/Select.java#L173)、[官方使用文档](https://jsqlparser.github.io/JSqlParser/usage.html)。 | 不解释 MyBatis XML/OGNL，也不替项目决定哪些原生 AST 字段进入持久化投影；具体 SQL 方言和构造需按样本验证，不能承诺所有 SQL 都可解析。 |
| **Alibaba Druid SQL parser**（备选） | 官方文档展示 `SQLUtils.parseStatements(sql, dbType)`、SQL AST 与 visitor，包含排序表达式统计；可在需要特定方言对照时评估。[官方 SQL Parser 文档](https://github.com/alibaba/druid/wiki/SQL-Parser)、[AST 文档](https://github.com/alibaba/druid/wiki/Druid_SQL_AST)。 | 也只接收 SQL，不展开 MyBatis XML/OGNL；本仓库未集成，不能据文档宣称其对当前样本比 JSqlParser 更完整。 |

## 安全与“完整”的边界

MyBatis 的 `getBoundSql` 不需要执行数据库查询，但它会应用动态节点。`if` 求值经过 OGNL；`${...}` 也求值并将结果直接并入 SQL；官方 `<bind>` 示例甚至显式调用参数对象的 `getTitle()`（[IfSqlNode](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/scripting/xmltags/IfSqlNode.html)、[OgnlCache](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/scripting/xmltags/OgnlCache.html)、[TextSqlNode](https://mybatis.org/mybatis-3/xref/org/apache/ibatis/scripting/xmltags/TextSqlNode.html)、[官方示例](https://mybatis.org/mybatis-3/dynamic-sql.html)）。**推论：**在同一进程中对不可信客户 XML/参数调用它，可能调用参数 getter/方法或触发其它表达式副作用；“不连数据库”不等于“纯静态、安全读取”。本次研究没有跨过该执行边界。

若目标是准确阅读所有可能分支，应保留原 XML、条件、循环及参数来源，并明确其为**脚本结构**。若目标是某一次调用的具体 SQL 形状，必须有可信的参数对象与隔离的执行边界，然后才能用 MyBatis 形成 `BoundSql`、再用 JSqlParser 解析其 SQL；还须分开记录 `?` 的参数映射及原始 XML 来源。运行时插件/拦截器可能再改写 SQL，因此该结果也不能自动称为最终数据库收到的语句。对当前研究范围，最直接、已证实的缺口是补全**现有** SQL AST 投影的排序字段；动态执行属于另外的授权与安全设计问题，不能用自行编写 OGNL/`foreach` 解释器代替 MyBatis。
