# 业务过程发现与重建：模块总览

> 当前完整Activity、narrative、规则用法、五文件发布、跨旧候选阅读与DRAFT前原文取材继续复用。本次[系统认识、聚焦选材与三阶段成稿](../../supplements/cross-object-process-reconstruction/business-reasoning-and-writing.md)仍是目标，尚未生产接线；当前差异见[实施状态](../../supplements/cross-object-process-reconstruction/implementation-status.md)，历史验证保留在[交付记录](../../supplements/cross-object-process-reconstruction/delivery.md)。

## 1. 模块目的

本模块簇把已经完成审阅的局部 Activity 和已保存 JDT/源码，转成仓库级业务过程。它解决两个不同问题：

1. 在全仓范围发现哪些 Activity 值得一起深入阅读。
2. 跨旧候选选择完整活动和同源原文，再解释条件、动作、状态变化、分支与结果。

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
| [FrozenAnalysisCorpus](frozen-analysis-corpus.md) | Activity/M10/同源冻结文本 | ID、文件、字面量只读查询；未进候选的既有知识 | 否 |
| [RepositoryBusinessCataloger](repository-business-cataloger.md) | 冻结项目说明、旧/首次目录、全仓导航和可选问题 | 系统判断、可证伪假设、问题候选与首批清单 | 系统认识并入一次全局选材；首次目录另算 |
| [ProcessMaterialAssembler](process-material-assembler.md) | 选材清单、corpus及CHECK最终保留/移出/补读决定 | DRAFT前完整阅读包 | 读取零模型；每候选配合一次模型阅读检查 |
| [CandidateProcessReconstructor](candidate-process-reconstructor.md) | 候选完整材料和调查问题 | 已最终核对的详细过程或明确不足/拆分 | 目标每候选 DRAFT→WRITE→RULE_REVIEW |
| [RepositoryProcessConsolidator](repository-process-consolidator.md) | 全部已审候选过程 | 唯一仓库过程目录及关系 | 是，一次 DRAFT+REVIEW |
| [BusinessProcessPublisher](business-process-publisher.md) | 封闭的 ProcessDiscoveryResult | catalog、coverage、业务正文、来源JSONL及sources.md | 否 |

模型任务复用现有两级并发和保存。全局选材后各候选阅读检查可并行，再重建；同候选三阶段在一个Callable内依次执行、保持同一绑定。WRITE只读完整实际事实草稿；最终RULE_REVIEW接收完整包、实际DRAFT及实际WRITE，直接修正正文和结构。单次阅读决定独立保存，新过程三阶段也不伪装为旧pair。补读可空，次数用完不表示语义充分。

## 4. 关键不变量

- Java 不包含行业业务词典，不决定销售或采购顺序。
- 全仓目录可以压缩；候选重建必须重新打开完整 Activity。
- JDT/源码只从已保存 corpus 读取，不能因模型失败重新扫描。
- 每条重要规则保留具体条件与结果，不能缩成“状态允许”或“满足条件”。
- 过程目录保存选中的对象、字段/维度、关系、公式/指标和问题正文及 refs；只保留指针会让九章丢内容。
- 未进入候选重建的 SUPPORT/STANDALONE/UNCLASSIFIED Activity 仍由 Discovery 确定性投影原有知识项；Publisher 不回读 Activity，也不为其制造过程。
- 一个Activity可有多个variant并参加同一或不同候选；正文去重，用法不去重。规则activityUseIds限定适用分支，阶段narrative提供完整业务叙述。
- 首批实际材料经模型阅读检查明确保留/移出后，补读最多一次。DRAFT和最终RULE_REVIEW共享完整包；WRITE不重新读源码，最终核对后不再模型润色。来源不受旧成员M10限制，不新增证据链。
- 未涉及Activity继承处置；只读context不必成为过程成员，增量成员变化仍使用现有覆盖记录。
- 查询、统计、配置等活动可作为支撑，不强行排进主时序。
- 所有 Activity 和候选都有最终处置；覆盖完整不等于业务事实全知。
- `business-processes.md` 是本模块簇的主要人工验收面；九章是下游展示。

## 5. 当前实现状态

`DefaultBusinessProcessDiscovery` 已实现统一 corpus、目录分片/合并、重叠候选、完整材料重建、源码按需核对和仓库归并；`CanonicalBusinessProcessPublisher` 已实现五项正式产物与 fresh reopen。过程专用运行入口从既有 Activity/M10 checkpoint 启动新批次，不运行 JDT、Builder、ActivityExplainer 或 Step08。旧 `ProcessExplainer` 不在这条新路径中。

46过程是历史v1对照；后续保存目录为24候选、138个不同成员Activity，全部326Activity可用。当前生产仍在DRAFT前封闭完整包后执行DRAFT/REVIEW，不重跑Activity或原目录模型。系统判断、问题收敛、三阶段及新私有保存尚待实现。本轮只生成三个样本的独立预览与准确性记录，不调用全仓归并或关闭全仓覆盖；审阅后再讨论扩大范围。
