# 业务过程发现与重建设计变更清单（APPROVED TARGET DESIGN）

> 本文记录 2026-09-14 已批准、尚待实现的语义层修改。它是后续实施计划的差异输入，不是已经完成的代码声明。总体目标见 [总体设计](../DESIGN.md)，术语见 [CONTEXT](../../CONTEXT.md)。

## 1. 为什么需要这次修改

现有技术取材和局部解释已经取得大量有效结果，但整仓过程输出没有回答“系统支持哪些业务，每种业务经历哪些阶段”。实际保存结果显示：

- 固定 jshERP 材料已经完成 **326/326** 个 Activity 的 DRAFT 与 REVIEW；当前每个 Activity 对应一个入口。
- 当前 `ProcessExplainer` 先用业务对象、术语、入口文本、Flow/Proof 引用和同源码文件的**精确相等**建立邻接，再按容量切分。
- 当前过程 Schema 的阶段只有 `order + activityId + description`。
- 当前保存了 **340** 个 Process；**340/340 都只有一个 Activity 和一个 Stage**。
- 340 个 Process 实际只引用 **325** 个不同 Activity；`activity:f557933b36ff5eb47a60b6c300ceb86e99d271431bc499dfd636c879ab05207c`（“租户限制下新增用户及其角色、部门关系”）既未进入 Process，也未进入 unmatched 列表。
- **275/326** 个已审 Activity 的 `formulasOrMetrics` 非空。若 Step08 改为只读过程目录，却只保存过程名称和引用，这些已经取得的公式/指标正文会被无声丢失。

因此，当前结果证明了局部活动解释和模型调用链能运行，但没有完成跨 Activity 的业务发现与串联。继续修改九章措辞、增加证据校验或扩大同一机械分组，不会解决这个问题。

## 2. 保留、替换和不重做

### 2.1 原样复用

- 冻结源码、719 文件清单、JDT 导航索引、方法正文、调用与 SourceRef。
- 326 个完整已审 Activity 及其目的、对象、输入、条件、步骤、结果、规则、公式、问题和限制。
- 现有 Provider seam、YAML 两级并发、DRAFT→完整 REVIEW job、逐 job 保存、模型批次和 checkpoint 复用。
- Canonical JSON、运行归属、覆盖记录和确定性 Markdown 渲染能力。
- JavaParser 作为可选代码引擎；它不需要追平 JDT。

### 2.2 替换

- 用“全仓业务目录发现 → 候选过程材料组装 → 详细过程重建 → 仓库过程归并”替换当前精确 token/file 连通分量分组。
- 用能表达条件、拒绝、状态变化、结果和多业务变体的过程模型替换浅的 `BusinessProcessStage(order, activityId, description)`。
- 用 `ActivityUse` 表示同一通用 Activity 在不同过程、不同业务变体中的用法。
- 用 `business-processes.md` 作为过程发现的首要可读产物；固定九章继续存在，但只消费已归并过程目录，不承担第二次过程发现。

### 2.3 不重做

- 不重新运行 JDT，也不重新扫描冻结仓库。
- 不重跑全部 326 个 Activity 的 DRAFT/REVIEW。
- 不重建五张图、Fact/Proof 或 Step05 上下文。
- 不在 Java 中加入“销售、采购、库存、财务”等业务词典。
- 不增加数值置信分、知识图数据库、向量数据库、消息队列或新恢复系统。

只有当候选过程深入核对时发现某个 Activity 与已保存源码存在实质矛盾，才允许为该 Activity 创建具名、定向的 delta review；这不是全量重跑入口。

## 3. 新的完整接力

```text
已保存 JDT/冻结源码
        │
        ├──────────────┐
        │              │按需取回真实片段
326 个 ReviewedActivity│
        ↓              │
ActivityIndexCard 全仓目录
        ↓
LLM 发现 Business Area 与重叠 Candidate Process
        ↓
程序按 activityId 取回完整 Activity，并提供 SourceRef 目录
        ↓
LLM DRAFT 选择过程相关分支并请求需要核对的源码
        ↓
程序从已保存 JDT/源码解析请求，不重新导航
        ↓
LLM REVIEW 形成详细阶段、分支、规则、结果和未知项
        ↓
LLM 对跨候选重复、父子和相关关系做一次仓库归并
        ↓
程序确定性发布仓库过程目录、coverage 与 business-processes.md
        ↓
现有九章模块只基于已归并目录写一份下游报告
```

目录阶段只看精简卡，避免一次发送 326 份完整源码；详细重建阶段必须重新打开完整 Activity，不能只靠卡片摘要。关键规则由保存的 JDT/源码片段核对，既不重新扫描，也不相信目录摘要能替代原文。

## 4. 新增和调整的模块

