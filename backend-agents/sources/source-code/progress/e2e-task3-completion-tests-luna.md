# Progress: Task 3 retry/carry-forward audit tests

- Status: IN_PROGRESS
- Agent role: TDD direct-test author
- Model: GPT-6 Luna / xhigh
- Started: 2026-09-23
- Last updated: 2026-09-23
- Scope: direct test support and `src/test` only; no production edits.
- Owning plan: `docs/plans/end-to-end-business-delivery-implementation-plan.md`, Task 3 retry/carry-forward follow-up.
- Approved inputs: `.workspace/end-to-end-business-delivery-20260923/task-3-brief.md`, `docs/end-to-end-business-delivery-design.md` sections 5–6, and `docs/modules/activity-explanation/integration-contracts.md` sections 3–7.
- Current branch/worktree: formal source-code checkout at `caa9d56`.

## Completed

- Read the Task 3 brief, scoped source-agent instructions, relevant design/contracts, and test-writing guidance.
- Confirmed the formal checkout was shared with the parent/production worker and preserved its existing changes.
- Coordinated the approved `ActivityPacketCompletion` and coverage-v4 test seam with the production worker/root.
- Added the literal configured `--reuse-only` parser regression; root observed RED (`ARGUMENTS_INVALID` instead of reaching configuration), then the directly affected CLI test class passed 8/8 after the parser fix.
- Added typed result tests for all three dispositions, explicit-empty versus historical-absent completion, completed-slice subset validation, and preservation of an earlier same-key completion alongside a current required-scope gap.
- Added real filesystem-canonical M11 publication/fresh-reopen coverage for COMPLETE, INCOMPLETE, and UNDETERMINED; retained the null-slice direct Activity and valid content for the UNDETERMINED case. The same canonical test rejects incomplete-scope entry IDs outside the packet before install and checks both production coverage-v3 and coverage-v4 policies through the portable registry.
- Migrated the existing current Step05 publisher fixture to pass its explicit packet completion list without dropping assertions.
- Added the configured CLI storage-boundary regression with an absent auth environment and a nonexistent executable. Root observed the initial config-fixture RED caused by missing journal/output YAML paths; after using the existing full v3 config fixture, combined configured-entry coverage passed 9/9.
- Prepared the next direct historical-pair resolver test: a scripted successful DRAFT+REVIEW pair is saved through the real `ActivityExplainer`/private stage stores, a test-only original-shape M11 v3 checkpoint is installed and fresh-reopened from a filesystem canonical store under the same explicit batch owner, and the expected resolver output preserves the legacy activity/checkpoint while adding direct whole-packet completion. The production resolver type is not present yet, so this is ready for a compile RED.
- Root observed the resolver RED as exactly the missing `ActivityHistoricalReuseResolver` type; Terra then added the implementation. The direct test fixture remains aligned to the source batch owner and asserts the M11 checkpoint owner explicitly.
- Added a canonical-store negative fixture for a v4 coverage artifact with no `packetCompletion`; the fresh reader must reject it. Added the packet-value invariant that COMPLETE cannot be vacuous with an empty required-scope set.

## Current state

`ActivityRetryPacketScopeTest` already covers F1/F2 targeted retry and untouched INCOMPLETE/COMPLETE carry-forward. For the current store audit, added test-only helpers that preserve the old `seedDirectPair` API while seeding two direct pairs or only one selected packet pair. Extended `SourceAnalysisReuseOnlyReplayTest`'s real RunStore/canonical M11/private-journal fixture to construct A (two complete pairs), B (one selected pair plus one packet carried from A in batch-result-v3), and invoke the production offline audit seam without a Provider. Assertions require both Activities and both COMPLETE packet scopes to survive, the mixed checkpoint to remain the returned checkpoint, and private reviewed-result records for A/B to remain unchanged. Root owns the Maven slot; this worker did not run Maven.

## Changed files

