# Source Code Analysis Agent

本 Agent 的目标是把一份**完整冻结的** Java/Spring MVC/MyBatis 仓库快照，整理成可信、可追溯、明确写出未知项的**一份仓库级**九章 Markdown 候选。程序先盘点全入口、构建五张程序图、证明 Fact，再为每个支持入口编译独立 Flow/EvidenceCapsule；流程解释为每个 Flow 提出有证据 basis 的仓库特定词，程序冻结唯一 `RepositoryInterpretationRegistry` 后再完成有限键 R1/R2 解释；最后把全部 Flow 准入并合成唯一 `RepositoryKnowledge`，只生成一份 `NineSectionPlan`、`document.md`、Trace 和完整分析运行归档。

DepotHead 八文件只是贯穿讲解和局部 fixture，不是产品分析范围。一个 Flow 成功、一个 shard 完成或一个局部 slice 可读都不能完成仓库分析；只有 `RepositoryCoverageLedger` 对完整仓库的文件、site、入口、图、Fact/atom、Outcome、Flow、解释、知识和 section owner 逐项闭合，才可产生完整分析结果。禁止一 Flow 一 Markdown，也禁止先渲染片段再拼接。

目标设计权威是 [docs/DESIGN.md](docs/DESIGN.md)。当前代码和测试只验证或反驳目标的一部分，不能反向降低设计。

## 五分钟阅读路线

1. 读 [总体设计 §1–2](docs/DESIGN.md#1-先说业务结果)：先理解业务/信任目标，以及真实 jshERP DepotHead status 路径。
2. 读 [八个分析步骤主线](docs/DESIGN.md#3-八个分析步骤纵向主线)及[仓库完成门禁](docs/DESIGN.md#37-repository-completion-gate)：确认每个模块和分析步骤立即产生 canonical JSON/JSONL，分片不降低覆盖，单 Flow PASS 不能完成运行。
3. 读 [当前实现审计](docs/DESIGN.md#15-当前实现审计与目标设计分开)：当前只完成语义目录/Maven/package骨架、JDK 17 Toolchain与通用新wire头门禁；八步业务分析尚未实现，当前代码也尚未运行jshERP。
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

结构性 Wire Reset 已完成：工程已经移动到目标目录，Maven/Java/package身份已经替换，pre-reset生产代码、测试与fixture已经删除。当前只有语义包骨架和通用wire头门禁，后续八步实现只写新格式。没有兼容 reader、alias、迁移 bridge、双写或旧 artifact 转换；旧 `stages/` 路径、编号类型、旧 schema/receipt/package identity 必须 fail closed。Git 历史和 `progress/*.md` 保留用于审计，但不能成为运行时输入。

每个分析步骤写入 `steps/<ordered-semantic-key>/`。42 个 semantic payload、八个语义 receipt、一个 `nine-section-archive-manifest.json` 和一个 root `run-manifest.json` 仍合计 52 个 reader-visible run outputs。Module artifact/receipt 和 exterior validation publication 不混入这 52 项。

同一运行不自动恢复。中断或 started Provider failure 令运行失败；调用者只能新建运行，或用精确已验证的上游 publication references 创建显式的新分析步骤执行。程序不自动重试、切换 Provider 或重扫来源。

## 当前实现审计

以下只描述 2026-09-01 Wire Reset 后的工作树，不把已删除代码的历史测试结果当成当前能力：

| 当前能力 | 中文状态 | 诚实边界 |
| --- | --- | --- |
| 工程身份与目录 | **已实现（结构）** | 目录为`backend-agents/sources/source-code/`，Maven坐标为`org.sourceanalysis:source-code-analysis-agent`，生产包全部位于`org.sourceanalysis.app`语义根下 |
| Java构建选择 | **已实现（构建）** | 项目提供JDK 17 Toolchain配置，compiler release固定为17；这只约束Agent自身构建，不代表任何业务分析步骤已实现 |
| 新wire头门禁 | **已实现（窄门禁）** | `AnalysisWireFormatGuard`只接受对象头`wireKind=SOURCE_ANALYSIS`且`wireVersion=v1`，并以`UNSUPPORTED_ANALYSIS_WIRE`拒绝顶层描述符元数据中的pre-reset path、编号stage、stage receipt/schema、旧Maven/Java package身份和wire alias；不扫描业务内容。owner-specific schema、canonical artifact reader与八步artifact校验尚未实现 |
| Canonical artifact foundation | **部分实现（首个落盘纵切）** | 已有canonical JSON、不可变bytes、typed identity/address和policy registry；另已实现源码盘点 M1 唯一已注册 JSON payload 的 module store：原子安装、receipt-last、fresh reopen、descriptor/root/receipt重算和策略表/符号链接/碰撞拒绝。它不是完整store：JSONL、RAW UTF-8、其他模块payload、analysis-step/run store、生产root bootstrap和runtime仍未实现 |
| 八个业务分析步骤 | **尚未实现** | 八个语义package目前只有`package-info.java`骨架；没有当前Local Git capture、源码清单、应用发现、五图、Fact/Proof、Flow/Capsule、R0/R1/R2、RepositoryKnowledge、九章或Trace生产代码与运行产物 |
| public Java/CLI/HTTP | **尚未实现** | `RepositoryAnalysisAgent`七方法、`source-analysis` CLI和loopback HTTP adapter均不存在 |
| pre-reset Stage/POC实现 | **已删除；仅历史证据** | 旧纵切、POC、fixture和旧接口不在当前生产/测试树中，不得包装成兼容层或作为当前jshERP结果 |

当前新实现尚未运行固定jshERP commit，因此不存在当前版的Flow、Capsule或九章结果。历史pre-reset审计曾得到“Gap、0 Flow、0 Capsule”，它只说明旧实现暴露过哪些证明缺口，不能冒充当前运行结果或当前能力。

真实 walkthrough 固定为：

~~~text
DepotHeadController.batchSetStatus
  -> DepotHeadService.batchSetStatus
  -> DepotHeadMapper.updateByExampleSelective
  -> DepotHeadMapper.xml#updateByExampleSelective
  -> jsh_depot_head.status
~~~

源码里能看到这条路径，不等于当前程序已经证明整条路径。当前语义骨架尚未执行该样例；历史pre-reset审计暴露出的DepotHead dataflow/Facts缺口只作为后续目标验收的反例来源。

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
