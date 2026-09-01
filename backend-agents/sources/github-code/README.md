# Source Code Analysis Agent

本 Agent 的目标是把一份**完整冻结的** Java/Spring MVC/MyBatis 仓库快照，整理成可信、可追溯、明确写出未知项的**一份仓库级**九章 Markdown 候选。程序先盘点全入口、构建五张程序图、证明 Fact，再为每个支持入口编译独立 Flow/EvidenceCapsule；流程解释为每个 Flow 提出有证据 basis 的仓库特定词，程序冻结唯一 `RepositoryInterpretationRegistry` 后再完成有限键 R1/R2 解释；最后把全部 Flow 准入并合成唯一 `RepositoryKnowledge`，只生成一份 `NineSectionPlan`、`document.md`、Trace 和完整分析运行归档。

DepotHead 八文件只是贯穿讲解和局部 fixture，不是产品分析范围。一个 Flow 成功、一个 shard 完成或一个局部 slice 可读都不能完成仓库分析；只有 `RepositoryCoverageLedger` 对完整仓库的文件、site、入口、图、Fact/atom、Outcome、Flow、解释、知识和 section owner 逐项闭合，才可产生完整分析结果。禁止一 Flow 一 Markdown，也禁止先渲染片段再拼接。

目标设计权威是 [docs/DESIGN.md](docs/DESIGN.md)。当前代码和测试只验证或反驳目标的一部分，不能反向降低设计。

## 五分钟阅读路线

