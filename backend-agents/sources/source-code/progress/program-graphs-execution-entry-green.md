# Progress: ProgramGraphs execution entry GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Implement the minimal persisted M1–M6 ProgramGraphs execution seam required by the existing public RED.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`, `docs/supplements/program-graphs-implementation-backlog.md`, both implementation plans, `ProgramGraphsExecutionTest`, and existing graph executors/readers/publishers.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the scoped rules, approved ProgramGraphs module/handoff contract, backlog P1, implementation plans, and the public RED.
- Added the minimal `ProgramGraphsExecution` composition seam. It accepts only fresh verified-source/application-discovery references, a graph-profile reference, and controls; it publishes and fresh-reopens M1 through M6 in dependency order.
- Corrected the existing M3 input reader's identity check without relaxing it: the canonical `application-profile.json` body `artifactId` must equal its descriptor artifact ID, while the separate detector semantic `applicationProfileId` must equal the capability report's `applicationProfileId`. The reader retains both checks and uses the descriptor reference downstream.

## Current state

- The execution seam is GREEN. It fresh-reopens each persisted predecessor before the next graph builder, publishes the exact M1–M6 module sequence, and returns the single fresh-reopenable ProgramGraphs publication.

## Changed files

- `progress/program-graphs-execution-entry-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsExecution.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/PersistedProgramGraphInputReader.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsExecutionTest test` | RED (recorded by Luna) | Missing `ProgramGraphsExecution`; test fixture and intended public seam compile otherwise. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsExecutionTest test` | BLOCKED | Main/test compilation succeeds; fixture fails before the seam because `temporaryDirectory/fixture` does not exist for strict `openForTest`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsExecutionTest test` | PASS | 1 test; the execution chain reaches and fresh-reopens the M6 public publication under strict profile identity checks. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphModulePublisherTest,CallGraphModulePublisherTest,ControlFlowGraphBuilderTest,DataFlowGraphBuilderTest,EvidenceGraphBuilderTest,PersistedProgramGraphInputReaderTest,ProgramGraphsPublicationSpecifierTest,ProgramGraphPublicWireTest,ProgramGraphGapProjectionTest,ProgramGraphsExecutionTest test` | PASS | 47 tests; direct M1–M6 publication, persisted-reader, public-wire, and execution coverage is green. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | No source files changed on the final formatting run. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The execution seam will remain package-local to the graph package until the approved global `RepositoryAnalysisAgent` work item exists.
- It will construct internal M1–M6 typed inputs only after each predecessor module has been persisted and fresh-reopened.

## Blockers

None.
## Exact next action

- Hand the implementation and verification evidence to the parent orchestrator. Do not stage, commit, or push.

## Resume checks

- Run the public RED selector first if production code needs to be revisited; then run the direct M1–M6 affected publication selectors serially, Spotless, and `git diff --check`.
- Do not edit tests, design, POM, another agent's progress file, stage, commit, or push.
