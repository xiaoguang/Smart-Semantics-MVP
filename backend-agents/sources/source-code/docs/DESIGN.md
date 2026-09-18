# Source Code Analysis Agent：从源码到仓库业务过程

## 1. 交付目标

本 Agent 面向不同的 Java/Spring 应用，不针对管伊佳写业务规则。它从一个冻结仓库中回答：

1. 系统支持哪些业务领域和业务过程？
2. 每个过程为什么存在，由哪些活动组成？
3. 每个阶段在什么条件下开始，执行什么，改变什么状态，何时拒绝，最后得到什么？
4. 哪些关系是源码直接支持，哪些是合理推断，哪些仍需确认？
5. 读者在哪里可以查看相应代码行或片段？

主要可读交付是仓库级 `business-processes.md`。固定九章 `document.md` 继续作为下游概览，但不承担业务过程发现，也不再作为判断语义成功的唯一标准。

术语以 [CONTEXT.md](../CONTEXT.md) 为准。当前实施工作是[JDT、可选持久化补全与第1—5步阅读材料](supplements/cross-object-process-reconstruction/jdt-persistence-reading-materials.md)：A工具实验和B的实施计划0--6均已完成，Step01--05生产接线、保存/重开、JDT运行/CLI、同次XML读取复用、旧producer退出新安装及469项clean CI均已验证。C的固定仓库验收已结束：325份材料、326条覆盖、1个明确导航失败；[实测结论](supplements/jdt-persistence-reading-materials-delivery.md)记录范围与局限。新生产止于第5步，不启动Activity、过程或九章；历史M10/Activity读取合同另行保留。

原[系统认识、聚焦选材与三阶段成稿](supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)已完成三阶段程序接线，真实三例仍有具体问题；用户认可目前可读性和业务联系，优化留待讨论。以[三例实测](supplements/cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)及[问题记录](supplements/implementation-lessons-and-followups.md)区分程序完成、样本质量和未执行范围。已有326条Activity及所有历史输入/输出保留。

## 2. 设计原则

- **成熟工具找代码。** 新目标只用JDT LS/Core定位Java实现与完整源码；可选MyBatis官方解析部件/JSqlParser补持久化材料。JavaParser与严格Fact/Proof生产路径退出，历史读取保留；实施计划0--6的直接验证与clean CI已完成，C的真实固定仓库验收仍以实际结果为准。
- **程序组织，模型理解。** Java 负责来源、ID、材料、覆盖、调度、保存和格式；LLM 负责业务领域、别名、过程成员、顺序、分支、目的和业务语言。
- **全仓发现与深入阅读分开。** 新仓库保留首次目录发现；已有目录直接重开，结合全部 Activity 导航和冻结文件目录增量选材，允许跨旧候选合读。
- **摘要只导航，原文才定规则。** 完整 ReviewedActivity 继续复用；所选关键原文在事实 DRAFT 前到位。WRITE只读完整实际事实草稿，最终RULE_REVIEW对照完整原文包、实际DRAFT和实际WRITE，直接修正交付正文。
- **语义优先，来源够用。** 读者只需短 SourceRef 回到文件、行号和代码片段；不为自然语言句子重建多层 Proof。
- **业务生命周期，不是技术模板。** 阶段由模型解释对象如何产生、流转和结束；不能用“接收参数→校验→执行→返回”冒充业务过程。同一Activity可有不同业务用法，规则必须限定适用用法。
- **具体条件不可缩水。** 已知 `status=0`、`0→1`、`purchaseStatus in {2,3}` 时，最终过程必须保留具体值和动作，不能写“状态满足条件”。
- **不硬编码行业。** Java 和 Prompt 都不预置销售、采购等期望答案；这些词只能来自被分析仓库或模型输出。
- **不因模型失败重扫。** JDT、Activity 和模型批次分别保存；候选重建失败只影响相应模型 job。
- **诚实而有用。** 静态代码可以支持“系统构造并保存对象”，但不能证明某次运行提交成功或外部系统已经完成动作。

