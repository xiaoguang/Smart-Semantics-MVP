# CandidateProcessReconstructor

## 为什么存在

模型要回答某种业务怎样完成，不是重新复述Controller如何接收参数。完整材料已在；本模块负责把它解释为对象生命周期、业务活动、具体规则和待确认联系。

## 一次候选job

目标为前置完整阅读包 → PROCESS_DRAFT事实推理 → PROCESS_WRITE业务全文 → PROCESS_RULE_REVIEW最终规则核对 → 保存完整已审结果。三阶段保持同一Provider/账户/model/effort和一个job名额。WRITE只接收完整实际DRAFT及其中业务名称、用途和规则；RULE_REVIEW必须同时接收完整原文包、实际DRAFT和实际WRITE，直接局部修正实际正文及对应结构字段。最终核对后不再模型润色。详细输入、响应和容量由[补充设计](../../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)维护。

DRAFT可以重建一个过程、拆成几个过程、处置为支撑或材料不足。它必须处置候选成员；不能以多写几个技术阶段替代业务重建。

DRAFT实际外层输入为readingPacket v1、investigationContext及readingSelections；后两项携带关注问题、系统判断/假设、候选问题和读取用途/选择说明，作为调查背景而非已确认事实。它们必须随决策保存、重开并参与指纹；最终RULE_REVIEW复用同一外层输入，不能只拿包而丢掉调查背景。WRITE仍仅接收完整实际事实草稿。

## 目标数据

保留现有name、purpose、scope、participants、businessObjects、activityUses、stages、branches、businessRules、endResults、knowledgeItems、supportActivityUses、pendingConnections及refs。

以下两个语义字段已实现，继续复用，不重建：

- ProcessStage.narrative：必填非空业务段落，与原进入条件、动作、状态变化、拒绝、结果、转移和certainty同时保留。
- BusinessRule.activityUseIds：必填非空适用用法集合；模型wire为activityUseLocalIds，程序转换ID。

一项通用Activity可产生多个variant用法；阶段和规则必须说明本次用的是哪一分支，不把完整Activity里所有分支条件加到每个对象上。knowledgeItems继续保留选中的完整对象、字段、关系、公式和问题，不只留ref。

## 业务叙述要求

阶段围绕业务对象的可辨认动作或里程碑命名；不默认按“接收→校验→调用→返回”展开。“接收两个参数”不能被写成业务动作长句。若本身研究的是接口协议或日志管理，技术活动仍可能就是业务，不能用Java关键词黑名单删除。

narrative应说明：
- 对什么对象做什么，为什么或在何种范围；
- 已知的允许/拒绝条件及具体状态值；
- 产生的结果、变化和下一步；
- 哪个联系只是推断，哪里缺材料。

未知岗位可不写；状态业务名称未核实则保留数值并注明未知。静态实现可以说明“保存对象”，不能声称某次运行已成功提交。

规则仍为subject、when、actionOrDecision、otherwise、result、certainty、refs。反例是“状态允许时可以修改”；目标是“原单状态为0时允许修改，否则拒绝”，并限定到真实适用用法。

## 最终RULE_REVIEW职责

最终RULE_REVIEW看到完整包、完整实际DRAFT和WRITE，返回完整最终过程及私有corrections，而非只列错误。正确且可读的段落保留，能确定的错误局部修正：
1. 逐项对照候选的不同variant，保留、细化或在现有reason/pendingConnections说明删除/缺失。当前结构分母仍按ActivityId，不能用其通过代替用法语义完整；本次不新增逐variant处置账。
2. 检查实际WRITE是否讲清业务步骤，是否仍为技术模板；不为了审阅而重写整篇。
3. 对每个用法核对条件分支；有引用不等于该规则适用所有variant。
4. 检查跨入口关联字段、对象与数量/状态更新；可推断关系标INFERRED，不能编成强制调用顺序。
5. 删除无材料的岗位、默认值、必经审批、外部成功；缺信息具体记录UNRESOLVED。
6. 保留全部具体规则、公式和拒绝路径，保证narrative与结构字段一致。
7. 同步rule.activityUseIds及引用；不能引入实际阅读包外的Activity/source。context可支持理解，不必成为成员。若原Activity与完整源码有差异，过程可依据原文纠正并保存原因，原Activity不变。

规则引用采用实际阅读包allowlist：包括读过的context Activity及未绑定旧Activity的同源源码。activityUseLocalIds只表达业务适用，不是来源所有权。未知/未提供引用仍拒绝；不因来源原来在别的候选而拒绝。

CONFIRMED至少有statement或source，但Java只验证来源存在；不验证中文蕴含。合法UNRESOLVED不导致整个运行自动失败。

## 结构错误和语义质量分开

未知ID/ref、未提供ref、缺字段、错误用法归属、遗漏处置、坏响应及Provider失败是fatal。Java只校验实际包和结构，不按ActivityUse追究证据所有权，不判定中文分支是否适用。合法读取不足保留UNRESOLVED。

“只有空泛状态描述”“把价格规则用到错误子类型”“没有生命周期”是语义质量不通过，由REVIEW与样例审阅发现；不能伪称已经存在能自动识别这些问题的校验器。也不因此无限重跑候选。

## 测试与当前差距

当前已实现同一完整Activity/原文包上的DRAFT/REVIEW双轮、详细结构和narrative/rule-use。目标三阶段尚未生产接线，沿用现有详细Process形状及五文件；仅新过程私有结果保存draft/writing/review的v3完整记录，历史v2 pair不能当三阶段结果复用。版本与实现差异见[实施状态](../../supplements/cross-object-process-reconstruction/implementation-status.md)。原326Activity不重跑。

直接RED覆盖三阶段顺序、WRITE完整事实输入、最终RULE_REVIEW实际包+DRAFT+WRITE、最终正文/规则保存渲染、完整三阶段重开及旧pair不误复用。真实语义只做[三个样本](../../supplements/cross-object-process-reconstruction/acceptance.md)，之后停止等待全仓讨论；自动fixture不能替代实际正文审阅。
