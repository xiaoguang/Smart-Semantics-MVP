# MyBatis / JSqlParser 最小工具实验

状态：**A通过，可进入B生产接线；B/C尚未完成。** 结论限定为安全读取、自动关联、完整动态原文及有界SQL结构可用，不表示全部动态SQL可转换成一条确定SQL。

对应[实施计划](../plans/jdt-persistence-reading-materials-implementation-plan.md)步骤1与[详细合同](cross-object-process-reconstruction/jdt-persistence-reading-materials.md#9-验收a工具最小调研实施)。本实验不运行JDT、客户构建、OGNL、数据库或产品模型。

## 固定输入

- jshERP提交：`8c30ce7861570458920175e200bb2a6442713580`。
- 已有冻结快照：`snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c`。
- 已有Java索引来源运行：`analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b`，Step03 module7 `java-code-index-v2`。
- 真实样本的核查目标：关联数量聚合/连接查询、条件化插入、include与foreach依赖。样本名字仅用于输出核查，自动关联不接收预期XML目标答案。
- 中性fixture用于跨命名和动态结构、安全行为验证，不能代替真实源码实验。

已直接读取冻结manifest核验：719个文件记录中有65份可读XML，共672340字节；65份XML的原始字节大小和SHA256均与manifest一致。这只是输入核验，不是全部XML已成功解析。

## 实际安装与API观察

| 工具 | 实际固定版本 | JAR SHA256 |
| --- | --- | --- |
| MyBatis | 3.5.19 | `93eea616ae355751bd5fbabb57f0732713fbe79f3196f33c51a0aeeb4255862a` |
| JSqlParser | 5.3 | `41bcb5b00488231db179cb5a375690830a59aba521dfa303daa94dcb9dcc8e88` |

依赖解析已成功。原始尝试因沙箱不能写本机Maven缓存失败，随后获得执行环境授权并完成下载；不是解析能力失败。实验编译/测试宿主使用Java17。

从已安装JAR通过`javap`观察到：

- `XPathParser(Document)`与`evalNodes`、`XNode.getNode()`：接受已安全解析的DOM并读取节点。
- `XMLIncludeTransformer(Configuration, MapperBuilderAssistant).applyIncludes(Node)`：可尝试在工作副本展开片段。
- `CCJSqlParserUtil.parse(String)`：完整SQL解析；`parseExpression(String, boolean)`、`parseCondExpression(String, boolean)`：表达式解析，可以禁止只接受前缀。

API存在不等于已完成取材。实际动态条件、原文与AST的完整性需下列检查通过。

## 当前验证

| 检查 | 实际状态 |
| --- | --- |
| 独立研究POM、固定依赖解析 | 已完成；不修改正式应用依赖 |
| 首批7项RED | 已执行：7项预期断言失败，0错误；原因是实验类尚不存在 |
| 增补自动绑定及include缺失/循环后的9项RED | 已执行：9项预期断言失败，0错误，3.589s；同为缺少实验类；日志red-nine-tests.log |
| XML/include/SQL实现及GREEN | 14项通过，0失败/错误；最终定向构建4.184s，测试0.626s |
| 固定XML与JDT索引自动关联 | 真实驱动成功，3.879s墙钟时间；输入只有manifest、blob目录、已保存Java索引和输出位置 |
| 三项实际输出与工具采用结论 | 下文逐项核对；采用官方窄部件＋DOM原文＋有界SQL增强 |

研究代码位于`research/persistence-tool-feasibility/`。RED报告保存在忽略目录`.workspace/persistence-tool-feasibility-20260917/red/`。后续完整输出保留在同一实验工作区，不覆盖旧业务资料。

驱动只接收冻结manifest、blob目录、已保存Java索引和新输出路径；XML按manifest校验大小与摘要，再交给XPath识别，不接收预期Mapper路径或方法答案。输出禁止覆盖已有文件。

## 真实输出及三个样本

[完整自动输出](../../.workspace/persistence-tool-feasibility-20260917/snapshot-probe-result.json)的SHA256为`869c35a21ee1826c391648b7aea4492801165f842e91b0988807042e46278905`。65份XML中61份识别为Mapper，取得573条语句，自动形成572项Java方法关联。SQL投影状态为112条PARSED、237条PARTIAL、224条UNSUPPORTED；后两类仍保留原文，不表示语句丢失。

### 1. 关联数量查询：getFinishNumber

程序从JDT方法`method:90522007ca87e88618a6ee65775a3bb8579f5c181d8e0d9da2b96b1799dec074`及声明`com.jsh.erp.datasource.mappers.DepotItemMapperEx.getFinishNumber`，自动关联到`jshERP-boot/src/main/resources/mapper_xml/DepotItemMapperEx.xml`中同名语句。

实际投影包含：

- 聚合：`ifnull(sum(di.basic_number), 0)`。
- 主表：`jsh_depot_item di`；LEFT JOIN：`jsh_depot_head dh`。
- ON：`di.header_id = dh.id AND ifnull(dh.delete_flag, '0') != '1'`，未搬到WHERE。
- WHERE：商品扩展标识、关联明细ID及明细删除标志三个原过滤条件。
- 三个动态条件完整保留：`noType == 'normal'`、`noType == 'apply'`、`goToType != null and goToType !=''`。
- 声明中的五个参数及`@Param`别名保留；`meId/linkId/linkStr/goToType`与原占位表达式对应。`noType`用于XML条件而不是SQL占位符，因此其占位符列表为空，条件原文仍在。

状态为PARTIAL：去除可选块得到的是分析骨架，并非最终执行SQL。完整statement和原XML同时保留；模型以后可以看到不同条件分别使用`link_number`和`link_apply`，不再止步于Java接口声明。

### 2. 条件化插入：DepotHeadMapper.insertSelective

真实`DepotHeadMapper.xml`保留35组条件列/值，例如：

| 原条件 | 原列片段 | 原值片段 |
| --- | --- | --- |
| `number != null` | `number,` | `#{number,jdbcType=VARCHAR},` |
| `deposit != null` | `deposit,` | `#{deposit,jdbcType=DECIMAL},` |
| `linkApply != null` | `link_apply,` | `#{linkApply,jdbcType=VARCHAR},` |

没有给这些参数编造取值，也没有执行OGNL。去除trim动态块后不是完整INSERT，因此状态PARTIAL并保留解析原因；不能把35组可选列当作必然写入。此投影只是本例原条件相同的片段对照，不是通用动态SQL执行器。

### 3. 公共片段与集合条件：DepotHeadMapper.selectByExample

实际依赖为`Base_Column_List`和`Example_Where_Clause`；官方`XMLIncludeTransformer`已成功展开工作副本。原include引用仍保留。展开后的材料包含`oredCriteria`、`criteria.criteria`、`criterion.value`三层集合条件，以及`criteria.valid`和noValue/singleValue/betweenValue/listValue选择条件。

这证明并非只找到XML文件名：引用片段的实际条件已经进入阅读结构。原XML文件字节独立保留；DOM序列化只标为结构化副本，不冒充原始片段。

## 采用范围与已知限制

- 采用MyBatis `XPathParser/XNode/XMLIncludeTransformer`；不用完整`XMLMapperBuilder`加载客户类型，不用`getBoundSql`或OGNL。
- JSqlParser已在Java17实际解析上述聚合/连接；动态XML仍以完整原文为主。UNION和尚无专用visitor的语句明确未完整投影，不用强制转换或空AST冒充成功。
- 真实输入暴露的UNION、无ON连接错误均是研究适配层getter误用；各自先增加失败回归，再修正。不是JSqlParser不支持这些语法。
- XML安全测试覆盖标准DOCTYPE、外部一般/参数实体、XInclude；禁用外部解析，保留的OGNL表达式不执行。真实generatorConfig.xml不是Mapper且使用其他DTD，被拒绝；另3份非Mapper为无mapper根，不影响61份Mapper。
- 驱动把索引全部Java方法送入关联，因此有8615条“没有同名XML”观察，**不可当作8615个Mapper缺口**。生产接线需按已有Mapper目录/实际声明范围处理。
- 研究JSON约18MiB，包含按语句重复的整文件原文；它不是生产存储合同。B按设计将原XML单元只保存一次，语句和入口引用复用。
- 本次范围定位采用整个冻结XML文件；不自研XML tokenizer取行号。原始文本保持完整。MyBatis为Apache-2.0，JSqlParser按其Apache-2.0许可分支采用；生产依赖需同步声明。
- 本次没有JDT、客户构建、数据库或产品模型调用，没有改写已有Activity和模型结果。

## A判定

官方组件复用、安全、原文与条件完整性、三个真实样本、自动关联及中性fixture均已得到实际结果。**A通过，采用范围如上；下一步生产接线，不扩大动态执行语义，不执行第6步。** 本报告不宣称B/C完成或业务解释质量提高已获模型验证。