外部只保留两个深 Module Interface：

```java
ProcessDiscoveryResult BusinessProcessDiscovery.discover(ProcessDiscoveryRequest request);
BusinessProcessPublication BusinessProcessPublisher.publish(ProcessDiscoveryResult result);
```

第一项隐藏目录、组装、候选重建和归并；第二项只做验证、覆盖与确定性发布。它们都是 `RepositoryAnalysisAgent` 内部模块，不新增公开 Agent Interface。

内部职责及详细设计：

| 内部模块 | 只负责什么 | 详细设计 |
| --- | --- | --- |
| FrozenAnalysisCorpus | 读取并验证现有 Activity、JDT、源码和 SourceRef；提供按 ID 查询；确定性投影未进候选 Activity 的已有知识项 | [设计](../modules/business-process-discovery/frozen-analysis-corpus.md) |
| RepositoryBusinessCataloger | 从全仓精简卡发现领域、候选过程和重叠成员关系 | [设计](../modules/business-process-discovery/repository-business-cataloger.md) |
| ProcessMaterialAssembler | 取回候选所需完整 Activity、语句 handle 和可按需查询的源码目录 | [设计](../modules/business-process-discovery/process-material-assembler.md) |
| CandidateProcessReconstructor | DRAFT+REVIEW 重建详细阶段、分支、规则和结果 | [设计](../modules/business-process-discovery/candidate-process-reconstructor.md) |
| RepositoryProcessConsolidator | 归并重复候选，保留父子、相关、替代和未决关系 | [设计](../modules/business-process-discovery/repository-process-consolidator.md) |
| BusinessProcessPublisher | 校验分母和引用，发布 JSON、coverage 与主业务 Markdown | [设计](../modules/business-process-discovery/business-process-publisher.md) |

## 5. 数据合同修改

### 5.1 精简索引卡

`ActivityIndexCard` 是现有 ReviewedActivity 的确定性投影，至少包含：

```json
{
  "activityId": "activity:…",
  "name": "批量审核或反审核库存单据",
  "purpose": "检查并提交一组单据的审核状态变化",
  "businessObjects": ["库存单据", "单据状态", "库存"],
  "actions": ["审核", "反审核", "更新状态"],
  "stateObservations": ["0→1", "1→0"],
  "identifiers": ["单据ID集合"],
  "resultSummaries": ["提交批量状态更新"],
  "limitations": ["外部事务提交结果未知"]
}
```

程序不能添加卡片中不存在的领域标签；模型从全部卡片中发现业务领域和同义词。

### 5.2 活动语句引用

完整 Activity 的可引用内容由程序赋予稳定 handle，例如：

```text
activity:42fb…/businessRules/0
activity:42fb…/conditions/1
activity:42fb…/codeDefinedResults/0
```

它让过程模型明确指出自己使用了哪条已审条件或规则，不为这些内容再造一套 Proof。

### 5.3 ActivityUse

```json
{
  "activityUseId": "use:sales-order-create",
  "activityId": "activity:c7f1…",
  "variant": "销售订单",
  "role": "CORE",
  "statementRefs": ["activity:c7f1…/businessRules/2"],
  "sourceRefs": ["S…"]
}
```

同一个“新增库存单据及明细” Activity 可以分别产生销售订单、销售出库、销售退货等 ActivityUse；不能把整项 Activity 的所有分支同时写入每个过程阶段。

### 5.4 详细过程

每个过程必须能直接支持下列读者问题：过程名称、目的和适用范围、步骤及分支、重要业务规则、结束结果、待确认联系和来源引用。核心结构为：

```text
BusinessProcess
  name / purpose / scope
  participants / businessObjects
  activityUses[]
  stages[]
    entryConditions[]
    actions[]
    stateChanges[]
    rejectionConditions[]
    outcomes[]
    transitions[]
    certainty
    activityStatementRefs[] / sourceRefs[]
  branches[] / businessRules[] / endResults[]
  knowledgeItems[]
    kind: OBJECT | FIELD_OR_DIMENSION | OBJECT_RELATION | FORMULA_OR_METRIC | QUESTION
    text / owner / certainty / activityStatementRefs[] / sourceRefs[]
  supportActivityUses[] / pendingConnections[]
```

每条重要规则使用 `subject + when + action/decision + otherwise + result + certainty + refs`。禁止输出“状态允许时可修改”“满足条件后审核”之类丢掉已知谓词的句子。

