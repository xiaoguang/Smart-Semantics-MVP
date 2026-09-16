# FrozenAnalysisCorpus

> 增量见[补充模块设计 §1](../../supplements/cross-object-process-reconstruction/module-design.md#1-frozenanalysiscorpus一次打开已有资料)。M10/Activity读取继续复用；冻结文件查询已接入Step07，读取与范围测试通过，完整验收见[交付记录](../../supplements/cross-object-process-reconstruction/delivery.md)。

## 为什么存在

Activity与M10来源材料来自不同checkpoint。corpus统一其只读查询和归属检查，让后续模型工作不触发重新扫描。它是Discovery内部能力，不要求再拆出公共框架。

## 当前范围与输入

固定输入是全部已审Activity、coverage、M10和同源verified inventory引用。当前已经可以读取这些保存材料和冻结文本，不再只开放Activity已引用的M10 SourceRef。本次系统认识与三阶段成稿复用这条接线，不把已完成reader再次列为待开发。

内部操作：overview、按ActivityId/statement/SourceRef查询；冻结文件目录、明确文件/行范围/全文读取、指定文件集合的字面量搜索。返回原文及实际范围、未命中/省略说明。模型选择fileKey或已列相对路径，不接收任意宿主Path。

复用VerifiedSourceTextReader读取注册的冻结字节；不加MethodKey/JavaCodeIndex查询，不导航、不读变化中的checkout、不执行源码。加载冻结文本不是重跑JDT。原文不必先绑定旧Activity，选材范围不再受旧候选限制。

## 程序工作

- 用输入checkpoint对应策略重开Activity/M10，用当前输出策略发布新Step07；输入与输出registry身份分离。
- 验证Activity、覆盖和来源归属，建立只读Activity/SourceRef索引。
- 为现有可引用字段分配稳定statement handle，不新建Proof。
- 为未进入候选的支撑、独立、未分类Activity确定性保留已有知识正文及原owner/disposition，放入封闭Discovery result。
- Assembler读取选中原文并在DRAFT前封包；Publisher只消费封闭result中的来源，不再读代码文件。
- 保留已知技术缺口。缺数据不冒充空结果，不隐式修复。

## 成功、Gap与fatal

合法搜索未命中或原文不足是reading gap，由模型写具体UNRESOLVED；来源损坏、请求越出冻结范围、未知ref/statement或owner不一致是fatal。均不触发JDT、JavaParser、Builder或ActivityExplainer。

## 模型边界与下游保证

本模块零模型。选材模型可见仓库相对路径/文件键，宿主路径、hash和checkpoint链不可见。完整字段和选定原文交给下游；Publisher不回读corpus。

## 测试与当前状态

现有corpus、冻结文本reader和326 Activity复用；跨旧候选、XML/Vue读取、未命中、同源检查及零扫描已有直接验证。本次在任务输入层接通项目说明/调查背景、读取用途与选择结果，不增业务解析器或存储框架，上游格式不升版。详细改动见[实施状态](../../supplements/cross-object-process-reconstruction/implementation-status.md)。
