# Progress: repository analysis run coordinator

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Compose the persisted technical executor and the persisted business executor into one
  internal runId-to-nine-section continuation without adding another public interface.
- Approved inputs: Existing persisted Step 01–05 and Step 06–08 executors, active runtime design,
  and deterministic local fixtures only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the two executors have a closed typed handoff: `TechnicalAnalysisWorkflowResult` owns
  the only `BusinessFlowsReference` that the business continuation accepts.
- Added `RepositoryAnalysisRunCoordinator` and `RepositoryAnalysisRunResult`. The coordinator
  starts from one `AnalysisRunId`, executes the registered technical prefix once, and forwards only
  its returned persisted Flow publication to the business continuation.
- Added a direct seam test proving the forwarding occurs exactly once and preserves Flow lineage.

## Current state

- Complete. The coordinator is still application-internal; the sole public Agent must next own the
  configured composition and expose execution without leaking bootstrap dependencies.

## Changed files

- `progress/repository-analysis-run-coordinator.md`
- `src/main/java/org/sourceanalysis/app/runtime/RepositoryAnalysisRunCoordinator.java`
- `src/main/java/org/sourceanalysis/app/runtime/RepositoryAnalysisRunResult.java`
- `src/test/java/org/sourceanalysis/app/runtime/RepositoryAnalysisRunCoordinatorTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=RepositoryAnalysisRunCoordinatorTest test` | PASS | 1 test, 0 failures/errors/skips; coordinator invoked each continuation once and forwarded the exact persisted Flow publication. |

## Decisions

- The coordinator is internal composition, not a replacement for the sole public
  `RepositoryAnalysisAgent`; the public interface grows only after this behavior exists.

## Blockers

- Public `RepositoryAnalysisAgent` execution, CLI, and real fixed-repository capacity/quality
  validation remain future runtime work.

## Exact next action

- Bind the coordinator into the sole public Agent with application-owned configuration.

## Resume checks

- Confirm the public Agent exposes only a queued run request and runId execution, not a source
  path, a model packet, or bootstrap configuration.
