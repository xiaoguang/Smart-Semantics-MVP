# CandidateProcessReconstructor

## 目的与一次候选job

从已冻结的完整ProcessReadingPacket解释业务对象的生命周期和具体规则。当前生产已实现事实DRAFT→业务WRITE→最终RULE_REVIEW；三个阶段保持同一Provider/账户/model/effort和一个候选job名额。其他候选可并行。

| 阶段 | 完整实际输入 | 输出与用途 |
| --- | --- | --- |
| BUSINESS_PROCESS_DRAFT（Prompt v4） | readingPacket v1、investigationContext、readingSelections | 现有完整详细过程草稿 |
| BUSINESS_PROCESS_WRITE（v1） | actualDraft，含名称/用途/规则/未知 | 相同结构及局部ID的业务全文，尚未已审 |
| BUSINESS_PROCESS_RULE_REVIEW（v1） | 原DRAFT外层输入+actualDraft+actualWriting | {processResult,corrections}，完整最终过程与私有修正说明 |

DRAFT可重建、拆分、处置为支撑或材料不足，不能靠多个技术阶段冒充生命周期。系统认识/假设是调查背景，不是CONFIRMED事实。所有关键原文在DRAFT前到位，最后核对后无模型润色或额外取材。

## 完整业务结构

保留name、purpose、scope、participants、businessObjects、activityUses、stages、branches、businessRules、endResults、knowledgeItems、supportActivityUses、pendingConnections和refs。stage.narrative为必填非空业务正文，同时保留进入条件、动作、状态变化、拒绝、结果、转移及certainty。rule.activityUseIds（模型wire为activityUseLocalIds）是非空业务适用集合，不是证据owner。

同一Activity可以有多个variant。每阶段围绕有业务意义的对象动作/里程碑，写清何时允许/拒绝、产生什么、怎样衔接。具体status条件、否定词、数量/金额用途、配置例外不能缩成“符合条件”。未知岗位不编造；业务名称无依据时保留实际值并说明未知。

跨对象说明应回答前一步产物、后一步关联/选择、是否可不关联或分批、如何回写数量/金额/状态、回退与配置范围。常识不能补出强制办理顺序；相同金额字段的不同用途不能统一翻译。演示数字只作规则边界例子，不是客户数据。

knowledgeItems保留完整对象/字段/关系/公式/问题正文、owner、certainty和refs，不只留指针。查询/统计/配置可作支撑，不强行变成时序步骤。

## 最终规则核对

RULE_REVIEW看实际WRITE，保留准确可读段落，直接修正错误正文与对应结构。重点检查对象交接、允许/拒绝/否定、默认值的条件、数量/金额/单位、配置范围、分批/回退，以及narrative和结构一致。

返回完整processResult与可空corrections。每项修正记录过程/阶段位置、原句、改句和原因；不新建Proof。finalizeCandidate只把processResult交既有parser，不能把包装对象当旧响应。重大矛盾可收窄或判不足，未知具体保留。

原文可纠正旧Activity解释并记录差异；原326条Activity不覆盖、不重新生成。规则可引用实际包内context Activity和未绑定旧Activity的冻结原文；ActivityUse必须是最终成员，引用只限实际allowlist。合法ref不证明适用全部variant，Java不判断中文蕴含。最终审阅须对照原候选所有variant，结构的Activity分母不等于variant质量门禁。

## 保存、容量、失败与复用

当前新过程私有格式是model-job-reviewed-result-v3，pipeline=business-reasoning-writing-rule-review-v1，producer v4。保存完整input、readingPacket、sourceReferenceMapping、inputFingerprint、binding/runtime identity、draft、writing、review与可空reusedFromModelBatchId。三份raw请求/响应及journal不改，最终ref归一化只改内存副本。

DRAFT/WRITE在fresh接受后和精确复用前都按实际响应Schema（networknt、本地$ref）校验类型/必填/enum；fresh失败PROCESS_MODEL_SCHEMA_INVALID，损坏复用MODEL_JOB_RESULT_INVALID。此门禁不提前执行最终覆盖/CONFIRMED依据或中文语义判断；最终RULE_REVIEW仍可纠错。

每阶段校验真实序列化input、Prompt、Schema和输出空间。DRAFT能容纳不保证原文包+DRAFT+WRITE能容纳；最终超窗时保存两稿及未发送原因，不删除其中一份、不标reviewed、不自动重试。

Step07无自动重试。fatal停止新派发，已经启动且前置合法的候选排空保存，完整成功候选不因别包失败丢失；不归并/发布。显式新批次只复用完整三阶段且全部输入/Prompt/Schema/顺序/模型绑定匹配的job。历史v2 pair和孤立中间稿不能自动续接为三阶段，也不适用新Activity的stage retry。

## 当前质量与直接验证

上述三阶段已在DefaultBusinessProcessDiscovery.reconstructRaw/finalizeCandidate及PrivateModelJobResultStore接线。producer v4正文以最终RULE_REVIEW为依据，历史2/3按原receipt渲染。

历史三例没有全部通过：采购核对超窗未发送；销售最终响应有7个未定义查询用法并有业务范围错误；调拨完整保存仍有两处配置限定问题。可读性获认可，不能说三例全通过或已完成全仓验收，见[原实测](../../supplements/cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。

直接测试只证明三阶段顺序、完整输入、Schema、保存/复用与零上游调用；真实正文仍需准确性审阅。新Step05接入应保留这些行为，本轮没有代码/测试/模型执行。
