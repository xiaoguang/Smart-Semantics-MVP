# ProcessMaterialAssembler

## 为什么存在

目录只负责找到一起阅读的活动。写精确业务流程需要完整Activity及可辨识的原文入口；一串无法区分的S编号不能帮助模型选择要核查的实现。

## 内部操作

```java
ProcessMaterial assemble(CandidateProcess candidate, FrozenAnalysisCorpus corpus,
                         ProcessMaterialProfile profile);
ProcessReviewMaterial resolveSourceRequests(ProcessMaterial material,
                                           CandidateProcessDraft draft,
                                           FrozenAnalysisCorpus corpus);
```
这是内部职责，不新增公共Agent或检索接口。两项操作零Provider、零导航。

## DRAFT前组装

1. 保留候选purpose及全部ActivityUse：同一Activity可有不同variant。
2. 每个不同Activity只放一份完整正文，按ActivityId引用，不能按用法重复大段内容。
3. 保留purpose、participants、objects、inputs、conditions、steps、results、rules、formulas、questions、limitations和原ref；分配稳定statement handle。
4. 来源目录包含ref、scope-local所属ActivityIds、保存snippet的前8个物理原始行openingLines。不要生成另一份用途摘要，不再统一写“用于按需核对已保存源码”。实际已有符号信息可带；不调用解析器补名字。
5. 全文件路径、全局身份、hash留在程序侧。来源仍限制为当前候选Activity的允许集合。

原文预览是选片线索，不是完整规则。完整Activity也不能因候选名称而由Java删掉“看似无关”的分支。

## REVIEW前组装

DRAFT输出requestedSourceRefs。程序验证allowlist，取得对应完整SourceReference.snippet，与原完整Activity、完整实际DRAFT一同交给REVIEW。零请求合法，不添加第三轮。未知ref立即失败；合法片段不足时模型明确UNRESOLVED，不自动查其他文件。

条件、拒绝分支和公式不能在容量控制时截断。去重以后仍超容量则使用现有明确未处理/拆分机制；不增自动补料循环。

## 输出保证

DRAFT知道有哪些可核查原文，REVIEW能读到所选完整实现。用法多对多不复制源对象，不丢引用归属，不把索引卡当详细材料。

## 测试和当前差距

当前完整Activity、statement handle及DRAFT请求→REVIEW源码已经接通；当前源码目录仍是无信息的ref+通用purpose。同Activity多用法需要同时贯通候选校验与正文去重。

Luna RED：两种variant共享正文但分别存在、预览逐行等于保存原文、完整REVIEW不被预览替代、非法/缺失引用、零隐式扫描。Terra GREEN限于确定性投影和接线。真实样例确认模型能够选中关联及条件源码，不把“每次必须请求源码”写成硬门槛。
