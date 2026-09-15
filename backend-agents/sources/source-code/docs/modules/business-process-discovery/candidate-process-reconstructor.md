# CandidateProcessReconstructor

## 为什么存在

模型要回答某种业务怎样完成，不是重新复述Controller如何接收参数。完整材料已在；本模块负责把它解释为对象生命周期、业务活动、具体规则和待确认联系。

## 一次候选job

完整材料 → PROCESS_DRAFT → 程序解析requestedSourceRefs → PROCESS_REVIEW → 保存完整已审结果。两轮保持同一Provider/model/effort，不自动重试；候选之间使用现有任务池并行。无新模型轮次。

DRAFT可以重建一个过程、拆成几个过程、处置为支撑或材料不足。它必须处置候选成员；不能以多写几个技术阶段替代业务重建。

## 目标数据

保留现有name、purpose、scope、participants、businessObjects、activityUses、stages、branches、businessRules、endResults、knowledgeItems、supportActivityUses、pendingConnections及refs。

本次仅新增两个语义字段：

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

## 完整REVIEW职责

REVIEW看到完整实际DRAFT、完整Activity及所请求原始源码，返回完整替代记录：
1. 逐项对照候选的不同variant，保留、细化或在现有reason/pendingConnections说明删除/缺失。当前结构分母仍按ActivityId，不能用其通过代替用法语义完整；本次不新增逐variant处置账。
2. 检查阶段是否业务步骤，是否仅将技术处理包装成生命周期。
3. 对每个用法核对条件分支；有引用不等于该规则适用所有variant。
4. 检查跨入口关联字段、对象与数量/状态更新；可推断关系标INFERRED，不能编成强制调用顺序。
5. 删除无材料的岗位、默认值、必经审批、外部成功；缺信息具体记录UNRESOLVED。
6. 保留全部具体规则、公式和拒绝路径，保证narrative与结构字段一致。
7. 同步修订rule.activityUseIds及引用，不能新增不在候选范围的Activity/source。

CONFIRMED至少有statement或source，但Java只验证来源存在；不验证中文蕴含。合法UNRESOLVED不导致整个运行自动失败。

## 结构错误和语义质量分开

未知ID/ref、缺必填字段、错误用法归属、遗漏处置、坏响应及Provider失败是fatal。Java校验规则refs属于所选用法对应Activity的允许集合，不由Java判定中文分支是否真正适用。

“只有空泛状态描述”“把价格规则用到错误子类型”“没有生命周期”是语义质量不通过，由REVIEW与样例审阅发现；不能伪称已经存在能自动识别这些问题的校验器。也不因此无限重跑候选。

## 测试与当前差距

模型两轮、详细结构、source请求、必填narrative和rule-use均已实现。v2 Prompt明确要求具体条件、拒绝路径和结果，并禁止用空泛状态说明掩盖缺失。旧版真实结果仍为技术处理阶段，只能作为失败对照；新版真实语义质量须由固定326条Activity的目录和代表候选重新验收。

Luna RED：新增字段完整传入REVIEW、保存重开和渲染；不同variant规则引用边界；不误拒合法不确定性。Terra GREEN只实现合同。真实语义验收见[贯穿例子](../../examples/semantic-framework-walkthrough.md)，自动fixture不能替代真实模型质量。
