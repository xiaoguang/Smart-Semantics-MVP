# 仓库业务过程发现与知识发布

> [总体设计](../DESIGN.md)；固定 key：`repository-knowledge`，目录：`steps/07-repository-knowledge/`。本步目标设计由 [Business Process Discovery 模块簇](../modules/business-process-discovery/README.md)拥有。当前代码仍是旧 `ProcessExplainer`，尚未实现本页的新接力。

## 1. 为什么存在

Step06 的 Activity 解释一个入口附近的局部业务行为。它不能独自回答仓库支持多少种业务、销售或采购经历哪些阶段。Step07 必须先获得全仓视野，再为每个候选重新打开完整材料，最终形成可直接阅读的业务过程目录。

当前实现把业务对象、术语、入口文本和同源码文件做精确相等连接，再将连通分量按容量切片。固定 jshERP 的实际结果是 326 个已审 Activity 被输出为 340 个 Process，全部只有一个 Activity 和一个 Stage；还有一个 Activity 没进入任何 Process，而 unmatched 仍为空。这证明现有分组和浅 Process Schema 不满足本步目的。

## 2. 上游真实交付

| 输入 | 状态 | 本步用途 |
| --- | --- | --- |
| ReviewedActivity checkpoint | 已实现；jshERP 已有 326 条 | 全仓业务索引和候选详细材料 |
| Activity coverage/unexplained entries | 已实现 | 过程覆盖的完整分母 |
| BusinessMaterial/Step05 context | 已实现 | 必要时查看 Activity 对应入口关系 |
| 保存的 JDT index、冻结源码与 SourceRef | 已实现 | 候选 REVIEW 按需核对具体实现，不重新扫描 |
| 模型 job 两级并发、保存和 batch 复用 | 已实现 | 并行执行候选，失败保留检查点 |

选择一个完整 Activity checkpoint 后，本步不得调用 ActivityExplainer。目录、候选和过程结果都必须指向同一冻结 corpus。

## 3. M1：FrozenAnalysisCorpus

程序首先验证 Activity、材料、JDT/source/ref 的来源一致，提供按 ActivityId、SourceRef 和 MethodKey 查询的只读 Interface。它为 Activity 数组字段分配稳定 statement handle，例如：

```text
activity:42fb…/conditions/0
activity:42fb…/businessRules/1
activity:42fb…/codeDefinedResults/0
```

这个 handle 只定位已审语句，不引入新的 Fact/Proof。读取失败明确终止，不调用 JDT、JavaParser、Builder 或上游分析补齐。

## 4. M2：全仓业务目录

程序把每个 Activity 确定性压缩为 ActivityIndexCard，保留：名称、目的、对象、动作、状态观察、标识、结果摘要和限制。目录模型读取**全部卡片**，发现：

- Business Area 和业务对象别名；
- 可重叠 Candidate Process；
- Activity 在候选中的 CORE、OPTIONAL、ROLLBACK、SUPPORT、QUERY、ANALYTICS 用途；
- STANDALONE 和 UNCLASSIFIED Activity。

Prompt 不给销售、采购等期望词。目录只是高召回导航，不宣布活动顺序或对象一定相同。

若全部卡片超过一次模型输入容量，程序按稳定顺序分片，每个 Activity 恰进入一个目录分片；分片 DRAFT/REVIEW 后再做一次全局目录合并。所有 Activity 必须在合并结果中有处置，不能静默遗漏。

目标卡片示意：

```json
{
  "activityId": "activity:42fb…",
  "name": "批量审核或反审核库存单据",
  "purpose": "检查并提交一组单据的审核状态变化",
  "businessObjects": ["库存单据", "单据状态", "库存"],
  "actions": ["审核", "反审核", "更新状态"],
  "stateObservations": ["0→1", "1→0"],
  "identifiers": ["单据ID集合"],
  "resultSummaries": ["提交批量状态更新"],
  "limitations": ["实际事务提交结果未知"]
}
```

## 5. M3：候选完整材料

对每个候选，`ProcessMaterialAssembler` 按 activityId 重新打开完整 ReviewedActivity，而不是继续使用卡片。输入保留 purpose、participants、objects、inputs、conditions、steps、results、rules、formulas、questions、limitations、statement handles 和 refs。

候选 DRAFT 还收到短 SourceRef 目录，但不先发送全部源码。它可以返回 `requestedSourceRefs`；程序只从已保存 JDT/source corpus 取回这些真实片段，作为 REVIEW 材料。未知 ref 或来源漂移失败，不重新导航。

一个候选过大时应由目录模型拆成有业务含义的子候选，或明确 `NOT_PROCESSED_CAPACITY`。不能机械截断数组中间内容后称为完整过程。

## 6. M4：详细过程 DRAFT 与 REVIEW

每个候选是一个可并行 job：

```text
完整 Activity 材料
→ DRAFT：选择 ActivityUse、阶段、分支、规则、结果和待核对源码
→ 程序加载请求的保存源码
→ REVIEW：完整材料 + 实际 DRAFT + 源码片段
→ 已审过程、拆分结果、支撑集合或材料不足处置
```

同一 Activity 可以有多个 ActivityUse。例如通用“新增库存单据”在销售订单、销售出库、销售退货过程中的 variant 和选用规则不同。过程阶段不再只保存 `order/activityId/description`，而要保存：

```text
stageId / name / activityUseIds
entryConditions / actions / stateChanges
rejectionConditions / outcomes / transitions
certainty
activityStatementRefs / sourceRefs
```

重要 BusinessRule 保存 `subject + when + action/decision + otherwise + result + certainty + refs`。已知条件不得缩水：

