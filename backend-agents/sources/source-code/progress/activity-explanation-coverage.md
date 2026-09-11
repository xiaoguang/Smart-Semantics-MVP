# Progress: activity explanation coverage

- Status: COMPLETE
- Agent role: Root coordinator; TDD continuation for the Step 06 business-first route
- Model: gpt-6-astra / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add the next bounded ActivityExplainer behavior: a material that exceeds the model-input budget becomes an observable entry-level coverage result with zero Provider calls, instead of failing the whole local explanation pass. This slice does not add a live provider, retry logic, or a new interpretation route.
- Approved inputs: Scoped `AGENTS.md`; `docs/DESIGN.md`; Step 06 sections 5.1–5.7; persisted BusinessMaterial public seam; deterministic test fixture only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Verified the completed first ActivityExplainer vertical slice: its direct selector reports 1 test, 0 failures/errors/skips.
- Reviewed the target Step 06 capacity rule: capacity failure is zero calls plus a concrete `NOT_ANALYZED_BUDGET` coverage disposition, not an exception that erases the rest of the denominator.
- Added a public-seam test against a persisted two-entry material checkpoint. It proves that an over-budget model packet calls the Provider zero times and leaves every affected entry as `NOT_ANALYZED / NOT_ANALYZED_BUDGET`.
- Implemented the smallest corresponding production behavior: `ActivityExplanationResult` now carries entry-level coverage; normal materials carry `ANALYZED` or `ANALYZED_WITH_GAPS`, an empty valid model result is `NOT_ANALYZED / MODEL_NO_ACTIVITY`, and M1 entries without material remain `NOT_ANALYZED` with their existing concrete reason.

## Current state

- The coverage calculation is green and contains no Provider or persistence side effect. Durable reviewed-activity and coverage checkpoint publication remains the next independent Step 06 slice.

## Changed files

- `progress/activity-explanation-coverage.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityEntryCoverage.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplanationResult.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainer.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplanationBudgetTest.java`
- `docs/analysis-steps/06-flow-interpretation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityExplainerTest test` | PASS | 1 test; 0 failures/errors/skips. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityExplanationBudgetTest test` | EXPECTED RED | 1 test; 1 assertion failure; existing code threw `ACTIVITY_INPUT_OVER_BUDGET` instead of returning coverage. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityExplanationBudgetTest,ActivityExplainerTest test` | PASS | 2 tests; 0 failures/errors/skips. |
| `git diff --check` for owned paths | PASS | No whitespace errors. |

## Decisions

- Keep the behavior entry-scoped and deterministic: this is a normal coverage result, not an exceptional Provider failure.
- Defer durable `activity-explanations.jsonl` publication to the next bounded slice; this one establishes the correct denominator before adding storage plumbing.
- Preserve an empty successful model response as coverage rather than treating it as an exception; it is an honest semantic non-result that downstream process reconstruction must see.

## Blockers

- None.

## Exact next action

- A future Agent should add the next TDD slice for durable `activity-explanations.jsonl` and `activity-coverage.json` publication, then re-open it through the existing canonical store.

## Resume checks

- Re-read this file, check the shared worktree status, and rerun only the direct activity selectors before further changes.
