# Source Code Analysis Agent

本 Agent 从固定源码收集技术证据。当前技术出口为按入口组织的R4 JSON；新设计的Ontology分支直接识别对象、联系、操作/规则、维度与指标，不经过旧Activity／业务过程。既有业务过程分支和历史结果独立保留。技术取材只走JDT；Step08不恢复新生成。

先读[总体设计](docs/DESIGN.md)，新分支沿[本体证据走查](docs/modules/ontology-recognition/evidence-walkthrough.md)与[实施计划](docs/plans/ontology-recognition-implementation-plan.md)阅读；旧路线另见[Activity/过程推演](docs/examples/activity-material-end-to-end-walkthrough.md)。业务术语见[CONTEXT](CONTEXT.md)。

当前技术生产路径为四个独立操作：`collect-frontend`、`collect-code`（02＋03）、`analyze-persistence`（04）、`assemble-materials`（05）。最终R4保存339份按后端入口组织的完整JSON、入口目录及51条已发现前端请求处置，正式保存、重开和查询已通过；旧 `plan-materials` 已退役。51条请求仍因运行时地址未确认而为候选，不能称为已证明部署连通。具名嵌套/Mapper/logger等问题经过复核，但全量逐边准确性和长等待根因未证明。见[技术材料采集](docs/modules/technical-analysis/README.md)、[最终入口证据](docs/modules/technical-analysis/entry-evidence.md)和[最终验收](docs/supplements/technical-entry-evidence-acceptance-20260929.md)。

[本体识别](docs/modules/ontology-recognition/README.md)是独立分支：只接受版本明确的R4及同R0可选原文/DDL；不兼容旧M10、Packet、Activity或业务过程输入，不迁移旧模型稿件。四个正式操作、typed识别/审阅、确定性发布及查询已经接通，并通过限定直接工程回归；旧MD逻辑保持原状。**十步计划工程完成0–8，最终静态质量已通过，三个真实模型样例尚未执行**，不把scripted测试称为客户本体质量通过。新前端/R4修复复用原后端和持久化结果，339份入口文件、91请求和14页面上下文已经正式保存、查询；新O0返回15,404个内容单元。实际范围见[当前验收记录](docs/supplements/ontology-formal-acceptance-20261003.md)。

当前外部交接是：**Maven输出依赖路径和已求值项目配置；Agent只交文件位置及明确选择；Java从输出提取并绑定环境，再交JDT。** 不由Agent手填源码根/编译级别/模块边，不在Java重做Maven求值或下载。v2配置及提取已接入正式CLI，固定源码运行使用106个可读JAR；具体合同见[依赖交接](docs/modules/technical-analysis/dependency-preparation.md)。Skill约定面向任意Agent运行器，不依赖Codex临时补全。本轮止于第五步，没有重跑业务模型。

前后端合并的旧三命令/47包及当时失败见[历史技术验收](docs/supplements/technical-analysis-fixed-source-acceptance.md)，不作为当前R4状态。

源码接入改造的[详细设计](docs/analysis-steps/01-verified-source-inventory.md)和[实施计划](docs/plans/source-preparation-implementation-plan.md)已落地到正式 `prepare-source` 命令：普通目录/固定 Git 提交可逐项读取，四文件可以保存和严格重开，用户可以基于具名旧版本刷新或排除范围。公共只读源码视图只返回有效、未排除的文件；真实发布/重开fixture证明排除的Java/XML不会进入Java项目及持久化请求/视图。新执行在读取旧 Step05/M10 材料前核对绑定来源与实际 Step01 身份。旧材料没有准备版来源及有效排除的材料级证明，因此选择 `PREPARED_SOURCE` 时明确拒绝复用；本轮未实现从准备版来源直接生产新Step05材料，也不声称支持跨版本增量复用。最终定向168/168及质量检查通过；资源上限停止枚举后的恢复只允许NEW全范围，计划按本轮范围完成。未运行真实客户JDT或业务模型。

## 当前能力与待实现接点

| 范围 | 当前事实 |
| --- | --- |
| Step01–05 | prepare-source＋四技术操作已接通；最终R4为339份入口证据、51条前端请求处置；旧Packet/技术结果仍可读。限制见最终验收，不以具名复核证明全量每条边正确 |
| Ontology识别 | 四操作及正式Agent/store/query接线通过限定直接回归；新旧R4→O0保存/查询和最终静态质量通过。三个真实样例待验，产品模型调用0；不宣称自主选材或全仓覆盖 |
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
| 07 | 独立Ontology操作族已有工程接线，真实样例待验；与既有业务过程分支不共用内容合同 | [仓库语义](docs/analysis-steps/07-repository-knowledge.md) |
| 08 | 历史九章读取、查询与确定性重渲染 | [历史九章](docs/analysis-steps/08-nine-section-document.md) |

## 按职责继续阅读

- [本体识别设计](docs/modules/ontology-recognition/README.md)与[实施计划](docs/plans/ontology-recognition-implementation-plan.md)：新证据分支、实现差距、薄PoC和正式化门禁。
- [Activity深Module](docs/modules/activity-explanation/README.md)：投影、模型阅读、语义范围、覆盖与接入。
- [业务过程Modules](docs/modules/business-process-discovery/README.md)：发现与发布两个内部Interface。
- [模型执行](docs/modules/model-job-execution.md)：并发、认证、阶段尝试、失败保存与显式复用。
- [中文Prompt合同](docs/references/semantic-interpretation-prompts.md)。
- [Java/JDT Modules](docs/modules/java-code-engines/README.md)。
- [来源与发布合同](docs/references/foundation-and-publication-contracts.md)、[公共Interface](docs/references/inherited-public-and-module-contracts.md)。
- [补充文档与历史验收索引](docs/supplements/cross-object-process-reconstruction/README.md)。

业务生成默认使用已登录ChatGPT的Codex上下文，Luna/high。方向判断、调度、设计文档和调试使用 GPT-6 Sol/xhigh，测试使用 Luna/xhigh，生产代码使用 Terra/xhigh。历史模型身份和结果不改写。