## 3. 八步接力

| 步骤 | 稳定职责 | 主要输出 | 下游用途 |
| --- | --- | --- | --- |
| 01 `verified-source-inventory` | 固定并核验仓库文件、字节和源码位置 | source inventory、snapshot | 所有后续读取的共同来源 |
| 02 `application-discovery` | 识别应用能力和全部 Spring 入口 | profile、entry inventory、mapper catalog | 建立入口分母和精确方法位置 |
| 03 `program-graphs`（既有存储槽位） | JDT导航与完整Java源码收集 | java-code-index | 找到实现、声明、参数、分支、返回和候选边界 |
| 04 `proven-code-facts`（既有存储槽位） | 可选持久化补全，不再生成Fact/Proof | persistence-material-index | Mapper调用关联XML原文与可用SQL结构 |
| 05 `business-flows`（既有存储槽位） | 唯一入口阅读材料owner，一次组装与保存 | code-reading-materials、coverage、按需完整预览 | 本轮验收到此；以后供模型阅读 |
| 06 `flow-interpretation` | 既有Activity生成/审阅能力保留；新Step05消费另议 | 既有ReviewedActivity、coverage | 当前326条可直接读取，不重做 |
| 07 `repository-knowledge` | 全仓业务发现、候选深入重建、归并和发布 | process catalog、coverage、`business-processes.md` | 直接供读者审阅，也供九章使用 |
| 08 `nine-section-document` | 根据已归并过程目录写一份固定九章概览 | report JSON、SourceRefs、`document.md` | 仓库级展示与公共 render |

