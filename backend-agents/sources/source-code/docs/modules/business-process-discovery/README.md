# 业务过程发现与重建：模块总览

> 当前模块已实现并真实发布46个过程，但订单生命周期、业务语言和来源导航未达用户目标。本次是局部修正设计，尚未实施；详见[变更清单](../../plans/business-process-discovery-and-reconstruction-change-design.md)。

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
| [FrozenAnalysisCorpus](frozen-analysis-corpus.md) | Activity/M10 SourceRef checkpoints | 可按 ID 查询的只读 corpus；未进候选 Activity 的确定性知识投影 | 否 |
| [RepositoryBusinessCataloger](repository-business-cataloger.md) | 全仓 ActivityIndexCard | 业务领域、重叠候选、Activity 处置草案 | 是，DRAFT+REVIEW |
| [ProcessMaterialAssembler](process-material-assembler.md) | 某候选及 corpus | 完整 Activity、语句 handle、源码目录 | 否 |
| [CandidateProcessReconstructor](candidate-process-reconstructor.md) | 候选完整材料 | 已审详细过程或明确拒绝/拆分 | 是，每候选 DRAFT+REVIEW |
| [RepositoryProcessConsolidator](repository-process-consolidator.md) | 全部已审候选过程 | 唯一仓库过程目录及关系 | 是，一次 DRAFT+REVIEW |
| [BusinessProcessPublisher](business-process-publisher.md) | 封闭的 ProcessDiscoveryResult | catalog、coverage、业务正文、来源JSONL及sources.md | 否 |

模型任务继续复用现有两级并发和逐 job 保存。目录合并和仓库归并是屏障任务；候选重建 job 可以并行。一次候选 job 内 DRAFT 在前、REVIEW 在后。

## 4. 关键不变量

- Java 不包含行业业务词典，不决定销售或采购顺序。
- 全仓目录可以压缩；候选重建必须重新打开完整 Activity。
- JDT/源码只从已保存 corpus 读取，不能因模型失败重新扫描。
- 每条重要规则保留具体条件与结果，不能缩成“状态允许”或“满足条件”。
- 过程目录保存选中的对象、字段/维度、关系、公式/指标和问题正文及 refs；只保留指针会让九章丢内容。
- 未进入候选重建的 SUPPORT/STANDALONE/UNCLASSIFIED Activity 仍由 Discovery 确定性投影原有知识项；Publisher 不回读 Activity，也不为其制造过程。
- 一个Activity可有多个variant并参加同一或不同候选；正文去重，用法不去重。规则activityUseIds限定适用分支，阶段narrative提供完整业务叙述。
- 来源预览帮助选择已保存片段；正文通过来源索引跳到独立sources.md，不新增证据链。
- 查询、统计、配置等活动可作为支撑，不强行排进主时序。
- 所有 Activity 和候选都有最终处置；覆盖完整不等于业务事实全知。
- `business-processes.md` 是本模块簇的主要人工验收面；九章是下游展示。

## 5. 当前实现状态

`DefaultBusinessProcessDiscovery` 已实现统一 corpus、目录分片/合并、重叠候选、完整材料重建、源码按需核对和仓库归并；`CanonicalBusinessProcessPublisher` 已实现四项正式产物与 fresh reopen。过程专用运行入口从既有 Activity/M10 checkpoint 启动新批次，不运行 JDT、Builder、ActivityExplainer 或 Step08。旧 `ProcessExplainer` 不在这条新路径中。

固定 326 Activity 的真实运行已发布 46 个多阶段过程，其中 21 个包含多个 Activity；102 个 Activity 成为过程成员，157 个作为支撑，17 个独立保留，50 个未归类。最终正式批次从完整已审 job 零调用复用，未运行 JDT、Builder、ActivityExplainer 或 Step08。结构门已通过，但真实过程仍偏技术模板、目录仍偏维护分类；没有通过用户所需生命周期语义验收。新增narrative、规则用法、来源预览/链接和v2五文件合同尚待实施。PARTIAL及未归类范围保留，不能用重新排版把旧结果称为质量通过。
