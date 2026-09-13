# Progress: parallel-activity-implementation

- Status: COMPLETE
- Agent role: Activity parallel implementation
- Model: inherited Codex execution model
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Approved Step 2 Activity-only bounded Java 17 parallel execution in `ActivityExplainer`, effective global/single-Provider cap injection through `RepositoryRunMain`, run-private reviewed-job saving, and journal/task-ID isolation; do not modify configuration parsing, API, or Process parallelism.
- Approved inputs: Root and module `AGENTS.md`; `docs/modules/model-job-execution.md`; existing `ActivityExplainer`, `StructuredModelProvider`, `RunJournalStructuredProvider`, and `ParallelActivityExplainerTest`.
- Current branch/worktree: shared formal `linguan-prototype-v2/backend-agents/sources/source-code` worktree; preserve pre-existing changes.

## Completed

- Read the required repository guidance, model-job execution contract, implementation seams, and the two direct RED tests.
- Confirmed the current `ActivityExplainer` has a serial per-material DRAFT/REVIEW loop and one final stable aggregation/publish point.
- Added and GREENed direct coverage for the configured cap, private immutable reviewed-job records, job-specific task IDs, fatal aggregate suppression, and composition-root mapping.

## Current state

The existing direct test is untracked work owned elsewhere. It requires default four in-flight Activity jobs, per-material serial DRAFT then complete REVIEW, stable material aggregation, and stopping new dispatch after a fatal while valid already-started jobs finish their one REVIEW. It is GREEN. The configured Activity test verifies the injected cap, task/journal isolation, successful immutable private records, and fatal non-publication while preserving valid started-job records. The composition root now maps the selected Codex Provider's effective cap, stable binding, quota scope, journal directory, run ID, and expected identity for generation and Activity sample execution; the five direct Step 2 tests were GREEN. A later no-source-change retry is blocked only by a pre-existing stale generated `target/test-classes/.../ActivityJobExecutionConfigurationTest.class` containing an Eclipse unresolved-type stub; the root task owns targeted generated-artifact recovery.

## Changed files

- `progress/parallel-activity-implementation.md` — this progress record only.
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainer.java` — prepares eligible material jobs, delegates execution, and aggregates completed results by material ID.
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityJobCoordinator.java` — bounded completion-queue coordinator plus the internal job, result, binding, and completion-sink types.
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityJobExecutionConfiguration.java` — parsed-to-Activity nonsecret execution values, including the expected runtime identity.
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityJobPrivateResultStore.java` — run-private, atomic no-replace REVIEW record writer.
- `src/main/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutor.java` — optional configured Activity execution seam while retaining the legacy in-memory default.
- `src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMain.java` — formal composition-root mapping into the Activity execution seam for generation and sample modes.
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityJobExecutionConfigurationTest.java` — direct cap, private-save, identity, and fatal behavior coverage.
- `src/test/java/org/sourceanalysis/app/adapter/cli/RepositoryRunModelJobsConfigurationTest.java` — direct composition-root mapping contract.
- `docs/modules/model-job-execution.md` and `docs/analysis-steps/06-flow-interpretation.md` — current Step 2 implementation status.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Required-source review | PASS | Read all required design and implementation inputs before editing. |
| `mvn -Dtest=ParallelActivityExplainerTest test` | BLOCKED (superseded) | Maven omitted the repository toolchain and stopped in toolchain selection. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=ParallelActivityExplainerTest test` | RED | Both tests failed at their expected serial-dispatch barriers before production changes. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=ParallelActivityExplainerTest test` | GREEN | 2 tests passed in 8.072 seconds after the bounded parallel path. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=ActivityJobExecutionConfigurationTest test` | RED | Expected `ACTIVITY_JOB_EXECUTION_CONFIGURATION_NOT_IMPLEMENTED` before the configuration seam existed. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=ActivityJobExecutionConfigurationTest,ParallelActivityExplainerTest test` | GREEN | All 4 direct Activity tests passed after the configured cap, runtime identity checks, and atomic private result sink were added. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=RepositoryRunModelJobsConfigurationTest#activityExecutionConfigurationUsesTheSelectedProviderEffectiveCapAndRuntimeIdentity test` | RED | Expected failure: no `RepositoryRunMain.activityJobExecutionConfiguration` mapping existed. |
| Same composition-root selector | GREEN | 1 test passed after formal composition-root injection. |
| `mvn -q -t .mvn/toolchains.xml -Dtest=ParallelActivityExplainerTest,ActivityJobExecutionConfigurationTest,RepositoryRunModelJobsConfigurationTest#activityExecutionConfigurationUsesTheSelectedProviderEffectiveCapAndRuntimeIdentity test` | GREEN | 5 tests passed (2 parallel, 2 configured Activity, 1 composition-root). |
| Scoped `mvn -q -t .mvn/toolchains.xml spotless:check -DspotlessFiles=…` | PASS | Checked only this work's eight Java source/test files. |
| `git diff --check` | PASS | No whitespace errors. |
| Later no-source-change retry after the hard-link portability catch | BLOCKED | JUnit discovers a stale generated test class whose descriptor references default-package `ProgramGraphsPublicFixture`; the correctly packaged fixture source and class are present. Root will replace only that generated stub, not clean the worktree. |

## Decisions

- Keep `ActivityExplainer` as the caller-facing deep Module. Add only an internal coordinator seam that schedules whole material jobs; it will not own prompts, schemas, business fields, or aggregate publication.
- Use a bounded executor and completion queue. The initial adapter binds every job to the existing single Provider and uses a cap of four; later global/per-provider caps use the job's stable binding key, stable routing fixes that key before dispatch, and per-job private saving uses the completion sink without changing the public constructor.
- The current root direction authorizes the remaining Activity wiring now: consume already-parsed runtime limits rather than parsing YAML in Activity; atomically save a completed REVIEW before its permit is reused; keep the public aggregate at the existing single publication point.

## Blockers

- No source blocker. One later verification retry awaits root-owned replacement of a stale generated test-class stub in shared `target/`; no source change follows the prior 5/5 GREEN result.

## Exact next action

Hand off the implementation files, 5/5 direct-test evidence, later generated-class retry blocker, and remaining non-Activity Step 2 gaps to the root task.

## Resume checks

- Re-read this file and `git status --short`.
- Re-run only the direct Activity and composition-root selectors before continuing implementation.
