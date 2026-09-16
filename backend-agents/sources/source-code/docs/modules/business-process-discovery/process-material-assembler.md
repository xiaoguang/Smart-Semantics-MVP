# ProcessMaterialAssembler

## 为什么存在

目录只负责找到一起阅读的活动。写精确业务流程需要完整Activity及可辨识的原文入口；一串无法区分的S编号不能帮助模型选择要核查的实现。

## 内部操作

```java
ReadingMaterial collect(ReadingRequests requests, FrozenAnalysisCorpus corpus);
ProcessReadingPacket assemble(CandidateProcess candidate,
                             ReadingMaterial initial, ReadingMaterial supplement);
```
这是目标内部职责示意，不新增公共Agent或检索框架。确定性读取/封包零Provider、零导航；其间一次PROCESS_READING_CHECK由Discovery使用现有Provider运行，模型决定首批保留/移出与一次补读。具体记录身份和字段由[补充设计](../../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)维护。

## DRAFT前组装

1. 保留候选purpose、调查问题及全部ActivityUse：同一Activity可有不同variant；系统类型判断仅为调查背景，不当成事实。
2. 每个不同Activity只放一份完整正文，按ActivityId引用，不能按用法重复大段内容。
3. 保留purpose、participants、objects、inputs、conditions、steps、results、rules、formulas、questions、limitations和原ref；分配稳定statement handle。
4. 执行全局首批阅读请求，取全部选中的M10或同源冻结文件原文。成员之外的context Activity也取全文，但不必成为成员。
5. 选材阶段可用冻结相对路径/行范围，程序创建来源编号；全局身份、宿主路径和hash不进模型。原文不必绑定旧Activity。

原文预览是选片线索，不是完整规则。完整Activity也不能因候选名称而由Java删掉“看似无关”的分支。

## 阅读检查与封包（DRAFT前）

目标每个候选一次模型检查首批实际材料，明确返回retainedReadingRecordIds、最终成员/context、可空supplementaryRequests、未知及选择理由。最终包由保留首批记录、实际补读和最终成员/context完整Activity组成；移出材料仍保留历史读取记录，空保留集不表示全部沿用。Java不判断语义充分，不再发第三次选材。首批定位预览必须标明不完整，不能冒充完整方法或查询。

DRAFT与最终RULE_REVIEW都见同一完整packet，WRITE只读完整实际事实草稿；最终核对另见实际DRAFT和WRITE。关键原文在DRAFT前到位，过程中不再取材。statement/ref允许集合来自最终实际读入包，包括context及新源码，不来自旧候选边界。

条件、拒绝分支和公式不能在容量控制时截断。去重以后仍超容量则使用现有明确未处理/拆分机制；不增自动补料循环。

## 输出保证

DRAFT看到选中的完整Activity和原文，最终RULE_REVIEW看到同一包和两份完整实际模型结果。用法多对多不复制正文；读取未命中或不完整随包保存，不能伪称业务完整。

readingPacket v1不承担新执行字段。调查背景investigationContext与实际读取用途/选择说明readingSelections放在DRAFT的外层输入，随决策保存、重开并参与指纹；最终RULE_REVIEW收到相同外层输入，详细字段由补充设计维护。

## 测试和当前差距

完整Activity、多用法、statement继续复用。新全局读取、一次检查、补读和DRAFT前封包已接线，Pipeline直接测试通过；真实选材和材料充分性见[交付记录](../../supplements/cross-object-process-reconstruction/delivery.md)，不把离线替身结果当作业务质量通过。

本次首批保留/移出合同、问题与系统背景传递尚待实现。沿用冻结读取器、来源映射和完整Activity，不新增SQL/Vue解析器或自动取材循环。直接测试补上保留/补读集合、完整内容与请求目的不丢及零上游调用；真实验收止于三个样本。

Luna RED：两种variant共享正文但分别存在、预览逐行等于保存原文、完整REVIEW不被预览替代、非法/缺失引用、零隐式扫描。Terra GREEN限于确定性投影和接线。真实样例确认模型能够选中关联及条件源码，不把“每次必须请求源码”写成硬门槛。
