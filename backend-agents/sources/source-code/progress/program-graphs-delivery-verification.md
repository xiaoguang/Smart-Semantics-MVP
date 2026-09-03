# Progress: ProgramGraphs delivery verification

- Status: COMPLETE
- Agent role: Delivery verification and integration
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Verify and publish the bounded, persistent ProgramGraphs M1–M6 delivery as one safe Git checkpoint; do not claim full Java control/data-flow coverage.
- Approved inputs: Published ProgramGraphs design, all current M1–M6 code/tests/progress, and user direction to merge one completed implementation stage at a time.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Ran all 20 direct ProgramGraphs test classes plus `SourceAnalysisArchitectureTest`: 90 tests passed with no failures, errors, or skips.
- Ran `spotless:check` and `git diff --check`: both passed.
- Root-caused and fixed a strict naming-gate failure by renaming four `call-graph-target-*` fixtures to `call-graph-resolution-*`; the gate was retained.

## Current state

- The delivery is a bounded M1–M6 ProgramGraphs implementation: it preserves five independently persistent graphs, reopens predecessor artifacts, records gaps, and stops data flow at the frozen-Java boundary.  The current design audit remains PARTIAL for general repository control/data-flow coverage and whole-jshERP acceptance.

## Changed files

- ProgramGraphs implementation, its direct tests and fixtures, current maturity/backlog documentation, and the associated retained progress records.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SourceAnalysisArchitectureTest,AmbiguousCallHandoffTest,CallGraphBuilderTest,CallGraphGapCarrierTest,CallGraphModulePublisherTest,CodeStructureGraphBuilderTest,CodeStructureGraphGapCarrierTest,CodeStructureGraphModulePublisherTest,ControlFlowGraphBuilderTest,ControlFlowGraphGapCarrierTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest,MultiArgumentBoundaryDataFlowTest,NestedGuardControlFlowSafetyTest,PersistedProgramGraphInputReaderTest,ProgramGraphGapProjectionTest,ProgramGraphPublicWireTest,ProgramGraphsExecutionTest,ProgramGraphsPublicationSpecifierTest,RepositoryScopeGapTest,SerialCallContinuationTest test` | PASS | 90 tests; 0 failures, errors, or skips |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | 297 Java files clean |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

- Commit this bounded ProgramGraphs delivery now as the user-requested safe stage checkpoint.  It is not the final ProgramGraphs completion claim; remaining general shapes stay in the documented backlog.

## Blockers

- None for checkpoint publication.

## Exact next action

- Start the next bounded analysis capability from the merged `origin/main` checkpoint; do not reopen this delivery merely to expand unsupported Java-flow shapes.

## Resume checks

- Read this progress file, rerun the recorded command, and inspect `git status --short` before Git publication.