1. 读 [总体设计 §1–2](docs/DESIGN.md#1-先说业务结果)：先理解业务/信任目标，以及真实 jshERP DepotHead status 路径。
2. 读 [八个分析步骤主线](docs/DESIGN.md#3-八个分析步骤纵向主线)及[仓库完成门禁](docs/DESIGN.md#37-repository-completion-gate)：确认每个模块和分析步骤立即产生 canonical JSON/JSONL，分片不降低覆盖，单 Flow PASS 不能完成运行。
3. 读 [当前实现审计](docs/DESIGN.md#15-当前实现审计与目标设计分开)：记住目标命名和当前 pre-reset 代码是两套事实，当前 jshERP 诚实结果仍是 Gap、0 Flow、0 Capsule。
4. 需要细节时选择一份分析步骤文档；不要先从当前 Java 类名推断目标架构。
5. 执行未来改造时读 [命名与交付实施计划](docs/plans/source-analysis-naming-and-delivery-plan.md)和[工具链计划](docs/plans/target-standards-and-toolchain-plan.md)。

POC 记录位于 `docs/history/`，只用于历史审计，不属于阅读路线、目标导航或可复用生产合同。

## 八个分析步骤

| 运行目录 | 人类问题 | 详细设计 |
| --- | --- | --- |
| `steps/01-verified-source-inventory/` | 分析的是哪份不可变源码？ | [已验证源码清单](docs/analysis-steps/01-verified-source-inventory.md) |
| `steps/02-application-discovery/` | 这是什么应用，入口在哪里？ | [应用发现](docs/analysis-steps/02-application-discovery.md) |
| `steps/03-program-graphs/` | 结构、调用、控制、数据和证据怎样连接？ | [程序图](docs/analysis-steps/03-program-graphs.md) |
| `steps/04-proven-code-facts/` | 哪些代码事实逐原子可证明？ | [已证明代码事实](docs/analysis-steps/04-proven-code-facts.md) |
| `steps/05-business-flows/` | 每个入口的完整流程和模型阅读包是什么？ | [业务流程](docs/analysis-steps/05-business-flows.md) |
| `steps/06-flow-interpretation/` | 新仓库业务词怎样由 R0 提出、程序冻结，再由 R1/R2 安全选择？ | [流程解释](docs/analysis-steps/06-flow-interpretation.md) |
| `steps/07-repository-knowledge/` | 谁决定解释能否进入仓库业务知识？ | [仓库知识](docs/analysis-steps/07-repository-knowledge.md) |
| `steps/08-nine-section-document/` | 九章、Markdown、Trace、验证和归档怎样闭合？ | [九章文档](docs/analysis-steps/08-nine-section-document.md) |

数字前缀只用于上述文档和运行目录排序。Java package、类型、字段、schema、artifact ID 和命令使用语义名称。

## 目标命名

- 目录：`backend-agents/sources/source-code/`
- Maven：`org.sourceanalysis:source-code-analysis-agent`
- 显示名：`Source Code Analysis Agent`
- Java public root：`org.sourceanalysis.app`
- 业务分析包：`org.sourceanalysis.app.analysis.inventory`、`org.sourceanalysis.app.analysis.discovery`、`org.sourceanalysis.app.analysis.graph`、`org.sourceanalysis.app.analysis.fact`、`org.sourceanalysis.app.analysis.flow`、`org.sourceanalysis.app.analysis.interpretation`、`org.sourceanalysis.app.analysis.knowledge`、`org.sourceanalysis.app.analysis.document`
- 横切包：`org.sourceanalysis.app.capture.localgit`、`org.sourceanalysis.app.artifact`、`org.sourceanalysis.app.evidence`、`org.sourceanalysis.app.runtime`、`org.sourceanalysis.app.validation`、`org.sourceanalysis.app.adapter.cli`、`org.sourceanalysis.app.adapter.http`、`org.sourceanalysis.app.adapter.provider`
- 未来数据库根：`org.sourceanalysis.db.analysis`，仅保留为文档约定，本次不创建数据库代码

目标不创建通用 `common`、`shared`、`misc` 或 `utils` 包，也不把 `target`、`mvp`、编号、`codemd`、`github` 或 `linguan` 带入未来命名。

## 技术参考路线

- 跨分析步骤 records、canonical identity、artifact reuse、预算、安全、failure families 和测试目标：[总体设计 §12–13](docs/DESIGN.md#12-业务产物持久化与显式复用)。
- 九章名称、顺序和基数的唯一权威：[共享 NineSectionProfile](../../../shared/source-agent-contracts/README.md)。
- 所有 Source Agent 的中文共同语境：[backend-agents/CONTEXT.md](../../CONTEXT.md)。
- Fact/Proof/Gap 技术合同：[已证明代码事实](docs/analysis-steps/04-proven-code-facts.md#8-技术合同)。
- Flow/Capsule 技术合同：[业务流程](docs/analysis-steps/05-business-flows.md#8-技术合同)。
- R0、唯一 registry、有限键 R1/R2、`E + 2R` 守恒和 Provider 失败边界：[流程解释](docs/analysis-steps/06-flow-interpretation.md#8-技术合同)。
- run-centric `start/executeStep/inspect/artifact/render/validate/trace`、plan-only renderer、typed Trace 和显式新分析步骤执行：[九章文档](docs/analysis-steps/08-nine-section-document.md#8-技术合同)。
- 同一运行自动恢复：[延期说明](docs/supplements/runtime-recovery-todo.md)，不属于 active v0 合同。

## Wire Reset

未来实现从完整 Wire Reset 开始：整体移动到目标目录，替换 Maven/Java/package/wire 命名，删除 pre-reset 实现与 fixture，且只写新格式。没有兼容 reader、alias、迁移 bridge、双写或旧 artifact 转换；旧 `stages/` 路径、编号类型、旧 schema/receipt/package identity 必须 fail closed。Git 历史和 `progress/*.md` 保留用于审计，但不能成为运行时输入。

每个分析步骤写入 `steps/<ordered-semantic-key>/`。42 个 semantic payload、八个语义 receipt、一个 `nine-section-archive-manifest.json` 和一个 root `run-manifest.json` 仍合计 52 个 reader-visible run outputs。Module artifact/receipt 和 exterior validation publication 不混入这 52 项。

同一运行不自动恢复。中断或 started Provider failure 令运行失败；调用者只能新建运行，或用精确已验证的上游 publication references 创建显式的新分析步骤执行。程序不自动重试、切换 Provider 或重扫来源。

## 当前实现审计

以下只描述 2026-09-01 的 pre-reset 工作树，不代表目标命名已经落地：

| 当前能力 | 中文状态 | 诚实边界 |
| --- | --- | --- |
| `com.linguan.codemd.target.stage01` 持久化纵切 | **已验证（小型冻结仓库）** | capture、request admission、source index、三项 semantic publication 和旧 `stage-receipt.json` 已有定向测试；尚未跑完整固定 jshERP commit |
| `com.linguan.codemd.target.stage02` 发现纵切 | **已验证（小型五文件闭环）** | application profile、HTTP entries、Mapper candidates、capability report 和旧 receipt 已持久化；尚未跑完整固定 jshERP，也未生成正式五图 |
| 更早的 `analysis`、`discovery`、`mvp`、`stage01`–`stage04` 代码 | **历史/有限实现** | 验证过部分 Fact、Flow、解释、九章和 archive 行为，但包和 wire shape 不属于新目标，不得被包装成兼容层 |
| 正式程序图、完整 Fact/Flow、R0 registry、仓库知识、九章 root manifest | **缺失或部分具备** | 尚未形成八个新语义分析步骤的完整持久化 DAG |
| public Java/CLI/HTTP | **目标 Interface 缺失** | 当前接口与 adapter 语义不是新的 `RepositoryAnalysisAgent` 七方法合同 |

当前代码目录仍是 `backend-agents/sources/github-code/`，Maven group 仍是 `com.linguan.codemd`。这是必须由 Wire Reset 删除的当前事实，不是目标别名。固定 jshERP 的诚实结果继续是 Gap、0 Flow、0 Capsule，直到一个新授权的完整离线运行直接证明其他结果。

真实 walkthrough 固定为：

~~~text
DepotHeadController.batchSetStatus
  -> DepotHeadService.batchSetStatus
  -> DepotHeadMapper.updateByExampleSelective
  -> DepotHeadMapper.xml#updateByExampleSelective
  -> jsh_depot_head.status
~~~

源码里能看到这条路径，不等于当前程序已经证明整条路径。缺少通用 DepotHead dataflow/Facts 是明确 Gap。

## 只读文档复验

文档-only 工作不运行 Maven、模型、客户代码、来源 capture 或网络调用。确认八份目标设计存在：

~~~bash
for f in \
  docs/analysis-steps/01-verified-source-inventory.md \
  docs/analysis-steps/02-application-discovery.md \
  docs/analysis-steps/03-program-graphs.md \
  docs/analysis-steps/04-proven-code-facts.md \
  docs/analysis-steps/05-business-flows.md \
  docs/analysis-steps/06-flow-interpretation.md \
  docs/analysis-steps/07-repository-knowledge.md \
  docs/analysis-steps/08-nine-section-document.md
do
  test -f "$f"
done

sed -n '/## NineSectionProfile/,/## 最小 Interface/p' \
  ../../../shared/source-agent-contracts/README.md
~~~

未来实现只运行新增或直接覆盖当前 work unit 的定向测试。
