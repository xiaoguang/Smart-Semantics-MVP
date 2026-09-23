# Progress: Task 2 direct tests — Luna

- Status: IN_PROGRESS
- Agent role: Test writing
- Model: Luna / xhigh
- Started: 2026-09-23
- Last updated: 2026-09-23
- Scope: Review inherited Task 2 test changes, finish direct v2 fixture/configuration coverage, and hand off only the eight agreed test classes to the root-owned Maven slot.
- Owning plan: `docs/plans/end-to-end-business-delivery-implementation-plan.md`, Task 2.
- Approved inputs: Task 2 brief/report, large-material-reading §8, integration-contracts §§1/5/6, Terra handoff, root's test-slot allocation.
- Current branch/worktree: `codex/step05-activity-full-generation`, formal source-code checkout at Task 1 checkpoint `585c797`.

## Completed

- Read the applicable repository instructions, Task 2 contracts, private brief/report, and Terra's handoff.
- Confirmed existing Task 2 production changes are dirty workspace state and out of this worker's edit scope.
- Created this per-agent progress file before further edits.

## Current state

Inherited Task 2 tests and this worker's fixture/config/reopen additions are frozen pending root's rerun. The three reopen regressions from Astra review are present: v2 selected/unread mismatch, v1 persisted-slice/raw-decision mismatch, and historical v2 scope shrink reopened under a lower current cap. Root's first merged run reported three persistence REDs for Terra and fixture/config failures; the latter are corrected below but not yet verified. No production edits were made.

## Changed files

- `progress/e2e-activity-reliability-tests-task-2-luna.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityLargePacketFormalEntryTest.java` (inherited fixture/config coverage retained)
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityPacketFailureIsolationTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityPacketReadingConcurrencyTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityReadingCoordinatorGuardrailTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityReadingCoordinatorTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityReadingPlanPersistenceTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivitySliceAggregationTest.java`
- `src/test/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisModelJobsConfigurationTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | Completed before edits | Existing Task 2 changes are present; preserve unrelated files. |
| `git branch --show-current && git rev-parse --short HEAD` | Completed | `codex/step05-activity-full-generation`, `585c797`. |
| `git diff --check` | Completed | No whitespace errors in the scoped test changes. |
| Root's same eight-class command in session `18677` | Completed, root reported `80 tests, 3 failures, 9 errors` in `38.371s` | Three persistence REDs were forwarded to Terra. Guardrail input-budget fixture failures and one stale reflection helper were corrected afterward; not yet rerun. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityLargePacketFormalEntryTest,ActivityPacketFailureIsolationTest,ActivityPacketReadingConcurrencyTest,ActivityReadingCoordinatorGuardrailTest,ActivityReadingCoordinatorTest,ActivityReadingPlanPersistenceTest,ActivitySliceAggregationTest,SourceAnalysisModelJobsConfigurationTest test` | Did not start Maven; exit 1 | Launcher reported `JAVA_HOME environment variable is not defined correctly`; no Maven session or compile output. Root has taken over the unique Maven slot and verified the same Java path locally. |

## Decisions

- Production code is frozen; any exact test failure is reported to root for assignment to production owner.
- Do not edit Terra's progress file or run the full suite. Use the isolated verification POM and Java 17 toolchain for the agreed direct test set.

## Blockers

- This worker's initial consolidated run was blocked before Maven startup by the launcher environment check. Root owns the active slot and the rerun; corrected fixture tests are not yet verified.

## Exact next action

Hand the fixture corrections to root for formatting and the same eight-class rerun. Do not run Maven unless root explicitly reallocates the slot; if fixture failures remain, correct only those tests and re-handoff.

## Resume checks

- Re-read `git status --short` and this progress file before resuming after any pause.
- Confirm the root-allocated Maven slot before invoking Maven.

## Plan closeout destinations

- Durable decisions: Task 2 contract documentation and implementation plan.
- Remaining issues: `.workspace/end-to-end-business-delivery-20260923/task-2-tests-report.md`.
- Verification and output references: the same test report plus Task 2 delivery progress.
