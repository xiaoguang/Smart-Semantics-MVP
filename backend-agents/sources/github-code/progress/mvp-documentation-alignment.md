# Progress: MVP documentation alignment

- Status: COMPLETE
- Agent role: 唯一总体设计与 MVP 阶段设计作者
- Model: gpt-5.6-sol
- Reasoning: ultra
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: 对齐共享九章合同、GitHub Code Agent 总体设计、MVP 阶段设计与入口文档；只改任务批准的 Markdown 文件。
- Approved inputs: 固定本地实现、测试、既有文档，以及忽略目录中的 jshERP commit `8c30ce7861570458920175e200bb2a6442713580` MVP manifest/产物；只读核验，不重新生成。
- Current branch/worktree: `codex/rag-frontend-phase-one`；共享工作区原位编辑，不创建 worktree。

## Completed

- 完整读取四层 `AGENTS.md`、`codebase-design/SKILL.md` 与 `DEEPENING.md`。
- 运行 `git status --short` 并确认目标原型目录整体未跟踪；无关改动不进入本任务。
- 从实现与测试核对当前 Java Interface、六个 CLI、MVP 两条生成路径、八文件归档、验证和 Trace 行为。
- 核对 discovery、CodeFact 与 runtime receipt seam 均未接 generation；确认无 HTTP、完整 Proof、自动 Flow 编译或远程 Git Adapter。
- 只读核对 `.workspace/mvp-depothead-audit-input/flow-manifest.json`：固定 commit、3 个文件、5 个 Evidence、5 个 LockedFact、1 个 Flow。
- 核对 R1/R2 task package、recorded JSON 与历史 failure receipt；确认 configured adapter/auth 与 observed upstream provider 当前未分开建模。
- 更新共享规则、语言、NineSectionProfile 权威与工作区导航；固定 ReaderCandidateRound/FlowInterpretationRound 区分。
- 将 GitHub `DESIGN.md` 重构为 ARCH-01..ARCH-12 稳定总体设计、跨阶段不变量、成熟度矩阵和 MVP+五期顺序。
- 新建 `docs/stages/00-mvp.md`，记录真实 Module、两条数据流、字段/身份/归档/CLI、DepotHead 投影、差异与测试矩阵。
- 精简 GitHub README 为入口、能力矩阵与复现命令；最小修正下游审阅设计的历史状态和权威指向。
- 最终核对 ARCH-01..ARCH-12、00/01..05 阶段名、共享九标题、四方法 Interface、六 CLI 与八文件合同。
- 重新计算 DepotHead taskSpecId/capsuleId，与 stage 文档记录逐字一致；禁止错误成熟度表述零命中。
- 只读复核 DepotHead 三份冻结源码的声明行段：反审核守卫和 Mapper 映射通过逐 atom 审计；HTTP 完整 route、审核 current-status/failure、持久化 target/selection/assigned field 未由各自声明 span 完整支持，样例 semantic Evidence closure 整体失败。
- 只读复核 `MvpGenerationCore`、`CodeMdCli`、discovery/analysis walker 与 `CandidateArchiveService`，确认 Java core、CLI、诊断 walker、archive 的 symlink 合同不同，并确认 `LockedFact` 当前允许空 attributes object、空 textual scalar 与空 evidenceIds array。
- 将 Fact 准入前 semantic Evidence closure 提升为 scoped 硬规则和总体跨阶段不变量；明确 known ID、有效 SHA 与 reference closure 只证明引用/完整性，不能证明 Fact atom。
- 修订总体 ARCH-01/04/12 当前成熟度、安全边界、失败分类、MVP 状态与总体退出条件，精确区分 Java core、CLI、诊断 walker、archive，并记录固定 DepotHead 样例被 semantic audit 拒绝。
- 重写 `00-mvp.md` 的阶段状态与退出条件、ARCH 映射、阶段内 Module/邻接 seam、两条执行流、path/symlink 与 LockedFact 精确合同；ARCH-03 已与总体矩阵统一为 `NOT_IN_STAGE`。
- 将 DepotHead 五 Fact 改为 declared Fact 逐 atom 审计：2 PASS / 3 FAIL；九章内容降级为修正冻结输入后才可用的拒绝草稿，并同步缺口、经验、测试矩阵和下一阶段输入。
- 更新 GitHub README 能力矩阵，新增 `Fact→Evidence semantic closure = NOT IMPLEMENTED`，把两条生成能力限定为执行路径，并将固定 DepotHead 样例明确降级为拒绝输入。
- 将共享固定输入措辞从 immutable repository snapshots 校准为 immutable source snapshots；下游审阅设计明确区分 `ReaderCandidateRound` 与 `FlowInterpretationRound`。
- 完成 10 份目标文档的相对链接/尾随空白/禁用断言检查、7 份表格文档列数检查，以及 ARCH、阶段文件、九标题、模型职责、两类 Round 和固定样例状态检查。
- 主 Agent 完成二次独立 Sol/ultra 只读复核、fresh 定向 Maven 测试与 fresh 静态检查；文档对齐获 `Ready: Yes`。

## Current state

- 文档对齐、最终外部复核与验证均已完成；二次评估为 `Ready: Yes`。该结论只覆盖文档对齐，MVP accuracy exit 仍为 `NOT MET`。

## Review closure