`knowledgeItems` 保存九章第 3、5、6、7、8 章所需的已审正文。只保存 statement/source ref 不够，因为 Step08 不再读取 raw Activity；候选 REVIEW 必须选择并携带实际对象说明、字段定义、关系、公式/指标或问题文本，仓库归并只能去重和归属，不能重新总结。被目录直接处置为 SUPPORT/STANDALONE/UNCLASSIFIED 的 Activity 不经过候选 REVIEW；`BusinessProcessDiscovery` 必须在同一 corpus 内从原 ReviewedActivity 确定性投影其知识项，写入 `ProcessDiscoveryResult.directActivityKnowledgeItems`，owner 明确为该 Activity 及其 grouping disposition。Publisher 只校验和发布这个封闭结果，不能另行回读 Activity、丢内容或创造假过程。没有公式时保持空集合，后续明确“未识别到可定义指标”。

## 6. 模型与程序的固定职责

### 程序固化

- 打开已保存 Activity/JDT/source/ref；生成卡片和语句 handle。
- 校验 ID、引用、分母、结构、Provider 输出范围和保存身份。
- 按模型返回的成员 ID 取回完整 Activity 和源码片段。
- 调度并行候选 job，稳定聚合，确定性渲染 JSON/Markdown。
- 对每个 Activity 和 Candidate 保存最终处置。

### Prompt + LLM 完成

- 从全仓卡片发现业务领域、同义词和候选过程。
- 判断 Activity 在过程中的 CORE、OPTIONAL、ROLLBACK、SUPPORT、QUERY 或 ANALYTICS 用途。
- 选择通用 Activity 中与当前过程相关的分支。
- 推断阶段、先后、并行、替代和回退，并将非直接关系标为 `INFERRED` 或 `UNRESOLVED`。
- 用业务语言写出精确条件、动作、状态变化和结果。
- 归并重复候选，识别父子、相关和替代过程。

PARTIAL 只表示合法且分母闭合的内容处置，例如容量预检后的明确未处理项、`UNCLASSIFIED` 或 `INSUFFICIENT_MATERIAL`。Provider transport/runtime/schema、坏响应或 uncertain started request 仍是 fatal：当前批次不发布正式过程目录，也不继续九章；后续新批次可以复用未受影响的完整已审结果，但不续接半轮。

模型不能创建 Activity、SourceRef、源码位置或确认现实中某次运行成功；Java 不能判断自由中文的业务同义词或硬编码领域顺序。

## 7. 保存与输出

有意义的可复用检查点：

- `activity-index-cards.jsonl`
- `business-process-candidates.jsonl`
- `reviewed-business-processes.jsonl`

正式业务交付：

- `repository-business-process-catalog.json`
- `process-coverage.json`
- `business-processes.md`
- 复用现有 `source-refs.jsonl`

检查点是为了失败后复用，不是新的证据层。`business-processes.md` 是判断过程发现是否有用的首要读物。现有 `business-processes.jsonl`、`repository-business-knowledge.json` 和九章文件在新实现完成前仍是历史结果，不可重写或冒充新合同输出。

## 8. 以销售业务闭环推演

以下推演使用当前 326 Activity 中已经存在的真实字段与已保存源码事实；它是新算法的目标演示，不是假称新模块已经运行。

1. 目录模型从卡片中发现“新增库存单据”“更新已有单据”“批量审核/反审核”“查询关联退货”“销售统计”“新增收款单”等活动可能围绕销售订单、销售出库、销售退货和客户收款形成重叠候选。
2. 程序按 activityId 取回完整 Activity。它看到通用新增活动同时含“销售订单/销售/销售退货”分支，因此为不同候选建立不同 ActivityUse，而不是复制一个模糊阶段。
3. 过程 DRAFT 选择并保留具体规则：原单状态必须为 `0` 才允许编辑；审核是 `0→1`；反审核要求当前 `1` 且采购状态 `0`，采购状态 `2/3` 时拒绝；销售出库可以关联销售订单，也可以独立创建。
4. DRAFT 请求核对关联数量和状态写回的 SourceRef。程序从已保存 JDT/源码返回片段；REVIEW 据此写明关联出库后订单可能保持已审核、进入部分完成或完成，而不是写“满足条件后更新状态”。
5. REVIEW 将查询详情和销售统计放入支撑/分析活动，不伪装成履约阶段；将销售退货和客户收款保留为相关过程。未被源码证明的“审核是出库的强制后端前置条件”“收款一定关联销售订单而非出库单”进入待确认。
6. 仓库归并得到至少“销售订单管理与履约”“销售退货处理”“客户收款与欠款”“销售分析与对账”等过程及关系。程序检查所有相关 Activity 已进入过程、支撑、独立或未分类处置。
7. `business-processes.md` 展示每个过程的完整阶段、规则和来源；九章模块以后只将这些已归并结论编排成仓库级概览，不再自行猜销售流程。