- 正确：当前单据状态为 `0` 时允许编辑；不是 `0` 时拒绝。
- 错误：状态允许时可以修改。

每个已审过程还要携带被选择的 `CatalogKnowledgeItem`：业务对象说明、字段/维度定义、对象关系、公式/指标定义和示例问题正文，以及各自的 owner、certainty、statement refs 和 source refs。只保存指针不能满足 Step08，因为九章不再读取 raw Activity；这些正文必须由候选 REVIEW 从完整 Activity/源码中选择并原样保留。直接处置为 SUPPORT/STANDALONE/UNCLASSIFIED 的 Activity 由 `BusinessProcessDiscovery` 从同一 FrozenAnalysisCorpus 的 ReviewedActivity 确定性投影知识项，写入 `ProcessDiscoveryResult.directActivityKnowledgeItems` 并标明 disposition；不能因没有过程而丢内容，也不能被包装成假过程。Publisher 不回读原 Activity。

REVIEW 可保留、收窄、拆分、转为可选/回退、降级 certainty 或删除关系；不能创建未知 Activity/ref，也不能只返回 patch。

## 7. M5：仓库过程归并

全部候选完成后，模型只读取已审过程摘要、ActivityUse 和开始/结束状态，做一次仓库级 DRAFT/REVIEW：

- KEEP_DISTINCT；
- MERGE_DUPLICATES；
- PARENT_CHILD；
- RELATED；
- ALTERNATIVE；
- REJECT_DUPLICATE_OR_UNSUPPORTED。

它只能决定过程之间的关系和选择哪些已审内容组合，不能重新写或压缩具体条件、规则、知识项和来源。Java 校验决定后确定性形成唯一 `RepositoryBusinessProcessCatalog`，并按类型聚合过程级 knowledge items 供九章使用。

每个 Activity 最终必须至少有一个过程 membership，或明确成为 SUPPORT/STANDALONE/UNCLASSIFIED。一个 Activity 可属于多个过程。相同名字不自动合并，不同名字不妨碍有证据的关联。

## 8. M6：确定性发布

正式业务输出：

| 文件 | 作用 |
| --- | --- |
| `repository-business-process-catalog.json` | 唯一仓库业务领域、过程、关系、ActivityUse 和待确认项 |
| `process-coverage.json` | 全 Activity、候选、过程和未处理范围的处置 |
| `business-processes.md` | 按过程展示目的、步骤、规则、结果、待确认和短来源引用 |
| `source-refs.jsonl` | 复用的短 ref 到冻结文件、行号和片段映射 |

可复用但不作为读者主交付的检查点：`activity-index-cards.jsonl`、`business-process-candidates.jsonl`、`reviewed-business-processes.jsonl`。

`business-processes.md` 由 Java 从 catalog 确定性渲染，零 Provider。它不是固定九章，而是每个过程使用相同的读者结构。Step08 只能消费发布后的 catalog，不能再发现或改变过程。

## 9. 失败、覆盖和复用

- 目录遗漏 Activity、候选遗漏成员处置、未知 statement/ref、REVIEW 引入新成员、归并丢规则或父子关系循环均失败。
- LLM 任务 started 后失败，停止派发新 job；已开始且合法的 job 完成唯一 REVIEW并保存。不自动重试或换 Provider。
- 候选无法判断可以返回 INSUFFICIENT_MATERIAL；coverage 记录缺什么，不能输出一个空洞单阶段过程冒充成功。
- `CONFIRMED` 只用于 Activity/source 直接支持的局部 claim；跨入口合理联系用 `INFERRED`；缺失、冲突和外部效果用 `UNRESOLVED`。
- 新 model batch 复用同一 Activity/JDT corpus。完整已审且 fingerprint 相同的目录/候选/归并 job 才可复用；任何模型失败都不重跑 JDT 或 326 个 Activity。
- Coverage complete 只表示所有输入有去向；业务质量还要求过程具体、连贯且不丢条件。

## 10. 销售闭环验收

使用现有真实 Activity 和保存源码，目标至少重建：销售订单管理与履约、销售退货、客户收款与欠款、销售分析与对账。核心过程必须能说明：

1. 创建订单时的单号、明细和关联号约束。
2. 数据库当前状态为 `0` 才允许修改；否则拒绝。
3. 保存时可携带状态 `1`，因此批量审核不是唯一审核入口。
4. 独立审核 `0→1`；反审核要求当前 `1` 且采购状态 `0`，采购状态 `2/3` 时拒绝。
5. 销售出库可关联订单或独立创建；有关联时数量驱动订单保持已审核、部分完成 `3` 或完成 `2`。
6. 销售退货可关联出库或独立创建；材料不足以证明一定回写订单状态。
7. 收款/欠款是相关过程；单据号关联的具体业务身份若不唯一则保留待确认。
8. 查询、统计和对账是支撑/分析过程，不作为主履约时序。

这一验收使用业务词作为 fixture 数据，不允许 Java 或通用 Prompt 写死销售答案。再用不同领域 fixture 验证相同 Interface。

## 11. 当前实现状态

已实现：完整 Activity 输入、旧过程组并行 DRAFT/REVIEW、逐 job 保存、model batch 复用、仓库短摘要和九章下游。

未实现：M1 统一 corpus、M2 全仓目录、M3 完整材料/源码按需核对、M4 ActivityUse 与详细过程、M5 真正归并、M6 新 catalog/Markdown。

现有 `ProcessExplainer`、`BusinessProcess`、`BusinessProcessStage` 和三个旧 Step07 输出是迁移输入，不是目标验收。实现计划必须先以 326 Activity/340 singleton 基线写 RED，再替换此路径；不能在旧精确 token 分组上继续补业务特例。
