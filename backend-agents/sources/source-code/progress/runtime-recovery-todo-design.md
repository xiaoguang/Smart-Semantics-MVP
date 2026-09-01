# Progress: runtime-recovery-todo-design

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority（本任务唯一主设计者）
- Model: GPT-5.6 Sol / ultra
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: 仅修改设计、规则、导航与本 progress；把同一运行自动恢复移出 active v0，同时保留八阶段业务、52 项正式输出和跨执行 stage artifact 复用。
- Approved inputs: 用户三层约定；父任务 brief；`/Users/yexiaoguang/Documents/ErpMock/AGENTS.md`、本目录 `AGENTS.md`、`docs/DESIGN.md`、`docs/stages/01-08`、`README.md`、`docs/plans/target-standards-and-toolchain-plan.md`。
- Current branch/worktree: shared dirty worktree；不得覆盖或发布其他未提交工作。

## Task brief

- 只改设计、规则、导航与本progress；不改Java、测试、POM、JSON Schema或运行产物，不调用Provider/客户Maven/部署，不提交或推送。
- 把现有同一run进程崩溃自动恢复整体移到`docs/supplements/runtime-recovery-todo.md`，标明DEFERRED/TODO、非实现合同、不得据此编码；保存旧能力意图/机制边界及56–96小时总量、其中模型终态自动修复32–56小时估算，不继续完善。
- active v0只保留`QUEUED/RUNNING/FINISHED/FAILED`与单进程worker；中断run失败，started Provider不自动重试/切换，已完整安装产物保留inspect/诊断。public Interface/CLI/HTTP删除resume，仅capability manifest可见`RUNTIME_RESUME=CAPABILITY_NOT_ENABLED`。
- 精确区分三层：编码Agent靠Git+各自`progress/*.md`续接；stage/module JSON/JSONL/receipt是核心业务数据流、上下游对话格式和可复用输入；同一run自动恢复延期。Stage07失败后必须能由新`executeStage`显式验证并直接消费Stage01–06，尤其Stage06 publication，不重扫/不重做有效上游且Provider调用0。
- 完整保留八阶段、52项正式输出、N/E/I/R、五图、CodeFact/Proof/Gap、FlowSlice/EvidenceCapsule、逐Flow Luna R0/R1/R2、唯一RepositoryKnowledge、唯一九章plan/document/Trace/archive、每module确切输入输出/数量/失败/下游保证、完整仓库覆盖、Java/CLI/loopback HTTP同一核心、canonical JSON/JSONL/hash/Evidence/Trace、程序/LLM责任、Luna/Terra/Sol指南、JDK17/Maven与成熟parser/formatter/quality工具、无POC/旧Stage01–04兼容、完整jshERP Stage01–05离线验收和另授权真实Luna。
- 修正Stage06 R0/R1/R2 shard denominator为`E/R/R`，失败分支仍保持同一`E+2R` planned task/disposition公式。移除active恢复专属records/enums/addresses/codes/tests/guides，不粗暴覆盖共享未提交设计。
- 结束前完成docs-only`git diff --check`、JSON fence parse、fence parity、全目录recovery residue scan、52 outputs与逐项coverage自审；本progress最终标为COMPLETE。

## Completed

- 完整读取 root/scoped AGENTS、总体设计、Stage01-08、README、标准与工具链计划、progress 模板。
- 锁定三层边界：开发 Agent 续接；已验证 stage/module artifacts 的显式新执行复用；同一 run 自动恢复 TODO。
- 锁定不可变 coverage：完整仓库冻结、应用/入口/MyBatis、五图、Fact/Proof/Gap、Flow/Capsule、逐 Flow R0/R1/R2、唯一 RepositoryKnowledge、九章/Trace/archive、52 项正式输出。
- 将同一run自动恢复的既有意图、旧机制名称、覆盖边界与56–96小时估算整体归档为非合同TODO；active v0不再包含其records/interfaces/routes/tests。
- 收口Stage06–08：`E+2R` task/disposition、`E/R/R` shard denominator、Stage07直接消费validated Stage01–06、Stage08七方法/四地址/最小四状态均已贯通。
- 修正显式stage execution的完成语义：结束于Stage02–07的成功执行可`FINISHED`，但`analysisResult=null`且无root manifest；只有执行至Stage08才产生四值Repository AnalysisResult。
- 发布前定点删除旧POC/兼容表述：固定jshERP来源明确为离线验收，target v0保持单Maven module，自动测试仅scripted fake Provider，诊断不得绕过Stage01–07验证链，`CodeToMarkdownAgent`及旧Interface/CLI/兼容路径必须删除且不得作为迁移桥。

