# Progress: unified source-analysis CLI cleanup

- Status: IN_PROGRESS
- Agent role: primary implementation and integration
- Model: Codex GPT-5
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: unify the public CLI, retire legacy generation paths, preserve historical outputs, and complete the approved deterministic cleanup
- Owning plan: 代码清理与统一 `source-analysis` 入口实施计划
- Approved inputs: current main at e8c40ea2f250da55d6b8797c32c380461061c3c9 plus the existing uncommitted cleanup audit/resource changes
- Current branch/worktree: codex/source-analysis-cli-cleanup in the formal source-code checkout

## Completed

- Confirmed formal checkout HEAD and origin/main match.
- Created the dedicated implementation branch without discarding the existing approved audit changes.
- Locked the execution boundary to zero real model calls and no regeneration of Activity or business-process results.

## Current state

The existing audit and production-resource cleanup are preserved in the worktree. Their direct Prompt/resource contract tests pass. Runtime and CLI implementation have not yet been changed.

## Changed files

- `progress/unified-source-analysis-cli-cleanup.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing audit changes identified; unrelated root `docs/research/` remains excluded |
| `git rev-parse HEAD origin/main` | PASS | Both resolve to `e8c40ea2f250da55d6b8797c32c380461061c3c9` |
| `mvn -t .mvn/toolchains.xml -Dtest=BusinessProcessPromptV2ContractTest,BusinessProcessSemanticFingerprintV2Test test` | PASS | 5 tests, 0 failures/errors/skips |

## Decisions

- Real model calls are forbidden for this plan.
- Existing 326 reviewed Activities, process outputs, journals, JDT material, and historical reports are read-only inputs.
- A targeted JDT integration check is allowed only if required; it must not trigger Activity or process generation.

## Blockers

None.

## Exact next action

Save the approved audit/design baseline, then write the first failing CLI/runtime contract tests.

## Resume checks

- Re-read `git status --short` and verify this branch.
- Verify `docs/supplements/more-findings.md` retains SHA-256 `59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e`.
- Do not initialize or call a product model provider.

## Plan closeout destinations

- Durable decisions: scoped AGENTS, active runtime/design documentation, cleanup audit
- Remaining issues: existing backlog documents, especially `docs/supplements/more-findings.md`
- Verification and output references: cleanup audit delivery section and final branch/PR verification

Keep this handoff while the plan is active. At whole-plan closeout, consolidate
the information above into its durable destinations and remove the temporary
task file; do not archive a second copy of the progress record.
