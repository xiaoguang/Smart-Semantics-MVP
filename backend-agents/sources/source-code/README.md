# Source Code Analysis Agent

本 Agent 的目标是把一份**完整冻结的** Java/Spring MVC/MyBatis 仓库快照，整理成可信、可追溯、明确写出未知项的**一份仓库级**九章 Markdown 候选。程序先盘点入口、建五图、证明Fact并为每个入口编译局部Flow/Capsule及证据支持的连接信号；流程解释保持单Flow R0/R1/R2，再以P1/P2这一唯一有界多Flow模型例外提出BusinessProcess。程序在零模型调用的仓库知识步骤完成准入、冲突保留和多对多membership，最终只生成一份`NineSectionPlan`、`document.md`、Trace和归档。

固定jshERP的Step05完整出口要求全入口各自成为`COMPILED`、`GAP`或`EXCLUDED`，不要求出现`DOMAIN_SPECIFIC`。当前没有用户提供的业务表映射，因此精确的Java→Mapper→XML→SQL引用仍是`GENERIC_TECHNICAL`/待解释结构材料，不能证明业务对象、先后、因果或外部效果，也不能形成`SHARED_ANCHOR`；`DOMAIN_SPECIFIC`与`SHARED_ANCHOR`仍保留给未来具备显式分类Authority时的更强证据。业务含义只能由Step06既定的R0/R1/R2与P1/P2受限流程解释。

DepotHead 八文件只是贯穿讲解和局部 fixture，不是产品分析范围。一个 Flow 成功、一个 shard 完成或一个局部 slice 可读都不能完成仓库分析；只有 `RepositoryCoverageLedger` 对完整仓库的文件、site、入口、图、Fact/atom、Outcome、Flow、解释、知识和 section owner 逐项闭合，才可产生完整分析结果。禁止一 Flow 一 Markdown，也禁止先渲染片段再拼接。

目标设计权威是 [docs/DESIGN.md](docs/DESIGN.md)。当前代码和测试只验证或反驳目标的一部分，不能反向降低设计。

## 五分钟阅读路线

