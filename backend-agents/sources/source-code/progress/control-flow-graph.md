# Progress: control flow graph

- Status: IN_PROGRESS
- Agent role: Luna/xhigh RED and Terra/xhigh GREEN under the published Program Graphs M3 contract
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Build the bounded entry-rooted control-flow draft from sealed M1/M2 graphs and same frozen inputs. Start with exact entry, branch polarity, and terminal behavior only.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md` at `dd5c4c1`, both implementation plans, and the sealed M1/M2 reopening contracts.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Confirmed M3 may consume only `ReopenedCodeStructureGraph`, `ReopenedCallGraph`, and the same `ReopenedProgramGraphInputs`; no raw drafts or worktree paths.
- Rebased this worktree onto the published M3 contract. The contract fixes the entry/callee
  return distinction, bidirectional M2 call-pair projection, per-entry reachability, and typed
  profile-stop disposition rules before production code begins.
- Added the public M3 records and first linear control-flow builder slice only after the expected
  absent-seam RED. The test builds M1/M2 through the real canonical store and feeds only sealed
  fresh-reopened aggregates into M3.

## Current state

- M2 closure is now installed and its direct tests pass. The linear M3 seam is green: it
  fresh-reopens M1/M2 from the canonical store, checks shared basis/profile/structure lineage, and
  projects the exact M2 call/return pairs into one entry-rooted traversal.

## Changed files

- `progress/control-flow-graph.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ControlFlow*.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Expected initial RED | Missing public M3 builder/types. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Blocked by M2 predecessor | Fresh M2 call-site evidence reference has no declared provenance; M3 fails closed with `GRAPH_REFERENCE_BROKEN`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 1 test, 0 failures/errors/skips; one frozen entry exposes typed entry, basic/callee/entry terminals and exact M2 call/return projections. |

## Decisions

- M3 will be implemented in vertical slices; it will not execute customer code or infer business outcomes.
- M3 will not recreate or relax M2 evidence; M2 is repaired at its producing seam before this test
  may become green.
- The first vertical slice sends `if` constructs to a typed profile-stop Gap. It does not yet
  claim guard polarity, throws, loop accounting, multi-entry traversal ownership, module
  publication, or full DFS/reachability closure.

## Blockers

- None for the linear slice. The next RED is the true/false guard and normal-fall-through
  contract; it requires no architecture change.

## Exact next action

- Publish the current implementation audit, then add one conditional Java fixture and a direct
  TRUE/FALSE/terminal RED.

## Resume checks

- Read this file, run `git status --short`, confirm `259b03d` is an ancestor, validate the M2
  closure selector, then run the direct M3 selector recorded above.
