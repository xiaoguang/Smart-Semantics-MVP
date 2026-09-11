# Progress: Program Graph local-Gap coverage implementation

- Status: COMPLETE
- Agent role: Terra/xhigh graph coverage GREEN implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Apply the published ProgramGraphs local-Gap coverage predicate to only the ControlFlow and DataFlow graph builders. Each coverage `closed` value must derive from the copied shared scope Gap set, not local Gap disposition emptiness or repository eligibility.
- Approved inputs: Published Step 03 accounting rule, current CallGraphBuilder correction, and frozen Luna REDs for the two actual builders.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Created this tracked progress checkpoint before production edits.
- Recorded the frozen REDs: `ControlFlowGraphBuilder` and `DataFlowGraphBuilder` each ran 1 test with 1 failure, 0 errors, and 0 skips (numeric exit 1). Their real `COMPLETE_CAPTURE`/eligible source has empty shared `scopeGapIds` and nonempty local Graph Gap content, proving the current local-Gap-and-eligibility predicate incorrectly emits `closed=false`.
- Confirmed the published Step 03 rule already used by the corrected CallGraph builder: graph coverage is `closed == scopeGapIds.isEmpty()`. Local Graph Gaps remain local disposition/accounting data and do not determine repository coverage.

## Current state

- This graph-only slice is complete. Its two direct graph coverage methods passed in the parent-coordinated combined selector; the aggregate’s only remaining failure was the separately scoped compiler-only raw `ENTRY` normalization RED.

## Changed files

- `progress/program-graph-local-gap-coverage-implementation.md` (owned implementation and verification record)
- `src/main/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilder.java` (copied scope-Gap predicate only)
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilder.java` (copied scope-Gap predicate only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Frozen Luna ControlFlow RED | RED; numeric exit 1 | 1 test, 1 failure, 0 errors, 0 skips at the coverage assertion after valid complete/eligible/empty-scope/nonempty-local-Gap prerequisites. |
| Frozen Luna DataFlow RED | RED; numeric exit 1 | 1 test, 1 failure, 0 errors, 0 skips at the coverage assertion after valid complete/eligible/empty-scope/nonempty-local-Gap prerequisites. |
| Absolute two-file Spotless apply | GREEN; numeric exit 0 | Both owned graph builders were selected; 1 changed to be clean and 1 was already clean. |
| Absolute two-file Spotless check | GREEN; numeric exit 0 | Both selected graph builders required no changes. |
| Parent-coordinated combined direct selector | Overall expected RED; numeric exit 1 | 9 tests, 1 failure, 0 errors, 0 skips. The ControlFlow coverage method passed (1 test, 0 failures/errors/skips) and the DataFlow coverage method passed (1 test, 0 failures/errors/skips); bounded public closure (1 test) and the five pre-existing provenance methods also passed. The only failure was the separately scoped new compiler-only raw `ENTRY` normalization RED. |

## Decisions

- Replace only `localGapEmpty && repositoryCompletionEligible` with `scopeGapIds.isEmpty()` at the two M3 builder coverage-constructor sites. Preserve all local Gaps, dispositions, nodes, edges, IDs, schema, and algorithms.
- Do not weaken coverage validation, add a schema/version, alter reader behavior, create a compatibility branch, or mix compiler-origin Gap normalization into this slice.

## Blockers

- Await parent confirmation that Luna’s compiler-only Gap test file is frozen before test compilation or verification. Exact two-file Spotless formatting is complete.

## Exact next action

- No further action in this completed graph-only slice. Do not modify or test the separately scoped compiler normalization work from this record.

## Resume checks

- Maven lease is released. Full Step 05 remains unaccepted; this was a narrow Step 03 coverage correction only.
