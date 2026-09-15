# FrozenAnalysisCorpus

## 为什么存在

Activity与M10来源材料来自不同checkpoint。corpus统一其只读查询和归属检查，让后续模型工作不触发重新扫描。它是Discovery内部能力，不要求再拆出公共框架。

## 当前范围与输入

固定输入是全部已审Activity、Activity coverage、对应M10材料的SourceReference及其checkpoint引用。当前实现只开放Activity已引用且在M10验证存在的SourceRef，不承诺任意M10源码搜索。

内部操作：overview、按ActivityId读取完整Activity、按statement handle查原字段、按SourceRef读文件/原行范围/完整snippet。返回不可变数据，不接收任意本地Path。

**本次不增加methods(MethodKey)、JavaCodeIndex或客户checkout读取。** 保存JDT材料是原始来源，但不能将“可复用它”误写为当前corpus已经提供整个索引检索。现有片段真正不足时先记录缺口，再讨论定向补料。

## 程序工作

- 用输入checkpoint对应策略重开Activity/M10，用当前输出策略发布新Step07；输入与输出registry身份分离。
- 验证Activity、覆盖和来源归属，建立只读Activity/SourceRef索引。
- 为现有可引用字段分配稳定statement handle，不新建Proof。
- 为未进入候选的支撑、独立、未分类Activity确定性保留已有知识正文及原owner/disposition，放入封闭Discovery result。
- 来源预览由Assembler从已保存snippet取原始行；发布来源页也只消费同一值，不再读代码文件。
- 保留已知技术缺口。缺数据不冒充空结果，不隐式修复。

## 成功、Gap与fatal

来源完整则返回原内容。合法片段不足以说明某条业务关系由上层记录UNRESOLVED；未知ref、损坏checkpoint、错误owner、越界statement为fatal。均不能触发JDT、JavaParser、Builder或ActivityExplainer。

## 模型边界与下游保证

本模块零模型。路径、hash和checkpoint链不进入模型输入；完整业务字段和选定原文由上层按需提供。Publisher收到封闭结果，不回读corpus。

## 测试与当前状态

现有corpus和固定326 Activity可复用；本次仅收紧文档承诺，不重建存储。Luna RED保持原引用映射、跨owner拒绝、缺片段不重扫；Terra不得借阅读辅助增加导航器或语义检索器。上游协议不升版。
