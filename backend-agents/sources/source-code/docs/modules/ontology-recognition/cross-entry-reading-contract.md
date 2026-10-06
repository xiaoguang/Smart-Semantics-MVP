# 本体阅读：已读证据反查与跨入口联系调查

适用范围：以下保留实验引用的类型安全矩阵和检索能力，旧记录按原格式读取。正式目标的请求绑定作用域、E/U/K短号、S原文包、已选/待读状态和定义结构由[material-preparation](material-preparation.md)及[contracts](contracts.md)拥有；实验v2的viewId引用不直接复用为正式v4决策。本页不另设新的模型额度。

状态：2026-10-01。本页是[共同线索与聚焦选材](evidence-navigation-and-selection.md)的细化合同。P2i 历史批次在已读 `J1/J4` 的查询键校验处停止；本轮类型安全修正和零模型重放已证明可跨过这个技术断点，**没有重新运行 P2i 产品模型，也没有产出无提示已审本体**。共同表、方法和 DDL 均不得被程序自动写成已确认业务关系。本页不批准新的真实模型调用、DDL 全量解析或正式本体发布。

## 1. 目标、边界和词义

目标不是把每个 entry 自动连成一条业务流程，而是让程序从**已保存的准确技术身份**找出值得共同阅读的入口，给模型一个小问题和少量完整原文，再核对是否存在对象身份或业务交接。两个入口可能毫无关系；空结果和反例都是合法结论。

本页严格区分三层：

| 层次 | 谁产生 | 能说什么 | 不能说什么 |
| --- | --- | --- | --- |
| 技术共现线索 | Java 对已验证 R4 做等值反查 | 两个入口的材料包含同一方法、语句或已解析表；返回准确句柄和未显示范围 | 两个业务同步、同一对象、先后顺序、某次运行发生 |
| 候选业务联系 | 模型阅读实际方法/调用、SQL、页面或有界 DDL 后提出 | 说明待核对的标识、写入/读取动作、适用条件和反例 | 未经审阅的正式 Link Type |
| 已审对象/关系 | 模型对完整相关原文及实际候选审阅，Java 校验合法来源和范围 | 在明确范围内保存 `CONFIRMED`、`INFERRED` 或 `UNRESOLVED` 的定义 | 静态代码证明真实部署中发生了付款、收货或实时同步 |

用户所说的“两个流程在一个表上有同步”在本合同中**不等同于同表访问**。只有同一对象标识的实际传递/保存/读取或更新得到原文支持，才可以调查“业务交接”；若只有共用表、字段名或通用 Service，就停在技术共现。跨 Controller 也遵守同一规则，不要求 Controller 之间直接互调。

输入仅为新 R4 `entry-evidence-v1` 家族及其准确 PREPARED_V1/R0；可选 DDL 只能来自同 R0 有效文件。旧 Activity、过程、M10、历史模型结果不参与无提示发现。现有完整源码、XML、SQL、前端单元及限制不删减；本轮不重新运行 JDT、Vue、MyBatis 或客户程序。

## 2. P2i 的真实断点和最小查询合同

