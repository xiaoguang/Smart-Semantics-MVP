# Progress: analysis run output

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Persist four existing business checkpoint references as the result of a successful run,
  so public inspection can find output without copying Markdown or creating a recovery subsystem.
- Approved inputs: Existing run registry, final-document execution seam, and canonical module
  publication references.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed that each business Module already persists its own immutable checkpoint. The missing
  piece is only one run-owned pointer to those four existing publications.

## Current state

- The output manifest is implemented and reopened only after a run reaches `FINISHED`. The public
  test now uses typed synthetic publications owned by that queued run; it does not pretend that a
  separately published fixture belongs to the new execution.

## Changed files

- `progress/analysis-run-output.md`
- `src/main/java/org/sourceanalysis/app/runtime/AnalysisRunOutput.java`
- `src/main/java/org/sourceanalysis/app/runtime/RunInspection.java`
- `src/main/java/org/sourceanalysis/app/runtime/LocalRepositoryAnalysisAgent.java`
- `src/main/java/org/sourceanalysis/app/artifact/AnalysisRunRegistry.java`
- `src/main/java/org/sourceanalysis/app/artifact/FileSystemAnalysisRunRegistry.java`
- `src/main/java/org/sourceanalysis/app/artifact/RunStoreBootstrap.java`
- `src/test/java/org/sourceanalysis/app/runtime/LocalRepositoryAnalysisAgentExecutionTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=LocalRepositoryAnalysisAgentExecutionTest test` | RED | The test fixture used a different run ID from the queued run; `ANALYSIS_RUN_OUTPUT_INVALID` proves the manifest refuses cross-run pointers. |
| `mvn -Dtest=LocalRepositoryAnalysisAgentExecutionTest,RepositoryAnalysisAgentStartTest,AnalysisRunRegistryTest test` | PASS | 5 tests passed; a finished public run fresh-inspects the four owned checkpoint references. |

## Decisions

- The manifest stores module publication references only. It does not duplicate JSON/Markdown,
  save model packets, or attempt to re-run a failed Provider call.
- The run-owned manifest rejects cross-run checkpoint pointers before the state moves to `FINISHED`.

## Blockers

- None.

## Exact next action

- Add the read-only document and source-reference query seam using these output pointers.

## Resume checks

- A run without `FINISHED` state must not present output; a finished run must have all four typed
  checkpoint references on the same run identity.