- A / Critical — CLOSED：DepotHead 现标为 hash/reference-valid、semantic-invalid；五 Fact 表记录 2 PASS / 3 FAIL，九章为修正冻结输入后才可使用的拒绝草稿，准确度出口保持 `NOT MET`。
- B / Important — CLOSED：按源码分别记录 Java core、CLI、diagnostic walker 与 archive 的 symlink 行为，并精确列出 `LockedFact` 空 object/scalar/array/evidenceIds 边界和后续硬化缺口。
- C / Important — CLOSED：00 MVP 的 ARCH-03 统一为 `NOT_IN_STAGE`；discovery/analysis 分别登记为 01/02 邻接 seam，runtime receipt 登记为 04 邻接 seam。
- 一致性 — CLOSED：共享规则使用 immutable source snapshots；下游设计明确两类 Round；MVP 固定样例不再作为验收或 Candidate 内容依据。
- 二次独立 Sol/ultra 只读复核 — PASS：A/B/C 全部通过，New Critical=None，Important=None；Assessment=`Ready: Yes`（仅文档对齐）。

## Changed files

- `linguan-prototype-v2/source-to-standard-markdown/AGENTS.md`
- `linguan-prototype-v2/source-to-standard-markdown/CONTEXT.md`
- `linguan-prototype-v2/source-to-standard-markdown/contracts/README.md`
- `linguan-prototype-v2/source-to-standard-markdown/README.md`
- `linguan-prototype-v2/source-to-standard-markdown/sources/github-code/AGENTS.md`
- `linguan-prototype-v2/source-to-standard-markdown/sources/github-code/DESIGN.md`
- `linguan-prototype-v2/source-to-standard-markdown/sources/github-code/README.md`
- `linguan-prototype-v2/source-to-standard-markdown/sources/github-code/docs/stages/00-mvp.md`
- `linguan-prototype-v2/docs/design/data-standardization-review-experience.md`
- `linguan-prototype-v2/source-to-standard-markdown/sources/github-code/progress/mvp-documentation-alignment.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | 已记录预存改动；`linguan-prototype-v2/` 整体为未跟踪目录 |
| `jq '.' .workspace/mvp-depothead-audit-input/flow-manifest.json` | PASS | commit `8c30ce…580`；3 files / 5 evidence / 5 locked facts / 1 flow |
| Java/测试静态核对 | PASS | Interface 4 方法、CLI 6 子命令、归档 8 文件；成熟度差异已列入设计输入 |
| `mvn -o -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,DeterministicBaselineGenerationTest,CandidateArchivePersistenceTest,CodeMdCliPersistenceTest,CodeMdCliValidateTest,TraceLocatorTest,RepositoryDiscovererTest,CodeMdCliDiscoveryTest,CodeFactAnalyzerTest,ModelRuntimeReceiptAdmissionTest test`（主 Agent fresh） | PASS | `BUILD SUCCESS`；28 tests；0 failures / 0 errors / 0 skipped |
| 相对 Markdown 链接存在性检查（主 Agent fresh） | PASS | 10 份目标文档全部链接可解析；未来阶段只列名、不建文件 |
| Markdown 表格列数检查 | PASS | 7 份含表格目标文档无列数漂移 |
| 逐文件尾随空白检查（主 Agent fresh） | PASS | 10 份目标文档无尾随空白 |
| `git diff --check`（主 Agent fresh） | PASS | 无 whitespace error；目标目录整体未跟踪，另有逐文件检查 |
| ARCH/NineSection/stage 枚举（主 Agent fresh） | PASS | ARCH headers=12、stage ARCH rows=12、两处 ARCH-03=`NOT_IN_STAGE`、九标题精确、`docs/stages/` 仅 `00-mvp.md` |
| scoped 模型职责与 Round 术语 | PASS | Sol/ultra 设计、Terra/xhigh 生产、Luna/xhigh 测试/审阅/九章 proposal；两类 Round 均明确 |
| DepotHead identity 重算 | PASS | taskSpecId `ceded384…e95b`；capsuleId `5599924…c28b` |
| DepotHead semantic audit | PASS（审计完成） | reverse guard 与 Mapper mapping 通过；HTTP、audit/stock、persistence Fact 失败；Flow 拒绝 |
| 旧 Candidate ID 与错误成熟度表述搜索（主 Agent fresh） | PASS | 均零命中 |

## Decisions

- 采用“共享语言无关合同 → 稳定总体设计 → `00-mvp` 当前设计与实现结果 → Java/CLI/测试/JSON → progress”的权威链。
- README 仅承担入口、能力索引和复现导航，不复制总体设计。
- 使用 codebase-design 的 Module、Interface、Seam、Adapter 术语区分目标架构与当前单模块实现。
- 将“准入前 semantic Evidence closure”与“准入后语义原子守恒”设为两道独立 fatal gate；hash、ID、reference、结构、Trace 和内容密度互不替代。

## Blockers

- 无任务阻塞；semantic Evidence closure 是已明确记录的实现/验收缺口，当前固定样例保持拒绝。

## Exact next action

- 下一实施工作先针对 semantic Evidence closure 另写阶段设计与 TDD 计划；未经明确授权，不得修正冻结 Manifest、生成 Candidate 或修改 `.workspace`。

## Resume checks

- 若需恢复，重新运行 `git status --short` 并读取本节 Review closure；只修改批准的 Markdown。
- 目标目录整体未跟踪，审阅时以本文件列出的 10 个 Markdown 路径为范围。
