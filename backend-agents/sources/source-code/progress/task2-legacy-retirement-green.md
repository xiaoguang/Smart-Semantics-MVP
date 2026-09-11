# Progress: Task 2 legacy semantic route retirement GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Approved cleanup implementation-plan Task 2 only: retire the old interpretation model/proposal/registry/process packages and dedicated tests; remove old Flow Interpretation module registrations 1–9 and their canonical artifact-contract branches. Preserve the current business workflow and Task 1 fixture migration.
- Approved inputs: `docs/plans/code-cleanup-and-scalable-activity-coverage-design.md` §4.3; `docs/plans/coherent-code-context-implementation-plan.md` Task 2; the existing Luna RED `LegacySemanticRouteRetirementTest`.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the source-scoped instructions, TDD guidance, and the approved Task 2 contract.
- Confirmed the only pre-existing worktree changes are the Task 2 Luna RED test and its owner progress file.
- Observed the existing Luna RED: one test failed with nine still-registered old modules and a loadable retired compiler.
- Removed only the approved old module registrations, canonical artifact branches, fixture policy registrations, 78 retired production classes, and 14 dedicated retired tests.
- Confirmed the Task 1 flow/testsupport regression set and all directly affected material/activity tests remain GREEN.
- Confirmed no active source/test reference to the retired packages or artifact contracts remains, except the intentional class-name assertion in the Luna retirement guard.
- Completed Task 2 without touching Capsule registry-proposal fields, documentation, commits, or pushes.

## Current state

- Task 2 behavior is complete. The current workflow regression bundle has two pre-existing v1 ActivityExplainer coverage failures (`ACTIVITY_DRAFT_INVALID`); they are owned by approved Task 4 and are not being changed here. Spotless currently reports 19 pre-existing Task 1/current-test files needing format changes; no broad formatter was applied outside this task's scope.

## Changed files

- `progress/task2-legacy-retirement-green.md`
- `src/main/java/org/sourceanalysis/app/artifact/AnalysisStepModuleAddress.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- Removed only the four approved retired production/test package trees.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Only Task 2 RED test/progress existed before this file. |
| dependency scan | PASS | No current production consumer outside the retired packages; old module/file-contract branches are in `AnalysisStepModuleAddress`, `AtomicCanonicalPublicationEngine`, and the fixture policy list. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=LegacySemanticRouteRetirementTest test` | RED | 1 test, 1 failure with 10 grouped assertions: retired addresses/classes remained. |
| `mvn -o -t .mvn/toolchains.xml clean test -Dtest=LegacySemanticRouteRetirementTest` | PASS | Cleanly compiled 367 main and 138 test sources; 1 test, 0 failures/errors/skips. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowTestSupportContractTest,BusinessFlowCoverageTest,AnalysisStepAddressTest,LegacySemanticRouteRetirementTest test` | PASS | 4 tests, 0 failures/errors/skips. |
| material/activity direct selectors | PASS | 22 tests across Builder and Activity classes, 0 failures/errors/skips. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessReportCheckpointTest,ProcessKnowledgeCheckpointTest,PersistedBusinessRunExecutorTest,RepositoryAnalysisRunCoordinatorTest test` | EXPECTED TASK-4 RED | Runtime selectors passed 3/3; ProcessKnowledgeCheckpoint and BusinessReportCheckpoint stop at known v1 `ActivityExplainer.ACTIVITY_DRAFT_INVALID`, before their Process/Report assertions. |
| active source/test stale-route scan | PASS | No retired package import or old artifact-contract reference remains; the retirement guard intentionally contains the removed class name as a string. |
| `mvn -o -t .mvn/toolchains.xml spotless:check` | KNOWN OUT-OF-SCOPE FAILURE | 19 Task 1/current-test files need formatting; no Task 2-only formatting violation was reported. |
| `git diff --check`; `git diff --cached --check` | PASS | No whitespace errors in unstaged Task 2 updates or staged deletions. |

## Decisions

- Do not change Capsule registry-proposal fields; that is explicitly Task 3.
- Do not change durable design documents in this implementation unit.

## Blockers

- None for Task 2. The v1 ActivityExplainer requires complete DRAFT entry coverage, which is approved Task 4 work. Global formatting of 19 Task 1/current-test files is also deferred to its owner or a separate formatter work unit.

## Exact next action

- Parent agent can incorporate the staged removals and unstaged Task 2 changes into the approved cleanup delivery, then start Task 3 only after deciding ownership of the existing formatting failures.

## Resume checks

- Re-read this file, check `git status --short`, and rerun `LegacySemanticRouteRetirementTest` if the staged removals or module contracts change.
