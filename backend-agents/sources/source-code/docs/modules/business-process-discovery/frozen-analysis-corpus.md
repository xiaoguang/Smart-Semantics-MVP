# FrozenAnalysisCorpus

## 为什么存在

业务发现需要同时复用三类已完成成果：全仓 ReviewedActivity、JDT 导航/完整源码、SourceRef。它们来自不同 checkpoint，但必须属于同一冻结源码。该模块把身份和读取复杂度藏在一个只读查询面后，防止下游重新扫描或直接拼宿主路径。

## Interface

目标内部 Interface：

```java
interface FrozenAnalysisCorpus extends AutoCloseable {
    CorpusOverview overview();
    List<ReviewedActivity> activities(List<ActivityId> ids);
    List<SourceExcerpt> sources(List<SourceRefId> refs);
    List<CodeMethod> methods(List<MethodKey> keys);
}
```

调用约束：先打开并完成一次整体身份核验；查询仅接受 typed ID；返回不可变值；`close` 后查询失败。它不暴露任意 `Path`，不提供搜索中文业务词的方法。

## 程序工作

- 验证 Activity、材料、JDT index、冻结 snapshot 和 SourceRef 的来源一致。
- 建立 ActivityId、SourceRefId、MethodKey 的只读索引。
- 为 Activity 数组字段生成确定性语句 handle，例如 `activity:<id>/conditions/2`。
- 为 SUPPORT/STANDALONE/UNCLASSIFIED Activity 确定性投影对象、字段/维度、对象关系、公式/指标和问题正文，连同 Activity owner、grouping disposition、statement refs 和 source refs 交给 Discovery result；不通过模型重写，也不创建假过程。
- 对请求的 ref 返回文件、行段和原文；共享方法正文只存一份。
- 保留 JDT 未解析边界和既有覆盖，不把缺失伪装为空结果。

## 模型工作

无。身份、路径、hash 和检索控制都不发送给模型。上层只把选中的业务内容、短 ref 和片段放入任务。

## 失败

缺 checkpoint、来源不一致、损坏 JSON、未知 Activity/ref/method、源码行段漂移均为明确失败。失败不能触发 JDT、JavaParser、Builder 或 ActivityExplainer。

## 下游保证

Cataloger 能一次看到完整 Activity 分母；Assembler 可以按候选 ID 取回完整内容；Reconstructor 请求的源码一定来自同一冻结 corpus；Discovery 可以把未进候选的 Activity 已有知识完整带到封闭结果，Publisher 无需再次打开 corpus。

## 测试与当前成熟度

接口测试覆盖同源重开、未知 ID、错 snapshot、重复 ref、跨 batch 材料归属和零扫描计数。现有各 checkpoint reader 可复用，但统一 corpus Interface 尚未实现。
