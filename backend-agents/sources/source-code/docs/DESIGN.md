# Source Code Analysis Agent：从源码到仓库业务过程

## 1. 交付目标

本 Agent 面向不同的 Java/Spring 应用，不针对管伊佳写业务规则。它从一个冻结仓库中回答：

1. 系统支持哪些业务领域和业务过程？
2. 每个过程为什么存在，由哪些活动组成？
3. 每个阶段在什么条件下开始，执行什么，改变什么状态，何时拒绝，最后得到什么？
4. 哪些关系是源码直接支持，哪些是合理推断，哪些仍需确认？
5. 读者在哪里可以查看相应代码行或片段？

主要可读交付是仓库级 `business-processes.md`。固定九章 `document.md` 继续作为下游概览，但不承担业务过程发现，也不再作为判断语义成功的唯一标准。

术语以 [CONTEXT.md](../CONTEXT.md) 为准。本轮批准的设计差异、当前实测基线和未来实施范围见[业务过程发现与重建设计变更清单](plans/business-process-discovery-and-reconstruction-change-design.md)。

## 2. 设计原则

- **成熟工具找代码。** JDT LS/Core 负责 Java 定义、实现、调用位置和完整方法；JavaParser 保留为能力较弱的可配置 Adapter。
- **程序组织，模型理解。** Java 负责来源、ID、材料、覆盖、调度、保存和格式；LLM 负责业务领域、别名、过程成员、顺序、分支、目的和业务语言。
- **全仓发现与深入阅读分开。** 模型先看全部 Activity 的精简卡建立目录，再按候选打开完整 Activity 和选定源码。
- **摘要只导航，原文才定规则。** ActivityIndexCard 不能替代 ReviewedActivity；ReviewedActivity 仍不足时，REVIEW 从已保存 JDT/源码核对关键片段。
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
| 03 `program-graphs` | 选定代码引擎；JDT 路径生成导航索引，JavaParser 路径可附五图 | java-code-index、可选图增强 | 找到完整实现、参数、分支和边界 |
| 04 `proven-code-facts` | 有图时保留严格技术 Fact/Proof；无图时明确 NOT_PRODUCED | facts/proofs 或未生成说明 | 可选技术增强，不阻断业务阅读 |
| 05 `business-flows` | 每入口保存连贯代码上下文、调用、参数、条件、返回和 Capsule | EntryCodeContext、technical Flow/Capsule、coverage | 为 Activity 提供自包含代码材料 |
| 06 `flow-interpretation` | 组包并逐包生成、审阅局部 Activity | business materials、ReviewedActivity、coverage | 为全仓目录提供完整活动分母 |
| 07 `repository-knowledge` | 全仓业务发现、候选深入重建、归并和发布 | process catalog、coverage、`business-processes.md` | 直接供读者审阅，也供九章使用 |
| 08 `nine-section-document` | 根据已归并过程目录写一份固定九章概览 | report JSON、SourceRefs、`document.md` | 仓库级展示与公共 render |

01–05 不调用业务模型。Step03/04 的五图和严格 Proof 是增强能力，不是 JDT 完整源码进入 Step05/06 的门禁。Step06 已经完成的 Activity 不因 Step07 设计调整而重跑。

## 4. 三层语义模型

### 4.1 Activity：局部解释

`ReviewedActivity` 说明一个局部活动的目的、参与者、对象、输入、条件、步骤、代码定义结果、业务规则、公式、问题、限制和来源。它可以覆盖一个或多个入口，也可以被多个 Business Process 使用。

Activity 不负责回答整个销售或采购过程。当前 jshERP 实测已有 326 个完整已审 Activity，这是一份可复用的语义索引和详细材料，而不是最终过程目录。

### 4.2 Repository Business Catalog：全仓发现

程序把每个 Activity 的现有业务字段原文投影成 `ActivityIndexCard`，包含原businessRules，不由Java抽取行业标签或编写摘要。Luna/high 阅读全仓卡片，发现 Business Area、对象别名、候选过程和重叠成员关系。它只回答“哪些活动值得一起深入阅读”，不写最终步骤。

