# 局部活动解释

> [总体设计](../DESIGN.md)；固定 key：`flow-interpretation`，目录：`steps/06-flow-interpretation/`。两个既有 Module：`BusinessMaterialBuilder`、`ActivityExplainer`。本步已经实现，是新 Step07 的可复用上游，不因过程设计调整而重跑。

## 1. 为什么存在

Step05 已按入口保存 Controller、Service、Mapper/外部边界、参数、条件、返回和完整源码。Step06 将相关入口上下文组为模型可读材料，并由 Luna/high 解释局部 Activity。

Activity 回答“这个入口附近的业务行为是什么”，不回答“整个系统有哪些业务、销售或采购经历哪些阶段”。材料分包只为连贯阅读和容量控制，也不是业务过程分组。

## 2. BusinessMaterialBuilder

### 输入

- 同源 Step05 EntryCodeContext/Capsule；
- 全入口 coverage；
- BusinessMaterialProfile；
- 已保存的源码和来源映射。

### 程序工作

- 选择完整入口上下文，不重新找 Service 或推导调用边。
- 在入口边界组包；一包可含任意正数 `N` 个入口，局部键为 E1…EN。
- 同一 BusinessMaterialSet 内为源码片段分配唯一 SourceRef。
- 将调用、实参/形参、条件、返回、异常和限制放入干净模型包。
- 保存 `business-materials.jsonl` 后才允许模型调用。

JDT/JavaParser、hash、路径、图和 Proof identity 不进入模型包。没有 strict Flow 但有安全代码上下文的入口仍可形成材料。Builder 不包含销售、采购等业务分类器。

### 失败和 coverage

错误来源、断引用或身份漂移为 fatal。无安全上下文、单入口超出可读容量或明确未选择时保存具体 NOT_ANALYZED 原因，Provider 调用为零。不同包中的 E1 没有任何关系，必须经 materialId 映射回 global entryId。

## 3. ActivityExplainer

一个材料是一个 job：

```text
完整 BusinessMaterial
→ Activity DRAFT
→ 完整实际 DRAFT + missingEntryKeys
→ Activity REVIEW
→ 完整 ReviewedActivity + coverage
```

DRAFT/REVIEW 固定同一 Provider/model/effort。不同材料可以通过已实现的两级线程池并行；一个 job 内两轮顺序执行。每个完成 REVIEW 的 job 立即私有保存，全部结束后按材料顺序稳定聚合。

### 模型输出

每个 ReviewedActivity 保留：

- name、businessPurpose；
- participants、businessObjects；
- triggerOrInput、conditions、activitySteps；
- codeDefinedResults、businessRules、formulasOrMetrics；
- terms、questions、scopeLimitations；
- entryIds、sourceRefs、certainty。

模型可以把清楚的构造+保存解释为“系统生成并保存对象”，但不能声称某次运行成功、外部系统已完成、岗位制度已确认或不存在的唯一性。

### 任意 N 入口闭合

DRAFT 允许暂时遗漏入口。程序计算 `missingEntryKeys`，REVIEW 必须返回完整 activities 和 required `unexplainedEntries`。最终：

```text
reviewed activity 覆盖的 keys
∪ unexplainedEntries
= material 全部 E1…EN
```

两集合不重叠。仍遗漏、未知 key/ref、坏 JSON 或 started 请求失败均 fatal，没有第三轮。`MODEL_NOT_EXPLAINED` 是本次模型未解释，不是源码缺失或技术 Gap。

## 4. 实际输出示例

下列字段取自当前已保存的“批量强制结单” ReviewedActivity，省略来源列表：

```json
{
  "name": "批量强制结单",
  "conditions": [
    "逐一读取的单据状态是否等于‘3’。",
    "待处理标识列表是否非空。",
    "批量更新返回数量是否大于0。"
  ],
  "activitySteps": [
    "解析逗号分隔的单据标识。",
    "任一单据状态不等于3时抛出强制结单失败异常。",
    "通过检查的单据状态被设置为2并提交批量更新。"
  ],
  "businessRules": [
    "每个待处理单据状态必须严格等于3，否则终止处理。",
    "只有批量更新返回数量大于0时接口使用成功信息。"
  ],
  "scopeLimitations": [
    "状态3和2的正式业务含义仍需确认。"
  ]
}
```

这已经比方法名翻译丰富，但它仍只是局部 Activity。Step07 必须把它与创建、修改、审核、履约等其他 Activity 一起发现和重建，不能把它直接包成一个单阶段端到端过程。

## 5. 文件和下游合同

| 文件 | 内容 | 下一消费者 |
| --- | --- | --- |
| `business-materials.jsonl` | 自包含入口代码材料、短 ref 和入口映射 | ActivityExplainer；Step07 必要回查 |
| `activity-explanations.jsonl` | 完整已审 Activity | Step07 ActivityIndexCard 与完整候选材料 |
| `activity-coverage.json` | 全入口处置和具体未解释记录 | Step07/08 coverage |

Step07 选择一个完整、已验证的 Activity checkpoint 后，直接读取这些文件。它不得调用本步再次生成 326 个 Activity。目录阶段只投影卡片；候选深入阶段按 ID 重新打开本文件中的完整 Activity。

## 6. 保存、批次和失败复用

材料 checkpoint 与模型 batch 已分开：材料保留 sourceRunId；Activity 输出属于 modelBatchId。新 batch 可以显式复用完整、输入和模型绑定都匹配的已审 job。孤立 DRAFT 或失败 REVIEW 不续接，必要时在新 batch 重做完整 pair。

无论 Activity job 是否失败，都不能以此为由重新运行 Capture、JDT、图、Fact、Flow 或 Builder。损坏 checkpoint 明确失败，不静默重扫。

## 7. 当前实现状态（2026-09-14）

BusinessMaterialBuilder、ActivityExplainer、v2 Prompt/coverage、任意 N 闭合、两级并发、逐 job 保存和 batch 复用均已实现。固定 jshERP 选择的完整 checkpoint 已有 **326 个 ReviewedActivity、326 个入口处置、零 unexplained entries**；这些记录均诚实包含技术限制。

本步不需要为新过程设计修改业务内容或全量重跑。未来只可能在 Step07 源码核对发现某 Activity 与冻结代码实质矛盾时，对该具名 Activity 做定向 delta review。

## 8. 直接验收

- 一个材料的 Controller/Service 实现、条件、返回和来源实际进入 DRAFT。
- REVIEW 看到完整真实 DRAFT，不丢长规则或公式。
- N=1、4、12+、跨包重复 E1、多 Activity/入口多对多、零入口均闭合。
- 改变 job 完成顺序不改变聚合业务内容。
- 保存后重开不调用代码引擎或模型。
- 换一个业务领域无需修改 Java 词表。
- 新 Step07 能从已保存 326 Activity 开始，ActivityExplainer 调用数为零。