- `src/test/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisReuseOnlyReplayTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityHistoricalReuseTestSupport.java`
- `progress/e2e-task3-completion-tests-luna.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` (formal source-code checkout) | PASS | HEAD `caa9d56`; only pre-existing untracked files listed by parent/worker. |
| `mvn -f pom.e2e-local-verification.xml -Dtest=SourceAnalysisConfiguredEntryPointTest test` (root-owned) | PASS | Directly affected configured CLI class: 8/8 passed after standalone flag parsing was fixed. |
| Root session 7802: `ActivityPacketCompletionTest` | PASS | Original typed tests: 2/2 passed. |
| Root session 11683: `ActivityPacketCompletionTest,ActivityPacketCompletionCheckpointTest#completeDirectPacketCompletionPublishesAndFreshReopensFromCanonicalStore` | PASS | Typed tests including overlap: 3; canonical COMPLETE publication/reopen: 1; total 4 passed. |
| Root session 18560: configured-entry and storage-boundary selectors | PASS | Configured entry tests plus storage boundary: 9 passed after fixture gained existing journal/output paths. |
| Root session 71939: `ActivityHistoricalReuseResolverTest#recoversCompleteDirectPairAgainstHistoricalV3WithoutChangingReviewedRows` | RED | Test compile failed only because `ActivityHistoricalReuseResolver` had not been implemented yet; Terra was authorized to add it. |
| Root session 65591: `ActivityPacketCompletionTest,ActivityPacketCompletionCheckpointTest,ActivityHistoricalReuseResolverTest` | RED | 7 tests, 1 failure/3 errors; empty COMPLETE correctly exposed the missing non-vacuous invariant, while three canonical/history/resolver cases failed in test setup by passing the portable policy-set directly to the canonical registry loader. |
| Root session 25734: `ActivityPacketCompletionTest,ActivityPacketCompletionCheckpointTest,ActivityHistoricalReuseResolverTest` | PASS | 7 tests passed after using the configured CLI's policy-set loader. |
| Root session 8241: `ActivityHistoricalReuseResolverTest` | RED | Exposed missing cross-batch `reusedFromModelBatchId` provenance traversal; child-local stage files were correctly absent in the real complete-reuse fixture. |
| Root session 9444: `ActivityHistoricalReuseResolverTest` | PASS | 3 tests passed: direct historical v3 recovery, explicit cross-batch chain with no child-local stage copies, and rejection of a damaged claimed stage. |
| Root session 57099: `ActivityLargePacketFormalEntryTest,ActivityHistoricalReuseResolverTest` | MIXED / EXPECTED RED | 22 tests; all 17 large-packet tests passed, including the corrected E1-only scope fixture. The two scoped-resolver/top-level-order errors were genuine production mismatches handed to Terra. |
| Root session 55589: direct Task3 selectors | MIXED / EXPECTED RED | 31 tests compiled; 29 passed and the two new Step07 packet-completion gate checks failed behaviorally as expected. |
| Root session 14274: `ActivityReadingPlanPersistenceTest,ActivityHistoricalReuseResolverTest,SourceAnalysisReuseOnlyReplayTest` | MIXED | Reader history (6) and resolver (3) passed; full CLI fixture initially stopped at `CONFIGURATION_INVALID` because it combined current Step05 reading materials with legacy flow/capsule fields. The YAML was corrected before session 3015. |
| Root session 3015: `SourceAnalysisReuseOnlyReplayTest` | FAIL / DIAGNOSTIC PENDING | `capture-local-git` returned exit 2 after config loaded. Fixture now uses the physical temp path and attaches stderr to capture/start failures; root owns the rerun. |
| Root session 99677: `SourceAnalysisReuseOnlyReplayTest` | FAIL / FIXTURE CAUSE | Capture and start passed; discovery clone failed because the graph fixture's test policy prefixes differed from the portable configured registry. Added a fixture overload accepting the runtime test registry; root owns verification. |
| Root session 45619: `SourceAnalysisReuseOnlyReplayTest` | FAIL / FIXTURE CAUSE | Capture/start and source/discovery clone passed; Step01–05 fixture publication hit `PERSISTENCE_MATERIAL_INDEX` because source artifacts used the model output registry. Split source/input and model/output registries/stores in the test fixture, keeping production policy sets unchanged. |
| Root session 69627: `SourceAnalysisReuseOnlyReplayTest` | EXPECTED BEHAVIOR RED | Full configured CLI/Agent/store fixture reached `ACTIVITY_REUSE_ONLY_OFFLINE_RESOLVER_REQUIRED` without fixture setup errors. Root owns the adoption rerun. |
| Root session 35715: `SourceAnalysisReuseOnlyReplayTest` | PASS | Literal `--reuse-only` through configured CLI/real Agent/store adoption: 1/1. |
| Root session 73054: `ProcessDiscoveryDualMaterialProtocolTest,ActivityReadingPlanPersistenceTest,ActivityPacketCompletionCheckpointTest` | PASS | 20/20 passed, including the exact Step07 gate, saved-v2, and canonical entry-order cases. |
| Root session 59344: `ActivityHistoricalReuseResolverTest` | FIXTURE ERRORS | 7 selected tests: 5 passed; the two new cases exposed only the null retry-profile helper and incorrect expectation that packet-result rows record stage-level reuse. Corrected; root owns the rerun. |
| Root session 34814: `ActivityHistoricalReuseResolverTest` | PASS | 7/7 passed, including the real retry path with stage-level reuse and a child-local packet result. |
| Root session 12820: `ActivityHistoricalReuseResolverTest` | PASS | 9/9 passed after v1 scoped-obligation support; both pure reopen and claimed missing-plan failure remained valid. |
| Root session 24708: `ActivityHistoricalReuseResolverTest` | TEST COMPILE FIXTURE ERROR | Main compilation passed; test compilation found only an undefined `stringList` helper in a new assertion. Replaced it with direct array-size/text assertions before root's rerun; no tests executed. |
| Root session 8704: `ActivityHistoricalReuseResolverTest` | MIXED / EXPECTED RED | 12 tests: 11 passed; the only failure was the intended v1 partial-navigation completion gap. The same-key positive/negative cases passed. |
| Root session 87070: `ActivityHistoricalReuseResolverTest` | TEST COMPILE FIXTURE ERROR | No tests ran; the new partial-result assertion called `singleElement` on `OptionalAssert`. Unwrapped the optional to its list; whitespace check is clean. |
| Root session 28666: `ActivityHistoricalReuseResolverTest` | MIXED / EXPECTED RED | 13 tests: 12 passed; the partial-no-aggregate case exposed that a single scoped slice result was being validated as a direct whole-packet pair. Root applied a narrowly scoped resolver correction; the new slice-body mismatch test is queued alongside its verification. |
| Root session 95877: `ActivityHistoricalReuseResolverTest` | PASS | 14/14, including the actual no-aggregate incomplete packet and temp-only private-slice/public M11 mismatch rejection. |
| `SourceAnalysisReuseOnlyReplayTest` offline batch-v2 group | PASS | Root reported 1/1 GREEN. New normal-writer/v1-metadata assertions are now being prepared in the same existing formal fixture; no Maven is run by this worker. |
| `SourceAnalysisReuseOnlyReplayTest` normal-v2/v1/inspect additions | FROZEN / ROOT VERIFICATION PENDING | Normal writer writes/reopens v2 with literal COMPLETE packet scope and explicit null adoption pair; valid historical v1 is reused, missing `activityCheckpoint` is required to fail, and configured inspect checks `packetCompletionCount=1`. This worker did not run Maven. |
| `git diff --check` after scoped cross-batch retry coverage | PASS | No whitespace errors. |
| Root session 44686: six directly affected classes | MIXED / EXPECTED RED | 68 tests; 5 failures/1 error, all new v2/v4-mode/filter/second-adoption regressions were behavioral REDs; additionally identified that the multi-entry edit had displaced the M3 preflight failure. Restored that original case and split out the explicit M3 REVIEW failure test. |
| `git diff --check` before latest group | PASS | No whitespace errors after the scoped-failure fixture correction, stable-ID ordering-fixture correction, current Step07 gate tests, pure-v2 contradiction test, and completion entry-order test. Latest resolver additions await root verification. |
| `git diff --check` after mixed carry-forward audit fixture | PASS | No whitespace errors. New direct test has not been run; no Maven was started by this worker. |

