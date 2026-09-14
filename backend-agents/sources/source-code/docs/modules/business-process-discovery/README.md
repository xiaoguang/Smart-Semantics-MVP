# 业务过程发现与重建：模块总览

> 目标设计，尚未由当前 Java 实现。当前实现状态和替换范围见[变更清单](../../plans/business-process-discovery-and-reconstruction-change-design.md)。

## 1. 模块目的

本模块簇把已经完成审阅的局部 Activity 和已保存 JDT/源码，转成仓库级业务过程。它解决两个不同问题：

1. 在全仓范围发现哪些 Activity 值得一起深入阅读。
2. 在候选范围内恢复完整条件、动作、状态变化、分支、结果和来源。

目录发现不能只读局部组；详细重建不能只读摘要卡。两者分开，才既能看见全仓，又不会把 326 份完整源码一次塞入一个请求。

## 2. 两个深 Module Interface

```java
public interface BusinessProcessDiscovery {
    ProcessDiscoveryResult discover(ProcessDiscoveryRequest request);
}

public interface BusinessProcessPublisher {
    BusinessProcessPublication publish(ProcessDiscoveryResult result);
}
```

这是 Step07 内部 Interface，不增加 `RepositoryAnalysisAgent` 的公开方法。调用方只需要提供已验证 corpus、模型执行配置和输出归属；目录分片、源码按需回查、候选重建和归并都隐藏在第一个 Module 内。

## 3. 内部模块与接力

| 内部模块 | 输入 | 输出 | 是否调用模型 |
| --- | --- | --- | --- |
| [FrozenAnalysisCorpus](frozen-analysis-corpus.md) | Activity/JDT/source/ref checkpoints | 可按 ID 查询的只读 corpus；未进候选 Activity 的确定性知识投影 | 否 |
| [RepositoryBusinessCataloger](repository-business-cataloger.md) | 全仓 ActivityIndexCard | 业务领域、重叠候选、Activity 处置草案 | 是，DRAFT+REVIEW |
| [ProcessMaterialAssembler](process-material-assembler.md) | 某候选及 corpus | 完整 Activity、语句 handle、源码目录 | 否 |
| [CandidateProcessReconstructor](candidate-process-reconstructor.md) | 候选完整材料 | 已审详细过程或明确拒绝/拆分 | 是，每候选 DRAFT+REVIEW |
| [RepositoryProcessConsolidator](repository-process-consolidator.md) | 全部已审候选过程 | 唯一仓库过程目录及关系 | 是，一次 DRAFT+REVIEW |
| [BusinessProcessPublisher](business-process-publisher.md) | 封闭的 ProcessDiscoveryResult | JSON、coverage、主业务 Markdown | 否 |

模型任务继续复用现有两级并发和逐 job 保存。目录合并和仓库归并是屏障任务；候选重建 job 可以并行。一次候选 job 内 DRAFT 在前、REVIEW 在后。

## 4. 关键不变量

- Java 不包含行业业务词典，不决定销售或采购顺序。
- 全仓目录可以压缩；候选重建必须重新打开完整 Activity。
- JDT/源码只从已保存 corpus 读取，不能因模型失败重新扫描。
- 每条重要规则保留具体条件与结果，不能缩成“状态允许”或“满足条件”。
- 过程目录保存选中的对象、字段/维度、关系、公式/指标和问题正文及 refs；只保留指针会让九章丢内容。
- 未进入候选重建的 SUPPORT/STANDALONE/UNCLASSIFIED Activity 仍由 Discovery 确定性投影原有知识项；Publisher 不回读 Activity，也不为其制造过程。
- 一个 Activity 可产生多个 ActivityUse，并参与多个过程。
- 查询、统计、配置等活动可作为支撑，不强行排进主时序。
- 所有 Activity 和候选都有最终处置；覆盖完整不等于业务事实全知。
- `business-processes.md` 是本模块簇的主要人工验收面；九章是下游展示。

## 5. 当前实现差距

当前代码只有 `ProcessExplainer`：精确 token/file 连边、连通分量切片、每组 DRAFT/REVIEW、可选仓库摘要。它没有全仓业务目录、ActivityUse、语句 handle、源码按需 REVIEW、详细阶段或过程归并。目前 326 个 Activity 的实际输出形成 340 个单 Activity/单 Stage Process，且一个 Activity 没有出现在过程或 unmatched 列表中。这一模块簇尚未实现。
