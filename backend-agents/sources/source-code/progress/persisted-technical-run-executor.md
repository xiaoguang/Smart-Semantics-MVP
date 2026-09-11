# Progress: persisted technical run executor

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Turn one queued, persisted analysis request plus a registered frozen source and closed
  runtime configuration into Step 01–05 publications without test-side assembly.
- Approved inputs: The existing local Git source registry, persisted run request reader, Step 01
  executor, Step 02–05 coordinator, canonical stores and active runtime design.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Reviewed the exact Step 01 request boundary and the just-completed Step 02–05 coordinator.
- Added `PersistedTechnicalRunConfiguration` for bootstrap-owned frozen request bytes and typed
  profiles, and `PersistedTechnicalRunExecutor` for request reopening, source-registration
  reopening, Step 01 execution and Step 02–05 delegation.
- Added a public-seam test proving one local-Git Spring MVC/MyBatis repository can move from only
  a persisted run ID and registered capture to the Step 05 Flow publication.

## Current state

- Complete. The configuration has no filesystem path; source bytes remain behind the registered
  capture. The next adapter work must call this executor rather than reconstruct its inputs.

## Changed files

- `src/main/java/org/sourceanalysis/app/runtime/PersistedTechnicalRunConfiguration.java`
- `src/main/java/org/sourceanalysis/app/runtime/PersistedTechnicalRunExecutor.java`
- `src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowTest.java`
- `docs/analysis-steps/01-verified-source-inventory.md`
- `progress/persisted-technical-run-executor.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=TechnicalAnalysisWorkflowTest#executesTheCompleteTechnicalPrefixFromOnlyAPersistedRunAndRegisteredSource test` | RED → PASS | RED: configuration/executor types were absent. PASS: one persisted run ID reopened its registered local-Git source and produced the Step 05 Flow publication without test-side Step01–05 assembly. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=TechnicalAnalysisWorkflowTest test` | PASS | 2 tests, 0 failures/errors/skips; direct coordinator and persisted-run coordinator both pass. |

## Decisions

- Runtime configuration is an application-bootstrap dependency, not a caller-provided source path
  or a second public analysis request. It must be explicitly bound to the persisted request.

## Blockers

- None.

## Exact next action

- Compose the existing business workflow from this returned Step 05 reference, then expose the
  completed chain through `RepositoryAnalysisAgent` and the CLI.

## Resume checks

- Confirm a run that successfully reaches Step 05 can continue into business materials without
  rescanning source or rebuilding the technical graph set.
