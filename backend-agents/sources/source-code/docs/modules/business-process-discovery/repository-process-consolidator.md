# RepositoryProcessConsolidator

## 为什么存在

候选可以重叠，仓库目录需要处理重复、父子、相关和替代过程。但归并不是再次发现生命周期，更不能把两个技术过程数组拼接成一条实际顺序。

## 输入与模型任务

沿用当前consolidationInput发送全部完整已审Process JSON，含narrative、ActivityUse、全部阶段/规则/条件、知识项、来源和待确认项，另有业务领域/覆盖信息。模型不再读原Activity/源码，但不能仅给它标题摘要却要求判断细节是否重复。本次不开发新的摘要投影。

沿用一次仓库DRAFT+完整REVIEW。每个已审过程有唯一主处置KEEP、MERGE_INTO或REJECT；关系为PARENT_CHILD、RELATED或ALTERNATIVE。不另创枚举。

模型只决定归属/去重和关系，不能生成新的阶段正文、规则、条件或来源，也不能提升certainty。

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

当前归并和主处置已实现，真实发布46个过程。新增字段的无损合并、用法映射和非同义过程保留尚待实现。

Luna RED重点覆盖“同Activity不同variant不丢”“规则条件不同不去重”“正文保存重开一致”“无法无损合并则不制造顺序”。Terra不得通过中文相似度或行业规则代替模型裁决。真实验收比较候选REVIEW与最终正文，确认归并没把具体业务改回抽象标题。
