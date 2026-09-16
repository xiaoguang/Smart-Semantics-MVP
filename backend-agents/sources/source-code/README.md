# Source Code Analysis Agent

本 Agent 把冻结的 Java/Spring 源码整理为仓库业务过程：先由 JDT/JavaParser 找到代码，再由模型理解局部活动、发现全仓业务并重建过程，当前正式出口为仓库级 `business-processes.md`。Step08 九章仍是后续能力，不在当前生产入口中生成。

先读：

- [总体设计](docs/DESIGN.md)
- [跨对象业务过程补充设计与验收状态](docs/supplements/cross-object-process-reconstruction/README.md)
- [本轮业务过程发现与重建设计变更](docs/plans/business-process-discovery-and-reconstruction-change-design.md)
- [业务过程模块总览](docs/modules/business-process-discovery/README.md)
- [从代码到业务过程的完整示例](docs/examples/semantic-framework-walkthrough.md)
- [中文模型提示词](docs/references/semantic-interpretation-prompts.md)

## 八步路线

| 步骤 | 做什么 | 详细设计 |
| --- | --- | --- |
| 01 | 固定并读取源码 | [已验证源码清单](docs/analysis-steps/01-verified-source-inventory.md) |
| 02 | 识别应用能力和全部 Spring 入口 | [应用发现](docs/analysis-steps/02-application-discovery.md) |
| 03 | 用选定 Java 引擎建立导航索引和可选程序图 | [程序图](docs/analysis-steps/03-program-graphs.md) |
| 04 | 保留可用的严格技术 Fact/Proof | [已证明代码事实](docs/analysis-steps/04-proven-code-facts.md) |
| 05 | 围绕每个入口保存连贯代码上下文 | [业务流程材料](docs/analysis-steps/05-business-flows.md) |
| 06 | 生成并审阅局部 Activity | [局部活动解释](docs/analysis-steps/06-flow-interpretation.md) |
| 07 | 发现、重建并发布仓库 Business Process | [仓库业务过程](docs/analysis-steps/07-repository-knowledge.md) |
| 08 | 未来从已归并过程目录生成固定九章概览；当前仅保留历史结果读取与确定性重渲染 | [九章文档](docs/analysis-steps/08-nine-section-document.md) |

## 关键职责

- Java/JDT：定义、实现、调用、完整源码、来源、ID、覆盖、调度和保存。
- Luna/high：业务领域发现、Activity 归属、过程阶段和分支、业务语言及不确定性。
- Java 不硬编码销售、采购等领域词；模型不创建源码位置或声称某次运行成功。
- 精确证据只用于定位和核对；五图与 Fact 是技术增强，不是业务阅读门禁。
- Activity 是局部活动，Business Process 是多活动端到端过程，两者不是同一个概念。

## 当前实现

当前生产链已经具备完整取材、并行 Activity 解释、模型批次复用，以及 Step07 的全仓目录、候选完整阅读、详细过程重建、归并和五文件发布。已经保存的 326 条 jshERP ReviewedActivity 和对应 JDT 材料属于可复用运行检查点，不需要因入口清理而重新扫描或重新调用模型。

生产命令统一由 `source-analysis` 提供，详见[运行说明](tools/repository-run/README.md)。当前业务生成路线是：

```text
plan-materials
→ execute-step --target flow-interpretation
→ execute-step --target repository-knowledge
→ business-processes.md
```

旧的 singleton ProcessExplainer、旧九章模型生成器和 `RepositoryRunMain` 已退出生产代码。历史九章检查点仍可严格读取并确定性重渲染；这不表示当前入口会调用模型生成新九章。

Step07 已实现多用法、业务正文、规则范围和独立sources.md。新[跨对象设计](docs/supplements/cross-object-process-reconstruction/README.md)复用旧目录和326条Activity，已接通跨旧候选选材、冻结文件读取、每候选一次阅读检查及DRAFT前完整原文；直接测试通过，不重跑Activity。完整验收以[交付记录](docs/supplements/cross-object-process-reconstruction/delivery.md)为准。[更多发现](docs/supplements/more-findings.md)保留原文，未纳入增强仍待讨论。覆盖/多阶段数量不能替代语义质量。

## 其他文档

- [Java 代码引擎](docs/modules/java-code-engines/README.md)
- [模型 job、并发和批次复用](docs/modules/model-job-execution.md)
- [来源、发布与信任](docs/references/foundation-and-publication-contracts.md)
- [公共 Interface 与 SourceRef](docs/references/inherited-public-and-module-contracts.md)
- [程序图稳定能力与 backlog](docs/supplements/program-graphs-implementation-backlog.md)

历史计划不是新的 Step07 实施依据。各 Agent 的 progress 在所属计划执行期间保留以便交接；整个计划结束时，将重要决定、遗留事项和验收结果收纳到正式文档，再删除该计划的临时 progress，不另建归档目录。已经提交的记录仍可从 Git 历史查阅。后续计划只从总体设计与本轮变更清单生成。
