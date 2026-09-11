# Progress: business material full method outline

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-terra / xhigh implementation, following approved Sol/ultra design
- Started: 2026-09-11 03:07 NDT
- Last updated: 2026-09-11 03:13 NDT
- Scope: Preserve syntax-known conditions and receiver-qualified calls from an already selected
  full Java method in a compact automatic business-material observation, even when its source-ref
  windows cannot include every middle statement. No business vocabulary, source expansion, or live
  Provider call.
- Approved inputs: Existing frozen fixture source and the approved business-first Step 06 design.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Identified the remaining selection risk after bounded source windows: a direct action in the
  middle of a long selected method can fall outside the retained source references.
- Added a public fallback-material regression: with only three source-reference slots, the packet
  contains the handler opening and final audit call but not the middle approval call as raw text.
- Changed the bounded syntax projection to read the full methods already selected as material,
  while retaining only the existing short source references for model reading and click-back.
- Updated the Step 06/design/readme implementation record with the precise bounded behavior.

## Current state

- The new direct selector is green: the model packet retains a neutral `源码调用` observation for
  the middle action without receiving that action as an additional raw source window.

## Changed files

- `progress/business-material-full-method-outline.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderFallbackTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `README.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=BusinessMaterialBuilderFallbackTest#summarizesAMiddleDirectCallWhenOnlyTheOpeningAndFinalWindowsFit test` | RED then PASS | RED omitted `approvalClient.record(normalized)`; GREEN preserves it as a neutral observation while raw refs remain bounded. |
| `mvn -Dtest=BusinessMaterialBuilderTest,BusinessMaterialBuilderFallbackTest test` | PASS | 8 tests, 0 failures/errors/skips. |
| scoped `spotless:apply` and `git diff --check` | PASS | Only the owned Java files were formatted; no whitespace error. |

## Decisions

- Full-method syntax projection remains bounded to the methods already selected as material; it is
  not a new call-graph traversal and does not infer a business object, purpose, role, or sequence.
- A compact syntax observation is permitted to describe an already selected source statement even
  when its raw ref was not retained under the source-window budget; it never claims business
  meaning, external success, or a new source location.

## Blockers

- The implementation task has no blocker. Real automatic-packet quality validation remains blocked
  by the incomplete fixed Git object set, not by this unit-test fixture.

## Exact next action

- Continue the approved Step 06 work with the next missing capability: turn the existing material
  checkpoint into a complete activity-explanation execution path only after a small automatic
  real packet can be inspected.

## Resume checks

- Read this file, inspect the full-method observation inputs, run the direct Builder selector, and
  do not invoke Luna until a regenerated automatic real packet is reviewed.
