# Progress: Activity coverage semantics

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-luna / xhigh for test design; no product Provider call
- Started: 2026-09-11 16:46 UTC
- Last updated: 2026-09-11 17:04 UTC
- Scope: Correct activity coverage so that a complete Flow material is not labelled `ANALYZED_WITH_GAPS` solely because the material records a generic snippet-budget explanation. Preserve substantive technical limitations and fallback materials as gaps. This does not alter source analysis, business content, model prompts or the failed DepotHead request.
- Approved inputs: Active business-first design; existing `FLOW_PREFERRED` and `ENTRY_SOURCE_FALLBACK` material contract.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Confirmed the current implementation uses `limitations().isEmpty()` as the only coverage discriminator. Every Builder material includes a generic budget explanation, so even a complete user-registration Flow is incorrectly classified `ANALYZED_WITH_GAPS`.
- Added the direct public-seam RED: a `FLOW_PREFERRED` material with only the generic budget notice initially produced `ANALYZED_WITH_GAPS`.
- Added a material-contract predicate: source fallback and every limitation other than the exact standard budget notice remain substantive; the standard notice does not.
- Made both material coverage and reviewed activity coverage consume that same predicate.
- Re-ran the direct ActivityExplainer selector: 2 tests pass with zero product Provider calls.

## Current state

- The coverage correction is implemented. The named live harness now derives its expected coverage from the same material contract, ready for the approved user-registration sample.

## Changed files

- progress/activity-coverage-semantics.md
- src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterial.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainer.java
- src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainerDirectEntryContextTest.java
- src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderTest.java
- src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/LiveLunaAutomaticMaterialIT.java
- docs/analysis-steps/06-flow-interpretation.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| persisted user-registration material inspection | PASS | It is `FLOW_PREFERRED`, but current activity coverage would still mark it with gaps because its generic budget narrative is nonempty. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ActivityExplainerDirectEntryContextTest test` | PASS | 2 tests, 0 failures/errors/skips; only the generic budget notice now yields `ANALYZED`. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ActivityExplainerDirectEntryContextTest,BusinessMaterialBuilderTest,LiveLunaAutomaticMaterialSelectionTest test` | PASS | 7 tests, 0 failures/errors/skips; builder, activity coverage and named material selection agree. |
| `mvn -o -t .mvn/toolchains.xml spotless:apply` and `git diff --check` | PASS | Three touched Java files formatted; no whitespace errors. |

## Decisions

- Do not parse business text or add an industry rule. The material contract now has one exact generic notice; source fallback and all other listed technical limitations remain coverage gaps.

## Blockers

- None.

## Exact next action

- Coverage correction complete. The separately scoped live-sample task may now run the explicitly approved user-registration DRAFT+REVIEW under the confirmed Codex state-write capability.

## Resume checks

- Read this file and retain the distinction: bounded snippets are a reader notice, while fallback and actual technical limitations remain gaps.