该推演可以走通，因为目录拥有全仓视野，详细重建重新打开完整 Activity，关键规则还能回到已保存 JDT/源码。仍无法从静态材料确定的外部审批、岗位制度、运行时配置和真实交易结果会以 `UNRESOLVED` 保存，不会被覆盖账隐藏。

## 9. 后续实施必须修改的代码面

本表是下一份实施计划的代码差异清单。它不授权本轮修改 Java，也不要求按文件机械拆任务；若实现中发现名字已移动，以同一职责的真实 owner 为准。

| 当前代码面 | 保留什么 | 必须修改或替换什么 |
| --- | --- | --- |
| `analysis.interpretation.activity` | `ReviewedActivity`、完整 DRAFT/REVIEW、326 条 checkpoint、coverage | 不全量重跑；只增加 corpus 可查询投影/statement handle 所需的只读适配 |
| `analysis.knowledge.ProcessExplainer` | Provider seam、两级并发、逐 job 保存、失败屏障的通用执行方式 | 移除精确 token/file 连通分量和容量切片作为过程发现入口；由 `BusinessProcessDiscovery` 取代 |
| `BusinessProcess` / `BusinessProcessStage` | 可复用的业务名称、目的、来源概念 | 升级为 ActivityUse、详细 stage、rule、transition、certainty、CatalogKnowledgeItem 和 candidate disposition；不能保留只含 `order/activityId/description` 的目标 wire |
| `ProcessKnowledgeCheckpointPublisher/Reader` | 原子保存、modelBatch/sourceRun 归属和重开校验 | 改为卡片、候选、逐候选 REVIEW、consolidated catalog 与 coverage 的新版本；旧输出不双读、不覆盖 |
| `process-group-*.txt`、`repository-summary-*.txt` | 无业务词硬编码、DRAFT/REVIEW 基本纪律 | 由目录、候选过程和仓库归并的中文 Prompt/Schema 取代；旧 Prompt 只作历史测试输入 |
| `BusinessAnalysisWorkflow` / `PersistedBusinessRunExecutor` | Activity checkpoint 复用、线程池、批次归属、失败保留 | Step06 后调用新的两个深 Interface；过程失败不得回退旧 `ProcessExplainer` 或触发 JDT/Activity |
| `BusinessReportPublisher` 与报告 Prompt | 单份九章 DRAFT/REVIEW、SourceRef、确定性 Markdown | 输入改为 consolidated catalog/coverage；删除 raw Activity 重新发现、重排过程的能力 |
| `BusinessCheckpointArtifactReader` / `BusinessOutputArtifactKey` / CLI artifact 查询 | 闭集查询、大小限制、无 Path | 登记新过程 catalog、coverage、`business-processes.md`；公共 `RepositoryAnalysisAgent` 方法不变 |
| 现有 Step07/08 测试 | Provider 身份、并发上限、失败保存、九章结构等仍有效断言 | 用 326/340 singleton 建 RED；新增跨分片语义候选、ActivityUse、精确谓词、归并、零遗漏和 report-only-catalog 验收；删除只保护旧精确分组的测试 |

明确不修改：JDT/Core/JavaParser 导航算法、Step01–05 wire、Activity Prompt v2、公共 Agent 方法、HTTP、第二存储系统和客户源码。若新过程实现为了方便要求其中任一项变化，必须先证明业务结果确实依赖它，不能顺手扩大范围。

## 10. 文档与未来实施范围

本轮设计刷新必须同步：README、`AGENTS.md`、总体设计、Step05–08、Java 引擎下游说明、模型任务执行、中文 Prompt、两个 walkthrough、来源/发布附录。Step01–04 只补充它们在新接力中的稳定职责，不改其算法。

下列历史/已完成计划保持历史原文，并在导航中明确不再是新语义实现依据：旧清理计划、JDT 可行性/接入计划、并发计划、旧整仓运行计划和十批计划。后续只基于本文和更新后的目标设计编写一份新的实施计划。

## 11. 实施验收闭环

- 同一程序处理完全不同业务领域，不修改 Java 业务词典。
- 全仓卡片全部进入候选成员、支撑、独立或未分类处置；候选可重叠。
- 至少一个真实候选形成多 Activity、多 Stage 过程；不能再以 340 个单阶段过程作为成功。
- 销售示例保留上述具体状态、允许/拒绝和关联数量规则；不得退化为空话。
- 同一通用 Activity 可在不同过程以不同 ActivityUse 出现。
- 关键规则能通过 Activity statement handle 和 SourceRef 回到已保存内容；不重新运行 JDT。
- 目录摘要不能替代详细 Activity；九章不能重新发现或改写业务过程。
- 未分类、冲突、外部效果和不确定顺序完整进入 coverage 与待确认项。
- 过程 JSON 已表达的条件、分支、规则和结果在 `business-processes.md` 中不得丢失。
