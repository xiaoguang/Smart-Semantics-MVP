# Progress: M2 CallGraph 到 M3 ControlFlow 的可信重开设计

- Status: COMPLETE
- Agent role: Sol/ultra design authority sub-agent
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02 09:56:09 NDT
- Last updated: 2026-09-02 10:07:00 NDT
- Scope: 只定义持久化 M2 CallGraph module 的重开 seam，以及 M3 只消费 sealed fresh-reopened aggregate 的交接合同。
- Approved inputs: `AGENTS.md`、`docs/DESIGN.md`、`docs/analysis-steps/03-program-graphs.md`、现有 M1→M2 重开合同。
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` / `/private/tmp/linguan-source-analysis-graph-provenance-design/backend-agents/sources/source-code`

## Completed

- 已读取 scoped Agent 规则、深模块设计规则与程序图阶段文档的现有 M1→M2 合同。
- 已定义 `PersistedCallGraphReader.reopen(...) -> ReopenedCallGraph` 的唯一 sealed 重开 seam。
- 已锁定 M2 address、单 payload、exact type/schema、八项 upstream、controls、profile、basis、endpoint 与 coverage 验证，以及稳定 `GRAPH_REFERENCE_BROKEN` 失败。
- 已将 M3 输入改为 `ControlFlowInputs`，明确禁止 raw `CodeStructureGraphDraft`、raw `CallGraphDraft` 和 detached lists。

## Current state

- 设计合同已完成，等待主 Agent 按该合同编写 M2 reader 与 M3 RED/GREEN。

## Changed files

- `docs/analysis-steps/03-program-graphs.md`
- `progress/m2-to-m3-call-reopen-design.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | 开始时 worktree 无未提交变更 |
| `git diff --check` | PASS | 无空白或补丁格式错误 |

## Decisions

- 保持六个 graph module 与八项 reader-visible ProgramGraphs 输出数量不变；reader 只是内部可信重开 seam，不产生文件。
- M2 publication 的 direct upstream 固定为 M1 payload、graph profile 与同一次 fresh-reopened 输入的六个 references，共八项。
- M3 必须从同一次 `ReopenedProgramGraphInputs`依次重开 M1/M2，并在解析任何 method body 前比较完整 basis。

## Blockers

- 无。

## Exact next action

- 主 Agent 以真实 canonical module store 编写 M2→M3 fresh-reopen 的 Luna RED，包括八项 lineage/controls/profile/basis mutation。

## Resume checks

- 先运行 `git status --short`，确认只有本 progress 与预期设计文档改动。
- 核对本地 docs-only commit、`git diff --check` 结果，以及主 Agent 未将该未完成实现推送到 main。
