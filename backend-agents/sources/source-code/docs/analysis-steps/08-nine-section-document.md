# 九章仓库概览

> [总体设计](../DESIGN.md)；固定 key：`nine-section-document`，目录：`steps/08-nine-section-document/`。旧 `BusinessReportPublisher` 生产路线已经退役；历史 checkpoint 的 reader/renderer 保留。本页描述未来 Step08 的目标合同，不代表当前生产实现。未来接入须读取 Step07 v2 的 narrative 和规则适用用法，不能回退旧 catalog 来补字段。

## 1. 为什么存在

`business-processes.md` 逐过程回答“系统支持哪些业务、每种业务怎样进行”。Step08 再把这份已审目录编排为一份固定九章的仓库概览，便于统一阅读和与其他 Source Agent 对齐。

Step08 不是第二个过程发现器。它不能从 326 个 Activity 重新分组、合并、拆分或排序；不能用流畅文字弥补 Step07 缺少过程。若 catalog 缺少某项关系，只能在第九章说明限制。

## 2. 输入

- `RepositoryBusinessProcessCatalog`：已归并业务领域、过程、ActivityUse、阶段、规则、关系、CatalogKnowledgeItem 正文和 certainty；
- `process-coverage.json`：Activity、候选、过程及未处理范围；
- SourceRef allowlist；
- 固定 NineSectionProfile。

报告模型不再接收全部 ReviewedActivity 作为重新发现过程的原料。必要的活动细节已经在 Step07 过程目录中以 ActivityUse、statement ref 和 source ref 保存。运行、批次、hash、路径、Provider 配置和调度数据留在程序侧。

## 3. 未来的 Step08 Publisher

| Interface 项 | 合同 |
| --- | --- |
| 输入 | 已发布 process catalog、coverage、SourceRef allowlist、report profile |
| 输出 | 完整九章 BusinessReport JSON、`source-refs.jsonl`、`document.md`、validation |
| 模型职责 | 将既有过程目录概括并编排为九章；不改变过程语义 |
| 程序职责 | 校验固定章节、catalog ID/ref、coverage；确定性排版 |
| 失败 | 缺章、非法 ref、虚假完整、改写 certainty、遗漏关键过程范围或 started 失败 |

一个报告 job 仍是一次 DRAFT 和一次完整 REVIEW：

```text
已归并 process catalog + coverage
→ 九章 DRAFT JSON
→ 完整实际 DRAFT + 与 DRAFT 相同的完整 catalog 投影/coverage
→ 九章 REVIEW JSON
→ Java deterministic Markdown
```

REVIEW 不需要重复接收 326 个 Activity，但必须接收 DRAFT 使用的完整 catalog 投影，不能换成会丢状态、规则、knowledge item 或 certainty 的短摘要。这不是重复过程发现，因为过程和详细规则已在 Step07 闭合。调用前对完整 catalog + DRAFT 做容量预检；不相容时零请求并明确失败，不能静默裁剪。DRAFT/REVIEW 固定同一 Provider/model/effort，不逐章并行，不增加第三轮。

## 4. 固定九章职责

| 序号 | H2 标题 | 只允许写什么 |
| --- | --- | --- |
| 1 | 文档说明 | 冻结来源、分析范围、coverage 与代码行为/运行事实区别 |
| 2 | 业务目标 | catalog 中已审 Business Process 的目的，不发明组织战略 |
| 3 | 业务对象 | catalog 已保存的 OBJECT 知识项、别名和用途 |
| 4 | 业务活动 | 按 Business Process 讲阶段、条件、分支、状态变化和结果 |
| 5 | 字段与维度 | catalog 已保存的 FIELD_OR_DIMENSION 知识项 |
| 6 | 对象关系 | catalog 已保存的 OBJECT_RELATION 及其 certainty |
| 7 | 指标口径 | catalog 已保存的 FORMULA_OR_METRIC；没有则明确未识别 |
| 8 | 示例问题 | catalog 已保存的 QUESTION，可围绕过程编排但不补造事实 |
| 9 | 待确认事项 | UNRESOLVED、未分类、未处理、外部效果和冲突替代 |

不能生成第十个 H2，也不能把一个 Process 的阶段拆散到不同章节后失去主线。第四章的每个过程至少保留过程名称、目的、主要阶段和关键分支；具体完整版本可引导读者查看 `business-processes.md`。

