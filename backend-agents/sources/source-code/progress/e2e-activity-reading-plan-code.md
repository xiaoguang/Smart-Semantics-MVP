# Progress: End-to-end Task 2 reading-plan protocol and configuration

- Status: IN_PROGRESS
- Agent role: Task 2 production-code implementer (Terra owns tests; root owns direction, debug, review, and build-slot coordination)
- Model role: Sol / xhigh
- Started: 2026-09-23
- Last updated: 2026-09-23 (configuration and strengthened historical-v1 verticals GREEN)
- Scope: Task 2 only — explicit final reading scopes first, then `sourceAnalysis.activityReading` and execution-config persistence
- Owning plan: docs/plans/end-to-end-business-delivery-implementation-plan.md, Task 2
- Approved inputs: task-2-brief.md; large-material-reading section 8; integration-contracts sections 1/5/6; existing configuration contracts and scripted fixtures
- Current branch/worktree: codex/step05-activity-full-generation; Task 1 checkpoint 585c797; preserve all pre-existing worktree changes

## Completed

- Read the complete Task 2 brief and the named plan/design/module authority.
- Re-loaded inline plan execution and TDD instructions; production implementation remains gated on a confirmed first-vertical RED.
- Confirmed exclusions: no Task 3 coverage/reuse-only/Step07 work, product calls, JDT/Builder, historical artifact edits, subagents, commits, or pushes.
- Confirmed the first protocol RED: the seven-field v2 response was rejected by the old exact four-field response contract (`19 tests / 1 failure / 0 errors`).
- Implemented the first protocol vertical: prompt/schema v2, temporary full-decision validation, explicit final keys and supersession validation, separate historical diagnostics/current issues, and plan-v2 persistence.
- Proved the exact explicit distinct-key replacement case GREEN (`1 test / 0 failures / 0 errors`); the rejected broad scope remains historical while its two valid replacements are the only final scopes and no third read occurs.
- Preserved the existing same-key capability after a genuine public RED: an oversized new definition remains a current capacity issue, while the most recent executable older definition remains the frozen packet; ordered selection is entry-first and stable.
- Made new saved plan-v2 state strict and replayable without a Provider: final keys, dispositions, finish intent, current issues, incompleteness, and executable packets must match the original raw decisions and verified material.
- New DIRECT plans now save a complete plan-v2 slice/packet and final-scope state instead of a header-only provisional v1 record.
- DIRECT reopen validates and reuses the exact verified whole packet; it does not pass the full packet through selective compaction and therefore preserves call positions and argument associations byte-for-byte.
- Complete historical paged v1 SELECTED/SLICED records have a finite strict coordinator reader that preserves the original record/slices without inventing v2 final or completion state; incomplete header-only legacy DIRECT remains outside this reader.
- Strengthened historical-v1 validation accepts the old producer's saved successful-slice caller closure and verifies `UPSTREAM_UNAVAILABLE` dispositions against the original packet's unavailable unit reference/reason; it still rejects a missing declared reading packet without Provider calls.
- Repository configuration v3 resolves strict optional `sourceAnalysis.activityReading` limits with 128/4/32 defaults while strict v2 remains readable. A configuration without `business.activity` still validates explicit limits but does not fabricate input/output capacity or install an Activity profile.
- The effective reading profile flows through `RepositoryRunConfiguration` and `ModelJobExecutionConfiguration` into `ActivityExplainer`. Step05 execution configuration now writes v5 with all five effective values and its reuse reader accepts strict v4/v5 shapes.
- `activityReading` is excluded from the Step05 base-configuration digest. The three runtime bounds are excluded from reading semantic identity while prompt/schema and actual input/output capacities remain fingerprinted.
- Private reading inputs now expose `currentOpenScopeIssues` recomputed from the current final scopes and separate `historicalDiagnostics`; explicitly superseded capacity warnings remain preserved evidence but no longer drive later selection.

## Current state

- Production is frozen after the strengthened historical-v1 GREEN; the unique Maven slot is released to Terra for the remaining Task 2 negative/configuration fixture regression.
- The first whole-class run after switching the formal runtime contract was intentionally not treated as GREEN: `19 tests / 3 failures / 13 errors`; most failures were obsolete four-field fixtures, while the first prompt draft also exceeded several old capacity fixtures.
- The v2 prompt was reduced from 3,920 to 2,146 bytes without dropping approved final-scope semantics. The exact new behavior then passed under its original configured capacity.
- Terra moved the same-key tests to public coordinate behavior; the temporary private compatibility seams were removed.
- Configuration source and runtime wiring are implemented in `RepositoryRunConfiguration`, `SourceAnalysisExecution`, `ModelJobExecutionConfiguration`, `ActivityExplainer`, `ActivityReadingProfile`, and the coordinator fingerprint.

## Changed files

