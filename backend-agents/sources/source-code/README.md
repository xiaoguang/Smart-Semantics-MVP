# Source Code Analysis Agent

本 Agent 把冻结的 Java/Spring 源码整理为仓库业务过程：先由 JDT/JavaParser 找到代码，再由模型理解局部活动、发现全仓业务并重建过程，最后输出 `business-processes.md` 和一份下游九章概览。

先读：

- [总体设计](docs/DESIGN.md)
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
| 08 | 从已归并过程目录生成固定九章概览 | [九章文档](docs/analysis-steps/08-nine-section-document.md) |

## 关键职责

- Java/JDT：定义、实现、调用、完整源码、来源、ID、覆盖、调度和保存。
- Luna/high：业务领域发现、Activity 归属、过程阶段和分支、业务语言及不确定性。
- Java 不硬编码销售、采购等领域词；模型不创建源码位置或声称某次运行成功。
- 精确证据只用于定位和核对；五图与 Fact 是技术增强，不是业务阅读门禁。
- Activity 是局部活动，Business Process 是多活动端到端过程，两者不是同一个概念。

## 当前实现与目标差距

当前 main 已有完整取材链、326 个 jshERP ReviewedActivity、并行模型任务、模型批次复用和九章渲染。JDT 材料与 Activity 都应复用，不需要重扫或全量重跑。

当前Step07新路线已经实现：全仓目录、候选完整阅读、按需来源、详细过程、归并与发布。真实输入为326条已审Activity；14候选生成46个过程，21个含多Activity。覆盖CLOSED，50条未分类，语义PARTIAL。旧340个singleton结果是历史前身，不是当前代码状态。

但本轮复核发现：目录偏向“某对象维护”，阶段仍偏“接收/校验/执行/返回”，没有达到所需采购/销售等生命周期叙述。多阶段和覆盖指标不代表业务质量通过。

本次设计仅修正Step07：
- 全仓卡片保留原规则，发现通用Activity不同业务用法。
- 同一Activity的不同variant分别使用；完整内容去重但不混用条件。
- 模型生成并完整审阅阶段业务正文和规则适用范围。
- 来源目录提供原文预览；发布business-processes.md及可点击的sources.md，继续保存JSONL。
- 不重扫、不重跑Activity，不改九章。新增合同尚未实施。

下一份实施计划以[本次差异清单](docs/plans/business-process-discovery-and-reconstruction-change-design.md)为准，不重开旧Step07建设计划。

## 其他文档

- [Java 代码引擎](docs/modules/java-code-engines/README.md)
- [模型 job、并发和批次复用](docs/modules/model-job-execution.md)
- [来源、发布与信任](docs/references/foundation-and-publication-contracts.md)
- [公共 Interface 与 SourceRef](docs/references/inherited-public-and-module-contracts.md)
- [程序图稳定能力与 backlog](docs/supplements/program-graphs-implementation-backlog.md)

历史计划不是新的 Step07 实施依据。各 Agent 的 progress 在所属计划执行期间保留以便交接；整个计划结束时，将重要决定、遗留事项和验收结果收纳到正式文档，再删除该计划的临时 progress，不另建归档目录。已经提交的记录仍可从 Git 历史查阅。后续计划只从总体设计与本轮变更清单生成。