每个 Activity 必须有处置：进入一个或多个候选、独立活动或未分类。成员用途为 CORE、OPTIONAL、ROLLBACK、SUPPORT、QUERY 或 ANALYTICS。一个 Activity 可以同时属于销售履约和库存管理；查询和统计可支撑过程而不是伪装成时序阶段。

### 4.3 Business Process：详细重建

程序为每个 Candidate Process 取回完整 Activity，并提供稳定 Activity statement handle 与 SourceRef 目录。DRAFT 选择与候选有关的业务变体、阶段和规则，并请求少量需要核对的源码；程序从已保存 JDT/源码返回片段；完整 REVIEW 收窄或修正结果。

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
重要业务规则（activityUseIds限定适用用法）
选中的业务对象说明、字段/维度、对象关系、公式/指标和示例问题
结束结果
支撑活动与相关过程
待确认联系
来源引用
```

Certainty 只使用 `CONFIRMED`、`INFERRED`、`UNRESOLVED`，并附在具体阶段、规则或连接上，不用一个总分掩盖差异。

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

1. `FrozenAnalysisCorpus`：验证并查询已有Activity及M10中经Activity引用的SourceRef；本次不加JavaCodeIndex/MethodKey查询。
2. `RepositoryBusinessCataloger`：全仓卡片 DRAFT/REVIEW，得到业务目录和重叠候选。
3. `ProcessMaterialAssembler`：重开完整 Activity，解析 DRAFT 的源码核对请求。
4. `CandidateProcessReconstructor`：候选 DRAFT/REVIEW，形成详细过程。
5. `RepositoryProcessConsolidator`：一次仓库级 DRAFT/REVIEW，处理重复、父子、相关和替代过程。

`ProcessDiscoveryResult` 已经封闭携带归并目录、全部 Activity/Candidate 处置以及 `directActivityKnowledgeItems`。`BusinessProcessPublisher` 只校验引用与 coverage，并确定性发布结构化目录和 Markdown；它不调用模型，也不自行重新打开 Activity。完整子模块设计见[模块总览](modules/business-process-discovery/README.md)。

## 6. 模型执行顺序与复用

```text
已保存 326 ReviewedActivity
  → ActivityIndexCard 全仓目录 job（必要时分片 + 一次全局合并）
  → 候选 Process jobs 并行：每个 DRAFT → 按需源码 → REVIEW → 保存
  → 一次 Repository consolidation DRAFT → REVIEW
  → 程序发布 business-processes.md
  → 唯一九章 DRAFT → REVIEW → 确定性 render
