# RepositoryProcessConsolidator

## 为什么存在

候选允许重叠，因此同一过程可能由多个候选各自识别；一个大候选也可能被拆成主过程、退货过程和分析支撑。简单拼接会产生重复或互相矛盾的仓库目录。本模块只裁决过程之间的关系，不重新写详细业务规则。

## 输入

- 全部 ReviewedCandidateDisposition；
- 每个过程的紧凑摘要、ActivityUse、对象、开始/结束状态、CatalogKnowledgeItem 和 pending connections；
- 全 Activity 与候选 coverage；
- 不发送全部源码和完整 Activity 正文。

## 模型工作

一次仓库级 DRAFT 和一次 REVIEW，输出明确 decision：

- KEEP_DISTINCT；
- MERGE_DUPLICATES；
- PARENT_CHILD；
- RELATED；
- ALTERNATIVE；
- REJECT_DUPLICATE_OR_UNSUPPORTED。

模型可以提出仓库业务领域层级和过程间关系，不能重写候选中的具体条件、状态值、规则、阶段正文、CatalogKnowledgeItem 或 SourceRef。若合并，只选择保留哪些已审字段以及如何排列，不生成缩水摘要替代原内容。

## 程序工作

- 校验 decision 只引用已审过程。
- 根据 decision 确定性合并成员与关系，保留原过程细节和来源。
- 按 kind 和明确归属聚合 knowledge items；重复可以去重，正文、certainty 和 refs 不得丢失。
- 计算每个 Activity 的最终多对多 membership 或 STANDALONE/UNCLASSIFIED。
- 保留互相冲突的替代过程，不任意选择一个为事实。

## 输出

`RepositoryBusinessProcessCatalog`：businessAreas、processes、processRelations、standaloneActivities、unclassifiedActivities、process knowledgeItems、pendingConfirmations 和 coverage summary。Discovery 随后把同一 corpus 确定性投影的 `directActivityKnowledgeItems` 与这份目录一同封装进 `ProcessDiscoveryResult`；Consolidator 不为未进入候选的 Activity 编造过程，Publisher 也不另行回读 Activity。

## 失败

遗漏 reviewed process、循环 parent、合并后丢 stage/rule/ref、给 unclassified Activity 伪造过程、把 INFERRED 升为 CONFIRMED 均失败。输入过大时必须保存未归并范围并标 PARTIAL，不能静默只整理前若干项。

## 下游保证

Publisher 得到一份唯一、没有无声丢失的仓库过程目录；Step08 不必再次判断哪些过程相同或属于哪个领域。

## 测试与当前成熟度

Consolidator 已实现唯一 DRAFT/REVIEW、KEEP/MERGE_INTO/REJECT 主处置、过程关系和三层 denominator 闭合；程序对已审阶段、规则和来源做确定性保留。旧 `RepositoryProcessSummary` 不在新过程运行路径中。真实仓库归并保留并发布 46 个过程和 21 条过程关系；覆盖 CLOSED，semantic delivery 因 50 个未归类 Activity 为 PARTIAL。
