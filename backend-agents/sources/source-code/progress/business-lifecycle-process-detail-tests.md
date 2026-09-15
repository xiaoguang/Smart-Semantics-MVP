# Progress: business lifecycle process-detail RED tests

- Status: COMPLETE (RED handoff)
- Agent role: Luna/xhigh RED-test author
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: Add focused Step07 tests for the approved lifecycle-correction contracts only; production, prompts, schemas/resources, publisher, and design/docs remain unchanged.
- Approved inputs: `docs/plans/business-process-discovery-and-reconstruction-change-design.md`, `docs/modules/07-repository-knowledge.md`, `docs/references/semantic-interpretation-prompts.md`, existing `BusinessProcessDiscoveryTest`, and `RepositoryBusinessProcessCatalog`.
- Current branch/worktree: `codex/business-lifecycle-readable-implementation` / `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`

## Required RED coverage

- `PROCESS_DRAFT` and `PROCESS_REVIEW` response schemas require a nonblank `ProcessStage.narrative`.
- `BusinessRule` carries nonempty wire-local activity-use IDs and the parser maps them to stored/global use IDs.
- A rule cannot name an activity use from another candidate or cite statement/source refs outside all specified uses.
- `PROCESS_REVIEW` receives the complete actual draft and complete requested saved source snippets, rather than only previews.
- `ProcessCoverage.ActivityDisposition` preserves the original Activity name through creation/JSON when this can be isolated without Task4 publication changes.

## Current state

- The current production model is v1 and has no `ProcessStage.narrative`, no rule activity-use collection, and no `ActivityDisposition.name` accessor. Tests will be adapted to compile against current APIs where possible; an absent accessor may remain an intentionally isolated compile-time RED and will be documented here.
- No model, network, live source, or customer build is used.

## Completed

- Extended `BusinessProcessDiscoveryTest` with a focused schema contract assertion for both `BUSINESS_PROCESS_DRAFT` and `BUSINESS_PROCESS_REVIEW`.
- Added a wire fixture containing `activityUseLocalIds` and a reflection-based assertion for the future stored/global `BusinessRule.activityUseIds` accessor, keeping the current v1 record compileable while isolating its missing accessor as RED.
- Added rejection tests for a foreign/unknown activity-use local ID and for a source reference outside the rule's specified activity use.
- Added a review-input assertion that compares `actualDraft` exactly with the complete draft response and checks a requested saved snippet includes content beyond the eight-line preview.
- Deliberately skipped `ProcessCoverage.ActivityDisposition.name`; the current record has no accessor and this task was narrowed to the four executable RED areas.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessDiscoveryTest test` | RED | Test compilation passed; 20 tests ran with 4 failures, 0 errors, 0 skips. Failures are the missing `narrative` schema requirement, missing `BusinessRule.activityUseIds` accessor, and both rule-scope violations being accepted. The complete-review-input test passes against current production. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java` | PASS | Focused test source formatted. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java progress/business-lifecycle-process-detail-tests.md` | PASS | No whitespace errors reported. |

## Changed files

- `progress/business-lifecycle-process-detail-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java`

## Blockers

- None. Four intended RED failures are behavioral assertions; the review-input test is a passing regression guard.

## Exact next action

- Terra/xhigh may implement only the approved Step07 lifecycle contract, then rerun this class selector. Do not modify the test fixture to accept v1 behavior.