1. 读 [总体设计 §1–2](docs/DESIGN.md#1-先说业务结果)：先理解业务/信任目标，以及真实 jshERP DepotHead status 路径。
2. 读 [八个分析步骤主线](docs/DESIGN.md#3-八个分析步骤纵向主线)及[仓库完成门禁](docs/DESIGN.md#37-repository-completion-gate)：确认每个模块和分析步骤立即产生 canonical JSON/JSONL，分片不降低覆盖，单 Flow PASS 不能完成运行。
3. 读 [当前实现审计](docs/DESIGN.md#15-当前实现审计与目标设计分开)：当前源码已有VerifiedSourceInventory M1–M3、ApplicationDiscovery M1–M4、ProgramGraphs M1–M6、ProvenCodeFacts v2 M1–M3、BusinessFlows M1–M3及FlowInterpretation局部M1–M5的受控纵切；跨Flow M6–M9、RepositoryKnowledge和NineSectionDocument仍未实现，当前代码也尚未运行jshERP。
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
| `steps/05-business-flows/` | 每个入口的局部活动和跨Flow连接证据是什么？ | [业务流程](docs/analysis-steps/05-business-flows.md) |
| `steps/06-flow-interpretation/` | 单Flow业务词与有界端到端过程怎样安全提出和复核？ | [流程解释](docs/analysis-steps/06-flow-interpretation.md) |
| `steps/07-repository-knowledge/` | 谁准入process claim、保留冲突并建立多对多membership？ | [仓库知识](docs/analysis-steps/07-repository-knowledge.md) |
| `steps/08-nine-section-document/` | process-first九章、plan-only Markdown和完整Trace怎样闭合？ | [九章文档](docs/analysis-steps/08-nine-section-document.md) |

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
- Flow/Capsule与`processJoinSignals`合同：[业务流程](docs/analysis-steps/05-business-flows.md)。
- 九模块、十五文件、`E+2R+2S`与P1/P2边界：[流程解释](docs/analysis-steps/06-flow-interpretation.md)。
- process admission、三值certainty与九过程数组：[仓库知识](docs/analysis-steps/07-repository-knowledge.md)。
- run-centric七方法、process-first Chapter 4、plan-only renderer与typed Trace：[九章文档](docs/analysis-steps/08-nine-section-document.md)。
- 同一运行自动恢复：[延期说明](docs/supplements/runtime-recovery-todo.md)，不属于 active v0 合同。

## Wire Reset

结构性 Wire Reset 已完成：工程已经移动到目标目录，Maven/Java/package身份已经替换，pre-reset生产代码、测试与fixture已经删除。reset后的代码已经在新格式上实现若干受控纵切（详见下表），未实现部分仍保留语义包骨架；它不是“只有骨架”的工程。没有兼容 reader、alias、迁移 bridge、双写或旧 artifact 转换；旧 `stages/` 路径、编号类型、旧 schema/receipt/package identity 必须 fail closed。Git 历史和 `progress/*.md` 保留用于审计，但不能成为运行时输入。

每个分析步骤写入`steps/<ordered-semantic-key>/`。47个semantic payload、八个语义receipt、一个`nine-section-archive-manifest.json`和一个root `run-manifest.json`合计**57**个正式run outputs。五个新增semantic全部属于仍名为`flow-interpretation`的Step 06；module artifact/receipt和外部validation不计入57。

同一运行不自动恢复。中断或 started Provider failure 令运行失败；调用者只能新建运行，或用精确已验证的上游 publication references 创建显式的新分析步骤执行。程序不自动重试、切换 Provider 或重扫来源。

## 当前实现审计

以下是对当前生产源码与定向测试树的只读审计；只描述 2026-09-01 Wire Reset 后仍存在的实现，不把已删除代码的历史测试结果当成当前能力：

| 当前能力 | 中文状态 | 诚实边界 |
| --- | --- | --- |
| 工程身份与目录 | **已实现（结构）** | 目录为`backend-agents/sources/source-code/`，Maven坐标为`org.sourceanalysis:source-code-analysis-agent`，生产包全部位于`org.sourceanalysis.app`语义根下 |
| 程序图 | **部分实现（受控fixture）** | 五张图、图索引和Gap的构建、canonical发布与重新打开已通过定向测试；当前尚未连接完整客户仓库输入 |
| 已证明代码事实 | **部分实现（v2 M1–M3受控纵切）** | 现有`FactCandidateEnumerator`、`AtomicProofBuilder`与`FactLedgerPublicationSpecifier`已经形成candidate→逐atom Proof decision→publication链，并发布/重开`proven-facts.json`、`proof-pack.json`、`gap-ledger.json`、`fact-accounting.json`；定向fixture覆盖可准入的Java boundary/guard Fact与拒绝Gap。它仍不是完整目标Fact taxonomy或客户仓库证明结果 |
| Java构建选择 | **已实现（构建）** | 项目提供JDK 17 Toolchain配置，compiler release固定为17；这只约束Agent自身构建，不代表任何业务分析步骤已实现 |
| 新wire头门禁 | **已实现（窄门禁）** | `AnalysisWireFormatGuard`只接受对象头`wireKind=SOURCE_ANALYSIS`且`wireVersion=v1`，并以`UNSUPPORTED_ANALYSIS_WIRE`拒绝顶层描述符元数据中的pre-reset path、编号stage、stage receipt/schema、旧Maven/Java package身份和wire alias；不扫描业务内容。owner-specific schema、canonical artifact reader与八步artifact校验尚未实现 |
| Canonical artifact foundation | **部分实现（多步骤受控纵切）** | 已有canonical JSON、不可变bytes、typed identity/address、policy registry及module/analysis-step persistence，被当前inventory、discovery、graph、fact、flow与local interpretation纵切实际使用；现有publisher能重算descriptor/root/receipt并拒绝策略、符号链接、碰撞、文件组和顺序错误。run manifest、完整runtime/public observation与NineSectionDocument RAW UTF-8链仍未实现 |
| 八个业务分析步骤 | **部分纵切，未形成完整run** | VerifiedSourceInventory M1–M3、ApplicationDiscovery M1–M4、ProgramGraphs M1–M6、ProvenCodeFacts v2 M1–M3、BusinessFlows M1–M3均已有生产类；FlowInterpretation已有局部registry proposal/freeze与finite-key M1–M5 scripted路径。当前BusinessFlows wire尚无目标`processJoinSignals`，跨Flow M6–M9/P1/P2、RepositoryKnowledge M1–M3、process-first NineSectionDocument M1–M4和完整Trace均未实现；没有jshERP当前run产物 |
| public Java/CLI/HTTP | **尚未实现** | `RepositoryAnalysisAgent`七方法、`source-analysis` CLI和loopback HTTP adapter均不存在 |
| pre-reset Stage/POC实现 | **已删除；仅历史证据** | 旧纵切、POC、fixture和旧接口不在当前生产/测试树中，不得包装成兼容层或作为当前jshERP结果 |

当前新实现尚未运行固定jshERP commit，因此不存在当前版的Flow、Capsule或九章结果。历史pre-reset审计曾得到“Gap、0 Flow、0 Capsule”，它只说明旧实现暴露过哪些证明缺口，不能冒充当前运行结果或当前能力。

当前用户已确认后续固定仓库验收可改用同一批准commit的**独立、完整、非promisor离线Git对象副本**；原partial/promisor仓库保持不变，既不作为capture输入，也不通过网络补对象。该确认只关闭验收输入设计与`DOMAIN_SPECIFIC`必要性两个未决项：独立副本尚未在本工作单元创建或运行，Step04 v3、Step05 signal cutover、固定仓库IT和Step06跨Flow能力仍未交付。

文档中的“提交补货申请→…→结算月度账单”仅是明确synthetic的跨Flow验收故事，永远不是jshERP行为。真实DepotHead材料也只证明静态入口、guard、ID与边界调用；没有专门Proof时，数据库、库存、账务、日志或配置效果均保持未证明。

真实 walkthrough 固定为：

~~~text
DepotHeadController.batchSetStatus
  -> DepotHeadService.batchSetStatus
  -> DepotHeadMapper.updateByExampleSelective
  -> DepotHeadMapper.xml#updateByExampleSelective
  -> jsh_depot_head.status
~~~

源码里能看到这条路径，不等于当前程序已经证明整条路径。当前语义骨架尚未执行该样例；历史pre-reset审计暴露出的DepotHead dataflow/Facts缺口只作为后续目标验收的反例来源。

即使未来对上述Java→Mapper→XML→SQL位置逐段建立精确静态引用，在没有显式业务分类Authority时，它们也只保持generic/pending；Step05不得借此声称DepotHead是某个业务对象、与另一Flow存在顺序或因果，或SQL已执行。

## 本地 Java 格式与构建检查

在本目录使用项目跟踪的 JDK 17 Toolchain，并保持离线。先检查格式；需要修复时显式执行 apply 后重跑 check：

~~~bash
mvn -o -t .mvn/toolchains.xml spotless:check
mvn -o -t .mvn/toolchains.xml spotless:apply
mvn -o -t .mvn/toolchains.xml spotless:check
~~~

本地 package 验证使用：

~~~bash
mvn -o -t .mvn/toolchains.xml -DskipUTs=true package
~~~

`skipUTs=true` 会有意跳过测试执行；该命令只证明 package 成功，不能表述为测试已通过。需要测试时，只运行当前 work unit 直接覆盖的定向 selector。

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
