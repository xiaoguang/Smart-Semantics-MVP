# Progress: Task 1 neutral fixture GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11T21:53:18Z
- Last updated: 2026-09-11T21:57:00Z
- Scope: Move only the generic Step05 test fixture helpers out of the retired registry-proposal test, migrate the fourteen active consumers plus the retained Flow/Capsule coverage test, and preserve their assertions.
- Approved inputs: `docs/plans/code-cleanup-and-scalable-activity-coverage-design.md` sections 2.3 and 4.1–4.2; the parent Task 1 brief; existing Luna RED contract test.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the scoped Source Code Analysis Agent instructions and the approved cleanup sections.
- Confirmed the Luna RED contract test exists and the current fourteen active consumers are precisely listed in the approved design.
- Ran the existing RED: it failed only because `BusinessFlowTestSupport` was absent.
- Added the neutral fixture and ran the same contract GREEN successfully.
- Migrated the fourteen approved current test classes and removed the legacy R0-only tail from `BusinessFlowCoverageTest` while retaining its Flow/Capsule, budget, span and eligibility assertions.
- Confirmed the fourteen active classes and the retained Flow coverage test no longer import or reference `RegistryProposalTaskCompilerTest`.
- Formatted only this task's sixteen modified test files. The independent Luna RED test was deliberately not modified.

## Current state

- The neutral fixture is now the only fixture dependency of current tests in scope. Task 1 is complete; legacy production/tests remain intentionally untouched for the next cleanup task.

## Changed files

- `progress/task1-neutral-fixture-green.md`
- `src/test/java/org/sourceanalysis/app/testsupport/BusinessFlowTestSupport.java`
- The fourteen approved current test classes in the cleanup design section 2.3.
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowCoverageTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `rg -n -l 'RegistryProposalTaskCompilerTest' src/test/java/org/sourceanalysis/app` | PASS | Located the fourteen approved active consumers plus legacy tests and the retained Flow coverage test. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowTestSupportContractTest test` | RED then PASS | Missing support class was the expected failure; new typed support satisfies the public contract. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowTestSupportContractTest,BusinessFlowCoverageTest,BusinessReportCheckpointTest,ActivityExplainerDirectEntryContextTest,ActivityExplainerTest,ActivityExplanationBudgetTest,ActivityExplanationCheckpointTest,ActivityOutputSchemaTest,ActivityPromptContractTest,BusinessMaterialBuilderFallbackTest,BusinessMaterialBuilderReplenishmentTest,BusinessMaterialBuilderTest,BusinessMaterialBuilderZeroEntryTest,ProcessKnowledgeCheckpointTest,PersistedBusinessRunExecutorTest,RepositoryAnalysisRunCoordinatorTest test` | PARTIAL | 14 current consumer classes + coverage/support compiled; 12 passed. `BusinessReportCheckpointTest` and `ProcessKnowledgeCheckpointTest` stop before report/knowledge at existing `ActivityExplainer` error `ACTIVITY_DRAFT_INVALID`. |
| scoped `spotless:check` for the sixteen owned files | PASS | No formatting violations in Task 1 files. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The neutral support exposes only the approved public publisher/reference signatures. Internal fact and profile construction stays private to prevent a new broad fixture API.
- Legacy tests retain their dependency until the later deletion task; this migration only removes the fourteen active consumers and Flow coverage from the old test class.

## Blockers

- No Task 1 blocker. The two checkpoint tests expose the later Activity v2 coverage work: their scripted DRAFT is rejected by the current `ActivityExplainer` before the test reaches knowledge/report. This task did not change production behavior and must not repair that future contract.

## Exact next action

- Hand the neutral fixture migration to the parent; next task may delete legacy semantic code only after checking active imports.

## Resume checks

- Reopen this file, check `git status --short`, run `BusinessFlowTestSupportContractTest`, then inspect imports for the approved current consumers.
