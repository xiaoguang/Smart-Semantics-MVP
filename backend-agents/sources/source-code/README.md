# Source Code Analysis Agent

本 Agent 从冻结 Java/Spring/MyBatis 源码解释局部业务活动，再发现、重建仓库业务过程。正式业务出口为 `business-processes.md`。技术取材只走 JDT；Step08 仅保留历史结果查询、读取和确定性重渲染。

先读[总体设计](docs/DESIGN.md)，再沿[真实材料推演](docs/examples/activity-material-end-to-end-walkthrough.md)核对“源码→阅读材料→Activity→Business Process”。业务术语见[CONTEXT](CONTEXT.md)。

当前实现与前五步目标见[技术材料采集](docs/modules/technical-analysis/README.md)、[实施清单](docs/plans/technical-analysis-cli-and-vue-cleanup-design.md)及[真实Vue→SQL例子](docs/supplements/vue-to-sql-walkthrough.md)。当前三个技术操作是 `collect-code`（02＋03）、`analyze-persistence`（04）、`assemble-materials`（05）；旧 `plan-materials` 已退役。固定源码已通过正式三个命令保存R1、R2、R3：339个入口中332个收集完成，7个查询失败；前端51条HTTP请求、持久化573条statement和47个最终阅读包可重开。采购选择请购单的Vue→Controller→Mapper XML→部分静态SQL技术链已取得，但动态baseURL仍未确认。新索引中的嵌套调用仍有错误候选，不能宣称Java导航准确性验收通过。

2026-09-29 已确认的新设计将前端独立为 `collect-frontend`，形成四个操作；组装时匹配前后端，按每个后端入口保存自包含JSON、入口目录及前端覆盖。它尚未实施，不把下面旧47包视为新输出。详见[最终入口证据](docs/modules/technical-analysis/entry-evidence.md)；本次只更新详细设计，不运行分析。

当前外部交接是：**Maven输出依赖路径和已求值项目配置；Agent只交文件位置及明确选择；Java从输出提取并绑定环境，再交JDT。** 不由Agent手填源码根/编译级别/模块边，不在Java重做Maven求值或下载。v2配置及提取已接入正式CLI，固定源码运行使用106个可读JAR；具体合同见[依赖交接](docs/modules/technical-analysis/dependency-preparation.md)。Skill约定面向任意Agent运行器，不依赖Codex临时补全。本轮止于第五步，没有重跑业务模型。

固定源码的实际覆盖、页面到SQL链和未通过的调用准确性见[技术验收结果](docs/supplements/technical-analysis-fixed-source-acceptance.md)。

源码接入改造的[详细设计](docs/analysis-steps/01-verified-source-inventory.md)和[实施计划](docs/plans/source-preparation-implementation-plan.md)已落地到正式 `prepare-source` 命令：普通目录/固定 Git 提交可逐项读取，四文件可以保存和严格重开，用户可以基于具名旧版本刷新或排除范围。公共只读源码视图只返回有效、未排除的文件；真实发布/重开fixture证明排除的Java/XML不会进入Java项目及持久化请求/视图。新执行在读取旧 Step05/M10 材料前核对绑定来源与实际 Step01 身份。旧材料没有准备版来源及有效排除的材料级证明，因此选择 `PREPARED_SOURCE` 时明确拒绝复用；本轮未实现从准备版来源直接生产新Step05材料，也不声称支持跨版本增量复用。最终定向168/168及质量检查通过；资源上限停止枚举后的恢复只允许NEW全范围，计划按本轮范围完成。未运行真实客户JDT或业务模型。

## 当前能力与待实现接点

| 范围 | 当前事实 |
| --- | --- |
| Step01–05 | 三个独立技术命令及保存/重开已接通；旧批次为325包、326入口处置，新固定源码R1/R2/R3为332/339入口收集、573条XML statement、47个阅读包。7个JDT定义查询失败及已确认的错误候选使准确性验收尚未通过 |
| Step06 历史路径 | ActivityExplainer 使用旧 M10 BusinessMaterialBuildResult；326条旧 ReviewedActivity 保持原样 |
| Step05→Step06 新路径 | 已实现直接投影、小包/大包阅读、分阶段保存重试及全量 Activity 运行；范围完整性与历史接续仍有待验收项，不能把某个切片成功等同于整个入口完成 |
| Step07 | 已实现系统认识/选材、每候选一次阅读检查、事实DRAFT→WRITE→最终RULE_REVIEW、私有三阶段保存、producer v4五文件发布 |
| 真实业务质量 | 历史采购/销售/调拨三例没有全部通过；可读性获认可，条件、引用和容量问题保留 |
| Step08 | 历史reader/query/renderer；本轮无新九章生成目标 |

新材料和新版 Activity 已有保存结果；正式端到端业务过程交付仍受范围完整性和来源绑定验收约束。不可用已退役旧材料路线冒充新 Step05 接力。当前CLI见[运行说明](tools/repository-run/README.md)，新接入配置见[集成合同](docs/modules/activity-explanation/integration-contracts.md)。

## 八步与详细设计

| 步骤 | 职责 | 设计 |
| --- | --- | --- |
| 源码准备（内部01） | 目录/Git逐项读取、四文件保存、刷新/排除、独立CLI及旧材料来源门禁已实现；Java/Persistence排除消费者fixture通过；最终定向168/168和质量通过，资源上限NEW恢复边界已复验 | [源码准备](docs/analysis-steps/01-verified-source-inventory.md) |
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

业务生成默认使用已登录ChatGPT的Codex上下文，Luna/high。方向判断、调度、设计文档和调试使用 GPT-6 Sol/xhigh，测试使用 Luna/xhigh，生产代码使用 Terra/xhigh。历史模型身份和结果不改写。
