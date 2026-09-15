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

MERGE_INTO的确定性前提：先按(activityId, variant, role)映射用法；忽略各自局部ID后，完整阶段序列的业务字段（含narrative、结构条件、certainty和refs）逐项相等。满足时可并入其余已审记录，只去除完全相同重复；不同阶段序列则拒绝该合并裁决，模型应KEEP+关系，不自动追加一次调用。不使用中文相似度或重新编排算法。

共享Activity但对象/variant不同不等于重复过程。阶段顺序、分支或规则无法无损合并时，模型应KEEP并记录RELATED/ALTERNATIVE，不强制MERGE_INTO。程序不得将两个阶段数组简单连接后宣称发生了业务流转；既有合并路径必须满足上述规则，否则拒绝该裁决。

未入候选Activity的知识仍由Discovery在原corpus中确定性投影，不为它制造假过程。

## 覆盖与失败

Activity、候选和已审过程三个分母保持现有闭合合同。未知过程、遗漏主处置、非法关系、循环父子、丢阶段/规则/来源是fatal；合法未分类、材料不足和容量未处理按既有PARTIAL说明，不掩盖Provider失败。

## 输出与下游

唯一RepositoryBusinessProcessCatalog连同coverage和完整来源组成封闭ProcessDiscoveryResult。Publisher只渲染；未来Step08也只能概览，不能重排业务顺序。

## 测试与当前状态

归并、主处置、narrative/规则/来源的稳定并集、ActivityUse局部编号重映射及非同义过程保留已经实现。只有名称、目的、范围和完整归一化阶段序列一致的过程才能合并；不同阶段不会再被机械拼接成新生命周期。旧版真实46个过程仍须由v2真实运行替换后再作语义验收。

Luna RED重点覆盖“同Activity不同variant不丢”“规则条件不同不去重”“正文保存重开一致”“无法无损合并则不制造顺序”。Terra不得通过中文相似度或行业规则代替模型裁决。真实验收比较候选REVIEW与最终正文，确认归并没把具体业务改回抽象标题。
