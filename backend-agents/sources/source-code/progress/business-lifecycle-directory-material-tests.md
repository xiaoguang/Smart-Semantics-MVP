# Progress: business lifecycle directory/material RED tests

- Status: COMPLETE
- Agent role: RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: Focused BusinessProcessDiscoveryTest/test-support coverage for the approved Step07 lifecycle-directory correction; tests and this progress file only.
- Approved inputs: `docs/plans/business-process-discovery-and-reconstruction-change-design.md` sections 1-5; frozen fixtures and existing reviewed Activity business content; no model, network, or source scan.
- Current branch/worktree: nested Git checkout `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`, branch `codex/business-lifecycle-readable-implementation`; preserve unrelated existing worktree changes.

## Completed

- Read repository, backend-agent, and source-code scoped instructions.
- Read the approved Step07 change-design sections 1-5 and progress template.
- Created this progress file before test edits.

## Current state

Added and audited four focused RED behaviors in `BusinessProcessDiscoveryTest`, including conversion of the orphan-member test to an explicit rejection contract. The scripted fixture now exposes catalog/process inputs and a ten-line frozen snippet for deterministic assertions. The exact class run produced four intended RED failures with no compilation or runtime errors.

## Changed files

- `progress/business-lifecycle-directory-material-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=BusinessProcessDiscoveryTest#sendsExistingReviewedBusinessRulesUnchangedInEachActivityIndexCard test` | SETUP BLOCKED | Maven stopped before compilation: no user-level JDK 17 toolchain was configured. Retried with the project-required explicit toolchain below. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessDiscoveryTest#sendsExistingReviewedBusinessRulesUnchangedInEachActivityIndexCard test` | RED | 1 test, 1 failure, 0 errors; update card `businessRules` was `[]` instead of the reviewed rule. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessDiscoveryTest#preservesDistinctActivityVariantsButSendsOneCompleteActivityBody test` | RED | 1 test, 1 failure, 0 errors; current parser rejected the legal duplicate Activity ID with `PROCESS_CATALOG_INVALID_CANDIDATE_MEMBERSHIP`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessDiscoveryTest#sendsSourceDirectoryWithScopeActivityIdsAndFirstEightPhysicalSnippetLines test` | RED | 1 test, 1 failure, 0 errors; current source directory had no `activityIds` (and therefore cannot provide `openingLines`). |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessDiscoveryTest#rejectsAnOrphanProcessMemberInsteadOfSilentlyDowngradingIt test` | RED | 1 test, 1 failure, 0 errors; current implementation returned a downgraded result instead of throwing. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessDiscoveryTest#discoversOneDetailedMultiActivityProcessWithoutLosingConcretePredicates test` | PASS | Existing directly affected lifecycle fixture still passes: 1 test, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java spotless:check` | PASS | Scoped test formatting check succeeded. |
| `mvn -t .mvn/toolchains.xml -Dtest=BusinessProcessDiscoveryTest test` | RED | Exact class run: 15 tests, 4 intended failures, 0 errors, 5.2s. |

## Decisions

- Keep assertions at the public/deep seam exposed by `BusinessProcessDiscoveryTest`; add a focused test class only if the existing fixture seam cannot express the contract.
- `sendsExistingReviewedBusinessRulesUnchangedInEachActivityIndexCard` catches `ActivityIndexCard.from/toJson` omitting or altering `ReviewedActivity.businessRules`.
- `preservesDistinctActivityVariantsButSendsOneCompleteActivityBody` catches candidate parsing keyed only by `activityId` and process material assembly duplicating or dropping complete Activity bodies for distinct variants.
- `sendsSourceDirectoryWithScopeActivityIdsAndFirstEightPhysicalSnippetLines` catches generic source-purpose projection instead of candidate-scoped Activity IDs and the first eight physical snippet lines.
- `rejectsAnOrphanProcessMemberInsteadOfSilentlyDowngradingIt` catches the catalog parser silently changing an explicit `PROCESS_MEMBER` to `UNCLASSIFIED` when no candidate includes that Activity.
- The rejection test currently assumes explicit error code `PROCESS_CATALOG_PROCESS_MEMBER_WITHOUT_CANDIDATE`; the design specifies rejection but does not prescribe this exact code.

### Named production mutations required for GREEN

- `ActivityIndexCard.from/toJson` must retain the reviewed `businessRules` array unchanged in every catalog card.
- Catalog candidate membership validation must allow repeated `activityId` values when their `variant` values differ; process material assembly must then deduplicate the complete Activity body while preserving both uses.
- `sourceDirectoryJson` must project each existing source ref's candidate-scoped `activityIds` and the first eight physical snippet lines, instead of only the generic `purpose` string.
- Catalog parsing must reject an explicit `PROCESS_MEMBER` disposition with no real candidate membership rather than silently rewriting it to `UNCLASSIFIED`; the exact error code remains a production-contract choice (the test currently names `PROCESS_CATALOG_PROCESS_MEMBER_WITHOUT_CANDIDATE`).

## Blockers

- None known.

## Exact next action

Commit only the changed test and this progress file after one final scope/status check; report the four RED outcomes and the error-code contract concern to the parent.

## Resume checks

- Re-run `git status --short` and inspect this progress file before continuing.
