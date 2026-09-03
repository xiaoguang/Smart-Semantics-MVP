# Progress: M3 serial continuation audit sync

- Status: COMPLETE
- Agent role: Design authority / implementation audit
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Record the bounded, verified M3 sequential-call continuation behavior without expanding the ProgramGraphs contract.
- Approved inputs: Published ProgramGraphs design, current M3 implementation and direct test results; user-approved Java-only graph boundary.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Re-read the M3 implementation, sequential-call regression, and current maturity audit.
- Verified the direct M2--M6 selector: 65 tests passed; `git diff --check` passed.
- Updated the M3 current-implementation audit and the ProgramGraphs backlog with the exact bounded behavior and remaining exclusions.

## Current state

- The implementation proves only two or more exact call sites in distinct top-level lexical blocks.  A call-site `NEXT` edge reaches the immediate next block only when the selected callee has a known normal exit.  The predecessor block no longer bypasses the call anchor.
- Nested calls, multiple calls in one statement/block, branch joins, loops, exceptions, and general cross-call reachability remain unsupported or partial.

## Changed files

- `docs/analysis-steps/03-program-graphs.md`
- `docs/supplements/program-graphs-implementation-backlog.md`
- `progress/m3-serial-continuation-audit-sync.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest,CallGraphModulePublisherTest,ControlFlowGraphBuilderTest,ControlFlowGraphGapCarrierTest,NestedGuardControlFlowSafetyTest,SerialCallContinuationTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest,AmbiguousCallHandoffTest,ProgramGraphPublicWireTest,ProgramGraphsPublicationSpecifierTest test` | PASS | 65 tests; 0 failures, errors, or skips |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest,ControlFlowGraphGapCarrierTest,NestedGuardControlFlowSafetyTest,SerialCallContinuationTest test` | PASS | 19 tests; 0 failures, errors, or skips |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

- This is a maturity-audit update, not a target-architecture or public-contract change.  It will not create a design-publication-only commit.

## Blockers

- None.

## Exact next action

- Continue the next bounded ProgramGraphs capability without widening the sequential-call rule beyond its documented shape.

## Resume checks

- Read this file, inspect `ControlFlowGraphBuilder.java` continuation code, and rerun the selector recorded above.
