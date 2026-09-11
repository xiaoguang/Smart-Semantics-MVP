# Progress: fixed jshERP control-flow index

- Status: IN_PROGRESS
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Diagnose and correct the generic control-flow method-index collision exposed by the
  explicitly authorized 8 GiB fixed-repository Step01–05 replay. Own only the direct
  `ControlFlowGraphBuilder` regression, implementation, this progress file, and the existing
  fixed-repository preflight progress record.
- Approved inputs: Current Step03 design, current public graph contracts, fixed jshERP commit,
  and the persisted report from the 8 GiB opt-in offline run.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Reproduced the 8 GiB replay failure after successful capture, inventory, and discovery:
  `ControlFlowGraphBuilder.Index.parseMethods` rejected a duplicate method signature before
  Step03 publication.
- Used a read-only temporary archive of the same frozen commit and a JavaParser diagnostic that
  mirrors the failing index. It found repeated nested `Criterion` types in generated `*Example`
  classes. The control index names a nested type only by package plus simple name, so methods such
  as `Criterion#getCondition()` collide across unrelated enclosing classes.
- Confirmed the preceding code-structure and call builders deliberately index only
  `CompilationUnit.getTypes()` (top-level types); they do not publish nodes for nested types.
- Added a direct nested-type regression. Before the correction it failed at the same
  `ControlFlowGraphBuilder.Index.parseMethods` duplicate-signature gate as the fixed repository.
- Changed the control index to use `CompilationUnit.getTypes()`, matching the persisted
  code-structure/call-graph domain. The regression and the direct control-flow suite are green.
- The next 8 GiB replay passed that duplicate-declaration gate and reached entry traversal. It
  exposed a second signature mismatch: control flow read a directly imported entry parameter as a
  simple name while the persisted structure graph used its imported FQN.
- Added a direct imported-entry-parameter regression. It failed at the same
  `structureMethodId` boundary, then passed after the control index adopted the established
  direct-import spelling rule used by the structure and call graphs. The direct suite is green.

## Current state

- Hypothesis confirmed: the control index must use the same top-level declaration domain as the
  preceding two graphs. It must not attempt to index nested methods that lack a structure-graph
  node.

## Changed files

- `progress/fixed-jsherp-control-flow-index.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilder.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Fixed repository Step01–05, 8 GiB | Reproduced | 719 files captured; Step01/02 succeeded; `GRAPH_REFERENCE_BROKEN` at ControlFlowGraphBuilder line 1208. |
| Read-only JavaParser duplicate-signature diagnostic | Root cause confirmed | Nested generated `Criterion` methods collide because their enclosing type is omitted from the index identity. |
| Nested-type direct regression before implementation | RED | Fails with `GRAPH_REFERENCE_BROKEN` at the same duplicate-signature index boundary. |
| Nested-type direct regression after implementation | PASS | 1 test, 0 failures/errors/skips. |
| `ControlFlowGraphBuilderTest` | PASS | 17 tests, 0 failures/errors/skips. |
| Imported entry parameter regression before implementation | RED | Fails at `structureMethodId` because `BigDecimal` differs from `java.math.BigDecimal`. |
| Imported entry parameter regression after implementation | PASS | 1 test, 0 failures/errors/skips. |
| `ControlFlowGraphBuilderTest` after import alignment | PASS | 18 tests, 0 failures/errors/skips. |

## Decisions

- Align the control-flow parser with the persisted code-structure/call graph domain. This is not a
  jshERP rule and does not add a business interpretation heuristic.
- Use the same direct-import type spelling for every graph that references persisted method
  signatures. Wildcard imports remain unresolved rather than guessed.

## Blockers

- None.

## Exact next action

- Run one fresh 8 GiB fixed-repository replay in a new empty ignored workspace. Preserve its
  report whether it succeeds or exposes the next generic boundary.

## Resume checks

- Reopen the retry-10 report and confirm capture/Step01/Step02 remain successful.
- Rerun only the new direct control-flow selector before any further fixed-repository replay.
