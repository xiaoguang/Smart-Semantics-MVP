# Progress: activity-explainer-tests

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Establish the smallest public-seam RED for Step 06 ActivityExplainer: one persisted BusinessMaterial, exactly one scripted DRAFT followed by one REVIEW receiving the full actual draft, complete reviewed activity fields/source refs/questions, and fail-closed unknown SourceRef/model-injected path handling.
- Approved inputs: `AGENTS.md`, `docs/analysis-steps/06-flow-interpretation.md` sections 5–7, existing persisted BusinessMaterialBuilder fixture/public seam, deterministic scripted Provider only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the root and scoped repository instructions.
- Read Step 06 sections 5–7, including the target `analysis.interpretation.activity` API, DRAFT/REVIEW call bound, full `actualDraft` requirement, SourceRef allowlist, and fatal unknown-ref/path rules.
- Confirmed the current target has no ActivityExplainer production seam yet; existing material tests provide a real persisted BusinessMaterial fixture.
- Added one bounded public-seam test in `ActivityExplainerTest`. It builds the real persisted
  BusinessMaterial checkpoint, selects one material, scripts exactly `ACTIVITY_DRAFT` then
  `ACTIVITY_REVIEW`, asserts REVIEW receives the complete actual DRAFT, and checks the reviewed
  purpose/objects/conditions/steps/code-defined result/source refs/questions. The same test then
  supplies an unknown SourceRef containing an injected path and requires
  `ACTIVITY_SOURCE_SCOPE_INVALID` after exactly one call.
- Corrected the valid scripted response to derive its first two SourceRefs from the selected
  material's actual allowlist; the invalid response retains the separate injected path ref.
- Kept the clean model response free of `materialId`; the outer task owns material identity.
- The initial direct selector reached the intended RED while the public class was absent; after the
  ActivityExplainer implementation appeared in the shared worktree, the corrected direct selector
  is GREEN with one test and zero failures/errors/skips.

## Current state

- The test remains a public-seam reflection test for absent-type RED compatibility, but now crosses
  the available ActivityExplainer implementation and verifies both the valid two-call path and the
  invalid one-call path. No production files or design documents were changed by this task.

## Changed files

- `progress/activity-explainer-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainerTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Shared worktree is dirty from other agents; changes are preserved. |
| Scoped source/design inspection | PASS | Step 06 ActivityExplainer is NOT IMPLEMENTED; Builder has a persisted material checkpoint seam. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainerTest.java` | PASS | Formatter completed successfully. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ActivityExplainerTest test` (before implementation) | EXPECTED RED | testCompile passed; 1 test, 1 failure, 0 errors/skips; exact failure `ACTIVITY_EXPLAINER_NOT_IMPLEMENTED` caused by missing `org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplainer`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ActivityExplainerTest test` (after SourceRef correction) | PASS | testCompile passed; 1 test, 0 failures/errors/skips; valid DRAFT→REVIEW and injected-path rejection passed. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainerTest.java progress/activity-explainer-tests.md` | PASS | No whitespace errors. |

## Decisions

- Use the existing `BusinessMaterialBuilder` fixture path rather than inventing a fake material or touching legacy finite-key/proposal/process APIs.
- Keep the first RED bounded to one material and a deterministic two-response Provider; no live model, network, customer build, or retry.
- Treat `ACTIVITY_EXPLAINER_NOT_IMPLEMENTED` as the only allowed pre-implementation reflection failure; once the seam exists, assertions will cover DRAFT/REVIEW order, full draft echo, complete reviewed fields, and unknown-ref/path rejection.
- Keep `materialId` out of model DRAFT/REVIEW response JSON; task ownership stays program-side.
- Derive valid response refs from the selected material's allowlist so the fixture remains stable
  under globally allocated short-ref values.

## Blockers

- The concrete new Provider transport constructors are not present yet; the test uses only the
  documented public seam and reflection for the absent target types. This is expected to resolve
  when the implementation lands.

## Exact next action

- Parent can retain this test as the focused ActivityExplainer contract check; no further work is
  required in this slice.

## Resume checks

- Re-read this file and `git status --short` before any later edits.
- Do not modify production, design, fixtures, or any progress file other than this one.
