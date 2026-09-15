# RepositoryBusinessCataloger

## 为什么存在

当前 Process 分组只比较精确字段和同文件，导致相关活动分散在不同容量组，模型从未同时看到一个业务的创建、修改、审核、履约、退回和结算。本模块给模型一次全仓视野，但只发送精简卡，不发送全部源码。

## 输入

程序从每个 ReviewedActivity 确定性生成一张 `ActivityIndexCard`：

```text
activityId
name / purpose
businessObjects
actions
stateObservations
identifiers
resultSummaries
limitations
```

`actions/stateObservations/identifiers` 只能从已有 Activity 字段投影和压缩，不能由 Java 行业词典补充。若卡片总体超过一次请求容量，按稳定分片生成目录片段，再用一次全局合并；每个 Activity 必须出现在恰好一个目录输入分片。

## 模型工作

Luna/high 的 DRAFT：

- 发现仓库中的 Business Area、对象别名、状态和常用标识。
- 提出可重叠 Candidate Process。
- 为候选指定 Activity 成员和初始用途：CORE、OPTIONAL、ROLLBACK、SUPPORT、QUERY、ANALYTICS。
- 将不能归类的 Activity 标为 STANDALONE 或 UNCLASSIFIED，并说明原因。

REVIEW 看到全部实际目录草稿和分片覆盖，只能修正领域、别名、成员、用途和遗漏；不能写详细过程步骤，也不能创建 ActivityId。

Prompt 不得列出“销售、采购、库存、财务”等期望答案。测试业务词只能作为输入 fixture 数据出现。

## 输出

`RepositoryBusinessCatalogDraft` 包含：

- businessAreas；
- candidateProcesses；
- candidateMemberships；
- aliases；
- activityDispositions；
- unresolvedCatalogQuestions。

候选只表示“值得一起深入阅读”，不证明顺序、因果、同一业务身份或运行结果。

## 失败与覆盖

未知/重复 ActivityId、遗漏 Activity、一个成员没有用途、非法枚举、分片未合并或把 UNCLASSIFIED 同时当成员均失败。允许一个 Activity 参加多个候选。所有 326 Activity 必须进入至少一个候选成员、STANDALONE 或 UNCLASSIFIED；不能出现当前结果中“过程没引用但 unmatched 为空”的情况。

## 下游保证

Assembler 获得全仓语义形成的候选，而不是原始文件顺序或固定每 24 个一组。候选仍可在详细审阅后拆分或拒绝。

## 测试与当前成熟度

Cataloger 已实现稳定分片、并行 DRAFT/REVIEW、唯一 merge、别名保存和 Activity 精确分母校验。生产 Java 与 Prompt 不预置销售、采购等行业词。固定 326 卡片的真实运行使用 6 个分片并形成 14 个可重叠候选；全部 326 张卡均有目录处置。
