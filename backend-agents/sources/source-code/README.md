# Source Code Analysis Agent

本 Agent 从冻结 Java/Spring/MyBatis 源码解释局部业务活动，再发现、重建仓库业务过程。正式业务出口为 `business-processes.md`。技术取材只走 JDT；Step08 仅保留历史结果查询、读取和确定性重渲染。

先读[总体设计](docs/DESIGN.md)，再沿[真实材料推演](docs/examples/activity-material-end-to-end-walkthrough.md)核对“源码→阅读材料→Activity→Business Process”。业务术语见[CONTEXT](CONTEXT.md)。

## 当前能力与待实现接点

| 范围 | 当前事实 |
| --- | --- |
| Step01–05 | JDT 导航、可选 MyBatis/JSqlParser 持久化补全、统一阅读材料及保存/重开已完成；固定仓库产生325包、326入口处置，其中1项导航失败 |
| Step06 历史路径 | ActivityExplainer 使用旧 M10 BusinessMaterialBuildResult；326条旧 ReviewedActivity 保持原样 |
| Step05→Step06 新路径 | **详细设计完成，尚未实现**：直接投影新材料、小包完整解释、大包模型阅读/语义分解、分阶段重试与人工新批次重试 |
| Step07 | 已实现系统认识/选材、每候选一次阅读检查、事实DRAFT→WRITE→最终RULE_REVIEW、私有三阶段保存、producer v4五文件发布 |
| 真实业务质量 | 历史采购/销售/调拨三例没有全部通过；可读性获认可，条件、引用和容量问题保留 |
| Step08 | 历史reader/query/renderer；本轮无新九章生成目标 |

新材料目前只支持 `READING_MATERIALS_ONLY`。不可把旧 `plan-materials → flow-interpretation` 命令链描述成已接入新Step05。当前CLI见[运行说明](tools/repository-run/README.md)，新接入配置见[目标集成合同](docs/modules/activity-explanation/integration-contracts.md)。

## 八步与详细设计

| 步骤 | 职责 | 设计 |
| --- | --- | --- |
| 01 | 核验冻结文件与来源 | [源码清单](docs/analysis-steps/01-verified-source-inventory.md) |
| 02 | 静态应用发现、全部Spring入口 | [应用发现](docs/analysis-steps/02-application-discovery.md) |
| 03 | JDT导航、完整方法、调用与候选 | [程序导航](docs/analysis-steps/03-program-graphs.md) |
| 04 | 可选MyBatis/XML/SQL持久化材料 | [持久化补全](docs/analysis-steps/04-proven-code-facts.md) |
| 05 | 唯一入口阅读材料owner | [阅读材料](docs/analysis-steps/05-business-flows.md) |
| 06 | 新材料上的局部Activity解释与审阅 | [Activity](docs/analysis-steps/06-flow-interpretation.md) |
| 07 | 目录、完整阅读、详细过程、归并、发布 | [Business Process](docs/analysis-steps/07-repository-knowledge.md) |
| 08 | 历史九章读取、查询与确定性重渲染 | [历史九章](docs/analysis-steps/08-nine-section-document.md) |

## 按职责继续阅读

- [Activity深Module](docs/modules/activity-explanation/README.md)：投影、模型阅读、语义范围、覆盖与接入。
- [业务过程Modules](docs/modules/business-process-discovery/README.md)：发现与发布两个内部Interface。
- [模型执行](docs/modules/model-job-execution.md)：并发、认证、阶段尝试、失败保存与显式复用。
- [中文Prompt合同](docs/references/semantic-interpretation-prompts.md)。
- [Java/JDT Modules](docs/modules/java-code-engines/README.md)。
- [来源与发布合同](docs/references/foundation-and-publication-contracts.md)、[公共Interface](docs/references/inherited-public-and-module-contracts.md)。
- [补充文档与历史验收索引](docs/supplements/cross-object-process-reconstruction/README.md)。

业务生成默认使用已登录ChatGPT的Codex上下文，gpt-5.6-terra/xhigh。设计与调试为Astra/ultra，测试为Terra/xhigh，生产代码为Sol/xhigh。历史模型身份和结果不改写。
