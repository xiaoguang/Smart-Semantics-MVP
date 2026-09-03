# Progress: M3 nested guard safety test

- Status: COMPLETE
- Agent role: Luna/xhigh test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam regression test for nested Java guards; no production, design, POM, or commit changes.
- Approved inputs: scoped AGENTS.md, program-graphs design, P4 backlog, existing M1→M3 fixtures.
- Current branch/worktree: codex/source-analysis-program-graphs / shared verified-inventory worktree

## Completed

- Added `NestedGuardControlFlowSafetyTest` with a two-level `if` fixture. The test uses the existing M1/M2 fixture factory only to construct verified predecessors, then invokes the public M3 `ControlFlowGraphBuilder` seam.
- The current bounded M3 implementation correctly refuses to claim an exact nested path: it emits one entry-owned `BRANCH_SLICE_NOT_INSTALLED` profile-stop Gap and does not emit a service-local mapper `CALL` edge/continuation from the unsupported nested body.

## Current state

The nested-guard fixture and safety assertion are written; current M3 passes by the explicit Gap branch.

## Changed files

- progress/m3-nested-guard-gap-tests.md
- src/test/java/org/sourceanalysis/app/analysis/graph/NestedGuardControlFlowSafetyTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=NestedGuardControlFlowSafetyTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

The test will accept either an exact nested CFG closure (all guard polarities and terminal paths) or a typed entry-owned Gap with no unproven call continuation. It will not require a new public Gap kind unless the current public model cannot express the safe outcome.

## Blockers

None. This slice did not require a new public Gap kind or a design change.

## Exact next action

No further action for this bounded test slice. A future Terra slice may replace the profile-stop Gap with an exact nested CFG only after a new RED and without weakening the safe fallback.

## Resume checks

- Confirm only this progress file and the test file are owned by this task.
- Re-run the direct selector before changing any expectation.
