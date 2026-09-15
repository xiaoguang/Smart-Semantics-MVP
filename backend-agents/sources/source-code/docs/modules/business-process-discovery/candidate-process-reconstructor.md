# CandidateProcessReconstructor

## 为什么存在

Candidate Process 只说明“这些活动可能相关”。本模块让模型从完整材料中回答：该过程为什么存在、怎样开始、经历哪些阶段和分支、什么条件允许或拒绝动作、最终得到什么，以及哪些连接仍需确认。

## 一个 job

每个候选最多一个 DRAFT 和一个完整 REVIEW：

```text
完整 ProcessMaterial
  → DRAFT：选择 ActivityUse、阶段、规则、分支；列出 requestedSourceRefs
  → 程序解析已保存源码
  → REVIEW：实际 DRAFT + 完整 Activity + 所选源码片段
  → ReviewedCandidateDisposition
```

不同候选可以复用现有线程池并行。同一候选两轮固定同一 Provider/model/effort；失败不自动重试或换服务。

## DRAFT 必须产生什么

模型可返回一个详细过程、将候选拆为多个过程、降为支撑集合或判定当前材料不足。过程至少包含：

- name、purpose、scope、participants、businessObjects；
- ActivityUse，含 activityId、variant、role、statementRefs 和 sourceRefs；
- stages，含进入条件、动作、状态变化、拒绝条件、结果、转移和 certainty；
- branches、businessRules、endResults；
- knowledgeItems，含 OBJECT、FIELD_OR_DIMENSION、OBJECT_RELATION、FORMULA_OR_METRIC、QUESTION 的实际正文、certainty 和 refs；
- supportActivityUses、pendingConnections；
- requestedSourceRefs。

同一 Activity 在不同变体中使用时必须创建不同 ActivityUse。ActivityUse 是过程内引用，不复制或篡改原 Activity。

`knowledgeItems` 从本候选完整 Activity 或已解析源码中选择，服务后续九章第 3、5、6、7、8 章。它必须保存实际正文；statement/source ref 只证明出处，不能代替内容。没有公式或指标时保持空数组，不根据字段名发明口径。

## 精确语言规则

每个重要规则必须包含对象、具体 `when`、动作/决定、`otherwise`（适用时）和结果。例如：

> 当前单据状态为 0 时允许编辑；不是 0 时拒绝编辑。

不能写：

> 状态允许时可以修改。

已知 `status=0→1`、`status=1 且 purchaseStatus=0 才能反审核`、`purchaseStatus=2/3 拒绝` 时必须保留这些值。源码中的值没有正式业务名称时，可以同时写代码值和“正式含义待确认”。

## REVIEW

REVIEW 核对成员、variant、顺序、条件、状态变化、规则和来源。它可以保留、收窄、拆分、转为可选/回退、降为 INFERRED/UNRESOLVED 或删除；不能新增未在候选/源码请求中的 Activity 和 ref。输出是完整替代记录，不是 patch。

Certainty 只有：CONFIRMED、INFERRED、UNRESOLVED。应按 stage/rule/connection 分配，不能用一个过程级分数掩盖不同结论。

## 失败与处置

非法 Activity/ref/statement handle、遗漏候选成员处置、空洞规则丢失已知谓词、REVIEW 引入新成员或 started 请求失败均为 fatal job 结果。材料不足可以合法返回 `INSUFFICIENT_MATERIAL`，并列出缺少什么；不能制造单阶段 Process 来假装成功。

## 下游保证

Consolidator 得到可直接阅读的完整过程候选，而不是 Activity 外包一层的标题。过程细节已经有来源，不允许仓库归并再次改写。

## 测试与当前成熟度

Candidate Reconstructor 已实现独立并行 job、DRAFT 源码请求、完整 REVIEW、详细阶段/规则/结果及 CONFIRMED/INFERRED/UNRESOLVED。直接测试证明输入使用完整 Activity、非法 statement/source ref 被拒绝、完成顺序不改变聚合结果。固定 326 Activity 的真实运行完成 14 个候选；1 个直接重建、13 个拆分，最终提供 46 个可归并的详细过程。
