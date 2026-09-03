# Progress: M2 multi-entry physical call owner union green

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Minimal M2 change so one physical Java call site reached by multiple entries has one disposition and a sorted union of owners.
- Approved inputs: Scoped AGENTS.md; Program Graphs M2.1 design; Program Graphs implementation backlog P3; existing Luna RED in CallGraphBuilderTest.
- Current branch/worktree: codex/source-analysis-program-graphs at /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Completed

- Read scoped instructions, M2.1 algorithm, and P3 backlog contract.
- Confirmed the worktree contains concurrent uncommitted ProgramGraphs work; this task will edit only M2 production code and this progress file.
- Confirmed the Luna RED with the required selector: one shared Service call node retained only the first entry owner.
- Added the minimal owner merge for duplicate physical call-node IDs. The node keeps its stable identity, canonical value, and provenance, while its owning entries become a sorted de-duplicated union.
- Changed local Call Gap aggregation to use its stable coverage candidate ID; when the same physical occurrence is reached again, it rebuilds the existing typed Gap with the union of owners and updates the one disposition.
- Ran Spotless and confirmed the Luna selector is GREEN.

## Current state

- The public M2 seam now produces one exact shared call node with the full sorted entry-owner union. The same aggregation rule applies to an M2 local Gap at a shared physical call site.

## Changed files

- progress/m2-multi-entry-owner-union-green.md
- src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED | 15 tests; the shared physical Service call node had only the first entry instead of the two-entry sorted union. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Formatted the scoped Java/test files. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 15 tests, 0 failures, 0 errors, 0 skipped. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Preserve M2.1 candidate resolution, public wire, node/gap identity, and all downstream M3–M6 behavior.
- A typed Gap identity correctly changes when owner IDs change; aggregation therefore keys transient state by the stable candidate ID and publishes one rebuilt Gap/disposition.

## Blockers

- None.

## Exact next action

- Hand off the GREEN result to the coordinator; do not commit or push from this slice.

## Resume checks

- Read this file, inspect scoped Git status, rerun CallGraphBuilderTest, then inspect CallGraphBuilder ownership propagation.
