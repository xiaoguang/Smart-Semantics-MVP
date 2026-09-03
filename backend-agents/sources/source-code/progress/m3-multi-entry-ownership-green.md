# Progress: M3 multi-entry ownership GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production slice
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Merge ownership for physically shared M3 control-flow nodes, edges, and profile-stop gaps; make graph identity cover the public ownership/traversal state.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`, program-graphs backlog P4, and the established `ControlFlowGraphBuilderTest` RED.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read scoped rules, M3 design references, backlog P4, and the existing RED progress record.
- Reproduced the exact public RED: `ControlFlowGraphBuilderTest` ran 14 tests, with one assertion failure because shared basic-block nodes kept only the first entry owner.
- Replaced first-writer node retention with a fail-closed merge: a repeated physical node must retain identical kind, canonical value, and provenance refs, then receives the sorted distinct entry-owner union.
- Made shared control-flow edges fail closed on any conflicting repeated representation while retaining one stored edge and both traversals.
- Merged repeated profile-stop Gap owners by candidate, rebuilding the Gap and its terminal disposition when the owner union changes.
- Made the public graph identity include canonical ordered nodes (including owners), edges, traversals, terminal dispositions, and Gap material; entry ordering is sorted before identity construction.
- Applied Spotless and verified the existing public selector is green.

## Current state

- The pre-existing public test has one expected failure: a physical shared M3 node preserves the first entry owner instead of the sorted owner union.

## Changed files

- `progress/m3-multi-entry-ownership-green.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | RED (expected) | 14 tests; 1 failure; shared physical basic blocks retain only their first entry ID. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Formatting applied to the existing M3 production/test worktree state. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 14 tests; 0 failures/errors/skips. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Only `ControlFlowGraphBuilder.java` may change in production. No M2/M4/M5/M6, design, test, POM, or behavior outside M3 ownership identity is in scope.
- The existing test file was already an uncommitted shared Luna work item. Spotless reported formatting it while applying the repository formatter; this slice intentionally did not change its assertions or fixture behavior.

## Blockers

- None.

## Exact next action

- Parent may integrate this green slice with the rest of the uncommitted ProgramGraphs work; this agent must not commit or push.

## Resume checks

- Re-read this file; confirm the exact selector stays green and preserve the concurrently owned source/test worktree changes.