## 5. 内容保持规则

- Step07 已知具体谓词时，报告不得改成“状态允许”“符合规则”。
- `CONFIRMED` 不能被报告降成无意义的模糊话；`INFERRED/UNRESOLVED` 也不能升级为确定事实。
- 查询、统计和配置支撑不能被重排为主过程时序。
- 同一Activity不同ActivityUse保持业务变体；rule.activityUseIds不能丢失。阶段narrative已是已审业务正文，概览不能将它缩成接收/校验/返回模板。
- 没有公式不造指标；没有岗位依据不造角色。
- ActivityStatementRef/SourceRef 只是出处；报告需要的对象、字段、关系、公式和问题正文必须已经存在于 catalog，不能在 Step08 解引用 raw Activity 后重新提炼。
- 源码清楚构造并调用保存时可说“系统生成并保存”，但不说某次运行成功提交。

## 6. 来源与渲染

模型只使用 allowlisted 短 ref。Java 接收时逐项检查 ref 属于 process catalog，并确认可在 `source-refs.jsonl` 查询。

本次Step07新增的sources.md是既有SourceReference的确定性视图，详情页可点击；不要求为此重跑九章。未来九章接入可引用该来源视图，但不得产生指向未随交付携带文件的链接，具体接线随Step08计划实现。现有九章渲染保持不变。

`document.md` 只保留业务正文和 `[S123]` 短标记，不附完整源码块、不生成同文档源码锚点。`source-refs.jsonl` 保存文件、行号和原文。Renderer 只读取已验证 BusinessReport JSON，零 Provider，逐字节确定。

## 7. 输出

| 文件 | 作用 |
| --- | --- |
| `business-report.json` | 完整 REVIEW 后的九章结构化正文 |
| `source-refs.jsonl` | 短 ref 到冻结文件、行段和片段 |
| `document.md` | 固定九章概览 |
| `report-validation.json` | 章节、ref、coverage 和审阅状态 |

这些输出不替代 Step07 的 `repository-business-process-catalog.json` 和 `business-processes.md`。前者是结构化事实源，后者是业务过程主读物，九章是跨来源一致的展示。

## 8. 失败与空范围

缺章节、错标题、非法 catalog/ref、把未处理范围写成完成、遗漏一个完整业务领域或违反 certainty 都停止发布。零 Activity/零 Process 可以产生范围说明，但语义验收为 INCOMPLETE。

报告模型不能为 UNCLASSIFIED Activity 补写过程，也不能把 Activity coverage 闭合解释为业务理解完整。Step07 只有在所有分母已经通过合法语义处置闭合时，才可以发布 PARTIAL catalog，例如容量预检产生的明确未处理范围、`UNCLASSIFIED` 或 `INSUFFICIENT_MATERIAL`。Provider transport、runtime、schema、坏响应或已启动请求状态不确定属于 fatal：该模型批次不安装正式 catalog，Step08 也不得在同一批次继续运行。

## 9. 当前实现状态（2026-09-14）

历史实现曾生成固定九章 Schema、完整 DRAFT/REVIEW、外置 SourceRef 和确定性 Markdown。已有历史 checkpoint 仍可严格读取和重渲染；生产生成器已删除，因为它消费的是退役 singleton-process 路线，不能冒充以当前 Step07 v2 catalog 为输入的新实现。

当前报告仍从旧 RepositoryBusinessKnowledge 和完整 Activity 输入生成，所以上游 340 个单阶段 Process 的缺陷被带入正文；它不能证明跨 Activity 业务过程已经识别。待实现的修改是：输入改为新的 consolidated process catalog，Prompt 禁止重新发现过程，并保留新过程的具体 stage/rule/certainty。

## 10. 直接验收

- 同一 process catalog 重渲染字节一致且零 Provider。
- 恰好九章，所有短 ref 可查询，正文没有源码块。
- 第四章的过程、阶段、具体条件和结果与 `business-processes.md` 一致。
- 报告不能新增、合并、拆分或重新排序 catalog 中的过程关系。
- `INFERRED/UNRESOLVED` 不被升级，未分类和未处理范围进入第九章。
- 删除全部 Activity 原始输入后，报告仍可仅凭完整 catalog 生成；若不能，说明 Step07 输出不完整而不是让 Step08回读旧材料。
