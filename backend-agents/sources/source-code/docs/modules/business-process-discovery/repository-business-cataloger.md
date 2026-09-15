# RepositoryBusinessCataloger

## 为什么存在

完整Activity分散在不同入口。目录要发现“哪些业务对象和目的需要把这些活动一起读”，而不只是归为某类数据的增删改查。它不决定最终顺序。当前已有目录算法与模型接线，本次修正的是卡片、用法和语义要求，不新建模块。

## 输入：确定性卡片

每个ReviewedActivity投影一张ActivityIndexCard，保留原文：
```text
activityId / name / businessPurpose / participants / businessObjects
triggerOrInput / conditions / activitySteps / codeDefinedResults
businessRules / terms / scopeLimitations
```
businessRules为本次补入字段。其余来自现有字段；不由Java提取行业词、推导状态语义或凭名字补关系。摘要卡不替代下游完整Activity，完整公式/问题仍由下游取回。

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

本次不新增逐variant处置Schema。深入阅读可收窄用法；REVIEW须对照所有原候选用法并在现有reason/pendingConnections说明差异。结构覆盖仍针对Activity/候选/已审过程，不能声称它自动发现同Activity某variant在语义上被漏掉。

目录不写最终详细阶段，不证明先后和因果。下游仍可拆分、收窄或拒绝候选。

## 测试、分工与当前差距

已实现稳定分片、并行两轮、唯一merge和326条覆盖。当前真实14候选偏技术维护分类；“有候选且覆盖闭合”没有验证业务发现质量。卡片尚未包含businessRules；解析器按ActivityId禁止同候选重复用法，并存在成员处置自动降级路径，均需修正。

Luna RED：同Activity多variant、跨分片成员保留、未知/遗漏不自动修正、卡片原规则保留。Terra GREEN只处理结构/投影；不写行业分类器。真实验收须由目录自己召回有关联的业务用法，不能将测试答案作为生产分组输入。