上表03–05已完成直接行为验证、旧producer退出新安装和469项clean CI；C的真实固定仓库验收已完成，导航失败与SQL部分解析均如实披露；未开展业务质量验收。旧step key仅保留存储定位，不代表还调用旧算法。新模块地址、版本、保存/读取与删除清单由[详细设计§7](supplements/cross-object-process-reconstruction/jdt-persistence-reading-materials.md#7-运行版本与历史迁移)维护。01–05无模型调用；本轮不执行第6步及以后。后文第6—8步语义设计作为已有/后续设计保留，不构成本次执行范围。

## 4. 三层语义模型

### 4.1 Activity：局部解释

`ReviewedActivity` 说明一个局部活动的目的、参与者、对象、输入、条件、步骤、代码定义结果、业务规则、公式、问题、限制和来源。它可以覆盖一个或多个入口，也可以被多个 Business Process 使用。

Activity 不负责回答整个销售或采购过程。当前 jshERP 实测已有 326 个完整已审 Activity，这是一份可复用的语义索引和详细材料，而不是最终过程目录。

### 4.2 Repository Business Catalog：全仓发现

程序把每个 Activity 的现有业务字段原文投影成 `ActivityIndexCard`，包含原businessRules，不由Java抽取行业标签或编写摘要。Luna/high 阅读全仓卡片，发现 Business Area、对象别名、候选过程和重叠成员关系。它只回答“哪些活动值得一起深入阅读”，不写最终步骤。

每个 Activity 必须有处置：进入一个或多个候选、独立活动或未分类。成员用途为 CORE、OPTIONAL、ROLLBACK、SUPPORT、QUERY 或 ANALYTICS。一个 Activity 可以同时属于销售履约和库存管理；查询和统计可支撑过程而不是伪装成时序阶段。

全仓分片合并以完整DRAFT保存的Activity处置作为覆盖分母。REVIEW裁决候选及业务边界；若它在重复输出整份处置长数组时仅产生重复或遗漏ID，程序保留DRAFT非成员处置，并按REVIEW后的实际候选关系重算PROCESS_MEMBER。该恢复不创建候选、不推断业务；未知Activity、非法处置以及完整唯一清单中的孤立PROCESS_MEMBER仍然失败。

已有目录是资料，不是成员白名单。目标在同一次全局选材中结合冻结项目说明、全体导航和文件目录判断系统特征，提出可证伪业务假设、具体调查问题、候选修正与阅读清单；不另开分类调用，不重跑原目录分片/合并。常识只提出待核实问题，不成为已确认能力。未涉及 Activity 承接原处置；新增或移出成员按最终候选更新，只为理解而读的 context Activity 不强迫成为阶段。被移出全部候选的原成员须由模型明确非成员去向，Java 不猜其业务用途。沿用现有覆盖记录，不新增台账。

### 4.3 Business Process：详细重建

程序依据问题选材取回完整 Activity 和同源冻结原文。目标每候选一次阅读检查明确首批保留/移出集合及可空补读清单，程序执行一次补读后冻结 ProcessReadingPacket。模型决定取舍，Java只取材；读完仍缺信息就记录具体未知。候选依次执行事实DRAFT、业务WRITE、最终RULE_REVIEW：WRITE只接收完整实际事实草稿，最终核对同时接收完整原文包、实际DRAFT和实际WRITE，局部修正正文及对应结构字段，返回完整最终结果。之后没有模型润色。详细输入与保存由[补充设计](supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)唯一维护。

同一通用 Activity 通过不同 `ActivityUse` 进入不同过程，也可在同一候选中有多个不同variant；候选按(ActivityId, variant)保留用法，完整Activity正文按ActivityId去重。例如“新增库存单据及明细”可以分别以销售订单、销售出库和销售退货用法出现，不能把所有分支复制到同一个阶段。

每个过程至少表达：

```text
名称
目的与适用范围
参与者与业务对象
活动用法
阶段
  narrative（完整业务正文）
  进入条件
  动作
  状态变化
  拒绝条件
  结果
  下一步/分支
重要业务规则（activityUseIds限定业务适用用法，不限定证据归属）
选中的业务对象说明、字段/维度、对象关系、公式/指标和示例问题
结束结果
支撑活动与相关过程
待确认联系
来源引用
```

Certainty 只使用 `CONFIRMED`、`INFERRED`、`UNRESOLVED`，并附在具体阶段、规则或连接上，不用一个总分掩盖差异。

来源约束保持轻量：规则可引用当前阅读包内实际提供的 statement 或 SourceRef，包括 context Activity 和未绑定旧 Activity 的冻结文本。Java 拒绝未知/未提供引用，不以旧候选成员关系限制选材。`activityUseIds` 只表示业务适用用法，不表示证据所有权。缺来源链接可留空，不启动补证据循环。

为避免 Step08 回读 raw Activity，详细过程还保存 `CatalogKnowledgeItem`。它只从候选的完整已审 Activity/源码或过程 REVIEW 中选择已有内容，类型限定为 `OBJECT`、`FIELD_OR_DIMENSION`、`OBJECT_RELATION`、`FORMULA_OR_METRIC`、`QUESTION`，并保留正文、owner、certainty、ActivityStatementRef 和 SourceRef。引用本身不能替代正文。被目录直接处置为 SUPPORT/STANDALONE/UNCLASSIFIED、没有经过候选重建的 Activity，由 `BusinessProcessDiscovery` 在同一个只读 corpus 内从原 ReviewedActivity 确定性投影同类知识项，并作为 `directActivityKnowledgeItems` 放入 `ProcessDiscoveryResult`；owner 保留 ActivityId 与 grouping disposition。这不会把它伪装成 Business Process，也不要求 Publisher 回读外部状态。

## 5. Step07 的两个深 Module

外部 Interface 保持小而稳定：

```java
public interface BusinessProcessDiscovery {
    ProcessDiscoveryResult discover(ProcessDiscoveryRequest request);
}

public interface BusinessProcessPublisher {
    BusinessProcessPublication publish(ProcessDiscoveryResult result);
}
```

`BusinessProcessDiscovery` 隐藏五个内部模块：

1. `FrozenAnalysisCorpus`：重开已有 Activity/M10 和同源冻结文本，按 ID、文件、行段与字面量读取；不导航、不查 JavaCodeIndex。
2. `RepositoryBusinessCataloger`：保留首次目录；已有目录直接重开，承载全局增量选材与候选修正。
3. `ProcessMaterialAssembler`：执行阅读请求；配合一次候选模型阅读检查，封装 DRAFT 前的完整阅读包。
4. `CandidateProcessReconstructor`：候选事实DRAFT → 业务WRITE → 最终RULE_REVIEW，形成详细已审过程。
5. `RepositoryProcessConsolidator`：一次仓库级 DRAFT/REVIEW，处理重复、父子、相关和替代过程。

`ProcessDiscoveryResult` 已经封闭携带归并目录、全部 Activity/Candidate 处置以及 `directActivityKnowledgeItems`。`BusinessProcessPublisher` 只校验引用与 coverage，并确定性发布结构化目录和 Markdown；它不调用模型，也不自行重新打开 Activity。完整子模块设计见[模块总览](modules/business-process-discovery/README.md)。

## 6. 模型执行顺序与复用

```text
旧目录 + 已保存 ReviewedActivity + 冻结文本
  → 一次系统认识与问题选材（新仓库先完成首次目录）
  → 首批取材
  → 每候选一次阅读检查 → 明确保留/移出 → 执行可空补读 → 封包
  → 候选 Process jobs 并行：每个 DRAFT → WRITE → RULE_REVIEW → 保存
  → 本轮三例确定性预览并停止
  → 全仓另行获准后：Repository consolidation DRAFT → REVIEW → 五文件发布
  → 未来可供九章概览；本轮不生成九章
```

仓库归并只接收确定性生成的“业务完整、证据精简”过程视图：保留过程目的、范围、对象、用法、阶段正文、具体条件/动作/拒绝/结果、规则和待确认联系；重复的 statement/source 证据字段留在程序侧完整过程内。归并模型只给出去重和关系裁决，程序将裁决应用到原始完整过程，因此不会因控制上下文体积而缩写最终业务正文。

归并的真实分母来自程序侧完整已审过程集合。REVIEW遗漏过程时安全 KEEP；模型建议合并但不满足无损条件时，沿用当前已实现行为，保留双方原过程及原因。未知 ID、重复处置或非法引用仍拒绝。不能拼接阶段或追加修稿来强行合并。

沿用现有 YAML 的全局并发和每 Provider 并发。候选 job 的三阶段及保存保持同一绑定和一个并发名额；不同候选并行。Activity、首次目录和仓库归并仍为原双轮；三例不执行仓库归并。

现有模型批次设计继续有效：材料属于 `sourceRunId`，新模型输出属于 `modelBatchId`；完整已审且 fingerprint 匹配的 job 才能显式复用。旧 DRAFT 或失败 REVIEW 不续接半轮。任何 Step07 模型失败都不得调用 Capture、JDT、图、Fact、Flow、Builder 或 ActivityExplainer。

PARTIAL 是闭合的语义处置，不是掩盖运行失败的状态。容量预检、`UNCLASSIFIED` 或 `INSUFFICIENT_MATERIAL` 可以在覆盖账完整时形成 PARTIAL catalog；Provider transport/runtime/schema、坏响应或 uncertain started request 属于 fatal，该批次不得发布正式 Step07 catalog，也不得继续 Step08。后续只能显式开启新模型批次，并复用仍然有效的材料或完整已审 job。

## 7. 来源和真实性

### 7.1 模型可见

- 精简 Activity 卡或候选所需完整 Activity；
- scope-local Activity/Process ID；
- Activity statement handle；
- 阅读决策可见冻结文件相对路径/fileKey及实际选中原文；过程DRAFT和最终RULE_REVIEW可见完整Activity、所选原文和包内短refs，WRITE只见完整实际事实草稿，不把文件目录当作已读正文；
- 已知限制和未解释范围。

### 7.2 模型不可见

- 本机路径、hash、run/artifact/publication identity；
- Provider 凭据、配额、并发或重试数据；
- 整套五图/Proof 链；
- 与当前候选无关的源码。

程序校验 ref 确实回到同一冻结来源。LLM 可用业务语言合理推断跨入口关系，但必须区分：源码直接行为、业务推断、当前无法确认。没有外部证据时，不声称支付完成、库存物理变化、消息已消费或某次事务已经提交。

## 8. 保存与主要输出

Step07 可复用检查点：

- `activity-index-cards.jsonl`
- `business-process-candidates.jsonl`
- `reviewed-business-processes.jsonl`

Step07 正式业务输出：

- `repository-business-process-catalog.json`
- `process-coverage.json`
- `business-processes.md`
- 本步独立保存的 `source-refs.jsonl`
- 从同一SourceReference确定性生成的 `sources.md`（完整源码与文件/原行范围，不新增取证）

当前五文件合同已实现：catalog/coverage/process Markdown为v2，来源JSONL/Markdown为v1，producer为v3。本轮目标producer v4，公共五文件字段不变；仅新候选过程采用私有三阶段reviewed-result-v3，旧pair严格保留而不冒充三阶段结果。阅读决策、Prompt及精确复用版本由[详细合同](supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)维护，不整体重置上游格式。新来源在封闭result前统一编号，旧结果不覆盖。

Step08 保留：

- `business-report.json`
- `source-refs.jsonl`
- `document.md`
- `report-validation.json`

固定 52/57 文件数量不是完成标准。检查点只服务可复用执行，不发展成重复证据层。正常进程内传 immutable view；磁盘重开验证来源和 Schema，不重新执行生产算法。

## 9. 九章的职责

九章固定为文档说明、业务目标、业务对象、业务活动、字段与维度、对象关系、指标口径、示例问题、待确认事项。

Step08 只消费已发布 `RepositoryBusinessProcessCatalog`、过程 coverage 和 SourceRef：

- 第四章按已归并 Business Process 组织，不再从 Activity 列表发现过程。
- 第七章只写过程目录中已有公式/指标口径。
- 第三、五、六、七、八章分别只使用 catalog 已保存的对象、字段/维度、关系、公式/指标和问题知识项；不能沿 statement ref 回读 326 个 Activity 后再解释一次。
- 第九章汇总 UNRESOLVED、未分类和未处理范围。
- 其余章节可以概括，但不能丢掉、改写或升级 Step07 的具体规则和 certainty。
- 正文只显示短 ref，完整源码在 `source-refs.jsonl`；纯重渲染零 Provider。

过程发现质量先通过业务正文验收。正文以最终RULE_REVIEW的阶段narrative为主，完整结构字段仍保存；关键规则不能只藏在明细中，本轮三例预览不输出HTML折叠。有直接源码引用时，用“查看依据”链接跳到过程来源索引，再按文件名/行范围打开sources.md片段，不铺满裸编号。来源链接允许留空，不强制说明或补齐、不增加取证/模型任务/专项验收，不阻塞主业务交付。九章不在本次修改范围，不能用格式正确掩盖生命周期缺失。

## 10. 实际材料与生命周期推演

[完整例子](examples/semantic-framework-walkthrough.md)保留已有Activity/M10的核对；[跨对象三例](supplements/cross-object-process-reconstruction/walkthrough.md)补充冻结页面、Mapper原文如何在DRAFT前进入同一包。明确区分实际代码、设计操作和人工示范，不冒充新模型结果。

已核实材料可支持“申请关联采购订单、关联订单入库/出库后的数量进度更新”等受限路径。默认状态、修改/审核/反审核条件必须保留原适用范围；价格检查不能从某单据子类型泛化到所有订单。退货查询不能证明整个退货生成、欠款冲减链，岗位和强制审批顺序也不得补造。

不在Prompt或Java中预置这些业务名称。它们只作为真实样例验收：模型应当自己发现用法并解释联系，不是开发者给出正确候选答案。

## 11. 历史业务实现状态（2026-09-16）

本节记录迁移前的业务链与可重开成果。它不覆盖本设计开头所述的JDT-only Step01--05当前生产状态，也不授权恢复JavaParser、严格图/Fact/Flow/Capsule或M10 producer。

已实现并保留：

- 已保存的JDT/JavaParser历史引擎结果和JDT导航/源码材料；
- Spring入口、旧Step05 EntryCodeContext、历史M10 BusinessMaterialBuilder结果；
- ActivityExplainer、任意 N 入口覆盖、DRAFT+完整 REVIEW；
- Provider 两级并发、逐 job 保存、固定材料与独立模型批次；
- 历史九章 checkpoint 的严格读取和确定性重渲染；当前生产路径不再生成九章；
- 固定 jshERP 的 326 个已审 Activity。

当前 main 已具备：

- FrozenAnalysisCorpus、Activity statement handle 和全仓 ActivityIndexCard；
- 分片目录 DRAFT/REVIEW、唯一目录合并及可重叠 Candidate Process；
- 完整 ActivityUse、详细 stage/rule、按需源码请求与 REVIEW；
- 仓库过程归并、三类分母 coverage、五项 canonical Step07 产物；
- 确定性 `business-processes.md`、独立来源文件和过程专用运行入口。

历史v1运行曾形成14候选、46过程（21个多Activity），覆盖CLOSED、语义PARTIAL；这不是当前补充设计的验收。本轮选定的后续已审目录实际为24候选、138个不同成员Activity，全部326条Activity仍可读。两份目录不混称同一运行，精确输入见补充设计入口。

跨批次复用的 JSON Schema enum 现按 UTF-8 稳定排序；历史输入 checkpoint 通过输入策略 Store 重开，新 Step07 通过当前策略 Store 发布。最终正式批次复用 22/22 个完整已审 job，模型调用为 0，也没有运行 JDT、Builder 或 ActivityExplainer。Step08 只消费归并过程目录的接线仍不在本轮范围。

旧 `ProcessExplainer` 的 340 个单 Activity/单 Stage 结果只作为历史对照；新过程专用入口不会消费它，也不会启动 Step08。

### 本次仍缺什么

阶段正文、规则用法、来源链接、五文件读写、统一CLI、跨候选选材、冻结文件读取、一次阅读检查及DRAFT前封包已实现。当前仍为过程双轮。新增系统认识、按问题收敛材料、DRAFT→WRITE→最终RULE_REVIEW、私有三阶段保存和三例预览尚待实现；研究正文的可读性已获认可，但写作引入的已知事实错误仍需最终核对纠正。三例结束先交付审阅，不自动扩到全仓，详见[实施状态](supplements/cross-object-process-reconstruction/implementation-status.md)。

## 12. 完成标准

- 同一实现处理不同 Java/Spring 业务仓库，不增加行业词典。
- 所有 Activity 有明确处置；候选和过程支持多对多。
- 真实目录自行发现对象/业务用法；过程能讲清实际业务步骤、分支和结束结果。多个Activity或Stage只是结构计数，不是语义通过条件。
- 同一Activity不同variant不混用规则；查询/统计与主流程职责分明，无法建立完整路径时保留片段和具体未知项。
- 每阶段写清具体条件、拒绝、动作、状态变化和结果。
- Activity 摘要、详细 Activity、源码核对和最终规则之间没有无声丢失。
- `business-processes.md` 能直接回答仓库有哪些业务、每种业务怎样进行。
- 每条重要结论可用短 ref 查看代码位置；无需重跑 JDT。
- 未能识别的 Activity、候选、外部效果和冲突关系有具体记录。
- 九章只汇总已归并过程，不重新猜流程，也不因格式完整冒充业务理解完整。
