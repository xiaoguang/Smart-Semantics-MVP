# RepositoryBusinessCataloger

> 首次目录及已有目录的增量选材已接线。本次在同一全局选材中加入系统认识与问题驱动的可证伪假设，详细目标由[补充设计](../../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)维护；实现差异见[实施状态](../../supplements/cross-object-process-reconstruction/implementation-status.md)。

## 为什么存在

完整Activity分散在不同入口。目录要发现“哪些业务对象和目的需要把这些活动一起读”，而不只是归为某类数据的增删改查。它不决定最终顺序。当前已有目录算法、卡片、旧目录重开与全局选材；本次补系统判断和具体调查问题，不重做首次目录模块。

## 新仓库首次目录：确定性卡片（已实现）

每个ReviewedActivity投影一张ActivityIndexCard，保留原文：
```text
activityId / name / businessPurpose / participants / businessObjects
triggerOrInput / conditions / activitySteps / codeDefinedResults
businessRules / terms / scopeLimitations
```
businessRules已经补入，继续复用。其余来自现有字段；不由Java提取行业词、推导状态语义或凭名字补关系。摘要卡不替代下游完整Activity，完整公式/问题仍由下游取回。

超出单任务上下文时，使用已有稳定分片、每片DRAFT+REVIEW及唯一全仓merge DRAFT+REVIEW。全部Activity恰好进入一个原始分片；不能截去卡片尾部。规则增加导致任务变大时显式规划，不静默删减。

## 模型工作

中文指令见[Prompt设计](../../references/semantic-interpretation-prompts.md)。模型发现业务领域、别名、可重叠候选及Activity处置：
- 沿业务对象的产生、变化、关联、完成或撤销寻找候选，不以API名字的CRUD分类结束。
- 通用Activity的不同业务分支使用不同variant；不凭同方法/同表认定相同业务。
- 复用candidate.purpose描述为什么值得合读；variant是具体对象/分支用途，role是CORE、OPTIONAL、ROLLBACK、SUPPORT、QUERY或ANALYTICS。
- 不把查询/统计当作对象流转动作，但可用其关联信息召回别的核心活动。
- 跨分片合并保留这些线索，不能把子类型重新压成一个“通用维护”候选。
- 不为凑生命周期编造缺失阶段；材料只支持片段则保留片段或未分类。

不在Java或Prompt中预设任何行业答案。领域名称只能来自输入/模型发现。

## 用法和覆盖

同一候选允许同一Activity的多个variant；唯一性按(activityId, variant)，不是ActivityId。不得把不同用途去重；候选组装时完整Activity正文可以去重。

每个Activity必须有现有处置：PROCESS_MEMBER、SUPPORT_ONLY、STANDALONE、UNCLASSIFIED或NOT_PROCESSED_CAPACITY。PROCESS_MEMBER必须有至少一个真实候选成员关系，不能由Java静默降成UNCLASSIFIED。重复分片输入、未知Activity、遗漏处置、相同用法重复及非法枚举是结构错误；候选可能重叠不是错误。

在全仓分片合并中，完整DRAFT固定Activity覆盖分母。REVIEW对候选和业务边界有最终裁决权，但它对完整处置清单的重复抄写不是新的业务判断：若该清单出现重复或遗漏ID，程序从DRAFT恢复完整非成员处置，再按REVIEW后的实际候选成员关系确定PROCESS_MEMBER。未知Activity、非法枚举和完整唯一清单中的孤立PROCESS_MEMBER仍然失败；这不是自动归组，也不增加候选。

本次不新增逐variant处置Schema。深入阅读可收窄用法；REVIEW须对照所有原候选用法并在现有reason/pendingConnections说明差异。结构覆盖仍针对Activity/候选/已审过程，不能声称它自动发现同Activity某variant在语义上被漏掉。

目录不写最终详细阶段，不证明先后和因果。下游仍可拆分、收窄或拒绝候选。

## 已有目录：直接重开后增量选材及本次目标

从显式旧批次读取完整目录任务，沿用现有DRAFT/REVIEW规范化，不重新运行上述目录分片/merge。旧目录可以作为新Prompt资料，不等于旧过程可直接当新版已审结果。

目标一次PROCESS_MATERIAL_SELECTION读取冻结项目说明、旧候选、全部Activity轻量导航、冻结文件目录和可选问题，同时返回systemAssessment、可证伪业务假设、候选investigationQuestions、首批阅读请求及受影响处置。常识只帮助提出核实/反证问题，不成为已实现能力；类型可混合或未知，不设行业枚举或分类分数，不另增模型调用。导航只从已有字段投影，完整规则/公式不在此重写。候选可跨旧组，查询/统计活动也可召回；上下文不足不静默漏卡或自动重跑旧目录。

未提到旧候选按KEEP继承。未涉及Activity承接旧处置；最终候选成员关系更新PROCESS_MEMBER。被移出全部候选须由模型给出已有合法非成员去向；只为理解而读过的context Activity不必成为过程步骤。阅读检查可继续细化成员，冻结包时统一应用增量；不重建台账。

旧目录为24候选/138个不同成员的事实只用于本次输入，不写死框架。全部326条均可选，不把原未入组188条静默排除。

## 测试、分工与当前差距

已实现首次目录、businessRules卡片、多用法、覆盖规范化、显式旧目录输入、全局阅读选择及增量更新。尚缺同一次选材中的系统认识、明确假设/调查问题及其下游传递；一次研究实测不能当生产接线完成。不把历史14候选与后续24候选混成同一结果。本轮仅三个样本，不自动重跑全仓候选。

Luna RED：同Activity多variant、跨分片成员保留、未知/遗漏不自动修正、卡片原规则保留。Terra GREEN只处理结构/投影；不写行业分类器。真实验收须由目录自己召回有关联的业务用法，不能将测试答案作为生产分组输入。
