# RepositoryBusinessCataloger

## 目的与两个入口

发现哪些业务对象、目的和Activity用法值得合读，不提前证明顺序或写详细过程。当前已实现首次目录与旧目录输入后的全局选材；本页整合其系统认识/问题选择合同。

有catalogFromModelBatch时重开完整、有效的旧目录DRAFT/REVIEW任务，来源批次必须已停止，Activity集合/检查点和冻结basis一致。目录后续步骤失败不抹去完整目录。缺失或损坏不自动重跑。旧目录作资料不要求Prompt相同；完整过程精确复用仍要求指纹相同。

没有旧目录的新仓库使用首次目录：全部ReviewedActivity确定性投影ActivityIndexCard，保留name、businessPurpose、participants、businessObjects、triggerOrInput、conditions、activitySteps、codeDefinedResults、businessRules、terms、scopeLimitations原文。必要时稳定分片，每片DRAFT/REVIEW，再唯一merge DRAFT/REVIEW；所有Activity恰好进入一个原始分片。

## 一次全局系统认识与选材（已实现）

PROCESS_MATERIAL_SELECTION v2读取冻结项目说明、旧/首次目录、全部Activity轻量导航、完整文件目录和可选关注问题。导航只投影已有字段，可带statementCount，不重复长statementHandles或业务全文。文件名只是查找线索；未读正文不能当事实。

同次返回：

~~~text
systemAssessment:
  description
  typeHypotheses[{label,basis,uncertainties}]
  businessHypotheses[{key,name,hypothesis,whyInvestigate,refutingObservation,questions}]
candidateChanges:
  名称/目的/范围、承接旧候选、activityUses、contextActivityIds
  investigationQuestions、initialReadingRequests
oldCandidateDecisions: KEEP或REPLACE及理由
changedActivityDispositions: 受影响且不再为成员的既有合法处置
~~~

类型是自由标签，可混合/未知；无ERP/CRM/WMS枚举、得分门槛或行业路由。常识只提出可证伪阅读问题，不能由类型推导系统必有某能力。项目说明缺失可明确记录，不编造说明。全部导航确实超出容量时明确未执行，不静默取前N张卡或另开自动调查。

模型寻找对象产生、变化、关联、完成、撤销；同方法/同表不等于同业务。每种有材料支持的用途使用variant与role（CORE/OPTIONAL/ROLLBACK/SUPPORT/QUERY/ANALYTICS）。查询/统计可以召回关联活动，但不因此成为时序动作。材料只支持片段时保留片段。

## 用法与覆盖

候选可重叠，唯一用法按(ActivityId,variant)；相同Activity的不同用途不能去重。完整正文在Assembler按ID去重。目录不决定最终阶段，下游可细化、拆分或拒绝。

Activity处置为PROCESS_MEMBER、SUPPORT_ONLY、STANDALONE、UNCLASSIFIED或NOT_PROCESSED_CAPACITY。未涉及旧候选按KEEP，未涉及Activity继承处置。最终成员更新PROCESS_MEMBER；移出最后成员关系必须由模型明确非成员去向，Java不猜。context被读过不改变成员处置。

分片merge的完整DRAFT固定Activity分母。REVIEW仅在重复/遗漏其冗余处置清单时，程序保留DRAFT非成员处置并按已审成员关系重算PROCESS_MEMBER；未知ID、非法枚举、完整唯一清单中的孤立PROCESS_MEMBER仍失败。该容错不发明候选。不新增variant覆盖台账，也不声称Activity ID闭合已证明所有用途被理解。

继承且无首批请求的候选仍带其全部成员Activity进入一次CHECK，不自动读取所有引用文件或跳过检查。CHECK可以用唯一补读机会取关键原文。

## 保存、失败与测试

selection是单次决策，process-reading-decision-v2/producer v4保存完整实际输入、响应和指纹；历史v1/producer v3精确读取。导航/项目说明/关注问题、实际Prompt/Schema及binding进入指纹。旧目录作为输入不同于复用新决策。

当前systemAssessment、问题和选择已实际接线，不再列为待实现。后续新Activity来源只改变上游Corpus接入与实际输入；不重做目录算法。直接测试覆盖完整导航、多variant、跨组召回、移出最后成员、完整旧目录读取及零行业分类器。真实样本/全仓质量单列，旧14候选与后来24候选不可混称同次结果。

## 端到端收尾：同入口导航（待实施）

现有 `ActivityIndexCard.from/toNavigationJson`、Corpus的Activity模型投影未传递完整packet/entry/slice关联；当前418条卡片不能只靠名称让模型猜哪些来自同一次调用。新增的是来源导航，不是业务合并算法。

1. 从实际Activity来源与coverage/保存计划获取原publication、packet、入口键集合及sliceKey；历史没有slice时保留未知，不人工补一个相同范围。
2. 按来源publication＋packet＋入口集合形成稳定导航组，分配局部P/E键。每条Activity仍保留自己的完整入口关联（多入口交叠不能丢失）、业务卡及ID。
3. 首次目录、分片/合并、SELECT和CHECK均传递来源组关联。组太大时分页并保留组键和页号；不能因同组截掉其余卡片。
4. 模型判断同入口解释之间是不同用途、相同阶段的组成、支撑查询还是独立过程。Java不按标题/表名/相似度自动删除或合并。
5. 目录处置仍逐Activity闭合，不新增variant台账。多个variant、多入口、多过程SPLIT继续有效；同入口不等于同业务。

新418条不能借旧326条目录冒充本次发现。当前Step05正式入口拒绝 `catalogFromModelBatchId`；使用本次全集首次目录，不顺带增加目录迁移能力。任何必需包不完整/无法判定时，成功子集可查询，但不隐式减少325包分母生成正式全仓目录。

目标资源：catalog/SHARD及merge v2→v3、SELECT v2→v3、CHECK v4→v5；首次目录算法和调用层次不变。单次decision容器仍v2（字段未变），新输入/Prompt/Schema及Discovery v5进入指纹；新增来源组在实际input中保存，不只写日志。旧决策按原producer严格读，不能误复用。

直接验证同入口18条均在、交叠入口集合不丢、名称相同但不同包不误归组、跨分片组键稳定、多variant及全部处置保留。真实验收看重复和假步骤是否减少，不预定一定合成一个业务。
