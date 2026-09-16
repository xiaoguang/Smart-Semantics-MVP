# RepositoryProcessConsolidator

## 为什么存在

候选可以重叠，仓库目录需要处理重复、父子、相关和替代过程。但归并不是再次发现生命周期，更不能把两个技术过程数组拼接成一条实际顺序。

## 输入与模型任务

`consolidationInput`发送由完整已审Process确定性投影出的业务完整视图，含过程身份、目的、范围、参与者、对象、ActivityUse身份与variant、全部阶段narrative及结构化条件/动作/状态变化/拒绝/结果/转移、业务规则、知识正文、结束结果和待确认项，另有业务领域信息。为避免全仓输入被重复证据撑爆，ActivityUse、阶段、规则和知识项内部的statement/source refs不重复发送；只保留过程级SourceRef allowlist供关系引用。完整Process仍留在程序侧，归并裁决最终应用到原对象。

这不是模型生成的摘要层，也不能只发送标题。凡影响判断两个过程是否相同、相关或替代的业务语义必须保留；哈希、statement handle及多层重复来源不参与业务归并。

沿用一次仓库DRAFT+完整REVIEW。每个已审过程有唯一主处置KEEP、MERGE_INTO或REJECT；关系为PARENT_CHILD、RELATED或ALTERNATIVE。不另创枚举。

模型只决定归属/去重和关系，不能生成新的阶段正文、规则、条件或来源，也不能提升certainty。程序仍用原始完整过程验证并执行MERGE_INTO，精简输入不改变无损合并条件。

全部已审Process是程序已知的固定分母。若唯一REVIEW在长`processDecisions`数组中遗漏Process，遗漏项确定性按`KEEP`处理，保留其完整原文；不采用未经审阅的合并，也不追加第三轮调用。未知Process、同一Process重复处置、非法target或非法关系仍是fatal。

## 无损归并

程序执行裁决时保留完整stage.narrative、结构字段、rule.activityUseIds、variant、knowledgeItems和refs；局部ID映射必须同步所有引用。只有完全相同内容可去重。

MERGE_INTO先按(activityId, variant, role)映射用法，满足现有完整阶段序列等无损条件时才并入。条件不满足时，沿用当前实现保留双方完整原过程、记录未合并原因，并同步最终目录和coverage；不是整个批次fatal，也不自动发明关系或追加调用。不使用中文相似度重排。

共享Activity但对象/variant不同不等于重复。局部过程和跨对象过程可以并存。阶段不同保留各原过程；模型有依据可给RELATED/ALTERNATIVE，但程序不通过拼数组制造长链。

未入候选Activity的知识仍由Discovery在原corpus中确定性投影，不为它制造假过程。

## 覆盖与失败

三个分母保持；遗漏主处置沿用安全KEEP，无法无损合并保留原过程。未知过程、重复处置、非法关系、循环父子或丢内容仍fatal。增量选材只更新受影响成员和处置，不新增阅读台账。

## 输出与下游

唯一RepositoryBusinessProcessCatalog连同coverage和完整来源组成封闭ProcessDiscoveryResult。Publisher只渲染；未来Step08也只能概览，不能重排业务顺序。

## 测试与当前状态

归并、完整内容保留、局部编号映射和不满足无损条件时KEEP均已实现。本轮只消费[新阅读重建结果](../../supplements/cross-object-process-reconstruction/README.md)，不扩建归并算法。必要新source refs在归并/最终result前统一映射，业务完整视图不丢条件。

Luna RED重点覆盖“同Activity不同variant不丢”“规则条件不同不去重”“正文保存重开一致”“无法无损合并则不制造顺序”。Terra不得通过中文相似度或行业规则代替模型裁决。真实验收比较候选REVIEW与最终正文，确认归并没把具体业务改回抽象标题。