- progress/e2e-activity-reading-plan-code.md (this Task 2 handoff only)
- src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityReadingCoordinator.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityPromptCatalog.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainer.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityReadingProfile.java
- src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunConfiguration.java
- src/main/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisExecution.java
- src/main/java/org/sourceanalysis/app/runtime/modeljob/ModelJobExecutionConfiguration.java
- src/main/resources/org/sourceanalysis/app/analysis/interpretation/activity/activity-reading-plan-v2.txt

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Task 1 is checkpointed; only root/Terra plan progress, isolated POM, and unrelated research files remain dirty/untracked. |
| Terra isolated `ActivityReadingCoordinatorGuardrailTest` RED | EXPECTED RED | 19 tests, 1 failure, 0 errors; seven-field response stopped at old exact field validation. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingCoordinatorGuardrailTest test` | EXPECTED MIGRATION FAILURES | 19 tests, 3 failures, 13 errors; identified obsolete four-field scripted fixtures, initial prompt-capacity regression, and two private reflection fixtures. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingCoordinatorGuardrailTest#explicitDistinctScopeReplacementClearsOnlyTheSupersededCapacityObligation test` | PASS | 1 test, 0 failures, 0 errors; build success in 53.351 s after prompt compaction. |
| Terra migrated `ActivityReadingCoordinatorGuardrailTest` | PASS | 19 tests, 0 failures, 0 errors in 25.420 s; formal seven-field fixtures and public same-key tests. |
| Terra corrected same-key retention expectation | EXPECTED RED | 19 tests, 2 failures, 0 errors; old executable scope was lost and `Set.copyOf` reordered selected units. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingCoordinatorGuardrailTest test` | PASS | 19 tests, 0 failures, 0 errors; build success in 35.887 s after retention/order fixes. |
| Terra isolated `ActivityReadingPlanPersistenceTest` v2 reopen RED | EXPECTED RED | 2 tests, 1 failure, 0 errors; removing `finalSliceKeys` was silently accepted. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingPlanPersistenceTest test` | PASS | 2 tests, 0 failures, 0 errors; build success in 45.356 s with valid zero-Provider reopen and strict missing/tampered rejection. |
| Terra isolated DIRECT exact-roundtrip RED | EXPECTED RED | 1 test, 0 failures, 1 error; reopen rebuilt the whole packet through selective compaction and rejected the saved original packet. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingPlanPersistenceTest#reopensDirectV2PlanWithTheExactOriginalWholePacketIncludingCalls test` | PASS | 1 test, 0 failures, 0 errors; build success in 56.597 s with original full packet and strict tamper rejection. |
| Terra isolated historical paged-v1 RED | EXPECTED RED | 1 test, 0 failures, 1 error; the complete 13-field v1 record was rejected by the v2-only schema gate. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingPlanPersistenceTest#reopensACompleteHistoricalPagedV1RecordAndRejectsItsMissingDeclaredPacket test` | PASS | 1 test, 0 failures, 0 errors; build success in 1:01 with exact v1 record preservation and missing packet rejection. |
| Terra configuration v3 RED | EXPECTED RED | 1 test, 1 failure, 0 errors; v3 stopped at the old v2-only schema check. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=SourceAnalysisModelJobsConfigurationTest#sourceAnalysisActivityReadingResolvesDefaultsAndCustomLimitsWithoutChangingStep05Basis test` | PASS | 1 test, 0 failures, 0 errors; session 55848, build success in 47.023 s with default/custom limits, stable Step05 basis, and no Provider launch. |
| Terra strengthened historical-v1 RED | EXPECTED RED | 1 test, 0 failures, 1 error at `requireHistoricalPagedV1`; the old producer's saved caller closure and upstream-unavailable disposition were rejected. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingPlanPersistenceTest#reopensACompleteHistoricalPagedV1RecordAndRejectsItsMissingDeclaredPacket test` | PASS | 1 test, 0 failures, 0 errors; session 5791, build success in 45.622 s with legal closure/unavailable preservation and missing-packet hard rejection. |
| Terra current/history input separation RED | EXPECTED RED | 1 test, 1 failure, 0 errors; the third request lacked `currentOpenScopeIssues`, before its historical assertion was reached. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingCoordinatorGuardrailTest#subsequentReadingInputKeepsHistoricalCapacityDiagnosticsOutOfCurrentSupersededScopeIssues test` | PASS | 1 test, 0 failures, 0 errors; session 79576, build success in 1:12 with current issues excluding the replaced old scope and historical diagnostics retaining it. |

## Decisions

- Implement Task 2 vertically: final-scope protocol and frozen plan first; configuration only after scope semantics are green.
- Historical diagnostics remain append-only evidence. Current completion/continuation must derive from explicit final keys, supersession dispositions, material availability, and navigation state—not accumulated unknown prose.
- Task 3 remains the owner of coverage-v4, packetCompletion, reuse-only CLI, and historical completion classification.

## Blockers

- Awaiting Terra's remaining final-scope/configuration negative tests and the seven-class fixture regression. Maven slot is released to Terra.

## Exact next action

- Accept the next precise RED from Terra; do not reopen the completed configuration or historical-v1 vertical without failing evidence.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm the current vertical's RED is a behavioral failure, not fixture/compile/environment noise.
- Confirm root/Terra has handed off the unique Maven slot before running a direct test.

## Plan closeout destinations

- Durable decisions: docs/modules/activity-explanation/large-material-reading.md, integration-contracts.md, and model-job-execution.md current facts
- Remaining issues: Task 3 coverage-v4/reuse-only/Step07 admission and any product acceptance remain separate
- Verification and output references: .workspace/end-to-end-business-delivery-20260923/task-2-code-report.md

Keep this handoff while the plan is active. At whole-plan closeout, consolidate
the information above into its durable destinations and remove the temporary
task file; do not archive a second copy of the progress record.