## Current state

- 100% COMPLETE。TODO supplement、scoped规则、README、总体设计、Stage00导航、Stage01–08与工具链计划已经从上到下自洽。
- active v0只有`QUEUED/RUNNING/FINISHED/FAILED`、单进程worker、七方法`start/executeStage/inspect/artifact/render/validate/trace`与四variant `ArtifactLocation`；没有public resume或同run自动恢复机制。
- Stage01–08业务主线、每module合同、52项正式输出、完整仓库coverage、`E+2R`和`E/R/R`、唯一RepositoryKnowledge、九章/Trace/archive均保留。
- Stage07新执行可直接fresh-validate并消费Stage01–06 publications，只运行Stage07且Provider调用为0；这是业务artifact复用，不是同run恢复。
- 旧`CodeToMarkdownAgent`、旧Interface/CLI与兼容路径明确是待删除实现差距，不是可保留Adapter、迁移桥或目标成熟度证据。

## Changed files

- `progress/runtime-recovery-todo-design.md`
- `docs/supplements/runtime-recovery-todo.md`
- `AGENTS.md`
- `README.md`
- `docs/DESIGN.md`
- `docs/stages/00-mvp.md`
- `docs/plans/target-standards-and-toolchain-plan.md`
- `docs/stages/01-freeze-source.md`
- `docs/stages/02-discover-application-and-entries.md`
- `docs/stages/03-build-five-program-graphs.md`
- `docs/stages/04-prove-code-facts.md`
- `docs/stages/05-compile-business-flows.md`
- `docs/stages/06-interpret-one-flow-at-a-time.md`
- `docs/stages/07-admit-and-merge-business-knowledge.md`
- `docs/stages/08-build-nine-section-document-and-archive.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| docs-only `git diff --check`（tracked docs）+ untracked files `--no-index --check` | PASS | tracked exit 0；plan/supplement/progress无whitespace error output；全清单trailing-whitespace scan无命中 |
| JSON/JSONL fence parser + fence parity | PASS | 15 files；12 JSON blocks、14 JSONL blocks均可解析；tilde/backtick fence均成对 |
| active-doc relative-link scan | PASS | README、DESIGN、Stage01–08、plan、supplement均解析；Stage00引用的5个ignored `.workspace`历史证据在本worktree缺失，按其POC历史声明排除，不影响active合同 |
| 发布前旧POC/兼容残留检查 | PASS | AGENTS不再使用MVP capture/recorded Provider/Manifest-Evidence-Pack路径；DESIGN与Stage08中`CodeToMarkdownAgent`命中均为必须删除旧Interface/CLI/兼容路径，不存在保留迁移桥表述 |
| exact output-table audit | PASS | Stage01–08=`4/5/8/5/6/10/6/8`，合计52；DESIGN与plan公式一致 |
| lifecycle/interface/schema residue scan | PASS | active状态仅四值；无旧lifecycle enum；无OutcomeInstall/PendingTerminalRepair/ResumeDecision/RunResumer/active resume address或route |
| full active recovery residue scan | PASS | 命中仅为明确的否定/TODO边界、coding Agent progress续接规则、`fresh-reopen`或`lowercaseHex`假阳性；未发现active恢复机制 |

## Core-scope coverage audit

| # | 用户锁定范围 | 结果 | 权威落点 |
| ---: | --- | --- | --- |
| 1 | 冻结完整客户仓库commit/文件清单/hash | PASS | DESIGN §4；Stage01 |
| 2 | 应用类型、Maven模块、Spring MVC入口、MyBatis能力 | PASS | DESIGN §5；Stage02 |
| 3 | 结构/调用/控制流/数据流/证据五图 | PASS | DESIGN §6；Stage03七semantic+receipt |
| 4 | 可验证CodeFact/Proof/Gap | PASS | DESIGN §7；Stage04 |
| 5 | 按入口FlowSlice与每Flow EvidenceCapsule | PASS | DESIGN §8；Stage05 |
| 6 | Luna逐Flow R0/R1/R2 | PASS | DESIGN §9；Stage06 `E+2R`、`E/R/R` |
| 7 | 程序准入并汇总唯一RepositoryKnowledge | PASS | DESIGN §10；Stage07 |
| 8 | 唯一九章plan/document/Trace/归档 | PASS | DESIGN §11；Stage08 |
| 9 | 每module确切输入/输出/数量/失败/下游保证 | PASS | 每份Stage文档 §8.0/§8.0.1与Luna/Terra handoff |
| 10 | 完整仓库覆盖、非DepotHead结局 | PASS | DESIGN §3.7；Stages01–08 coverage/accounting；DepotHead保持Gap/0 Flow/0 Capsule |
| 11 | Java/CLI/loopback HTTP同一核心 | PASS | DESIGN §13.1；Stage08 §8.1 |
| 12 | Canonical JSON/JSONL/hash/Evidence/Trace | PASS | DESIGN §13.2–13.3；各stage artifact contracts |
| 13 | 程序/LLM责任边界 | PASS | DESIGN §2/§9；每份stage §7 |
| 14 | Luna/Terra/Sol指南 | PASS | AGENTS；DESIGN §13.10–13.11；plan §7 |
| 15 | 成熟parser/formatter/quality插件、JDK17 Maven toolchain | PASS | standards/toolchain plan §2–5 |
| 16 | POC与旧Stage01–04无兼容目标 | PASS | Stage00仅历史；DESIGN §13.4/§15与plan拒绝旧旁路/兼容猜测 |
| 17 | 完整jshERP Stage01–05离线验收；真实Luna另授权 | PASS | DESIGN §13.9/§13.10；AGENTS model/testing rules；plan Task11 |
| 18 | 52个reader-visible正式输出 | PASS | DESIGN §3.3；plan §6；机械表审计 |
| 19 | 三层约定与Stage07显式复用 | PASS | AGENTS三层规则；DESIGN §12.2；Stages06/07/08 |
| 20 | 同run自动恢复全部TODO化 | PASS | supplement；DESIGN §12.3；capability=`RUNTIME_RESUME=CAPABILITY_NOT_ENABLED` |

## Decisions

- Stage/module JSON/JSONL/receipt 是业务分析产物与上下游对话格式，不是崩溃恢复 journal。
- `executeStage(StageExecutionRequest)` 创建新的 stage execution/run identity；它不是同 run resume，不继承 queue/worker/model-call 状态。
- Provider 调用一旦开始，active v0 不自动重试或切换；当前 run 失败并保留已安装诊断。
- 同一 run 崩溃恢复的完整既有意图只归档至 `docs/supplements/runtime-recovery-todo.md`，不得据此编码。

## Blockers

- 无设计级 blocker。Java/测试/POM/JSON Schema与完整Stage01–08实现仍是后续implementation work units，不属于本docs-only任务；当前jshERP data-flow/Fact closure仍是已明示业务Gap，不是本轮恢复设计阻塞。

## Exact next action

- 无；父任务可复审并发布本docs-only设计。不得据此开始runtime recovery实现。

## Resume checks

- 任务 brief 的唯一续接路径就是本文件：`progress/runtime-recovery-todo-design.md`。
- 恢复工作前先读本文件、`git status --short` 与当前 diff；保留其他 Agent 的并行未提交修改。
- 不改 Java、测试、POM、JSON Schema、运行产物；不调用 Provider/客户 Maven；不提交或推送。
