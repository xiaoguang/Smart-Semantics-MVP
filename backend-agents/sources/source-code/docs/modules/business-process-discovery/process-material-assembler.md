# ProcessMaterialAssembler

## 为什么存在

目录只负责找到一起阅读的活动。写精确业务流程需要完整Activity及可辨识的原文入口；一串无法区分的S编号不能帮助模型选择要核查的实现。

## 内部操作

```java
ReadingMaterial collect(ReadingRequests requests, FrozenAnalysisCorpus corpus);
ProcessReadingPacket assemble(CandidateProcess candidate,
                             ReadingMaterial initial, ReadingMaterial supplement);
```
这是目标内部职责示意，不新增公共Agent或检索框架。确定性读取/封包零Provider、零导航；其间一次PROCESS_READING_CHECK由Discovery使用现有Provider运行，模型决定补读什么。

## DRAFT前组装

1. 保留候选purpose及全部ActivityUse：同一Activity可有不同variant。
2. 每个不同Activity只放一份完整正文，按ActivityId引用，不能按用法重复大段内容。
3. 保留purpose、participants、objects、inputs、conditions、steps、results、rules、formulas、questions、limitations和原ref；分配稳定statement handle。
4. 执行全局首批阅读请求，取全部选中的M10或同源冻结文件原文。成员之外的context Activity也取全文，但不必成为成员。
5. 选材阶段可用冻结相对路径/行范围，程序创建来源编号；全局身份、宿主路径和hash不进模型。原文不必绑定旧Activity。

原文预览是选片线索，不是完整规则。完整Activity也不能因候选名称而由Java删掉“看似无关”的分支。

## 阅读检查与封包（DRAFT前）

每个候选一次模型检查首批实际材料，返回可空supplementaryRequests及具体未知项。空清单直接封包；非空执行一次补读，再封包。Java不判断语义充分，不再发第三次选材。详见[补充模块设计 §3](../../supplements/cross-object-process-reconstruction/module-design.md#3-processmaterialassembler取原文检查后封包)。

DRAFT与REVIEW都见同一完整packet，REVIEW另见完整实际DRAFT。旧DRAFT请求→REVIEW取片段的路径退出新任务；关键原文不等到REVIEW才可见。statement/ref允许集合来自实际读入包，包括context及新源码，不来自旧候选边界。

条件、拒绝分支和公式不能在容量控制时截断。去重以后仍超容量则使用现有明确未处理/拆分机制；不增自动补料循环。

## 输出保证

DRAFT已经看到选中的完整Activity和原文，REVIEW看到相同材料和草稿。用法多对多不复制正文；读取未命中或不完整随包保存，不能伪称业务完整。

## 测试和当前差距

当前完整Activity、多用法、statement及前8行预览已实现；完整源码当前仍到REVIEW才到位。新全局读取、一次检查、补读和DRAFT前封包尚未实施；已有完整Activity传递继续复用。

Luna RED：两种variant共享正文但分别存在、预览逐行等于保存原文、完整REVIEW不被预览替代、非法/缺失引用、零隐式扫描。Terra GREEN限于确定性投影和接线。真实样例确认模型能够选中关联及条件源码，不把“每次必须请求源码”写成硬门槛。