P2i 第二轮的模型输入包含 `readingPacket.units` 中四个真实 `JAVA_METHOD` 单元；`J1/J4` 是 `{viewId, ref}` 限定的**包内短引用**，并非模型编造的字符串。模型返回两个 `METHOD_USES` 查询，目标为这两个已读方法。现有 `OntologyDecisionRunner.validateQuery` 只允许导航 `CLUE/METHOD`；`OntologyReadingCoordinator.query` 同样只从 `key.clue()`取键。因此即使只放宽前者，后者仍不能执行。此断点尚未进入 EXTRACT/REVIEW，P2i 的无提示已审小样为零。[原始记录与断点](../../supplements/ontology-recognition-poc-acceptance.md#p2g之后的最小调整与独立重验)

现行类型矩阵如下；未列出的组合一律拒绝，不接受模型直写的 methodKey、表名、文件路径或裸 `J1`：

| 查询 | 仍可使用的导航键 | 新允许的已读包键 | 程序取实际查询键 |
| --- | --- | --- | --- |
| `ENTRY_UNITS` | `ENTRY` | 无 | 已验证 entryId |
| `METHOD_USES` | `CLUE/METHOD` | `PACKET_UNIT/JAVA_METHOD` | 线索 lookupKey 或已读单元 `originalId` |
| `STATEMENT_USES` | `CLUE/STATEMENT` | `PACKET_UNIT/XML_STATEMENT` | 线索 lookupKey 或已读单元 `originalId`；仍遵守语句变体歧义规则 |
| `TABLE_STATEMENTS` | `CLUE/TABLE` | 无 | 已解析 AST 的表线索键 |
| `COLUMN_STATEMENTS` | `CLUE/COLUMN` | 无 | 已解析 AST 的列线索键 |

具体执行顺序：

1. `OntologyNavigationView.resolve` 用**本次实际可见**的 `viewId + ref` 还原 `Target`；不同包的 `J1`、过期视图和未知引用均拒绝。
2. `OntologyDecisionRunner` 用上表验证查询种类与目标种类；`retainedRefs` 仍只接包单元，`readRequests` 仍只接可读取单元，不能因为本次扩展而互换。
3. `OntologyReadingCoordinator` 对已读方法/语句从 `PackedUnit.originalId` 取经 `OntologyReadingPacket` 保存的真实身份，不从模型响应的中文说明或 `content` 字符串猜键。初始已读单元已由 Corpus 的 `read` 和 canonical bytes 核对；包视图只能指向该包内的成员。
4. 执行现有 `OntologyEvidenceCorpus.methodUses/statementUses`，返回有界 `UnitPage`。`limit` 及合计页数仍受已有上限，`offset` 仍按 Corpus 实际总数校验；不自动翻到所有入口。
5. 把查询页、准确总数、未显示数、来源映射和原始模型响应**分别保存**。下一轮可见查询页中的入口/单元，模型再决定读什么；反查结果不自动进入当前原文包，也不自动批准一个 Link Type。

本轮只修改现有 DecisionRunner/ReadingCoordinator 的私有阅读 Interface，不新增公共 Agent 方法、CLI、查询引擎或模型步骤。实际 Prompt 已明确“已读 `J` 方法可查询 `METHOD_USES`、已读 `X` 语句可查询 `STATEMENT_USES`，其余沿类型矩阵”；资源内容变化参与新任务指纹。历史 P2i 请求/响应和失败状态保持原样，不就地改成成功。

## 3. Java 能确定性建立哪些 entry 邻居

`OntologyEvidenceCorpus` 当前已按 `methodKey`、`statementRef` 和 SQL AST 中实际 `TABLE/COLUMN` 节点提供反查。Java 可以对每个命中返回入口句柄、原始单元、查询种类、总数及未显示范围。未来正式 `prepare-ontology` 可把这些私有分页结果作为调查导航；**当前没有已发布的全仓 entry 关系文件**，本次实验不新增该产物。实现成本应停在复用现有索引和薄投影，不生成全入口两两组合。

线索的强弱必须保持可检查，不做虚构的单一相关度分数：

| 已保存证据 | Java 可直接报告 | 若要判断业务交接，后续须读什么 |
| --- | --- | --- |
| 同一 `methodKey` 出现在两个入口材料 | 两入口有共同方法材料；保留调用目标是否确定等原限制 | 两侧的真实调用位置、参数、分支和方法用途；公共工具方法可能是反例 |
| 同一 `statementRef` 的绑定或用法 | 同一静态 Mapper 语句的候选使用范围 | 绑定的 Java 调用、XML 动态条件、不同 `databaseId` 变体 |
| 两入口 SQL AST 出现同一表名 | 仅在实际解析覆盖内共用表线索 | 哪一方读/写、以什么标识、条件和字段作用于哪类记录；`PARTIAL` 不代表全 SQL 已解析 |
| 相同列名 | 字面相同且实际 AST 提取的列候选 | 列的表/别名限定、参数传递及语义；泛用 `id/status` 不能直接合并 |

Java 负责“哪些 entry 值得进一步读”，模型只收到一个具体问题、有限邻居及选中的完整原文。模型不需要记住全部入口、复述长 ID 或凭大上下文完成穷举；程序也不使用 ERP/CRM 关键词或表名推断业务关系。高扇出方法/表仍分页披露实际分母，不能因为只展示第一页便写“仅此两个入口”。

正式 `prepare-ontology` CLI 若接出跨入口导航，应复用 Corpus 的倒排键而不是把所有 entry 两两组合：从每个入口已保存的 `methodKey`、`statementRef`、实际 SQL AST 表/列键建立 `技术键 → entryId 集合`；按准确键取交集，合并同一入口对的多个命中，并按稳定身份分页。**当前只实现了这些有界反查 API，尚未发布全仓邻居文件或新增 CLI 命令。**建议的私有候选形状为 `leftEntryId / rightEntryId / signals[{kind, identity, sourceUnitHandles, limitations}] / totalMatches / unreadMatches`；其中 `signals` 是可重读的技术事实，不含 `BUSINESS_LINK` 或“同步”标签。通用日志、权限、分页和基础查询方法的高扇出应披露命中总数，并可供调查者跳过，不能静默删除，也不能因为共用它们而给出高业务相关度。

后续模型输入应只包含一个调查问题、一个有限候选页、少量按模型请求读回的完整单元以及明确的未读分母。小上下文模型也不需全仓记忆或长 ID 对齐。模型第一次只提“需要核对哪个对象标识、动作方向、条件或反例”，读到实际原文后才输出候选业务关系；第二次对该候选和原文核对。若邻居页过大就继续分页或留下未调查范围，**不压缩源码、不提高大模型等级来掩盖材料选择错误**。

判断某个候选 Link Type 时，模型至少分别回答：两端是什么业务对象；同一对象或关联对象通过哪个已保存的标识/字段对应；涉及哪一条实际写入、读取或更新及条件；哪些入口和分支不适用；哪一部分仍不能确认。只有看过相关原文才能引用它。若无法证实相同标识或动作方向，输出 `UNRESOLVED` 或不建立 Link Type；“都使用同表/Service”不得改写成“同步”。最终是否 `CONFIRMED`/`INFERRED` 由原文审阅决定，Java只检查结构和引用。

## 4. DDL：可选的定向补证，不是本次断点的前置条件

同 R0 中有已核验的 `jsh_erp.sql`，但本次 P2i 模型包未包含 DDL；不能把人工读取过的 DDL 说成模型已见证据。若调查某个已定位的表需要核对主键、列类型、NULL、索引或**明确声明的**外键，可按同源相对路径读取对应定义，并单独记录实际原文范围和解析限制。已有 JSqlParser 的有限走查只证明两张指定表的定义可解析，整份 SQL 导出曾在方言语句处失败；不得因此自写 DDL/SQL 解析器、批量装入导出数据 INSERT 或宣称全库结构已可枚举。[有限走查](evidence-walkthrough.md#2-ddl能提供什么已经验证到哪里)

DDL 是存储声明，不说明谁先操作、Service 是否调用、运行时实际 SQL 或付款/库存动作是否发生。普通 INDEX 不是 FOREIGN KEY；同表也不能证明两条业务“同步”。没有 DDL 时仍可用现有 Java/SQL 证据提出并核对联系；只有“数据库明确声明了某约束”这一类结论不能凭代码猜。

若后续确需自动 DDL 表导航，应另做成熟工具的有界适配实验：绑定同 R0、只索引 parser 实际识别的表及列、明确整文件失败和无法精确定位的范围。该能力不是修复 `J1/J4` 的条件，也不是这次薄实验的实施范围。

## 5. 薄实验、验收和停止边界

本次问题仅是：**同一固定证据包里的已读方法/语句能否安全充当反查键，程序能否不靠模型猜测找到真实其它入口，同时继续拒绝错类型？**实验不宣称已经识别业务本体。下面的第1–3项已离线通过；准确数值与保留边界见[验收记录](../../supplements/ontology-recognition-poc-acceptance.md#已读引用的零模型重放)。

执行顺序：

1. 用现有 `OntologyDecisionRunner` 和 `OntologyReadingCoordinator` 的直接测试先做 RED：已读 `JAVA_METHOD→METHOD_USES`、已读 `XML_STATEMENT→STATEMENT_USES`；在当前代码分别应出现类别拒绝。再以最少代码 GREEN；同时测试 `J` 不能做 `STATEMENT_USES/TABLE_STATEMENTS`、`X` 不能做 `METHOD_USES`、未知或另一个 viewId 不能借同名短号通行。
2. 使用 P2i 保存的第二轮**原始响应**和四个实际已读 R4 方法构造同一 packet，核对 packetId/viewId 与原请求一致；只把保存的模型字节作为本地 scripted 响应，不初始化 Codex Provider。检查两个 `METHOD_USES` 查询从真实 methodKey 返回有界页、页内容可按 R4 入口重读、原始模型记录字节不变。若无法重构相同包或任一身份不符，实验失败，不改写响应凑成功。
3. 用 R4 中一个已核对的跨入口共同方法做第二个零模型反查，核对至少两个真实 entry 的命中与原文身份；这只证明 Java 能提供跨入口**技术邻居**，不证明两个业务同一或先后。可用[现有人工走查中的分类查询例子](evidence-navigation-and-selection.md#6-从实际证据看一条线索不把人工核查说成程序输出)核对，不把例子名称放进通用 Prompt。
4. 记录请求数 **0**、准确查询结果/限制、直接测试、字节校验和仍未验证的语义目标。实验若只越过查询断点而没有 EXTRACT/REVIEW，G1 仍为未通过；是否另发真实模型请求须另行确定，不由本实验自动触发。

通过条件是：已保存 P2i 响应可以通过相同包的类型校验并取得真实查询页；类型隔离仍成立；共同方法在两个真实入口中的结果可重读；没有重新扫描、DDL全量解析、业务模型调用或自动生成本体。失败时保留具体失败层：包重建、来源、类别、查询页或容量，不能把某层通过说成整体业务识别成功。

实验输入/输出保留在忽略的本地实验目录，代码只保留真正需要的类型安全修正及直接回归测试。历史 P2i 运行和原始模型响应不覆盖；本页的设计结论若被实测推翻，先修正文档再扩大实现。
