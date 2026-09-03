# Progress: repository scope and local graph gap GREEN

- Status: COMPLETE
- Agent role: Terra production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02T23:17:45-0230
- Last updated: 2026-09-02T23:33:15-0230
- Scope: Implement only the published M1 local-source Gap and M6 repository-scope Gap accounting correction.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md` at published commit `db88d31`; `RepositoryScopeGapTest` RED.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read root and scoped guidance, both implementation plans, the published M1/M6/8.0.1/8.5/8.7 contract, existing RED test, and current production seams.
- Confirmed the RED boundaries: `GraphGapDraft` currently rejects every empty owner; M1 fills unknown local ownership with all discovered entries; M6 currently rejects every nonempty `scopeGapIds` set.

## Current state

- `RepositoryScopeGapTest` is GREEN after Luna corrected its one published-design expectation: 3 tests, 0 failures/errors/skips.
- The required M3 selector reaches 53/57. Two failures are stale M1 assertions that require a fake global entry owner and `coverage.closed=false`; two errors are `GRAPH_REFERENCE_BROKEN` at M6. Initial source inspection finds CallGraph still derives a separate bounded-scope Gap ID instead of propagating M1's canonical `scopeGapIds`, so M6 correctly rejects the inconsistent fixture.
- CallGraph now copies the canonical M1 `scopeGapIds`; its obsolete independent scope-ID helper is removed. The focused scope contract remains GREEN.
- The two residual M6 failures are not scope accounting: final module install rejects one final standalone JSON payload with `MODULE_INSTALL_REQUEST_INVALID` at canonical standalone artifact identity verification. A temporary causal diagnostic was removed before completion; no diagnostic behavior remains in production.

## Changed files

- `progress/repository-scope-gap-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/GraphGapDraft.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphDraft.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphDraft.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphDraft.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphDraft.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphSetPublicationSpecifier.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RepositoryScopeGapTest test` | RED | 3 tests: 1 pass, 2 expected errors at `GraphGapDraft.forLocalOccurrence` and `ProgramGraphSetPublicationSpecifier.requireNoUnprojectableScopeGaps`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RepositoryScopeGapTest test` | BLOCKED ON TEST CONTRACT | 3 tests: 2 pass; only the published-design mismatch at `graph-index.closed` remains. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RepositoryScopeGapTest test` | PASS | 3 tests, 0 failures/errors/skips after the Luna-owned correction. |
| precise 57-test M3 selector | PARTIAL | 53/57 pass; 2 stale M1 test assertions and 2 `GRAPH_REFERENCE_BROKEN` M6 errors. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsPublicationSpecifierTest test` | DIAGNOSTIC | Confirms final standalone artifact identity validation fails in the M6 install path; temporary causal diagnostic removed afterward. |

## Decisions

- `GraphGapDraft` will normalize and identify empty owners but not decide their validity; M1 and M6 enforce the only permitted empty-owner case, while M2--M4 keep their nonempty-owner invariant.
- Scope Gap IDs remain in per-graph coverage and receipts/index union accounting, never in JSONL rows.
- M3/M4 copy the same bounded-scope ID from M1. M2 must do the same; its existing independent `scopeGap()` construction violates the published M1--M4 equality requirement and is the active M6 production correction.

## Blockers

- The two M1 selector failures are Luna-owned stale test contracts; production must not restore a fake global entry owner or make local Gaps imply incomplete repository scope.
- M6 final-public-wire payload identity is outside this local/scope Gap change and needs its assigned owner; this task deliberately does not modify unrelated M6 renderer/identity code.

## Exact next action

- Parent or the dedicated M6 public-wire owner should repair the final standalone payload identity mismatch and Luna should update the two stale M1 assertions. This task has no further authorized production work.

## Resume checks

- Verify the published design commit is still `db88d31` or its descendant.
- Re-run `RepositoryScopeGapTest` before modifying this contract again and preserve its public assertions.