```

沿用现有 YAML 的全局并发和每 Provider 并发。候选 job 的 DRAFT/REVIEW 保持同一绑定；不同候选并行。目录全局合并、仓库归并和九章是各自的屏障任务。

现有模型批次设计继续有效：材料属于 `sourceRunId`，新模型输出属于 `modelBatchId`；完整已审且 fingerprint 匹配的 job 才能显式复用。旧 DRAFT 或失败 REVIEW 不续接半轮。任何 Step07 模型失败都不得调用 Capture、JDT、图、Fact、Flow、Builder 或 ActivityExplainer。

PARTIAL 是闭合的语义处置，不是掩盖运行失败的状态。容量预检、`UNCLASSIFIED` 或 `INSUFFICIENT_MATERIAL` 可以在覆盖账完整时形成 PARTIAL catalog；Provider transport/runtime/schema、坏响应或 uncertain started request 属于 fatal，该批次不得发布正式 Step07 catalog，也不得继续 Step08。后续只能显式开启新模型批次，并复用仍然有效的材料或完整已审 job。

## 7. 来源和真实性

### 7.1 模型可见

- 精简 Activity 卡或候选所需完整 Activity；
- scope-local Activity/Process ID；
- Activity statement handle；
- allowlisted短SourceRef目录，含所属局部Activity和保存原文前8行预览；DRAFT请求后，REVIEW读取完整片段，不把预览当完整依据；
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

目标Step07 producer为v2；catalog/coverage/process Markdown升v2，SourceReference JSONL结构仍v1，sources Markdown新增v1。五项文件的producer、reader、registry和artifact查询必须同时贯通；[版本表](plans/business-process-discovery-and-reconstruction-change-design.md#82-输出和版本)为实施依据。阶段正文narrative、规则activityUseIds和coverage的Activity原name为新增字段。当前v1四文件产物保留，不静默转换。

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

过程发现质量先通过 `business-processes.md` 验收。正文以阶段narrative为主，结构字段可在明细折叠区无损保留。有直接源码引用时，用“查看依据”链接跳到过程来源索引，再按文件名/行范围打开sources.md片段，不铺满裸编号。来源链接允许留空，不强制说明或补齐、不增加取证/模型任务/专项验收，不阻塞主业务交付。九章不在本次修改范围，不能用格式正确掩盖生命周期缺失。

## 10. 实际材料与生命周期推演

[完整例子](examples/semantic-framework-walkthrough.md)用已保存的Activity和SourceReference，从全仓目录、候选用法、完整阅读到最终业务正文推演采购与销售。例子明确区分已核实代码和未来模型目标，不把人工演示冒充运行结果。

已核实材料可支持“申请关联采购订单、关联订单入库/出库后的数量进度更新”等受限路径。默认状态、修改/审核/反审核条件必须保留原适用范围；价格检查不能从某单据子类型泛化到所有订单。退货查询不能证明整个退货生成、欠款冲减链，岗位和强制审批顺序也不得补造。

不在Prompt或Java中预置这些业务名称。它们只作为真实样例验收：模型应当自己发现用法并解释联系，不是开发者给出正确候选答案。

## 11. 当前实现状态（2026-09-15）

已实现并保留：

- JDT/JavaParser 可选引擎和保存的 JDT 导航/源码材料；
- Spring 入口、Step05 EntryCodeContext、BusinessMaterialBuilder；
- ActivityExplainer、任意 N 入口覆盖、DRAFT+完整 REVIEW；
- Provider 两级并发、逐 job 保存、固定材料与独立模型批次；
- BusinessReportPublisher 和九章确定性渲染；
- 固定 jshERP 的 326 个已审 Activity。

当前实现分支已经完成并通过直接测试：

- FrozenAnalysisCorpus、Activity statement handle 和全仓 ActivityIndexCard；
- 分片目录 DRAFT/REVIEW、唯一目录合并及可重叠 Candidate Process；
- 完整 ActivityUse、详细 stage/rule、按需源码请求与 REVIEW；
- 仓库过程归并、三类分母 coverage、四项 canonical Step07 产物；
- 确定性 `business-processes.md`、独立来源文件和过程专用运行入口。

固定 326 Activity 的真实 Luna/high 运行已经完成：6 个目录分片及唯一合并形成 14 个候选，候选 DRAFT/REVIEW 和唯一仓库归并均已保存；正式复用批次发布 46 个过程，其中 21 个过程包含多个 Activity，全部 46 个过程包含多个阶段。326 个 Activity 的覆盖账为 CLOSED：102 个 PROCESS_MEMBER、157 个 SUPPORT_ONLY、17 个 STANDALONE、50 个 UNCLASSIFIED，因此 semantic delivery 如实标为 PARTIAL。Markdown 使用的 680 个短引用均能在独立来源文件中查询。

跨批次复用的 JSON Schema enum 现按 UTF-8 稳定排序；历史输入 checkpoint 通过输入策略 Store 重开，新 Step07 通过当前策略 Store 发布。最终正式批次复用 22/22 个完整已审 job，模型调用为 0，也没有运行 JDT、Builder 或 ActivityExplainer。Step08 只消费归并过程目录的接线仍不在本轮范围。

旧 `ProcessExplainer` 的 340 个单 Activity/单 Stage 结果只作为历史对照；新过程专用入口不会消费它，也不会启动 Step08。

### 本次复核：语义目标仍未通过

上述发布和覆盖指标属实，但当前14候选偏维护分类，库存阶段仍是“接收/校验/执行/返回”，所核查候选没有请求源码。46个过程并不证明已经识别所需订单生命周期。当前源码目录也未提供可辨识预览，正文来源不可直接点击。

本次设计修正不重建已完成模块；待实施项是业务用法、阶段正文、规则适用范围、来源预览与链接，以及直接读写v2接线。历史340结果仅为前身。结构CLOSED、语义PARTIAL和真实样例不达目标必须同时如实报告。

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