## Decisions

- Preserve legacy `ActivityExplanationResult` constructors as packet-completion-absent; tests must not let historical callers synthesize an empty successful packet set.
- Keep assertions on real canonical store reopen and the existing configured CLI/Agent seam; a reflection-only or fake-store result is insufficient.
- Do not run Maven, invoke a model, change production code, stage, or commit.

## Blockers

Root owns all Maven runs and has production changes in the shared worktree. The added multi-packet audit is a direct reflected call to the same offline historical audit seam used by reuse-only execution; it does not construct a separate CLI C run. If root requires a full C lifecycle record rather than this audit boundary, it will need a larger fixture change.

## Exact next action

Freeze `SourceAnalysisReuseOnlyReplayTest` and `ActivityHistoricalReuseTestSupport` for root's focused Maven run (`-Dtest=SourceAnalysisReuseOnlyReplayTest`). Report any fixture setup error without editing production. No Maven was run by this worker.

## Resume checks

- Confirm the production worker did not change shared tests concurrently.
- Re-read this note and `git status --short` before further edits.
- Do not start Maven; root owns the sole Maven slot.

## Plan closeout destinations

- Durable decisions: `docs/modules/activity-explanation/integration-contracts.md` (owned by implementation/root).
- Remaining issues: Task 3 plan handoff.
- Verification and output references: `.workspace/end-to-end-business-delivery-20260923/task-3-tests-report.md`.
